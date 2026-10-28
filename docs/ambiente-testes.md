# Banco de dados exclusivo para testes

Testes nao podem utilizar o banco principal ou bancos dos clientes.

## Configuracao no PowerShell

Instale Java 25, Maven e Docker Desktop. Na raiz do projeto:

```powershell
$env:TEST_DB_PASSWORD = "senha_exclusiva_para_testes"
docker compose -f compose.test.yml up -d
mvn -q -Dtest=TestDatabaseGuardTest test
mvn test
```

Para parar: `docker compose -f compose.test.yml down`. Para descartar dados **somente do teste**: `docker compose -f compose.test.yml down -v`.

## Variaveis do ambiente de testes

- `TEST_DB_URL`: `jdbc:postgresql://localhost:55432/sistema_gerenciamento_test`.
- `TEST_DB_USERNAME`: `stockos_test`.
- `TEST_DB_PASSWORD`: senha exclusiva do container de testes.

`DB_URL`, `DB_USERNAME` e `DB_PASSWORD` pertencem a aplicacao normal e nao sao utilizados pela suite. Opcionalmente, configure as variaveis `TEST_DB_*` em `src/test/resources/application-test-local.properties`, arquivo que ja e ignorado pelo Git.

A protecao bloqueia URLs fora de localhost:55432, nomes de bancos nao autorizados e CREATE/DROP DATABASE fora da lista temporaria `sg_f46_...`. O Flyway valida todas as conexoes JDBC abertas diretamente pelo teste.

**Limite:** toda conexao JDBC direta criada em testes futuros devera passar pelo metodo `TestDatabaseGuard.assertSafeJdbcUrl`. O banco de testes e fisicamente isolado para que suas credenciais nao alcancem dados reais.
