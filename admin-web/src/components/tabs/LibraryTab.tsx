"use client";

import React, { useCallback, useEffect, useState } from "react";
import { friendlyError, server } from "@/lib/server";
import { parseTemplate, ParsedTemplate, SAMPLE_TEMPLATE_BN } from "@/lib/templateParser";
import { Btn, Card, Empty, ErrorBox, Loading, Pill, SectionTitle, inputCls } from "@/components/ui";
import type { TabProps } from "@/components/SparcAdminPortal";

interface BookRow {
  id: string;
  title: string;
  author: string;
  category: string;
  total_chapters: number;
  is_first_chapter_free: boolean;
  min_members_default: number;
  physical_price_bdt: number;
  pdf_url: string;
  source_attribution?: string;
}

interface ChapterRow {
  id: string;
  book_id: string;
  chapter_no: number;
  title: string;
  type: string;
  total_pages: number;
  total_marks: number;
  pass_marks: number;
  audio_summary_url: string | null;
}

export default function LibraryTab({ me, notify }: TabProps) {
  const canEdit = me.access === "admin";
  const [books, setBooks] = useState<BookRow[]>([]);
  const [chapters, setChapters] = useState<Record<string, ChapterRow[]>>({});
  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setErr(null);
    try {
      const sb = server();
      const { data: bs, error: be } = await sb
        .from("books")
        .select("id,title,author,category,total_chapters,is_first_chapter_free,min_members_default,physical_price_bdt,pdf_url,source_attribution")
        .order("created_at", { ascending: false });
      if (be) throw be;
      const list = (bs ?? []) as BookRow[];
      setBooks(list);
      if (list.length) {
        const { data: chs } = await sb
          .from("chapters")
          .select("id,book_id,chapter_no,title,type,total_pages,total_marks,pass_marks,audio_summary_url")
          .in("book_id", list.map((b) => b.id))
          .order("chapter_no");
        const grouped: Record<string, ChapterRow[]> = {};
        (chs ?? []).forEach((c) => (grouped[c.book_id] ??= []).push(c as ChapterRow));
        setChapters(grouped);
      }
    } catch (e) {
      setErr(friendlyError(e));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  return (
    <div className="space-y-6">
      <SectionTitle onRefresh={load} loading={loading}>
        📚 লাইব্রেরি স্টুডিও ({books.length} টি বই/প্রবন্ধ)
      </SectionTitle>
      <ErrorBox message={err} />

      {canEdit && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <PublishBookForm notify={notify} onPublished={load} />
          <TemplateImporter books={books} notify={notify} onImported={load} />
        </div>
      )}

      {loading && books.length === 0 && <Loading />}
      {!loading && books.length === 0 && <Empty>কোনো বই নেই।</Empty>}
      <div className="space-y-3">
        {books.map((b) => (
          <Card key={b.id} className="space-y-3">
            <div className="flex flex-wrap justify-between items-start gap-2">
              <div>
                <h3 className="font-bold text-base">{b.title}</h3>
                <p className="text-xs text-slate-600">
                  {b.author} • {b.category} • মোট {b.total_chapters} অধ্যায় • হার্ডকপি ৳{Number(b.physical_price_bdt).toFixed(0)} • ন্যূনতম সদস্য {b.min_members_default}
                </p>
                {b.source_attribution && <p className="text-[11px] text-slate-500 mt-0.5">সূত্র/অনুমতি: {b.source_attribution}</p>}
              </div>
              <div className="flex items-center gap-2">
                {b.is_first_chapter_free && <Pill tone="green">১ম অধ্যায় ফ্রি</Pill>}
                {b.pdf_url && (
                  <a href={b.pdf_url} target="_blank" rel="noreferrer" className="text-xs underline text-[#0F5132] font-semibold">
                    📄 PDF খুলুন
                  </a>
                )}
              </div>
            </div>
            <div className="space-y-1.5">
              {(chapters[b.id] ?? []).map((ch) => (
                <div key={ch.id} className="bg-slate-50 border border-slate-200 rounded-lg px-3 py-2 flex flex-wrap justify-between text-xs gap-2">
                  <span>
                    <strong>অধ্যায় {ch.chapter_no}:</strong> {ch.title} ({ch.total_pages} পৃষ্ঠা)
                  </span>
                  <span>
                    {ch.type.toUpperCase()} • মোট {ch.total_marks} (পাস {ch.pass_marks})
                    {ch.audio_summary_url && (
                      <a href={ch.audio_summary_url} target="_blank" rel="noreferrer" className="ml-2 underline text-amber-800">
                        🎧 অডিও
                      </a>
                    )}
                  </span>
                </div>
              ))}
            </div>
          </Card>
        ))}
      </div>
    </div>
  );
}

function PublishBookForm({ notify, onPublished }: { notify: TabProps["notify"]; onPublished: () => void }) {
  const [title, setTitle] = useState("");
  const [author, setAuthor] = useState("");
  const [category, setCategory] = useState("Research & Pedagogy");
  const [price, setPrice] = useState(450);
  const [pages, setPages] = useState(24);
  const [attribution, setAttribution] = useState("");
  const [permitted, setPermitted] = useState(true);
  const [pdfFile, setPdfFile] = useState<File | null>(null);
  const [audioFile, setAudioFile] = useState<File | null>(null);
  const [busy, setBusy] = useState(false);

  const publish = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim()) return;
    if (!permitted) {
      notify("কপিরাইট/ওপেন-অ্যাক্সেস অনুমতি নিশ্চিত করুন।", "err");
      return;
    }
    setBusy(true);
    try {
      const sb = server();
      const ts = Date.now();
      let pdfUrl = "";
      let audioUrl = "";
      if (pdfFile) {
        const path = `pdfs/${ts}_${pdfFile.name.replace(/[^a-zA-Z0-9._-]/g, "_")}`;
        const up = await sb.storage.from("sparc-books").upload(path, pdfFile, { upsert: true });
        if (up.error) throw up.error;
        pdfUrl = sb.storage.from("sparc-books").getPublicUrl(path).data.publicUrl;
      }
      if (audioFile) {
        const path = `summaries/${ts}_${audioFile.name.replace(/[^a-zA-Z0-9._-]/g, "_")}`;
        const up = await sb.storage.from("sparc-audio").upload(path, audioFile, { upsert: true });
        if (up.error) throw up.error;
        audioUrl = sb.storage.from("sparc-audio").getPublicUrl(path).data.publicUrl;
      }
      const { error } = await sb.rpc("publish_new_book_with_media_rpc", {
        p_title: title.trim(),
        p_author: author.trim() || "SPARC Academic Press",
        p_category: category.trim() || "Research & Pedagogy",
        p_price_bdt: price,
        p_total_pages: pages,
        p_pdf_url: pdfUrl,
        p_audio_url: audioUrl || null,
        p_source_attribution: attribution.trim(),
        p_permission_declared: permitted,
      });
      if (error) throw error;
      setTitle("");
      setAuthor("");
      setAttribution("");
      setPdfFile(null);
      setAudioFile(null);
      notify("নতুন বই/PDF সফলভাবে সার্ভারে প্রকাশিত হয়েছে!");
      onPublished();
    } catch (er) {
      notify(friendlyError(er), "err");
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card>
      <form onSubmit={publish} className="space-y-3">
        <h3 className="text-sm font-bold">☁️ নতুন বই বা অনুমতিপ্রাপ্ত PDF ও অডিও আপলোড</h3>
        <input value={title} onChange={(e) => setTitle(e.target.value)} required placeholder="বই / প্রবন্ধের শিরোনাম *" className={inputCls} />
        <div className="grid grid-cols-2 gap-2">
          <input value={author} onChange={(e) => setAuthor(e.target.value)} placeholder="লেখকের নাম" className={inputCls} />
          <input value={category} onChange={(e) => setCategory(e.target.value)} placeholder="ক্যাটাগরি" className={inputCls} />
        </div>
        <div className="grid grid-cols-2 gap-2">
          <div>
            <label className="text-[11px] font-semibold">হার্ডকপি মূল্য (৳)</label>
            <input type="number" min={0} value={price} onChange={(e) => setPrice(Number(e.target.value) || 0)} className={inputCls} />
          </div>
          <div>
            <label className="text-[11px] font-semibold">১ম অধ্যায়ের পৃষ্ঠা</label>
            <input type="number" min={1} value={pages} onChange={(e) => setPages(Number(e.target.value) || 1)} className={inputCls} />
          </div>
        </div>
        <input value={attribution} onChange={(e) => setAttribution(e.target.value)} placeholder="সূত্র / লাইসেন্স / লেখকের অনুমতি নোট (ঐচ্ছিক)" className={inputCls} />
        <div className="grid grid-cols-1 md:grid-cols-2 gap-2 text-xs">
          <label className="border border-dashed border-emerald-700 bg-emerald-50/40 rounded-lg p-2 cursor-pointer">
            📄 PDF ফাইল
            <input type="file" accept="application/pdf" onChange={(e) => setPdfFile(e.target.files?.[0] ?? null)} className="mt-1 block w-full text-[11px]" />
          </label>
          <label className="border border-dashed border-amber-700 bg-amber-50/40 rounded-lg p-2 cursor-pointer">
            🎧 বাংলা অডিও সামারি
            <input type="file" accept="audio/*" onChange={(e) => setAudioFile(e.target.files?.[0] ?? null)} className="mt-1 block w-full text-[11px]" />
          </label>
        </div>
        <label className="flex items-center gap-2 text-xs">
          <input type="checkbox" checked={permitted} onChange={(e) => setPermitted(e.target.checked)} />
          কপিরাইট / ওপেন-অ্যাক্সেস অনুমতি নিশ্চিত করা হয়েছে
        </label>
        <Btn type="submit" busy={busy} className="w-full py-2 text-sm">
          ☁️ সার্ভারে বই প্রকাশ করুন
        </Btn>
      </form>
    </Card>
  );
}

function TemplateImporter({ books, notify, onImported }: { books: BookRow[]; notify: TabProps["notify"]; onImported: () => void }) {
  const [bookId, setBookId] = useState(books[0]?.id ?? "");
  const [raw, setRaw] = useState("");
  const [replace, setReplace] = useState(false);
  const [preview, setPreview] = useState<ParsedTemplate | null>(null);
  const [parseErr, setParseErr] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!bookId && books[0]) setBookId(books[0].id);
  }, [books, bookId]);

  const check = (txt: string) => {
    setRaw(txt);
    if (!txt.trim()) {
      setPreview(null);
      setParseErr(null);
      return;
    }
    try {
      setPreview(parseTemplate(txt));
      setParseErr(null);
    } catch (e) {
      setPreview(null);
      setParseErr(friendlyError(e));
    }
  };

  const onFile = async (f: File | null) => {
    if (!f) return;
    check(await f.text());
  };

  const submit = async () => {
    if (!bookId) {
      notify("একটি বই নির্বাচন করুন।", "err");
      return;
    }
    setBusy(true);
    try {
      const p = parseTemplate(raw);
      const { error } = await server().rpc("import_exercise_template_rpc", {
        p_book_id: bookId,
        p_chapter_no: p.chapterNo,
        p_title: p.chapterTitle || `অধ্যায় ${p.chapterNo}`,
        p_type: p.chapterType,
        p_total_pages: p.totalPages,
        p_flash_notes: p.flashNotes,
        p_learning_outcomes: p.learningOutcomes,
        p_writing_prompt: p.writingPrompt,
        p_min_words: p.minWords,
        p_max_words: p.maxWords,
        p_questions: p.questions,
        p_replace_questions: replace,
      });
      if (error) throw error;
      notify(`অধ্যায় ${p.chapterNo}: ${p.questions.length} টি প্রশ্ন ও ফ্ল্যাশ নোট ইমপোর্ট হয়েছে!`);
      setRaw("");
      setPreview(null);
      onImported();
    } catch (e) {
      notify(friendlyError(e), "err");
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card className="space-y-3">
      <div className="flex justify-between items-center">
        <h3 className="text-sm font-bold">📝 নির্ধারিত অনুশীলনী টেমপ্লেট ইমপোর্টার</h3>
        <button type="button" onClick={() => check(SAMPLE_TEMPLATE_BN)} className="text-xs underline text-[#0F5132] font-semibold">
          নমুনা টেমপ্লেট বসান
        </button>
      </div>
      <select value={bookId} onChange={(e) => setBookId(e.target.value)} className={inputCls}>
        {books.map((b) => (
          <option key={b.id} value={b.id}>
            {b.title}
          </option>
        ))}
      </select>
      <input type="file" accept=".txt,.md,.json,.csv" onChange={(e) => onFile(e.target.files?.[0] ?? null)} className="text-xs" />
      <textarea
        value={raw}
        onChange={(e) => check(e.target.value)}
        rows={7}
        placeholder=".txt / .md / JSON / CSV টেমপ্লেট এখানে পেস্ট করুন…"
        className={`${inputCls} font-mono text-xs`}
      />
      {parseErr && <p className="text-xs text-red-700">{parseErr}</p>}
      {preview && (
        <p className="text-xs bg-emerald-50 border border-emerald-200 rounded-lg px-3 py-2 text-emerald-950">
          প্রস্তুত: অধ্যায় {preview.chapterNo} ({preview.chapterType.toUpperCase()}) • {preview.questions.filter((q) => q.is_auto_scored).length} MCQ +{" "}
          {preview.questions.filter((q) => !q.is_auto_scored).length} OPEN প্রশ্ন • {preview.flashNotes.length} ফ্ল্যাশ নোট
        </p>
      )}
      <label className="flex items-center gap-2 text-xs">
        <input type="checkbox" checked={replace} onChange={(e) => setReplace(e.target.checked)} />
        এই অধ্যায়ের আগের প্রশ্ন মুছে নতুন প্রশ্ন বসান
      </label>
      <Btn onClick={submit} busy={busy} disabled={!preview} className="w-full py-2 text-sm">
        📥 সার্ভারে অনুশীলনী ইমপোর্ট করুন
      </Btn>
    </Card>
  );
}
