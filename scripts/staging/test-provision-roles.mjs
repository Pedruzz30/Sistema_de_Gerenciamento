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
console.log('PASS staging role script: transactional, fail-closed, no default passwords');
