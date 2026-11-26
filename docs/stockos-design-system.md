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
