// Server Edge Function: auth-register
// 1. Default action: Creates an auto-confirmed user via Admin API (bypassing SMTP 2/hr HTTP 429 rate limits)
//    and immediately returns an authenticated session token + provisioned researcher profile.
// 2. action === "admin_reset_password": Allows an authenticated SPARC Admin (base_role = 'admin')
//    to reset any researcher's password in 1 click (and optionally email via Resend if configured in admin_api_vault).
// 3. action === "change_my_password": Allows any signed-in user to update their own password securely.

import { serve } from "https://deno.land/std@0.224.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.45.4";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
    const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
    const anonKey = Deno.env.get("SUPABASE_ANON_KEY") ?? serviceRoleKey;

    const adminClient = createClient(supabaseUrl, serviceRoleKey, {
      auth: { autoRefreshToken: false, persistSession: false },
    });
    const publicClient = createClient(supabaseUrl, anonKey, {
      auth: { autoRefreshToken: false, persistSession: false },
    });

    const payload = await req.json();

    // ------------------------------------------------------------------------
    // Self-Service Authenticated Password Change (from redesigned Settings Page)
    // ------------------------------------------------------------------------
    if (payload?.action === "change_my_password") {
      const authHeader = req.headers.get("Authorization") ?? "";
      const jwt = authHeader.replace(/^Bearer\s+/i, "").trim();
      if (!jwt) throw new Error("Authentication required.");

      const { data: callerAuth, error: callerErr } = await adminClient.auth.getUser(jwt);
      if (callerErr || !callerAuth?.user) throw new Error("সেশনটির মেয়াদ শেষ হয়েছে। আবার সাইন ইন করুন।");

      const newPassword = String(payload.new_password ?? "");
      if (newPassword.length < 6) {
        throw new Error("নতুন পাসওয়ার্ড কমপক্ষে ৬ অক্ষরের হতে হবে।");
      }

      const { error: updErr } = await adminClient.auth.admin.updateUserById(callerAuth.user.id, {
        password: newPassword,
        email_confirm: true,
      });
      if (updErr) throw updErr;

      return new Response(
        JSON.stringify({ updated: true }),
        { headers: { ...corsHeaders, "Content-Type": "application/json" } },
      );
    }

    // ------------------------------------------------------------------------
    // Admin 1-Click Password Reset (+ Optional Resend Email from admin_api_vault)
    // ------------------------------------------------------------------------
    if (payload?.action === "admin_reset_password") {
      const authHeader = req.headers.get("Authorization") ?? "";
      const jwt = authHeader.replace(/^Bearer\s+/i, "").trim();
      if (!jwt) throw new Error("Admin authentication required.");

      const { data: callerAuth, error: callerErr } = await adminClient.auth.getUser(jwt);
      if (callerErr || !callerAuth?.user) throw new Error("Invalid session.");

      const { data: callerProf } = await adminClient
        .from("profiles")
        .select("role,base_role")
        .eq("id", callerAuth.user.id)
        .single();

      if (callerProf?.base_role !== "admin" && callerProf?.role !== "admin") {
        throw new Error("শুধুমাত্র অ্যাডমিন পাসওয়ার্ড রিসেট করতে পারেন।");
      }

      const targetUserId = String(payload.target_user_id ?? "").trim();
      const newPassword = String(payload.new_password ?? "");
      if (!targetUserId || newPassword.length < 6) {
        throw new Error("নতুন পাসওয়ার্ড কমপক্ষে ৬ অক্ষরের হতে হবে।");
      }

      const { data: targetUser, error: updErr } = await adminClient.auth.admin.updateUserById(targetUserId, {
        password: newPassword,
        email_confirm: true,
      });
      if (updErr) throw updErr;

      await adminClient
        .from("password_reset_requests")
        .update({ status: "resolved", resolved_at: new Date().toISOString() })
        .eq("user_id", targetUserId)
        .eq("status", "pending");

      // Optional: dispatch email via Resend if configured in admin_api_vault
      let emailed = false;
      const { data: vault } = await adminClient
        .from("admin_api_vault")
        .select("resend_api_key")
        .eq("id", "global")
        .maybeSingle();
      const resendKey = String(vault?.resend_api_key ?? "").trim();
      const targetEmail = targetUser?.user?.email;
      if (resendKey && targetEmail) {
        try {
          const mailResp = await fetch("https://api.resend.com/emails", {
            method: "POST",
            headers: {
              "Authorization": `Bearer ${resendKey}`,
              "Content-Type": "application/json",
            },
            body: JSON.stringify({
              from: "SPARC Study Circle <onboarding@resend.dev>",
              to: [targetEmail],
              subject: "SPARC Study Circle — পাসওয়ার্ড রিসেট সম্পন্ন",
              html: `<p>আসসালামু আলাইকুম,</p><p>আপনার SPARC Study Circle অ্যাকাউন্টের নতুন অস্থায়ী পাসওয়ার্ড: <strong>${newPassword}</strong></p><p>অ্যাপে সাইন ইন করার পর সেটিংস পেইজ থেকে পাসওয়ার্ড পরিবর্তন করে নিন।</p>`,
            }),
          });
          emailed = mailResp.ok;
        } catch (_e) {
          emailed = false;
        }
      }

      return new Response(
        JSON.stringify({ updated: true, emailed }),
        { headers: { ...corsHeaders, "Content-Type": "application/json" } },
      );
    }

    // ------------------------------------------------------------------------
    // Standard Auto-Confirmed Researcher Registration
    // ------------------------------------------------------------------------
    const { email, password, name, department, skills, referral_code } = payload;
    const cleanEmail = String(email ?? "").trim().toLowerCase();
    const cleanPassword = String(password ?? "");
    const cleanName = String(name ?? "").trim() || cleanEmail.split("@")[0];
    const cleanDept = String(department ?? "").trim() || "General Research";

    if (!cleanEmail || cleanPassword.length < 6) {
      throw new Error("Please provide a valid email and a password of at least 6 characters.");
    }

    const { data: created, error: createErr } = await adminClient.auth.admin.createUser({
      email: cleanEmail,
      password: cleanPassword,
      email_confirm: true,
      user_metadata: {
        name: cleanName,
        department: cleanDept,
      },
    });

    if (createErr && !createErr.message.toLowerCase().includes("already")) {
      throw createErr;
    }

    const userId = created?.user?.id;
    if (userId) {
      if (Array.isArray(skills) && skills.length > 0) {
        await adminClient
          .from("profiles")
          .update({ skills, department: cleanDept, name: cleanName })
          .eq("id", userId);
      }
      if (referral_code && String(referral_code).trim().length > 0) {
        await adminClient.rpc("apply_referral_bonus", {
          p_new_user_id: userId,
          p_referral_code: String(referral_code).trim(),
        });
      }
    }

    const { data: signInData, error: signInErr } = await publicClient.auth.signInWithPassword({
      email: cleanEmail,
      password: cleanPassword,
    });

    if (signInErr) throw signInErr;

    return new Response(
      JSON.stringify({
        user: signInData.user,
        access_token: signInData.session?.access_token ?? "",
        refresh_token: signInData.session?.refresh_token ?? "",
      }),
      { headers: { ...corsHeaders, "Content-Type": "application/json" } },
    );
  } catch (err) {
    return new Response(
      JSON.stringify({ error: (err as Error).message }),
      { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } },
    );
  }
});
