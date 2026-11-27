# StockOS — Fase 3 · Persistência e integridade de dados

Base da Fase 2 em Java 25, com 292 testes aprovados, 496 avisos JDT e um aviso javac. A árvore já continha alterações das fases anteriores. A Fase 3 preserva o schema, os contratos HTTP e o escopo de autorização existente.

## 3.1 e 3.2 · Contratos e integridade

- **Média — idempotência em memória.** A implementação sobrescrevia um registro distinto quando a chave coincidia no mesmo escopo. PostgreSQL aplica a restrição `uk_registros_idempotencia_escopo` sobre filial, operação e chave. O InMemory agora rejeita a segunda gravação com a exceção traduzida usada pelo adapter, preserva atualização por ID e aplica o mesmo tratamento de chave ausente ou com espaços. O contrato compartilhado confirma duplicata rejeitada, manutenção do primeiro registro e independência entre filiais.
- **Alta — confirmação duplicada de compra.** O teste vermelho abriu duas transações para a mesma compra e chaves distintas. A primeira segurou a transação depois de retornar do serviço. A segunda chegou a um lock do banco antes do commit da primeira. A segunda retornou com sucesso quando deveria rejeitar a confirmação. O monitor Java serializava apenas o corpo do método dentro daquela JVM. Ele não cobria o intervalo até o commit do proxy Spring. O lock de estoque protegia o saldo individual, mas era adquirido depois da leitura do estado de compra.
- **Média — unidade de medida via proxy.** `UnidadeMedida.exigirCompatibilidade` lia diretamente a dimensão e o código da instância de destino. Um teste com `EntityManager.getReference` e unidade sintética reproduziu `Unidades incompatíveis: UNIDADE_ORIGEM (CONTAGEM) para null (null)`. A validação agora acessa a unidade de destino pelos getters que o Hibernate intercepta. O erro de dimensões realmente incompatíveis conserva o formato anterior.

### Revisão complementar

| Área | Evidência e limite |
| --- | --- |
| Produtos, variações, códigos, categorias e cardápio | As consultas por código e as ordenações de referência foram revisadas no escopo da Fase 2. O catálogo mantém vínculos de entidades e entradas de código com ordenação explícita em `JpaCodigoVendaRepository`. Sem revisão de regras de unicidade entre catálogos de instalações diferentes. |
| Fornecedores, embalagens e cotações | Fornecedores e produtos têm escopo global de catálogo na instalação. A migration Fase 0 referencia produto e unidade na embalagem, exige quantidade positiva e dá unicidade por produto e nome. A integração Wave2 verifica cotação atualizada sem duplicar produto e mês. |
| Compras, estoque e movimentos | A migration Fase 0 vincula itens a compra e produto, exige quantidade positiva e registra movimento com origem. Saldo decimal usa escala 6, custo médio escala 8 e valor de estoque escala 4 no caminho de compra. O estoque tem unicidade por filial e produto e campo de versão. A gravação em memória de um novo saldo com a mesma chave composta ainda substitui o anterior. Essa divergência ficou fora da mudança de idempotência e merece contrato separado. |
| Fichas técnicas, ingredientes e disponibilidade | Fichas usam lock na publicação e nas alterações concorrentes do agregado. A disponibilidade usa carregamento em lote e snapshots de custo por versão e filial. Não foram feitas mudanças nos fetch plans, escalas ou mapeamentos. |
| Caixa, vendas e tarefas JDBC | A revisão confirmou escopo por instalação em fluxos de caixa. Consultas de venda com filial opcional e buscas globais por ID exigem contexto de autorização adicional para uso em banco compartilhado. A fila de impressão e a tradução ampla de `DataIntegrityViolationException` nos serviços foram mantidas para revisão dedicada. |

## 3.3 · Atomicidade e concorrência

A compra agora é bloqueada pela chave primária antes de adicionar item ou confirmar, dentro das transações existentes. O lock recai na linha raiz sem o grafo de joins da consulta de leitura. Operações em compras distintas não compartilham esse lock de linha; a sincronização Java preexistente da confirmação foi preservada. O caminho de confirmação mantém a ordem compra → saldos em ordem de produto, e as consultas de leitura continuam sem esse lock. O teste final exige conflito `COMPRA_JA_CONFIRMADA`, um único movimento, saldo acrescido uma vez e ausência da segunda reserva depois do rollback. Não se estende esta evidência a todas as combinações de compra, cancelamento e venda.

## 3.4 · Consultas e desempenho

Foram revisadas consultas JPQL de compra, estoque, venda, movimentos recentes e disponibilidade. A consulta em lote de estoque faz fetch de produto e unidade e ordena por ID. A consulta de movimentos recentes aplica limite e desempate por ID no PostgreSQL. Listagens completas do catálogo e os filtros de venda continuam candidatos a medição com volume representativo. Nenhum cache, índice ou fetch amplo foi introduzido. A validação abaixo inclui a contagem do teste existente de disponibilidade e o limite existente de movimentos recentes. A medição de teste sintético estabelece regressão, sem estimar ganho em produção.

O cenário sintético de 100 itens, 300 variações e 1.500 ingredientes registrou **744 statements preparados** e 5.365 entidades carregadas tanto na Fase 2 quanto nas execuções finais da Fase 3. O limite já coberto pelo teste é 1.300. A duração observada foi de 527 ms na Fase 2, 621 ms na seleção final de integração e 724 ms no `verify` final, em execuções locais sujeitas a contenção. A comparação de latência não foi controlada. O SQL do Hibernate mostrou o lock da compra em `compras_estoque` por chave primária.

## 3.5 · Escopo de estabelecimentos

O novo contrato exercita dois IDs de filial na mesma base de teste, o mesmo produto global com saldos distintos, alteração do saldo A sem alteração de B, listagem limitada a B e busca vazia em uma filial sem saldo. A mesma chave de idempotência fica independente em A e B. O teste passa pelos adapters JPA e pelas implementações InMemory. O escopo é fornecido internamente ao repositório. O teste comprova a filtragem de persistência, sem comprovar que uma identidade HTTP esteja vinculada a um estabelecimento. A aplicação atual ainda usa filial padrão em serviços e recursos globais na instalação. O uso de um banco SaaS compartilhado exige validação de autorização por identidade, recursos indiretos, filtros, agregações, caixa e tarefas.

## 3.6 · Reutilização, avisos e ambiente

Quatro falhas anteriores (`encerrarCaixaComComandaAbertaPedeConfirmacao`, `nomeDoClienteFicaJuntoDaComanda`, `pedidoDoCelularAbreAComandaESomaNaMesmaLinha` e `transferirParteDeUmItemEJuntarComandas`) reutilizam números derivados de uma sequência local à JVM. A preparação carrega usuários e deixa comandas e itens persistidos, então a segunda execução pode reocupar números. A execução controlada da classe `PdvBalcaoApiIntegrationTest` passou os 16 casos na primeira JVM e falhou somente nesses quatro na segunda JVM contra o mesmo banco descartável. O caso de importação NF-e passou nas duas execuções após a correção do proxy. A baseline oficial segue com PostgreSQL novo por suíte.

O único aviso javac envolve `ErroNegocioException.detalhes`. Não encontrei uso de serialização Java para a exceção. O getter alimenta o envelope JSON do advice e aceita `Map<String,Object>`, sem impor serialização binária dos valores. Alterar a forma do campo ou restringir os valores mudaria o contrato; o aviso foi preservado.

A estratégia `org.springframework.lang` da Fase 2 foi preservada. A triagem JDT de persistência continua concentrada em inferência de referências de método e conversões para parâmetros Spring não nulos. Nenhuma anotação global ou supressão foi introduzida.

O JDT em importação limpa, com Java 25 e análise de nulabilidade ativa, reportou **495 avisos, 12 tarefas e zero erros**, contra 496 avisos na Fase 2. A comparação por caminho, código e mensagem mostrou a remoção de uma suspeita de nulabilidade na unidade de destino e zero avisos novos. A captura está em `target/phase3-green-jdt/diagnostics.json`.

## Arquivos alterados na Fase 3

| Arquivo | Mudança |
| --- | --- |
| `CompraEstoqueRepository.java`, `JpaCompraEstoqueRepository.java` e `CompraEstoqueRepositoryAdapter.java` | Exposição e implementação da busca por chave primária com lock transacional para escrita. |
| `CompraEstoqueService.java` | Protege adição e confirmação no mesmo agregado, mantendo transações, permissões e códigos de erro. |
| `InMemoryRegistroIdempotenciaRepository.java` | Rejeita duplicidade em vez de descarte do registro original e alinha normalização com JPA. |
| `UnidadeMedida.java` | Corrige acesso ao destino lazy durante comparação. |
| `Fase0EstoqueDecimalApiIntegrationTest.java` | Três testes cobrindo corrida, rollback da reserva, contrato entre duas filiais e conversão via proxy real. |
| `PHASE_3_PERSISTENCE_DATA_INTEGRITY.md` | Registra evidências, limitações e parecer. |

## Validação

```powershell
mvn -B -ntp -Pbackend-baseline '-Dtest=app.**,controller.**,repository.**,service.**,*MigrationSql*Test' test
mvn -B -ntp -Pbackend-baseline '-Dtest=*IntegrationTest' test
mvn -B -ntp -Pbackend-baseline clean verify
```

A execução usou cache Maven local em modo offline e POM temporário com diretório de saída separado. O runner manteve as suítes independentes em PostgreSQL 18.3 novo, com loopback, SCRAM e senha aleatória. Somente o diagnóstico da preparação de fixtures compartilha um cluster comprovadamente descartável entre duas execuções. Os logs e XMLs permanecem em `target/phase3-*`. PostgreSQL 17 ficou indisponível porque só há binários 18 instalados e o daemon Docker retornou pipe ausente. A construção e inicialização da imagem ficam pendentes.

| Execução | Resultado | Tempo Maven |
| --- | --- | --- |
| Seleção sem PostgreSQL | 212 aprovados | 1 min 12 s |
| Seleção de integração | 83 aprovados, incluindo os três novos e as verificações existentes de autorização | 2 min 19 s |
| Compilação limpa, suíte completa e build | 295 aprovados, zero falhas, erros ou ignorados, com um aviso javac e JAR executável | 2 min 30 s |
| PDV em cluster novo, depois reutilizado | 16 de 16 na primeira execução. Na segunda, 12 de 16, com quatro falhas de fixture reproduzidas | 1 min 5 s e 44,269 s |

A inspeção confirmou que o JAR contém `Main.class` e exclui a configuração privada local. `git diff --check` passou. O teste vermelho de concorrência está em `target/phase3-red-integration-*/maven.log`. O teste vermelho de proxy, com a fixture válida, está em `target/phase3-proxy-red-integration-*/maven.log`.

## Parecer

**Recomendo aprovar a Fase 3 no escopo implementado.** A compilação limpa, o build e os 292 casos existentes passaram junto dos três casos novos. A confirmação concorrente e a conversão via proxy têm evidência vermelha e verde. O teste entre filiais demonstra independência do saldo e da idempotência nas portas cobertas. A suíte continua dependente de fixtures novas por execução, e PostgreSQL 17, imagem Docker e autorização em banco compartilhado continuam pendentes. A Fase 4 aguarda aprovação humana.
