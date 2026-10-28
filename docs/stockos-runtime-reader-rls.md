# StockOS — isolamento RLS de leitura com papel PostgreSQL restrito

## Status
Implementação em desenvolvimento na PR #3. **Não implantado no Supabase real.**
O primeiro passo é uma role `NOLOGIN` apenas de leitura, sem privilégio
`BYPASSRLS` e sem acesso direto de clientes.

A migration é `src/main/resources/db/saas-migration/V20261028_01__runtime_readonly_rls.sql`.
As 13 migrations anteriores já estão no Supabase Staging São Paulo, mas **esta
nova migration ainda não foi aplicada**.

## Design de segurança
- `stockos_runtime_reader`: papel sem LOGIN, SUPERUSER ou BYPASSRLS.
- Apenas SELECT em empresas, filiais, membros, planos, licenças, produtos,
  comandas, e USAGE do schema `stockos_private`.
- `anon`, `authenticated` e `service_role` continuam sem USAGE.
- `membros`: policy somente para a própria associação ativa a uma empresa.
- `empresas`, `filiais`, `produtos`, `comandas`, `licencas`: SELECT
  condicionado a vínculo ativo numa empresa também ativa.
- `planos`: catálogo global legível apenas por funcionário com algum
  vínculo ativo a uma empresa ativa.
- Nenhuma permissão de INSERT, UPDATE, DELETE, nem acesso a convites e auditoria.

## Alerta: identidade por GUC exige middleware confiável
A policy usa `nullif(current_setting('stockos.actor_id', true),'')::uuid`.
Isso não é uma assinatura digital: um processo que tenha acesso SQL ao
papel runtime pode modificar a configuração. Portanto, esse mecanismo oferece
**defesa em profundidade contra bugs de filtro de tenant**, mas não basta contra
credenciais JDBC comprometidas ou SQL injection.

Na API Java, o ID terá de vir *exclusivamente* de JWT Supabase validado
(assinatura, emissor, audiência), nunca de header controlado pelo cliente.
Usar `select set_config('stockos.actor_id', ?, true)` numa transação aberta,
com escopo local à transação, antes de consultar dados do tenant. O pool
não deve reter contexto entre requisições. É necessário testar explicitamente
commit, rollback e reuso de conexão.

**Não usar `postgres`, `service_role` ou DB owner no runtime.** Provisionar
posteriormente um usuário de login específico e somente com grants apropriados.

## Testes de segurança
`scripts/ci/verify-saas-runtime-reader-rls.sh` roda após a inicialização de
um banco PostgreSQL 17 **descartável** com 23 tabelas privadas, cria duas
empresas de teste, troca o papel via `SET LOCAL ROLE` e verifica:
- sem identidade não vê empresas nem planos;
- A não enxerga empresa/estoque/comandas da B e vice-versa;
- funcionário desativado não enxerga seus dados;
- sem contexto após COMMIT não enxerga a empresa anterior;
- atualização de estoque e leitura de convites falham por permissão.

Testes ainda não comprovam fluxo Java de autenticação conectado a este papel,
operações de escrita com RLS, ou robustez em caso de injeção SQL.

## Próximo estágio
1. CI e revisão da migration em banco descartável.
2. Implementar limites transacionais no Java em um contexto com JWT real.
3. Planejar role de escrita com políticas que considerem empresa, filial e papel.
4. Aplicar no Supabase Staging apenas após verificar as etapas anteriores.
5. Testar com contas fictícias, chaves geridas em ambiente seguro e sem
   expor o banco na internet ou abrir sistema publicamente.

Referência:
https://supabase.com/docs/guides/database/postgres/row-level-security
