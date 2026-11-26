"use client";

import React, { useCallback, useEffect, useState } from "react";
import { Video } from "lucide-react";
import { friendlyError, server } from "@/lib/server";
import { Btn, Card, Empty, ErrorBox, Loading, Pill, SectionTitle, inputCls } from "@/components/ui";
import type { TabProps } from "@/components/SparcAdminPortal";

interface CircleRow {
  id: string;
  name: string;
  book_id: string;
  created_by: string;
  status: string;
  min_members_required: number;
  max_members: number;
  supervisor_id: string | null;
  supervisor_assignment_type: string | null;
  circle_lead_id: string | null;
  is_private: boolean;
  invite_code: string | null;
  session_schedule_label: string;
  default_meeting_url: string;
  current_group_chapter: number;
}

export default function CirclesTab({ me, notify }: TabProps) {
  const [circles, setCircles] = useState<CircleRow[]>([]);
  const [books, setBooks] = useState<Record<string, string>>({});
  const [people, setPeople] = useState<Record<string, string>>({});
  const [memberCount, setMemberCount] = useState<Record<string, number>>({});
  const [mentors, setMentors] = useState<{ id: string; name: string }[]>([]);
  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState<string | null>(null);
  const [search, setSearch] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setErr(null);
    try {
      const sb = server();
      let q = sb
        .from("study_circles")
        .select(
          "id,name,book_id,created_by,status,min_members_required,max_members,supervisor_id,supervisor_assignment_type,circle_lead_id,is_private,invite_code,session_schedule_label,default_meeting_url,current_group_chapter"
        )
        .order("created_at", { ascending: false });
      if (me.access === "supervisor") q = q.eq("supervisor_id", me.id);
      const { data, error } = await q;
      if (error) throw error;
      const rows = (data ?? []) as CircleRow[];
      setCircles(rows);

      const bookIds = Array.from(new Set(rows.map((r) => r.book_id)));
      if (bookIds.length) {
        const { data: bs } = await sb.from("books").select("id,title").in("id", bookIds);
        setBooks(Object.fromEntries((bs ?? []).map((b) => [b.id, b.title])));
      }
      const ids = rows.map((r) => r.id);
      if (ids.length) {
        const { data: mem } = await sb.from("study_circle_members").select("circle_id,invite_status").in("circle_id", ids);
        const counts: Record<string, number> = {};
        (mem ?? []).forEach((m) => {
          if (m.invite_status === "joined") counts[m.circle_id] = (counts[m.circle_id] ?? 0) + 1;
        });
        setMemberCount(counts);
      }
      const personIds = Array.from(
        new Set(rows.flatMap((r) => [r.supervisor_id, r.circle_lead_id, r.created_by]).filter(Boolean) as string[])
      );
      const { data: ms } = await sb
        .from("profiles")
        .select("id,name")
        .or("base_role.eq.supervisor,base_role.eq.admin")
        .order("name");
      setMentors(ms ?? []);
      const map: Record<string, string> = Object.fromEntries((ms ?? []).map((m) => [m.id, m.name]));
      const missing = personIds.filter((id) => !map[id]);
      if (missing.length) {
        const { data: ps } = await sb.from("profiles").select("id,name").in("id", missing);
        (ps ?? []).forEach((p) => (map[p.id] = p.name));
      }
      setPeople(map);
    } catch (e) {
      setErr(friendlyError(e));
    } finally {
      setLoading(false);
    }
  }, [me]);

  useEffect(() => {
    load();
  }, [load]);

  const filtered = circles.filter(
    (c) => !search.trim() || c.name.toLowerCase().includes(search.toLowerCase()) || (books[c.book_id] ?? "").toLowerCase().includes(search.toLowerCase())
  );

  return (
    <div className="space-y-4">
      <SectionTitle
        onRefresh={load}
        loading={loading}
        right={<input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="সার্কেল বা বই খুঁজুন…" className={`${inputCls} w-56 py-1.5 text-xs`} />}
      >
        👥 স্টাডি সার্কেল {me.access === "supervisor" ? "(আমি মেন্টর)" : `(${circles.length})`}
      </SectionTitle>
      <ErrorBox message={err} />
      {loading && circles.length === 0 && <Loading />}
      {!loading && filtered.length === 0 && <Empty>কোনো সার্কেল পাওয়া যায়নি।</Empty>}
      {filtered.map((c) => (
        <CircleCard
          key={c.id}
          c={c}
          bookTitle={books[c.book_id] ?? "—"}
          members={memberCount[c.id] ?? 0}
          people={people}
          mentors={mentors}
          canAssign={me.access === "admin"}
          notify={notify}
          onSaved={load}
        />
      ))}
    </div>
  );
}

function CircleCard({
  c,
  bookTitle,
  members,
  people,
  mentors,
  canAssign,
  notify,
  onSaved,
}: {
  c: CircleRow;
  bookTitle: string;
  members: number;
  people: Record<string, string>;
  mentors: { id: string; name: string }[];
  canAssign: boolean;
  notify: TabProps["notify"];
  onSaved: () => void;
}) {
  const [mentor, setMentor] = useState(c.supervisor_id ?? "");
  const [minMembers, setMinMembers] = useState(c.min_members_required);
  const [busy, setBusy] = useState(false);
  const pct = Math.min(100, Math.round((members / Math.max(1, c.min_members_required)) * 100));

  const save = async () => {
    setBusy(true);
    try {
      const { error } = await server().rpc("admin_manage_roles_and_orders_rpc", {
        p_circle_id: c.id,
        p_mentor_id: mentor && mentor !== c.supervisor_id ? mentor : null,
        p_min_members: minMembers !== c.min_members_required ? minMembers : null,
      });
      if (error) throw error;
      notify(`${c.name}: মেন্টর/সদস্যসংখ্যা আপডেট হয়েছে।`);
      onSaved();
    } catch (e) {
      notify(friendlyError(e), "err");
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card className="space-y-3">
      <div className="flex flex-wrap justify-between items-start gap-3">
        <div>
          <h3 className="text-base font-bold">
            {c.name} {c.is_private && <Pill tone="blue">🔒 প্রাইভেট {c.invite_code ? `• ${c.invite_code}` : ""}</Pill>}
          </h3>
          <p className="text-xs text-slate-600">
            📚 {bookTitle} • চলতি অধ্যায় {c.current_group_chapter} • 🗓️ {c.session_schedule_label}
          </p>
        </div>
        <Pill tone={c.status === "active" ? "green" : c.status === "forming" ? "amber" : "slate"}>
          {c.status === "active" ? "সক্রিয়" : c.status === "forming" ? "গঠনাধীন" : "সম্পন্ন"}
        </Pill>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-2 text-xs">
        <div className="bg-slate-50 rounded-lg px-3 py-2">
          🎓 মেন্টর: <strong>{c.supervisor_id ? people[c.supervisor_id] ?? "—" : "নিয়োগ বাকি"}</strong>
          {c.supervisor_assignment_type === "admin_assigned" && <span className="text-slate-500"> (অ্যাডমিন নিযুক্ত)</span>}
        </div>
        <div className="bg-slate-50 rounded-lg px-3 py-2">
          ⭐ সার্কেল লিড (সদস্যদের ভোটে): <strong>{c.circle_lead_id ? people[c.circle_lead_id] ?? "—" : "—"}</strong>
        </div>
        <div className="bg-slate-50 rounded-lg px-3 py-2">
          🚀 উদ্যোক্তা: <strong>{people[c.created_by] ?? "—"}</strong>
        </div>
      </div>

      <div>
        <div className="w-full bg-slate-200 h-2 rounded-full overflow-hidden">
          <div className="bg-[#0F5132] h-full" style={{ width: `${pct}%` }} />
        </div>
        <p className="text-[11px] text-slate-600 mt-1">
          সদস্য {members} / ন্যূনতম {c.min_members_required} (সর্বোচ্চ {c.max_members})
        </p>
      </div>

      <div className="flex flex-wrap items-end gap-3">
        {canAssign && (
          <>
            <div className="space-y-1">
              <label className="text-[11px] font-semibold">মেন্টর নিয়োগ</label>
              <select value={mentor} onChange={(e) => setMentor(e.target.value)} className={`${inputCls} py-1.5 text-xs w-56`}>
                <option value="">— নির্বাচন করুন —</option>
                {mentors.map((m) => (
                  <option key={m.id} value={m.id}>
                    {m.name}
                  </option>
                ))}
              </select>
            </div>
            <div className="space-y-1">
              <label className="text-[11px] font-semibold">ন্যূনতম সদস্য</label>
              <input
                type="number"
                min={2}
                value={minMembers}
                onChange={(e) => setMinMembers(Math.max(2, Number(e.target.value) || 2))}
                className={`${inputCls} py-1.5 text-xs w-24`}
              />
            </div>
            <Btn onClick={save} busy={busy}>সংরক্ষণ</Btn>
          </>
        )}
        <a
          href={c.default_meeting_url}
          target="_blank"
          rel="noreferrer"
          className="ml-auto inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-emerald-700 text-white text-xs font-semibold"
        >
          <Video className="w-3.5 h-3.5" /> লাইভ সেশনে যোগ দিন
        </a>
      </div>
    </Card>
  );
}
