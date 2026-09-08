# Plano de migracao Flyway

## Estado atual apos Fase 4.6

O projeto agora possui Flyway no `pom.xml` e a aplicacao esta configurada para:

```properties
spring.flyway.enabled=true
spring.jpa.hibernate.ddl-auto=validate
```

As migrations versionadas ficam em `src/main/resources/db/migration`. A estrutura nova nao deve mais
depender do Hibernate `ddl-auto=update`.

O baseline padrao para bancos ja existentes e:

```properties
spring.flyway.baseline-on-migrate=${FLYWAY_BASELINE_ON_MIGRATE:true}
spring.flyway.baseline-version=${FLYWAY_BASELINE_VERSION:20260714.03}
```

Esse baseline representa um banco de desenvolvimento que ja foi criado/atualizado pelo Hibernate ate a
Fase 4. A migration `V20260714_04__fase46_persistencia_concorrencia_integridade.sql` passa a executar
os ajustes posteriores de integridade.

## Migrations atuais

- `V20260417_00__base_legacy_schema.sql`
- `V20260417_01__create_fechamentos_caixa.sql`
- `V20260713_01__fase0_estoque_decimal.sql`
- `V20260713_02__fase1_variacoes_fichas.sql`
- `V20260713_03__fase2_vendas_snapshots_consumos.sql`
- `V20260714_01__fase25_endurecimento_vendas_cancelamentos.sql`
- `V20260714_02__fase3_disponibilidade_pausas_horarios.sql`
- `V20260714_03__fase4_modificadores_composicao.sql`
- `V20260714_04__fase46_persistencia_concorrencia_integridade.sql`
- `V20260715_01__fase5_composicao_multissabor.sql`

## Banco vazio

Para banco vazio:

1. Criar o database sem tabelas.
2. Iniciar a aplicacao com Flyway habilitado.
3. Flyway executa todas as migrations desde `V20260417_00`.
4. Hibernate valida o schema com `ddl-auto=validate`.
5. Rodar a suite Maven completa.

Comando de validacao local:

```bash
mvn -q -Dtest=Fase46FlywayMigrationIntegrationTest test
```

O teste `bancoVazioExecutaFlywayEHibernateValidate` cria um banco PostgreSQL temporario, executa as
migrations, inicia o contexto Spring e valida que a aplicacao sobe com Hibernate em `validate`.

## Banco existente compativel

Para banco ja criado por `ddl-auto=update` ate a Fase 4:

1. Fazer backup.
2. Validar que o schema possui as tabelas das Fases 0 a 4.
3. Ativar Flyway com `baseline-on-migrate=true`.
4. Usar `baseline-version=20260714.03`.
5. Subir a aplicacao.
6. Flyway registra o baseline e aplica apenas migrations posteriores, como `V20260714_04` e `V20260715_01`.
7. Hibernate valida o schema.

Comando:

```bash
mvn -q -Dtest=Fase46FlywayMigrationIntegrationTest test
```

O teste `bancoExistenteCompativelPodeSerBaselinedEValidado` simula um schema ja migrado, remove o
historico Flyway e comprova que o baseline controlado permite subir e validar sem recriar estrutura.

## Banco existente incompativel

Banco incompleto ou divergente nao deve ser corrigido silenciosamente.

Comportamento esperado:

- Flyway falha em uma migration objetiva.
- Nenhuma migration posterior e executada.
- A aplicacao nao sobe com schema parcial.
- O operador deve aplicar migration corretiva especifica ou restaurar backup adequado.

O teste `bancoIncompativelFalhaAntesDeValidarAplicacao` cria um banco parcial e confirma falha
explicita quando as tabelas esperadas das fases anteriores nao existem.

## Runner temporario de compatibilidade

Propriedades:

```properties
stockos.schema-compatibility-runner.enabled=${SCHEMA_COMPATIBILITY_RUNNER_ENABLED:false}
stockos.schema-compatibility-runner.fail-on-mismatch=${SCHEMA_COMPATIBILITY_RUNNER_FAIL_ON_MISMATCH:true}
```

A partir da Fase 4.6, o runner nao executa DDL estrutural. Ele nao cria tabelas, nao cria colunas,
nao altera constraints e nao deve ser usado como mecanismo de migracao.

Quando habilitado, ele apenas valida divergencias historicas conhecidas e pode falhar de modo claro
se o banco ainda depender de reparos que agora pertencem ao Flyway.

### Tabela de validacoes do runner

| Constraint/indice | Tabela | Motivo | Acao atual | Banco suportado hoje | Condicao para remocao |
| --- | --- | --- | --- | --- | --- |
| `movimentos_estoque_decimal_tipo_check` | `movimentos_estoque_decimal` | Tipos novos como `SAIDA_VENDA` e `ESTORNO_VENDA` precisam estar no check. | Apenas valida se todos os valores do enum existem. | PostgreSQL via `information_schema`. | Todos os ambientes validados por Flyway e Hibernate `validate`. |
| `uk_movimentos_estoque_estorno_unico` | `movimentos_estoque_decimal` | Estornos parciais de venda permitem varios movimentos inversos para a mesma saida original. | Apenas valida que a constraint/indice unico legado nao existe. | PostgreSQL via `information_schema` e `pg_indexes`. | Migration `V20260714_04` aplicada em todos os ambientes. |
| `ck_venda_consumo_origem` | `venda_item_consumos` | Origens de modificadores precisam estar permitidas. | Apenas valida se todos os valores de `OrigemComposicaoVenda` existem. | PostgreSQL via `information_schema`. | Migration `V20260714_04` aplicada em todos os ambientes. |

## Politica de decimais

Backend:

- Dinheiro, custo, quantidade, percentual, rendimento e fatores usam `BigDecimal`.
- Nao usar `double` ou `float` para regras financeiras ou estoque.
- Arredondamento padrao operacional: `RoundingMode.HALF_UP`.

Banco:

- Quantidade: `NUMERIC(19,6)`.
- Fator de conversao: `NUMERIC(19,8)`.
- Custo unitario e custo medio: `NUMERIC(19,8)`.
- Totais monetarios e snapshots financeiros: `NUMERIC(19,4)`.
- Impactos negativos de modificadores continuam permitidos nas colunas de delta/valor de impacto.

Valor atual de estoque continua sendo derivado por:

```text
valorEstoque = quantidadeAtual * custoMedio
```

Snapshots em movimentos e vendas sao historicos auditaveis, nao uma segunda fonte mutavel de saldo.

## Backfill de Produto legado

Os campos novos de `Produto` continuam nullable onde a compatibilidade historica exige. O backfill
futuro deve:

1. Preencher `politica_falta = BLOQUEAR` e `ativo = true` quando o legado nao indicar outro estado.
2. Identificar produtos com unidade-base confiavel.
3. Gerar relatorio de produtos sem unidade determinavel.
4. Marcar esses produtos para revisao manual.
5. Aplicar `NOT NULL` apenas depois que todos os registros forem classificados.

Nao atribuir automaticamente `UNIDADE`, `GRAMA` ou `MILILITRO` para produtos sem evidencia. Unidade
errada corrompe custo medio, ficha tecnica e rentabilidade.

## NotaFiscal e CompraEstoque

Os modelos continuam separados:

- `NotaFiscal`: documento fiscal/comercial legado. Ao confirmar, registra entrada no estoque inteiro
  legado via `EstoqueService`.
- `CompraEstoque`: recebimento operacional decimal. Converte embalagem/unidade para unidade-base,
  valoriza estoque e gera `MovimentoEstoque`.

Risco atual: registrar a mesma mercadoria em `NotaFiscal` e `CompraEstoque` pode duplicar a entrada
em fontes diferentes. A convergencia futura deve permitir que uma `NotaFiscal` gere ou referencie uma
`CompraEstoque`, bloqueie duplicidade por numero/fornecedor/filial, preserve fornecedor e numero fiscal
e suporte recebimento parcial.

## Estoque legado e estoque decimal

`EstoqueProduto` e a fonte de verdade futura para ingredientes, custo medio, ficha tecnica,
disponibilidade e venda nova. O estoque inteiro legado (`Produto.quantidadeAtual` + `Pedido`) permanece
temporariamente por compatibilidade com telas, NotaFiscal, movimentacoes antigas e ranking estimado.

Endpoints legado:

- `POST /api/movimentacoes`
- `GET /api/movimentacoes`
- `POST /api/notas-fiscais/{id}/confirmar`
- fluxos antigos que chamam `EstoqueService.registrarSaidasPorVenda`

Endpoints decimais:

- `GET /api/produtos/{id}/estoque`
- `POST /api/compras`
- `POST /api/compras/{id}/itens`
- `POST /api/compras/{id}/confirmar`
- `GET /api/movimentos-estoque`
- `POST /api/movimentos-estoque/{id}/estornar`
- `POST /api/vendas/{id}/finalizar`
- `POST /api/vendas/{id}/cancelar`

Nao sincronizar silenciosamente os dois modelos em duas direcoes.

## Checklist de producao futura

Antes de aplicar em producao:

1. Backup completo.
2. Restaurar backup em homologacao.
3. Conferir se o schema representa ate a Fase 4 antes do baseline.
4. Executar baseline com `20260714.03`.
5. Aplicar migrations posteriores.
6. Validar com Hibernate `validate`.
7. Rodar testes de compra, venda, cancelamento, ficha, modificadores e ranking estimado.
8. Manter `stockos.schema-compatibility-runner.enabled=false`.
9. Monitorar logs Flyway e erros de integridade na primeira subida.
