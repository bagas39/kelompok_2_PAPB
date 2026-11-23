BEGIN;

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
    v_remaining_barter_count INTEGER;
    v_invalidated_outgoing_ids BIGINT[];
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

    RETURN jsonb_build_object('code', 'SUCCESS', 'itemId', p_item_id);
END;
$$;

REVOKE ALL ON FUNCTION public.rpc_soft_delete_clothing_protected(BIGINT, BIGINT, BIGINT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.rpc_soft_delete_clothing_protected(BIGINT, BIGINT, BIGINT) TO anon, authenticated, service_role;

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
    v_invalidated_interest_ids BIGINT[];
    v_remaining_barter_count INTEGER;
    v_invalidated_outgoing_ids BIGINT[];
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

    v_now_ms := (EXTRACT(EPOCH FROM pg_catalog.now()) * 1000)::BIGINT;

    UPDATE public.clothing_items
    SET "isBarterAllowed" = COALESCE(p_allowed, FALSE),
        "updatedAt" = v_now_ms
    WHERE id = p_item_id;

    IF v_item."isBarterAllowed" = TRUE AND COALESCE(p_allowed, FALSE) = FALSE THEN
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

    IF COALESCE(p_allowed, FALSE) = FALSE THEN
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

    RETURN jsonb_build_object('code', 'SUCCESS', 'itemId', p_item_id);
END;
$$;

REVOKE ALL ON FUNCTION public.rpc_set_barter_allowed_protected(BIGINT, BIGINT, BOOLEAN, BIGINT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.rpc_set_barter_allowed_protected(BIGINT, BIGINT, BOOLEAN, BIGINT) TO anon, authenticated, service_role;

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

    RETURN jsonb_build_object('code', 'SUCCESS', 'itemId', p_item_id);
END;
$$;

REVOKE ALL ON FUNCTION public.rpc_update_clothing_protected(
    BIGINT, BIGINT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, BOOLEAN, BIGINT
) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.rpc_update_clothing_protected(
    BIGINT, BIGINT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, BOOLEAN, BIGINT
) TO anon, authenticated, service_role;

COMMIT;
