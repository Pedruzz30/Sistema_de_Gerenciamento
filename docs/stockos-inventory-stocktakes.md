# StockOS: conferência física de inventário (piloto)

- Somente GERENTE ou PROPRIETARIO com JWT verificado, vínculo ativo com a empresa
  e licença vigente pode conferir e corrigir saldos da filial.
- A requisição informa `estoqueEsperado` (saldo mostrado na tela) e
  `estoqueContado` (quantidade física observada), ambos não negativos,
  com motivo obrigatório (8–240 caracteres) e `Idempotency-Key`.
- O serviço trava a linha do produto no PostgreSQL, valida se o saldo ainda
  corresponde ao saldo esperado e devolve HTTP 409 se uma venda, reposição ou
  outra correção ocorreu desde que o funcionário abriu a tela.
- Ajuste e auditoria são gravados na mesma transação. O registro inclui
  produto, filial, valores antes e depois, diferença, motivo, data, operador
  e chave de idempotência. Retentativas idênticas não alteram o saldo.
- Conferências sem divergência (`diferenca = 0`) também são registradas para
  demonstrar que o estoque físico foi contado.
- Histórico consultável em `GET /api/saas/empresas/{empresa}/filiais/{filial}/produtos/ajustes`,
  inclusive correções com diferenças positivas e negativas.
- Este fluxo é para **corrigir divergências físicas**, não para registrar
  compras de fornecedores, vendas, mercadorias devolvidas ou quebras.
  Esses casos têm registros operacionais próprios.
- Falta fluxo completo de entrada de mercadorias com nota/lote/validade,
  conferência cíclica por categoria, controle de produtos perecíveis,
  relatórios analíticos, E2E real e testes em banco de staging antes do piloto.
