# StockOS — Design system (incremento 1)

Objetivo: aproximar a experiência de gestão da referência aprovada, mantendo os fluxos operacionais, paleta, permissões e dados atuais.

## Tokens

A camada nova usa os tokens de cor existentes (`--paper-raised`, `--paper-sunk`, `--ink`, `--rule`, `--accent`). Não introduz uma nova paleta.

| Token | Valor | Uso |
| --- | --- | --- |
| --stockos-panel-radius | 10px | Painéis, tabelas e cartões |
| --stockos-control-radius | 8px | Botões e campos |
| --stockos-dialog-radius | 14px | Modais de gestão |
| --stockos-panel-gap | 14px | Separação de painéis |
| --stockos-panel-padding | 18px | Espaçamento interno |
| --stockos-control-height | 40px | Controles; 44px para filtros móveis |
| --stockos-section-title-size | 15px | Títulos de seção |
| --stockos-table-heading-size | 11px | Cabeçalhos de tabela |
| --stockos-table-text-size | 13px | Corpo de tabela |

## Regras

- O seletor `.app.stockos-shell-v2` delimita a experiência nova. Modais externos são delimitados com `body:has(> .app.stockos-shell-v2)`.
- Preservar IDs, classes de comportamento, handlers, filtros e permissões.
- Tabelas devem ter rolagem própria ou cartões móveis; nunca forçar rolagem horizontal do documento.
- Usar bordas discretas, títulos em sentence case, valores tabulares e foco perceptível.
- Não alterar dimensões operacionais do PDV nem os fluxos de leitor de código de barras.
- Sem indicadores inventados, sem ocultar erros como zero e sem simular notificações reais.
- Manter a comparação original funcional.

## Aplicação inicial

Dashboard, Estoque e Cardápio. Estoque mantém cartões móveis existentes, busca e dois grupos de filtros identificados. Cardápio mantém tabelas inteiras em notebooks e usa duas colunas apenas em workstations largas. A configuração do celular tem acesso direto no cabeçalho quando disponível; o QR Code continua no painel original.

## Validação

Executar `npm run test:shell-redesign`, `npm run test:preview`, `npm run test:design`, `npm run test:spa` e o E2E real. Conferir capturas de desktop e mobile. A aprovação visual humana continua necessária antes do merge.


## Incremento 2 — Caixas, PDV e Comandas

- Caixas: ação PDV em destaque, distinção de dinheiro em espécie e pagamentos eletrônicos, dados de sessões e ações originais preservados.
- PDV real: somente apresentação (tipografia, bordas, foco e superfície do cabeçalho); manter dimensões, painéis, atalhos e fluxo de cobrança.
- Comandas: seleção anunciada via aria-pressed, foco preservado em atualizações e acesso direto ao detalhe no mobile. Ignorar respostas atrasadas de outra comanda.
- Validar no E2E: abrir caixa, visibilidade do PDV, scanner e atalhos existentes, seleção da comanda no celular, cobrança, divisão, desconto e estorno.


## Incremento 3 — Relatórios e administração

- Fornecedores, notas, cotações, funcionários e logs: mesma hierarquia de painéis, tabelas, controles, abas e títulos. Mantidos os layouts específicos, inclusive tabelas responsivas e etapas de importação/entrada de nota.
- Relatórios: página independente para impressão, com a mesma linguagem visual em tela; filtros acessíveis, indicadores e tabelas em painéis. A impressão não recebe a camada de estilo de tela.
- Relatórios: bloquear a impressão enquanto a consulta não termina, remover números antigos durante uma nova consulta e ignorar respostas de consultas anteriores.
- Nenhuma modificação de contrato da API, permissões, cálculo financeiro, persistência ou geração de documentos.

## Refinamento de homologação

- Comandas: ação de cobrança antes da lista de itens; compactação para notebooks de baixa altura. Não cobrar com dados de outra comanda ou antes de confirmar o detalhe selecionado. Respostas atrasadas de requisições concorrentes são ignoradas.
- O E2E mantém a exigência de visibilidade da ação principal em 1366 × 650, além dos testes em 390 × 844.

### Matriz visual real

O E2E autenticado verifica os 10 módulos com App Shell em 1366 × 650, 768 × 1024 e 390 × 844. Cada combinação exige conteúdo dentro da área útil, ausência de rolagem horizontal do documento, rota ativa correta e abertura/fechamento acessíveis do menu no mobile/tablet. Capturas ficam nos artefatos de CI; não substituem inspeção visual humana.

### Consistência de atualização e CSS

O dashboard distingue falha de consulta de estoque/movimentações de listas vazias e ignora respostas de atualizações anteriores. Consolidadas regras sobrescritas do próprio redesign (ícones, colunas de gráfico e ordem do cabeçalho); mantida a especificidade necessária contra CSS legado. Evitar uma refatoração ampla antes da matriz de navegador passar.

## Reconstrução fiel à referência

Quatro painéis explícitos no HTML: evolução de vendas (7/30 dias ou hoje), quatro KPIs, ranking diário, distribuição de pagamentos e estoque. Ações operacionais e ferramentas de demonstração abaixo dos painéis. Comparativo somente quando o período anterior existe; sem inventar séries ausentes. O backend usa os relatórios existentes; a demonstração agrega os registros fictícios já gravados.

## Gráfico principal de vendas

Chart.js existente, carregado após a primeira renderização local SVG. Eixos monetários arredondados, barras agrupadas e centralizadas, rótulos automáticos, tooltip com data exata de ambos os períodos, tabela acessível e fallback sem dependência de rede. Mostrar variação percentual somente quando os períodos têm registros completos e a base anterior é positiva; o dia atual está em andamento. Descartar renderizações atrasadas e destruir a instância ao sair da rota ou alternar período.

## Painel de indicadores

Grade 2×2 com título e ícone na mesma linha, número central e contexto factual. Cores semânticas apenas para alertas e caixas em operação; não usar setas de tendência sem comparação real. Ticket médio usa o valor da API. Erros de consulta não equivalem a zero.

## Ranking diário de produtos

Top 5 com posição, ordenação por quantidade ou receita, participação na receita de todos os produtos informados e total do Top 5. Não confundir receita de itens com recebimentos líquidos do caixa. Ranking parcial identificado; sem percentual quando a base é não positiva ou possui valores negativos. Sem consultas adicionais.

## Distribuição da operação

Pagamentos por valor líquido, ordenados, com percentual, cores fixas por forma e indicação de maior concentração. Estoque ativo separado em adequado, baixo/moderado, crítico/sem estoque e sem classificação quando necessário; verde/âmbar/vermelho semântico e links para filtros existentes. Sem percentual ou anel para valores líquidos negativos ou total não positivo; preservar valores exatos e estados indisponíveis. Sem novos endpoints.

## Próximas ações

Fila compacta ordenada por urgência. A contagem representa grupos de prioridades pendentes (alertas e consultas indisponíveis), não o número de produtos nem os caixas abertos. Caixas em operação e estoque sem alertas são atalhos informativos. Preservar filtros e permissões existentes.

## Detalhes operacionais secundários

Movimentações de hoje com estado vazio compacto e acesso ao estoque; limite de 10 registros recentes. Resumo repetido substituído na experiência nova por sessões de caixa abertas ou com abertura/encerramento hoje. Até oito sessões, operador, estado, contagem de vendas e total líquido acumulado por sessão, não vendas de hoje. Dados ausentes permanecem indisponíveis. Preservar modo original e permissões.

## Central de alertas de estoque

Contagens por grupo, filtros de gravidade, pesquisa sem distinção de acentos, páginas de seis produtos e acesso aos filtros existentes de estoque. Prioridade: sem estoque, crítico, baixo, moderado; dentro do nível, menor cobertura do mínimo primeiro. Déficit = máximo de zero e mínimo menos quantidade atual; valores desconhecidos não viram zero. Percentual representa cobertura do mínimo, não previsão de compra. Superfície neutra, cor restrita à situação. Sem consultas ou permissões novas.

## PDV de demonstração — terminal de vendas

Novo terminal em duas colunas inspirado na referência: busca por nome ou código, categorias reais, cartões de produtos, saldo disponível, carrinho, quantidade, subtotal, pagamento fictício e troco. No celular, painéis Produtos/Pedido. Transação permanece no endpoint atômico da demonstração; retentativas ambíguas preservam o payload. Não altera o PDV real, nem simula desconto, autorização, TEF ou emissão fiscal. O modo original mantém o modal antigo; o caixa completo continua disponível separadamente.
