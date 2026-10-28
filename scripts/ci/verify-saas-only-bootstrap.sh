#!/usr/bin/env bash
# CI-only: reproduce the Supabase SaaS schema WITHOUT running any legacy migration.
set -Eeuo pipefail
: "${CI_POSTGRES_CONTAINER:?The disposable PostgreSQL service id is required}"
if [[ "${GITHUB_ACTIONS:-}" != "true" ||
      "${STOCKOS_TEST_DB_URL:-}" != "jdbc:postgresql://127.0.0.1:5432/stockos_ci_test" ]]; then
  echo 'REFUSED: only CI disposable local postgres is allowed' >&2
  exit 2
fi
if ! docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' "$CI_POSTGRES_CONTAINER" |
     grep -Fxq 'POSTGRES_DB=stockos_ci_test'; then
  echo 'REFUSED: PostgreSQL service identity mismatch' >&2
  exit 2
fi
db=stockos_ci_saas
docker exec "$CI_POSTGRES_CONTAINER" createdb -U postgres "$db"
readonly migrations=(
  V20261026_02__saas_core_comandas.sql
  V20261026_03__saas_licenses.sql
  V20261026_04__admin_audit.sql
  V20261027_01__saas_cash_shifts.sql
  V20261027_02__saas_payment_reversals.sql
  V20261027_03__closed_sale_manual_refunds.sql
  V20261027_04__physical_stock_returns.sql
  V20261027_05__saas_inventory_stocktakes.sql
  V20261027_06__saas_goods_receipts.sql
  V20261027_07__member_management_audit.sql
  V20261027_08__product_catalog_audit.sql
  V20261027_09__verified_employee_invites.sql
  V20261027_10__private_schema_access_guard.sql
)
for file in "${migrations[@]}"; do
  path="src/main/resources/db/migration/$file"
  [[ -f "$path" ]] || { echo "MISSING MIGRATION: $path" >&2; exit 2; }
  docker exec -i "$CI_POSTGRES_CONTAINER" psql -X -v ON_ERROR_STOP=1 -U postgres -d "$db" < "$path" > /dev/null
done
query="select
  (select count(*) from pg_namespace where nspname='stockos_private')::text || '|' ||
  (select count(*) from pg_class c join pg_namespace n on n.oid=c.relnamespace
     where n.nspname='stockos_private' and c.relkind='r')::text || '|' ||
  (select count(*) from information_schema.tables where table_schema='public')::text || '|' ||
  (select count(*) from pg_class c join pg_namespace n on n.oid=c.relnamespace
     where n.nspname='stockos_private' and c.relkind='r' and not c.relrowsecurity)::text;"
actual=$(docker exec "$CI_POSTGRES_CONTAINER" psql -X -Atq -U postgres -d "$db" -c "$query")
if [[ "$actual" != "1|23|0|0" ]]; then
  echo "FAIL: SaaS-only schema expected 1|23|0|0 but got $actual" >&2
  exit 1
fi
echo "PASS: 13 SaaS migrations applied, 23 RLS tables, 0 public legacy tables on disposable CI database."
