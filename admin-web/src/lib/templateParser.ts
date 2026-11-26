// TypeScript port of the Android ExerciseTemplateParser (same .txt/.md, JSON and CSV formats).

export interface ParsedQuestion {
  is_auto_scored: boolean;
  section_code: string;
  prompt_bn: string;
  options: string[];
  correct_answer: string;
  explanation_bn: string;
  model_points: string[];
}

export interface ParsedTemplate {
  chapterNo: number;
  chapterTitle: string;
  chapterType: "standard" | "long";
  totalPages: number;
  flashNotes: string[];
  learningOutcomes: string[];
  writingPrompt: string;
  minWords: number;
  maxWords: number;
  questions: ParsedQuestion[];
}

const empty = (): ParsedTemplate => ({
  chapterNo: 1,
  chapterTitle: "",
  chapterType: "standard",
  totalPages: 24,
  flashNotes: [],
  learningOutcomes: [],
  writingPrompt: "",
  minWords: 400,
  maxWords: 600,
  questions: [],
});

export const SAMPLE_TEMPLATE_BN = `# SPARC নির্ধারিত অনুশীলনী টেমপ্লেট (.txt / .md)
CHAPTER_NO: 1
CHAPTER_TITLE: অধ্যায় ১: গবেষণা প্রবন্ধের মূল কাঠামো ও সমস্যা নির্ণয়
CHAPTER_TYPE: STANDARD
TOTAL_PAGES: 22
FLASH_NOTE: গবেষণা প্রশ্ন সবসময় সুনির্দিষ্ট, প্রাসঙ্গিক ও বাস্তবসম্মত হতে হবে।
OUTCOME: গবেষণা সমস্যা ও উদ্দেশ্য সুস্পষ্টভাবে আলাদা করতে পারা
WRITING_PROMPT: এই প্রবন্ধের আলোকে ৪০০–৬০০ শব্দে আপনার নির্বাচিত বিষয়ের Research Gap লিখুন।
WORD_LIMIT: 400-600

[MCQ | C]
Q: একাডেমিক লিটারেচার রিভিউয়ের প্রধান উদ্দেশ্য কোনটি?
OPT: শুধু বই ও প্রবন্ধের লম্বা তালিকা তৈরি করা
OPT*: পূর্ববর্তী গবেষণার বিতর্ক বিশ্লেষণ করে Research Gap চিহ্নিত করা
OPT: লেখকদের ব্যক্তিগত জীবনী আলোচনা করা
EXP: Research Gap চিহ্নিত করাই নতুন গবেষণার যৌক্তিক ভিত্তি তৈরি করে।

[OPEN | H]
Q: এই প্রবন্ধের পদ্ধতির আলোকে আপনার বিভাগে একটি প্রাথমিক ফিল্ড পর্যবেক্ষণ কীভাবে পরিকল্পনা করবেন?
POINT: ১ পয়েন্ট: গবেষণা ক্ষেত্র (Site) ও অংশগ্রহণকারী চিহ্নিত করা
POINT: ১ পয়েন্ট: পর্যবেক্ষণ ও ফিল্ড নোট নেওয়ার নিয়ম উল্লেখ করা
POINT: ১ পয়েন্ট: গবেষণার নৈতিক সম্মতি (Informed Consent) নিশ্চিত করা
EXP: মেন্টর উপরের ৩টি পয়েন্টের ভিত্তিতে ০–৩ নম্বর দেবেন।`;

export const AI_GENERATOR_PROMPT_BN = `তুমি একজন বিশ্ববিদ্যালয়ের গবেষণা শিক্ষক। সংযুক্ত বই/প্রবন্ধের অধ্যায়টি পড়ে হুবহু নিচের SPARC Standard Exercise Template (.txt) ছকে বাংলায় ফ্ল্যাশ নোট, লার্নিং আউটকাম, রাইটিং প্রম্পট, ৫টি MCQ প্রশ্ন (সঠিক উত্তরে OPT*: চিহ্ন দাও) এবং ৩টি বিশ্লেষণী OPEN প্রশ্ন (প্রতিটিতে ৩টি করে POINT: রুব্রিক দাও) তৈরি করে দাও। অতিরিক্ত কোনো ভূমিকা বা ব্যাখ্যা না লিখে শুধু নিচের ছকটি দাও:

CHAPTER_NO: 1
CHAPTER_TITLE: অধ্যায় ১: [অধ্যায়ের শিরোনাম]
CHAPTER_TYPE: STANDARD
TOTAL_PAGES: 24
FLASH_NOTE: [মূল ধারণা ১]
FLASH_NOTE: [মূল ধারণা ২]
FLASH_NOTE: [মূল ধারণা ৩]
OUTCOME: [শিখনফল ১]
OUTCOME: [শিখনফল ২]
WRITING_PROMPT: [৪০০-৬০০ শব্দের একাডেমিক রাইটিং টাস্ক]
WORD_LIMIT: 400-600

[MCQ | A]
Q: [প্রশ্ন ১]
OPT: [ভুল অপশন]
OPT*: [সঠিক অপশন]
OPT: [ভুল অপশন]
EXP: [সংক্ষিপ্ত ব্যাখ্যা]

[OPEN | F]
Q: [বিশ্লেষণধর্মী উন্মুক্ত প্রশ্ন ১]
POINT: ১ পয়েন্ট: [রুব্রিক ১]
POINT: ১ পয়েন্ট: [রুব্রিক ২]
POINT: ১ পয়েন্ট: [রুব্রিক ৩]
EXP: মানব মেন্টর উপরের ৩টি পয়েন্টের ভিত্তিতে ০–৩ নম্বর দেবেন।`;

export function parseTemplate(raw: string): ParsedTemplate {
  const trimmed = raw.trim();
  if (!trimmed) throw new Error("টেমপ্লেট খালি।");
  if (trimmed.startsWith("{")) return parseJson(trimmed);
  if (isLikelyCsv(trimmed)) return parseCsv(trimmed);
  return parseText(trimmed);
}

function isLikelyCsv(text: string) {
  const first = text.split("\n").find((l) => l.trim() && !l.trim().startsWith("#")) ?? "";
  const up = first.toUpperCase();
  return first.includes(",") && (up.startsWith("TYPE,") || up.startsWith("MCQ,") || up.startsWith("OPEN,"));
}

/* eslint-disable @typescript-eslint/no-explicit-any */
function parseJson(raw: string): ParsedTemplate {
  const o: any = JSON.parse(raw);
  const strs = (a: any): string[] => (Array.isArray(a) ? a.filter((x) => typeof x === "string") : []);
  const questions: ParsedQuestion[] = (Array.isArray(o.questions) ? o.questions : [])
    .map((q: any) => {
      const prompt = q.prompt ?? q.prompt_bn;
      if (!prompt) return null;
      const isAuto = typeof q.is_auto_scored === "boolean" ? q.is_auto_scored : String(q.type ?? "MCQ").toUpperCase() !== "OPEN";
      const opts = strs(q.options);
      return {
        is_auto_scored: isAuto,
        section_code: String(q.section ?? (isAuto ? "C" : "H")).toUpperCase().slice(0, 1),
        prompt_bn: String(prompt),
        options: isAuto ? opts : [],
        correct_answer: isAuto ? String(q.correct_answer ?? opts[0] ?? "") : "",
        explanation_bn: String(q.explanation ?? ""),
        model_points: isAuto ? [] : strs(q.model_points),
      } as ParsedQuestion;
    })
    .filter(Boolean) as ParsedQuestion[];
  return {
    chapterNo: Math.max(1, Number(o.chapter_no) || 1),
    chapterTitle: String(o.chapter_title ?? o.title ?? ""),
    chapterType: String(o.chapter_type ?? "STANDARD").toUpperCase() === "LONG" ? "long" : "standard",
    totalPages: Math.max(1, Number(o.total_pages) || 24),
    flashNotes: strs(o.flash_notes),
    learningOutcomes: strs(o.learning_outcomes),
    writingPrompt: String(o.writing_prompt ?? ""),
    minWords: Number(o.min_words) || 400,
    maxWords: Number(o.max_words) || 600,
    questions,
  };
}
/* eslint-enable @typescript-eslint/no-explicit-any */

function parseCsv(raw: string): ParsedTemplate {
  const t = empty();
  raw
    .split("\n")
    .map((l) => l.trim())
    .filter((l) => l && !l.startsWith("#"))
    .forEach((line, idx) => {
      const cols = line.split(",").map((c) => c.trim().replace(/^"|"$/g, ""));
      if (idx === 0 && cols[0]?.toUpperCase() === "TYPE") return;
      if (cols.length < 3) return;
      const type = cols[0].toUpperCase();
      const sec = cols[1] || (type === "OPEN" ? "H" : "C");
      if (type === "OPEN") {
        t.questions.push({
          is_auto_scored: false,
          section_code: sec.toUpperCase().slice(0, 1),
          prompt_bn: cols[2],
          options: [],
          correct_answer: "",
          explanation_bn: cols[6] ?? "",
          model_points: cols.slice(3, 6).filter(Boolean),
        });
      } else {
        const opts = cols.slice(3, 6).filter(Boolean);
        t.questions.push({
          is_auto_scored: true,
          section_code: sec.toUpperCase().slice(0, 1),
          prompt_bn: cols[2],
          options: opts,
          correct_answer: cols[6] || opts[0] || "",
          explanation_bn: cols[7] ?? "",
          model_points: [],
        });
      }
    });
  if (!t.questions.length) throw new Error("CSV-তে কোনো বৈধ প্রশ্ন পাওয়া যায়নি।");
  return t;
}

function parseText(raw: string): ParsedTemplate {
  const t = empty();
  let auto: boolean | null = null;
  let sec = "C";
  let prompt = "";
  let opts: string[] = [];
  let correct = "";
  let exp = "";
  let points: string[] = [];

  const flush = () => {
    if (auto === null) return;
    if (prompt.trim()) {
      t.questions.push({
        is_auto_scored: auto,
        section_code: sec,
        prompt_bn: prompt.trim(),
        options: auto ? [...opts] : [],
        correct_answer: auto ? correct || opts[0] || "" : "",
        explanation_bn: exp.trim(),
        model_points: auto ? [] : [...points],
      });
    }
    auto = null;
    prompt = "";
    opts = [];
    correct = "";
    exp = "";
    points = [];
  };

  for (const rawLine of raw.split("\n")) {
    const line = rawLine.trim();
    if (!line || line.startsWith("#")) continue;
    if (line.startsWith("[") && line.endsWith("]")) {
      flush();
      const parts = line.slice(1, -1).split("|").map((p) => p.trim());
      const kind = (parts[0] ?? "MCQ").toUpperCase();
      auto = !["OPEN", "CREATIVE", "SCENARIO"].includes(kind);
      sec = (parts[1] ?? (auto ? "C" : "H")).toUpperCase().slice(0, 1) || (auto ? "C" : "H");
      continue;
    }
    const ci = line.indexOf(":");
    if (ci <= 0) continue;
    const key = line.slice(0, ci).trim().toUpperCase();
    const val = line.slice(ci + 1).trim();
    if (!val) continue;
    if (auto !== null) {
      if (["Q", "QUESTION", "PROMPT"].includes(key)) prompt = val;
      else if (key === "OPT*") {
        opts.push(val);
        correct = val;
      } else if (["OPT", "OPTION"].includes(key)) opts.push(val);
      else if (["ANS", "CORRECT"].includes(key)) correct = val;
      else if (["EXP", "EXPLANATION"].includes(key)) exp = val;
      else if (["POINT", "MODEL_POINT"].includes(key)) points.push(val);
    } else {
      if (key === "CHAPTER_NO") t.chapterNo = Math.max(1, parseInt(val, 10) || 1);
      else if (key === "CHAPTER_TITLE" || key === "TITLE") t.chapterTitle = val;
      else if (key === "CHAPTER_TYPE" || key === "TYPE") t.chapterType = val.toUpperCase() === "LONG" ? "long" : "standard";
      else if (key === "TOTAL_PAGES" || key === "PAGES") t.totalPages = Math.max(1, parseInt(val, 10) || 24);
      else if (key === "FLASH_NOTE" || key === "NOTE") t.flashNotes.push(val);
      else if (key === "OUTCOME" || key === "LEARNING_OUTCOME") t.learningOutcomes.push(val);
      else if (key === "WRITING_PROMPT") t.writingPrompt = val;
      else if (key === "WORD_LIMIT") {
        const nums = val.split(/[-–\s]+/).map((n) => parseInt(n, 10)).filter((n) => !isNaN(n));
        if (nums.length >= 2) {
          t.minWords = nums[0];
          t.maxWords = nums[1];
        }
      }
    }
  }
  flush();
  if (!t.chapterTitle && !t.flashNotes.length && !t.questions.length && !t.writingPrompt) {
    throw new Error("টেমপ্লেটে কোনো অধ্যায় তথ্য বা প্রশ্ন পাওয়া যায়নি।");
  }
  return t;
}
