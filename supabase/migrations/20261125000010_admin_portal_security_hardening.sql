-- ============================================================================
-- Migration 010: Admin Portal Security Hardening (v3.9 Web Portal)
-- ----------------------------------------------------------------------------
-- 1. profiles.base_role  = the REAL (Admin-granted) role; profiles.role may be a
--    temporary preview role for SPARC Admin testers only.
-- 2. Members can no longer self-escalate (role / base_role / wallet_balance are
--    protected from direct client UPDATEs; only SECURITY DEFINER RPCs/admins).
-- 3. switch_my_role_preview_rpc → only real Admins (base_role = admin).
-- 4. admin_manage_roles_and_orders_rpc → role changes by Admin only
--    (SPARC Rep keeps order/wallet processing).
-- 5. upsert_app_customization_rpc (brand + payment numbers) → Admin only.
-- 6. portal_whoami_rpc → web portal role gate helper.
-- ============================================================================

ALTER TABLE public.profiles
    ADD COLUMN IF NOT EXISTS base_role public.user_role;

UPDATE public.profiles SET base_role = role WHERE base_role IS NULL;

ALTER TABLE public.profiles
    ALTER COLUMN base_role SET DEFAULT 'member';

-- Real-admin helper (survives an Admin previewing a lower role)
CREATE OR REPLACE FUNCTION public.is_real_admin()
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT EXISTS (
        SELECT 1 FROM public.profiles
        WHERE id = auth.uid() AND (base_role = 'admin' OR role = 'admin')
    );
$$;

-- ----------------------------------------------------------------------------
-- Protect privileged profile columns from direct client updates
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.fn_protect_profile_privileged_columns()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    -- Direct PostgREST calls run as 'authenticated'/'anon'; SECURITY DEFINER RPCs
    -- and triggers run as the function owner and are allowed.
    IF current_user IN ('authenticated', 'anon') AND NOT public.is_real_admin() THEN
        IF NEW.role IS DISTINCT FROM OLD.role
           OR NEW.base_role IS DISTINCT FROM OLD.base_role
           OR NEW.wallet_balance IS DISTINCT FROM OLD.wallet_balance THEN
            RAISE EXCEPTION 'রোল বা ওয়ালেট ব্যালান্স শুধুমাত্র অ্যাডমিন পরিবর্তন করতে পারেন।';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_protect_profile_privileged_columns ON public.profiles;
CREATE TRIGGER trg_protect_profile_privileged_columns
    BEFORE UPDATE ON public.profiles
    FOR EACH ROW
    EXECUTE FUNCTION public.fn_protect_profile_privileged_columns();

-- New sign-ups: keep base_role in sync on insert
CREATE OR REPLACE FUNCTION public.fn_profile_base_role_on_insert()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF current_user IN ('authenticated', 'anon') THEN
        NEW.role := 'member';
        NEW.base_role := 'member';
        NEW.wallet_balance := 0;
    ELSE
        NEW.base_role := COALESCE(NEW.base_role, NEW.role, 'member');
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_profile_base_role_on_insert ON public.profiles;
CREATE TRIGGER trg_profile_base_role_on_insert
    BEFORE INSERT ON public.profiles
    FOR EACH ROW
    EXECUTE FUNCTION public.fn_profile_base_role_on_insert();

-- ----------------------------------------------------------------------------
-- Role preview: real Admins only
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
    v_base TEXT;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Authentication required';
    END IF;

    SELECT COALESCE(base_role, role)::text INTO v_base FROM public.profiles WHERE id = v_uid;
    IF v_base IS DISTINCT FROM 'admin' THEN
        RAISE EXCEPTION 'রোল প্রিভিউ শুধুমাত্র SPARC অ্যাডমিনদের জন্য। রোল পরিবর্তনের জন্য অ্যাডমিনের সাথে যোগাযোগ করুন।';
    END IF;

    IF v_clean_role NOT IN ('member', 'moderator', 'supervisor', 'sparc_rep', 'admin') THEN
        v_clean_role := 'member';
    END IF;

    EXECUTE 'UPDATE public.profiles SET role = $1::public.user_role, updated_at = NOW() WHERE id = $2'
    USING v_clean_role, v_uid;

    RETURN jsonb_build_object('role', v_clean_role, 'base_role', v_base);
END;
$$;

-- ----------------------------------------------------------------------------
-- Admin management RPC: role changes by Admin only
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
    v_is_admin BOOLEAN;
    v_is_rep BOOLEAN;
    v_clean_role TEXT;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Authentication required';
    END IF;

    v_is_admin := public.is_real_admin();
    SELECT EXISTS (
        SELECT 1 FROM public.profiles
        WHERE id = v_uid AND (role = 'sparc_rep' OR base_role = 'sparc_rep')
    ) INTO v_is_rep;

    IF NOT v_is_admin AND NOT v_is_rep THEN
        RAISE EXCEPTION 'শুধুমাত্র অ্যাডমিন (SPARC টিম) এই কার্যক্রম সম্পাদন করতে পারেন।';
    END IF;

    IF p_target_user_id IS NOT NULL AND p_new_role IS NOT NULL THEN
        IF NOT v_is_admin THEN
            RAISE EXCEPTION 'রোল পরিবর্তন শুধুমাত্র অ্যাডমিন করতে পারেন।';
        END IF;
        v_clean_role := LOWER(TRIM(p_new_role));
        IF v_clean_role IN ('member', 'moderator', 'supervisor', 'sparc_rep', 'admin') THEN
            EXECUTE 'UPDATE public.profiles SET role = $1::public.user_role, base_role = $1::public.user_role, updated_at = NOW() WHERE id = $2'
            USING v_clean_role, p_target_user_id;
        END IF;
    END IF;

    IF p_circle_id IS NOT NULL AND (p_mentor_id IS NOT NULL OR p_min_members IS NOT NULL) THEN
        IF NOT v_is_admin THEN
            RAISE EXCEPTION 'মেন্টর নিয়োগ শুধুমাত্র অ্যাডমিন করতে পারেন।';
        END IF;
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
        IF NOT v_is_admin THEN
            RAISE EXCEPTION 'ওয়ালেট অনুমোদন শুধুমাত্র অ্যাডমিন করতে পারেন।';
        END IF;
        UPDATE public.wallet_transactions
        SET
            status = LOWER(TRIM(p_wallet_tx_status))::wallet_tx_status,
            verified_by = v_uid,
            verified_at = NOW()
        WHERE id = p_wallet_tx_id
          AND status = 'pending';
    END IF;

    RETURN jsonb_build_object('updated', TRUE);
END;
$$;

-- ----------------------------------------------------------------------------
-- Brand / notice / payment numbers: Admin only (wrap existing implementation)
-- ----------------------------------------------------------------------------
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
        WHERE n.nspname = 'public' AND p.proname = 'upsert_app_customization_rpc'
    ) AND NOT EXISTS (
        SELECT 1 FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
        WHERE n.nspname = 'public' AND p.proname = 'upsert_app_customization_impl'
    ) THEN
        ALTER FUNCTION public.upsert_app_customization_rpc(
            TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, BOOLEAN, TEXT, TEXT, TEXT, NUMERIC, NUMERIC, NUMERIC
        ) RENAME TO upsert_app_customization_impl;
    END IF;
END $$;

REVOKE ALL ON FUNCTION public.upsert_app_customization_impl(
    TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, BOOLEAN, TEXT, TEXT, TEXT, NUMERIC, NUMERIC, NUMERIC
) FROM PUBLIC, anon, authenticated;

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
BEGIN
    IF auth.uid() IS NULL THEN
        RAISE EXCEPTION 'Authentication required';
    END IF;
    IF NOT public.is_real_admin() THEN
        RAISE EXCEPTION 'ব্র্যান্ড, নোটিশ ও পেমেন্ট নম্বর শুধুমাত্র অ্যাডমিন পরিবর্তন করতে পারেন।';
    END IF;
    RETURN public.upsert_app_customization_impl(
        p_app_title, p_app_tagline_bn, p_app_tagline_en, p_splash_quote_bn, p_splash_quote_en,
        p_announcement_bn, p_announcement_en, p_show_announcement, p_bkash_number, p_nagad_number,
        p_rocket_number, p_supervision_fee_bdt, p_borrow_deposit_bdt, p_min_topup_bdt
    );
END;
$$;

GRANT EXECUTE ON FUNCTION public.upsert_app_customization_rpc(
    TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, BOOLEAN, TEXT, TEXT, TEXT, NUMERIC, NUMERIC, NUMERIC
) TO authenticated;

-- ----------------------------------------------------------------------------
-- Web portal helper: who am I + portal access level
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.portal_whoami_rpc()
RETURNS JSONB
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_p RECORD;
    v_base TEXT;
    v_access TEXT;
BEGIN
    IF auth.uid() IS NULL THEN
        RAISE EXCEPTION 'Authentication required';
    END IF;
    SELECT id, name, email, department, role::text AS role, COALESCE(base_role, role)::text AS base_role
    INTO v_p FROM public.profiles WHERE id = auth.uid();

    IF v_p.id IS NULL THEN
        RETURN jsonb_build_object('access', 'none');
    END IF;

    v_base := v_p.base_role;
    v_access := CASE
        WHEN v_base = 'admin' OR v_p.role = 'admin' THEN 'admin'
        WHEN v_base = 'sparc_rep' OR v_p.role = 'sparc_rep' THEN 'sparc_rep'
        WHEN v_base = 'supervisor' OR v_p.role = 'supervisor'
             OR EXISTS (SELECT 1 FROM public.study_circles WHERE supervisor_id = v_p.id) THEN 'supervisor'
        ELSE 'none'
    END;

    RETURN jsonb_build_object(
        'id', v_p.id,
        'name', v_p.name,
        'email', v_p.email,
        'department', v_p.department,
        'role', v_p.role,
        'base_role', v_base,
        'access', v_access
    );
END;
$$;

GRANT EXECUTE ON FUNCTION public.portal_whoami_rpc() TO authenticated;
GRANT EXECUTE ON FUNCTION public.is_real_admin() TO authenticated;
