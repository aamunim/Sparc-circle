# SPARC Study Circle (v2.0) — কনফিগারেশন ও ডিপ্লয়মেন্ট গাইড

## ১. Supabase Environment Variables

### `admin-web/.env.local`
```env
NEXT_PUBLIC_SUPABASE_URL=https://your-project-ref.supabase.co
NEXT_PUBLIC_SUPABASE_ANON_KEY=your-anon-key
```

### `android-app/gradle.properties` (অথবা CI Secrets)
```properties
SUPABASE_URL=https://your-project-ref.supabase.co
SUPABASE_ANON_KEY=your-anon-key
```

### Supabase Edge Function Secrets
```bash
supabase secrets set GEMINI_API_KEY="your-google-gemini-api-key"
supabase secrets set SSLCOMMERZ_STORE_ID="your-store-id"
supabase secrets set SSLCOMMERZ_STORE_PASS="your-store-password"
supabase secrets set BKASH_APP_KEY="your-bkash-app-key"
```

---

## ২. নিরাপত্তা ও প্রাইভেসি আর্কিটেকচার (RLS সারাংশ)

1. **Locked Answer Key (`chapter_answer_keys`):** ইউজার যতক্ষণ না `Exercises Submitted` ধাপে পৌঁছাচ্ছেন, ততক্ষণ PostgreSQL RLS কোনোভাবেই সঠিক উত্তর বা মডেল পয়েন্ট রিটার্ন করবে না।
2. **Poke Privacy (`pokes` বনাম `supervisor_poke_summary`):** সুপারভাইজার শুধুমাত্র কোন সদস্য কতটি Poke পেয়েছেন বা রিপ্লাই দিয়েছেন তার সংখ্যা দেখতে পান; ব্যক্তিগত বার্তা কেবল প্রেরক ও প্রাপকের মধ্যেই সীমাবদ্ধ।
3. **Physical Book Orders (`physical_book_orders`):** শুধুমাত্র `admin`, `supervisor` (শিক্ষক), `sparc_rep` এবং অর্ডারকারী নিজে অর্ডারের তথ্য দেখতে পারেন।
4. **Private Marks & Writing (`chapter_progress` ও `writing_submissions`):** গ্রুপ ড্যাশবোর্ডে `group_chapter_progress_public` ভিউয়ের মাধ্যমে কেবল ৬-ধাপের স্ট্যাটাস দেখানো হয়; ব্যক্তিগত নম্বর ও রাইটিং অন্য সদস্যদের কাছ থেকে গোপন থাকে।
