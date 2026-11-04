import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const sql = readFileSync(new URL('./01-provision-roles.psql', import.meta.url), 'utf8');
const patterns = [
  /\\set ON_ERROR_STOP on/,
  /\\set ON_ERROR_ROLLBACK off/,
  /BEGIN;/,
  /current_database\(\)\s*<>\s*'stockos_java_staging_temp'/,
  /RAISE EXCEPTION 'Refusing provisioning outside/,
  /rolname IN \('stockos_app', 'stockos_migrator'\)/,
  /RAISE EXCEPTION 'One or both StockOS roles already exist/,
  /CREATE ROLE stockos_migrator NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS/,
  /CREATE ROLE stockos_app NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS/,
  /SET LOCAL password_encryption = 'scram-sha-256'/,
  /\\password stockos_migrator/,
  /\\password stockos_app/,
  /rolpassword IS NULL/,
  /ALTER ROLE stockos_migrator LOGIN/,
  /ALTER ROLE stockos_app LOGIN/,
  /COMMIT;/
];
for (const pattern of patterns) {
  assert.match(sql, pattern, 'Missing fail-closed provisioning step: ' + pattern);
}
const before = needle => sql.indexOf(needle);
assert.ok(before('BEGIN;') < before('CREATE ROLE stockos_migrator'));
assert.ok(before('CREATE ROLE stockos_app') < before('\\password stockos_app'));
assert.ok(before('\\password stockos_app') < before('ALTER ROLE stockos_app LOGIN'));
assert.ok(before('ALTER ROLE stockos_app LOGIN') < before('COMMIT;'));
assert.doesNotMatch(sql, /CREATE ROLE stockos_(app|migrator) LOGIN/);
assert.doesNotMatch(sql, /0\.0\.0\.0\/0/);
const grants = readFileSync(new URL('./02-grant-runtime-access.psql', import.meta.url), 'utf8');
const verify = readFileSync(new URL('./03-verify-runtime-access.psql', import.meta.url), 'utf8');
for (const [name, source, required] of [
  ['grants', grants, [/BEGIN;/, /COMMIT;/, /current_database\(\) <> 'stockos_java_staging_temp'/,
    /current_user <> 'stockos_migrator'/, /GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public/,
    /GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public/,
    /REVOKE CREATE ON SCHEMA public FROM stockos_app/]],
  ['verify', verify, [/current_database\(\) <> 'stockos_java_staging_temp'/,
    /current_user <> 'stockos_app'/, /rolbypassrls/, /has_database_privilege/,
    /has_schema_privilege/, /pg_has_role/]]
]) {
  for (const expression of required) assert.match(source, expression, name + ' missing ' + expression);
}
assert.doesNotMatch(grants, /GRANT\s+(ALL|CREATE)\s+ON\s+(ALL\s+)?TABLES/i);
assert.doesNotMatch(grants, /ALTER\s+ROLE\s+stockos_app\s+SUPERUSER/i);
console.log('PASS staging role scripts: provisioning, post-migration grants, read-only privilege audit');
