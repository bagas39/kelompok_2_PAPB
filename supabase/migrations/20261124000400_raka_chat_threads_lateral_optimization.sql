BEGIN;

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
          AND (
              p_partner_user_id IS NULL
              OR (CASE WHEN m."user1Id" = v_user_id THEN m."user2Id" ELSE m."user1Id" END) = p_partner_user_id
          )
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
        LEFT JOIN LATERAL (
            SELECT AVG(r.rating)::double precision AS avg_rating
            FROM public.reviews r
            WHERE r."revieweeId" = mb.partner_user_id
        ) pr ON TRUE
        LEFT JOIN LATERAL (
            SELECT cm."messageText", cm."sentAt"
            FROM public.chat_messages cm
            WHERE cm."matchId" = mb.match_id
            ORDER BY cm."sentAt" DESC, cm.id DESC
            LIMIT 1
        ) lm ON TRUE
        LEFT JOIN LATERAL (
            SELECT COUNT(*)::integer AS unread_count
            FROM public.chat_messages cm2
            WHERE cm2."matchId" = mb.match_id
              AND cm2."senderId" <> v_user_id
              AND cm2."isRead" = FALSE
        ) uc ON TRUE
    ),
    filtered AS (
        SELECT *
        FROM enriched
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

COMMIT;
