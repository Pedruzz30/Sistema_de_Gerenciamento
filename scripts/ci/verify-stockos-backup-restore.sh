#!/usr/bin/env bash
# Strictly for CI's disposable local PostgreSQL service, never a Supabase project.
set -Eeuo pipefail
: "${CI_POSTGRES_CONTAINER:?GitHub Actions PostgreSQL service container id required}"
: "${STOCKOS_TEST_DB_URL:?Explicit test URL required}"
if [[ "${GITHUB_ACTIONS:-}" != "true" || "${STOCKOS_TEST_DB_URL}" != "jdbc:postgresql://127.0.0.1:5432/stockos_ci_test" ]]; then
  echo "REFUSED: backup/restore verification may only run against CI's local stockos_ci_test" >&2
  exit 2
fi
if ! docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' "$CI_POSTGRES_CONTAINER" |
     grep -Fxq 'POSTGRES_DB=stockos_ci_test'; then
  echo "REFUSED: service is not the disposable stockos_ci_test container" >&2
  exit 2
fi
dump="$RUNNER_TEMP/stockos-ci-backup.dump"
trap 'rm -f "$dump"' EXIT
docker exec "$CI_POSTGRES_CONTAINER" pg_dump -U postgres -Fc -d stockos_ci_test > "$dump"
test -s "$dump"
docker exec "$CI_POSTGRES_CONTAINER" createdb -U postgres stockos_ci_restore
docker exec -i "$CI_POSTGRES_CONTAINER" pg_restore -U postgres --no-owner --no-acl      -d stockos_ci_restore < "$dump"
query="select
 (select count(*) from stockos_private.empresas)::text || '|' ||
 (select count(*) from stockos_private.filiais)::text || '|' ||
 (select count(*) from stockos_private.produtos)::text || '|' ||
 (select count(*) from stockos_private.comandas)::text || '|' ||
 (select count(*) from stockos_private.pagamentos)::text || '|' ||
 (select count(*) from stockos_private.entradas_mercadoria)::text || '|' ||
 (select count(*) from stockos_private.ajustes_inventario)::text || '|' ||
 (select count(*) from flyway_schema_history)::text;"
before=$(docker exec "$CI_POSTGRES_CONTAINER" psql -U postgres -d stockos_ci_test -Atqc "$query")
after=$(docker exec "$CI_POSTGRES_CONTAINER" psql -U postgres -d stockos_ci_restore -Atqc "$query")
if [[ "$before" != "$after" ]]; then
  echo "FAIL: restored database does not match original record counts" >&2
  exit 1
fi
# Nonempty fixture data proves data, not merely schema, was restored.
IFS='|' read -r company_count _ <<< "$after"
if [[ "$company_count" -lt 1 ]]; then
  echo "FAIL: no company fixture survived restore" >&2
  exit 1
fi
echo "PASS: isolated PostgreSQL full backup and restore, Flyway history and SaaS data counts match."
