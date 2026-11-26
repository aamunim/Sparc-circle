-- ============================================================================
-- SPARC Study Circle (v2.0 Complete Edition)
-- Migration 003: Strict Row-Level Security (RLS) & Privacy Policies
-- ============================================================================

ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.member_messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.wallet_transactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.books ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.chapters ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.chapter_questions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.chapter_answer_keys ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.reading_highlights ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.reading_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.physical_book_orders ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.study_circles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.study_circle_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.supervisor_votes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.pokes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.poke_mute_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.chapter_polls ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.chapter_poll_votes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.debate_threads ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.debate_comments ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.chapter_progress ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.exercise_submissions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.online_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.writing_submissions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.research_groups ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.research_group_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.supervision_negotiations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.certificates ENABLE ROW LEVEL SECURITY;

-- Helper role checks
CREATE OR REPLACE FUNCTION public.is_admin()
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
AS $$
    SELECT EXISTS (
        SELECT 1 FROM public.profiles
        WHERE id = auth.uid() AND role = 'admin'
    );
$$;

CREATE OR REPLACE FUNCTION public.is_circle_supervisor(p_circle_id UUID)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
AS $$
    SELECT EXISTS (
        SELECT 1 FROM public.study_circles
        WHERE id = p_circle_id AND supervisor_id = auth.uid()
    ) OR public.is_admin();
$$;

-- 1. Profiles: authenticated members can browse directory; only owner/admin can update
CREATE POLICY profiles_select_authenticated ON public.profiles
    FOR SELECT TO authenticated USING (TRUE);
CREATE POLICY profiles_insert_self ON public.profiles
    FOR INSERT TO authenticated WITH CHECK (id = auth.uid());
CREATE POLICY profiles_update_self_or_admin ON public.profiles
    FOR UPDATE TO authenticated USING (id = auth.uid() OR public.is_admin());

-- 2. Member Messages: private between sender and receiver
CREATE POLICY messages_select_participants ON public.member_messages
    FOR SELECT TO authenticated USING (sender_id = auth.uid() OR receiver_id = auth.uid());
CREATE POLICY messages_insert_sender ON public.member_messages
    FOR INSERT TO authenticated WITH CHECK (sender_id = auth.uid());

-- 3. Wallet Transactions: owner sees own ledger; admin manages verification
CREATE POLICY wallet_select_owner_or_admin ON public.wallet_transactions
    FOR SELECT TO authenticated USING (user_id = auth.uid() OR public.is_admin());
CREATE POLICY wallet_insert_pending_topup ON public.wallet_transactions
    FOR INSERT TO authenticated
    WITH CHECK (user_id = auth.uid() AND status = 'pending' AND amount_bdt > 0);
CREATE POLICY wallet_admin_all ON public.wallet_transactions
    FOR ALL TO authenticated USING (public.is_admin());

-- 4. Books & Chapters: readable by authenticated users; managed by admin
CREATE POLICY books_select_published ON public.books
    FOR SELECT TO authenticated USING (is_published = TRUE OR public.is_admin());
CREATE POLICY books_admin_manage ON public.books
    FOR ALL TO authenticated USING (public.is_admin());

CREATE POLICY chapters_select_all ON public.chapters
    FOR SELECT TO authenticated USING (TRUE);
CREATE POLICY chapters_admin_manage ON public.chapters
    FOR ALL TO authenticated USING (public.is_admin());

-- 5. Questions & LOCKED Answer Keys (Section 5.1 & 7.2)
CREATE POLICY questions_select_unlocked ON public.chapter_questions
    FOR SELECT TO authenticated
    USING (
        public.is_admin()
        OR EXISTS (
            SELECT 1 FROM public.chapters c
            JOIN public.books b ON b.id = c.book_id
            WHERE c.id = chapter_id AND c.chapter_no = 1 AND b.is_first_chapter_free = TRUE
        )
        OR EXISTS (
            SELECT 1 FROM public.chapter_progress cp
            WHERE cp.chapter_id = chapter_questions.chapter_id
              AND cp.user_id = auth.uid()
              AND cp.status IN ('Reading Completed', 'Exercises Submitted', 'Learning Verified')
        )
        OR EXISTS (
            SELECT 1 FROM public.study_circles sc
            JOIN public.chapters c ON c.book_id = sc.book_id
            WHERE c.id = chapter_questions.chapter_id AND sc.supervisor_id = auth.uid()
        )
    );

-- CRITICAL: Answer Key ONLY unlocked AFTER 'Exercises Submitted' (or for Supervisor/Admin)!
CREATE POLICY answer_keys_strict_lock ON public.chapter_answer_keys
    FOR SELECT TO authenticated
    USING (
        public.is_admin()
        OR EXISTS (
            SELECT 1 FROM public.chapter_questions cq
            JOIN public.chapter_progress cp ON cp.chapter_id = cq.chapter_id
            WHERE cq.id = chapter_answer_keys.question_id
              AND cp.user_id = auth.uid()
              AND cp.status IN ('Exercises Submitted', 'Learning Verified')
        )
        OR EXISTS (
            SELECT 1 FROM public.chapter_questions cq
            JOIN public.chapters c ON c.id = cq.chapter_id
            JOIN public.study_circles sc ON sc.book_id = c.book_id
            WHERE cq.id = chapter_answer_keys.question_id
              AND sc.supervisor_id = auth.uid()
        )
    );

-- 6. Highlights Privacy Control ('private', 'supervisor', 'group' - Section 3.2)
CREATE POLICY highlights_privacy_select ON public.reading_highlights
    FOR SELECT TO authenticated
    USING (
        user_id = auth.uid()
        OR (
            visibility = 'supervisor' AND EXISTS (
                SELECT 1 FROM public.study_circles sc
                JOIN public.study_circle_members scm ON scm.circle_id = sc.id
                WHERE sc.book_id = reading_highlights.book_id
                  AND scm.user_id = reading_highlights.user_id
                  AND sc.supervisor_id = auth.uid()
            )
        )
        OR (
            visibility = 'group' AND EXISTS (
                SELECT 1 FROM public.study_circle_members scm_owner
                JOIN public.study_circle_members scm_viewer ON scm_viewer.circle_id = scm_owner.circle_id
                JOIN public.study_circles sc ON sc.id = scm_owner.circle_id
                WHERE sc.book_id = reading_highlights.book_id
                  AND scm_owner.user_id = reading_highlights.user_id
                  AND scm_viewer.user_id = auth.uid()
                  AND scm_viewer.invite_status = 'joined'
            )
        )
    );
CREATE POLICY highlights_owner_write ON public.reading_highlights
    FOR ALL TO authenticated USING (user_id = auth.uid()) WITH CHECK (user_id = auth.uid());

-- 7. Physical Book Orders: strictly Admin, Supervisor/Teacher, SPARC Rep, or ordering user (Sec 3.5)
CREATE POLICY book_orders_restricted_select ON public.physical_book_orders
    FOR SELECT TO authenticated
    USING (
        user_id = auth.uid()
        OR EXISTS (
            SELECT 1 FROM public.profiles p
            WHERE p.id = auth.uid() AND p.role IN ('admin', 'supervisor', 'sparc_rep')
        )
    );
CREATE POLICY book_orders_insert_self ON public.physical_book_orders
    FOR INSERT TO authenticated WITH CHECK (user_id = auth.uid());
CREATE POLICY book_orders_staff_update ON public.physical_book_orders
    FOR UPDATE TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.profiles p
            WHERE p.id = auth.uid() AND p.role IN ('admin', 'sparc_rep')
        )
    );

-- 8. Pokes: sender & receiver see message; Supervisor CANNOT read poke messages (Sec 4.3)!
CREATE POLICY pokes_private_messages ON public.pokes
    FOR SELECT TO authenticated
    USING (sender_id = auth.uid() OR receiver_id = auth.uid());
CREATE POLICY pokes_receiver_quick_reply ON public.pokes
    FOR UPDATE TO authenticated
    USING (receiver_id = auth.uid())
    WITH CHECK (receiver_id = auth.uid());

-- 9. Chapter Progress & Writing Privacy: scores & writing hidden from peers (Sec 5.4)
CREATE POLICY progress_owner_or_supervisor ON public.chapter_progress
    FOR SELECT TO authenticated
    USING (
        user_id = auth.uid()
        OR public.is_circle_supervisor(circle_id)
    );
CREATE POLICY progress_owner_self_declare ON public.chapter_progress
    FOR INSERT TO authenticated WITH CHECK (user_id = auth.uid());
CREATE POLICY progress_update_owner_or_supervisor ON public.chapter_progress
    FOR UPDATE TO authenticated
    USING (user_id = auth.uid() OR public.is_circle_supervisor(circle_id));

CREATE POLICY submissions_owner_or_supervisor ON public.exercise_submissions
    FOR SELECT TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.chapter_progress cp
            WHERE cp.id = exercise_submissions.progress_id
              AND (cp.user_id = auth.uid() OR public.is_circle_supervisor(cp.circle_id))
        )
    );

CREATE POLICY writing_owner_or_supervisor ON public.writing_submissions
    FOR SELECT TO authenticated
    USING (
        user_id = auth.uid()
        OR EXISTS (
            SELECT 1 FROM public.chapter_progress cp
            WHERE cp.id = writing_submissions.progress_id
              AND public.is_circle_supervisor(cp.circle_id)
        )
    );
CREATE POLICY writing_owner_upsert ON public.writing_submissions
    FOR INSERT TO authenticated WITH CHECK (user_id = auth.uid());
CREATE POLICY writing_update_owner_or_supervisor ON public.writing_submissions
    FOR UPDATE TO authenticated
    USING (
        user_id = auth.uid()
        OR EXISTS (
            SELECT 1 FROM public.chapter_progress cp
            WHERE cp.id = writing_submissions.progress_id
              AND public.is_circle_supervisor(cp.circle_id)
        )
    );

-- 10. Circles, Polls, Debates, Sessions & Certificates
CREATE POLICY circles_select_all ON public.study_circles FOR SELECT TO authenticated USING (TRUE);
CREATE POLICY circles_create_auth ON public.study_circles FOR INSERT TO authenticated WITH CHECK (created_by = auth.uid());
CREATE POLICY circles_update_staff ON public.study_circles FOR UPDATE TO authenticated USING (created_by = auth.uid() OR public.is_circle_supervisor(id));

CREATE POLICY circle_members_select ON public.study_circle_members FOR SELECT TO authenticated USING (TRUE);
CREATE POLICY circle_members_join ON public.study_circle_members FOR INSERT TO authenticated WITH CHECK (user_id = auth.uid());

CREATE POLICY reading_logs_owner ON public.reading_logs FOR ALL TO authenticated USING (user_id = auth.uid()) WITH CHECK (user_id = auth.uid());
CREATE POLICY mute_settings_owner ON public.poke_mute_settings FOR ALL TO authenticated USING (user_id = auth.uid()) WITH CHECK (user_id = auth.uid());
CREATE POLICY sessions_select_auth ON public.online_sessions FOR SELECT TO authenticated USING (TRUE);
CREATE POLICY certificates_public_verify ON public.certificates FOR SELECT TO anon, authenticated USING (TRUE);
