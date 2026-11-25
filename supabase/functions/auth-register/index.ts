// Supabase Edge Function: auth-register
// Creates an auto-confirmed user via Admin API (bypassing SMTP 2/hr HTTP 429 rate limits)
// and immediately returns an authenticated session token + provisioned researcher profile.

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

    const { email, password, name, department, skills, referral_code } = await req.json();
    const cleanEmail = String(email ?? "").trim().toLowerCase();
    const cleanPassword = String(password ?? "");
    const cleanName = String(name ?? "").trim() || cleanEmail.split("@")[0];
    const cleanDept = String(department ?? "").trim() || "General Research";

    if (!cleanEmail || cleanPassword.length < 6) {
      throw new Error("Please provide a valid email and a password of at least 6 characters.");
    }

    // Create confirmed user via Admin API — zero confirmation emails sent, zero HTTP 429 rate limits!
    const { data: created, error: createErr } = await adminClient.auth.admin.createUser({
      email: cleanEmail,
      password: cleanPassword,
      email_confirm: true,
      user_metadata: {
        name: cleanName,
        department: cleanDept,
      },
    });

    if (createErr) {
      // If user already exists, allow signing in directly with their password
      if (!createErr.message.toLowerCase().includes("already")) {
        throw createErr;
      }
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

    // Immediately issue a real JWT session
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
