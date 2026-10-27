# StockOS — convites de funcionários (staging)

## Fluxo
1. Proprietário com licença ativa entra em Comandas > Funcionários.
2. Informa e-mail e papel GERENTE ou ATENDENTE. A API gera 32 bytes aleatórios,
   codificados em URL-safe Base64. O código só aparece na resposta inicial.
3. O backend armazena **somente SHA-256** do código, o e-mail normalizado,
   empresa, papel, emissor e vencimento de 72 horas. Convites pendentes reservam
   vagas do plano e podem ser revogados.
4. Proprietário entrega o código ao destinatário por canal privado. O envio de
   e-mail **não está integrado** e o StockOS **não cria** contas Supabase.
5. A pessoa cria/verifica sua própria conta no Supabase Auth do projeto StockOS,
   entra no painel e informa o código no formulário "Recebeu um convite?".
6. O backend consulta Supabase Auth `GET /auth/v1/user` com o **access token
   da sessão atual**, exige UUID idêntico ao `sub` validado, confirmação de
   e-mail e comparação com o destinatário exato do convite. Não confia em
   `user_metadata`, e-mail enviado pelo navegador nem em UUID escolhido.
7. Sob bloqueio da empresa e do convite, exige status PENDENTE, prazo válido,
   emissor ainda proprietário, licença válida, vaga disponível e não existência
   prévia de vínculo. Insere a associação e consome o convite em uma transação.

## Segurança e limitações
- Nunca registrar código puro em logs, analytics, e-mails automáticos não
  configurados ou URLs; o destinatário deve digitá-lo diretamente no formulário.
- O dono recebe o código **uma vez**. Perda do código exige revogar e gerar outro.
- Convites pendentes antigos expiram; recriação revoga convite expirado do mesmo
  e-mail. Não há tarefa automática de limpeza periódica.
- Conferência de e-mail confirmado depende da disponibilidade do Auth e
  **não foi validada ainda com um projeto Supabase real**. CI usa identidade
  simulada em testes PostgreSQL. É necessário teste de integração real, também
  de revogação de sessão e mudança de endereço de e-mail.
- Convites reservam vagas, mas mudanças simultâneas de licença e outros
  ajustes administrativos ainda requerem auditoria rigorosa de concorrência.
- Endpoints: POST/GET /api/saas/empresas/{id}/convites;
  DELETE /api/saas/empresas/{id}/convites/{conviteId};
  POST /api/saas/convites/aceitar.
- O piloto continua bloqueado para produção. Nenhum Supabase real foi modificado.

Fontes oficiais:
https://supabase.com/docs/guides/auth/jwt-fields
https://supabase.com/docs/guides/auth/passwords
