BEGIN;

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
            CASE
                WHEN v_viewer."preferredSize" IS NOT NULL
                  AND BTRIM(v_viewer."preferredSize") <> ''
                  AND LOWER(v_viewer."preferredSize") <> 'semua'
                  AND c.size = v_viewer."preferredSize"
                THEN 0
                ELSE 1
            END AS pref_size_rank,
            CASE
                WHEN v_viewer."preferredGender" IS NOT NULL
                  AND BTRIM(v_viewer."preferredGender") <> ''
                  AND LOWER(v_viewer."preferredGender") NOT IN ('semua', 'uniseks')
                  AND (c.gender = v_viewer."preferredGender" OR LOWER(c.gender) = 'uniseks')
                THEN 0
                ELSE 1
            END AS pref_gender_rank,
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
              SELECT 1 FROM public.matches m_hist
              WHERE m_hist.status IN ('ACTIVE', 'COMPLETED')
                AND (m_hist."user1Id" = v_user_id OR m_hist."user2Id" = v_user_id)
                AND (m_hist."user1ItemId" = c.id OR m_hist."user2ItemId" = c.id)
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
              OR c.gender = ANY(p_genders)
          )
          AND (p_categories IS NULL OR COALESCE(pg_catalog.array_length(p_categories, 1), 0) = 0 OR c.category = ANY(p_categories))
          AND (p_colors IS NULL OR COALESCE(pg_catalog.array_length(p_colors, 1), 0) = 0 OR c.color = ANY(p_colors))
    ),
    filtered_candidates AS (
        SELECT *
        FROM candidate_calc
        WHERE distance_km <= v_effective_radius
        ORDER BY distance_km ASC, pref_size_rank ASC, pref_gender_rank ASC, "createdAt" DESC, id DESC
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
            ORDER BY fc.distance_km ASC, fc.pref_size_rank ASC, fc.pref_gender_rank ASC, fc."createdAt" DESC, fc.id DESC
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

    SELECT * INTO v_interest FROM public.interests WHERE id = p_interest_id;
    IF NOT FOUND OR v_interest."receiverUserId" <> v_session_user_id OR v_interest.status <> 'PENDING' THEN
        RETURN jsonb_build_object('code', 'INTEREST_ALREADY_PROCESSED_OR_INVALID');
    END IF;

    PERFORM id FROM public.clothing_items
    WHERE id IN (v_interest."targetItemId", p_chosen_sender_item_id)
    ORDER BY id FOR UPDATE;

    SELECT * INTO v_interest FROM public.interests WHERE id = p_interest_id FOR UPDATE;
    IF NOT FOUND OR v_interest."receiverUserId" <> v_session_user_id OR v_interest.status <> 'PENDING' THEN
        RETURN jsonb_build_object('code', 'INTEREST_ALREADY_PROCESSED_OR_INVALID');
    END IF;

    SELECT * INTO v_target_item FROM public.clothing_items WHERE id = v_interest."targetItemId";
    IF NOT FOUND THEN
        UPDATE public.interests SET status = 'INVALIDATED', "respondedAt" = v_now_ms WHERE id = v_interest.id;
        UPDATE public.notifications SET "isRead" = TRUE
        WHERE type = 'INCOMING_INTEREST' AND "relatedInterestId" = v_interest.id;
        RETURN jsonb_build_object('code', 'TARGET_ITEM_NOT_FOUND', 'invalidated', TRUE);
    END IF;

    IF v_target_item."ownerId" <> v_session_user_id OR v_session_user_id = v_interest."senderUserId" THEN
        RETURN jsonb_build_object('code', 'INVALID_ITEM_OWNERSHIP');
    END IF;

    IF v_target_item.status <> 'AVAILABLE' THEN
        UPDATE public.interests SET status = 'INVALIDATED', "respondedAt" = v_now_ms WHERE id = v_interest.id;
        UPDATE public.notifications SET "isRead" = TRUE
        WHERE type = 'INCOMING_INTEREST' AND "relatedInterestId" = v_interest.id;
        RETURN jsonb_build_object('code', 'TARGET_ITEM_UNAVAILABLE', 'invalidated', TRUE);
    END IF;

    SELECT * INTO v_chosen_item FROM public.clothing_items WHERE id = p_chosen_sender_item_id;
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

    DELETE FROM public.swipe_passes
    WHERE ("userId" = v_session_user_id AND "targetItemId" = v_chosen_item.id)
       OR ("userId" = v_interest."senderUserId" AND "targetItemId" = v_target_item.id);

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

    IF COALESCE(pg_catalog.array_length(v_resolved_interest_ids, 1), 0) = 0 THEN
        INSERT INTO public.interests (
            "senderUserId", "receiverUserId", "targetItemId", status, "createdAt", "respondedAt"
        )
        VALUES (
            v_session_user_id, v_interest."senderUserId", v_chosen_item.id, 'RESOLVED_BY_MATCH', v_now_ms, v_now_ms
        );
    END IF;

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
        TRUE,
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

    SELECT * INTO v_match FROM public.matches WHERE id = p_match_id;
    IF NOT FOUND THEN
        RETURN jsonb_build_object('code', 'MATCH_NOT_FOUND');
    END IF;

    IF v_session_user_id <> v_match."user1Id" AND v_session_user_id <> v_match."user2Id" THEN
        RETURN jsonb_build_object('code', 'NOT_MATCH_PARTICIPANT');
    END IF;

    PERFORM id FROM public.clothing_items
    WHERE id IN (v_match."user1ItemId", v_match."user2ItemId")
    ORDER BY id FOR UPDATE;

    SELECT * INTO v_match FROM public.matches WHERE id = p_match_id FOR UPDATE;
    IF NOT FOUND THEN
        RETURN jsonb_build_object('code', 'MATCH_NOT_FOUND');
    END IF;

    IF v_match.status <> 'ACTIVE' THEN
        RETURN jsonb_build_object('code', 'MATCH_NOT_ACTIVE', 'currentStatus', v_match.status);
    END IF;

    SELECT * INTO v_item1 FROM public.clothing_items WHERE id = v_match."user1ItemId";
    SELECT * INTO v_item2 FROM public.clothing_items WHERE id = v_match."user2ItemId";

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
    SET status = 'EXCHANGED',
        "isBarterAllowed" = FALSE,
        "updatedAt" = v_now_ms
    WHERE id IN (v_match."user1ItemId", v_match."user2ItemId");

    WITH invalidated AS (
        UPDATE public.interests
        SET status = 'INVALIDATED', "respondedAt" = v_now_ms
        WHERE status = 'PENDING'
          AND (
              "targetItemId" IN (v_match."user1ItemId", v_match."user2ItemId")
              OR (
                  "senderUserId" IN (v_match."user1Id", v_match."user2Id")
                  AND NOT EXISTS (
                      SELECT 1 FROM public.clothing_items c
                      WHERE c."ownerId" = public.interests."senderUserId"
                        AND c.status = 'AVAILABLE' AND c."isBarterAllowed"
                        AND NOT EXISTS (
                            SELECT 1 FROM public.matches m WHERE m.status = 'ACTIVE'
                            AND (m."user1ItemId" = c.id OR m."user2ItemId" = c.id)
                        )
                  )
              )
          )
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

    SELECT * INTO v_match FROM public.matches WHERE id = p_match_id;
    IF NOT FOUND THEN
        RETURN jsonb_build_object('code', 'MATCH_NOT_FOUND');
    END IF;

    IF v_session_user_id <> v_match."user1Id" AND v_session_user_id <> v_match."user2Id" THEN
        RETURN jsonb_build_object('code', 'NOT_MATCH_PARTICIPANT');
    END IF;

    PERFORM id FROM public.clothing_items
    WHERE id IN (v_match."user1ItemId", v_match."user2ItemId")
    ORDER BY id FOR UPDATE;

    SELECT * INTO v_match FROM public.matches WHERE id = p_match_id FOR UPDATE;
    IF NOT FOUND THEN
        RETURN jsonb_build_object('code', 'MATCH_NOT_FOUND');
    END IF;

    IF v_match.status <> 'ACTIVE' THEN
        RETURN jsonb_build_object('code', 'MATCH_NOT_ACTIVE', 'currentStatus', v_match.status);
    END IF;

    v_now_ms := (EXTRACT(EPOCH FROM pg_catalog.now()) * 1000)::BIGINT;

    UPDATE public.matches
    SET status = 'CANCELLED', "updatedAt" = v_now_ms
    WHERE id = p_match_id;

    UPDATE public.notifications n
    SET "isRead" = FALSE
    FROM public.interests i
    JOIN public.clothing_items c ON c.id = i."targetItemId"
    WHERE n."relatedInterestId" = i.id AND n.type = 'INCOMING_INTEREST'
      AND i.status = 'PENDING' AND n."userId" = i."receiverUserId"
      AND i."senderUserId" NOT IN (v_match."user1Id", v_match."user2Id")
      AND c.id IN (v_match."user1ItemId", v_match."user2ItemId")
      AND c.status = 'AVAILABLE' AND c."isBarterAllowed"
      AND NOT EXISTS (
          SELECT 1 FROM public.matches m WHERE m.status = 'ACTIVE'
          AND (m."user1ItemId" = c.id OR m."user2ItemId" = c.id)
      )
      AND EXISTS (
          SELECT 1 FROM public.clothing_items sender_item
          WHERE sender_item."ownerId" = i."senderUserId"
            AND sender_item.status = 'AVAILABLE' AND sender_item."isBarterAllowed"
            AND NOT EXISTS (
                SELECT 1 FROM public.matches m WHERE m.status = 'ACTIVE'
                AND (m."user1ItemId" = sender_item.id OR m."user2ItemId" = sender_item.id)
            )
      );

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

CREATE OR REPLACE FUNCTION public.rpc_cancel_outgoing_interest(
    p_interest_id BIGINT,
    p_sender_user_id BIGINT
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_session_user_id BIGINT;
    v_interest public.interests%ROWTYPE;
BEGIN
    v_session_user_id := public.current_session_user_id();
    IF v_session_user_id IS NULL OR v_session_user_id <> p_sender_user_id THEN
        RETURN jsonb_build_object('code', 'NOT_INTEREST_OWNER');
    END IF;

    SELECT * INTO v_interest
    FROM public.interests
    WHERE id = p_interest_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RETURN jsonb_build_object('code', 'INTEREST_NOT_FOUND');
    END IF;

    IF v_interest."senderUserId" <> p_sender_user_id THEN
        RETURN jsonb_build_object('code', 'NOT_INTEREST_OWNER');
    END IF;

    IF v_interest.status <> 'PENDING' THEN
        RETURN jsonb_build_object('code', 'INTEREST_ALREADY_PROCESSED', 'currentStatus', v_interest.status);
    END IF;

    DELETE FROM public.notifications
    WHERE "relatedInterestId" = p_interest_id
      AND type = 'INCOMING_INTEREST';

    DELETE FROM public.notifications n
    USING public.interests i
    WHERE n."relatedInterestId" = i.id AND n.type = 'INCOMING_INTEREST'
      AND i."senderUserId" = p_sender_user_id AND i."targetItemId" = v_interest."targetItemId"
      AND i.status = 'INVALIDATED'
      AND NOT EXISTS (SELECT 1 FROM public.matches m WHERE m."sourceInterestId" = i.id);

    DELETE FROM public.interests i
    WHERE i."senderUserId" = p_sender_user_id AND i."targetItemId" = v_interest."targetItemId"
      AND i.status = 'INVALIDATED'
      AND NOT EXISTS (SELECT 1 FROM public.matches m WHERE m."sourceInterestId" = i.id)
      AND NOT EXISTS (SELECT 1 FROM public.notifications n WHERE n."relatedInterestId" = i.id);

    -- A withdrawn offer remains the latest row, preserving older match history without resurfacing it.
    UPDATE public.interests
    SET status = 'INVALIDATED', "respondedAt" = NULL
    WHERE id = p_interest_id;

    RETURN jsonb_build_object(
        'code', 'SUCCESS',
        'receiverUserId', v_interest."receiverUserId"
    );
END;
$$;

REVOKE ALL ON FUNCTION public.rpc_cancel_outgoing_interest(BIGINT, BIGINT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.rpc_cancel_outgoing_interest(BIGINT, BIGINT) TO anon, authenticated, service_role;

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



    WITH latest_interests AS (
        SELECT DISTINCT ON (i."targetItemId") i.*
        FROM public.interests i
        WHERE i."senderUserId" = v_user_id
        ORDER BY i."targetItemId", i."createdAt" DESC, i.id DESC
    )
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
                    'detailPhotoUri', ci."detailPhotoUri",
                    'extraPhotoUri', ci."extraPhotoUri",
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
                            ORDER BY
                                CASE m.status
                                    WHEN 'ACTIVE' THEN 0
                                    WHEN 'COMPLETED' THEN 1
                                    ELSE 2
                                END,
                                CASE WHEN m."sourceInterestId" = i.id THEN 0 ELSE 1 END,
                                pg_catalog.abs(m."createdAt" - i."createdAt") ASC,
                                m."updatedAt" DESC,
                                m.id DESC
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
                    ),
                    '[]'::jsonb
                )
            )
            ORDER BY i."createdAt" DESC, i.id DESC
        ),
        '[]'::jsonb
    )
    INTO v_interests
    FROM latest_interests i
    LEFT JOIN public.clothing_items ci ON ci.id = i."targetItemId"
    LEFT JOIN public.users cu ON cu.id = ci."ownerId"
    WHERE NOT (
        i.status = 'INVALIDATED' AND i."respondedAt" IS NULL
        AND NOT EXISTS (SELECT 1 FROM public.matches m WHERE m."sourceInterestId" = i.id)
    ) AND NOT (
        i.status = 'INVALIDATED' AND ci.status = 'AVAILABLE' AND ci."isBarterAllowed"
        AND NOT EXISTS (
            SELECT 1 FROM public.matches m WHERE m.status = 'ACTIVE'
            AND (m."user1ItemId" = ci.id OR m."user2ItemId" = ci.id)
        )
        AND NOT EXISTS (SELECT 1 FROM public.matches m WHERE m."sourceInterestId" = i.id)
    );

    SELECT COALESCE(jsonb_agg(DISTINCT item_id), '[]'::jsonb) INTO v_active_item_ids
    FROM (
        SELECT "user1ItemId" AS item_id FROM public.matches WHERE status = 'ACTIVE'
        UNION
        SELECT "user2ItemId" AS item_id FROM public.matches WHERE status = 'ACTIVE'
    ) t
    WHERE item_id IN (
        SELECT (entry->>'targetItemId')::BIGINT
        FROM pg_catalog.jsonb_array_elements(v_interests) entry
    );

    SELECT COALESCE(jsonb_agg(DISTINCT item_id), '[]'::jsonb) INTO v_other_active_item_ids
    FROM (
        SELECT "user1ItemId" AS item_id FROM public.matches WHERE status = 'ACTIVE' AND "user1Id" <> v_user_id AND "user2Id" <> v_user_id
        UNION
        SELECT "user2ItemId" AS item_id FROM public.matches WHERE status = 'ACTIVE' AND "user1Id" <> v_user_id AND "user2Id" <> v_user_id
    ) t
    WHERE item_id IN (
        SELECT (entry->>'targetItemId')::BIGINT
        FROM pg_catalog.jsonb_array_elements(v_interests) entry
    );

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
                    'detailPhotoUri', ci."detailPhotoUri",
                    'extraPhotoUri', ci."extraPhotoUri",
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
    WHERE sp."userId" = v_user_id AND ci.status <> 'HIDDEN_DELETED';

    SELECT COALESCE(jsonb_agg(DISTINCT item_id), '[]'::jsonb) INTO v_active_item_ids
    FROM (
        SELECT "user1ItemId" AS item_id FROM public.matches WHERE status = 'ACTIVE'
        UNION
        SELECT "user2ItemId" AS item_id FROM public.matches WHERE status = 'ACTIVE'
    ) t
    WHERE item_id IN (
        SELECT (entry->>'targetItemId')::BIGINT
        FROM pg_catalog.jsonb_array_elements(v_passes) entry
    );

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
BEGIN
    v_user_id := public.current_session_user_id();
    IF v_user_id IS NULL THEN
        RETURN jsonb_build_object('code', 'UNAUTHORIZED');
    END IF;

    SELECT "ownerId" INTO v_target_owner_id
    FROM public.clothing_items
    WHERE id = p_target_item_id
    LIMIT 1 FOR UPDATE;

    IF NOT FOUND OR v_target_owner_id = v_user_id THEN
        RETURN jsonb_build_object('code', 'INVALID_TARGET_ITEM');
    END IF;

    IF EXISTS (
        SELECT 1 FROM public.interests i
        WHERE i."senderUserId" = v_user_id AND i."targetItemId" = p_target_item_id
          AND i.status IN ('PENDING', 'DECLINED')
    ) OR EXISTS (
        SELECT 1 FROM public.matches m WHERE m.status = 'ACTIVE'
          AND (m."user1ItemId" = p_target_item_id OR m."user2ItemId" = p_target_item_id)
    ) OR NOT EXISTS (
        SELECT 1 FROM public.clothing_items c WHERE c.id = p_target_item_id
          AND c.status = 'AVAILABLE' AND c."isBarterAllowed"
    ) THEN
        RETURN jsonb_build_object('code', 'INVALID_TARGET_ITEM');
    END IF;

    v_now_ms := (EXTRACT(EPOCH FROM pg_catalog.now()) * 1000)::BIGINT;
    INSERT INTO public.swipe_passes ("userId", "targetItemId", "createdAt")
    VALUES (v_user_id, p_target_item_id, v_now_ms)
    ON CONFLICT ("userId", "targetItemId") DO NOTHING;

    RETURN jsonb_build_object('code', 'SUCCESS');
END;
$$;

REVOKE ALL ON FUNCTION public.rpc_pass_candidate(BIGINT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.rpc_pass_candidate(BIGINT) TO anon, authenticated, service_role;

COMMIT;
