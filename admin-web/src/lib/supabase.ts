import { createClient } from "@supabase/supabase-js";

const supabaseUrl = process.env.NEXT_PUBLIC_SUPABASE_URL ?? "";
const supabaseAnonKey = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY ?? "";

export const isSupabaseConfigured = Boolean(supabaseUrl && supabaseAnonKey);

export const supabase = isSupabaseConfigured
  ? createClient(supabaseUrl, supabaseAnonKey)
  : null;

export type PortalRole = "admin" | "supervisor" | "sparc_rep";

export interface BookRecord {
  id: string;
  title: string;
  author: string;
  category: string;
  totalChapters: number;
  isFirstChapterFree: boolean;
  minMembersRequired: number;
  physicalPriceBdt: number;
  chapters: {
    chapterNo: number;
    title: string;
    type: "standard" | "long";
    totalPages: number;
    totalMarks: 52 | 61;
    passMarks: 32 | 37;
    redFlagMarks: 26 | 31;
    sessionsCount: 1 | 2;
    audioSummaryUrl: string;
  }[];
}

export interface LearnerSubmission {
  id: string;
  memberName: string;
  department: string;
  circleName: string;
  chapterNo: number;
  chapterType: "standard" | "long";
  stage: string;
  autoScore: number;
  supervisorScore: number | null;
  supervisorOverride: boolean;
  selfRatingAvg: number;
  genuinenessPassed: boolean;
  overdueDays: number;
  writingSubmitted: boolean;
  writingWordCount: number;
  writingExcerpt: string;
  finalProposalApproved: boolean;
  openAnswerText: string;
  pokesReceivedCount: number;
  pokeRepliesCount: number;
}

export interface WalletTopupRequest {
  id: string;
  userName: string;
  method: "SSLCommerz" | "bKash PGW" | "Manual TrxID";
  amountBdt: number;
  reference: string;
  phone: string;
  status: "pending" | "verified" | "rejected";
  createdAt: string;
}

export interface PhysicalOrderRecord {
  id: string;
  userName: string;
  bookTitle: string;
  mode: "Purchase" | "Borrow";
  address: string;
  phone: string;
  amountBdt: number;
  status: "pending" | "approved" | "dispatched" | "delivered";
}

export interface CircleOverview {
  id: string;
  name: string;
  bookTitle: string;
  memberCount: number;
  minMembersRequired: number;
  status: "forming" | "active";
  supervisorName: string;
  assignmentType: "member_voted" | "admin_assigned";
  exercisesSubmittedPct: number;
  daysSinceLastSession: number;
  jitsiRoom: string;
  externalMeetUrl: string;
}

export const INITIAL_BOOKS: BookRecord[] = [
  {
    id: "book-cpar",
    title: "The Action Research Planner: Doing CPAR",
    author: "Kemmis, McTaggart & Nixon",
    category: "Action Research & Pedagogy",
    totalChapters: 7,
    isFirstChapterFree: true,
    minMembersRequired: 10,
    physicalPriceBdt: 450,
    chapters: [
      {
        chapterNo: 1,
        title: "অধ্যায় ১: CPAR ও Practice Architectures",
        type: "long",
        totalPages: 32,
        totalMarks: 61,
        passMarks: 37,
        redFlagMarks: 31,
        sessionsCount: 2,
        audioSummaryUrl: "https://storage.sparc.org.bd/audio/cpar-ch1-bn.mp3",
      },
      {
        chapterNo: 2,
        title: "অধ্যায় ২: Reconnaissance ও Public Sphere",
        type: "standard",
        totalPages: 24,
        totalMarks: 52,
        passMarks: 32,
        redFlagMarks: 26,
        sessionsCount: 1,
        audioSummaryUrl: "https://storage.sparc.org.bd/audio/cpar-ch2-bn.mp3",
      },
    ],
  },
  {
    id: "book-creswell",
    title: "Research Design: Qualitative, Quantitative & Mixed Methods",
    author: "John W. Creswell",
    category: "Research Methodology",
    totalChapters: 10,
    isFirstChapterFree: true,
    minMembersRequired: 12,
    physicalPriceBdt: 520,
    chapters: [
      {
        chapterNo: 1,
        title: "অধ্যায় ১: গবেষণা অ্যাপ্রোচ নির্বাচন",
        type: "standard",
        totalPages: 28,
        totalMarks: 52,
        passMarks: 32,
        redFlagMarks: 26,
        sessionsCount: 1,
        audioSummaryUrl: "https://storage.sparc.org.bd/audio/creswell-ch1-bn.mp3",
      },
    ],
  },
];

export const INITIAL_CIRCLES: CircleOverview[] = [
  {
    id: "c-01",
    name: "CPAR কোহর্ট-০১ (সমাজবিজ্ঞান ও শিক্ষা)",
    bookTitle: "The Action Research Planner: Doing CPAR",
    memberCount: 12,
    minMembersRequired: 10,
    status: "active",
    supervisorName: "ড. নুসরাত জাহান",
    assignmentType: "member_voted",
    exercisesSubmittedPct: 83,
    daysSinceLastSession: 6,
    jitsiRoom: "SPARC_CPAR_Cohort01_Ch1",
    externalMeetUrl: "https://meet.google.com/sparc-cpar-01",
  },
  {
    id: "c-02",
    name: "Mixed-Methods রিসার্চ সার্কেল-০২",
    bookTitle: "Research Design (Creswell)",
    memberCount: 8,
    minMembersRequired: 12,
    status: "forming",
    supervisorName: "নির্ধারণ বাকি",
    assignmentType: "admin_assigned",
    exercisesSubmittedPct: 0,
    daysSinceLastSession: 0,
    jitsiRoom: "SPARC_Creswell_Cohort02",
    externalMeetUrl: "https://zoom.us/j/9988776655",
  },
];

export const INITIAL_SUBMISSIONS: LearnerSubmission[] = [
  {
    id: "sub-1",
    memberName: "তানভীর আহমেদ",
    department: "Sociology",
    circleName: "CPAR কোহর্ট-০১",
    chapterNo: 1,
    chapterType: "long",
    stage: "Exercises Submitted",
    autoScore: 23,
    supervisorScore: 19,
    supervisorOverride: false,
    selfRatingAvg: 1.8, // Under-confident: <=2 while score >= 80% (42/52 or high)
    genuinenessPassed: true,
    overdueDays: 0,
    writingSubmitted: true,
    writingWordCount: 612,
    writingExcerpt:
      "বিশ্ববিদ্যালয়ের সমাজবিজ্ঞান বিভাগে সেমিনার উপস্থিতি কমে যাওয়ার মূল কারণ কেবল সময়সূচি (Doings) নয়, বরং সংলাপের ভীতি (Relatings)...",
    finalProposalApproved: false,
    openAnswerText:
      "Sayings, Doings ও Relatings এই তিনটি মাত্রার মাধ্যমে বিভাগের সেমিনার সংস্কৃতি বিশ্লেষণ করে অংশগ্রহণমূলক সংলাপ খোলা সম্ভব।",
    pokesReceivedCount: 2,
    pokeRepliesCount: 2,
  },
  {
    id: "sub-2",
    memberName: "সাদিয়া ইসলাম",
    department: "Sociology",
    circleName: "CPAR কোহর্ট-০১",
    chapterNo: 1,
    chapterType: "long",
    stage: "Exercises Submitted",
    autoScore: 12,
    supervisorScore: 11,
    supervisorOverride: false,
    selfRatingAvg: 4.5, // Over-confident (>=4 & score <50%) + >=3 Red Flags => Needs Support!
    genuinenessPassed: false,
    overdueDays: 9,
    writingSubmitted: false,
    writingWordCount: 0,
    writingExcerpt: "",
    finalProposalApproved: false,
    openAnswerText: "শিক্ষার্থীদের উপস্থিতি বাড়াতে নোটিশ দেওয়া যেতে পারে।",
    pokesReceivedCount: 5,
    pokeRepliesCount: 1,
  },
];

export const INITIAL_WALLET_REQUESTS: WalletTopupRequest[] = [
  {
    id: "w-1",
    userName: "মালিহা তাবাসসুম",
    method: "Manual TrxID",
    amountBdt: 1000,
    reference: "BKH948271QX",
    phone: "01711-XXXXXX",
    status: "pending",
    createdAt: "আজ, দুপুর ২:১৫",
  },
  {
    id: "w-2",
    userName: "রাফিউজ্জামান খান",
    method: "Manual TrxID",
    amountBdt: 500,
    reference: "NGD773910KL",
    phone: "01819-XXXXXX",
    status: "pending",
    createdAt: "আজ, সকাল ১১:৪০",
  },
  {
    id: "w-3",
    userName: "তানভীর আহমেদ",
    method: "bKash PGW",
    amountBdt: 1000,
    reference: "BKASH-984120",
    phone: "01912-XXXXXX",
    status: "verified",
    createdAt: "গতকাল",
  },
];

export const INITIAL_BOOK_ORDERS: PhysicalOrderRecord[] = [
  {
    id: "ord-1",
    userName: "ফারহান সাদিক",
    bookTitle: "The Action Research Planner: Doing CPAR",
    mode: "Purchase",
    address: "কলা ভবন, সমাজবিজ্ঞান বিভাগ, ঢাকা",
    phone: "01715-XXXXXX",
    amountBdt: 450,
    status: "pending",
  },
  {
    id: "ord-2",
    userName: "মালিহা তাবাসসুম",
    bookTitle: "Research Design (Creswell)",
    mode: "Borrow",
    address: "নৃবিজ্ঞান সেমিনার লাইব্রেরি",
    phone: "01677-XXXXXX",
    amountBdt: 0,
    status: "approved",
  },
];
