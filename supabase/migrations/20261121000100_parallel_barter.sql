-- Allow simultaneous exchanges between the same users, with exclusive item reservations.
-- Run the entire file in Supabase SQL Editor. All changes commit or roll back together.
BEGIN;
SET LOCAL lock_timeout = '10s';
SET LOCAL statement_timeout = '60s';

-- Refuse to hide or delete any existing inconsistent transactions.
DO $preflight$
BEGIN
    IF EXISTS (
        SELECT item_id
        FROM public.matches m
        CROSS JOIN LATERAL (VALUES (m."user1ItemId"), (m."user2ItemId")) AS items(item_id)
        WHERE m.status = 'ACTIVE'
        GROUP BY item_id
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'An item is already used by multiple active matches. Resolve those matches before retrying.';
    END IF;
END;
$preflight$;

-- One constraint covers BOTH item columns, including reversed item positions.
-- Two separate UNIQUE indexes would not protect cross-column reuse.
DO $constraint$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.matches'::regclass
          AND conname = 'matches_active_items_excl'
    ) THEN
        ALTER TABLE public.matches
        ADD CONSTRAINT matches_active_items_excl
        EXCLUDE USING gist (
            (int8multirange(
                int8range("user1ItemId", "user1ItemId", '[]'),
                int8range("user2ItemId", "user2ItemId", '[]')
            )) WITH &&
        ) WHERE (status = 'ACTIVE');
    END IF;
END;
$constraint$;

-- Patch the inspected server functions in place to retain their auth checks,
-- notifications, return values, grants, and any existing deployment settings.
DO $functions$
DECLARE
    v_signature text;
    v_definition text;
    v_changed text;
    v_guard_pattern text := $pattern$SELECT[[:space:]]+COUNT\(\*\)[[:space:]]+INTO[[:space:]]+v_active_user_pair_count[^;]*;[[:space:]]*IF[[:space:]]+v_active_user_pair_count[[:space:]]*>[[:space:]]*0[[:space:]]+THEN[[:space:]]+RETURN[[:space:]]+jsonb_build_object\('code',[[:space:]]*'PAIR_ALREADY_MATCHED'\);[[:space:]]*END IF;$pattern$;
    v_reverse_old text := $old$    SELECT * INTO v_reverse_interest
    FROM public.interests
    WHERE "senderUserId" = v_target."ownerId"
      AND "receiverUserId" = p_sender_user_id
      AND status = 'PENDING'
    ORDER BY "createdAt" DESC
    LIMIT 1
    FOR UPDATE;$old$;
    v_reverse_new text := $new$    SELECT i.* INTO v_reverse_interest
    FROM public.interests i
    JOIN public.clothing_items c ON c.id = i."targetItemId"
    WHERE i."senderUserId" = v_target."ownerId"
      AND i."receiverUserId" = p_sender_user_id
      AND i.status = 'PENDING'
      AND c."ownerId" = p_sender_user_id
      AND c.status = 'AVAILABLE'
      AND c."isBarterAllowed" = TRUE
      AND NOT EXISTS (
          SELECT 1 FROM public.matches m
          WHERE m.status = 'ACTIVE'
            AND (m."user1ItemId" = c.id OR m."user2ItemId" = c.id)
      )
    ORDER BY i."createdAt" DESC, i.id DESC
    LIMIT 1
    FOR UPDATE OF i;$new$;
    v_handler text;
BEGIN
    FOREACH v_signature IN ARRAY ARRAY[
        'public.rpc_send_interest(bigint,bigint,bigint,boolean)',
        'public.rpc_respond_to_interest_and_create_match(bigint,bigint,bigint,bigint)'
    ] LOOP
        IF to_regprocedure(v_signature) IS NULL THEN
            RAISE EXCEPTION 'Expected function is missing: %', v_signature;
        END IF;
        SELECT pg_get_functiondef(to_regprocedure(v_signature)) INTO v_definition;
        v_changed := v_definition;

        IF strpos(v_changed, 'v_active_user_pair_count') > 0 THEN
            v_changed := regexp_replace(v_changed, v_guard_pattern, '');
            IF v_changed = v_definition THEN
                RAISE EXCEPTION 'User-pair guard differs from the inspected version: %', v_signature;
            END IF;
            v_changed := regexp_replace(v_changed,
                '[[:space:]]+v_active_user_pair_count[[:space:]]+INTEGER;', '', 'i');
            IF strpos(v_changed, 'v_active_user_pair_count') > 0 THEN
                RAISE EXCEPTION 'Unrecognized remaining user-pair guard: %', v_signature;
            END IF;
        END IF;

        IF v_signature LIKE 'public.rpc_send_interest(%' THEN
            IF strpos(v_changed, v_reverse_old) > 0 THEN
                v_changed := replace(v_changed, v_reverse_old, v_reverse_new);
            ELSIF strpos(v_changed, v_reverse_new) = 0 THEN
                RAISE EXCEPTION 'Reverse-interest query differs from the inspected version';
            END IF;
        END IF;

        IF strpos(v_changed, '-- parallel-barter: handle item reservation conflicts') = 0 THEN
            IF v_changed ~ E'\\n[[:space:]]*EXCEPTION[[:space:]]' THEN
                RAISE EXCEPTION 'Unexpected existing exception handler: %', v_signature;
            END IF;
            v_changed := replace(v_changed, E'DECLARE\n', E'DECLARE\n    v_constraint_name TEXT;\n');
            IF strpos(v_changed, 'v_constraint_name TEXT;') = 0 THEN
                RAISE EXCEPTION 'Unexpected declaration layout: %', v_signature;
            END IF;
            v_handler := $handler$-- parallel-barter: handle item reservation conflicts
EXCEPTION WHEN exclusion_violation THEN
    GET STACKED DIAGNOSTICS v_constraint_name = CONSTRAINT_NAME;
    IF v_constraint_name <> 'matches_active_items_excl' THEN
        RAISE;
    END IF;
    RETURN jsonb_build_object('code', $handler$ ||
                CASE WHEN v_signature LIKE 'public.rpc_send_interest(%'
                     THEN quote_literal('TARGET_ITEM_NOT_ELIGIBLE')
                     ELSE quote_literal('ITEM_ALREADY_IN_ACTIVE_MATCH') END || E');\nEND;\n$function$';
            v_definition := v_changed;
            v_changed := regexp_replace(v_changed,
                'END;[[:space:]]*\$function\$', v_handler);
            IF v_changed = v_definition THEN
                RAISE EXCEPTION 'Unexpected function ending: %', v_signature;
            END IF;
        END IF;
        EXECUTE v_changed;
    END LOOP;
END;
$functions$;

-- Remove only the obsolete user-pair limit; item/source-interest protection stays.
DROP INDEX IF EXISTS public.index_matches_unique_active_user_pair;
COMMIT;

SELECT 'parallel_barter_ready' AS result,
       EXISTS (
           SELECT 1 FROM pg_constraint
           WHERE conrelid = 'public.matches'::regclass
             AND conname = 'matches_active_items_excl'
       ) AS item_lock_enabled,
       to_regclass('public.index_matches_unique_active_user_pair') IS NULL AS user_pair_limit_removed;
