# Sistema Gerenciamento

Sistema web para operacao de estoque, compras, fornecedores, notas fiscais, cotacoes, caixas e gestao de cardapio para uma operacao de pizzaria/restaurante. A aplicacao usa Spring Boot no backend, PostgreSQL para persistencia e paginas HTML/CSS/JS servidas pelo proprio projeto.

## Stack

- Java 17
- Spring Boot 3.3
- Spring Web
- Spring Data JPA
- Thymeleaf
- PostgreSQL
- Maven
- HTML, CSS e JavaScript

## Funcionalidades

- Cadastro, consulta e movimentacao de produtos em estoque
- Gestao de fornecedores e confirmacao de notas fiscais com entrada de estoque
- Registro e comparacao de cotacoes mensais
- Operacao de caixas com abertura, venda, suprimento, sangria e fechamento
- Cardapio unificado para PDV e administracao
- Gestao de funcionarios, perfis e logs de auditoria
- Seed automatico de usuarios padrao, categorias e itens de cardapio

## Requisitos

- Java 17 ou superior
- Maven 3.9 ou superior
- PostgreSQL em execucao

## Configuracao

O projeto usa estas configuracoes padrao em `src/main/resources/application.properties`:

- `server.port=8080`
- `spring.datasource.url=jdbc:postgresql://localhost:5432/sistema_gerenciamento`
- `spring.datasource.username=postgres`

Defina as credenciais por variaveis de ambiente no PowerShell:

```powershell
$env:DB_URL="jdbc:postgresql://localhost:5432/sistema_gerenciamento"
$env:DB_USERNAME="postgres"
$env:DB_PASSWORD="sua_senha"
```

Se preferir, crie um arquivo local `src/main/resources/application-local.properties` com overrides que nao devem ser versionados, por exemplo:

```properties
DB_PASSWORD=sua_senha
```

Antes de subir a aplicacao, crie o banco:

```sql
CREATE DATABASE sistema_gerenciamento;
```

## Execucao

Suba a aplicacao com Maven:

```powershell
mvn spring-boot:run
```

Depois acesse:

- `http://localhost:8080`
- `http://localhost:8080/login`

## Usuarios iniciais

O login usa `nome`, `sobrenome` e `senha`.

| Nome  | Sobrenome | Perfil            | Senha |
|-------|-----------|-------------------|-------|
| Admin | Superior  | acesso total      | 1234  |
| Maria | Gerente   | gerente estoque   | 1234  |
| Joao  | Silva     | estoquista        | 1234  |
| Ana   | Caixa     | operadora de caixa| 1234  |

## Rotas principais

Paginas web:

- `/dashboard`
- `/cardapio`
- `/estoque`
- `/fornecedores`
- `/notas`
- `/caixas`
- `/cotacoes`
- `/funcionarios`
- `/logs`
- `/login`

APIs principais:

- `POST /api/auth/login`
- `/api/produtos`
- `/api/movimentacoes`
- `/api/fornecedores`
- `/api/notas`
- `/api/cotacoes`
- `/api/caixas`
- `/api/cardapio`
- `/api/cardapio/admin`
- `/api/funcionarios`
- `/api/logs`

## Autorizacao da API

Depois do login, a maior parte dos endpoints exige o cabecalho `X-User-RU`.

Exemplo de login:

```powershell
curl -Method Post "http://localhost:8080/api/auth/login" `
  -ContentType "application/json" `
  -Body '{"nome":"Admin","sobrenome":"Superior","senha":"1234"}'
```

Use o `ru` retornado pela API nas chamadas protegidas:

```powershell
curl -Headers @{ "X-User-RU" = "<ru-retornado>" } "http://localhost:8080/api/produtos"
```

## Testes

Executar toda a suite:

```powershell
mvn -q test
```

Os testes cobrem:

- persistencia JPA e schema do PostgreSQL
- estoque, compras e notas fiscais
- caixa e fluxo de vendas
- autorizacao por perfil
- shell frontend e paginas principais
- gestao administrativa do cardapio

## Estrutura do projeto

```text
src/main/java/app
src/main/java/controller
src/main/java/model
src/main/java/repository
src/main/java/service
src/main/resources/static
src/test/java
```

## Observacoes

- A classe principal da aplicacao web e `app.Main`.
- Existe uma implementacao legada de console em `app.SistemaGerenciamentoEstoque`.
- Senhas ainda sao armazenadas em texto simples e a autorizacao HTTP usa `X-User-RU`; isso serve para desenvolvimento e estudo, nao para producao.
