# StockOS HTTP authorization integration tests

Class: `integration.SaasHttpSecurityPostgresIntegrationTest`.

The workflow starts a **real Spring Boot application context** using `MockMvc`
so the real Spring Security filter chain, controller routes, service authorization
and PostgreSQL reads/writes are exercised together.

`JwtDecoder` is intentionally mocked; test-issued bearer tokens map to fixture
user UUIDs. No Supabase project or signing key is accessed. Covered scenarios:

- Requests without bearer token, forged legacy identity headers and invalid tokens;
- Legacy API blocked in JWT mode;
- Company isolation and out-of-company branch identifier;
- Attendant blocked from product creation and cash closing;
- Suspended license blocks operational requests while status remains readable;
- Authorized company owner can open and list a tab.

**Limitations:** these are HTTP integration tests, not real Supabase login,
cryptographic JWKS/signature verification or browser-to-Java E2E. The companion
Chromium suite mocks backend responses, and the transactional PostgreSQL tests
exercise the underlying services. Keep all three checks until a dedicated staging
environment is configured. No production database must be used.
