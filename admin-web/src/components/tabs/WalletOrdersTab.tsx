"use client";

import React, { useCallback, useEffect, useState } from "react";
import { Briefcase, CreditCard, Ticket, Truck } from "lucide-react";
import { friendlyError, server, shortDate } from "@/lib/server";
import { Btn, Card, Empty, ErrorBox, Loading, Pill, SectionTitle, inputCls } from "@/components/ui";
import type { TabProps } from "@/components/SparcAdminPortal";

interface WalletRow {
  id: string;
  user_id: string;
  tx_type: string;
  amount_bdt: number;
  status: "pending" | "verified" | "rejected";
  gateway_ref: string | null;
  manual_trx_id: string | null;
  payment_phone: string | null;
  notes: string | null;
  created_at: string;
}

interface OrderRow {
  id: string;
  user_id: string;
  book_id: string;
  order_mode: string;
  delivery_address: string;
  contact_phone: string;
  amount_bdt: number;
  paid_via_wallet: boolean;
  admin_notes: string | null;
  status: string;
  created_at: string;
}

interface CouponRow {
  code: string;
  credit_amount_bdt: number;
  max_uses: number;
  used_count: number;
  description: string;
  is_active: boolean;
}

interface SupervisionRow {
  id: string;
  name: string;
  supervisor_id: string | null;
  agreed_sessions: number;
  agreed_fee_bdt: number;
  payment_status: string;
}

const ORDER_STATUSES = ["pending", "approved", "dispatched", "delivered", "cancelled"];

export default function WalletOrdersTab({ me, notify }: TabProps) {
  const isAdmin = me.access === "admin";
  const [wallets, setWallets] = useState<WalletRow[]>([]);
  const [orders, setOrders] = useState<OrderRow[]>([]);
  const [coupons, setCoupons] = useState<CouponRow[]>([]);
  const [supervisions, setSupervisions] = useState<SupervisionRow[]>([]);
  const [mentorSharePct, setMentorSharePct] = useState(70);
  const [people, setPeople] = useState<Record<string, string>>({});
  const [books, setBooks] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);

  // Coupon creator form
  const [couponCode, setCouponCode] = useState("");
  const [couponAmt, setCouponAmt] = useState(200);
  const [couponUses, setCouponUses] = useState(25);
  const [couponDesc, setCouponDesc] = useState("SPARC স্কলারশিপ ভাউচার");
  const [couponBusy, setCouponBusy] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setErr(null);
    try {
      const sb = server();
      const [wq, oq, sq, cfgQ, cq] = await Promise.all([
        sb
          .from("wallet_transactions")
          .select("id,user_id,tx_type,amount_bdt,status,gateway_ref,manual_trx_id,payment_phone,notes,created_at")
          .gt("amount_bdt", 0)
          .order("created_at", { ascending: false })
          .limit(100),
        sb
          .from("physical_book_orders")
          .select("id,user_id,book_id,order_mode,delivery_address,contact_phone,amount_bdt,paid_via_wallet,admin_notes,status,created_at")
          .order("created_at", { ascending: false })
          .limit(100),
        sb
          .from("research_groups")
          .select("id,name,supervisor_id,agreed_sessions,agreed_fee_bdt,payment_status")
          .gt("agreed_fee_bdt", 0)
          .order("created_at", { ascending: false }),
        sb.from("app_customization").select("mentor_honorarium_pct").eq("id", "global").maybeSingle(),
        isAdmin
          ? sb.from("scholarship_coupons").select("code,credit_amount_bdt,max_uses,used_count,description,is_active").order("created_at", { ascending: false })
          : Promise.resolve({ data: [], error: null }),
      ]);
      if (wq.error) throw wq.error;
      if (oq.error) throw oq.error;
      const ws = (wq.data ?? []) as WalletRow[];
      const os = (oq.data ?? []) as OrderRow[];
      const sups = (sq.data ?? []) as SupervisionRow[];
      setWallets(ws);
      setOrders(os);
      setSupervisions(sups);
      setCoupons((cq.data ?? []) as CouponRow[]);
      setMentorSharePct(Number((cfgQ.data as { mentor_honorarium_pct?: number } | null)?.mentor_honorarium_pct ?? 70));

      const uids = Array.from(
        new Set([...ws.map((w) => w.user_id), ...os.map((o) => o.user_id), ...(sups.map((s) => s.supervisor_id).filter(Boolean) as string[])])
      );
      if (uids.length) {
        const { data: ps } = await sb.from("profiles").select("id,name").in("id", uids);
        setPeople(Object.fromEntries((ps ?? []).map((p) => [p.id, p.name])));
      }
      const bids = Array.from(new Set(os.map((o) => o.book_id)));
      if (bids.length) {
        const { data: bs } = await sb.from("books").select("id,title").in("id", bids);
        setBooks(Object.fromEntries((bs ?? []).map((b) => [b.id, b.title])));
      }
    } catch (e) {
      setErr(friendlyError(e));
    } finally {
      setLoading(false);
    }
  }, [isAdmin]);

  useEffect(() => {
    load();
  }, [load]);

  const actWallet = async (w: WalletRow, next: "verified" | "rejected") => {
    setBusyId(w.id);
    try {
      const { error } = await server().rpc("admin_manage_roles_and_orders_rpc", { p_wallet_tx_id: w.id, p_wallet_tx_status: next });
      if (error) throw error;
      notify(next === "verified" ? `৳${w.amount_bdt} অনুমোদিত হয়েছে ও ওয়ালেটে যুক্ত হয়েছে।` : "TrxID বাতিল করা হয়েছে।");
      load();
    } catch (e) {
      notify(friendlyError(e), "err");
    } finally {
      setBusyId(null);
    }
  };

  const actOrder = async (o: OrderRow, next: string) => {
    setBusyId(o.id);
    try {
      const { error } = await server().rpc("admin_manage_roles_and_orders_rpc", { p_book_order_id: o.id, p_book_order_status: next });
      if (error) throw error;
      notify(`বই অর্ডার স্ট্যাটাস: ${next}`);
      load();
    } catch (e) {
      notify(friendlyError(e), "err");
    } finally {
      setBusyId(null);
    }
  };

  const createCoupon = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!couponCode.trim()) return;
    setCouponBusy(true);
    try {
      const { error } = await server().rpc("admin_upsert_scholarship_coupon_rpc", {
        p_code: couponCode.trim().toUpperCase(),
        p_credit_amount_bdt: couponAmt,
        p_max_uses: couponUses,
        p_description: couponDesc.trim(),
        p_is_active: true,
      });
      if (error) throw error;
      setCouponCode("");
      notify("✓ নতুন স্কলারশিপ ভাউচার কোড তৈরি হয়েছে!");
      load();
    } catch (er) {
      notify(friendlyError(er), "err");
    } finally {
      setCouponBusy(false);
    }
  };

  const toggleCoupon = async (c: CouponRow) => {
    await server().rpc("admin_upsert_scholarship_coupon_rpc", {
      p_code: c.code,
      p_credit_amount_bdt: c.credit_amount_bdt,
      p_max_uses: c.max_uses,
      p_description: c.description,
      p_is_active: !c.is_active,
    });
    load();
  };

  return (
    <div className="space-y-6">
      <SectionTitle onRefresh={load} loading={loading}>
        💳 ওয়ালেট TrxID, বই অর্ডার, স্কলারশিপ কুপন ও মেন্টর সম্মানী
      </SectionTitle>
      <ErrorBox message={err} />
      {loading && wallets.length === 0 && orders.length === 0 && <Loading />}

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <Card className="space-y-3">
          <h3 className="text-sm font-bold flex items-center gap-2">
            <CreditCard className="w-4 h-4 text-[#0F5132]" /> ওয়ালেট টপ-আপ ও ম্যানুয়াল TrxID যাচাই
          </h3>
          {!isAdmin && <p className="text-xs text-slate-500">ওয়ালেট অনুমোদন শুধুমাত্র অ্যাডমিন করতে পারেন।</p>}
          {wallets.length === 0 && !loading && <Empty>কোনো টপ-আপ রিকোয়েস্ট নেই।</Empty>}
          {wallets.map((w) => (
            <div key={w.id} className="bg-slate-50 border border-slate-200 rounded-lg p-3 flex flex-wrap justify-between items-center gap-2">
              <div className="text-xs space-y-0.5">
                <p className="font-semibold text-sm">
                  {people[w.user_id] ?? "ইউজার"} — <span className="text-[#0F5132]">৳{Number(w.amount_bdt).toFixed(0)}</span>
                </p>
                <p className="text-slate-700">
                  TrxID: <strong className="font-mono bg-white px-1.5 py-0.5 rounded border">{w.manual_trx_id || w.gateway_ref || "—"}</strong>
                  {w.payment_phone ? ` • প্রেরক: ${w.payment_phone}` : ""}
                </p>
                {w.notes && <p className="text-slate-500">{w.notes}</p>}
                <p className="text-slate-400">{shortDate(w.created_at)}</p>
              </div>
              {w.status === "pending" && isAdmin ? (
                <div className="flex gap-1.5">
                  <Btn busy={busyId === w.id} onClick={() => actWallet(w, "verified")}>
                    অনুমোদন
                  </Btn>
                  <Btn variant="danger" busy={busyId === w.id} onClick={() => actWallet(w, "rejected")}>
                    বাতিল
                  </Btn>
                </div>
              ) : (
                <Pill tone={w.status === "verified" ? "green" : w.status === "rejected" ? "red" : "amber"}>{w.status}</Pill>
              )}
            </div>
          ))}
        </Card>

        <Card className="space-y-3">
          <h3 className="text-sm font-bold flex items-center gap-2">
            <Truck className="w-4 h-4 text-[#0F5132]" /> হার্ডকপি বই অর্ডার
          </h3>
          {orders.length === 0 && !loading && <Empty>কোনো বই অর্ডার নেই।</Empty>}
          {orders.map((o) => (
            <div key={o.id} className="bg-slate-50 border border-slate-200 rounded-lg p-3 flex flex-wrap justify-between items-start gap-2">
              <div className="text-xs space-y-0.5">
                <p className="font-semibold text-sm">
                  {people[o.user_id] ?? "ইউজার"} •{" "}
                  {o.order_mode === "purchase" ? `ক্রয় ৳${Number(o.amount_bdt).toFixed(0)}` : `ধার (জামানত ৳${Number(o.amount_bdt).toFixed(0)})`}{" "}
                  <Pill tone={o.paid_via_wallet ? "green" : "amber"}>
                    {o.paid_via_wallet ? "ওয়ালেট পরিশোধিত" : "COD / TrxID"}
                  </Pill>
                </p>
                <p className="text-slate-800 font-medium">{books[o.book_id] ?? "বই"}</p>
                {o.admin_notes && <p className="text-emerald-800 font-medium">{o.admin_notes}</p>}
                <p className="text-slate-500">
                  📍 {o.delivery_address} • 📞 {o.contact_phone} • {shortDate(o.created_at)}
                </p>
              </div>
              <select
                value={o.status}
                disabled={busyId === o.id}
                onChange={(e) => actOrder(o, e.target.value)}
                className={`${inputCls} w-36 py-1 text-xs font-semibold`}
              >
                {ORDER_STATUSES.map((s) => (
                  <option key={s} value={s}>
                    {s}
                  </option>
                ))}
              </select>
            </div>
          ))}
        </Card>
      </div>

      {isAdmin && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <Card className="space-y-3">
            <h3 className="text-sm font-bold flex items-center gap-2">
              <Ticket className="w-4 h-4 text-[#0F5132]" /> স্কলারশিপ ও ওয়ার্কশপ ভাউচার কোড
            </h3>
            <form onSubmit={createCoupon} className="grid grid-cols-2 gap-2">
              <input
                value={couponCode}
                onChange={(e) => setCouponCode(e.target.value.toUpperCase())}
                placeholder="কোড (যেমন FELLOW200)"
                className={`${inputCls} font-mono`}
              />
              <input
                type="number"
                min={10}
                value={couponAmt}
                onChange={(e) => setCouponAmt(Number(e.target.value) || 100)}
                placeholder="ক্রেডিট ৳"
                className={inputCls}
              />
              <input
                type="number"
                min={1}
                value={couponUses}
                onChange={(e) => setCouponUses(Number(e.target.value) || 10)}
                placeholder="সর্বোচ্চ ব্যবহার সংখ্যা"
                className={inputCls}
              />
              <input
                value={couponDesc}
                onChange={(e) => setCouponDesc(e.target.value)}
                placeholder="বিবরণ"
                className={inputCls}
              />
              <Btn type="submit" busy={couponBusy} className="col-span-2 py-2">
                + নতুন স্কলারশিপ কোড তৈরি করুন
              </Btn>
            </form>
            <div className="space-y-1.5">
              {coupons.map((c) => (
                <div key={c.code} className="bg-slate-50 border border-slate-200 rounded-lg px-3 py-2 flex justify-between items-center text-xs">
                  <div>
                    <strong className="font-mono">{c.code}</strong> — ৳{Number(c.credit_amount_bdt).toFixed(0)} ({c.used_count}/{c.max_uses} ব্যবহৃত)
                    <p className="text-slate-500">{c.description}</p>
                  </div>
                  <Btn variant="ghost" onClick={() => toggleCoupon(c)}>
                    {c.is_active ? "বন্ধ করুন" : "চালু করুন"}
                  </Btn>
                </div>
              ))}
            </div>
          </Card>

          <Card className="space-y-3">
            <h3 className="text-sm font-bold flex items-center gap-2">
              <Briefcase className="w-4 h-4 text-[#0F5132]" /> মেন্টর সম্মানী হিসাব ({mentorSharePct}% মেন্টর / {100 - mentorSharePct}% SPARC)
            </h3>
            {supervisions.length === 0 ? (
              <Empty>এখনো কোনো পেইড রিসার্চ সুপারভিশন চুক্তি হয়নি।</Empty>
            ) : (
              supervisions.map((s) => {
                const fee = Number(s.agreed_fee_bdt);
                const mentorAmt = Math.round((fee * mentorSharePct) / 100);
                const sparcAmt = fee - mentorAmt;
                return (
                  <div key={s.id} className="bg-slate-50 border border-slate-200 rounded-lg p-3 text-xs space-y-1">
                    <div className="flex justify-between">
                      <strong>{s.name}</strong>
                      <Pill tone={s.payment_status === "paid_via_wallet" ? "green" : "amber"}>
                        {s.payment_status === "paid_via_wallet" ? "পরিশোধিত" : "বকেয়া"}
                      </Pill>
                    </div>
                    <p className="text-slate-600">
                      মেন্টর: <strong>{s.supervisor_id ? people[s.supervisor_id] ?? "—" : "নিয়োগ বাকি"}</strong> • {s.agreed_sessions} সেশন • মোট ৳{fee}
                    </p>
                    <p className="font-semibold text-[#0F5132]">
                      মেন্টর প্রাপ্য: ৳{mentorAmt} • SPARC তহবিল: ৳{sparcAmt}
                    </p>
                  </div>
                );
              })
            )}
          </Card>
        </div>
      )}
    </div>
  );
}
