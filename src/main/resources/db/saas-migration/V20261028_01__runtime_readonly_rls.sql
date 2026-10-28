-- StockOS runtime READ ONLY, restricted to the private tenant schema.
-- NOLOGIN; direct client or browser connections must never use this role.
-- IMPORTANT: custom GUCs are mutable SQL session state, not signed claims.
-- Only set stockos.actor_id from a verified JWT in Java, inside same transaction.
DO $stockos_create_role$
DECLARE existing pg_catalog.pg_roles%ROWTYPE;
BEGIN
  SELECT * INTO existing FROM pg_catalog.pg_roles WHERE rolname='stockos_runtime_reader';
  IF NOT FOUND THEN
    CREATE ROLE stockos_runtime_reader NOLOGIN NOSUPERUSER NOCREATEDB
      NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS;
  ELSIF existing.rolcanlogin OR existing.rolsuper OR existing.rolbypassrls
        OR existing.rolcreatedb OR existing.rolcreaterole THEN
    RAISE EXCEPTION 'Refusing unsafe preexisting stockos_runtime_reader role';
  END IF;
END
$stockos_create_role$;

GRANT USAGE ON SCHEMA stockos_private TO stockos_runtime_reader;
GRANT SELECT ON TABLE
  stockos_private.empresas, stockos_private.filiais,
  stockos_private.membros, stockos_private.planos,
  stockos_private.licencas, stockos_private.produtos,
  stockos_private.comandas
TO stockos_runtime_reader;

-- The self-only members policy avoids a self-referential RLS recursion.
CREATE POLICY stockos_reader_membros_self ON stockos_private.membros
  FOR SELECT TO stockos_runtime_reader USING (
    ativo AND auth_user_id = nullif(current_setting('stockos.actor_id',true),'')::uuid
  );

CREATE POLICY stockos_reader_empresas ON stockos_private.empresas
  FOR SELECT TO stockos_runtime_reader USING (
    ativa AND EXISTS (
      SELECT 1 FROM stockos_private.membros m
      WHERE m.empresa_id=empresas.id
        AND m.auth_user_id=nullif(current_setting('stockos.actor_id',true),'')::uuid
        AND m.ativo
    )
  );
CREATE POLICY stockos_reader_filiais ON stockos_private.filiais
  FOR SELECT TO stockos_runtime_reader USING (
    ativa AND EXISTS (
      SELECT 1 FROM stockos_private.membros m
      JOIN stockos_private.empresas e ON e.id=m.empresa_id
      WHERE m.empresa_id=filiais.empresa_id
        AND m.auth_user_id=nullif(current_setting('stockos.actor_id',true),'')::uuid
        AND m.ativo AND e.ativa
    )
  );
CREATE POLICY stockos_reader_produtos ON stockos_private.produtos
  FOR SELECT TO stockos_runtime_reader USING (
    EXISTS (
      SELECT 1 FROM stockos_private.membros m
      JOIN stockos_private.empresas e ON e.id=m.empresa_id
      WHERE m.empresa_id=produtos.empresa_id
        AND m.auth_user_id=nullif(current_setting('stockos.actor_id',true),'')::uuid
        AND m.ativo AND e.ativa
    )
  );
CREATE POLICY stockos_reader_comandas ON stockos_private.comandas
  FOR SELECT TO stockos_runtime_reader USING (
    EXISTS (
      SELECT 1 FROM stockos_private.membros m
      JOIN stockos_private.empresas e ON e.id=m.empresa_id
      WHERE m.empresa_id=comandas.empresa_id
        AND m.auth_user_id=nullif(current_setting('stockos.actor_id',true),'')::uuid
        AND m.ativo AND e.ativa
    )
  );
CREATE POLICY stockos_reader_licencas ON stockos_private.licencas
  FOR SELECT TO stockos_runtime_reader USING (
    EXISTS (
      SELECT 1 FROM stockos_private.membros m
      JOIN stockos_private.empresas e ON e.id=m.empresa_id
      WHERE m.empresa_id=licencas.empresa_id
        AND m.auth_user_id=nullif(current_setting('stockos.actor_id',true),'')::uuid
        AND m.ativo AND e.ativa
    )
  );
-- Plan catalog shared only with employees of at least one active company.
CREATE POLICY stockos_reader_planos ON stockos_private.planos
  FOR SELECT TO stockos_runtime_reader USING (
    EXISTS (
      SELECT 1 FROM stockos_private.membros m
      JOIN stockos_private.empresas e ON e.id=m.empresa_id
      WHERE m.auth_user_id=nullif(current_setting('stockos.actor_id',true),'')::uuid
        AND m.ativo AND e.ativa
    )
  );
