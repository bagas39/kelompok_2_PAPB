-- Bug #16: liking an item creates only a pending offer, including reciprocal likes.
-- Matches must be created by rpc_respond_to_interest_and_create_match after item selection.
-- Apply this entire file in Supabase SQL Editor; no existing offers or matches are removed.
BEGIN;
SET LOCAL lock_timeout = '10s';
SET LOCAL statement_timeout = '60s';

CREATE OR REPLACE FUNCTION public.rpc_send_interest(
    p_sender_user_id BIGINT,
    p_target_item_id BIGINT,
    p_now_millis BIGINT,
    p_delete_swipe_pass BOOLEAN DEFAULT FALSE
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
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

    RETURN jsonb_build_object('code', 'SUCCESS', 'interestId', v_interest_id, 'isMutualMatch', FALSE);
END;
$$;

COMMIT;

SELECT 'explicit_match_confirmation_ready' AS result;
