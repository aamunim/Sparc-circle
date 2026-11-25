# SPARC Study Circle — GitHub Secrets ও প্রোডাকশন ডিপ্লয়মেন্ট গাইড

## ১. GitHub Repository Secrets তালিকা (`Settings → Secrets and variables → Actions`)

চ্যাটে কোনো গোপন কী শেয়ার না করে সরাসরি আপনার GitHub রিপোজিটরির **Repository secrets**-এ নিচের নামগুলো হুবহু বসিয়ে দিন:

| Secret Name | উদাহরণ / কোথায় পাবেন | কাজ |
| :--- | :--- | :--- |
| `SUPABASE_URL` | `https://abcdefghijklm.supabase.co` *(Project Settings → API)* | Android APK ও ওয়েব পোর্টালে সংযোগ এবং প্রজেক্ট রেফারেন্স শনাক্তকরণ |
| `SUPABASE_ANON_KEY` | `eyJ...` বা `sb_publishable_...` *(Project Settings → API Keys)* | Android APK-এর ভেতরে পাবলিক ক্লায়েন্ট কী হিসেবে যুক্ত হবে |
| `SUPABASE_ACCESS_TOKEN` | `sbp_...` *(https://supabase.com/dashboard/account/tokens থেকে Generate new token)* | GitHub Actions থেকে স্বয়ংক্রিয়ভাবে `supabase db push` ও Edge Function ডিপ্লয় করবে |
| `SUPABASE_DB_PASSWORD` | প্রজেক্ট খোলার সময় দেওয়া Database Password | রিমোট PostgreSQL মাইগ্রেশন ও RLS পলিসি পুশ করতে ব্যবহৃত হবে |
| `GEMINI_API_KEY` *(ঐচ্ছিক)* | Google AI Studio API Key | রাইটিং টাস্কের লাইভ AI ফিডব্যাক চালু করতে Edge Function-এ সেট হবে |

---

## ২. স্বয়ংক্রিয় GitHub Actions Workflows

1. **`.github/workflows/supabase-deploy.yml`:**
   - স্বয়ংক্রিয়ভাবে সবগুলো SQL মাইগ্রেশন (`001`–`004`: টেবিল, ট্রিগার, RLS পলিসি ও সিড বই) আপনার লাইভ Supabase ডাটাবেসে পুশ করে এবং ৪টি Edge Function (`ai-writing-assistant`, `wallet-payment`, `poke-dispatch`, `generate-certificate`) ডিপ্লয় করে।
2. **`.github/workflows/android-apk.yml`:**
   - `SUPABASE_URL` ও `SUPABASE_ANON_KEY` সরাসরি অ্যাপে ইনজেক্ট করে নতুন `Latest` APK রিলিজ পাবলিশ করে।
