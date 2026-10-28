# StockOS — Catálogo e relatórios operacionais (staging)

## Catálogo
- POST /api/saas/empresas/{empresa}/filiais/{filial}/produtos — criar produto.
- PUT /.../produtos/{produto} — atualizar nome, código, preço e ativo com
  `versaoEsperada`; saldo de estoque **não** é editável nesta API.
- GET /.../produtos/{produto}/historico — auditoria de catálogo.

Alterações simultâneas são rejeitadas com HTTP 409; não sobrescreva
sem recarregar. Preços históricos dos itens da comanda são snapshots,
de forma que alterar o preço atual não muda vendas antigas.

## Relatórios
- GET /api/saas/empresas/{empresa}/filiais/{filial}/relatorios/financeiro?inicio=...&fim=...
- GET /.../relatorios/estoque-baixo?quantidadeMaxima=5&limite=50
- GET /.../relatorios/produtos-mais-entregues?inicio=...&fim=...&limite=20

Datas em RFC 3339 com fuso, fim exclusivo e janela de até 366 dias.
O relatório financeiro mostra **fluxos de pagamentos registrados**:
recebimentos - estornos - devoluções pós-fechamento, agrupados por
modalidade. Não é lucro, conciliação bancária, nota fiscal ou receita
reconhecida; pode incluir pagamentos de comandas ainda abertas e
estornos de pagamentos antigos, registrados no período atual.
O relatório de entregas é bruto e pode incluir bens devolvidos depois;
não representa receita líquida nem margem.
