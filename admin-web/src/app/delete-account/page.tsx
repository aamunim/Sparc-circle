"use client";

import React, { useState } from "react";
import Link from "next/link";
import { friendlyError, getServer } from "@/lib/server";
import { Btn, inputCls } from "@/components/ui";

export default function DeleteAccountPage() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirmWord, setConfirmWord] = useState("");
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  const handleDelete = async (e: React.FormEvent) => {
    e.preventDefault();
    if (confirmWord.trim().toUpperCase() !== "DELETE") {
      setErr("নিশ্চিত করতে বড় হাতের অক্ষরে DELETE লিখুন।");
      return;
    }
    const sb = getServer();
    if (!sb) {
      setErr("সেলফ-সার্ভিস সংযোগ অনুপস্থিত — অনুগ্রহ করে নিচের ইমেইলে অনুরোধ পাঠান।");
      return;
    }
    setBusy(true);
    setErr(null);
    try {
      const sign = await sb.auth.signInWithPassword({ email: email.trim(), password });
      if (sign.error) throw sign.error;
      const del = await sb.rpc("delete_my_account_rpc");
      if (del.error) throw del.error;
      await sb.auth.signOut();
      setDone(true);
    } catch (er) {
      setErr(friendlyError(er));
    } finally {
      setBusy(false);
    }
  };

  return (
    <main className="min-h-screen bg-[#F8F7F4] text-slate-900 py-10 px-4">
      <div className="max-w-2xl mx-auto bg-white rounded-2xl border border-slate-200 shadow-sm p-6 md:p-10 space-y-6">
        <header className="border-b border-slate-200 pb-4 space-y-1">
          <p className="text-xs font-bold uppercase tracking-wider text-red-700">
            SPARC Study Circle • Account &amp; Data Deletion
          </p>
          <h1 className="text-2xl font-bold">অ্যাকাউন্ট ও ডেটা মুছে ফেলা / Delete Your Account</h1>
        </header>

        <section className="text-sm text-slate-700 space-y-2 leading-relaxed">
          <p>
            <strong>কী কী স্থায়ীভাবে মুছে যাবে (Data Permanently Deleted):</strong> আপনার লগইন ক্রেডেনশিয়াল, প্রোফাইল তথ্য, আপলোড করা ছবি, পাঠের হাইলাইট ও লগ, অনুশীলনী ও রাইটিং জমা, ব্যক্তিগত বার্তা, Poke, ওয়ালেট ও বই অর্ডারের রেকর্ড তাৎক্ষণিকভাবে এবং স্থায়ীভাবে মুছে যাবে। যৌথ স্টাডি সার্কেলে অন্যান্য সদস্যদের কার্যক্রম অক্ষুণ্ণ রাখতে কেবল সার্কেল কাঠামো সংরক্ষিত থাকে।
          </p>
          <p className="text-xs text-slate-500">
            Upon confirmation, your authentication credentials, profile, avatar, reading logs, highlights, exercise and writing submissions, direct messages, pokes, and wallet/order records are permanently deleted immediately.
          </p>
        </section>

        {done ? (
          <div className="bg-emerald-50 border border-emerald-300 text-emerald-950 rounded-xl p-5 text-sm space-y-2">
            <p className="font-bold">✓ আপনার অ্যাকাউন্ট ও সংশ্লিষ্ট ব্যক্তিগত তথ্য স্থায়ীভাবে মুছে ফেলা হয়েছে।</p>
            <p className="text-xs">Your SPARC Study Circle account and personal data have been permanently deleted.</p>
          </div>
        ) : (
          <form onSubmit={handleDelete} className="space-y-3 bg-red-50/50 border border-red-200 rounded-xl p-5">
            <h2 className="text-sm font-bold text-red-900">পদ্ধতি ১: তাৎক্ষণিক সেলফ-সার্ভিস ডিলিট (Instant Self-Service Deletion)</h2>
            <div>
              <label className="text-xs font-semibold">অ্যাকাউন্ট ইমেইল (Email)</label>
              <input type="email" required value={email} onChange={(e) => setEmail(e.target.value)} className={inputCls} />
            </div>
            <div>
              <label className="text-xs font-semibold">পাসওয়ার্ড (Password)</label>
              <input type="password" required value={password} onChange={(e) => setPassword(e.target.value)} className={inputCls} />
            </div>
            <div>
              <label className="text-xs font-semibold">নিশ্চিত করতে DELETE লিখুন (Type DELETE to confirm)</label>
              <input value={confirmWord} onChange={(e) => setConfirmWord(e.target.value)} placeholder="DELETE" className={inputCls} />
            </div>
            {err && <p className="text-xs text-red-700 bg-white px-3 py-2 rounded border border-red-200">{err}</p>}
            <Btn type="submit" variant="danger" busy={busy} className="w-full py-2.5 text-sm">
              🗑️ স্থায়ীভাবে অ্যাকাউন্ট ও ডেটা মুছে ফেলুন
            </Btn>
          </form>
        )}

        <section className="text-xs text-slate-600 space-y-1 border-t border-slate-200 pt-4">
          <p className="font-semibold text-slate-800">পদ্ধতি ২: ইমেইলের মাধ্যমে অনুরোধ (Request by Email)</p>
          <p>
            পাসওয়ার্ড মনে না থাকলে আপনার নিবন্ধিত ইমেইল থেকে{" "}
            <a href="mailto:abamunim@gmail.com?subject=SPARC%20Account%20Deletion%20Request" className="text-[#0F5132] font-semibold underline">
              abamunim@gmail.com
            </a>{" "}
            ঠিকানায় &ldquo;SPARC Account Deletion Request&rdquo; বিষয়ে ইমেইল পাঠান — ৭ কার্যদিবসের মধ্যে আপনার সকল তথ্য মুছে ফেলা হবে।
          </p>
          <p className="pt-2 flex gap-4">
            <Link href="/privacy" className="underline">গোপনীয়তা নীতি / Privacy Policy</Link>
            <Link href="/" className="underline">← পোর্টাল / Portal</Link>
          </p>
        </section>
      </div>
    </main>
  );
}
