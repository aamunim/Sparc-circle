-- ============================================================================
-- SPARC Study Circle (v3.8 Two-Tier Role System & Mentor/Admin Workspaces)
-- Migration 009: Core System Roles (Admin, Mentor/Supervisor, Circle Lead/Moderator,
--                Researcher/Member) + Dynamic Activity Roles (Initiator, Presenter,
--                Co-Author, Peer Buddy) + Mentor & Admin Server RPCs
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. Extend Core System Role Enum with 'moderator' (Circle Lead)
-- ----------------------------------------------------------------------------
ALTER TYPE public.user_role ADD VALUE IF NOT EXISTS 'moderator';

-- ----------------------------------------------------------------------------
-- 2. Separate Member-Voted Circle Lead from Admin-Assigned Mentor (Supervisor)
-- ----------------------------------------------------------------------------
ALTER TABLE public.study_circles
    ADD COLUMN IF NOT EXISTS circle_lead_id UUID REFERENCES public.profiles(id) ON DELETE SET NULL;

CREATE TABLE IF NOT EXISTS public.circle_lead_votes (
    circle_id UUID NOT NULL REFERENCES public.study_circles(id) ON DELETE CASCADE,
    voter_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    candidate_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (circle_id, voter_id)
);

ALTER TABLE public.circle_lead_votes ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS circle_lead_votes_select_all ON public.circle_lead_votes;
CREATE POLICY circle_lead_votes_select_all ON public.circle_lead_votes
    FOR SELECT TO authenticated USING (TRUE);

DROP POLICY IF EXISTS circle_lead_votes_upsert_self ON public.circle_lead_votes;
CREATE POLICY circle_lead_votes_upsert_self ON public.circle_lead_votes
    FOR ALL TO authenticated
    USING (voter_id = auth.uid())
    WITH CHECK (voter_id = auth.uid());

-- Migrate any legacy member votes into circle_lead_votes & populate circle_lead_id
INSERT INTO public.circle_lead_votes (circle_id, voter_id, candidate_id, created_at)
SELECT circle_id, voter_id, candidate_id, created_at
FROM public.supervisor_votes
ON CONFLICT (circle_id, voter_id) DO NOTHING;

UPDATE public.study_circles
SET circle_lead_id = COALESCE(circle_lead_id, supervisor_id, created_by)
WHERE circle_lead_id IS NULL;

-- Ensure legacy supervisor_votes trigger no longer overwrites Admin-assigned Mentor
CREATE OR REPLACE FUNCTION public.fn_tally_circle_lead_votes()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_member_count INTEGER;
    v_leader_id UUID;
    v_leader_votes INTEGER;
    v_existing_lead UUID;
BEGIN
    SELECT COUNT(*) INTO v_member_count
    FROM public.study_circle_members
    WHERE circle_id = NEW.circle_id AND invite_status = 'joined';

    SELECT circle_lead_id INTO v_existing_lead
    FROM public.study_circles
    WHERE id = NEW.circle_id;

    SELECT candidate_id, COUNT(*)
    INTO v_leader_id, v_leader_votes
    FROM public.circle_lead_votes
    WHERE circle_id = NEW.circle_id
    GROUP BY candidate_id
    ORDER BY COUNT(*) DESC
    LIMIT 1;

    IF v_leader_id IS NOT NULL AND (v_leader_votes > (GREATEST(v_member_count, 1) / 2) OR v_existing_lead IS NULL) THEN
        UPDATE public.study_circles
        SET circle_lead_id = v_leader_id
        WHERE id = NEW.circle_id;
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_tally_circle_lead_votes ON public.circle_lead_votes;
CREATE TRIGGER trg_tally_circle_lead_votes
AFTER INSERT OR UPDATE ON public.circle_lead_votes
FOR EACH ROW
EXECUTE FUNCTION public.fn_tally_circle_lead_votes();

-- ----------------------------------------------------------------------------
-- 3. Peer Buddy & Open-Question Mentor Grading Columns + Mentor/Admin Read RLS
-- ----------------------------------------------------------------------------
ALTER TABLE public.chapter_progress
    ADD COLUMN IF NOT EXISTS peer_buddy_id UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS open_question_scores JSONB NOT NULL DEFAULT '{}'::jsonb;

DROP POLICY IF EXISTS chapter_progress_mentor_select ON public.chapter_progress;
CREATE POLICY chapter_progress_mentor_select ON public.chapter_progress
    FOR SELECT TO authenticated
    USING (
        user_id = auth.uid()
        OR EXISTS (
            SELECT 1 FROM public.profiles p
            WHERE p.id = auth.uid()
              AND p.role::text IN ('supervisor', 'admin', 'sparc_rep')
        )
        OR EXISTS (
            SELECT 1 FROM public.study_circles sc
            WHERE sc.id = chapter_progress.circle_id
              AND (sc.supervisor_id = auth.uid() OR sc.circle_lead_id = auth.uid())
        )
    );

DROP POLICY IF EXISTS exercise_submissions_mentor_select ON public.exercise_submissions;
CREATE POLICY exercise_submissions_mentor_select ON public.exercise_submissions
    FOR SELECT TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.chapter_progress cp
            WHERE cp.id = exercise_submissions.progress_id
              AND (
                  cp.user_id = auth.uid()
                  OR EXISTS (
                      SELECT 1 FROM public.profiles p
                      WHERE p.id = auth.uid()
                        AND p.role::text IN ('supervisor', 'admin', 'sparc_rep')
                  )
              )
        )
    );

DROP POLICY IF EXISTS writing_submissions_mentor_select ON public.writing_submissions;
CREATE POLICY writing_submissions_mentor_select ON public.writing_submissions
    FOR SELECT TO authenticated
    USING (
        user_id = auth.uid()
        OR EXISTS (
            SELECT 1 FROM public.profiles p
            WHERE p.id = auth.uid()
              AND p.role::text IN ('supervisor', 'admin', 'sparc_rep')
        )
    );

DROP POLICY IF EXISTS orders_admin_select ON public.physical_book_orders;
CREATE POLICY orders_admin_select ON public.physical_book_orders
    FOR SELECT TO authenticated
    USING (
        user_id = auth.uid()
        OR EXISTS (
            SELECT 1 FROM public.profiles p
            WHERE p.id = auth.uid()
              AND p.role::text IN ('admin', 'sparc_rep')
        )
    );

DROP POLICY IF EXISTS wallet_admin_select ON public.wallet_transactions;
CREATE POLICY wallet_admin_select ON public.wallet_transactions
    FOR SELECT TO authenticated
    USING (
        user_id = auth.uid()
        OR EXISTS (
            SELECT 1 FROM public.profiles p
            WHERE p.id = auth.uid()
              AND p.role::text IN ('admin', 'sparc_rep')
        )
    );

-- Auto-bootstrap project owner (earliest registered account) as Admin if none exists yet
UPDATE public.profiles
SET role = 'admin'
WHERE id = (SELECT id FROM public.profiles ORDER BY created_at ASC LIMIT 1)
  AND NOT EXISTS (SELECT 1 FROM public.profiles WHERE role::text = 'admin');

-- ----------------------------------------------------------------------------
-- 4. Upgrade create_custom_circle_rpc (+50 Gamification Pts & Circle Initiator Badge)
-- ----------------------------------------------------------------------------
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
        name, book_id, created_by, circle_lead_id, min_members_required, max_members,
        status, current_group_chapter,
        is_private, invite_code, session_schedule_label, default_meeting_url, circle_rules
    )
    VALUES (
        TRIM(p_name),
        p_book_id,
        v_uid,
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

    -- Award +50 gamification points & Circle Initiator badge to the host!
    UPDATE public.profiles
    SET
        gamification_points = gamification_points + 50,
        earned_badges = CASE
            WHEN NOT ('Circle Initiator' = ANY(earned_badges))
            THEN array_append(earned_badges, 'Circle Initiator')
            ELSE earned_badges
        END,
        updated_at = NOW()
    WHERE id = v_uid;

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
        'invite_code', v_code,
        'bonus_points', 50
    );
END;
$$;

-- ----------------------------------------------------------------------------
-- 5. Member Vote for Circle Lead & Presenter Rotation RPCs
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.vote_circle_lead_rpc(
    p_circle_id UUID,
    p_candidate_id UUID
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
    v_lead UUID;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Authentication required';
    END IF;

    INSERT INTO public.circle_lead_votes (circle_id, voter_id, candidate_id)
    VALUES (p_circle_id, v_uid, p_candidate_id)
    ON CONFLICT (circle_id, voter_id) DO UPDATE SET candidate_id = EXCLUDED.candidate_id, created_at = NOW();

    SELECT circle_lead_id INTO v_lead
    FROM public.study_circles
    WHERE id = p_circle_id;

    RETURN jsonb_build_object('voted', TRUE, 'circle_lead_id', v_lead);
END;
$$;

CREATE OR REPLACE FUNCTION public.assign_session_presenter_rpc(
    p_session_id UUID,
    p_presenter_user_id UUID
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
    v_role TEXT;
    v_circle_id UUID;
    v_allowed BOOLEAN := FALSE;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Authentication required';
    END IF;

    SELECT role::text INTO v_role FROM public.profiles WHERE id = v_uid;
    SELECT circle_id INTO v_circle_id FROM public.online_sessions WHERE id = p_session_id;

    IF v_role IN ('admin', 'sparc_rep', 'supervisor', 'moderator') THEN
        v_allowed := TRUE;
    ELSIF v_circle_id IS NOT NULL AND EXISTS (
        SELECT 1 FROM public.study_circles sc
        WHERE sc.id = v_circle_id
          AND (sc.circle_lead_id = v_uid OR sc.supervisor_id = v_uid OR sc.created_by = v_uid)
    ) THEN
        v_allowed := TRUE;
    END IF;

    IF NOT v_allowed THEN
        RAISE EXCEPTION 'শুধুমাত্র সার্কেল লিড, মেন্টর বা অ্যাডমিন উপস্থাপক নির্ধারণ করতে পারেন।';
    END IF;

    UPDATE public.online_sessions
    SET presenter_user_id = p_presenter_user_id
    WHERE id = p_session_id;

    RETURN jsonb_build_object('updated', TRUE, 'presenter_user_id', p_presenter_user_id);
END;
$$;

-- ----------------------------------------------------------------------------
-- 6. Mentor Grading (F–J 0–3 Marks), Red-Flag Support, Peer Buddy & Unlock RPC
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.mentor_evaluate_and_support_rpc(
    p_circle_id UUID,
    p_target_user_id UUID,
    p_chapter_no INTEGER,
    p_supervisor_score INTEGER DEFAULT NULL,
    p_open_scores JSONB DEFAULT NULL,
    p_peer_buddy_id UUID DEFAULT NULL,
    p_extend_days INTEGER DEFAULT 0,
    p_supervisor_override BOOLEAN DEFAULT NULL,
    p_approve_writing BOOLEAN DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
    v_role TEXT;
    v_is_circle_mentor BOOLEAN := FALSE;
    v_prog RECORD;
    v_pass_marks INTEGER := 32;
    v_new_total INTEGER;
    v_next_status progress_status;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Authentication required';
    END IF;

    SELECT role::text INTO v_role FROM public.profiles WHERE id = v_uid;
    SELECT EXISTS (
        SELECT 1 FROM public.study_circles
        WHERE id = p_circle_id AND supervisor_id = v_uid
    ) INTO v_is_circle_mentor;

    IF v_role NOT IN ('supervisor', 'admin', 'sparc_rep') AND NOT v_is_circle_mentor THEN
        RAISE EXCEPTION 'শুধুমাত্র মেন্টর (সুপারভাইজার) বা অ্যাডমিন মূল্যায়ন ও পিয়ার বাডি যুক্ত করতে পারেন।';
    END IF;

    SELECT cp.*, c.pass_marks AS ch_pass_marks
    INTO v_prog
    FROM public.chapter_progress cp
    JOIN public.chapters c ON c.id = cp.chapter_id
    WHERE cp.circle_id = p_circle_id
      AND cp.user_id = p_target_user_id
      AND cp.chapter_no = p_chapter_no
    LIMIT 1;

    IF v_prog.id IS NULL THEN
        RAISE EXCEPTION 'Target researcher progress row not found';
    END IF;

    v_pass_marks := COALESCE(v_prog.ch_pass_marks, 32);
    v_new_total := COALESCE(v_prog.auto_score, 0) + COALESCE(p_supervisor_score, v_prog.supervisor_score, 0);

    IF COALESCE(p_approve_writing, FALSE) THEN
        UPDATE public.writing_submissions
        SET supervisor_approved = TRUE, updated_at = NOW()
        WHERE progress_id = v_prog.id;
    END IF;

    v_next_status := v_prog.status;
    IF COALESCE(p_supervisor_override, v_prog.supervisor_override, FALSE)
       OR (
           v_prog.status IN ('Exercises Submitted', 'Learning Verified')
           AND v_new_total >= v_pass_marks
           AND (v_prog.writing_task_submitted OR COALESCE(p_approve_writing, FALSE))
       ) THEN
        v_next_status := 'Learning Verified';
    END IF;

    UPDATE public.chapter_progress
    SET
        supervisor_score = COALESCE(p_supervisor_score, supervisor_score),
        supervisor_graded_by = v_uid,
        open_question_scores = COALESCE(p_open_scores, open_question_scores),
        peer_buddy_id = COALESCE(p_peer_buddy_id, peer_buddy_id),
        expected_completion_date = CASE
            WHEN COALESCE(p_extend_days, 0) > 0
            THEN COALESCE(expected_completion_date, CURRENT_DATE) + (p_extend_days || ' days')::INTERVAL
            ELSE expected_completion_date
        END,
        supervisor_override = COALESCE(p_supervisor_override, supervisor_override),
        writing_task_submitted = CASE WHEN COALESCE(p_approve_writing, FALSE) THEN TRUE ELSE writing_task_submitted END,
        status = v_next_status,
        learning_verified_at = CASE WHEN v_next_status = 'Learning Verified' THEN COALESCE(learning_verified_at, NOW()) ELSE learning_verified_at END,
        updated_at = NOW()
    WHERE id = v_prog.id;

    RETURN jsonb_build_object(
        'updated', TRUE,
        'status', v_next_status::text,
        'total_score', v_new_total,
        'peer_buddy_id', COALESCE(p_peer_buddy_id, v_prog.peer_buddy_id)
    );
END;
$$;

-- ----------------------------------------------------------------------------
-- 7. Admin Role Management, Circle Mentor Assignment & Order/Wallet Processing
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.admin_manage_roles_and_orders_rpc(
    p_target_user_id UUID DEFAULT NULL,
    p_new_role TEXT DEFAULT NULL,
    p_circle_id UUID DEFAULT NULL,
    p_mentor_id UUID DEFAULT NULL,
    p_min_members INTEGER DEFAULT NULL,
    p_book_order_id UUID DEFAULT NULL,
    p_book_order_status TEXT DEFAULT NULL,
    p_wallet_tx_id UUID DEFAULT NULL,
    p_wallet_tx_status TEXT DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
    v_my_role TEXT;
    v_clean_role TEXT;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Authentication required';
    END IF;

    SELECT role::text INTO v_my_role FROM public.profiles WHERE id = v_uid;
    IF v_my_role NOT IN ('admin', 'sparc_rep') THEN
        RAISE EXCEPTION 'শুধুমাত্র অ্যাডমিন (SPARC টিম) এই কার্যক্রম সম্পাদন করতে পারেন।';
    END IF;

    IF p_target_user_id IS NOT NULL AND p_new_role IS NOT NULL THEN
        v_clean_role := LOWER(TRIM(p_new_role));
        IF v_clean_role IN ('member', 'moderator', 'supervisor', 'sparc_rep', 'admin') THEN
            EXECUTE 'UPDATE public.profiles SET role = $1::public.user_role, updated_at = NOW() WHERE id = $2'
            USING v_clean_role, p_target_user_id;
        END IF;
    END IF;

    IF p_circle_id IS NOT NULL THEN
        UPDATE public.study_circles
        SET
            supervisor_id = COALESCE(p_mentor_id, supervisor_id),
            supervisor_assignment_type = CASE WHEN p_mentor_id IS NOT NULL THEN 'admin_assigned'::supervisor_assignment_type ELSE supervisor_assignment_type END,
            min_members_required = CASE WHEN p_min_members IS NOT NULL THEN GREATEST(p_min_members, 2) ELSE min_members_required END
        WHERE id = p_circle_id;
    END IF;

    IF p_book_order_id IS NOT NULL AND p_book_order_status IS NOT NULL THEN
        UPDATE public.physical_book_orders
        SET
            status = LOWER(TRIM(p_book_order_status))::book_order_status,
            assigned_rep_id = v_uid,
            updated_at = NOW()
        WHERE id = p_book_order_id;
    END IF;

    IF p_wallet_tx_id IS NOT NULL AND p_wallet_tx_status IS NOT NULL THEN
        UPDATE public.wallet_transactions
        SET
            status = LOWER(TRIM(p_wallet_tx_status))::wallet_tx_status,
            verified_by = v_uid,
            verified_at = NOW()
        WHERE id = p_wallet_tx_id;
    END IF;

    RETURN jsonb_build_object('updated', TRUE);
END;
$$;

-- ----------------------------------------------------------------------------
-- 8. Settings Role Preview Switcher RPC (For Testing / Developer Mode)
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.switch_my_role_preview_rpc(
    p_role TEXT
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
    v_clean_role TEXT := LOWER(TRIM(COALESCE(p_role, 'member')));
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Authentication required';
    END IF;

    IF v_clean_role NOT IN ('member', 'moderator', 'supervisor', 'sparc_rep', 'admin') THEN
        v_clean_role := 'member';
    END IF;

    EXECUTE 'UPDATE public.profiles SET role = $1::public.user_role, updated_at = NOW() WHERE id = $2'
    USING v_clean_role, v_uid;

    RETURN jsonb_build_object('role', v_clean_role);
END;
$$;
