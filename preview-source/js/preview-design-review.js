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
