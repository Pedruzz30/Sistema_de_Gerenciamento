# StockOS SaaS — fornecedores e recebimento de mercadorias

A base Java legada já tinha módulos de fornecedores e compras, porém eles não
possuem o isolamento da nova arquitetura SaaS. Esta implementação cria um fluxo
segregado em `stockos_private` sem habilitar os endpoints antigos.

## Fluxo do piloto

1. Gerente/proprietário cadastra fornecedor na empresa (documento opcional).
2. Seleciona filial, fornecedor, documento de referência e até 100 produtos.
3. Informa quantidade e custo unitário de cada produto.
4. O backend confere produtos ativos e pertencentes à filial, trava as linhas
   e registra documento, itens e acréscimo de estoque **na mesma transação**.
5. Uma chave idempotente evita repetir a entrada em caso de reenvio. Documento
   repetido do mesmo fornecedor na filial também é bloqueado.
6. O histórico mostra quem registrou, quando, fornecedor, referência e custo.

O custo unitário é um *snapshot* do recebimento, não altera
automaticamente o preço de venda e não implementa CMV médio.
A referência do documento é uma anotação operacional, **não valida NFC-e/NF-e,
CFOP, XML, tributação ou contas a pagar**.

### Limitações e validações pendentes

- Testes PostgreSQL e de concorrência, operações do frontend com usuários reais;
- Cadastro/edição de produtos, fornecedor e parâmetros comerciais mais completos;
- Compras planejadas, recebimento parcial, histórico fiscal por lote e validade,
  integração NF-e, custo médio, contas a pagar, cancelamento autorizado de entrada;
- Isolamento multiempresa em todas as APIs e privilégio mínimo no banco;
- Infraestrutura, backup/restauração e auditoria de segurança para produção.

Nunca implantar essa PR em produção até finalizar esses bloqueadores.
