BEGIN;
SET LOCAL lock_timeout = '10s';
SET LOCAL statement_timeout = '60s';

-- 1. RPC Validasi Token Sesi Aktif (Bug #15)
CREATE OR REPLACE FUNCTION public.rpc_validate_session()
RETURNS JSONB
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_user_id BIGINT;
BEGIN
    v_user_id := public.current_session_user_id();
    IF v_user_id IS NULL THEN
        RETURN jsonb_build_object('code', 'INVALID_SESSION');
    END IF;

    RETURN jsonb_build_object('code', 'VALID', 'userId', v_user_id);
END;
$$;

GRANT EXECUTE ON FUNCTION public.rpc_validate_session() TO anon, authenticated, service_role;

-- 2. Bersihkan Ghost Unread Notification pada Penyelesaian Barter (Bug #19)
CREATE OR REPLACE FUNCTION public.rpc_complete_match_exchange(
    p_match_id BIGINT,
    p_actor_user_id BIGINT,
    p_meeting_location TEXT,
    p_now_millis BIGINT
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_session_user_id BIGINT;
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

    IF p_actor_user_id <> v_match."user1Id" AND p_actor_user_id <> v_match."user2Id" THEN
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

    UPDATE public.matches
    SET status = 'COMPLETED',
        "meetingLocation" = NULLIF(BTRIM(p_meeting_location), ''),
        "updatedAt" = p_now_millis
    WHERE id = p_match_id;

    UPDATE public.clothing_items
    SET status = 'EXCHANGED', "updatedAt" = p_now_millis
    WHERE id IN (v_match."user1ItemId", v_match."user2ItemId");

    WITH invalidated AS (
        UPDATE public.interests
        SET status = 'INVALIDATED', "respondedAt" = p_now_millis
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

    v_counterpart_id := CASE WHEN p_actor_user_id = v_match."user1Id" THEN v_match."user2Id" ELSE v_match."user1Id" END;

    INSERT INTO public.notifications ("userId", type, title, body, "relatedInterestId", "relatedMatchId", "isRead", "createdAt")
    VALUES (
        v_counterpart_id,
        'EXCHANGE_COMPLETED',
        'Pertukaran selesai',
        'Pasangan pakaian telah ditandai sudah ditukar. Berikan rating & ulasan.',
        v_match."sourceInterestId",
        p_match_id,
        FALSE,
        p_now_millis
    );

    RETURN jsonb_build_object('code', 'SUCCESS');
END;
$$;

-- 3. Bersihkan Ghost Unread Notification pada Soft-Delete Pakaian (Bug #19)
CREATE OR REPLACE FUNCTION public.rpc_soft_delete_clothing_protected(
    p_item_id BIGINT,
    p_actor_user_id BIGINT,
    p_now_millis BIGINT
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_session_user_id BIGINT;
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

    IF v_existing."ownerId" <> p_actor_user_id THEN
        RETURN jsonb_build_object('code', 'INVALID_OWNERSHIP');
    END IF;

    SELECT COUNT(*) INTO v_active_match_count
    FROM public.matches
    WHERE status = 'ACTIVE' AND ("user1ItemId" = p_item_id OR "user2ItemId" = p_item_id);

    IF v_active_match_count > 0 THEN
        RETURN jsonb_build_object('code', 'LOCKED_IN_ACTIVE_MATCH');
    END IF;

    UPDATE public.clothing_items
    SET status = 'HIDDEN_DELETED',
        "isBarterAllowed" = FALSE,
        "updatedAt" = p_now_millis
    WHERE id = p_item_id;

    WITH invalidated AS (
        UPDATE public.interests
        SET status = 'INVALIDATED', "respondedAt" = p_now_millis
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

-- Bersihkan notifikasi INCOMING_INTEREST lama yang interest-nya sudah tidak PENDING
UPDATE public.notifications n
SET "isRead" = TRUE
FROM public.interests i
WHERE n.type = 'INCOMING_INTEREST'
  AND n."isRead" = FALSE
  AND n."relatedInterestId" = i.id
  AND i.status <> 'PENDING';

-- 4. Perketat RLS & Pembatasan Kolom REST untuk Cegah Bypass RPC (Bug #13)
DROP POLICY IF EXISTS "clothing_items_update_own_only" ON public.clothing_items;
REVOKE UPDATE, DELETE ON public.clothing_items FROM anon, authenticated;

REVOKE INSERT, UPDATE, DELETE ON public.matches FROM anon, authenticated;
GRANT UPDATE ("updatedAt") ON public.matches TO anon, authenticated;
DROP POLICY IF EXISTS "matches_update_participant_only" ON public.matches;
CREATE POLICY "matches_update_participant_only" ON public.matches FOR UPDATE
USING (
    status = 'ACTIVE'
    AND ("user1Id" = public.current_session_user_id() OR "user2Id" = public.current_session_user_id())
)
WITH CHECK (
    status = 'ACTIVE'
    AND ("user1Id" = public.current_session_user_id() OR "user2Id" = public.current_session_user_id())
);

REVOKE INSERT, UPDATE, DELETE ON public.users FROM anon, authenticated;
GRANT UPDATE (
    name, bio, "avatarUri", city, latitude, longitude,
    "preferredSize", "preferredGender", "preferredRadiusKm"
) ON public.users TO anon, authenticated;

REVOKE UPDATE, DELETE ON public.chat_messages FROM anon, authenticated;
GRANT UPDATE ("isRead") ON public.chat_messages TO anon, authenticated;
DROP POLICY IF EXISTS "chat_messages_update_participant_only" ON public.chat_messages;
CREATE POLICY "chat_messages_update_participant_only" ON public.chat_messages FOR UPDATE
USING (
    EXISTS (
        SELECT 1 FROM public.matches m
        WHERE m.id = chat_messages."matchId"
          AND (m."user1Id" = public.current_session_user_id() OR m."user2Id" = public.current_session_user_id())
    )
)
WITH CHECK (
    EXISTS (
        SELECT 1 FROM public.matches m
        WHERE m.id = chat_messages."matchId"
          AND (m."user1Id" = public.current_session_user_id() OR m."user2Id" = public.current_session_user_id())
    )
);

REVOKE INSERT, UPDATE, DELETE ON public.notifications FROM anon, authenticated;
GRANT UPDATE ("isRead") ON public.notifications TO anon, authenticated;

REVOKE INSERT, UPDATE, DELETE ON public.interests FROM anon, authenticated;
REVOKE INSERT, UPDATE, DELETE ON public.reviews FROM anon, authenticated;
REVOKE ALL ON public.user_sessions FROM anon, authenticated;
REVOKE ALL ON public.password_reset_otps FROM anon, authenticated;

COMMIT;
