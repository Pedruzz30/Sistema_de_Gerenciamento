# StockOS — Gestao Operacional e Estoque

Sistema web para operacao de estoque, compras, fornecedores, notas fiscais, cotacoes, caixas e cardapio configuravel para restaurantes, bares, hamburguerias, pizzarias, sushi e outros estabelecimentos. Backend Spring Boot e PostgreSQL, com frontend HTML/CSS/JS e navegacao SPA progressiva.

## Stack

- Java 17
- Spring Boot 3.5.16
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
- Cadastro de categorias e itens por estabelecimento; catalogo demonstrativo generico opcional


## Docker (ambiente local isolado)

A infraestrutura para desenvolvimento local fica em
[docs/infra-docker-supabase.md](docs/infra-docker-supabase.md).

```powershell
Copy-Item .env.example .env
notepad .env
docker compose up -d --wait db
.\scripts\test-docker.ps1
```

O banco da aplicação (`stockos_dev`) e o banco dos testes (`stockos_test`)
ficam em containers Docker, sem usar o PostgreSQL instalado no Windows.
Para iniciar também o backend Java local: `docker compose --profile app up -d --build app`.

**Supabase Cloud e Supabase Auth ainda não estão conectados nesta etapa.**
Não exponha o backend na Internet: falta HTTPS e autorização por tenant. Em rede
local, o login usa sessão em cookie (veja "Login e autorização da API").

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

## Catalogo demonstrativo (somente desenvolvimento)

O exemplo de catalogo foi reduzido a duas categorias (Preparados e Bebidas), um prato preparado sob demanda e dois produtos de venda direta. O seed e desligado por padrao. Para habilitar em um banco de desenvolvimento isolado:

```powershell
$env:STOCKOS_SEED_DEMO_CATALOG_ENABLED="true"
```

Nao ative esse seed em bancos de clientes. Nao ha limpeza automatica de dados antigos. A definicao de produtos controlados por estoque nao depende do nome: pizzas congeladas podem ser itens de estoque e pizzas preparadas podem ser itens de cardapio sob demanda.

## Usuarios iniciais

O login usa `nome`, `sobrenome` e `senha`.

| Nome  | Sobrenome | Perfil            | Senha |
|-------|-----------|-------------------|-------|
| Admin | Superior  | acesso total      | 1234  |
| Maria | Gerente   | gerente estoque   | 1234  |
| Joao  | Silva     | estoquista        | 1234  |
| Ana   | Caixa     | operadora de caixa| 1234  |

Essas senhas são provisórias: viram hash ao subir o sistema e precisam ser trocadas no primeiro login.

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

## Login e autorização da API

`POST /api/auth/login` cria uma sessão no servidor e devolve o cookie `STOCKOS_SESSAO`
(`HttpOnly`, `SameSite=Strict`). Todas as rotas de `/api/*`, menos o login e
`/api/auth/config`, exigem essa sessão. O cabeçalho `X-User-RU` enviado pelo cliente é descartado.

```bash
curl -c sessao.txt -H "Content-Type: application/json" -d '{"nome":"Admin","sobrenome":"Superior","senha":"sua-senha"}' http://localhost:8080/api/auth/login
```

```bash
curl -b sessao.txt http://localhost:8080/api/caixas
```

- **Senhas e PINs:** ficam como hash BCrypt. Senhas antigas são convertidas ao subir o sistema e exigem troca.
- **Tentativas:** 5 erros de senha ou PIN bloqueiam aquela pessoa por 5 minutos.
- **Troca de operador:** F12 no PDV, com o PIN de quem vai assumir. Até um novo login com senha, essa sessão só alcança a área de caixa.

| Propriedade | Padrão | Para que serve |
|---|---|---|
| `stockos.auth.sessao-horas` | `14` | Duração da sessão |
| `stockos.auth.cookie-seguro` | `false` | Ligue quando houver HTTPS |
| `stockos.auth.aceitar-header-legado` | `false` | Só para os testes automatizados. **Nunca ligue em uma loja:** qualquer um se passaria por outro usuário |

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
- A limpeza de catalogo nao implementa multitenancy, isolamento por estabelecimento ou onboarding; esses pontos continuam pendentes antes da comercializacao.
