"use client";

import React, { useCallback, useEffect, useState } from "react";
import {
  Award,
  BookOpen,
  CheckCircle2,
  CreditCard,
  LogOut,
  Palette,
  ShieldCheck,
  UserCog,
  Users,
} from "lucide-react";
import type { Session } from "@supabase/supabase-js";
import {
  ACCESS_LABELS,
  clearBrowserServerConfig,
  friendlyError,
  getServer,
  LATEST_APK_URL,
  PortalAccess,
  PortalMe,
  readServerConfig,
  saveBrowserServerConfig,
} from "@/lib/server";
import { Btn, inputCls, Loading } from "@/components/ui";
import OverviewTab from "@/components/tabs/OverviewTab";
import MentorQueueTab from "@/components/tabs/MentorQueueTab";
import CirclesTab from "@/components/tabs/CirclesTab";
import UsersTab from "@/components/tabs/UsersTab";
import LibraryTab from "@/components/tabs/LibraryTab";
import WalletOrdersTab from "@/components/tabs/WalletOrdersTab";
import BrandTab from "@/components/tabs/BrandTab";

export type TabKey = "overview" | "grading" | "circles" | "users" | "library" | "wallet_orders" | "brand";

export interface TabProps {
  me: PortalMe;
  notify: (msg: string, tone?: "ok" | "err") => void;
}

const TABS: { key: TabKey; label: string; icon: React.ElementType; access: PortalAccess[] }[] = [
  { key: "overview", label: "সারসংক্ষেপ", icon: Award, access: ["admin", "sparc_rep", "supervisor"] },
  { key: "grading", label: "মেন্টর মূল্যায়ন কিউ", icon: CheckCircle2, access: ["admin", "supervisor"] },
  { key: "circles", label: "স্টাডি সার্কেল", icon: Users, access: ["admin", "sparc_rep", "supervisor"] },
  { key: "users", label: "ইউজার ও রোল", icon: UserCog, access: ["admin"] },
  { key: "library", label: "লাইব্রেরি স্টুডিও", icon: BookOpen, access: ["admin", "sparc_rep"] },
  { key: "wallet_orders", label: "ওয়ালেট ও বই অর্ডার", icon: CreditCard, access: ["admin", "sparc_rep"] },
  { key: "brand", label: "ব্র্যান্ড ও নোটিশ", icon: Palette, access: ["admin"] },
];

export default function SparcAdminPortal() {
  const [ready, setReady] = useState(false);
  const [configured, setConfigured] = useState(false);
  const [session, setSession] = useState<Session | null>(null);
  const [me, setMe] = useState<PortalMe | null>(null);
  const [meError, setMeError] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<TabKey>("overview");
  const [toast, setToast] = useState<{ msg: string; tone: "ok" | "err" } | null>(null);

  const notify = useCallback((msg: string, tone: "ok" | "err" = "ok") => {
    setToast({ msg, tone });
    window.setTimeout(() => setToast(null), 5000);
  }, []);

  // Initial: server config + session
  useEffect(() => {
    const sb = getServer();
    setConfigured(Boolean(sb));
    if (!sb) {
      setReady(true);
      return;
    }
    sb.auth.getSession().then(({ data }) => {
      setSession(data.session);
      setReady(true);
    });
    const { data: sub } = sb.auth.onAuthStateChange((_evt, s) => setSession(s));
    return () => sub.subscription.unsubscribe();
  }, [configured]);

  // Load portal identity once signed in
  useEffect(() => {
    if (!session) {
      setMe(null);
      return;
    }
    const sb = getServer();
    if (!sb) return;
    setMeError(null);
    sb.rpc("portal_whoami_rpc").then(({ data, error }) => {
      if (error) setMeError(friendlyError(error));
      else setMe(data as PortalMe);
    });
  }, [session]);

  const signOut = async () => {
    await getServer()?.auth.signOut();
    setMe(null);
    setActiveTab("overview");
  };

  if (!ready) return <FullScreen><Loading /></FullScreen>;

  if (!configured) {
    return <ServerSetup onSaved={() => setConfigured(true)} />;
  }

  if (!session) {
    return <LoginScreen onReconfigure={() => setConfigured(false)} />;
  }

  if (meError) {
    return (
      <FullScreen>
        <div className="text-center space-y-3">
          <p className="text-sm text-red-700">{meError}</p>
          <Btn variant="ghost" onClick={signOut}>সাইন আউট</Btn>
        </div>
      </FullScreen>
    );
  }

  if (!me) return <FullScreen><Loading /></FullScreen>;

  if (me.access === "none") {
    return (
      <FullScreen>
        <div className="max-w-md text-center space-y-4 bg-white p-8 rounded-2xl border border-slate-200 shadow-sm">
          <ShieldCheck className="w-10 h-10 mx-auto text-amber-500" />
          <h1 className="text-lg font-bold">এই পোর্টাল শুধু মেন্টর, SPARC প্রতিনিধি ও অ্যাডমিনের জন্য</h1>
          <p className="text-sm text-slate-600">
            আপনি <strong>{me.name}</strong> হিসেবে সাইন ইন করেছেন। পড়া, অনুশীলনী ও স্টাডি সার্কেলের জন্য অনুগ্রহ করে
            SPARC Study Circle অ্যান্ড্রয়েড অ্যাপ ব্যবহার করুন। মেন্টর হতে চাইলে SPARC অ্যাডমিনের সাথে যোগাযোগ করুন।
          </p>
          <div className="flex justify-center gap-2">
            <a href={LATEST_APK_URL} target="_blank" rel="noreferrer" className="px-3 py-1.5 rounded-lg bg-amber-400 text-xs font-bold">
              📱 অ্যাপ ডাউনলোড
            </a>
            <Btn variant="ghost" onClick={signOut}>সাইন আউট</Btn>
          </div>
        </div>
      </FullScreen>
    );
  }

  const visibleTabs = TABS.filter((t) => t.access.includes(me.access));
  const current = visibleTabs.find((t) => t.key === activeTab) ? activeTab : "overview";
  const tabProps: TabProps = { me, notify };

  return (
    <div className="min-h-screen flex flex-col bg-[#F8F7F4] text-slate-900">
      <header className="bg-[#0F5132] text-white sticky top-0 z-20 shadow">
        <div className="max-w-7xl mx-auto px-4 py-3 flex flex-wrap items-center justify-between gap-3">
          <div>
            <div className="flex items-center gap-2">
              <ShieldCheck className="w-6 h-6 text-amber-300" />
              <h1 className="text-lg font-bold tracking-tight">SPARC Study Circle — অ্যাডমিন ও মেন্টর পোর্টাল</h1>
            </div>
            <p className="text-[11px] text-emerald-100 mt-0.5">
              🟢 লাইভ সার্ভারে সংযুক্ত • {me.name} • {ACCESS_LABELS[me.access]}
            </p>
          </div>
          <div className="flex items-center gap-2">
            <a
              href={LATEST_APK_URL}
              target="_blank"
              rel="noreferrer"
              className="px-3 py-1.5 rounded-lg bg-amber-400 hover:bg-amber-300 text-slate-950 text-xs font-bold"
            >
              📱 সর্বশেষ APK
            </a>
            <button
              onClick={signOut}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-emerald-950/50 hover:bg-emerald-950 text-xs font-semibold"
            >
              <LogOut className="w-3.5 h-3.5" /> সাইন আউট
            </button>
          </div>
        </div>
        <nav className="max-w-7xl mx-auto px-4 flex gap-1 overflow-x-auto">
          {visibleTabs.map((tab) => {
            const Icon = tab.icon;
            return (
              <button
                key={tab.key}
                onClick={() => setActiveTab(tab.key)}
                className={`flex items-center gap-2 px-3.5 py-2 rounded-t-lg text-sm whitespace-nowrap transition ${
                  current === tab.key ? "bg-[#F8F7F4] text-[#0F5132] font-semibold" : "text-emerald-100 hover:bg-emerald-800/50"
                }`}
              >
                <Icon className="w-4 h-4" />
                {tab.label}
              </button>
            );
          })}
        </nav>
      </header>

      {toast && (
        <div className="fixed bottom-4 right-4 z-30 max-w-sm">
          <div
            className={`px-4 py-3 rounded-lg text-sm font-medium shadow-lg border ${
              toast.tone === "ok" ? "bg-emerald-50 border-emerald-300 text-emerald-950" : "bg-red-50 border-red-300 text-red-900"
            }`}
          >
            {toast.msg}
          </div>
        </div>
      )}

      <main className="max-w-7xl mx-auto w-full px-4 py-6 flex-1">
        {current === "overview" && <OverviewTab {...tabProps} />}
        {current === "grading" && <MentorQueueTab {...tabProps} />}
        {current === "circles" && <CirclesTab {...tabProps} />}
        {current === "users" && <UsersTab {...tabProps} />}
        {current === "library" && <LibraryTab {...tabProps} />}
        {current === "wallet_orders" && <WalletOrdersTab {...tabProps} />}
        {current === "brand" && <BrandTab {...tabProps} />}
      </main>

      <footer className="text-center text-[11px] text-slate-500 py-4">
        SPARC Study Circle • বই পড়তে পড়তে লেখা শেখা • মানব মেন্টরের মূল্যায়নই চূড়ান্ত
      </footer>
    </div>
  );
}

function FullScreen({ children }: { children: React.ReactNode }) {
  return <div className="min-h-screen flex items-center justify-center bg-[#F8F7F4] p-4">{children}</div>;
}

function BrandHeader({ subtitle }: { subtitle: string }) {
  return (
    <div className="text-center mb-6">
      <div className="mx-auto w-14 h-14 rounded-2xl bg-[#0F5132] flex items-center justify-center shadow">
        <ShieldCheck className="w-8 h-8 text-amber-300" />
      </div>
      <h1 className="text-xl font-bold mt-3">SPARC Study Circle</h1>
      <p className="text-sm text-slate-600">{subtitle}</p>
    </div>
  );
}

function LoginScreen({ onReconfigure }: { onReconfigure: () => void }) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const cfg = readServerConfig();

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    const sb = getServer();
    if (!sb) return;
    setBusy(true);
    setErr(null);
    const { error } = await sb.auth.signInWithPassword({ email: email.trim(), password });
    setBusy(false);
    if (error) setErr(friendlyError(error));
  };

  return (
    <FullScreen>
      <form onSubmit={submit} className="w-full max-w-sm bg-white p-8 rounded-2xl border border-slate-200 shadow-sm space-y-4">
        <BrandHeader subtitle="অ্যাডমিন ও মেন্টর পোর্টালে সাইন ইন করুন" />
        <div className="space-y-1">
          <label className="text-xs font-semibold text-slate-700">ইমেইল</label>
          <input type="email" required value={email} onChange={(e) => setEmail(e.target.value)} className={inputCls} autoComplete="email" />
        </div>
        <div className="space-y-1">
          <label className="text-xs font-semibold text-slate-700">পাসওয়ার্ড</label>
          <input type="password" required value={password} onChange={(e) => setPassword(e.target.value)} className={inputCls} autoComplete="current-password" />
        </div>
        {err && <p className="text-xs text-red-700 bg-red-50 px-3 py-2 rounded-lg">{err}</p>}
        <Btn type="submit" busy={busy} className="w-full py-2.5 text-sm">সাইন ইন</Btn>
        <p className="text-[11px] text-slate-500 text-center">
          অ্যান্ড্রয়েড অ্যাপের একই অ্যাকাউন্ট দিয়ে সাইন ইন করুন। শুধু মেন্টর, SPARC প্রতিনিধি ও অ্যাডমিন প্রবেশ করতে পারবেন।
        </p>
        {cfg.source === "browser" && (
          <button
            type="button"
            onClick={() => {
              clearBrowserServerConfig();
              onReconfigure();
            }}
            className="block mx-auto text-[11px] text-slate-500 underline"
          >
            সার্ভার সংযোগ পরিবর্তন করুন
          </button>
        )}
      </form>
    </FullScreen>
  );
}

function ServerSetup({ onSaved }: { onSaved: () => void }) {
  const [url, setUrl] = useState("");
  const [key, setKey] = useState("");
  const [err, setErr] = useState<string | null>(null);

  const save = (e: React.FormEvent) => {
    e.preventDefault();
    if (!/^https:\/\/.+/.test(url.trim())) {
      setErr("সঠিক সার্ভার URL দিন (https:// দিয়ে শুরু)।");
      return;
    }
    if (key.trim().length < 20) {
      setErr("সঠিক পাবলিক কী দিন।");
      return;
    }
    saveBrowserServerConfig(url, key);
    onSaved();
  };

  return (
    <FullScreen>
      <form onSubmit={save} className="w-full max-w-md bg-white p-8 rounded-2xl border border-slate-200 shadow-sm space-y-4">
        <BrandHeader subtitle="প্রথমবার: সার্ভার সংযোগ সেটআপ" />
        <p className="text-xs text-slate-600 bg-amber-50 border border-amber-200 rounded-lg px-3 py-2">
          হোস্টিং-এ সার্ভার সেটিং এখনো যোগ করা হয়নি। সার্ভার URL ও পাবলিক কী একবার দিন — এটি শুধু এই ব্রাউজারে সংরক্ষিত থাকবে।
        </p>
        <div className="space-y-1">
          <label className="text-xs font-semibold text-slate-700">সার্ভার URL</label>
          <input value={url} onChange={(e) => setUrl(e.target.value)} placeholder="https://xxxx.example.co" className={inputCls} />
        </div>
        <div className="space-y-1">
          <label className="text-xs font-semibold text-slate-700">সার্ভার পাবলিক কী</label>
          <textarea value={key} onChange={(e) => setKey(e.target.value)} rows={3} className={`${inputCls} font-mono text-xs`} />
        </div>
        {err && <p className="text-xs text-red-700">{err}</p>}
        <Btn type="submit" className="w-full py-2.5 text-sm">সংরক্ষণ ও এগিয়ে যান</Btn>
      </form>
    </FullScreen>
  );
}
