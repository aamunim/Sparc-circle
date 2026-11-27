-- ============================================================================
-- Migration 016: Admin-Only Encrypted/RLS-Locked API Vault & Public Jitsi Sync
-- ----------------------------------------------------------------------------
-- 1. Moves all third-party API keys (Gemini AI, bKash PGW, SSLCommerz, Jitsi,
--    Resend Email) out of the user-facing Android mobile app into a locked
--    server table `public.admin_api_vault` accessible ONLY via Admin RPCs and
--    Service-Role Edge Functions.
-- 2. Adds non-sensitive `custom_jitsi_server_url` and `ai_coach_enabled` to
--    `public.app_customization` so mobile clients automatically pick up the
--    configured Jitsi server and AI Coach toggle without reading any secrets.
-- ============================================================================

-- 1. Public non-sensitive sync fields on app_customization
ALTER TABLE public.app_customization
    ADD COLUMN IF NOT EXISTS custom_jitsi_server_url TEXT NOT NULL DEFAULT 'https://meet.jit.si',
    ADD COLUMN IF NOT EXISTS ai_coach_enabled BOOLEAN NOT NULL DEFAULT TRUE;

-- 2. Locked Admin API Vault table
CREATE TABLE IF NOT EXISTS public.admin_api_vault (
    id TEXT PRIMARY KEY DEFAULT 'global' CHECK (id = 'global'),
    gemini_api_key TEXT NOT NULL DEFAULT '',
    gemini_model TEXT NOT NULL DEFAULT 'gemini-2.5-flash',
    ai_coach_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    gateway_environment TEXT NOT NULL DEFAULT 'sandbox' CHECK (gateway_environment IN ('sandbox', 'live')),
    bkash_app_key TEXT NOT NULL DEFAULT '',
    bkash_app_secret TEXT NOT NULL DEFAULT '',
    sslcommerz_store_id TEXT NOT NULL DEFAULT '',
    sslcommerz_store_passwd TEXT NOT NULL DEFAULT '',
    custom_jitsi_server_url TEXT NOT NULL DEFAULT 'https://meet.jit.si',
    resend_api_key TEXT NOT NULL DEFAULT '',
    updated_by UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO public.admin_api_vault (id)
VALUES ('global')
ON CONFLICT (id) DO NOTHING;

-- Strict lockdown: zero direct SELECT/INSERT/UPDATE/DELETE for anon or authenticated!
ALTER TABLE public.admin_api_vault ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON TABLE public.admin_api_vault FROM anon, authenticated;

-- 3. Strict Admin-only Read RPC
CREATE OR REPLACE FUNCTION public.get_admin_api_vault_rpc()
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
    v_is_strict_admin BOOLEAN := FALSE;
    v_row public.admin_api_vault%ROWTYPE;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Authentication required';
    END IF;

    SELECT EXISTS (
        SELECT 1
        FROM public.profiles p
        WHERE p.id = v_uid
          AND (p.role = 'admin' OR p.base_role = 'admin')
    ) INTO v_is_strict_admin;

    IF NOT v_is_strict_admin THEN
        RAISE EXCEPTION 'শুধুমাত্র মূল অ্যাডমিন (Admin) API ভল্ট দেখতে পারেন।';
    END IF;

    SELECT * INTO v_row
    FROM public.admin_api_vault
    WHERE id = 'global';

    RETURN to_jsonb(v_row);
END;
$$;

-- 4. Strict Admin-only Upsert RPC (also syncs safe Jitsi URL & AI toggle to app_customization)
CREATE OR REPLACE FUNCTION public.upsert_admin_api_vault_rpc(
    p_gemini_api_key TEXT DEFAULT '',
    p_gemini_model TEXT DEFAULT 'gemini-2.5-flash',
    p_ai_coach_enabled BOOLEAN DEFAULT TRUE,
    p_gateway_environment TEXT DEFAULT 'sandbox',
    p_bkash_app_key TEXT DEFAULT '',
    p_bkash_app_secret TEXT DEFAULT '',
    p_sslcommerz_store_id TEXT DEFAULT '',
    p_sslcommerz_store_passwd TEXT DEFAULT '',
    p_custom_jitsi_server_url TEXT DEFAULT 'https://meet.jit.si',
    p_resend_api_key TEXT DEFAULT ''
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
    v_is_strict_admin BOOLEAN := FALSE;
    v_clean_jitsi TEXT := COALESCE(NULLIF(TRIM(p_custom_jitsi_server_url), ''), 'https://meet.jit.si');
    v_clean_env TEXT := CASE WHEN LOWER(COALESCE(p_gateway_environment, 'sandbox')) = 'live' THEN 'live' ELSE 'sandbox' END;
    v_clean_model TEXT := COALESCE(NULLIF(TRIM(p_gemini_model), ''), 'gemini-2.5-flash');
    v_row public.admin_api_vault%ROWTYPE;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Authentication required';
    END IF;

    SELECT EXISTS (
        SELECT 1
        FROM public.profiles p
        WHERE p.id = v_uid
          AND (p.role = 'admin' OR p.base_role = 'admin')
    ) INTO v_is_strict_admin;

    IF NOT v_is_strict_admin THEN
        RAISE EXCEPTION 'শুধুমাত্র মূল অ্যাডমিন (Admin) API কী পরিবর্তন করতে পারেন।';
    END IF;

    INSERT INTO public.admin_api_vault (
        id,
        gemini_api_key,
        gemini_model,
        ai_coach_enabled,
        gateway_environment,
        bkash_app_key,
        bkash_app_secret,
        sslcommerz_store_id,
        sslcommerz_store_passwd,
        custom_jitsi_server_url,
        resend_api_key,
        updated_by,
        updated_at
    )
    VALUES (
        'global',
        COALESCE(TRIM(p_gemini_api_key), ''),
        v_clean_model,
        COALESCE(p_ai_coach_enabled, TRUE),
        v_clean_env,
        COALESCE(TRIM(p_bkash_app_key), ''),
        COALESCE(TRIM(p_bkash_app_secret), ''),
        COALESCE(TRIM(p_sslcommerz_store_id), ''),
        COALESCE(TRIM(p_sslcommerz_store_passwd), ''),
        v_clean_jitsi,
        COALESCE(TRIM(p_resend_api_key), ''),
        v_uid,
        NOW()
    )
    ON CONFLICT (id) DO UPDATE SET
        gemini_api_key = EXCLUDED.gemini_api_key,
        gemini_model = EXCLUDED.gemini_model,
        ai_coach_enabled = EXCLUDED.ai_coach_enabled,
        gateway_environment = EXCLUDED.gateway_environment,
        bkash_app_key = EXCLUDED.bkash_app_key,
        bkash_app_secret = EXCLUDED.bkash_app_secret,
        sslcommerz_store_id = EXCLUDED.sslcommerz_store_id,
        sslcommerz_store_passwd = EXCLUDED.sslcommerz_store_passwd,
        custom_jitsi_server_url = EXCLUDED.custom_jitsi_server_url,
        resend_api_key = EXCLUDED.resend_api_key,
        updated_by = v_uid,
        updated_at = NOW()
    RETURNING * INTO v_row;

    -- Sync non-sensitive public fields to app_customization for mobile clients
    UPDATE public.app_customization
    SET
        custom_jitsi_server_url = v_clean_jitsi,
        ai_coach_enabled = COALESCE(p_ai_coach_enabled, TRUE),
        updated_by = v_uid,
        updated_at = NOW()
    WHERE id = 'global';

    RETURN to_jsonb(v_row);
END;
$$;

GRANT EXECUTE ON FUNCTION public.get_admin_api_vault_rpc() TO authenticated;
GRANT EXECUTE ON FUNCTION public.upsert_admin_api_vault_rpc(TEXT, TEXT, BOOLEAN, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT) TO authenticated;
