# StockOS — reconciliação do SaaS antigo (#3) com o modelo seguro (#33)

## Decisão técnica em 10/11/2026

A PR **#3** (`feat/stockos-saas-auth-foundation-20261026`) é um protótipo histórico de arquitetura SaaS no schema **`stockos_private`** do Supabase, com módulos e migrations próprios. A PR **#33** adota **Supabase Auth somente para identidade** e **PostgreSQL separado com Flyway e tenant IDs** para o negócio.

Esses modelos são incompatíveis sem reconciliação explícita. **Nunca mesclar o backend da #3 diretamente na `main` ou na #33**, nem executar suas migrations de `db/saas-migration` no banco que usa `db/migration`. A PR #33 continua experimental até homologação em staging.

A PR **#5** é derivada da #3 e inclui o mock antigo. A documentação do preview foi atualizada pela **#50**, enquanto o preview Netlify atual já está implementado em `preview-source/` e `scripts/build-preview.mjs`. O mock de comandas **não é** implementação financeira real.

## Funcionalidades a portar — não concluídas na #33

| Módulo no SaaS antigo | Ponto de partida para revisão | Status na #33 | Próxima entrega segura |
|---|---|---|---|
| Comandas, itens e cancelamentos | `SaasComandaService`, `SaasComandaController` | Não incorporado ao backend tenant-aware final | Desenhar tabelas Flyway compatíveis e transações por loja; testar concorrência, estoque e idempotência |
| Caixa/turnos, pagamentos e estornos | `SaasCashService`, `SaasPostCloseRefundService` | Exposição HTTP financeira intencionalmente bloqueada | Criar APIs de escrita tenant-aware, testes de papel, rollback e auditoria; manter pagamento externo inicialmente |
| Produtos/estoque e recebimento de mercadorias | `SaasProdutoService`, `SaasGoodsReceiptService`, `SaasInventoryAdjustmentService` | Catálogo básico e leitura de estoque existem | Migrar entradas, saídas, inventários e recebimentos com autorização, FK por tenant e transações |
| Funcionários e convites | `SaasEmployeeService`, `SaasEmployeeInvitations` | Membership básico/roles da #33 | Testar convite verificado, vínculo revogado, RU local, perfil e auditoria |
| Licenças e administração SaaS | `SaasLicenseService`, `SaasAdminService` | Não homologado como serviço multiempresa | Implementar licenças por estabelecimento, sem acesso global de funcionários ou bypass de tenant |
| Relatórios de custos e vendas | `SaasReportsService` | Apenas serviços de leitura interna no novo modelo | Recriar consultas tenant-scoped, permissões e desempenho sem vazamento de dados |
| Devoluções físicas e pós-fechamento | `SaasStockReturnService`, `SaasPostCloseRefundService` | Não exposto | Especificar fluxos financeiros e reconciliação, testar repetição e integridade |

A fonte de código da #3 é **referência de lógica de negócio**, não fonte para cherry-pick indiscriminado. As migrations antigas e grants do `stockos_private` não podem ser transferidos automaticamente. Prefira novas PRs pequenas baseadas na integração tenant-aware, com backend + testes transacionais por módulo.

## Sequência recomendada para dois desenvolvedores

1. **Segurança/infra:** estabilizar #33; testar JWT de duas contas descartáveis no `StockOS Staging` (atualmente inativo), verificando isolamento e revogação; aplicar Flyway **somente** em PostgreSQL staging separado com roles migrator/runtime; não usar banco Space.
2. **Aplicação:** começar pelo módulo tenant-aware de **comandas e estoque**, com contratos de API mínimos; implementar inicialmente apenas dados de teste e processamento sem TEF.
3. **Financeiro:** só habilitar caixa, cobrança, estornos e fechamento após os testes de concorrência, rollback, idempotência e auditoria por estabelecimento.
4. **UX:** reutilizar as interfaces já na `main`, sem fingir que o mock Netlify realiza operações reais.

## Critérios objetivos antes de liberar para cliente

- Falhar em produção com login `legacy` e sem variáveis explícitas de PostgreSQL/runtime; checks da #33.
- Garantir 401/403 para token ausente, inválido, usuário desvinculado ou outro tenant.
- Usar PostgreSQL com role runtime sem DDL/superuser e migrations aplicadas externamente como migrator.
- Comprovar isolamento cruzado de duas lojas em consultas, mutações e IDs arbitrários.
- Testar dados financeiros e estoque com rollback, idempotência, múltiplos operadores e conexões interrompidas.
- Backups/restauração e observabilidade testados; migração real e execução comercial ainda não autorizadas.

**Histórico preservado:** fechar a PR #3 como arquitetura histórica, sem deletar branch ou código, não implica concluir as funcionalidades acima. Este documento é o backlog explícito de migração.
