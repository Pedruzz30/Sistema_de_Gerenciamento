# StockOS JWT foundation — STAGING ONLY

This is **not** a commercial release. ProductionSafetyGate refuses the `prod`/`production` profiles while multi-company authorization remains incomplete. No project or credentials have been created, and the current HTML login remains legacy-only.

## Changes

- Opt-in JWT bearer validation in Spring Security for `/api/**`.
- Access tokens validated against the Supabase Auth issuer and JWKS, expiration, role=`authenticated`, and audience=`authenticated`.
- `POST /api/auth/login` is forbidden in JWT mode; no default password can authenticate through the old login endpoint.
- In JWT mode, `ControllerUtils.resolveUser` resolves an active user by the verified JWT `sub` UUID, ignoring any submitted `X-User-RU`.
- `GET /api/auth/me` returns the local user identity after authorization.
- Migration adds nullable unique `usuarios.auth_user_id` to link preexisting employees to Supabase identities. No self-service linking route is exposed.
- In `legacy-dev` mode, server binding must remain loopback (127.0.0.1/localhost/::1). This mode must never be exposed through a public proxy.

## Staging setup (after provisioning a *separate* StockOS Supabase project)

Use asymmetric JWT signing keys (JWKS); legacy symmetric HS256 projects are **not supported** by this validation path.

Configure staging environment secrets privately (do not commit):

```bash
export STOCKOS_SECURITY_MODE=supabase-jwt
export STOCKOS_SUPABASE_AUTH_ISSUER=https://YOUR_PROJECT_REF.supabase.co/auth/v1
export STOCKOS_SERVER_ADDRESS=127.0.0.1
export DB_URL='jdbc:postgresql://.../postgres?sslmode=verify-full'
export DB_USERNAME='...'
export DB_PASSWORD='...'
```

Use the correct SSL certificate trust chain and database hostname from the Supabase connection dialog; the JDBC URL above is illustrative. Only publish staging through a carefully controlled authenticated environment. Direct SQL access via Spring Data JPA DOES NOT inherit Supabase Auth user RLS. Tenant checks still need to be enforced before any public deployment.

Database linking is a **manual, audited staging-only operation** until a trusted provisioning flow exists:

```sql
-- Example ONLY: substitute actual values from verified tenant onboarding.
UPDATE usuarios SET auth_user_id = '<verified-supabase-auth-user-uuid>'
 WHERE ru = <local-user-ru>;
```

After obtaining a signed access token from Supabase Auth, test:

```bash
curl -H "Authorization: Bearer $ACCESS_TOKEN" http://127.0.0.1:8080/api/auth/me
```

If there is no linked active user, access fails closed. A different `X-User-RU` header cannot override token identity.

## Open blockers

1. **Production is disabled** even in JWT mode, until tenants and roles have verified server-side isolation.
2. The current HTML login uses name/surname/password and needs a Supabase Auth UI implementation. The existing `shared.js` header injection must be removed when swapping to JWT mode.
3. Local employee provisioning still requires a trusted admin workflow, including removal of seeded plaintext user credentials and migration of the legacy passwords.
4. User/branch/tenant scope must be added to *all* queries and write paths, not just sales.
5. JWT mode protects `/api/**`; inspect and secure other exposed server routes before opening the app publicly.
6. Database integration tests and end-to-end auth tests need to pass against an isolated staging database.

Sources:
- https://supabase.com/docs/guides/auth/jwts
- https://supabase.com/docs/guides/database/postgres/row-level-security
- https://supabase.com/docs/guides/platform/ssl-enforcement
