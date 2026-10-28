# Devoluções após fechamento — piloto StockOS

- Não reabre comandas ou turnos já fechados.
- Somente GERENTE ou PROPRIETARIO pode registrar devolução integral de um pagamento de comanda FECHADA.
- Requer um turno de caixa atualmente aberto, motivo, referência de comprovante, declaração explícita do operador de que a devolução externa já foi executada e chave idempotente.
- O registro não envia Pix, não cancela cartão e não transfere recursos. É apenas a declaração auditável do operador; o sistema não verifica o comprovante com o provedor.
- O pagamento original e o histórico do turno anterior permanecem inalterados.
- Uma devolução de dinheiro reduz o dinheiro esperado do turno atual, enquanto Pix, débito e crédito são contabilizados separadamente como saídas registradas.
- Um pagamento pode ter no máximo uma devolução integral e jamais pode ter um estorno de comanda aberta e uma devolução de venda fechada.
- Exige testes em PostgreSQL descartável, provas de concorrência, política de compensação bancária/fiscal e validação da interface antes de uso real.
