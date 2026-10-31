# StockOS — Supabase Auth (Staging, opt-in)

## O que foi implementado

- No modo `legacy` (padrão), o StockOS continua usando o login local e o Docker isolado.
- No modo `supabase`, o formulário solicita **e-mail/senha** e obtém uma sessão do projeto Supabase.
- Chamadas à API enviam `Authorization: Bearer <access_token>`; o backend verifica assinatura assimétrica **ES256/RS256** via JWKS do issuer, expiração, `iss`, audience `authenticated`, `role=authenticated` e `sub` UUID.
- O servidor resolve o UUID exclusivamente contra `usuarios.supabase_auth_user_id`, precisa encontrar usuário *ativo*, e obtém as permissões da classe do StockOS. **O `X-User-RU` recebido do navegador é ignorado** em modo Supabase.
- O formulário legado `POST /api/auth/login` é bloqueado em modo Supabase. O seed de administrador é desabilitado nesse modo. O preview do Netlify permanece mock.
- Uma migration Flyway **aditiva** cria `usuarios.supabase_auth_user_id` (único e nullable).
- O JWT nunca contém a autoridade real do operador; ele identifica a pessoa. Autorizações são baseadas no usuário vinculado no banco.

## Limites de segurança — não publicar ainda

**Não existe isolamento de multi-tenancy nesta etapa.** Usuários de empresas diferentes poderiam ler dados compartilhados através dos repositórios legados. Habilitar Supabase Auth **não** torna a plataforma segura para múltiplos estabelecimentos.

Não há convite/onboarding, políticas de filial, gestão de sessões sensíveis, MFA obrigatório, remoção de senhas legadas, nem testes E2E completos de login com um projeto real.

Não faça deploy público nem compartilhe credenciais reais. A implantação exige uma etapa posterior de `tenant_id` obrigatório e autorização por estabelecimento em todas as operações. O projeto Supabase do Space Underground permanece isolado.

## Preparação e ativação local (somente staging)

1. Configure um projeto **StockOS Staging** separado do Space Underground.
2. Em Supabase Auth, confirme que a chave ativa de assinatura JWT é **assimétrica** (ES256 ou RS256). Tokens HS256 legados serão **rejeitados** por este backend. Não compartilhe JWT secret nem service role com o frontend.
3. Verifique/aplique a migration Flyway `V20261031_01__stockos_supabase_auth_link.sql` somente no **PostgreSQL local Docker**, antes do primeiro login.
4. Em `.env`, configure os valores públicos abaixo, com os dados específicos do projeto StockOS Staging:

```dotenv
STOCKOS_AUTH_MODE=supabase
STOCKOS_SUPABASE_URL=https://SEU-PROJETO-STOCKOS.supabase.co
STOCKOS_SUPABASE_PUBLISHABLE_KEY=sb_publishable_EXEMPLO
STOCKOS_CORS_ORIGIN=http://127.0.0.1:8080
```

As informações de projeto/token **não devem ser reutilizadas** do Space Underground. No Compose, o JDBC continua apontando para `stockos_dev` local — nada conecta o banco remoto.

5. Na primeira utilização do PostgreSQL Docker novo, os usuários de demonstração podem ainda não existir. Inicie o aplicativo em modo `legacy` **apenas localmente** uma vez para provisionar as contas legadas de desenvolvimento; confirme os RUs no próprio banco. Desative o aplicativo antes de mudar de modo. Nunca expor este modo à Internet.
6. Cadastre/invite uma conta de teste via Supabase Auth e obtenha o UUID a partir da ferramenta **Users** do próprio projeto StockOS.
7. Ligue esse UUID **manualmente**, sem fazer correspondência por e-mail, CPF ou outros dados editáveis. Em um SQL editor ligado ao banco `stockos_dev` local, execute **após revisar o RU correto**:

```sql
UPDATE public.usuarios
SET supabase_auth_user_id = '<UUID-DE-TESTE-AUTH>'
WHERE ru = <RU-DO-USUARIO-EXISTENTE>;
```

Revise o resultado com `SELECT ru, nome, ativo, supabase_auth_user_id FROM public.usuarios` sem expor CPF/senha.

8. Execute `docker compose --profile app up -d --build app`. Navegue para `http://127.0.0.1:8080/login.html` e use o login do Supabase.
9. Verifique que uma conta **sem vínculo** recebe 403 e que um token ausente ou inválido recebe 401 em `/api/auth/me` e nos demais endpoints de negócio.

### Manter histórico e dados separados

Não executar testes Maven no banco Supabase Cloud; eles criam e alteram dados. Não importar o banco Windows sem auditar as versões Flyway. A PR #31 tem como base a PR #30 e não deve ser mesclada primeiro.

## Próximas alterações indispensáveis

- Design do modelo `tenant_id` / `estabelecimento_id`, vínculos `user -> tenant -> permissions`.
- Aplicar tenant a **todas** as queries, transações, operações de caixa, estoque, log e Storage, com testes de acesso cruzado.
- RLS nas tabelas expostas pelo Supabase Data API. O backend JDBC deve aplicar o próprio controle de acesso; não assumir que o JWT do cliente opera automaticamente como identidade SQL da conexão.
- Retirar credenciais legadas em claro e seed de contas reais, regras de sessão e auditoria de acesso; revisar os endpoints de administração.
- Configurar frontend de produção e API hospedada somente após testes E2E de autorização.

Guia: https://supabase.com/docs/guides/auth/jwts
