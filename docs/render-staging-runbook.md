# StockOS — homologação temporária Render + Supabase Auth

> Estado em 03/11/2026: banco **Render PostgreSQL Free** criado, aplicação
> Java ainda **não hospedada**. Não publicar serviços financeiros nem
> executar migrations no projeto `StockOS Staging` do Supabase.

## Verificação dos conectores — 10/11/2026

Esta observação é um **estado pontual** conferido pelos conectores, não um teste de homologação:

- **Supabase `StockOS Staging`: INACTIVE.** A autenticação real não está disponível para testes com dois usuários neste momento. O projeto `Space Underground` é independente e **não deve ser utilizado como alternativa**.
- **Render PostgreSQL `stockos-java-staging-temp`: available**, plano free; previsão de expiração **03/12/2026**. Ainda não há indicação de que as roles `stockos_app` e `stockos_migrator` estejam provisionadas — não assumir que estão.
- **Render web services**: somente o serviço da Space Underground foi encontrado; **nenhum backend StockOS de staging foi implantado**. Não acessar nem reconfigurar o serviço Space.
- A branch de segurança passou na CI com PostgreSQL descartável **sintético**, mas isso não constitui teste com JWT emitido pelo projeto Supabase.

**Próximo passo seguro:** reativar o projeto de autenticação StockOS apenas após verificar impacto de cobrança; provisionar roles separadas no PostgreSQL **descartável** com credenciais armazenadas em secrets; aplicar Flyway como migrator; executar `03-verify-runtime-access.psql` como runtime. Só depois criar web service de homologação opt-in sem auto-deploy, usando as variáveis e checklists abaixo. **Sem essas verificações, manter PR #33 em draft e impedir deploy comercial.**

Perfis `staging`, `prod` e `production` agora usam guard de autenticação antecipado; o fallback `legacy` não pode ser usado nesses perfis. Para um servidor remoto, o perfil ativo deve ser explicitamente configurado e conferido — nunca depender do perfil padrão local.

## Inventário e isolamento

| Recurso | Identificador | Finalidade |
| --- | --- | --- |
| Render PostgreSQL | `stockos-java-staging-temp` | Banco vazio e descartável para Flyway Java |
| Supabase | `StockOS Staging` | Apenas autenticação JWT; **não** migrar `stockos_private` |
| GitHub | `integration/stockos-jwt-tenant-security-20261102` | Código de homologação, PR #33 em draft |
| Netlify | `spacestocklab.netlify.app` | Demo pública com dados fictícios, independente |

O banco Render usa plano **free**, PostgreSQL 17, região Oregon e expira em
**03/12/2026**. Não há garantia de backups. Use exclusivamente dados de teste.
Não converter o plano, aumentar recursos nem criar hospedagem paga sem aprovação.

## Bloqueio de inicialização antecipado (staging)

O `StagingAuthenticationModeGuard` agora exige **antes de criar o contexto
Spring, executar Flyway ou iniciar a API**:

- `STOCKOS_AUTH_MODE=supabase` e URL Supabase não vazia;
- `DB_URL`, `DB_USERNAME` e `DB_PASSWORD` explicitamente configurados;
- URL JDBC PostgreSQL não-local;
- `stockos.migrations.mode=external` e `spring.flyway.enabled=false`;
- nenhuma variável `SPRING_FLYWAY_USER`, `SPRING_FLYWAY_PASSWORD` ou
  `SPRING_FLYWAY_URL` no serviço web;
- runtime não nomeado `postgres`, `stockos_local` ou `stockos_migrator`.

O teste `StagingAuthenticationModeGuardTest` cobre ausência de cada variável,
URL local, segredos de migração presentes na API e desenvolvimento legado separado. Esta
verificação **não substitui** o guard PostgreSQL que inspecciona privilégios
efetivos após conectar, nem autoriza provisionar o serviço antes dos grants.

## Pré-condições para criar o serviço web

1. Confirmar que o banco Render está `available`, sem tabelas de clientes.
2. Provisionar **duas credenciais distintas**: `stockos_migrator` e
   `stockos_app`. Senhas fortes geradas em cofre e nunca inseridas no Git.
3. O usuário **migrator** executa DDL/Flyway. O usuário **app** tem apenas
   `CONNECT`, `USAGE`, DML necessário e acesso às sequences. Ele não é
   dono de tabelas, não é membro do migrator e não tem poderes de DDL,
   `SUPERUSER` ou `BYPASSRLS`.
4. Aplicar Flyway somente em banco descartável usando credenciais do
   migrador, com `FLYWAY_BASELINE_ON_MIGRATE=false`. Depois, confirmar
   grants com a conta runtime. O guard `SupabaseSchemaCompatibilityGuard`
   rejeita o modelo antigo `stockos_private`.
5. Criar serviço Render Docker **free**, com `autoDeploy=no` na branch de
   homologação. Não usar `main` nem ativar deploy automático da PR #33.
6. Configurar as variáveis abaixo no gerenciador de segredos do Render.
   O nome `PORT` é injetado pelo Render e utilizado por
   `application-staging.properties`.

## Variáveis do backend (somente secrets / Render dashboard)

```text
SPRING_PROFILES_ACTIVE=staging
STOCKOS_AUTH_MODE=supabase
STOCKOS_SUPABASE_URL=https://<ref-do-projeto-stockos>.supabase.co
STOCKOS_SUPABASE_PUBLISHABLE_KEY=<sb_publishable_...>
DB_URL=jdbc:postgresql://<host-privado-do-Render>:5432/<db>?sslmode=require
DB_USERNAME=stockos_app
DB_PASSWORD=<runtime-secret>
STOCKOS_SEED_DEMO_CATALOG_ENABLED=false
SPRING_FLYWAY_ENABLED=false
STOCKOS_MIGRATIONS_MODE=external
FLYWAY_BASELINE_ON_MIGRATE=false
```

Para evitar gravar segredos nos logs, não habilitar SQL debug e não repassar
`DB_URL` ou credenciais ao frontend. `STOCKOS_SUPABASE_PUBLISHABLE_KEY` é
pública por natureza, mas `service_role`, JWT secret e senhas do banco nunca
devem ser enviados ao navegador. Chaves JWT assimétricas e JWKS ativo são
obrigatórios; HS256 legado não é suportado pelo backend.

### Script interativo de provisionamento pronto

Arquivo: `scripts/staging/01-provision-roles.psql`. Execute **somente**
em uma conexão administrativa autorizada ao banco descartável do Render
(`psql -X -v ON_ERROR_STOP=1 -f scripts/staging/01-provision-roles.psql`).
O script verifica o nome exato do banco, a permissão para criar roles,
recusa sobrescrever roles existentes e solicita as duas senhas
interativamente via `\password`, sem gravá-las no Git. As roles começam
com `NOLOGIN`; todo o processo, inclusive a configuração de senhas, ocorre
em uma transação. Se houver erro ou interrupção, o `psql` encerra a sessão
e desfaz a transação, sem deixar contas incompletas.

A conexão externa ao banco está bloqueada por sua allowlist e o conector
disponível não consegue executar DDL. **Não abra a allowlist para
`0.0.0.0/0` e não execute este script no Supabase Staging.**
Faça o provisionamento em um ambiente administrativo autorizado dentro da
rede privada do Render ou via acesso pontual de IP conhecido e restrito.
Após aplicar Flyway externamente, conceder as permissões DML/sequence
ao runtime e confirmar os testes de negação antes de hospedar a API.

### Provisionamento seguro de roles (administrador, banco DESCARTÁVEL)

Com acesso `psql` usando a conta administradora do novo banco, conectar ao
banco exato `stockos_java_staging_temp`. Substituir as senhas por valores do
cofre **em execução privada**, nunca salvar SQL com valores preenchidos.

```sql
CREATE ROLE stockos_migrator LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
CREATE ROLE stockos_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
-- Definir senhas de cada papel através de \password stockos_migrator e
-- \password stockos_app, interativamente no psql.
GRANT CONNECT ON DATABASE stockos_java_staging_temp TO stockos_migrator, stockos_app;
GRANT USAGE, CREATE ON SCHEMA public TO stockos_migrator;
GRANT USAGE ON SCHEMA public TO stockos_app;
REVOKE CREATE ON SCHEMA public FROM stockos_app;
```

**Atenção à propriedade das tabelas:** todas as migrations devem ser
executadas como `stockos_migrator` em um processo de administração separado
do web service. O processo web recebe **apenas** a senha de `stockos_app`.
Nenhuma senha do migrador deve permanecer entre as variáveis do serviço.  Se o schema/tabelas antigos tiverem
outro proprietário, não usar `SET ROLE` sem revisão. Depois do Flyway,
conceder DML e sequences ao runtime conforme
[`postgresql-least-privilege.md`](postgresql-least-privilege.md), ajustando
o nome do banco e revisando grants herdados, funções e RLS.

**Separação de propriedade do schema:** a remoção de `CREATE` herdado
de `PUBLIC` acontece durante `01-provision-roles.psql`, executado
pela conta administrativa proprietária do schema. O migrador não executa
`REVOKE` sobre o schema, pois não deve ser seu proprietário.
`02-grant-runtime-access.psql` verifica que `PUBLIC` já não tem
`CREATE` e aborta se a condição não for atendida.

## Grants após Flyway e auditoria sem privilégios

Após aplicar Flyway **no banco Render descartável**, como `stockos_migrator`,
execute `psql -X -v ON_ERROR_STOP=1 -f scripts/staging/02-grant-runtime-access.psql`.
Esse script exige o banco e usuário exatos, rejeita a presença de tabelas
`stockos_private` do modelo Supabase antigo e concede somente DML/sequence
ao runtime (não DDL).

Em uma **nova conexão como `stockos_app`**, execute
`psql -X -v ON_ERROR_STOP=1 -f scripts/staging/03-verify-runtime-access.psql`.
O script verifica ausência de superuser, poderes de criação, BYPASSRLS,
vínculo com migrador e propriedade de tabelas. O teste automático
`node scripts/staging/test-provision-roles.mjs` valida a estrutura desses
três scripts, **não executa SQL remoto**.

## Check de negação antes de expor a API

Como usuário `stockos_app` no banco descartável:

```sql
SELECT current_user, current_database(), current_schema();
SELECT rolsuper, rolcreatedb, rolcreaterole, rolbypassrls
  FROM pg_roles WHERE rolname = current_user;
SELECT has_database_privilege(current_user, current_database(), 'CREATE');
SELECT has_schema_privilege(current_user, current_schema(), 'CREATE');
```

Os seis indicadores de privilégio administrativo devem estar ausentes/false.
Executar uma tentativa `CREATE TABLE` em uma transação separada e confirmar
erro de permissão; não tente em `stockos_private`. Confirmar que a aplicação
inicia com os guards ativos e consulta apenas tenants vinculados.

## Smoke tests HTTP esperados (HTTPS)

- `GET /api/auth/config` responde somente metadados públicos.
- `GET /api/v2/estabelecimentos` sem bearer JWT → 401.
- Token falso, expirado, com role ou issuer inválidos → 401.
- Bearer válido, mas UUID sem vínculo operacional → 403.
- Conta da loja A → somente lojas vinculadas à conta A.
- Tentar UUID da loja B com JWT da loja A → 403.
- Desativar vínculo da loja A e repetir requisição → acesso negado.
- `/api/vendas`, `/api/caixas`, cancelamentos e estornos → 403.
- Confirmar que `/estabelecimentos.html` funciona no próprio serviço
  HTTPS de homologação. Nenhum login real deve ser realizado no Netlify mock.

Esses cenários exigem **duas identidades reais descartáveis** no Supabase Auth
e vínculos administrativos no novo banco, criados por procedimento seguro
posterior. Não usar contas pessoais ou funcionários reais no piloto.

## Rollback e desativação

Desligar o serviço Render staging e invalidar as sessões de teste. Excluir
o banco temporário apenas após confirmação, já que isso destrói seu conteúdo.
Não aplicar `drop`/truncates em projetos Supabase ou bancos de produção.
