"use client";

import React, { useCallback, useEffect, useState } from "react";
import { Eye, EyeOff, KeyRound, ShieldCheck, Sparkles, Trash2 } from "lucide-react";
import { friendlyError, readServerConfig, server, shortDate } from "@/lib/server";
import { Btn, Card, ErrorBox, Loading, Pill, SectionTitle, inputCls } from "@/components/ui";
import type { TabProps } from "@/components/SparcAdminPortal";

interface VaultRow {
  gemini_api_key: string;
  gemini_model: string;
  ai_coach_enabled: boolean;
  gateway_environment: "sandbox" | "live";
  bkash_app_key: string;
  bkash_app_secret: string;
  sslcommerz_store_id: string;
  sslcommerz_store_passwd: string;
  custom_jitsi_server_url: string;
  resend_api_key: string;
  updated_at?: string | null;
}

const EMPTY_VAULT: VaultRow = {
  gemini_api_key: "",
  gemini_model: "gemini-2.5-flash",
  ai_coach_enabled: true,
  gateway_environment: "sandbox",
  bkash_app_key: "",
  bkash_app_secret: "",
  sslcommerz_store_id: "",
  sslcommerz_store_passwd: "",
  custom_jitsi_server_url: "https://meet.jit.si",
  resend_api_key: "",
  updated_at: null,
};

export default function ApiVaultTab({ notify }: TabProps) {
  const [form, setForm] = useState<VaultRow | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [testingAi, setTestingAi] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const [revealed, setRevealed] = useState<Record<string, boolean>>({});

  const load = useCallback(async () => {
    setLoading(true);
    setErr(null);
    try {
      const { data, error } = await server().rpc("get_admin_api_vault_rpc");
      if (error) throw error;
      const d = (data ?? {}) as Partial<VaultRow>;
      setForm({
        gemini_api_key: d.gemini_api_key ?? "",
        gemini_model: d.gemini_model || "gemini-2.5-flash",
        ai_coach_enabled: d.ai_coach_enabled ?? true,
        gateway_environment: d.gateway_environment === "live" ? "live" : "sandbox",
        bkash_app_key: d.bkash_app_key ?? "",
        bkash_app_secret: d.bkash_app_secret ?? "",
        sslcommerz_store_id: d.sslcommerz_store_id ?? "",
        sslcommerz_store_passwd: d.sslcommerz_store_passwd ?? "",
        custom_jitsi_server_url: d.custom_jitsi_server_url || "https://meet.jit.si",
        resend_api_key: d.resend_api_key ?? "",
        updated_at: d.updated_at ?? null,
      });
    } catch (e) {
      setErr(friendlyError(e));
      setForm((prev) => prev ?? EMPTY_VAULT);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const set = <K extends keyof VaultRow>(k: K, v: VaultRow[K]) =>
    setForm((f) => (f ? { ...f, [k]: v } : f));

  const toggleReveal = (field: string) =>
    setRevealed((r) => ({ ...r, [field]: !r[field] }));

  const save = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form) return;
    setBusy(true);
    try {
      const { data, error } = await server().rpc("upsert_admin_api_vault_rpc", {
        p_gemini_api_key: form.gemini_api_key.trim(),
        p_gemini_model: form.gemini_model,
        p_ai_coach_enabled: form.ai_coach_enabled,
        p_gateway_environment: form.gateway_environment,
        p_bkash_app_key: form.bkash_app_key.trim(),
        p_bkash_app_secret: form.bkash_app_secret.trim(),
        p_sslcommerz_store_id: form.sslcommerz_store_id.trim(),
        p_sslcommerz_store_passwd: form.sslcommerz_store_passwd.trim(),
        p_custom_jitsi_server_url: form.custom_jitsi_server_url.trim() || "https://meet.jit.si",
        p_resend_api_key: form.resend_api_key.trim(),
      });
      if (error) throw error;
      const saved = (data ?? {}) as Partial<VaultRow>;
      setForm((prev) => (prev ? { ...prev, updated_at: saved.updated_at ?? new Date().toISOString() } : prev));
      notify("সব API কী ও ইন্টিগ্রেশন নিরাপদভাবে সার্ভার ভল্টে সংরক্ষিত হয়েছে!");
    } catch (er) {
      notify(friendlyError(er), "err");
    } finally {
      setBusy(false);
    }
  };

  const testGeminiConnection = async () => {
    if (!form) return;
    const keyToTest = form.gemini_api_key.trim();
    if (!keyToTest) {
      notify("পরীক্ষা করার আগে Gemini API Key ঘরে কী বসান।", "err");
      return;
    }
    setTestingAi(true);
    try {
      const cfg = readServerConfig();
      const { data: sess } = await server().auth.getSession();
      const token = sess.session?.access_token ?? cfg.key;
      const resp = await fetch(`${cfg.url.replace(/\/+$/, "")}/functions/v1/ai-writing-assistant`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          apikey: cfg.key,
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({
          action: "ping_test",
          gemini_api_key: keyToTest,
          gemini_model: form.gemini_model,
        }),
      });
      const json = await resp.json();
      if (!resp.ok || !json.ok) {
        throw new Error(json.error || `HTTP ${resp.status}`);
      }
      notify(`✓ সংযোগ সফল! সক্রিয় মডেল: ${json.model}`);
    } catch (e) {
      notify(`Gemini সংযোগ ব্যর্থ: ${friendlyError(e)}`, "err");
    } finally {
      setTestingAi(false);
    }
  };

  const hasGemini = Boolean(form?.gemini_api_key?.trim());
  const hasGateway = Boolean(form?.bkash_app_key?.trim() || form?.sslcommerz_store_id?.trim());
  const hasResend = Boolean(form?.resend_api_key?.trim());

  return (
    <div className="space-y-4">
      <SectionTitle
        onRefresh={load}
        loading={loading}
        right={
          form?.updated_at ? (
            <span className="text-[11px] text-slate-500">সর্বশেষ সংরক্ষণ: {shortDate(form.updated_at)}</span>
          ) : null
        }
      >
        🔑 API ও সিস্টেম ইন্টিগ্রেশন ভল্ট
      </SectionTitle>

      {/* Security Banner */}
      <div className="bg-emerald-50 border border-emerald-200 rounded-xl p-4 flex items-start gap-3">
        <ShieldCheck className="w-5 h-5 text-emerald-700 shrink-0 mt-0.5" />
        <div className="text-xs text-emerald-950 space-y-1">
          <p className="font-bold">মোবাইল অ্যাপ থেকে সব গোপন API কী স্থায়ীভাবে এই ভল্টে স্থানান্তর করা হয়েছে</p>
          <p className="text-emerald-900">
            সাধারণ শিক্ষার্থী বা রিভিউয়ারের অ্যান্ড্রয়েড অ্যাপে কোনো API কী পাঠানো বা দেখানো হয় না। এখানে সংরক্ষিত কী শুধুমাত্র সার্ভারের ব্যাকএন্ড ফাংশন নিরাপদভাবে ব্যবহার করে।
          </p>
        </div>
      </div>

      <ErrorBox message={err} />
      {loading && !form && <Loading />}

      {form && (
        <>
          {/* Live Status Summary Strip */}
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
            <StatusTile
              title="🤖 Gemini AI Coach"
              badge={
                !form.ai_coach_enabled
                  ? { label: "বন্ধ রাখা হয়েছে", tone: "slate" }
                  : hasGemini
                  ? { label: "🟢 কী সংযুক্ত", tone: "green" }
                  : { label: "⚪ হিউরিস্টিক মোড", tone: "amber" }
              }
              sub={`মডেল: ${form.gemini_model}`}
            />
            <StatusTile
              title="💳 পেমেন্ট গেটওয়ে"
              badge={
                hasGateway
                  ? { label: form.gateway_environment === "live" ? "🟢 Live PGW" : "🟡 Sandbox PGW", tone: form.gateway_environment === "live" ? "green" : "amber" }
                  : { label: "🔒 ম্যানুয়াল TrxID", tone: "slate" }
              }
              sub={hasGateway ? "গেটওয়ে কী প্রস্তুত" : "কী ছাড়াই ম্যানুয়াল মোড সচল"}
            />
            <StatusTile
              title="🎥 লাইভ ভিডিও সার্ভার"
              badge={{ label: "🟢 অ্যাপে সিঙ্কড", tone: "blue" }}
              sub={form.custom_jitsi_server_url || "https://meet.jit.si"}
            />
            <StatusTile
              title="✉️ পাসওয়ার্ড রিসেট ইমেইল"
              badge={
                hasResend
                  ? { label: "🟢 Resend সক্রিয়", tone: "green" }
                  : { label: "⚪ ম্যানুয়াল রিসেট", tone: "slate" }
              }
              sub={hasResend ? "রিসেটে স্বয়ংক্রিয় ইমেইল যাবে" : "অ্যাডমিন ১-ক্লিক রিসেট সচল"}
            />
          </div>

          <form onSubmit={save} className="space-y-4">
            {/* Card 1: Google Gemini AI */}
            <Card className="space-y-3">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div>
                  <h3 className="text-sm font-bold flex items-center gap-1.5">
                    <Sparkles className="w-4 h-4 text-amber-500" />
                    ১. Google Gemini AI (রাইটিং কোচ ও অনুশীলনী সহায়তা)
                  </h3>
                  <p className="text-xs text-slate-500">
                    গবেষণা পাণ্ডুলিপির ভাষা, একাডেমিক টোন ও কাঠামো বিশ্লেষণের জন্য ব্যবহৃত হয়।
                  </p>
                </div>
                <label className="flex items-center gap-2 text-xs font-semibold cursor-pointer">
                  <input
                    type="checkbox"
                    checked={form.ai_coach_enabled}
                    onChange={(e) => set("ai_coach_enabled", e.target.checked)}
                    className="accent-emerald-700 w-4 h-4"
                  />
                  অ্যাপে AI পরামর্শ চালু রাখুন
                </label>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
                <div className="md:col-span-2">
                  <SecretField
                    label="Google Gemini API Key"
                    placeholder="AIzaSy..."
                    value={form.gemini_api_key}
                    revealed={Boolean(revealed.gemini)}
                    onToggleReveal={() => toggleReveal("gemini")}
                    onChange={(v) => set("gemini_api_key", v)}
                    onClear={() => set("gemini_api_key", "")}
                  />
                </div>
                <div className="space-y-1">
                  <label className="text-xs font-semibold text-slate-700">AI মডেল নির্বাচন</label>
                  <select
                    value={form.gemini_model}
                    onChange={(e) => set("gemini_model", e.target.value)}
                    className={`${inputCls} bg-white`}
                  >
                    <option value="gemini-2.5-flash">gemini-2.5-flash (দ্রুত ও সাশ্রয়ী)</option>
                    <option value="gemini-2.5-pro">gemini-2.5-pro (গভীর একাডেমিক বিশ্লেষণ)</option>
                  </select>
                </div>
              </div>

              <div>
                <Btn variant="ghost" onClick={testGeminiConnection} busy={testingAi}>
                  ⚡ Gemini সংযোগ পরীক্ষা করুন
                </Btn>
              </div>
            </Card>

            {/* Card 2: Payment Gateway Credentials */}
            <Card className="space-y-3">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div>
                  <h3 className="text-sm font-bold flex items-center gap-1.5">
                    <KeyRound className="w-4 h-4 text-emerald-700" />
                    ২. অনলাইন পেমেন্ট গেটওয়ে ক্রেডেনশিয়াল (bKash PGW & SSLCommerz)
                  </h3>
                  <p className="text-xs text-slate-500">
                    মার্চেন্ট API অনুমোদন পাওয়ার পর এখানে কী যুক্ত করে ‘ব্র্যান্ড ও নোটিশ’ ট্যাব থেকে গেটওয়ে সুইচ চালু করতে পারবেন।
                  </p>
                </div>
                <div className="inline-flex rounded-lg bg-slate-100 p-0.5 text-xs font-bold">
                  <button
                    type="button"
                    onClick={() => set("gateway_environment", "sandbox")}
                    className={`px-3 py-1 rounded-md transition ${
                      form.gateway_environment === "sandbox" ? "bg-amber-500 text-white" : "text-slate-600"
                    }`}
                  >
                    Sandbox (Test)
                  </button>
                  <button
                    type="button"
                    onClick={() => set("gateway_environment", "live")}
                    className={`px-3 py-1 rounded-md transition ${
                      form.gateway_environment === "live" ? "bg-emerald-700 text-white" : "text-slate-600"
                    }`}
                  >
                    Production (Live)
                  </button>
                </div>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                <SecretField
                  label="bKash PGW App / Merchant Key"
                  placeholder="bkash_app_key_..."
                  value={form.bkash_app_key}
                  revealed={Boolean(revealed.bkash_key)}
                  onToggleReveal={() => toggleReveal("bkash_key")}
                  onChange={(v) => set("bkash_app_key", v)}
                  onClear={() => set("bkash_app_key", "")}
                />
                <SecretField
                  label="bKash PGW App Secret"
                  placeholder="bkash_app_secret_..."
                  value={form.bkash_app_secret}
                  revealed={Boolean(revealed.bkash_secret)}
                  onToggleReveal={() => toggleReveal("bkash_secret")}
                  onChange={(v) => set("bkash_app_secret", v)}
                  onClear={() => set("bkash_app_secret", "")}
                />
                <SecretField
                  label="SSLCommerz Store ID"
                  placeholder="sparc_live_store..."
                  value={form.sslcommerz_store_id}
                  revealed={true}
                  hideEye
                  onToggleReveal={() => {}}
                  onChange={(v) => set("sslcommerz_store_id", v)}
                  onClear={() => set("sslcommerz_store_id", "")}
                />
                <SecretField
                  label="SSLCommerz Store Password"
                  placeholder="store_password..."
                  value={form.sslcommerz_store_passwd}
                  revealed={Boolean(revealed.ssl_pass)}
                  onToggleReveal={() => toggleReveal("ssl_pass")}
                  onChange={(v) => set("sslcommerz_store_passwd", v)}
                  onClear={() => set("sslcommerz_store_passwd", "")}
                />
              </div>
            </Card>

            {/* Card 3: Live Video & Automated Email */}
            <Card className="space-y-3">
              <div>
                <h3 className="text-sm font-bold">🎥 ৩. লাইভ ভিডিও সার্ভার ও অটোমেটেড ইমেইল সার্ভিস</h3>
                <p className="text-xs text-slate-500">
                  ভিডিও সার্ভারের ঠিকানা স্বয়ংক্রিয়ভাবে অ্যান্ড্রয়েড অ্যাপের সব স্টাডি সার্কেলের লাইভ সেশনে সিঙ্ক হয়ে যাবে।
                </p>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                <div className="space-y-1">
                  <label className="text-xs font-semibold text-slate-700">Custom Jitsi Video Server URL</label>
                  <div className="flex gap-1.5">
                    <input
                      value={form.custom_jitsi_server_url}
                      onChange={(e) => set("custom_jitsi_server_url", e.target.value)}
                      placeholder="https://meet.jit.si"
                      className={`${inputCls} font-mono text-xs`}
                    />
                    <button
                      type="button"
                      onClick={() => set("custom_jitsi_server_url", "https://meet.jit.si")}
                      className="px-2.5 py-2 rounded-lg border border-slate-300 hover:bg-slate-100 text-xs font-semibold whitespace-nowrap"
                    >
                      ডিফল্ট
                    </button>
                  </div>
                </div>

                <SecretField
                  label="Resend Email API Key (পাসওয়ার্ড রিসেট ইমেইল)"
                  placeholder="re_..."
                  value={form.resend_api_key}
                  revealed={Boolean(revealed.resend)}
                  onToggleReveal={() => toggleReveal("resend")}
                  onChange={(v) => set("resend_api_key", v)}
                  onClear={() => set("resend_api_key", "")}
                />
              </div>
            </Card>

            <div className="flex flex-wrap items-center gap-3">
              <Btn type="submit" busy={busy} className="px-6 py-2.5 text-sm">
                ✓ এনক্রিপ্টেড ভল্টে সব API কী সংরক্ষণ করুন
              </Btn>
            </div>
          </form>
        </>
      )}
    </div>
  );
}

function StatusTile({
  title,
  badge,
  sub,
}: {
  title: string;
  badge: { label: string; tone: "slate" | "green" | "red" | "amber" | "blue" };
  sub: string;
}) {
  return (
    <div className="bg-white rounded-xl border border-slate-200 p-3.5 space-y-1.5 shadow-sm">
      <div className="flex items-center justify-between gap-2">
        <span className="text-xs font-bold text-slate-900">{title}</span>
        <Pill tone={badge.tone}>{badge.label}</Pill>
      </div>
      <p className="text-[11px] text-slate-500 truncate font-mono">{sub}</p>
    </div>
  );
}

function SecretField({
  label,
  placeholder,
  value,
  revealed,
  hideEye,
  onToggleReveal,
  onChange,
  onClear,
}: {
  label: string;
  placeholder: string;
  value: string;
  revealed: boolean;
  hideEye?: boolean;
  onToggleReveal: () => void;
  onChange: (v: string) => void;
  onClear: () => void;
}) {
  return (
    <div className="space-y-1">
      <div className="flex items-center justify-between">
        <label className="text-xs font-semibold text-slate-700">{label}</label>
        {value.trim() ? (
          <span className="text-[10px] font-semibold text-emerald-700">● সেট করা আছে</span>
        ) : (
          <span className="text-[10px] text-slate-400">খালি</span>
        )}
      </div>
      <div className="flex items-center gap-1.5">
        <input
          type={revealed ? "text" : "password"}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          placeholder={placeholder}
          autoComplete="off"
          className={`${inputCls} font-mono text-xs`}
        />
        {!hideEye && (
          <button
            type="button"
            onClick={onToggleReveal}
            title={revealed ? "লুকান" : "দেখুন"}
            className="p-2 rounded-lg border border-slate-300 hover:bg-slate-100 text-slate-700"
          >
            {revealed ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
          </button>
        )}
        {value && (
          <button
            type="button"
            onClick={onClear}
            title="মুছে ফেলুন"
            className="p-2 rounded-lg border border-red-200 hover:bg-red-50 text-red-600"
          >
            <Trash2 className="w-4 h-4" />
          </button>
        )}
      </div>
    </div>
  );
}
