-- ============================================================================
-- SPARC Study Circle (v2.0 Complete Edition)
-- Migration 001: Core Relational Schema, Enums, Constraints & Indexes
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

-- ----------------------------------------------------------------------------
-- 1. Custom Enumerated Types
-- ----------------------------------------------------------------------------
CREATE TYPE user_role AS ENUM ('member', 'supervisor', 'sparc_rep', 'admin');
CREATE TYPE highlight_visibility AS ENUM ('private', 'supervisor', 'group');
CREATE TYPE chapter_type AS ENUM ('standard', 'long');
CREATE TYPE question_category AS ENUM (
    'fill_blank',
    'one_word',
    'mcq',
    'true_false',
    'matching',
    'short_answer',
    'compare',
    'scenario',
    'application',
    'critical_thinking'
);
CREATE TYPE circle_status AS ENUM ('forming', 'active', 'completed');
CREATE TYPE supervisor_assignment_type AS ENUM ('member_voted', 'admin_assigned');
CREATE TYPE progress_status AS ENUM (
    'Not Started',
    'Reading Started',
    'Reading in Progress',
    'Reading Completed',
    'Exercises Submitted',
    'Learning Verified'
);
CREATE TYPE calibration_flag AS ENUM ('over-confident', 'under-confident');
CREATE TYPE wallet_tx_type AS ENUM (
    'topup_sslcommerz',
    'topup_bkash_pgw',
    'topup_manual_trx',
    'referral_bonus',
    'physical_book_order',
    'supervision_escrow',
    'supervision_release',
    'refund'
);
CREATE TYPE wallet_tx_status AS ENUM ('pending', 'verified', 'rejected');
CREATE TYPE supervision_type AS ENUM ('free_self_guided', 'paid_supervised');
CREATE TYPE session_video_provider AS ENUM ('jitsi_in_app', 'google_meet', 'zoom');
CREATE TYPE book_order_status AS ENUM ('pending', 'approved', 'dispatched', 'delivered', 'cancelled');

-- ----------------------------------------------------------------------------
-- 2. User Profiles, Directory & Messaging (Section 2.1 - 2.2)
-- ----------------------------------------------------------------------------
CREATE TABLE public.profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    email TEXT UNIQUE,
    avatar_url TEXT,
    department TEXT NOT NULL,
    research_interests TEXT[] NOT NULL DEFAULT '{}',
    skills TEXT[] NOT NULL DEFAULT '{}',
    role user_role NOT NULL DEFAULT 'member',
    rating NUMERIC(3,2) NOT NULL DEFAULT 5.00 CHECK (rating >= 0 AND rating <= 5.00),
    gamification_points INTEGER NOT NULL DEFAULT 0 CHECK (gamification_points >= 0),
    reading_streak_days INTEGER NOT NULL DEFAULT 0 CHECK (reading_streak_days >= 0),
    longest_streak_days INTEGER NOT NULL DEFAULT 0 CHECK (longest_streak_days >= 0),
    last_read_date DATE,
    referral_code TEXT NOT NULL UNIQUE,
    referred_by UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    wallet_balance NUMERIC(12,2) NOT NULL DEFAULT 0.00 CHECK (wallet_balance >= 0),
    earned_badges TEXT[] NOT NULL DEFAULT '{}',
    fcm_token TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_profiles_department ON public.profiles(department);
CREATE INDEX idx_profiles_research_interests ON public.profiles USING GIN (research_interests);
CREATE INDEX idx_profiles_skills ON public.profiles USING GIN (skills);
CREATE INDEX idx_profiles_gamification ON public.profiles(gamification_points DESC, reading_streak_days DESC);

CREATE TABLE public.member_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sender_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    receiver_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    book_id UUID,
    circle_id UUID,
    template_key TEXT,
    body TEXT NOT NULL CHECK (char_length(body) BETWEEN 1 AND 1000),
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_member_messages_participants ON public.member_messages(receiver_id, sender_id, created_at DESC);

-- ----------------------------------------------------------------------------
-- 3. Credit Wallet & Referral Ledger (Section 2.3)
-- ----------------------------------------------------------------------------
CREATE TABLE public.wallet_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    tx_type wallet_tx_type NOT NULL,
    -- Positive for credits (topups, referral_bonus, refund), negative for debits (orders, supervision)
    amount_bdt NUMERIC(12,2) NOT NULL CHECK (amount_bdt <> 0),
    status wallet_tx_status NOT NULL DEFAULT 'pending',
    gateway_ref TEXT,
    manual_trx_id TEXT UNIQUE,
    payment_phone TEXT,
    verified_by UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    verified_at TIMESTAMPTZ
);

CREATE INDEX idx_wallet_tx_user ON public.wallet_transactions(user_id, created_at DESC);
CREATE INDEX idx_wallet_tx_pending ON public.wallet_transactions(status) WHERE status = 'pending';

-- ----------------------------------------------------------------------------
-- 4. Digital & Physical Library + Locked Question Bank (Section 3 & 5.3)
-- ----------------------------------------------------------------------------
CREATE TABLE public.books (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title TEXT NOT NULL,
    subtitle TEXT,
    author TEXT NOT NULL,
    category TEXT NOT NULL,
    description TEXT,
    cover_url TEXT,
    pdf_url TEXT NOT NULL,
    physical_price_bdt NUMERIC(10,2) NOT NULL DEFAULT 0.00,
    total_chapters INTEGER NOT NULL CHECK (total_chapters > 0),
    is_first_chapter_free BOOLEAN NOT NULL DEFAULT TRUE,
    min_members_default INTEGER NOT NULL DEFAULT 10 CHECK (min_members_default >= 2),
    copyright_verified BOOLEAN NOT NULL DEFAULT TRUE,
    is_published BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE public.chapters (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    book_id UUID NOT NULL REFERENCES public.books(id) ON DELETE CASCADE,
    chapter_no INTEGER NOT NULL CHECK (chapter_no >= 1),
    title TEXT NOT NULL,
    type chapter_type NOT NULL DEFAULT 'standard',
    total_pages INTEGER NOT NULL CHECK (total_pages > 0),
    default_reading_minutes INTEGER NOT NULL CHECK (default_reading_minutes > 0),
    page_matrix JSONB NOT NULL DEFAULT '[]'::jsonb,
    sections JSONB NOT NULL DEFAULT '[]'::jsonb,
    audio_summary_url TEXT,
    audio_duration_seconds INTEGER DEFAULT 360,
    infographic_notes_urls TEXT[] NOT NULL DEFAULT '{}',
    total_marks INTEGER NOT NULL CHECK (
        (type = 'standard' AND total_marks = 52) OR (type = 'long' AND total_marks = 61)
    ),
    pass_marks INTEGER NOT NULL CHECK (
        (type = 'standard' AND pass_marks = 32) OR (type = 'long' AND pass_marks = 37)
    ),
    red_flag_marks INTEGER NOT NULL CHECK (
        (type = 'standard' AND red_flag_marks = 26) OR (type = 'long' AND red_flag_marks = 31)
    ),
    learning_outcomes JSONB NOT NULL DEFAULT '[]'::jsonb,
    writing_task_prompt TEXT NOT NULL,
    writing_min_words INTEGER NOT NULL DEFAULT 500,
    writing_max_words INTEGER NOT NULL DEFAULT 700,
    session_parts INTEGER NOT NULL DEFAULT 1 CHECK (session_parts IN (1, 2)),
    slide_plan_url TEXT,
    UNIQUE (book_id, chapter_no)
);

CREATE TABLE public.chapter_questions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chapter_id UUID NOT NULL REFERENCES public.chapters(id) ON DELETE CASCADE,
    question_no INTEGER NOT NULL CHECK (question_no >= 1),
    section_code CHAR(1) NOT NULL CHECK (section_code IN ('A','B','C','D','E','F','G','H','I','J')),
    category question_category NOT NULL,
    is_auto_scored BOOLEAN NOT NULL,
    is_genuineness_check BOOLEAN NOT NULL DEFAULT FALSE,
    max_marks INTEGER NOT NULL CHECK (
        (is_auto_scored = TRUE AND max_marks = 1) OR (is_auto_scored = FALSE AND max_marks = 3)
    ),
    prompt_bn TEXT NOT NULL,
    prompt_en TEXT,
    options JSONB NOT NULL DEFAULT '[]'::jsonb,
    learning_outcome_index INTEGER NOT NULL DEFAULT 0,
    UNIQUE (chapter_id, question_no)
);

-- Isolated table so RLS prevents reading answers until Exercises Submitted!
CREATE TABLE public.chapter_answer_keys (
    question_id UUID PRIMARY KEY REFERENCES public.chapter_questions(id) ON DELETE CASCADE,
    correct_answer JSONB NOT NULL,
    explanation_bn TEXT,
    explanation_en TEXT,
    supervisor_model_points JSONB NOT NULL DEFAULT '[]'::jsonb
);

CREATE TABLE public.reading_highlights (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    book_id UUID NOT NULL REFERENCES public.books(id) ON DELETE CASCADE,
    chapter_id UUID NOT NULL REFERENCES public.chapters(id) ON DELETE CASCADE,
    page_no INTEGER NOT NULL CHECK (page_no >= 1),
    percentage_read NUMERIC(5,2) NOT NULL DEFAULT 0 CHECK (percentage_read BETWEEN 0 AND 100),
    selection_rect JSONB NOT NULL DEFAULT '{}'::jsonb,
    highlighted_text TEXT NOT NULL,
    note_text TEXT,
    style TEXT NOT NULL DEFAULT 'highlight' CHECK (style IN ('highlight', 'underline')),
    visibility highlight_visibility NOT NULL DEFAULT 'private',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_highlights_chapter_vis ON public.reading_highlights(chapter_id, visibility);

CREATE TABLE public.reading_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    book_id UUID NOT NULL REFERENCES public.books(id) ON DELETE CASCADE,
    chapter_id UUID NOT NULL REFERENCES public.chapters(id) ON DELETE CASCADE,
    read_date DATE NOT NULL DEFAULT CURRENT_DATE,
    pages_read INTEGER NOT NULL DEFAULT 1 CHECK (pages_read >= 0),
    minutes_spent INTEGER NOT NULL DEFAULT 0 CHECK (minutes_spent >= 0),
    UNIQUE (user_id, chapter_id, read_date)
);

CREATE TABLE public.physical_book_orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    book_id UUID NOT NULL REFERENCES public.books(id) ON DELETE RESTRICT,
    order_mode TEXT NOT NULL CHECK (order_mode IN ('purchase', 'borrow')),
    delivery_address TEXT NOT NULL,
    contact_phone TEXT NOT NULL,
    amount_bdt NUMERIC(10,2) NOT NULL DEFAULT 0.00,
    paid_via_wallet BOOLEAN NOT NULL DEFAULT FALSE,
    status book_order_status NOT NULL DEFAULT 'pending',
    assigned_rep_id UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    admin_notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ----------------------------------------------------------------------------
-- 5. Book-Centric Study Circles, Supervisor Voting, Poke & Debate (Section 4)
-- ----------------------------------------------------------------------------
CREATE TABLE public.study_circles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL,
    book_id UUID NOT NULL REFERENCES public.books(id) ON DELETE RESTRICT,
    created_by UUID NOT NULL REFERENCES public.profiles(id) ON DELETE RESTRICT,
    min_members_required INTEGER NOT NULL DEFAULT 10 CHECK (min_members_required >= 2),
    status circle_status NOT NULL DEFAULT 'forming',
    supervisor_id UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    supervisor_assignment_type supervisor_assignment_type,
    current_group_chapter INTEGER NOT NULL DEFAULT 1,
    last_session_at TIMESTAMPTZ,
    next_session_announced_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE public.study_circle_members (
    circle_id UUID NOT NULL REFERENCES public.study_circles(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    invite_status TEXT NOT NULL DEFAULT 'joined' CHECK (invite_status IN ('invited', 'joined', 'left')),
    joined_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (circle_id, user_id)
);

CREATE TABLE public.supervisor_votes (
    circle_id UUID NOT NULL REFERENCES public.study_circles(id) ON DELETE CASCADE,
    voter_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    candidate_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (circle_id, voter_id)
);

CREATE TABLE public.pokes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    circle_id UUID NOT NULL REFERENCES public.study_circles(id) ON DELETE CASCADE,
    chapter_no INTEGER NOT NULL,
    sender_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    receiver_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    preset_message TEXT NOT NULL CHECK (
        preset_message IN (
            'এগিয়ে চলুন!',
            'পড়া কেমন চলছে?',
            'আমরা আপনার অপেক্ষায়!',
            'চলুন একসাথে অধ্যায়টি শেষ করি।',
            'আপনি পারবেন!'
        )
    ),
    quick_reply TEXT CHECK (quick_reply IN ('ধন্যবাদ!', 'করছি!')),
    delivery_mode TEXT NOT NULL DEFAULT 'instant' CHECK (
        delivery_mode IN ('instant', 'daily_summary', 'quiet_hours_queued', 'muted')
    ),
    poke_date DATE NOT NULL DEFAULT (NOW() AT TIME ZONE 'Asia/Dhaka')::DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    replied_at TIMESTAMPTZ,
    -- Sender can poke a specific person at most ONCE per day
    UNIQUE (sender_id, receiver_id, poke_date)
);

CREATE TABLE public.poke_mute_settings (
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    circle_id UUID NOT NULL REFERENCES public.study_circles(id) ON DELETE CASCADE,
    mute_scope TEXT NOT NULL CHECK (mute_scope IN ('24h', '7d', 'chapter')),
    chapter_no INTEGER,
    muted_until TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, circle_id)
);

CREATE TABLE public.chapter_polls (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    circle_id UUID NOT NULL REFERENCES public.study_circles(id) ON DELETE CASCADE,
    chapter_no INTEGER NOT NULL,
    created_by UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    question TEXT NOT NULL,
    options JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE public.chapter_poll_votes (
    poll_id UUID NOT NULL REFERENCES public.chapter_polls(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    option_index INTEGER NOT NULL CHECK (option_index >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (poll_id, user_id)
);

CREATE TABLE public.debate_threads (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    circle_id UUID NOT NULL REFERENCES public.study_circles(id) ON DELETE CASCADE,
    chapter_no INTEGER NOT NULL,
    author_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    topic_title TEXT NOT NULL,
    stance_prompt TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE public.debate_comments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    thread_id UUID NOT NULL REFERENCES public.debate_threads(id) ON DELETE CASCADE,
    author_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    stance TEXT CHECK (stance IN ('support', 'counter', 'synthesis')),
    body TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ----------------------------------------------------------------------------
-- 6. 6-Stage Chapter Progress, Submissions & Sessions (Section 5)
-- ----------------------------------------------------------------------------
CREATE TABLE public.chapter_progress (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    circle_id UUID NOT NULL REFERENCES public.study_circles(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    chapter_id UUID NOT NULL REFERENCES public.chapters(id) ON DELETE CASCADE,
    chapter_no INTEGER NOT NULL,
    status progress_status NOT NULL DEFAULT 'Not Started',
    reading_started_at TIMESTAMPTZ,
    expected_completion_date DATE,
    date_modified_count INTEGER NOT NULL DEFAULT 0 CHECK (date_modified_count BETWEEN 0 AND 1),
    daily_target_pages NUMERIC(6,2),
    target_days INTEGER,
    completed_sections TEXT[] NOT NULL DEFAULT '{}',
    reading_completed_at TIMESTAMPTZ,
    exercises_submitted_at TIMESTAMPTZ,
    first_auto_score INTEGER,
    auto_score INTEGER NOT NULL DEFAULT 0,
    reattempt_used BOOLEAN NOT NULL DEFAULT FALSE,
    reattempt_at TIMESTAMPTZ,
    supervisor_score INTEGER,
    supervisor_graded_by UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    supervisor_override BOOLEAN NOT NULL DEFAULT FALSE,
    self_ratings INTEGER[] NOT NULL DEFAULT '{}',
    calibration_flags calibration_flag[] NOT NULL DEFAULT '{}',
    genuineness_passed BOOLEAN NOT NULL DEFAULT TRUE,
    red_flag_indicators TEXT[] NOT NULL DEFAULT '{}',
    red_flag_count INTEGER NOT NULL DEFAULT 0,
    needs_support BOOLEAN NOT NULL DEFAULT FALSE,
    writing_task_submitted BOOLEAN NOT NULL DEFAULT FALSE,
    learning_verified_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (circle_id, user_id, chapter_no)
);

CREATE INDEX idx_chapter_progress_circle ON public.chapter_progress(circle_id, chapter_no, status);

CREATE TABLE public.exercise_submissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    progress_id UUID NOT NULL REFERENCES public.chapter_progress(id) ON DELETE CASCADE,
    question_id UUID NOT NULL REFERENCES public.chapter_questions(id) ON DELETE CASCADE,
    attempt_no INTEGER NOT NULL DEFAULT 1 CHECK (attempt_no IN (1, 2)),
    answer_payload JSONB NOT NULL,
    is_locked BOOLEAN NOT NULL DEFAULT TRUE,
    awarded_marks INTEGER CHECK (awarded_marks BETWEEN 0 AND 3),
    supervisor_feedback TEXT,
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (progress_id, question_id, attempt_no)
);

CREATE TABLE public.online_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    circle_id UUID REFERENCES public.study_circles(id) ON DELETE CASCADE,
    chapter_no INTEGER,
    part_no INTEGER NOT NULL DEFAULT 1 CHECK (part_no IN (1, 2)),
    is_research_clinic BOOLEAN NOT NULL DEFAULT FALSE,
    title TEXT NOT NULL,
    scheduled_at TIMESTAMPTZ NOT NULL,
    duration_minutes INTEGER NOT NULL DEFAULT 40,
    presenter_user_id UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    slide_upload_url TEXT,
    slide_uploaded_at TIMESTAMPTZ,
    video_provider session_video_provider NOT NULL DEFAULT 'jitsi_in_app',
    jitsi_room_name TEXT,
    external_meeting_url TEXT,
    status TEXT NOT NULL DEFAULT 'scheduled' CHECK (status IN ('scheduled', 'live', 'completed', 'cancelled')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ----------------------------------------------------------------------------
-- 7. Writing Module, Co-Author Matchmaking, Supervision & Certificates (Sec 6)
-- ----------------------------------------------------------------------------
CREATE TABLE public.writing_submissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    progress_id UUID NOT NULL REFERENCES public.chapter_progress(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    book_id UUID NOT NULL REFERENCES public.books(id) ON DELETE CASCADE,
    chapter_no INTEGER NOT NULL,
    title TEXT NOT NULL,
    content TEXT NOT NULL,
    word_count INTEGER NOT NULL CHECK (word_count >= 0),
    language TEXT NOT NULL DEFAULT 'bn' CHECK (language IN ('bn', 'en')),
    ai_feedback JSONB,
    supervisor_feedback TEXT,
    supervisor_approved BOOLEAN NOT NULL DEFAULT FALSE,
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (progress_id)
);

CREATE TABLE public.research_groups (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL,
    paper_topic TEXT NOT NULL,
    source_circle_id UUID REFERENCES public.study_circles(id) ON DELETE SET NULL,
    created_by UUID NOT NULL REFERENCES public.profiles(id) ON DELETE RESTRICT,
    supervision_type supervision_type NOT NULL DEFAULT 'free_self_guided',
    supervisor_id UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    agreed_sessions INTEGER NOT NULL DEFAULT 0,
    agreed_fee_bdt NUMERIC(12,2) NOT NULL DEFAULT 0.00,
    payment_status TEXT NOT NULL DEFAULT 'unpaid' CHECK (payment_status IN ('unpaid', 'paid_via_wallet')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE public.research_group_members (
    research_group_id UUID NOT NULL REFERENCES public.research_groups(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    assigned_skill_role TEXT,
    joined_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (research_group_id, user_id)
);

CREATE TABLE public.supervision_negotiations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    research_group_id UUID NOT NULL REFERENCES public.research_groups(id) ON DELETE CASCADE,
    supervisor_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    proposed_by UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    proposed_sessions INTEGER NOT NULL CHECK (proposed_sessions > 0),
    proposed_fee_bdt NUMERIC(12,2) NOT NULL CHECK (proposed_fee_bdt >= 0),
    message TEXT,
    status TEXT NOT NULL DEFAULT 'negotiating' CHECK (
        status IN ('negotiating', 'agreed', 'paid_via_wallet', 'rejected')
    ),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE public.certificates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    certificate_code TEXT NOT NULL UNIQUE,
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    circle_id UUID REFERENCES public.study_circles(id) ON DELETE SET NULL,
    book_id UUID REFERENCES public.books(id) ON DELETE SET NULL,
    certificate_type TEXT NOT NULL CHECK (certificate_type IN ('micro_badge', 'study_circle_completion')),
    badge_key TEXT,
    title TEXT NOT NULL,
    pdf_url TEXT,
    share_card_url TEXT,
    issued_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
