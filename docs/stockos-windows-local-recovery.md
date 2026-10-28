# Abrir projeto no Windows sem banco antigo e sem merge

A PR #3 ainda deve ficar em **draft**. Para abrir o aplicativo legado local,
**não** precisa criar tabelas no Supabase nem migrar o banco antigo.

> Importante: este procedimento inicia a **interface legada local**
> (`app.Main` + `legacy-dev`). Não é o login SaaS do Supabase Auth
> e não libera a aplicação para clientes.

## Pré-requisitos

- Docker Desktop iniciado e Docker Compose funcionando
- **JDK 25 ou superior** e Maven instalados no Windows; o Java usado pelo Maven importa mais que `java -version`.
  Confira com `mvn -version` (a linha `Java version:` deve mostrar **25** ou superior).
- Clonar o repositório; nenhuma credencial Supabase necessária

### Se aparecer `release version 25 not supported`

O Docker e o banco local podem estar saudaveis, mas a compilacao para
Java 25 falhara quando o **Maven estiver usando JDK anterior a 25**.
Nao remova containers/volumes e nao faca merge por conta desse erro.

No PowerShell execute:

```powershell
mvn -version
winget install --id EclipseAdoptium.Temurin.25.JDK -e
```

Feche e abra novamente o PowerShell (e a IDE) e rode `mvn -version`.
Se o Maven continuar com uma versao antiga, `JAVA_HOME` provavelmente
ainda aponta para o JDK antigo. Escolha a **pasta exata** do JDK 25
encontrada em `C:\Program Files\Eclipse Adoptium` e atualize sua
variavel `JAVA_HOME`, sem adicionar `\\bin` ao final da pasta.

Para a sessao atual do PowerShell, exemplo (ajuste o caminho pelo que
aparecer em sua maquina):

```powershell
Get-ChildItem 'C:\Program Files\Eclipse Adoptium' -Directory
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-25.x.x-hotspot'
$env:Path = "$env:JAVA_HOME\\bin;$env:Path"
mvn -version
```

O script agora realiza essa verificacao de JDK **antes de iniciar
containers** e pode ser testado sem subir o banco:
`powershell -NoProfile -File .\scripts\dev\start-local-legacy.ps1 -CheckJavaOnly`.

Abra o PowerShell na pasta do projeto:

```powershell
git status
git fetch origin
git switch feat/stockos-saas-auth-foundation-20261026
git pull --ff-only origin feat/stockos-saas-auth-foundation-20261026
.\scripts\dev\start-local-legacy.ps1
```

Se a branch ainda não existir localmente:

```powershell
git switch --track origin/feat/stockos-saas-auth-foundation-20261026
```

**Não rode `git reset --hard`**, e não faça `git switch` se houver
arquivos locais não salvos ou alterações importantes. Primeiro resolva o
`git status`.

O script cria:
- PostgreSQL 17 **somente no Docker local**, em `127.0.0.1:55432`;
- banco novo `stockos_legacy_local`, usuário local `stockos_local`;
- volume Docker exclusivo `stockos-local-legacy`, sem excluir o banco antigo;
- senha gerada aleatoriamente em `.stockos-local.env`, no `.gitignore`;
- credenciais em variáveis de ambiente e `SPRING_APPLICATION_JSON` (não
  expõe senha em parâmetros Maven), sobrescrevendo configurações de banco
  antigas eventualmente carregadas;
- Flyway executa somente `classpath:db/migration`, com baseline desativado;
- aplicação bindada somente em `127.0.0.1:8080`, modo `legacy-dev`
  exclusivamente local.

Acesse: **http://localhost:8080/login**.

### Encerrar e recuperar

- `Ctrl+C` encerra a aplicação Java; o banco fica disponível no Docker.
- Parar Docker (preserva dados):
  `docker compose --project-name stockos-local-legacy -f compose.stockos-local.yml down`
- Nunca rode `down -v` se quiser preservar os dados de demonstração.
- Se a porta `55432` estiver ocupada, **não pare outro banco**.
  Ajustaremos a porta local.
- Se a porta `8080` estiver ocupada, pare o processo Java antigo,
  sem tocar no PostgreSQL.

Se o PowerShell bloquear scripts por política, é possível invocar apenas
essa execução com:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\dev\start-local-legacy.ps1
```

Esse procedimento não depende de Supabase Staging. Não copie dados pessoais
nem senhas de produção para o ambiente local.

### Não confundir: StockOS SaaS novo

O projeto SaaS agora inicia com `saas.StockOsStagingApplication` e
`supabase-jwt`, sem tabelas legadas; **ele ainda exige** o fluxo de Auth,
a configuração e as roles JDBC específicas e está em desenvolvimento.
O script acima é o caminho mais rápido para recuperar **o acesso local
ao sistema antigo**, sem fazer merge precipitado nem conectar o Supabase
por uma conta privilegiada.

