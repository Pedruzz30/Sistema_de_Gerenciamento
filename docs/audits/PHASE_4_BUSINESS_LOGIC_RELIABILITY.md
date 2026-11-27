# StockOS — Fase 4 · Regras comerciais e confiabilidade do domínio

Baseline da Fase 3 em Java 25, PostgreSQL 18.3 descartável, 295 testes, 495 avisos JDT, um aviso javac e build aprovado. O Git foi verificado antes das mudanças. A fase preserva schema, formato das respostas HTTP e as correções anteriores de concorrência, idempotência e proxies JPA.

## 4.1 · Mapa do domínio

| Fluxo | Entradas e coordenação | Estados, invariantes e efeitos |
| --- | --- | --- |
| PDV e vendas | `VendaController` e records de venda, item, desconto, finalização e pagamento. `VendaService`, `VendaItemService`, `DescontoVendaService`, `FinalizacaoVendaService` e `CancelamentoVendaService`. | `Venda` nasce em rascunho e finaliza ou cancela. Venda finalizada pode ser cancelada parcialmente ou integralmente. Itens capturam preço, custo, variação e composição. Finalização reserva idempotência, bloqueia venda e estoques, grava consumos, pagamentos e caixa. Cancelamento grava estornos vinculados ao consumo original. |
| Comandas | `ComandaController`, records de lançamento, transferência e retirada, `ComandaService`, `Comanda` e venda vinculada. | Aberta → fechada ou cancelada. Abertura retoma somente a comanda ativa. Reutilização depois de fechada cria outro atendimento. Transferência bloqueia ambas as vendas em ordem de ID. Retirada exige a autorização financeira existente. |
| Estoque e unidades | `ProdutoController`, `MovimentoEstoqueController`, DTOs de movimentação e estorno, `EstoqueService`, `SincronizadorEstoque`, `MovimentoEstoqueService`, `UnidadeMedidaService`. | `EstoqueProduto` controla saldo, reserva e custo por filial e produto. `MovimentoEstoque` liga os saldos anterior e posterior à origem. Quantidades são decimais em seis casas. A política de bloqueio do negativo é condicional. Unidade exige dimensão compatível e mantém a correção de proxy da Fase 3. |
| Caixa | `CaixaController` e records de abertura, movimento, autorização e fechamento. `CaixaService`, `IntegracaoCaixaLegadoService`, `Caixa`, `MovimentacaoCaixa`, `FechamentoCaixa` e `VendaPagamento`. | Caixa aberto recebe vendas, sangrias, suprimentos e estornos. Fechamento registra diferença contado/sistema. Pagamentos devem coincidir com o total. Dinheiro integra o saldo da gaveta e é a única forma com troco. Valor de movimentação precisa ser positivo na escala monetária persistida. |
| Compras, fornecedores e cotações | `CompraEstoqueController`, `FornecedorController`, `CotacaoController` e respectivos records. `CompraEstoqueService`, `FornecedorService`, `CotacaoService`. | Compra rascunho aceita alterações, confirmação bloqueia o agregado e grava saldo, custo médio, movimento e idempotência. Fornecedor tem documento validado e estados ativo/inativo. Desativação mantém identidade e vínculos históricos. Cotações registram preço por produto/mês. |
| Catálogo e produção | `ProdutoRevendaController`, `CodigoVendaController`, controllers de cardápio, ficha, variação e modificadores. Services de catálogo, custo, disponibilidade e publicação. | Códigos resolvem variações; embalagem e ingrediente referenciam unidade base. Ativação e pausa influenciam disponibilidade. Publicação de ficha muda a versão utilizada em novas vendas; snapshots de vendas concluídas preservam o histórico. |
| Nota fiscal e NF-e | `NotaFiscalController`, `ImportacaoNfeController`, `PreviaRequest`, `ImportarRequest` e `DecisaoRequest`. `ImportacaoNfeService`, `NfeXmlParser`, `NotaFiscalService`. | Prévia não baixa estoque. Importação transacional valida decisões, impede duplicação por chave fiscal e cria/vincula produtos. A nota pendente confirma ou cancela conforme a máquina de estados do domínio. A confirmação lança entradas e vínculos de fornecedor. Parser proíbe DOCTYPE e entidades externas. |
| Operação e segurança | `SessaoFilter`, resoluções de usuário e serviços de autenticação, funcionários, logs, relatórios, rentabilidade, backup, cozinha e canal móvel. | Sessão, permissões e autorização financeira são pré-condições de operações sensíveis. Relatórios e backups são consumidores. Impressão e transmissão externa têm efeitos fora da transação do banco. |

As regras de troco, política de estoque negativo, transições de nota e bloqueios de venda decorrem dos contratos e validações atuais. A composição de totais de venda em quantidades fracionárias e a alocação de reembolsos sucessivos são comportamentos de código a confirmar antes de redefinir política. As operações que alteram caixa ou estoque exigem estorno ou ajuste auditado após efetivadas. A transferência disponível no PDV move itens entre comandas. A revisão não encontrou fluxo de transferência de estoque entre estabelecimentos.

## 4.2 · Vendas e comandas

Foram revisados a montagem de linhas, os snapshots, os estados de venda e comanda, a autorização para retirada e desconto, o uso de chaves de idempotência e a ordem dos locks. A suíte existente verifica pagamento incompatível seguido de correção, reversão de estoque, cancelamento repetido, transferência parcial e integral, retirada bloqueada sem autorização e reutilização legítima de comanda encerrada. A finalização grava venda, baixa, consumos e pagamentos antes do commit da mesma transação. O cenário de falha intermediária em `Fase2VendaApiIntegrationTest` verifica rollback de consumo. Não houve mudança nesses serviços.

## 4.3 · Defeitos de precisão corrigidos

| Gravidade e evidência | Correção e efeito |
| --- | --- |
| Média · zero monetário indevido. A sangria de `0.004` passava pela validação de valor bruto. Em memória, a operação prosseguia. Na API, o teste vermelho recebeu 500 e o PostgreSQL apontou SQLSTATE `23514` em `ck_movimentacoes_caixa_valor`. | `MovimentacaoCaixa` usa a mesma escala de duas casas e `HALF_UP`, e valida a positividade antes de registrar o valor arredondado. Sangria e suprimento usam o tratamento 400 e o envelope que o controller já prevê para valor inválido. O teste exige saldo e número de movimentos inalterados para `0.004`, e lançamento de `0.01` para `0.005`. |
| Média · movimento sem quantidade efetiva. O teste vermelho mostrou que `EstoqueProduto` aceitava entrada abaixo de seis casas decimais. Na ramificação de saldo zero, o cálculo atribuía o novo custo apesar de a quantidade normalizada ser zero. `MovimentoEstoque` também aceitava quantidade bruta positiva antes de armazenar zero. | Entrada e saída validam a quantidade normalizada antes de mutar saldo, custo ou timestamp. O registro decimal também rejeita quantidade que não satisfaça a restrição positiva do banco. `0.0000004` é inválido e `0.0000005` continua arredondando para `0.000001`. A política condicional de estoque negativo permanece igual. |

## 4.4 e 4.5 · Revisões sem mudança de regra

- **Fornecedores e compras.** `FornecedorController` resolve identidade e delega ao serviço. `FornecedorService` controla permissões, CNPJ duplicado, ativação e auditoria. O adapter e o InMemory normalizam busca por CNPJ e ordenam por ID. O InMemory também protege duplicação no armazenamento, assim como a restrição de unicidade no PostgreSQL. A desativação atualiza o estado e preserva as referências de compras e notas. O enum de compra inclui `CANCELADA`, mas nenhum novo fluxo de cancelamento de compras foi introduzido. Mantive o lock da confirmação da Fase 3.
- **NF-e.** A previsão e as decisões de importar validam fornecedor, documentos, vínculo, valores e quantidade. O recebimento é transacional. A suíte existente verifica rollback após erro, reconhecimento de produto em nota posterior e proteção de operação financeira. As validações de XML e a correção de unidade lazy permanecem. Não foi feito teste de carga do parser nem revisão de segurança além do escopo atual.
- **Catálogo e histórico.** O serviço de finalização revalida o rascunho contra o estado oficial. Produto, variação, composição, preços, embalagem e ficha continuam usando os relacionamentos e snapshots existentes. O encaixe entre preços históricos e preço atual em transferência parcial merece decisão de produto antes de mudar.
- **HTTP.** Os records de entrada e validações manuais seguem vigentes. JSON malformado pode alcançar o fallback genérico do advice. Uma nova política de status e códigos de erro precisa de aprovação pelo efeito no contrato. O único aviso javac envolve `Map<String,Object>` em `ErroNegocioException`. O mapa alimenta o JSON do conselho de exceções e não foi localizado uso de serialização Java. Preservado sem supressão.

### Ambiguidades e riscos pendentes

| Risco | Evidência e decisão necessária |
| --- | --- |
| Reembolsos parciais sucessivos em pagamento misto | `IntegracaoCaixaLegadoService.alocarEstorno` toma os pagamentos originais como limite por chamada. A inspeção não encontrou abatimento dos estornos anteriores nessa alocação. É possível repetir a preferência eletrônica entre etapas, e o código atual não registra movimento eletrônico se o caixa estiver encerrado. Definir saldo reembolsável por meio, fonte de histórico e tratamento de operações já conciliadas antes de alterar a regra. |
| Total de venda de linhas fracionárias | `FinalizacaoVendaService` arredonda base, desconto e acréscimo na agregação, enquanto `VendaItem` arredonda o subtotal da linha. O caixa normaliza para duas casas mais adiante. Confirmar se a base oficial deve ser a soma das linhas ou o total recomposto e como distribuir diferenças. Sem mudança nos cálculos nesta fase. |
| Vendas gratuitas e pagamentos de valor inferior à unidade monetária | O banco exige valor de pagamento positivo. O serviço tem fallback em dinheiro quando o cliente antigo omite pagamentos. A política para venda de total zero e para linhas de pagamento que arredondam a zero precisa de decisão comercial antes de mudar o contrato. |
| Concorrência de caixa e comanda | A compra e as vendas têm evidência de proteção em cenários específicos. Esta fase não demonstra serialização de todos os pares abertura, fechamento, suprimento e cancelamento entre múltiplas instâncias. Impressão externa também exige política própria de reentrega. |
| Autorização por estabelecimento | O contrato de duas filiais da Fase 3 verifica saldo e idempotência por escopo no repositório. Não há contexto de identidade por estabelecimento para provar acesso ponta a ponta em banco compartilhado. A fase não altera o filtro de sessão, autorização, chaves da filial nem o modelo de dados. |

## 4.6 · Fixtures e validação

As quatro falhas de reutilização do PDV vinham de números calculados a partir de um contador reiniciado na JVM, enquanto as comandas permaneciam na base. Em `PdvBalcaoApiIntegrationTest`, a preparação passou a consultar a faixa de números já usada antes do cenário. A transferência reserva dois números contíguos livres; o teste de reabertura continua reutilizando deliberadamente a mesma comanda dentro do cenário. A preparação não apaga nenhum registro e não muda assertions. O experimento reutiliza o banco somente de forma controlada, em duas execuções sequenciais da mesma classe. A baseline permanece com banco novo por suíte.

Arquivos de produção desta fase são `MovimentacaoCaixa.java`, `EstoqueProduto.java` e `MovimentoEstoque.java`. Os testes adicionados ficam em `CaixaServiceTest.java`, `CompraEstoqueServiceTest.java` e `PdvBalcaoApiIntegrationTest.java`, este último também com o ajuste das fixtures.

### Diagnósticos estáticos

O JDT em Java 25 com análise de nulabilidade ativa registrou **495 avisos, 12 tarefas e zero erros**. A comparação por arquivo, código e mensagem com a Fase 3 mostrou zero avisos novos. Nos arquivos alterados, os diagnósticos remanescentes se agrupam em uma perda de informação de nulabilidade após validação delegada em `MovimentacaoCaixa`, cinco inferências em descritores funcionais nos testes de caixa e compras e duas conversões de nulabilidade nos helpers do teste de PDV. `EstoqueProduto` e `MovimentoEstoque` ficaram sem avisos. A estratégia segue com guards explícitos onde há benefício de domínio e anotações Spring para contratos reais. Não foram usadas supressões nem trocas cosméticas de referências de método.

### Execuções

```powershell
mvn -B -ntp -Pbackend-baseline '-Dtest=app.**,controller.**,repository.**,service.**,*MigrationSql*Test' test
mvn -B -ntp -Pbackend-baseline '-Dtest=*IntegrationTest' test
mvn -B -ntp -Pbackend-baseline clean verify
```

As execuções usaram cache Maven local offline e POM temporário que redireciona o diretório de build para preservar evidências. Os clusters PostgreSQL 18.3 são descartáveis, com loopback, SCRAM e senha exclusiva; a seleção de integração e a suíte completa usam bancos distintos. O teste de reutilização usa seu próprio cluster separado para exatamente duas execuções da classe PDV. Os logs e relatórios Surefire estão em `target/phase4-*` e os avisos do JDT em `target/phase4-jdt/diagnostics.json`.

PostgreSQL 17 e Docker ficaram pendentes. A máquina dispõe de PostgreSQL 18 e o CLI Docker retornou pipe do daemon ausente. Nenhuma infraestrutura de produção foi usada ou alterada.

| Execução | Resultado | Tempo Maven |
| --- | --- | --- |
| Seleção sem PostgreSQL | 214 aprovados, incluindo as duas regressões unitárias | 1 min 12 s |
| Integração em PostgreSQL 18.3 novo | 84 aprovados, incluindo o teste HTTP do caixa e as verificações de concorrência de compra e escopo de duas filiais da Fase 3 | 2 min 24 s |
| Classe PDV em duas JVMs contra o mesmo PostgreSQL descartável | 17 aprovados na primeira e 17 na segunda, sem as quatro falhas anteriores | 1 min 4 s e 44,207 s |
| Compilação limpa, suíte completa e build | 298 aprovados, zero falhas, erros ou ignorados, um aviso javac e JAR executável | 2 min 37 s |

Nos cenários cobertos, os rejeitados deixam saldo, custo e movimentações intactos. O limite positivo continua sendo arredondado e registrado. A suíte também cobre cancelamento e estorno no estoque, validação do total pago, separação entre dinheiro e eletrônico, rollback de erros compostos e autorização de gerente.

O JAR contém `Main.class` e preserva a exclusão de `application-local.properties`. `git diff --check` passou. As instâncias descartáveis foram encerradas.

## Parecer técnico

**Recomendo aprovar a Fase 4 e autorizar a Fase 5 em escopo próprio.** Os 295 testes anteriores e três regressões novas passaram com build completo. As quatro colisões de fixtures deixaram de ocorrer na repetição controlada. O comportamento financeiro e de estoque foi corrigido no limite da precisão já adotada. A política de estornos parciais mistos, a base oficial para arredondamentos fracionários e a autorização entre estabelecimentos em banco compartilhado exigem decisões próprias. A paridade de PostgreSQL 17 e a imagem Docker permanecem pendentes. A Fase 5 aguarda aprovação humana.
