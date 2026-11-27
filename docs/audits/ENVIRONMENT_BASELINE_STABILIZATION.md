# StockOS — Estabilização do ambiente e da baseline

**Data** 26/11/2026 · **Fase 1.1** · **Base** `c6101fd` e alterações locais já presentes. A Fase 2 exige aprovação humana.

## Java e causa da divergência

A versão do StockOS nesta árvore é **Java 25**. O POM local já declarava 25 antes desta fase, os seis workflows que configuram Java usam Temurin 25, o Dockerfile seleciona JDK 25 para build e JRE 25 para execução, e o instalador Windows aponta para Temurin JRE 25.0.5+8. O POM em HEAD ainda declarava 17. A configuração Java 25 local foi mantida, sem novo upgrade, downgrade ou alteração em infraestrutura publicada.

`JAVA_HOME`, o primeiro `java.exe` do PATH, `java -version` e `mvn -version` concordaram em Temurin 25.0.5+8. Há uma entrada Oracle posterior no PATH. Maven 3.9.14 veio do Chocolatey. Não há `mvnw`, `mvnw.cmd` ou wrapper em `.mvn`. O Maven Compiler Plugin 3.14.1 herdado do parent invocou javac com `release 25` para fontes principais e de teste. O acesso direto pelo ambiente restrito anteriormente falhava com cache Maven e ZipFS. As execuções válidas usaram o cache local explicitamente selecionado, em modo offline, fora daquela restrição.

O processo do Java Language Server tem runtime próprio. A extensão Red Hat Java 1.58.0 pode usar seu JRE embarcado ou um JDK 25+ definido em `java.jdt.ls.java.home`. Esse ajuste governa o processo do servidor. Já o POM e o classpath Maven importado por m2e governam o nível do projeto. Não havia override de JDK relevante nas configurações de usuário verificadas. O runtime exato da janela aberta não foi inspecionado por causa da restrição do sistema ao consultar o processo. O experimento independente de JDT LS executou em Temurin 25.0.5.

O workspace original persistia `source`, `compliance`, `targetPlatform` e o contêiner de JRE em Java 17, com análise de nulabilidade ativa. A reimportação dependia de aceitação manual porque a configuração local era `interactive`; tampouco era distribuída pelo Git, pois `.vscode/` estava ignorada. O modelo Maven antigo explica a divergência observada. O mesmo projeto foi importado em um workspace JDT limpo e passou a usar `JavaSE-25` e `source=25`, com `nullanalysis=enabled`.

Spring Boot 3.5.16 aceita Java 25 e Maven 3.6.3+. Compilação e teste locais exercitaram o stack Boot, Hibernate, driver PostgreSQL, Flyway 12.11.0, JUnit/Surefire e Byte Buddy com Java 25. O Maven 3.10.2 e JDK 25 do Dockerfile correspondem a uma tag oficial disponível. O daemon Docker não estava acessível, portanto o build da imagem, a execução no JRE da imagem, o instalador e o ambiente implantado permanecem sem verificação operacional desta fase. [Requisitos do Spring Boot 3.5](https://docs.spring.io/spring-boot/3.5/system-requirements.html) · [Imagem oficial Maven](https://hub.docker.com/_/maven/tags?name=-eclipse-temurin&page=1)

## Configuração alterada

| Arquivo | Alteração e justificativa |
| --- | --- |
| `.vscode/settings.json` | Atualização automática do modelo de build Java. A análise de nulabilidade continua em `automatic`. Nenhum caminho local de JDK foi adicionado. |
| `.gitignore` | Permite compartilhar apenas `.vscode/settings.json`; demais arquivos da pasta permanecem ignorados. |
| `pom.xml` | Perfil Maven opt-in `backend-baseline` ativa `showWarnings` e `-Xlint:all` em compilação principal e de teste. O plugin de JAR exclui o arquivo de configuração privada `application-local.properties` do artefato. O import opcional e o uso local a partir de `target/classes` continuam disponíveis para `spring-boot:run`. |
| `docs/infra-docker-supabase.md` | Atualiza o pré-requisito antigo de Java 17 para JDK 25 na execução fora do container. |
| `docs/audits/ENVIRONMENT_BASELINE_STABILIZATION.md` | Registra ambiente, evidências, procedimentos e parecer. |

## PostgreSQL e os 80 erros da Fase 1

- **77 casos** de integração dependiam do datasource de `src/test/resources/application.properties`; o processo PostgreSQL esperado em `127.0.0.1:5433` estava indisponível. A inspeção do log anterior confirmou `Connection refused` e rejeições subsequentes de recarga dos contextos. O serviço existente em 5432 ficou fora da auditoria.
- **3 casos** em `Fase46FlywayMigrationIntegrationTest` exigiam `STOCKOS_TEST_DB_PASSWORD` não vazia antes de conectar. Esse requisito é independente da disponibilidade TCP. A classe conecta à instância local e cria/remove bancos próprios temporários para validar banco vazio, baseline compatível e baseline incompatível.
- A suíte não usa Testcontainers. A configuração de teste é carregada pelo classpath, sem depender de um profile Spring ativo e sem importar `application-local.properties`. Ela usa as variáveis `STOCKOS_TEST_DB_*`, Flyway e Hibernate em `validate`, com o modo legado de identidade ativado para classes antigas. Classes de autenticação podem sobrescrevê-lo. Variáveis Spring de ambiente têm precedência sobre as propriedades de teste.
- A primeira tentativa desta fase demonstrou uma interferência adicional de configuração. Fixar `SPRING_FLYWAY_URL` globalmente no banco de teste principal fez Flyway migrar o banco errado nos cenários Fase46. O Hibernate ficou apontado para os bancos temporários e acusou tabelas ausentes em dois casos. A execução de referência removeu os overrides globais de datasource/Flyway, deixando cada teste controlar sua URL.
- O Compose local e o CI usam PostgreSQL 17. O daemon Docker estava indisponível. A execução local usou PostgreSQL **18.3** em cluster novo, porta loopback exclusiva, autenticação SCRAM, usuário novo e senha aleatória específica da instância. `select version(), current_database(), inet_server_port()` confirmou o destino. Somente os bancos descartáveis receberam migrations e dados sintéticos. O serviço existente, o banco de produção e dados de clientes ficaram fora da execução.

## Diagnósticos antes e depois

A captura nova foi feita com o mesmo JDT LS instalado (1.63.0, extensão 1.58.0), em cópia dos fontes e do POM, sem arquivo de configuração privada. Um cliente LSP de terminal coletou a última notificação `textDocument/publishDiagnostics` de cada URI. A comparação com o snapshot da Fase 1 usa caminho relativo, código e mensagem como multiconjunto. A janela do VS Code aberta pelo usuário mantém seu cache até reimportação ou restart.

| Diagnósticos | Fase 1, import persistido 17 | Importação limpa Java 25 |
| --- | ---: | ---: |
| Produção | 434 | 434 |
| Testes | 86 | 91 |
| Total de avisos | 520 | 525 |
| Tarefas e erros | 12 e 0 | 12 e 0 |

**Zero avisos eliminados**. A importação de controle em outro workspace limpo reproduziu 520 avisos e 12 tarefas quando a única mudança na cópia do POM foi trocar o nível do projeto para 17. Com o mesmo servidor, runtime e regras de nulabilidade, o nível 25 acrescentou três variáveis `ignored` em `Fase46FlywayMigrationIntegrationTest` e duas chamadas deprecated a `URL(String)` em `CelularNovoTest`.

Os 525 avisos se concentram em 258 descritores funcionais sem prova de nulabilidade, 71 conversões sem prova, 116 variáveis potencialmente nulas, 42 retornos potencialmente nulos em `getBody()`, 14 contratos de override, dois autounboxings, 20 símbolos sem uso e duas depreciações. Os 12 itens informativos vêm de palavras-chave em máscaras de CNPJ. A auditoria anterior demonstra falsos positivos de fluxo nos construtores com guard e no ramo de embalagem. O autounboxing em JDBC, o acesso a corpos de resposta e os contratos incompatíveis ainda exigem verificação. A classificação individual por grupo e arquivo da Fase 1 continua aplicável aos 520 diagnósticos preservados.

### Revisão dos 16 avisos do javac

| Local | Avaliação individual |
| --- | --- |
| `VendaItem.java:177` | Construtor chama `aplicarSnapshotComercial` virtual. Override poderia observar estado incompleto. Risco potencial. |
| `FichaTecnicaIngrediente.java:90` | Construtor chama `atualizar` virtual para inicializar vínculo e quantidades. Risco potencial em subclasses. |
| `FichaTecnicaVersao.java:109` | Construtor chama `atualizarRascunho` virtual. Um override poderia observar versão parcialmente inicializada. Risco potencial. |
| `OpcaoModificadorComponente.java:88` | Construtor chama `atualizar` virtual para vincular opção e produtos. Risco potencial. |
| `VariacaoGrupoModificador.java:80` | Construtor chama `atualizar` virtual para configurar associação e sobrescritas. Risco potencial. |
| `OpcaoModificador.java:96` | Construtor chama `atualizar` virtual para popular grupo e dados comerciais. Risco potencial. |
| `GrupoModificador.java:92` | Construtor chama `atualizar` virtual para limites e política de seleção. Risco potencial. |
| `ConfiguracaoComposicaoMultipla.java:96` | Construtor chama a atualização pública virtual da composição. Risco potencial. |
| `ErroNegocioException.java:7` | `serialVersionUID` ausente. Tarefa de compatibilidade caso haja fluxo real de serialização Java. |
| `ErroNegocioException.java:10` | `Map<String,Object>` não garante serialização de valores. Risco condicionado ao transporte por serialização Java. |
| `ConflitoIdempotenciaException.java:3` | `serialVersionUID` ausente. Tarefa de manutenção de contrato serializado. |
| `Fase46FlywayMigrationIntegrationTest.java:31` | Contexto de banco vazio aberto para verificar startup e fechado pelo `try`. Aviso verdadeiro sobre variável sem uso. O efeito do teste é intencional. |
| `Fase46FlywayMigrationIntegrationTest.java:66` | Mesmo padrão para baseline compatível. Preservar startup e fechamento. |
| `Fase46FlywayMigrationIntegrationTest.java:91` | Mesmo padrão dentro da asserção de inicialização rejeitada. Preservar a asserção. |
| `CelularNovoTest.java:122` | Construtor deprecated `URL(String)` na primeira chamada de teste. Tarefa de migração para URI com semântica equivalente. |
| `CelularNovoTest.java:138` | Segunda chamada deprecated do mesmo construtor. Tarefa de migração, com verificação individual do endpoint. |

Nenhum desses 16 avisos por si só demonstrou defeito em execução. Para os oito alertas de construção, investigar subclasses e proxies antes de alterar a capacidade de sobrescrita. Os avisos de execução de Byte Buddy e de APIs do JVM são contabilizados à parte dos diagnósticos Java.

## Reprodução e validação

Em PowerShell, confirme `java -version` e `mvn -version`. Use o JDK 25 e, para o conjunto com banco, disponibilize uma instância nova e dedicada na mesma máquina. A opção mais próxima do CI é PostgreSQL 17 via Compose em projeto e porta próprios, com volume novo, senha descartável e sem carregar configurações de produção. O script `scripts/test-docker.ps1` usa `stockos_test` e a senha do container quando executado no ambiente local preparado. Em automação independente, defina explicitamente `STOCKOS_TEST_DB_URL`, `STOCKOS_TEST_DB_USERNAME` e `STOCKOS_TEST_DB_PASSWORD`, limpe overrides externos de Spring config/datasource/Flyway e confirme host, porta e banco antes de chamar Maven. Use um usuário da instância descartável com permissão para criar os bancos Fase46.

```powershell
mvn -B -ntp -Pbackend-baseline '-Dtest=app.**,controller.**,repository.**,service.**,*MigrationSql*Test' clean test
mvn -B -ntp -Pbackend-baseline '-Dtest=*IntegrationTest' test
mvn -B -ntp -Pbackend-baseline test
mvn -B -ntp -Pbackend-baseline -DskipTests package
```

O último comando separa empacotamento da execução já medida da suíte completa. Para rodar offline, acrescente `-o` e `-Dmaven.repo.local=<cache já populado>`. Na auditoria usou-se `-Dstyle.color=never` e um POM temporário, contendo somente uma mudança adicional em `build.directory` para preservar artefatos anteriores. Valide o JAR com `jar tf` e confira ausência de `BOOT-INF/classes/application-local.properties`. Na janela ativa do VS Code, execute **Java: Clean Java Language Server Workspace**, reinicie quando solicitado, abra o projeto na raiz, aguarde a importação e use **Java: Configure Java Runtime** para conferir Java 25. Conserve a análise de nulabilidade e capture Problems ou o trace LSP conforme a Fase 1.

Para reproduzir sem Docker, use os binários do PostgreSQL local com um diretório de dados novo por execução. Inicialize com `initdb -D <diretório novo> -U <usuário de teste> -A scram-sha-256 --pwfile=<arquivo temporário> --encoding=UTF8 --locale=C`, apague o arquivo temporário da senha após a inicialização e suba o cluster com `pg_ctl -D <diretório novo> -l <log> -o "-h 127.0.0.1 -p <porta livre>" -w start`. Crie apenas `stockos_test` nessa instância e aponte as três variáveis de teste para ela. Execute só uma suíte com banco por cluster. Ao terminar, pare a instância com `pg_ctl -D <mesmo diretório> -m fast -w stop`. Use um cluster separado para repetir ou comparar resultados de integração.

## Resultados medidos

| Execução | Tempo Maven | Resultado |
| --- | ---: | --- |
| Compilação limpa com `-Xlint:all` e seleção sem PostgreSQL | 33,727 s | **BUILD SUCCESS**. 337 fontes principais e 57 de teste compilados em release 25. 16 avisos, os mesmos da Fase 1. 208 testes passaram. |
| Seleção de integração no PostgreSQL 18.3 novo | 1 min 38 s | **BUILD SUCCESS**. 80 testes passaram, sem falhas, erros ou ignorados. Flyway aplicou 18 migrations até `20261112.01` na base nova. Os três cenários Fase46 passaram. |
| Suíte completa reutilizando o banco da execução de integração | 1 min 36 s | **BUILD FAILURE**. 288 testes, cinco falhas de asserção, zero erros e zero ignorados. As cinco falhas são de `PdvBalcaoApiIntegrationTest`. |
| Empacotamento separado | 6,061 s | **BUILD SUCCESS**. O JAR executável contém `Main.class`, `application.properties` e `application-docker.properties`. `jar tf` confirmou ausência de `application-local.properties`. |
| `mvn -B -ntp -Pbackend-baseline clean verify` em outro PostgreSQL 18.3 novo | 1 min 40 s | **BUILD SUCCESS**. 288 testes passaram, com zero falhas, erros ou ignorados, e o Spring Boot reempacotou o JAR. A inspeção do JAR confirmou a mesma exclusão do arquivo privado. |

Os cinco casos que falharam no banco reutilizado foram `encerrarCaixaComComandaAbertaPedeConfirmacao`, `importarXmlCriaProdutoVendeEReconheceNaSegundaNota`, `nomeDoClienteFicaJuntoDaComanda`, `pedidoDoCelularAbreAComandaESomaNaMesmaLinha` e `transferirParteDeUmItemEJuntarComandas`. Houve divergências de valores, estado de comanda e unidade na importação. A repetição usou o mesmo banco depois de os 80 casos de integração passarem. Isso demonstra que o estado deixado por uma execução afeta a próxima. Nenhuma assertion ou rotina de limpeza do aplicativo foi alterada. A reprodução exige instância nova para cada suíte com banco.

`Wave4ApiAuthorizationIntegrationTest` aprovou 2 casos e `LoginSeguroApiIntegrationTest` aprovou 7. A busca em `src/test` e workflows não encontrou testes dedicados de segregação entre tenants ou RLS nesta árvore. Os casos de autorização existentes verificam permissões e identidade no escopo de instalação.

Os logs Maven e os relatórios Surefire ficam sob `target/phase11-final-*` e `target/phase11-clean-full-*`. As capturas do JDT estão em `target/phase11-jdt/diagnostics.json` e `target/phase11-jdt17/diagnostics.json`. Esses arquivos temporários ficam fora do controle de versão. Os clusters de auditoria foram parados ao final.

## Parecer técnico

**Recomendo aprovar a Fase 2 para correções incrementais do backend, tomando a execução de 288 testes em banco descartável como referência.** Java 25 foi confirmado no Maven e em importação limpa do JDT. A suíte completa e o empacotamento passaram. Os 80 erros anteriores foram resolvidos pela preparação do ambiente, e os alertas restantes têm baseline de comparação.

Use uma instância PostgreSQL nova por execução e repita o `clean verify` no CI com PostgreSQL 17 antes de promover alterações. A paridade operacional da imagem, do instalador e do ambiente implantado segue pendente. A evidência de autorização obtida cobre uma instalação. Implantação com banco compartilhado continua dependente de validação multi-tenant específica. A próxima fase aguarda aprovação humana.
