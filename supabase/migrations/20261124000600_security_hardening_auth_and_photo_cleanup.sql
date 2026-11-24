BEGIN;

-- ============================================================================
-- 1. Eliminate Pass-the-Hash: lock client-hash auth RPCs to service_role only
--    so all login/register/change-password/reset verification happens server-side
--    inside Edge Function auth-service.
-- ============================================================================

REVOKE ALL ON FUNCTION public.rpc_get_login_challenge(TEXT) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.rpc_get_login_challenge(TEXT) TO service_role;

REVOKE ALL ON FUNCTION public.rpc_create_session(TEXT, TEXT) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.rpc_create_session(TEXT, TEXT) TO service_role;

REVOKE ALL ON FUNCTION public.rpc_register_user(TEXT, TEXT, TEXT, TEXT, DOUBLE PRECISION, DOUBLE PRECISION, BIGINT) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.rpc_register_user(TEXT, TEXT, TEXT, TEXT, DOUBLE PRECISION, DOUBLE PRECISION, BIGINT) TO service_role;

REVOKE ALL ON FUNCTION public.rpc_get_current_password_challenge() FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.rpc_get_current_password_challenge() TO service_role;

REVOKE ALL ON FUNCTION public.rpc_change_password_authenticated(TEXT, TEXT, BIGINT) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.rpc_change_password_authenticated(TEXT, TEXT, BIGINT) TO service_role;

REVOKE ALL ON FUNCTION public.rpc_verify_otp_and_reset_password(TEXT, TEXT, TEXT, BIGINT) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.rpc_verify_otp_and_reset_password(TEXT, TEXT, TEXT, BIGINT) TO service_role;

-- ============================================================================
-- 2. Service-only helper for manage-photo Edge Function delete verification
-- ============================================================================

CREATE OR REPLACE FUNCTION public.rpc_filter_deletable_photo_uris(
    p_user_id BIGINT,
    p_photo_uris TEXT[]
)
RETURNS JSONB
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_deletable JSONB;
BEGIN
    IF p_user_id IS NULL OR p_user_id <= 0 OR p_photo_uris IS NULL OR CARDINALITY(p_photo_uris) = 0 THEN
        RETURN jsonb_build_object('code', 'SUCCESS', 'deletableUris', '[]'::jsonb);
    END IF;

    SELECT COALESCE(jsonb_agg(DISTINCT req.uri), '[]'::jsonb)
    INTO v_deletable
    FROM unnest(p_photo_uris) AS req(uri)
    WHERE NULLIF(BTRIM(req.uri), '') IS NOT NULL
      AND NOT EXISTS (
          SELECT 1
          FROM public.users u
          WHERE u."avatarUri" = req.uri
      )
      AND NOT EXISTS (
          SELECT 1
          FROM public.clothing_items ci
          WHERE (
              ci."mainPhotoUri" = req.uri
              OR ci."detailPhotoUri" = req.uri
              OR ci."extraPhotoUri" = req.uri
          )
            AND (
                ci.status <> 'HIDDEN_DELETED'
                OR EXISTS (
                    SELECT 1
                    FROM public.matches m
                    WHERE m."user1ItemId" = ci.id
                       OR m."user2ItemId" = ci.id
                )
            )
      );

    RETURN jsonb_build_object(
        'code', 'SUCCESS',
        'deletableUris', v_deletable
    );
END;
$$;

REVOKE ALL ON FUNCTION public.rpc_filter_deletable_photo_uris(BIGINT, TEXT[]) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.rpc_filter_deletable_photo_uris(BIGINT, TEXT[]) TO service_role;

-- ============================================================================
-- 3. Return stale storage cleanup URIs from profile/clothing mutations
-- ============================================================================

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
    v_old_avatar TEXT;
    v_clean_name TEXT;
    v_clean_bio TEXT;
    v_clean_avatar TEXT;
    v_clean_city TEXT;
    v_clean_size TEXT;
    v_clean_gender TEXT;
    v_cleanup_uris JSONB;
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

    SELECT "avatarUri" INTO v_old_avatar
    FROM public.users
    WHERE id = v_user_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RETURN jsonb_build_object('code', 'UNAUTHORIZED');
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

    IF v_old_avatar IS NOT NULL
       AND BTRIM(v_old_avatar) <> ''
       AND v_old_avatar IS DISTINCT FROM v_clean_avatar THEN
        v_cleanup_uris := jsonb_build_array(v_old_avatar);
    ELSE
        v_cleanup_uris := '[]'::jsonb;
    END IF;

    RETURN jsonb_build_object(
        'code', 'SUCCESS',
        'cleanupPhotoUris', v_cleanup_uris
    );
END;
$$;

REVOKE ALL ON FUNCTION public.rpc_update_profile(
    TEXT, TEXT, TEXT, TEXT, DOUBLE PRECISION, DOUBLE PRECISION, TEXT, TEXT, INTEGER
) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.rpc_update_profile(
    TEXT, TEXT, TEXT, TEXT, DOUBLE PRECISION, DOUBLE PRECISION, TEXT, TEXT, INTEGER
) TO anon, authenticated, service_role;

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
    v_invalidated_interest_ids BIGINT[];
    v_remaining_barter_count INTEGER;
    v_invalidated_outgoing_ids BIGINT[];
    v_cleanup_uris JSONB;
BEGIN
    v_session_user_id := public.current_session_user_id();
    IF v_session_user_id IS NULL OR v_session_user_id <> p_actor_user_id THEN
        RETURN jsonb_build_object('code', 'INVALID_OWNERSHIP');
    END IF;

    PERFORM pg_catalog.pg_advisory_xact_lock(72003, (v_session_user_id % 2147483647)::INTEGER);

    SELECT * INTO v_item FROM public.clothing_items WHERE id = p_item_id FOR UPDATE;
    IF NOT FOUND THEN
        RETURN jsonb_build_object('code', 'ITEM_NOT_FOUND');
    END IF;

    IF v_item."ownerId" <> v_session_user_id THEN
        RETURN jsonb_build_object('code', 'INVALID_OWNERSHIP');
    END IF;

    IF v_item.status <> 'AVAILABLE' THEN
        RETURN jsonb_build_object('code', 'LOCKED_IN_ACTIVE_MATCH', 'reason', v_item.status);
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
            DELETE FROM public.notifications
            WHERE type = 'INCOMING_INTEREST'
              AND "relatedInterestId" = ANY(v_invalidated_interest_ids);
        END IF;
    END IF;

    IF COALESCE(p_is_barter_allowed, FALSE) = FALSE THEN
        SELECT COUNT(*)::INTEGER INTO v_remaining_barter_count
        FROM public.clothing_items c
        WHERE c."ownerId" = v_session_user_id
          AND c.status = 'AVAILABLE'
          AND c."isBarterAllowed" = TRUE
          AND NOT EXISTS (
              SELECT 1
              FROM public.matches m
              WHERE m.status = 'ACTIVE'
                AND (m."user1ItemId" = c.id OR m."user2ItemId" = c.id)
          );

        IF v_remaining_barter_count = 0 THEN
            WITH invalidated_outgoing AS (
                UPDATE public.interests
                SET status = 'INVALIDATED', "respondedAt" = v_now_ms
                WHERE "senderUserId" = v_session_user_id AND status = 'PENDING'
                RETURNING id
            )
            SELECT COALESCE(ARRAY_AGG(id), ARRAY[]::BIGINT[])
            INTO v_invalidated_outgoing_ids
            FROM invalidated_outgoing;

            IF CARDINALITY(v_invalidated_outgoing_ids) > 0 THEN
                DELETE FROM public.notifications
                WHERE type = 'INCOMING_INTEREST'
                  AND "relatedInterestId" = ANY(v_invalidated_outgoing_ids);
            END IF;
        END IF;
    END IF;

    SELECT COALESCE(jsonb_agg(DISTINCT old_uri), '[]'::jsonb)
    INTO v_cleanup_uris
    FROM unnest(ARRAY[v_item."mainPhotoUri", v_item."detailPhotoUri", v_item."extraPhotoUri"]) AS old_uri
    WHERE NULLIF(BTRIM(old_uri), '') IS NOT NULL
      AND old_uri NOT IN (
          COALESCE(v_clean_main_photo, ''),
          COALESCE(v_clean_detail_photo, ''),
          COALESCE(v_clean_extra_photo, '')
      );

    RETURN jsonb_build_object(
        'code', 'SUCCESS',
        'itemId', p_item_id,
        'cleanupPhotoUris', v_cleanup_uris
    );
END;
$$;

REVOKE ALL ON FUNCTION public.rpc_update_clothing_protected(
    BIGINT, BIGINT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, BOOLEAN, BIGINT
) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.rpc_update_clothing_protected(
    BIGINT, BIGINT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, BOOLEAN, BIGINT
) TO anon, authenticated, service_role;

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
    v_has_match_history BOOLEAN;
    v_invalidated_interest_ids BIGINT[];
    v_remaining_barter_count INTEGER;
    v_invalidated_outgoing_ids BIGINT[];
    v_cleanup_uris JSONB;
BEGIN
    v_session_user_id := public.current_session_user_id();
    IF v_session_user_id IS NULL OR v_session_user_id <> p_actor_user_id THEN
        RETURN jsonb_build_object('code', 'INVALID_OWNERSHIP');
    END IF;

    PERFORM pg_catalog.pg_advisory_xact_lock(72003, (v_session_user_id % 2147483647)::INTEGER);

    SELECT * INTO v_existing FROM public.clothing_items WHERE id = p_item_id FOR UPDATE;
    IF NOT FOUND THEN
        RETURN jsonb_build_object('code', 'ITEM_NOT_FOUND');
    END IF;

    IF v_existing."ownerId" <> v_session_user_id THEN
        RETURN jsonb_build_object('code', 'INVALID_OWNERSHIP');
    END IF;

    IF v_existing.status <> 'AVAILABLE' THEN
        RETURN jsonb_build_object('code', 'LOCKED_IN_ACTIVE_MATCH', 'reason', v_existing.status);
    END IF;

    SELECT COUNT(*) INTO v_active_match_count
    FROM public.matches
    WHERE status = 'ACTIVE' AND ("user1ItemId" = p_item_id OR "user2ItemId" = p_item_id);

    IF v_active_match_count > 0 THEN
        RETURN jsonb_build_object('code', 'LOCKED_IN_ACTIVE_MATCH');
    END IF;

    SELECT EXISTS (
        SELECT 1
        FROM public.matches
        WHERE "user1ItemId" = p_item_id OR "user2ItemId" = p_item_id
    ) INTO v_has_match_history;

    v_now_ms := (EXTRACT(EPOCH FROM pg_catalog.now()) * 1000)::BIGINT;

    UPDATE public.clothing_items
    SET status = 'HIDDEN_DELETED',
        "isBarterAllowed" = FALSE,
        "updatedAt" = v_now_ms
    WHERE id = p_item_id;

    DELETE FROM public.swipe_passes
    WHERE "targetItemId" = p_item_id;

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
        DELETE FROM public.notifications
        WHERE type = 'INCOMING_INTEREST'
          AND "relatedInterestId" = ANY(v_invalidated_interest_ids);
    END IF;

    SELECT COUNT(*)::INTEGER INTO v_remaining_barter_count
    FROM public.clothing_items c
    WHERE c."ownerId" = v_session_user_id
      AND c.status = 'AVAILABLE'
      AND c."isBarterAllowed" = TRUE
      AND NOT EXISTS (
          SELECT 1
          FROM public.matches m
          WHERE m.status = 'ACTIVE'
            AND (m."user1ItemId" = c.id OR m."user2ItemId" = c.id)
      );

    IF v_remaining_barter_count = 0 THEN
        WITH invalidated_outgoing AS (
            UPDATE public.interests
            SET status = 'INVALIDATED', "respondedAt" = v_now_ms
            WHERE "senderUserId" = v_session_user_id AND status = 'PENDING'
            RETURNING id
        )
        SELECT COALESCE(ARRAY_AGG(id), ARRAY[]::BIGINT[])
        INTO v_invalidated_outgoing_ids
        FROM invalidated_outgoing;

        IF CARDINALITY(v_invalidated_outgoing_ids) > 0 THEN
            DELETE FROM public.notifications
            WHERE type = 'INCOMING_INTEREST'
              AND "relatedInterestId" = ANY(v_invalidated_outgoing_ids);
        END IF;
    END IF;

    IF NOT v_has_match_history THEN
        SELECT COALESCE(jsonb_agg(DISTINCT old_uri), '[]'::jsonb)
        INTO v_cleanup_uris
        FROM unnest(ARRAY[v_existing."mainPhotoUri", v_existing."detailPhotoUri", v_existing."extraPhotoUri"]) AS old_uri
        WHERE NULLIF(BTRIM(old_uri), '') IS NOT NULL;
    ELSE
        v_cleanup_uris := '[]'::jsonb;
    END IF;

    RETURN jsonb_build_object(
        'code', 'SUCCESS',
        'itemId', p_item_id,
        'cleanupPhotoUris', v_cleanup_uris
    );
END;
$$;

REVOKE ALL ON FUNCTION public.rpc_soft_delete_clothing_protected(BIGINT, BIGINT, BIGINT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.rpc_soft_delete_clothing_protected(BIGINT, BIGINT, BIGINT) TO anon, authenticated, service_role;

COMMIT;
