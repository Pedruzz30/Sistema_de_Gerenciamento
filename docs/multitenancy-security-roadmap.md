# StockOS — Segurança multi-tenant: fundação (não pronta para produção)

## Auditoria read-only do StockOS Staging existente (03/11/2026)

Inspeção do projeto **StockOS Staging** via consultas PostgreSQL
**somente leitura** (sem criar usuários, executar migrations ou alterar RLS):

- O banco existente contém 23 tabelas em `stockos_private`, com
  `empresas`, `filiais`, `membros` e `produtos`. Isso representa um modelo
  **diferente** do modelo Flyway Java desta PR (`estabelecimentos`,
  `usuarios_estabelecimentos` e produtos com IDs numéricos).
- As tabelas estão vazias conforme a contagem disponível; `public` não contém
  as tabelas de negócio Flyway inspecionadas.
- As tabelas privadas possuem RLS habilitada e sem políticas explícitas.
  O advisor marca isso como `INFO`, não como prova de exposição.
- As permissões verificadas negam acesso/DDL ao schema `stockos_private`
  para `anon`, `authenticated` e `service_role`; as consultas verificaram
  ausência de `SELECT`, `INSERT`, `UPDATE` e `DELETE` para os papéis
  `anon` e `authenticated`. Não foram encontradas funções próprias no
  schema. Isso não substitui auditoria de todo o projeto nem de credenciais
  com privilégios elevados.

**Medida fail-closed:** `SupabaseSchemaCompatibilityGuard` inspeciona
`pg_catalog` usando a *mesma conexão configurada no Flyway*, antes de
`flyway.migrate()`, apenas quando `stockos.auth.mode=supabase`.
Caso encontre as tabelas identificadoras do modelo `stockos_private`, ou não
consiga consultar a base, bloqueia as migrations. Testes confirmam
pré-verificação, negação e ausência de chamadas `migrate()` na colisão.

**Importante:** esta proteção não faz reconciliação automática. Não migre as
estruturas Java para o Supabase Staging existente. Para homologação com o
backend Java, use banco PostgreSQL **separado**, descartável, com usuário
runtime e migrador independentes e sem clientes reais, até ser definido um
modelo canônico e um plano de migração revisado.


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
- Migration `V20261102_09`: partes da venda, consumos e estornos
  recebem propriedade de estabelecimento opcional, sem reassociar legado.
  Triggers e FKs compostas rejeitam produto, item, parte ou movimento de outra
  loja, omissão do tenant e estorno que não reverta o movimento exato consumido.
  Testes PostgreSQL cobrem sucesso autorizado e negações entre duas lojas.
  **Isto não habilita endpoints de finalização/cancelamento** nem substitui
  limites totais estornáveis, idempotência ou bloqueios transacionais.
- Migration `V20261103_01`: para registros pertencentes a um tenant,
  travas de linha e triggers limitam a soma de estornos pela quantidade do
  consumo original **e** pela quantidade do movimento de devolução. Bloqueiam
  alterações posteriores em quantidades de consumos/movimentos já vinculados
  a estornos. Testes cobrem estorno parcial, excesso cumulativo, distribuição
  acima do movimento e edição retroativa. Registros legados continuam intactos.
  **Pendente:** teste concorrente com duas transações reais, auditoria de
  integridade das atualizações de referências já vinculadas e autorização
  transacional no serviço; rotas financeiras seguem bloqueadas.
- Migration `V20261103_02`: impede que vários consumos de um tenant
  aloquem mais unidades do que o movimento `SAIDA_VENDA` original, exigindo
  movimento para consumos tenant-owned. O movimento é bloqueado com
  `FOR UPDATE` antes da soma, e referências já usadas em consumos ou estornos
  não podem ter quantidade/produto/tipo/reversão reescritos. Testes de
  regressão de alocação duplicada e referência imutável foram adicionados.
  **Pendente:** testes concorrentes com conexões distintas e proteção do
  vínculo do consumo após um estorno já realizado.
- Migration `V20261103_03`: protege a trilha de auditoria do
  estorno. Após registrar um estorno de tenant, o consumo não pode trocar
  sua venda/item/parte/produto/movimento; o lançamento de estorno não
  pode ser editado ou excluído por DML comum. Correções deverão usar eventos
  compensatórios próprios e auditados; estes fluxos ainda não estão habilitados.
- **Nenhum** proprietário ou estabelecimento é criado automaticamente;
  **nenhum** dado legado é migrado, reclassificado ou eliminado.


## Console tenant-aware de homologação (03/11/2026)

- O login Supabase passa a encaminhar para `/estabelecimentos.html`, evitando
  abrir o dashboard legado cuja API está bloqueada no modo multi-tenant.
- A página consulta `GET /api/v2/estabelecimentos` com o JWT da sessão,
  permite escolher **somente** um estabelecimento da lista autenticada e
  consulta catálogo, fornecedores, saldos e movimentos tenant-aware em modo
  **somente leitura**.
- Ao trocar ou atualizar uma loja, o frontend consulta novamente os vínculos
  ativos no servidor. A autorização continua obrigatória em cada serviço SQL;
  a escolha local não é autoridade de acesso.
- O JavaScript usa `textContent`/DOM seguro, não armazena o tenant em
  `localStorage`, limpa a interface após falha e não libera pagamentos ou caixa.
- O login Supabase remove o cache persistente do usuário operacional e guarda
  somente a informação de exibição em `sessionStorage`.
- `scripts/test-tenant-console.mjs` testa a allowlist de leitura, JWT por
  requisição, seleção não autorizada e vínculo revogado; o workflow Backend
  and Docker passou a executar esse teste.
- A página `estabelecimentos.html` é excluída do build Netlify mock, por sua
  lista explícita de páginas permitidas. O site público segue demonstração.

**Ainda não é homologação E2E real:** faltam contas Supabase reais, vínculos
cadastrados, credenciais de banco com grants mínimos, HTTPS hospedado para
backend, teste com duas sessões e monitoramento operacional. A API financeira
continua bloqueada.

## Descoberta autenticada de estabelecimentos (03/11/2026)

A API de homologação oferece `GET /api/v2/estabelecimentos` **apenas** em modo
`supabase`. O RU operacional vem do JWT validado e do vínculo servidor-side
`supabase_auth_user_id`, nunca do header `X-User-RU` nem de um parâmetro.

Ela lista no máximo 100 estabelecimentos para os quais o usuário possui vínculo
ativo, com a própria loja ativa e a conta operacional ativa. Responde com
`id`, `nome`, `papel` e `ruLocal` e não permite criar/selecionar automaticamente
uma loja sem autorização. O catálogo e o estoque continuam usando o UUID no
caminho exclusivamente como seletor, com a checagem de membership no backend.

Testes em memória e integração PostgreSQL cobrem usuário inativo, duas lojas,
vínculo revogado, loja suspensa e tentativa de RU falsificado. **Este endpoint
não disponibiliza transações financeiras nem é um teste E2E de Supabase Auth real.**

## Validações acrescentadas em 03/11/2026

- Teste com **duas transações PostgreSQL independentes** tentando consumir a
  mesma unidade de estoque; somente uma operação deve ser aceita.
- Testes de rollback transacional: consumo inválido após um consumo aceito
  anteriormente na mesma transação não deve persistir parcialmente; a mesma
  regra se aplica a tentativa de cruzar lojas.
- `SupabaseDatabasePrivilegeGuard`: startup em modo Supabase falha quando a
  credencial JDBC da aplicação tem privilégios administrativos, ownership
  de tabelas críticas ou possibilidade de criar objetos no schema/banco.
- `SupabaseFlywaySeparation`: exige credenciais de migração diferentes das
  credenciais de runtime, antes de Flyway executar.
- Provisionamento operacional documentado em
  [PostgreSQL — least privilege](postgresql-least-privilege.md).

**Ainda pendente:** testes reais de autenticação com Supabase, segregação
de papéis testada em banco efêmero com grants reais, auditoria de privilégios
de funções herdadas e implementação de transações tenant-aware para
pagamentos e fechamentos de caixa. Estas medidas não habilitam APIs financeiras.

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
