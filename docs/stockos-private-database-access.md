# StockOS — fronteira SQL e roles (fundação)

## O que a migração garante
- Schema privado `stockos_private` continua fora da configuração da Data API.
- `PUBLIC` perde USAGE/CREATE no schema e privilégios sobre tabelas, funções e sequências.
- Quando existirem, `anon`, `authenticated` e `service_role` têm seus GRANTs diretos
  explicitamente revogados no schema e objetos existentes.
- Nenhuma nova conta ou role é criada automaticamente. Flyway continua com o
  usuário confiável de migração.

## O que NÃO garante
- O papel `postgres`, donos dos objetos e papéis com `BYPASSRLS` ainda têm
  privilégios especiais. **O backend legado ainda usa uma conexão privilegiada**.
- Um papel SQL que herde privilégios por outra role ou tenha grants via outro
  schema precisa de auditoria separada.
- RLS das tabelas privadas está habilitada como barreira adicional mas, sem
  políticas específicas para um papel limitado, acesso via esse papel será
  negado por padrão. Não é seguro simplesmente trocar `DB_USERNAME` agora.
- O Java confere as empresas e filiais nos serviços SaaS. Uma vulnerabilidade
  no backend privilegiado ainda pode afetar outros estabelecimentos. Para
  resolver isso, precisa separar migrações e acesso de execução, definir
  policies tenant-scoped e passar um contexto de empresa verificado para cada
  transação; depois testar todas as rotas.

## Plano seguro de implantação (ainda não executar)
1. Configurar Supabase **dedicado ao StockOS**; jamais reutilizar banco da Space.
2. Inspecionar os grants atuais e schemas expostos da Data API no projeto novo.
3. Aplicar migrations usando uma role de migração controlada; avaliar permissões
   reais de `anon`, `authenticated`, `service_role` e usuários herdados.
4. Separar runtime JDBC com acesso mínimo sem `CREATEROLE`, `CREATEDB`,
   `BYPASSRLS` nem acesso a schemas legados.
5. Criar policies de isolamento por empresa com contexto verificado e executar
   testes de leitura e escrita entre empresas, incluindo concorrência.
6. Testar backup e restauração antes de habilitar perfil de produção.

Referência: https://supabase.com/docs/guides/api/securing-your-api

A PR permanece em rascunho e o perfil `prod` continua bloqueado.
