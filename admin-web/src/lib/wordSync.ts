export interface WordCue {
  /** Word token including trailing punctuation */
  w: string;
  /** Start time in milliseconds */
  s: number;
  /** End time in milliseconds */
  e: number;
  /** Paragraph index (0-based) */
  p: number;
}

export type ChapterContentMode = "text" | "pdf" | "hybrid";

/** Splits full chapter text into clean non-empty paragraphs. */
export function splitTextIntoParagraphs(rawText: string): string[] {
  return rawText
    .replace(/\r\n/g, "\n")
    .split(/\n{2,}|\n/)
    .map((line) => line.trim())
    .filter((line) => line.length > 0);
}

/** Estimates phonetic + punctuation pause weight for Bengali & English words. */
export function estimateWordWeight(word: string): number {
  const cleanLen = Math.max(word.replace(/[^\p{L}\p{N}]/gu, "").length, 2);
  let pauseBonus = 0;
  if (/[।.?!]$/.test(word)) pauseBonus = 4.5;
  else if (/[,;:—–-]$/.test(word)) pauseBonus = 2.2;
  return cleanLen + pauseBonus;
}

/**
 * Builds millisecond word-by-word timestamps from paragraph anchor times (Tap-to-Sync)
 * or purely automatically when no anchors are set.
 */
export function buildWordTimestampsFromParagraphAnchors(
  rawText: string,
  paragraphAnchorsMs: Record<number, number>,
  totalDurationMs: number
): WordCue[] {
  const paragraphs = splitTextIntoParagraphs(rawText);
  if (paragraphs.length === 0) return [];

  const safeDuration = Math.max(totalDurationMs, paragraphs.length * 2000, 5000);
  const paraWords = paragraphs.map((p) => p.split(/\s+/).filter(Boolean));
  const paraWeights = paraWords.map((words) =>
    Math.max(
      words.reduce((acc, w) => acc + estimateWordWeight(w), 0),
      1
    )
  );
  const totalWeight = Math.max(
    paraWeights.reduce((a, b) => a + b, 0),
    1
  );

  // Determine start & end window for every paragraph
  const paraStarts: number[] = new Array(paragraphs.length).fill(0);
  const anchoredIndices = Object.keys(paragraphAnchorsMs)
    .map((k) => Number(k))
    .filter((idx) => !Number.isNaN(idx) && idx >= 0 && idx < paragraphs.length && paragraphAnchorsMs[idx] >= 0)
    .sort((a, b) => a - b);

  if (anchoredIndices.length === 0) {
    let cursor = 0;
    for (let i = 0; i < paragraphs.length; i++) {
      paraStarts[i] = Math.round(cursor);
      cursor += (paraWeights[i] / totalWeight) * safeDuration;
    }
  } else {
    // Interpolate before first anchor
    const firstIdx = anchoredIndices[0];
    const firstTime = Math.min(paragraphAnchorsMs[firstIdx], safeDuration - 500);
    if (firstIdx > 0) {
      const preWeight = paraWeights.slice(0, firstIdx).reduce((a, b) => a + b, 0);
      let cursor = 0;
      for (let i = 0; i < firstIdx; i++) {
        paraStarts[i] = Math.round(cursor);
        cursor += (paraWeights[i] / Math.max(preWeight, 1)) * firstTime;
      }
    }
    // Interpolate between consecutive anchors
    for (let a = 0; a < anchoredIndices.length; a++) {
      const startIdx = anchoredIndices[a];
      const endIdx = a + 1 < anchoredIndices.length ? anchoredIndices[a + 1] : paragraphs.length;
      const startTime = Math.min(paragraphAnchorsMs[startIdx], safeDuration);
      const endTime =
        a + 1 < anchoredIndices.length
          ? Math.max(paragraphAnchorsMs[endIdx], startTime + 200)
          : Math.max(safeDuration, startTime + 1000);

      const spanWeight = paraWeights.slice(startIdx, endIdx).reduce((acc, w) => acc + w, 0);
      let cursor = startTime;
      for (let i = startIdx; i < endIdx; i++) {
        paraStarts[i] = Math.round(cursor);
        cursor += (paraWeights[i] / Math.max(spanWeight, 1)) * (endTime - startTime);
      }
    }
  }

  const cues: WordCue[] = [];
  for (let pIdx = 0; pIdx < paragraphs.length; pIdx++) {
    const startMs = paraStarts[pIdx];
    const endMs = pIdx + 1 < paragraphs.length ? Math.max(paraStarts[pIdx + 1], startMs + 150) : Math.max(safeDuration, startMs + 500);
    const words = paraWords[pIdx];
    const pWeight = paraWeights[pIdx];
    let wCursor = startMs;
    for (let wIdx = 0; wIdx < words.length; wIdx++) {
      const w = words[wIdx];
      const span = (estimateWordWeight(w) / pWeight) * (endMs - startMs);
      const s = Math.round(wCursor);
      const e = wIdx === words.length - 1 ? Math.round(endMs) : Math.max(Math.round(wCursor + span), s + 40);
      cues.push({ w, s, e, p: pIdx });
      wCursor += span;
    }
  }
  return cues;
}

function parseSrtTimeMs(raw: string): number | null {
  const m = raw.trim().match(/(?:(\d{1,2}):)?(\d{1,2}):(\d{1,2})[.,](\d{1,3})/);
  if (!m) return null;
  const hrs = Number(m[1] ?? 0);
  const mins = Number(m[2] ?? 0);
  const secs = Number(m[3] ?? 0);
  const msStr = (m[4] ?? "0").padEnd(3, "0").slice(0, 3);
  return ((hrs * 3600 + mins * 60 + secs) * 1000) + Number(msStr);
}

/**
 * Parses .srt, .vtt, or JSON WordCue[] into word-by-word timestamps.
 */
export function parseSubtitleToWordTimestamps(rawSubtitle: string): { text: string; cues: WordCue[] } {
  const trimmed = rawSubtitle.trim();
  if (!trimmed) throw new Error("সাবটাইটেল বা টাইমস্ট্যাম্প ফাইল ফাঁকা।");

  if (trimmed.startsWith("[")) {
    const arr = JSON.parse(trimmed) as WordCue[];
    const valid = arr.filter((x) => typeof x.w === "string" && typeof x.s === "number");
    const byPara: Record<number, string[]> = {};
    valid.forEach((c) => {
      const p = c.p ?? 0;
      (byPara[p] ??= []).push(c.w);
    });
    const text = Object.keys(byPara)
      .map(Number)
      .sort((a, b) => a - b)
      .map((k) => byPara[k].join(" "))
      .join("\n\n");
    return { text, cues: valid };
  }

  // Parse SRT / WebVTT blocks
  const blocks = trimmed
    .replace(/\r\n/g, "\n")
    .replace(/^WEBVTT[^\n]*\n+/i, "")
    .split(/\n{2,}/);

  const cues: WordCue[] = [];
  const paragraphs: string[] = [];

  for (const block of blocks) {
    const lines = block
      .split("\n")
      .map((l) => l.trim())
      .filter(Boolean);
    const arrowLineIdx = lines.findIndex((l) => l.includes("-->"));
    if (arrowLineIdx === -1) continue;
    const [left, right] = lines[arrowLineIdx].split("-->");
    const startMs = parseSrtTimeMs((left ?? "").trim());
    const endMs = parseSrtTimeMs((right ?? "").trim().split(/\s+/)[0] ?? "");
    if (startMs === null || endMs === null || endMs <= startMs) continue;

    const textLine = lines
      .slice(arrowLineIdx + 1)
      .join(" ")
      .replace(/<[^>]+>/g, "")
      .trim();
    if (!textLine) continue;

    const pIdx = paragraphs.length;
    paragraphs.push(textLine);
    const words = textLine.split(/\s+/).filter(Boolean);
    const weightSum = Math.max(words.reduce((a, w) => a + estimateWordWeight(w), 0), 1);
    let cursor = startMs;
    words.forEach((w, idx) => {
      const span = (estimateWordWeight(w) / weightSum) * (endMs - startMs);
      const s = Math.round(cursor);
      const e = idx === words.length - 1 ? endMs : Math.max(Math.round(cursor + span), s + 30);
      cues.push({ w, s, e, p: pIdx });
      cursor += span;
    });
  }

  if (cues.length === 0) {
    throw new Error("সঠিক .srt / .vtt / JSON টাইমস্ট্যাম্প পাওয়া যায়নি।");
  }
  return { text: paragraphs.join("\n\n"), cues };
}

/** Binary search for the active word index at `positionMs`. */
export function findActiveWordIndex(cues: WordCue[], positionMs: number): number {
  if (cues.length === 0 || positionMs < 0) return -1;
  let lo = 0;
  let hi = cues.length - 1;
  let best = -1;
  while (lo <= hi) {
    const mid = (lo + hi) >> 1;
    const c = cues[mid];
    if (positionMs >= c.s && positionMs <= c.e) return mid;
    if (positionMs > c.e) {
      best = mid;
      lo = mid + 1;
    } else {
      hi = mid - 1;
    }
  }
  return best;
}

export function formatTimestampMs(ms: number): string {
  const totalSec = Math.max(0, Math.floor(ms / 1000));
  const m = Math.floor(totalSec / 60);
  const s = totalSec % 60;
  return `${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}`;
}
