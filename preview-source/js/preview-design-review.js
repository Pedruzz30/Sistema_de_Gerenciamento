/* Netlify/preview-only StockOS redesign, enabled by default.
 * Original theme can be restored with ?design=original or the on-page toggle.
 * Spring frontend source remains unchanged. */
(() => {
  'use strict';
  const PARAM = 'design';
  const KEY = 'stockos-preview-design-review-v1';
  const files = [
    'tokens','reset','typography','global','layout','sidebar','topbar',
    'buttons','forms','table','badges','sheet','modal','toolbar','kpi',
    'pages','estoque','cardapio','pdv','react-app','compat'
  ];
  const url = new URL(location.href);
  const choice = url.searchParams.get(PARAM);
  if (choice === 'novo' || choice === 'original') localStorage.setItem(KEY, choice);
  const active = localStorage.getItem(KEY) !== 'original';

  // A página Java carrega o refinamento diretamente; na prévia em modo original
  // retiramos esse link para preservar a comparação visual sem CSS novo.
  if (!active && /\/(?:logs|logs\.html)$/.test(location.pathname)) {
    document.querySelectorAll('link[href="/css/logs-refinement.css"]').forEach(link => link.remove());
  }

  if (!active && /\/(?:funcionarios|funcionarios\.html)$/.test(location.pathname)) {
    document.querySelectorAll('link[href="/css/funcionarios-refinement.css"]').forEach(link => link.remove());
  }

  if (!active && /\/(?:cotacoes|cotacoes\.html)$/.test(location.pathname)) {
    document.querySelectorAll('link[href="/css/cotacoes-refinement.css"]').forEach(link => link.remove());
  }

  if (!active && /\/(?:caixas|caixas\.html)$/.test(location.pathname)) {
    document.querySelectorAll('link[href="/css/caixas-refinement.css"]').forEach(link => link.remove());
  }

  if (!active && /\/(?:notas|notas\.html)$/.test(location.pathname)) {
    document.querySelectorAll('link[href="/css/notas-refinement.css"]').forEach(link => link.remove());
  }

  if (!active && /\/(?:fornecedores|fornecedores\.html)$/.test(location.pathname)) {
    document.querySelectorAll('link[href="/css/fornecedores-refinement.css"]').forEach(link => link.remove());
  }

  if (!active && /\/(?:cardapio|cardapio\.html)$/.test(location.pathname)) {
    document.querySelectorAll('link[href="/css/cardapio-refinement.css"]').forEach(link => link.remove());
  }

  if (!active && /\/(?:estoque|estoque\.html)$/.test(location.pathname)) {
    document.querySelectorAll('link[href="/css/estoque-refinement.css"]').forEach(link => link.remove());
  }

  if (!active && /\/(?:dashboard|dashboard\.html)$/.test(location.pathname)) {
    document.querySelectorAll('link[href="/css/dashboard-refinement.css"]').forEach(link => link.remove());
  }

  if (active) {
    // Redesign CSS loads after the classic styles only in the preview; it remains reversible.
    for (const file of files) {
      const link = document.createElement('link');
      link.rel = 'stylesheet';
      link.href = '/css/' + file + '.css';
      link.dataset.stockosDesign = 'novo';
      document.head.appendChild(link);
    }
    // Restore the warm/paper color scheme from the original site without losing
    // the redesigned layout, spacing or components.
    const palette = document.createElement('link');
    palette.rel = 'stylesheet';
    palette.href = '/css/palette-original.css';
    palette.dataset.stockosPalette = 'original';
    document.head.appendChild(palette);
    // Logs: camada exclusiva desta tela, após a paleta original.
    if (/\/(?:logs|logs\.html)$/.test(location.pathname)) {
      const logsStyles = document.createElement('link');
      logsStyles.rel = 'stylesheet';
      logsStyles.href = '/css/logs-refinement.css';
      logsStyles.dataset.stockosLogsRefinement = 'true';
      document.head.appendChild(logsStyles);
    }
    // Funcionários: refinamento isolado carregado após a paleta original.
    if (/\/(?:funcionarios|funcionarios\.html)$/.test(location.pathname)) {
      const employees = document.createElement('link');
      employees.rel = 'stylesheet';
      employees.href = '/css/funcionarios-refinement.css';
      employees.dataset.stockosFuncionariosRefinement = 'true';
      document.head.appendChild(employees);
    }
    // Cotações: CSS específico desta tela, após a paleta original.
    if (/\/(?:cotacoes|cotacoes\.html)$/.test(location.pathname)) {
      const quotes = document.createElement('link');
      quotes.rel = 'stylesheet';
      quotes.href = '/css/cotacoes-refinement.css';
      quotes.dataset.stockosCotacoesRefinement = 'true';
      document.head.appendChild(quotes);
    }
    // Caixas PDV — estilo isolado depois da paleta do site.
    if (/\/(?:caixas|caixas\.html)$/.test(location.pathname)) {
      const boxes = document.createElement('link');
      boxes.rel = 'stylesheet';
      boxes.href = '/css/caixas-refinement.css';
      boxes.dataset.stockosCaixasRefinement = 'true';
      document.head.appendChild(boxes);
    }
    // Notas: CSS só desta página, após a paleta de comparação.
    if (/\/(?:notas|notas\.html)$/.test(location.pathname)) {
      const notes = document.createElement('link');
      notes.rel = 'stylesheet';
      notes.href = '/css/notas-refinement.css';
      notes.dataset.stockosNotasRefinement = 'true';
      document.head.appendChild(notes);
    }
    // Fornecedores: refinamento apenas nesta tela, depois da paleta restaurada.
    if (/\/(?:fornecedores|fornecedores\.html)$/.test(location.pathname)) {
      const suppliers = document.createElement('link');
      suppliers.rel = 'stylesheet';
      suppliers.href = '/css/fornecedores-refinement.css';
      suppliers.dataset.stockosFornecedoresRefinement = 'true';
      document.head.appendChild(suppliers);
    }
    // Cardápio: CSS localizado após a paleta para comparar sem alterar outras telas.
    if (/\/(?:cardapio|cardapio\.html)$/.test(location.pathname)) {
      const menuStyle = document.createElement('link');
      menuStyle.rel = 'stylesheet';
      menuStyle.href = '/css/cardapio-refinement.css';
      menuStyle.dataset.stockosCardapioRefinement = 'true';
      document.head.appendChild(menuStyle);
    }
    // Estoque: styles after preview palette, preserving original comparison.
    if (/\/(?:estoque|estoque\.html)$/.test(location.pathname)) {
      const stock = document.createElement('link');
      stock.rel = 'stylesheet';
      stock.href = '/css/estoque-refinement.css';
      stock.dataset.stockosEstoqueRefinement = 'true';
      document.head.appendChild(stock);
    }
    // Refinamento específico do Dashboard, após a paleta restaurada.
    if (/\/(?:dashboard|dashboard\.html)$/.test(location.pathname)) {
      const sheet = document.createElement('link');
      sheet.rel = 'stylesheet';
      sheet.href = '/css/dashboard-refinement.css';
      sheet.dataset.stockosDashboardRefinement = 'true';
      document.head.appendChild(sheet);
    }
  }

  document.addEventListener('DOMContentLoaded', () => {
    const control = document.createElement('button');
    control.type = 'button';
    control.textContent = active ? 'Visual novo · Ver original' : 'Visual original · Ver novo';
    control.setAttribute('aria-label', control.textContent);
    control.style.cssText = [
      'position:fixed','top:12px','right:12px','z-index:2147483645',
      'background:#13263d','color:white','border:1px solid #8196b3',
      'border-radius:8px','padding:9px 12px','font:600 12px system-ui',
      'cursor:pointer','box-shadow:0 4px 18px #0005'
    ].join(';');
    control.addEventListener('click', () => {
      localStorage.setItem(KEY, active ? 'original' : 'novo');
      location.reload();
    });
    document.body.appendChild(control);
  });
  window.StockOsDesignReview = { mode: active ? 'novo' : 'original', styles: files.slice() };
})();
