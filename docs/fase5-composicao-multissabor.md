# Fase 5 - Composicao multissabor

## Compatibilidade de migracao

- Versao anterior esperada do Flyway no projeto: `10.10.0`.
- Versao adotada: `12.11.0`.
- Spring Boot adotado: `3.5.16`.
- PostgreSQL testado localmente: `18.3`.
- Dependencias Flyway explicitas:
  - `org.flywaydb:flyway-core`
  - `org.flywaydb:flyway-database-postgresql`

O Flyway 12 foi adotado porque a linha 10 executava as migrations, mas avisava que o PostgreSQL 18 era mais novo que a versao suportada/testada. A primeira tentativa de usar Flyway 12 com Spring Boot 3.3 falhou em runtime por incompatibilidade de API de autoconfiguracao; por isso o Spring Boot tambem foi atualizado.

## Migration

A estrutura nova fica em:

```text
src/main/resources/db/migration/V20260715_01__fase5_composicao_multissabor.sql
```

Ela cria:

- `configuracoes_composicao_multipla`;
- `configuracao_composicao_multipla_partes`;
- colunas de snapshot multissabor em `venda_itens`;
- `venda_item_partes`;
- vinculo opcional `venda_item_parte_id` em `venda_item_modificadores`;
- vinculo opcional `venda_item_parte_id` em `venda_item_consumos`.

As migrations seguem o fluxo esperado do Flyway: scripts versionados, sem `ddl-auto=update` e sem scripts genericos que silenciem divergencias estruturais.

## Modelo de dominio

Um item multissabor continua sendo uma unidade comercial:

```text
1 VendaItem
1..N VendaItemParte
```

Cada parte referencia uma `VariacaoItemCardapio` vendavel e preserva snapshot de:

- item e variacao;
- proporcao;
- preco de referencia;
- custo calculado;
- ficha tecnica e versao, quando houver;
- hash da configuracao da parte;
- ordem visual;
- status.

O movimento fisico de estoque continua consolidado por produto antes dos locks. O consumo detalhado pode preservar a origem por parte via `venda_item_parte_id`.

## Politicas implementadas

Politicas de preco:

- `MAIOR_PRECO`;
- `MEDIA_ARITMETICA`;
- `MEDIA_PONDERADA`;
- `SOMA_PROPORCIONAL`;
- `PRECO_FIXO_DA_VARIACAO`.

Politica de proporcao:

- proporcoes iguais calculadas pelo backend;
- proporcao personalizada validada com soma `1.000000`;
- escala persistida de proporcao: `numeric(19, 6)`.

Politica de custo:

- custo calculado pela composicao fisica proporcional;
- produtos iguais entre partes sao consolidados no movimento de estoque;
- custo da parte e atualizado a partir dos consumos planejados e do custo medio da filial no momento da finalizacao.

## Hash e idempotencia

O hash multissabor inclui:

- variacao principal;
- configuracao multissabor;
- politica de preco;
- regras de quantidade e proporcao;
- partes escolhidas;
- proporcoes oficiais;
- preco oficial das partes;
- ficha tecnica e versao, quando aplicavel.

Mudanca operacional de preco, ficha ou configuracao provoca erro:

```text
CONFIGURACAO_MULTISSABOR_ALTERADA
```

O payload de idempotencia de finalizacao inclui os campos de snapshot multissabor do item.

## Cancelamento

Cancelamento usa os snapshots de `VendaItemConsumo` e os movimentos originais. O cancelamento de venda ou item marca as `VendaItemParte` como `CANCELADO` e nao consulta configuracao atual, preco atual ou ficha atual para devolver estoque.

## Testes adicionados

- `Fase5MigrationSqlTest`;
- `Fase5MultissaborApiIntegrationTest`.

Cobertura objetiva:

- migration da estrutura multissabor;
- previsao oficial de preco e proporcoes;
- duas e tres partes;
- politicas `MAIOR_PRECO`, `MEDIA_ARITMETICA`, `MEDIA_PONDERADA` e `PRECO_FIXO_DA_VARIACAO`;
- parte duplicada bloqueada;
- parte nao permitida bloqueada;
- soma de proporcoes invalida bloqueada;
- venda com quantidade maior que um;
- snapshot de partes;
- consumos por parte;
- movimento fisico consolidado por produto;
- cancelamento por snapshot;
- hash antigo bloqueando finalizacao;
- concorrencia de duas vendas disputando a ultima capacidade.

## Limites restantes

Ainda nao estao fechados nesta implementacao:

- UI administrativa e PDV completas para montagem multissabor;
- disponibilidade consolidada em endpoint dedicado com todos os motivos/capacidade por parte;
- regras avancadas de modificador global versus por parte;
- incompatibilidade parte contra parte e opcao de parte contra outra parte;
- benchmark de carga com os volumes especificados;
- autorizacao granular real alem do isolamento das rotas administrativas ja existente;
- cancelamento de uma parte isolada de uma unidade, que continua fora do escopo.
