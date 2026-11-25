-- ============================================================================
-- SPARC Study Circle (v3.6 Complete Customization Suite — Packs 1 to 5)
-- Migration 007: Dynamic Brand/Payment Config, Rich Profiles & Avatars,
--                In-App Curriculum/Quiz Builder & Custom Circle Rules/Privacy
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. Storage Bucket for Profile Photo Avatars + Unified Media RLS Policies
-- ----------------------------------------------------------------------------
INSERT INTO storage.buckets (id, name, public)
VALUES
    ('sparc-books', 'sparc-books', TRUE),
    ('sparc-audio', 'sparc-audio', TRUE),
    ('sparc-avatars', 'sparc-avatars', TRUE)
ON CONFLICT (id) DO UPDATE SET public = TRUE;

DROP POLICY IF EXISTS sparc_media_public_read ON storage.objects;
CREATE POLICY sparc_media_public_read ON storage.objects
    FOR SELECT TO anon, authenticated
    USING (bucket_id IN ('sparc-books', 'sparc-audio', 'sparc-avatars'));

DROP POLICY IF EXISTS sparc_media_auth_insert ON storage.objects;
CREATE POLICY sparc_media_auth_insert ON storage.objects
    FOR INSERT TO authenticated
    WITH CHECK (bucket_id IN ('sparc-books', 'sparc-audio', 'sparc-avatars'));

DROP POLICY IF EXISTS sparc_media_auth_update ON storage.objects;
CREATE POLICY sparc_media_auth_update ON storage.objects
    FOR UPDATE TO authenticated
    USING (bucket_id IN ('sparc-books', 'sparc-audio', 'sparc-avatars'))
    WITH CHECK (bucket_id IN ('sparc-books', 'sparc-audio', 'sparc-avatars'));

DROP POLICY IF EXISTS sparc_media_auth_delete ON storage.objects;
CREATE POLICY sparc_media_auth_delete ON storage.objects
    FOR DELETE TO authenticated
    USING (bucket_id IN ('sparc-books', 'sparc-audio', 'sparc-avatars'));

-- ----------------------------------------------------------------------------
-- 2. Pack 1: Organization Brand, Notice Banner & Payment Config Table + RPC
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.app_customization (
    id TEXT PRIMARY KEY DEFAULT 'global',
    app_title TEXT NOT NULL DEFAULT 'SPARC Study Circle',
    app_tagline_bn TEXT NOT NULL DEFAULT 'বই পড়তে পড়তে লেখা শেখা',
    app_tagline_en TEXT NOT NULL DEFAULT 'Learning Academic Writing While Reading',
    splash_quote_bn TEXT NOT NULL DEFAULT 'যৌথ পাঠ, গভীর চিন্তা ও একাডেমিক গবেষণার সমন্বিত প্ল্যাটফর্ম',
    splash_quote_en TEXT NOT NULL DEFAULT 'Collaborative reading, critical inquiry & scholarly writing',
    announcement_banner_bn TEXT NOT NULL DEFAULT '📢 নতুন কোহর্টে ভর্তি ও ১ম অধ্যায়ের ফ্রি রিডিং চলছে!',
    announcement_banner_en TEXT NOT NULL DEFAULT '📢 New cohort enrollment & free Chapter 1 reading are open!',
    show_announcement_banner BOOLEAN NOT NULL DEFAULT TRUE,
    bkash_merchant_number TEXT NOT NULL DEFAULT '01700-000000',
    nagad_merchant_number TEXT NOT NULL DEFAULT '01800-000000',
    rocket_merchant_number TEXT NOT NULL DEFAULT '01900-000000',
    default_supervision_fee_bdt NUMERIC(10,2) NOT NULL DEFAULT 300.00,
    borrow_deposit_bdt NUMERIC(10,2) NOT NULL DEFAULT 150.00,
    min_topup_bdt NUMERIC(10,2) NOT NULL DEFAULT 500.00,
    updated_by UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO public.app_customization (id)
VALUES ('global')
ON CONFLICT (id) DO NOTHING;

ALTER TABLE public.app_customization ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS app_customization_select_all ON public.app_customization;
CREATE POLICY app_customization_select_all ON public.app_customization
    FOR SELECT TO anon, authenticated
    USING (TRUE);

CREATE OR REPLACE FUNCTION public.upsert_app_customization_rpc(
    p_app_title TEXT,
    p_app_tagline_bn TEXT,
    p_app_tagline_en TEXT,
    p_splash_quote_bn TEXT,
    p_splash_quote_en TEXT,
    p_announcement_bn TEXT,
    p_announcement_en TEXT,
    p_show_announcement BOOLEAN,
    p_bkash_number TEXT,
    p_nagad_number TEXT,
    p_rocket_number TEXT,
    p_supervision_fee_bdt NUMERIC,
    p_borrow_deposit_bdt NUMERIC,
    p_min_topup_bdt NUMERIC
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Authentication required';
    END IF;

    INSERT INTO public.app_customization (
        id, app_title, app_tagline_bn, app_tagline_en,
        splash_quote_bn, splash_quote_en,
        announcement_banner_bn, announcement_banner_en, show_announcement_banner,
        bkash_merchant_number, nagad_merchant_number, rocket_merchant_number,
        default_supervision_fee_bdt, borrow_deposit_bdt, min_topup_bdt,
        updated_by, updated_at
    )
    VALUES (
        'global',
        COALESCE(NULLIF(TRIM(p_app_title), ''), 'SPARC Study Circle'),
        COALESCE(NULLIF(TRIM(p_app_tagline_bn), ''), 'বই পড়তে পড়তে লেখা শেখা'),
        COALESCE(NULLIF(TRIM(p_app_tagline_en), ''), 'Learning Academic Writing While Reading'),
        COALESCE(NULLIF(TRIM(p_splash_quote_bn), ''), 'যৌথ পাঠ, গভীর চিন্তা ও একাডেমিক গবেষণার সমন্বিত প্ল্যাটফর্ম'),
        COALESCE(NULLIF(TRIM(p_splash_quote_en), ''), 'Collaborative reading, critical inquiry & scholarly writing'),
        COALESCE(TRIM(p_announcement_bn), ''),
        COALESCE(TRIM(p_announcement_en), ''),
        COALESCE(p_show_announcement, TRUE),
        COALESCE(NULLIF(TRIM(p_bkash_number), ''), '01700-000000'),
        COALESCE(NULLIF(TRIM(p_nagad_number), ''), '01800-000000'),
        COALESCE(NULLIF(TRIM(p_rocket_number), ''), '01900-000000'),
        GREATEST(COALESCE(p_supervision_fee_bdt, 300), 0),
        GREATEST(COALESCE(p_borrow_deposit_bdt, 150), 0),
        GREATEST(COALESCE(p_min_topup_bdt, 100), 50),
        v_uid,
        NOW()
    )
    ON CONFLICT (id) DO UPDATE SET
        app_title = EXCLUDED.app_title,
        app_tagline_bn = EXCLUDED.app_tagline_bn,
        app_tagline_en = EXCLUDED.app_tagline_en,
        splash_quote_bn = EXCLUDED.splash_quote_bn,
        splash_quote_en = EXCLUDED.splash_quote_en,
        announcement_banner_bn = EXCLUDED.announcement_banner_bn,
        announcement_banner_en = EXCLUDED.announcement_banner_en,
        show_announcement_banner = EXCLUDED.show_announcement_banner,
        bkash_merchant_number = EXCLUDED.bkash_merchant_number,
        nagad_merchant_number = EXCLUDED.nagad_merchant_number,
        rocket_merchant_number = EXCLUDED.rocket_merchant_number,
        default_supervision_fee_bdt = EXCLUDED.default_supervision_fee_bdt,
        borrow_deposit_bdt = EXCLUDED.borrow_deposit_bdt,
        min_topup_bdt = EXCLUDED.min_topup_bdt,
        updated_by = v_uid,
        updated_at = NOW();

    RETURN jsonb_build_object('saved', TRUE);
END;
$$;

-- ----------------------------------------------------------------------------
-- 3. Pack 2: Rich Researcher Profile Columns (Institution, Bio, Daily Goal)
-- ----------------------------------------------------------------------------
ALTER TABLE public.profiles
    ADD COLUMN IF NOT EXISTS institution TEXT NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS bio TEXT NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS daily_reading_goal_minutes INTEGER NOT NULL DEFAULT 30;

-- ----------------------------------------------------------------------------
-- 4. Pack 3: In-App Chapter, Flash Notes & Quiz Customizer RPCs
-- ----------------------------------------------------------------------------
ALTER TABLE public.chapters
    ADD COLUMN IF NOT EXISTS flash_notes JSONB NOT NULL DEFAULT '[]'::jsonb;

UPDATE public.chapters
SET flash_notes = '[
  "CPAR-এ গবেষক বাইরে থেকে দর্শক নন; অংশগ্রহণকারীরাই নিজেদের প্র্যাকটিস রূপান্তর করেন।",
  "Practice Architectures তিনটি মাত্রায় গঠিত: Sayings (ভাষা/চিন্তা), Doings (কাজ/উপকরণ), Relatings (ক্ষমতা/সম্পর্ক)।",
  "Communicative Space এমন একটি উন্মুক্ত পরিসর যেখানে সবাই নির্ভয়ে সমালোচনামূলক মতামত দিতে পারেন।"
]'::jsonb
WHERE flash_notes = '[]'::jsonb;

CREATE OR REPLACE FUNCTION public.upsert_chapter_curriculum_rpc(
    p_book_id UUID,
    p_chapter_no INTEGER,
    p_title TEXT,
    p_type TEXT DEFAULT 'standard',
    p_total_pages INTEGER DEFAULT 24,
    p_flash_notes JSONB DEFAULT '[]'::jsonb,
    p_learning_outcomes JSONB DEFAULT '[]'::jsonb,
    p_writing_prompt TEXT DEFAULT '',
    p_min_words INTEGER DEFAULT 500,
    p_max_words INTEGER DEFAULT 700
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
    v_ch_type chapter_type;
    v_tot_marks INTEGER;
    v_pass_marks INTEGER;
    v_red_marks INTEGER;
    v_ch_id UUID;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Authentication required';
    END IF;

    v_ch_type := CASE WHEN LOWER(COALESCE(p_type, 'standard')) = 'long' THEN 'long'::chapter_type ELSE 'standard'::chapter_type END;
    v_tot_marks := CASE WHEN v_ch_type = 'long' THEN 61 ELSE 52 END;
    v_pass_marks := CASE WHEN v_ch_type = 'long' THEN 37 ELSE 32 END;
    v_red_marks := CASE WHEN v_ch_type = 'long' THEN 31 ELSE 26 END;

    INSERT INTO public.chapters (
        book_id, chapter_no, title, type, total_pages, default_reading_minutes,
        total_marks, pass_marks, red_flag_marks,
        flash_notes, learning_outcomes,
        writing_task_prompt, writing_min_words, writing_max_words
    )
    VALUES (
        p_book_id,
        GREATEST(COALESCE(p_chapter_no, 1), 1),
        COALESCE(NULLIF(TRIM(p_title), ''), 'অধ্যায় ' || GREATEST(COALESCE(p_chapter_no, 1), 1)),
        v_ch_type,
        GREATEST(COALESCE(p_total_pages, 24), 1),
        GREATEST(COALESCE(p_total_pages, 24) * 4, 30),
        v_tot_marks,
        v_pass_marks,
        v_red_marks,
        COALESCE(p_flash_notes, '[]'::jsonb),
        COALESCE(p_learning_outcomes, '[]'::jsonb),
        COALESCE(NULLIF(TRIM(p_writing_prompt), ''), 'এই অধ্যায়ের মূল ধারণার আলোকে একাডেমিক রাইটিং ড্রাফট লিখুন।'),
        GREATEST(COALESCE(p_min_words, 500), 50),
        GREATEST(COALESCE(p_max_words, 700), GREATEST(COALESCE(p_min_words, 500), 50) + 50)
    )
    ON CONFLICT (book_id, chapter_no) DO UPDATE SET
        title = EXCLUDED.title,
        type = EXCLUDED.type,
        total_pages = EXCLUDED.total_pages,
        total_marks = EXCLUDED.total_marks,
        pass_marks = EXCLUDED.pass_marks,
        red_flag_marks = EXCLUDED.red_flag_marks,
        flash_notes = EXCLUDED.flash_notes,
        learning_outcomes = EXCLUDED.learning_outcomes,
        writing_task_prompt = EXCLUDED.writing_task_prompt,
        writing_min_words = EXCLUDED.writing_min_words,
        writing_max_words = EXCLUDED.writing_max_words
    RETURNING id INTO v_ch_id;

    UPDATE public.books
    SET total_chapters = GREATEST(total_chapters, GREATEST(COALESCE(p_chapter_no, 1), 1))
    WHERE id = p_book_id;

    RETURN jsonb_build_object(
        'chapter_id', v_ch_id,
        'book_id', p_book_id,
        'chapter_no', GREATEST(COALESCE(p_chapter_no, 1), 1)
    );
END;
$$;

CREATE OR REPLACE FUNCTION public.upsert_chapter_question_rpc(
    p_book_id UUID,
    p_chapter_no INTEGER,
    p_is_auto_scored BOOLEAN,
    p_section_code TEXT,
    p_prompt_bn TEXT,
    p_options JSONB DEFAULT '[]'::jsonb,
    p_correct_answer TEXT DEFAULT '',
    p_explanation_bn TEXT DEFAULT '',
    p_model_points JSONB DEFAULT '[]'::jsonb
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
    v_ch_id UUID;
    v_next_qno INTEGER;
    v_sec CHAR(1);
    v_cat question_category;
    v_marks INTEGER;
    v_qid UUID;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Authentication required';
    END IF;
    IF p_prompt_bn IS NULL OR TRIM(p_prompt_bn) = '' THEN
        RAISE EXCEPTION 'Question prompt is required';
    END IF;

    SELECT id INTO v_ch_id
    FROM public.chapters
    WHERE book_id = p_book_id AND chapter_no = GREATEST(COALESCE(p_chapter_no, 1), 1)
    LIMIT 1;

    IF v_ch_id IS NULL THEN
        RAISE EXCEPTION 'Chapter not found for book';
    END IF;

    SELECT COALESCE(MAX(question_no), 0) + 1 INTO v_next_qno
    FROM public.chapter_questions
    WHERE chapter_id = v_ch_id;

    IF COALESCE(p_is_auto_scored, TRUE) THEN
        v_sec := CASE WHEN UPPER(LEFT(COALESCE(p_section_code, 'C'), 1)) IN ('A','B','C','D','E')
                      THEN UPPER(LEFT(p_section_code, 1))
                      ELSE 'C' END;
        v_cat := 'mcq'::question_category;
        v_marks := 1;
    ELSE
        v_sec := CASE WHEN UPPER(LEFT(COALESCE(p_section_code, 'H'), 1)) IN ('F','G','H','I','J')
                      THEN UPPER(LEFT(p_section_code, 1))
                      ELSE 'H' END;
        v_cat := 'scenario'::question_category;
        v_marks := 3;
    END IF;

    INSERT INTO public.chapter_questions (
        chapter_id, question_no, section_code, category,
        is_auto_scored, is_genuineness_check, max_marks,
        prompt_bn, prompt_en, options
    )
    VALUES (
        v_ch_id,
        v_next_qno,
        v_sec,
        v_cat,
        COALESCE(p_is_auto_scored, TRUE),
        FALSE,
        v_marks,
        TRIM(p_prompt_bn),
        TRIM(p_prompt_bn),
        COALESCE(p_options, '[]'::jsonb)
    )
    RETURNING id INTO v_qid;

    INSERT INTO public.chapter_answer_keys (
        question_id, correct_answer, explanation_bn, explanation_en, supervisor_model_points
    )
    VALUES (
        v_qid,
        to_jsonb(COALESCE(p_correct_answer, '')),
        COALESCE(p_explanation_bn, ''),
        COALESCE(p_explanation_bn, ''),
        COALESCE(p_model_points, '[]'::jsonb)
    )
    ON CONFLICT (question_id) DO UPDATE SET
        correct_answer = EXCLUDED.correct_answer,
        explanation_bn = EXCLUDED.explanation_bn,
        supervisor_model_points = EXCLUDED.supervisor_model_points;

    RETURN jsonb_build_object(
        'question_id', v_qid,
        'question_no', v_next_qno
    );
END;
$$;

-- ----------------------------------------------------------------------------
-- 5. Pack 4: Study Circle Rules, Privacy, Invite Code & Schedule Customization
-- ----------------------------------------------------------------------------
ALTER TABLE public.study_circles
    ADD COLUMN IF NOT EXISTS is_private BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS invite_code TEXT,
    ADD COLUMN IF NOT EXISTS max_members INTEGER NOT NULL DEFAULT 25,
    ADD COLUMN IF NOT EXISTS session_schedule_label TEXT NOT NULL DEFAULT 'শুক্রবার রাত ৮:৩০',
    ADD COLUMN IF NOT EXISTS default_meeting_url TEXT NOT NULL DEFAULT 'https://meet.jit.si/SPARC_Circle_Live',
    ADD COLUMN IF NOT EXISTS circle_rules TEXT NOT NULL DEFAULT 'নিয়মিত অধ্যায় পড়া ও সহপাঠীদের প্রতি শ্রদ্ধাশীল থেকে আলোচনায় অংশ নেওয়া।';

CREATE OR REPLACE FUNCTION public.create_custom_circle_rpc(
    p_name TEXT,
    p_book_id UUID,
    p_min_members INTEGER DEFAULT 5,
    p_max_members INTEGER DEFAULT 25,
    p_is_private BOOLEAN DEFAULT FALSE,
    p_invite_code TEXT DEFAULT NULL,
    p_schedule_label TEXT DEFAULT 'শুক্রবার রাত ৮:৩০',
    p_meeting_url TEXT DEFAULT 'https://meet.jit.si/SPARC_Circle_Live',
    p_rules TEXT DEFAULT ''
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
    v_circle_id UUID;
    v_ch1_id UUID;
    v_code TEXT;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Authentication required';
    END IF;
    IF p_name IS NULL OR TRIM(p_name) = '' THEN
        RAISE EXCEPTION 'Circle name required';
    END IF;

    v_code := CASE
        WHEN COALESCE(p_is_private, FALSE) THEN
            COALESCE(NULLIF(UPPER(TRIM(p_invite_code)), ''), 'SPARC-' || UPPER(SUBSTRING(MD5(RANDOM()::TEXT) FROM 1 FOR 5)))
        ELSE NULL
    END;

    INSERT INTO public.study_circles (
        name, book_id, created_by, min_members_required, max_members,
        status, current_group_chapter,
        is_private, invite_code, session_schedule_label, default_meeting_url, circle_rules
    )
    VALUES (
        TRIM(p_name),
        p_book_id,
        v_uid,
        GREATEST(COALESCE(p_min_members, 5), 2),
        GREATEST(COALESCE(p_max_members, 25), GREATEST(COALESCE(p_min_members, 5), 2)),
        'forming',
        1,
        COALESCE(p_is_private, FALSE),
        v_code,
        COALESCE(NULLIF(TRIM(p_schedule_label), ''), 'শুক্রবার রাত ৮:৩০'),
        COALESCE(NULLIF(TRIM(p_meeting_url), ''), 'https://meet.jit.si/SPARC_Circle_Live'),
        COALESCE(NULLIF(TRIM(p_rules), ''), 'নিয়মিত অধ্যায় পড়া ও সহপাঠীদের প্রতি শ্রদ্ধাশীল থেকে আলোচনায় অংশ নেওয়া।')
    )
    RETURNING id INTO v_circle_id;

    INSERT INTO public.study_circle_members (circle_id, user_id, invite_status)
    VALUES (v_circle_id, v_uid, 'joined')
    ON CONFLICT (circle_id, user_id) DO UPDATE SET invite_status = 'joined';

    SELECT id INTO v_ch1_id
    FROM public.chapters
    WHERE book_id = p_book_id AND chapter_no = 1
    LIMIT 1;

    IF v_ch1_id IS NOT NULL THEN
        INSERT INTO public.chapter_progress (
            circle_id, user_id, chapter_id, chapter_no, status,
            reading_started_at, expected_completion_date, daily_target_pages, target_days
        )
        VALUES (
            v_circle_id, v_uid, v_ch1_id, 1, 'Reading Started',
            NOW(), (CURRENT_DATE + INTERVAL '8 days')::DATE, 4, 8
        )
        ON CONFLICT (circle_id, user_id, chapter_no) DO NOTHING;
    END IF;

    RETURN jsonb_build_object(
        'circle_id', v_circle_id,
        'invite_code', v_code
    );
END;
$$;

CREATE OR REPLACE FUNCTION public.update_circle_customization_rpc(
    p_circle_id UUID,
    p_name TEXT,
    p_min_members INTEGER,
    p_max_members INTEGER,
    p_is_private BOOLEAN,
    p_invite_code TEXT,
    p_schedule_label TEXT,
    p_meeting_url TEXT,
    p_rules TEXT,
    p_current_chapter INTEGER
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
    v_code TEXT;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Authentication required';
    END IF;

    v_code := CASE
        WHEN COALESCE(p_is_private, FALSE) THEN
            COALESCE(NULLIF(UPPER(TRIM(p_invite_code)), ''), 'SPARC-' || UPPER(SUBSTRING(MD5(RANDOM()::TEXT) FROM 1 FOR 5)))
        ELSE NULL
    END;

    UPDATE public.study_circles
    SET
        name = COALESCE(NULLIF(TRIM(p_name), ''), name),
        min_members_required = GREATEST(COALESCE(p_min_members, min_members_required), 2),
        max_members = GREATEST(COALESCE(p_max_members, max_members), GREATEST(COALESCE(p_min_members, 2), 2)),
        is_private = COALESCE(p_is_private, FALSE),
        invite_code = v_code,
        session_schedule_label = COALESCE(NULLIF(TRIM(p_schedule_label), ''), session_schedule_label),
        default_meeting_url = COALESCE(NULLIF(TRIM(p_meeting_url), ''), default_meeting_url),
        circle_rules = COALESCE(NULLIF(TRIM(p_rules), ''), circle_rules),
        current_group_chapter = GREATEST(COALESCE(p_current_chapter, current_group_chapter), 1)
    WHERE id = p_circle_id;

    RETURN jsonb_build_object('updated', TRUE, 'invite_code', v_code);
END;
$$;

CREATE OR REPLACE FUNCTION public.join_circle_with_code_rpc(
    p_circle_id UUID,
    p_invite_code TEXT DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
    v_book_id UUID;
    v_min_req INTEGER;
    v_max_mem INTEGER;
    v_is_priv BOOLEAN;
    v_code TEXT;
    v_joined_count INTEGER;
    v_already BOOLEAN;
    v_ch1_id UUID;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Authentication required';
    END IF;

    SELECT book_id, min_members_required, max_members, is_private, invite_code
    INTO v_book_id, v_min_req, v_max_mem, v_is_priv, v_code
    FROM public.study_circles
    WHERE id = p_circle_id;

    IF v_book_id IS NULL THEN
        RAISE EXCEPTION 'Study circle not found';
    END IF;

    SELECT EXISTS (
        SELECT 1 FROM public.study_circle_members
        WHERE circle_id = p_circle_id AND user_id = v_uid AND invite_status = 'joined'
    ) INTO v_already;

    IF NOT v_already THEN
        IF v_is_priv AND COALESCE(UPPER(TRIM(p_invite_code)), '') <> COALESCE(UPPER(TRIM(v_code)), '') THEN
            RAISE EXCEPTION 'প্রাইভেট সার্কেলে জয়েন করার সঠিক Invite Code দিন (Invalid Invite Code)';
        END IF;

        SELECT COUNT(*) INTO v_joined_count
        FROM public.study_circle_members
        WHERE circle_id = p_circle_id AND invite_status = 'joined';

        IF v_joined_count >= COALESCE(v_max_mem, 30) THEN
            RAISE EXCEPTION 'এই সার্কেলের সদস্য সীমা পূর্ণ হয়ে গেছে (Circle is full)';
        END IF;

        INSERT INTO public.study_circle_members (circle_id, user_id, invite_status)
        VALUES (p_circle_id, v_uid, 'joined')
        ON CONFLICT (circle_id, user_id) DO UPDATE SET invite_status = 'joined';
    END IF;

    SELECT COUNT(*) INTO v_joined_count
    FROM public.study_circle_members
    WHERE circle_id = p_circle_id AND invite_status = 'joined';

    IF v_joined_count >= COALESCE(v_min_req, 5) THEN
        UPDATE public.study_circles
        SET status = 'active'
        WHERE id = p_circle_id AND status = 'forming';
    END IF;

    SELECT id INTO v_ch1_id
    FROM public.chapters
    WHERE book_id = v_book_id AND chapter_no = 1
    LIMIT 1;

    IF v_ch1_id IS NOT NULL THEN
        INSERT INTO public.chapter_progress (
            circle_id, user_id, chapter_id, chapter_no, status,
            reading_started_at, expected_completion_date, daily_target_pages, target_days
        )
        VALUES (
            p_circle_id, v_uid, v_ch1_id, 1, 'Reading Started',
            NOW(), (CURRENT_DATE + INTERVAL '8 days')::DATE, 4, 8
        )
        ON CONFLICT (circle_id, user_id, chapter_no) DO NOTHING;
    END IF;

    RETURN jsonb_build_object('joined', TRUE, 'member_count', v_joined_count);
END;
$$;
