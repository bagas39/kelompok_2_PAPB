BEGIN;
SET LOCAL lock_timeout = '10s';
SET LOCAL statement_timeout = '60s';

-- 1. Kunci akses SELECT langsung terhadap kolom PII pada tabel users (#13b / #31)
REVOKE SELECT ON public.users FROM anon, authenticated;
GRANT SELECT (
    id, name, bio, "avatarUri", city,
    "preferredSize", "preferredGender", "preferredRadiusKm", "createdAt"
) ON public.users TO anon, authenticated;

-- 2. RPC pembacaan aman untuk daftar akun demo, profil pengguna, dan koordinat kasar Discover (#31)
CREATE OR REPLACE FUNCTION public.rpc_get_demo_accounts()
RETURNS JSONB
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_accounts JSONB;
BEGIN
    SELECT COALESCE(
        jsonb_agg(
            jsonb_build_object(
                'id', u.id,
                'name', u.name,
                'email', u.email
            )
            ORDER BY u.id ASC
        ),
        '[]'::jsonb
    )
    INTO v_accounts
    FROM public.users u
    WHERE LOWER(u.email) LIKE '%@cloble.demo';

    RETURN jsonb_build_object('code', 'SUCCESS', 'accounts', v_accounts);
END;
$$;

CREATE OR REPLACE FUNCTION public.rpc_get_user_profile(
    p_target_user_id BIGINT
)
RETURNS JSONB
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_caller_id BIGINT;
    v_user public.users%ROWTYPE;
    v_avg_rating DOUBLE PRECISION;
    v_review_count INTEGER;
    v_is_self BOOLEAN;
BEGIN
    v_caller_id := public.current_session_user_id();

    SELECT * INTO v_user
    FROM public.users
    WHERE id = p_target_user_id
    LIMIT 1;

    IF NOT FOUND THEN
        RETURN jsonb_build_object('code', 'NOT_FOUND');
    END IF;

    SELECT COALESCE(AVG(rating)::double precision, 0.0), COUNT(*)::integer
    INTO v_avg_rating, v_review_count
    FROM public.reviews
    WHERE "revieweeId" = p_target_user_id;

    v_is_self := (v_caller_id IS NOT NULL AND v_caller_id = v_user.id);

    RETURN jsonb_build_object(
        'code', 'SUCCESS',
        'user', jsonb_build_object(
            'id', v_user.id,
            'name', v_user.name,
            'email', CASE WHEN v_is_self THEN v_user.email ELSE '' END,
            'bio', v_user.bio,
            'avatarUri', v_user."avatarUri",
            'city', v_user.city,
            'latitude', CASE WHEN v_is_self THEN v_user.latitude ELSE ROUND(v_user.latitude::numeric, 2)::double precision END,
            'longitude', CASE WHEN v_is_self THEN v_user.longitude ELSE ROUND(v_user.longitude::numeric, 2)::double precision END,
            'preferredSize', v_user."preferredSize",
            'preferredGender', v_user."preferredGender",
            'preferredRadiusKm', v_user."preferredRadiusKm",
            'createdAt', v_user."createdAt"
        ),
        'averageRating', v_avg_rating,
        'reviewCount', v_review_count
    );
END;
$$;

CREATE OR REPLACE FUNCTION public.rpc_get_users_coarse_locations(
    p_user_ids BIGINT[]
)
RETURNS JSONB
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_caller_id BIGINT;
    v_locations JSONB;
BEGIN
    v_caller_id := public.current_session_user_id();
    IF v_caller_id IS NULL THEN
        RETURN jsonb_build_object('code', 'UNAUTHORIZED', 'locations', '[]'::jsonb);
    END IF;

    SELECT COALESCE(
        jsonb_agg(
            jsonb_build_object(
                'id', u.id,
                'city', u.city,
                'latitude', ROUND(u.latitude::numeric, 2)::double precision,
                'longitude', ROUND(u.longitude::numeric, 2)::double precision
            )
            ORDER BY u.id ASC
        ),
        '[]'::jsonb
    )
    INTO v_locations
    FROM public.users u
    WHERE u.id = ANY(COALESCE(p_user_ids, ARRAY[]::BIGINT[]));

    RETURN jsonb_build_object('code', 'SUCCESS', 'locations', v_locations);
END;
$$;

GRANT EXECUTE ON FUNCTION public.rpc_get_demo_accounts() TO anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.rpc_get_user_profile(BIGINT) TO anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.rpc_get_users_coarse_locations(BIGINT[]) TO anon, authenticated, service_role;

-- 3. Validasi jam server & input pada RPC Autentikasi dan Registrasi (#32, #33)
CREATE OR REPLACE FUNCTION public.rpc_issue_password_reset_otp(
    p_email TEXT,
    p_otp_code TEXT,
    p_now_millis BIGINT
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

    IF v_clean_email = '' OR POSITION('@' IN v_clean_email) = 0 THEN
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

CREATE OR REPLACE FUNCTION public.rpc_verify_otp_and_reset_password(
    p_email TEXT,
    p_otp_code TEXT,
    p_new_password_hash TEXT,
    p_now_millis BIGINT
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

    IF v_clean_email = '' OR POSITION('@' IN v_clean_email) = 0 THEN
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

CREATE OR REPLACE FUNCTION public.rpc_register_user(
    p_name TEXT,
    p_email TEXT,
    p_password_hash TEXT,
    p_city TEXT,
    p_latitude DOUBLE PRECISION,
    p_longitude DOUBLE PRECISION,
    p_now_millis BIGINT
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
    v_clean_city := COALESCE(NULLIF(BTRIM(p_city), ''), 'Bandung, Jawa Barat');

    IF v_clean_name = '' OR LENGTH(v_clean_name) > 100 THEN
        RETURN jsonb_build_object('code', 'VALIDATION_FAILED', 'message', 'Nama tidak valid');
    END IF;

    IF v_clean_email = '' OR LENGTH(v_clean_email) > 254 OR POSITION('@' IN v_clean_email) = 0 THEN
        RETURN jsonb_build_object('code', 'VALIDATION_FAILED', 'message', 'Format email tidak valid');
    END IF;

    IF v_clean_email LIKE '%@cloble.demo' THEN
        RETURN jsonb_build_object('code', 'VALIDATION_FAILED', 'message', 'Email domain @cloble.demo tidak dapat didaftarkan');
    END IF;

    IF COALESCE(BTRIM(p_password_hash), '') NOT LIKE 'pbkdf2_hmac_sha256$%$%$%' THEN
        RETURN jsonb_build_object('code', 'VALIDATION_FAILED', 'message', 'Format hash password tidak valid');
    END IF;

    IF p_latitude IS NULL OR p_longitude IS NULL
       OR p_latitude < -90 OR p_latitude > 90
       OR p_longitude < -180 OR p_longitude > 180 THEN
        RETURN jsonb_build_object('code', 'VALIDATION_FAILED', 'message', 'Koordinat lokasi tidak valid');
    END IF;

    SELECT id INTO v_existing_id FROM public.users WHERE LOWER(email) = v_clean_email LIMIT 1;
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

-- 4. Sanitasi trigger & pengetatan RLS terhadap manipulasi parameter REST (#34)
CREATE OR REPLACE FUNCTION public.trg_sanitize_clothing_items_insert()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = public
AS $$
DECLARE
    v_now_ms BIGINT;
BEGIN
    IF current_user IN ('anon', 'authenticated') THEN
        v_now_ms := (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT;
        NEW."ownerId" := public.current_session_user_id();
        NEW.status := 'AVAILABLE';
        NEW."createdAt" := v_now_ms;
        NEW."updatedAt" := v_now_ms;
        NEW.name := BTRIM(COALESCE(NEW.name, ''));
        NEW.description := BTRIM(COALESCE(NEW.description, ''));

        IF NEW.name = '' OR LENGTH(NEW.name) > 120 THEN
            RAISE EXCEPTION 'Nama pakaian tidak valid';
        END IF;

        IF LENGTH(NEW.description) > 2000 THEN
            RAISE EXCEPTION 'Deskripsi pakaian terlalu panjang';
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS "trg_sanitize_clothing_items_insert" ON public.clothing_items;
CREATE TRIGGER "trg_sanitize_clothing_items_insert"
BEFORE INSERT ON public.clothing_items
FOR EACH ROW
EXECUTE FUNCTION public.trg_sanitize_clothing_items_insert();

CREATE OR REPLACE FUNCTION public.trg_sanitize_chat_messages_write()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = public
AS $$
DECLARE
    v_now_ms BIGINT;
BEGIN
    IF current_user IN ('anon', 'authenticated') THEN
        IF TG_OP = 'INSERT' THEN
            v_now_ms := (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT;
            NEW."senderId" := public.current_session_user_id();
            NEW."isRead" := FALSE;
            NEW."sentAt" := v_now_ms;
            NEW."messageText" := BTRIM(COALESCE(NEW."messageText", ''));

            IF NEW."messageText" = '' OR LENGTH(NEW."messageText") > 2000 THEN
                RAISE EXCEPTION 'Pesan chat tidak valid';
            END IF;
        ELSIF TG_OP = 'UPDATE' THEN
            NEW."isRead" := TRUE;
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS "trg_sanitize_chat_messages_write" ON public.chat_messages;
CREATE TRIGGER "trg_sanitize_chat_messages_write"
BEFORE INSERT OR UPDATE ON public.chat_messages
FOR EACH ROW
EXECUTE FUNCTION public.trg_sanitize_chat_messages_write();

DROP POLICY IF EXISTS "chat_messages_update_participant_only" ON public.chat_messages;
CREATE POLICY "chat_messages_update_participant_only" ON public.chat_messages FOR UPDATE
USING (
    "senderId" <> public.current_session_user_id()
    AND EXISTS (
        SELECT 1 FROM public.matches m
        WHERE m.id = chat_messages."matchId"
          AND (m."user1Id" = public.current_session_user_id() OR m."user2Id" = public.current_session_user_id())
    )
)
WITH CHECK (
    "senderId" <> public.current_session_user_id()
    AND EXISTS (
        SELECT 1 FROM public.matches m
        WHERE m.id = chat_messages."matchId"
          AND (m."user1Id" = public.current_session_user_id() OR m."user2Id" = public.current_session_user_id())
    )
);

CREATE OR REPLACE FUNCTION public.trg_sanitize_users_update()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = public
AS $$
BEGIN
    IF current_user IN ('anon', 'authenticated') THEN
        NEW.name := BTRIM(COALESCE(NEW.name, ''));
        NEW.bio := BTRIM(COALESCE(NEW.bio, ''));

        IF NEW.name = '' OR LENGTH(NEW.name) > 100 THEN
            RAISE EXCEPTION 'Nama profil tidak valid';
        END IF;

        IF LENGTH(NEW.bio) > 1000 THEN
            RAISE EXCEPTION 'Bio profil terlalu panjang';
        END IF;

        IF NEW.latitude IS NULL OR NEW.longitude IS NULL
           OR NEW.latitude < -90 OR NEW.latitude > 90
           OR NEW.longitude < -180 OR NEW.longitude > 180 THEN
            RAISE EXCEPTION 'Koordinat pengguna tidak valid';
        END IF;

        IF NEW."preferredRadiusKm" IS NULL OR NEW."preferredRadiusKm" < 1 OR NEW."preferredRadiusKm" > 20000 THEN
            RAISE EXCEPTION 'Radius preferensi tidak valid';
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS "trg_sanitize_users_update" ON public.users;
CREATE TRIGGER "trg_sanitize_users_update"
BEFORE UPDATE ON public.users
FOR EACH ROW
EXECUTE FUNCTION public.trg_sanitize_users_update();

CREATE OR REPLACE FUNCTION public.trg_sanitize_matches_update()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = public
AS $$
BEGIN
    IF current_user IN ('anon', 'authenticated') AND OLD.status = NEW.status THEN
        NEW."updatedAt" := (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT;
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS "trg_sanitize_matches_update" ON public.matches;
CREATE TRIGGER "trg_sanitize_matches_update"
BEFORE UPDATE ON public.matches
FOR EACH ROW
EXECUTE FUNCTION public.trg_sanitize_matches_update();

CREATE OR REPLACE FUNCTION public.trg_sanitize_swipe_passes_insert()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = public
AS $$
DECLARE
    v_target_owner_id BIGINT;
BEGIN
    IF current_user IN ('anon', 'authenticated') THEN
        NEW."userId" := public.current_session_user_id();
        NEW."createdAt" := (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT;

        SELECT "ownerId" INTO v_target_owner_id
        FROM public.clothing_items
        WHERE id = NEW."targetItemId";

        IF NOT FOUND OR v_target_owner_id = NEW."userId" THEN
            RAISE EXCEPTION 'Target pakaian swipe pass tidak valid';
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS "trg_sanitize_swipe_passes_insert" ON public.swipe_passes;
CREATE TRIGGER "trg_sanitize_swipe_passes_insert"
BEFORE INSERT ON public.swipe_passes
FOR EACH ROW
EXECUTE FUNCTION public.trg_sanitize_swipe_passes_insert();

-- 5. Cegah penimpaan atau penghapusan foto pengguna lain di Storage (#35)
DROP POLICY IF EXISTS "Public update clothing-photos" ON storage.objects;
DROP POLICY IF EXISTS "Public delete clothing-photos" ON storage.objects;

COMMIT;
