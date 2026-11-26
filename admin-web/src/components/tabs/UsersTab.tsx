"use client";

import React, { useCallback, useEffect, useState } from "react";
import { KeyRound } from "lucide-react";
import { friendlyError, ROLE_LABELS, server, shortDate } from "@/lib/server";
import { Btn, Card, Empty, ErrorBox, Loading, Pill, SectionTitle, inputCls } from "@/components/ui";
import type { TabProps } from "@/components/SparcAdminPortal";

interface UserRow {
  id: string;
  name: string;
  email: string | null;
  department: string;
  institution?: string;
  role: string;
  base_role: string | null;
  gamification_points: number;
  wallet_balance: number;
  created_at: string;
}

interface ResetReqRow {
  id: string;
  email: string;
  user_id: string | null;
  contact_note: string;
  created_at: string;
}

const ROLE_ORDER = ["member", "moderator", "supervisor", "sparc_rep", "admin"];

export default function UsersTab({ me, notify }: TabProps) {
  const [users, setUsers] = useState<UserRow[]>([]);
  const [resetReqs, setResetReqs] = useState<ResetReqRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState<string | null>(null);
  const [search, setSearch] = useState("");
  const [roleFilter, setRoleFilter] = useState("all");

  const load = useCallback(async () => {
    setLoading(true);
    setErr(null);
    try {
      const sb = server();
      let q = sb
        .from("profiles")
        .select("id,name,email,department,institution,role,base_role,gamification_points,wallet_balance,created_at")
        .order("created_at", { ascending: false })
        .limit(300);
      const s = search.trim();
      if (s) q = q.or(`name.ilike.%${s}%,email.ilike.%${s}%,department.ilike.%${s}%`);
      if (roleFilter !== "all") q = q.eq("base_role", roleFilter);
      const [{ data, error }, rr] = await Promise.all([
        q,
        sb
          .from("password_reset_requests")
          .select("id,email,user_id,contact_note,created_at")
          .eq("status", "pending")
          .order("created_at", { ascending: false }),
      ]);
      if (error) throw error;
      setUsers((data ?? []) as UserRow[]);
      setResetReqs((rr.data ?? []) as ResetReqRow[]);
    } catch (e) {
      setErr(friendlyError(e));
    } finally {
      setLoading(false);
    }
  }, [search, roleFilter]);

  useEffect(() => {
    const t = window.setTimeout(load, 300);
    return () => window.clearTimeout(t);
  }, [load]);

  const dismissReq = async (id: string) => {
    await server().from("password_reset_requests").update({ status: "dismissed" }).eq("id", id);
    load();
  };

  return (
    <div className="space-y-4">
      <SectionTitle onRefresh={load} loading={loading}>
        🧑‍🤝‍🧑 ইউজার, রোল ও পাসওয়ার্ড রিসেট ম্যানেজার
      </SectionTitle>

      {resetReqs.length > 0 && (
        <Card className="border-amber-300 bg-amber-50/70 space-y-2">
          <h3 className="text-sm font-bold text-amber-950 flex items-center gap-1.5">
            <KeyRound className="w-4 h-4" /> পেন্ডিং পাসওয়ার্ড রিসেট অনুরোধ ({resetReqs.length})
          </h3>
          {resetReqs.map((r) => (
            <div key={r.id} className="bg-white border border-amber-200 rounded-lg px-3 py-2 flex flex-wrap justify-between items-center text-xs gap-2">
              <div>
                <strong>{r.email}</strong>
                {r.contact_note && <span className="text-slate-600"> • নোট: {r.contact_note}</span>}
                <span className="text-slate-400"> • {shortDate(r.created_at)}</span>
              </div>
              <div className="flex gap-2">
                <Btn
                  variant="ghost"
                  onClick={() => {
                    setSearch(r.email);
                  }}
                >
                  ইউজার খুঁজুন
                </Btn>
                <Btn variant="ghost" onClick={() => dismissReq(r.id)}>
                  সমাধান হয়েছে
                </Btn>
              </div>
            </div>
          ))}
        </Card>
      )}

      <Card className="text-xs text-slate-700 space-y-1">
        <p>
          <strong>মূল রোল:</strong> 📖 গবেষক (Researcher) • ⭐ সার্কেল লিড • 🎓 মেন্টর • 🤝 SPARC প্রতিনিধি • 🛡️ অ্যাডমিন
        </p>
        <p className="text-slate-500">
          রোল পরিবর্তন ও পাসওয়ার্ড রিসেট সাথে সাথে অ্যাপে কার্যকর হবে।
        </p>
      </Card>

      <div className="flex flex-wrap gap-2">
        <input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="নাম, ইমেইল বা বিভাগ…" className={`${inputCls} w-64`} />
        <select value={roleFilter} onChange={(e) => setRoleFilter(e.target.value)} className={`${inputCls} w-48`}>
          <option value="all">সব রোল</option>
          {ROLE_ORDER.map((r) => (
            <option key={r} value={r}>
              {ROLE_LABELS[r].emoji} {ROLE_LABELS[r].bn}
            </option>
          ))}
        </select>
      </div>

      <ErrorBox message={err} />
      {loading && users.length === 0 && <Loading />}
      {!loading && users.length === 0 && <Empty>কোনো ইউজার পাওয়া যায়নি।</Empty>}
      <div className="space-y-2">
        {users.map((u) => (
          <UserLine key={u.id} u={u} isMe={u.id === me.id} notify={notify} onSaved={load} />
        ))}
      </div>
    </div>
  );
}

function UserLine({ u, isMe, notify, onSaved }: { u: UserRow; isMe: boolean; notify: TabProps["notify"]; onSaved: () => void }) {
  const realRole = u.base_role ?? u.role;
  const [role, setRole] = useState(realRole);
  const [busy, setBusy] = useState(false);
  const [resetBusy, setResetBusy] = useState(false);

  const save = async () => {
    if (role === "admin" && !window.confirm(`${u.name}-কে অ্যাডমিন বানাতে চান?`)) return;
    setBusy(true);
    try {
      const { error } = await server().rpc("admin_manage_roles_and_orders_rpc", { p_target_user_id: u.id, p_new_role: role });
      if (error) throw error;
      notify(`${u.name}: রোল এখন ${ROLE_LABELS[role]?.bn ?? role}।`);
      onSaved();
    } catch (e) {
      notify(friendlyError(e), "err");
    } finally {
      setBusy(false);
    }
  };

  const resetPassword = async () => {
    const nextPass = window.prompt(`${u.name} (${u.email ?? "ইমেইল"})-এর নতুন পাসওয়ার্ড দিন (কমপক্ষে ৬ অক্ষর):`, "sparc2026");
    if (!nextPass) return;
    if (nextPass.length < 6) {
      notify("পাসওয়ার্ড কমপক্ষে ৬ অক্ষরের হতে হবে।", "err");
      return;
    }
    setResetBusy(true);
    try {
      const { data, error } = await server().functions.invoke("auth-register", {
        body: {
          action: "admin_reset_password",
          target_user_id: u.id,
          new_password: nextPass,
        },
      });
      if (error) throw error;
      if (data?.error) throw new Error(data.error);
      notify(`✓ ${u.name}-এর পাসওয়ার্ড আপডেট হয়েছে। নতুন পাসওয়ার্ড ইউজারকে জানিয়ে দিন।`);
      onSaved();
    } catch (e) {
      notify(friendlyError(e), "err");
    } finally {
      setResetBusy(false);
    }
  };

  return (
    <div className="bg-white border border-slate-200 rounded-xl px-4 py-3 flex flex-wrap items-center justify-between gap-3">
      <div className="min-w-0">
        <p className="font-semibold text-sm">
          {u.name} {isMe && <Pill tone="blue">আপনি</Pill>}{" "}
          <Pill tone={realRole === "admin" ? "red" : realRole === "supervisor" ? "green" : "slate"}>
            {ROLE_LABELS[realRole]?.emoji} {ROLE_LABELS[realRole]?.bn ?? realRole}
          </Pill>
          {u.role !== realRole && <Pill tone="amber">প্রিভিউ: {ROLE_LABELS[u.role]?.bn ?? u.role}</Pill>}
        </p>
        <p className="text-[11px] text-slate-500 truncate">
          {u.email ?? "—"} • {[u.institution, u.department].filter(Boolean).join(" — ")} • {u.gamification_points} পয়েন্ট • ওয়ালেট ৳{Number(u.wallet_balance).toFixed(0)}
        </p>
      </div>
      <div className="flex flex-wrap items-center gap-2">
        <select value={role} onChange={(e) => setRole(e.target.value)} disabled={isMe} className={`${inputCls} py-1.5 text-xs w-40`}>
          {ROLE_ORDER.map((r) => (
            <option key={r} value={r}>
              {ROLE_LABELS[r].emoji} {ROLE_LABELS[r].bn}
            </option>
          ))}
        </select>
        <Btn onClick={save} busy={busy} disabled={isMe || role === realRole}>
          রোল সংরক্ষণ
        </Btn>
        <Btn variant="ghost" busy={resetBusy} onClick={resetPassword}>
          <KeyRound className="w-3.5 h-3.5" /> পাসওয়ার্ড রিসেট
        </Btn>
      </div>
    </div>
  );
}
