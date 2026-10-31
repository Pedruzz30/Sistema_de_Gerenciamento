/* StockOS — SPA shell: the nine management views.
 * Keep the authenticated sidebar/header alive, reuse DOM per route,
 * and never evaluate scripts fetched from an HTML response.
 * Authentication, login and unknown routes retain traditional navigation.
 */
(() => {
  'use strict';

  const ROUTES = {
    dashboard: { href: '/dashboard.html', script: '/js/dashboard.js', css: '/css/dashboard-refinement.css', bodyClass: 'dashboard-page' },
    estoque: { href: '/estoque.html', script: '/js/estoque.js', css: '/css/estoque-refinement.css', bodyClass: 'estoque-page' },
    cardapio: { href: '/cardapio.html', script: '/js/cardapio.js', css: '/css/cardapio-refinement.css', bodyClass: 'cardapio-page' },
    fornecedores: { href: '/fornecedores.html', script: '/js/fornecedores.js', css: '/css/fornecedores-refinement.css', bodyClass: 'fornecedores-page' },
    notas: { href: '/notas.html', script: '/js/notas.js', css: '/css/notas-refinement.css', bodyClass: 'notas-page' },
    caixas: { href: '/caixas.html', script: '/js/caixas.js', css: '/css/caixas-refinement.css', bodyClass: 'caixas-page' },
    cotacoes: { href: '/cotacoes.html', script: '/js/cotacoes.js', css: '/css/cotacoes-refinement.css', bodyClass: 'cotacoes-page' },
    funcionarios: { href: '/funcionarios.html', script: '/js/funcionarios.js', css: '/css/funcionarios-refinement.css', bodyClass: 'funcionarios-page' },
    logs: { href: '/logs.html', script: '/js/logs.js', css: '/css/logs-refinement.css', bodyClass: 'logs-page' },
  };

  function routeFromUrl(href) {
    let url;
    try { url = new URL(href, location.href); } catch { return null; }
    if (url.origin !== location.origin) return null;
    const match = /^\/(dashboard|estoque|cardapio|fornecedores|notas|caixas|cotacoes|funcionarios|logs)(?:\.html)?\/?$/.exec(url.pathname);
    return match ? match[1] : null;
  }

  let active = routeFromUrl(location.href);
  if (!active || !document.querySelector('.app > .main.page') ||
      !document.querySelector('.app > .sidebar')) return;

  const main = document.querySelector('.app > .main.page');
  const header = main.querySelector('.topbar.header');
  if (!header) return;
  const pages = new Map();
  const loading = new Map();
  let navigationId = 0;

  function locateVisibleParts() {
    return {
      left: header.querySelector('.topbar-left'),
      right: header.querySelector('.topbar-right'),
      content: main.querySelector('.main-body.content')
    };
  }

  function rememberCurrent() {
    const parts = locateVisibleParts();
    const entry = pages.get(active) || {
      title: document.title,
      ...parts
    };
    // Include dialogs created dynamically (e.g. PDV configuration).
    entry.modals = Array.from(document.body.children).filter(el =>
      el.classList && el.classList.contains('modal-overlay'));
    entry.scrollY = window.scrollY;
    pages.set(active, entry);
    return entry;
  }

  function ensureStyles(route) {
    const spec = ROUTES[route];
    // In original-preview mode, the original page must remain CSS-only.
    if (window.StockOsDesignReview && window.StockOsDesignReview.mode === 'original') {
      return Promise.resolve();
    }
    const existing = Array.from(document.querySelectorAll('link[rel="stylesheet"]'))
      .some(link => new URL(link.href, location.href).pathname === spec.css);
    if (existing) return Promise.resolve();
    const link = document.createElement('link');
    link.rel = 'stylesheet';
    link.href = spec.css;
    link.dataset.stockosSpaStylesheet = route;
    return new Promise((resolve, reject) => {
      link.onload = resolve;
      link.onerror = () => reject(new Error('CSS indisponível: ' + route));
      document.head.appendChild(link);
    });
  }

  // Every route is a standalone HTML page as well as a SPA view.
  // Preload demo API shims before the route controller executes.
  function loadScript(src) {
    if (loading.has(src)) return loading.get(src);
    const script = document.createElement('script');
    script.src = src;
    script.async = false;
    const promise = new Promise((resolve,reject) => {
      script.onload = resolve;
      script.onerror = () => reject(new Error('Script indisponível: '+src));
      document.body.appendChild(script);
    }).catch(error => {
      loading.delete(src);
      script.remove();
      throw error;
    });
    loading.set(src,promise);
    return promise;
  }

  async function ensureScript(route) {
    if (window.StockOsPreview && window.StockOsPreview.mode === 'mock') {
      const demo = {
        notas: '/js/preview-notes.js',
        cardapio: '/js/preview-cardapio.js',
        caixas: '/js/preview-pdv.js'
      };
      if (demo[route]) await loadScript(demo[route]);
    }
    if (!window.StockOSPages || !window.StockOSPages[route]) {
      await loadScript(ROUTES[route].script);
    }
    if (route === 'caixas' && !window.StockOSPDV) {
      await loadScript('/js/pdv.js');
    }
    if (!window.StockOSPages || !window.StockOSPages[route]) {
      throw new Error('Módulo de tela não iniciou: ' + route);
    }
  }

  async function prepare(route) {
    if (pages.has(route)) return pages.get(route);
    const spec = ROUTES[route];
    const response = await fetch(spec.href, {
      headers: { Accept: 'text/html' },
      credentials: 'same-origin'
    });
    if (!response.ok) throw new Error('Página indisponível (' + response.status + ')');
    if (!(response.headers.get('content-type') || '').includes('text/html')) {
      throw new Error('Resposta não é HTML: ' + route);
    }
    const doc = new DOMParser().parseFromString(await response.text(), 'text/html');
    const container = doc.querySelector('.app > .main.page');
    const topbar = container && container.querySelector('.topbar.header');
    const content = container && container.querySelector('.main-body.content');
    const left = topbar && topbar.querySelector('.topbar-left');
    const right = topbar && topbar.querySelector('.topbar-right');
    if (!container || !left || !right || !content ||
        !doc.body.classList.contains(spec.bodyClass)) {
      throw new Error('HTML inesperado; navegação tradicional é necessária');
    }
    const entry = {
      title: doc.title,
      left, right, content,
      modals: Array.from(doc.body.children).filter(el =>
        el.classList && el.classList.contains('modal-overlay')),
      scrollY: 0
    };
    pages.set(route, entry);
    return entry;
  }

  function clearOpenOverlays() {
    document.body.classList.remove('sidebar-open');
    document.querySelectorAll('.modal-overlay.open, .sidebar-overlay.open')
      .forEach(el => el.classList.remove('open'));
    document.getElementById('stockos-preview-pdv')?.remove();
    const sidebar = document.querySelector('.app > .sidebar');
    if (sidebar) sidebar.classList.remove('open');
    const toggle = header.querySelector('.sidebar-toggle');
    if (toggle) toggle.setAttribute('aria-expanded', 'false');
  }

  function render(route, entry) {
    const previous = pages.get(active);
    const visible = locateVisibleParts();
    if (!previous || !visible.left || !visible.right || !visible.content) {
      throw new Error('Shell do StockOS indisponível');
    }

    clearOpenOverlays();

    // Keep the original header element and its mobile sidebar toggle alive.
    visible.left.replaceWith(entry.left);
    visible.right.replaceWith(entry.right);
    visible.content.replaceWith(entry.content);
    for (const modal of previous.modals) modal.remove();
    for (const modal of entry.modals) document.body.appendChild(modal);

    document.body.classList.remove('dashboard-page', 'estoque-page', 'cardapio-page', 'fornecedores-page', 'notas-page', 'caixas-page', 'cotacoes-page', 'funcionarios-page', 'logs-page');
    document.body.classList.add(ROUTES[route].bodyClass);
    document.title = entry.title;
    active = route;

    // Navigation and user session belong to shared.js and remain mounted.
    if (typeof renderSidebar === 'function') renderSidebar();
    if (typeof renderUserCard === 'function') renderUserCard();
    if (typeof aplicarIconesEstaticos === 'function') {
      aplicarIconesEstaticos(main);
    }
    for (const modal of entry.modals) {
      modal.querySelectorAll('.modal').forEach(el => {
        el.setAttribute('role', 'dialog');
        el.setAttribute('aria-modal', 'true');
      });
    }

    const lifecycle = window.StockOSPages && window.StockOSPages[route];
    if (lifecycle && typeof lifecycle.activate === 'function') lifecycle.activate();
    // PDV is an additional module within Caixas and depends on its helpers.
    if (route === 'caixas' && window.StockOSPDV) window.StockOSPDV.activate();
    // Preview-only behaviour: notes disclaimer and fake checkout are mounted
    // after the new document's content becomes visible.
    if (window.StockOSPreviewHooks && typeof window.StockOSPreviewHooks[route] === 'function') {
      window.StockOSPreviewHooks[route]();
    }
    if (lifecycle && typeof lifecycle.mount === 'function') {
      Promise.resolve(lifecycle.mount()).catch(error =>
        console.error('StockOS: erro ao iniciar '+route, error));
    }
  }

  function fallback(href) {
    // Missing page, auth failure, or an unexpected DOM structure:
    // keep legacy navigation fully functional instead of showing a blank view.
    window.location.assign(href);
  }

  async function navigate(href, mode = 'push') {
    const target = routeFromUrl(href);
    if (!target) return fallback(href);
    if (target === active) return;

    // Enforce the same page permissions on client transitions; Spring remains
    // authoritative on every API call and on full HTML navigation.
    if (typeof PERMISSOES_POR_PAGINA !== 'undefined' &&
        typeof temAlgumaPermissao === 'function') {
      const required = PERMISSOES_POR_PAGINA[target];
      if (required && !temAlgumaPermissao(required)) return fallback(href);
    }

    const id = ++navigationId;
    // Do not close in-progress forms until the destination is ready.
    try {
      const [next] = await Promise.all([
        prepare(target), ensureStyles(target), ensureScript(target)
      ]);
      if (id !== navigationId) return;
      rememberCurrent();
      // Lifecycle initialization checks paginaAtual() (location.pathname).
      // Update the URL *before* mounting the new page.
      if (mode === 'push') history.pushState({ stockosSpa: true }, '', href);
      render(target, next);
      window.scrollTo(0, mode === 'pop' ? next.scrollY || 0 : 0);
    } catch (error) {
      if (id !== navigationId) return;
      console.warn('StockOS: SPA indisponível, usando navegação tradicional', error);
      fallback(href);
    }
  }

  function init() {
    if (!document.querySelector('#nav-items')) return;
    rememberCurrent();

    document.addEventListener('click', event => {
      if (event.defaultPrevented || event.button !== 0 ||
          event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
      const target = event.target;
      const link = target && target.closest && target.closest('a[href]');
      if (!link || link.hasAttribute('download') ||
          (link.target && link.target !== '_self')) return;
      const route = routeFromUrl(link.href);
      if (!route) {
        ++navigationId; // Cancel any pending SPA transition before native navigation.
        return;
      }
      if (route === active) {
        ++navigationId;
        return;
      }
      event.preventDefault();
      void navigate(link.href);
    });

    window.addEventListener('popstate', () => {
      const route = routeFromUrl(location.href);
      if (!route) return window.location.reload();
      void navigate(location.href, 'pop');
    });

    // The next page starts downloading when the user signals intent.
    document.addEventListener('pointerover', event => {
      const link = event.target && event.target.closest && event.target.closest('a[href]');
      if (!link) return;
      const route = routeFromUrl(link.href);
      if (route && route !== active) {
        // Avoid pre-running page modules; fetch just the static HTML.
        void prepare(route).catch(() => {});
      }
    }, { passive: true });

    window.StockOSSPA = {
      routes: Object.keys(ROUTES),
      current: () => active,
      navigate
    };
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init, { once: true });
  } else {
    init();
  }
})();
