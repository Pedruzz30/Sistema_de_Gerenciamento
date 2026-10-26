# StockOS — SaaS foundation (draft)

## Scope of this branch

This branch starts from `appmod/java-upgrade-20260908035846`, retaining the dedicated test database safety guard. It adds a **fail-closed production startup check** so existing `X-User-RU` authentication is never mistaken for production-ready authentication.

**Not ready for customers**: do not expose the current endpoints to the internet. No Supabase project has been provisioned or modified. Do not use the Space Underground project as a customer database.

## Current critical findings

- `AuthController` accepts first and last name + plaintext password; `Usuario` stores plaintext passwords.
- `ControllerUtils.resolveUser` trusts a caller-supplied numeric `X-User-RU` header, not a cryptographically verified user identity.
- `shared.js` stores user metadata client-side and injects this header.
- `VendaController` accepts `filialId` from the caller; `JpaVendaRepository.listarFiltradas` supports a null tenant/branch filter.
- Some data structures include `filial_id`, but `Produto`, `ItemCardapio`, and other records lack enforced tenant association; a client-controlled branch ID is not authorization.
- `FinalizacaoVendaService` already implements transactions, locks and idempotency. Preserve and extend this.
- `Venda` can act as a draft but is not a full consumption-tab/comanda; inventory is currently decremented at sale finalization.
- Existing cash movements don't comprehensively model individual Pix/card/cash payments, partial payments, or reconciliation.
- `static/app.html` references an experimental JSX file not present in the committed main tree; do not ship it as a completed React migration.

## Implementation sequence (separate PRs)

1. **P0 authentication**: create a separate StockOS Supabase project in staging; wire Auth with validated JWTs in Spring Security, use verified subject (UUID) rather than caller-controlled RU, replace plaintext login, disable demo user seeds for production, and test invalid/expired/tampered tokens. Do not enable `stockos.security.mode=supabase-jwt` until actually implemented.
2. **P0 multi-tenancy**: add `empresas`, `filiais`, `empresa_usuarios` with memberships/roles and stable identifiers; scope all stock, catalog, customer, financial and audit tables and foreign keys; enforce tenant membership server-side and with appropriate database access controls. Never accept an arbitrary `filialId` as authorization. Migrate global uniqueness rules (product codes, customer membership, etc.) to appropriate tenant-scoped uniqueness. Write automated tests proving cross-tenant reads/writes are denied.
3. **P1 consumption tabs**: introduce reusable display numbers 001/002/003 backed by immutable unique internal IDs and a per-establishment unique open-tab constraint. Track added, delivered, voided and returned items with actor/time and idempotency. Reserve/reduce inventory on delivered products, then never deduct those same units again on sale finalization. Handle reversals transactionally and reconcile with the existing sale service.
4. **P1 payment journal**: persist payment type, amount, transaction reference, paid time, operator, void/refund status, and reconciliation. A closing shift must distinguish cash in drawer from Pix/cards on the terminal. Support authorized corrections.
5. **P1 pilot**: rapid UI for employees, check out, backups, disaster recovery, connectivity handling, receipt/fiscal scope assessment, staged rollout at one establishment, then license enforcement and billing.

## Stack decision

- Preserve Java 17 + Spring Boot + Spring Data JPA + Flyway and existing domain logic.
- Separate front-end modernization from the backend migration; do not rewrite the whole business logic.
- Use a **dedicated** Supabase PostgreSQL project for StockOS, with Supabase Auth for identities.
- Configure PostgreSQL TLS and least-privilege database credentials for Spring JDBC; never expose a service-role key in client code.
- Supabase Data API RLS is not automatically applied as user-context RLS through an arbitrary Spring JDBC connection. Implement server-side membership checks and appropriate DB roles/policies; prove isolation with tests.
- Make `prod` mode refuse boot until JWT-auth and tenant controls are fully implemented. Current `legacy-dev` mode is **local development only**.

## Local development / configuration

Production deployment is intentionally blocked pending P0 remediation.

```powershell
$env:SPRING_PROFILES_ACTIVE="test"
$env:STOCKOS_SECURITY_MODE="legacy-dev"
mvn test
```

Do not use the production database for tests. The inherited test database guard blocks accidental use of the default operational database. Keep secrets in environment variables; never commit connection strings with passwords.

## Acceptance gates

- No fixed passwords or privileged demo users created in production.
- No API accepts `X-User-RU` or user-supplied tenant ID as proof of identity/authorization.
- Verified JWT subject is linked to an active membership in the requested company.
- Cross-company reads/writes denied through every API and database access path.
- Transactional consistency across comanda events, stock movements, final sales and payments.
- Tested migrations, JUnit integration, concurrency and idempotency against **isolated disposable** PostgreSQL.
- Verified recovery, backups and support runbook before paid rollout.

This document is a design and deployment blocker checklist, not a claim of completed SaaS capabilities.
