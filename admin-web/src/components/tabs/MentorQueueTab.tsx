"use client";

import React, { useCallback, useEffect, useMemo, useState } from "react";
import { AlertTriangle } from "lucide-react";
import { friendlyError, server, shortDate } from "@/lib/server";
import { Btn, Card, Empty, ErrorBox, Loading, Pill, SectionTitle, inputCls } from "@/components/ui";
import type { TabProps } from "@/components/SparcAdminPortal";

interface ProgressRow {
  id: string;
  circle_id: string;
  user_id: string;
  chapter_id: string;
  chapter_no: number;
  status: string;
  auto_score: number;
  supervisor_score: number | null;
  supervisor_override: boolean;
  self_ratings: number[];
  calibration_flags: string[];
  genuineness_passed: boolean;
  red_flag_indicators: string[];
  red_flag_count: number;
  needs_support: boolean;
  writing_task_submitted: boolean;
  expected_completion_date: string | null;
  exercises_submitted_at: string | null;
  open_question_scores: Record<string, number> | null;
  peer_buddy_id: string | null;
}

interface OpenAnswer {
  questionId: string;
  section: string;
  prompt: string;
  answer: string;
}

interface Writing {
  progress_id: string;
  title: string;
  content: string;
  word_count: number;
  supervisor_approved: boolean;
  ai_feedback: unknown;
}

interface QueueData {
  rows: ProgressRow[];
  people: Record<string, { name: string; department: string }>;
  circles: Record<string, { name: string }>;
  members: Record<string, string[]>;
  chapters: Record<string, { title: string; type: string; total_marks: number; pass_marks: number }>;
  openAnswers: Record<string, OpenAnswer[]>;
  writings: Record<string, Writing>;
}

type Filter = "pending" | "support" | "verified" | "all";

function answerText(payload: unknown): string {
  if (payload == null) return "";
  if (typeof payload === "string") return payload;
  if (typeof payload === "object") {
    const o = payload as Record<string, unknown>;
    for (const k of ["text", "answer", "value", "response"]) if (typeof o[k] === "string") return o[k] as string;
  }
  return JSON.stringify(payload);
}

export default function MentorQueueTab({ me, notify }: TabProps) {
  const [data, setData] = useState<QueueData | null>(null);
  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState<string | null>(null);
  const [filter, setFilter] = useState<Filter>("pending");

  const load = useCallback(async () => {
    setLoading(true);
    setErr(null);
    try {
      const sb = server();
      let cq = sb.from("study_circles").select("id,name,supervisor_id");
      if (me.access === "supervisor") cq = cq.eq("supervisor_id", me.id);
      const { data: circles, error: ce } = await cq;
      if (ce) throw ce;
      const circleIds = (circles ?? []).map((c) => c.id);
      if (!circleIds.length) {
        setData({ rows: [], people: {}, circles: {}, members: {}, chapters: {}, openAnswers: {}, writings: {} });
        return;
      }

      const { data: rows, error: pe } = await sb
        .from("chapter_progress")
        .select(
          "id,circle_id,user_id,chapter_id,chapter_no,status,auto_score,supervisor_score,supervisor_override,self_ratings,calibration_flags,genuineness_passed,red_flag_indicators,red_flag_count,needs_support,writing_task_submitted,expected_completion_date,exercises_submitted_at,open_question_scores,peer_buddy_id"
        )
        .in("circle_id", circleIds)
        .or('status.eq."Exercises Submitted",status.eq."Learning Verified",needs_support.eq.true,writing_task_submitted.eq.true')
        .order("exercises_submitted_at", { ascending: false, nullsFirst: false })
        .limit(200);
      if (pe) throw pe;
      const progress = (rows ?? []) as ProgressRow[];

      const { data: mem } = await sb.from("study_circle_members").select("circle_id,user_id,invite_status").in("circle_id", circleIds);
      const members: Record<string, string[]> = {};
      (mem ?? []).forEach((m) => {
        if (m.invite_status === "left") return;
        (members[m.circle_id] ??= []).push(m.user_id);
      });

      const userIds = Array.from(new Set([...progress.map((r) => r.user_id), ...(mem ?? []).map((m) => m.user_id)]));
      const people: QueueData["people"] = {};
      if (userIds.length) {
        const { data: ps } = await sb.from("profiles").select("id,name,department").in("id", userIds);
        (ps ?? []).forEach((p) => (people[p.id] = { name: p.name, department: p.department }));
      }

      const chapterIds = Array.from(new Set(progress.map((r) => r.chapter_id)));
      const chapters: QueueData["chapters"] = {};
      if (chapterIds.length) {
        const { data: chs } = await sb.from("chapters").select("id,title,type,total_marks,pass_marks").in("id", chapterIds);
        (chs ?? []).forEach((c) => (chapters[c.id] = c));
      }

      const progressIds = progress.map((r) => r.id);
      const openAnswers: QueueData["openAnswers"] = {};
      const writings: QueueData["writings"] = {};
      if (progressIds.length) {
        const { data: subs } = await sb
          .from("exercise_submissions")
          .select("progress_id,question_id,attempt_no,answer_payload")
          .in("progress_id", progressIds);
        const qIds = Array.from(new Set((subs ?? []).map((s) => s.question_id)));
        const qMap: Record<string, { section_code: string; prompt_bn: string; is_auto_scored: boolean }> = {};
        if (qIds.length) {
          const { data: qs } = await sb.from("chapter_questions").select("id,section_code,prompt_bn,is_auto_scored").in("id", qIds);
          (qs ?? []).forEach((q) => (qMap[q.id] = q));
        }
        // keep latest attempt per question; only open (F–J) questions
        const latest: Record<string, { attempt: number; ans: OpenAnswer; pid: string }> = {};
        (subs ?? []).forEach((s) => {
          const q = qMap[s.question_id];
          const section = q?.section_code ?? "";
          const isOpen = q ? !q.is_auto_scored : false;
          if (!isOpen) return;
          const key = `${s.progress_id}:${s.question_id}`;
          if (!latest[key] || latest[key].attempt < s.attempt_no) {
            latest[key] = {
              attempt: s.attempt_no,
              pid: s.progress_id,
              ans: { questionId: s.question_id, section, prompt: q?.prompt_bn ?? `প্রশ্ন ${section}`, answer: answerText(s.answer_payload) },
            };
          }
        });
        Object.values(latest).forEach(({ pid, ans }) => (openAnswers[pid] ??= []).push(ans));
        Object.values(openAnswers).forEach((list) => list.sort((a, b) => a.section.localeCompare(b.section)));

        const { data: ws } = await sb
          .from("writing_submissions")
          .select("progress_id,title,content,word_count,supervisor_approved,ai_feedback")
          .in("progress_id", progressIds);
        (ws ?? []).forEach((w) => (writings[w.progress_id] = w as Writing));
      }

      const circleMap: QueueData["circles"] = {};
      (circles ?? []).forEach((c) => (circleMap[c.id] = { name: c.name }));
      setData({ rows: progress, people, circles: circleMap, members, chapters, openAnswers, writings });
    } catch (e) {
      setErr(friendlyError(e));
    } finally {
      setLoading(false);
    }
  }, [me]);

  useEffect(() => {
    load();
  }, [load]);

  const visible = useMemo(() => {
    const rows = data?.rows ?? [];
    switch (filter) {
      case "pending":
        return rows.filter((r) => r.status === "Exercises Submitted");
      case "support":
        return rows.filter((r) => r.needs_support);
      case "verified":
        return rows.filter((r) => r.status === "Learning Verified");
      default:
        return rows;
    }
  }, [data, filter]);

  return (
    <div className="space-y-4">
      <SectionTitle onRefresh={load} loading={loading}>
        ✅ মেন্টর মূল্যায়ন কিউ {me.access === "supervisor" ? "(আমার সার্কেল)" : "(সব সার্কেল)"}
      </SectionTitle>
      <div className="bg-amber-50 border border-amber-200 p-3 rounded-xl text-xs text-amber-950">
        <strong>গোপনীয়তা ও মানব মূল্যায়ন:</strong> এখানে F–J উন্মুক্ত প্রশ্নের উত্তর, সেলফ-রেটিং ক্যালিব্রেশন ও রেড-ফ্ল্যাগ দেখা যায়।
        AI মতামত শুধু সহায়ক — মেন্টরের নম্বর ও অনুমোদনই চূড়ান্ত।
      </div>
      <div className="flex flex-wrap gap-2">
        {(
          [
            ["pending", "মূল্যায়ন বাকি"],
            ["support", "Needs Support"],
            ["verified", "যাচাইকৃত"],
            ["all", "সব"],
          ] as [Filter, string][]
        ).map(([k, l]) => (
          <button
            key={k}
            onClick={() => setFilter(k)}
            className={`px-3 py-1.5 rounded-full text-xs font-semibold border ${
              filter === k ? "bg-[#0F5132] text-white border-[#0F5132]" : "bg-white border-slate-300 hover:bg-slate-100"
            }`}
          >
            {l} ({(data?.rows ?? []).filter((r) => (k === "pending" ? r.status === "Exercises Submitted" : k === "support" ? r.needs_support : k === "verified" ? r.status === "Learning Verified" : true)).length})
          </button>
        ))}
      </div>
      <ErrorBox message={err} />
      {loading && !data && <Loading />}
      {data && visible.length === 0 && <Empty>এই তালিকায় এখন কোনো জমা নেই।</Empty>}
      {data &&
        visible.map((r) => (
          <EvaluationCard key={r.id} row={r} data={data} notify={notify} onSaved={load} />
        ))}
    </div>
  );
}

function EvaluationCard({
  row,
  data,
  notify,
  onSaved,
}: {
  row: ProgressRow;
  data: QueueData;
  notify: TabProps["notify"];
  onSaved: () => void;
}) {
  const person = data.people[row.user_id];
  const ch = data.chapters[row.chapter_id];
  const answers = data.openAnswers[row.id] ?? [];
  const writing = data.writings[row.id];
  const [scores, setScores] = useState<Record<string, number>>(row.open_question_scores ?? {});
  const [buddy, setBuddy] = useState<string>(row.peer_buddy_id ?? "");
  const [extend, setExtend] = useState(0);
  const [override, setOverride] = useState<boolean>(row.supervisor_override);
  const [approveWriting, setApproveWriting] = useState<boolean>(writing?.supervisor_approved ?? false);
  const [busy, setBusy] = useState(false);

  const supScore = answers.length
    ? answers.reduce((sum, a) => sum + (scores[a.questionId] ?? 0), 0)
    : row.supervisor_score ?? 0;
  const total = (row.auto_score ?? 0) + supScore;
  const maxMarks = ch?.total_marks ?? 52;
  const passMarks = ch?.pass_marks ?? 32;
  const avgSelf = row.self_ratings?.length ? row.self_ratings.reduce((a, b) => a + b, 0) / row.self_ratings.length : null;
  const buddyOptions = (data.members[row.circle_id] ?? []).filter((id) => id !== row.user_id);

  const save = async () => {
    setBusy(true);
    try {
      const { error } = await server().rpc("mentor_evaluate_and_support_rpc", {
        p_circle_id: row.circle_id,
        p_target_user_id: row.user_id,
        p_chapter_no: row.chapter_no,
        p_supervisor_score: answers.length ? supScore : row.supervisor_score,
        p_open_scores: answers.length ? scores : null,
        p_peer_buddy_id: buddy || null,
        p_extend_days: extend,
        p_supervisor_override: override,
        p_approve_writing: writing ? approveWriting : null,
      });
      if (error) throw error;
      notify(`${person?.name ?? "গবেষক"}-এর মূল্যায়ন সার্ভারে সংরক্ষিত হয়েছে।`);
      onSaved();
    } catch (e) {
      notify(friendlyError(e), "err");
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card className="space-y-4">
      <div className="flex flex-wrap justify-between items-start gap-3">
        <div>
          <h3 className="text-base font-bold">
            {person?.name ?? "অজানা গবেষক"}{" "}
            <span className="text-xs font-normal text-slate-500">
              ({person?.department ?? "—"} • {data.circles[row.circle_id]?.name ?? "সার্কেল"} • অধ্যায় {row.chapter_no}
              {ch ? ` — ${ch.title}` : ""})
            </span>
          </h3>
          <p className="text-[11px] text-slate-500 mt-0.5">
            জমা: {shortDate(row.exercises_submitted_at)} • ডেডলাইন: {row.expected_completion_date ?? "—"}
          </p>
        </div>
        <div className="flex flex-wrap gap-1.5">
          {row.needs_support && (
            <Pill tone="red">
              <AlertTriangle className="w-3 h-3" /> Needs Support ({row.red_flag_count})
            </Pill>
          )}
          {row.calibration_flags?.map((f) => (
            <Pill key={f} tone="amber">
              {f === "over-confident" ? "অতি-আত্মবিশ্বাসী" : "কম-আত্মবিশ্বাসী"}
              {avgSelf !== null ? ` (সেলফ ${avgSelf.toFixed(1)}/5)` : ""}
            </Pill>
          ))}
          {!row.genuineness_passed && <Pill tone="red">রিডিং-জেনুইননেস ভুল</Pill>}
          <Pill tone={row.status === "Learning Verified" ? "green" : "slate"}>{row.status}</Pill>
        </div>
      </div>

      {row.red_flag_indicators?.length > 0 && (
        <div className="text-xs text-red-700 bg-red-50 px-3 py-2 rounded-lg">রেড ইন্ডিকেটর: {row.red_flag_indicators.join(" • ")}</div>
      )}

      <div className="p-4 rounded-lg bg-slate-50 border border-slate-200 space-y-3">
        <div className="flex flex-wrap justify-between text-xs">
          <span className="font-semibold text-slate-700">F–J উন্মুক্ত প্রশ্ন — প্রতিটিতে ০–৩ নম্বর দিন</span>
          <span className="font-bold text-[#0F5132]">
            অটো {row.auto_score} + মেন্টর {supScore} = {total} / {maxMarks} (পাস {passMarks})
          </span>
        </div>
        {answers.length === 0 && <p className="text-xs text-slate-500">উন্মুক্ত প্রশ্নের উত্তর পাওয়া যায়নি (বা এখনো জমা হয়নি)।</p>}
        {answers.map((a) => (
          <div key={a.questionId} className="bg-white rounded-lg border border-slate-200 p-3 space-y-2">
            <p className="text-xs font-semibold">
              [{a.section}] {a.prompt}
            </p>
            <p className="text-sm italic text-slate-800 whitespace-pre-wrap">“{a.answer || "—"}”</p>
            <div className="flex gap-1.5">
              {[0, 1, 2, 3].map((m) => (
                <button
                  key={m}
                  onClick={() => setScores((s) => ({ ...s, [a.questionId]: m }))}
                  className={`w-8 h-8 rounded-lg text-sm font-bold border ${
                    scores[a.questionId] === m ? "bg-[#0F5132] text-white border-[#0F5132]" : "bg-white border-slate-300 hover:bg-slate-100"
                  }`}
                >
                  {m}
                </button>
              ))}
            </div>
          </div>
        ))}
      </div>

      {writing && (
        <div className="p-4 rounded-lg bg-emerald-50/60 border border-emerald-200 space-y-2">
          <div className="flex flex-wrap justify-between items-center gap-2">
            <span className="text-xs font-bold text-emerald-950">
              ✍️ রাইটিং টাস্ক: {writing.title} ({writing.word_count} শব্দ)
            </span>
            <label className="flex items-center gap-2 text-xs font-semibold">
              <input type="checkbox" checked={approveWriting} onChange={(e) => setApproveWriting(e.target.checked)} />
              মানব মূল্যায়নে অনুমোদিত
            </label>
          </div>
          <p className="text-sm text-slate-800 whitespace-pre-wrap max-h-56 overflow-y-auto">{writing.content}</p>
          {writing.ai_feedback != null && (
            <p className="text-[11px] text-slate-500">ℹ️ AI মতামত সংযুক্ত আছে (সহায়ক মাত্র; AI ভুল করতে পারে)।</p>
          )}
        </div>
      )}

      <div className="grid grid-cols-1 md:grid-cols-3 gap-3 items-end">
        <div className="space-y-1">
          <label className="text-xs font-semibold">🤝 Peer Buddy যুক্ত করুন</label>
          <select value={buddy} onChange={(e) => setBuddy(e.target.value)} className={inputCls}>
            <option value="">— নেই —</option>
            {buddyOptions.map((id) => (
              <option key={id} value={id}>
                {data.people[id]?.name ?? id.slice(0, 8)}
              </option>
            ))}
          </select>
        </div>
        <div className="space-y-1">
          <label className="text-xs font-semibold">⏳ ডেডলাইন বাড়ান</label>
          <select value={extend} onChange={(e) => setExtend(Number(e.target.value))} className={inputCls}>
            <option value={0}>পরিবর্তন নেই</option>
            <option value={3}>+৩ দিন</option>
            <option value={7}>+৭ দিন</option>
          </select>
        </div>
        <label className="flex items-center gap-2 text-xs font-semibold pb-2">
          <input type="checkbox" checked={override} onChange={(e) => setOverride(e.target.checked)} />
          🔓 মেন্টর ওভাররাইড / অধ্যায় আনলক
        </label>
      </div>

      <div className="flex justify-end">
        <Btn onClick={save} busy={busy} className="px-4 py-2 text-sm">
          ✓ মূল্যায়ন সার্ভারে সংরক্ষণ করুন
        </Btn>
      </div>
    </Card>
  );
}
