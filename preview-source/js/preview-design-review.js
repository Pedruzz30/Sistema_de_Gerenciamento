/* Preview-only opt-in for the redesigned StockOS CSS.
 * The real Spring frontend and standard preview remain unchanged by default. */
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
  if (choice === 'novo') localStorage.setItem(KEY, 'novo');
  if (choice === 'original') localStorage.removeItem(KEY);
  const active = localStorage.getItem(KEY) === 'novo';

  if (active) {
    // CSS loads AFTER the classic styles, making the redesign explicit and reversible.
    for (const file of files) {
      const link = document.createElement('link');
      link.rel = 'stylesheet';
      link.href = '/css/' + file + '.css';
      link.dataset.stockosDesign = 'novo';
      document.head.appendChild(link);
    }
  }

  document.addEventListener('DOMContentLoaded', () => {
    const control = document.createElement('button');
    control.type = 'button';
    control.textContent = active ? 'Visual novo (teste) · Voltar ao original' : 'Testar visual novo';
    control.setAttribute('aria-label', control.textContent);
    control.style.cssText = [
      'position:fixed','top:12px','right:12px','z-index:2147483645',
      'background:#13263d','color:white','border:1px solid #8196b3',
      'border-radius:8px','padding:9px 12px','font:600 12px system-ui',
      'cursor:pointer','box-shadow:0 4px 18px #0005'
    ].join(';');
    control.addEventListener('click', () => {
      if (active) localStorage.removeItem(KEY);
      else localStorage.setItem(KEY,'novo');
      location.reload();
    });
    document.body.appendChild(control);
  });
  window.StockOsDesignReview = { mode: active ? 'novo' : 'original', styles: files.slice() };
})();
