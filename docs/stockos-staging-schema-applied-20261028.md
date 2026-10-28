# StockOS Staging — migrações SaaS aplicadas (2026-10-28)

**Projeto único:** `hucfndwhaebvithtkovn` — `sa-east-1` (São Paulo).
As migrações desta página foram executadas por meio da conexão administrativa
Supabase, que grava histórico em `supabase_migrations.schema_migrations`.
**Não são entradas do Flyway.**

## Resultado de verificação do banco

- **13 migrations** `stockos_*` aplicadas com sucesso, em ordem.
- **23 tabelas** no schema `stockos_private`.
- **0 tabelas** em `public`.
- **23/23 tabelas privadas com RLS habilitada**, nenhuma sem RLS.
- **0 empresas, 0 convites e nenhum dado de cliente**.
- `anon`, `authenticated` e `service_role` sem permissão de USAGE em
  `stockos_private`; sem SELECT na amostra de tabelas testadas.
- Consulta do advisor de segurança indicou `rls_enabled_no_policy` INFO nas
  23 tabelas. Não é permissão aberta: sem política e sem grants, acesso
  não privilegiado é negado. Precisaremos políticas tenant-scoped para
  um papel runtime restrito.
- Advisor de desempenho retornou avisos sobre índices para algumas FKs e
  índices ainda não utilizados. Reavaliar com cargas e consultas reais.
- Banco de produção da Space Underground não foi alterado.

## Migrações executadas, origem canônica

Todas foram extraídas literalmente da PR #3 do repositório
`Pedruzz30/Sistema_de_Gerenciamento`, no diretório
`src/main/resources/db/migration`:

| Arquivo Flyway original | Migração Supabase |
| --- | --- |
| V20261026_02__saas_core_comandas.sql | stockos_20261026_02_saas_core_comandas |
| V20261026_03__saas_licenses.sql | stockos_20261026_03_saas_licenses |
| V20261026_04__admin_audit.sql | stockos_20261026_04_admin_audit |
| V20261027_01__saas_cash_shifts.sql | stockos_20261027_01_saas_cash_shifts |
| V20261027_02__saas_payment_reversals.sql | stockos_20261027_02_saas_payment_reversals |
| V20261027_03__closed_sale_manual_refunds.sql | stockos_20261027_03_closed_sale_manual_refunds |
| V20261027_04__physical_stock_returns.sql | stockos_20261027_04_physical_stock_returns |
| V20261027_05__saas_inventory_stocktakes.sql | stockos_20261027_05_saas_inventory_stocktakes |
| V20261027_06__saas_goods_receipts.sql | stockos_20261027_06_saas_goods_receipts |
| V20261027_07__member_management_audit.sql | stockos_20261027_07_member_management_audit |
| V20261027_08__product_catalog_audit.sql | stockos_20261027_08_product_catalog_audit |
| V20261027_09__verified_employee_invites.sql | stockos_20261027_09_verified_employee_invites |
| V20261027_10__private_schema_access_guard.sql | stockos_20261027_10_private_schema_access_guard |

**Exclusão deliberada:** `V20261026_01__auth_user_identity.sql` modifica
`usuarios` do sistema legado; ela NÃO foi aplicada ao staging.

## Atenção: não executar o Flyway legado contra este banco

O aplicativo Java atual contém entidades JPA e migrations `V20260417_00`
em diante que exigem tabelas legadas no schema `public`. As migrations do
SaaS foram aplicadas pelo Supabase e NÃO constam em `flyway_schema_history`.
Se iniciar o aplicativo com `spring.flyway.enabled=true`, o Flyway
poderá tentar executar tudo novamente e falhar ou criar tabelas desnecessárias.

O perfil `staging` desativa Flyway por padrão em
`application-staging.properties`. Isso é somente proteção contra reexecução,
**não significa que o backend já esteja pronto para conectar**, pois ele
ainda precisa separar entidades/serviços legados para não depender dessas
tabelas. O `ddl-auto=validate` continua exigindo correspondência de entidades.

## Pendências antes do primeiro login operacional

1. Separar configuração de JPA e controllers legados no modo SaaS.
2. Definir usuário JDBC runtime de baixo privilégio, distinto do papel de
   migração, e testar RLS por empresa/filial em transação.
3. Verificar callback/Auth real, chave pública e fluxo de e-mail confirmado.
4. Provisionar primeira empresa/plano/proprietário por fluxo administrativo
   seguro, sem inserir contas de demonstração.
5. Realizar teste E2E com dados fictícios e backup/recovery de staging.
6. Manter `prod` bloqueado e a PR em rascunho até aprovação completa.

Não gravar segredo `DB_PASSWORD`, `sb_secret`, `service_role` ou sessão
JWT no GitHub ou em mensagens.
