BEGIN;

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

    IF EXISTS (
        SELECT 1 FROM public.interests i
        WHERE i."senderUserId" = v_session_user_id AND i."targetItemId" = p_target_item_id
          AND i.status = 'PENDING'
    ) THEN
        RETURN jsonb_build_object('code', 'ALREADY_INTERESTED', 'existingStatus', 'PENDING');
    END IF;

    SELECT * INTO v_existing FROM public.interests
    WHERE "senderUserId" = v_session_user_id AND "targetItemId" = p_target_item_id
    ORDER BY "createdAt" DESC, id DESC
    LIMIT 1;

    IF v_existing.status = 'DECLINED' THEN
        RETURN jsonb_build_object('code', 'ALREADY_INTERESTED', 'existingStatus', 'DECLINED');
    END IF;

    SELECT COUNT(*) INTO v_non_cancelled_match_count
    FROM public.matches m
    WHERE m.status IN ('ACTIVE', 'COMPLETED')
      AND (m."user1Id" = v_session_user_id OR m."user2Id" = v_session_user_id)
      AND (m."user1ItemId" = p_target_item_id OR m."user2ItemId" = p_target_item_id);

    IF v_non_cancelled_match_count > 0 THEN
        RETURN jsonb_build_object('code', 'ALREADY_INTERESTED', 'existingStatus', 'RESOLVED_BY_MATCH');
    END IF;

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
                    i.status = 'PENDING'
                    OR i.id = (
                        SELECT latest.id FROM public.interests latest
                        WHERE latest."senderUserId" = v_user_id AND latest."targetItemId" = c.id
                        ORDER BY latest."createdAt" DESC, latest.id DESC LIMIT 1
                    )
                )
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
          AND (
              i.status = 'PENDING'
              OR (i.status = 'DECLINED' AND i.id = (
                  SELECT latest.id FROM public.interests latest
                  WHERE latest."senderUserId" = v_user_id AND latest."targetItemId" = p_target_item_id
                  ORDER BY latest."createdAt" DESC, latest.id DESC LIMIT 1
              ))
          )
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
