-- ============================================================================
-- Migration 012: Production Clean Slate — Wipe All Seed/Demo Data & Test Users
-- ----------------------------------------------------------------------------
-- 1. Disables & replaces ensure_user_in_default_cohort() so new sign-ups are
--    never enrolled into demo circles, polls, debates, sessions, or teams.
-- 2. Truncates all seeded/demo books, chapters, questions, circles, progress,
--    submissions, polls, debates, sessions, research groups, pokes, messages,
--    wallet transactions, book orders, and certificates.
-- 3. Clears test/demo files in Storage buckets.
-- 4. Removes every demo/test user in auth.users & public.profiles, keeping
--    ONLY the founder Admin account (abamunim@gmail.com) at a clean baseline.
-- ============================================================================

-- 1. Remove auto-demo-cohort trigger permanently
DROP TRIGGER IF EXISTS trg_auto_join_default_cohort ON public.profiles;

CREATE OR REPLACE FUNCTION public.ensure_user_in_default_cohort(p_user_id UUID)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    -- Intentional no-op in production: users create or join real Study Circles.
    RETURN;
END;
$$;

-- 2. Wipe all seeded & demo relational data cleanly
TRUNCATE TABLE
    public.certificates,
    public.supervision_negotiations,
    public.research_group_members,
    public.research_groups,
    public.writing_submissions,
    public.online_sessions,
    public.exercise_submissions,
    public.chapter_progress,
    public.debate_comments,
    public.debate_threads,
    public.chapter_poll_votes,
    public.chapter_polls,
    public.poke_mute_settings,
    public.pokes,
    public.circle_lead_votes,
    public.supervisor_votes,
    public.study_circle_members,
    public.study_circles,
    public.physical_book_orders,
    public.reading_logs,
    public.reading_highlights,
    public.chapter_answer_keys,
    public.chapter_questions,
    public.chapters,
    public.books,
    public.wallet_transactions,
    public.member_messages
RESTART IDENTITY CASCADE;

-- 3. Clear demo/test Storage objects
DELETE FROM storage.objects
WHERE bucket_id IN ('sparc-books', 'sparc-audio', 'sparc-avatars');

-- 4. Delete all demo/test users except founder Admin (abamunim@gmail.com)
DELETE FROM public.profiles p
WHERE LOWER(TRIM(COALESCE(p.email, ''))) <> 'abamunim@gmail.com'
  AND NOT EXISTS (
      SELECT 1 FROM auth.users u
      WHERE u.id = p.id
        AND LOWER(TRIM(COALESCE(u.email, ''))) = 'abamunim@gmail.com'
  );

DELETE FROM auth.users
WHERE LOWER(TRIM(COALESCE(email, ''))) <> 'abamunim@gmail.com';

-- 5. Reset founder Admin profile stats to a clean production baseline
UPDATE public.profiles p
SET role = 'admin',
    base_role = 'admin',
    wallet_balance = 0.00,
    gamification_points = 0,
    reading_streak_days = 0,
    longest_streak_days = 0,
    last_read_date = NULL,
    referred_by = NULL,
    earned_badges = '{}',
    updated_at = NOW()
WHERE LOWER(TRIM(COALESCE(p.email, ''))) = 'abamunim@gmail.com'
   OR EXISTS (
       SELECT 1 FROM auth.users u
       WHERE u.id = p.id
         AND LOWER(TRIM(COALESCE(u.email, ''))) = 'abamunim@gmail.com'
   );
