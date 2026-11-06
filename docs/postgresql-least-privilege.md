# StockOS — segregação das credenciais PostgreSQL (PR #33, somente staging)

O backend utiliza duas identidades distintas:
- **Migrador:** executa Flyway/DDL em um processo ou inicialização controlada.
- **Aplicação:** opera somente dados permitidos, sem ser dono do schema e sem DDL.

Não reutilize o superusuário Docker `stockos_local` ou `postgres` na API SaaS.
Não execute estes exemplos contra Supabase Cloud ou banco com dados sem plano
de migração, backup, revisão de grants e aprovação do responsável.

## Configuração segura do Spring Boot (sem segredos no Git)

No ambiente protegido do backend:

```text
STOCKOS_AUTH_MODE=supabase
DB_URL=jdbc:postgresql://HOST:5432/DB?sslmode=verify-full
DB_USERNAME=stockos_app
DB_PASSWORD=<secret do papel runtime>
SPRING_FLYWAY_USER=stockos_migrator
SPRING_FLYWAY_PASSWORD=<secret do migrador>
```

Se o Flyway usar outro host ou base, configurar `SPRING_FLYWAY_URL`
explicitamente, apontando **somente** para a base revisada.
As credenciais de produção devem existir exclusivamente no cofre de segredos.
O novo guard impede iniciar o modo Supabase sem usuário de migração
distinto, e recusa conexão runtime que seja superusuária, criadora de
roles/bancos, `BYPASSRLS`, tenha `CREATE` no database/schema corrente ou
participe do papel dono de tabelas de negócio críticas.

**Limitação:** essa verificação é defesa adicional, não prova de least privilege
completo. Não verifica todas as concessões herdadas, funções `SECURITY DEFINER`,
privileges via `PUBLIC`, `pg_execute_server_program`, `pg_read_server_files`,
conexões a outros bancos, nem permissões concedidas após o startup. Faça auditoria
independente dos papéis e teste SQL de negação no ambiente de homologação.

## Exemplo de concessões (DB vazio e revisado, PostgreSQL dedicado)

A criação dos papéis abaixo é operacional/manual, **fora das migrations Flyway**.
Em Supabase gerenciado, os papéis e políticas exigem validação específica,
pois os owners, permissões de schema e RLS podem ser diferentes.

```sql
-- Administrador executa estas ações só no provisionamento aprovado:
CREATE ROLE stockos_migrator LOGIN PASSWORD '<secret-migrador>' NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
CREATE ROLE stockos_app LOGIN PASSWORD '<secret-runtime>' NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;

-- Execute conectado ao banco StockOS correto.
GRANT CONNECT ON DATABASE stockos_db TO stockos_migrator, stockos_app;
GRANT USAGE, CREATE ON SCHEMA public TO stockos_migrator;
GRANT USAGE ON SCHEMA public TO stockos_app;
REVOKE CREATE ON SCHEMA public FROM stockos_app;

-- Após aplicar as migrations com o papel migrador:
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO stockos_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO stockos_app;
ALTER DEFAULT PRIVILEGES FOR ROLE stockos_migrator IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO stockos_app;
ALTER DEFAULT PRIVILEGES FOR ROLE stockos_migrator IN SCHEMA public
  GRANT USAGE, SELECT ON SEQUENCES TO stockos_app;
```

Esses grants são um **exemplo amplo para homologação**, não a política final
por tabela: endurecer por operação, especialmente em movimentos/estornos
imutáveis, e privilegiar serviços transacionais que auditam a identidade.
Tratar execução de funções, views, triggers e tabelas expostas pela Data API
separadamente. Não tornar `stockos_app` owner, membro do papel de migração
ou detentor de `CREATE`, `TRUNCATE`, `REFERENCES` ou `TRIGGER`.

## Testes de negação obrigatórios (executar como stockos_app)

```sql
SELECT current_user, current_database(), current_schema();
SELECT rolsuper, rolcreatedb, rolcreaterole, rolbypassrls
  FROM pg_roles WHERE rolname = current_user;
SELECT has_database_privilege(current_user, current_database(), 'CREATE');
SELECT has_schema_privilege(current_user, current_schema(), 'CREATE');

-- Execuções abaixo devem falhar. Usar transação com rollback.
BEGIN;
CREATE TABLE public._stockos_probe_privilegio(id integer);
ROLLBACK;
```

Em conexões separadas, confirmar que o runtime não pode alterar objetos,
desabilitar triggers, modificar owners, contornar RLS, criar funções nem
ler dados de outra loja por SQL autorizado. Nunca usar as credenciais de
migração nas requisições normais da API.

## Rollback / implantação

1. Fazer inventário e backup de um banco descartável de homologação.
2. Aplicar migrations Flyway com o papel migrador, depois iniciar a API com
   papel runtime; revisar grants antes do startup.
3. Validar startup fail-closed, duas lojas, negação cruzada, concorrência,
   lançamento/compensação e revogação de funcionários.
4. Conectar produção somente após auditoria independente; caso o startup
   rejeite privilégios, corrigir grants/roles em homologação — não desligar o guard.
