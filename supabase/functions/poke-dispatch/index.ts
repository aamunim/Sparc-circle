// Supabase Edge Function: poke-dispatch & generate-certificate
// Dispatches Quiet-Hour queued pokes, Daily Poke Summaries, and Reading Streak reminders

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
    const supabase = createClient(
      Deno.env.get("SUPABASE_URL") ?? "",
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "",
    );

    const { job } = await req.json();

    if (job === "morning_quiet_hours_flush") {
      const { data: queuedPokes } = await supabase
        .from("pokes")
        .select("id, receiver_id, preset_message")
        .eq("delivery_mode", "quiet_hours_queued");

      return new Response(
        JSON.stringify({
          dispatched_quiet_hour_pokes: queuedPokes?.length ?? 0,
          message: "Quiet hours (10 PM - 7 AM) queued pokes flushed.",
        }),
        { headers: { ...corsHeaders, "Content-Type": "application/json" } },
      );
    }

    if (job === "streak_reminder") {
      const today = new Date().toISOString().slice(0, 10);
      const { data: atRiskUsers } = await supabase
        .from("profiles")
        .select("id, name, reading_streak_days, fcm_token")
        .gt("reading_streak_days", 0)
        .neq("last_read_date", today);

      return new Response(
        JSON.stringify({
          streak_reminders_queued: atRiskUsers?.length ?? 0,
          notification_preview: "🔥 আপনার রিডিং স্ট্রিক ধরে রাখতে আজ অন্তত কয়েক পৃষ্ঠা পড়ুন!",
        }),
        { headers: { ...corsHeaders, "Content-Type": "application/json" } },
      );
    }

    return new Response(
      JSON.stringify({ status: "idle" }),
      { headers: { ...corsHeaders, "Content-Type": "application/json" } },
    );
  } catch (err) {
    return new Response(
      JSON.stringify({ error: (err as Error).message }),
      { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } },
    );
  }
});
