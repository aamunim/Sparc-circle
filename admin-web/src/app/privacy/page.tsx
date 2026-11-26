import type { Metadata } from "next";
import Link from "next/link";

export const metadata: Metadata = {
  title: "Privacy Policy — SPARC Study Circle",
  description: "গোপনীয়তা নীতি (Privacy Policy) — SPARC Study Circle (org.sparc.studycircle)",
};

export default function PrivacyPolicyPage() {
  return (
    <main className="min-h-screen bg-[#F8F7F4] text-slate-900 py-10 px-4">
      <div className="max-w-3xl mx-auto bg-white rounded-2xl border border-slate-200 shadow-sm p-6 md:p-10 space-y-6">
        <header className="border-b border-slate-200 pb-4 space-y-1">
          <p className="text-xs font-bold uppercase tracking-wider text-[#0F5132]">
            SPARC Study Circle • Package: org.sparc.studycircle
          </p>
          <h1 className="text-2xl font-bold">গোপনীয়তা নীতি / Privacy Policy</h1>
          <p className="text-xs text-slate-500">সর্বশেষ হালনাগাদ / Effective Date: November 26, 2026</p>
        </header>

        <section className="space-y-2 text-sm leading-relaxed text-slate-700">
          <h2 className="text-base font-bold text-slate-900">১. পরিচিতি (Overview)</h2>
          <p>
            <strong>SPARC Study Circle</strong> (&ldquo;বই পড়তে পড়তে লেখা শেখা&rdquo;) একটি যৌথ একাডেমিক পাঠ, অনুশীলন ও গবেষণা-লেখা
            প্ল্যাটফর্ম, যা <strong>SPARC</strong> পরিচালনা করে। আমরা ব্যবহারকারীদের ব্যক্তিগত তথ্যের গোপনীয়তা ও নিরাপত্তা সর্বোচ্চ গুরুত্বের সাথে সুরক্ষা করি। অ্যাপে কোনো বিজ্ঞাপন (Third-party Ads) বা ট্র্যাকিং SDK নেই এবং কোনো তথ্য তৃতীয় পক্ষের কাছে বিক্রি করা হয় না।
          </p>
          <p className="text-slate-600">
            SPARC Study Circle is a collaborative academic reading, guided practice, and scholarly writing platform operated by SPARC. We do not show third-party ads and we never sell personal user data.
          </p>
        </section>

        <section className="space-y-2 text-sm leading-relaxed text-slate-700">
          <h2 className="text-base font-bold text-slate-900">২. আমরা কী কী তথ্য সংগ্রহ করি (Information We Collect)</h2>
          <ul className="list-disc pl-5 space-y-1.5">
            <li>
              <strong>অ্যাকাউন্ট ও প্রোফাইল তথ্য (Account &amp; Profile):</strong> নাম, ইমেইল ঠিকানা, বিভাগ/প্রতিষ্ঠান, গবেষণার আগ্রহ এবং ঐচ্ছিক প্রোফাইল ছবি — অ্যাকাউন্ট তৈরি, লগইন ও সহপাঠী গবেষক ডিরেক্টরির জন্য।
            </li>
            <li>
              <strong>পাঠ, অনুশীলনী ও রাইটিং তথ্য (Academic Activity):</strong> পঠিত পৃষ্ঠা, হাইলাইট/নোট, অধ্যায়ের অনুশীলনীর উত্তর, সেলফ-রেটিং এবং রাইটিং টাস্ক — আপনার অগ্রগতি সংরক্ষণ ও মানব মেন্টরের (Supervisor/Mentor) মূল্যায়নের জন্য।
            </li>
            <li>
              <strong>স্টাডি সার্কেল ও যোগাযোগ (Circle Collaboration):</strong> স্টাডি সার্কেল সদস্যপদ, পোল ভোট, একাডেমিক বিতর্ক মন্তব্য, সহপাঠী বার্তা ও Poke। <em>গোপনীয়তা সুরক্ষা:</em> মেন্টর কেবল Poke-এর সংখ্যা দেখতে পান, ব্যক্তিগত Poke বার্তা কখনোই দেখতে পান না।
            </li>
            <li>
              <strong>ওয়ালেট ও বই অর্ডার তথ্য (Wallet &amp; Physical Book Orders):</strong> আপনি হার্ডকপি বই অর্ডার বা ম্যানুয়াল ওয়ালেট টপ-আপ করলে ডেলিভারি ঠিকানা, যোগাযোগের ফোন নম্বর এবং পেমেন্ট রেফারেন্স/TrxID সংগৃহীত হয় — কেবল অর্ডার ডেলিভারি ও পেমেন্ট যাচাইয়ের জন্য।
            </li>
            <li>
              <strong>ঐচ্ছিক AI সহায়তা (Optional AI Feedback):</strong> আপনি স্বেচ্ছায় &ldquo;AI Feedback&rdquo; বাটনে চাপলে কেবল আপনার লেখা খসড়াটি ভাষাগত পরামর্শের জন্য প্রক্রিয়াকরণ হয়; মানব মেন্টরের মূল্যায়নই চূড়ান্ত।
            </li>
          </ul>
        </section>

        <section className="space-y-2 text-sm leading-relaxed text-slate-700">
          <h2 className="text-base font-bold text-slate-900">৩. তথ্যের নিরাপত্তা ও সংরক্ষণ (Data Security &amp; Retention)</h2>
          <p>
            সকল তথ্য আদান-প্রদান HTTPS/TLS এনক্রিপশনের মাধ্যমে সম্পন্ন হয় এবং ডাটাবেসে Row-Level Access Control দ্বারা সুরক্ষিত থাকে। যতদিন আপনার অ্যাকাউন্ট সক্রিয় থাকবে ততদিন তথ্য সংরক্ষিত থাকবে, অথবা আপনি অ্যাকাউন্ট মুছে ফেললে তাৎক্ষণিকভাবে মুছে যাবে।
          </p>
          <p className="text-slate-600">
            All data is transmitted over HTTPS/TLS encryption and protected by strict role-based row-level access controls. Physical book delivery details are visible only to authorized SPARC staff.
          </p>
        </section>

        <section className="space-y-2 text-sm leading-relaxed text-slate-700">
          <h2 className="text-base font-bold text-slate-900">৪. অ্যাকাউন্ট ও ডেটা মুছে ফেলার অধিকার (Account &amp; Data Deletion)</h2>
          <p>
            আপনি যেকোনো সময় নিজেই আপনার অ্যাকাউন্ট এবং সংশ্লিষ্ট সকল ব্যক্তিগত তথ্য স্থায়ীভাবে মুছে ফেলতে পারেন আমাদের সেলফ-সার্ভিস পেজ থেকে:
          </p>
          <p>
            <Link
              href="/delete-account"
              className="inline-block px-4 py-2 rounded-lg bg-[#0F5132] text-white text-xs font-semibold"
            >
              🗑️ অ্যাকাউন্ট ও ডেটা মুছে ফেলুন / Delete Account &amp; Data
            </Link>
          </p>
        </section>

        <section className="space-y-1 text-sm leading-relaxed text-slate-700 border-t border-slate-200 pt-4">
          <h2 className="text-base font-bold text-slate-900">৫. যোগাযোগ (Contact Us)</h2>
          <p>
            গোপনীয়তা বা ডেটা সংক্রান্ত যেকোনো প্রশ্নে যোগাযোগ করুন:{" "}
            <a href="mailto:abamunim@gmail.com" className="text-[#0F5132] font-semibold underline">
              abamunim@gmail.com
            </a>
          </p>
          <p className="pt-2">
            <Link href="/" className="text-xs text-slate-500 underline">
              ← পোর্টালে ফিরে যান / Back to Portal
            </Link>
          </p>
        </section>
      </div>
    </main>
  );
}
