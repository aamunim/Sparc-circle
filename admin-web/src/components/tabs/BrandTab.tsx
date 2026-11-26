"use client";

import React, { useCallback, useEffect, useState } from "react";
import { friendlyError, server } from "@/lib/server";
import { Btn, Card, ErrorBox, Loading, Pill, SectionTitle, inputCls } from "@/components/ui";
import type { TabProps } from "@/components/SparcAdminPortal";

interface BrandRow {
  app_title: string;
  app_tagline_bn: string;
  app_tagline_en: string;
  splash_quote_bn: string;
  splash_quote_en: string;
  announcement_banner_bn: string;
  announcement_banner_en: string;
  show_announcement_banner: boolean;
  bkash_merchant_number: string;
  nagad_merchant_number: string;
  rocket_merchant_number: string;
  default_supervision_fee_bdt: number;
  borrow_deposit_bdt: number;
  min_topup_bdt: number;
  online_gateway_enabled: boolean;
  payment_instructions_bn: string;
  payment_instructions_en: string;
}

export default function BrandTab({ notify }: TabProps) {
  const [form, setForm] = useState<BrandRow | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setErr(null);
    try {
      const { data, error } = await server().from("app_customization").select("*").eq("id", "global").single();
      if (error) throw error;
      const d = data as Partial<BrandRow>;
      setForm({
        app_title: d.app_title ?? "SPARC Study Circle",
        app_tagline_bn: d.app_tagline_bn ?? "",
        app_tagline_en: d.app_tagline_en ?? "",
        splash_quote_bn: d.splash_quote_bn ?? "",
        splash_quote_en: d.splash_quote_en ?? "",
        announcement_banner_bn: d.announcement_banner_bn ?? "",
        announcement_banner_en: d.announcement_banner_en ?? "",
        show_announcement_banner: d.show_announcement_banner ?? true,
        bkash_merchant_number: d.bkash_merchant_number ?? "01700-000000",
        nagad_merchant_number: d.nagad_merchant_number ?? "01800-000000",
        rocket_merchant_number: d.rocket_merchant_number ?? "01900-000000",
        default_supervision_fee_bdt: Number(d.default_supervision_fee_bdt ?? 300),
        borrow_deposit_bdt: Number(d.borrow_deposit_bdt ?? 150),
        min_topup_bdt: Number(d.min_topup_bdt ?? 200),
        online_gateway_enabled: Boolean(d.online_gateway_enabled ?? false),
        payment_instructions_bn:
          d.payment_instructions_bn ??
          "উপরের নম্বরে সেন্ড মানি / পেমেন্ট করে নিচের ঘরে প্রেরকের নম্বর ও TrxID জমা দিন।",
        payment_instructions_en:
          d.payment_instructions_en ??
          "Send money to the number above, then submit your sender phone number and TrxID below.",
      });
    } catch (e) {
      setErr(friendlyError(e));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const set = <K extends keyof BrandRow>(k: K, v: BrandRow[K]) => setForm((f) => (f ? { ...f, [k]: v } : f));

  const save = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form) return;
    setBusy(true);
    try {
      const { error } = await server().rpc("upsert_app_customization_rpc", {
        p_app_title: form.app_title,
        p_app_tagline_bn: form.app_tagline_bn,
        p_app_tagline_en: form.app_tagline_en,
        p_splash_quote_bn: form.splash_quote_bn,
        p_splash_quote_en: form.splash_quote_en,
        p_announcement_bn: form.announcement_banner_bn,
        p_announcement_en: form.announcement_banner_en,
        p_show_announcement: form.show_announcement_banner,
        p_bkash_number: form.bkash_merchant_number,
        p_nagad_number: form.nagad_merchant_number,
        p_rocket_number: form.rocket_merchant_number,
        p_supervision_fee_bdt: Number(form.default_supervision_fee_bdt),
        p_borrow_deposit_bdt: Number(form.borrow_deposit_bdt),
        p_min_topup_bdt: Number(form.min_topup_bdt),
        p_online_gateway_enabled: form.online_gateway_enabled,
        p_payment_instructions_bn: form.payment_instructions_bn,
        p_payment_instructions_en: form.payment_instructions_en,
      });
      if (error) throw error;
      notify("ব্র্যান্ড, নোটিশ ও পেমেন্ট সেটিংস সংরক্ষিত হয়েছে — অ্যাপে সরাসরি প্রতিফলিত হবে।");
    } catch (er) {
      notify(friendlyError(er), "err");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="space-y-4">
      <SectionTitle onRefresh={load} loading={loading}>
        🏛️ ব্র্যান্ড, নোটিশ ব্যানার ও পেমেন্ট সেটিংস
      </SectionTitle>
      <ErrorBox message={err} />
      {loading && !form && <Loading />}
      {form && (
        <form onSubmit={save} className="space-y-4">
          <Card className="space-y-3">
            <h3 className="text-sm font-bold">ব্র্যান্ড ও স্লোগান</h3>
            <Field label="অ্যাপ শিরোনাম">
              <input value={form.app_title} onChange={(e) => set("app_title", e.target.value)} className={inputCls} />
            </Field>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
              <Field label="ট্যাগলাইন (বাংলা)">
                <input value={form.app_tagline_bn} onChange={(e) => set("app_tagline_bn", e.target.value)} className={inputCls} />
              </Field>
              <Field label="ট্যাগলাইন (English)">
                <input value={form.app_tagline_en} onChange={(e) => set("app_tagline_en", e.target.value)} className={inputCls} />
              </Field>
              <Field label="স্প্ল্যাশ উদ্ধৃতি (বাংলা)">
                <input value={form.splash_quote_bn} onChange={(e) => set("splash_quote_bn", e.target.value)} className={inputCls} />
              </Field>
              <Field label="স্প্ল্যাশ উদ্ধৃতি (English)">
                <input value={form.splash_quote_en} onChange={(e) => set("splash_quote_en", e.target.value)} className={inputCls} />
              </Field>
            </div>
          </Card>

          <Card className="space-y-3">
            <div className="flex justify-between items-center">
              <h3 className="text-sm font-bold">📢 ঘোষণা ব্যানার</h3>
              <label className="flex items-center gap-2 text-xs font-semibold">
                <input type="checkbox" checked={form.show_announcement_banner} onChange={(e) => set("show_announcement_banner", e.target.checked)} />
                অ্যাপে দেখান
              </label>
            </div>
            <Field label="ব্যানার বার্তা (বাংলা)">
              <input value={form.announcement_banner_bn} onChange={(e) => set("announcement_banner_bn", e.target.value)} className={inputCls} />
            </Field>
            <Field label="ব্যানার বার্তা (English)">
              <input value={form.announcement_banner_en} onChange={(e) => set("announcement_banner_en", e.target.value)} className={inputCls} />
            </Field>
          </Card>

          <Card className="space-y-3">
            <div className="flex flex-wrap justify-between items-center gap-2">
              <h3 className="text-sm font-bold">💳 পেমেন্ট নম্বর, ফি ও গেটওয়ে মোড</h3>
              <Pill tone={form.online_gateway_enabled ? "green" : "amber"}>
                {form.online_gateway_enabled ? "🟢 অনলাইন গেটওয়ে চালু" : "🔒 ম্যানুয়াল TrxID যাচাই মোড"}
              </Pill>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
              <Field label="bKash নম্বর (Send Money / Payment)">
                <input value={form.bkash_merchant_number} onChange={(e) => set("bkash_merchant_number", e.target.value)} className={inputCls} />
              </Field>
              <Field label="Nagad নম্বর">
                <input value={form.nagad_merchant_number} onChange={(e) => set("nagad_merchant_number", e.target.value)} className={inputCls} />
              </Field>
              <Field label="Rocket নম্বর">
                <input value={form.rocket_merchant_number} onChange={(e) => set("rocket_merchant_number", e.target.value)} className={inputCls} />
              </Field>
              <Field label="সুপারভিশন ফি (৳/সেশন)">
                <input type="number" value={form.default_supervision_fee_bdt} onChange={(e) => set("default_supervision_fee_bdt", Number(e.target.value) || 0)} className={inputCls} />
              </Field>
              <Field label="বই ধার জামানত (৳)">
                <input type="number" value={form.borrow_deposit_bdt} onChange={(e) => set("borrow_deposit_bdt", Number(e.target.value) || 0)} className={inputCls} />
              </Field>
              <Field label="ন্যূনতম ওয়ালেট টপ-আপ (৳)">
                <input type="number" min={50} value={form.min_topup_bdt} onChange={(e) => set("min_topup_bdt", Number(e.target.value) || 50)} className={inputCls} />
              </Field>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
              <Field label="পেমেন্ট নির্দেশনা (বাংলা)">
                <input value={form.payment_instructions_bn} onChange={(e) => set("payment_instructions_bn", e.target.value)} className={inputCls} />
              </Field>
              <Field label="পেমেন্ট নির্দেশনা (English)">
                <input value={form.payment_instructions_en} onChange={(e) => set("payment_instructions_en", e.target.value)} className={inputCls} />
              </Field>
            </div>

            <div className="bg-amber-50 border border-amber-200 rounded-lg p-3 space-y-1">
              <label className="flex items-center gap-2 text-xs font-bold text-amber-950 cursor-pointer">
                <input
                  type="checkbox"
                  checked={form.online_gateway_enabled}
                  onChange={(e) => set("online_gateway_enabled", e.target.checked)}
                />
                অনলাইন পেমেন্ট গেটওয়ে (SSLCommerz / bKash PGW) চালু করুন
              </label>
              <p className="text-[11px] text-amber-900">
                গেটওয়ে API কী যুক্ত না করা পর্যন্ত এটি <strong>বন্ধ</strong> রাখুন। বন্ধ থাকলে অ্যাপে শুধুমাত্র যাচাইকৃত bKash/Nagad/Rocket TrxID পেমেন্ট দেখাবে এবং অনুমোদন ছাড়া কোনো ক্রেডিট যোগ হবে না।
              </p>
            </div>
          </Card>

          <Btn type="submit" busy={busy} className="px-6 py-2.5 text-sm">
            ✓ সব পরিবর্তন সার্ভারে সংরক্ষণ করুন
          </Btn>
        </form>
      )}
    </div>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="space-y-1">
      <label className="text-xs font-semibold text-slate-700">{label}</label>
      {children}
    </div>
  );
}
