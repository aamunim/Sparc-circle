-- ============================================================================
-- SPARC Study Circle (v3.4 Live Multi-User Two-Way Sync)
-- Migration 005: Collaborative RLS Policies, Shared Cohort Bootstrap & Atomic RPCs
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. Complete RLS Policies for Collaborative Tables
-- ----------------------------------------------------------------------------
DROP POLICY IF EXISTS supervisor_votes_select ON public.supervisor_votes;
CREATE POLICY supervisor_votes_select ON public.supervisor_votes
    FOR SELECT TO authenticated USING (TRUE);

DROP POLICY IF EXISTS supervisor_votes_upsert_self ON public.supervisor_votes;
CREATE POLICY supervisor_votes_upsert_self ON public.supervisor_votes
    FOR ALL TO authenticated
    USING (voter_id = auth.uid())
    WITH CHECK (voter_id = auth.uid());

DROP POLICY IF EXISTS polls_select_all ON public.chapter_polls;
CREATE POLICY polls_select_all ON public.chapter_polls
    FOR SELECT TO authenticated USING (TRUE);

DROP POLICY IF EXISTS polls_insert_auth ON public.chapter_polls;
CREATE POLICY polls_insert_auth ON public.chapter_polls
    FOR INSERT TO authenticated WITH CHECK (created_by = auth.uid());

DROP POLICY IF EXISTS poll_votes_select_all ON public.chapter_poll_votes;
CREATE POLICY poll_votes_select_all ON public.chapter_poll_votes
    FOR SELECT TO authenticated USING (TRUE);

DROP POLICY IF EXISTS poll_votes_upsert_self ON public.chapter_poll_votes;
CREATE POLICY poll_votes_upsert_self ON public.chapter_poll_votes
    FOR ALL TO authenticated
    USING (user_id = auth.uid())
    WITH CHECK (user_id = auth.uid());

DROP POLICY IF EXISTS debate_threads_select_all ON public.debate_threads;
CREATE POLICY debate_threads_select_all ON public.debate_threads
    FOR SELECT TO authenticated USING (TRUE);

DROP POLICY IF EXISTS debate_threads_insert_auth ON public.debate_threads;
CREATE POLICY debate_threads_insert_auth ON public.debate_threads
    FOR INSERT TO authenticated WITH CHECK (author_id = auth.uid());

DROP POLICY IF EXISTS debate_comments_select_all ON public.debate_comments;
CREATE POLICY debate_comments_select_all ON public.debate_comments
    FOR SELECT TO authenticated USING (TRUE);

DROP POLICY IF EXISTS debate_comments_insert_self ON public.debate_comments;
CREATE POLICY debate_comments_insert_self ON public.debate_comments
    FOR INSERT TO authenticated WITH CHECK (author_id = auth.uid());

DROP POLICY IF EXISTS sessions_update_slides ON public.online_sessions;
CREATE POLICY sessions_update_slides ON public.online_sessions
    FOR UPDATE TO authenticated USING (TRUE) WITH CHECK (TRUE);

DROP POLICY IF EXISTS research_groups_select_all ON public.research_groups;
CREATE POLICY research_groups_select_all ON public.research_groups
    FOR SELECT TO authenticated USING (TRUE);

DROP POLICY IF EXISTS research_groups_insert_self ON public.research_groups;
CREATE POLICY research_groups_insert_self ON public.research_groups
    FOR INSERT TO authenticated WITH CHECK (created_by = auth.uid());

DROP POLICY IF EXISTS research_group_members_select_all ON public.research_group_members;
CREATE POLICY research_group_members_select_all ON public.research_group_members
    FOR SELECT TO authenticated USING (TRUE);

DROP POLICY IF EXISTS research_group_members_join_self ON public.research_group_members;
CREATE POLICY research_group_members_join_self ON public.research_group_members
    FOR INSERT TO authenticated WITH CHECK (user_id = auth.uid());

DROP POLICY IF EXISTS negotiations_select_all ON public.supervision_negotiations;
CREATE POLICY negotiations_select_all ON public.supervision_negotiations
    FOR SELECT TO authenticated USING (TRUE);

-- Grant SELECT on safe public progress & supervisor summary views
GRANT SELECT ON public.group_chapter_progress_public TO authenticated;
GRANT SELECT ON public.supervisor_poke_summary TO authenticated;

-- ----------------------------------------------------------------------------
-- 2. Auto-Bootstrap Default Shared Cohort, Poll, Debate & Sessions for All Users
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.ensure_user_in_default_cohort(p_user_id UUID)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_book_id UUID := '11111111-1111-1111-1111-111111111101';
    v_ch1_id  UUID := '22222222-2222-2222-2222-222222222201';
    v_circle_id UUID := '44444444-4444-4444-4444-444444444401';
    v_poll_id   UUID := '55555555-5555-5555-5555-555555555501';
    v_thread_id UUID := '66666666-6666-6666-6666-666666666601';
    v_sess1_id  UUID := '77777777-7777-7777-7777-777777777701';
    v_clinic_id UUID := '77777777-7777-7777-7777-777777777702';
    v_rgroup_id UUID := '88888888-8888-8888-8888-888888888801';
BEGIN
    IF NOT EXISTS (SELECT 1 FROM public.books WHERE id = v_book_id) THEN
        RETURN;
    END IF;

    INSERT INTO public.study_circles (
        id, name, book_id, created_by, min_members_required, status, current_group_chapter
    )
    VALUES (
        v_circle_id,
        'CPAR কোহর্ট-০১ (সমাজবিজ্ঞান ও শিক্ষা গবেষণা)',
        v_book_id,
        p_user_id,
        5,
        'active',
        1
    )
    ON CONFLICT (id) DO NOTHING;

    INSERT INTO public.study_circle_members (circle_id, user_id, invite_status)
    VALUES (v_circle_id, p_user_id, 'joined')
    ON CONFLICT (circle_id, user_id) DO UPDATE SET invite_status = 'joined';

    IF EXISTS (SELECT 1 FROM public.chapters WHERE id = v_ch1_id) THEN
        INSERT INTO public.chapter_progress (
            circle_id, user_id, chapter_id, chapter_no, status,
            reading_started_at, expected_completion_date, daily_target_pages, target_days
        )
        VALUES (
            v_circle_id, p_user_id, v_ch1_id, 1, 'Reading in Progress',
            NOW(), (CURRENT_DATE + INTERVAL '8 days')::DATE, 4, 8
        )
        ON CONFLICT (circle_id, user_id, chapter_no) DO NOTHING;
    END IF;

    INSERT INTO public.chapter_polls (id, circle_id, chapter_no, created_by, question, options)
    VALUES (
        v_poll_id,
        v_circle_id,
        1,
        p_user_id,
        'বাংলাদেশের বিশ্ববিদ্যালয়ে CPAR বাস্তবায়নে সবচেয়ে বড় বাধা কোনটি বলে মনে করেন?',
        '[
          "শিক্ষক-শিক্ষার্থী ক্ষমতার দূরত্ব (Relatings)",
          "সময় ও ফান্ডিং সীমাবদ্ধতা (Doings)",
          "গবেষণা ভীতি ও প্রচলিত মুখস্থ সংস্কৃতি (Sayings)"
        ]'::jsonb
    )
    ON CONFLICT (id) DO NOTHING;

    INSERT INTO public.debate_threads (id, circle_id, chapter_no, author_id, topic_title, stance_prompt)
    VALUES (
        v_thread_id,
        v_circle_id,
        1,
        p_user_id,
        'Practice Architectures in Classrooms',
        'Support or Counter: Can Communicative Space exist within graded university courses?'
    )
    ON CONFLICT (id) DO NOTHING;

    INSERT INTO public.online_sessions (
        id, circle_id, chapter_no, part_no, is_research_clinic, title,
        scheduled_at, duration_minutes, presenter_user_id, video_provider,
        jitsi_room_name, external_meeting_url
    )
    VALUES
    (
        v_sess1_id,
        v_circle_id,
        1,
        1,
        FALSE,
        'অধ্যায় ১ (পর্ব ১/২): Practice Architectures অনলাইন সেশন',
        NOW() + INTERVAL '3 days',
        40,
        p_user_id,
        'jitsi_in_app',
        'SPARC_CPAR_Cohort01_Ch1_Part1',
        'https://meet.google.com/sparc-cpar-ch1'
    ),
    (
        v_clinic_id,
        NULL,
        NULL,
        1,
        TRUE,
        'মাসিক লাইভ রিসার্চ ক্লিনিক (Live Q&A with Senior Researchers)',
        NOW() + INTERVAL '7 days',
        60,
        p_user_id,
        'jitsi_in_app',
        'SPARC_Monthly_Research_Clinic',
        'https://zoom.us/j/9988776655'
    )
    ON CONFLICT (id) DO NOTHING;

    INSERT INTO public.research_groups (
        id, name, paper_topic, source_circle_id, created_by,
        supervision_type, agreed_sessions, agreed_fee_bdt, payment_status
    )
    VALUES (
        v_rgroup_id,
        'CPAR Higher-Ed Co-Author Team',
        'বিশ্ববিদ্যালয় শ্রেণিকক্ষে অংশগ্রহণমূলক মূল্যায়ন সংস্কার',
        v_circle_id,
        p_user_id,
        'paid_supervised',
        4,
        800.00,
        'unpaid'
    )
    ON CONFLICT (id) DO NOTHING;

    INSERT INTO public.research_group_members (research_group_id, user_id, assigned_skill_role)
    VALUES (v_rgroup_id, p_user_id, 'Co-Author')
    ON CONFLICT (research_group_id, user_id) DO NOTHING;
END;
$$;

CREATE OR REPLACE FUNCTION public.fn_trg_profile_default_cohort()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
    PERFORM public.ensure_user_in_default_cohort(NEW.id);
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_auto_join_default_cohort ON public.profiles;
CREATE TRIGGER trg_auto_join_default_cohort
AFTER INSERT ON public.profiles
FOR EACH ROW
EXECUTE FUNCTION public.fn_trg_profile_default_cohort();

-- Backfill any already-registered profiles into the default live cohort
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN SELECT id FROM public.profiles LOOP
        PERFORM public.ensure_user_in_default_cohort(r.id);
    END LOOP;
END;
$$;

-- ----------------------------------------------------------------------------
-- 3. Atomic Multi-Step Server RPCs (Wallet, Book Orders, Circles & Writing)
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.topup_wallet_rpc(
    p_method TEXT,
    p_amount_bdt NUMERIC,
    p_manual_trx_id TEXT DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
    v_tx_type wallet_tx_type;
    v_status wallet_tx_status;
    v_ref TEXT;
    v_manual TEXT := NULL;
    v_tx_id UUID;
    v_new_balance NUMERIC;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Authentication required';
    END IF;
    IF p_amount_bdt < 500 THEN
        RAISE EXCEPTION 'Minimum wallet top-up is 500 BDT';
    END IF;

    IF p_method = 'manual_trx' THEN
        IF p_manual_trx_id IS NULL OR char_length(TRIM(p_manual_trx_id)) < 6 THEN
            RAISE EXCEPTION 'Valid bKash/Nagad TrxID required';
        END IF;
        v_tx_type := 'topup_manual_trx';
        v_status := 'pending';
        v_manual := UPPER(TRIM(p_manual_trx_id));
        v_ref := v_manual;
    ELSIF p_method = 'bkash_pgw' THEN
        v_tx_type := 'topup_bkash_pgw';
        v_status := 'verified';
        v_ref := 'BKASH-' || UPPER(SUBSTRING(REPLACE(gen_random_uuid()::TEXT, '-', '') FROM 1 FOR 8));
    ELSE
        v_tx_type := 'topup_sslcommerz';
        v_status := 'verified';
        v_ref := 'SSLC-' || UPPER(SUBSTRING(REPLACE(gen_random_uuid()::TEXT, '-', '') FROM 1 FOR 8));
    END IF;

    INSERT INTO public.wallet_transactions (
        user_id, tx_type, amount_bdt, status, gateway_ref, manual_trx_id, notes,
        verified_at
    )
    VALUES (
        v_uid, v_tx_type, p_amount_bdt, v_status, v_ref, v_manual,
        'App wallet top-up (' || p_method || ')',
        CASE WHEN v_status = 'verified' THEN NOW() ELSE NULL END
    )
    RETURNING id INTO v_tx_id;

    SELECT wallet_balance INTO v_new_balance FROM public.profiles WHERE id = v_uid;

    RETURN jsonb_build_object(
        'tx_id', v_tx_id,
        'status', v_status,
        'reference', v_ref,
        'wallet_balance', v_new_balance
    );
END;
$$;

CREATE OR REPLACE FUNCTION public.place_book_order_rpc(
    p_book_id UUID,
    p_order_mode TEXT,
    p_address TEXT,
    p_phone TEXT
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
    v_mode TEXT := LOWER(TRIM(p_order_mode));
    v_price NUMERIC := 0;
    v_order_id UUID;
    v_new_balance NUMERIC;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Authentication required';
    END IF;
    IF v_mode NOT IN ('purchase', 'borrow') THEN
        v_mode := 'purchase';
    END IF;

    IF v_mode = 'purchase' THEN
        SELECT physical_price_bdt INTO v_price FROM public.books WHERE id = p_book_id;
        v_price := COALESCE(v_price, 0);
        IF v_price > 0 THEN
            -- trg_sync_wallet_balance atomically verifies balance >= v_price and deducts it
            INSERT INTO public.wallet_transactions (
                user_id, tx_type, amount_bdt, status, gateway_ref, notes, verified_at
            )
            VALUES (
                v_uid, 'physical_book_order', -v_price, 'verified',
                'BOOK-' || UPPER(SUBSTRING(REPLACE(gen_random_uuid()::TEXT, '-', '') FROM 1 FOR 6)),
                'Hardcopy book purchase',
                NOW()
            );
        END IF;
    END IF;

    INSERT INTO public.physical_book_orders (
        user_id, book_id, order_mode, delivery_address, contact_phone,
        amount_bdt, paid_via_wallet, status
    )
    VALUES (
        v_uid, p_book_id, v_mode, TRIM(p_address), TRIM(p_phone),
        v_price, (v_mode = 'purchase' AND v_price > 0), 'pending'
    )
    RETURNING id INTO v_order_id;

    SELECT wallet_balance INTO v_new_balance FROM public.profiles WHERE id = v_uid;

    RETURN jsonb_build_object(
        'order_id', v_order_id,
        'amount_bdt', v_price,
        'wallet_balance', v_new_balance
    );
END;
$$;

CREATE OR REPLACE FUNCTION public.pay_supervision_rpc(p_group_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
    v_fee NUMERIC;
    v_status TEXT;
    v_new_balance NUMERIC;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Authentication required';
    END IF;

    SELECT agreed_fee_bdt, payment_status INTO v_fee, v_status
    FROM public.research_groups
    WHERE id = p_group_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Research group not found';
    END IF;

    IF v_status = 'paid_via_wallet' THEN
        SELECT wallet_balance INTO v_new_balance FROM public.profiles WHERE id = v_uid;
        RETURN jsonb_build_object('paid', TRUE, 'wallet_balance', v_new_balance);
    END IF;

    IF COALESCE(v_fee, 0) > 0 THEN
        INSERT INTO public.wallet_transactions (
            user_id, tx_type, amount_bdt, status, gateway_ref, notes, verified_at
        )
        VALUES (
            v_uid, 'supervision_escrow', -v_fee, 'verified',
            'SUP-' || UPPER(SUBSTRING(REPLACE(p_group_id::TEXT, '-', '') FROM 1 FOR 6)),
            'Paid research supervision escrow',
            NOW()
        );
    END IF;

    UPDATE public.research_groups
    SET payment_status = 'paid_via_wallet'
    WHERE id = p_group_id;

    SELECT wallet_balance INTO v_new_balance FROM public.profiles WHERE id = v_uid;
    RETURN jsonb_build_object('paid', TRUE, 'wallet_balance', v_new_balance);
END;
$$;

CREATE OR REPLACE FUNCTION public.create_or_join_circle_rpc(
    p_name TEXT,
    p_book_id UUID,
    p_min_members INTEGER DEFAULT 5
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
    v_pages INTEGER;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Authentication required';
    END IF;

    INSERT INTO public.study_circles (
        name, book_id, created_by, min_members_required, status, current_group_chapter
    )
    VALUES (
        TRIM(p_name), p_book_id, v_uid, GREATEST(COALESCE(p_min_members, 5), 2), 'forming', 1
    )
    RETURNING id INTO v_circle_id;

    INSERT INTO public.study_circle_members (circle_id, user_id, invite_status)
    VALUES (v_circle_id, v_uid, 'joined')
    ON CONFLICT (circle_id, user_id) DO UPDATE SET invite_status = 'joined';

    SELECT id, total_pages INTO v_ch1_id, v_pages
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
            NOW(), (CURRENT_DATE + INTERVAL '8 days')::DATE,
            CEIL(v_pages::NUMERIC / 8.0), 8
        )
        ON CONFLICT (circle_id, user_id, chapter_no) DO NOTHING;
    END IF;

    RETURN jsonb_build_object('circle_id', v_circle_id);
END;
$$;

CREATE OR REPLACE FUNCTION public.join_existing_circle_rpc(p_circle_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
    v_book_id UUID;
    v_ch1_id UUID;
    v_pages INTEGER;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Authentication required';
    END IF;

    SELECT book_id INTO v_book_id FROM public.study_circles WHERE id = p_circle_id;
    IF v_book_id IS NULL THEN
        RAISE EXCEPTION 'Study circle not found';
    END IF;

    INSERT INTO public.study_circle_members (circle_id, user_id, invite_status)
    VALUES (p_circle_id, v_uid, 'joined')
    ON CONFLICT (circle_id, user_id) DO UPDATE SET invite_status = 'joined';

    SELECT id, total_pages INTO v_ch1_id, v_pages
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
            NOW(), (CURRENT_DATE + INTERVAL '8 days')::DATE,
            CEIL(v_pages::NUMERIC / 8.0), 8
        )
        ON CONFLICT (circle_id, user_id, chapter_no) DO NOTHING;
    END IF;

    RETURN jsonb_build_object('joined', TRUE, 'circle_id', p_circle_id);
END;
$$;

CREATE OR REPLACE FUNCTION public.submit_writing_task_rpc(
    p_circle_id UUID,
    p_chapter_no INTEGER,
    p_content TEXT,
    p_language TEXT DEFAULT 'bn',
    p_ai_feedback JSONB DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
    v_prog public.chapter_progress%ROWTYPE;
    v_book_id UUID;
    v_words INTEGER;
    v_final_status progress_status;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Authentication required';
    END IF;

    SELECT * INTO v_prog
    FROM public.chapter_progress
    WHERE circle_id = p_circle_id AND user_id = v_uid AND chapter_no = p_chapter_no;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Chapter progress not found';
    END IF;

    SELECT book_id INTO v_book_id FROM public.chapters WHERE id = v_prog.chapter_id;
    v_words := COALESCE(array_length(regexp_split_to_array(TRIM(p_content), '\s+'), 1), 0);

    INSERT INTO public.writing_submissions (
        progress_id, user_id, book_id, chapter_no, title, content, word_count, language, ai_feedback
    )
    VALUES (
        v_prog.id, v_uid, v_book_id, p_chapter_no,
        'Chapter ' || p_chapter_no || ' Proposal Draft',
        TRIM(p_content), v_words,
        CASE WHEN p_language = 'en' THEN 'en' ELSE 'bn' END,
        p_ai_feedback
    )
    ON CONFLICT (progress_id) DO UPDATE
    SET content = EXCLUDED.content,
        word_count = EXCLUDED.word_count,
        language = EXCLUDED.language,
        ai_feedback = COALESCE(EXCLUDED.ai_feedback, public.writing_submissions.ai_feedback),
        updated_at = NOW();

    IF NOT v_prog.writing_task_submitted THEN
        UPDATE public.profiles
        SET gamification_points = gamification_points + 30,
            earned_badges = CASE
                WHEN NOT ('Research Proposal Writer' = ANY(earned_badges))
                THEN array_append(earned_badges, 'Research Proposal Writer')
                ELSE earned_badges
            END
        WHERE id = v_uid;
    END IF;

    UPDATE public.chapter_progress
    SET writing_task_submitted = TRUE,
        updated_at = NOW()
    WHERE id = v_prog.id
    RETURNING status INTO v_final_status;

    RETURN jsonb_build_object(
        'word_count', v_words,
        'status', v_final_status
    );
END;
$$;
