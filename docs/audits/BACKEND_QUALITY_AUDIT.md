# StockOS — Auditoria de qualidade e confiabilidade do backend

**Data** 26/11/2026 · **Fase 1** · **Base** `c6101fd` com as alterações locais já presentes na abertura da auditoria. O POM local solicita Java 25 e o POM em HEAD solicita Java 17. Os resultados abaixo descrevem a árvore local. A Fase 2 depende de aprovação humana.

## Resumo executivo

- O snapshot persistido do Eclipse JDT contém **520 avisos em 173 arquivos, 12 marcadores de tarefa e zero erros**. Destes, 434 avisos são de produção e 86 de testes. A maioria envolve contratos de nulabilidade e inferência, mas a mesma categoria também contém acessos que exigem inspeção funcional.
- O `javac` 25 compilou 337 fontes de produção e 57 fontes de teste. Com `-Xlint:all`, reportou **16 avisos Java** e concluiu com sucesso. A suíte sem PostgreSQL passou integralmente com **208 testes**.
- A suíte completa descobriu **288 testes, com 208 aprovados, zero falhas de asserção, 80 erros e zero ignorados**. Os 80 erros foram de infraestrutura/configuração do PostgreSQL de teste, incluindo efeitos em cascata após a falha inicial de contexto. As verificações de persistência real, transações, Flyway e autorização via aplicação completa continuam sem validação nesta execução.
- O backend auditado usa uma base operacional por instalação. Não há evidência nesta árvore de segregação SaaS por tenant. Trate o isolamento de instalações como requisito de implantação e bloqueie a promoção para banco compartilhado até comprovar o controle em todas as camadas.

## 1. Arquitetura e configuração

| Área | Estado observado |
| --- | --- |
| Entrada | `app/Main.java` importa controllers e services, entidades de `model` e repositórios Spring Data. `app/AppConfig.java` faz o wiring e registra o filtro de sessão em `/api/*`. Há também uma entrada legada em memória em `app/SistemaGerenciamentoEstoque.java`. |
| Aplicação | 37 arquivos em `controller`, 61 em `service`, 77 em `model`, 155 em `repository` e 7 em `app`. Controllers usam records de request/response. Serviços coordenam validação e transações. Entidades JPA também aplicam invariantes de domínio. |
| Persistência | Interfaces locais → adapters Spring → interfaces JPA. 20 classes `InMemory*` atendem testes e a entrada legada. Existem operações JDBC diretas, incluindo a fila da cozinha. PostgreSQL, Flyway e Hibernate validam o esquema. `open-in-view=false` torna relevante o limite de carregamento de associações para DTOs. |
| Build e dependências | Maven 3.9.14, Temurin 25.0.5, Spring Boot 3.5.16, compiler plugin 3.14.1 e Flyway 12.11.0 sobrescrevendo a versão gerenciada pelo parent. Há starters Web, Thymeleaf, Data JPA e Test, Spring Security Crypto, PostgreSQL e módulo Flyway para PostgreSQL. Não há configuração explícita de verificador de nulabilidade no POM, nem Maven Wrapper identificado. |
| Segurança | Sessão por cookie, `HttpOnly`, `SameSite=Strict`, restrição de PIN e substituição do cabeçalho de identidade pelo filtro. O modo de compatibilidade permite o cabeçalho do chamador quando ativado e vem desligado na configuração padrão do filtro. O parser NFe desativa DOCTYPE e entidades externas. |
| Validação e erros | Validação manual em controllers e domínio, sem uso identificado de Bean Validation. O advice combina códigos de domínio, exceções genéricas e inferência de código por texto. Controllers também podem responder localmente, com envelopes próprios. |
| Integração contínua | Os workflows locais de backend, E2E, instalador e desempenho solicitam Java 25. O workflow de backend usa PostgreSQL separado na porta 5433. A auditoria não executou scan de CVEs nem verificou o ambiente de produção. |

## 2. Baseline de execução

| Verificação | Resultado observado |
| --- | --- |
| Maven padrão, offline | Falha ao resolver o parent com o repositório efetivo padrão. O cache explícito em `%USERPROFILE%/.m2/repository` já continha o artefato. Tentativa online com cache vazio recebeu `Permission denied: getsockopt`. |
| `clean` e compilação restrita | `clean` recebeu `AccessDeniedException` ao remover `target`. Em diretórios novos, o ambiente restrito também negou acesso na resolução de caminho durante fechamento de JAR pelo ZipFS do JDK. As classes parciais dessas tentativas foram descartadas como evidência de sucesso. |
| `test-compile` com `-Xlint:all` | **BUILD SUCCESS**. 11 avisos nos fontes de produção e 5 nos testes. Habilitação explícita de `<compilerArgs><arg>-Xlint:all</arg></compilerArgs>` no POM temporário. |
| Seleção sem PostgreSQL | **BUILD SUCCESS**, 208 testes, 0 falhas, 0 erros, 0 ignorados. Seleção Surefire `app.**,controller.**,repository.**,service.**,*MigrationSql*Test`. Os testes SQL dessa seleção verificam o conteúdo das migrations. O JVM emitiu aviso de carregamento dinâmico do agente Byte Buddy durante os testes. |
| Suíte completa | **BUILD FAILURE**, 288 testes, 0 falhas, 80 erros, 0 ignorados. Aplicação de teste com conexão recusada em `127.0.0.1:5433/stockos_test`. Três casos de `Fase46FlywayMigrationIntegrationTest` exigem senha de teste não vazia e falharam antes da conexão administrativa. Os 77 demais erros ocorreram em testes dependentes do contexto PostgreSQL. |

As execuções válidas usaram um POM temporário na raiz com saída nova sob `target`, espelho local do cache Maven e execução autorizada fora da restrição do filesystem. O POM versionado foi preservado. O serviço PostgreSQL existente em 5432 ficou fora da suíte. Nenhum banco ou migration foi alterado.

### Reprodução

Em checkout com o mesmo conteúdo e JDK 25, execute `mvn -B -ntp -Dstyle.color=never clean test-compile` e a seleção abaixo. Em modo offline, adicione `-o` e aponte `-Dmaven.repo.local` para um cache já populado. Se a remoção de `target` estiver bloqueada, use uma cópia temporária do POM na raiz, alterando apenas `build.directory` para uma pasta nova e passando `-f`. Para reproduzir os 16 avisos, acrescente a configuração explícita do `maven-compiler-plugin` descrita acima, com `showWarnings=true`.

```powershell
mvn -B -ntp '-Dstyle.color=never' '-Dtest=app.**,controller.**,repository.**,service.**,*MigrationSql*Test' test
mvn -B -ntp '-Dstyle.color=never' test
```

Para validar a segunda linha com sucesso, use uma instância descartável dedicada à suíte com `STOCKOS_TEST_DB_URL`, `STOCKOS_TEST_DB_USERNAME` e `STOCKOS_TEST_DB_PASSWORD` explícitos. O teste Fase46 conecta ao banco administrativo da mesma instância para criar e remover bancos temporários. Use a configuração do workflow `.github/workflows/stockos-backend-docker.yml` como referência e confira os totais dos XMLs do Surefire antes de aceitar a baseline.

### Avisos de compilação

| Categoria | Qtd. | Arquivos e avaliação |
| --- | ---: | --- |
| `this-escape` | 8 | `VendaItem:177`, `FichaTecnicaIngrediente:90`, `FichaTecnicaVersao:109`, `OpcaoModificadorComponente:88`, `VariacaoGrupoModificador:80`, `OpcaoModificador:96`, `GrupoModificador:92`, `ConfiguracaoComposicaoMultipla:96`. Baixa a média. Chamadas potencialmente sobrescrevíveis durante construção. Revisar a compatibilidade com proxies e construtores JPA antes de mudar. |
| `serial` | 3 | `ErroNegocioException:7,10` e `ConflitoIdempotenciaException:3`. Baixa. Identificador de versão ausente e campo de tipo não serializável. Confirmar se há contrato real de serialização. |
| `try` | 3 | `Fase46FlywayMigrationIntegrationTest:31,66,91`. Baixa. Recurso intencionalmente aberto e fechado para verificar inicialização. Manter a semântica dessa verificação. |
| `deprecation` | 2 | `CelularNovoTest:122,138`. Baixa. Construtor legado de `URL`. Validar a equivalência da construção de URI/URL em teste. |

## 3. Diagnósticos do VS Code

A extensão instalada é `redhat.java 1.58.0`. A configuração versionada `.vscode/settings.json` usa `java.compile.nullAnalysis.mode=automatic` e reimportação Maven interativa. No cache do projeto importado, `annotation.nullanalysis=enabled`, com `org.springframework.lang.NonNull`, `Nullable` e `NonNullApi`; análise sintática de campos ativa. O JDT aplica os contratos vindos do Spring ao analisar código local com tipos e limites genéricos sem nulabilidade explícita. `javac` e JDT medem coisas diferentes.

A preferência global do workspace JDT indica Java 25, mas o projeto importado persiste `source`, `compliance` e `targetPlatform` em 17, com contêiner `JavaSE-17` no classpath. Há evidência de importação desatualizada em relação ao POM local. Não foi medida a diferença que uma reimportação provocaria nos avisos.

A contagem foi obtida por leitura do arquivo binário `.markers` persistido em 26/11 às 18:18, com o layout versão 3 de `MarkerReader_3` da extensão instalada. Foram contados os registros `org.eclipse.jdt.core.problem` por severidade, separando os `org.eclipse.jdt.core.task`. O snapshot coincide com o painel informado. É um retrato do projeto importado, sem diagnósticos transitórios de buffers, sem consolidação do delta posterior `.markers.snap` e sem diagnósticos de outras extensões. Nenhum arquivo do IDE foi alterado.

| Grupo do JDT | Produção | Testes | Total | Avaliação inicial |
| --- | ---: | ---: | ---: | --- |
| Nulabilidade inferida em descritor funcional (67109822) | 233 | 25 | 258 | Baixa a média. Conversões sem prova em referências de método. 46 ocorrências têm a mesma mensagem para `BiFunction<BigDecimal,...>`. |
| Conversão de nulabilidade sem prova (16778128) | 61 | 10 | 71 | Baixa a média. Exemplos em chamadas Spring, `Duration`, `HttpHeaders` e `HttpStatus`. |
| Variável potencialmente nula (536871364) | 115 | 1 | 116 | Média até rastrear origem. Alta concentração em entidades com guards delegados a `validar(boolean, String)`. |
| Retorno potencialmente nulo (536871831) | 0 | 42 | 42 | Média para qualidade dos testes. Todos sobre `getBody()`; status HTTP bem-sucedido por si só não garante corpo. |
| Contratos de overrides (67109781, 67109778) | 14 | 0 | 14 | Média. 6 parâmetros em `SessaoFilter` e `PortaCelularFilter`. 4 parâmetros e 4 retornos em `JpaCaixaRepository`, `JpaCompraEstoqueRepository`, `JpaFornecedorRepository`, `JpaNotaFiscalRepository`. Revisar as anotações de `findById` preservando os fetch plans. |
| Autounboxing de `Long` (536871371) | 2 | 0 | 2 | Um caso protegido e um risco no limite JDBC, detalhados abaixo. |
| Imports, métodos, campos e variável sem uso | 9 | 8 | 17 | Baixa. 9 imports, 4 métodos, 3 campos, 1 variável. Verificar reflexão e uso indireto antes de excluir. |
| **Total de avisos** | **434** | **86** | **520** | Os 12 marcadores informativos correspondem a seis tarefas em `Fornecedor.java` e seis em `Usuario.java`, originadas por `XXX` em documentação de máscara de CNPJ. |

Distribuição de produção por pacote: `app` 1, `controller` 28, `model` 125, `repository` 101 e `service` 179. Nos testes, `integration` 73 e `service` 13. O inventário por arquivo está no apêndice.

### Como capturar o estado vivo do IDE

1. Abra a raiz no VS Code, salve os arquivos e use o servidor Java em modo Standard. Abra o painel **Problems**, limpe os filtros e registre o total, a versão da extensão e os JDKs em **Java: Configure Java Runtime**.
2. Capture a situação anterior à reimportação. Para exportação estruturada no Extension Host, leia `vscode.languages.getDiagnostics()` e serialize URI, `source`, `severity`, `code`, `message` e `range`. Alternativamente, habilite temporariamente `java.trace.server=verbose` e capture as notificações `textDocument/publishDiagnostics` do canal Java. Retenha a última publicação por URI, incluindo listas vazias, e confira a cobertura com Problems. O trace cobre apenas o servidor Java.
3. Em seguida, reimporte a configuração Maven após aceitar a atualização do build. Confirme Java 25 no projeto, aguarde a conclusão da análise e repita a captura para comparar por arquivo e código. Se o import permanecer obsoleto, use **Java: Clean Java Language Server Workspace** e repita depois do reinício. Mantenha `nullAnalysis` ativo e não compartilhe logs com dados locais desnecessários.

## 4. Achados e recomendações

| Gravidade e estado | Evidência e impacto | Proposta e risco de regressão |
| --- | --- | --- |
| Alta · deficiência comprovada para SaaS compartilhado | `ProdutoEstoqueService.java:20` fixa a filial em `1L`. `CompraEstoqueService` reutiliza a constante. `ProdutoRepositoryAdapter.java:25–35` consulta por ID ou lista global. `SessaoFilter` determina o usuário, sem selecionar tenant. O escopo observado é de instalação. Falha de isolamento entre instalações em produção não foi demonstrada. | Preservar instâncias e credenciais separadas. Antes de migrar para infraestrutura compartilhada, revisar contexto de tenant, autorização, consultas JPA/JDBC, jobs, backups e chaves de idempotência com testes de duas organizações. Expansão fora da Fase 2 de nulabilidade. |
| Alta · risco condicionado à configuração | `SessaoFilter.java:59–65` permite o cabeçalho legado sem sessão quando configurado. `AppConfig.java:693–698` registra o filtro no espaço de APIs. Testes antigos habilitam a opção, o que reduz seu valor como evidência da política real de sessão. | Criar controle de startup/configuração que restrinja o modo a testes e expandir testes de autorização com o modo desligado, incluindo PIN e permissão por operação. Proteger rotas existentes de clientes legítimos. |
| Alta se a credencial for válida · exposição local comprovada | O arquivo ignorado `src/main/resources/application-local.properties:7` contém senha literal e é importado por `application.properties:5`. `git ls-files` não o listou e `.dockerignore` o exclui. O build Maven copia recursos locais, podendo incluir a configuração no JAR produzido naquela árvore. Validade ou exposição externa da credencial não foram verificadas. | Remover credenciais literais do fluxo de empacotamento, injetar valores por ambiente e conferir o conteúdo dos artefatos. Revisar a validade e rotacionar a credencial caso tenha sido distribuída. |
| Média · defeito de tratamento demonstrável por inspeção | `GlobalExceptionHandler.java:65–78` captura `Exception` e devolve 500. Os `@RequestBody` obrigatórios, como os de `ProdutoController.java:94,127,159`, são desserializados antes da execução do método. O advice não tem tratamento para `HttpMessageNotReadableException`; uma falha de leitura que alcance o fallback vira 500. A execução isolada da sondagem não completou, portanto o status HTTP ainda requer teste. | Cobrir JSON malformado e corpo ausente com MockMvc e definir explicitamente status e envelope. A mudança observável de resposta exige aprovação sobre compatibilidade. A classificação por texto em `codigoPorMensagem` também pode variar com a redação das mensagens. |
| Média · divergência comprovada por código | `InMemoryProdutoRepository.java:60–61` devolve uma cópia da lista na ordem interna. `salvar` remove e reinsere no fim; `ProdutoRepositoryAdapter.java:34–35` ordena por ID. Atualizar o menor ID quando há dois produtos muda a ordem em memória, enquanto o adapter mantém a ordem por ID. `InMemoryProdutoRepository` guarda e devolve referências, sem as garantias de unidade de trabalho JPA. | Definir a ordem e identidade observáveis de cada porta antes de alinhar os doubles. Testar atualização, ordem, busca ausente e comportamento quando há mutação/rollback. Preservar o resultado consumido pela entrada legada. |
| Média · risco de duplicação operacional | `CozinhaService.java:220–243` lê pedidos pendentes, imprime e confirma em etapas distintas. O `SELECT` não reivindica registros antes do efeito externo. Mais de um worker pode selecionar o mesmo pedido. Falha após impressão e antes do update também pode produzir repetição. | Definir política de reentrega, reivindicação atômica e identificação da impressão. Validar retries e falha pós-efeito com impressora simulada. Pode alterar o comportamento esperado de reimpressão. |
| Média · risco condicionado a dados JDBC | `CozinhaService.java:211–215` converte automaticamente `Long` de `JdbcTemplate.queryForObject` para `long`. `INSERT … RETURNING id` torna nulo improvável no banco esperado, mas o limite de chamada admite esse estado. | Documentar a garantia do ID e tratar violação da invariante junto ao JDBC. Cobrir o caso de retorno ausente e o rollback. |
| Média · risco de concorrência e tradução incorreta | `FinalizacaoVendaService.java:497–509` transforma qualquer `DataIntegrityViolationException` de `saveAndFlush` em venda concorrente. A causa pode ser outra restrição. `CompraEstoqueService` usa lógica de reserva semelhante. Há locks pessimistas em `JpaVendaRepository`, `JpaEstoqueProdutoRepository` e `JpaFichaTecnicaRepository`, e controle otimista em entidades como `Venda` e `EstoqueProduto`. Nenhum teste concorrente desta execução validou o resultado no PostgreSQL. | Isolar o conflito de chave esperado de violações diferentes. Verificar chamadas concorrentes, rollback integral, locks, retomada da operação e replay do mesmo payload sem replicar efeitos. |
| Média · risco de DTO e carregamento | Records de DTO em `ProdutoController`, `VendaController` e `DisponibilidadeDtos` combinam campos opcionais, objetos de domínio e projeções. Sem `open-in-view`, mapeamentos que acessem associação lazy fora da transação podem falhar. A cobertura dos endpoints de integração ficou bloqueada. | Inventariar campos obrigatórios e opcionais por endpoint, rastrear carregamento até a projeção e testar ausência, nulo explícito e dados legados. Preservar nomes e formatos JSON e evitar fixes globais de fetch. |
| Baixa a média · causa transversal de manutenção | Não foi encontrada anotação local consistente de nulabilidade nas portas, entidades ou DTOs. Guards booleanos em `MovimentoEstoque`, `ItemCardapio`, `CotacaoMensal` e outros ocultam refinamento de fluxo. As mesmas fronteiras se repetem em adapters JPA, Streams e comparadores InMemory. | Começar por contratos diretamente verificados e guards locais com preservação da classe e mensagem de exceção. Anotar fronteiras JPA e servlet em pequenos lotes. Garantir tratamento dos registros legados e evitar default global prematuro em pacotes de entidades e records. |

### Falsos positivos e alertas que exigem evidência

- **Confirmado no fluxo de compra.** Em `CompraEstoqueService.java:110–120`, `temEmbalagem` só é verdadeiro se `embalagemId != null` e for positivo. O autounboxing no ramo está protegido. A marcação reflete a perda de correlação do analisador com o booleano local.
- **Exemplo de falso positivo por pré-condição.** `ItemCardapio.java:87–88,203–204` valida `precoVenda != null` com helper que lança antes do `compareTo`. Isso vale para esse construtor. Entidades hidratadas pelo JPA ou dados legados exigem análise própria. O comparador de `InMemoryItemCardapioRepository.java:33–34` depende ainda do contrato da categoria e do nome de exibição.
- **Candidatos que pedem revisão de origem.** A chave em `InMemoryCotacaoRepository.java:56–65` vem do getter de `CotacaoMensal`, cujo construtor público exige mês. Verificar criação por JPA antes de classificar genericamente os avisos. Em `BigDecimal::add`, o aviso de referência funcional pode ser ruído quando há identidade não nula e todos os elementos são validados. Cada fonte precisa dessa prova. Trocar a referência por lambda sem esclarecer a invariante apenas desloca o diagnóstico.
- **Tarefas espúrias confirmadas.** Os 12 registros informativos são marcadores de palavra-chave dentro das máscaras de CNPJ. Os avisos de overrides do Spring, em contraste, representam divergência formal de contrato e devem ser tratados como tal.

## 5. Plano incremental e critérios de aceitação

| Etapa | Trabalho proposto | Critério de aceitação |
| --- | --- | --- |
| 1 · diagnóstico entregue | Inventário JDT salvo, tentativa completa da suíte, execução sem banco, compilação estrita e triagem. | Relatório identifica base e toolchain, distingue contagens, evidencia erros de infraestrutura e deixa registrada a lacuna de integração. |
| 2A · baseline viva, após aprovação | Reimportar Java 25 no JDT e executar a suíte em PostgreSQL descartável. | Exportar os diagnósticos antes/depois com fonte, severidade e versão. Build limpo e suíte completa sem erro de contexto, com totais Surefire registrados. |
| 2B · contratos locais e fronteiras | Corrigir guards que o analisador não entende, overrides e contratos em pontos comprovados. Tratar DTOs e comparadores por invariantes verificadas. | O mesmo JDK e a mesma captura do JDT mostram redução por código, sem novos avisos no lote. Mesmos endpoints e respostas esperadas, incluindo null/ausência, ordenação e exceções. Análise ativa e sem supressão genérica. |
| 2C · defeitos funcionais e concorrência | Priorizar configuração de autenticação, credenciais, erros MVC e idempotência. Alinhar doubles nos contratos efetivamente consumidos. | Casos de regressão reproduzem o problema anterior e passam após a correção. Testes em PostgreSQL comprovam rollback, idempotência, autorização e ausência de efeitos duplicados nos cenários aceitos. Qualquer mudança observável de contrato passa por aprovação. |
| Posterior · isolamento compartilhado | Auditar e implementar separação de tenant somente em escopo próprio. | Matriz com dois tenants cobre leitura, escrita, IDs indiretos, jobs e backup. A promoção depende de validação em ambiente dedicado. |

## Apêndice — avisos JDT por arquivo

**Ref.** inferência em descritor funcional. **Conv.** conversão sem prova. **Var.** variável potencialmente nula. **Ret.** retorno potencialmente nulo. **Override** parâmetro ou retorno incompatível. **Unbox** autounboxing. **Manut.** símbolos sem uso. As contagens não incluem tarefas e usam os arquivos do snapshot persistido.

| Arquivo sob `src/` | Ref. | Conv. | Var. | Ret. | Override | Unbox | Manut. | Total |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `main/java/app/DemoCatalogSeedRunner.java` | 0 | 0 | 0 | 0 | 0 | 0 | 1 | 1 |
| `main/java/controller/AuthController.java` | 1 | 2 | 0 | 0 | 0 | 0 | 0 | 3 |
| `main/java/controller/CaixaController.java` | 8 | 0 | 0 | 0 | 0 | 0 | 0 | 8 |
| `main/java/controller/CelularController.java` | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `main/java/controller/CompraEstoqueController.java` | 0 | 3 | 0 | 0 | 0 | 0 | 0 | 3 |
| `main/java/controller/FuncionarioController.java` | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| `main/java/controller/GlobalExceptionHandler.java` | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `main/java/controller/ImportacaoNfeController.java` | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| `main/java/controller/LogController.java` | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| `main/java/controller/MovimentoEstoqueController.java` | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `main/java/controller/PortaCelularFilter.java` | 0 | 0 | 0 | 0 | 3 | 0 | 0 | 3 |
| `main/java/controller/ProdutoController.java` | 0 | 0 | 0 | 0 | 0 | 0 | 2 | 2 |
| `main/java/controller/SessaoFilter.java` | 0 | 0 | 0 | 0 | 3 | 0 | 0 | 3 |
| `main/java/model/Caixa.java` | 8 | 0 | 1 | 0 | 0 | 0 | 0 | 9 |
| `main/java/model/CategoriaCardapio.java` | 0 | 0 | 2 | 0 | 0 | 0 | 0 | 2 |
| `main/java/model/ClasseFuncionario.java` | 0 | 0 | 4 | 0 | 0 | 0 | 0 | 4 |
| `main/java/model/CompraEstoque.java` | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 2 |
| `main/java/model/CotacaoMensal.java` | 0 | 0 | 1 | 0 | 0 | 0 | 0 | 1 |
| `main/java/model/EmbalagemProduto.java` | 0 | 0 | 3 | 0 | 0 | 0 | 0 | 3 |
| `main/java/model/FechamentoCaixa.java` | 0 | 0 | 4 | 0 | 0 | 0 | 0 | 4 |
| `main/java/model/FichaTecnica.java` | 0 | 0 | 4 | 0 | 0 | 0 | 0 | 4 |
| `main/java/model/FichaTecnicaCustoSnapshot.java` | 0 | 0 | 2 | 0 | 0 | 0 | 0 | 2 |
| `main/java/model/FichaTecnicaIngrediente.java` | 0 | 0 | 4 | 0 | 0 | 0 | 0 | 4 |
| `main/java/model/FichaTecnicaIngredienteCustoSnapshot.java` | 0 | 0 | 3 | 0 | 0 | 0 | 0 | 3 |
| `main/java/model/FichaTecnicaVersao.java` | 0 | 0 | 1 | 0 | 0 | 0 | 0 | 1 |
| `main/java/model/Fornecedor.java` | 0 | 0 | 3 | 0 | 0 | 0 | 0 | 3 |
| `main/java/model/GrupoModificador.java` | 0 | 0 | 1 | 0 | 0 | 0 | 0 | 1 |
| `main/java/model/IncompatibilidadeOpcaoModificador.java` | 0 | 0 | 1 | 0 | 0 | 0 | 0 | 1 |
| `main/java/model/ItemCardapio.java` | 0 | 0 | 3 | 0 | 0 | 0 | 0 | 3 |
| `main/java/model/ItemCompraEstoque.java` | 0 | 0 | 4 | 0 | 0 | 0 | 0 | 4 |
| `main/java/model/ItemNotaFiscal.java` | 0 | 0 | 2 | 0 | 0 | 0 | 0 | 2 |
| `main/java/model/LogAcao.java` | 0 | 0 | 1 | 0 | 0 | 0 | 0 | 1 |
| `main/java/model/MovimentacaoCaixa.java` | 0 | 0 | 1 | 0 | 0 | 0 | 0 | 1 |
| `main/java/model/MovimentoEstoque.java` | 0 | 0 | 11 | 0 | 0 | 0 | 0 | 11 |
| `main/java/model/NotaFiscal.java` | 3 | 0 | 1 | 0 | 0 | 0 | 0 | 4 |
| `main/java/model/OpcaoModificador.java` | 0 | 0 | 1 | 0 | 0 | 0 | 0 | 1 |
| `main/java/model/Pedido.java` | 0 | 0 | 4 | 0 | 0 | 0 | 0 | 4 |
| `main/java/model/PermissoesStringConverter.java` | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 2 |
| `main/java/model/Produto.java` | 0 | 0 | 2 | 0 | 0 | 0 | 0 | 2 |
| `main/java/model/RegistroIdempotencia.java` | 0 | 0 | 3 | 0 | 0 | 0 | 0 | 3 |
| `main/java/model/RegraHorarioCardapio.java` | 0 | 0 | 2 | 0 | 0 | 0 | 0 | 2 |
| `main/java/model/ResultadoComparacao.java` | 0 | 0 | 2 | 0 | 0 | 0 | 0 | 2 |
| `main/java/model/UnidadeMedida.java` | 0 | 0 | 5 | 0 | 0 | 0 | 0 | 5 |
| `main/java/model/Usuario.java` | 0 | 0 | 3 | 0 | 0 | 0 | 0 | 3 |
| `main/java/model/VariacaoItemCardapio.java` | 0 | 0 | 2 | 0 | 0 | 0 | 0 | 2 |
| `main/java/model/Venda.java` | 0 | 0 | 3 | 0 | 0 | 0 | 0 | 3 |
| `main/java/model/VendaItem.java` | 0 | 0 | 5 | 0 | 0 | 0 | 0 | 5 |
| `main/java/model/VendaItemConsumo.java` | 0 | 0 | 6 | 0 | 0 | 0 | 0 | 6 |
| `main/java/model/VendaItemConsumoEstorno.java` | 0 | 0 | 4 | 0 | 0 | 0 | 0 | 4 |
| `main/java/model/VendaItemModificador.java` | 0 | 0 | 2 | 0 | 0 | 0 | 0 | 2 |
| `main/java/model/VendaItemModificadorImpacto.java` | 0 | 0 | 3 | 0 | 0 | 0 | 0 | 3 |
| `main/java/model/VendaItemParte.java` | 0 | 0 | 5 | 0 | 0 | 0 | 0 | 5 |
| `main/java/model/VendaPagamento.java` | 0 | 0 | 1 | 0 | 0 | 0 | 0 | 1 |
| `main/java/repository/CaixaRepositoryAdapter.java` | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `main/java/repository/CategoriaCardapioRepositoryAdapter.java` | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `main/java/repository/CodigoVendaRepositoryAdapter.java` | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `main/java/repository/ComandaRepositoryAdapter.java` | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `main/java/repository/CompraEstoqueRepositoryAdapter.java` | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `main/java/repository/ConfiguracaoComposicaoMultiplaParteRepositoryAdapter.java` | 0 | 2 | 0 | 0 | 0 | 0 | 0 | 2 |
| `main/java/repository/ConfiguracaoComposicaoMultiplaRepositoryAdapter.java` | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `main/java/repository/CotacaoRepository.java` | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 2 |
| `main/java/repository/CotacaoRepositoryAdapter.java` | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `main/java/repository/EmbalagemProdutoRepositoryAdapter.java` | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `main/java/repository/EstoqueProdutoRepositoryAdapter.java` | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `main/java/repository/FechamentoCaixaRepositoryAdapter.java` | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `main/java/repository/FichaTecnicaCustoSnapshotRepositoryAdapter.java` | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `main/java/repository/FichaTecnicaIngredienteCustoSnapshotRepositoryAdapter.java` | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `main/java/repository/FichaTecnicaIngredienteRepositoryAdapter.java` | 0 | 2 | 0 | 0 | 0 | 0 | 0 | 2 |
| `main/java/repository/FichaTecnicaRepositoryAdapter.java` | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `main/java/repository/FichaTecnicaVersaoRepositoryAdapter.java` | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `main/java/repository/FornecedorRepositoryAdapter.java` | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `main/java/repository/GrupoModificadorRepositoryAdapter.java` | 3 | 1 | 0 | 0 | 0 | 0 | 0 | 4 |
| `main/java/repository/InMemoryCategoriaCardapioRepository.java` | 3 | 0 | 0 | 0 | 0 | 0 | 0 | 3 |
| `main/java/repository/InMemoryCodigoVendaRepository.java` | 9 | 0 | 0 | 0 | 0 | 0 | 0 | 9 |
| `main/java/repository/InMemoryCompraEstoqueRepository.java` | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| `main/java/repository/InMemoryCotacaoRepository.java` | 3 | 0 | 0 | 0 | 0 | 0 | 0 | 3 |
| `main/java/repository/InMemoryEmbalagemProdutoRepository.java` | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| `main/java/repository/InMemoryFornecedorRepository.java` | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| `main/java/repository/InMemoryItemCardapioRepository.java` | 4 | 0 | 0 | 0 | 0 | 0 | 0 | 4 |
| `main/java/repository/InMemoryMovimentoEstoqueRepository.java` | 3 | 0 | 0 | 0 | 0 | 0 | 0 | 3 |
| `main/java/repository/InMemoryUnidadeMedidaRepository.java` | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| `main/java/repository/InMemoryUsuarioRepository.java` | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| `main/java/repository/IncompatibilidadeOpcaoModificadorRepositoryAdapter.java` | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `main/java/repository/ItemCardapioRepositoryAdapter.java` | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `main/java/repository/JpaCaixaRepository.java` | 0 | 0 | 0 | 0 | 2 | 0 | 0 | 2 |
| `main/java/repository/JpaCompraEstoqueRepository.java` | 0 | 0 | 0 | 0 | 2 | 0 | 0 | 2 |
| `main/java/repository/JpaFornecedorRepository.java` | 0 | 0 | 0 | 0 | 2 | 0 | 0 | 2 |
| `main/java/repository/JpaNotaFiscalRepository.java` | 0 | 0 | 0 | 0 | 2 | 0 | 0 | 2 |
| `main/java/repository/LogRepositoryAdapter.java` | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `main/java/repository/MovimentoEstoqueRepositoryAdapter.java` | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `main/java/repository/NotaFiscalRepositoryAdapter.java` | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `main/java/repository/OpcaoModificadorComponenteRepositoryAdapter.java` | 0 | 2 | 0 | 0 | 0 | 0 | 0 | 2 |
| `main/java/repository/OpcaoModificadorRepositoryAdapter.java` | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `main/java/repository/PausaCardapioRepositoryAdapter.java` | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `main/java/repository/PedidoRepository.java` | 4 | 0 | 0 | 0 | 0 | 0 | 0 | 4 |
| `main/java/repository/PedidoRepositoryAdapter.java` | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `main/java/repository/ProdutoRepositoryAdapter.java` | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `main/java/repository/RegistroIdempotenciaRepositoryAdapter.java` | 0 | 2 | 0 | 0 | 0 | 0 | 0 | 2 |
| `main/java/repository/RegraHorarioCardapioRepositoryAdapter.java` | 0 | 2 | 0 | 0 | 0 | 0 | 0 | 2 |
| `main/java/repository/SessaoUsuarioRepositoryAdapter.java` | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `main/java/repository/UnidadeMedidaRepositoryAdapter.java` | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `main/java/repository/UsuarioRepository.java` | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| `main/java/repository/UsuarioRepositoryAdapter.java` | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `main/java/repository/VariacaoCanalDisponibilidadeRepositoryAdapter.java` | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `main/java/repository/VariacaoGrupoModificadorRepositoryAdapter.java` | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `main/java/repository/VariacaoItemCardapioRepositoryAdapter.java` | 3 | 1 | 0 | 0 | 0 | 0 | 0 | 4 |
| `main/java/repository/VendaItemConsumoEstornoRepositoryAdapter.java` | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `main/java/repository/VendaItemConsumoRepositoryAdapter.java` | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `main/java/repository/VendaItemModificadorImpactoRepositoryAdapter.java` | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `main/java/repository/VendaItemModificadorRepositoryAdapter.java` | 0 | 2 | 0 | 0 | 0 | 0 | 0 | 2 |
| `main/java/repository/VendaItemParteRepositoryAdapter.java` | 0 | 2 | 0 | 0 | 0 | 0 | 0 | 2 |
| `main/java/repository/VendaItemRepositoryAdapter.java` | 0 | 2 | 0 | 0 | 0 | 0 | 0 | 2 |
| `main/java/repository/VendaPagamentoRepositoryAdapter.java` | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `main/java/repository/VendaRepositoryAdapter.java` | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `main/java/repository/VinculoProdutoFornecedorRepositoryAdapter.java` | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `main/java/service/BackupService.java` | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 2 |
| `main/java/service/CaixaService.java` | 5 | 0 | 0 | 0 | 0 | 0 | 0 | 5 |
| `main/java/service/CalculadoraCapacidadeService.java` | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 2 |
| `main/java/service/CalculadoraCustoFichaTecnicaService.java` | 0 | 0 | 1 | 0 | 0 | 0 | 0 | 1 |
| `main/java/service/CalculadoraDisponibilidadeConfiguracaoService.java` | 19 | 0 | 1 | 0 | 0 | 0 | 0 | 20 |
| `main/java/service/CalculadoraDisponibilidadeService.java` | 8 | 0 | 1 | 0 | 0 | 0 | 1 | 10 |
| `main/java/service/CancelamentoVendaService.java` | 6 | 0 | 0 | 0 | 0 | 0 | 0 | 6 |
| `main/java/service/CardapioService.java` | 8 | 0 | 0 | 0 | 0 | 0 | 0 | 8 |
| `main/java/service/CertificadoCelular.java` | 3 | 0 | 0 | 0 | 0 | 0 | 0 | 3 |
| `main/java/service/CodigoVendaService.java` | 4 | 0 | 0 | 0 | 0 | 0 | 0 | 4 |
| `main/java/service/ComandaService.java` | 11 | 0 | 0 | 0 | 0 | 0 | 0 | 11 |
| `main/java/service/ComposicaoMultiplaService.java` | 8 | 0 | 0 | 0 | 0 | 0 | 0 | 8 |
| `main/java/service/CompraEstoqueService.java` | 1 | 0 | 0 | 0 | 0 | 1 | 0 | 2 |
| `main/java/service/CotacaoService.java` | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| `main/java/service/CozinhaService.java` | 2 | 0 | 0 | 0 | 0 | 1 | 0 | 3 |
| `main/java/service/DescontoVendaService.java` | 3 | 0 | 0 | 0 | 0 | 0 | 0 | 3 |
| `main/java/service/EstoqueService.java` | 3 | 0 | 0 | 0 | 0 | 0 | 0 | 3 |
| `main/java/service/FinalizacaoVendaService.java` | 3 | 0 | 0 | 0 | 0 | 0 | 0 | 3 |
| `main/java/service/ImportacaoNfeService.java` | 4 | 0 | 0 | 0 | 0 | 0 | 0 | 4 |
| `main/java/service/ImpressoraCozinha.java` | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| `main/java/service/IntegracaoCaixaLegadoService.java` | 4 | 0 | 0 | 0 | 0 | 0 | 0 | 4 |
| `main/java/service/MontagemComposicaoVendaService.java` | 1 | 0 | 0 | 0 | 0 | 0 | 1 | 2 |
| `main/java/service/MontagemConfiguracaoItemService.java` | 20 | 0 | 0 | 0 | 0 | 0 | 0 | 20 |
| `main/java/service/NfeXmlParser.java` | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 2 |
| `main/java/service/NotaFiscalService.java` | 0 | 0 | 0 | 0 | 0 | 0 | 1 | 1 |
| `main/java/service/ProdutoEstoqueService.java` | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| `main/java/service/RelatorioVendasService.java` | 22 | 0 | 0 | 0 | 0 | 0 | 0 | 22 |
| `main/java/service/RentabilidadeService.java` | 12 | 0 | 2 | 0 | 0 | 0 | 2 | 16 |
| `main/java/service/ResolucaoComposicaoVariacaoService.java` | 3 | 0 | 0 | 0 | 0 | 0 | 0 | 3 |
| `main/java/service/ResumoOperadoresCaixa.java` | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 2 |
| `main/java/service/RetencaoBackup.java` | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| `main/java/service/SessaoService.java` | 3 | 0 | 0 | 0 | 0 | 0 | 0 | 3 |
| `main/java/service/SimulacaoPrecoFichaTecnicaService.java` | 0 | 0 | 0 | 0 | 0 | 0 | 1 | 1 |
| `main/java/service/SincronizadorEstoque.java` | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| `test/java/integration/CardapioAdminIntegrationTest.java` | 0 | 0 | 0 | 14 | 0 | 0 | 0 | 14 |
| `test/java/integration/Fase0EstoqueDecimalApiIntegrationTest.java` | 0 | 0 | 0 | 2 | 0 | 0 | 0 | 2 |
| `test/java/integration/Fase1FichaTecnicaApiIntegrationTest.java` | 1 | 1 | 0 | 0 | 0 | 0 | 1 | 3 |
| `test/java/integration/Fase2VendaApiIntegrationTest.java` | 2 | 1 | 0 | 0 | 0 | 0 | 3 | 6 |
| `test/java/integration/Fase3DisponibilidadeApiIntegrationTest.java` | 1 | 1 | 0 | 0 | 0 | 0 | 0 | 2 |
| `test/java/integration/Fase45DisponibilidadePerformanceIntegrationTest.java` | 0 | 1 | 0 | 0 | 0 | 0 | 1 | 2 |
| `test/java/integration/Fase4ModificadoresApiIntegrationTest.java` | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `test/java/integration/Fase5MultissaborApiIntegrationTest.java` | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `test/java/integration/LoginSeguroApiIntegrationTest.java` | 0 | 2 | 1 | 7 | 0 | 0 | 0 | 10 |
| `test/java/integration/MovimentacoesRecentesPostgresIntegrationTest.java` | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 2 |
| `test/java/integration/PdvBalcaoApiIntegrationTest.java` | 0 | 2 | 0 | 0 | 0 | 0 | 0 | 2 |
| `test/java/integration/PdvMenuCatalogIntegrationTest.java` | 0 | 0 | 0 | 10 | 0 | 0 | 0 | 10 |
| `test/java/integration/RentabilidadePostgresIntegrationTest.java` | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| `test/java/integration/Wave2InventoryAndProcurementIntegrationTest.java` | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| `test/java/integration/Wave3CashRegisterIntegrationTest.java` | 6 | 0 | 0 | 0 | 0 | 0 | 1 | 7 |
| `test/java/integration/Wave4ApiAuthorizationIntegrationTest.java` | 0 | 0 | 0 | 1 | 0 | 0 | 0 | 1 |
| `test/java/integration/Wave5FrontendShellIntegrationTest.java` | 0 | 0 | 0 | 8 | 0 | 0 | 0 | 8 |
| `test/java/service/CaixaServiceTest.java` | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| `test/java/service/CompraEstoqueServiceTest.java` | 4 | 0 | 0 | 0 | 0 | 0 | 0 | 4 |
| `test/java/service/DescontoVendaNovoTest.java` | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| `test/java/service/FichaTecnicaCalculoServiceTest.java` | 0 | 0 | 0 | 0 | 0 | 0 | 1 | 1 |
| `test/java/service/LoginSeguroNovoTest.java` | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| `test/java/service/ProdutoRevendaNovoTest.java` | 2 | 0 | 0 | 0 | 0 | 0 | 1 | 3 |
| `test/java/service/RelatorioVendasNovoTest.java` | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 2 |
