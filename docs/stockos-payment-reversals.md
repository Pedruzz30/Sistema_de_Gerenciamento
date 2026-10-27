# Estornos de pagamentos — piloto StockOS

**Implementação técnica de estorno integral registral**. Não é reembolso bancário ou fiscal.

- Apenas GERENTE/PROPRIETARIO autenticado e vinculado à empresa pode estornar.
- Exige licença ativa, comanda aberta, turno de caixa ainda aberto e o **mesmo
  turno em que o pagamento original foi registrado**.
- Requer motivo de 8–240 caracteres e Idempotency-Key.
- O original permanece na tabela `pagamentos`; registra-se um evento separado
  em `pagamento_estornos` com autor, motivo, horário, valor, modalidade e turno.
- Um pagamento só pode ser estornado integralmente uma vez. Repetir a mesma
  operação com a mesma chave idempotente devolve o registro anterior; outra
  chave ou parâmetros conflitantes retornam conflito.
- Saldos de comanda e totais por modalidade do turno **desconsideram** os
  pagamentos que já têm estorno registrado. Dinheiro em caixa é reduzido por
  estornos de pagamentos em espécie; não mistura Pix/cartões no caixa físico.
- Se o caixa esperado não cobrir a devolução em dinheiro, a operação é
  bloqueada até que o caixa seja conciliado.
- Não permite estorno posterior ao fechamento da comanda ou do turno.
  Isso exige um fluxo financeiro específico de devolução pós-venda (pendente).
- **Pix e cartão:** registrar estorno NÃO cancela automaticamente transferência
  nem transação na adquirente. O responsável deverá executar e documentar a
  devolução externa, usando o comprovante do provedor. Não anunciar como
  reembolso realizado sem sua confirmação.

Faltam estornos parciais, documentação fiscal, política formal de autorização,
prova de devolução do valor, controle de dinheiro físico e teste end-to-end
com o operador em ambiente de staging antes de uso comercial.
