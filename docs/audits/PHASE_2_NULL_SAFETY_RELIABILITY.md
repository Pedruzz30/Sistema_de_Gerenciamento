# StockOS — Fase 2 · Null Safety & Backend Reliability

**Base de comparação** Fase 1.1, Java 25, 525 avisos do JDT, 16 do javac com `-Xlint:all`, 288 testes aprovados em PostgreSQL descartável. As alterações preexistentes do workspace foram preservadas.

## Estratégia

Usei as anotações `org.springframework.lang` já instaladas e reconhecidas pelo JDT. Elas documentam contratos explícitos de parâmetros, retornos e overrides. A anotação de uma coleção refere-se à coleção em si. A versão usada no projeto não é uma solução geral para elementos de genéricos, inferência em referências de métodos ou o ciclo de hidratação JPA. A análise automática do JDT foi mantida e nenhum pacote ganhou um padrão generalizado de não nulabilidade. Também preservei `Optional.empty()` e listas vazias quando representam ausência.

A inspeção distinguiu igualdade por ID persistido em `CodigoVenda` e `MovimentoEstoque`, igualdade de fornecedor e ordem dos repositórios. Não foram alterados `equals` e `hashCode`. IDs gerados são primitivos nas entidades auditadas. O ID encapsulado usado pelo CRUD JPA continua sujeito ao contrato Spring. O modelo permite referências opcionais, como movimento estornado e usuário, e esses campos não receberam declaração indiscriminada de não nulabilidade. Comparadores usam a ordem persistente. Em movimentos, a data vem do construtor ou de `@PrePersist`, e o empate é desfeito pelo ID. Permanecem os mapeamentos, queries, chaves, schema e contratos HTTP.

## Lotes implementados

| Lote | Arquivos e evidência |
| --- | --- |
| Persistência local | `InMemoryProdutoRepository` e `InMemoryFornecedorRepository` retornavam a ordem de inserção após substituir uma entidade, enquanto os adapters ordenam por ID. Dois testes reproduziram a divergência antes da correção. As listas em memória continuam independentes e mantêm a ordem por ID após atualizar. A busca de fornecedor trata nulo ou branco como ausência. |
| Identidade e entrada | `InMemoryCodigoVendaRepository` avançava a sequência apenas para IDs gerados. Um registro já identificado podia ser removido ao gerar o mesmo ID para o próximo objeto. A sequência agora considera o ID persistido e a regressão verifica que ambos permanecem. Os repositórios em memória de código e movimento rejeitam entidade nula antes de acessar seus campos. A ordenação de código usa comparadores primitivos para IDs, preservando os critérios do adapter. |
| Contratos Spring | `ProdutoRepository`, `FornecedorRepository`, `MovimentoEstoqueRepository`, `CodigoVendaRepository`, os quatro adapters e os quatro InMemory declaram entrada obrigatória de `salvar`; o CNPJ opcional aparece no contrato e nas implementações. `JpaCaixaRepository`, `JpaCompraEstoqueRepository`, `JpaFornecedorRepository` e `JpaNotaFiscalRepository` carregam a nulabilidade do `CrudRepository.findById` no override. `JpaMovimentoEstoqueRepository` declara retornos de coleção não nulos. `SessaoFilter` e `PortaCelularFilter` repetem o contrato da superclasse sem alterar as condições de autorização. |
| Guards de domínio | `Fornecedor`, `MovimentoEstoque` e `VendaItem` tornam diretas algumas validações que o JDT não conseguia acompanhar pelo helper booleano. A lógica, a ordem e as mensagens das validações foram mantidas. Os avisos nesses pontos eram falsos positivos de análise de fluxo. |
| Construção e compilador | Os oito casos de chamada virtual durante construção foram tratados em caminhos internos de `VendaItem`, `FichaTecnicaIngrediente`, `FichaTecnicaVersao`, `OpcaoModificadorComponente`, `VariacaoGrupoModificador`, `OpcaoModificador`, `GrupoModificador` e `ConfiguracaoComposicaoMultipla`. Construtores de JPA, encapsulamento e mutadores públicos permanecem. Os cenários de migração e de HTTPS foram ajustados em `Fase46FlywayMigrationIntegrationTest` e `CelularNovoTest`. |
| Testes de regressão | `InMemoryRepositoryContractTest` adicionou quatro casos cobrindo ordenação após update, coleção vazia, ID ausente, CNPJ nulo ou branco, rejeição de entidade nula, preservação de registro identificado e precedência do código padrão. A equivalência de ordenação é testada contra o contrato da consulta real por ID. Os testes existentes exercitam os adapters e o Hibernate em PostgreSQL. |

## Revisão dos 16 diagnósticos javac

| Local inicial | Causa, gravidade e tratamento |
| --- | --- |
| `VendaItem:177` | Risco de observar snapshot incompleto, médio. A inicialização chama rotinas privadas de snapshot e custo. |
| `FichaTecnicaIngrediente:90` | Risco de acesso a quantidades durante construção, médio. Inicialização privada. |
| `FichaTecnicaVersao:109` | Risco de observar rascunho incompleto, médio. Inicialização privada, com a verificação de transição preservada no mutador público. |
| `OpcaoModificadorComponente:88` | Risco de observar vínculos incompletos, médio. Inicialização privada. |
| `VariacaoGrupoModificador:80` | Risco de observar associação incompleta, médio. Inicialização privada. |
| `OpcaoModificador:96` | Risco de observar dados comerciais incompletos, médio. Inicialização privada. |
| `GrupoModificador:92` | Risco de observar configuração incompleta, médio. Inicialização privada. |
| `ConfiguracaoComposicaoMultipla:96` | Risco de observar composição incompleta, médio. Inicialização privada. |
| `ErroNegocioException:7` | Compatibilidade séria apenas se houver serialização Java, baixo. UID explícito. |
| `ErroNegocioException:10` | Risco condicionado à serialização Java, baixo. `Map<String,Object>` aceita valores não serializáveis. Preservado o contrato de detalhes entregue ao advice. Não foi encontrado consumidor que use serialização Java para essa exceção. |
| `ConflitoIdempotenciaException:3` | Compatibilidade condicionada à serialização Java, baixo. UID explícito. |
| `Fase46FlywayMigrationIntegrationTest:31` | Recurso ignorado, baixo. O contexto inicializado passa a ter verificação explícita de atividade. |
| `Fase46FlywayMigrationIntegrationTest:66` | Recurso ignorado, baixo. Mesma verificação no cenário de baseline compatível. |
| `Fase46FlywayMigrationIntegrationTest:91` | Recurso ignorado, baixo. O teste continua esperando exceção e fecha o contexto se a chamada inesperadamente retornar. |
| `CelularNovoTest:122` | Construção de URL depreciada, baixo. URI seguida de conversão para URL. |
| `CelularNovoTest:138` | Segunda ocorrência da mesma depreciação, baixo. Mesma conversão. |

O risco de regressão dos construtores está concentrado em subclasses que poderiam depender de override durante inicialização. A busca no projeto não encontrou subclasses que usem esse comportamento. Consultas, carregamento Hibernate e cenários de API são cobertos pela suíte existente. Os UIDs explícitos evitam mudanças futuras implícitas, mas streams Java gravados anteriormente com o UID derivado poderiam ser incompatíveis. Não foi aplicado `@SuppressWarnings`.

## Estado residual entre execuções da suíte

Quatro falhas anteriores de comanda se concentram em números selecionados por uma sequência estática local à JVM (`encerrarCaixaComComandaAbertaPedeConfirmacao`, `nomeDoClienteFicaJuntoDaComanda`, `pedidoDoCelularAbreAComandaESomaNaMesmaLinha` e `transferirParteDeUmItemEJuntarComandas`). A sequência reinicia na próxima execução, enquanto comandas e itens ficam persistidos. O `@BeforeEach` recupera os usuários e deixa essas fixtures no banco. Os totais acima do esperado e a comanda já aberta correspondem ao estado residual.

O quinto caso, `importarXmlCriaProdutoVendeEReconheceNaSegundaNota`, falhou com incompatibilidade de unidade durante a criação. Mesmo padrão de falha restrito à reutilização da base na Fase 1.1. A inspeção mostrou que a nota e os códigos são derivados de um sufixo de tempo e contador; os logs disponíveis não identificam a linha de fixture ou o cadastro que causou essa resolução. Classifico como falha de isolamento da preparação da suíte, com causa individual da unidade pendente. Cada comando com integração desta fase usa cluster e banco descartáveis novos. O contrato de APIs e a política de fixtures existente foram preservados.

## Diagnósticos finais

| Fonte | Inicial | Final |
| --- | ---: | ---: |
| JDT, produção | 434 | 408 |
| JDT, testes | 91 | 88 |
| JDT, total | 525 | 496 |
| javac `-Xlint:all` | 16 | 1 |

A captura usou o mesmo JDT LS da Fase 1.1 em workspace novo com Java 25 e `nullanalysis=enabled`. Houve zero erros e 12 marcadores de tarefa. A comparação por arquivo, código e mensagem mostrou 41 ocorrências iniciais ausentes e 12 ocorrências novas, redução líquida de 29. Saíram 14 diagnósticos de override, 15 avisos de variável potencialmente nula, quatro conversões em adapters, três mensagens de descritor funcional, três variáveis ignoradas e duas depreciações.

Dos 12 diagnósticos novos, seis são inferências em referências dos comparadores de ordem por ID. A coleção é privada e preenchida pelos caminhos de persistência validados. Quatro são marcados como código morto porque o chamador Java anotado deve fornecer entidade válida, mas as verificações em runtime preservam a rejeição previsível de chamadas sem verificação estática. Dois são valores nulos deliberadamente injetados pelos testes negativos. Os diagnósticos ficam visíveis.

Restam 261 mensagens de descritor funcional, 101 acessos a variáveis potencialmente nulas, 67 conversões sem prova, 42 retornos potencialmente nulos em testes, 17 avisos de uso de símbolos, quatro avisos sobre código morto, dois autounboxings e dois nulos deliberados. Em particular, a correlação entre o booleano de embalagem e seu ID continua um falso positivo conhecido; o unboxing do ID no limite JDBC da cozinha exige validação própria. O único aviso javac remanescente é o mapa de detalhes de `ErroNegocioException`.

## Validação

```powershell
mvn -B -ntp -Pbackend-baseline '-Dtest=app.**,controller.**,repository.**,service.**,*MigrationSql*Test' test
mvn -B -ntp -Pbackend-baseline '-Dtest=*IntegrationTest' test
mvn -B -ntp -Pbackend-baseline clean verify
```

Os comandos executados também usaram `-o`, cache Maven local explícito e POM temporário com saída redirecionada para preservar as evidências anteriores. As seleções de integração e a suíte completa recebem clusters independentes PostgreSQL 18.3, com SCRAM, senha aleatória, porta local exclusiva e somente dados sintéticos. O procedimento de inicialização está no relatório da Fase 1.1. As seleções e a suíte completa foram executadas no lote inicial e repetidas após os ajustes de entidades e diagnósticos.

| Execução final | Resultado | Tempo Maven |
| --- | --- | --- |
| Testes sem PostgreSQL | 212 aprovados, incluindo os quatro novos | 1 min 29 s |
| Integração em PostgreSQL novo | 80 aprovados, incluindo três de Flyway, sete de autenticação e dois de autorização | 2 min 13 s |
| Compilação limpa e suíte completa via `clean verify` | 292 aprovados, zero falhas, erros ou ignorados. Empacotamento Spring Boot concluído | 2 min 34 s |

O JAR foi inspecionado e não inclui `application-local.properties`. Os clusters descartáveis foram parados. `git diff --check` passou.

Docker Desktop estava indisponível pelo pipe do daemon e só havia PostgreSQL 18 instalado localmente. A imagem e a paridade com o PostgreSQL 17 do CI continuam pendentes. A árvore não possui verificação de isolamento entre tenants em base compartilhada. A cobertura autenticada e de autorização existente refere-se a uma instalação. Nenhuma consulta, critério de escopo ou filtro de autorização foi alterado funcionalmente.

As hipóteses da Fase 1 sobre mapeamento HTTP de JSON malformado, duplicação de impressão e tradução de restrições concorrentes no fluxo de idempotência permanecem abertas. O primeiro caso envolve resposta observável do endpoint. Os outros dependem de reproduções controladas de falha ou concorrência. Esta rodada preserva os respectivos contratos e comportamentos.

Os logs e XMLs Surefire ficam em `target/phase2-final-*`; a captura LSP está em `target/phase2-final-jdt/diagnostics.json`. Os dois testes inicialmente vermelhos estão em `target/phase2-red.log`. Esses arquivos são evidência local e ficam fora do controle de versão.

## Parecer

**Recomendo aprovar a Fase 2 e autorizar a próxima fase em escopo incremental.** Os 288 casos existentes continuam passando junto dos quatro novos. O build e a comparação de diagnósticos foram repetidos com as verificações estáticas ativas. A reprodução usa banco novo por execução. A validação de PostgreSQL 17 e da imagem Docker permanece necessária antes de promover a distribuição correspondente. Operação multi-tenant em banco compartilhado requer validação específica antes de qualquer promoção. A Fase 3 aguarda aprovação humana.
