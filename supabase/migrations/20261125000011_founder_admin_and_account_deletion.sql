-- ============================================================================
-- Migration 011: Founder Admin Bootstrap & Self-Service Account Deletion RPC
-- ----------------------------------------------------------------------------
-- 1. Promotes founder account (abamunim@gmail.com) to 'admin' (both role &
--    base_role) immediately if already registered, and automatically on insert
--    if they register later.
-- 2. Adds delete_my_account_rpc() for Google Play Store User Data & Account
--    Deletion policy compliance.
-- ============================================================================

-- 1. Backfill profile email from auth.users if missing, then promote founder
UPDATE public.profiles p
SET email = COALESCE(p.email, u.email)
FROM auth.users u
WHERE p.id = u.id AND p.email IS NULL AND u.email IS NOT NULL;

UPDATE public.profiles p
SET role = 'admin',
    base_role = 'admin',
    updated_at = NOW()
WHERE LOWER(TRIM(COALESCE(p.email, ''))) = 'abamunim@gmail.com'
   OR EXISTS (
       SELECT 1 FROM auth.users u
       WHERE u.id = p.id AND LOWER(TRIM(COALESCE(u.email, ''))) = 'abamunim@gmail.com'
   );

-- Ensure future sign-up with founder email automatically gets admin + base_role=admin
CREATE OR REPLACE FUNCTION public.fn_profile_base_role_on_insert()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_auth_email TEXT;
    v_eff_email TEXT;
BEGIN
    SELECT email INTO v_auth_email FROM auth.users WHERE id = NEW.id;
    v_eff_email := LOWER(TRIM(COALESCE(NEW.email, v_auth_email, '')));

    IF v_eff_email = 'abamunim@gmail.com' THEN
        NEW.email := COALESCE(NEW.email, v_auth_email);
        NEW.role := 'admin';
        NEW.base_role := 'admin';
        RETURN NEW;
    END IF;

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

-- ----------------------------------------------------------------------------
-- 2. Self-Service Account & Personal Data Deletion RPC (Google Play Policy)
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.delete_my_account_rpc()
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
    v_fallback_owner UUID;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Authentication required';
    END IF;

    -- Find another user (prefer admin) to inherit created_by on shared circles/groups
    SELECT id INTO v_fallback_owner
    FROM public.profiles
    WHERE id <> v_uid
    ORDER BY (CASE WHEN base_role = 'admin' OR role = 'admin' THEN 0 ELSE 1 END), created_at ASC
    LIMIT 1;

    IF v_fallback_owner IS NOT NULL THEN
        UPDATE public.study_circles
        SET created_by = v_fallback_owner
        WHERE created_by = v_uid;

        UPDATE public.research_groups
        SET created_by = v_fallback_owner
        WHERE created_by = v_uid;
    ELSE
        DELETE FROM public.study_circles WHERE created_by = v_uid;
        DELETE FROM public.research_groups WHERE created_by = v_uid;
    END IF;

    -- Delete storage objects owned by this user
    DELETE FROM storage.objects WHERE owner = v_uid;

    -- Deleting auth.users cascades to public.profiles and all personal rows
    DELETE FROM auth.users WHERE id = v_uid;

    RETURN jsonb_build_object('deleted', TRUE);
END;
$$;

GRANT EXECUTE ON FUNCTION public.delete_my_account_rpc() TO authenticated;
