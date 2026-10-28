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
mvn -B spring-boot:run \
  -Dspring-boot.run.main-class=saas.StockOsStagingApplication \
  -Dspring-boot.run.profiles=staging
```

**DO NOT start against the remote Supabase DB yet**:
runtime JDBC role with least privilege, policies and tenant-bound context
are still being implemented. Startup blocks legacy auth mode even in staging.
The previous default `Main` remains available for local legacy development.

CI boots a **second disposable PostgreSQL database** `stockos_ci_saas`
using the 13 SaaS migrations only (not the legacy Flyway scripts), then
runs `SaasIsolatedStagingHttpIntegrationTest` against it. The test checks
zero public legacy tables, 23 private SaaS tables, no JPA or Flyway beans,
no legacy controller/repository beans, and a real SaaS HTTP comanda operation.
JWT decoder is mocked in this integration test; cryptographic JWKS
signatures are verified in a separate unit test suite.

No deploy, migrations or user provisioning are performed by this entrypoint.
