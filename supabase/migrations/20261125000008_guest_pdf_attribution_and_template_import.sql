-- ============================================================================
-- SPARC Study Circle (v3.7 Guided Reading, Circle Invites & Exercise Templates)
-- Migration 008: Guest Author PDF Source Attribution & Bulk Exercise Template RPC
-- ============================================================================

-- 1. Add Source Attribution & Permission Declaration on Books
ALTER TABLE public.books
    ADD COLUMN IF NOT EXISTS source_attribution TEXT NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS permission_declared BOOLEAN NOT NULL DEFAULT TRUE;

-- 2. Upgrade publish_new_book_with_media_rpc with Author Attribution & Permission
CREATE OR REPLACE FUNCTION public.publish_new_book_with_media_rpc(
    p_title TEXT,
    p_author TEXT,
    p_category TEXT DEFAULT 'Research & Pedagogy',
    p_price_bdt NUMERIC DEFAULT 0.00,
    p_chapter_title TEXT DEFAULT 'অধ্যায় ১: সূচনা ও মূল ধারণা',
    p_total_pages INTEGER DEFAULT 24,
    p_pdf_url TEXT DEFAULT '',
    p_audio_url TEXT DEFAULT NULL,
    p_source_attribution TEXT DEFAULT '',
    p_permission_declared BOOLEAN DEFAULT TRUE
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
        RAISE EXCEPTION 'Book or paper title is required';
    END IF;

    INSERT INTO public.books (
        title, subtitle, author, category, pdf_url,
        physical_price_bdt, total_chapters, is_first_chapter_free,
        min_members_default, copyright_verified, is_published,
        source_attribution, permission_declared
    )
    VALUES (
        TRIM(p_title),
        CASE
            WHEN COALESCE(TRIM(p_source_attribution), '') <> '' THEN TRIM(p_source_attribution)
            ELSE '১ম অধ্যায় ও অডিও সামারি ফ্রি'
        END,
        COALESCE(NULLIF(TRIM(p_author), ''), 'Guest Researcher'),
        COALESCE(NULLIF(TRIM(p_category), ''), 'Research & Pedagogy'),
        COALESCE(TRIM(p_pdf_url), ''),
        GREATEST(COALESCE(p_price_bdt, 0), 0),
        1,
        TRUE,
        5,
        COALESCE(p_permission_declared, TRUE),
        TRUE,
        COALESCE(TRIM(p_source_attribution), ''),
        COALESCE(p_permission_declared, TRUE)
    )
    RETURNING id INTO v_book_id;

    INSERT INTO public.chapters (
        book_id, chapter_no, title, type, total_pages, default_reading_minutes,
        audio_summary_url, total_marks, pass_marks, red_flag_marks,
        flash_notes, learning_outcomes, writing_task_prompt
    )
    VALUES (
        v_book_id,
        1,
        COALESCE(NULLIF(TRIM(p_chapter_title), ''), 'অধ্যায় ১: সূচনা ও মূল ধারণা'),
        'standard',
        GREATEST(COALESCE(p_total_pages, 24), 1),
        90,
        NULLIF(TRIM(COALESCE(p_audio_url, '')), ''),
        52,
        32,
        26,
        '["প্রবন্ধ বা অধ্যায়ের মূল যুক্তি চিহ্নিত করুন", "নিজের গবেষণা ক্ষেত্রের সাথে তুলনা করুন"]'::jsonb,
        '["মূল গবেষণা ধারণা ব্যাখ্যা করতে পারা", "নিজের গবেষণা প্রপোজালে প্রয়োগ করতে পারা"]'::jsonb,
        'এই প্রবন্ধ/অধ্যায়ের আলোকে ৪০০–৬০০ শব্দে আপনার সমালোচনামূলক পাঠ-প্রতিক্রিয়া লিখুন।'
    )
    RETURNING id INTO v_ch1_id;

    INSERT INTO public.chapter_questions (
        chapter_id, question_no, section_code, category, is_auto_scored,
        is_genuineness_check, max_marks, prompt_bn, prompt_en, options
    )
    VALUES (
        v_ch1_id, 1, 'C', 'mcq', TRUE, FALSE, 1,
        'এই পাঠটির প্রধান একাডেমিক উদ্দেশ্য কী?',
        'What is the primary academic objective of this reading?',
        '["তাত্ত্বিক ধারণা বুঝে গবেষণায় প্রয়োগ করা", "শুধু মুখস্থ করা", "কোনোটিই নয়"]'::jsonb
    )
    RETURNING id INTO v_q1_id;

    INSERT INTO public.chapter_answer_keys (question_id, correct_answer, explanation_bn, explanation_en)
    VALUES (
        v_q1_id,
        '"তাত্ত্বিক ধারণা বুঝে গবেষণায় প্রয়োগ করা"'::jsonb,
        'পাঠের মূল ধারণা বুঝে মানব মেন্টরের সহায়তায় গবেষণায় প্রয়োগ করাই লক্ষ্য।',
        'Applying core concepts to research with mentor guidance is the goal.'
    );

    RETURN jsonb_build_object(
        'book_id', v_book_id,
        'chapter_id', v_ch1_id
    );
END;
$$;

-- 3. Atomic Bulk Exercise Template Import RPC
CREATE OR REPLACE FUNCTION public.import_exercise_template_rpc(
    p_book_id UUID,
    p_chapter_no INTEGER,
    p_title TEXT,
    p_type TEXT DEFAULT 'standard',
    p_total_pages INTEGER DEFAULT 24,
    p_flash_notes JSONB DEFAULT '[]'::jsonb,
    p_learning_outcomes JSONB DEFAULT '[]'::jsonb,
    p_writing_prompt TEXT DEFAULT '',
    p_min_words INTEGER DEFAULT 400,
    p_max_words INTEGER DEFAULT 600,
    p_questions JSONB DEFAULT '[]'::jsonb,
    p_replace_questions BOOLEAN DEFAULT FALSE
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
    v_next_qno INTEGER;
    v_q JSONB;
    v_is_auto BOOLEAN;
    v_sec CHAR(1);
    v_cat question_category;
    v_marks INTEGER;
    v_qid UUID;
    v_imported INTEGER := 0;
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
        COALESCE(NULLIF(TRIM(p_writing_prompt), ''), 'এই পাঠের আলোকে আপনার বিশ্লেষণমূলক অনুচ্ছেদ লিখুন।'),
        GREATEST(COALESCE(p_min_words, 400), 50),
        GREATEST(COALESCE(p_max_words, 600), GREATEST(COALESCE(p_min_words, 400), 50) + 50)
    )
    ON CONFLICT (book_id, chapter_no) DO UPDATE SET
        title = EXCLUDED.title,
        type = EXCLUDED.type,
        total_pages = EXCLUDED.total_pages,
        total_marks = EXCLUDED.total_marks,
        pass_marks = EXCLUDED.pass_marks,
        red_flag_marks = EXCLUDED.red_flag_marks,
        flash_notes = CASE WHEN jsonb_array_length(EXCLUDED.flash_notes) > 0 THEN EXCLUDED.flash_notes ELSE public.chapters.flash_notes END,
        learning_outcomes = CASE WHEN jsonb_array_length(EXCLUDED.learning_outcomes) > 0 THEN EXCLUDED.learning_outcomes ELSE public.chapters.learning_outcomes END,
        writing_task_prompt = CASE WHEN TRIM(COALESCE(p_writing_prompt, '')) <> '' THEN EXCLUDED.writing_task_prompt ELSE public.chapters.writing_task_prompt END,
        writing_min_words = EXCLUDED.writing_min_words,
        writing_max_words = EXCLUDED.writing_max_words
    RETURNING id INTO v_ch_id;

    UPDATE public.books
    SET total_chapters = GREATEST(total_chapters, GREATEST(COALESCE(p_chapter_no, 1), 1))
    WHERE id = p_book_id;

    IF COALESCE(p_replace_questions, FALSE) THEN
        DELETE FROM public.chapter_questions WHERE chapter_id = v_ch_id;
    END IF;

    SELECT COALESCE(MAX(question_no), 0) INTO v_next_qno
    FROM public.chapter_questions
    WHERE chapter_id = v_ch_id;

    FOR v_q IN SELECT * FROM jsonb_array_elements(COALESCE(p_questions, '[]'::jsonb))
    LOOP
        v_next_qno := v_next_qno + 1;
        v_is_auto := COALESCE((v_q->>'is_auto_scored')::BOOLEAN, TRUE);

        IF v_is_auto THEN
            v_sec := CASE WHEN UPPER(LEFT(COALESCE(v_q->>'section_code', 'C'), 1)) IN ('A','B','C','D','E')
                          THEN UPPER(LEFT(v_q->>'section_code', 1))
                          ELSE 'C' END;
            v_cat := 'mcq'::question_category;
            v_marks := 1;
        ELSE
            v_sec := CASE WHEN UPPER(LEFT(COALESCE(v_q->>'section_code', 'H'), 1)) IN ('F','G','H','I','J')
                          THEN UPPER(LEFT(v_q->>'section_code', 1))
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
            v_is_auto,
            FALSE,
            v_marks,
            COALESCE(v_q->>'prompt_bn', 'প্রশ্ন'),
            COALESCE(v_q->>'prompt_bn', 'Question'),
            COALESCE(v_q->'options', '[]'::jsonb)
        )
        RETURNING id INTO v_qid;

        INSERT INTO public.chapter_answer_keys (
            question_id, correct_answer, explanation_bn, explanation_en, supervisor_model_points
        )
        VALUES (
            v_qid,
            to_jsonb(COALESCE(v_q->>'correct_answer', '')),
            COALESCE(v_q->>'explanation_bn', ''),
            COALESCE(v_q->>'explanation_bn', ''),
            COALESCE(v_q->'model_points', '[]'::jsonb)
        );

        v_imported := v_imported + 1;
    END LOOP;

    RETURN jsonb_build_object(
        'chapter_id', v_ch_id,
        'imported_questions', v_imported
    );
END;
$$;
