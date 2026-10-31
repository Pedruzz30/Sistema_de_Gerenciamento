# StockOS — migracao do legado de pizzaria para catalogo generico

## Principio

O StockOS deve aceitar diferentes tipos de operacao sem reconhecer tipos de produto por partes do nome. O tipo de venda deve ser explicito:

- `Produto` com controle de estoque para itens estocaveis (inclusive pizzas congeladas);
- `ItemCardapio.PREPARADO_SOB_DEMANDA` para preparados sob demanda, cujo consumo real depende de ficha tecnica;
- `ItemCardapio.ESTOQUE_DIRETO` vinculado a um `Produto` para venda de unidades em estoque.

## Refatoracao concluida nesta etapa

- `PizzariaMenuSeedRunner` substituido por `DemoCatalogSeedRunner`, com exemplos genericamente aplicaveis.
- Removida de `EstoqueService` a rejeicao de produtos iniciados por "Pizza".
- Removida a rotina que desativava controle de estoque por nome do produto.
- Flag migrada de `STOCKOS_SEED_DEMO_MENU_ENABLED` para `STOCKOS_SEED_DEMO_CATALOG_ENABLED`; permanece desativada por padrao.
- Testes do seed e do endpoint de cardapio adaptados a exemplos genericos.
- Nenhuma migration foi alterada e nenhum dado existente sera excluido.

## O que deve continuar

Fichas tecnicas, modificadores, composicao multipla/multissabor e snapshots de vendas continuam como funcionalidades opcionais. Nao remover migrations vinculadas nem rescrever historico financeiro.

## Limites e proxima etapa

O seed e para ambiente local e nao substitui onboarding por estabelecimento. Antes de vender o SaaS: autenticacao com senhas protegidas e sessoes, autorizacao server-side robusta, isolamento de dados por estabelecimento, politicas de catalogo por cliente e testes de acesso cruzado. Dados legados presentes em bancos existentes devem ser revisados manualmente com integridade referencial; esta etapa nao os apaga.
