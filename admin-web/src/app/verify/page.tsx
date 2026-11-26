"use client";

import React, { Suspense, useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Award, CheckCircle2, Search, ShieldCheck, XCircle } from "lucide-react";
import { friendlyError, getServer, shortDate } from "@/lib/server";
import { Btn, inputCls } from "@/components/ui";

interface CertResult {
  valid: boolean;
  certificate_code?: string;
  title?: string;
  certificate_type?: string;
  issued_at?: string;
  researcher_name?: string;
  department?: string;
  institution?: string;
  book_title?: string;
  circle_name?: string;
}

function VerifyInner() {
  const params = useSearchParams();
  const initialCode = params.get("code") ?? "";
  const [code, setCode] = useState(initialCode);
  const [busy, setBusy] = useState(false);
  const [res, setRes] = useState<CertResult | null>(null);
  const [err, setErr] = useState<string | null>(null);

  const checkCode = useCallback(async (raw: string) => {
    const clean = raw.trim().toUpperCase();
    if (!clean) return;
    const sb = getServer();
    if (!sb) {
      setErr("সার্ভার সংযোগ কনফিগার করা নেই।");
      return;
    }
    setBusy(true);
    setErr(null);
    try {
      const { data, error } = await sb.rpc("verify_certificate_public_rpc", { p_code: clean });
      if (error) throw error;
      setRes(data as CertResult);
    } catch (e) {
      setErr(friendlyError(e));
    } finally {
      setBusy(false);
    }
  }, []);

  useEffect(() => {
    if (initialCode.trim()) {
      checkCode(initialCode);
    }
  }, [initialCode, checkCode]);

  return (
    <div className="max-w-2xl mx-auto bg-white rounded-2xl border border-slate-200 shadow-sm p-6 md:p-10 space-y-6">
      <header className="border-b border-slate-200 pb-4 flex items-center gap-3">
        <div className="w-12 h-12 rounded-xl bg-[#0F5132] flex items-center justify-center shrink-0">
          <Award className="w-6 h-6 text-amber-300" />
        </div>
        <div>
          <p className="text-xs font-bold uppercase tracking-wider text-[#0F5132]">
            SPARC Study Circle • Credential Verification
          </p>
          <h1 className="text-xl font-bold">ডিজিটাল সার্টিফিকেট যাচাই / Verify Certificate</h1>
        </div>
      </header>

      <form
        onSubmit={(e) => {
          e.preventDefault();
          checkCode(code);
        }}
        className="flex flex-wrap gap-2"
      >
        <input
          value={code}
          onChange={(e) => setCode(e.target.value.toUpperCase())}
          placeholder="সার্টিফিকেট কোড লিখুন (যেমন: SPARC-XXXXXXXX)"
          className={`${inputCls} flex-1 font-mono`}
        />
        <Btn type="submit" busy={busy} className="px-5 py-2.5 text-sm">
          <Search className="w-4 h-4" /> যাচাই করুন
        </Btn>
      </form>

      {err && <p className="text-xs text-red-700 bg-red-50 px-3 py-2 rounded-lg">{err}</p>}

      {res && res.valid && (
        <div className="rounded-2xl border-2 border-emerald-500 bg-emerald-50/70 p-6 space-y-3">
          <div className="flex items-center gap-2 text-emerald-900 font-bold text-base">
            <CheckCircle2 className="w-6 h-6 text-emerald-600" />
            যাচাইকৃত আসল সার্টিফিকেট (Authentic SPARC Credential)
          </div>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3 text-sm pt-2">
            <Info label="গবেষকের নাম / Recipient" value={res.researcher_name || "—"} />
            <Info label="সার্টিফিকেট কোড / Code" value={res.certificate_code || "—"} mono />
            <Info
              label="বিভাগ ও প্রতিষ্ঠান / Affiliation"
              value={[res.department, res.institution].filter(Boolean).join(" • ") || "—"}
            />
            <Info label="ইস্যুর তারিখ / Issued On" value={shortDate(res.issued_at)} />
            {res.book_title && <Info label="পঠিত গ্রন্থ / Book" value={res.book_title} />}
            {res.circle_name && <Info label="স্টাডি সার্কেল / Cohort" value={res.circle_name} />}
          </div>
          <p className="text-xs text-emerald-900 pt-2 border-t border-emerald-200 flex items-center gap-1.5">
            <ShieldCheck className="w-4 h-4" /> {res.title}
          </p>
        </div>
      )}

      {res && !res.valid && (
        <div className="rounded-xl border border-red-300 bg-red-50 p-4 flex items-center gap-3 text-sm text-red-900">
          <XCircle className="w-5 h-5 text-red-600 shrink-0" />
          <span>এই কোডে কোনো যাচাইকৃত SPARC সার্টিফিকেট পাওয়া যায়নি। কোডটি পুনরায় মিলিয়ে দেখুন।</span>
        </div>
      )}

      <footer className="text-xs text-slate-500 border-t border-slate-100 pt-4 flex justify-between">
        <span>SPARC Study Circle Academic Verification Registry</span>
        <Link href="/" className="underline text-[#0F5132] font-semibold">
          ← পোর্টালে ফিরে যান
        </Link>
      </footer>
    </div>
  );
}

function Info({ label, value, mono }: { label: string; value: string; mono?: boolean }) {
  return (
    <div className="bg-white rounded-lg border border-emerald-200 px-3 py-2">
      <p className="text-[11px] text-slate-500">{label}</p>
      <p className={`font-bold text-slate-900 ${mono ? "font-mono" : ""}`}>{value}</p>
    </div>
  );
}

export default function VerifyCertificatePage() {
  return (
    <main className="min-h-screen bg-[#F8F7F4] text-slate-900 py-10 px-4">
      <Suspense fallback={<div className="text-center py-10 text-sm text-slate-500">লোড হচ্ছে…</div>}>
        <VerifyInner />
      </Suspense>
    </main>
  );
}
