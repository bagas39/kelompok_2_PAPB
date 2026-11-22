BEGIN;

DROP FUNCTION IF EXISTS public.rpc_get_users_coarse_locations(BIGINT[]);

REVOKE ALL ON ALL TABLES IN SCHEMA public FROM PUBLIC, anon, authenticated;
REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM PUBLIC, anon, authenticated;

ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON TABLES FROM PUBLIC, anon, authenticated;
ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON SEQUENCES FROM PUBLIC, anon, authenticated;

DO $$
DECLARE
    v_tbl TEXT;
BEGIN
    FOR v_tbl IN
        SELECT tablename
        FROM pg_publication_tables
        WHERE pubname = 'supabase_realtime'
          AND schemaname = 'public'
    LOOP
        EXECUTE pg_catalog.format('ALTER PUBLICATION supabase_realtime DROP TABLE %I.%I', 'public', v_tbl);
    END LOOP;
END;
$$;

COMMIT;
