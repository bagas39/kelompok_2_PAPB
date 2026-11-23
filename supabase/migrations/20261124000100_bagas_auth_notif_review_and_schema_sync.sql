BEGIN;

DROP INDEX IF EXISTS public.index_matches_unique_active_user_pair;

DO $constraint$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'public.matches'::regclass
          AND conname = 'matches_active_items_excl'
    ) THEN
        ALTER TABLE public.matches
        ADD CONSTRAINT matches_active_items_excl
        EXCLUDE USING gist (
            (int8multirange(
                int8range("user1ItemId", "user1ItemId", '[]'),
                int8range("user2ItemId", "user2ItemId", '[]')
            )) WITH &&
        ) WHERE (status = 'ACTIVE');
    END IF;
END;
$constraint$;

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
SET search_path = public, extensions
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
    v_now_ms := (EXTRACT(EPOCH FROM pg_catalog.clock_timestamp()) * 1000)::BIGINT;
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

    IF v_clean_email LIKE '%@cloble.demo' THEN
        RETURN jsonb_build_object('code', 'VALIDATION_FAILED', 'message', 'Email domain @cloble.demo tidak dapat didaftarkan');
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

    BEGIN
        INSERT INTO public.users (
            name, email, "passwordHash", bio, city, latitude, longitude,
            "preferredSize", "preferredGender", "preferredRadiusKm", "createdAt"
        )
        VALUES (
            v_clean_name, v_clean_email, p_password_hash, '', v_clean_city, p_latitude, p_longitude,
            'M', 'Uniseks', 10, v_now_ms
        )
        RETURNING id INTO v_new_user_id;
    EXCEPTION WHEN unique_violation THEN
        RETURN jsonb_build_object('code', 'EMAIL_ALREADY_REGISTERED');
    END;

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
    v_new_otp_id BIGINT;
BEGIN
    v_now_ms := (EXTRACT(EPOCH FROM pg_catalog.clock_timestamp()) * 1000)::BIGINT;
    v_clean_email := LOWER(BTRIM(COALESCE(p_email, '')));
    v_clean_otp := BTRIM(COALESCE(p_otp_code, ''));

    DELETE FROM public.password_reset_otps WHERE "expiresAt" < v_now_ms - 86400000;

    IF v_clean_email = '' OR v_clean_email !~ '^[^@\s]+@[^@\s]+\.[^@\s]+$' THEN
        RETURN jsonb_build_object('code', 'VALIDATION_FAILED', 'message', 'Format email tidak valid');
    END IF;

    IF v_clean_otp !~ '^[0-9]{6}$' THEN
        RETURN jsonb_build_object('code', 'VALIDATION_FAILED', 'message', 'Kode OTP harus 6 digit');
    END IF;

    PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtext(v_clean_email));

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
    VALUES (v_user_id, v_clean_email, v_otp_hash, 0, v_now_ms + 600000, NULL, v_now_ms)
    RETURNING id INTO v_new_otp_id;

    RETURN jsonb_build_object(
        'code', 'SUCCESS',
        'shouldSendEmail', TRUE,
        'userName', COALESCE(v_user_name, 'Pengguna'),
        'otpRowId', v_new_otp_id
    );
END;
$$;

REVOKE ALL ON FUNCTION public.rpc_issue_password_reset_otp(TEXT, TEXT, BIGINT) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.rpc_issue_password_reset_otp(TEXT, TEXT, BIGINT) TO service_role;

CREATE OR REPLACE FUNCTION public.rpc_rollback_issued_password_reset_otp(
    p_otp_id BIGINT
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    IF p_otp_id IS NOT NULL AND p_otp_id > 0 THEN
        DELETE FROM public.password_reset_otps
        WHERE id = p_otp_id
          AND "consumedAt" IS NULL;
    END IF;

    RETURN jsonb_build_object('code', 'SUCCESS');
END;
$$;

REVOKE ALL ON FUNCTION public.rpc_rollback_issued_password_reset_otp(BIGINT) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.rpc_rollback_issued_password_reset_otp(BIGINT) TO service_role;

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
    v_now_ms := (EXTRACT(EPOCH FROM pg_catalog.clock_timestamp()) * 1000)::BIGINT;
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
    DELETE FROM public.user_fcm_tokens WHERE "userId" = v_user_id;
    DELETE FROM public.login_attempts WHERE email = v_clean_email;

    RETURN jsonb_build_object('code', 'SUCCESS', 'userId', v_user_id);
END;
$$;

REVOKE ALL ON FUNCTION public.rpc_verify_otp_and_reset_password(TEXT, TEXT, TEXT, BIGINT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.rpc_verify_otp_and_reset_password(TEXT, TEXT, TEXT, BIGINT) TO anon, authenticated, service_role;

CREATE OR REPLACE FUNCTION public.rpc_get_notifications()
RETURNS JSONB
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_user_id BIGINT;
    v_notifications JSONB;
    v_active_item_ids JSONB;
    v_reviewed_match_ids JSONB;
BEGIN
    v_user_id := public.current_session_user_id();
    IF v_user_id IS NULL THEN
        RETURN jsonb_build_object(
            'code', 'UNAUTHORIZED',
            'notifications', '[]'::jsonb,
            'activeItemIds', '[]'::jsonb,
            'reviewedMatchIds', '[]'::jsonb
        );
    END IF;

    SELECT COALESCE(jsonb_agg(DISTINCT active_items.item_id), '[]'::jsonb)
    INTO v_active_item_ids
    FROM (
        SELECT "user1ItemId" AS item_id FROM public.matches WHERE status = 'ACTIVE'
        UNION
        SELECT "user2ItemId" AS item_id FROM public.matches WHERE status = 'ACTIVE'
    ) active_items
    WHERE active_items.item_id IN (
        SELECT i."targetItemId"
        FROM public.notifications n
        JOIN public.interests i ON i.id = n."relatedInterestId"
        WHERE n."userId" = v_user_id
          AND i."targetItemId" IS NOT NULL
    );

    SELECT COALESCE(jsonb_agg(DISTINCT r."matchId"), '[]'::jsonb)
    INTO v_reviewed_match_ids
    FROM public.reviews r
    WHERE r."reviewerId" = v_user_id;

    SELECT COALESCE(
        jsonb_agg(
            jsonb_build_object(
                'id', n.id,
                'userId', n."userId",
                'type', n.type,
                'title', n.title,
                'body', n.body,
                'relatedInterestId', n."relatedInterestId",
                'relatedMatchId', n."relatedMatchId",
                'isRead', n."isRead",
                'createdAt', n."createdAt",
                'interests', CASE WHEN i.id IS NULL THEN NULL ELSE jsonb_build_object(
                    'id', i.id,
                    'senderUserId', i."senderUserId",
                    'targetItemId', i."targetItemId",
                    'status', i.status,
                    'users', CASE WHEN su.id IS NULL THEN NULL ELSE jsonb_build_object(
                        'id', su.id,
                        'name', su.name
                    ) END,
                    'clothing_items', CASE WHEN ti.id IS NULL THEN NULL ELSE jsonb_build_object(
                        'id', ti.id,
                        'name', ti.name,
                        'status', ti.status,
                        'isBarterAllowed', ti."isBarterAllowed",
                        'mainPhotoUri', ti."mainPhotoUri"
                    ) END
                ) END,
                'matches', CASE WHEN m.id IS NULL THEN NULL ELSE jsonb_build_object(
                    'id', m.id,
                    'user1Id', m."user1Id",
                    'user2Id', m."user2Id",
                    'status', m.status,
                    'user1', CASE WHEN u1.id IS NULL THEN NULL ELSE jsonb_build_object(
                        'id', u1.id,
                        'name', u1.name
                    ) END,
                    'user2', CASE WHEN u2.id IS NULL THEN NULL ELSE jsonb_build_object(
                        'id', u2.id,
                        'name', u2.name
                    ) END
                ) END
            )
            ORDER BY n."createdAt" DESC, n.id DESC
        ),
        '[]'::jsonb
    )
    INTO v_notifications
    FROM public.notifications n
    LEFT JOIN public.interests i ON i.id = n."relatedInterestId"
    LEFT JOIN public.users su ON su.id = CASE
        WHEN n.type = 'INCOMING_INTEREST' THEN i."senderUserId"
        WHEN i."senderUserId" = v_user_id THEN i."receiverUserId"
        ELSE i."senderUserId"
    END
    LEFT JOIN public.clothing_items ti ON ti.id = i."targetItemId"
    LEFT JOIN public.matches m ON m.id = n."relatedMatchId"
    LEFT JOIN public.users u1 ON u1.id = m."user1Id"
    LEFT JOIN public.users u2 ON u2.id = m."user2Id"
    WHERE n."userId" = v_user_id;

    RETURN jsonb_build_object(
        'code', 'SUCCESS',
        'notifications', v_notifications,
        'activeItemIds', v_active_item_ids,
        'reviewedMatchIds', v_reviewed_match_ids
    );
END;
$$;

REVOKE ALL ON FUNCTION public.rpc_get_notifications() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.rpc_get_notifications() TO anon, authenticated, service_role;

DROP FUNCTION IF EXISTS public.rpc_submit_review_protected(BIGINT, BIGINT, INTEGER, TEXT, BIGINT);
CREATE OR REPLACE FUNCTION public.rpc_submit_review_protected(
    p_match_id BIGINT,
    p_reviewer_id BIGINT,
    p_rating INTEGER,
    p_comment TEXT,
    p_now_millis BIGINT DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_session_user_id BIGINT;
    v_now_ms BIGINT;
    v_clean_comment TEXT;
    v_match public.matches%ROWTYPE;
    v_reviewee_id BIGINT;
    v_review_id BIGINT;
BEGIN
    v_session_user_id := public.current_session_user_id();
    IF v_session_user_id IS NULL OR v_session_user_id <> p_reviewer_id THEN
        RETURN jsonb_build_object('code', 'NOT_MATCH_PARTICIPANT');
    END IF;

    IF p_rating IS NULL OR p_rating < 1 OR p_rating > 5 THEN
        RETURN jsonb_build_object('code', 'INVALID_RATING');
    END IF;

    v_clean_comment := BTRIM(COALESCE(p_comment, ''));
    IF LENGTH(v_clean_comment) > 1000 THEN
        RETURN jsonb_build_object('code', 'VALIDATION_FAILED', 'message', 'Komentar ulasan terlalu panjang');
    END IF;

    SELECT * INTO v_match FROM public.matches WHERE id = p_match_id FOR UPDATE;
    IF NOT FOUND THEN
        RETURN jsonb_build_object('code', 'MATCH_NOT_FOUND');
    END IF;

    IF v_session_user_id <> v_match."user1Id" AND v_session_user_id <> v_match."user2Id" THEN
        RETURN jsonb_build_object('code', 'NOT_MATCH_PARTICIPANT');
    END IF;

    IF v_match.status <> 'COMPLETED' THEN
        RETURN jsonb_build_object('code', 'MATCH_NOT_COMPLETED');
    END IF;

    v_now_ms := (EXTRACT(EPOCH FROM pg_catalog.clock_timestamp()) * 1000)::BIGINT;
    v_reviewee_id := CASE WHEN v_session_user_id = v_match."user1Id" THEN v_match."user2Id" ELSE v_match."user1Id" END;

    INSERT INTO public.reviews ("matchId", "reviewerId", "revieweeId", rating, comment, "createdAt")
    VALUES (p_match_id, v_session_user_id, v_reviewee_id, p_rating, v_clean_comment, v_now_ms)
    ON CONFLICT ("matchId", "reviewerId") DO NOTHING
    RETURNING id INTO v_review_id;

    IF v_review_id IS NULL THEN
        RETURN jsonb_build_object('code', 'ALREADY_REVIEWED');
    END IF;

    UPDATE public.notifications
    SET "isRead" = TRUE
    WHERE "userId" = v_session_user_id
      AND "relatedMatchId" = p_match_id
      AND type = 'EXCHANGE_COMPLETED'
      AND "isRead" = FALSE;

    RETURN jsonb_build_object('code', 'SUCCESS', 'reviewId', v_review_id);
END;
$$;

REVOKE ALL ON FUNCTION public.rpc_submit_review_protected(BIGINT, BIGINT, INTEGER, TEXT, BIGINT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.rpc_submit_review_protected(BIGINT, BIGINT, INTEGER, TEXT, BIGINT) TO anon, authenticated, service_role;

COMMIT;
