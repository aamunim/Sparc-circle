"use client";

import { createClient, SupabaseClient } from "@supabase/supabase-js";

/**
 * Server connection for the SPARC web portal.
 *
 * On Vercel the two NEXT_PUBLIC_* environment variables are read at build time.
 * If they are not set yet, an Admin can enter the Server URL + Public Key once on
 * the login screen (stored only in this browser's localStorage).
 *
 * NOTE: user-facing UI must always say "সার্ভার / Server".
 */

const ENV_URL = process.env.NEXT_PUBLIC_SUPABASE_URL ?? "";
const ENV_KEY = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY ?? "";

const LS_URL = "sparc.portal.serverUrl";
const LS_KEY = "sparc.portal.serverKey";

let cached: { url: string; key: string; client: SupabaseClient } | null = null;

export interface ServerConfig {
  url: string;
  key: string;
  source: "env" | "browser" | "none";
}

export function readServerConfig(): ServerConfig {
  if (ENV_URL && ENV_KEY) return { url: ENV_URL, key: ENV_KEY, source: "env" };
  if (typeof window !== "undefined") {
    const url = window.localStorage.getItem(LS_URL) ?? "";
    const key = window.localStorage.getItem(LS_KEY) ?? "";
    if (url && key) return { url, key, source: "browser" };
  }
  return { url: "", key: "", source: "none" };
}

export function saveBrowserServerConfig(url: string, key: string) {
  window.localStorage.setItem(LS_URL, url.trim().replace(/\/+$/, ""));
  window.localStorage.setItem(LS_KEY, key.trim());
  cached = null;
}

export function clearBrowserServerConfig() {
  window.localStorage.removeItem(LS_URL);
  window.localStorage.removeItem(LS_KEY);
  cached = null;
}

export function getServer(): SupabaseClient | null {
  const cfg = readServerConfig();
  if (!cfg.url || !cfg.key) return null;
  if (cached && cached.url === cfg.url && cached.key === cfg.key) return cached.client;
  const client = createClient(cfg.url, cfg.key, {
    auth: { persistSession: true, autoRefreshToken: true, storageKey: "sparc.portal.session" },
  });
  cached = { url: cfg.url, key: cfg.key, client };
  return client;
}

/** Throws if the server is not configured (used inside signed-in screens). */
export function server(): SupabaseClient {
  const c = getServer();
  if (!c) throw new Error("সার্ভার সংযোগ কনফিগার করা হয়নি।");
  return c;
}

/** Human-friendly error text; never leaks the vendor name. */
export function friendlyError(err: unknown): string {
  const raw =
    typeof err === "string"
      ? err
      : err && typeof err === "object" && "message" in err
      ? String((err as { message: unknown }).message)
      : "অজানা ত্রুটি";
  if (/invalid login credentials/i.test(raw)) return "ইমেইল বা পাসওয়ার্ড সঠিক নয়।";
  if (/email not confirmed/i.test(raw)) return "ইমেইল এখনো নিশ্চিত করা হয়নি।";
  if (/failed to fetch|network/i.test(raw)) return "সার্ভারে সংযোগ করা যাচ্ছে না। ইন্টারনেট বা সার্ভার URL যাচাই করুন।";
  return raw.replace(/supabase/gi, "সার্ভার");
}

// ---------------------------------------------------------------------------
// Shared types
// ---------------------------------------------------------------------------
export type PortalAccess = "admin" | "sparc_rep" | "supervisor" | "none";

export interface PortalMe {
  id: string;
  name: string;
  email: string | null;
  department: string;
  role: string;
  base_role: string;
  access: PortalAccess;
}

export const ROLE_LABELS: Record<string, { bn: string; en: string; emoji: string }> = {
  member: { bn: "গবেষক", en: "Researcher", emoji: "📖" },
  moderator: { bn: "সার্কেল লিড", en: "Circle Lead", emoji: "⭐" },
  supervisor: { bn: "মেন্টর", en: "Mentor", emoji: "🎓" },
  sparc_rep: { bn: "SPARC প্রতিনিধি", en: "SPARC Rep", emoji: "🤝" },
  admin: { bn: "অ্যাডমিন", en: "Admin / SPARC Team", emoji: "🛡️" },
};

export const ACCESS_LABELS: Record<PortalAccess, string> = {
  admin: "🛡️ অ্যাডমিন (SPARC টিম)",
  sparc_rep: "🤝 SPARC প্রতিনিধি",
  supervisor: "🎓 মেন্টর",
  none: "—",
};

export const LATEST_APK_URL = "https://github.com/aamunim/Sparc-circle/releases/latest";

export function shortDate(iso: string | null | undefined): string {
  if (!iso) return "—";
  try {
    return new Date(iso).toLocaleString("bn-BD", { dateStyle: "medium", timeStyle: "short" });
  } catch {
    return iso;
  }
}
