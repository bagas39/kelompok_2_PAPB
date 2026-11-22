BEGIN;
SET LOCAL lock_timeout = '10s';
SET LOCAL statement_timeout = '60s';

-- 1. #16: Hapus silent auto-match dari rpc_send_interest & kembalikan receiverUserId untuk realtime (#23)
-- Tetap pertahankan dukungan Multi-Match Paralel (#11) tanpa batas v_active_user_pair_count
CREATE OR REPLACE FUNCTION public.rpc_send_interest(
    p_sender_user_id BIGINT,
    p_target_item_id BIGINT,
    p_now_millis BIGINT,
    p_delete_swipe_pass BOOLEAN DEFAULT FALSE
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_session_user_id BIGINT;
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

    SELECT * INTO v_sender FROM public.users WHERE id = p_sender_user_id;
    IF NOT FOUND THEN
        RETURN jsonb_build_object('code', 'SENDER_NOT_FOUND');
    END IF;

    SELECT * INTO v_target FROM public.clothing_items WHERE id = p_target_item_id FOR UPDATE;
    IF NOT FOUND THEN
        RETURN jsonb_build_object('code', 'TARGET_ITEM_NOT_FOUND');
    END IF;

    IF v_target."ownerId" = p_sender_user_id THEN
        RETURN jsonb_build_object('code', 'CANNOT_INTEREST_OWN_ITEM');
    END IF;

    FOR v_existing IN
        SELECT *
        FROM public.interests
        WHERE "senderUserId" = p_sender_user_id AND "targetItemId" = p_target_item_id
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
                      AND (m."user1Id" = p_sender_user_id OR m."user2Id" = p_sender_user_id)
                  )
              );

            IF v_non_cancelled_match_count > 0 THEN
                RETURN jsonb_build_object('code', 'ALREADY_INTERESTED', 'existingStatus', v_existing.status);
            END IF;
        END IF;
    END LOOP;

    SELECT COUNT(*) INTO v_eligible_count
    FROM public.clothing_items c
    WHERE c."ownerId" = p_sender_user_id
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

    INSERT INTO public.interests ("senderUserId", "receiverUserId", "targetItemId", status, "createdAt", "respondedAt")
    VALUES (p_sender_user_id, v_target."ownerId", p_target_item_id, 'PENDING', p_now_millis, NULL)
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
        p_now_millis
    );

    IF p_delete_swipe_pass THEN
        DELETE FROM public.swipe_passes
        WHERE "userId" = p_sender_user_id AND "targetItemId" = p_target_item_id;
    END IF;

    RETURN jsonb_build_object(
        'code', 'SUCCESS',
        'interestId', v_interest_id,
        'isMutualMatch', FALSE,
        'receiverUserId', v_target."ownerId"
    );
END;
$$;

-- 2. #18 (RPC): Kirim notifikasi penolakan ke pengirim & kembalikan senderUserId untuk realtime (#23)
CREATE OR REPLACE FUNCTION public.rpc_decline_incoming_interest(
    p_actor_user_id BIGINT,
    p_interest_id BIGINT,
    p_now_millis BIGINT
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_session_user_id BIGINT;
    v_interest public.interests%ROWTYPE;
    v_actor_name TEXT;
    v_target_item_name TEXT;
BEGIN
    v_session_user_id := public.current_session_user_id();
    IF v_session_user_id IS NULL OR v_session_user_id <> p_actor_user_id THEN
        RETURN jsonb_build_object('code', 'INTEREST_ALREADY_PROCESSED_OR_INVALID');
    END IF;

    SELECT * INTO v_interest FROM public.interests WHERE id = p_interest_id FOR UPDATE;
    IF NOT FOUND OR v_interest."receiverUserId" <> p_actor_user_id OR v_interest.status <> 'PENDING' THEN
        RETURN jsonb_build_object('code', 'INTEREST_ALREADY_PROCESSED_OR_INVALID');
    END IF;

    UPDATE public.interests
    SET status = 'DECLINED',
        "respondedAt" = p_now_millis
    WHERE id = p_interest_id;

    UPDATE public.notifications
    SET "isRead" = TRUE
    WHERE "relatedInterestId" = p_interest_id
      AND type = 'INCOMING_INTEREST'
      AND "userId" = p_actor_user_id;

    SELECT COALESCE(NULLIF(BTRIM(name), ''), 'Pemilik pakaian')
    INTO v_actor_name
    FROM public.users
    WHERE id = p_actor_user_id;

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
        p_now_millis
    );

    RETURN jsonb_build_object(
        'code', 'SUCCESS',
        'senderUserId', v_interest."senderUserId"
    );
END;
$$;

-- 3. #23: Kembalikan receiverUserId dari rpc_cancel_outgoing_interest untuk broadcast realtime
CREATE OR REPLACE FUNCTION public.rpc_cancel_outgoing_interest(
    p_interest_id BIGINT,
    p_sender_user_id BIGINT
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
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

    DELETE FROM public.interests
    WHERE id = p_interest_id;

    RETURN jsonb_build_object(
        'code', 'SUCCESS',
        'receiverUserId', v_interest."receiverUserId"
    );
END;
$$;

GRANT EXECUTE ON FUNCTION public.rpc_send_interest(BIGINT, BIGINT, BIGINT, BOOLEAN) TO anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.rpc_decline_incoming_interest(BIGINT, BIGINT, BIGINT) TO anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.rpc_cancel_outgoing_interest(BIGINT, BIGINT) TO anon, authenticated, service_role;

COMMIT;
