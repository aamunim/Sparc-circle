-- ============================================================================
-- SPARC Study Circle (v3.5 Cloud PDF & Audio Summary Storage)
-- Migration 006: Supabase Storage Buckets, Policies & Book/Audio Media RPCs
-- ============================================================================

-- 1. Create Public-Readable, Authenticated-Uploadable Storage Buckets
INSERT INTO storage.buckets (id, name, public)
VALUES
    ('sparc-books', 'sparc-books', TRUE),
    ('sparc-audio', 'sparc-audio', TRUE)
ON CONFLICT (id) DO UPDATE SET public = TRUE;

-- 2. Storage Object RLS Policies (Public CDN Stream/Download + Authenticated Upload)
DROP POLICY IF EXISTS sparc_media_public_read ON storage.objects;
CREATE POLICY sparc_media_public_read ON storage.objects
    FOR SELECT TO anon, authenticated
    USING (bucket_id IN ('sparc-books', 'sparc-audio'));

DROP POLICY IF EXISTS sparc_media_auth_insert ON storage.objects;
CREATE POLICY sparc_media_auth_insert ON storage.objects
    FOR INSERT TO authenticated
    WITH CHECK (bucket_id IN ('sparc-books', 'sparc-audio'));

DROP POLICY IF EXISTS sparc_media_auth_update ON storage.objects;
CREATE POLICY sparc_media_auth_update ON storage.objects
    FOR UPDATE TO authenticated
    USING (bucket_id IN ('sparc-books', 'sparc-audio'))
    WITH CHECK (bucket_id IN ('sparc-books', 'sparc-audio'));

DROP POLICY IF EXISTS sparc_media_auth_delete ON storage.objects;
CREATE POLICY sparc_media_auth_delete ON storage.objects
    FOR DELETE TO authenticated
    USING (bucket_id IN ('sparc-books', 'sparc-audio'));

-- 3. Clear Non-Resolvable Placeholder Domain URLs on Seeded Rows
UPDATE public.books
SET pdf_url = ''
WHERE pdf_url LIKE '%storage.sparc.org.bd%';

UPDATE public.chapters
SET audio_summary_url = NULL
WHERE audio_summary_url LIKE '%storage.sparc.org.bd%';

-- 4. RPC: Attach Uploaded Cloud PDF and/or Bangla Audio Summary to Existing Book/Chapter
CREATE OR REPLACE FUNCTION public.attach_book_and_audio_media_rpc(
    p_book_id UUID,
    p_chapter_no INTEGER DEFAULT 1,
    p_pdf_url TEXT DEFAULT NULL,
    p_audio_url TEXT DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
    v_ch_id UUID;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Authentication required';
    END IF;

    IF p_pdf_url IS NOT NULL AND TRIM(p_pdf_url) <> '' THEN
        UPDATE public.books
        SET pdf_url = TRIM(p_pdf_url)
        WHERE id = p_book_id;
    END IF;

    SELECT id INTO v_ch_id
    FROM public.chapters
    WHERE book_id = p_book_id AND chapter_no = COALESCE(p_chapter_no, 1)
    LIMIT 1;

    IF v_ch_id IS NULL THEN
        INSERT INTO public.chapters (
            book_id, chapter_no, title, type, total_pages, default_reading_minutes,
            total_marks, pass_marks, red_flag_marks, writing_task_prompt, audio_summary_url
        )
        VALUES (
            p_book_id, COALESCE(p_chapter_no, 1),
            'অধ্যায় ' || COALESCE(p_chapter_no, 1),
            'standard', 24, 110, 52, 32, 26,
            'এই অধ্যায়ের মূল ধারণা ও আপনার গবেষণা ক্ষেত্রের সংযোগ নিয়ে ৫০০–৭০০ শব্দে লিখুন।',
            NULLIF(TRIM(COALESCE(p_audio_url, '')), '')
        )
        RETURNING id INTO v_ch_id;
    ELSIF p_audio_url IS NOT NULL AND TRIM(p_audio_url) <> '' THEN
        UPDATE public.chapters
        SET audio_summary_url = TRIM(p_audio_url)
        WHERE id = v_ch_id;
    END IF;

    RETURN jsonb_build_object(
        'updated', TRUE,
        'book_id', p_book_id,
        'chapter_id', v_ch_id
    );
END;
$$;

-- 5. RPC: Publish a Brand-New Book + Chapter 1 + Cloud PDF & Audio Summary
CREATE OR REPLACE FUNCTION public.publish_new_book_with_media_rpc(
    p_title TEXT,
    p_author TEXT,
    p_category TEXT DEFAULT 'Research & Pedagogy',
    p_price_bdt NUMERIC DEFAULT 450.00,
    p_chapter_title TEXT DEFAULT 'অধ্যায় ১: সূচনা ও মূল ধারণা',
    p_total_pages INTEGER DEFAULT 28,
    p_pdf_url TEXT DEFAULT '',
    p_audio_url TEXT DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
    v_book_id UUID;
    v_ch1_id UUID;
    v_q1_id UUID;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Authentication required';
    END IF;
    IF p_title IS NULL OR TRIM(p_title) = '' THEN
        RAISE EXCEPTION 'Book title is required';
    END IF;

    INSERT INTO public.books (
        title, subtitle, author, category, pdf_url,
        physical_price_bdt, total_chapters, is_first_chapter_free,
        min_members_default, copyright_verified, is_published
    )
    VALUES (
        TRIM(p_title),
        '১ম অধ্যায় ও অডিও সামারি ফ্রি',
        COALESCE(NULLIF(TRIM(p_author), ''), 'SPARC Author'),
        COALESCE(NULLIF(TRIM(p_category), ''), 'Research & Pedagogy'),
        COALESCE(TRIM(p_pdf_url), ''),
        GREATEST(COALESCE(p_price_bdt, 450), 0),
        7,
        TRUE,
        5,
        TRUE,
        TRUE
    )
    RETURNING id INTO v_book_id;

    INSERT INTO public.chapters (
        book_id, chapter_no, title, type, total_pages, default_reading_minutes,
        audio_summary_url, total_marks, pass_marks, red_flag_marks,
        learning_outcomes, writing_task_prompt
    )
    VALUES (
        v_book_id,
        1,
        COALESCE(NULLIF(TRIM(p_chapter_title), ''), 'অধ্যায় ১: সূচনা ও মূল ধারণা'),
        'standard',
        GREATEST(COALESCE(p_total_pages, 28), 1),
        120,
        NULLIF(TRIM(COALESCE(p_audio_url, '')), ''),
        52,
        32,
        26,
        '["মূল গবেষণা ধারণা ব্যাখ্যা করতে পারা", "নিজের গবেষণা প্রপোজালে প্রয়োগ করতে পারা"]'::jsonb,
        'এই বইয়ের ১ম অধ্যায়ের আলোকে ৫০০–৭০০ শব্দে আপনার গবেষণার সমস্যা ও সাইট বিবৃতি লিখুন।'
    )
    RETURNING id INTO v_ch1_id;

    INSERT INTO public.chapter_questions (
        chapter_id, question_no, section_code, category, is_auto_scored,
        is_genuineness_check, max_marks, prompt_bn, prompt_en, options
    )
    VALUES (
        v_ch1_id, 1, 'C', 'mcq', TRUE, FALSE, 1,
        'এই অধ্যায়ের মূল উদ্দেশ্য কোনটি?',
        'What is the primary objective of this chapter?',
        '["অংশগ্রহণমূলক গবেষণা ও একাডেমিক রাইটিং দক্ষতা অর্জন", "শুধু মুখস্থ করা", "কোনোটিই নয়"]'::jsonb
    )
    RETURNING id INTO v_q1_id;

    INSERT INTO public.chapter_answer_keys (question_id, correct_answer, explanation_bn, explanation_en)
    VALUES (
        v_q1_id,
        '"অংশগ্রহণমূলক গবেষণা ও একাডেমিক রাইটিং দক্ষতা অর্জন"'::jsonb,
        'বই পড়তে পড়তে গবেষণা লেখা শেখাই SPARC Study Circle-এর মূল দর্শন।',
        'Learning academic writing while reading is the core philosophy of SPARC Study Circle.'
    );

    RETURN jsonb_build_object(
        'book_id', v_book_id,
        'chapter_id', v_ch1_id
    );
END;
$$;
