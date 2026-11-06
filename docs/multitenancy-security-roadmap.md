# StockOS — Segurança multi-tenant: fundação (não pronta para produção)

## Objetivo

Cada estabelecimento é um tenant independente. O dono entra com identidade
verificada do Supabase Auth e gerencia seus operadores; funcionários usam RU
**local à loja** + PIN **apenas em terminais pareados**, futuramente. O RU é
identificador público, nunca senha. Proprietário não empresta sessão a caixa.

## Implementado nesta PR (somente fundação)

- Tabela `estabelecimentos` com UUID e estado `ATIVO` ou `SUSPENSO`.
- Tabela `usuarios_estabelecimentos`, vínculo explícito entre loja e usuário
  operacional existente, papel (`PROPRIETARIO`, `GERENTE`, `OPERADOR`) e RU local
  único por loja. Um RU local pode repetir em outra loja.
- `TenantMembershipGuard`: exige vínculo, operador e loja ativos. Rejeita ausência
  de vínculo, troca de loja, suspensão, revogação e papel não autorizado.
- Testes de integração de autorização e constraints do PostgreSQL.
- Primeira migração **aditiva** de propriedade de catálogo (`V20261102_02`):
  `estabelecimento_id` anulável em produtos, fornecedores, caixas e cardápio.
  Registros legados continuam sem dono, sem associação automática.
- Serviço Java **interno** `TenantScopedCatalogService` para listar, consultar,
  cadastrar e renomear produtos apenas dentro do tenant verificado. A cláusula
  SQL filtra pelo estabelecimento inclusive no `UPDATE`; não há endpoint público.
- Testes de acesso cruzado em produtos, revogação e impossibilidade de modificar
  registro de outra loja.
- Migration `V20261102_03`: impede vincular fornecedor a produto de outra
  loja (inclusive quando uma associação omite o `estabelecimento_id`), e
  torna a propriedade de produtos, fornecedores, caixas e cardápio imutável
  para operações SQL ordinárias.
- Migration `V20261102_04`: impede que um item de cardápio use categoria ou
  produto de outra loja, inclusive tentando omitir `estabelecimento_id`.
  Testes de integração exercitam os dois bloqueios e preservam associações
  legadas integralmente sem proprietário.
- Primeiro controller HTTP tenant-aware em `TenantCatalogController`, com GET de
  produtos/fornecedores, POST de produto e PATCH de nome. **Não é registrado
  no modo legado**: depende da classe de filtro verificado da PR #31 e da
  propriedade `stockos.auth.mode=supabase`. Lê o atributo de usuário
  preenchido exclusivamente pelo filtro de autenticação; ignora qualquer RU
  enviado em cabeçalhos. Testes unitários cobrem ausência de identidade,
  cabeçalho falso, usuário desativado e ausência de vazamento na resposta.
  Os dados de catálogo são sempre filtrados por loja pelo serviço.
- `LegacyApiIsolationGate`: em modo `supabase`, retorna 403 para toda API
  legada por padrão, permitindo apenas configuração/sessão autenticada e o
  caminho do novo catálogo tenant-aware. Esta camada **não autentica** ninguém:
  é indispensável integrar a cadeia Spring Security/JWT da PR #31 antes de
  qualquer uso real. O modo `legacy` permanece exclusivamente local.
  Testes unitários verificam bloqueio de APIs globais e prefixos falsificados.
- Migration `V20261102_05`: acrescenta dono de tenant a notas fiscais e seus
  itens; FKs compostas, triggers e owner imutável barram referências cruzadas
  entre notas, fornecedores e produtos, preservando registros legados sem
  proprietário. Testes incluem nota válida, fornecedor externo, operador sem
  vínculo, produto externo, omissão de tenant e compatibilidade com legado.
- `SupabaseModeStartupGuard`: impede iniciar a aplicação em modo Supabase se
  o filtro de identidade verificada e a cadeia Spring Security JWT ainda não
  estiverem integrados. Teste garante falha explícita na ausência do filtro.
- Migration `V20261102_06`: saldos (`estoques_produto`) e movimentos
  (`movimentos_estoque_decimal`) passam a ter estabelecimento opcional, FKs
  compostas, triggers contra produto de outra loja e estorno cruzado;
  propriedade do registro fica imutável. Sem backfill. Testes de banco
  validam operações autorizadas, cruzamento, omissão de tenant e legado.
- Serviço interno `TenantScopedStockService`: lista saldos e movimentos
  com `estabelecimento_id` explícito nas cláusulas `WHERE` **e** nos joins;
  impõe vínculo ativo ao usuário verificado e limita volume de resultados.
  Testes tentam consulta entre lojas e acesso a linhas legadas.
- Migration `V20261102_07`: adiciona `estabelecimento_id` opcional a
  `vendas` e `venda_itens`; a venda, o item do cardápio e a variação devem
  concordar, com chaves compostas e trigger que impede omissões de tenant.
  Testes cobrem associações entre lojas, troca de variação e compatibilidade
  com registros antigos.
- Serviço interno `TenantScopedSalesService`: busca vendas por loja com
  filtros SQL obrigatórios, limite de resultados e acesso financeiro somente
  para `PROPRIETARIO` ou `GERENTE`. Testes negam acesso ao operador, a membros
  de outra loja e a vendas legadas sem estabelecimento. Ainda **não existe
  rota HTTP pública** para estes relatórios.
- Nova rota read-only `TenantStockController`: GET saldos/movimentos em
  `/api/v2/estabelecimentos/{id}/estoque`, habilitada somente com JWT
  verificado pelo filtro da PR #31 e `stockos.auth.mode=supabase`.
  `LegacyApiIsolationGate` libera **apenas** esses dois GETs de estoque;
  escrita continua bloqueada. Testes de identidade forjada, usuário inativo,
  rotas não permitidas e desativação total no modo legado.
- Migration `V20261102_08`: movimentos de caixa ganham
  `estabelecimento_id` opcional, referência composta ao caixa e triggers que
  rejeitam vinculação a caixa de outra loja, omissão de tenant e operador
  sem vínculo ativo. Registros legados continuam sem dono. O dono de um
  movimento não pode ser transferido por UPDATE ordinário.
- Serviço **interno**, somente leitura, `TenantScopedCashService`: consultas
  financeiras exigem `PROPRIETARIO`/`GERENTE`, filtram `estabelecimento_id`
  no SQL e validam a loja também no JOIN. Testes impedem acesso cruzado,
  leitura por operador e vazamento de caixas legados.
- **Nenhum** proprietário ou estabelecimento é criado automaticamente;
  **nenhum** dado legado é migrado, reclassificado ou eliminado.

## Restrições de segurança e etapas obrigatórias

1. **Ainda não há isolamento de dados operacionais completo**. As colunas
   novas em `produtos`, `fornecedores`, `caixas` e cardápio são anuláveis, e os
   repositórios/controllers **legados continuam globais**. Vendas, notas,
   logs e várias outras tabelas ainda não receberam escopo obrigatório.
   Saldo, movimento de estoque, venda e item de venda possuem escopo opcional,
   mas as rotas legadas ainda não aplicam os filtros de tenant exigidos.
   Movimentos de caixa recebem tenant opcional; os fluxos de fechamento,
   pagamentos e reconciliação ainda não foram portados e seguem bloqueados.
   O novo modelo de vendas ainda não protege consumos, estornos, pagamento
   e todas as referências de ficha técnica. A coluna antiga `vendas.filial_id` não garante isolamento.
2. A nova API de catálogo tenant-aware não substitui os **endpoints legados**.
   **Não se deve ativar multi-tenancy publicamente**; as consultas antigas
   ainda podem ignorar `estabelecimento_id`. O controller novo fica inativo
   na branch atual, até integrar a PR #31 e validar o filtro JWT real.
   Nenhuma operação comercial deve depender dele antes dos testes de ponta a ponta.
3. Próxima etapa: mapear todas as tabelas e suas relações; introduzir
   `estabelecimento_id` obrigatório em operações novas e constraints/FKs
   compostas que impeçam referenciar objetos de outra loja. Planejar a
   associação dos dados legados somente com inventário e confirmação do dono.
4. As queries JDBC/JPA de leitura e escrita devem filtrar por **tenant derivado
   do contexto autenticado** e rejeitar IDs externos antes de retornar dados.
   Guardas antes da query não substituem filtro SQL. Cobrir transações,
   paginação, relatórios, exportação, auditoria e processos assíncronos.
5. Ao integrar a PR de Supabase Auth, `verifiedUsuarioRu` deve vir do token
   validado e vínculo server-side; nunca confiar em RU, tenant ID ou papel
   arbitrário enviado no cabeçalho, body, query string ou localStorage.
6. Para terminal compartilhado: dispositivo pareado/revogável, PIN individual
   protegido por hash Argon2id (ou equivalente), limitação de tentativas,
   sessão de operador curta, reautenticação para estornos e ações críticas.
7. Remover as senhas legadas em texto puro e qualquer modo de login legado
   antes de deploy público. MFA obrigatório para dono/administrador.
8. Testar dois tenants com IDs que colidem e tentativas cruzadas em TODOS os
   endpoints, inclusive erro sem revelar a existência dos recursos.
9. Se usar Supabase Data API, revisar RLS. O backend JDBC deve impor suas
   próprias políticas: RLS de um client Supabase não protege automaticamente
   a conta JDBC do Spring.
10. Separar credenciais do migrador Flyway das credenciais de execução Java:
    a conexão da aplicação **não pode utilizar o superusuário PostgreSQL**
    usado no Docker local. O papel de aplicação deve ter privilégios mínimos,
    sem BYPASSRLS e sem permissões para DDL ou outros bancos. Uma mesma conta
    superuser para todos os tenants anula boa parte da defesa em profundidade.
11. Revisão de logs/PII, backups por tenant, proteção de cookies/tokens,
    limites de acesso e resposta a incidentes antes do piloto.

## Matriz mínima de ataques de regressão

| Área | Tentativa proibida | Resultado requerido |
| --- | --- | --- |
| Autenticação | JWT sem assinatura, com audiência errada ou expirado | 401 |
| Identidade | Alterar RU ou papel no cabeçalho/JSON | Identidade vinculada server-side prevalece |
| Associação | Funcionário da loja A acessa loja B | 403/404 genérico, sem dados |
| Estado | Usuário revogado ou loja suspensa usa sessão antiga | Acesso negado |
| Estoque | ID de produto de B em operação de A | Nenhuma leitura, baixa ou modificação |
| Caixa | Terminal de A finaliza venda de B | Negado e registrado |
| Relatórios | Paginação/totalização/exportação cruzada | Valores exclusivamente do tenant autenticado |
| Relacionamentos | Nota fiscal de A referencia fornecedor/produto de B | Constraint ou operação rejeita |
| Background | Job agendado processa linhas de outra loja | Escopo de tenant obrigatório e auditável |
| Banco | Credencial da API tenta DDL ou acesso administrativo | Permissão negada |

Não considerar a plataforma pronta enquanto esta matriz não estiver executada
contra os endpoints e transações reais. Testes de associação isolados não
comprovam a proteção das operações de negócio.

**Status:** trabalho de infraestrutura numa PR draft. Não conectar à Cloud
nem executar migrações manualmente em ambiente de cliente.
