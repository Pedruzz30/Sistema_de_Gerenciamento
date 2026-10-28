#!/usr/bin/env bash
# Verifies RLS with a real NON-OWNER role on disposable CI PostgreSQL only.
set -Eeuo pipefail
if [[ "$GITHUB_ACTIONS" != "true" || "$STOCKOS_TEST_DB_URL" != "jdbc:postgresql://127.0.0.1:5432/stockos_ci_test" || -z "$CI_POSTGRES_CONTAINER" ]]; then
  echo "REFUSED: use only disposable CI Postgres" >&2
  exit 2
fi
container="$CI_POSTGRES_CONTAINER"
if ! docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' "$container" | grep -Fxq 'POSTGRES_DB=stockos_ci_test'; then
  echo "REFUSED: incorrect disposable PostgreSQL container" >&2
  exit 2
fi
db=stockos_ci_saas
docker exec -i "$container" psql -X -q -v ON_ERROR_STOP=1 -U postgres -d "$db" < src/main/resources/db/saas-migration/V20261028_01__runtime_readonly_rls.sql
docker exec -i "$container" psql -X -q -v ON_ERROR_STOP=1 -U postgres -d "$db" <<'SQL'
INSERT INTO stockos_private.empresas (id,nome) VALUES
 ('aaaaaaaa-aaaa-4aaa-aaaa-aaaaaaaaaaaa','RLS A'),
 ('bbbbbbbb-bbbb-4bbb-bbbb-bbbbbbbbbbbb','RLS B');
INSERT INTO stockos_private.filiais(id,empresa_id,nome) VALUES
 ('aaaaaaaa-1111-4111-8111-aaaaaaaaaaaa','aaaaaaaa-aaaa-4aaa-aaaa-aaaaaaaaaaaa','A'),
 ('bbbbbbbb-1111-4111-8111-bbbbbbbbbbbb','bbbbbbbb-bbbb-4bbb-bbbb-bbbbbbbbbbbb','B');
INSERT INTO stockos_private.membros(empresa_id,auth_user_id,papel,ativo) VALUES
 ('aaaaaaaa-aaaa-4aaa-aaaa-aaaaaaaaaaaa','aaaaaaaa-2222-4222-8222-aaaaaaaaaaaa','PROPRIETARIO',true),
 ('bbbbbbbb-bbbb-4bbb-bbbb-bbbbbbbbbbbb','bbbbbbbb-2222-4222-8222-bbbbbbbbbbbb','PROPRIETARIO',true),
 ('bbbbbbbb-bbbb-4bbb-bbbb-bbbbbbbbbbbb','bbbbbbbb-3333-4333-8333-bbbbbbbbbbbb','ATENDENTE',false);
INSERT INTO stockos_private.produtos(empresa_id,filial_id,codigo,nome,preco_unitario,estoque_atual) VALUES
 ('aaaaaaaa-aaaa-4aaa-aaaa-aaaaaaaaaaaa','aaaaaaaa-1111-4111-8111-aaaaaaaaaaaa','A','Produto A',10,5),
 ('bbbbbbbb-bbbb-4bbb-bbbb-bbbbbbbbbbbb','bbbbbbbb-1111-4111-8111-bbbbbbbbbbbb','B','Produto B',10,5);
INSERT INTO stockos_private.comandas(empresa_id,filial_id,numero,aberta_por) VALUES
 ('aaaaaaaa-aaaa-4aaa-aaaa-aaaaaaaaaaaa','aaaaaaaa-1111-4111-8111-aaaaaaaaaaaa',1,'aaaaaaaa-2222-4222-8222-aaaaaaaaaaaa'),
 ('bbbbbbbb-bbbb-4bbb-bbbb-bbbbbbbbbbbb','bbbbbbbb-1111-4111-8111-bbbbbbbbbbbb',1,'bbbbbbbb-2222-4222-8222-bbbbbbbbbbbb');
INSERT INTO stockos_private.planos(codigo,nome,preco_mensal,max_filiais,max_usuarios)
  VALUES ('rls_ci','Plano RLS',0,2,20);
INSERT INTO stockos_private.licencas(empresa_id,plano_id,status,vigencia_inicio,vigencia_fim,valor_mensal_snapshot)
  SELECT e.id,p.id,'TRIAL',now(),now()+interval '1 day',0
  FROM stockos_private.empresas e CROSS JOIN stockos_private.planos p WHERE p.codigo='rls_ci';
DO $$
BEGIN
 IF (SELECT rolcanlogin OR rolsuper OR rolbypassrls OR rolcreatedb OR rolcreaterole
     FROM pg_roles WHERE rolname='stockos_runtime_reader') IS DISTINCT FROM false THEN
   RAISE EXCEPTION 'Unsafe runtime reader role';
 END IF;
 IF has_table_privilege('stockos_runtime_reader','stockos_private.produtos','INSERT')
    OR has_table_privilege('stockos_runtime_reader','stockos_private.produtos','UPDATE')
    OR has_table_privilege('stockos_runtime_reader','stockos_private.comandas','DELETE')
    OR has_table_privilege('stockos_runtime_reader','stockos_private.convites_funcionarios','SELECT') THEN
   RAISE EXCEPTION 'Reader has excessive permissions';
 END IF;
END $$;
SQL
# SET LOCAL ROLE ensures real RLS evaluation; SET LOCAL actor is scoped to transaction.
result="$(docker exec -i "$container" psql -X -Atq -v ON_ERROR_STOP=1 -U postgres -d "$db" <<'SQL'
BEGIN;
SET LOCAL ROLE stockos_runtime_reader;
SELECT 'EMPTY:' || count(*) FROM stockos_private.empresas;
SELECT 'EMPTYPLAN:' || count(*) FROM stockos_private.planos;
SELECT set_config('stockos.actor_id','aaaaaaaa-2222-4222-8222-aaaaaaaaaaaa',true);
SELECT 'A:' || count(*) FROM stockos_private.empresas;
SELECT 'APRODUCT:' || string_agg(nome,',') FROM stockos_private.produtos;
SELECT 'ATAB:' || count(*) FROM stockos_private.comandas;
SELECT 'ALICENSE:' || count(*) FROM stockos_private.licencas;
SELECT 'AOTHERMEMBER:' || count(*) FROM stockos_private.membros WHERE empresa_id='bbbbbbbb-bbbb-4bbb-bbbb-bbbbbbbbbbbb';
COMMIT;
BEGIN;
SET LOCAL ROLE stockos_runtime_reader;
SELECT 'AFTERCOMMIT:' || count(*) FROM stockos_private.empresas;
SELECT set_config('stockos.actor_id','bbbbbbbb-2222-4222-8222-bbbbbbbbbbbb',true);
SELECT 'B:' || count(*) FROM stockos_private.empresas;
SELECT 'BPRODUCT:' || string_agg(nome,',') FROM stockos_private.produtos;
SELECT set_config('stockos.actor_id','bbbbbbbb-3333-4333-8333-bbbbbbbbbbbb',true);
SELECT 'DISABLED:' || count(*) FROM stockos_private.empresas;
COMMIT;
SQL
)"
echo "$result"
for expected in 'EMPTY:0' 'EMPTYPLAN:0' 'A:1' 'APRODUCT:Produto A' 'ATAB:1' 'ALICENSE:1' 'AOTHERMEMBER:0' 'AFTERCOMMIT:0' 'B:1' 'BPRODUCT:Produto B' 'DISABLED:0'; do
 grep -Fxq "$expected" <<< "$result" || { echo "FAIL missing $expected" >&2; exit 1; }
done
for sql in "UPDATE stockos_private.produtos SET estoque_atual=500 WHERE codigo='A';" "SELECT * FROM stockos_private.convites_funcionarios LIMIT 1;"; do
 if docker exec -i "$container" psql -X -Atq -v ON_ERROR_STOP=1 -U postgres -d "$db" >/dev/null 2>&1 <<SQL
BEGIN;
SET LOCAL ROLE stockos_runtime_reader;
SELECT set_config('stockos.actor_id','aaaaaaaa-2222-4222-8222-aaaaaaaaaaaa',true);
$sql
COMMIT;
SQL
 then
   echo "FAIL: reader executed prohibited SQL" >&2
   exit 1
 fi
done
echo "PASS: nonowner RLS blocks cross-tenant, context leakage, mutations and invitations"
