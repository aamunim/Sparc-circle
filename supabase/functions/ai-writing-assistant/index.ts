// Supabase Edge Function: ai-writing-assistant
// Supports Gemini API key from EITHER In-App Settings (request payload/header) OR Supabase Edge Secrets

import { serve } from "https://deno.land/std@0.224.0/http/server.ts";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type, x-gemini-api-key",
};

interface WritingAssistantRequest {
  chapter_no: number;
  prompt: string;
  content: string;
  language?: "bn" | "en";
  min_words?: number;
  max_words?: number;
  gemini_api_key?: string;
}

serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const body: WritingAssistantRequest = await req.json();
    const words = body.content.trim().split(/\s+/).filter(Boolean);
    const wordCount = words.length;
    const minWords = body.min_words ?? 500;
    const maxWords = body.max_words ?? 700;

    const geminiApiKey =
      body.gemini_api_key?.trim() ||
      req.headers.get("x-gemini-api-key")?.trim() ||
      Deno.env.get("GEMINI_API_KEY")?.trim();

    if (!geminiApiKey) {
      const lengthAdvice = wordCount < minWords
        ? `শব্দসংখ্যা বর্তমানে ${wordCount}; লক্ষ্যমাত্রা পূরণে আরও অন্তত ${minWords - wordCount} শব্দ যোগ করুন।`
        : wordCount > maxWords
        ? `শব্দসংখ্যা (${wordCount}) নির্ধারিত সীমার (${maxWords}) চেয়ে বেশি; মূল যুক্তিতে সংক্ষিপ্ত করুন।`
        : `শব্দসংখ্যা (${wordCount}) আদর্শ সীমার (${minWords}–${maxWords}) মধ্যে আছে।`;

      return new Response(
        JSON.stringify({
          provider: "heuristic-fallback",
          word_count: wordCount,
          grammar_feedback: [
            "বাক্যগুলো স্পষ্ট ও সংক্ষিপ্ত রাখুন এবং প্যাসিভ ভয়েস কমানোর চেষ্টা করুন।",
          ],
          academic_tone_score: 82,
          tone_feedback:
            "একাডেমিক টোন ভালো আছে; অ্যাপের Settings → API Keys থেকে Gemini API Key যুক্ত করলে লাইভ AI বিশ্লেষণ পাবেন।",
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
Chapter ${body.chapter_no} Task Prompt: ${body.prompt}
Target Word Range: ${minWords}-${maxWords} words (Current: ${wordCount} words).`;

    const geminiResp = await fetch(
      `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=${geminiApiKey}`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          contents: [
            {
              role: "user",
              parts: [{ text: `${systemInstruction}\n\nStudent Draft:\n${body.content}` }],
            },
          ],
          generationConfig: {
            responseMimeType: "application/json",
            temperature: 0.3,
          },
        }),
      },
    );

    const geminiJson = await geminiResp.json();
    const rawText = geminiJson?.candidates?.[0]?.content?.parts?.[0]?.text ?? "{}";
    const parsed = JSON.parse(rawText);

    return new Response(
      JSON.stringify({
        provider: "gemini-2.5-flash",
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
