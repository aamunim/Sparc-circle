// Supabase Edge Function: generate-certificate
// Issues Study-Circle Completion Certificates & Shareable Social Progress Card metadata

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

    const { user_id, circle_id, book_id } = await req.json();

    const { data: book } = await supabase
      .from("books")
      .select("id, title, total_chapters")
      .eq("id", book_id)
      .single();

    if (!book) throw new Error("Book not found");

    const { count: verifiedChapters } = await supabase
      .from("chapter_progress")
      .select("id", { count: "exact", head: true })
      .eq("circle_id", circle_id)
      .eq("user_id", user_id)
      .eq("status", "Learning Verified");

    if ((verifiedChapters ?? 0) < book.total_chapters) {
      throw new Error(
        `সার্টিফিকেট অর্জনের জন্য সবকটি (${book.total_chapters}) অধ্যায়ে 'Learning Verified' সম্পন্ন হতে হবে।`,
      );
    }

    const { data: finalProposal } = await supabase
      .from("writing_submissions")
      .select("id, supervisor_approved")
      .eq("user_id", user_id)
      .eq("book_id", book_id)
      .eq("chapter_no", book.total_chapters)
      .single();

    if (!finalProposal?.supervisor_approved) {
      throw new Error("চূড়ান্ত রিসার্চ প্রপোজালটি সুপারভাইজার কর্তৃক অনুমোদিত হতে হবে।");
    }

    const year = new Date().getFullYear();
    const certCode = `SPARC-${year}-${crypto.randomUUID().slice(0, 8).toUpperCase()}`;

    const { data: cert, error } = await supabase
      .from("certificates")
      .insert({
        certificate_code: certCode,
        user_id,
        circle_id,
        book_id,
        certificate_type: "study_circle_completion",
        title: `Study-Circle Completion Certificate — ${book.title}`,
        pdf_url: `https://verify.sparc.org.bd/certificates/${certCode}.pdf`,
        share_card_url: `https://verify.sparc.org.bd/cards/${certCode}.png`,
      })
      .select()
      .single();

    if (error) throw error;

    return new Response(
      JSON.stringify({
        certificate: cert,
        linkedin_share_url:
          `https://www.linkedin.com/sharing/share-offsite/?url=${encodeURIComponent(`https://verify.sparc.org.bd/c/${certCode}`)}`,
        facebook_share_url:
          `https://www.facebook.com/sharer/sharer.php?u=${encodeURIComponent(`https://verify.sparc.org.bd/c/${certCode}`)}`,
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
