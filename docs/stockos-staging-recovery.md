# StockOS — Staging, Auth, backups e restauração

## Estado atual
**StockOS Staging agora existe**, na organização Space Underground, região `us-east-1`, referência `earocdtfxtvcsfsfxbvt`. O banco está vazio e não existe implantação nem backup de clientes configurado. Auth real ainda não foi testado.
Perfil Spring `prod` bloqueado por código.

## Configuração isolada
1. Projeto criado: `StockOS Staging` (`earocdtfxtvcsfsfxbvt`). Não reutilizar
   os bancos existentes da Space Underground.
2. Selecionar assinatura assimétrica JWT com endpoint JWKS (não HS256 legado).
   Obter URL `https://<ref>.supabase.co` e **publishable key**
   `sb_publishable_...`. Nunca usar `service_role`/`sb_secret` no browser.
3. Configurar em ambiente privado:
   `STOCKOS_SECURITY_MODE=supabase-jwt`,
   `STOCKOS_SUPABASE_AUTH_ISSUER=https://<ref>.supabase.co/auth/v1`,
   `STOCKOS_SUPABASE_URL=https://<ref>.supabase.co`,
   `STOCKOS_SUPABASE_PUBLISHABLE_KEY=sb_publishable_...` e
   `STOCKOS_SUPABASE_EXPECTED_PROJECT_REF=earocdtfxtvcsfsfxbvt`.
   Config inválida impede inicialização. Habilitar SSL verificado no JDBC
   conforme certificado e connection string providos pelo projeto.
4. O fluxo de convites com código de uso único está implementado; o envio de
   e-mails automáticos e testes de identidade com Supabase Auth real ainda faltam.
5. Validar todas as rotas e políticas, papéis DB e logs ANTES de liberar
   portas externas; não usar credenciais de superuser em produção.
6. **NÃO remover o bloqueio `prod`** por conta destas etapas.

## Backup e recuperação
- O CI usa contêiner PostgreSQL 17 **descartável**, faz `pg_dump -Fc`
  do banco de testes e `pg_restore` em segundo banco vazio do mesmo
  contêiner. Compara contagens em tabelas de SaaS e histórico Flyway.
- O arquivo temporário é eliminado ao fim do job; nenhum dump é enviado
  como artefato público e nenhum banco Supabase é acessado.
- Isso prova somente o procedimento técnico no CI, **não** equivale a
  backups diários de produção, ponto de recuperação, criptografia off-site
  ou restauração de Auth/Storage.
- Para o projeto Supabase futuro, revisar backups gerenciados, retenção
  específica do plano, PITR se necessário, criptografia, controle de
  acesso, armazenamento externo separado e exercícios de restauração.
- Supabase Storage armazena blobs fora do banco: o backup da base contém
  metadados e **não** recupera automaticamente arquivos do Storage.

Referências oficiais:
https://supabase.com/docs/guides/platform/backups
https://supabase.com/docs/guides/auth/jwts
https://supabase.com/docs/guides/getting-started/api-keys
