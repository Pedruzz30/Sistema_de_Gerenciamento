# StockOS standalone SaaS entrypoint

Entry point: `saas.StockOsStagingApplication`.

The legacy `app.Main` must **never** be used to start StockOS against the
Supabase Staging database. It scans all legacy Hibernate entities and would
attempt to validate non-existent public legacy tables.

The new entrypoint scans only `saas` and explicitly imports SaaS controllers,
services, JWT validation and startup safety checks. Hibernate JPA and Spring
Data JPA repository auto-configurations are excluded, while JDBC and Spring's
transaction manager remain enabled.

```bash
# Set the non-secret public project settings using environment variables.
# Supply DB_URL/DB_USERNAME/DB_PASSWORD only via a private env manager.
STOCKOS_SECURITY_MODE=supabase-jwt
STOCKOS_SUPABASE_EXPECTED_PROJECT_REF=hucfndwhaebvithtkovn
STOCKOS_SUPABASE_URL=https://hucfndwhaebvithtkovn.supabase.co
STOCKOS_SUPABASE_AUTH_ISSUER=https://hucfndwhaebvithtkovn.supabase.co/auth/v1

# After safe DB runtime roles/policies have been deployed and verified:
java -cp target/classes:... saas.StockOsStagingApplication --spring.profiles.active=staging
```

**DO NOT start against the remote Supabase DB yet**:
runtime JDBC role with least privilege, policies and tenant-bound context
are still being implemented. Startup blocks legacy auth mode even in staging.
The previous default `Main` remains available for local legacy development.

CI test: `SaasIsolatedStagingHttpIntegrationTest` checks that JPA, Flyway,
legacy controllers/repositories are absent and SaaS HTTP+JWT routes work on
disposable PostgreSQL. JWT decoder is mocked in this integration test; real
JWKS signatures are verified in a separate test suite.

No deploy, migrations or user provisioning are performed by this entrypoint.
