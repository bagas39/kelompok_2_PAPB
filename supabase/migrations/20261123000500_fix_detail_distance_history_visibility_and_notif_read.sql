BEGIN;

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
    v_caller public.users%ROWTYPE;
    v_user public.users%ROWTYPE;
    v_avg_rating DOUBLE PRECISION;
    v_review_count INTEGER;
    v_is_self BOOLEAN;
    v_distance_km INTEGER;
BEGIN
    v_caller_id := public.current_session_user_id();
    IF v_caller_id IS NULL THEN
        RETURN jsonb_build_object('code', 'UNAUTHORIZED');
    END IF;

    SELECT * INTO v_caller
    FROM public.users
    WHERE id = v_caller_id
    LIMIT 1;

    IF NOT FOUND THEN
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
    v_distance_km := CASE
        WHEN v_is_self THEN 0
        ELSE GREATEST(
            0,
            ROUND(
                (
                    6371.0::double precision * 2.0::double precision * ASIN(
                        SQRT(
                            LEAST(
                                1.0::double precision,
                                GREATEST(
                                    0.0::double precision,
                                    POWER(SIN(RADIANS(v_user.latitude - v_caller.latitude) / 2.0::double precision), 2.0::double precision)
                                    + COS(RADIANS(v_caller.latitude)) * COS(RADIANS(v_user.latitude))
                                    * POWER(SIN(RADIANS(v_user.longitude - v_caller.longitude) / 2.0::double precision), 2.0::double precision)
                                )
                            )
                        )
                    )
                )::numeric
            )::INTEGER
        )
    END;

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
            'createdAt', v_user."createdAt",
            'distanceKm', v_distance_km
        ),
        'averageRating', v_avg_rating,
        'reviewCount', v_review_count
    );
END;
$$;

REVOKE ALL ON FUNCTION public.rpc_get_user_profile(BIGINT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.rpc_get_user_profile(BIGINT) TO anon, authenticated, service_role;

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
                OR EXISTS (
                    SELECT 1 FROM public.interests i
                    WHERE i."targetItemId" = v_item.id
                      AND (i."senderUserId" = v_caller_id OR i."receiverUserId" = v_caller_id)
                )
                OR EXISTS (
                    SELECT 1 FROM public.swipe_passes sp
                    WHERE sp."targetItemId" = v_item.id
                      AND sp."userId" = v_caller_id
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
    v_resolved_interest_ids BIGINT[];
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

    WITH resolved AS (
        UPDATE public.interests
        SET status = 'RESOLVED_BY_MATCH', "respondedAt" = v_now_ms
        WHERE "senderUserId" = v_session_user_id
          AND "receiverUserId" = v_interest."senderUserId"
          AND "targetItemId" = v_chosen_item.id
          AND status = 'PENDING'
        RETURNING id
    )
    SELECT COALESCE(ARRAY_AGG(id), ARRAY[]::BIGINT[])
    INTO v_resolved_interest_ids
    FROM resolved;

    UPDATE public.notifications
    SET "isRead" = TRUE
    WHERE type = 'INCOMING_INTEREST'
      AND "isRead" = FALSE
      AND (
          "relatedInterestId" = v_interest.id
          OR "relatedInterestId" = ANY(v_resolved_interest_ids)
      );

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

UPDATE public.notifications n
SET "isRead" = TRUE
FROM public.interests i
WHERE n."relatedInterestId" = i.id
  AND n.type = 'INCOMING_INTEREST'
  AND n."isRead" = FALSE
  AND i.status <> 'PENDING';

COMMIT;
