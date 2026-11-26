"use client";

import React, { useState } from "react";
import {
  AlertTriangle,
  Award,
  BookOpen,
  CheckCircle2,
  CreditCard,
  ShieldCheck,
  Truck,
  Users,
  Video,
} from "lucide-react";
import {
  BookRecord,
  CircleOverview,
  INITIAL_BOOK_ORDERS,
  INITIAL_BOOKS,
  INITIAL_CIRCLES,
  INITIAL_SUBMISSIONS,
  INITIAL_WALLET_REQUESTS,
  isSupabaseConfigured,
  LearnerSubmission,
  PhysicalOrderRecord,
  PortalRole,
  WalletTopupRequest,
} from "@/lib/supabase";

type TabKey = "overview" | "grading" | "library" | "circles" | "wallet_orders";

function computeLearnerDiagnostics(sub: LearnerSubmission) {
  const maxMarks = sub.chapterType === "long" ? 61 : 52;
  const passMarks = sub.chapterType === "long" ? 37 : 32;
  const redFlagMarks = sub.chapterType === "long" ? 31 : 26;
  const totalScore = sub.autoScore + (sub.supervisorScore ?? 0);
  const scorePct = (totalScore / maxMarks) * 100;

  const calibration =
    sub.selfRatingAvg >= 4 && scorePct < 50
      ? "Over-confident"
      : sub.selfRatingAvg <= 2 && scorePct >= 65
      ? "Under-confident"
      : "Well-calibrated";

  const redIndicators: string[] = [];
  if (sub.overdueDays > 7) redIndicators.push(">৭ দিন বিলম্ব");
  if (totalScore < redFlagMarks || scorePct < 50) redIndicators.push("রেড-ফ্ল্যাগ/৫০%-এর কম নম্বর");
  if (!sub.genuinenessPassed) redIndicators.push("রিডিং-জেনুইননেস প্রশ্নে ভুল");
  if (!sub.writingSubmitted) redIndicators.push("রাইটিং টাস্ক মিস");

  const needsSupport = redIndicators.length >= 3;
  const verified =
    (totalScore >= passMarks || sub.supervisorOverride) && sub.writingSubmitted;

  return {
    maxMarks,
    passMarks,
    totalScore,
    scorePct: Math.round(scorePct),
    calibration,
    redIndicators,
    needsSupport,
    verified,
  };
}

export default function SparcAdminPortal() {
  const [role, setRole] = useState<PortalRole>("admin");
  const [activeTab, setActiveTab] = useState<TabKey>("overview");
  const [books, setBooks] = useState<BookRecord[]>(INITIAL_BOOKS);
  const [circles, setCircles] = useState<CircleOverview[]>(INITIAL_CIRCLES);
  const [submissions, setSubmissions] = useState<LearnerSubmission[]>(INITIAL_SUBMISSIONS);
  const [walletRequests, setWalletRequests] = useState<WalletTopupRequest[]>(INITIAL_WALLET_REQUESTS);
  const [bookOrders, setBookOrders] = useState<PhysicalOrderRecord[]>(INITIAL_BOOK_ORDERS);
  const [toast, setToast] = useState<string | null>(null);

  // New book quick form state
  const [newBookTitle, setNewBookTitle] = useState("");
  const [newBookAuthor, setNewBookAuthor] = useState("");
  const [supervisorPick, setSupervisorPick] = useState("ড. মাহবুবুর রহমান");

  const notify = (msg: string) => {
    setToast(msg);
    setTimeout(() => setToast(null), 4000);
  };

  const pendingWalletCount = walletRequests.filter((w) => w.status === "pending").length;
  const needsSupportCount = submissions.filter(
    (s) => computeLearnerDiagnostics(s).needsSupport
  ).length;

  return (
    <div className="min-h-screen flex flex-col bg-[#F8F7F4] text-slate-900">
      {/* Header */}
      <header className="bg-[#0F5132] text-white border-b border-emerald-900 sticky top-0 z-20">
        <div className="max-w-7xl mx-auto px-4 py-4 flex flex-wrap items-center justify-between gap-4">
          <div>
            <div className="flex items-center gap-2">
              <ShieldCheck className="w-6 h-6 text-amber-300" />
              <h1 className="text-xl font-bold tracking-tight">
                SPARC Study Circle — কন্ট্রোল ও সুপারভিশন পোর্টাল (v2.0)
              </h1>
            </div>
            <p className="text-xs text-emerald-100 mt-0.5">
              বই পড়তে পড়তে লেখা শেখা •{" "}
              {isSupabaseConfigured
                ? "Connected to Live Server"
                : "Interactive Sandbox Mode (Configure .env.local for Live Server)"}
            </p>
          </div>

          {/* Download APK & Role Switcher */}
          <div className="flex flex-wrap items-center gap-3">
            <a
              href="/downloads/SPARC-Study-Circle-v2.0.0-debug.apk"
              download
              className="px-3.5 py-1.5 rounded-lg bg-amber-400 hover:bg-amber-300 text-slate-950 text-xs font-bold shadow transition"
            >
              📱 Android APK ডাউনলোড (v2.0)
            </a>
            <div className="flex items-center gap-2 bg-emerald-950/60 p-1.5 rounded-lg">
            {(
              [
                ["admin", "Admin প্যানেল"],
                ["supervisor", "Supervisor (শিক্ষক)"],
                ["sparc_rep", "SPARC প্রতিনিধি"],
              ] as [PortalRole, string][]
            ).map(([r, label]) => (
              <button
                key={r}
                onClick={() => setRole(r)}
                className={`px-3 py-1.5 rounded-md text-xs font-semibold transition ${
                  role === r
                    ? "bg-amber-400 text-slate-950 shadow"
                    : "text-emerald-100 hover:bg-emerald-800/60"
                }`}
              >
                {label}
              </button>
            ))}
            </div>
          </div>
        </div>

        {/* Navigation Tabs */}
        <div className="max-w-7xl mx-auto px-4 flex gap-1 overflow-x-auto pb-2">
          {[
            { key: "overview", label: "সারসংক্ষেপ ও অ্যালার্ট", icon: Award },
            { key: "grading", label: "সুপারভাইজার মূল্যায়ন ও ক্যালিব্রেশন", icon: CheckCircle2 },
            { key: "circles", label: "স্টাডি সার্কেল ও লাইভ সেশন", icon: Users },
            { key: "library", label: "বই, অধ্যায় ও প্রশ্নব্যাংক", icon: BookOpen },
            { key: "wallet_orders", label: "ওয়ালেট TrxID ও হার্ডকপি অর্ডার", icon: CreditCard },
          ].map((tab) => {
            const Icon = tab.icon;
            return (
              <button
                key={tab.key}
                onClick={() => setActiveTab(tab.key as TabKey)}
                className={`flex items-center gap-2 px-3.5 py-2 rounded-t-lg text-sm font-medium whitespace-nowrap transition ${
                  activeTab === tab.key
                    ? "bg-[#F8F7F4] text-[#0F5132] font-semibold"
                    : "text-emerald-100 hover:bg-emerald-800/50"
                }`}
              >
                <Icon className="w-4 h-4" />
                {tab.label}
              </button>
            );
          })}
        </div>
      </header>

      {toast && (
        <div className="max-w-7xl mx-auto w-full px-4 mt-4">
          <div className="bg-emerald-100 border border-emerald-400 text-emerald-950 px-4 py-3 rounded-lg text-sm font-medium flex justify-between items-center">
            <span>{toast}</span>
            <button onClick={() => setToast(null)} className="text-xs underline">
              বন্ধ করুন
            </button>
          </div>
        </div>
      )}

      <main className="max-w-7xl mx-auto w-full px-4 py-6 flex-1 space-y-6">
        {/* TAB 1: OVERVIEW */}
        {activeTab === "overview" && (
          <div className="space-y-6">
            <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
              <div className="bg-white p-5 rounded-xl border border-slate-200 shadow-sm">
                <p className="text-xs text-slate-500 font-medium">সক্রিয় স্টাডি সার্কেল</p>
                <p className="text-2xl font-bold text-[#0F5132] mt-1">
                  {circles.filter((c) => c.status === "active").length} / {circles.length}
                </p>
                <p className="text-xs text-slate-600 mt-1">৮০% থ্রেশহোল্ড অটো-ট্রিগার চালু</p>
              </div>
              <div className="bg-white p-5 rounded-xl border border-red-200 shadow-sm">
                <p className="text-xs text-red-700 font-medium">Needs Support (≥৩ রেড ফ্ল্যাগ)</p>
                <p className="text-2xl font-bold text-red-600 mt-1">{needsSupportCount} জন</p>
                <p className="text-xs text-slate-600 mt-1">সুপারভাইজারের বিশেষ সহায়তা প্রয়োজন</p>
              </div>
              <div className="bg-white p-5 rounded-xl border border-amber-200 shadow-sm">
                <p className="text-xs text-amber-800 font-medium">পেন্ডিং ম্যানুয়াল TrxID ওয়ালেট</p>
                <p className="text-2xl font-bold text-amber-700 mt-1">{pendingWalletCount} টি</p>
                <p className="text-xs text-slate-600 mt-1">bKash/Nagad + SSLCommerz সমন্বিত</p>
              </div>
              <div className="bg-white p-5 rounded-xl border border-slate-200 shadow-sm">
                <p className="text-xs text-slate-500 font-medium">Researcher of the Month</p>
                <p className="text-lg font-bold text-slate-900 mt-1">ফারহান সাদিক (Sociology)</p>
                <p className="text-xs text-emerald-700 font-medium mt-1">🔥 ৯ দিন স্ট্রিক • ৩১০ পয়েন্ট</p>
              </div>
            </div>

            <div className="bg-white p-6 rounded-xl border border-slate-200 shadow-sm space-y-4">
              <h2 className="text-base font-bold">
                দলীয় সেশন ৮০% লক্ষ্যমাত্রা ও ১০-দিনের অটো ট্রিগার মনিটর
              </h2>
              {circles.map((c) => {
                const ready = c.exercisesSubmittedPct >= 80 || c.daysSinceLastSession >= 10;
                return (
                  <div key={c.id} className="p-4 rounded-lg bg-slate-50 border border-slate-200 space-y-2">
                    <div className="flex flex-wrap justify-between items-center gap-2">
                      <div>
                        <span className="font-semibold">{c.name}</span>
                        <span className="ml-2 text-xs px-2 py-0.5 rounded bg-emerald-100 text-emerald-900">
                          {c.status.toUpperCase()} ({c.memberCount}/{c.minMembersRequired} সদস্য)
                        </span>
                      </div>
                      <span className="text-xs font-medium text-slate-600">
                        সুপারভাইজার: {c.supervisorName} ({c.assignmentType})
                      </span>
                    </div>
                    <div className="w-full bg-slate-200 h-2.5 rounded-full overflow-hidden">
                      <div
                        className="bg-[#0F5132] h-full transition-all"
                        style={{ width: `${Math.min(100, c.exercisesSubmittedPct)}%` }}
                      />
                    </div>
                    <div className="flex justify-between text-xs text-slate-600">
                      <span>Exercises Submitted: {c.exercisesSubmittedPct}% (লক্ষ্যমাত্রা ৮০%)</span>
                      <span>
                        {ready
                          ? "✅ পরবর্তী দলীয় সেশন স্বয়ংক্রিয়ভাবে ঘোষিত!"
                          : `আগের সেশন: ${c.daysSinceLastSession} দিন আগে`}
                      </span>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        )}

        {/* TAB 2: SUPERVISOR GRADING, CALIBRATION & RED-FLAGS */}
        {activeTab === "grading" && (
          <div className="space-y-4">
            <div className="bg-amber-50 border border-amber-200 p-4 rounded-xl text-xs text-amber-950">
              <strong>গোপনীয়তা সুরক্ষা (Section 4.3 & 5.4):</strong> সুপারভাইজার এখানে F–J প্রশ্নের উত্তর,
              সেলফ-রেটিং ক্যালিব্রেশন এবং কেবল Poke-এর সংখ্যা দেখতে পান (কোনো ব্যক্তিগত Poke বার্তা প্রদর্শিত হয় না)।
            </div>

            {submissions.map((sub) => {
              const diag = computeLearnerDiagnostics(sub);
              return (
                <div
                  key={sub.id}
                  className="bg-white p-6 rounded-xl border border-slate-200 shadow-sm space-y-4"
                >
                  <div className="flex flex-wrap justify-between items-start gap-3">
                    <div>
                      <h3 className="text-base font-bold">
                        {sub.memberName}{" "}
                        <span className="text-xs font-normal text-slate-500">
                          ({sub.department} • {sub.circleName} • অধ্যায় {sub.chapterNo} [{sub.chapterType.toUpperCase()}])
                        </span>
                      </h3>
                      <p className="text-xs text-slate-600 mt-1">
                        প্রাপ্ত Poke সংখ্যা: {sub.pokesReceivedCount} টি • কুইক রিপ্লাই দিয়েছেন:{" "}
                        {sub.pokeRepliesCount} টি (বার্তা গোপন)
                      </p>
                    </div>

                    <div className="flex flex-wrap gap-2">
                      {diag.needsSupport && (
                        <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-bold bg-red-100 text-red-800">
                          <AlertTriangle className="w-3.5 h-3.5" /> Needs Support ({diag.redIndicators.length} রেড ফ্ল্যাগ)
                        </span>
                      )}
                      {diag.calibration !== "Well-calibrated" && (
                        <span className="px-2.5 py-1 rounded-full text-xs font-semibold bg-amber-100 text-amber-900">
                          ক্যালিব্রেশন: {diag.calibration} (Self-rating {sub.selfRatingAvg}/5)
                        </span>
                      )}
                      <span
                        className={`px-2.5 py-1 rounded-full text-xs font-semibold ${
                          diag.verified
                            ? "bg-emerald-100 text-emerald-900"
                            : "bg-slate-100 text-slate-800"
                        }`}
                      >
                        {diag.verified ? "Learning Verified ✔" : sub.stage}
                      </span>
                    </div>
                  </div>

                  {diag.redIndicators.length > 0 && (
                    <div className="text-xs text-red-700 bg-red-50 px-3 py-2 rounded-lg">
                      রেড ইন্ডিকেটরসমূহ: {diag.redIndicators.join(" • ")}
                    </div>
                  )}

                  <div className="p-4 rounded-lg bg-slate-50 border border-slate-200 space-y-2">
                    <p className="text-xs font-semibold text-slate-700">
                      বিশ্লেষণী উত্তর (F–J ওপেন প্রশ্ন) — মডেল পয়েন্টস দেখে ০–৩ স্কেলে মূল্যায়ন করুন:
                    </p>
                    <p className="text-sm text-slate-900 italic">&ldquo;{sub.openAnswerText}&rdquo;</p>
                    <div className="flex flex-wrap items-center gap-4 pt-2">
                      <label className="text-xs font-semibold">
                        সুপারভাইজার স্কোর (F–J মোট): {sub.supervisorScore ?? 0}
                      </label>
                      <input
                        type="range"
                        min={0}
                        max={30}
                        value={sub.supervisorScore ?? 0}
                        onChange={(e) => {
                          const val = Number(e.target.value);
                          setSubmissions((prev) =>
                            prev.map((item) =>
                              item.id === sub.id ? { ...item, supervisorScore: val } : item
                            )
                          );
                        }}
                        className="w-48 accent-[#0F5132]"
                      />
                      <span className="text-xs font-bold text-[#0F5132]">
                        সর্বমোট নম্বর: {diag.totalScore} / {diag.maxMarks} ({diag.scorePct}% • পাস{" "}
                        {diag.passMarks})
                      </span>
                      <button
                        onClick={() => {
                          setSubmissions((prev) =>
                            prev.map((item) =>
                              item.id === sub.id
                                ? { ...item, supervisorOverride: !item.supervisorOverride }
                                : item
                            )
                          );
                          notify(`${sub.memberName}-এর জন্য সুপারভাইজার ওভাররাইড আপডেট করা হয়েছে।`);
                        }}
                        className="text-xs px-3 py-1.5 rounded border border-slate-300 hover:bg-slate-100"
                      >
                        {sub.supervisorOverride ? "Override চালু ✔" : "Supervisor Override দিন"}
                      </button>
                    </div>
                  </div>

                  {sub.writingSubmitted && (
                    <div className="p-4 rounded-lg bg-emerald-50/60 border border-emerald-200 space-y-2">
                      <div className="flex justify-between items-center">
                        <span className="text-xs font-bold text-emerald-950">
                          রাইটিং টাস্ক / CPAR প্রপোজাল খসড়া ({sub.writingWordCount} শব্দ)
                        </span>
                        <button
                          onClick={() => {
                            setSubmissions((prev) =>
                              prev.map((item) =>
                                item.id === sub.id
                                  ? { ...item, finalProposalApproved: true }
                                  : item
                              )
                            );
                            notify(
                              `${sub.memberName}-এর রাইটিং ও প্রপোজাল অনুমোদিত! Study-Circle Completion Certificate প্রস্তুত।`
                            );
                          }}
                          className="px-3 py-1.5 rounded bg-[#0F5132] text-white text-xs font-semibold"
                        >
                          {sub.finalProposalApproved
                            ? "প্রপোজাল ও সার্টিফিকেট অনুমোদিত ✔"
                            : "প্রপোজাল অনুমোদন ও সার্টিফিকেট ইস্যু করুন"}
                        </button>
                      </div>
                      <p className="text-sm text-slate-800">{sub.writingExcerpt}</p>
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        )}

        {/* TAB 3: STUDY CIRCLES, DUAL SUPERVISOR & LIVE VIDEO SESSIONS */}
        {activeTab === "circles" && (
          <div className="space-y-4">
            {circles.map((c) => (
              <div
                key={c.id}
                className="bg-white p-6 rounded-xl border border-slate-200 shadow-sm space-y-4"
              >
                <div className="flex flex-wrap justify-between items-center gap-3">
                  <div>
                    <h3 className="text-base font-bold">{c.name}</h3>
                    <p className="text-xs text-slate-600">
                      বই: {c.bookTitle} • সদস্য: {c.memberCount}/{c.minMembersRequired}
                    </p>
                  </div>
                  {role === "admin" && (
                    <div className="flex items-center gap-2">
                      <input
                        type="text"
                        value={supervisorPick}
                        onChange={(e) => setSupervisorPick(e.target.value)}
                        className="border border-slate-300 rounded px-2.5 py-1 text-xs"
                        placeholder="সুপারভাইজারের নাম"
                      />
                      <button
                        onClick={() => {
                          setCircles((prev) =>
                            prev.map((item) =>
                              item.id === c.id
                                ? {
                                    ...item,
                                    supervisorName: supervisorPick,
                                    assignmentType: "admin_assigned",
                                  }
                                : item
                            )
                          );
                          notify(`${c.name}-এ অ্যাডমিন কর্তৃক সুপারভাইজার নিয়োগ সম্পন্ন হয়েছে।`);
                        }}
                        className="px-3 py-1.5 rounded bg-[#0F5132] text-white text-xs font-semibold"
                      >
                        Admin Assign Supervisor
                      </button>
                    </div>
                  )}
                </div>

                <div className="grid grid-cols-1 md:grid-cols-2 gap-3 pt-2">
                  <div className="p-3.5 rounded-lg bg-slate-50 border border-slate-200 flex items-center justify-between">
                    <div>
                      <p className="text-xs font-semibold text-slate-800">
                        ইন-অ্যাপ লাইভ ভিডিও রুম (Jitsi SDK)
                      </p>
                      <p className="text-xs text-slate-500">Room: {c.jitsiRoom}</p>
                    </div>
                    <a
                      href={`https://meet.jit.si/${c.jitsiRoom}`}
                      target="_blank"
                      rel="noreferrer"
                      className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded bg-emerald-700 text-white text-xs font-semibold"
                    >
                      <Video className="w-3.5 h-3.5" /> Jitsi জয়েন
                    </a>
                  </div>
                  <div className="p-3.5 rounded-lg bg-slate-50 border border-slate-200 flex items-center justify-between">
                    <div>
                      <p className="text-xs font-semibold text-slate-800">
                        এক্সটার্নাল সেশন লিংক (Google Meet / Zoom)
                      </p>
                      <p className="text-xs text-slate-500">{c.externalMeetUrl}</p>
                    </div>
                    <a
                      href={c.externalMeetUrl}
                      target="_blank"
                      rel="noreferrer"
                      className="px-3 py-1.5 rounded border border-slate-300 text-xs font-semibold hover:bg-slate-100"
                    >
                      Meet / Zoom ওপেন
                    </a>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}

        {/* TAB 4: DIGITAL LIBRARY & SUPABASE STORAGE CLOUD UPLOAD */}
        {activeTab === "library" && (
          <div className="space-y-6">
            {role === "admin" && (
              <div className="bg-white p-6 rounded-xl border border-slate-200 shadow-sm space-y-4">
                <h3 className="text-base font-bold">
                  ☁️ নতুন কপিরাইট-অনুমোদিত বই, PDF (`sparc-books`) ও বাংলা অডিও সামারি (`sparc-audio`) আপলোড করুন
                </h3>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                  <input
                    value={newBookTitle}
                    onChange={(e) => setNewBookTitle(e.target.value)}
                    placeholder="বইয়ের শিরোনাম"
                    className="border border-slate-300 rounded-lg px-3 py-2 text-sm"
                  />
                  <input
                    value={newBookAuthor}
                    onChange={(e) => setNewBookAuthor(e.target.value)}
                    placeholder="লেখকের নাম"
                    className="border border-slate-300 rounded-lg px-3 py-2 text-sm"
                  />
                </div>
                <div className="grid grid-cols-1 md:grid-cols-3 gap-3 items-center">
                  <label className="border border-dashed border-emerald-700 bg-emerald-50/50 rounded-lg px-3 py-2 text-xs font-semibold cursor-pointer block">
                    📄 বইয়ের PDF ফাইল নির্বাচন করুন (`sparc-books`)
                    <input
                      id="sparc-admin-pdf"
                      type="file"
                      accept="application/pdf"
                      className="mt-1 block w-full text-xs"
                    />
                  </label>
                  <label className="border border-dashed border-amber-700 bg-amber-50/50 rounded-lg px-3 py-2 text-xs font-semibold cursor-pointer block">
                    🎧 বাংলা অডিও সামারি MP3/M4A (`sparc-audio`)
                    <input
                      id="sparc-admin-audio"
                      type="file"
                      accept="audio/*"
                      className="mt-1 block w-full text-xs"
                    />
                  </label>
                  <button
                    onClick={async () => {
                      if (!newBookTitle.trim()) return;
                      const pdfInput = document.getElementById("sparc-admin-pdf") as HTMLInputElement | null;
                      const audioInput = document.getElementById("sparc-admin-audio") as HTMLInputElement | null;
                      const pdfFile = pdfInput?.files?.[0] ?? null;
                      const audioFile = audioInput?.files?.[0] ?? null;

                      let uploadedPdfUrl = "";
                      let uploadedAudioUrl = "";

                      const { supabase: sb } = await import("@/lib/supabase");
                      if (sb) {
                        const ts = Date.now();
                        if (pdfFile) {
                          const pdfPath = `pdfs/${ts}_${pdfFile.name.replace(/[^a-zA-Z0-9._-]/g, "_")}`;
                          const { error } = await sb.storage.from("sparc-books").upload(pdfPath, pdfFile, { upsert: true });
                          if (!error) {
                            uploadedPdfUrl = sb.storage.from("sparc-books").getPublicUrl(pdfPath).data.publicUrl;
                          }
                        }
                        if (audioFile) {
                          const audioPath = `summaries/${ts}_${audioFile.name.replace(/[^a-zA-Z0-9._-]/g, "_")}`;
                          const { error } = await sb.storage.from("sparc-audio").upload(audioPath, audioFile, { upsert: true });
                          if (!error) {
                            uploadedAudioUrl = sb.storage.from("sparc-audio").getPublicUrl(audioPath).data.publicUrl;
                          }
                        }
                        await sb.rpc("publish_new_book_with_media_rpc", {
                          p_title: newBookTitle.trim(),
                          p_author: newBookAuthor.trim() || "SPARC Academic Press",
                          p_price_bdt: 450,
                          p_pdf_url: uploadedPdfUrl,
                          p_audio_url: uploadedAudioUrl || null,
                        });
                      }

                      setBooks((prev) => [
                        ...prev,
                        {
                          id: `b-${Date.now()}`,
                          title: newBookTitle.trim(),
                          author: newBookAuthor.trim() || "SPARC Academic Press",
                          category: "Academic Research",
                          totalChapters: 7,
                          isFirstChapterFree: true,
                          minMembersRequired: 10,
                          physicalPriceBdt: 450,
                          chapters: [
                            {
                              chapterNo: 1,
                              title: "অধ্যায় ১: সূচনা ও মূল ধারণা",
                              type: "standard",
                              totalPages: 28,
                              totalMarks: 52,
                              passMarks: 32,
                              redFlagMarks: 26,
                              sessionsCount: 1,
                              audioSummaryUrl: uploadedAudioUrl,
                            },
                          ],
                        },
                      ]);
                      setNewBookTitle("");
                      setNewBookAuthor("");
                      if (pdfInput) pdfInput.value = "";
                      if (audioInput) audioInput.value = "";
                      notify("নতুন বই ও ক্লাউড মিডিয়া সফলভাবে ক্লাউড সার্ভারে যুক্ত হয়েছে!");
                    }}
                    className="bg-[#0F5132] text-white rounded-lg px-4 py-3 text-sm font-semibold"
                  >
                    ☁️ সার্ভারে বই ও মিডিয়া প্রকাশ করুন
                  </button>
                </div>
              </div>
            )}

            {books.map((b) => (
              <div key={b.id} className="bg-white p-6 rounded-xl border border-slate-200 shadow-sm space-y-3">
                <div className="flex justify-between items-center">
                  <div>
                    <h3 className="font-bold text-base">{b.title}</h3>
                    <p className="text-xs text-slate-600">
                      {b.author} • মোট {b.totalChapters} অধ্যায় • হার্ডকপি মূল্য ৳{b.physicalPriceBdt} • ন্যূনতম সদস্য {b.minMembersRequired}
                    </p>
                  </div>
                  {b.isFirstChapterFree && (
                    <span className="px-2.5 py-1 rounded bg-emerald-100 text-emerald-900 text-xs font-semibold">
                      ১ম অধ্যায় ফ্রি ট্রায়াল চালু
                    </span>
                  )}
                </div>
                <div className="space-y-2">
                  {b.chapters.map((ch) => (
                    <div
                      key={ch.chapterNo}
                      className="p-3 rounded-lg bg-slate-50 border border-slate-200 flex flex-wrap justify-between text-xs gap-2"
                    >
                      <span className="font-semibold">{ch.title}</span>
                      <span>
                        ধরন: <strong>{ch.type.toUpperCase()}</strong> • মোট নম্বর {ch.totalMarks} (পাস{" "}
                        {ch.passMarks}, রেড ফ্ল্যাগ &lt;{ch.redFlagMarks}) • {ch.sessionsCount} সেশন
                      </span>
                    </div>
                  ))}
                </div>
              </div>
            ))}
          </div>
        )}

        {/* TAB 5: WALLET MANUAL TRX APPROVAL & PHYSICAL BOOK ORDERS */}
        {activeTab === "wallet_orders" && (
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            {/* Wallet Top-up Verification */}
            <div className="bg-white p-6 rounded-xl border border-slate-200 shadow-sm space-y-4">
              <h3 className="text-base font-bold flex items-center gap-2">
                <CreditCard className="w-5 h-5 text-[#0F5132]" />
                ক্রেডিট ওয়ালেট ও ম্যানুয়াল TrxID ভেরিফিকেশন (≥৳৫০০)
              </h3>
              {role !== "admin" ? (
                <p className="text-xs text-slate-500">
                  ওয়ালেট অনুমোদনের ক্ষমতা শুধুমাত্র SPARC Admin-এর জন্য সংরক্ষিত।
                </p>
              ) : (
                walletRequests.map((w) => (
                  <div
                    key={w.id}
                    className="p-4 rounded-lg bg-slate-50 border border-slate-200 flex justify-between items-center gap-3"
                  >
                    <div>
                      <p className="font-semibold text-sm">
                        {w.userName} — ৳{w.amountBdt} ({w.method})
                      </p>
                      <p className="text-xs text-slate-600">
                        Ref/TrxID: <strong>{w.reference}</strong> • {w.phone} • {w.createdAt}
                      </p>
                    </div>
                    {w.status === "pending" ? (
                      <div className="flex gap-2">
                        <button
                          onClick={() => {
                            setWalletRequests((prev) =>
                              prev.map((item) =>
                                item.id === w.id ? { ...item, status: "verified" } : item
                              )
                            );
                            notify(`${w.userName}-এর ওয়ালেটে ৳${w.amountBdt} ভেরিফাই ও যুক্ত হয়েছে!`);
                          }}
                          className="px-3 py-1.5 rounded bg-[#0F5132] text-white text-xs font-semibold"
                        >
                          Approve
                        </button>
                      </div>
                    ) : (
                      <span className="text-xs font-bold text-emerald-800 uppercase">{w.status} ✔</span>
                    )}
                  </div>
                ))
              )}
            </div>

            {/* Physical Book Orders (Restricted to Admin, Supervisor/Teacher, SPARC Rep per Sec 3.5) */}
            <div className="bg-white p-6 rounded-xl border border-slate-200 shadow-sm space-y-4">
              <h3 className="text-base font-bold flex items-center gap-2">
                <Truck className="w-5 h-5 text-[#0F5132]" />
                ফিজিক্যাল বই অর্ডার ম্যানেজমেন্ট (Admin / শিক্ষক / SPARC প্রতিনিধি)
              </h3>
              {bookOrders.map((ord) => (
                <div
                  key={ord.id}
                  className="p-4 rounded-lg bg-slate-50 border border-slate-200 space-y-2"
                >
                  <div className="flex justify-between items-start">
                    <div>
                      <p className="font-semibold text-sm">
                        {ord.userName} • {ord.mode === "Purchase" ? `ক্রয় (৳${ord.amountBdt})` : "সংগ্রহ (Borrow)"}
                      </p>
                      <p className="text-xs text-slate-700">{ord.bookTitle}</p>
                      <p className="text-xs text-slate-500">
                        ঠিকানা: {ord.address} • {ord.phone}
                      </p>
                    </div>
                    <select
                      value={ord.status}
                      onChange={(e) => {
                        const next = e.target.value as PhysicalOrderRecord["status"];
                        setBookOrders((prev) =>
                          prev.map((item) => (item.id === ord.id ? { ...item, status: next } : item))
                        );
                        notify(`${ord.userName}-এর বই অর্ডারের স্ট্যাটাস '${next}' করা হয়েছে।`);
                      }}
                      className="border border-slate-300 rounded px-2 py-1 text-xs bg-white font-semibold"
                    >
                      <option value="pending">Pending</option>
                      <option value="approved">Approved</option>
                      <option value="dispatched">Dispatched</option>
                      <option value="delivered">Delivered</option>
                    </select>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}
      </main>
    </div>
  );
}
