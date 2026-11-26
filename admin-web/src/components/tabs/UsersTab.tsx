"use client";

import React, { useCallback, useEffect, useState } from "react";
import { friendlyError, ROLE_LABELS, server } from "@/lib/server";
import { Btn, Card, Empty, ErrorBox, Loading, Pill, SectionTitle, inputCls } from "@/components/ui";
import type { TabProps } from "@/components/SparcAdminPortal";

interface UserRow {
  id: string;
  name: string;
  email: string | null;
  department: string;
  role: string;
  base_role: string | null;
  gamification_points: number;
  wallet_balance: number;
  created_at: string;
}

const ROLE_ORDER = ["member", "moderator", "supervisor", "sparc_rep", "admin"];

export default function UsersTab({ me, notify }: TabProps) {
  const [users, setUsers] = useState<UserRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState<string | null>(null);
  const [search, setSearch] = useState("");
  const [roleFilter, setRoleFilter] = useState("all");

  const load = useCallback(async () => {
    setLoading(true);
    setErr(null);
    try {
      let q = server()
        .from("profiles")
        .select("id,name,email,department,role,base_role,gamification_points,wallet_balance,created_at")
        .order("created_at", { ascending: false })
        .limit(300);
      const s = search.trim();
      if (s) q = q.or(`name.ilike.%${s}%,email.ilike.%${s}%,department.ilike.%${s}%`);
      if (roleFilter !== "all") q = q.eq("base_role", roleFilter);
      const { data, error } = await q;
      if (error) throw error;
      setUsers((data ?? []) as UserRow[]);
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

  return (
    <div className="space-y-4">
      <SectionTitle onRefresh={load} loading={loading}>
        🧑‍🤝‍🧑 ইউজার ও রোল ম্যানেজার
      </SectionTitle>
      <Card className="text-xs text-slate-700 space-y-1">
        <p>
          <strong>মূল রোল:</strong> 📖 গবেষক (Researcher) • ⭐ সার্কেল লিড • 🎓 মেন্টর (নম্বর দেন ও তত্ত্বাবধান করেন) • 🤝 SPARC প্রতিনিধি (বই অর্ডার) • 🛡️ অ্যাডমিন
        </p>
        <p className="text-slate-500">
          সার্কেল লিড সাধারণত সার্কেলের সদস্যদের ভোটে নির্বাচিত হন; এখানে শুধু স্থায়ী সিস্টেম রোল পরিবর্তন হয়। রোল পরিবর্তন সাথে সাথে অ্যাপে কার্যকর হবে।
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

  const save = async () => {
    if (role === "admin" && !window.confirm(`${u.name}-কে অ্যাডমিন বানাতে চান? অ্যাডমিন সব কিছু নিয়ন্ত্রণ করতে পারেন।`)) return;
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
          {u.email ?? "—"} • {u.department} • {u.gamification_points} পয়েন্ট • ওয়ালেট ৳{Number(u.wallet_balance).toFixed(0)}
        </p>
      </div>
      <div className="flex items-center gap-2">
        <select value={role} onChange={(e) => setRole(e.target.value)} disabled={isMe} className={`${inputCls} py-1.5 text-xs w-44`}>
          {ROLE_ORDER.map((r) => (
            <option key={r} value={r}>
              {ROLE_LABELS[r].emoji} {ROLE_LABELS[r].bn}
            </option>
          ))}
        </select>
        <Btn onClick={save} busy={busy} disabled={isMe || role === realRole}>
          রোল সংরক্ষণ
        </Btn>
      </div>
    </div>
  );
}
