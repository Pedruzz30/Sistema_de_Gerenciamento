# StockOS: devoluções físicas após fechamento

Devolução de mercadoria é um evento independente da devolução do valor ao consumidor.

- Um gerente/proprietário autenticado deve confirmar quantidade e condição do
  produto efetivamente devolvido em uma comanda FECHADA.
- Escolher `REESTOQUE` só quando o item estiver fisicamente recuperado e apto
  à revenda; `DESCARTE` registra a devolução mas **não aumenta o estoque**.
- Não é permitido devolver mais unidades do que foram originalmente entregues,
  mesmo entre solicitações de dois operadores ao mesmo tempo.
- Chave de idempotência, motivo, usuário e horário permanecem armazenados.
- Produto/filial/empresa vêm dos registros confiáveis do backend, não de um
  ID de produto escolhido pelo navegador. O banco aplica FKs de escopo por filial.
- O estoque é alterado uma única vez em transação com o registro de retorno.
- Não reabre nem altera a comanda vendida, o pagamento ou turnos de caixa fechados.
- **Não registra reembolso financeiro automaticamente** nem o exige: uma troca de
  mercadoria ou recebimento físico pode ocorrer sem devolução financeira.
- Ainda faltam políticas comerciais de devolução, fiscalização sanitária de
  produtos perecíveis, identificação de lote/validade, testes ponta a ponta no
  navegador e integração fiscal. Não executar comercialmente sem revisão.
