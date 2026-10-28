-- Explicit deny for StockOS private schema.
-- Supabase's API roles must NEVER be able to use this schema, its tables,
-- sequences or functions through the Data API.
-- Run with the trusted migration owner; do not connect as the browser role.
REVOKE ALL PRIVILEGES ON SCHEMA stockos_private FROM PUBLIC;
REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA stockos_private FROM PUBLIC;
REVOKE ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA stockos_private FROM PUBLIC;
REVOKE ALL PRIVILEGES ON ALL FUNCTIONS IN SCHEMA stockos_private FROM PUBLIC;

-- Local PostgreSQL CI does not have the Supabase roles. Handle only the roles
-- that exist; do not CREATE or grant them.
DO $stockos_roles$
DECLARE target_role text;
BEGIN
  FOREACH target_role IN ARRAY ARRAY['anon','authenticated','service_role'] LOOP
    IF EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname=target_role) THEN
      EXECUTE format('REVOKE ALL PRIVILEGES ON SCHEMA stockos_private FROM %I',target_role);
      EXECUTE format('REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA stockos_private FROM %I',target_role);
      EXECUTE format('REVOKE ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA stockos_private FROM %I',target_role);
      EXECUTE format('REVOKE ALL PRIVILEGES ON ALL FUNCTIONS IN SCHEMA stockos_private FROM %I',target_role);
      EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA stockos_private REVOKE ALL ON TABLES FROM %I',target_role);
      EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA stockos_private REVOKE ALL ON SEQUENCES FROM %I',target_role);
    END IF;
  END LOOP;
END
$stockos_roles$;

-- These defaults apply to objects created later by the CURRENT migration
-- owner. Changes to the owner require re-auditing default privileges.
ALTER DEFAULT PRIVILEGES IN SCHEMA stockos_private REVOKE ALL ON TABLES FROM PUBLIC;
ALTER DEFAULT PRIVILEGES IN SCHEMA stockos_private REVOKE ALL ON SEQUENCES FROM PUBLIC;

-- Postgres owner/BYPASSRLS sessions still bypass row-level checks. Production
-- JDBC permissions and tenant-level RLS policies require separate validation;
-- DO NOT interpret this migration as proof of end-to-end tenant isolation.
