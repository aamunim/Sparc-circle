-- ============================================================================
-- SPARC Study Circle (v2.0) — Seed Data (Core CPAR Book, Chapters & Questions)
-- ============================================================================

INSERT INTO public.books (
    id, title, subtitle, author, category, description,
    cover_url, pdf_url, physical_price_bdt, total_chapters,
    is_first_chapter_free, min_members_default, copyright_verified, is_published
) VALUES
(
    '11111111-1111-1111-1111-111111111101',
    'The Action Research Planner: Doing Critical Participatory Action Research',
    'বই পড়তে পড়তে CPAR প্রপোজাল লেখা শিখুন',
    'Stephen Kemmis, Robin McTaggart & Rhonda Nixon',
    'Action Research & Pedagogy',
    'SPARC ফ্ল্যাগশিপ স্টাডি সার্কেল বই। ৭টি অধ্যায় সম্পন্ন করার মাধ্যমে গবেষক একটি পূর্ণাঙ্গ Critical Participatory Action Research (CPAR) প্রপোজাল প্রস্তুত করবেন।',
    'https://storage.sparc.org.bd/covers/cpar-planner.jpg',
    'https://storage.sparc.org.bd/books/cpar-planner-licensed.pdf',
    450.00,
    7,
    TRUE,
    10,
    TRUE,
    TRUE
),
(
    '11111111-1111-1111-1111-111111111102',
    'Research Design: Qualitative, Quantitative, and Mixed Methods Approaches',
    'সামাজিক ও শিক্ষা গবেষণার মেথডোলজি গাইড',
    'John W. Creswell & J. David Creswell',
    'Research Methodology',
    'গবেষণা প্রশ্ন তৈরি, লিটারেচার রিভিউ এবং মিক্সড-মেথড ডেটা অ্যানালাইসিস শেখার কোর পাঠ্যবই।',
    'https://storage.sparc.org.bd/covers/creswell-design.jpg',
    'https://storage.sparc.org.bd/books/creswell-design-licensed.pdf',
    520.00,
    10,
    TRUE,
    12,
    TRUE,
    TRUE
)
ON CONFLICT (id) DO NOTHING;

-- Chapter 1 (Long Chapter: 32 pages, 150 mins, 36 questions, 61 marks, pass 37, red flag < 31, 2 sessions)
INSERT INTO public.chapters (
    id, book_id, chapter_no, title, type, total_pages, default_reading_minutes,
    page_matrix, sections, audio_summary_url, audio_duration_seconds,
    infographic_notes_urls, total_marks, pass_marks, red_flag_marks,
    learning_outcomes, writing_task_prompt, writing_min_words, writing_max_words,
    session_parts, slide_plan_url
) VALUES
(
    '22222222-2222-2222-2222-222222222201',
    '11111111-1111-1111-1111-111111111101',
    1,
    'অধ্যায় ১: Critical Participatory Action Research (CPAR)-এর পরিচয় ও প্র্যাকটিস আর্কিটেকচার',
    'long',
    32,
    150,
    '[
      {"page_range": "1-8", "estimated_minutes": 35, "difficulty": "medium"},
      {"page_range": "9-16", "estimated_minutes": 40, "difficulty": "high"},
      {"page_range": "17-24", "estimated_minutes": 40, "difficulty": "medium"},
      {"page_range": "25-32", "estimated_minutes": 35, "difficulty": "medium"}
    ]'::jsonb,
    '[
      {"id": "sec-1-1", "title": "1.1 Action Research বনাম CPAR-এর মৌলিক পার্থক্য"},
      {"id": "sec-1-2", "title": "1.2 Public Spheres ও Communicative Space গঠন"},
      {"id": "sec-1-3", "title": "1.3 Sayings, Doings এবং Relatings (Practice Architectures)"},
      {"id": "sec-1-4", "title": "1.4 গবেষণার ক্ষেত্রে Felt-Concern চিহ্নিতকরণ"}
    ]'::jsonb,
    'https://storage.sparc.org.bd/audio/cpar-ch1-bn-summary.mp3',
    390,
    ARRAY[
      'https://storage.sparc.org.bd/notes/cpar-ch1-card1.png',
      'https://storage.sparc.org.bd/notes/cpar-ch1-card2.png'
    ],
    61,
    37,
    31,
    '[
      "CPAR-এর মূল দর্শন ও ঐতিহ্যগত অ্যাকশন রিসার্চের পার্থক্য ব্যাখ্যা করতে পারা",
      "Sayings, Doings ও Relatings-এর আলোকে প্র্যাকটিস বিশ্লেষণ করতে পারা",
      "নিজের কর্মক্ষেত্র বা সমাজ থেকে একটি বাস্তব Felt-concern ও Research Site নির্ধারণ করতে পারা"
    ]'::jsonb,
    'আপনার পরিচিত কোনো শিক্ষা প্রতিষ্ঠান বা সামাজিক প্রেক্ষাপট থেকে একটি সুনির্দিষ্ট "Felt-concern and site statement" (৫০০–৭০০ শব্দে, বাংলা বা ইংরেজিতে) লিখুন। কেন এই সমস্যাটি অন্যায্য বা অটেকসই এবং কারা এতে অংশগ্রহণকারী হবেন তা স্পষ্ট করুন।',
    500,
    700,
    2,
    'https://storage.sparc.org.bd/slides/cpar-ch1-slide-plan.pdf'
),
-- Chapter 2 (Standard Chapter: 24 pages, 110 mins, 30 questions, 52 marks, pass 32, red flag < 26, 1 session)
(
    '22222222-2222-2222-2222-222222222202',
    '11111111-1111-1111-1111-111111111101',
    2,
    'অধ্যায় ২: Reconnaissance ও Public Sphere গঠন',
    'standard',
    24,
    110,
    '[
      {"page_range": "33-44", "estimated_minutes": 55, "difficulty": "medium"},
      {"page_range": "45-56", "estimated_minutes": 55, "difficulty": "medium"}
    ]'::jsonb,
    '[
      {"id": "sec-2-1", "title": "2.1 অংশগ্রহণকারীদের সাথে Communicative Space খোলা"},
      {"id": "sec-2-2", "title": "2.2 প্র্যাকটিসের ঐতিহাসিক ও প্রাতিষ্ঠানিক শর্ত বিশ্লেষণ"},
      {"id": "sec-2-3", "title": "2.3 প্রাথমিক গবেষণা প্রশ্ন পরিমার্জন"}
    ]'::jsonb,
    'https://storage.sparc.org.bd/audio/cpar-ch2-bn-summary.mp3',
    340,
    ARRAY['https://storage.sparc.org.bd/notes/cpar-ch2-card1.png'],
    52,
    32,
    26,
    '[
      "Communicative Action ও Public Sphere-এর বৈশিষ্ট্য চিহ্নিত করতে পারা",
      "স্টেকহোল্ডারদের অংশগ্রহণে Reconnaissance পরিকল্পনা তৈরি করতে পারা"
    ]'::jsonb,
    'আপনার ১ম অধ্যায়ে চিহ্নিত সাইটের জন্য "Communicative Space & Participant Reconnaissance Plan" (৫০০–৭০০ শব্দে) তৈরি করুন।',
    500,
    700,
    1,
    'https://storage.sparc.org.bd/slides/cpar-ch2-slide-plan.pdf'
)
ON CONFLICT (id) DO NOTHING;

-- Sample Auto-Scored (A-E) & Supervisor-Scored (F-J) Questions + Locked Answer Keys for Chapter 1
INSERT INTO public.chapter_questions (
    id, chapter_id, question_no, section_code, category, is_auto_scored,
    is_genuineness_check, max_marks, prompt_bn, prompt_en, options, learning_outcome_index
) VALUES
(
    '33333333-3333-3333-3333-333333333301',
    '22222222-2222-2222-2222-222222222201',
    1, 'C', 'mcq', TRUE, FALSE, 1,
    'Kemmis et al.-এর মতে প্র্যাকটিসকে ধরে রাখে এমন তিনটি আন্তঃসম্পর্কিত মাত্রা কী কী?',
    'According to Kemmis et al., what are the three interrelated dimensions that compose a practice?',
    '["Sayings, Doings, and Relatings", "Inputs, Processes, and Outputs", "Theory, Survey, and Report", "Planning, Coding, and Publishing"]'::jsonb,
    1
),
(
    '33333333-3333-3333-3333-333333333302',
    '22222222-2222-2222-2222-222222222201',
    2, 'D', 'true_false', TRUE, TRUE, 1,
    'বইয়ের ১ম অধ্যায়ের উদাহরণ অনুযায়ী, CPAR শুধুমাত্র বহিরাগত বিশেষজ্ঞদের দ্বারা পরিচালিত হয় এবং স্থানীয় অংশগ্রহণকারীদের কোনো ভূমিকা থাকে না।',
    'According to Chapter 1, CPAR is conducted exclusively by external experts with no role for local participants.',
    '["সত্য (True)", "মিথ্যা (False)"]'::jsonb,
    0
),
(
    '33333333-3333-3333-3333-333333333303',
    '22222222-2222-2222-2222-222222222201',
    3, 'H', 'scenario', FALSE, FALSE, 3,
    'ধরুন একটি বিশ্ববিদ্যালয়ের বিভাগে শিক্ষার্থীদের উপস্থিতি কমছে। CPAR পদ্ধতিতে Sayings, Doings ও Relatings ব্যবহার করে কীভাবে সমস্যাটির প্রাথমিক অনুসন্ধান করবেন?',
    'Suppose attendance is dropping in a university department. How would you investigate this using Sayings, Doings, and Relatings in CPAR?',
    '[]'::jsonb,
    2
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.chapter_answer_keys (
    question_id, correct_answer, explanation_bn, explanation_en, supervisor_model_points
) VALUES
(
    '33333333-3333-3333-3333-333333333301',
    '"Sayings, Doings, and Relatings"'::jsonb,
    'প্র্যাকটিস আর্কিটেকচার তত্ত্বে সাংস্কৃতিক-ডিসকার্সিভ (Sayings), বস্তুগত-অর্থনৈতিক (Doings) এবং সামাজিক-রাজনৈতিক (Relatings) মাত্রা একত্রে প্র্যাকটিস গঠন করে।',
    'Practice architectures hold practices in place through sayings, doings, and relatings.',
    '[]'::jsonb
),
(
    '33333333-3333-3333-3333-333333333302',
    '"মিথ্যা (False)"'::jsonb,
    'CPAR-এ খোদ অংশগ্রহণকারীরাই (Insiders) নিজেদের প্র্যাকটিস নিয়ে যৌথভাবে গবেষণা ও পরিবর্তন সাধন করেন।',
    'In CPAR, insiders/participants themselves research and transform their shared practices.',
    '[]'::jsonb
),
(
    '33333333-3333-3333-3333-333333333303',
    'null'::jsonb,
    'সুপারভাইজার নিচের মডেল পয়েন্টগুলোর ভিত্তিতে ০–৩ নম্বর প্রদান করবেন।',
    'Graded 0-3 by Supervisor against model points.',
    '[
      "১ পয়েন্ট: শ্রেণিকক্ষ ও বিভাগের প্রচলিত আলোচনা/ভাষা (Sayings) চিহ্নিত করা",
      "১ পয়েন্ট: ক্লাসরুমের সময়সূচি, রিসোর্স ও পাঠদান কার্যক্রম (Doings) বিশ্লেষণ করা",
      "১ পয়েন্ট: শিক্ষক-শিক্ষার্থী সম্পর্ক ও অংশগ্রহণমূলক Communicative Space (Relatings) উল্লেখ করা"
    ]'::jsonb
)
ON CONFLICT (question_id) DO NOTHING;
