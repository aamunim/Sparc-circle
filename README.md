# SPARC Study Circle (ভার্সন ২.০ — কমপ্লিট এডিশন)

**"বই পড়তে পড়তে লেখা শেখা"** — **SPARC (Strategic Pedagogy and Advanced Research Center)**-এর একটি একাডেমিক, স্কিল-ওরিয়েন্টেড ও রিসার্চ-কেন্দ্রিক কোলাবোরেটিভ লার্নিং প্ল্যাটফর্ম।

---

## প্রজেক্ট আর্কিটেকচার ও স্ট্রাকচার

| ফোল্ডার | প্রযুক্তি | বিবরণ |
| :--- | :--- | :--- |
| [`android-app/`](./android-app) | **Kotlin 2.0, Jetpack Compose (Material 3)**, Coroutines/StateFlow, OkHttp, JUnit4 | নেটিভ অ্যান্ড্রয়েড অ্যাপ — অনবোর্ডিং, মেম্বার ডিরেক্টরি, ইন্টারেক্টিভ রিডার ও হাইলাইটার, অডিও সামারি, স্টাডি সার্কেল, রেট-লিমিটেড Poke, ৬-ধাপের মূল্যায়ন, Jitsi ইন-অ্যাপ লাইভ ভিডিও + Meet/Zoom, রাইটিং মডিউল + AI অ্যাসিস্ট্যান্ট, কো-অথর ম্যাচমেকিং, ওয়ালেট ও সোশ্যাল প্রগ্রেস কার্ড |
| [`admin-web/`](./admin-web) | **Next.js (App Router, TypeScript), Tailwind CSS**, Supabase JS | অ্যাডমিন, সুপারভাইজার (শিক্ষক) ও SPARC প্রতিনিধিদের ওয়েব পোর্টাল — বই/প্রশ্ন আপলোড, F–J ম্যানুয়াল স্কোরিং, সেলফ-রেটিং ক্যালিব্রেশন, রেড-ফ্ল্যাগ (`Needs Support`) মনিটর, ম্যানুয়াল TrxID ওয়ালেট অনুমোদন ও ফিজিক্যাল বই অর্ডার ম্যানেজমেন্ট |
| [`supabase/`](./supabase) | **PostgreSQL 15+, Row-Level Security (RLS), PL/pgSQL Triggers, Deno Edge Functions** | রিলেশনাল ডাটাবেস স্কিমা, লকড `chapter_answer_keys`, ওয়ালেট লেজার, ৬-ধাপের স্টেট মেশিন ও সার্ভারলেস এজ ফাংশন (`ai-writing-assistant`, `wallet-payment`, `poke-dispatch`, `generate-certificate`) |

---

## দ্রুত রান ও বিল্ড নির্দেশিকা

### ১. Native Android App (`android-app/`)
```bash
cd android-app
./gradlew testDebugUnitTest
./gradlew assembleDebug
```
তৈরিকৃত APK পাওয়া যাবে: `android-app/app/build/outputs/apk/debug/app-debug.apk`।

### ২. Admin & Supervisor Web Portal (`admin-web/`)
```bash
cd admin-web
pnpm install
pnpm dev
```

### ৩. Supabase Database & Edge Functions (`supabase/`)
1. নতুন একটি Supabase প্রজেক্ট খুলে SQL Editor-এ ক্রমানুসারে নিচের ফাইলগুলো রান করুন:
   - `supabase/migrations/20261125000001_initial_schema.sql`
   - `supabase/migrations/20261125000002_triggers_and_rpcs.sql`
   - `supabase/migrations/20261125000003_rls_policies.sql`
   - `supabase/seed.sql`
2. Edge Functions ডিপ্লয় করুন:
   ```bash
   supabase functions deploy ai-writing-assistant
   supabase functions deploy wallet-payment
   supabase functions deploy poke-dispatch
   supabase functions deploy generate-certificate
   ```

বিস্তারিত এনভায়রনমেন্ট সেটআপ ও API Key কনফিগারেশনের জন্য দেখুন: [`docs/DEPLOYMENT_AND_SETUP.md`](./docs/DEPLOYMENT_AND_SETUP.md)।