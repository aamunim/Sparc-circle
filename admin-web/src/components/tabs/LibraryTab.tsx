"use client";

import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { friendlyError, server } from "@/lib/server";
import { AI_GENERATOR_PROMPT_BN, parseTemplate, ParsedTemplate, SAMPLE_TEMPLATE_BN } from "@/lib/templateParser";
import {
  buildWordTimestampsFromParagraphAnchors,
  ChapterContentMode,
  findActiveWordIndex,
  formatTimestampMs,
  parseSubtitleToWordTimestamps,
  splitTextIntoParagraphs,
  WordCue,
} from "@/lib/wordSync";
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
  audio_duration_seconds?: number | null;
  content_mode?: ChapterContentMode;
  full_text_content?: string;
  word_timestamps?: WordCue[];
}

async function readAudioDurationMs(file: File): Promise<number> {
  return new Promise((resolve) => {
    const url = URL.createObjectURL(file);
    const audio = document.createElement("audio");
    audio.preload = "metadata";
    audio.onloadedmetadata = () => {
      const ms = Number.isFinite(audio.duration) && audio.duration > 0 ? Math.round(audio.duration * 1000) : 180_000;
      URL.revokeObjectURL(url);
      resolve(ms);
    };
    audio.onerror = () => {
      URL.revokeObjectURL(url);
      resolve(180_000);
    };
    audio.src = url;
  });
}

export default function LibraryTab({ me, notify }: TabProps) {
  const canEdit = me.access === "admin" || me.access === "sparc_rep" || me.access === "supervisor";
  const isAdmin = me.access === "admin";
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
          .select("id,book_id,chapter_no,title,type,total_pages,total_marks,pass_marks,audio_summary_url,audio_duration_seconds,content_mode,full_text_content,word_timestamps")
          .in("book_id", list.map((b) => b.id))
          .order("chapter_no");
        const grouped: Record<string, ChapterRow[]> = {};
        (chs ?? []).forEach((c) => (grouped[c.book_id] ??= []).push(c as ChapterRow));
        setChapters(grouped);
      } else {
        setChapters({});
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
        📚 লাইব্রেরি ও Read-Along টেক্সট স্টুডিও ({books.length} টি বই/প্রবন্ধ)
      </SectionTitle>
      <ErrorBox message={err} />

      {isAdmin && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <PublishBookForm notify={notify} onPublished={load} />
          <TemplateImporter books={books} notify={notify} onImported={load} />
        </div>
      )}

      {canEdit && books.length > 0 && (
        <ReadAlongChapterStudio books={books} chaptersByBook={chapters} notify={notify} onSaved={load} />
      )}

      {loading && books.length === 0 && <Loading />}
      {!loading && books.length === 0 && <Empty>কোনো বই নেই। উপরের ফর্ম থেকে সরাসরি টেক্সট বা PDF যুক্ত করুন।</Empty>}

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
              {(chapters[b.id] ?? []).map((ch) => {
                const mode = ch.content_mode ?? "pdf";
                const wordCount = (ch.full_text_content ?? "").trim().split(/\s+/).filter(Boolean).length;
                const cueCount = Array.isArray(ch.word_timestamps) ? ch.word_timestamps.length : 0;
                return (
                  <div key={ch.id} className="bg-slate-50 border border-slate-200 rounded-lg px-3 py-2 flex flex-wrap justify-between items-center text-xs gap-2">
                    <div className="flex flex-wrap items-center gap-2">
                      <span>
                        <strong>অধ্যায় {ch.chapter_no}:</strong> {ch.title} ({ch.total_pages} পৃষ্ঠা)
                      </span>
                      {mode === "text" && <Pill tone="green">📝 Read-Along টেক্সট ({wordCount} শব্দ)</Pill>}
                      {mode === "hybrid" && <Pill tone="blue">📚 হাইব্রিড ({wordCount} শব্দ + PDF)</Pill>}
                      {cueCount > 0 && <Pill tone="amber">⏱️ {cueCount} শব্দের টাইমস্ট্যাম্প</Pill>}
                    </div>
                    <span>
                      {ch.type.toUpperCase()} • মোট {ch.total_marks} (পাস {ch.pass_marks})
                      {ch.audio_summary_url && (
                        <a href={ch.audio_summary_url} target="_blank" rel="noreferrer" className="ml-2 underline text-amber-800 font-semibold">
                          🎧 অডিও
                        </a>
                      )}
                    </span>
                  </div>
                );
              })}
            </div>
          </Card>
        ))}
      </div>
    </div>
  );
}

function PublishBookForm({ notify, onPublished }: { notify: TabProps["notify"]; onPublished: () => void }) {
  const [contentMode, setContentMode] = useState<ChapterContentMode>("text");
  const [title, setTitle] = useState("");
  const [chapterTitle, setChapterTitle] = useState("অধ্যায় ১: সূচনা ও মূল ধারণা");
  const [author, setAuthor] = useState("");
  const [category, setCategory] = useState("Research & Pedagogy");
  const [price, setPrice] = useState(0);
  const [pages, setPages] = useState(12);
  const [attribution, setAttribution] = useState("");
  const [permitted, setPermitted] = useState(true);
  const [chapterText, setChapterText] = useState("");
  const [pdfFile, setPdfFile] = useState<File | null>(null);
  const [audioFile, setAudioFile] = useState<File | null>(null);
  const [busy, setBusy] = useState(false);

  const wordCount = useMemo(() => chapterText.trim().split(/\s+/).filter(Boolean).length, [chapterText]);

  const onTextFileSelect = async (f: File | null) => {
    if (!f) return;
    setChapterText(await f.text());
  };

  const publish = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim()) return;
    if (!permitted) {
      notify("কপিরাইট/ওপেন-অ্যাক্সেস অনুমতি নিশ্চিত করুন।", "err");
      return;
    }
    if ((contentMode === "text" || contentMode === "hybrid") && wordCount < 5) {
      notify("১ম অধ্যায়ের টেক্সট লিখুন বা .txt/.md ফাইল দিন (অথবা 'শুধু PDF' মোড বেছে নিন)।", "err");
      return;
    }
    if ((contentMode === "pdf" || contentMode === "hybrid") && !pdfFile) {
      notify("PDF ফাইল নির্বাচন করুন (অথবা 'সরাসরি টেক্সট' মোড বেছে নিন)।", "err");
      return;
    }

    setBusy(true);
    try {
      const sb = server();
      const ts = Date.now();
      let pdfUrl = "";
      let audioUrl = "";
      let audioDurationMs = Math.max(wordCount * 420, 30_000);

      if (pdfFile) {
        const path = `pdfs/${ts}_${pdfFile.name.replace(/[^a-zA-Z0-9._-]/g, "_")}`;
        const up = await sb.storage.from("sparc-books").upload(path, pdfFile, { upsert: true });
        if (up.error) throw up.error;
        pdfUrl = sb.storage.from("sparc-books").getPublicUrl(path).data.publicUrl;
      }
      if (audioFile) {
        audioDurationMs = await readAudioDurationMs(audioFile);
        const path = `summaries/${ts}_${audioFile.name.replace(/[^a-zA-Z0-9._-]/g, "_")}`;
        const up = await sb.storage.from("sparc-audio").upload(path, audioFile, { upsert: true });
        if (up.error) throw up.error;
        audioUrl = sb.storage.from("sparc-audio").getPublicUrl(path).data.publicUrl;
      }

      const autoCues =
        chapterText.trim().length > 0
          ? buildWordTimestampsFromParagraphAnchors(chapterText, {}, audioDurationMs)
          : [];

      const { error } = await sb.rpc("publish_book_with_text_or_pdf_rpc", {
        p_title: title.trim(),
        p_author: author.trim() || "SPARC Academic Press",
        p_category: category.trim() || "Research & Pedagogy",
        p_price_bdt: price,
        p_chapter_title: chapterTitle.trim() || "অধ্যায় ১: সূচনা ও মূল ধারণা",
        p_total_pages: wordCount > 0 ? Math.max(Math.ceil(wordCount / 250), 1) : pages,
        p_content_mode: contentMode,
        p_full_text_content: chapterText.trim(),
        p_word_timestamps: autoCues,
        p_pdf_url: pdfUrl,
        p_audio_url: audioUrl || null,
        p_audio_duration_seconds: Math.max(Math.round(audioDurationMs / 1000), 10),
        p_source_attribution: attribution.trim(),
        p_permission_declared: permitted,
      });
      if (error) throw error;

      setTitle("");
      setAuthor("");
      setAttribution("");
      setChapterText("");
      setPdfFile(null);
      setAudioFile(null);
      notify(
        autoCues.length > 0
          ? `✓ বই প্রকাশিত হয়েছে (${autoCues.length}টি শব্দের অটো টাইমস্ট্যাম্পসহ)!`
          : "✓ নতুন বই সফলভাবে সার্ভারে প্রকাশিত হয়েছে!"
      );
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
        <h3 className="text-sm font-bold">☁️ নতুন বই প্রকাশ (সরাসরি টেক্সট / PDF / হাইব্রিড)</h3>

        <div className="grid grid-cols-3 gap-1 bg-slate-100 p-1 rounded-xl text-xs font-semibold">
          {(
            [
              ["text", "📝 সরাসরি টেক্সট"],
              ["pdf", "📄 শুধু PDF"],
              ["hybrid", "📚 টেক্সট + PDF"],
            ] as [ChapterContentMode, string][]
          ).map(([m, label]) => (
            <button
              key={m}
              type="button"
              onClick={() => setContentMode(m)}
              className={`py-1.5 rounded-lg transition ${
                contentMode === m ? "bg-white text-[#0F5132] shadow-sm font-bold" : "text-slate-600"
              }`}
            >
              {label}
            </button>
          ))}
        </div>

        <input value={title} onChange={(e) => setTitle(e.target.value)} required placeholder="বই / প্রবন্ধের শিরোনাম *" className={inputCls} />
        <div className="grid grid-cols-2 gap-2">
          <input value={author} onChange={(e) => setAuthor(e.target.value)} placeholder="লেখকের নাম" className={inputCls} />
          <input value={category} onChange={(e) => setCategory(e.target.value)} placeholder="ক্যাটাগরি" className={inputCls} />
        </div>
        <div className="grid grid-cols-2 gap-2">
          <div>
            <label className="text-[11px] font-semibold">হার্ডকপি মূল্য (৳, ফ্রি হলে ০)</label>
            <input type="number" min={0} value={price} onChange={(e) => setPrice(Number(e.target.value) || 0)} className={inputCls} />
          </div>
          <div>
            <label className="text-[11px] font-semibold">১ম অধ্যায়ের শিরোনাম</label>
            <input value={chapterTitle} onChange={(e) => setChapterTitle(e.target.value)} className={inputCls} />
          </div>
        </div>

        {(contentMode === "text" || contentMode === "hybrid") && (
          <div className="space-y-1.5 bg-emerald-50/50 border border-emerald-200 rounded-xl p-2.5">
            <div className="flex flex-wrap justify-between items-center text-xs">
              <span className="font-semibold text-[#0F5132]">
                📝 ১ম অধ্যায়ের পাঠ্য ({wordCount} শব্দ • ~{Math.max(1, Math.ceil(wordCount / 130))} মিনিট পাঠ)
              </span>
              <label className="underline cursor-pointer text-[#0F5132] font-semibold">
                📂 .txt / .md ফাইল নিন
                <input type="file" accept=".txt,.md,text/plain" onChange={(e) => onTextFileSelect(e.target.files?.[0] ?? null)} className="hidden" />
              </label>
            </div>
            <textarea
              rows={5}
              value={chapterText}
              onChange={(e) => setChapterText(e.target.value)}
              placeholder="এখানে অধ্যায়ের মূল বাংলা বা ইংরেজি টেক্সট পেস্ট করুন। অনুচ্ছেদগুলো ফাঁকা লাইন দিয়ে আলাদা রাখুন..."
              className={`${inputCls} text-xs leading-relaxed`}
            />
          </div>
        )}

        <div className="grid grid-cols-1 md:grid-cols-2 gap-2 text-xs">
          {(contentMode === "pdf" || contentMode === "hybrid") && (
            <label className="border border-dashed border-emerald-700 bg-emerald-50/40 rounded-lg p-2 cursor-pointer">
              📄 PDF ফাইল {contentMode === "pdf" ? "*" : ""}
              <input type="file" accept="application/pdf" onChange={(e) => setPdfFile(e.target.files?.[0] ?? null)} className="mt-1 block w-full text-[11px]" />
            </label>
          )}
          <label className={`border border-dashed border-amber-700 bg-amber-50/40 rounded-lg p-2 cursor-pointer ${contentMode === "text" ? "md:col-span-2" : ""}`}>
            🎧 অডিও ফাইল (MP3/WAV — শব্দে-শব্দে টাইমস্ট্যাম্প সিঙ্কের জন্য, ঐচ্ছিক)
            <input type="file" accept="audio/*" onChange={(e) => setAudioFile(e.target.files?.[0] ?? null)} className="mt-1 block w-full text-[11px]" />
          </label>
        </div>

        {contentMode === "pdf" && (
          <div>
            <label className="text-[11px] font-semibold">১ম অধ্যায়ের পৃষ্ঠা সংখ্যা</label>
            <input type="number" min={1} value={pages} onChange={(e) => setPages(Number(e.target.value) || 1)} className={inputCls} />
          </div>
        )}

        <input value={attribution} onChange={(e) => setAttribution(e.target.value)} placeholder="সূত্র / লাইসেন্স / লেখকের অনুমতি নোট (ঐচ্ছিক)" className={inputCls} />
        <label className="flex items-center gap-2 text-xs">
          <input type="checkbox" checked={permitted} onChange={(e) => setPermitted(e.target.checked)} />
          কপিরাইট / ওপেন-অ্যাক্সেস অনুমতি নিশ্চিত করা হয়েছে
        </label>
        <Btn type="submit" busy={busy} className="w-full py-2 text-sm">
          ☁️ সার্ভারে বই ও টেক্সট প্রকাশ করুন
        </Btn>
      </form>
    </Card>
  );
}

function ReadAlongChapterStudio({
  books,
  chaptersByBook,
  notify,
  onSaved,
}: {
  books: BookRow[];
  chaptersByBook: Record<string, ChapterRow[]>;
  notify: TabProps["notify"];
  onSaved: () => void;
}) {
  const [bookId, setBookId] = useState(books[0]?.id ?? "");
  const [chapterNo, setChapterNo] = useState(1);
  const [chapterTitle, setChapterTitle] = useState("");
  const [contentMode, setContentMode] = useState<ChapterContentMode>("text");
  const [text, setText] = useState("");
  const [audioUrl, setAudioUrl] = useState("");
  const [audioFile, setAudioFile] = useState<File | null>(null);
  const [localPreviewUrl, setLocalPreviewUrl] = useState<string | null>(null);
  const [durationMs, setDurationMs] = useState(180_000);
  const [currentMs, setCurrentMs] = useState(0);
  const [anchorsMs, setAnchorsMs] = useState<Record<number, number>>({});
  const [importedCues, setImportedCues] = useState<WordCue[] | null>(null);
  const [busy, setBusy] = useState(false);

  const audioRef = useRef<HTMLAudioElement | null>(null);

  useEffect(() => {
    if (!bookId && books[0]) setBookId(books[0].id);
  }, [books, bookId]);

  // Load existing chapter data when bookId or chapterNo changes
  useEffect(() => {
    const existing = (chaptersByBook[bookId] ?? []).find((c) => c.chapter_no === chapterNo);
    if (existing) {
      setChapterTitle(existing.title);
      setContentMode(existing.content_mode ?? (existing.full_text_content ? "text" : "pdf"));
      setText(existing.full_text_content ?? "");
      setAudioUrl(existing.audio_summary_url ?? "");
      setDurationMs(Math.max((existing.audio_duration_seconds ?? 180) * 1000, 5000));
      setImportedCues(Array.isArray(existing.word_timestamps) && existing.word_timestamps.length > 0 ? existing.word_timestamps : null);
      setAnchorsMs({});
    } else {
      setChapterTitle(`অধ্যায় ${chapterNo}`);
      setContentMode("text");
      setText("");
      setAudioUrl("");
      setImportedCues(null);
      setAnchorsMs({});
    }
  }, [bookId, chapterNo, chaptersByBook]);

  // Poll HTML5 audio currentTime at 60ms while playing for smooth karaoke highlight
  useEffect(() => {
    const timer = window.setInterval(() => {
      const el = audioRef.current;
      if (el && !el.paused) {
        setCurrentMs(Math.round(el.currentTime * 1000));
      }
    }, 60);
    return () => window.clearInterval(timer);
  }, []);

  const paragraphs = useMemo(() => splitTextIntoParagraphs(text), [text]);

  const cues: WordCue[] = useMemo(() => {
    if (importedCues && importedCues.length > 0) return importedCues;
    return buildWordTimestampsFromParagraphAnchors(text, anchorsMs, durationMs);
  }, [importedCues, text, anchorsMs, durationMs]);

  const activeWordIdx = useMemo(() => findActiveWordIndex(cues, currentMs), [cues, currentMs]);

  const cuesByParagraph = useMemo(() => {
    const map: Record<number, { cue: WordCue; globalIdx: number }[]> = {};
    cues.forEach((cue, globalIdx) => {
      (map[cue.p] ??= []).push({ cue, globalIdx });
    });
    return map;
  }, [cues]);

  const onSelectAudioFile = async (f: File | null) => {
    setAudioFile(f);
    if (localPreviewUrl) URL.revokeObjectURL(localPreviewUrl);
    if (!f) {
      setLocalPreviewUrl(null);
      return;
    }
    const objUrl = URL.createObjectURL(f);
    setLocalPreviewUrl(objUrl);
    const ms = await readAudioDurationMs(f);
    setDurationMs(ms);
    setImportedCues(null);
  };

  const onImportSubtitleFile = async (f: File | null) => {
    if (!f) return;
    try {
      const raw = await f.text();
      const parsed = parseSubtitleToWordTimestamps(raw);
      setText(parsed.text);
      setImportedCues(parsed.cues);
      const lastEnd = parsed.cues[parsed.cues.length - 1]?.e ?? durationMs;
      setDurationMs(Math.max(durationMs, lastEnd));
      notify(`✓ সাবটাইটেল থেকে ${parsed.cues.length}টি শব্দের নিখুঁত টাইমস্ট্যাম্প লোড হয়েছে!`);
    } catch (er) {
      notify(friendlyError(er), "err");
    }
  };

  const markParagraphHere = (pIdx: number) => {
    const el = audioRef.current;
    const ms = el ? Math.round(el.currentTime * 1000) : currentMs;
    setImportedCues(null);
    setAnchorsMs((prev) => ({ ...prev, [pIdx]: ms }));
  };

  const seekToMs = (ms: number) => {
    setCurrentMs(ms);
    if (audioRef.current) {
      audioRef.current.currentTime = ms / 1000;
      audioRef.current.play().catch(() => null);
    }
  };

  const saveChapterSync = async () => {
    if (!bookId) return;
    if (!text.trim() && contentMode !== "pdf") {
      notify("অধ্যায়ের টেক্সট লিখুন বা .txt/.md ফাইল আপলোড করুন।", "err");
      return;
    }
    setBusy(true);
    try {
      const sb = server();
      let finalAudioUrl = audioUrl;
      if (audioFile) {
        const ts = Date.now();
        const path = `summaries/${ts}_${audioFile.name.replace(/[^a-zA-Z0-9._-]/g, "_")}`;
        const up = await sb.storage.from("sparc-audio").upload(path, audioFile, { upsert: true });
        if (up.error) throw up.error;
        finalAudioUrl = sb.storage.from("sparc-audio").getPublicUrl(path).data.publicUrl;
      }

      const { error } = await sb.rpc("upsert_chapter_text_and_sync_rpc", {
        p_book_id: bookId,
        p_chapter_no: chapterNo,
        p_title: chapterTitle.trim() || `অধ্যায় ${chapterNo}`,
        p_content_mode: contentMode,
        p_full_text_content: text.trim(),
        p_audio_url: finalAudioUrl || null,
        p_audio_duration_seconds: Math.max(Math.round(durationMs / 1000), 5),
        p_word_timestamps: cues,
      });
      if (error) throw error;
      setAudioFile(null);
      notify(`✓ অধ্যায় ${chapterNo}-এর টেক্সট ও ${cues.length}টি শব্দের টাইমস্ট্যাম্প সার্ভারে সংরক্ষিত হয়েছে!`);
      onSaved();
    } catch (er) {
      notify(friendlyError(er), "err");
    } finally {
      setBusy(false);
    }
  };

  const activeSrc = localPreviewUrl || audioUrl;

  return (
    <Card className="space-y-4 border-2 border-emerald-800/20">
      <div className="flex flex-wrap justify-between items-center gap-2">
        <div>
          <h3 className="text-base font-bold text-[#0F5132]">
            🎧 Read-Along টেক্সট ও শব্দে-শব্দে টাইমস্ট্যাম্প স্টুডিও (Word-by-Word Sync)
          </h3>
          <p className="text-xs text-slate-600">
            যেকোনো অধ্যায়ে সরাসরি টেক্সট ও অডিও যুক্ত করুন, অনুচ্ছেদে ১ ক্লিকে টাইমস্ট্যাম্প মেলান অথবা .srt/.vtt ফাইল ইমপোর্ট করুন।
          </p>
        </div>
        <div className="flex items-center gap-2 text-xs">
          <label className="px-2.5 py-1.5 rounded-lg bg-slate-100 hover:bg-slate-200 cursor-pointer font-semibold">
            📂 .txt / .md লোড
            <input
              type="file"
              accept=".txt,.md,text/plain"
              onChange={async (e) => {
                const f = e.target.files?.[0];
                if (f) {
                  setImportedCues(null);
                  setText(await f.text());
                }
              }}
              className="hidden"
            />
          </label>
          <label className="px-2.5 py-1.5 rounded-lg bg-amber-100 hover:bg-amber-200 text-amber-950 cursor-pointer font-semibold">
            ⏱️ .srt / .vtt / JSON ইমপোর্ট
            <input
              type="file"
              accept=".srt,.vtt,.json,text/plain"
              onChange={(e) => onImportSubtitleFile(e.target.files?.[0] ?? null)}
              className="hidden"
            />
          </label>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-4 gap-2">
        <select value={bookId} onChange={(e) => setBookId(e.target.value)} className={`${inputCls} md:col-span-2`}>
          {books.map((b) => (
            <option key={b.id} value={b.id}>
              {b.title}
            </option>
          ))}
        </select>
        <div className="flex items-center gap-1">
          <label className="text-xs font-semibold whitespace-nowrap">অধ্যায়:</label>
          <input
            type="number"
            min={1}
            value={chapterNo}
            onChange={(e) => setChapterNo(Math.max(1, Number(e.target.value) || 1))}
            className={inputCls}
          />
        </div>
        <select value={contentMode} onChange={(e) => setContentMode(e.target.value as ChapterContentMode)} className={inputCls}>
          <option value="text">📝 সরাসরি টেক্সট (Read-Along)</option>
          <option value="hybrid">📚 হাইব্রিড (টেক্সট + PDF)</option>
          <option value="pdf">📄 শুধু PDF</option>
        </select>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-2">
        <input
          value={chapterTitle}
          onChange={(e) => setChapterTitle(e.target.value)}
          placeholder="অধ্যায়ের শিরোনাম"
          className={inputCls}
        />
        <label className="border border-dashed border-amber-700 bg-amber-50/40 rounded-lg px-3 py-1.5 text-xs cursor-pointer flex items-center justify-between">
          <span>🎧 নতুন অডিও MP3/WAV নির্বাচন (ঐচ্ছিক)</span>
          <input type="file" accept="audio/*" onChange={(e) => onSelectAudioFile(e.target.files?.[0] ?? null)} className="text-[11px] max-w-[190px]" />
        </label>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        <div className="space-y-1.5">
          <label className="text-xs font-semibold text-slate-700">
            ✍️ অধ্যায়ের মূল পাঠ্য ({cues.length}টি শব্দ • প্রতিটি নতুন লাইনে অনুচ্ছেদ ভাগ হবে)
          </label>
          <textarea
            rows={10}
            value={text}
            onChange={(e) => {
              setImportedCues(null);
              setText(e.target.value);
            }}
            placeholder="এখানে অধ্যায়ের সম্পূর্ণ টেক্সট লিখুন বা পেস্ট করুন..."
            className={`${inputCls} text-xs leading-relaxed`}
          />
        </div>

        <div className="space-y-2 bg-slate-50 border border-slate-200 rounded-xl p-3 flex flex-col">
          <div className="flex flex-wrap justify-between items-center gap-2">
            <span className="text-xs font-bold text-[#0F5132]">
              ▶️ লাইভ শব্দে-শব্দে প্রিভিউ • চলমান সময়: {formatTimestampMs(currentMs)}
            </span>
            {Object.keys(anchorsMs).length > 0 && (
              <button
                type="button"
                onClick={() => setAnchorsMs({})}
                className="text-[11px] underline text-slate-600"
              >
                অটো টাইমলাইনে রিসেট করুন
              </button>
            )}
          </div>

          {activeSrc ? (
            <audio
              ref={audioRef}
              controls
              src={activeSrc}
              onLoadedMetadata={(e) => {
                const dur = e.currentTarget.duration;
                if (Number.isFinite(dur) && dur > 0) setDurationMs(Math.round(dur * 1000));
              }}
              onTimeUpdate={(e) => setCurrentMs(Math.round(e.currentTarget.currentTime * 1000))}
              className="w-full h-9"
            />
          ) : (
            <p className="text-[11px] text-amber-900 bg-amber-50 border border-amber-200 rounded-lg px-2.5 py-1.5">
              অডিও ফাইল না দিলেও অ্যান্ড্রয়েড অ্যাপে ফোনের বিল্ট-ইন বাংলা/ইংরেজি অডিও রিডার (TTS) দিয়ে শব্দে-শব্দে হাইলাইট চলবে!
            </p>
          )}

          <div className="max-h-60 overflow-y-auto space-y-2.5 pr-1 text-sm leading-relaxed">
            {paragraphs.length === 0 ? (
              <p className="text-xs text-slate-400 py-8 text-center">
                বাম পাশে টেক্সট দিলেই এখানে প্রতিটি শব্দের ক্লিকেবল টাইমস্ট্যাম্প প্রিভিউ দেখা যাবে।
              </p>
            ) : (
              paragraphs.map((_, pIdx) => {
                const items = cuesByParagraph[pIdx] ?? [];
                const firstCueMs = items[0]?.cue.s ?? 0;
                const isAnchored = anchorsMs[pIdx] !== undefined;
                return (
                  <div key={pIdx} className="bg-white border border-slate-200 rounded-lg p-2.5 space-y-1.5">
                    <div className="flex justify-between items-center text-[11px]">
                      <button
                        type="button"
                        onClick={() => seekToMs(firstCueMs)}
                        className="px-2 py-0.5 rounded bg-emerald-50 text-[#0F5132] font-mono font-bold hover:bg-emerald-100"
                      >
                        ▶ {formatTimestampMs(firstCueMs)}
                      </button>
                      {activeSrc && (
                        <button
                          type="button"
                          onClick={() => markParagraphHere(pIdx)}
                          className={`px-2 py-0.5 rounded font-semibold ${
                            isAnchored ? "bg-amber-300 text-slate-950" : "bg-slate-100 hover:bg-slate-200 text-slate-700"
                          }`}
                        >
                          {isAnchored ? `✓ সিঙ্কড (${formatTimestampMs(anchorsMs[pIdx])})` : "⏱️ এই অনুচ্ছেদ এখানে সিঙ্ক করুন"}
                        </button>
                      )}
                    </div>
                    <p className="text-xs leading-6 text-slate-800">
                      {items.map(({ cue, globalIdx }) => {
                        const active = globalIdx === activeWordIdx;
                        return (
                          <span
                            key={globalIdx}
                            onClick={() => seekToMs(cue.s)}
                            title={`${formatTimestampMs(cue.s)}`}
                            className={`cursor-pointer rounded px-0.5 transition ${
                              active ? "bg-amber-300 text-slate-950 font-bold shadow-sm" : "hover:bg-emerald-100"
                            }`}
                          >
                            {cue.w}{" "}
                          </span>
                        );
                      })}
                    </p>
                  </div>
                );
              })
            )}
          </div>
        </div>
      </div>

      <Btn onClick={saveChapterSync} busy={busy} className="w-full py-2.5 text-sm">
        💾 অধ্যায় {chapterNo}-এর টেক্সট ও শব্দে-শব্দে টাইমস্ট্যাম্প সার্ভারে সংরক্ষণ করুন
      </Btn>
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
      <div className="flex flex-wrap justify-between items-center gap-2">
        <h3 className="text-sm font-bold">📝 নির্ধারিত অনুশীলনী টেমপ্লেট ইমপোর্টার</h3>
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={async () => {
              await navigator.clipboard.writeText(AI_GENERATOR_PROMPT_BN);
              notify("🤖 AI প্রম্পট কপি হয়েছে! যেকোনো AI-তে আপনার টেক্সট/PDF দিয়ে এটি পেস্ট করলেই পূর্ণাঙ্গ টেমপ্লেট পেয়ে যাবেন।");
            }}
            className="text-xs px-2.5 py-1 rounded-md bg-amber-100 hover:bg-amber-200 text-amber-950 font-semibold"
          >
            🤖 AI প্রম্পট কপি
          </button>
          <button type="button" onClick={() => check(SAMPLE_TEMPLATE_BN)} className="text-xs underline text-[#0F5132] font-semibold">
            নমুনা লোড করুন
          </button>
        </div>
      </div>
      <select value={bookId} onChange={(e) => setBookId(e.target.value)} className={inputCls}>
        <option value="">— বই নির্বাচন করুন —</option>
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
        rows={8}
        placeholder="এখানে টেমপ্লেট (.txt / .md / JSON / CSV) পেস্ট করুন..."
        className={`${inputCls} font-mono text-xs`}
      />
      {parseErr && <p className="text-xs text-red-700">{parseErr}</p>}
      {preview && (
        <div className="bg-emerald-50 border border-emerald-200 rounded-lg px-3 py-2 text-xs text-emerald-950">
          প্রস্তুত: <strong>অধ্যায় {preview.chapterNo}</strong> ({preview.chapterTitle || "শিরোনামহীন"}) —{" "}
          <strong>{preview.questions.length}</strong> টি প্রশ্ন, <strong>{preview.flashNotes.length}</strong> টি ফ্ল্যাশ নোট।
        </div>
      )}
      <label className="flex items-center gap-2 text-xs">
        <input type="checkbox" checked={replace} onChange={(e) => setReplace(e.target.checked)} />
        পূর্বের প্রশ্ন মুছে নতুন প্রশ্ন বসান
      </label>
      <Btn onClick={submit} disabled={!preview || !bookId} busy={busy} className="w-full py-2 text-sm">
        📥 সার্ভারে অনুশীলনী ইমপোর্ট করুন
      </Btn>
    </Card>
  );
}
