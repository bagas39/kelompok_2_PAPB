BEGIN;

INSERT INTO public.app_internal_config (key, value, "updatedAt")
VALUES (
    'storage_photo_url_prefix',
    'https://ifgfvvhygsvknmstescf.supabase.co/storage/v1/object/public/clothing-photos/',
    (EXTRACT(EPOCH FROM pg_catalog.clock_timestamp()) * 1000)::BIGINT
)
ON CONFLICT (key) DO UPDATE
SET value = EXCLUDED.value,
    "updatedAt" = EXCLUDED."updatedAt";

UPDATE public.users
SET "avatarUri" = NULL
WHERE "avatarUri" IS NOT NULL
  AND (
      BTRIM("avatarUri") = ''
      OR "avatarUri" NOT LIKE 'https://ifgfvvhygsvknmstescf.supabase.co/storage/v1/object/public/clothing-photos/%'
  );

UPDATE public.clothing_items
SET "mainPhotoUri" = NULL
WHERE "mainPhotoUri" IS NOT NULL
  AND (
      BTRIM("mainPhotoUri") = ''
      OR "mainPhotoUri" NOT LIKE 'https://ifgfvvhygsvknmstescf.supabase.co/storage/v1/object/public/clothing-photos/%'
  );

UPDATE public.clothing_items
SET "detailPhotoUri" = NULL
WHERE "detailPhotoUri" IS NOT NULL
  AND (
      BTRIM("detailPhotoUri") = ''
      OR "detailPhotoUri" NOT LIKE 'https://ifgfvvhygsvknmstescf.supabase.co/storage/v1/object/public/clothing-photos/%'
  );

UPDATE public.clothing_items
SET "extraPhotoUri" = NULL
WHERE "extraPhotoUri" IS NOT NULL
  AND (
      BTRIM("extraPhotoUri") = ''
      OR "extraPhotoUri" NOT LIKE 'https://ifgfvvhygsvknmstescf.supabase.co/storage/v1/object/public/clothing-photos/%'
  );

CREATE OR REPLACE FUNCTION public.is_valid_storage_photo_uri(
    p_uri TEXT
)
RETURNS BOOLEAN
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_clean TEXT;
    v_prefix TEXT;
    v_suffix TEXT;
BEGIN
    v_clean := NULLIF(BTRIM(COALESCE(p_uri, '')), '');
    IF v_clean IS NULL THEN
        RETURN TRUE;
    END IF;

    IF LENGTH(v_clean) > 500 THEN
        RETURN FALSE;
    END IF;

    SELECT value INTO v_prefix
    FROM public.app_internal_config
    WHERE key = 'storage_photo_url_prefix'
    LIMIT 1;

    IF v_prefix IS NULL OR v_prefix = '' THEN
        RETURN FALSE;
    END IF;

    IF LEFT(v_clean, LENGTH(v_prefix)) <> v_prefix THEN
        RETURN FALSE;
    END IF;

    v_suffix := SUBSTRING(v_clean FROM LENGTH(v_prefix) + 1);
    IF v_suffix IS NULL OR v_suffix = '' THEN
        RETURN FALSE;
    END IF;

    IF POSITION('..' IN v_suffix) > 0 OR POSITION('/.' IN v_suffix) > 0 THEN
        RETURN FALSE;
    END IF;

    RETURN v_suffix ~* '^[A-Za-z0-9][A-Za-z0-9._/-]*\.(jpg|jpeg|png|webp)$';
END;
$$;

REVOKE ALL ON FUNCTION public.is_valid_storage_photo_uri(TEXT) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.is_valid_storage_photo_uri(TEXT) TO service_role;

CREATE OR REPLACE FUNCTION public.current_session_user_id()
RETURNS BIGINT
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_headers_raw TEXT;
    v_token TEXT;
    v_user_id BIGINT;
    v_now_ms BIGINT;
BEGIN
    v_headers_raw := current_setting('request.headers', true);
    IF v_headers_raw IS NULL OR v_headers_raw = '' THEN
        RETURN NULL;
    END IF;

    v_token := (v_headers_raw::jsonb)->>'x-session-token';
    IF v_token IS NULL OR v_token = '' THEN
        RETURN NULL;
    END IF;

    v_now_ms := (EXTRACT(EPOCH FROM pg_catalog.clock_timestamp()) * 1000)::BIGINT;

    SELECT "userId" INTO v_user_id
    FROM public.user_sessions
    WHERE token = v_token AND "expiresAt" > v_now_ms
    LIMIT 1;

    RETURN v_user_id;
EXCEPTION
    WHEN OTHERS THEN
        RETURN NULL;
END;
$$;

REVOKE ALL ON FUNCTION public.current_session_user_id() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.current_session_user_id() TO anon, authenticated, service_role;

CREATE OR REPLACE FUNCTION public.rpc_get_current_password_challenge()
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_user_id BIGINT;
    v_user public.users%ROWTYPE;
    v_iterations INTEGER;
    v_salt_hex TEXT;
BEGIN
    v_user_id := public.current_session_user_id();
    IF v_user_id IS NULL THEN
        RETURN jsonb_build_object('code', 'UNAUTHORIZED');
    END IF;

    SELECT * INTO v_user FROM public.users WHERE id = v_user_id LIMIT 1;
    IF NOT FOUND THEN
        RETURN jsonb_build_object('code', 'UNAUTHORIZED');
    END IF;

    v_iterations := COALESCE(NULLIF(split_part(v_user."passwordHash", '$', 2), '')::INTEGER, 120000);
    v_salt_hex := split_part(v_user."passwordHash", '$', 3);

    RETURN jsonb_build_object(
        'code', 'SUCCESS',
        'userId', v_user.id,
        'iterations', v_iterations,
        'saltHex', v_salt_hex
    );
END;
$$;

REVOKE ALL ON FUNCTION public.rpc_get_current_password_challenge() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.rpc_get_current_password_challenge() TO anon, authenticated, service_role;

CREATE OR REPLACE FUNCTION public.rpc_get_active_match_context()
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_user_id BIGINT;
    v_active_items JSONB;
    v_other_active_items JSONB;
    v_active_partners JSONB;
BEGIN
    v_user_id := public.current_session_user_id();
    IF v_user_id IS NULL THEN
        RETURN jsonb_build_object(
            'code', 'UNAUTHORIZED',
            'activeItemIds', '[]'::jsonb,
            'otherActiveItemIds', '[]'::jsonb,
            'activePartnerUserIds', '[]'::jsonb
        );
    END IF;

    SELECT COALESCE(jsonb_agg(DISTINCT item_id), '[]'::jsonb) INTO v_active_items
    FROM (
        SELECT "user1ItemId" AS item_id FROM public.matches WHERE status = 'ACTIVE'
        UNION
        SELECT "user2ItemId" AS item_id FROM public.matches WHERE status = 'ACTIVE'
    ) t;

    SELECT COALESCE(jsonb_agg(DISTINCT item_id), '[]'::jsonb) INTO v_other_active_items
    FROM (
        SELECT "user1ItemId" AS item_id FROM public.matches WHERE status = 'ACTIVE' AND "user1Id" <> v_user_id AND "user2Id" <> v_user_id
        UNION
        SELECT "user2ItemId" AS item_id FROM public.matches WHERE status = 'ACTIVE' AND "user1Id" <> v_user_id AND "user2Id" <> v_user_id
    ) t;

    SELECT COALESCE(jsonb_agg(DISTINCT partner_id), '[]'::jsonb) INTO v_active_partners
    FROM (
        SELECT CASE WHEN "user1Id" = v_user_id THEN "user2Id" ELSE "user1Id" END AS partner_id
        FROM public.matches
        WHERE status = 'ACTIVE' AND ("user1Id" = v_user_id OR "user2Id" = v_user_id)
    ) t;

    RETURN jsonb_build_object(
        'code', 'SUCCESS',
        'activeItemIds', v_active_items,
        'otherActiveItemIds', v_other_active_items,
        'activePartnerUserIds', v_active_partners
    );
END;
$$;

REVOKE ALL ON FUNCTION public.rpc_get_active_match_context() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.rpc_get_active_match_context() TO anon, authenticated, service_role;

CREATE OR REPLACE FUNCTION public.rpc_get_user_profile(
    p_target_user_id BIGINT
)
RETURNS JSONB
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_caller_id BIGINT;
    v_user public.users%ROWTYPE;
    v_avg_rating DOUBLE PRECISION;
    v_review_count INTEGER;
    v_is_self BOOLEAN;
BEGIN
    v_caller_id := public.current_session_user_id();
    IF v_caller_id IS NULL THEN
        RETURN jsonb_build_object('code', 'UNAUTHORIZED');
    END IF;

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

    v_is_self := (v_caller_id = v_user.id);

    RETURN jsonb_build_object(
        'code', 'SUCCESS',
        'user', jsonb_build_object(
            'id', v_user.id,
            'name', v_user.name,
            'email', CASE WHEN v_is_self THEN v_user.email ELSE '' END,
            'bio', v_user.bio,
            'avatarUri', v_user."avatarUri",
            'city', v_user.city,
            'latitude', CASE WHEN v_is_self THEN v_user.latitude ELSE 0.0::double precision END,
            'longitude', CASE WHEN v_is_self THEN v_user.longitude ELSE 0.0::double precision END,
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

REVOKE ALL ON FUNCTION public.rpc_get_user_profile(BIGINT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.rpc_get_user_profile(BIGINT) TO anon, authenticated, service_role;

DROP FUNCTION IF EXISTS public.rpc_cancel_match(BIGINT, BIGINT, BIGINT);
CREATE OR REPLACE FUNCTION public.rpc_cancel_match(
    p_match_id BIGINT,
    p_actor_user_id BIGINT,
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
    v_match public.matches%ROWTYPE;
    v_counterpart_id BIGINT;
BEGIN
    v_session_user_id := public.current_session_user_id();
    IF v_session_user_id IS NULL OR v_session_user_id <> p_actor_user_id THEN
        RETURN jsonb_build_object('code', 'NOT_MATCH_PARTICIPANT');
    END IF;

    SELECT * INTO v_match FROM public.matches WHERE id = p_match_id FOR UPDATE;
    IF NOT FOUND THEN
        RETURN jsonb_build_object('code', 'MATCH_NOT_FOUND');
    END IF;

    IF v_session_user_id <> v_match."user1Id" AND v_session_user_id <> v_match."user2Id" THEN
        RETURN jsonb_build_object('code', 'NOT_MATCH_PARTICIPANT');
    END IF;

    IF v_match.status <> 'ACTIVE' THEN
        RETURN jsonb_build_object('code', 'MATCH_NOT_ACTIVE', 'currentStatus', v_match.status);
    END IF;

    v_now_ms := (EXTRACT(EPOCH FROM pg_catalog.now()) * 1000)::BIGINT;

    UPDATE public.matches
    SET status = 'CANCELLED', "updatedAt" = v_now_ms
    WHERE id = p_match_id;

    v_counterpart_id := CASE WHEN v_session_user_id = v_match."user1Id" THEN v_match."user2Id" ELSE v_match."user1Id" END;

    INSERT INTO public.notifications ("userId", type, title, body, "relatedInterestId", "relatedMatchId", "isRead", "createdAt")
    VALUES (
        v_counterpart_id,
        'MATCH_CANCELLED',
        'Match dibatalkan',
        'Rekan barter membatalkan Match.',
        v_match."sourceInterestId",
        p_match_id,
        FALSE,
        v_now_ms
    );

    RETURN jsonb_build_object(
        'code', 'SUCCESS',
        'user1Id', v_match."user1Id",
        'user2Id', v_match."user2Id"
    );
END;
$$;

REVOKE ALL ON FUNCTION public.rpc_cancel_match(BIGINT, BIGINT, BIGINT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.rpc_cancel_match(BIGINT, BIGINT, BIGINT) TO anon, authenticated, service_role;

DROP FUNCTION IF EXISTS public.rpc_complete_match_exchange(BIGINT, BIGINT, TEXT, BIGINT);
CREATE OR REPLACE FUNCTION public.rpc_complete_match_exchange(
    p_match_id BIGINT,
    p_actor_user_id BIGINT,
    p_meeting_location TEXT,
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
    v_clean_location TEXT;
    v_match public.matches%ROWTYPE;
    v_item1 public.clothing_items%ROWTYPE;
    v_item2 public.clothing_items%ROWTYPE;
    v_counterpart_id BIGINT;
    v_invalidated_interest_ids BIGINT[];
BEGIN
    v_session_user_id := public.current_session_user_id();
    IF v_session_user_id IS NULL OR v_session_user_id <> p_actor_user_id THEN
        RETURN jsonb_build_object('code', 'NOT_MATCH_PARTICIPANT');
    END IF;

    SELECT * INTO v_match FROM public.matches WHERE id = p_match_id FOR UPDATE;
    IF NOT FOUND THEN
        RETURN jsonb_build_object('code', 'MATCH_NOT_FOUND');
    END IF;

    IF v_session_user_id <> v_match."user1Id" AND v_session_user_id <> v_match."user2Id" THEN
        RETURN jsonb_build_object('code', 'NOT_MATCH_PARTICIPANT');
    END IF;

    IF v_match.status <> 'ACTIVE' THEN
        RETURN jsonb_build_object('code', 'MATCH_NOT_ACTIVE', 'currentStatus', v_match.status);
    END IF;

    SELECT * INTO v_item1 FROM public.clothing_items WHERE id = v_match."user1ItemId" FOR UPDATE;
    SELECT * INTO v_item2 FROM public.clothing_items WHERE id = v_match."user2ItemId" FOR UPDATE;

    IF v_item1.id IS NULL OR v_item2.id IS NULL OR v_item1.status <> 'AVAILABLE' OR v_item2.status <> 'AVAILABLE' THEN
        RETURN jsonb_build_object('code', 'ITEMS_NO_LONGER_AVAILABLE');
    END IF;

    v_now_ms := (EXTRACT(EPOCH FROM pg_catalog.now()) * 1000)::BIGINT;
    v_clean_location := NULLIF(LEFT(BTRIM(COALESCE(p_meeting_location, '')), 255), '');

    UPDATE public.matches
    SET status = 'COMPLETED',
        "meetingLocation" = v_clean_location,
        "updatedAt" = v_now_ms
    WHERE id = p_match_id;

    UPDATE public.clothing_items
    SET status = 'EXCHANGED', "updatedAt" = v_now_ms
    WHERE id IN (v_match."user1ItemId", v_match."user2ItemId");

    WITH invalidated AS (
        UPDATE public.interests
        SET status = 'INVALIDATED', "respondedAt" = v_now_ms
        WHERE "targetItemId" IN (v_match."user1ItemId", v_match."user2ItemId")
          AND status = 'PENDING'
        RETURNING id
    )
    SELECT COALESCE(ARRAY_AGG(id), ARRAY[]::BIGINT[])
    INTO v_invalidated_interest_ids
    FROM invalidated;

    UPDATE public.notifications
    SET "isRead" = TRUE
    WHERE type = 'INCOMING_INTEREST'
      AND "isRead" = FALSE
      AND (
          "relatedInterestId" = v_match."sourceInterestId"
          OR "relatedInterestId" = ANY(v_invalidated_interest_ids)
      );

    v_counterpart_id := CASE WHEN v_session_user_id = v_match."user1Id" THEN v_match."user2Id" ELSE v_match."user1Id" END;

    INSERT INTO public.notifications ("userId", type, title, body, "relatedInterestId", "relatedMatchId", "isRead", "createdAt")
    VALUES (
        v_counterpart_id,
        'EXCHANGE_COMPLETED',
        'Pertukaran selesai',
        'Pasangan pakaian telah ditandai sudah ditukar. Berikan rating & ulasan.',
        v_match."sourceInterestId",
        p_match_id,
        FALSE,
        v_now_ms
    );

    RETURN jsonb_build_object(
        'code', 'SUCCESS',
        'user1Id', v_match."user1Id",
        'user2Id', v_match."user2Id"
    );
END;
$$;

REVOKE ALL ON FUNCTION public.rpc_complete_match_exchange(BIGINT, BIGINT, TEXT, BIGINT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.rpc_complete_match_exchange(BIGINT, BIGINT, TEXT, BIGINT) TO anon, authenticated, service_role;

DROP FUNCTION IF EXISTS public.rpc_decline_incoming_interest(BIGINT, BIGINT, BIGINT);
CREATE OR REPLACE FUNCTION public.rpc_decline_incoming_interest(
    p_actor_user_id BIGINT,
    p_interest_id BIGINT,
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
    v_interest public.interests%ROWTYPE;
    v_actor_name TEXT;
    v_target_item_name TEXT;
BEGIN
    v_session_user_id := public.current_session_user_id();
    IF v_session_user_id IS NULL OR v_session_user_id <> p_actor_user_id THEN
        RETURN jsonb_build_object('code', 'INTEREST_ALREADY_PROCESSED_OR_INVALID');
    END IF;

    SELECT * INTO v_interest FROM public.interests WHERE id = p_interest_id FOR UPDATE;
    IF NOT FOUND OR v_interest."receiverUserId" <> v_session_user_id OR v_interest.status <> 'PENDING' THEN
        RETURN jsonb_build_object('code', 'INTEREST_ALREADY_PROCESSED_OR_INVALID');
    END IF;

    v_now_ms := (EXTRACT(EPOCH FROM pg_catalog.now()) * 1000)::BIGINT;

    UPDATE public.interests
    SET status = 'DECLINED',
        "respondedAt" = v_now_ms
    WHERE id = p_interest_id;

    UPDATE public.notifications
    SET "isRead" = TRUE
    WHERE "relatedInterestId" = p_interest_id
      AND type = 'INCOMING_INTEREST'
      AND "userId" = v_session_user_id;

    SELECT COALESCE(NULLIF(BTRIM(name), ''), 'Pemilik pakaian')
    INTO v_actor_name
    FROM public.users
    WHERE id = v_session_user_id;

    SELECT COALESCE(NULLIF(BTRIM(name), ''), 'pakaian')
    INTO v_target_item_name
    FROM public.clothing_items
    WHERE id = v_interest."targetItemId";

    INSERT INTO public.notifications ("userId", type, title, body, "relatedInterestId", "relatedMatchId", "isRead", "createdAt")
    VALUES (
        v_interest."senderUserId",
        'MATCH_CANCELLED',
        'Tawaran barter ditolak',
        COALESCE(v_actor_name, 'Pemilik pakaian') || ' menolak tawaran barter Anda untuk ' || COALESCE(v_target_item_name, 'pakaian') || '.',
        p_interest_id,
        NULL,
        FALSE,
        v_now_ms
    );

    RETURN jsonb_build_object(
        'code', 'SUCCESS',
        'senderUserId', v_interest."senderUserId"
    );
END;
$$;

REVOKE ALL ON FUNCTION public.rpc_decline_incoming_interest(BIGINT, BIGINT, BIGINT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.rpc_decline_incoming_interest(BIGINT, BIGINT, BIGINT) TO anon, authenticated, service_role;

DROP FUNCTION IF EXISTS public.rpc_respond_to_interest_and_create_match(BIGINT, BIGINT, BIGINT, BIGINT);
CREATE OR REPLACE FUNCTION public.rpc_respond_to_interest_and_create_match(
    p_actor_user_id BIGINT,
    p_interest_id BIGINT,
    p_chosen_sender_item_id BIGINT,
    p_now_millis BIGINT DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_constraint_name TEXT;
    v_session_user_id BIGINT;
    v_now_ms BIGINT;
    v_interest public.interests%ROWTYPE;
    v_target_item public.clothing_items%ROWTYPE;
    v_chosen_item public.clothing_items%ROWTYPE;
    v_active_either_count INTEGER;
    v_pair_history_count INTEGER;
    v_actor_name TEXT;
    v_sender_name TEXT;
    v_match_id BIGINT;
BEGIN
    v_session_user_id := public.current_session_user_id();
    IF v_session_user_id IS NULL OR v_session_user_id <> p_actor_user_id THEN
        RETURN jsonb_build_object('code', 'INVALID_ITEM_OWNERSHIP');
    END IF;

    v_now_ms := (EXTRACT(EPOCH FROM pg_catalog.now()) * 1000)::BIGINT;

    SELECT * INTO v_interest FROM public.interests WHERE id = p_interest_id FOR UPDATE;
    IF NOT FOUND OR v_interest."receiverUserId" <> v_session_user_id OR v_interest.status <> 'PENDING' THEN
        RETURN jsonb_build_object('code', 'INTEREST_ALREADY_PROCESSED_OR_INVALID');
    END IF;

    SELECT * INTO v_target_item FROM public.clothing_items WHERE id = v_interest."targetItemId" FOR UPDATE;
    IF NOT FOUND THEN
        UPDATE public.interests SET status = 'INVALIDATED', "respondedAt" = v_now_ms WHERE id = v_interest.id;
        RETURN jsonb_build_object('code', 'TARGET_ITEM_NOT_FOUND', 'invalidated', TRUE);
    END IF;

    IF v_target_item."ownerId" <> v_session_user_id OR v_session_user_id = v_interest."senderUserId" THEN
        RETURN jsonb_build_object('code', 'INVALID_ITEM_OWNERSHIP');
    END IF;

    IF v_target_item.status <> 'AVAILABLE' THEN
        UPDATE public.interests SET status = 'INVALIDATED', "respondedAt" = v_now_ms WHERE id = v_interest.id;
        RETURN jsonb_build_object('code', 'TARGET_ITEM_UNAVAILABLE', 'invalidated', TRUE);
    END IF;

    SELECT * INTO v_chosen_item FROM public.clothing_items WHERE id = p_chosen_sender_item_id FOR UPDATE;
    IF NOT FOUND THEN
        RETURN jsonb_build_object('code', 'CHOSEN_ITEM_NOT_FOUND');
    END IF;

    IF v_chosen_item."ownerId" <> v_interest."senderUserId" THEN
        RETURN jsonb_build_object('code', 'INVALID_ITEM_OWNERSHIP');
    END IF;

    IF v_chosen_item.status <> 'AVAILABLE' THEN
        RETURN jsonb_build_object('code', 'CHOSEN_ITEM_UNAVAILABLE');
    END IF;

    IF NOT v_target_item."isBarterAllowed" OR NOT v_chosen_item."isBarterAllowed" THEN
        RETURN jsonb_build_object('code', 'BARTER_NOT_ALLOWED');
    END IF;

    SELECT COUNT(*) INTO v_active_either_count
    FROM public.matches
    WHERE status = 'ACTIVE'
      AND ("user1ItemId" IN (v_target_item.id, v_chosen_item.id) OR "user2ItemId" IN (v_target_item.id, v_chosen_item.id));

    IF v_active_either_count > 0 THEN
        RETURN jsonb_build_object('code', 'ITEM_ALREADY_IN_ACTIVE_MATCH');
    END IF;

    SELECT COUNT(*) INTO v_pair_history_count
    FROM public.matches
    WHERE status IN ('ACTIVE', 'COMPLETED')
      AND (("user1ItemId" = v_target_item.id AND "user2ItemId" = v_chosen_item.id)
        OR ("user1ItemId" = v_chosen_item.id AND "user2ItemId" = v_target_item.id));

    IF v_pair_history_count > 0 THEN
        RETURN jsonb_build_object('code', 'PAIR_ALREADY_MATCHED');
    END IF;

    SELECT COALESCE(name, '') INTO v_actor_name FROM public.users WHERE id = v_session_user_id;
    SELECT COALESCE(name, '') INTO v_sender_name FROM public.users WHERE id = v_interest."senderUserId";

    INSERT INTO public.matches (
        "sourceInterestId", "user1Id", "user1ItemId", "user2Id", "user2ItemId",
        status, "meetingLocation", "createdAt", "updatedAt"
    )
    VALUES (
        v_interest.id, v_session_user_id, v_target_item.id, v_interest."senderUserId", v_chosen_item.id,
        'ACTIVE', NULL, v_now_ms, v_now_ms
    )
    RETURNING id INTO v_match_id;

    UPDATE public.interests
    SET status = 'ACCEPTED_MATCHED', "respondedAt" = v_now_ms
    WHERE id = v_interest.id;

    UPDATE public.interests
    SET status = 'RESOLVED_BY_MATCH', "respondedAt" = v_now_ms
    WHERE "senderUserId" = v_session_user_id
      AND "receiverUserId" = v_interest."senderUserId"
      AND "targetItemId" = v_chosen_item.id
      AND status = 'PENDING';

    INSERT INTO public.notifications ("userId", type, title, body, "relatedInterestId", "relatedMatchId", "isRead", "createdAt")
    VALUES
    (
        v_interest."senderUserId",
        'MATCH_VERIFIED',
        'Match terverifikasi dengan ' || v_actor_name,
        v_target_item.name || ' - ' || v_chosen_item.name || ' siap didiskusikan di ruang Chat.',
        v_interest.id,
        v_match_id,
        FALSE,
        v_now_ms
    ),
    (
        v_session_user_id,
        'MATCH_VERIFIED',
        'Match terverifikasi dengan ' || v_sender_name,
        v_target_item.name || ' - ' || v_chosen_item.name || ' siap didiskusikan di ruang Chat.',
        v_interest.id,
        v_match_id,
        FALSE,
        v_now_ms
    );

    RETURN jsonb_build_object(
        'code', 'SUCCESS',
        'matchId', v_match_id,
        'user1Id', v_session_user_id,
        'user2Id', v_interest."senderUserId"
    );
EXCEPTION WHEN exclusion_violation THEN
    GET STACKED DIAGNOSTICS v_constraint_name = CONSTRAINT_NAME;
    IF v_constraint_name <> 'matches_active_items_excl' THEN
        RAISE;
    END IF;
    RETURN jsonb_build_object('code', 'ITEM_ALREADY_IN_ACTIVE_MATCH');
END;
$$;

REVOKE ALL ON FUNCTION public.rpc_respond_to_interest_and_create_match(BIGINT, BIGINT, BIGINT, BIGINT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.rpc_respond_to_interest_and_create_match(BIGINT, BIGINT, BIGINT, BIGINT) TO anon, authenticated, service_role;

DROP FUNCTION IF EXISTS public.rpc_send_interest(BIGINT, BIGINT, BIGINT, BOOLEAN);
CREATE OR REPLACE FUNCTION public.rpc_send_interest(
    p_sender_user_id BIGINT,
    p_target_item_id BIGINT,
    p_now_millis BIGINT DEFAULT NULL,
    p_delete_swipe_pass BOOLEAN DEFAULT FALSE
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_session_user_id BIGINT;
    v_now_ms BIGINT;
    v_sender public.users%ROWTYPE;
    v_target public.clothing_items%ROWTYPE;
    v_existing public.interests%ROWTYPE;
    v_non_cancelled_match_count INTEGER;
    v_eligible_count INTEGER;
    v_active_match_count INTEGER;
    v_interest_id BIGINT;
BEGIN
    v_session_user_id := public.current_session_user_id();
    IF v_session_user_id IS NULL OR v_session_user_id <> p_sender_user_id THEN
        RETURN jsonb_build_object('code', 'SENDER_NOT_FOUND');
    END IF;

    SELECT * INTO v_sender FROM public.users WHERE id = v_session_user_id;
    IF NOT FOUND THEN
        RETURN jsonb_build_object('code', 'SENDER_NOT_FOUND');
    END IF;

    SELECT * INTO v_target FROM public.clothing_items WHERE id = p_target_item_id FOR UPDATE;
    IF NOT FOUND THEN
        RETURN jsonb_build_object('code', 'TARGET_ITEM_NOT_FOUND');
    END IF;

    IF v_target."ownerId" = v_session_user_id THEN
        RETURN jsonb_build_object('code', 'CANNOT_INTEREST_OWN_ITEM');
    END IF;

    FOR v_existing IN
        SELECT *
        FROM public.interests
        WHERE "senderUserId" = v_session_user_id AND "targetItemId" = p_target_item_id
        ORDER BY "createdAt" DESC
    LOOP
        IF v_existing.status IN ('PENDING', 'DECLINED') THEN
            RETURN jsonb_build_object('code', 'ALREADY_INTERESTED', 'existingStatus', v_existing.status);
        ELSIF v_existing.status IN ('ACCEPTED_MATCHED', 'RESOLVED_BY_MATCH') THEN
            SELECT COUNT(*) INTO v_non_cancelled_match_count
            FROM public.matches m
            WHERE m.status IN ('ACTIVE', 'COMPLETED')
              AND (
                  m."sourceInterestId" = v_existing.id
                  OR (
                      (m."user1ItemId" = p_target_item_id OR m."user2ItemId" = p_target_item_id)
                      AND (m."user1Id" = v_session_user_id OR m."user2Id" = v_session_user_id)
                  )
              );

            IF v_non_cancelled_match_count > 0 THEN
                RETURN jsonb_build_object('code', 'ALREADY_INTERESTED', 'existingStatus', v_existing.status);
            END IF;
        END IF;
    END LOOP;

    SELECT COUNT(*) INTO v_eligible_count
    FROM public.clothing_items c
    WHERE c."ownerId" = v_session_user_id
      AND c.status = 'AVAILABLE'
      AND c."isBarterAllowed" = TRUE
      AND c.id NOT IN (
          SELECT "user1ItemId" FROM public.matches WHERE status = 'ACTIVE'
          UNION
          SELECT "user2ItemId" FROM public.matches WHERE status = 'ACTIVE'
      );

    IF v_eligible_count = 0 THEN
        RETURN jsonb_build_object('code', 'REQUIRES_BARTERABLE_ITEM');
    END IF;

    SELECT COUNT(*) INTO v_active_match_count
    FROM public.matches
    WHERE status = 'ACTIVE' AND ("user1ItemId" = p_target_item_id OR "user2ItemId" = p_target_item_id);

    IF v_target.status <> 'AVAILABLE' OR NOT v_target."isBarterAllowed" OR v_active_match_count > 0 THEN
        RETURN jsonb_build_object('code', 'TARGET_ITEM_NOT_ELIGIBLE');
    END IF;

    v_now_ms := (EXTRACT(EPOCH FROM pg_catalog.now()) * 1000)::BIGINT;

    INSERT INTO public.interests ("senderUserId", "receiverUserId", "targetItemId", status, "createdAt", "respondedAt")
    VALUES (v_session_user_id, v_target."ownerId", p_target_item_id, 'PENDING', v_now_ms, NULL)
    RETURNING id INTO v_interest_id;

    INSERT INTO public.notifications ("userId", type, title, body, "relatedInterestId", "relatedMatchId", "isRead", "createdAt")
    VALUES (
        v_target."ownerId",
        'INCOMING_INTEREST',
        v_sender.name || ' tertarik pada ' || v_target.name,
        'Buka lemari ' || v_sender.name || ' dan pilih tepat satu pakaian berizin barter.',
        v_interest_id,
        NULL,
        FALSE,
        v_now_ms
    );

    IF p_delete_swipe_pass THEN
        DELETE FROM public.swipe_passes
        WHERE "userId" = v_session_user_id AND "targetItemId" = p_target_item_id;
    END IF;

    RETURN jsonb_build_object(
        'code', 'SUCCESS',
        'interestId', v_interest_id,
        'isMutualMatch', FALSE,
        'receiverUserId', v_target."ownerId"
    );
END;
$$;

REVOKE ALL ON FUNCTION public.rpc_send_interest(BIGINT, BIGINT, BIGINT, BOOLEAN) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.rpc_send_interest(BIGINT, BIGINT, BIGINT, BOOLEAN) TO anon, authenticated, service_role;

DROP FUNCTION IF EXISTS public.rpc_set_barter_allowed_protected(BIGINT, BIGINT, BOOLEAN, BIGINT);
CREATE OR REPLACE FUNCTION public.rpc_set_barter_allowed_protected(
    p_actor_user_id BIGINT,
    p_item_id BIGINT,
    p_allowed BOOLEAN,
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
    v_item public.clothing_items%ROWTYPE;
    v_active_match_count INTEGER;
BEGIN
    v_session_user_id := public.current_session_user_id();
    IF v_session_user_id IS NULL OR v_session_user_id <> p_actor_user_id THEN
        RETURN jsonb_build_object('code', 'INVALID_OWNERSHIP');
    END IF;

    SELECT * INTO v_item FROM public.clothing_items WHERE id = p_item_id FOR UPDATE;
    IF NOT FOUND THEN
        RETURN jsonb_build_object('code', 'ITEM_NOT_FOUND');
    END IF;

    IF v_item."ownerId" <> v_session_user_id THEN
        RETURN jsonb_build_object('code', 'INVALID_OWNERSHIP');
    END IF;

    IF v_item.status <> 'AVAILABLE' THEN
        RETURN jsonb_build_object('code', 'LOCKED_IN_ACTIVE_MATCH');
    END IF;

    SELECT COUNT(*) INTO v_active_match_count
    FROM public.matches
    WHERE status = 'ACTIVE' AND ("user1ItemId" = p_item_id OR "user2ItemId" = p_item_id);

    IF v_active_match_count > 0 THEN
        RETURN jsonb_build_object('code', 'LOCKED_IN_ACTIVE_MATCH');
    END IF;

    v_now_ms := (EXTRACT(EPOCH FROM pg_catalog.now()) * 1000)::BIGINT;

    UPDATE public.clothing_items
    SET "isBarterAllowed" = COALESCE(p_allowed, FALSE),
        "updatedAt" = v_now_ms
    WHERE id = p_item_id;

    IF v_item."isBarterAllowed" = TRUE AND COALESCE(p_allowed, FALSE) = FALSE THEN
        DELETE FROM public.notifications
        WHERE type = 'INCOMING_INTEREST'
          AND "relatedInterestId" IN (
              SELECT id FROM public.interests
              WHERE "targetItemId" = p_item_id AND status = 'PENDING'
          );

        UPDATE public.interests
        SET status = 'INVALIDATED', "respondedAt" = v_now_ms
        WHERE "targetItemId" = p_item_id AND status = 'PENDING';
    END IF;

    RETURN jsonb_build_object('code', 'SUCCESS', 'itemId', p_item_id);
END;
$$;

REVOKE ALL ON FUNCTION public.rpc_set_barter_allowed_protected(BIGINT, BIGINT, BOOLEAN, BIGINT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.rpc_set_barter_allowed_protected(BIGINT, BIGINT, BOOLEAN, BIGINT) TO anon, authenticated, service_role;

DROP FUNCTION IF EXISTS public.rpc_soft_delete_clothing_protected(BIGINT, BIGINT, BIGINT);
CREATE OR REPLACE FUNCTION public.rpc_soft_delete_clothing_protected(
    p_item_id BIGINT,
    p_actor_user_id BIGINT,
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
    v_existing public.clothing_items%ROWTYPE;
    v_active_match_count INTEGER;
    v_invalidated_interest_ids BIGINT[];
BEGIN
    v_session_user_id := public.current_session_user_id();
    IF v_session_user_id IS NULL OR v_session_user_id <> p_actor_user_id THEN
        RETURN jsonb_build_object('code', 'INVALID_OWNERSHIP');
    END IF;

    SELECT * INTO v_existing FROM public.clothing_items WHERE id = p_item_id FOR UPDATE;
    IF NOT FOUND THEN
        RETURN jsonb_build_object('code', 'ITEM_NOT_FOUND');
    END IF;

    IF v_existing."ownerId" <> v_session_user_id THEN
        RETURN jsonb_build_object('code', 'INVALID_OWNERSHIP');
    END IF;

    SELECT COUNT(*) INTO v_active_match_count
    FROM public.matches
    WHERE status = 'ACTIVE' AND ("user1ItemId" = p_item_id OR "user2ItemId" = p_item_id);

    IF v_active_match_count > 0 THEN
        RETURN jsonb_build_object('code', 'LOCKED_IN_ACTIVE_MATCH');
    END IF;

    v_now_ms := (EXTRACT(EPOCH FROM pg_catalog.now()) * 1000)::BIGINT;

    UPDATE public.clothing_items
    SET status = 'HIDDEN_DELETED',
        "isBarterAllowed" = FALSE,
        "updatedAt" = v_now_ms
    WHERE id = p_item_id;

    WITH invalidated AS (
        UPDATE public.interests
        SET status = 'INVALIDATED', "respondedAt" = v_now_ms
        WHERE "targetItemId" = p_item_id AND status = 'PENDING'
        RETURNING id
    )
    SELECT COALESCE(ARRAY_AGG(id), ARRAY[]::BIGINT[])
    INTO v_invalidated_interest_ids
    FROM invalidated;

    IF CARDINALITY(v_invalidated_interest_ids) > 0 THEN
        UPDATE public.notifications
        SET "isRead" = TRUE
        WHERE type = 'INCOMING_INTEREST'
          AND "isRead" = FALSE
          AND "relatedInterestId" = ANY(v_invalidated_interest_ids);
    END IF;

    RETURN jsonb_build_object('code', 'SUCCESS', 'itemId', p_item_id);
END;
$$;

REVOKE ALL ON FUNCTION public.rpc_soft_delete_clothing_protected(BIGINT, BIGINT, BIGINT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.rpc_soft_delete_clothing_protected(BIGINT, BIGINT, BIGINT) TO anon, authenticated, service_role;

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
    v_existing_count INTEGER;
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

    SELECT * INTO v_match FROM public.matches WHERE id = p_match_id;
    IF NOT FOUND THEN
        RETURN jsonb_build_object('code', 'MATCH_NOT_FOUND');
    END IF;

    IF v_session_user_id <> v_match."user1Id" AND v_session_user_id <> v_match."user2Id" THEN
        RETURN jsonb_build_object('code', 'NOT_MATCH_PARTICIPANT');
    END IF;

    IF v_match.status <> 'COMPLETED' THEN
        RETURN jsonb_build_object('code', 'MATCH_NOT_COMPLETED');
    END IF;

    SELECT COUNT(*) INTO v_existing_count
    FROM public.reviews
    WHERE "matchId" = p_match_id AND "reviewerId" = v_session_user_id;

    IF v_existing_count > 0 THEN
        RETURN jsonb_build_object('code', 'ALREADY_REVIEWED');
    END IF;

    v_now_ms := (EXTRACT(EPOCH FROM pg_catalog.now()) * 1000)::BIGINT;
    v_reviewee_id := CASE WHEN v_session_user_id = v_match."user1Id" THEN v_match."user2Id" ELSE v_match."user1Id" END;

    INSERT INTO public.reviews ("matchId", "reviewerId", "revieweeId", rating, comment, "createdAt")
    VALUES (p_match_id, v_session_user_id, v_reviewee_id, p_rating, v_clean_comment, v_now_ms)
    RETURNING id INTO v_review_id;

    RETURN jsonb_build_object('code', 'SUCCESS', 'reviewId', v_review_id);
END;
$$;

REVOKE ALL ON FUNCTION public.rpc_submit_review_protected(BIGINT, BIGINT, INTEGER, TEXT, BIGINT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.rpc_submit_review_protected(BIGINT, BIGINT, INTEGER, TEXT, BIGINT) TO anon, authenticated, service_role;

DROP FUNCTION IF EXISTS public.rpc_update_clothing_protected(
    BIGINT, BIGINT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, BOOLEAN, BIGINT
);
CREATE OR REPLACE FUNCTION public.rpc_update_clothing_protected(
    p_actor_user_id BIGINT,
    p_item_id BIGINT,
    p_name TEXT,
    p_description TEXT,
    p_category TEXT,
    p_size TEXT,
    p_gender TEXT,
    p_color TEXT,
    p_condition TEXT,
    p_main_photo_uri TEXT,
    p_detail_photo_uri TEXT,
    p_extra_photo_uri TEXT,
    p_is_barter_allowed BOOLEAN,
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
    v_item public.clothing_items%ROWTYPE;
    v_clean_name TEXT;
    v_clean_desc TEXT;
    v_clean_category TEXT;
    v_clean_size TEXT;
    v_clean_gender TEXT;
    v_clean_color TEXT;
    v_clean_condition TEXT;
    v_clean_main_photo TEXT;
    v_clean_detail_photo TEXT;
    v_clean_extra_photo TEXT;
    v_active_match_count INTEGER;
BEGIN
    v_session_user_id := public.current_session_user_id();
    IF v_session_user_id IS NULL OR v_session_user_id <> p_actor_user_id THEN
        RETURN jsonb_build_object('code', 'INVALID_OWNERSHIP');
    END IF;

    SELECT * INTO v_item FROM public.clothing_items WHERE id = p_item_id FOR UPDATE;
    IF NOT FOUND THEN
        RETURN jsonb_build_object('code', 'ITEM_NOT_FOUND');
    END IF;

    IF v_item."ownerId" <> v_session_user_id THEN
        RETURN jsonb_build_object('code', 'INVALID_OWNERSHIP');
    END IF;

    IF v_item.status <> 'AVAILABLE' THEN
        RETURN jsonb_build_object('code', 'LOCKED_IN_ACTIVE_MATCH');
    END IF;

    SELECT COUNT(*) INTO v_active_match_count
    FROM public.matches
    WHERE status = 'ACTIVE' AND ("user1ItemId" = p_item_id OR "user2ItemId" = p_item_id);

    IF v_active_match_count > 0 THEN
        RETURN jsonb_build_object('code', 'LOCKED_IN_ACTIVE_MATCH');
    END IF;

    v_clean_name := BTRIM(COALESCE(p_name, ''));
    v_clean_desc := BTRIM(COALESCE(p_description, ''));
    v_clean_category := BTRIM(COALESCE(p_category, ''));
    v_clean_size := BTRIM(COALESCE(p_size, ''));
    v_clean_gender := BTRIM(COALESCE(p_gender, ''));
    v_clean_color := BTRIM(COALESCE(p_color, ''));
    v_clean_condition := BTRIM(COALESCE(p_condition, ''));
    v_clean_main_photo := NULLIF(BTRIM(COALESCE(p_main_photo_uri, '')), '');
    v_clean_detail_photo := NULLIF(BTRIM(COALESCE(p_detail_photo_uri, '')), '');
    v_clean_extra_photo := NULLIF(BTRIM(COALESCE(p_extra_photo_uri, '')), '');

    IF v_clean_name = '' OR LENGTH(v_clean_name) > 120 THEN
        RETURN jsonb_build_object('code', 'VALIDATION_FAILED', 'message', 'Nama pakaian tidak valid');
    END IF;

    IF LENGTH(v_clean_desc) > 2000 THEN
        RETURN jsonb_build_object('code', 'VALIDATION_FAILED', 'message', 'Deskripsi pakaian terlalu panjang');
    END IF;

    IF v_clean_category = '' OR LENGTH(v_clean_category) > 60
       OR v_clean_size = '' OR LENGTH(v_clean_size) > 60
       OR v_clean_gender = '' OR LENGTH(v_clean_gender) > 60
       OR v_clean_color = '' OR LENGTH(v_clean_color) > 60
       OR v_clean_condition = '' OR LENGTH(v_clean_condition) > 60 THEN
        RETURN jsonb_build_object('code', 'VALIDATION_FAILED', 'message', 'Atribut pakaian tidak valid');
    END IF;

    IF NOT public.is_valid_storage_photo_uri(v_clean_main_photo)
       OR NOT public.is_valid_storage_photo_uri(v_clean_detail_photo)
       OR NOT public.is_valid_storage_photo_uri(v_clean_extra_photo) THEN
        RETURN jsonb_build_object('code', 'VALIDATION_FAILED', 'message', 'URL foto pakaian tidak valid');
    END IF;

    v_now_ms := (EXTRACT(EPOCH FROM pg_catalog.now()) * 1000)::BIGINT;

    UPDATE public.clothing_items
    SET name = v_clean_name,
        description = v_clean_desc,
        category = v_clean_category,
        size = v_clean_size,
        gender = v_clean_gender,
        color = v_clean_color,
        condition = v_clean_condition,
        "mainPhotoUri" = v_clean_main_photo,
        "detailPhotoUri" = v_clean_detail_photo,
        "extraPhotoUri" = v_clean_extra_photo,
        "isBarterAllowed" = COALESCE(p_is_barter_allowed, FALSE),
        "updatedAt" = v_now_ms
    WHERE id = p_item_id;

    IF v_item."isBarterAllowed" = TRUE AND COALESCE(p_is_barter_allowed, FALSE) = FALSE THEN
        DELETE FROM public.notifications
        WHERE type = 'INCOMING_INTEREST'
          AND "relatedInterestId" IN (
              SELECT id FROM public.interests
              WHERE "targetItemId" = p_item_id AND status = 'PENDING'
          );

        UPDATE public.interests
        SET status = 'INVALIDATED', "respondedAt" = v_now_ms
        WHERE "targetItemId" = p_item_id AND status = 'PENDING';
    END IF;

    RETURN jsonb_build_object('code', 'SUCCESS', 'itemId', p_item_id);
END;
$$;

REVOKE ALL ON FUNCTION public.rpc_update_clothing_protected(
    BIGINT, BIGINT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, BOOLEAN, BIGINT
) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.rpc_update_clothing_protected(
    BIGINT, BIGINT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, BOOLEAN, BIGINT
) TO anon, authenticated, service_role;

ALTER FUNCTION public.rpc_cancel_outgoing_interest(BIGINT, BIGINT) SET search_path = '';
ALTER FUNCTION public.rpc_get_login_challenge(TEXT) SET search_path = '';
ALTER FUNCTION public.rpc_create_session(TEXT, TEXT) SET search_path = '';
ALTER FUNCTION public.rpc_register_user(TEXT, TEXT, TEXT, TEXT, DOUBLE PRECISION, DOUBLE PRECISION, BIGINT) SET search_path = '';
ALTER FUNCTION public.rpc_change_password_authenticated(TEXT, TEXT, BIGINT) SET search_path = '';
ALTER FUNCTION public.rpc_issue_password_reset_otp(TEXT, TEXT, BIGINT) SET search_path = '';
ALTER FUNCTION public.rpc_verify_otp_and_reset_password(TEXT, TEXT, TEXT, BIGINT) SET search_path = '';
ALTER FUNCTION public.rpc_logout_session() SET search_path = '';
ALTER FUNCTION public.rpc_validate_session() SET search_path = '';
ALTER FUNCTION public.rpc_register_fcm_token(TEXT, TEXT) SET search_path = '';
ALTER FUNCTION public.rpc_unregister_fcm_token(TEXT) SET search_path = '';

ALTER FUNCTION public.trg_sanitize_chat_messages_write() SET search_path = '';
ALTER FUNCTION public.trg_sanitize_clothing_items_insert() SET search_path = '';
ALTER FUNCTION public.trg_sanitize_matches_update() SET search_path = '';
ALTER FUNCTION public.trg_sanitize_swipe_passes_insert() SET search_path = '';
ALTER FUNCTION public.trg_sanitize_users_update() SET search_path = '';

REVOKE ALL ON FUNCTION public.trg_sanitize_chat_messages_write() FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.trg_sanitize_clothing_items_insert() FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.trg_sanitize_matches_update() FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.trg_sanitize_swipe_passes_insert() FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.trg_sanitize_users_update() FROM PUBLIC, anon, authenticated;

-- ============================================================================
-- New Wardrobe RPCs
-- ============================================================================

CREATE OR REPLACE FUNCTION public.rpc_get_my_wardrobe()
RETURNS JSONB
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_user_id BIGINT;
    v_items JSONB;
BEGIN
    v_user_id := public.current_session_user_id();
    IF v_user_id IS NULL THEN
        RETURN jsonb_build_object('code', 'UNAUTHORIZED', 'items', '[]'::jsonb);
    END IF;

    SELECT COALESCE(
        jsonb_agg(
            jsonb_build_object(
                'item', jsonb_build_object(
                    'id', c.id,
                    'ownerId', c."ownerId",
                    'name', c.name,
                    'description', c.description,
                    'category', c.category,
                    'size', c.size,
                    'gender', c.gender,
                    'color', c.color,
                    'condition', c.condition,
                    'mainPhotoUri', c."mainPhotoUri",
                    'detailPhotoUri', c."detailPhotoUri",
                    'extraPhotoUri', c."extraPhotoUri",
                    'isBarterAllowed', c."isBarterAllowed",
                    'status', c.status,
                    'createdAt', c."createdAt",
                    'updatedAt', c."updatedAt"
                ),
                'isInActiveMatch', EXISTS (
                    SELECT 1 FROM public.matches m
                    WHERE m.status = 'ACTIVE'
                      AND (m."user1ItemId" = c.id OR m."user2ItemId" = c.id)
                ),
                'interestedSenderNames', COALESCE(
                    (
                        SELECT jsonb_agg(u.name ORDER BY i."createdAt" DESC, i.id DESC)
                        FROM public.interests i
                        JOIN public.users u ON u.id = i."senderUserId"
                        WHERE i."targetItemId" = c.id
                          AND i.status = 'PENDING'
                    ),
                    '[]'::jsonb
                )
            )
            ORDER BY c."createdAt" DESC, c.id DESC
        ),
        '[]'::jsonb
    )
    INTO v_items
    FROM public.clothing_items c
    WHERE c."ownerId" = v_user_id
      AND c.status <> 'HIDDEN_DELETED';

    RETURN jsonb_build_object('code', 'SUCCESS', 'items', v_items);
END;
$$;

REVOKE ALL ON FUNCTION public.rpc_get_my_wardrobe() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.rpc_get_my_wardrobe() TO anon, authenticated, service_role;

CREATE OR REPLACE FUNCTION public.rpc_get_user_public_wardrobe(
    p_owner_id BIGINT
)
RETURNS JSONB
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_caller_id BIGINT;
    v_items JSONB;
BEGIN
    v_caller_id := public.current_session_user_id();
    IF v_caller_id IS NULL THEN
        RETURN jsonb_build_object('code', 'UNAUTHORIZED', 'items', '[]'::jsonb);
    END IF;

    SELECT COALESCE(
        jsonb_agg(
            jsonb_build_object(
                'item', jsonb_build_object(
                    'id', c.id,
                    'ownerId', c."ownerId",
                    'name', c.name,
                    'description', c.description,
                    'category', c.category,
                    'size', c.size,
                    'gender', c.gender,
                    'color', c.color,
                    'condition', c.condition,
                    'mainPhotoUri', c."mainPhotoUri",
                    'detailPhotoUri', c."detailPhotoUri",
                    'extraPhotoUri', c."extraPhotoUri",
                    'isBarterAllowed', c."isBarterAllowed",
                    'status', c.status,
                    'createdAt', c."createdAt",
                    'updatedAt', c."updatedAt"
                ),
                'isInActiveMatch', EXISTS (
                    SELECT 1 FROM public.matches m
                    WHERE m.status = 'ACTIVE'
                      AND (m."user1ItemId" = c.id OR m."user2ItemId" = c.id)
                ),
                'interestedSenderNames', '[]'::jsonb
            )
            ORDER BY c."createdAt" DESC, c.id DESC
        ),
        '[]'::jsonb
    )
    INTO v_items
    FROM public.clothing_items c
    WHERE c."ownerId" = p_owner_id
      AND c.status = 'AVAILABLE'
      AND c."isBarterAllowed" = TRUE;

    RETURN jsonb_build_object('code', 'SUCCESS', 'items', v_items);
END;
$$;

REVOKE ALL ON FUNCTION public.rpc_get_user_public_wardrobe(BIGINT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.rpc_get_user_public_wardrobe(BIGINT) TO anon, authenticated, service_role;

CREATE OR REPLACE FUNCTION public.rpc_get_clothing_item(
    p_item_id BIGINT
)
RETURNS JSONB
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_caller_id BIGINT;
    v_item public.clothing_items%ROWTYPE;
    v_is_in_active_match BOOLEAN;
    v_is_locked BOOLEAN;
    v_can_view BOOLEAN;
BEGIN
    v_caller_id := public.current_session_user_id();
    IF v_caller_id IS NULL THEN
        RETURN jsonb_build_object('code', 'UNAUTHORIZED');
    END IF;

    SELECT * INTO v_item
    FROM public.clothing_items
    WHERE id = p_item_id
    LIMIT 1;

    IF NOT FOUND THEN
        RETURN jsonb_build_object('code', 'NOT_FOUND');
    END IF;

    v_can_view := (v_item."ownerId" = v_caller_id)
        OR (
            v_item.status <> 'HIDDEN_DELETED'
            AND (
                v_item."isBarterAllowed" = TRUE
                OR EXISTS (
                    SELECT 1 FROM public.matches m
                    WHERE (m."user1ItemId" = v_item.id OR m."user2ItemId" = v_item.id)
                      AND (m."user1Id" = v_caller_id OR m."user2Id" = v_caller_id)
                )
            )
        );

    IF NOT v_can_view THEN
        RETURN jsonb_build_object('code', 'NOT_FOUND');
    END IF;

    v_is_in_active_match := EXISTS (
        SELECT 1 FROM public.matches m
        WHERE m.status = 'ACTIVE'
          AND (m."user1ItemId" = v_item.id OR m."user2ItemId" = v_item.id)
    );
    v_is_locked := (v_item.status <> 'AVAILABLE') OR v_is_in_active_match;

    RETURN jsonb_build_object(
        'code', 'SUCCESS',
        'item', jsonb_build_object(
            'id', v_item.id,
            'ownerId', v_item."ownerId",
            'name', v_item.name,
            'description', v_item.description,
            'category', v_item.category,
            'size', v_item.size,
            'gender', v_item.gender,
            'color', v_item.color,
            'condition', v_item.condition,
            'mainPhotoUri', v_item."mainPhotoUri",
            'detailPhotoUri', v_item."detailPhotoUri",
            'extraPhotoUri', v_item."extraPhotoUri",
            'isBarterAllowed', v_item."isBarterAllowed",
            'status', v_item.status,
            'createdAt', v_item."createdAt",
            'updatedAt', v_item."updatedAt"
        ),
        'isInActiveMatch', v_is_in_active_match,
        'isLocked', v_is_locked
    );
END;
$$;

REVOKE ALL ON FUNCTION public.rpc_get_clothing_item(BIGINT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.rpc_get_clothing_item(BIGINT) TO anon, authenticated, service_role;

CREATE OR REPLACE FUNCTION public.rpc_add_clothing_item(
    p_name TEXT,
    p_description TEXT,
    p_category TEXT,
    p_size TEXT,
    p_gender TEXT,
    p_color TEXT,
    p_condition TEXT,
    p_main_photo_uri TEXT,
    p_detail_photo_uri TEXT,
    p_extra_photo_uri TEXT,
    p_is_barter_allowed BOOLEAN
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_user_id BIGINT;
    v_now_ms BIGINT;
    v_clean_name TEXT;
    v_clean_desc TEXT;
    v_clean_category TEXT;
    v_clean_size TEXT;
    v_clean_gender TEXT;
    v_clean_color TEXT;
    v_clean_condition TEXT;
    v_clean_main_photo TEXT;
    v_clean_detail_photo TEXT;
    v_clean_extra_photo TEXT;
    v_item_id BIGINT;
BEGIN
    v_user_id := public.current_session_user_id();
    IF v_user_id IS NULL THEN
        RETURN jsonb_build_object('code', 'UNAUTHORIZED');
    END IF;

    v_clean_name := BTRIM(COALESCE(p_name, ''));
    v_clean_desc := BTRIM(COALESCE(p_description, ''));
    v_clean_category := BTRIM(COALESCE(p_category, ''));
    v_clean_size := BTRIM(COALESCE(p_size, ''));
    v_clean_gender := BTRIM(COALESCE(p_gender, ''));
    v_clean_color := BTRIM(COALESCE(p_color, ''));
    v_clean_condition := BTRIM(COALESCE(p_condition, ''));
    v_clean_main_photo := NULLIF(BTRIM(COALESCE(p_main_photo_uri, '')), '');
    v_clean_detail_photo := NULLIF(BTRIM(COALESCE(p_detail_photo_uri, '')), '');
    v_clean_extra_photo := NULLIF(BTRIM(COALESCE(p_extra_photo_uri, '')), '');

    IF v_clean_name = '' OR LENGTH(v_clean_name) > 120 THEN
        RETURN jsonb_build_object('code', 'VALIDATION_FAILED', 'message', 'Nama pakaian tidak boleh kosong');
    END IF;

    IF LENGTH(v_clean_desc) > 2000 THEN
        RETURN jsonb_build_object('code', 'VALIDATION_FAILED', 'message', 'Deskripsi pakaian terlalu panjang');
    END IF;

    IF v_clean_category = '' OR LENGTH(v_clean_category) > 60
       OR v_clean_size = '' OR LENGTH(v_clean_size) > 60
       OR v_clean_gender = '' OR LENGTH(v_clean_gender) > 60
       OR v_clean_color = '' OR LENGTH(v_clean_color) > 60
       OR v_clean_condition = '' OR LENGTH(v_clean_condition) > 60 THEN
        RETURN jsonb_build_object('code', 'VALIDATION_FAILED', 'message', 'Atribut pakaian tidak valid');
    END IF;

    IF NOT public.is_valid_storage_photo_uri(v_clean_main_photo)
       OR NOT public.is_valid_storage_photo_uri(v_clean_detail_photo)
       OR NOT public.is_valid_storage_photo_uri(v_clean_extra_photo) THEN
        RETURN jsonb_build_object('code', 'VALIDATION_FAILED', 'message', 'URL foto pakaian tidak valid');
    END IF;

    v_now_ms := (EXTRACT(EPOCH FROM pg_catalog.now()) * 1000)::BIGINT;

    INSERT INTO public.clothing_items (
        "ownerId", name, description, category, size, gender, color, condition,
        "mainPhotoUri", "detailPhotoUri", "extraPhotoUri", "isBarterAllowed",
        status, "createdAt", "updatedAt"
    )
    VALUES (
        v_user_id, v_clean_name, v_clean_desc, v_clean_category, v_clean_size,
        v_clean_gender, v_clean_color, v_clean_condition,
        v_clean_main_photo, v_clean_detail_photo, v_clean_extra_photo,
        COALESCE(p_is_barter_allowed, FALSE),
        'AVAILABLE', v_now_ms, v_now_ms
    )
    RETURNING id INTO v_item_id;

    RETURN jsonb_build_object('code', 'SUCCESS', 'itemId', v_item_id);
END;
$$;

REVOKE ALL ON FUNCTION public.rpc_add_clothing_item(
    TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, BOOLEAN
) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.rpc_add_clothing_item(
    TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, BOOLEAN
) TO anon, authenticated, service_role;

CREATE OR REPLACE FUNCTION public.rpc_count_eligible_barter_items(
    p_owner_id BIGINT DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_caller_id BIGINT;
    v_target_owner_id BIGINT;
    v_count INTEGER;
BEGIN
    v_caller_id := public.current_session_user_id();
    IF v_caller_id IS NULL THEN
        RETURN jsonb_build_object('code', 'UNAUTHORIZED', 'count', 0);
    END IF;

    v_target_owner_id := COALESCE(p_owner_id, v_caller_id);

    SELECT COUNT(*)::INTEGER INTO v_count
    FROM public.clothing_items c
    WHERE c."ownerId" = v_target_owner_id
      AND c.status = 'AVAILABLE'
      AND c."isBarterAllowed" = TRUE
      AND NOT EXISTS (
          SELECT 1 FROM public.matches m
          WHERE m.status = 'ACTIVE'
            AND (m."user1ItemId" = c.id OR m."user2ItemId" = c.id)
      );

    RETURN jsonb_build_object('code', 'SUCCESS', 'count', v_count);
END;
$$;

REVOKE ALL ON FUNCTION public.rpc_count_eligible_barter_items(BIGINT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.rpc_count_eligible_barter_items(BIGINT) TO anon, authenticated, service_role;

-- ============================================================================
-- New Profile & Review RPCs
-- ============================================================================

CREATE OR REPLACE FUNCTION public.rpc_get_user_reviews(
    p_user_id BIGINT
)
RETURNS JSONB
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_caller_id BIGINT;
    v_reviews JSONB;
BEGIN
    v_caller_id := public.current_session_user_id();
    IF v_caller_id IS NULL THEN
        RETURN jsonb_build_object('code', 'UNAUTHORIZED', 'reviews', '[]'::jsonb);
    END IF;

    SELECT COALESCE(
        jsonb_agg(
            jsonb_build_object(
                'id', r.id,
                'matchId', r."matchId",
                'reviewerId', r."reviewerId",
                'revieweeId', r."revieweeId",
                'rating', r.rating,
                'comment', r.comment,
                'createdAt', r."createdAt",
                'users', CASE WHEN u.id IS NULL THEN NULL ELSE jsonb_build_object(
                    'name', u.name,
                    'city', u.city
                ) END
            )
            ORDER BY r."createdAt" DESC, r.id DESC
        ),
        '[]'::jsonb
    )
    INTO v_reviews
    FROM public.reviews r
    LEFT JOIN public.users u ON u.id = r."reviewerId"
    WHERE r."revieweeId" = p_user_id;

    RETURN jsonb_build_object('code', 'SUCCESS', 'reviews', v_reviews);
END;
$$;

REVOKE ALL ON FUNCTION public.rpc_get_user_reviews(BIGINT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.rpc_get_user_reviews(BIGINT) TO anon, authenticated, service_role;

CREATE OR REPLACE FUNCTION public.rpc_has_user_reviewed_match(
    p_match_id BIGINT
)
RETURNS JSONB
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_user_id BIGINT;
    v_has_reviewed BOOLEAN;
BEGIN
    v_user_id := public.current_session_user_id();
    IF v_user_id IS NULL THEN
        RETURN jsonb_build_object('code', 'UNAUTHORIZED', 'hasReviewed', FALSE);
    END IF;

    v_has_reviewed := EXISTS (
        SELECT 1
        FROM public.reviews r
        WHERE r."matchId" = p_match_id
          AND r."reviewerId" = v_user_id
    );

    RETURN jsonb_build_object('code', 'SUCCESS', 'hasReviewed', v_has_reviewed);
END;
$$;

REVOKE ALL ON FUNCTION public.rpc_has_user_reviewed_match(BIGINT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.rpc_has_user_reviewed_match(BIGINT) TO anon, authenticated, service_role;

CREATE OR REPLACE FUNCTION public.rpc_update_preferences(
    p_preferred_size TEXT,
    p_preferred_gender TEXT,
    p_city TEXT,
    p_latitude DOUBLE PRECISION,
    p_longitude DOUBLE PRECISION,
    p_preferred_radius_km INTEGER
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_user_id BIGINT;
    v_clean_size TEXT;
    v_clean_gender TEXT;
    v_clean_city TEXT;
BEGIN
    v_user_id := public.current_session_user_id();
    IF v_user_id IS NULL THEN
        RETURN jsonb_build_object('code', 'UNAUTHORIZED');
    END IF;

    v_clean_size := BTRIM(COALESCE(p_preferred_size, ''));
    v_clean_gender := BTRIM(COALESCE(p_preferred_gender, ''));
    v_clean_city := BTRIM(COALESCE(p_city, ''));

    IF v_clean_size = '' OR LENGTH(v_clean_size) > 30
       OR v_clean_gender = '' OR LENGTH(v_clean_gender) > 30
       OR v_clean_city = '' OR LENGTH(v_clean_city) > 120 THEN
        RETURN jsonb_build_object('code', 'VALIDATION_FAILED', 'message', 'Preferensi atau lokasi tidak valid');
    END IF;

    IF p_latitude IS NULL OR p_longitude IS NULL
       OR NOT (p_latitude >= -90.0 AND p_latitude <= 90.0)
       OR NOT (p_longitude >= -180.0 AND p_longitude <= 180.0) THEN
        RETURN jsonb_build_object('code', 'VALIDATION_FAILED', 'message', 'Koordinat lokasi tidak valid');
    END IF;

    IF p_preferred_radius_km IS NULL OR p_preferred_radius_km < 1 OR p_preferred_radius_km > 20000 THEN
        RETURN jsonb_build_object('code', 'VALIDATION_FAILED', 'message', 'Radius preferensi harus 1 sampai 20000 km');
    END IF;

    UPDATE public.users
    SET "preferredSize" = v_clean_size,
        "preferredGender" = v_clean_gender,
        city = v_clean_city,
        latitude = p_latitude,
        longitude = p_longitude,
        "preferredRadiusKm" = p_preferred_radius_km
    WHERE id = v_user_id;

    RETURN jsonb_build_object('code', 'SUCCESS');
END;
$$;

REVOKE ALL ON FUNCTION public.rpc_update_preferences(TEXT, TEXT, TEXT, DOUBLE PRECISION, DOUBLE PRECISION, INTEGER) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.rpc_update_preferences(TEXT, TEXT, TEXT, DOUBLE PRECISION, DOUBLE PRECISION, INTEGER) TO anon, authenticated, service_role;

CREATE OR REPLACE FUNCTION public.rpc_update_bio(
    p_bio TEXT
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_user_id BIGINT;
    v_clean_bio TEXT;
BEGIN
    v_user_id := public.current_session_user_id();
    IF v_user_id IS NULL THEN
        RETURN jsonb_build_object('code', 'UNAUTHORIZED');
    END IF;

    v_clean_bio := BTRIM(COALESCE(p_bio, ''));
    IF LENGTH(v_clean_bio) > 1000 THEN
        RETURN jsonb_build_object('code', 'VALIDATION_FAILED', 'message', 'Bio terlalu panjang');
    END IF;

    UPDATE public.users
    SET bio = v_clean_bio
    WHERE id = v_user_id;

    RETURN jsonb_build_object('code', 'SUCCESS');
END;
$$;

REVOKE ALL ON FUNCTION public.rpc_update_bio(TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.rpc_update_bio(TEXT) TO anon, authenticated, service_role;

CREATE OR REPLACE FUNCTION public.rpc_update_profile(
    p_name TEXT,
    p_bio TEXT,
    p_avatar_uri TEXT,
    p_city TEXT,
    p_latitude DOUBLE PRECISION,
    p_longitude DOUBLE PRECISION,
    p_preferred_size TEXT,
    p_preferred_gender TEXT,
    p_preferred_radius_km INTEGER
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_user_id BIGINT;
    v_clean_name TEXT;
    v_clean_bio TEXT;
    v_clean_avatar TEXT;
    v_clean_city TEXT;
    v_clean_size TEXT;
    v_clean_gender TEXT;
BEGIN
    v_user_id := public.current_session_user_id();
    IF v_user_id IS NULL THEN
        RETURN jsonb_build_object('code', 'UNAUTHORIZED');
    END IF;

    v_clean_name := BTRIM(COALESCE(p_name, ''));
    v_clean_bio := BTRIM(COALESCE(p_bio, ''));
    v_clean_avatar := NULLIF(BTRIM(COALESCE(p_avatar_uri, '')), '');
    v_clean_city := BTRIM(COALESCE(p_city, ''));
    v_clean_size := BTRIM(COALESCE(p_preferred_size, ''));
    v_clean_gender := BTRIM(COALESCE(p_preferred_gender, ''));

    IF v_clean_name = '' OR LENGTH(v_clean_name) > 100 THEN
        RETURN jsonb_build_object('code', 'VALIDATION_FAILED', 'message', 'Nama profil tidak valid');
    END IF;

    IF LENGTH(v_clean_bio) > 1000 THEN
        RETURN jsonb_build_object('code', 'VALIDATION_FAILED', 'message', 'Bio profil terlalu panjang');
    END IF;

    IF v_clean_city = '' OR LENGTH(v_clean_city) > 120
       OR v_clean_size = '' OR LENGTH(v_clean_size) > 30
       OR v_clean_gender = '' OR LENGTH(v_clean_gender) > 30 THEN
        RETURN jsonb_build_object('code', 'VALIDATION_FAILED', 'message', 'Lokasi atau preferensi profil tidak valid');
    END IF;

    IF p_latitude IS NULL OR p_longitude IS NULL
       OR NOT (p_latitude >= -90.0 AND p_latitude <= 90.0)
       OR NOT (p_longitude >= -180.0 AND p_longitude <= 180.0) THEN
        RETURN jsonb_build_object('code', 'VALIDATION_FAILED', 'message', 'Koordinat lokasi tidak valid');
    END IF;

    IF p_preferred_radius_km IS NULL OR p_preferred_radius_km < 1 OR p_preferred_radius_km > 20000 THEN
        RETURN jsonb_build_object('code', 'VALIDATION_FAILED', 'message', 'Radius preferensi harus 1 sampai 20000 km');
    END IF;

    IF NOT public.is_valid_storage_photo_uri(v_clean_avatar) THEN
        RETURN jsonb_build_object('code', 'VALIDATION_FAILED', 'message', 'URL avatar tidak valid');
    END IF;

    UPDATE public.users
    SET name = v_clean_name,
        bio = v_clean_bio,
        "avatarUri" = v_clean_avatar,
        city = v_clean_city,
        latitude = p_latitude,
        longitude = p_longitude,
        "preferredSize" = v_clean_size,
        "preferredGender" = v_clean_gender,
        "preferredRadiusKm" = p_preferred_radius_km
    WHERE id = v_user_id;

    RETURN jsonb_build_object('code', 'SUCCESS');
END;
$$;

REVOKE ALL ON FUNCTION public.rpc_update_profile(
    TEXT, TEXT, TEXT, TEXT, DOUBLE PRECISION, DOUBLE PRECISION, TEXT, TEXT, INTEGER
) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.rpc_update_profile(
    TEXT, TEXT, TEXT, TEXT, DOUBLE PRECISION, DOUBLE PRECISION, TEXT, TEXT, INTEGER
) TO anon, authenticated, service_role;

-- ============================================================================
-- New Discovery, Match Detail, Notification, Interest & Pass RPCs
-- ============================================================================

CREATE OR REPLACE FUNCTION public.rpc_get_discover_candidates(
    p_sizes TEXT[] DEFAULT NULL,
    p_genders TEXT[] DEFAULT NULL,
    p_categories TEXT[] DEFAULT NULL,
    p_colors TEXT[] DEFAULT NULL,
    p_max_radius_km INTEGER DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_user_id BIGINT;
    v_viewer public.users%ROWTYPE;
    v_effective_radius INTEGER;
    v_candidates JSONB;
BEGIN
    v_user_id := public.current_session_user_id();
    IF v_user_id IS NULL THEN
        RETURN jsonb_build_object('code', 'UNAUTHORIZED', 'candidates', '[]'::jsonb);
    END IF;

    SELECT * INTO v_viewer FROM public.users WHERE id = v_user_id LIMIT 1;
    IF NOT FOUND THEN
        RETURN jsonb_build_object('code', 'UNAUTHORIZED', 'candidates', '[]'::jsonb);
    END IF;

    v_effective_radius := LEAST(GREATEST(COALESCE(p_max_radius_km, v_viewer."preferredRadiusKm", 10), 1), 20000);

    WITH candidate_calc AS (
        SELECT
            c.id,
            c."ownerId",
            c.name,
            c.description,
            c.category,
            c.size,
            c.gender,
            c.color,
            c.condition,
            c."mainPhotoUri",
            c."detailPhotoUri",
            c."extraPhotoUri",
            c."isBarterAllowed",
            c.status,
            c."createdAt",
            c."updatedAt",
            owner_u.name AS owner_name,
            owner_u.city AS owner_city,
            owner_u."avatarUri" AS owner_avatar_uri,
            GREATEST(
                0,
                ROUND(
                    (
                        6371.0::double precision * 2.0::double precision * ASIN(
                            SQRT(
                                LEAST(
                                    1.0::double precision,
                                    GREATEST(
                                        0.0::double precision,
                                        POWER(SIN(RADIANS(owner_u.latitude - v_viewer.latitude) / 2.0::double precision), 2.0::double precision)
                                        + COS(RADIANS(v_viewer.latitude)) * COS(RADIANS(owner_u.latitude))
                                        * POWER(SIN(RADIANS(owner_u.longitude - v_viewer.longitude) / 2.0::double precision), 2.0::double precision)
                                    )
                                )
                            )
                        )
                    )::numeric
                )::INTEGER
            ) AS distance_km,
            COALESCE(rev.avg_rating, 0.0::double precision) AS owner_avg_rating,
            COALESCE(rev.review_count, 0) AS owner_review_count
        FROM public.clothing_items c
        JOIN public.users owner_u ON owner_u.id = c."ownerId"
        LEFT JOIN (
            SELECT
                r."revieweeId",
                AVG(r.rating)::double precision AS avg_rating,
                COUNT(*)::integer AS review_count
            FROM public.reviews r
            GROUP BY r."revieweeId"
        ) rev ON rev."revieweeId" = owner_u.id
        WHERE c."ownerId" <> v_user_id
          AND c.status = 'AVAILABLE'
          AND c."isBarterAllowed" = TRUE
          AND NOT EXISTS (
              SELECT 1 FROM public.matches m
              WHERE m.status = 'ACTIVE'
                AND (m."user1ItemId" = c.id OR m."user2ItemId" = c.id)
          )
          AND NOT EXISTS (
              SELECT 1 FROM public.swipe_passes sp
              WHERE sp."userId" = v_user_id
                AND sp."targetItemId" = c.id
          )
          AND NOT EXISTS (
              SELECT 1 FROM public.interests i
              WHERE i."senderUserId" = v_user_id
                AND i."targetItemId" = c.id
                AND (
                    i.status IN ('PENDING', 'DECLINED')
                    OR (
                        i.status IN ('ACCEPTED_MATCHED', 'RESOLVED_BY_MATCH')
                        AND EXISTS (
                            SELECT 1 FROM public.matches m2
                            WHERE m2.status IN ('ACTIVE', 'COMPLETED')
                              AND (
                                  m2."sourceInterestId" = i.id
                                  OR (
                                      (m2."user1ItemId" = c.id OR m2."user2ItemId" = c.id)
                                      AND (m2."user1Id" = v_user_id OR m2."user2Id" = v_user_id)
                                  )
                              )
                        )
                    )
                )
          )
          AND (p_sizes IS NULL OR COALESCE(pg_catalog.array_length(p_sizes, 1), 0) = 0 OR c.size = ANY(p_sizes))
          AND (
              p_genders IS NULL
              OR COALESCE(pg_catalog.array_length(p_genders, 1), 0) = 0
              OR 'Uniseks' = ANY(p_genders)
              OR c.gender = ANY(p_genders)
              OR LOWER(c.gender) = 'uniseks'
          )
          AND (p_categories IS NULL OR COALESCE(pg_catalog.array_length(p_categories, 1), 0) = 0 OR c.category = ANY(p_categories))
          AND (p_colors IS NULL OR COALESCE(pg_catalog.array_length(p_colors, 1), 0) = 0 OR c.color = ANY(p_colors))
    ),
    filtered_candidates AS (
        SELECT *
        FROM candidate_calc
        WHERE distance_km <= v_effective_radius
        ORDER BY distance_km ASC, "createdAt" DESC, id DESC
        LIMIT 100
    )
    SELECT COALESCE(
        jsonb_agg(
            jsonb_build_object(
                'id', fc.id,
                'ownerId', fc."ownerId",
                'name', fc.name,
                'description', fc.description,
                'category', fc.category,
                'size', fc.size,
                'gender', fc.gender,
                'color', fc.color,
                'condition', fc.condition,
                'mainPhotoUri', fc."mainPhotoUri",
                'detailPhotoUri', fc."detailPhotoUri",
                'extraPhotoUri', fc."extraPhotoUri",
                'isBarterAllowed', fc."isBarterAllowed",
                'status', fc.status,
                'createdAt', fc."createdAt",
                'updatedAt', fc."updatedAt",
                'distanceKm', fc.distance_km,
                'ownerAverageRating', fc.owner_avg_rating,
                'ownerReviewCount', fc.owner_review_count,
                'users', jsonb_build_object(
                    'id', fc."ownerId",
                    'name', fc.owner_name,
                    'city', fc.owner_city,
                    'avatarUri', fc.owner_avatar_uri
                )
            )
            ORDER BY fc.distance_km ASC, fc."createdAt" DESC, fc.id DESC
        ),
        '[]'::jsonb
    )
    INTO v_candidates
    FROM filtered_candidates fc;

    RETURN jsonb_build_object('code', 'SUCCESS', 'candidates', v_candidates);
END;
$$;

REVOKE ALL ON FUNCTION public.rpc_get_discover_candidates(TEXT[], TEXT[], TEXT[], TEXT[], INTEGER) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.rpc_get_discover_candidates(TEXT[], TEXT[], TEXT[], TEXT[], INTEGER) TO anon, authenticated, service_role;

CREATE OR REPLACE FUNCTION public.rpc_get_match_detail(
    p_match_id BIGINT
)
RETURNS JSONB
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_user_id BIGINT;
    v_match public.matches%ROWTYPE;
    v_partner_id BIGINT;
    v_my_item_id BIGINT;
    v_partner_item_id BIGINT;
    v_partner public.users%ROWTYPE;
    v_my_item public.clothing_items%ROWTYPE;
    v_partner_item public.clothing_items%ROWTYPE;
    v_partner_avg DOUBLE PRECISION;
    v_partner_reviews INTEGER;
BEGIN
    v_user_id := public.current_session_user_id();
    IF v_user_id IS NULL THEN
        RETURN jsonb_build_object('code', 'UNAUTHORIZED');
    END IF;

    SELECT * INTO v_match FROM public.matches WHERE id = p_match_id LIMIT 1;
    IF NOT FOUND THEN
        RETURN jsonb_build_object('code', 'MATCH_NOT_FOUND');
    END IF;

    IF v_user_id <> v_match."user1Id" AND v_user_id <> v_match."user2Id" THEN
        RETURN jsonb_build_object('code', 'NOT_MATCH_PARTICIPANT');
    END IF;

    IF v_user_id = v_match."user1Id" THEN
        v_partner_id := v_match."user2Id";
        v_my_item_id := v_match."user1ItemId";
        v_partner_item_id := v_match."user2ItemId";
    ELSE
        v_partner_id := v_match."user1Id";
        v_my_item_id := v_match."user2ItemId";
        v_partner_item_id := v_match."user1ItemId";
    END IF;

    SELECT * INTO v_partner FROM public.users WHERE id = v_partner_id LIMIT 1;
    SELECT * INTO v_my_item FROM public.clothing_items WHERE id = v_my_item_id LIMIT 1;
    SELECT * INTO v_partner_item FROM public.clothing_items WHERE id = v_partner_item_id LIMIT 1;

    IF v_partner.id IS NULL OR v_my_item.id IS NULL OR v_partner_item.id IS NULL THEN
        RETURN jsonb_build_object('code', 'MATCH_NOT_FOUND');
    END IF;

    SELECT COALESCE(AVG(rating)::double precision, 0.0), COUNT(*)::integer
    INTO v_partner_avg, v_partner_reviews
    FROM public.reviews
    WHERE "revieweeId" = v_partner_id;

    RETURN jsonb_build_object(
        'code', 'SUCCESS',
        'match', jsonb_build_object(
            'id', v_match.id,
            'sourceInterestId', v_match."sourceInterestId",
            'user1Id', v_match."user1Id",
            'user2Id', v_match."user2Id",
            'user1ItemId', v_match."user1ItemId",
            'user2ItemId', v_match."user2ItemId",
            'status', v_match.status,
            'meetingLocation', v_match."meetingLocation",
            'createdAt', v_match."createdAt",
            'updatedAt', v_match."updatedAt"
        ),
        'myUserId', v_user_id,
        'partner', jsonb_build_object(
            'id', v_partner.id,
            'name', v_partner.name,
            'city', v_partner.city,
            'avatarUri', v_partner."avatarUri",
            'averageRating', v_partner_avg,
            'reviewCount', v_partner_reviews
        ),
        'myItem', jsonb_build_object(
            'id', v_my_item.id,
            'ownerId', v_my_item."ownerId",
            'name', v_my_item.name,
            'description', v_my_item.description,
            'category', v_my_item.category,
            'size', v_my_item.size,
            'gender', v_my_item.gender,
            'color', v_my_item.color,
            'condition', v_my_item.condition,
            'mainPhotoUri', v_my_item."mainPhotoUri",
            'detailPhotoUri', v_my_item."detailPhotoUri",
            'extraPhotoUri', v_my_item."extraPhotoUri",
            'isBarterAllowed', v_my_item."isBarterAllowed",
            'status', v_my_item.status,
            'createdAt', v_my_item."createdAt",
            'updatedAt', v_my_item."updatedAt"
        ),
        'partnerItem', jsonb_build_object(
            'id', v_partner_item.id,
            'ownerId', v_partner_item."ownerId",
            'name', v_partner_item.name,
            'description', v_partner_item.description,
            'category', v_partner_item.category,
            'size', v_partner_item.size,
            'gender', v_partner_item.gender,
            'color', v_partner_item.color,
            'condition', v_partner_item.condition,
            'mainPhotoUri', v_partner_item."mainPhotoUri",
            'detailPhotoUri', v_partner_item."detailPhotoUri",
            'extraPhotoUri', v_partner_item."extraPhotoUri",
            'isBarterAllowed', v_partner_item."isBarterAllowed",
            'status', v_partner_item.status,
            'createdAt', v_partner_item."createdAt",
            'updatedAt', v_partner_item."updatedAt"
        )
    );
END;
$$;

REVOKE ALL ON FUNCTION public.rpc_get_match_detail(BIGINT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.rpc_get_match_detail(BIGINT) TO anon, authenticated, service_role;

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

    SELECT COALESCE(jsonb_agg(DISTINCT item_id), '[]'::jsonb) INTO v_active_item_ids
    FROM (
        SELECT "user1ItemId" AS item_id FROM public.matches WHERE status = 'ACTIVE'
        UNION
        SELECT "user2ItemId" AS item_id FROM public.matches WHERE status = 'ACTIVE'
    ) t;

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
    LEFT JOIN public.users su ON su.id = i."senderUserId"
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

CREATE OR REPLACE FUNCTION public.rpc_get_unread_notification_count()
RETURNS JSONB
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_user_id BIGINT;
    v_count INTEGER;
BEGIN
    v_user_id := public.current_session_user_id();
    IF v_user_id IS NULL THEN
        RETURN jsonb_build_object('code', 'UNAUTHORIZED', 'count', 0);
    END IF;

    SELECT COUNT(*)::INTEGER INTO v_count
    FROM public.notifications n
    WHERE n."userId" = v_user_id
      AND n."isRead" = FALSE;

    RETURN jsonb_build_object('code', 'SUCCESS', 'count', v_count);
END;
$$;

REVOKE ALL ON FUNCTION public.rpc_get_unread_notification_count() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.rpc_get_unread_notification_count() TO anon, authenticated, service_role;

CREATE OR REPLACE FUNCTION public.rpc_get_outgoing_interests()
RETURNS JSONB
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_user_id BIGINT;
    v_interests JSONB;
    v_active_item_ids JSONB;
    v_other_active_item_ids JSONB;
BEGIN
    v_user_id := public.current_session_user_id();
    IF v_user_id IS NULL THEN
        RETURN jsonb_build_object(
            'code', 'UNAUTHORIZED',
            'interests', '[]'::jsonb,
            'activeItemIds', '[]'::jsonb,
            'otherActiveItemIds', '[]'::jsonb
        );
    END IF;

    SELECT COALESCE(jsonb_agg(DISTINCT item_id), '[]'::jsonb) INTO v_active_item_ids
    FROM (
        SELECT "user1ItemId" AS item_id FROM public.matches WHERE status = 'ACTIVE'
        UNION
        SELECT "user2ItemId" AS item_id FROM public.matches WHERE status = 'ACTIVE'
    ) t;

    SELECT COALESCE(jsonb_agg(DISTINCT item_id), '[]'::jsonb) INTO v_other_active_item_ids
    FROM (
        SELECT "user1ItemId" AS item_id FROM public.matches WHERE status = 'ACTIVE' AND "user1Id" <> v_user_id AND "user2Id" <> v_user_id
        UNION
        SELECT "user2ItemId" AS item_id FROM public.matches WHERE status = 'ACTIVE' AND "user1Id" <> v_user_id AND "user2Id" <> v_user_id
    ) t;

    SELECT COALESCE(
        jsonb_agg(
            jsonb_build_object(
                'id', i.id,
                'senderUserId', i."senderUserId",
                'receiverUserId', i."receiverUserId",
                'targetItemId', i."targetItemId",
                'status', i.status,
                'createdAt', i."createdAt",
                'respondedAt', i."respondedAt",
                'clothing_items', CASE WHEN ci.id IS NULL THEN NULL ELSE jsonb_build_object(
                    'id', ci.id,
                    'ownerId', ci."ownerId",
                    'name', ci.name,
                    'category', ci.category,
                    'mainPhotoUri', ci."mainPhotoUri",
                    'status', ci.status,
                    'isBarterAllowed', ci."isBarterAllowed",
                    'users', CASE WHEN cu.id IS NULL THEN NULL ELSE jsonb_build_object(
                        'id', cu.id,
                        'name', cu.name,
                        'city', cu.city,
                        'avatarUri', cu."avatarUri"
                    ) END
                ) END,
                'matches', COALESCE(
                    (
                        SELECT jsonb_agg(
                            jsonb_build_object(
                                'id', m.id,
                                'sourceInterestId', m."sourceInterestId",
                                'user1Id', m."user1Id",
                                'user2Id', m."user2Id",
                                'status', m.status,
                                'updatedAt', m."updatedAt"
                            )
                            ORDER BY m."updatedAt" DESC, m.id DESC
                        )
                        FROM public.matches m
                        WHERE m."sourceInterestId" = i.id
                           OR (
                               i.status IN ('ACCEPTED_MATCHED', 'RESOLVED_BY_MATCH')
                               AND (m."user1ItemId" = i."targetItemId" OR m."user2ItemId" = i."targetItemId")
                               AND (
                                   (m."user1Id" = v_user_id AND m."user2Id" = ci."ownerId")
                                   OR (m."user2Id" = v_user_id AND m."user1Id" = ci."ownerId")
                               )
                           )
                        ORDER BY
                            CASE WHEN m."sourceInterestId" = i.id THEN 0 ELSE 1 END,
                            pg_catalog.abs(m."createdAt" - i."createdAt") ASC,
                            m."updatedAt" DESC,
                            m.id DESC
                    ),
                    '[]'::jsonb
                )
            )
            ORDER BY i."createdAt" DESC, i.id DESC
        ),
        '[]'::jsonb
    )
    INTO v_interests
    FROM public.interests i
    LEFT JOIN public.clothing_items ci ON ci.id = i."targetItemId"
    LEFT JOIN public.users cu ON cu.id = ci."ownerId"
    WHERE i."senderUserId" = v_user_id;

    RETURN jsonb_build_object(
        'code', 'SUCCESS',
        'interests', v_interests,
        'activeItemIds', v_active_item_ids,
        'otherActiveItemIds', v_other_active_item_ids
    );
END;
$$;

REVOKE ALL ON FUNCTION public.rpc_get_outgoing_interests() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.rpc_get_outgoing_interests() TO anon, authenticated, service_role;

CREATE OR REPLACE FUNCTION public.rpc_get_passed_clothing()
RETURNS JSONB
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_user_id BIGINT;
    v_passes JSONB;
    v_active_item_ids JSONB;
BEGIN
    v_user_id := public.current_session_user_id();
    IF v_user_id IS NULL THEN
        RETURN jsonb_build_object(
            'code', 'UNAUTHORIZED',
            'passes', '[]'::jsonb,
            'activeItemIds', '[]'::jsonb
        );
    END IF;

    SELECT COALESCE(jsonb_agg(DISTINCT item_id), '[]'::jsonb) INTO v_active_item_ids
    FROM (
        SELECT "user1ItemId" AS item_id FROM public.matches WHERE status = 'ACTIVE'
        UNION
        SELECT "user2ItemId" AS item_id FROM public.matches WHERE status = 'ACTIVE'
    ) t;

    SELECT COALESCE(
        jsonb_agg(
            jsonb_build_object(
                'id', sp.id,
                'targetItemId', sp."targetItemId",
                'createdAt', sp."createdAt",
                'clothing_items', jsonb_build_object(
                    'id', ci.id,
                    'ownerId', ci."ownerId",
                    'name', ci.name,
                    'category', ci.category,
                    'mainPhotoUri', ci."mainPhotoUri",
                    'status', ci.status,
                    'isBarterAllowed', ci."isBarterAllowed",
                    'users', CASE WHEN cu.id IS NULL THEN NULL ELSE jsonb_build_object(
                        'id', cu.id,
                        'name', cu.name,
                        'city', cu.city,
                        'avatarUri', cu."avatarUri"
                    ) END
                )
            )
            ORDER BY sp."createdAt" DESC, sp.id DESC
        ),
        '[]'::jsonb
    )
    INTO v_passes
    FROM public.swipe_passes sp
    JOIN public.clothing_items ci ON ci.id = sp."targetItemId"
    LEFT JOIN public.users cu ON cu.id = ci."ownerId"
    WHERE sp."userId" = v_user_id;

    RETURN jsonb_build_object(
        'code', 'SUCCESS',
        'passes', v_passes,
        'activeItemIds', v_active_item_ids
    );
END;
$$;

REVOKE ALL ON FUNCTION public.rpc_get_passed_clothing() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.rpc_get_passed_clothing() TO anon, authenticated, service_role;

CREATE OR REPLACE FUNCTION public.rpc_pass_candidate(
    p_target_item_id BIGINT
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_user_id BIGINT;
    v_now_ms BIGINT;
    v_target_owner_id BIGINT;
    v_existing_id BIGINT;
BEGIN
    v_user_id := public.current_session_user_id();
    IF v_user_id IS NULL THEN
        RETURN jsonb_build_object('code', 'UNAUTHORIZED');
    END IF;

    SELECT "ownerId" INTO v_target_owner_id
    FROM public.clothing_items
    WHERE id = p_target_item_id
    LIMIT 1;

    IF NOT FOUND OR v_target_owner_id = v_user_id THEN
        RETURN jsonb_build_object('code', 'INVALID_TARGET_ITEM');
    END IF;

    SELECT id INTO v_existing_id
    FROM public.swipe_passes
    WHERE "userId" = v_user_id
      AND "targetItemId" = p_target_item_id
    LIMIT 1;

    IF NOT FOUND THEN
        v_now_ms := (EXTRACT(EPOCH FROM pg_catalog.now()) * 1000)::BIGINT;
        INSERT INTO public.swipe_passes ("userId", "targetItemId", "createdAt")
        VALUES (v_user_id, p_target_item_id, v_now_ms);
    END IF;

    RETURN jsonb_build_object('code', 'SUCCESS');
END;
$$;

REVOKE ALL ON FUNCTION public.rpc_pass_candidate(BIGINT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.rpc_pass_candidate(BIGINT) TO anon, authenticated, service_role;

CREATE OR REPLACE FUNCTION public.rpc_restore_pass_to_discover(
    p_target_item_id BIGINT
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_user_id BIGINT;
BEGIN
    v_user_id := public.current_session_user_id();
    IF v_user_id IS NULL THEN
        RETURN jsonb_build_object('code', 'UNAUTHORIZED');
    END IF;

    DELETE FROM public.swipe_passes
    WHERE "userId" = v_user_id
      AND "targetItemId" = p_target_item_id;

    RETURN jsonb_build_object('code', 'SUCCESS');
END;
$$;

REVOKE ALL ON FUNCTION public.rpc_restore_pass_to_discover(BIGINT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.rpc_restore_pass_to_discover(BIGINT) TO anon, authenticated, service_role;

CREATE OR REPLACE FUNCTION public.rpc_mark_notifications_read()
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_user_id BIGINT;
BEGIN
    v_user_id := public.current_session_user_id();
    IF v_user_id IS NULL THEN
        RETURN jsonb_build_object('code', 'UNAUTHORIZED');
    END IF;

    UPDATE public.notifications
    SET "isRead" = TRUE
    WHERE "userId" = v_user_id
      AND "isRead" = FALSE;

    RETURN jsonb_build_object('code', 'SUCCESS');
END;
$$;

REVOKE ALL ON FUNCTION public.rpc_mark_notifications_read() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.rpc_mark_notifications_read() TO anon, authenticated, service_role;

CREATE OR REPLACE FUNCTION public.rpc_mark_notification_read_by_id(
    p_notification_id BIGINT
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_user_id BIGINT;
    v_updated INTEGER;
BEGIN
    v_user_id := public.current_session_user_id();
    IF v_user_id IS NULL THEN
        RETURN jsonb_build_object('code', 'UNAUTHORIZED');
    END IF;

    UPDATE public.notifications
    SET "isRead" = TRUE
    WHERE id = p_notification_id
      AND "userId" = v_user_id
      AND "isRead" = FALSE;

    GET DIAGNOSTICS v_updated = ROW_COUNT;

    RETURN jsonb_build_object('code', 'SUCCESS', 'updatedCount', v_updated);
END;
$$;

REVOKE ALL ON FUNCTION public.rpc_mark_notification_read_by_id(BIGINT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.rpc_mark_notification_read_by_id(BIGINT) TO anon, authenticated, service_role;

-- ============================================================================
-- New Chat RPCs
-- ============================================================================

CREATE OR REPLACE FUNCTION public.rpc_get_chat_threads(
    p_partner_user_id BIGINT DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_user_id BIGINT;
    v_threads JSONB;
BEGIN
    v_user_id := public.current_session_user_id();
    IF v_user_id IS NULL THEN
        RETURN jsonb_build_object('code', 'UNAUTHORIZED', 'threads', '[]'::jsonb);
    END IF;

    WITH match_base AS (
        SELECT
            m.id AS match_id,
            CASE WHEN m."user1Id" = v_user_id THEN m."user2Id" ELSE m."user1Id" END AS partner_user_id,
            CASE WHEN m."user1Id" = v_user_id THEN m."user1ItemId" ELSE m."user2ItemId" END AS my_item_id,
            CASE WHEN m."user1Id" = v_user_id THEN m."user2ItemId" ELSE m."user1ItemId" END AS partner_item_id,
            m.status,
            m."updatedAt" AS updated_at
        FROM public.matches m
        WHERE (m."user1Id" = v_user_id OR m."user2Id" = v_user_id)
    ),
    enriched AS (
        SELECT
            mb.match_id,
            mb.partner_user_id,
            COALESCE(pu.name, 'Pengguna') AS partner_name,
            COALESCE(pu.city, '') AS partner_city,
            pu."avatarUri" AS partner_avatar_uri,
            COALESCE(pr.avg_rating, 0.0::double precision) AS partner_average_rating,
            COALESCE(mi.name, '') AS my_item_name,
            COALESCE(pi.name, '') AS partner_item_name,
            mb.status,
            lm."messageText" AS last_message_text,
            lm."sentAt" AS last_message_sent_at,
            COALESCE(uc.unread_count, 0) AS unread_count,
            mb.updated_at,
            COUNT(*) OVER (PARTITION BY mb.partner_user_id)::integer AS total_matches_with_partner
        FROM match_base mb
        LEFT JOIN public.users pu ON pu.id = mb.partner_user_id
        LEFT JOIN public.clothing_items mi ON mi.id = mb.my_item_id
        LEFT JOIN public.clothing_items pi ON pi.id = mb.partner_item_id
        LEFT JOIN (
            SELECT r."revieweeId", AVG(r.rating)::double precision AS avg_rating
            FROM public.reviews r
            GROUP BY r."revieweeId"
        ) pr ON pr."revieweeId" = mb.partner_user_id
        LEFT JOIN LATERAL (
            SELECT cm."messageText", cm."sentAt"
            FROM public.chat_messages cm
            WHERE cm."matchId" = mb.match_id
            ORDER BY cm."sentAt" DESC, cm.id DESC
            LIMIT 1
        ) lm ON TRUE
        LEFT JOIN (
            SELECT cm2."matchId", COUNT(*)::integer AS unread_count
            FROM public.chat_messages cm2
            WHERE cm2."senderId" <> v_user_id
              AND cm2."isRead" = FALSE
            GROUP BY cm2."matchId"
        ) uc ON uc."matchId" = mb.match_id
    ),
    filtered AS (
        SELECT *
        FROM enriched
        WHERE p_partner_user_id IS NULL OR partner_user_id = p_partner_user_id
        ORDER BY GREATEST(COALESCE(last_message_sent_at, 0), updated_at) DESC, match_id DESC
    )
    SELECT COALESCE(
        jsonb_agg(
            jsonb_build_object(
                'matchId', f.match_id,
                'partnerUserId', f.partner_user_id,
                'partnerName', f.partner_name,
                'partnerCity', f.partner_city,
                'partnerAvatarUri', f.partner_avatar_uri,
                'partnerAverageRating', f.partner_average_rating,
                'myItemName', f.my_item_name,
                'partnerItemName', f.partner_item_name,
                'status', f.status,
                'lastMessageText', f.last_message_text,
                'lastMessageSentAt', f.last_message_sent_at,
                'unreadCount', f.unread_count,
                'updatedAt', f.updated_at,
                'totalMatchesWithPartner', f.total_matches_with_partner
            )
            ORDER BY GREATEST(COALESCE(f.last_message_sent_at, 0), f.updated_at) DESC, f.match_id DESC
        ),
        '[]'::jsonb
    )
    INTO v_threads
    FROM filtered f;

    RETURN jsonb_build_object('code', 'SUCCESS', 'threads', v_threads);
END;
$$;

REVOKE ALL ON FUNCTION public.rpc_get_chat_threads(BIGINT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.rpc_get_chat_threads(BIGINT) TO anon, authenticated, service_role;

CREATE OR REPLACE FUNCTION public.rpc_get_chat_messages(
    p_match_id BIGINT
)
RETURNS JSONB
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_user_id BIGINT;
    v_match public.matches%ROWTYPE;
    v_messages JSONB;
BEGIN
    v_user_id := public.current_session_user_id();
    IF v_user_id IS NULL THEN
        RETURN jsonb_build_object('code', 'UNAUTHORIZED', 'messages', '[]'::jsonb);
    END IF;

    SELECT * INTO v_match FROM public.matches WHERE id = p_match_id LIMIT 1;
    IF NOT FOUND THEN
        RETURN jsonb_build_object('code', 'MATCH_NOT_FOUND', 'messages', '[]'::jsonb);
    END IF;

    IF v_user_id <> v_match."user1Id" AND v_user_id <> v_match."user2Id" THEN
        RETURN jsonb_build_object('code', 'NOT_MATCH_PARTICIPANT', 'messages', '[]'::jsonb);
    END IF;

    SELECT COALESCE(
        jsonb_agg(
            jsonb_build_object(
                'id', cm.id,
                'matchId', cm."matchId",
                'senderId', cm."senderId",
                'messageText', cm."messageText",
                'isRead', cm."isRead",
                'sentAt', cm."sentAt",
                'users', CASE WHEN u.id IS NULL THEN NULL ELSE jsonb_build_object('name', u.name) END
            )
            ORDER BY cm."sentAt" ASC, cm.id ASC
        ),
        '[]'::jsonb
    )
    INTO v_messages
    FROM public.chat_messages cm
    LEFT JOIN public.users u ON u.id = cm."senderId"
    WHERE cm."matchId" = p_match_id;

    RETURN jsonb_build_object('code', 'SUCCESS', 'messages', v_messages);
END;
$$;

REVOKE ALL ON FUNCTION public.rpc_get_chat_messages(BIGINT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.rpc_get_chat_messages(BIGINT) TO anon, authenticated, service_role;

CREATE OR REPLACE FUNCTION public.rpc_send_chat_message(
    p_match_id BIGINT,
    p_message_text TEXT
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_user_id BIGINT;
    v_now_ms BIGINT;
    v_clean_text TEXT;
    v_match public.matches%ROWTYPE;
    v_sender_name TEXT;
    v_message_id BIGINT;
    v_recipient_id BIGINT;
BEGIN
    v_user_id := public.current_session_user_id();
    IF v_user_id IS NULL THEN
        RETURN jsonb_build_object('code', 'UNAUTHORIZED');
    END IF;

    v_clean_text := BTRIM(COALESCE(p_message_text, ''));
    IF v_clean_text = '' THEN
        RETURN jsonb_build_object('code', 'EMPTY_MESSAGE');
    END IF;

    IF LENGTH(v_clean_text) > 2000 THEN
        RETURN jsonb_build_object('code', 'VALIDATION_FAILED', 'message', 'Pesan chat terlalu panjang');
    END IF;

    SELECT * INTO v_match FROM public.matches WHERE id = p_match_id FOR UPDATE;
    IF NOT FOUND THEN
        RETURN jsonb_build_object('code', 'MATCH_NOT_FOUND');
    END IF;

    IF v_user_id <> v_match."user1Id" AND v_user_id <> v_match."user2Id" THEN
        RETURN jsonb_build_object('code', 'NOT_MATCH_PARTICIPANT');
    END IF;

    IF v_match.status <> 'ACTIVE' THEN
        RETURN jsonb_build_object('code', 'CHAT_READ_ONLY');
    END IF;

    v_now_ms := (EXTRACT(EPOCH FROM pg_catalog.now()) * 1000)::BIGINT;
    v_recipient_id := CASE WHEN v_user_id = v_match."user1Id" THEN v_match."user2Id" ELSE v_match."user1Id" END;

    INSERT INTO public.chat_messages ("matchId", "senderId", "messageText", "isRead", "sentAt")
    VALUES (p_match_id, v_user_id, v_clean_text, FALSE, v_now_ms)
    RETURNING id INTO v_message_id;

    UPDATE public.matches
    SET "updatedAt" = v_now_ms
    WHERE id = p_match_id;

    SELECT COALESCE(NULLIF(BTRIM(name), ''), 'Anda')
    INTO v_sender_name
    FROM public.users
    WHERE id = v_user_id
    LIMIT 1;

    RETURN jsonb_build_object(
        'code', 'SUCCESS',
        'messageId', v_message_id,
        'senderName', COALESCE(v_sender_name, 'Anda'),
        'sentAt', v_now_ms,
        'recipientId', v_recipient_id
    );
END;
$$;

REVOKE ALL ON FUNCTION public.rpc_send_chat_message(BIGINT, TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.rpc_send_chat_message(BIGINT, TEXT) TO anon, authenticated, service_role;

CREATE OR REPLACE FUNCTION public.rpc_mark_chat_messages_read(
    p_match_id BIGINT
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_user_id BIGINT;
    v_match public.matches%ROWTYPE;
    v_updated INTEGER;
BEGIN
    v_user_id := public.current_session_user_id();
    IF v_user_id IS NULL THEN
        RETURN jsonb_build_object('code', 'UNAUTHORIZED');
    END IF;

    SELECT * INTO v_match FROM public.matches WHERE id = p_match_id LIMIT 1;
    IF NOT FOUND THEN
        RETURN jsonb_build_object('code', 'MATCH_NOT_FOUND');
    END IF;

    IF v_user_id <> v_match."user1Id" AND v_user_id <> v_match."user2Id" THEN
        RETURN jsonb_build_object('code', 'NOT_MATCH_PARTICIPANT');
    END IF;

    UPDATE public.chat_messages
    SET "isRead" = TRUE
    WHERE "matchId" = p_match_id
      AND "senderId" <> v_user_id
      AND "isRead" = FALSE;

    GET DIAGNOSTICS v_updated = ROW_COUNT;

    RETURN jsonb_build_object('code', 'SUCCESS', 'updatedCount', v_updated);
END;
$$;

REVOKE ALL ON FUNCTION public.rpc_mark_chat_messages_read(BIGINT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.rpc_mark_chat_messages_read(BIGINT) TO anon, authenticated, service_role;

COMMIT;
