-- ============================================================================
-- Migration 014: v4.0 Complete Suite
--   • Password Reset Requests queue (Pack 1)
--   • QR-Verifiable Certificates & Public Verification RPC (Pack 3)
--   • Scholarship / Fellowship Coupon Codes & Mentor Honorarium Split (Pack 4)
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. Password Reset Requests (Pack 1 — Zero-SMTP-Block Recovery Queue)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.password_reset_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email TEXT NOT NULL,
    user_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE,
    contact_note TEXT NOT NULL DEFAULT '',
    status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'resolved', 'dismissed')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at TIMESTAMPTZ
);

ALTER TABLE public.password_reset_requests ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS reset_req_admin_select ON public.password_reset_requests;
CREATE POLICY reset_req_admin_select ON public.password_reset_requests
    FOR SELECT TO authenticated
    USING (public.is_real_admin());

DROP POLICY IF EXISTS reset_req_admin_update ON public.password_reset_requests;
CREATE POLICY reset_req_admin_update ON public.password_reset_requests
    FOR UPDATE TO authenticated
    USING (public.is_real_admin());

CREATE OR REPLACE FUNCTION public.request_password_reset_rpc(
    p_email TEXT,
    p_contact_note TEXT DEFAULT ''
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_clean_email TEXT := LOWER(TRIM(COALESCE(p_email, '')));
    v_uid UUID;
BEGIN
    IF v_clean_email = '' OR position('@' IN v_clean_email) = 0 THEN
        RAISE EXCEPTION 'সঠিক ইমেইল ঠিকানা লিখুন।';
    END IF;

    SELECT p.id INTO v_uid
    FROM public.profiles p
    LEFT JOIN auth.users u ON u.id = p.id
    WHERE LOWER(TRIM(COALESCE(p.email, u.email, ''))) = v_clean_email
    LIMIT 1;

    -- Avoid duplicate pending rows for the same email
    IF NOT EXISTS (
        SELECT 1 FROM public.password_reset_requests
        WHERE LOWER(email) = v_clean_email AND status = 'pending'
    ) THEN
        INSERT INTO public.password_reset_requests (email, user_id, contact_note, status)
        VALUES (v_clean_email, v_uid, LEFT(TRIM(COALESCE(p_contact_note, '')), 240), 'pending');
    END IF;

    RETURN jsonb_build_object('submitted', TRUE);
END;
$$;

GRANT EXECUTE ON FUNCTION public.request_password_reset_rpc(TEXT, TEXT) TO anon, authenticated;

-- ----------------------------------------------------------------------------
-- 2. Verifiable Certificates & Public Lookup RPC (Pack 3)
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.issue_or_get_certificate_rpc(
    p_circle_id UUID DEFAULT NULL,
    p_book_id UUID DEFAULT NULL,
    p_title TEXT DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
    v_existing RECORD;
    v_verified_count INTEGER := 0;
    v_writing_count INTEGER := 0;
    v_code TEXT;
    v_cert_title TEXT;
    v_resolved_book UUID := p_book_id;
    v_resolved_circle UUID := p_circle_id;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Authentication required';
    END IF;

    SELECT * INTO v_existing
    FROM public.certificates
    WHERE user_id = v_uid
      AND (p_circle_id IS NULL OR circle_id = p_circle_id)
    ORDER BY issued_at DESC
    LIMIT 1;

    IF v_existing.id IS NOT NULL THEN
        RETURN jsonb_build_object(
            'certificate_code', v_existing.certificate_code,
            'title', v_existing.title,
            'issued_at', v_existing.issued_at
        );
    END IF;

    SELECT COUNT(*) INTO v_verified_count
    FROM public.chapter_progress
    WHERE user_id = v_uid AND status = 'Learning Verified';

    SELECT COUNT(*) INTO v_writing_count
    FROM public.writing_submissions
    WHERE user_id = v_uid AND supervisor_approved = TRUE;

    IF v_verified_count = 0 AND v_writing_count = 0 AND NOT public.is_real_admin() THEN
        RAISE EXCEPTION 'সার্টিফিকেট ইস্যু করতে অন্তত ১টি অধ্যায় Learning Verified বা মেন্টর-অনুমোদিত রাইটিং সম্পন্ন করুন।';
    END IF;

    IF v_resolved_circle IS NULL THEN
        SELECT circle_id, c.book_id INTO v_resolved_circle, v_resolved_book
        FROM public.chapter_progress cp
        JOIN public.study_circles c ON c.id = cp.circle_id
        WHERE cp.user_id = v_uid
        ORDER BY cp.updated_at DESC
        LIMIT 1;
    END IF;

    v_code := 'SPARC-' || UPPER(SUBSTRING(REPLACE(gen_random_uuid()::TEXT, '-', '') FROM 1 FOR 8));
    v_cert_title := COALESCE(NULLIF(TRIM(p_title), ''), 'SPARC Study Circle Scholarly Completion Certificate');

    INSERT INTO public.certificates (
        certificate_code, user_id, circle_id, book_id, certificate_type, title
    )
    VALUES (
        v_code, v_uid, v_resolved_circle, v_resolved_book, 'study_circle_completion', v_cert_title
    );

    RETURN jsonb_build_object(
        'certificate_code', v_code,
        'title', v_cert_title,
        'issued_at', NOW()
    );
END;
$$;

GRANT EXECUTE ON FUNCTION public.issue_or_get_certificate_rpc(UUID, UUID, TEXT) TO authenticated;

CREATE OR REPLACE FUNCTION public.verify_certificate_public_rpc(
    p_code TEXT
)
RETURNS JSONB
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_clean TEXT := UPPER(TRIM(COALESCE(p_code, '')));
    v_rec RECORD;
BEGIN
    IF v_clean = '' THEN
        RETURN jsonb_build_object('valid', FALSE);
    END IF;

    SELECT
        cert.certificate_code,
        cert.title AS cert_title,
        cert.certificate_type,
        cert.issued_at,
        prof.name AS researcher_name,
        prof.department,
        COALESCE(prof.institution, '') AS institution,
        COALESCE(bk.title, '') AS book_title,
        COALESCE(sc.name, '') AS circle_name
    INTO v_rec
    FROM public.certificates cert
    JOIN public.profiles prof ON prof.id = cert.user_id
    LEFT JOIN public.books bk ON bk.id = cert.book_id
    LEFT JOIN public.study_circles sc ON sc.id = cert.circle_id
    WHERE UPPER(cert.certificate_code) = v_clean
    LIMIT 1;

    IF v_rec.certificate_code IS NULL THEN
        RETURN jsonb_build_object('valid', FALSE);
    END IF;

    RETURN jsonb_build_object(
        'valid', TRUE,
        'certificate_code', v_rec.certificate_code,
        'title', v_rec.cert_title,
        'certificate_type', v_rec.certificate_type,
        'issued_at', v_rec.issued_at,
        'researcher_name', v_rec.researcher_name,
        'department', v_rec.department,
        'institution', v_rec.institution,
        'book_title', v_rec.book_title,
        'circle_name', v_rec.circle_name
    );
END;
$$;

GRANT EXECUTE ON FUNCTION public.verify_certificate_public_rpc(TEXT) TO anon, authenticated;

-- ----------------------------------------------------------------------------
-- 3. Scholarship / Voucher Coupons & Mentor Honorarium Split (Pack 4)
-- ----------------------------------------------------------------------------
ALTER TABLE public.app_customization
    ADD COLUMN IF NOT EXISTS mentor_honorarium_pct INTEGER NOT NULL DEFAULT 70
    CHECK (mentor_honorarium_pct BETWEEN 0 AND 100);

DROP POLICY IF EXISTS app_customization_admin_update ON public.app_customization;
CREATE POLICY app_customization_admin_update ON public.app_customization
    FOR UPDATE TO authenticated
    USING (public.is_real_admin())
    WITH CHECK (public.is_real_admin());

CREATE TABLE IF NOT EXISTS public.scholarship_coupons (
    code TEXT PRIMARY KEY,
    credit_amount_bdt NUMERIC(10,2) NOT NULL CHECK (credit_amount_bdt > 0),
    max_uses INTEGER NOT NULL DEFAULT 25 CHECK (max_uses >= 1),
    used_count INTEGER NOT NULL DEFAULT 0 CHECK (used_count >= 0),
    description TEXT NOT NULL DEFAULT 'SPARC স্কলারশিপ / ওয়ার্কশপ ভাউচার',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.coupon_redemptions (
    coupon_code TEXT NOT NULL REFERENCES public.scholarship_coupons(code) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    amount_bdt NUMERIC(10,2) NOT NULL,
    redeemed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (coupon_code, user_id)
);

ALTER TABLE public.scholarship_coupons ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.coupon_redemptions ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS coupons_admin_select ON public.scholarship_coupons;
CREATE POLICY coupons_admin_select ON public.scholarship_coupons
    FOR SELECT TO authenticated
    USING (public.is_real_admin());

DROP POLICY IF EXISTS redemptions_owner_or_admin ON public.coupon_redemptions;
CREATE POLICY redemptions_owner_or_admin ON public.coupon_redemptions
    FOR SELECT TO authenticated
    USING (user_id = auth.uid() OR public.is_real_admin());

CREATE OR REPLACE FUNCTION public.admin_upsert_scholarship_coupon_rpc(
    p_code TEXT,
    p_credit_amount_bdt NUMERIC,
    p_max_uses INTEGER DEFAULT 25,
    p_description TEXT DEFAULT 'SPARC স্কলারশিপ ভাউচার',
    p_is_active BOOLEAN DEFAULT TRUE
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_clean_code TEXT := UPPER(REGEXP_REPLACE(TRIM(COALESCE(p_code, '')), '[^A-Za-z0-9_-]', '', 'g'));
BEGIN
    IF NOT public.is_real_admin() THEN
        RAISE EXCEPTION 'শুধুমাত্র অ্যাডমিন স্কলারশিপ কুপন তৈরি বা পরিবর্তন করতে পারেন।';
    END IF;
    IF char_length(v_clean_code) < 3 THEN
        RAISE EXCEPTION 'কুপন কোড কমপক্ষে ৩ অক্ষরের হতে হবে।';
    END IF;
    IF COALESCE(p_credit_amount_bdt, 0) <= 0 THEN
        RAISE EXCEPTION 'ক্রেডিট পরিমাণ ০-এর বেশি হতে হবে।';
    END IF;

    INSERT INTO public.scholarship_coupons (
        code, credit_amount_bdt, max_uses, description, is_active, created_by
    )
    VALUES (
        v_clean_code,
        p_credit_amount_bdt,
        GREATEST(COALESCE(p_max_uses, 25), 1),
        COALESCE(NULLIF(TRIM(p_description), ''), 'SPARC স্কলারশিপ ভাউচার'),
        COALESCE(p_is_active, TRUE),
        auth.uid()
    )
    ON CONFLICT (code) DO UPDATE SET
        credit_amount_bdt = EXCLUDED.credit_amount_bdt,
        max_uses = EXCLUDED.max_uses,
        description = EXCLUDED.description,
        is_active = EXCLUDED.is_active;

    RETURN jsonb_build_object('code', v_clean_code, 'saved', TRUE);
END;
$$;

GRANT EXECUTE ON FUNCTION public.admin_upsert_scholarship_coupon_rpc(TEXT, NUMERIC, INTEGER, TEXT, BOOLEAN) TO authenticated;

CREATE OR REPLACE FUNCTION public.redeem_scholarship_coupon_rpc(
    p_code TEXT
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
    v_clean_code TEXT := UPPER(TRIM(COALESCE(p_code, '')));
    v_coupon RECORD;
    v_new_balance NUMERIC;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Authentication required';
    END IF;
    IF v_clean_code = '' THEN
        RAISE EXCEPTION 'কুপন বা স্কলারশিপ কোড লিখুন।';
    END IF;

    SELECT * INTO v_coupon
    FROM public.scholarship_coupons
    WHERE code = v_clean_code
    FOR UPDATE;

    IF v_coupon.code IS NULL OR NOT v_coupon.is_active THEN
        RAISE EXCEPTION 'এই কুপন কোডটি বৈধ নয় বা বর্তমানে বন্ধ আছে।';
    END IF;

    IF v_coupon.used_count >= v_coupon.max_uses THEN
        RAISE EXCEPTION 'এই কুপন কোডটির ব্যবহারের সীমা শেষ হয়ে গেছে।';
    END IF;

    IF EXISTS (
        SELECT 1 FROM public.coupon_redemptions
        WHERE coupon_code = v_clean_code AND user_id = v_uid
    ) THEN
        RAISE EXCEPTION 'আপনি ইতিমধ্যে এই স্কলারশিপ কোডটি ব্যবহার করেছেন।';
    END IF;

    INSERT INTO public.coupon_redemptions (coupon_code, user_id, amount_bdt)
    VALUES (v_clean_code, v_uid, v_coupon.credit_amount_bdt);

    UPDATE public.scholarship_coupons
    SET used_count = used_count + 1
    WHERE code = v_clean_code;

    -- Credit researcher wallet via verified referral_bonus ledger entry
    INSERT INTO public.wallet_transactions (
        user_id, tx_type, amount_bdt, status, gateway_ref, notes, verified_at
    )
    VALUES (
        v_uid,
        'referral_bonus',
        v_coupon.credit_amount_bdt,
        'verified',
        'COUPON-' || v_clean_code || '-' || SUBSTRING(REPLACE(v_uid::TEXT, '-', '') FROM 1 FOR 4),
        'স্কলারশিপ ভাউচার (' || v_clean_code || '): ' || v_coupon.description,
        NOW()
    );

    SELECT wallet_balance INTO v_new_balance FROM public.profiles WHERE id = v_uid;

    RETURN jsonb_build_object(
        'redeemed', TRUE,
        'code', v_clean_code,
        'credited_bdt', v_coupon.credit_amount_bdt,
        'bonus_bdt', v_coupon.credit_amount_bdt,
        'wallet_balance', v_new_balance,
        'new_balance_bdt', v_new_balance
    );
END;
$$;

GRANT EXECUTE ON FUNCTION public.redeem_scholarship_coupon_rpc(TEXT) TO authenticated;
