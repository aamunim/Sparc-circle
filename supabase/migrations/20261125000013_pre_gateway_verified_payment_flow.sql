-- ============================================================================
-- Migration 013: Pre-Gateway Verified Payment Workflow & Security Hardening
-- ----------------------------------------------------------------------------
-- 1. Adds online_gateway_enabled (DEFAULT FALSE) and payment_instructions_bn/en
--    to public.app_customization; sets friendly 200 BDT default min top-up.
-- 2. Closes the sandbox auto-credit loophole in topup_wallet_rpc(): client RPCs
--    NEVER insert status='verified' directly; stores sender phone & channel
--    (bKash / Nagad / Rocket) for 1-click Admin verification.
-- 3. Extends place_book_order_rpc() to support Wallet, Cash on Delivery (COD),
--    and Direct TrxID on physical book orders.
-- ============================================================================

-- 1. Extend app_customization with gateway switch & payment instructions
ALTER TABLE public.app_customization
    ADD COLUMN IF NOT EXISTS online_gateway_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS payment_instructions_bn TEXT NOT NULL DEFAULT 'উপরের নম্বরে সেন্ড মানি / পেমেন্ট করে নিচের ঘরে প্রেরকের নম্বর ও TrxID জমা দিন।',
    ADD COLUMN IF NOT EXISTS payment_instructions_en TEXT NOT NULL DEFAULT 'Send money to the number above, then submit your sender phone number and TrxID below.';

UPDATE public.app_customization
SET min_topup_bdt = 200.00
WHERE id = 'global' AND min_topup_bdt = 500.00;

-- Replace upsert_app_customization_rpc (drop old 14-arg wrapper & impl first to avoid overload ambiguity)
DROP FUNCTION IF EXISTS public.upsert_app_customization_rpc(
    TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, BOOLEAN, TEXT, TEXT, TEXT, NUMERIC, NUMERIC, NUMERIC
);
DROP FUNCTION IF EXISTS public.upsert_app_customization_impl(
    TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, BOOLEAN, TEXT, TEXT, TEXT, NUMERIC, NUMERIC, NUMERIC
);

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
    p_min_topup_bdt NUMERIC,
    p_online_gateway_enabled BOOLEAN DEFAULT NULL,
    p_payment_instructions_bn TEXT DEFAULT NULL,
    p_payment_instructions_en TEXT DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
    v_existing RECORD;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Authentication required';
    END IF;
    IF NOT public.is_real_admin() THEN
        RAISE EXCEPTION 'ব্র্যান্ড, নোটিশ ও পেমেন্ট সেটিংস শুধুমাত্র অ্যাডমিন পরিবর্তন করতে পারেন।';
    END IF;

    SELECT online_gateway_enabled, payment_instructions_bn, payment_instructions_en
    INTO v_existing
    FROM public.app_customization
    WHERE id = 'global';

    INSERT INTO public.app_customization (
        id, app_title, app_tagline_bn, app_tagline_en,
        splash_quote_bn, splash_quote_en,
        announcement_banner_bn, announcement_banner_en, show_announcement_banner,
        bkash_merchant_number, nagad_merchant_number, rocket_merchant_number,
        default_supervision_fee_bdt, borrow_deposit_bdt, min_topup_bdt,
        online_gateway_enabled, payment_instructions_bn, payment_instructions_en,
        updated_by, updated_at
    )
    VALUES (
        'global',
        COALESCE(NULLIF(TRIM(p_app_title), ''), 'SPARC Study Circle'),
        COALESCE(NULLIF(TRIM(p_app_tagline_bn), ''), 'বই পড়তে পড়তে লেখা শেখা'),
        COALESCE(NULLIF(TRIM(p_app_tagline_en), ''), 'Learning Academic Writing While Reading'),
        COALESCE(NULLIF(TRIM(p_splash_quote_bn), ''), 'যৌথ পাঠ, গভীর চিন্তা ও একাডেমিক গবেষণার সমন্বিত প্ল্যাটফর্ম'),
        COALESCE(NULLIF(TRIM(p_splash_quote_en), ''), 'Collaborative reading, critical inquiry & scholarly writing'),
        COALESCE(TRIM(p_announcement_bn), ''),
        COALESCE(TRIM(p_announcement_en), ''),
        COALESCE(p_show_announcement, TRUE),
        COALESCE(NULLIF(TRIM(p_bkash_number), ''), '01700-000000'),
        COALESCE(NULLIF(TRIM(p_nagad_number), ''), '01800-000000'),
        COALESCE(NULLIF(TRIM(p_rocket_number), ''), '01900-000000'),
        GREATEST(COALESCE(p_supervision_fee_bdt, 300), 0),
        GREATEST(COALESCE(p_borrow_deposit_bdt, 150), 0),
        GREATEST(COALESCE(p_min_topup_bdt, 200), 50),
        COALESCE(p_online_gateway_enabled, v_existing.online_gateway_enabled, FALSE),
        COALESCE(NULLIF(TRIM(p_payment_instructions_bn), ''), v_existing.payment_instructions_bn, 'উপরের নম্বরে সেন্ড মানি / পেমেন্ট করে নিচের ঘরে প্রেরকের নম্বর ও TrxID জমা দিন।'),
        COALESCE(NULLIF(TRIM(p_payment_instructions_en), ''), v_existing.payment_instructions_en, 'Send money to the number above, then submit your sender phone number and TrxID below.'),
        v_uid,
        NOW()
    )
    ON CONFLICT (id) DO UPDATE SET
        app_title = EXCLUDED.app_title,
        app_tagline_bn = EXCLUDED.app_tagline_bn,
        app_tagline_en = EXCLUDED.app_tagline_en,
        splash_quote_bn = EXCLUDED.splash_quote_bn,
        splash_quote_en = EXCLUDED.splash_quote_en,
        announcement_banner_bn = EXCLUDED.announcement_banner_bn,
        announcement_banner_en = EXCLUDED.announcement_banner_en,
        show_announcement_banner = EXCLUDED.show_announcement_banner,
        bkash_merchant_number = EXCLUDED.bkash_merchant_number,
        nagad_merchant_number = EXCLUDED.nagad_merchant_number,
        rocket_merchant_number = EXCLUDED.rocket_merchant_number,
        default_supervision_fee_bdt = EXCLUDED.default_supervision_fee_bdt,
        borrow_deposit_bdt = EXCLUDED.borrow_deposit_bdt,
        min_topup_bdt = EXCLUDED.min_topup_bdt,
        online_gateway_enabled = EXCLUDED.online_gateway_enabled,
        payment_instructions_bn = EXCLUDED.payment_instructions_bn,
        payment_instructions_en = EXCLUDED.payment_instructions_en,
        updated_by = v_uid,
        updated_at = NOW();

    RETURN jsonb_build_object('saved', TRUE);
END;
$$;

GRANT EXECUTE ON FUNCTION public.upsert_app_customization_rpc(
    TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, BOOLEAN, TEXT, TEXT, TEXT, NUMERIC, NUMERIC, NUMERIC, BOOLEAN, TEXT, TEXT
) TO authenticated;

-- ----------------------------------------------------------------------------
-- 2. Secure Wallet Top-Up RPC (No Auto-Verified Sandbox Credits!)
-- ----------------------------------------------------------------------------
DROP FUNCTION IF EXISTS public.topup_wallet_rpc(TEXT, NUMERIC, TEXT);

CREATE OR REPLACE FUNCTION public.topup_wallet_rpc(
    p_method TEXT,
    p_amount_bdt NUMERIC,
    p_manual_trx_id TEXT DEFAULT NULL,
    p_payment_phone TEXT DEFAULT NULL,
    p_channel TEXT DEFAULT 'bKash'
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
    v_min_topup NUMERIC := 200;
    v_gateway_enabled BOOLEAN := FALSE;
    v_tx_type wallet_tx_type;
    v_ref TEXT;
    v_manual TEXT := NULL;
    v_clean_phone TEXT := NULLIF(TRIM(COALESCE(p_payment_phone, '')), '');
    v_clean_channel TEXT := COALESCE(NULLIF(TRIM(p_channel), ''), 'bKash');
    v_notes TEXT;
    v_tx_id UUID;
    v_new_balance NUMERIC;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Authentication required';
    END IF;

    SELECT GREATEST(COALESCE(min_topup_bdt, 200), 50), COALESCE(online_gateway_enabled, FALSE)
    INTO v_min_topup, v_gateway_enabled
    FROM public.app_customization
    WHERE id = 'global';

    IF p_amount_bdt IS NULL OR p_amount_bdt < v_min_topup THEN
        RAISE EXCEPTION 'ন্যূনতম ৳% টাকা টপ-আপ করতে হবে।', v_min_topup::INTEGER;
    END IF;

    IF p_method = 'manual_trx' THEN
        IF p_manual_trx_id IS NULL OR char_length(TRIM(p_manual_trx_id)) < 5 THEN
            RAISE EXCEPTION 'সঠিক Transaction ID (TrxID) লিখুন।';
        END IF;
        v_tx_type := 'topup_manual_trx';
        v_manual := UPPER(TRIM(p_manual_trx_id));
        v_ref := v_manual;
        v_notes := v_clean_channel || ' TrxID' ||
            CASE WHEN v_clean_phone IS NOT NULL THEN ' • প্রেরক: ' || v_clean_phone ELSE '' END;
    ELSE
        IF NOT v_gateway_enabled THEN
            RAISE EXCEPTION 'অনলাইন পেমেন্ট গেটওয়ে বর্তমানে বন্ধ আছে। অনুগ্রহ করে bKash/Nagad/Rocket নম্বরে পাঠিয়ে TrxID জমা দিন।';
        END IF;
        v_tx_type := CASE WHEN p_method = 'bkash_pgw' THEN 'topup_bkash_pgw'::wallet_tx_type ELSE 'topup_sslcommerz'::wallet_tx_type END;
        v_ref := 'PGW-' || UPPER(SUBSTRING(REPLACE(gen_random_uuid()::TEXT, '-', '') FROM 1 FOR 8));
        v_notes := 'Online gateway checkout initiated (' || p_method || ') — awaiting gateway webhook verification';
    END IF;

    INSERT INTO public.wallet_transactions (
        user_id, tx_type, amount_bdt, status, gateway_ref, manual_trx_id, payment_phone, notes
    )
    VALUES (
        v_uid, v_tx_type, p_amount_bdt, 'pending', v_ref, v_manual, v_clean_phone, v_notes
    )
    ON CONFLICT (manual_trx_id) DO NOTHING
    RETURNING id INTO v_tx_id;

    IF v_tx_id IS NULL THEN
        RAISE EXCEPTION 'এই Transaction ID (TrxID) আগেই জমা দেওয়া হয়েছে।';
    END IF;

    SELECT wallet_balance INTO v_new_balance FROM public.profiles WHERE id = v_uid;

    RETURN jsonb_build_object(
        'tx_id', v_tx_id,
        'status', 'pending',
        'reference', v_ref,
        'wallet_balance', v_new_balance
    );
END;
$$;

GRANT EXECUTE ON FUNCTION public.topup_wallet_rpc(TEXT, NUMERIC, TEXT, TEXT, TEXT) TO authenticated;

-- ----------------------------------------------------------------------------
-- 3. Physical Book Order RPC (Supports Wallet, Cash on Delivery & Direct TrxID)
-- ----------------------------------------------------------------------------
DROP FUNCTION IF EXISTS public.place_book_order_rpc(UUID, TEXT, TEXT, TEXT);

CREATE OR REPLACE FUNCTION public.place_book_order_rpc(
    p_book_id UUID,
    p_order_mode TEXT,
    p_address TEXT,
    p_phone TEXT,
    p_payment_option TEXT DEFAULT 'wallet',
    p_direct_trx_id TEXT DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
    v_mode TEXT := LOWER(TRIM(COALESCE(p_order_mode, 'purchase')));
    v_pay_opt TEXT := LOWER(TRIM(COALESCE(p_payment_option, 'wallet')));
    v_price NUMERIC := 0;
    v_borrow_dep NUMERIC := 150;
    v_paid_wallet BOOLEAN := FALSE;
    v_notes TEXT := '';
    v_order_id UUID;
    v_new_balance NUMERIC;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Authentication required';
    END IF;
    IF TRIM(COALESCE(p_address, '')) = '' OR TRIM(COALESCE(p_phone, '')) = '' THEN
        RAISE EXCEPTION 'ঠিকানা ও মোবাইল নম্বর প্রদান করুন।';
    END IF;
    IF v_mode NOT IN ('purchase', 'borrow') THEN
        v_mode := 'purchase';
    END IF;

    SELECT COALESCE(borrow_deposit_bdt, 150) INTO v_borrow_dep
    FROM public.app_customization WHERE id = 'global';

    IF v_mode = 'purchase' THEN
        SELECT COALESCE(physical_price_bdt, 0) INTO v_price FROM public.books WHERE id = p_book_id;
    ELSE
        v_price := v_borrow_dep;
    END IF;

    IF v_price <= 0 THEN
        v_paid_wallet := FALSE;
        v_notes := 'ফ্রি / জামানতমুক্ত সংগ্রহ';
    ELSIF v_pay_opt = 'cod' THEN
        v_paid_wallet := FALSE;
        v_notes := 'ক্যাশ অন ডেলিভারি (COD — বই হাতে পেয়ে ৳' || v_price::INTEGER || ' পরিশোধ)';
    ELSIF v_pay_opt = 'direct_trx' THEN
        IF p_direct_trx_id IS NULL OR char_length(TRIM(p_direct_trx_id)) < 5 THEN
            RAISE EXCEPTION 'সরাসরি পেমেন্টের জন্য সঠিক TrxID লিখুন।';
        END IF;
        v_paid_wallet := FALSE;
        v_notes := 'Direct TrxID: ' || UPPER(TRIM(p_direct_trx_id)) || ' (৳' || v_price::INTEGER || ' • অ্যাডমিন যাচাই করবেন)';
    ELSE
        -- Pay from verified wallet balance
        INSERT INTO public.wallet_transactions (
            user_id, tx_type, amount_bdt, status, gateway_ref, notes, verified_at
        )
        VALUES (
            v_uid, 'physical_book_order', -v_price, 'verified',
            'BOOK-' || UPPER(SUBSTRING(REPLACE(gen_random_uuid()::TEXT, '-', '') FROM 1 FOR 6)),
            CASE WHEN v_mode = 'purchase' THEN 'হার্ডকপি বই ক্রয়' ELSE 'হার্ডকপি বই ধার জামানত' END,
            NOW()
        );
        v_paid_wallet := TRUE;
        v_notes := 'ওয়ালেট ব্যালান্স থেকে পরিশোধিত (৳' || v_price::INTEGER || ')';
    END IF;

    INSERT INTO public.physical_book_orders (
        user_id, book_id, order_mode, delivery_address, contact_phone,
        amount_bdt, paid_via_wallet, admin_notes, status
    )
    VALUES (
        v_uid, p_book_id, v_mode, TRIM(p_address), TRIM(p_phone),
        v_price, v_paid_wallet, v_notes, 'pending'
    )
    RETURNING id INTO v_order_id;

    SELECT wallet_balance INTO v_new_balance FROM public.profiles WHERE id = v_uid;

    RETURN jsonb_build_object(
        'order_id', v_order_id,
        'amount_bdt', v_price,
        'paid_via_wallet', v_paid_wallet,
        'wallet_balance', v_new_balance
    );
END;
$$;

GRANT EXECUTE ON FUNCTION public.place_book_order_rpc(UUID, TEXT, TEXT, TEXT, TEXT, TEXT) TO authenticated;
