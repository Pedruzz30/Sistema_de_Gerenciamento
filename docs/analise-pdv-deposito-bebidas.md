# Análise do Caixa PDV do StockOS para um depósito de bebidas

Data da análise: 04/11/2026. Base: branch `main`, commit `0a3f04b`, e o print da tela `caixas.html` no Netlify.

Contexto: um depósito de bebidas que também vende petiscos e caldos, com bastante movimento, hoje tudo anotado em caderno. A ideia é rodar o sistema num notebook no balcão, como gerenciador de estoque e caixa PDV.

### Respostas da cliente (04/11/2026)

| Pergunta | Resposta | O que muda no projeto |
|---|---|---|
| Vende fiado? | **Não** | Sai do roteiro: sem conta de cliente e sem contas a receber |
| Casco retornável? | **Não** | Sai do roteiro |
| Gelada tem preço diferente? | **Sim** | Entra na **Fase 1**: um produto, dois preços, mesmo estoque (seção 5.2) |
| Tem mesas? | Sim, mas **não há cobrança por mesa** | Sem comandas: toda venda é de **balcão**, paga na hora |
| Regime tributário | **Não sabem**; os donos fazem a própria contabilidade | Descobrir antes da parte fiscal (seção 8.1) |
| Quem opera o caixa | 3 pessoas na loja; **1 ou 2 no caixa ao mesmo tempo** | Troca rápida de operador por PIN (seção 5.3) |

---

## 1. Veredito

O **backend de vendas está bem feito**: dinheiro em `BigDecimal`, estoque decimal por filial, trava pessimista no estoque, idempotência na finalização, cancelamento com estorno e trilha de auditoria. Muito sistema comercial pequeno não tem isso.

O problema é que **o PDV de hoje é de restaurante/pizzaria, não de mercearia**. Ele funciona com um cardápio de cards clicáveis, variações e modificadores. Um caixa de supermercado precisa de outra coisa: **bipar o código de barras, receber o pagamento, dar o troco e imprimir o cupom**, tudo pelo teclado e em poucos segundos.

**Ainda não dá para usar o sistema na loja.** Faltam 6 coisas que impedem a operação:

| # | O que falta | Por que impede |
|---|---|---|
| 1 | Forma de pagamento (dinheiro, Pix, débito, crédito) | O sistema trata **toda venda como dinheiro na gaveta**, então o fechamento sempre diverge |
| 2 | Leitura por código de barras no PDV | Sem isso a fila trava em dia de movimento |
| 3 | Valor recebido e troco | Função básica de qualquer caixa |
| 4 | Comprovante / cupom impresso | O cliente espera receber, e é a base da NFC-e |
| 5 | NFC-e (nota fiscal do consumidor) | Obrigatória no varejo na maioria dos estados (ver seção 8) |
| 6 | Autenticação de verdade | Hoje é o cabeçalho `X-User-RU` com senha em texto puro (o próprio README avisa) |

---

## 2. O que já está bom (dá para aproveitar)

| Ponto forte | Onde está |
|---|---|
| Estados do caixa: FECHADO → ABERTO → ENCERRADO | `src/main/java/model/Caixa.java:66` |
| Abertura com fundo de troco; sangria e suprimento separados | `CaixaService.java:57-114` |
| Fechamento com valor contado × valor do sistema e divergência registrada | `CaixaService.java:162-199`, `FechamentoCaixa` |
| Venda em rascunho → itens → finalizar (API `/api/vendas`) | `VendaController.java:59-200` |
| Idempotência: o mesmo clique duplo não gera duas vendas | `FinalizarVendaRequest(idempotencyKey…)`, `RegistroIdempotencia` |
| Trava pessimista ao baixar o estoque (dois caixas não vendem a mesma última unidade) | `JpaEstoqueProdutoRepository.java:30`, `FinalizacaoVendaService.java:362` |
| Política de falta por produto: BLOQUEAR / ALERTAR / IGNORAR | `model/PoliticaFalta.java` |
| Estoque decimal (`numeric(19,6)`) e custo médio por filial | `model/EstoqueProduto.java:49-56` |
| **Embalagens de produto** (fardo de 12 = 12 unidades) já com campo `codigo_barras` | `model/EmbalagemProduto.java` |
| Ficha técnica: serve direitinho para o **caldo** (baixa ingredientes ao vender um copo) | `FichaTecnica*`, `VendaItemConsumo` |
| Cancelamento total ou por item com motivo | `CancelamentoVendaService.java` |
| Logs de auditoria de toda movimentação | `LogAcao` |
| Migrações Flyway e testes de integração | `src/main/resources/db/migration`, `src/test` |

Esta base é o motivo de valer a pena **adaptar o PDV**, em vez de reescrever.

---

## 3. Problemas encontrados no código (do mais grave ao menos grave)

### 3.1 Toda venda entra como dinheiro na gaveta (crítico)
`Caixa.registrarMovimentacao` (`Caixa.java:229`) soma **toda** entrada ao `saldoAtual`. A finalização (`FinalizarVendaRequest`, `VendaController.java:318`) não pede forma de pagamento.

**Consequência real:** com Saldo inicial R$ 300 e R$ 2.439 vendidos, o sistema espera **R$ 2.739 em espécie** na gaveta, que é exatamente o que aparece no seu print. Num depósito, talvez 70% disso seja Pix ou cartão. No fechamento a operadora conta R$ 900 e o sistema acusa **"falta" de R$ 1.839**. O fechamento perde o sentido.

### 3.2 Cancelamento de venda é lançado como SANGRIA
`IntegracaoCaixaLegadoService.java:43` registra o estorno como `SANGRIA`. Isso gera dois problemas:
- o relatório de sangrias fica errado (sangria é retirada de dinheiro para o cofre, não estorno);
- `Caixa.registrarMovimentacao` **recusa saídas maiores que o saldo**. Então cancelar uma venda de R$ 200 paga no Pix, com R$ 150 na gaveta, **falha**.

O cancelamento precisa de um tipo próprio (`ESTORNO_VENDA`), ligado à forma de pagamento original.

### 3.3 Não existe busca por código de barras
O campo `codigo_barras` existe só em `embalagens_produto` (migração `V20260713_01`), **sem índice único e sem endpoint de busca**. O PDV (`pdv.js:139-160`) filtra o cardápio por **nome**, e o operador clica num card. No caixa de mercearia isso é lento demais.

### 3.4 Dois caminhos de venda em paralelo
- `/api/caixas/venda` (legado) aceita **venda só com valor, sem itens** (`CaixaService.java:298-311`). Isso gera dinheiro no caixa sem baixa de estoque, e é por aí que somem as bebidas.
- `/api/vendas` (novo, decimal, com trava). É o caminho certo.

**Recomendação:** o PDV usa só `/api/vendas`. A "venda avulsa sem item" fica restrita ao gerente, ou vira um produto genérico ("Diversos") que exige permissão.

### 3.5 Fechamento não é "cego"
`caixas.js:282` preenche o campo **valor contado** com o valor do sistema. A operadora só clica em confirmar. Em PDV profissional o operador digita o que contou **sem ver** o esperado (fechamento cego), e a conferência é por forma de pagamento.

### 3.6 Permissões do perfil CAIXA
Pelo `ClasseFuncionario.java:28`, a classe CAIXA tem só `VER_VENDAS` e `VER_ESTOQUE`. Sangria, suprimento e encerramento exigem `VER_FINANCAS` (`CaixaService.java:101-155`). Na loja, 3 pessoas se revezam no caixa. O padrão de mercado é: qualquer operador vende, e sangria, cancelamento, desconto e fechamento pedem o **PIN de um dono/gerente** na hora, sem precisar trocar o usuário logado.

**Senhas padrão:** o README traz `Admin / Superior / 1234` e os outros usuários com `1234`. Troquem todas antes de usar na loja.

### 3.7 Gerador de ID de movimentação em memória
`CaixaService.java:47` cria um `AtomicLong` lendo **todas as movimentações de todos os caixas** na inicialização (com `FetchType.EAGER`). Isso funciona, mas a inicialização fica lenta com meses de histórico e quebra se houver duas instâncias. Troquem por `@GeneratedValue` ou por uma sequence do Postgres.

### 3.8 O modal do PDV fecha ao clicar fora
`caixas.html:205` usa `fecharModalSeClicouFora`. No balcão, um clique errado fecha o terminal no meio da venda. O rascunho fica salvo no servidor, mas a operadora se perde. O PDV deve ser uma **tela própria** (rota `/pdv/{caixa}`), não um modal.

### 3.9 Detalhes de visual no print
- Moeda em formato americano: `R$7283.00` (`caixas.js:167-205` usa `toFixed(2)`). O certo é `R$ 7.283,00`, e já existe `formatarMoedaPDV` com `Intl.NumberFormat('pt-BR')` no `pdv.js`.
- "Abertura 09:00" sem a data. Um caixa esquecido aberto de ontem parece ser de hoje.
- O selo flutuante "Visual novo · Ver original" cobre o botão **+ Abrir Caixa**.
- "FORNO · V2" no logo é resto do tema pizzaria.
- O card mostra "Total Vendas", mas não mostra **quantas vendas** nem o **ticket médio**.


### 3.10 Teste prático no site publicado (04/11/2026, logado como Admin Demo)
**Atenção:** o PDV publicado no Netlify é uma **versão simplificada de demonstração** (`preview-pdv.js`). Ele não é o PDV real do repositório (`pdv.js`, com grade de cardápio). O que foi testado no Caixa 1:

| Teste | Resultado | Avaliação |
|---|---|---|
| Escolher o produto | Um **menu suspenso com ~150 produtos** em ordem fixa (arroz, farinha…) | ❌ Inviável no balcão: procurar uma lata na lista leva vários segundos |
| Vender 5 de um item com 2 em estoque | Bloqueou com "Estoque insuficiente" | ✅ Regra certa, mas a mensagem é pequena, sem destaque e sem som |
| Adicionar o mesmo produto duas vezes (6 + 1) | Juntou numa linha: "7 × Refrigerante" | ✅ Bom |
| Remover item / mudar quantidade no carrinho | **Não existe** botão | ❌ Errou, tem que fechar e começar de novo |
| Forma de pagamento / troco | **Não pergunta** | ❌ Confirma o problema 3.1 |
| Registrar a venda de R$ 50 | Saldo da gaveta foi de R$ 2.739 para **R$ 2.789** | ❌ Tudo entra como dinheiro em espécie |
| Baixa de estoque | Refrigerante de 72 para **65** | ✅ Correto |
| Depois de registrar | O carrinho **continua na tela** com o botão desativado; precisa fechar e reabrir para a próxima venda | ⚠ Lento para fila: deveria limpar e voltar ao campo de leitura na hora |
| "Ver movs" | Lista "VENDA · Venda fictícia do PDV · +R$ 50,00 · 22:19" | ⚠ Não mostra os itens, a forma de pagamento nem quem vendeu, e não permite cancelar dali |
| Encerrar caixa | Mostra "Valor no sistema R$ 2789.00" e **já preenche "Valor contado" com 2789.00** | ❌ Confirma o problema 3.5: basta clicar em Encerrar sem contar |
| Teclado | Tudo exige mouse (escolher no menu, clicar Adicionar, clicar Registrar) | ❌ |
| Formato de moeda | `R$ 6.00`, `R$2789.00` em vários lugares | ❌ Usar `R$ 6,00` / `R$ 2.789,00` |
| Tela estreita (notebook ~1400 px) | Os botões do card quebram linha ("Encerrar" desce sozinho) | ⚠ Ajustar o layout para notebook de 14" |

**Conclusão do teste:** a lógica de estoque funciona, mas a experiência do operador ainda é de "cadastro de venda", não de "frente de caixa". Isso reforça os itens 1, 2, 4 e 7 da Fase 1.


### 3.11 Demo "Central Bebidas 2M" (PR #38, já na `main`)
A branch `feat/demo-deposito-bebidas-2m-20261104` (`preview-source/js/preview-beverages.js`) mexe **só nos dados de demonstração** do site. Não altera backend, banco nem o PDV real.

| Item | Demo | Loja real da cliente |
|---|---|---|
| Faturamento | R$ 2.000.000 em 30 dias (~R$ 64 mil/dia) | Bem menor: 3 pessoas na loja |
| Vendas | 12.000 no mês, **1 item por venda**, ticket R$ 166,67 | Muitas vendas pequenas e mistas (latas + gelo + caldo) |
| Caixas | 8 abertos | 1, às vezes 2 |
| Funcionários | 80 | 3 |
| Produtos | 1.600 (25 bebidas × 16 embalagens × 4 marcas) | Algumas centenas, com petiscos e caldos |

**O que tem de bom:** as vendas da demo guardam a forma de pagamento (51% Pix, 37% cartão, 12% dinheiro), e o volume serve para testar desempenho.

**Onde contradiz a análise:**
- **Saldo da gaveta:** o saldo continua somando tudo (`saldoAtual = vendas + 500`), mesmo a venda sabendo a forma de pagamento. Com 12% em dinheiro, a gaveta da Caixa 1 deveria ter ~R$ 1.400, não R$ 8.285 (problema 3.1).
- **Embalagens:** cada embalagem é um produto separado, com estoque próprio. Vender um fardo não baixa as 12 latas, enquanto o backend tem o modelo certo (`EmbalagemProduto`).
- **Itens da cliente:** sem código de barras, sem preço gelado, sem caldos ou petiscos.
- **Fora do escopo:** inclui "retornável" e "barril", que a cliente não trabalha.
- **PDV:** o menu suspenso do PDV de demonstração passaria a ter 1.600 produtos.
- **Dashboard:** mostra "Nenhuma movimentação hoje" com R$ 64 mil vendidos no dia. O logo muda entre "GESTÃO · ESTOQUE" (Dashboard) e "FORNO · V2" (outras páginas).

**Sugestão:** manter essa demo para escala e desempenho, e criar uma demo **do tamanho real da cliente** para apresentar a ela:
- 1-2 caixas;
- ~300 produtos com EAN, fardo como embalagem do mesmo produto e preço gelado;
- caldos e petiscos;
- vendas com vários itens;
- gaveta somando só o dinheiro.


### 3.12 Dois saldos de estoque que não se conversam (crítico, ver seção 11)
O sistema tem **dois saldos para o mesmo produto**:
- `produtos.quantidade_atual` (inteiro, legado);
- `estoque_produtos.quantidade_atual` (decimal, por filial).

Telas diferentes mexem em saldos diferentes, então **o estoque que aparece na tela não é o que o PDV baixa**. Detalhes na seção 11.1.

---

## 4. Como funciona um PDV profissional (supermercado/mercearia)

O fluxo padrão no Brasil, usado por sistemas de frente de caixa de varejo:

1. **Abertura do caixa:** login do operador e fundo de troco (o StockOS já tem).
2. **Registro de itens:**
   - O leitor de código de barras **funciona como um teclado**: "digita" o EAN e aperta Enter. O campo de leitura fica **sempre com o foco**.
   - Multiplicador: digita `6*` e bipa, e entram 6 unidades.
   - Produto sem código: busca por nome (F2) ou por um **código interno curto** (ex.: `101` = Caldo de feijão).
   - Produto pesado (se um dia vender por kg): etiqueta de balança com prefixo `2`, PLU e preço ou peso embutidos.
   - A lista mostra o **último item em destaque**, com nome, quantidade, preço e total em fonte grande.
3. **Correções:** cancelar item (F6) e cancelar cupom inteiro. Normalmente pede senha de supervisor.
4. **Subtotal (F10/F12):** desconto (com limite por perfil) e CPF na nota.
5. **Pagamento:**
   - uma ou **várias formas** (ex.: R$ 50 em dinheiro + resto no Pix);
   - dinheiro: digita o valor recebido e o sistema mostra o **troco** em destaque;
   - cartão ou Pix: na versão simples, a operadora passa na maquininha e marca no sistema; na versão integrada (TEF / Smart POS / Pix dinâmico), o valor vai automático para a maquininha e volta aprovado;
   - (fiado: comum em depósito de bairro, mas a cliente não trabalha com fiado).
6. **Finalização:** baixa de estoque, emissão de NFC-e, impressão do cupom e abertura da gaveta. Tudo numa transação: ou dá tudo certo, ou nada é gravado.
7. **Durante o turno:** sangrias quando a gaveta passa de um limite (ex.: acima de R$ 500 o sistema avisa) e suprimentos.
8. **Fechamento:** contagem **cega** por forma de pagamento, relatório "Redução/Leitura X" com vendas, cancelamentos, descontos, sangrias e diferenças por forma. O dinheiro é conferido na contagem; cartão e Pix são conferidos com o extrato da maquininha (conciliação).

---

## 5. Adaptando à realidade do depósito

### 5.1 Cadastro de produtos e códigos de barras
Os produtos de bebida já vêm com **EAN-13** de fábrica (cerveja, refrigerante, água, destilados). Na prática:

- **Lata e fardo têm EANs diferentes.** A lata tem um código e o fardo de 12 tem outro (DUN-14 ou EAN próprio). O modelo `EmbalagemProduto` já resolve isso: produto "Skol lata 350ml" (unidade base) com as embalagens "Unidade" (EAN da lata, 1 un) e "Fardo 12" (EAN do fardo, 12 un). **Bipar o fardo baixa 12 unidades.**
- **Gelada × natural:** confirmado pela cliente, ver 5.2.
- **Petiscos industrializados** (amendoim, salgadinho) também têm EAN.
- **Caldos e porções feitos na casa não têm código.** Ganham código interno curto (`101`, `102`…) e/ou botão de atalho na tela. Vendidos como item de cardápio com ficha técnica, baixam os ingredientes. Isso o sistema já sabe fazer.
- **Gelo, carvão e itens soltos** levam código interno.
- **Cadastro rápido:** na entrada da nota do fornecedor, bipa o produto e o sistema vincula o EAN. O cadastro do estoque precisa de um campo "bipe aqui" que preenche o código.

**Proposta de modelo:**
```sql
create table produto_codigos (
  id            bigserial primary key,
  produto_id    int    not null references produtos(id),
  embalagem_id  bigint     null references embalagens_produto(id),
  codigo        varchar(32) not null,
  tipo          varchar(12) not null, -- EAN13, EAN8, DUN14, INTERNO
  constraint uk_produto_codigos_codigo unique (codigo)
);
```
Endpoint: `GET /api/pdv/codigo/{codigo}` devolve produto, embalagem, quantidade base, preço e estoque. A resposta precisa ser instantânea (índice único e, no front, um cache do catálogo carregado na abertura do caixa).

Algoritmo de leitura no front:
```
ao receber Enter no campo de leitura:
  texto = "6*7891149103102"  -> qtd = 6, codigo = "7891149103102"
  se codigo começa com "2" e tem 13 dígitos e a balança estiver configurada -> decodificar PLU + preço/peso
  senão -> buscar no cache local pelo código; se não achar, buscar na API
  achou -> adicionar item (ou somar quantidade se for o mesmo item, conforme configuração) + bip curto de sucesso
  não achou -> som de erro + abrir "produto não cadastrado" (gerente pode cadastrar na hora)
  devolver o foco ao campo de leitura
```

### 5.2 Gelada × natural (confirmado: preço diferente)
A lata é a mesma: mesmo EAN e **mesmo estoque**. Só o preço muda, conforme ela sai da geladeira ou da prateleira.

- **Cadastro:** o produto ganha `preco_venda` (natural) e `preco_gelado` (opcional). Se `preco_gelado` estiver vazio, o produto não tem essa opção (ex.: salgadinho).
- **No PDV:** uma configuração "temperatura padrão" do caixa. Num depósito, quase tudo sai gelado, então o padrão deve ser **Gelada**. A tecla **F4** alterna o próximo item (ou o item selecionado) para Natural.
  - A lista mostra um selo `GELADA` / `NATURAL` em cada linha, para a operadora conferir.
  - Quem bipa um fardo fechado normalmente leva natural. Dá para configurar a temperatura padrão **por embalagem** (unidade = gelada, fardo = natural).
- **No estoque:** a baixa é igual nos dois casos (1 lata = 1 lata). Opcionalmente, um "estoque na geladeira" para avisar quando repor. Fica para depois.
- **No relatório:** quanto foi vendido gelado e quanto natural, e quanto a mais isso rendeu.
- **Na NFC-e:** o mesmo produto e o mesmo código, só com preço unitário diferente. A descrição pode levar o sufixo " GELADA".

```sql
alter table produtos add column preco_gelado numeric(19,2);
alter table embalagens_produto add column temperatura_padrao varchar(10); -- GELADA | NATURAL | null
alter table venda_itens add column temperatura varchar(10);              -- registra o que foi cobrado
```

### 5.3 1 ou 2 operadores no mesmo caixa
Na loja ficam 3 pessoas, e 1 ou 2 operam o caixa ao mesmo tempo. Há dois cenários:

**Cenário A: um notebook e uma gaveta, operadores se revezando (o mais provável)**
- Uma sessão de caixa por dia/turno, aberta por quem é responsável pela gaveta.
- **Troca rápida de operador:** tecla F12 e um PIN de 4 dígitos, sem sair da tela. Cada venda grava **quem passou**. O `Venda.criadaPor` e o `finalizadaPor` já existem, só falta o PDV enviar o operador do momento.
- O relatório mostra as vendas por operador, mas a **conferência da gaveta é uma só**, porque o dinheiro é um só.
- Se quiserem separar a responsabilidade por turno, o "fechamento parcial / troca de turno" conta a gaveta e passa para o próximo.

**Cenário B: dois notebooks e duas gavetas (dia de jogo, sexta à noite)**
- O sistema já suporta isso: Caixa 1 e Caixa 2, cada um com sua sessão e seu fundo de troco.
- A trava de estoque que já existe garante que os dois não vendem a mesma última unidade.
- É preciso que os dois notebooks acessem **o mesmo banco**: um deles vira "servidor" na rede local da loja, ou o banco fica na nuvem.

**Recomendação:** comecem pelo Cenário A, que é o mais barato e funciona com um notebook, e deixem o B possível.

Hoje o `CaixaService` não confere se quem registra a venda é o `operadorAtual` do caixa. Isso é bom para o revezamento, mas o PDV precisa deixar claro **quem está operando agora** (nome grande no topo).

### 5.4 Fora do escopo (confirmado pela cliente)
- **Fiado:** não vende. Nada de conta de cliente.
- **Casco retornável:** não trabalha.
- **Comandas/mesas:** as mesas existem, mas não há cobrança por mesa. Toda venda é de balcão, paga na hora (canal `BALCAO`). A tela `saas-comandas` não precisa entrar na Fase 1.

### 5.5 Delivery por WhatsApp
Ainda não perguntado. Se existir, fica para a Fase 3: venda com canal `DELIVERY_PROPRIO`, endereço e taxa de entrega.

## 6. Como deve ser a venda, o cálculo e a baixa no estoque

### 6.1 Ciclo de vida
```
[abrir venda RASCUNHO] -> bipar/adicionar itens -> [subtotal]
   -> desconto (opcional, com permissão) -> pagamentos (1..N)
   -> soma dos pagamentos >= total ?  -> FINALIZAR (transação única)
        1. trava estoque dos produtos (já existe)
        2. baixa estoque (unidade base: fardo = 12 un) / ficha técnica do caldo
        3. grava pagamentos (forma, valor, recebido, troco, NSU da maquininha)
        4. lança no caixa SÓ o que é dinheiro na gaveta (entrada = valor em dinheiro - troco)
        5. gera cupom / envia NFC-e
        6. log de auditoria
```

### 6.2 Cálculo (regras para o código)
- `subtotal_item = preco_unitario × quantidade`, arredondado a 2 casas, `HALF_UP`.
- `subtotal_venda = Σ subtotal_item`.
- Desconto em R$ ou %: guardar o valor final em R$ e **ratear entre os itens**. A NFC-e exige desconto por item, e o rateio também ajuda no cálculo da margem.
- `total = subtotal - desconto + acrescimo` (`Venda` já tem esses campos).
- Pagamentos: `Σ pagamentos ≥ total`. Só **dinheiro** pode passar do total; o excedente é o troco. `troco = Σ pagamentos - total` (sai sempre do dinheiro).
- Margem: `Venda.custoIngredientesTotal` e `margemBrutaEstimadaTotal` já existem. Para bebida, o custo vem do `custo_medio` do `EstoqueProduto`.

### 6.3 Nova tabela de pagamentos
```sql
create table venda_pagamentos (
  id               bigserial primary key,
  venda_id         bigint not null references vendas(id),
  forma            varchar(20) not null, -- DINHEIRO, PIX, DEBITO, CREDITO, VOUCHER
  valor            numeric(19,2) not null,  -- quanto abate do total
  valor_recebido   numeric(19,2),           -- só dinheiro
  troco            numeric(19,2),           -- só dinheiro
  nsu              varchar(40),             -- comprovante da maquininha
  autorizacao      varchar(40),
  operador_id      bigint                   -- quem recebeu (revezamento de operadores)
);
```
E a movimentação do caixa ganha a coluna `forma_pagamento`. O **saldo da gaveta** passa a ser `fundo inicial + vendas em dinheiro - troco + suprimentos - sangrias - estornos em dinheiro`. Os totais de Pix e cartão aparecem separados no fechamento.

### 6.4 Fechamento por forma de pagamento
| Forma | Sistema | Contado/Conferido | Diferença |
|---|---|---|---|
| Dinheiro | 412,00 | (digitado às cegas) | |
| Pix | 1.230,00 | (extrato do banco) | |
| Débito | 560,00 | (relatório da maquininha) | |
| Crédito | 237,00 | (relatório da maquininha) | |

---

## 7. Hardware e infraestrutura para o notebook

| Item | Recomendação | Observação |
|---|---|---|
| Notebook | i3/Ryzen 3, 8 GB RAM, SSD | Rode Postgres + Spring **local**, ver abaixo |
| Leitor de código de barras | USB, modo teclado (HID), 1D/2D | O sistema não precisa de driver; o 2D também lê o QR do Pix |
| Impressora térmica | 80 mm, USB, ESC/POS (Elgin i9, Epson TM-T20, Bematech MP-4200…) | Para imprimir sem a caixa de diálogo do navegador, use um agente local (ex.: QZ Tray) ou Chrome em modo `--kiosk-printing` |
| Gaveta de dinheiro | Ligada na impressora (RJ11) | A impressora abre a gaveta com um comando ESC/POS ao finalizar em dinheiro |
| Maquininha | Smart POS ou TEF de alguma adquirente | Fase 1: lançamento manual da forma. Fase 3: integração |
| Nobreak | Pequeno | Queda de luz no meio da venda corrompe a operação |
| Internet | Qualquer, com 4G de reserva no celular | Necessária para NFC-e e backup, **não** para vender |

**Onde rodar:** a recomendação é rodar **no próprio notebook** (pacote sem Docker, seção 17), com **backup automático para pen drive ou nuvem**. Se a internet cair, o caixa continua vendendo, e a NFC-e entra em contingência offline e é transmitida depois. Se rodar só na nuvem (Netlify + backend remoto), **sem internet não há venda**, e isso num depósito em dia de jogo é inaceitável.

---

## 8. Parte fiscal (NFC-e)

### 8.1 Primeiro passo: descobrir o regime (os donos fazem a própria contabilidade)
A obrigação de emitir nota depende do enquadramento do CNPJ:

1. Peguem o CNPJ da loja e consultem no **Portal do Simples Nacional → "Consulta Optantes"** (site da Receita Federal). O resultado mostra se a empresa é **SIMEI (MEI)**, **Simples Nacional (ME/EPP)** ou nenhum dos dois (Lucro Presumido/Real).
2. O que cada resultado significa para o PDV:

| Regime | Precisa emitir NFC-e para consumidor pessoa física? | O que fazer no sistema |
|---|---|---|
| **MEI** (até R$ 81 mil/ano, ~R$ 6.750/mês) | **Não**, em regra (dispensa vigente em 2026, que a Resolução CGSN 195/2026 mantém a partir de 2027). Para venda a CNPJ, sim. Atenção: alguns estados exigem nota em venda no cartão | Cupom não fiscal basta no começo. Deixem a NFC-e preparada |
| **Simples Nacional (ME/EPP)** | **Sim**, em toda venda | NFC-e na Fase 3 é obrigatória antes de operar "no oficial". CSOSN no cadastro. IBS/CBS a partir de 2027 |
| **Lucro Presumido/Real** | **Sim** | NFC-e com IBS/CBS já em 2026 |

3. **Atenção ao limite do MEI:** um depósito "com bastante movimento" passa fácil de R$ 6.750/mês. Se a loja for MEI e faturar mais que isso, ela é desenquadrada e passa a ter de emitir nota. O próprio StockOS pode ajudar com um **alerta de faturamento acumulado no ano**.
4. Mesmo que os donos façam a própria contabilidade, **vale uma consulta pontual a um contador** na configuração inicial: NCM/CEST e tributação de bebidas com ST. Um erro aqui vira multa, e isso é configurado uma vez só.


- A NFC-e já é **obrigatória no varejo em praticamente todo o país**. SP encerrou o SAT: cupons SAT emitidos desde 01/01/2026 são rejeitados. MG proibiu a nota modelo 2 desde 01/08/2026.
- Para emitir é preciso: **certificado digital A1 (e-CNPJ)**, **CSC** (código gerado no site da SEFAZ do estado) e um emissor.
- **Não construam a assinatura e a comunicação com a SEFAZ do zero.** Usem uma **API de emissão** (Focus NFe, Nuvem Fiscal, PlugNotas/Tecnospeed etc.) ou a biblioteca ACBr. O StockOS só manda os dados da venda em JSON e recebe a chave e o QR Code para imprimir.
- **Dados fiscais do produto que precisam entrar no cadastro:** NCM, CEST, CFOP, CST/CSOSN, origem e unidade comercial. Bebidas quase sempre estão em **substituição tributária (ST)** de ICMS, e cerveja e refrigerante têm regras próprias. **Validem a tabela tributária com um contador (ver 8.1).**
- **Reforma tributária:** os campos de IBS/CBS são obrigatórios em 2026 para Lucro Real e Presumido. Para o **Simples Nacional** (se for o caso, ver 8.1), a obrigação começa em **2027**. Bebidas alcoólicas e açucaradas entram no **Imposto Seletivo**. Já deixem colunas no cadastro e usem uma API que acompanhe as notas técnicas.
- CPF na nota: campo opcional no subtotal.

---

## 9. Visual e usabilidade do PDV (proposta de tela)

O PDV precisa funcionar **sem mouse**. Layout sugerido (tela cheia, rota `/pdv/1`):

```
┌──────────────────────────────────────────────────────────────────────────────┐
│ CAIXA 1 · aberto 04/11 09:00 · OPERANDO: MARINA  [F12 trocar]   Venda 000123 │
├────────────────────────────────────────────┬─────────────────────────────────┤
│ ▶ [ Bipe o código ou digite… ]  qtd: 1     │ ÚLTIMO ITEM                     │
│   Próximo item: ❄ GELADA   (F4 = natural)  │ HEINEKEN LONG NECK 330ML ❄      │
│  #  Produto                Qtd  Unit  Total│ 6 x R$ 7,49 = R$ 44,94          │
│  1  Skol lata 350 fardo12 N  1  38,90 38,90│─────────────────────────────────│
│  2  Heineken LN 330      ❄   6   7,49 44,94│ SUBTOTAL                        │
│  3  Caldo de feijão 500ml    2  14,00 28,00│                                 │
│  4  Gelo 5kg                 1  12,00 12,00│        R$ 123,84                │
│                                            │                                 │
│   ❄ = gelada   N = natural                 │ Itens: 10                       │
├────────────────────────────────────────────┴─────────────────────────────────┤
│ F2 Buscar  F3 Qtd  F4 Gelada/Natural  F6 Cancelar item*  F8 Desconto*        │
│ F10 PAGAMENTO  F11 Sangria*  F12 Trocar operador  ESC Sair   [Caldo][Gelo]   │
│ * pede PIN do gerente                                                        │
└──────────────────────────────────────────────────────────────────────────────┘
```

Tela de pagamento (F10):
```
TOTAL R$ 123,84
[1] Dinheiro  [2] Pix  [3] Débito  [4] Crédito
Dinheiro recebido: R$ 150,00
TROCO: R$ 26,16        (fonte enorme, cor de destaque)
[Enter] Finalizar e imprimir
```

**Regras de usabilidade:**
- o foco **volta sempre** ao campo de leitura depois de qualquer ação;
- **sons:** bip curto quando lê o código, som grave de erro quando o produto não é encontrado;
- total e troco em fonte grande e alto contraste (a operadora olha de longe);
- **botões de atalho** para os 6-12 itens sem código mais vendidos (caldo, porção, gelo);
- alerta visual quando a gaveta passar do limite de sangria;
- pedir **senha do gerente** para cancelar item ou cupom, dar desconto acima do limite e fazer sangria;
- o visual atual (cards de cardápio com categorias) pode continuar como **aba secundária** "Cozinha/Porções".

**Na tela de Caixas (a do print):** formatar a moeda em pt-BR, mostrar a data junto da hora de abertura, mostrar o número de vendas e o ticket médio, separar "Dinheiro na gaveta" de "Pix/Cartão" e tirar o selo que cobre o botão.

---

## 10. Roteiro sugerido (em fases)

Situação conferida no código em 10/11/2026. ✅ feito; 🔶 em parte; sem marca: não começou.

**Fase 0 – Pré-requisitos do estoque (seção 11)**
0a. ✅ (seção 12) **Um único saldo de estoque** (o decimal): cadastro, entrada manual, notas e tela de Estoque passam a usar `estoque_produtos`, com migração que copia os saldos atuais.
0b. ✅ **Cadastro rápido de "produto de revenda"** (seção 13): cria Produto + Item de cardápio + Variações (lata gelada, lata natural, fardo) de uma vez.
0c. ✅ **Importar XML da NF-e** (seção 14) do distribuidor (EAN, embalagem, NCM/CEST/CFOP e custo).

**Fase 1 – Abrir a loja (MVP do caixa)**
1. 🔶 Tabela `codigos_venda` (código → variação, seção 11.4) + endpoint de busca + cadastro "bipe aqui" + folha de códigos para itens sem EAN. Vincular código a um produto que já existe, pelo PDV (seção 21), e folha de códigos para o que não tem código (seção 23).
2. ✅ Tela PDV própria, voltada para teclado: campo de leitura, multiplicador `6*`, lista, último item, teclas rápidas para caldo, gelo e porções (seção 30). Cadastrar na hora quando o código não é encontrado (seção 21).
3. ✅ **Gelada × Natural:** variações irmãs do mesmo produto (seção 11.2), padrão "Gelada", tecla F4 e padrão por embalagem (fardo = natural).
4. ✅ `venda_pagamentos`: dinheiro/Pix/débito/crédito, multi-pagamento e troco. A gaveta recebe só dinheiro.
5. ✅ **Troca rápida de operador por PIN (F12)** (seção 15), com cada venda gravando quem passou. Sangria e fechamento pedem a senha do gerente na hora. Cancelar venda finalizada pelo F9, com o gerente (seção 18). Desconto no total pelo F3, com o gerente (seção 20).
6. ✅ Tipo `ESTORNO_VENDA` no lugar da sangria no cancelamento.
7. ✅ Fechamento cego por forma de pagamento, com vendas por operador, + relatório de fechamento impresso (seção 22).
8. ✅ Cupom não fiscal impresso (80 mm), conferência da comanda e abertura da gaveta pelo driver da impressora (seção 25).
9. ✅ Desligar a venda avulsa sem itens no PDV (só gerente).
10. ✅ (seção 15) Login com senha em hash (BCrypt) e sessão/JWT no lugar do `X-User-RU`. **Trocar as senhas padrão `1234`.**
11. ✅ (seção 17) Instalação local no notebook, sem Docker, + backup automático. **Falta:** conferir no notebook real.

**Fase 2 – Substituir o caderno**
12. 🔶 Relatórios: mais vendidos, curva ABC, margem por produto, gelada × natural, vendas por operador e por horário. Feitos: relatório do dia e do mês, com mais vendidos, margem, operador e hora (seção 27), e curva ABC com cobertura de estoque (seção 28). **Falta:** comparativos entre períodos.
13. Estoque mínimo e sugestão de compra por fornecedor.
14. Inventário pelo celular (câmera no Chrome Android) ou leitor sem fio, bipando a prateleira para contar.
15. Alerta de faturamento acumulado no ano (limite do MEI, se for o caso).
16. Segundo caixa (Cenário B) em rede local, se o movimento pedir.

**Fase 3 – Profissionalizar**
17. NFC-e via API de emissão, obrigatória se a loja for Simples ou Presumido (ver 8.1).
18. Integração com maquininha (TEF/Smart POS) e Pix com QR dinâmico e conciliação.
19. Delivery pelo WhatsApp (se existir).

**Pedidos fora das fases:** ✅ redefinir senha esquecida (seção 19); ✅ comandas com cartão, divisão da conta, transferência e controles (seções 24, 26, 29 e 30). ✅ atendente lançando a comanda pelo celular (seções 31 e 32, com iPhone); ✅ pedido impresso para a cozinha (seção 33).

**Fora do escopo (confirmado):** fiado e casco retornável. Comandas entraram em 10/11/2026 (seção 24).

**Ainda falta descobrir:**
- O regime do CNPJ (consulta no Portal do Simples, ver 8.1).
- Quais maquininhas ela usa (para a Fase 3).
- Se fazem entrega.
- Média de vendas por dia e de faturamento mensal (para dimensionar e checar o limite do MEI).

---


---

## 11. Estoque e código de barras (análise de 04/11/2026)

### 11.1 Como o estoque funciona hoje
| Ação | Tela / API | Saldo que muda |
|---|---|---|
| Cadastrar produto com quantidade inicial | Estoque → `POST /api/produtos` | **legado** (`produtos.quantidade_atual`) |
| Entrada/Saída manual | Estoque → `POST /api/movimentacoes` (`EstoqueService.registrarMovimentacaoInterna`) | **legado** |
| Confirmar nota de fornecedor | Notas → `POST /api/notas/{id}/confirmar` (`NotaFiscalService`) | **legado** |
| Venda pela rota antiga | `POST /api/caixas/venda` | **legado** |
| Compra com embalagem e custo médio | `POST /api/compras` (`CompraEstoqueService`), **sem tela no front** | **decimal** (`estoque_produtos`) |
| Venda pelo PDV real | `POST /api/vendas/{id}/finalizar` (`FinalizacaoVendaService`) | **decimal** |
| Estorno/cancelamento | `MovimentoEstoqueService`, `CancelamentoVendaService` | **decimal** |
| O que a tela de Estoque mostra | `ProdutoResponse.from` → `p.getQuantidadeAtual()` | **legado** |

**O que isso causa na loja:**
1. A dona confirma a nota do distribuidor (entram 120 latas no saldo legado). O PDV real tenta vender e falha com "Produto sem estoque decimal configurado" ou "custo médio" (`FinalizacaoVendaService.java:362-380`), porque o saldo decimal está vazio.
2. Se o saldo decimal for alimentado pela API de compras, o PDV vende e baixa o decimal, mas a **tela de Estoque continua mostrando o número antigo**. Os alertas de estoque mínimo também ficam errados.
3. A migração `V20260713_01` criou `estoque_produtos`, mas **não copiou os saldos** de `produtos`.
4. Os testes de integração alimentam o estoque por `/api/compras`, por isso passam, mas o caminho que a tela usa nunca chega ao PDV.

**Recomendação (pré-requisito da Fase 1):** ter **um único saldo**, o decimal (`estoque_produtos` + `movimentos_estoque`), que já tem custo médio, trava e estorno.
- Cadastro, entrada manual, nota e tela de Estoque passam a ler e gravar nele.
- O campo legado vira só leitura (ou é removido), com uma migração que copia os saldos atuais.
- A tela de Notas passa a usar `CompraEstoqueService`, que já aceita **embalagem** (fardo = 12 un) e calcula custo unitário.

### 11.2 Outras lacunas do estoque para um depósito
- **Nota de fornecedor é digitada à mão**, item por item (`NotaFiscalController.AdicionarItemRequest`: produto, quantidade inteira, preço). Não há importação do **XML da NF-e**, que o distribuidor manda por e-mail ou que se baixa pela chave de acesso.
- **Produto novo criado pela nota** (`NovoProdutoRequest`) não pede código de barras nem embalagem.
- **O cadastro de produto não tem campo de código de barras.** Ele só existe em `embalagens_produto`, sem índice único e sem tela.
- **Para vender uma bebida, o PDV real exige 3 cadastros**: Produto (estoque) + Item de cardápio + Variação com `produto_direto_id` (`VendaController.ItemVendaRequest` só aceita `variacaoId`). Para ~300 bebidas, isso é inviável na mão.
  - Precisa de um **cadastro rápido de "produto de revenda"** que crie os três de uma vez.

**Uma boa notícia: a Variação resolve a gelada.** A `VariacaoItemCardapio` já tem `produto_direto_id` + `quantidade_produto_direto_base` + preço. Isso permite:
- "Skol lata — **Gelada**" (R$ 4,50, baixa 1 lata) e "Skol lata — **Natural**" (R$ 3,99, baixa 1 lata), duas variações do mesmo produto e do mesmo estoque;
- "Skol **fardo 12**" (R$ 38,90, baixa 12 latas), mais uma variação.

Então o `preco_gelado` proposto na seção 5.2 pode virar **uma variação "Gelada"**, sem coluna nova no produto. O código de barras aponta para a variação padrão, e a tecla F4 troca para a variação irmã.

### 11.3 Como os códigos de barras vão entrar no sistema
Do mais automático para o mais manual:

1. **Importar o XML da NF-e do distribuidor (o caminho principal).** Desde 2017 a NF-e é obrigada a trazer o GTIN nos campos `cEAN` (unidade comercial, ex.: o fardo/caixa) e `cEANTrib` (unidade tributável, ex.: a lata). Com o XML importado, o sistema:
   - acha o produto pelo código; se não achar, propõe cadastrar ou "amarrar" a um produto existente (só na primeira vez);
   - cria a embalagem com o fator (o `cEAN` do fardo vale 12 unidades do `cEANTrib`);
   - traz **NCM, CEST, CFOP e custo**, que a NFC-e vai exigir depois;
   - dá entrada no estoque.

   Em poucas notas, a maior parte do catálogo de bebidas fica cadastrada com código, sem digitar nada.
2. **"Bipe aqui" no cadastro:** no formulário de produto/embalagem, um campo onde se bipa a lata e o código é preenchido.
3. **Produto não encontrado no PDV:** som de erro + janela "cadastrar agora" (pede PIN do gerente). O produto entra com o código já preenchido e a venda continua.
4. **Consulta opcional por GTIN** (ex.: Bluesoft Cosmos, que devolve descrição e NCM a partir do código; confirmar termos e limites da API). Serve para preencher o nome automaticamente no cadastro.
5. **Códigos internos para o que não tem EAN** (caldo, porção, gelo, carvão): `101`, `102`… e uma **folha plastificada com os códigos de barras impressos** ao lado do caixa. A operadora bipa a folha. É prática comum em mercearia e evita digitar.

### 11.4 Modelo de dados proposto (atualizado)
```sql
create table codigos_venda (
  id           bigserial primary key,
  codigo       varchar(32) not null,
  tipo         varchar(12) not null,  -- EAN13, EAN8, DUN14, INTERNO
  variacao_id  bigint not null references variacoes_item_cardapio(id), -- o que é vendido (lata gelada, fardo...)
  padrao       boolean not null default true, -- quando o mesmo código serve a 2 variações (gelada/natural)
  ativo        boolean not null default true
);
create unique index uk_codigos_venda_padrao on codigos_venda (codigo) where padrao and ativo;
```
- `GET /api/pdv/codigo/{codigo}` → variação padrão + irmãs (ex.: Natural) + preço + estoque disponível.
- O PDV baixa a **lista inteira de códigos na abertura do caixa**, algumas centenas de linhas, para cada leitura ser instantânea e funcionar mesmo com o servidor lento.

### 11.5 Como o leitor funciona com o sistema (navegador)
- Leitor USB comum = **teclado** (modo HID / "keyboard wedge"). Ele "digita" os números e aperta **Enter**. Não precisa driver nem programa: funciona em qualquer navegador.
- **Configuração inicial**, pelos códigos do manual do leitor:
  - sufixo **Enter** (o padrão de fábrica na maioria);
  - teclado compatível com o Windows (ABNT2 ou US; os números saem iguais);
  - bip ligado.
- **No sistema:**
  - o campo de leitura fica sempre com o foco;
  - um "ouvinte" global detecta digitação muito rápida terminando em Enter (o leitor digita 13 dígitos em menos de ~50 ms; uma pessoa não). Assim, mesmo com o foco em outro lugar, a leitura é capturada como bipe;
  - o multiplicador `6*` antes do bipe vale 6 unidades;
  - bipar o mesmo item seguido soma na mesma linha.

### 11.6 Investimento em leitor (preços pesquisados em 04/11/2026, varejo online, só referência)
| Opção | Exemplos | Preço aproximado | Para quem |
|---|---|---|---|
| Econômica 1D com fio | Tanca TL-120, Elgin Flash II | **R$ 130–140** | Só código de barras comum, orçamento apertado |
| **Recomendada: 2D com fio + suporte** | Tanca TL-320, Elgin EL250 | **R$ 360–450** | Lê também QR Code e código na tela do celular, lê melhor rótulo amassado/curvo e funciona apoiado no suporte (mãos livres) |
| 2D de marca premium | Honeywell HH490, Zebra DS2208 | R$ 570–600 | Mais durável, para uso pesado |
| Sem fio | Tanca TL-120W, Elgin EL 300-BT, Honeywell HH492 | R$ 390–1.130 | Para bipar fardos e caixas pesados no chão sem levantar, e para contar estoque nas prateleiras |
| Fixo de mesa (tipo supermercado) | Elgin EL 720 / EL8600, Zebra SP20 / DS9308 | R$ 640–1.720 | Exagero para 1-2 caixas |

**Recomendação para a cliente:** **um leitor 2D com fio e suporte (~R$ 400)** por caixa. Se o orçamento permitir, depois um **sem fio** para o estoque e os fardos pesados. Com 2 caixas: ~R$ 800 no total. É o item mais barato do kit (notebook, impressora térmica e gaveta custam mais).

### 11.7 E sem comprar leitor? (câmera)
- **Webcam do notebook no caixa: não recomendo.** Tem foco fixo e baixa resolução, a operadora teria que levar cada lata até a tela, e é mais lento que digitar. Além disso, a API nativa de leitura do Chrome (`BarcodeDetector`) **não funciona no Windows**, só em Android, macOS e ChromeOS. Seria preciso uma biblioteca (ex.: zxing-js), e o resultado depende muito da câmera.
- **Celular como leitor para cadastro e inventário: vale a pena e é de graça.** No Chrome do Android a leitura pela câmera é nativa. Uma página "Inventário" no celular, na mesma rede, permite andar pelo depósito bipando prateleiras para contar o estoque ou cadastrar códigos.
- **Conclusão:** **no caixa, leitor USB**. A câmera do celular serve só como apoio para estoque.

### 11.8 Dicas práticas para bebidas
- **Lata gelada "suando"** (gelo e condensação) e plástico de fardo refletem a luz e atrapalham a leitura. Leitor 2D (imager) lida melhor com isso que laser barato. Deixar um pano no caixa ajuda.
- **Fardo fechado:** bipar o código do fardo (não o da lata) para baixar 12 de uma vez. Se o fardo não tiver código próprio, usar a folha de códigos ou `12*` + lata.
- **Caixa de 24 pesada no chão:** leitor sem fio, ou a folha de códigos.
- **Itens sem código** (caldo, porção, gelo): botões de atalho na tela + folha de códigos.
- **Código repetido entre produtos diferentes** (acontece com marca própria/importado): o sistema pergunta qual é na primeira leitura.

---

## 12. Correções aplicadas (04/11/2026)

Feitas do mais difícil para o mais fácil, na branch `feat/caixas-pdv-20261104`.

| # | Problema | O que mudou | Onde |
|---|---|---|---|
| 3.12 | Dois saldos de estoque | O saldo decimal é o oficial. Cadastro, entrada/saída manual e nota fiscal passam por ele (a nota leva o custo, que o PDV exige). Venda, compra e estornos atualizam a coluna antiga como espelho. A migração copia os saldos atuais | `SincronizadorEstoque`, `EstoqueService`, `NotaFiscalService`, `V20261104_01` |
| 3.1 | Toda venda como dinheiro na gaveta | Venda com pagamentos (dinheiro/Pix/débito/crédito/voucher, dividido, troco só no dinheiro). A soma precisa bater com o total. O caixa lança uma movimentação por forma e **só o dinheiro muda o saldo da gaveta**. A API sem pagamentos continua tratando a venda como dinheiro | `VendaPagamento`, `IntegracaoCaixaLegadoService`, `Caixa`, `POST /api/vendas/{id}/finalizar` |
| 3.3 | Sem busca por código de barras | Tabela `codigos_venda` (código → variação, com padrão + alternativa para gelada/natural), conferência do dígito verificador EAN/GTIN e API `/api/pdv/codigos`. O PDV ganhou campo de leitura com multiplicador `6*`, bip de sucesso/erro, cache dos códigos e F4 para a variação alternativa | `CodigoVendaService`, `CodigoVendaController`, `pdv.js` |
| 3.2 | Cancelamento como SANGRIA | Novo tipo `ESTORNO_VENDA`, lançado na forma em que o cliente pagou (eletrônicas primeiro, ou a forma escolhida). Estorno eletrônico não exige gaveta nem caixa aberto | `TipoMovimentacaoCaixa`, `IntegracaoCaixaLegadoService` |
| 3.4 | Venda avulsa sem itens | `/api/caixas/venda` sem itens agora exige gerente (`VER_FINANCAS`) ou autorização na hora | `CaixaService.registrarVenda` |
| 3.6 | Perfil CAIXA sem sangria/encerrar | Sangria, suprimento, encerramento e venda avulsa aceitam **login do gerente na hora**, sem trocar o operador. Fica anotado "[autorizado por …]" | `CaixaService`, `CaixaController`, `caixas.html` |
| 3.7 | ID de movimentação em memória | Sequence no banco (`movimentacoes_caixa_id_seq`) e `@GeneratedValue`. Sem leitura de todo o histórico na inicialização | `MovimentacaoCaixa`, `V20261104_02` |
| 3.10 | PDV da demonstração | Busca por código ou nome, `6*`, mudar quantidade e remover item, forma de pagamento e troco, carrinho limpo após a venda, teclado (F10, F5 a F8, Enter, Esc), sem fechar ao clicar fora. O simulador separa dinheiro de Pix/cartão | `preview-pdv.js`, `preview-mock.js`, `preview-scale.js` |
| 3.8 | Modal do PDV fecha ao clicar fora | Não fecha mais. Esc/Fechar pedem confirmação se houver itens | `caixas.html`, `pdv.js` |
| 3.5 | Fechamento não cego | O campo começa vazio e não mostra o esperado. A diferença aparece só no resultado ("Dinheiro esperado × contado") | `caixas.js`, `caixas.html` |
| 3.9 | Moeda e visual | `R$ 1.234,56` em toda a tela de Caixas; abertura com data; card com "Dinheiro na gaveta", "Pix e cartões", nº de vendas e ticket médio; formas e operador no "Ver movs" | `caixas.js`, `app.css` |

**Testes:**
- 129 testes de unidade passando, 21 deles novos (`PdvVarejoNovoTest`, `VarejoPdvMigrationSqlNovoTest`).
- Testes da demonstração passando (`check-preview`, `test-atomic-pdv-preview` com casos novos de Pix/troco/fechamento cego, `test-scale-preview`).
- A demonstração foi testada no navegador: venda só pelo teclado com troco, venda no Pix sem mexer na gaveta, fechamento cego com diferença.

**Verificação no banco real (atualizado em 06/11/2026):** a suíte completa com PostgreSQL roda pelo GitHub Actions (`stockos-backend-docker.yml`), porque o Docker não sobe na máquina de desenvolvimento. Migrações, mapeamento JPA, ID pela sequence, venda dividida, estorno e autorização do gerente passaram.

**Ainda não verificado:** o **PDV real** (`pdv.js` com o backend) não foi aberto no navegador, porque precisa do Java rodando com banco.

---

## 13. Cadastro rápido de bebida (06/11/2026)

Na tela de Estoque, o botão **+ Bebida rápida** abre um formulário feito para o leitor de código de barras. Uma única tela cria, numa só transação, tudo o que o PDV precisa:

| Preenchido | O que é criado |
|---|---|
| Sempre | Produto, item de cardápio e saldo oficial com custo médio |
| Preço de gelada | Variações "Gelada" (padrão) e "Natural", que baixam o mesmo estoque |
| Sem preço de gelada | Variação "Unidade" |
| Código da unidade | Bipe leva à gelada; a natural fica como alternativa (F4) |
| Fardo | Variação "Fardo N" que baixa N unidades, embalagem para compras e código próprio |

**Regras:**
- O Enter do leitor passa para o próximo campo e nunca salva sozinho. Ctrl+Enter salva, limpa o formulário e volta ao campo de bipe.
- Código com dígito verificador errado é recusado na hora. Código já cadastrado mostra de qual produto é.
- O custo é obrigatório, porque o PDV recusa vender produto sem custo médio. Sem estoque inicial, o custo já fica guardado para a entrada futura.
- Nome repetido é recusado. Se algo falhar, nada é criado.
- Só quem tem `EDITAR_ESTOQUE` cadastra.

**Onde está:** `ProdutoRevendaService`, `ProdutoRevendaController` (`POST /api/produtos/revenda`), `estoque.html`, `estoque.js`.

**Na demonstração:** a gelada vira o preço do produto e o fardo vira um produto fictício separado, com estoque próprio. A variação Natural e a baixa de 12 unidades pelo fardo só existem no sistema real.

**Ainda falta:**
- vincular código a um produto que já existe (hoje só pela API);
- cadastrar pelo próprio PDV quando o código não é encontrado;
- categoria "Petiscos" usa o estoque `SECO`; caldos e porções continuam pelo Cardápio, com ficha técnica.

---

## 14. Importar o XML da nota do distribuidor (06/11/2026)

Na tela de Notas, o botão **Importar XML** recebe o arquivo da NF-e do fornecedor em dois passos:

1. **Conferência (nada é gravado):** mostra número, fornecedor, destinatário e cada item com a conta "5 CX × 12 = 60 unidades · custo R$ 2,75/un".
2. **Entrada:** com tudo conferido, numa única transação, cadastra o que falta, registra a nota confirmada e dá entrada no estoque. Se algo falhar, nada fica pela metade.

**Como cada item é reconhecido, nesta ordem:**
| Origem | Como funciona |
|---|---|
| Vínculo | O código do produto naquele fornecedor, lembrado da nota anterior, com as unidades por embalagem |
| Código de barras | `cEAN` / `cEANTrib` já cadastrados no sistema |
| Produto novo | Pede nome e preços; o código de barras, o NCM e o CEST vêm da nota |

**Unidades por embalagem:** vêm da própria nota (`qTrib ÷ qCom`) quando ela informa. Se a nota não informa, a descrição ("C/12", "6X2L") vira só um palpite, e o item fica destacado para conferir. O campo é sempre editável.

**Custo de cada item:** produto + frete + seguro + outras despesas + IPI + ICMS-ST + FCP-ST − desconto, dividido pelas unidades. **Essa fórmula deve ser validada por um contador.**

**Segurança e regras:**
- O XML vem de fora: DOCTYPE e entidades externas são recusados (XXE).
- Quantidades e valores são sempre lidos do XML no servidor, nunca do navegador.
- A chave de acesso tem o dígito verificador conferido e não pode entrar duas vezes.
- NFC-e é recusada, por não ser nota de compra.
- Item sem custo ou que não dá unidades inteiras é recusado, e pode ser marcado como "Não dar entrada".
- Linhas repetidas do mesmo produto viram um item, com o custo médio entre elas.

**Onde está:** `NfeXmlParser`, `ImportacaoNfeService`, `ImportacaoNfeController`, `VinculoProdutoFornecedor`, `V20261106_01`, `notas.html`, `notas.js`.

**Limites conhecidos:**
- **A assinatura digital não é validada e a SEFAZ não é consultada:** uma nota cancelada depois de emitida não é detectada.
- **Não confere se a nota é mesmo para a loja:** o sistema ainda não sabe o CNPJ da loja, então mostra o destinatário para a pessoa conferir.
- **Ainda não foi testado com um XML real de distribuidor,** só com arquivos gerados pelos testes. Peça um à cliente: cada distribuidor preenche unidades e códigos do seu jeito.
- O custo da nota é arredondado a 2 casas por unidade, então o total da nota no sistema pode diferir alguns centavos do XML.
- Produtos vendidos em fração (chopp por litro) não entram por aqui.
- Na demonstração o XML é lido no navegador, com regras simplificadas e sem a conferência da chave.

---

## 15. Login seguro e troca de operador por PIN (06/11/2026)

**O que era o problema:** a API confiava no cabeçalho `X-User-RU`. Qualquer pessoa na rede podia dizer "sou o usuário 1" e agir como dono. As senhas ficavam em texto puro no banco.

**O que mudou:**

| Item | Como ficou |
|---|---|
| Identidade | Sessão no servidor, com cookie `HttpOnly` e `SameSite=Strict`. O banco guarda só o hash do token. O `X-User-RU` enviado pelo cliente é descartado por um filtro em `/api/*` |
| Senhas e PINs | Hash BCrypt. Senhas antigas são convertidas ao subir o sistema e viram provisórias |
| Senha provisória | Sem criar uma senha nova, a pessoa não usa o sistema. Vale para os usuários padrão (`1234`) e para funcionário novo |
| Tentativas | 5 erros de senha ou PIN bloqueiam aquela pessoa por 5 minutos |
| Autorização do gerente | Confere o hash e respeita o mesmo limite |
| Sessão | Dura 14 horas (configurável), termina no logout e quando o usuário é desativado |

**Troca de operador:** F12 no PDV, escolhe a pessoa e digita o PIN. A venda em andamento continua, e cada venda grava quem passou. Só aparece quem tem PIN e opera caixa.

**Por segurança, quem entra por PIN fica preso à área de caixa, mesmo sendo o dono.** PIN é curto; ele não pode abrir cadastro de funcionários, estoque ou notas. Para isso, é preciso entrar com a senha.

**Bug antigo corrigido:** a tela de login chamava `/api/auth/config`, que não existia no backend. Com isso, o botão "Entrar" ficava desativado no sistema real. A demonstração escondia o problema, porque não faz essa chamada.

**Onde está:** `SessaoFilter`, `AuthController`, `SessaoService`, `AutenticacaoService`, `SenhaService`, `LimiteTentativas`, `V20261106_02`, `login.html`, `shared.js`, `pdv.js`.

**Limites conhecidos:**
- **Não há HTTPS.** Em rede local a senha trafega sem criptografia entre os notebooks. No mesmo notebook (`localhost`) isso não pesa. Antes de expor na Internet, é preciso HTTPS e ligar `stockos.auth.cookie-seguro`.
- **Ninguém recupera senha esquecida pela tela ainda.** Falta um "redefinir senha" feito pelo gerente.
- **O bloqueio de tentativas fica em memória:** reiniciar o sistema zera os contadores.
- **As telas não têm login próprio:** o HTML abre sem sessão, mas sem nenhum dado, porque toda informação vem de `/api/*`.
- **Os testes antigos** usam `stockos.auth.aceitar-header-legado=true`, só em `src/test/resources`. Essa opção nunca deve ser ligada em uma loja.
- **Na demonstração** não há troca de operador nem criação de senha, porque não existe servidor.
- **Plano Supabase:** o projeto tem branches de autenticação com Supabase, não integradas. Se esse caminho for retomado, esta sessão local precisará ser substituída ou convivida com ele.
- A tela de login mostra o selo "Conexão segura", que já existia e não reflete a falta de HTTPS.

## 16. Teste de ponta a ponta no navegador (06/11/2026)

**O que é:** `scripts/test-e2e-real.mjs`, rodado pelo workflow `stockos-e2e.yml` a cada envio que mexa no sistema. Sobe o PostgreSQL e o Java em modo de produção, sem o cabeçalho legado, e abre um Chromium em janela de notebook (1366×650). Guarda um print de cada passo, por 7 dias, na página da execução no GitHub.

**O que ele percorre (35 passos):** login com senha errada e certa, criação de senha e PIN, Bebida rápida só pelo teclado, abertura de caixa, venda bipada com `2*` e fardo, troco, gelada × natural com F4, Pix, venda dividida, troca de operador por PIN, sangria com gerente, fechamento cego e importação de XML, incluindo a nota repetida.

**O que ele revelou, já corrigido:**

| Problema | Efeito na loja | Como foi achado |
|---|---|---|
| O atributo `hidden` não valia nas telas | O painel de pagamento ficava sempre aberto e espremia a lista de itens a zero. O operador não via o que estava vendendo | Olhando os prints: o teste passava, porque os itens existiam na página |
| Teclas 1 a 4 no pagamento | Com o foco no campo de valor, digitavam o número em vez de trocar a forma. Agora são F5 a F8 | Revisão para escrever o teste |
| Finalizar e avisos fora da tela | Em notebook, o erro ficava abaixo da área visível. O aviso subiu; pagando, a lista de itens dá lugar ao pagamento (Esc volta) | Prints |
| F5 fora do pagamento | Recarregava a página no meio da venda. Agora o PDV bloqueia | Revisão dos atalhos |
| Menu de quem entrou por PIN | Mostrava telas que essa pessoa não pode abrir. Agora mostra só Caixas PDV | Prints |
| Tela no nome da pessoa anterior | Depois de trocar de operador, a tela seguia com os dados de quem saiu. Agora atualiza ao sair do PDV | Prints |

**Lição:** o teste agora confere se o elemento está à vista (inteiro, sem corte e sem nada por cima), e não só se existe.

**O que este teste não cobre:**
- **Leitor de verdade.** A leitura é simulada digitando o código e Enter, que é o que o leitor USB faz, mas nenhum aparelho foi testado.
- **F5 no Chrome real.** O teste confirma que o PDV bloqueia a tecla; o navegador automatizado não recarrega com F5 de qualquer forma.
- **Windows e Chrome instalado.** Roda em Chromium no Linux.
- **Petiscos e caldos, Cardápio, Fornecedores, Funcionários e o conteúdo do Dashboard.** Essas telas não entram no percurso.
- **XML real de distribuidor.** A nota do teste é gerada pelo próprio script.

**Faltas achadas ao conferir o roteiro no código (ver seção 10):**
- **Cancelar uma venda já finalizada:** o servidor faz, com estorno, mas nenhuma tela chama. Hoje um erro do operador depois de finalizar só se resolve direto na API.
- **Desconto:** não existe na tela do PDV.
- **Vendas por operador e fechamento impresso:** cada movimento grava quem passou, mas não há resumo por pessoa nem impressão.
- **Atalhos de caldo, gelo e porções, e folha de códigos** para itens sem código de barras: não existem.

---

## 17. Instalação na loja com backup (06/11/2026)

**Decisão: sem Docker.** O roteiro previa Docker, mas ele não subiu na própria máquina de desenvolvimento, por causa da virtualização do Windows, e seria o mesmo risco no notebook da loja. O pacote traz o sistema, o Java e o PostgreSQL prontos, em um ZIP. Guia para a loja: `docs/instalacao-loja.md`.

| Item | Como ficou |
|---|---|
| Instalação | `Instalar.cmd` em `C:\StockOS`, sem internet. Cria banco com senha aleatória, atalho na Área de Trabalho e início ao entrar no Windows |
| Acesso | Sistema e banco atendem só neste computador. Isso também resolve, por enquanto, a falta de HTTPS da seção 15 |
| Backup | Dentro do próprio sistema, a cada 3 horas e a cada fechamento de caixa. Assim, nenhuma janela abre por cima do caixa e tira o foco do leitor |
| Conferência | O arquivo só ganha o nome definitivo depois de passar pelo `pg_restore --list` |
| Cópia externa | Qualquer pasta chamada `StockOS-Backups` em disco, pen drive ou nuvem recebe cópia. O pen drive conectado depois recebe o último backup em até 15 minutos |
| Guarda | Tudo de 2 dias, um por dia por 30 dias, um por mês por um ano. Arquivos com outro nome nunca são apagados |
| Aviso | Painel no Dashboard: em dia, sem cópia externa há mais de 7 dias, ou com problema |
| Restauração | Carrega em um banco novo e só troca no fim. Antes, guarda uma cópia. Se o sistema não subir, volta o banco anterior |
| Atualização | O mesmo `Instalar.cmd`. Guarda uma cópia antes e volta sozinho se a versão nova não subir |

**O que é testado em um Windows limpo a cada envio (`stockos-instalador.yml`), com o PowerShell que vem no Windows:**
- instalação a partir de uma pasta com espaço e acento;
- os 36 passos no navegador, com backup pela tela;
- backup com cópia, e backup disparado pelo fechamento de caixa;
- restauração, incluindo a recusa de um arquivo que não é backup;
- queda de energia, com os processos encerrados à força e retomada pelo início automático;
- atualização boa e atualização com programa quebrado;
- notebook perdido: apaga tudo, instala do zero e restaura da pasta externa, com a senha antiga funcionando;
- pasta fechada para outros usuários, sistema inacessível pela rede e ordem alfabética em português.

**O que este teste não cobre (conferir na primeira instalação real):**
- **Reinício de verdade do notebook.** O teste roda o script de início, mas não reinicia o Windows nem usa o atalho da pasta Inicializar.
- **Janela piscando durante o backup.** O Java promete rodar o programa de backup sem janela, mas o teste não tem tela para ver. Se piscar, o leitor pode perder um código.
- **Usuário sem administrador.** O usuário do teste é administrador. A instalação foi feita para não depender disso, mas não foi testada assim.
- **Windows sem o runtime do Visual C++.** O pacote traz os arquivos, mas o Windows do teste já os tem.
- **Pen drive e OneDrive de verdade.** O teste usa uma pasta `C:\StockOS-Backups`.
- **Antivírus e aviso do SmartScreen.** O pacote não é assinado.
- **Notebook fraco.** Os tempos do teste são de uma máquina do GitHub.

**Limites conhecidos:**
- **Backup sem criptografia:** quem pegar o pen drive lê vendas e cadastros. As senhas ficam só como hash.
- **O PostgreSQL 17.11 não tem hash publicado pelo fabricante:** o valor fixado no script é o do download feito em 06/11/2026, então ele garante que o arquivo não mudou, mas não a origem.
- **Depois de reiniciar,** o sistema só liga quando alguém entra no Windows.
- **O pacote expira em 30 dias** no GitHub e exige login. Para a loja, vale guardar uma cópia.

---

## 18. Cancelar venda finalizada pelo PDV (09/11/2026)

**O que era o problema:** o servidor já cancelava uma venda com estorno, mas nenhuma tela chamava isso, e só dono ou gerente podiam usar. Um erro do operador depois de finalizar não tinha como ser desfeito no balcão.

**Como ficou:**

| Passo | O que acontece |
|---|---|
| F9 no PDV | Lista as vendas deste caixa, da mais nova para a mais antiga, com hora, operador, valor, formas pagas e se já foi cancelada |
| Escolher a venda | Mostra os itens e quanto volta ao cliente em cada forma, avisando quanto sai da gaveta |
| Motivo | Obrigatório, com sugestões ("Cliente desistiu", "Item lançado errado", "Forma de pagamento errada") |
| Gerente | Quem não é gerente pede o login dele na hora, como na sangria. A senha errada é recusada e apagada do campo |
| Resultado | O estoque volta, a gaveta devolve só a parte paga em dinheiro, e Pix e cartões só abatem os totais. O estorno fica no caixa em nome de quem opera, com "autorizado por" na descrição; a venda fica em nome de quem autorizou |
| Venda já cancelada | Aparece marcada e não pode ser cancelada de novo |

**Testado:** no banco (operadora sem gerente é recusada; com gerente, estoque, gaveta, totais e um estorno por forma), e no navegador real, no meio do caminho da loja (lista, recusa sem motivo, recusa com senha errada, cancelamento e valores seguintes).

**Achado pelo caminho:** o PDV só deixa os botões da tela usarem as funções que ele exporta. As novas tinham ficado de fora, e o clique em "Cancelar esta venda" não fazia nada; só o Enter funcionava. O teste no navegador agora confere, em Estoque, Caixas e Notas, se todo botão chama uma função que existe.

**Limites conhecidos:**
- **Só a venda inteira.** O servidor também cancela um item, mas não há tela para isso.
- **Só as vendas do caixa aberto.** Uma venda de um caixa já encerrado não aparece na lista; para devolver dinheiro dela, o servidor exige um caixa aberto com o mesmo número.
- **A senha errada do gerente conta no limite de 5 tentativas da conta dele:** quem errar muitas vezes no caixa bloqueia o login do gerente por 5 minutos.
- **A demonstração não tem o F9.**

---

## 19. Redefinir senha esquecida (09/11/2026)

**O que era o problema:** ninguém recuperava uma senha esquecida. Um funcionário ficava sem acesso, e, se fosse o dono, a loja inteira ficava sem quem administrasse.

**Funcionário:** em Funcionários, o gerente clica em **Redefinir senha**.

| Regra | Por quê |
|---|---|
| A senha temporária tem 8 caracteres, sem O/0, I/1 e L | Para ditar em voz alta sem confusão |
| Aparece uma vez e some ao fechar a janela | Não fica exposta na tela |
| A senha antiga para de valer na hora, e quem estava logado com ela sai | Se a senha vazou, o acesso acaba ali |
| O bloqueio por tentativas erradas é liberado | A pessoa entra logo |
| No próximo acesso, o sistema pede uma senha nova e um PIN | Nem o gerente fica sabendo da senha definitiva |
| Ninguém redefine a própria senha por ali | Para isso, existe a troca com a senha atual |
| Um gerente não redefine a senha de um dono | Senão ele poderia tomar a conta do dono |

**Dono:** `Redefinir senha do dono.cmd`, na pasta `C:\StockOS` do notebook da loja, faz o mesmo direto no banco. Só funciona nesse notebook: quem chega até ali já tem acesso ao computador e aos dados. Com mais de um dono, ele pede o número de quem esqueceu.

A tela de login explica os dois caminhos.

**Corrigido no caminho:** o botão **Classe** em Funcionários não funcionava, porque o nome da pessoa entrava com aspas e quebrava o botão.

**Testado:**
- **Regras:** testes de unidade (senha antiga, sessão aberta, bloqueio, permissões, dono e gerente).
- **Navegador real:** o dono redefine a senha da Ana, a senha antiga é recusada, e a Ana entra com a temporária e cria outra.
- **Windows limpo:** o atalho do dono gera a senha, a antiga deixa de valer, e o dono entra com a temporária e troca.

**Limites conhecidos:**
- **A senha temporária não expira sozinha.** Ela vale até ser usada ou redefinida de novo.
- **O atalho do dono só está na instalação da loja,** não no servidor de desenvolvimento.

---

## 20. Desconto no PDV (09/11/2026)

**Como funciona:** depois de bipar tudo, no pagamento, **F3** abre o desconto. Também funciona direto da lista de itens, porque o F3 já abre o pagamento.

| O que digitar | Efeito |
|---|---|
| `5` ou `5,00` | Tira R$ 5,00 |
| `10%` | Tira 10% |
| `=50` | Fecha a venda em R$ 50,00 ("faz por 50") |
| `0` | Retira o desconto |

- **Quem cuida do financeiro** (dono, gerente) dá o desconto sozinho. **Quem só opera o caixa** precisa do login do gerente na hora, como na sangria e no cancelamento.
- O resumo mostra **Subtotal, Desconto e Total**. O que falta pagar e o troco já usam o total com desconto.
- **Voltar aos itens (Esc) retira o desconto.** Assim ele não muda sozinho se a quantidade de um item mudar. É só aplicar de novo.
- Cada desconto fica registrado no log, com o valor, o operador e quem autorizou.

**Como o valor é gravado:** o servidor reparte o desconto entre os itens, na proporção de cada um, e grava por unidade. A sobra do arredondamento vai para o item de menor quantidade, e o total fecha no centavo pedido. Isso mantém certos:
- o pagamento, conferido no centavo;
- o cancelamento, que devolve o valor de cada item com o desconto;
- a margem por produto nos relatórios.

**Testado:**
- **Unidade:** repartição exata em vários carrinhos e valores, porcentagem, recusas e quem pode dar desconto.
- **Banco:** operadora sem gerente é recusada; com gerente, o total fecha em R$ 50,00, o pagamento passa, a gaveta e o estoque conferem, e, depois de finalizada, a venda não aceita mais desconto.
- **Navegador real:** "=8" com o gerente, linha de desconto, Esc retirando o desconto, e nova venda com R$ 1 de desconto e troco.

**Limites conhecidos:**
- **Só no total.** Não há desconto em um item escolhido, como "esta lata sai por 4".
- **Não há limite automático de desconto.** O controle é a senha do gerente para quem opera o caixa. Se a dona quiser, por exemplo, "até 5% sem gerente", isso pode virar uma opção.
- **A demonstração não tem o F3.**

---

## 21. Cadastrar pelo PDV quando o código não existe (09/11/2026)

**O que era o problema:** um código desconhecido parava a venda com "Peça ao gerente para cadastrar no Cardápio/Estoque". O cliente esperava enquanto alguém saía do caixa, abria outra tela e voltava.

**Como ficou:** o aviso do código desconhecido ganha o botão **"Cadastrar este código"**, que abre uma janela com duas opções:

| Opção | Quando usar | O que pede |
|---|---|---|
| Produto novo | O produto ainda não existe no sistema | Nome, preço gelada (opcional), preço natural, custo e quantos tem na loja |
| Produto que já existe | Mesma bebida com código diferente (outra fábrica ou embalagem) | Procurar pelo nome e escolher a versão: gelada, natural ou fardo |

- **Ao salvar, o item já entra na venda**, com o multiplicador digitado (`6*`).
- **Quem não pode cadastrar** (o caixa) precisa do login do gerente na hora. Funciona também para quem entrou por PIN, sempre com essa autorização. O dono e o gerente cadastram sozinhos.
- O cadastro fica no log, com quem estava no caixa e quem autorizou.

**Onde está:** `PdvCadastroController` (`/api/pdv/cadastro/produto` e `/api/pdv/cadastro/codigo`), que usa o mesmo cadastro rápido e o mesmo vínculo de código das outras telas; janela `#modal-pdv-novo-codigo` no PDV.

**Testado:**
- **Banco:** a operadora é recusada sem o gerente nas duas opções, e passa com ele; o código novo é lido no caixa; o vínculo repetido e o código inválido são recusados.
- **Navegador real:** o dono cadastra "Gelo 5kg" pelo PDV e o item entra na venda com o estoque informado; a Ana, que entrou por PIN, liga um código novo à Skol natural com o gerente, e o item entra na venda.

**Limites conhecidos:**
- **O produto novo entra na categoria Bebidas e sem fardo.** Para fardo, petisco ou outra categoria, ajuste depois em Estoque ou Cardápio.
- **Não confere se o código é de outro produto real.** Um código lido errado vira um cadastro errado; quem autoriza precisa olhar a embalagem.
- **A demonstração não tem esta janela.**

---

## 22. Resumo por operador e impressão do fechamento (09/11/2026)

**O que era o problema:** cada movimentação já gravava quem operava, mas nenhuma tela juntava isso. Com duas ou três pessoas se revezando no caixa, a dona não via quem vendeu quanto, quem cancelou ou quem tirou dinheiro da gaveta.

**Como ficou:** no fechamento do caixa e em **Ver movs**, uma tabela mostra, por pessoa:

| Coluna | O que conta |
|---|---|
| Vendas e Vendido | Vendas finalizadas por ela, antes dos cancelamentos |
| Dinheiro / Pix e cartões | Em que forma entrou |
| Cancelou | Quantas vendas ela cancelou e o valor devolvido |
| Descontos | Desconto dado nas vendas que ela finalizou |
| Sangrias | Dinheiro que ela tirou da gaveta |

Venda conta para quem finalizou; cancelamento e sangria, para quem fez. Quem mais vendeu aparece primeiro.

**Impressão:** o botão **Imprimir fechamento** manda para a impressora só o fechamento e a tabela, sem menu nem cards. Funciona com qualquer impressora instalada no Windows.

**Testado:**
- **Unidade:** venda em duas formas conta como uma, e o cancelamento em duas formas também; desconto, sangria e suprimento.
- **Banco:** resumo de uma venda com desconto.
- **Navegador real:** a tabela no fechamento do caminho completo, com a dona e a Ana, e a página como sai no papel.

**Limites conhecidos:**
- **A tabela é larga.** Numa impressora térmica de 80 mm, ela sai reduzida ou cortada, conforme o driver. Para a bobina, faria falta um layout estreito próprio, junto com o cupom (próximo item do roteiro).
- **A impressão não foi feita numa impressora de verdade.** O teste confere a página no modo de impressão do navegador.
- **É por sessão de caixa.** Ainda não há o mesmo resumo por dia ou por mês.

---

## 23. Folha de códigos (10/11/2026)

**O que era o problema:** gelo, copo, petiscos e caldos não têm código de barras. O caixa precisava procurar pelo nome a cada venda.

**Como ficou:** em **Estoque**, o botão **Folha de códigos** abre uma página que:
- avisa quais itens ainda não podem ser bipados;
- cria, com um clique, um código para cada um. É um EAN-13 começando com 20, faixa que o padrão GS1 reserva para uso dentro da loja: qualquer leitor bipa, e ele nunca coincide com o código de fábrica de outro produto. O código é sempre o mesmo para o mesmo item;
- imprime em A4, três etiquetas por linha, com nome, versão (gelada, natural, fardo), preço e código de barras. Na impressão saem só as etiquetas. É para deixar no balcão, de preferência plastificada.

Os produtos com código de fábrica ficam fora da folha, mas uma opção mostra todos.

**Como o código de barras é feito:** desenhado em SVG pela própria página, sem biblioteca de fora, pela codificação EAN-13 do padrão GS1.

**Testado:**
- **Leitor de verdade:** o leitor ZXing, o mesmo tipo de leitura que celulares e leitores usam, decodificou um código para cada primeiro dígito de 0 a 9. É esse dígito que muda o desenho do lado esquerdo.
- **Navegador real:** o dono cadastra um item sem código, cria o código pela folha, o ZXing lê a etiqueta na tela e o código vende no caixa; a versão impressa sai só com as etiquetas.
- **Banco:** só quem pode editar o estoque cria códigos; o item some da lista do que falta.

**Limites conhecidos:**
- **Não testei com um leitor físico nem numa impressora.** A leitura foi feita na imagem da tela. Vale imprimir uma folha e bipar antes de plastificar.
- **Impressoras muito simples** podem borrar as barras finas. Se o leitor não pegar, imprima em qualidade alta.
- **Um código por versão:** o código da etiqueta vende a versão escolhida, como a natural. Para a gelada, use o F4 ou outra etiqueta.

---

## 24. Comandas para quem consome na loja (10/11/2026)

**Mudança de escopo:** em 04/11, a cliente disse que não havia comandas. Em 10/11, o fluxo pedido passou a ser: quem só compra paga na hora, no balcão; quem consome na loja paga no fim, por comanda. Decisões:
- cada comanda é um **cartão numerado com código de barras**;
- os pedidos são **lançados no próprio PDV**;
- comandas vieram antes da maquininha integrada.

**Como funciona no balcão:**

| Momento | O que a operadora faz |
|---|---|
| Cliente vai consumir | Entrega um cartão. Bipa o cartão no PDV: a comanda abre, e o selo "Comanda 7" aparece no topo |
| Cliente pede algo | Bipa o cartão (ou digita `c7`), bipa o produto e aperta **Esc**: a comanda fica guardada e o caixa volta ao balcão |
| Conferir | **F7** lista as comandas abertas com hora, quantidade de itens e total |
| Fechar a conta | Bipa o cartão, confere os itens na tela e paga com **F10**, como no balcão: forma, troco, divisão, desconto |
| Depois de pagar | A comanda fecha, o estoque baixa e o número do cartão fica livre para o próximo cliente |

- Uma venda de balcão pela metade bloqueia a abertura de comanda, para não misturar as duas.
- Comanda aberta por engano, ainda vazia, pode ser excluída pelo F7.
- **O estoque só baixa no pagamento.** Enquanto a comanda está aberta, o que foi consumido ainda aparece como disponível.

**Os cartões:** a Folha de códigos imprime os cartões (escolha "do número X até Y"), com um EAN-13 começando com 29. Essa faixa fica separada da dos produtos (20), e qualquer leitor bipa.

**Como foi feito:** cada comanda é uma venda em rascunho ligada ao número (tabela `comandas`, migração `V20261110_01`), com um só número aberto por vez. Por isso ela usa o mesmo carrinho, pagamento, desconto, cancelamento e resumo por operador do balcão, sem um segundo caminho de venda. Ao pagar, o servidor fecha a comanda.

**Testado:**
- **Banco:** abrir e retomar pelo número, itens em vários momentos, estoque intocado até pagar, lista de abertas com total, recusa de excluir com itens, pagamento fechando a comanda e liberando o número, e exclusão de comanda vazia.
- **Navegador real:** o leitor ZXing lê o cartão impresso; o dono abre a comanda pelo cartão, lança 2 latas e guarda; acha a comanda pelo F7, acrescenta 1 lata e paga no Pix; a comanda fecha e o estoque baixa 3.

**Limites conhecidos:**
- **Um aparelho só:** feito depois para o celular do atendente (seção 31). Segundo computador de caixa ainda não.
- **Divisão da conta:** feita depois, na seção 26.
- **Transferir e juntar comandas:** feito depois, na seção 29.
- **Se o estoque acabar** enquanto a comanda está aberta, o pagamento é recusado, como no balcão. Corrija o estoque e pague de novo.
- **A demonstração não tem comandas.**

**Maquininha integrada (cartão e Pix):** ainda não feita. Depende da maquininha que a loja escolher. Mercado Pago Point integra pela internet, sem programa no notebook; Cielo, Rede e Getnet costumam exigir TEF (SiTef ou PayGo), um programa pago. Hoje a operadora escolhe a forma no PDV e cobra na maquininha à parte.

---

## 25. Cupom de 80 mm e conferência da comanda (10/11/2026)

**Como funciona no PDV:**

| Tecla ou opção | O que imprime |
|---|---|
| **F8**, no balcão | O último cupom de novo (2ª via) |
| **F8**, com uma comanda aberta | A conferência da comanda, para o cliente conferir antes de pagar. Ela diz que ainda não foi paga |
| **Cupom automático** (marcado no topo do pedido) | O cupom sai sozinho ao finalizar cada venda. Fica guardado neste computador |
| Botão "Imprimir cupom" no aviso da venda | Quando o cupom automático está desligado |

O cupom tem data, caixa, número da venda, operador, comanda (se houver), itens com quantidade e preço, subtotal, desconto, total, formas pagas, valor recebido e troco. A largura cabe na bobina de 80 mm, e na impressão sai só o cupom. Ele diz "Não é documento fiscal": a NFC-e continua dependendo do regime da loja (seção 8).

**Imprimir sem a janela de impressão:** o instalador cria dois atalhos.
- **StockOS Caixa:** abre o Edge em janela própria, com impressão direta. O cupom sai na impressora **padrão** do Windows, sem perguntar. Por isso a térmica precisa ser a padrão.
- **StockOS:** o de sempre, com a janela de impressão, para a folha de códigos (A4) e o fechamento.

**Gaveta:** o navegador não manda o comando de abrir a gaveta. Nas térmicas comuns (Elgin, Epson, Bematech), o driver do Windows tem a opção "abrir gaveta ao imprimir". Com ela ligada, a gaveta abre a cada cupom.

**Testado:** no navegador real, o F8 manda para a impressora o cupom da venda com troco, e só ele vai para o papel, em largura de bobina. O cupom automático sai ao finalizar uma venda no Pix. A conferência da comanda sai com os itens e o total, e não como recibo. No Windows limpo, o instalador cria o atalho do caixa com impressão direta.

**Limites conhecidos:**
- **Não testei numa impressora térmica de verdade**, nem a impressão direta do Edge. Na primeira instalação, imprima um cupom de teste.
- **Sem nome e endereço da loja no cupom.** Pode entrar quando houver uma tela de dados da loja.
- **Sem 2ª via de vendas antigas:** o F8 reimprime só a última venda deste caixa desde que o PDV foi aberto.
- **Gaveta só pelo driver:** se a impressora não tiver essa opção, a gaveta abre na chave.

---

## 26. Dividir a conta (10/11/2026)

**Como funciona:** no pagamento (F10), **F9** abre a divisão, em dois modos.

| Modo | Como usa | Exemplo |
|---|---|---|
| Partes iguais | Digite quantas pessoas | R$ 13,50 por 4: três pagam R$ 3,37 e a última R$ 3,39 |
| Pelos itens de cada pessoa | Marque quanto do que cada pessoa consumiu e aperte Enter; repita para a próxima | Pessoa 1: 2 latas (R$ 9,00) no Pix; pessoa 2: a lata que falta (R$ 4,50) em dinheiro |

- Depois de cada pessoa, o aviso mostra quem pagou, o troco dela e quanto falta. Depois, chama a próxima pessoa.
- Na divisão pelos itens, o que já foi pago some da lista. Quem fica com tudo o que falta paga o restante exato, sem sobra de centavo.
- Cada pessoa pode pagar numa forma diferente. A tela e o cupom mostram "Pessoa 1 · Pix", "Pessoa 2 · Dinheiro", com recebido e troco de cada uma.
- Tirar um pagamento (x) devolve os itens daquela pessoa para a lista. "Parar de dividir" volta ao pagamento normal.
- Vale para comanda e para venda de balcão.

**Como foi feito:** sem mudança no servidor. Continua sendo uma venda só, com um pagamento por pessoa, conferido no centavo como antes. Por isso o caixa, o fechamento e o resumo por operador já contam certo.

**Testado no navegador real:** a comanda 7 dividida pelos itens (2 latas no Pix, 1 em dinheiro com troco de R$ 0,50) e o cupom com cada pessoa. A comanda 8 dividida em 4 partes iguais, com a sobra de centavos na última. O estoque baixa uma vez só.

**Limites conhecidos:**
- **Todos pagam na mesma hora.** Quem sai antes não paga a parte dele e deixa o resto aberto. Para isso, o costume de bar é uma comanda por pessoa, e o sistema já permite: cada um com seu cartão.
- **Partes iguais exige começar antes de lançar pagamentos.**
- **Desconto aplicado no meio** recomeça a divisão, porque o total muda.

---

## 27. Relatório do dia e do mês (10/11/2026)

**Onde fica:** no Dashboard, botão **Relatório do dia**, para quem cuida do financeiro. Quem só opera o caixa não vê.

**No dia:**

| Bloco | O que mostra |
|---|---|
| Cartões | Faturamento (já sem os cancelamentos), vendas e quantas foram canceladas, ticket médio, margem estimada, descontos e comandas pagas |
| Formas de pagamento | Quanto entrou em dinheiro, Pix, débito e crédito, já sem o que foi devolvido |
| Movimento do caixa | Vendido, cancelado, sangrias e suprimentos |
| Vendas por hora | Quantas vendas e quanto em cada hora, com barras |
| Por operador | Quem vendeu quanto, em que forma, quem cancelou e quem fez sangria, somando todos os caixas do dia |
| Produtos mais vendidos | Quantidade, valor e margem estimada de cada produto e versão (gelada, natural, fardo) |

**No mês:** os mesmos totais, o dia a dia e os produtos do mês. As setas andam um dia ou um mês. O botão **Imprimir** sai sem os controles.

**De onde vêm os números:**
- **Dinheiro:** das movimentações do caixa, o que entrou e saiu de fato. Por isso ele bate com o fechamento.
- **Produtos e margem:** das vendas pagas no período, inclusive as comandas, pela hora do pagamento.
- **Margem estimada:** usa o custo médio de cada produto no momento da venda. Depende do custo informado no cadastro e nas notas.

**Testado:**
- **Unidade:** o mesmo dia do caminho completo da loja, com R$ 91,39 líquidos, 6 vendas (1 cancelada), ticket de R$ 18,28, dinheiro R$ 64,90 e Pix R$ 26,49.
- **Banco:** a venda com desconto aparece no relatório; o caixa é recusado; data inválida é recusada.
- **Navegador real:** a página confere com o caixa do dia, e o mês mostra o dia. A versão impressa sai sem botões.

**Limites conhecidos:**
- **Sem gráfico do mês e sem comparação com o mês anterior.**
- **Sem exportar para planilha.** Dá para imprimir ou salvar em PDF pela impressão do Windows.
- **Gelada × natural aparecem como produtos separados**, não como um comparativo pronto.
- **O mês lê todas as vendas do mês de uma vez.** Com muitos anos de uso, pode ficar mais lento; dá para otimizar depois.

---

## 28. Curva ABC (10/11/2026)

**Onde fica:** no Relatório, aba **Curva ABC**. O período padrão são os últimos 30 dias, e dá para escolher outro de até um ano. O critério pode ser **faturamento** ou **margem**.

**Como lê:**

| Classe | Quem entra | O que fazer |
|---|---|---|
| **A** | Os poucos produtos que somam os primeiros 80% | Nunca deixar faltar; negociar preço com o distribuidor |
| **B** | Os que levam a soma até 95% | Acompanhar |
| **C** | O resto | Comprar pouco; avaliar se vale manter |

O produto que cruza a faixa fica na classe de cima, como é o costume. Gelada, natural e fardo do mesmo produto somam juntos. Margem negativa vai para C.

**Estoque e "Dura":** para cada bebida, a tabela mostra o estoque de agora e para quantos dias ele dá. A conta usa a média de venda por dia no período, com o fardo contado pelas latas. Menos de 7 dias aparece em vermelho. Petiscos com receita, que baixam vários ingredientes, ficam sem esse cálculo.

**Testado:**
- **Unidade:** classes com o produto que cruza a faixa, ordem diferente por margem, margem negativa em C e cobertura (150 latas em 30 dias com 84 em estoque dão 16 dias).
- **Banco:** a curva responde; período invertido é recusado; o caixa não vê.
- **Navegador real:** a Skol aparece em A com 100%, estoque de 84 e "105 dias". Foram 24 latas vendidas no dia, contando o fardo, o que dá 0,8 por dia em 30 dias. A troca para margem também foi testada.

**Limites conhecidos:**
- **A cobertura usa a média do período inteiro.** Num período com poucos dias de venda, ela parece maior do que é. Para comprar, olhe um período de movimento normal.
- **O estoque é o de agora**, não o do fim do período escolhido.
- **Sem sugestão de quanto comprar.** Pode vir depois, juntando a cobertura com o estoque mínimo.

---

## 29. Transferir itens e juntar comandas (10/11/2026)

**Para que serve:** lançou na comanda errada, o cliente mudou de mesa, um grupo virou um só, ou a venda foi bipada no balcão e o cliente vai consumir na loja.

**Como funciona:** com a conta aberta no PDV (comanda ou balcão), aperte **F6** ou o botão **Transferir**, no topo do pedido.
1. Digite o número da comanda de destino. Se ela não estiver aberta, abre.
2. Para cada item, diga quanto vai. O padrão é tudo; os botões "Tudo" e "Nada" ajudam.
3. Enter.

| Situação | Resultado |
|---|---|
| Parte de um item (1 de 3 latas) | A linha de origem fica com 2, e a de destino ganha 1 |
| Item com adicionais (ex.: porção com molho) | Vai inteiro, com os adicionais; parte dele é recusada |
| Tudo da comanda | A comanda de origem é juntada na outra e fecha |
| Do balcão | Os itens vão para a comanda, e o caixa volta ao balcão quando não sobra nada |

**Regras:**
- Não muda o estoque: ele só baixa quando a comanda é paga.
- O desconto do item transferido fica para trás, porque o desconto é dado no pagamento.
- Não transfere para a mesma comanda, nem mais do que tem.
- A transferência fica no log, com quem fez.

**Como foi feito:** numa única operação no servidor (`POST /api/comandas/{destino}/transferir`), que trava as duas contas sempre na mesma ordem, para dois caixas não se travarem. O item inteiro só troca de conta, levando junto os adicionais e as partes. Na parte de um item, a linha de origem é reduzida e uma nova é criada no destino.

**Testado:**
- **Banco:** 1 de 3 latas para outra comanda (R$ 10 + R$ 5), recusa de quantidade maior que a que existe e de transferir para a mesma comanda, e junção que fecha a comanda de origem.
- **Navegador real:** 2 latas bipadas no balcão vão para a comanda 9, uma de cada vez. A comanda 10 é juntada na 9 e fecha. A comanda 9 fica com as 3 latas, e o estoque não muda.

**Limites conhecidos:**
- **Parte de item com adicionais não se transfere.** Vai o item inteiro.
- **Não transfere entre comandas pagas.** Venda paga se corrige pelo cancelamento (F9).

---

## 30. Controles da comanda (10/11/2026)

**Para que serve:** a comanda guarda o que o cliente já consumiu. Tirar um item dela, ou fechar o dia com ela aberta, é dinheiro que pode sumir. Os dois controles abaixo fecham essas portas, e as teclas rápidas aceleram o que não tem código.

### Tirar item da comanda só com o gerente

Na comanda, o **x** do item e o botão **Limpar** abrem a janela "Tirar da comanda".
1. Diga quanto sai de cada item. O padrão é tudo; para tirar 1 de 2, digite 1.
2. Escolha ou escreva o motivo: lançado errado, cliente desistiu, produto com defeito.
3. O gerente digita nome e senha. Quem cuida do financeiro (o dono) não precisa.

| Situação | Resultado |
|---|---|
| Operadora sem o gerente, ou senha errada | Recusado; a janela fica aberta para tentar de novo |
| Sem motivo | Recusado |
| Item com adicionais (ex.: porção com molho) | Sai inteiro; parte dele é recusada |
| A comanda fica vazia | A comanda fecha e o caixa volta ao balcão |
| Venda de balcão, ainda não paga | O x continua livre, como antes: o cliente ainda não consumiu |

Tudo fica no log: o que saiu, o valor, o motivo, quem pediu e quem autorizou. A regra está no servidor: mesmo chamando a rota comum da venda, a operadora não tira nem diminui item de uma comanda aberta.

### Aviso de comandas abertas ao encerrar o caixa

Ao abrir "Encerrar", o caixa mostra as comandas ainda sem pagar e quanto somam. Para encerrar assim mesmo, é preciso marcar **"Deixar abertas e encerrar mesmo assim"**; a escolha vai para o log. Serve para a troca de turno, quando a comanda passa para o próximo caixa. A comanda vazia (cartão bipado sem nada lançado) não conta.

### Teclas rápidas

Uma faixa abaixo do campo de leitura, com até 8 botões para o que não tem código de barras: gelo, copo, caldo, porções.
- **Lançar:** toque no botão ou aperte **Alt e o número** (Alt+1 a Alt+8). Para mais de um, digite a quantidade antes: **3** e Alt+1 lança 3.
- **Configurar:** "Criar teclas rápidas" ou "Editar teclas" abre a lista de produtos do cardápio. Mudar pede o gerente (quem cuida do estoque não precisa).
- As teclas valem para todos os caixas da loja e ficam no banco, junto com o backup.

**Como foi feito:**
- Tirar item é uma rota da comanda (`POST /api/comandas/{numero}/retirar`), que trava a conta, confere o gerente e o motivo e grava o log. A rota comum da venda passou a recusar tirar ou diminuir item de comanda aberta para quem não cuida do financeiro.
- O encerramento devolve "conflito" (409) com a lista quando há comanda com itens, até vir a confirmação.
- As teclas ficam numa tabela nova (`pdv_teclas_rapidas`, migração `V20261110_02`) e são lançadas pelo mesmo caminho do código bipado.

**Testado:**
- **Unidade:** quem pode tirar item e quem autoriza, o texto do aviso de comandas, quem muda as teclas e o que vale para quem entrou por PIN.
- **Banco:** a rota comum recusa tirar e diminuir item de comanda; tirar sem gerente, com senha errada, sem motivo e mais do que tem é recusado; 1 de 3 latas sai com o gerente; o gerente tira o resto sem senha e a comanda fecha. Encerrar com comanda aberta dá o aviso e passa com a confirmação. Teclas: a operadora sozinha não muda, com o gerente salva, só há 8 teclas.
- **Navegador real:** o dono cria a tecla Alt+1 com o Gelo e lança 2 gelos na comanda 9 digitando 2 e Alt+1. A Ana abre a comanda 9 em outra tela e tenta tirar 1 gelo: sem motivo, sem gerente e com senha errada é recusado; com o gerente sai 1 gelo e o log registra o motivo e quem autorizou. O Limpar também pede o gerente. O dono encerra o caixa 2 com a comanda 9 aberta: o aviso aparece, a confirmação é exigida e vai para o log.
- **Windows:** o fechamento depois do roteiro avisa da comanda 9 e só encerra com a confirmação.

**Limites conhecidos:**
- **Diminuir a quantidade pela tela de edição do item**, numa comanda, também é recusado para a operadora, com o aviso de usar o x. É o mesmo controle, por outro caminho.
- **Item quebrado ou derramado** sai da comanda, mas não vira perda no estoque sozinho: lance a perda no Estoque.
- **As teclas usam Alt.** Se algum programa do notebook usar Alt+número, toque nos botões.

---

## 31. Comanda pelo celular do atendente (10/11/2026)

**Para que serve:** o atendente anota na mesa e lança na hora, pelo próprio celular, sem ir ao caixa. O caixa vê a comanda atualizada e cobra no fim, como antes.

**Como funciona no celular:**
1. Abre o endereço da loja (QR no Dashboard), toca no nome e digita o PIN.
2. Vê as comandas abertas, com total e há quanto tempo estão abertas. Abre uma pelo número ou lendo o cartão com a câmera.
3. Toca nos produtos (busca pelo nome ou teclas rápidas, as mesmas do caixa) e monta o pedido. Antes de lançar, ajusta as quantidades com − e + à vontade.
4. Toca em **Lançar**: o pedido entra de uma vez. O mesmo produto soma na mesma linha da comanda.

| Pode pelo celular | Fica só no caixa |
|---|---|
| Ver comandas e o que já foi lançado | Tirar item já lançado (com o gerente) |
| Abrir comanda e lançar pedido | Receber, dividir, transferir, desconto |
| Ler o cartão com a câmera | Todo o resto do sistema |

**Segurança (decisão de 10/11/2026: conexão segura):**
- O sistema completo continua aceitando só o próprio notebook. O celular usa outra porta (8443), com conexão segura (HTTPS), que só serve a tela de comanda: qualquer outro endereço responde "não encontrado", inclusive o login por senha.
- O atendente entra só por PIN, e a sessão fica restrita à área de caixa. 5 PINs errados bloqueiam aquela pessoa por 5 minutos, e cada erro fica no log.
- **Certificado da loja:** o próprio sistema cria uma autoridade só desta loja, que se instala uma vez em cada celular. Quando o roteador troca o endereço do notebook, o sistema refaz o certificado do servidor, assinado por ela, e o celular continua confiando. Sem instalar, funciona tocando em "Continuar" no aviso do navegador.
- O firewall do Windows libera a porta só para a rede local (`Liberar celular.cmd`, pede administrador uma vez).

**Como foi feito:**
- Um segundo conector do Tomcat com TLS, ligado por `stockos.celular.porta` e `stockos.celular.pasta`. O pacote da loja liga sozinho.
- O certificado é feito com o `keytool` do próprio Java, que vem no pacote.
- O filtro `PortaCelularFilter` só deixa passar a lista fechada de endereços da tela de comanda.
- `POST /api/comandas/{numero}/lancar` lança o pedido inteiro numa operação, abrindo a comanda se preciso. `GET /api/comandas/{numero}` mostra o que já foi lançado.
- Tela própria (`celular.html`), feita para o dedo. A câmera usa o ZXing 0.21.3 (licença Apache 2.0), embutido no sistema para funcionar sem internet. O mesmo ZXing desenha o QR do Dashboard.

**Testado:**
- **Unidade:** o que a porta do celular deixa passar e o que recusa (login por senha, tirar item, rota da venda, `..` no caminho); o certificado da loja criado de verdade, com a cadeia conferida e uma conexão TLS que confia só nele.
- **Banco:** o primeiro pedido abre a comanda; o segundo soma na mesma linha; pedido vazio é recusado.
- **Navegador real como celular (Pixel 7):**
  - Conexão que confia só no certificado da loja passa, e sem ele é recusada.
  - Pela porta do celular, Estoque, produtos, login por senha e rota da venda dão "não encontrado".
  - A Ana erra o PIN (fica no log), entra e vê a comanda 9.
  - Abre a comanda 11 pelo número, monta 2 Skol e 1 gelo (tira um gelo do pedido antes) e lança.
  - Depois lança mais 1 Skol, que soma na mesma linha. Sai.
  - O caixa vê a comanda 11 com o total certo e o log com quem lançou.
- **Windows:** a porta do celular abre pela rede e a do sistema não; o resto responde 404; `Liberar celular` cria a regra só para a rede local; o roteiro do celular roda pelo endereço de rede do notebook.

**Limites conhecidos:**
- **Itens com adicionais obrigatórios** (ex.: porção que exige escolher o molho) ainda não se montam no celular: o lançamento é recusado com a mensagem do sistema. Lance esses no caixa.
- **iPhone:** instalar o certificado tem mais passos (perfil + confiança). Melhorado depois, na seção 32.
- **Antivírus que inspeciona conexões** no notebook (visto com o Norton no computador de desenvolvimento) não afeta o celular, que conecta direto no notebook.
- **Pedido para a cozinha:** feito depois, na seção 33.

---

## 32. iPhone no celular do atendente (10/11/2026)

**O que mudou:** o iPhone tem regras próprias para confiar num certificado e só instala perfil pelo Safari. Agora:
- **Perfil próprio do iPhone** (`/certificado.mobileconfig`): o mesmo certificado da loja, com o nome "StockOS - celular do atendente". Baixar de novo não duplica.
- **Guia de acordo com o aparelho:** a tela mostra só o passo a passo do iPhone ou só o do Android. No Chrome ou Firefox do iPhone, ela pede para abrir no Safari e esconde o botão do perfil.
- **Regras da Apple conferidas no teste:** assinatura SHA-256 ou maior, chave RSA de 2048, validade de no máximo 825 dias (o nosso é de 397) e uso marcado como servidor.

**Testado:**
- **Unidade:** as regras da Apple no certificado, e um perfil que é um XML válido e sempre o mesmo.
- **Navegador:** a tela roda no motor do Safari (WebKit) como iPhone 13. Mostra o guia do iPhone e baixa o perfil; a Ana entra pelo PIN e lança 1 Skol na comanda 12, e o caixa vê R$ 4,50. No "Chrome do iPhone", a tela pede o Safari.

**Limites:**
- **O teste não instala o perfil num iPhone de verdade.** Isso só se confere na loja.
- **Atalho na tela inicial do iPhone:** ele guarda a entrada separada do Safari, então pede o PIN uma vez a mais.

---

## 33. Pedido impresso para a cozinha (10/11/2026)

**Para que serve:** o atendente lança a porção pelo celular e a cozinha recebe o papel na hora, sem alguém levar o pedido.

**Quando sai o papel:**

| Momento | O que sai |
|---|---|
| Atendente lança pelo celular | O que é da cozinha nesse pedido |
| Caixa guarda a comanda (Esc) ou troca de comanda | O que é da cozinha e ainda não tinha ido |
| Venda paga (balcão ou comanda) | O que ainda não tinha ido |
| Item já impresso sai da comanda (com o gerente) | Papel **CANCELAR** com o item |
| Parte de um item muda de comanda | Nada: o que já foi impresso vai junto |

O papel tem em letra grande "COZINHA" e o número da comanda (ou "BALCÃO - VENDA n"), a hora, quem pediu, os itens com quantidade, os adicionais e a observação.

**O que vai para a cozinha:** as categorias marcadas na configuração. Sem nada marcado, os itens cadastrados como "preparado sob demanda". Bebida não vai.

**Configuração** (botão **Cozinha** no caixa, para quem cuida do estoque ou do financeiro): impressora do Windows, papel de 80 ou 58 mm, categorias e "Imprimir teste". Quem entrou por PIN vê os pedidos e reimprime, mas não configura.

**Como foi feito:**
- A impressão é feita pelo servidor, direto na térmica pelo Windows (comandos ESC/POS), sem navegador nem janela de impressão. Funciona com a cozinha longe do caixa.
- O texto sai sem acento, porque cada marca de térmica usa uma tabela de letras diferente. A comanda sai em letra dupla e o papel é cortado no fim.
- Cada pedido fica gravado (tabelas `pedidos_cozinha` e `pedidos_cozinha_itens`, migração `V20261110_03`), com quanto de cada item já foi. Por isso nada sai em dobro e o cancelamento sabe o que avisar.
- Uma tarefa imprime a cada 2 segundos o que está esperando. Com a impressora desligada ou sem papel, tenta de novo: depois de 5 s, 10 s, até 1 minuto entre tentativas.
- O botão **Cozinha** mostra em vermelho quantos pedidos não saíram, também dentro do PDV.

**Testado:**
- **Unidade:** o que vai para a cozinha. O texto do papel, com comanda, quem pediu, adicionais, observação, quantidade fracionada e cancelamento. Os comandos da térmica: início, letra dupla, só ASCII e corte. E quem pode ver, reimprimir e configurar.
- **Navegador**, com o papel gravado em arquivo no lugar da térmica:
  - O dono marca "Porções" e salva.
  - A Ana lança 2 porções e 1 Skol na comanda 13 pelo celular; o papel sai só com as porções.
  - Tirar 1 porção imprime "CANCELAR".
  - A janela mostra os pedidos como impressos, e o "Reimprimir" gera outro papel.

**Limites conhecidos:**
- **Só impressora térmica (ESC/POS).** Impressora comum, de folha, não está prevista.
- **Não foi testado numa térmica de verdade.** No Windows do GitHub não há impressora; o papel é conferido em arquivo. Na loja: "Imprimir teste" na configuração.
- **Sem acento no papel**, de propósito (ver acima).
- **Uma impressora de cozinha só.** Cozinha e bar separados (dois papéis) ainda não.

---

## 34. Tela Comandas, relógio da loja e acertos (10/11/2026, branch feat/caixas-pdv-20261110)

**Por quê:** as comandas só apareciam dentro do PDV (bipar o cartão, `c15` ou F7). Quem abria o sistema não achava onde elas estavam.

**Tela Comandas** (menu lateral, logo abaixo de Caixas PDV; também para quem entrou por PIN):
- As comandas abertas, atualizando a cada 10 segundos: número, total, itens e há quanto tempo estão abertas.
- Resumo: quantas estão abertas, quanto há a receber, itens lançados e a mais antiga.
- Tocar numa comanda mostra o que foi lançado. **Cobrar no caixa** abre o PDV no caixa aberto, já com a comanda. Sem caixa aberto, avisa para abrir um.
- **+ Nova comanda:** pede o número do cartão, já sugerindo o primeiro livre, e recusa número já aberto. Com caixa aberto, vai direto para o PDV já na comanda nova, para bipar o pedido. Sem caixa aberto, abre a comanda vazia ali mesmo; os itens entram pelo celular ou depois no caixa.
- Um quadro "Como as comandas funcionam" explica como abrir, lançar e cobrar.

**Relógio da loja no Dashboard:** hora e data no fuso da loja (Brasília), andando a cada segundo. A hora vem do sistema (`GET /api/sistema/hora`) e acerta de novo a cada 10 minutos. O que aparece não depende do fuso configurado no Windows. Se o relógio do próprio notebook estiver errado, o horário fica errado também: sistema e Windows usam o mesmo relógio.

**Acertos na mesma branch:**
- Os botões Produtos/Pedido do PDV no celular estavam sem estilo no computador. Agora ficam escondidos no computador e funcionam no celular.
- O sistema passou a assumir o fuso da loja ao ligar. Antes, com o Windows em outro fuso, o caixa e as comandas caíam em dias diferentes perto da meia-noite, e o relatório contava 0 comandas pagas.

**Testado:**
- No sistema rodando, com o pacote da loja: o menu leva à tela, aparecem 3 comandas, o detalhe da 12 mostra o lançado, e "Cobrar no caixa" abre o PDV na comanda 12. O relógio anda e mostra "Horário de Brasília".
- **Navegador (GitHub):** o relógio anda e bate com Brasília. Pelo menu Comandas, a comanda 9 tem o total certo e os itens, e "Cobrar no caixa" abre o PDV do caixa 3 já nela.

---

## Fontes

- [Guia Completo da NFC-e 2026 (Contmatic)](https://simplifique.contmatic.com.br/blogs/guia-completo-nfc-e-2026)
- [São Paulo: NFC-e obrigatória em 2026 (Crypto ID)](https://cryptoid.com.br/e-commerce-e-varejo-retail/nfc-e-se-torna-obrigatoria-em-2026/)
- [NFC-e em SP: obrigatoriedade, CSC e contingência](https://granmoney.com/nfc-e/sp)
- [NFC-e em MG: obrigatoriedade, CSC e contingência](https://granmoney.com/nfc-e/mg)
- [CONFAZ revoga proibição de CNPJ na NFC-e (LegisWeb)](https://www.legisweb.com.br/noticia/?id=33182)
- [Reforma Tributária: campos IBS/CBS obrigatórios em 2026 (LegisWeb)](https://www.legisweb.com.br/noticia/?id=31719)
- [Reforma: cronograma de adequações (IOB)](https://noticias.iob.com.br/reforma-cronograma-de-adequacoes/)
- [NFC-e em 2026 — IBS, CBS e Imposto Seletivo (NotaGateway)](https://notagateway.com.br/blog/nfc-e-em-2026/)
- [Leitura de etiqueta de balança no PDV (Alterdata)](https://ajuda.alterdata.com.br/pdvbase/saiba-como-configurar-o-sistema-para-leitura-de-etiqueta-de-balancas-37261348.html)
- [Nomenclatura de código de barras (Odoo)](https://www.odoo.com/documentation/16.0/pt_BR/applications/inventory_and_mrp/barcode/operations/barcode_nomenclature.html)
- [Pix integrado ao TEF (Woovi)](https://woovi.com/en/pix/pix-tef/)
- [Sistema de frente de caixa PDV: recursos (Omie)](https://www.omie.com.br/blog/sistema-de-frente-de-caixa-pdv-recursos-para-escolher/index.md)
- [Smart TEF integrada ao PDV (Tray)](https://tray.com.br/escola/tray-lanca-maquininha-smart-tef/)
- [MEI dispensado de emitir nota para pessoa física a partir de 2027 (Mix Vale)](https://www.mixvale.com.br/2026/10/23/mei-fica-dispensado-de-emitir-nota-fiscal-eletronica-para-pessoa-fisica-a-partir-de-janeiro-de-2027/)
- [NFC-e para MEI: regras 2026 (Nota Ágil)](https://notaagil.com.br/nfce-para-mei)
- [Leitores de código de barras: preços (BZ Tech)](https://www.bztech.com.br/leitores/leitor-de-codigo-de-barras)
- [Leitores com suporte (BZ Tech)](https://www.bztech.com.br/leitores/leitor-de-codigo-de-barras/com-suporte)
- [Leitores fixos (BZ Tech)](https://www.bztech.com.br/leitores/leitor-de-codigo-de-barras/fixo)
- [Leitores (Manchester Automação)](https://www.manchesterautomacao.com.br/leitor-de-codigo-de-barras)
- [Melhores leitores 2026 (my-best)](https://br.my-best.com/18709)
- [cEAN/cEANTrib obrigatórios na NF-e (Systax)](https://www.systax.com.br/?p=5066)
- [Importação de notas e fator da caixa (Trier Sistemas)](https://documentacao.triersistemas.com.br/entrada-de-mercadorias/importação-de-notas)
- [Compatibilidade da BarcodeDetector API (MDN/Chromium)](https://chromium.googlesource.com/external/github.com/mdn/browser-compat-data/+/HEAD/api/BarcodeDetector.json)
- [Bluesoft Cosmos API (wrapper npm, não oficial)](https://www.npmjs.com/package/bluesoft-cosmos-api)
