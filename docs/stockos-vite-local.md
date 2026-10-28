# StockOS — Vite para desenvolver frontend local

A arquitetura continua **Spring Boot + PostgreSQL**. Vite substitui
o Live Server no desenvolvimento do frontend, mas **não substitui o Java**.

## Pré-requisitos
- Node.js **22.12+** (ou 20.19+), npm instalado;
- Java JDK 25 e Maven para rodar o backend;
- Docker Desktop com PostgreSQL local iniciado.

## Rodar no Windows com duas janelas do PowerShell

Antes de começar, `git pull --ff-only origin
feat/stockos-saas-auth-foundation-20261026` na branch correta.

**Terminal A — API Java + Docker** (não fechar esta janela):

```powershell
.\scripts\dev\start-local-legacy.ps1
```

Verifique que o log mostra `Tomcat started on port 8080`. O
script usa PostgreSQL 17 isolado em `127.0.0.1:55432`.

**Terminal B — Vite / frontend com live reload:**

```powershell
node --version
npm --version
npm install
npm run dev
```

O Vite abre em **http://127.0.0.1:5173/login.html**.
As páginas também funcionam por URL limpa: `/login`, `/dashboard`, etc.
Se quiser abrir outra interface, navegue para
`http://127.0.0.1:5173/saas-comandas.html` (necessita Supabase Auth real
e backend SaaS, **ainda não pronto para operação**).

**O que acontece por trás:**

- Vite serve HTML/CSS/JS da pasta original
  `src/main/resources/static`. Edite esses arquivos normalmente
  no VS Code; CSS/HTML/JS são atualizados no browser.
- Vite repassa apenas `/api/**` à API Spring Boot em
  `127.0.0.1:8080`. Portanto `fetch('/api/auth/login')` e demais chamadas
  continuam na mesma origem do navegador, sem liberar CORS globalmente.
- API Java continua protegida pelas validações da aplicação. Não coloque
  chaves `service_role`, senhas de banco ou JWTs fixos em arquivos frontend.
- Vite escuta somente em `127.0.0.1`. Não usar `--host 0.0.0.0`.
- Este fluxo é exclusivamente de desenvolvimento local; não há alteração
  nas migrations, no deploy do Spring, Supabase real ou `main`.

## Troubleshooting

**Frontend abre, mas login não funciona:** o navegador poder servir
HTML mesmo que Java esteja desligado. No Terminal A procure
`Tomcat started on port 8080`, ou teste:

```powershell
Invoke-WebRequest http://127.0.0.1:8080/login.html -UseBasicParsing |
  Select-Object StatusCode
```

Se o backend não estiver rodando, `/api` exibirá erro de proxy (502 ou
sem resposta) no DevTools. Se as credenciais do formulário não forem
aceitas, isso é um problema de autenticação, **não do Vite**.

**Página não encontrada:** confira exatamente
`http://127.0.0.1:5173/login.html`; `/login**` é uma URL diferente.

**Porta ocupada:** Vite usa porta 5173 e Spring usa porta 8080.
Verifique se outro processo já está usando essas portas antes de tentar
parar algo. Não derrube containers da Space Underground.

**Maven continua no Java 17 após instalar o 25:** o launcher agora detecta
a instalação do Temurin 25 e prioriza esse JDK **somente no processo
PowerShell atual**. Confirme `mvn -version`.

**Live Server do VS Code:** serve HTML/CSS/JS, mas não faz proxy `/api`
por padrão. Não abra a interface pelo Live Server em 5500 se pretende
usar login/estoque/caixa com a API. Abra pelo Vite em 5173.

Documentação oficial: https://vite.dev/guide/
e https://vite.dev/config/server-options#server-proxy.
