"use client";

import React, { useCallback, useEffect, useState } from "react";
import { server, friendlyError } from "@/lib/server";
import { Card, ErrorBox, Loading, SectionTitle } from "@/components/ui";
import type { TabProps } from "@/components/SparcAdminPortal";

interface Stats {
  circlesActive: number;
  circlesForming: number;
  myCircles: number;
  needsSupport: number;
  awaitingGrading: number;
  pendingWallet: number | null;
  pendingOrders: number | null;
  totalUsers: number;
  mentors: number;
  top: { name: string; department: string; gamification_points: number; reading_streak_days: number }[];
}

export default function OverviewTab({ me }: TabProps) {
  const [stats, setStats] = useState<Stats | null>(null);
  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setErr(null);
    try {
      const sb = server();
      const isStaff = me.access === "admin" || me.access === "sparc_rep";

      const circlesQ = sb.from("study_circles").select("id,status,supervisor_id");
      const { data: circles, error: cErr } = await circlesQ;
      if (cErr) throw cErr;
      const mine = (circles ?? []).filter((c) => c.supervisor_id === me.id).map((c) => c.id);
      const scopeIds = me.access === "supervisor" ? mine : (circles ?? []).map((c) => c.id);

      let needsSupport = 0;
      let awaiting = 0;
      if (scopeIds.length) {
        const ns = await sb
          .from("chapter_progress")
          .select("id", { count: "exact", head: true })
          .eq("needs_support", true)
          .in("circle_id", scopeIds);
        needsSupport = ns.count ?? 0;
        const aw = await sb
          .from("chapter_progress")
          .select("id", { count: "exact", head: true })
          .eq("status", "Exercises Submitted")
          .in("circle_id", scopeIds);
        awaiting = aw.count ?? 0;
      }

      let pendingWallet: number | null = null;
      let pendingOrders: number | null = null;
      if (isStaff) {
        const w = await sb.from("wallet_transactions").select("id", { count: "exact", head: true }).eq("status", "pending");
        pendingWallet = w.count ?? 0;
        const o = await sb.from("physical_book_orders").select("id", { count: "exact", head: true }).eq("status", "pending");
        pendingOrders = o.count ?? 0;
      }

      const users = await sb.from("profiles").select("id", { count: "exact", head: true });
      const mentors = await sb.from("profiles").select("id", { count: "exact", head: true }).eq("base_role", "supervisor");
      const top = await sb
        .from("profiles")
        .select("name,department,gamification_points,reading_streak_days")
        .order("gamification_points", { ascending: false })
        .limit(5);

      setStats({
        circlesActive: (circles ?? []).filter((c) => c.status === "active").length,
        circlesForming: (circles ?? []).filter((c) => c.status === "forming").length,
        myCircles: mine.length,
        needsSupport,
        awaitingGrading: awaiting,
        pendingWallet,
        pendingOrders,
        totalUsers: users.count ?? 0,
        mentors: mentors.count ?? 0,
        top: top.data ?? [],
      });
    } catch (e) {
      setErr(friendlyError(e));
    } finally {
      setLoading(false);
    }
  }, [me]);

  useEffect(() => {
    load();
  }, [load]);

  return (
    <div className="space-y-6">
      <SectionTitle onRefresh={load} loading={loading}>
        📊 লাইভ সারসংক্ষেপ
      </SectionTitle>
      <ErrorBox message={err} />
      {!stats && loading && <Loading />}
      {stats && (
        <>
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            <Stat label="সক্রিয় সার্কেল" value={`${stats.circlesActive}`} hint={`গঠনাধীন: ${stats.circlesForming}`} />
            {me.access === "supervisor" && <Stat label="আমার সার্কেল (মেন্টর)" value={`${stats.myCircles}`} />}
            <Stat label="মূল্যায়নের অপেক্ষায়" value={`${stats.awaitingGrading}`} hint="Exercises Submitted" tone="amber" />
            <Stat label="Needs Support (≥৩ রেড ফ্ল্যাগ)" value={`${stats.needsSupport}`} tone="red" />
            {stats.pendingWallet !== null && <Stat label="পেন্ডিং TrxID" value={`${stats.pendingWallet}`} tone="amber" />}
            {stats.pendingOrders !== null && <Stat label="পেন্ডিং বই অর্ডার" value={`${stats.pendingOrders}`} />}
            <Stat label="মোট ইউজার" value={`${stats.totalUsers}`} hint={`মেন্টর: ${stats.mentors}`} />
          </div>

          <Card>
            <h3 className="text-sm font-bold mb-3">🏆 শীর্ষ গবেষক (পয়েন্ট অনুযায়ী)</h3>
            {stats.top.length === 0 ? (
              <p className="text-xs text-slate-500">এখনো কোনো ডেটা নেই।</p>
            ) : (
              <ol className="space-y-2">
                {stats.top.map((p, i) => (
                  <li key={i} className="flex justify-between text-sm bg-slate-50 rounded-lg px-3 py-2">
                    <span>
                      {i + 1}. <strong>{p.name}</strong> <span className="text-xs text-slate-500">({p.department})</span>
                    </span>
                    <span className="text-xs text-emerald-800 font-semibold">
                      {p.gamification_points} পয়েন্ট • 🔥 {p.reading_streak_days} দিন
                    </span>
                  </li>
                ))}
              </ol>
            )}
          </Card>
        </>
      )}
    </div>
  );
}

function Stat({ label, value, hint, tone }: { label: string; value: string; hint?: string; tone?: "red" | "amber" }) {
  const color = tone === "red" ? "text-red-600" : tone === "amber" ? "text-amber-700" : "text-[#0F5132]";
  return (
    <Card>
      <p className="text-xs text-slate-500 font-medium">{label}</p>
      <p className={`text-2xl font-bold mt-1 ${color}`}>{value}</p>
      {hint && <p className="text-[11px] text-slate-500 mt-1">{hint}</p>}
    </Card>
  );
}
