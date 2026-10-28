# StockOS SaaS MVP — API experimental

**Não usar em produção.** O perfil `prod` permanece bloqueado até autenticação, multi-tenancy,
migrações e testes ponta a ponta serem validados.

O modo `supabase-jwt` usa tokens assinados e mantém as APIs legadas bloqueadas.
As tabelas SaaS ficam no schema privado `stockos_private`, sem exposição direta
ao Supabase Data API. Os controllers usam UUIDs de empresa/filial **somente após
verificar o vínculo do usuário autenticado**.

## Fluxo implementado (backend)

1. `GET /api/saas/empresas` — empresas acessíveis ao JWT.
2. `GET /api/saas/empresas/{empresa}/filiais/{filial}/produtos` — catálogo.
3. `POST .../produtos` — cadastrar produto (gerente/proprietário).
4. `GET .../comandas` — comandas abertas com total e valor pago.
5. `POST .../comandas` — abrir `{"numero":1}`.
6. `POST .../comandas/{id}/itens` — adicionar item com cabeçalho `Idempotency-Key`.
7. `DELETE .../comandas/{id}/itens/{itemId}` — cancelar item com estorno (gerente/proprietário).
8. `POST .../comandas/{id}/pagamentos` — receber valor parcial, também com chave idempotente.
9. `POST .../comandas/{id}/fechar` — exige pagamento integral.

As operações de entrega e estoque são transacionais. O número de comanda é
reutilizável após fechamento, porém o ID interno é único. O banco possui
índices de unicidade para comandas abertas e operações idempotentes.

**ATENÇÃO:** as operações atuais são um MVP técnico, não um PDV fiscal. Ainda faltam
testes de concorrência reais, melhorias na API (cancelamento/estorno de pagamento),
regras de fechamento de caixa, onboarding seguro, interface de comandas, gestão
de assinatura, migrations de dados legados e recuperação após falhas. O estorno
de item ainda precisa de política de devolução física da mercadoria: cancelar
um item não significa necessariamente devolvê-lo ao estoque.

## Provisionamento

Empresas, filiais e vínculos `membros` não são criados pelo cliente.
Devem ser provisionados por administrador confiável em banco de staging
com auditoria e controle de acesso; não existe endpoint público de inscrição.

Crie projeto Supabase **exclusivo** para StockOS; não use o banco existente da
Space Underground. Use contas restritas para backend, SSL verificado e
credenciais em variáveis de ambiente.

## Testes sugeridos antes de qualquer merge

- Usuário autenticado sem vínculo não acessa produtos nem comandas de outra empresa.
- Atendente não cadastra produto nem cancela estoque.
- Duas tentativas concorrentes para abrir comanda `001` não criam duas abertas.
- Retentativas com a mesma `Idempotency-Key` não reduzem estoque duas vezes.
- Duplicidade de chave com dados diferentes retorna conflito.
- Pagamentos parciais somam corretamente e não permitem saldo excedente.
- Fechamento recusa comandas incompletas e evita duas finalizações.
- Cancelamento de item restaura estoque de modo auditável e transacional.

