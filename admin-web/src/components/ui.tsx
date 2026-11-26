"use client";

import React from "react";
import { Loader2, RefreshCw } from "lucide-react";

export function Card({ children, className = "" }: { children: React.ReactNode; className?: string }) {
  return <div className={`bg-white p-5 rounded-xl border border-slate-200 shadow-sm ${className}`}>{children}</div>;
}

export function SectionTitle({
  children,
  onRefresh,
  loading,
  right,
}: {
  children: React.ReactNode;
  onRefresh?: () => void;
  loading?: boolean;
  right?: React.ReactNode;
}) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-2 mb-3">
      <h2 className="text-base font-bold text-slate-900">{children}</h2>
      <div className="flex items-center gap-2">
        {right}
        {onRefresh && (
          <button
            onClick={onRefresh}
            disabled={loading}
            className="inline-flex items-center gap-1.5 text-xs px-3 py-1.5 rounded-lg border border-slate-300 hover:bg-slate-100 disabled:opacity-50"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${loading ? "animate-spin" : ""}`} /> রিফ্রেশ
          </button>
        )}
      </div>
    </div>
  );
}

export function Btn({
  children,
  onClick,
  variant = "primary",
  disabled,
  busy,
  type = "button",
  className = "",
}: {
  children: React.ReactNode;
  onClick?: () => void;
  variant?: "primary" | "ghost" | "danger" | "amber";
  disabled?: boolean;
  busy?: boolean;
  type?: "button" | "submit";
  className?: string;
}) {
  const styles = {
    primary: "bg-[#0F5132] hover:bg-[#0c4129] text-white",
    ghost: "border border-slate-300 hover:bg-slate-100 text-slate-800",
    danger: "bg-red-600 hover:bg-red-700 text-white",
    amber: "bg-amber-400 hover:bg-amber-300 text-slate-950",
  }[variant];
  return (
    <button
      type={type}
      onClick={onClick}
      disabled={disabled || busy}
      className={`inline-flex items-center justify-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold transition disabled:opacity-50 ${styles} ${className}`}
    >
      {busy && <Loader2 className="w-3.5 h-3.5 animate-spin" />}
      {children}
    </button>
  );
}

export function Pill({ children, tone = "slate" }: { children: React.ReactNode; tone?: "slate" | "green" | "red" | "amber" | "blue" }) {
  const t = {
    slate: "bg-slate-100 text-slate-800",
    green: "bg-emerald-100 text-emerald-900",
    red: "bg-red-100 text-red-800",
    amber: "bg-amber-100 text-amber-900",
    blue: "bg-sky-100 text-sky-900",
  }[tone];
  return <span className={`inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[11px] font-semibold ${t}`}>{children}</span>;
}

export function Empty({ children }: { children: React.ReactNode }) {
  return <p className="text-sm text-slate-500 py-6 text-center">{children}</p>;
}

export function Loading() {
  return (
    <div className="flex items-center justify-center py-10 text-slate-500 text-sm gap-2">
      <Loader2 className="w-4 h-4 animate-spin" /> সার্ভার থেকে লোড হচ্ছে…
    </div>
  );
}

export function ErrorBox({ message }: { message: string | null }) {
  if (!message) return null;
  return <div className="bg-red-50 border border-red-200 text-red-800 text-xs px-3 py-2 rounded-lg mb-3">{message}</div>;
}

export const inputCls =
  "border border-slate-300 rounded-lg px-3 py-2 text-sm w-full focus:outline-none focus:ring-2 focus:ring-emerald-600/40";
