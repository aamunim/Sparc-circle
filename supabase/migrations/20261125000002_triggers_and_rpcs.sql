-- ============================================================================
-- SPARC Study Circle (v2.0 Complete Edition)
-- Migration 002: Core Triggers, State Machine, Poke Rate-Limiter & RPCs
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. Wallet Ledger Sync & Referral Bonus (Section 2.3)
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.fn_sync_wallet_balance()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
    IF (TG_OP = 'INSERT' AND NEW.status = 'verified')
       OR (TG_OP = 'UPDATE' AND OLD.status <> 'verified' AND NEW.status = 'verified') THEN
        IF (SELECT wallet_balance + NEW.amount_bdt FROM public.profiles WHERE id = NEW.user_id) < 0 THEN
            RAISE EXCEPTION 'Insufficient wallet balance for debit transaction';
        END IF;

        UPDATE public.profiles
        SET wallet_balance = wallet_balance + NEW.amount_bdt,
            updated_at = NOW()
        WHERE id = NEW.user_id;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_sync_wallet_balance
AFTER INSERT OR UPDATE ON public.wallet_transactions
FOR EACH ROW
EXECUTE FUNCTION public.fn_sync_wallet_balance();

CREATE OR REPLACE FUNCTION public.apply_referral_bonus(
    p_new_user_id UUID,
    p_referral_code TEXT,
    p_bonus_bdt NUMERIC DEFAULT 50.00
)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_referrer_id UUID;
BEGIN
    SELECT id INTO v_referrer_id
    FROM public.profiles
    WHERE referral_code = UPPER(TRIM(p_referral_code))
      AND id <> p_new_user_id;

    IF v_referrer_id IS NULL THEN
        RETURN FALSE;
    END IF;

    UPDATE public.profiles
    SET referred_by = v_referrer_id,
        gamification_points = gamification_points + 25
    WHERE id = p_new_user_id AND referred_by IS NULL;

    IF NOT FOUND THEN
        RETURN FALSE;
    END IF;

    UPDATE public.profiles
    SET gamification_points = gamification_points + 50
    WHERE id = v_referrer_id;

    INSERT INTO public.wallet_transactions (user_id, tx_type, amount_bdt, status, notes, verified_at)
    VALUES
        (p_new_user_id, 'referral_bonus', p_bonus_bdt, 'verified', 'Welcome referral bonus', NOW()),
        (v_referrer_id, 'referral_bonus', p_bonus_bdt, 'verified', 'Invite & Earn referral bonus', NOW());

    RETURN TRUE;
END;
$$;

-- ----------------------------------------------------------------------------
-- 2. Reading Streak (3, 7, 15 Days) & Gamification Points (Section 2.4)
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.fn_update_reading_streak()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_last_date DATE;
    v_current_streak INTEGER;
    v_longest_streak INTEGER;
    v_bonus_points INTEGER := 5;
BEGIN
    IF NEW.pages_read < 1 THEN
        RETURN NEW;
    END IF;

    SELECT last_read_date, reading_streak_days, longest_streak_days
    INTO v_last_date, v_current_streak, v_longest_streak
    FROM public.profiles
    WHERE id = NEW.user_id;

    IF v_last_date = NEW.read_date THEN
        RETURN NEW;
    ELSIF v_last_date = (NEW.read_date - INTERVAL '1 day')::DATE THEN
        v_current_streak := v_current_streak + 1;
    ELSE
        v_current_streak := 1;
    END IF;

    IF v_current_streak IN (3, 7, 15, 30) THEN
        v_bonus_points := v_bonus_points + (v_current_streak * 5);
    END IF;

    UPDATE public.profiles
    SET last_read_date = GREATEST(COALESCE(v_last_date, NEW.read_date), NEW.read_date),
        reading_streak_days = v_current_streak,
        longest_streak_days = GREATEST(v_longest_streak, v_current_streak),
        gamification_points = gamification_points + v_bonus_points,
        updated_at = NOW()
    WHERE id = NEW.user_id;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_update_reading_streak
AFTER INSERT OR UPDATE ON public.reading_logs
FOR EACH ROW
EXECUTE FUNCTION public.fn_update_reading_streak();

-- ----------------------------------------------------------------------------
-- 3. Daily Page Target Estimator (Section 3.3 & 7.2)
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.calculate_daily_page_target(
    p_total_pages INTEGER,
    p_target_days INTEGER
)
RETURNS INTEGER
LANGUAGE sql
IMMUTABLE
AS $$
    SELECT CEIL(p_total_pages::NUMERIC / GREATEST(p_target_days, 1))::INTEGER;
$$;

-- ----------------------------------------------------------------------------
-- 4. Group Activation Trigger & Supervisor Voting (Section 4.1 - 4.2 & 7.2)
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.fn_check_circle_activation()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_joined_count INTEGER;
    v_min_required INTEGER;
    v_current_status circle_status;
BEGIN
    SELECT COUNT(*) INTO v_joined_count
    FROM public.study_circle_members
    WHERE circle_id = NEW.circle_id AND invite_status = 'joined';

    SELECT min_members_required, status
    INTO v_min_required, v_current_status
    FROM public.study_circles
    WHERE id = NEW.circle_id;

    IF v_current_status = 'forming' AND v_joined_count >= v_min_required THEN
        UPDATE public.study_circles
        SET status = 'active'
        WHERE id = NEW.circle_id;

        -- Reward creator for successfully activating a study circle
        UPDATE public.profiles
        SET gamification_points = gamification_points + 40
        WHERE id = (SELECT created_by FROM public.study_circles WHERE id = NEW.circle_id);
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_check_circle_activation
AFTER INSERT OR UPDATE ON public.study_circle_members
FOR EACH ROW
EXECUTE FUNCTION public.fn_check_circle_activation();

CREATE OR REPLACE FUNCTION public.fn_tally_supervisor_votes()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_member_count INTEGER;
    v_leader_id UUID;
    v_leader_votes INTEGER;
    v_assign_type supervisor_assignment_type;
BEGIN
    SELECT supervisor_assignment_type INTO v_assign_type
    FROM public.study_circles
    WHERE id = NEW.circle_id;

    -- Do not override an admin-assigned supervisor
    IF v_assign_type = 'admin_assigned' THEN
        RETURN NEW;
    END IF;

    SELECT COUNT(*) INTO v_member_count
    FROM public.study_circle_members
    WHERE circle_id = NEW.circle_id AND invite_status = 'joined';

    SELECT candidate_id, COUNT(*)
    INTO v_leader_id, v_leader_votes
    FROM public.supervisor_votes
    WHERE circle_id = NEW.circle_id
    GROUP BY candidate_id
    ORDER BY COUNT(*) DESC
    LIMIT 1;

    -- Majority (> 50%) of joined members elects the supervisor
    IF v_leader_id IS NOT NULL AND v_leader_votes > (v_member_count / 2) THEN
        UPDATE public.study_circles
        SET supervisor_id = v_leader_id,
            supervisor_assignment_type = 'member_voted'
        WHERE id = NEW.circle_id;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_tally_supervisor_votes
AFTER INSERT OR UPDATE ON public.supervisor_votes
FOR EACH ROW
EXECUTE FUNCTION public.fn_tally_supervisor_votes();

-- ----------------------------------------------------------------------------
-- 5. Peer Motivation ('Poke' Button) Rate-Limiter & Quiet Hours (Section 4.3)
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.send_poke(
    p_circle_id UUID,
    p_chapter_no INTEGER,
    p_receiver_id UUID,
    p_preset_message TEXT
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_sender_id UUID := auth.uid();
    v_dhaka_now TIMESTAMP := NOW() AT TIME ZONE 'Asia/Dhaka';
    v_today DATE := v_dhaka_now::DATE;
    v_hour INTEGER := EXTRACT(HOUR FROM v_dhaka_now);
    v_receiver_status progress_status;
    v_sender_daily_count INTEGER;
    v_receiver_instant_count INTEGER;
    v_is_muted BOOLEAN := FALSE;
    v_delivery_mode TEXT := 'instant';
    v_poke_id UUID;
BEGIN
    IF v_sender_id IS NULL OR v_sender_id = p_receiver_id THEN
        RAISE EXCEPTION 'Invalid poke sender or receiver';
    END IF;

    -- Rule 1: Only members who have NOT reached 'Exercises Submitted' can be poked
    SELECT status INTO v_receiver_status
    FROM public.chapter_progress
    WHERE circle_id = p_circle_id AND user_id = p_receiver_id AND chapter_no = p_chapter_no;

    IF v_receiver_status IN ('Exercises Submitted', 'Learning Verified') THEN
        RAISE EXCEPTION 'এই সদস্য ইতোমধ্যে অনুশীলনী জমা দিয়েছেন।';
    END IF;

    -- Rule 2: Sender max 3 pokes per day across all recipients
    SELECT COUNT(*) INTO v_sender_daily_count
    FROM public.pokes
    WHERE sender_id = v_sender_id AND poke_date = v_today;

    IF v_sender_daily_count >= 3 THEN
        RAISE EXCEPTION 'আপনি আজ সর্বোচ্চ ৩টি Poke পাঠিয়ে ফেলেছেন। আগামীকাল আবার চেষ্টা করুন।';
    END IF;

    -- Rule 3: Check recipient mute settings (24h, 7d, or entire chapter)
    SELECT EXISTS (
        SELECT 1 FROM public.poke_mute_settings
        WHERE user_id = p_receiver_id
          AND circle_id = p_circle_id
          AND (
              (mute_scope IN ('24h', '7d') AND muted_until > NOW())
              OR (mute_scope = 'chapter' AND chapter_no = p_chapter_no)
          )
    ) INTO v_is_muted;

    -- Rule 4: Quiet Hours (10 PM - 7 AM Dhaka time) & Max 3 instant notifications/day
    SELECT COUNT(*) INTO v_receiver_instant_count
    FROM public.pokes
    WHERE receiver_id = p_receiver_id
      AND poke_date = v_today
      AND delivery_mode = 'instant';

    IF v_is_muted THEN
        v_delivery_mode := 'muted';
    ELSIF v_hour >= 22 OR v_hour < 7 THEN
        v_delivery_mode := 'quiet_hours_queued';
    ELSIF v_receiver_instant_count >= 3 THEN
        v_delivery_mode := 'daily_summary';
    ELSE
        v_delivery_mode := 'instant';
    END IF;

    INSERT INTO public.pokes (
        circle_id, chapter_no, sender_id, receiver_id, preset_message, delivery_mode, poke_date
    )
    VALUES (
        p_circle_id, p_chapter_no, v_sender_id, p_receiver_id, p_preset_message, v_delivery_mode, v_today
    )
    RETURNING id INTO v_poke_id;

    UPDATE public.profiles
    SET gamification_points = gamification_points + 2
    WHERE id = v_sender_id;

    RETURN jsonb_build_object(
        'poke_id', v_poke_id,
        'delivery_mode', v_delivery_mode
    );
END;
$$;

-- Safe view for Supervisors: exposes counts ONLY, never message text!
CREATE OR REPLACE VIEW public.supervisor_poke_summary AS
SELECT
    circle_id,
    chapter_no,
    receiver_id AS member_id,
    COUNT(*) AS pokes_received_count,
    COUNT(quick_reply) AS replies_sent_count
FROM public.pokes
GROUP BY circle_id, chapter_no, receiver_id;

-- ----------------------------------------------------------------------------
-- 6. Auto-Scoring Engine (A-E), 48h Re-Attempt & Locking (Section 5.3)
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.submit_chapter_exercises(
    p_progress_id UUID,
    p_answers JSONB, -- Array of {"question_id": "...", "answer": ...}
    p_is_reattempt BOOLEAN DEFAULT FALSE
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_progress public.chapter_progress%ROWTYPE;
    v_item JSONB;
    v_q public.chapter_questions%ROWTYPE;
    v_correct JSONB;
    v_auto_total INTEGER := 0;
    v_genuineness_ok BOOLEAN := TRUE;
    v_attempt INTEGER := 1;
    v_marks INTEGER;
BEGIN
    SELECT * INTO v_progress
    FROM public.chapter_progress
    WHERE id = p_progress_id AND user_id = auth.uid();

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Progress record not found';
    END IF;

    IF NOT p_is_reattempt THEN
        IF v_progress.status <> 'Reading Completed' THEN
            RAISE EXCEPTION 'অনুশীলনী জমা দেওয়ার আগে পড়া শেষের ঘোষণা দিন।';
        END IF;
    ELSE
        IF v_progress.reattempt_used THEN
            RAISE EXCEPTION 'অটো-স্কোরড অংশে শুধুমাত্র একবার রি-অ্যাটেম্পট করা যায়।';
        END IF;
        IF v_progress.exercises_submitted_at > (NOW() - INTERVAL '48 hours') THEN
            RAISE EXCEPTION 'প্রথম জমা দেওয়ার ৪৮ ঘণ্টা পর রি-অ্যাটেম্পট করা যাবে।';
        END IF;
        v_attempt := 2;
    END IF;

    FOR v_item IN SELECT * FROM jsonb_array_elements(p_answers)
    LOOP
        SELECT * INTO v_q
        FROM public.chapter_questions
        WHERE id = (v_item->>'question_id')::UUID
          AND chapter_id = v_progress.chapter_id;

        IF v_q.id IS NULL THEN
            CONTINUE;
        END IF;

        -- On re-attempt, only auto-scored questions (A-E) can be re-submitted
        IF p_is_reattempt AND NOT v_q.is_auto_scored THEN
            CONTINUE;
        END IF;

        v_marks := NULL;
        IF v_q.is_auto_scored THEN
            SELECT correct_answer INTO v_correct
            FROM public.chapter_answer_keys
            WHERE question_id = v_q.id;

            IF v_correct = (v_item->'answer') THEN
                v_marks := 1;
                v_auto_total := v_auto_total + 1;
            ELSE
                v_marks := 0;
                IF v_q.is_genuineness_check THEN
                    v_genuineness_ok := FALSE;
                END IF;
            END IF;
        END IF;

        INSERT INTO public.exercise_submissions (
            progress_id, question_id, attempt_no, answer_payload, is_locked, awarded_marks
        )
        VALUES (
            p_progress_id, v_q.id, v_attempt, v_item->'answer', TRUE, v_marks
        )
        ON CONFLICT (progress_id, question_id, attempt_no) DO NOTHING;
    END LOOP;

    IF NOT p_is_reattempt THEN
        UPDATE public.chapter_progress
        SET status = 'Exercises Submitted',
            exercises_submitted_at = NOW(),
            first_auto_score = v_auto_total,
            auto_score = v_auto_total,
            genuineness_passed = v_genuineness_ok,
            updated_at = NOW()
        WHERE id = p_progress_id;
    ELSE
        UPDATE public.chapter_progress
        SET auto_score = v_auto_total,
            reattempt_used = TRUE,
            reattempt_at = NOW(),
            genuineness_passed = v_genuineness_ok,
            updated_at = NOW()
        WHERE id = p_progress_id;
    END IF;

    RETURN jsonb_build_object(
        'auto_score', v_auto_total,
        'attempt_no', v_attempt,
        'answer_key_unlocked', TRUE
    );
END;
$$;

-- ----------------------------------------------------------------------------
-- 7. Calibration Flags, Red-Flags, Unlocking & 80% Group Session Trigger
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.fn_evaluate_chapter_verification()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_chapter public.chapters%ROWTYPE;
    v_total_score INTEGER;
    v_score_pct NUMERIC;
    v_avg_self_rating NUMERIC;
    v_cal_flags calibration_flag[] := '{}';
    v_red_flags TEXT[] := '{}';
    v_total_members INTEGER;
    v_submitted_members INTEGER;
    v_last_session TIMESTAMPTZ;
BEGIN
    SELECT * INTO v_chapter FROM public.chapters WHERE id = NEW.chapter_id;

    v_total_score := COALESCE(NEW.auto_score, 0) + COALESCE(NEW.supervisor_score, 0);
    v_score_pct := (v_total_score::NUMERIC / GREATEST(v_chapter.total_marks, 1)) * 100.0;

    -- Self-Rating Calibration (Section 5.3)
    IF COALESCE(array_length(NEW.self_ratings, 1), 0) > 0 THEN
        SELECT AVG(val)::NUMERIC INTO v_avg_self_rating
        FROM unnest(NEW.self_ratings) AS val;

        IF v_avg_self_rating >= 4.0 AND v_score_pct < 50.0 THEN
            v_cal_flags := array_append(v_cal_flags, 'over-confident'::calibration_flag);
        ELSIF v_avg_self_rating <= 2.0 AND v_score_pct >= 80.0 THEN
            v_cal_flags := array_append(v_cal_flags, 'under-confident'::calibration_flag);
        END IF;
    END IF;

    -- Red-Flag Indicators (Section 5.4: >= 3 indicators => Needs Support)
    IF NEW.status = 'Not Started' THEN
        v_red_flags := array_append(v_red_flags, 'no_reading_declaration');
    END IF;
    IF NEW.expected_completion_date IS NOT NULL
       AND CURRENT_DATE > (NEW.expected_completion_date + INTERVAL '7 days')::DATE
       AND NEW.status NOT IN ('Exercises Submitted', 'Learning Verified') THEN
        v_red_flags := array_append(v_red_flags, 'overdue_more_than_7_days');
    END IF;
    IF NEW.status IN ('Exercises Submitted', 'Learning Verified')
       AND NEW.supervisor_score IS NOT NULL
       AND (v_total_score < v_chapter.red_flag_marks OR v_score_pct < 50.0) THEN
        v_red_flags := array_append(v_red_flags, 'low_assessment_score');
    END IF;
    IF NOT NEW.genuineness_passed THEN
        v_red_flags := array_append(v_red_flags, 'genuineness_check_failed');
    END IF;
    IF NEW.status = 'Exercises Submitted'
       AND NOT NEW.writing_task_submitted
       AND NEW.exercises_submitted_at < (NOW() - INTERVAL '5 days') THEN
        v_red_flags := array_append(v_red_flags, 'writing_task_missed');
    END IF;

    NEW.calibration_flags := v_cal_flags;
    NEW.red_flag_indicators := v_red_flags;
    NEW.red_flag_count := COALESCE(array_length(v_red_flags, 1), 0);
    NEW.needs_support := (NEW.red_flag_count >= 3);

    -- Individual Chapter Verification & Unlocking (Section 5.4 & 7.2)
    IF NEW.status = 'Exercises Submitted'
       AND (v_total_score >= v_chapter.pass_marks OR NEW.supervisor_override = TRUE)
       AND COALESCE(array_length(NEW.self_ratings, 1), 0) > 0
       AND NEW.writing_task_submitted = TRUE THEN
        NEW.status := 'Learning Verified';
        NEW.learning_verified_at := COALESCE(NEW.learning_verified_at, NOW());

        UPDATE public.profiles
        SET gamification_points = gamification_points + 30
        WHERE id = NEW.user_id;
    END IF;

    -- Group Session Threshold Trigger (>= 80% submitted OR >= 10 days elapsed)
    SELECT COUNT(*) INTO v_total_members
    FROM public.study_circle_members
    WHERE circle_id = NEW.circle_id AND invite_status = 'joined';

    SELECT COUNT(*) INTO v_submitted_members
    FROM public.chapter_progress
    WHERE circle_id = NEW.circle_id
      AND chapter_no = NEW.chapter_no
      AND status IN ('Exercises Submitted', 'Learning Verified');

    SELECT last_session_at INTO v_last_session
    FROM public.study_circles
    WHERE id = NEW.circle_id;

    IF v_total_members > 0 AND (
        ((v_submitted_members::NUMERIC / v_total_members) * 100.0) >= 80.0
        OR (v_last_session IS NOT NULL AND NOW() >= (v_last_session + INTERVAL '10 days'))
    ) THEN
        UPDATE public.study_circles
        SET next_session_announced_at = COALESCE(next_session_announced_at, NOW())
        WHERE id = NEW.circle_id AND current_group_chapter = NEW.chapter_no;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_evaluate_chapter_verification
BEFORE INSERT OR UPDATE ON public.chapter_progress
FOR EACH ROW
EXECUTE FUNCTION public.fn_evaluate_chapter_verification();

-- Safe Group Dashboard Progress View (hides private marks & writing!)
CREATE OR REPLACE VIEW public.group_chapter_progress_public AS
SELECT
    cp.circle_id,
    cp.chapter_no,
    cp.user_id,
    p.name AS member_name,
    p.department,
    p.avatar_url,
    cp.status,
    cp.expected_completion_date,
    cp.updated_at
FROM public.chapter_progress cp
JOIN public.profiles p ON p.id = cp.user_id;

-- ----------------------------------------------------------------------------
-- 8. Co-Author Matchmaking RPC (Section 6.2: Complementary Skills + Interests)
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.suggest_coauthor_team(
    p_user_id UUID,
    p_team_size INTEGER DEFAULT 4
)
RETURNS TABLE (
    candidate_id UUID,
    name TEXT,
    department TEXT,
    shared_interests TEXT[],
    complementary_skills TEXT[],
    rating NUMERIC,
    match_score NUMERIC
)
LANGUAGE sql
STABLE
SECURITY DEFINER
AS $$
    WITH me AS (
        SELECT id, research_interests, skills
        FROM public.profiles
        WHERE id = p_user_id
    )
    SELECT
        p.id AS candidate_id,
        p.name,
        p.department,
        ARRAY(
            SELECT UNNEST(p.research_interests)
            INTERSECT
            SELECT UNNEST(me.research_interests)
        ) AS shared_interests,
        ARRAY(
            SELECT UNNEST(p.skills)
            EXCEPT
            SELECT UNNEST(me.skills)
        ) AS complementary_skills,
        p.rating,
        (
            COALESCE(array_length(ARRAY(
                SELECT UNNEST(p.research_interests) INTERSECT SELECT UNNEST(me.research_interests)
            ), 1), 0) * 15.0
            + COALESCE(array_length(ARRAY(
                SELECT UNNEST(p.skills) EXCEPT SELECT UNNEST(me.skills)
            ), 1), 0) * 20.0
            + (p.rating * 5.0)
        )::NUMERIC(6,2) AS match_score
    FROM public.profiles p
    CROSS JOIN me
    WHERE p.id <> me.id
    ORDER BY match_score DESC, p.gamification_points DESC
    LIMIT GREATEST(p_team_size, 3);
$$;


-- ----------------------------------------------------------------------------
-- 9. Auto-Provision Researcher Profile on Supabase Auth Signup
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.handle_new_auth_user()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_name TEXT;
    v_dept TEXT;
    v_ref_code TEXT;
BEGIN
    v_name := COALESCE(
        NULLIF(TRIM(NEW.raw_user_meta_data->>'name'), ''),
        SPLIT_PART(COALESCE(NEW.email, 'Researcher'), '@', 1)
    );
    v_dept := COALESCE(
        NULLIF(TRIM(NEW.raw_user_meta_data->>'department'), ''),
        'General Research'
    );
    v_ref_code := 'SPARC-' || UPPER(SUBSTRING(REPLACE(NEW.id::TEXT, '-', '') FROM 1 FOR 6));

    INSERT INTO public.profiles (id, name, email, department, referral_code)
    VALUES (NEW.id, v_name, NEW.email, v_dept, v_ref_code)
    ON CONFLICT (id) DO NOTHING;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
AFTER INSERT ON auth.users
FOR EACH ROW
EXECUTE FUNCTION public.handle_new_auth_user();
