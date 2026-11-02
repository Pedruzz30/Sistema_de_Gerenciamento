# StockOS — Docker local, testes isolados e caminho para Supabase

## Escopo desta etapa

Esta etapa implementa um ambiente **local e descartável** para o PostgreSQL e
um container opcional do backend Java, sem mexer no Supabase Cloud nem copiar
dados do PostgreSQL instalado no Windows. O frontend Netlify/Vite existente
continua sendo um **preview com dados mock**, não uma interface conectada à API Java.

Esta etapa **não** migra autenticação, não habilita multi-tenant e não coloca
o StockOS em produção. A API atual ainda aceita `X-User-RU` e possui senhas
legadas em texto simples. Mesmo com o backend em Docker, isso **não é seguro
para exposição pública**.

## 1. Desenvolvimento local no Windows

Pré-requisitos: Docker Desktop com Docker Compose v2, Git e Maven/Java 17+.

No PowerShell, a partir da raiz do repositório:

```powershell
Copy-Item .env.example .env
notepad .env
docker compose up -d --wait db
docker compose ps
```

Troque `STOCKOS_DB_PASSWORD` por uma senha de **desenvolvimento**. Não use
credenciais do Supabase. O Compose publica o PostgreSQL **somente** em
`127.0.0.1:5433` (ou na porta definida em `.env`).

A primeira inicialização cria dois bancos independentes:
- `stockos_dev`: para executar a aplicação e testar a API manualmente.
- `stockos_test`: para os testes automatizados; os testes podem apagar dados
  desse banco. Não use dados de verdade ali.

A inicialização SQL só executa ao criar um volume novo. Se o volume já existir,
o banco `stockos_test` precisará ser criado manualmente; **não** faça
`docker compose down -v` sem querer apagar todos os dados dos volumes.

## 2. Iniciar o backend Java opcionalmente

```powershell
docker compose --profile app up -d --build app
docker compose logs -f app
```

A API escuta em `http://127.0.0.1:8080`. O serviço `app` usa o hostname
interno `db:5432`, configura `FLYWAY_BASELINE_ON_MIGRATE=false` e
**não** liga o catálogo de exemplo. O Hibernate valida o schema e o Flyway
aplica as migrations na primeira execução.

Atenção: apenas para desenvolvimento local. A API tem autenticação
legada e suas rotas **não** devem ser publicadas na Internet.

Para continuar executando Java fora do container, direcione o Maven à porta
local 5433 e ao banco `stockos_dev`, com as credenciais da sua `.env`.
Nunca use `stockos_test` como banco da aplicação.

## 3. Executar a suíte completa sem atingir o banco da aplicação

```powershell
.\scripts\test-docker.ps1
```

O script inicia o PostgreSQL Docker, descobre sua porta e sua senha local,
configura `STOCKOS_TEST_DB_*` **somente durante a execução**, executa
`mvn test` e restaura o ambiente anterior. Ele ignora `DB_URL`,
`DB_USERNAME` e `DB_PASSWORD` que você possa ter usado em outros projetos.

O `src/test/resources/application.properties` usa apenas
`STOCKOS_TEST_DB_URL`, `STOCKOS_TEST_DB_USERNAME` e
`STOCKOS_TEST_DB_PASSWORD`, e o teste de migrations recusa hosts não locais.
Assim os testes não acessam o Supabase acidentalmente.

Na CI, o GitHub Actions cria seu próprio PostgreSQL efêmero e executa a suíte.
Nenhuma credencial de produção é usada.

## 4. Supabase Cloud — **próxima etapa, não conectada ainda**

A arquitetura-alvo:

```text
Netlify/Vite SPA
  | login: Supabase Auth (publishable key + JWT do usuário)
  | ações de negócio: Authorization Bearer <JWT>
  v
StockOS Spring Boot (Docker; servidor persistente)
  | verifica JWT, identidade/tenant e permissões
  | JPA + @Transactional + Flyway controlado
  v
Projeto Supabase EXCLUSIVO do StockOS (PostgreSQL)
```

1. Criar projeto **separado** do Space Underground.
2. Escolher **um único fluxo de escrita** para estoque, caixa e vendas:
   frontend -> Spring Boot -> PostgreSQL. Não colocar CRUD concorrente de
   estoque/venda diretamente no cliente Supabase.
3. Integrar o Supabase Auth e verificar tokens assinados no backend; mapear
   `auth.users.id` para usuário/estabelecimento autorizado. **Nunca**
   aceitar `X-User-RU` como identidade enviada pelo navegador nesse modo.
4. Isolar dados de cada empresa/filial com tenant obrigatório em consultas,
   transações e permissões. Avaliar RLS nas tabelas expostas pelo Supabase.
   A conexão JDBC do backend não equivale automaticamente ao JWT do usuário:
   autorizações precisam ser verificadas no backend também.
5. Conferir divergências do histórico Flyway local antes de criar/aplicar
   migrations no projeto remoto. Nunca fazer baseline automático em banco
   com dados desconhecidos.
6. Só após esses itens, configurar JDBC remoto no ambiente de hospedagem.
   Não commitar senhas ou `service_role` no repositório/frontend.

Conexão JDBC Supabase: usar **direct connection** em ambiente com IPv6
ou **session pooler** para backend persistente em rede IPv4. Use TLS.
Evitar transaction pooler para JPA sem avaliação explícita de prepared
statements. Consulte:
https://supabase.com/docs/guides/database/connecting-to-postgres

**Não existe variável `SUPABASE_DB_*` ativa nem deploy remoto nesta etapa.**
Não remova o PostgreSQL antigo do Windows até validar a rotina no Docker e
conferir seus backups.

## 5. Compatibilidade de schema e segurança de dados

O PostgreSQL instalado no Windows pode ter Flyway em `20261026.01`,
enquanto a `main` do StockOS contém migrations até `20260715.01`.
Essa diferença requer auditoria **antes** de qualquer importação para Cloud.
Esta alteração não executa `repair`, `clean`, `baseline` ou migrations
contra o banco Windows/Supabase.

No Docker novo, o banco começa vazio e executa o histórico versionado.
O volume `stockos_postgres_data` preserva dados de desenvolvimento entre
reinicializações; o banco de testes continua sendo descartável por finalidade.
