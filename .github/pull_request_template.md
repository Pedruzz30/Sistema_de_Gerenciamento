## StockOS change checklist

- [ ] Tested on dedicated disposable database (never production)
- [ ] No plaintext credentials/secrets/demo users exposed to production
- [ ] Tenant/company access is enforced server-side on affected operations
- [ ] Cross-tenant access denied in automated tests
- [ ] Stock, payment, and sale transaction effects verified
- [ ] Flyway migration and rollback/recovery assessed
- [ ] `mvn test` result attached
- [ ] Feature is explicitly identified as ready or **not ready** for production
