-- ============================================================================
-- Migration 015: Direct Text Chapters & Word-by-Word Audio Timestamp Sync
--   • Adds content_mode ('pdf' | 'text' | 'hybrid'), full_text_content, and
--     word_timestamps JSONB to public.chapters
--   • Adds upsert_chapter_text_and_sync_rpc & publish_book_with_text_or_pdf_rpc
-- ============================================================================

ALTER TABLE public.chapters
    ADD COLUMN IF NOT EXISTS content_mode TEXT NOT NULL DEFAULT 'pdf'
        CHECK (content_mode IN ('pdf', 'text', 'hybrid')),
    ADD COLUMN IF NOT EXISTS full_text_content TEXT NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS word_timestamps JSONB NOT NULL DEFAULT '[]'::jsonb;

-- Upsert direct text, content_mode, audio URL, and word-by-word timestamps for any chapter
CREATE OR REPLACE FUNCTION public.upsert_chapter_text_and_sync_rpc(
    p_book_id UUID,
    p_chapter_no INTEGER,
    p_title TEXT DEFAULT NULL,
    p_content_mode TEXT DEFAULT 'text',
    p_full_text_content TEXT DEFAULT '',
    p_audio_url TEXT DEFAULT NULL,
    p_audio_duration_seconds INTEGER DEFAULT NULL,
    p_word_timestamps JSONB DEFAULT '[]'::jsonb
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
    v_safe_ch INTEGER := GREATEST(COALESCE(p_chapter_no, 1), 1);
    v_mode TEXT := CASE
        WHEN LOWER(COALESCE(p_content_mode, 'text')) IN ('pdf', 'text', 'hybrid')
            THEN LOWER(p_content_mode)
        ELSE 'text'
    END;
    v_clean_text TEXT := COALESCE(p_full_text_content, '');
    v_word_count INTEGER;
    v_est_pages INTEGER;
    v_est_minutes INTEGER;
    v_ch_id UUID;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Authentication required';
    END IF;

    v_word_count := CASE
        WHEN TRIM(v_clean_text) = '' THEN 0
        ELSE COALESCE(array_length(regexp_split_to_array(TRIM(v_clean_text), '\s+'), 1), 0)
    END;
    v_est_pages := GREATEST(CEIL(GREATEST(v_word_count, 250) / 250.0)::INTEGER, 1);
    v_est_minutes := GREATEST(CEIL(GREATEST(v_word_count, 260) / 130.0)::INTEGER, 5);

    INSERT INTO public.chapters (
        book_id,
        chapter_no,
        title,
        type,
        total_pages,
        default_reading_minutes,
        audio_summary_url,
        audio_duration_seconds,
        total_marks,
        pass_marks,
        red_flag_marks,
        flash_notes,
        learning_outcomes,
        writing_task_prompt,
        content_mode,
        full_text_content,
        word_timestamps
    )
    VALUES (
        p_book_id,
        v_safe_ch,
        COALESCE(NULLIF(TRIM(p_title), ''), 'অধ্যায় ' || v_safe_ch),
        'standard',
        v_est_pages,
        v_est_minutes,
        NULLIF(TRIM(COALESCE(p_audio_url, '')), ''),
        GREATEST(COALESCE(p_audio_duration_seconds, 360), 1),
        52,
        32,
        26,
        '["অধ্যায়ের মূল যুক্তি চিহ্নিত করুন", "অডিওর সাথে মিলিয়ে মূল ধারণা নোট করুন"]'::jsonb,
        '["মূল ধারণা বিশ্লেষণ করতে পারা", "নিজের গবেষণায় প্রয়োগ করতে পারা"]'::jsonb,
        'এই অধ্যায়ের আলোকে ৪০০–৬০০ শব্দে আপনার বিশ্লেষণমূলক প্রতিক্রিয়া লিখুন।',
        v_mode,
        v_clean_text,
        COALESCE(p_word_timestamps, '[]'::jsonb)
    )
    ON CONFLICT (book_id, chapter_no) DO UPDATE SET
        title = COALESCE(NULLIF(TRIM(p_title), ''), public.chapters.title),
        content_mode = v_mode,
        full_text_content = v_clean_text,
        total_pages = CASE WHEN v_word_count > 0 THEN v_est_pages ELSE public.chapters.total_pages END,
        default_reading_minutes = CASE WHEN v_word_count > 0 THEN v_est_minutes ELSE public.chapters.default_reading_minutes END,
        audio_summary_url = COALESCE(NULLIF(TRIM(COALESCE(p_audio_url, '')), ''), public.chapters.audio_summary_url),
        audio_duration_seconds = COALESCE(NULLIF(p_audio_duration_seconds, 0), public.chapters.audio_duration_seconds),
        word_timestamps = CASE
            WHEN p_word_timestamps IS NOT NULL THEN p_word_timestamps
            ELSE public.chapters.word_timestamps
        END
    RETURNING id INTO v_ch_id;

    UPDATE public.books
    SET total_chapters = GREATEST(total_chapters, v_safe_ch)
    WHERE id = p_book_id;

    RETURN jsonb_build_object(
        'chapter_id', v_ch_id,
        'chapter_no', v_safe_ch,
        'word_count', v_word_count,
        'cue_count', jsonb_array_length(COALESCE(p_word_timestamps, '[]'::jsonb))
    );
END;
$$;

GRANT EXECUTE ON FUNCTION public.upsert_chapter_text_and_sync_rpc(
    UUID, INTEGER, TEXT, TEXT, TEXT, TEXT, INTEGER, JSONB
) TO authenticated;

-- Publish a brand-new book directly in Text, PDF, or Hybrid mode in a single atomic transaction
CREATE OR REPLACE FUNCTION public.publish_book_with_text_or_pdf_rpc(
    p_title TEXT,
    p_author TEXT,
    p_category TEXT DEFAULT 'Research & Pedagogy',
    p_price_bdt NUMERIC DEFAULT 0.00,
    p_chapter_title TEXT DEFAULT 'অধ্যায় ১: সূচনা ও মূল ধারণা',
    p_total_pages INTEGER DEFAULT 12,
    p_content_mode TEXT DEFAULT 'text',
    p_full_text_content TEXT DEFAULT '',
    p_word_timestamps JSONB DEFAULT '[]'::jsonb,
    p_pdf_url TEXT DEFAULT '',
    p_audio_url TEXT DEFAULT NULL,
    p_audio_duration_seconds INTEGER DEFAULT 360,
    p_source_attribution TEXT DEFAULT '',
    p_permission_declared BOOLEAN DEFAULT TRUE
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_base JSONB;
    v_book_id UUID;
    v_sync JSONB;
BEGIN
    v_base := public.publish_new_book_with_media_rpc(
        p_title := p_title,
        p_author := p_author,
        p_category := p_category,
        p_price_bdt := p_price_bdt,
        p_chapter_title := p_chapter_title,
        p_total_pages := p_total_pages,
        p_pdf_url := COALESCE(p_pdf_url, ''),
        p_audio_url := p_audio_url,
        p_source_attribution := p_source_attribution,
        p_permission_declared := p_permission_declared
    );
    v_book_id := (v_base ->> 'book_id')::UUID;

    v_sync := public.upsert_chapter_text_and_sync_rpc(
        p_book_id := v_book_id,
        p_chapter_no := 1,
        p_title := p_chapter_title,
        p_content_mode := p_content_mode,
        p_full_text_content := p_full_text_content,
        p_audio_url := p_audio_url,
        p_audio_duration_seconds := p_audio_duration_seconds,
        p_word_timestamps := p_word_timestamps
    );

    RETURN jsonb_build_object(
        'book_id', v_book_id,
        'chapter_id', v_sync ->> 'chapter_id',
        'cue_count', v_sync -> 'cue_count'
    );
END;
$$;

GRANT EXECUTE ON FUNCTION public.publish_book_with_text_or_pdf_rpc(
    TEXT, TEXT, TEXT, NUMERIC, TEXT, INTEGER, TEXT, TEXT, JSONB, TEXT, TEXT, INTEGER, TEXT, BOOLEAN
) TO authenticated;
