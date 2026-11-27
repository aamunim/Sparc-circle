// Server Edge Function: ai-writing-assistant
// Reads Gemini API Key & Model securely from `public.admin_api_vault` (managed via Web Admin Portal)
// or falls back to environment secret `GEMINI_API_KEY` / heuristic academic coach.

import { serve } from "https://deno.land/std@0.224.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.45.4";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type, x-gemini-api-key",
};

interface WritingAssistantRequest {
  action?: "analyze" | "ping_test";
  chapter_no?: number;
  prompt?: string;
  content?: string;
  language?: "bn" | "en";
  min_words?: number;
  max_words?: number;
  gemini_api_key?: string;
  gemini_model?: string;
}

serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const body: WritingAssistantRequest = await req.json();

    const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
    const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
    let vaultKey = "";
    let vaultModel = "gemini-2.5-flash";
    let aiCoachEnabled = true;

    if (supabaseUrl && serviceRoleKey) {
      const adminDb = createClient(supabaseUrl, serviceRoleKey, {
        auth: { autoRefreshToken: false, persistSession: false },
      });
      const { data: vaultRow } = await adminDb
        .from("admin_api_vault")
        .select("gemini_api_key,gemini_model,ai_coach_enabled")
        .eq("id", "global")
        .maybeSingle();
      if (vaultRow) {
        vaultKey = String(vaultRow.gemini_api_key ?? "").trim();
        vaultModel = String(vaultRow.gemini_model ?? "gemini-2.5-flash").trim() || "gemini-2.5-flash";
        aiCoachEnabled = vaultRow.ai_coach_enabled !== false;
      }
    }

    const geminiApiKey =
      body.gemini_api_key?.trim() ||
      vaultKey ||
      req.headers.get("x-gemini-api-key")?.trim() ||
      Deno.env.get("GEMINI_API_KEY")?.trim() ||
      "";
    const activeModel = body.gemini_model?.trim() || vaultModel || "gemini-2.5-flash";

    // 1-Click Connection Test from Web Admin Portal
    if (body.action === "ping_test") {
      if (!geminiApiKey) {
        return new Response(
          JSON.stringify({ ok: false, error: "কোনো Gemini API Key সংরক্ষিত নেই।" }),
          { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } },
        );
      }
      const pingResp = await fetch(
        `https://generativelanguage.googleapis.com/v1beta/models/${activeModel}:generateContent?key=${geminiApiKey}`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            contents: [{ role: "user", parts: [{ text: "Reply with OK" }] }],
          }),
        },
      );
      if (!pingResp.ok) {
        const errText = await pingResp.text();
        return new Response(
          JSON.stringify({ ok: false, error: `Gemini API HTTP ${pingResp.status}: ${errText.slice(0, 180)}` }),
          { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } },
        );
      }
      return new Response(
        JSON.stringify({ ok: true, model: activeModel }),
        { headers: { ...corsHeaders, "Content-Type": "application/json" } },
      );
    }

    const rawContent = String(body.content ?? "");
    const words = rawContent.trim().split(/\s+/).filter(Boolean);
    const wordCount = words.length;
    const minWords = body.min_words ?? 500;
    const maxWords = body.max_words ?? 700;

    if (!aiCoachEnabled || !geminiApiKey) {
      const lengthAdvice = wordCount < minWords
        ? `শব্দসংখ্যা বর্তমানে ${wordCount}; লক্ষ্যমাত্রা পূরণে আরও অন্তত ${minWords - wordCount} শব্দ যোগ করুন।`
        : wordCount > maxWords
        ? `শব্দসংখ্যা (${wordCount}) নির্ধারিত সীমার (${maxWords}) চেয়ে বেশি; মূল যুক্তিতে সংক্ষিপ্ত করুন।`
        : `শব্দসংখ্যা (${wordCount}) আদর্শ সীমার (${minWords}–${maxWords}) মধ্যে আছে।`;

      return new Response(
        JSON.stringify({
          provider: "heuristic-fallback",
          word_count: wordCount,
          grammar_feedback: [
            "বাক্যগুলো স্পষ্ট ও সংক্ষিপ্ত রাখুন এবং প্যাসিভ ভয়েস কমানোর চেষ্টা করুন।",
          ],
          academic_tone_score: 82,
          tone_feedback:
            "একাডেমিক টোন ভালো আছে; চূড়ান্ত মূল্যায়নের জন্য মানব মেন্টরের কাছে পাণ্ডুলিপি জমা দিন।",
          structure_feedback: [
            lengthAdvice,
            "প্রথম অনুচ্ছেদে প্রেক্ষাপট (Research Site), মাঝের অংশে মূল সমস্যা (Felt-concern) এবং শেষে অংশগ্রহণকারীদের ভূমিকা স্পষ্ট করুন।",
          ],
        }),
        { headers: { ...corsHeaders, "Content-Type": "application/json" } },
      );
    }

    const systemInstruction = `You are SPARC Study Circle's Academic Writing Coach.
Do NOT rewrite the student's paper for them. Give constructive feedback in ${
      body.language === "en" ? "English" : "Bengali"
    } strictly as JSON with keys:
- grammar_feedback (string[])
- academic_tone_score (number 0-100)
- tone_feedback (string)
- structure_feedback (string[])
Chapter ${body.chapter_no ?? 1} Task Prompt: ${body.prompt ?? ""}
Target Word Range: ${minWords}-${maxWords} words (Current: ${wordCount} words).`;

    const geminiResp = await fetch(
      `https://generativelanguage.googleapis.com/v1beta/models/${activeModel}:generateContent?key=${geminiApiKey}`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          contents: [
            {
              role: "user",
              parts: [{ text: `${systemInstruction}\n\nStudent Draft:\n${rawContent}` }],
            },
          ],
          generationConfig: {
            responseMimeType: "application/json",
            temperature: 0.3,
          },
        }),
      },
    );

    if (!geminiResp.ok) {
      throw new Error(`Gemini API HTTP ${geminiResp.status}`);
    }

    const geminiData = await geminiResp.json();
    const rawJsonText =
      geminiData?.candidates?.[0]?.content?.parts?.[0]?.text ?? "{}";
    const parsed = JSON.parse(rawJsonText);

    return new Response(
      JSON.stringify({
        provider: activeModel,
        word_count: wordCount,
        ...parsed,
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
