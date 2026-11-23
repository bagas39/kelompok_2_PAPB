BEGIN;

-- ============================================================================
-- Production Readiness Cleanup & Hardening
-- 1. Config internal & rate-limit login attempts
-- 2. Hapus akun @cloble.demo & data seed terkait
-- 3. Hapus jalur demo login & samakan autentikasi
-- 4. Perketat validasi registrasi, OTP, dan storage clothing-photos
-- ============================================================================

CREATE TABLE IF NOT EXISTS public.app_internal_config (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL,
    "updatedAt" BIGINT NOT NULL
);

REVOKE ALL ON public.app_internal_config FROM PUBLIC, anon, authenticated;

INSERT INTO public.app_internal_config (key, value, "updatedAt")
VALUES (
    'login_challenge_pepper',
    gen_random_uuid()::TEXT || '-' || gen_random_uuid()::TEXT,
    (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT
)
ON CONFLICT (key) DO NOTHING;

CREATE TABLE IF NOT EXISTS public.login_attempts (
    id BIGSERIAL PRIMARY KEY,
    email TEXT NOT NULL,
    "attemptedAt" BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS index_login_attempts_email_time
ON public.login_attempts (email, "attemptedAt");

REVOKE ALL ON public.login_attempts FROM PUBLIC, anon, authenticated;
REVOKE ALL ON SEQUENCE public.login_attempts_id_seq FROM PUBLIC, anon, authenticated;

DO $$
DECLARE
    v_demo_user_ids BIGINT[];
    v_demo_item_ids BIGINT[];
    v_demo_interest_ids BIGINT[];
    v_demo_match_ids BIGINT[];
BEGIN
    SELECT COALESCE(ARRAY_AGG(id), ARRAY[]::BIGINT[])
    INTO v_demo_user_ids
    FROM public.users
    WHERE LOWER(BTRIM(email)) LIKE '%@cloble.demo';

    IF COALESCE(array_length(v_demo_user_ids, 1), 0) > 0 THEN
        SELECT COALESCE(ARRAY_AGG(id), ARRAY[]::BIGINT[])
        INTO v_demo_item_ids
        FROM public.clothing_items
        WHERE "ownerId" = ANY(v_demo_user_ids);

        SELECT COALESCE(ARRAY_AGG(id), ARRAY[]::BIGINT[])
        INTO v_demo_interest_ids
        FROM public.interests
        WHERE "senderUserId" = ANY(v_demo_user_ids)
           OR "receiverUserId" = ANY(v_demo_user_ids)
           OR "targetItemId" = ANY(v_demo_item_ids);

        SELECT COALESCE(ARRAY_AGG(id), ARRAY[]::BIGINT[])
        INTO v_demo_match_ids
        FROM public.matches
        WHERE "user1Id" = ANY(v_demo_user_ids)
           OR "user2Id" = ANY(v_demo_user_ids)
           OR "user1ItemId" = ANY(v_demo_item_ids)
           OR "user2ItemId" = ANY(v_demo_item_ids)
           OR "sourceInterestId" = ANY(v_demo_interest_ids);

        DELETE FROM public.chat_messages
        WHERE "senderId" = ANY(v_demo_user_ids)
           OR "matchId" = ANY(v_demo_match_ids);

        DELETE FROM public.reviews
        WHERE "reviewerId" = ANY(v_demo_user_ids)
           OR "revieweeId" = ANY(v_demo_user_ids)
           OR "matchId" = ANY(v_demo_match_ids);

        DELETE FROM public.notifications
        WHERE "userId" = ANY(v_demo_user_ids)
           OR "relatedMatchId" = ANY(v_demo_match_ids)
           OR "relatedInterestId" = ANY(v_demo_interest_ids);

        DELETE FROM public.matches
        WHERE id = ANY(v_demo_match_ids);

        DELETE FROM public.interests
        WHERE id = ANY(v_demo_interest_ids);

        DELETE FROM public.swipe_passes
        WHERE "userId" = ANY(v_demo_user_ids)
           OR "targetItemId" = ANY(v_demo_item_ids);

        DELETE FROM public.clothing_items
        WHERE id = ANY(v_demo_item_ids);

        DELETE FROM public.user_sessions
        WHERE "userId" = ANY(v_demo_user_ids);

        DELETE FROM public.user_fcm_tokens
        WHERE "userId" = ANY(v_demo_user_ids);

        DELETE FROM public.password_reset_otps
        WHERE "userId" = ANY(v_demo_user_ids)
           OR LOWER(BTRIM(email)) LIKE '%@cloble.demo';

        DELETE FROM public.users
        WHERE id = ANY(v_demo_user_ids);
    ELSE
        DELETE FROM public.password_reset_otps
        WHERE LOWER(BTRIM(email)) LIKE '%@cloble.demo';
    END IF;
END;
$$;

DROP FUNCTION IF EXISTS public.rpc_get_demo_accounts();
DROP FUNCTION IF EXISTS public.rpc_create_session(BIGINT, TEXT, BOOLEAN, TEXT);
DROP FUNCTION IF EXISTS public.rpc_create_session(BIGINT, TEXT, BOOLEAN);

CREATE OR REPLACE FUNCTION public.rpc_get_login_challenge(
    p_email TEXT
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_clean_email TEXT;
    v_user public.users%ROWTYPE;
    v_iterations INTEGER;
    v_salt_hex TEXT;
    v_pepper TEXT;
BEGIN
    v_clean_email := LOWER(BTRIM(COALESCE(p_email, '')));

    SELECT * INTO v_user
    FROM public.users
    WHERE LOWER(BTRIM(email)) = v_clean_email
    LIMIT 1;

    IF NOT FOUND THEN
        SELECT COALESCE(value, 'cloble_pepper') INTO v_pepper
        FROM public.app_internal_config
        WHERE key = 'login_challenge_pepper'
        LIMIT 1;

        v_pepper := COALESCE(v_pepper, 'cloble_pepper');

        RETURN jsonb_build_object(
            'code', 'SUCCESS',
            'iterations', 120000,
            'saltHex', md5(v_clean_email || ':' || v_pepper || ':cloble_challenge_salt_v1')
        );
    END IF;

    v_iterations := COALESCE(NULLIF(split_part(v_user."passwordHash", '$', 2), '')::INTEGER, 120000);
    v_salt_hex := split_part(v_user."passwordHash", '$', 3);

    RETURN jsonb_build_object(
        'code', 'SUCCESS',
        'iterations', v_iterations,
        'saltHex', v_salt_hex
    );
END;
$$;

REVOKE ALL ON FUNCTION public.rpc_get_login_challenge(TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.rpc_get_login_challenge(TEXT) TO anon, authenticated, service_role;

CREATE OR REPLACE FUNCTION public.rpc_create_session(
    p_email TEXT,
    p_password_hash TEXT
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_clean_email TEXT;
    v_now_ms BIGINT;
    v_window_start_ms BIGINT;
    v_attempt_count INTEGER;
    v_oldest_attempt BIGINT;
    v_retry_seconds INTEGER;
    v_retry_minutes INTEGER;
    v_user public.users%ROWTYPE;
    v_token TEXT;
    v_expires_ms BIGINT;
BEGIN
    v_clean_email := LOWER(BTRIM(COALESCE(p_email, '')));
    v_now_ms := (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT;
    v_window_start_ms := v_now_ms - 900000;

    DELETE FROM public.login_attempts WHERE "attemptedAt" < v_now_ms - 86400000;
    DELETE FROM public.user_sessions WHERE "expiresAt" < v_now_ms;
    DELETE FROM public.password_reset_otps WHERE "expiresAt" < v_now_ms - 86400000;

    IF v_clean_email = '' OR COALESCE(BTRIM(p_password_hash), '') = '' THEN
        RETURN jsonb_build_object('code', 'INVALID_CREDENTIALS');
    END IF;

    SELECT COUNT(*)::INTEGER, MIN("attemptedAt")
    INTO v_attempt_count, v_oldest_attempt
    FROM public.login_attempts
    WHERE email = v_clean_email
      AND "attemptedAt" >= v_window_start_ms;

    IF v_attempt_count >= 5 THEN
        v_retry_seconds := GREATEST(1, CEIL(((v_oldest_attempt + 900000) - v_now_ms) / 1000.0)::INTEGER);
        v_retry_minutes := GREATEST(1, CEIL(v_retry_seconds / 60.0)::INTEGER);
        RETURN jsonb_build_object(
            'code', 'RATE_LIMITED',
            'retryAfterSeconds', v_retry_seconds,
            'message', 'Terlalu banyak percobaan login gagal. Coba lagi dalam ' || v_retry_minutes || ' menit.'
        );
    END IF;

    SELECT * INTO v_user
    FROM public.users
    WHERE LOWER(BTRIM(email)) = v_clean_email
    LIMIT 1;

    IF NOT FOUND OR v_user."passwordHash" <> p_password_hash THEN
        INSERT INTO public.login_attempts (email, "attemptedAt")
        VALUES (v_clean_email, v_now_ms);
        RETURN jsonb_build_object('code', 'INVALID_CREDENTIALS');
    END IF;

    DELETE FROM public.login_attempts WHERE email = v_clean_email;

    v_token := gen_random_uuid()::TEXT || '-' || gen_random_uuid()::TEXT;
    v_expires_ms := v_now_ms + (30::BIGINT * 86400000::BIGINT);

    INSERT INTO public.user_sessions (token, "userId", "createdAt", "expiresAt")
    VALUES (v_token, v_user.id, v_now_ms, v_expires_ms);

    RETURN jsonb_build_object(
        'code', 'SUCCESS',
        'userId', v_user.id,
        'sessionToken', v_token
    );
END;
$$;

REVOKE ALL ON FUNCTION public.rpc_create_session(TEXT, TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.rpc_create_session(TEXT, TEXT) TO anon, authenticated, service_role;

DROP FUNCTION IF EXISTS public.rpc_register_user(TEXT, TEXT, TEXT, TEXT, DOUBLE PRECISION, DOUBLE PRECISION, BIGINT);
CREATE OR REPLACE FUNCTION public.rpc_register_user(
    p_name TEXT,
    p_email TEXT,
    p_password_hash TEXT,
    p_city TEXT,
    p_latitude DOUBLE PRECISION,
    p_longitude DOUBLE PRECISION,
    p_now_millis BIGINT DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_now_ms BIGINT;
    v_clean_email TEXT;
    v_clean_name TEXT;
    v_clean_city TEXT;
    v_existing_id BIGINT;
    v_new_user_id BIGINT;
    v_token TEXT;
    v_expires_ms BIGINT;
BEGIN
    v_now_ms := (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT;
    v_clean_email := LOWER(BTRIM(COALESCE(p_email, '')));
    v_clean_name := BTRIM(COALESCE(p_name, ''));
    v_clean_city := BTRIM(COALESCE(p_city, ''));

    DELETE FROM public.user_sessions WHERE "expiresAt" < v_now_ms;

    IF v_clean_name = '' OR LENGTH(v_clean_name) > 100 THEN
        RETURN jsonb_build_object('code', 'VALIDATION_FAILED', 'message', 'Nama tidak valid');
    END IF;

    IF v_clean_email = '' OR LENGTH(v_clean_email) > 254 OR v_clean_email !~ '^[^@\s]+@[^@\s]+\.[^@\s]+$' THEN
        RETURN jsonb_build_object('code', 'VALIDATION_FAILED', 'message', 'Format email tidak valid');
    END IF;

    IF v_clean_city = '' OR LENGTH(v_clean_city) > 120 THEN
        RETURN jsonb_build_object('code', 'VALIDATION_FAILED', 'message', 'Lokasi domisili wajib dipilih');
    END IF;

    IF COALESCE(BTRIM(p_password_hash), '') NOT LIKE 'pbkdf2_hmac_sha256$%$%$%' THEN
        RETURN jsonb_build_object('code', 'VALIDATION_FAILED', 'message', 'Format hash password tidak valid');
    END IF;

    IF p_latitude IS NULL OR p_longitude IS NULL
       OR p_latitude < -90 OR p_latitude > 90
       OR p_longitude < -180 OR p_longitude > 180 THEN
        RETURN jsonb_build_object('code', 'VALIDATION_FAILED', 'message', 'Koordinat lokasi tidak valid');
    END IF;

    SELECT id INTO v_existing_id FROM public.users WHERE LOWER(BTRIM(email)) = v_clean_email LIMIT 1;
    IF FOUND THEN
        RETURN jsonb_build_object('code', 'EMAIL_ALREADY_REGISTERED');
    END IF;

    INSERT INTO public.users (
        name, email, "passwordHash", bio, city, latitude, longitude,
        "preferredSize", "preferredGender", "preferredRadiusKm", "createdAt"
    )
    VALUES (
        v_clean_name, v_clean_email, p_password_hash, '', v_clean_city, p_latitude, p_longitude,
        'M', 'Uniseks', 10, v_now_ms
    )
    RETURNING id INTO v_new_user_id;

    v_token := gen_random_uuid()::TEXT || '-' || gen_random_uuid()::TEXT;
    v_expires_ms := v_now_ms + (30::BIGINT * 86400000::BIGINT);

    INSERT INTO public.user_sessions (token, "userId", "createdAt", "expiresAt")
    VALUES (v_token, v_new_user_id, v_now_ms, v_expires_ms);

    RETURN jsonb_build_object(
        'code', 'SUCCESS',
        'userId', v_new_user_id,
        'sessionToken', v_token
    );
END;
$$;

REVOKE ALL ON FUNCTION public.rpc_register_user(TEXT, TEXT, TEXT, TEXT, DOUBLE PRECISION, DOUBLE PRECISION, BIGINT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.rpc_register_user(TEXT, TEXT, TEXT, TEXT, DOUBLE PRECISION, DOUBLE PRECISION, BIGINT) TO anon, authenticated, service_role;

DROP FUNCTION IF EXISTS public.rpc_change_password_authenticated(TEXT, TEXT, BIGINT);
CREATE OR REPLACE FUNCTION public.rpc_change_password_authenticated(
    p_current_password_hash TEXT,
    p_new_password_hash TEXT,
    p_now_millis BIGINT DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_user_id BIGINT;
    v_now_ms BIGINT;
    v_user public.users%ROWTYPE;
    v_headers_raw TEXT;
    v_current_token TEXT;
BEGIN
    v_user_id := public.current_session_user_id();
    IF v_user_id IS NULL THEN
        RETURN jsonb_build_object('code', 'UNAUTHORIZED');
    END IF;

    IF COALESCE(BTRIM(p_new_password_hash), '') NOT LIKE 'pbkdf2_hmac_sha256$%$%$%' THEN
        RETURN jsonb_build_object('code', 'VALIDATION_FAILED', 'message', 'Password baru tidak valid');
    END IF;

    v_now_ms := (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT;
    DELETE FROM public.user_sessions WHERE "expiresAt" < v_now_ms;

    SELECT * INTO v_user FROM public.users WHERE id = v_user_id FOR UPDATE;
    IF NOT FOUND THEN
        RETURN jsonb_build_object('code', 'UNAUTHORIZED');
    END IF;

    IF COALESCE(p_current_password_hash, '') = '' OR v_user."passwordHash" <> p_current_password_hash THEN
        RETURN jsonb_build_object('code', 'INVALID_CURRENT_PASSWORD');
    END IF;

    UPDATE public.users
    SET "passwordHash" = p_new_password_hash
    WHERE id = v_user_id;

    v_headers_raw := current_setting('request.headers', true);
    IF v_headers_raw IS NOT NULL AND v_headers_raw <> '' THEN
        v_current_token := NULLIF(BTRIM((v_headers_raw::jsonb)->>'x-session-token'), '');
    END IF;

    IF v_current_token IS NOT NULL THEN
        DELETE FROM public.user_sessions WHERE "userId" = v_user_id AND token <> v_current_token;
    END IF;

    RETURN jsonb_build_object('code', 'SUCCESS', 'userId', v_user_id);
END;
$$;

REVOKE ALL ON FUNCTION public.rpc_change_password_authenticated(TEXT, TEXT, BIGINT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.rpc_change_password_authenticated(TEXT, TEXT, BIGINT) TO anon, authenticated, service_role;

DROP FUNCTION IF EXISTS public.rpc_issue_password_reset_otp(TEXT, TEXT, BIGINT);
CREATE OR REPLACE FUNCTION public.rpc_issue_password_reset_otp(
    p_email TEXT,
    p_otp_code TEXT,
    p_now_millis BIGINT DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, extensions
AS $$
DECLARE
    v_now_ms BIGINT;
    v_clean_email TEXT;
    v_clean_otp TEXT;
    v_user_id BIGINT;
    v_user_name TEXT;
    v_last_created BIGINT;
    v_otp_hash TEXT;
BEGIN
    v_now_ms := (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT;
    v_clean_email := LOWER(BTRIM(COALESCE(p_email, '')));
    v_clean_otp := BTRIM(COALESCE(p_otp_code, ''));

    DELETE FROM public.password_reset_otps WHERE "expiresAt" < v_now_ms - 86400000;

    IF v_clean_email = '' OR v_clean_email !~ '^[^@\s]+@[^@\s]+\.[^@\s]+$' THEN
        RETURN jsonb_build_object('code', 'VALIDATION_FAILED', 'message', 'Format email tidak valid');
    END IF;

    IF v_clean_otp !~ '^[0-9]{6}$' THEN
        RETURN jsonb_build_object('code', 'VALIDATION_FAILED', 'message', 'Kode OTP harus 6 digit');
    END IF;

    SELECT "createdAt" INTO v_last_created
    FROM public.password_reset_otps
    WHERE email = v_clean_email
    ORDER BY "createdAt" DESC
    LIMIT 1;

    IF FOUND AND (v_now_ms - v_last_created) < 30000 THEN
        RETURN jsonb_build_object('code', 'RATE_LIMITED', 'message', 'Tunggu 30 detik sebelum meminta kode OTP baru.');
    END IF;

    SELECT id, name INTO v_user_id, v_user_name
    FROM public.users
    WHERE LOWER(BTRIM(email)) = v_clean_email
    LIMIT 1;

    IF NOT FOUND THEN
        INSERT INTO public.password_reset_otps ("userId", email, "otpHash", "attemptCount", "expiresAt", "consumedAt", "createdAt")
        VALUES (NULL, v_clean_email, md5(v_clean_email || ':' || v_now_ms::TEXT), 0, v_now_ms, v_now_ms, v_now_ms);

        RETURN jsonb_build_object('code', 'SUCCESS', 'shouldSendEmail', FALSE);
    END IF;

    UPDATE public.password_reset_otps
    SET "consumedAt" = v_now_ms
    WHERE "userId" = v_user_id AND "consumedAt" IS NULL;

    v_otp_hash := md5(v_clean_email || ':' || v_clean_otp || ':cloble_otp_v1');

    INSERT INTO public.password_reset_otps ("userId", email, "otpHash", "attemptCount", "expiresAt", "consumedAt", "createdAt")
    VALUES (v_user_id, v_clean_email, v_otp_hash, 0, v_now_ms + 600000, NULL, v_now_ms);

    RETURN jsonb_build_object('code', 'SUCCESS', 'shouldSendEmail', TRUE, 'userName', COALESCE(v_user_name, 'Pengguna'));
END;
$$;

REVOKE ALL ON FUNCTION public.rpc_issue_password_reset_otp(TEXT, TEXT, BIGINT) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.rpc_issue_password_reset_otp(TEXT, TEXT, BIGINT) TO service_role;

DROP FUNCTION IF EXISTS public.rpc_verify_otp_and_reset_password(TEXT, TEXT, TEXT, BIGINT);
CREATE OR REPLACE FUNCTION public.rpc_verify_otp_and_reset_password(
    p_email TEXT,
    p_otp_code TEXT,
    p_new_password_hash TEXT,
    p_now_millis BIGINT DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, extensions
AS $$
DECLARE
    v_now_ms BIGINT;
    v_clean_email TEXT;
    v_clean_otp TEXT;
    v_user_id BIGINT;
    v_otp_row public.password_reset_otps%ROWTYPE;
    v_expected_hash TEXT;
    v_new_attempts INTEGER;
BEGIN
    v_now_ms := (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT;
    v_clean_email := LOWER(BTRIM(COALESCE(p_email, '')));
    v_clean_otp := BTRIM(COALESCE(p_otp_code, ''));

    DELETE FROM public.password_reset_otps WHERE "expiresAt" < v_now_ms - 86400000;

    IF v_clean_email = '' OR v_clean_email !~ '^[^@\s]+@[^@\s]+\.[^@\s]+$' THEN
        RETURN jsonb_build_object('code', 'VALIDATION_FAILED', 'message', 'Format email tidak valid');
    END IF;

    IF v_clean_otp !~ '^[0-9]{6}$' THEN
        RETURN jsonb_build_object('code', 'VALIDATION_FAILED', 'message', 'Kode OTP harus 6 digit angka');
    END IF;

    IF COALESCE(BTRIM(p_new_password_hash), '') NOT LIKE 'pbkdf2_hmac_sha256$%$%$%' THEN
        RETURN jsonb_build_object('code', 'VALIDATION_FAILED', 'message', 'Format hash password baru tidak valid');
    END IF;

    SELECT id INTO v_user_id
    FROM public.users
    WHERE LOWER(BTRIM(email)) = v_clean_email
    LIMIT 1;

    IF NOT FOUND THEN
        RETURN jsonb_build_object('code', 'OTP_EXPIRED_OR_NOT_FOUND');
    END IF;

    SELECT * INTO v_otp_row
    FROM public.password_reset_otps
    WHERE "userId" = v_user_id
      AND "consumedAt" IS NULL
    ORDER BY "createdAt" DESC
    LIMIT 1
    FOR UPDATE;

    IF NOT FOUND OR v_otp_row."expiresAt" < v_now_ms THEN
        RETURN jsonb_build_object('code', 'OTP_EXPIRED_OR_NOT_FOUND');
    END IF;

    IF v_otp_row."attemptCount" >= 5 THEN
        UPDATE public.password_reset_otps SET "consumedAt" = v_now_ms WHERE id = v_otp_row.id;
        RETURN jsonb_build_object('code', 'OTP_MAX_ATTEMPTS_EXCEEDED');
    END IF;

    v_expected_hash := md5(v_clean_email || ':' || v_clean_otp || ':cloble_otp_v1');

    IF v_otp_row."otpHash" <> v_expected_hash THEN
        v_new_attempts := v_otp_row."attemptCount" + 1;
        IF v_new_attempts >= 5 THEN
            UPDATE public.password_reset_otps
            SET "attemptCount" = v_new_attempts, "consumedAt" = v_now_ms
            WHERE id = v_otp_row.id;
            RETURN jsonb_build_object('code', 'OTP_MAX_ATTEMPTS_EXCEEDED');
        ELSE
            UPDATE public.password_reset_otps
            SET "attemptCount" = v_new_attempts
            WHERE id = v_otp_row.id;
            RETURN jsonb_build_object('code', 'OTP_INVALID', 'remainingAttempts', 5 - v_new_attempts);
        END IF;
    END IF;

    UPDATE public.password_reset_otps SET "consumedAt" = v_now_ms WHERE id = v_otp_row.id;
    UPDATE public.users SET "passwordHash" = p_new_password_hash WHERE id = v_user_id;
    DELETE FROM public.user_sessions WHERE "userId" = v_user_id;

    RETURN jsonb_build_object('code', 'SUCCESS', 'userId', v_user_id);
END;
$$;

REVOKE ALL ON FUNCTION public.rpc_verify_otp_and_reset_password(TEXT, TEXT, TEXT, BIGINT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.rpc_verify_otp_and_reset_password(TEXT, TEXT, TEXT, BIGINT) TO anon, authenticated, service_role;

CREATE OR REPLACE FUNCTION public.rpc_logout_session()
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_headers_raw TEXT;
    v_token TEXT;
BEGIN
    v_headers_raw := current_setting('request.headers', true);
    IF v_headers_raw IS NOT NULL AND v_headers_raw <> '' THEN
        v_token := (v_headers_raw::jsonb)->>'x-session-token';
        IF v_token IS NOT NULL AND v_token <> '' THEN
            DELETE FROM public.user_sessions WHERE token = v_token;
        END IF;
    END IF;
    RETURN jsonb_build_object('code', 'SUCCESS');
EXCEPTION
    WHEN OTHERS THEN
        RETURN jsonb_build_object('code', 'SUCCESS');
END;
$$;

REVOKE ALL ON FUNCTION public.rpc_logout_session() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.rpc_logout_session() TO anon, authenticated, service_role;

CREATE OR REPLACE FUNCTION public.rpc_validate_session()
RETURNS JSONB
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_user_id BIGINT;
BEGIN
    v_user_id := public.current_session_user_id();
    IF v_user_id IS NULL THEN
        RETURN jsonb_build_object('code', 'INVALID_SESSION');
    END IF;

    RETURN jsonb_build_object('code', 'VALID', 'userId', v_user_id);
END;
$$;

REVOKE ALL ON FUNCTION public.rpc_validate_session() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.rpc_validate_session() TO anon, authenticated, service_role;

UPDATE storage.buckets
SET public = true,
    file_size_limit = 5242880,
    allowed_mime_types = ARRAY['image/jpeg', 'image/png', 'image/webp']
WHERE id = 'clothing-photos';

DROP POLICY IF EXISTS "Public read clothing-photos" ON storage.objects;
DROP POLICY IF EXISTS "Public upload clothing-photos" ON storage.objects;
DROP POLICY IF EXISTS "Public update clothing-photos" ON storage.objects;
DROP POLICY IF EXISTS "Public delete clothing-photos" ON storage.objects;

CREATE POLICY "Public read clothing-photos"
ON storage.objects FOR SELECT
USING (bucket_id = 'clothing-photos');

CREATE POLICY "Public upload clothing-photos"
ON storage.objects FOR INSERT
WITH CHECK (bucket_id = 'clothing-photos' AND public.current_session_user_id() IS NOT NULL);

COMMIT;
