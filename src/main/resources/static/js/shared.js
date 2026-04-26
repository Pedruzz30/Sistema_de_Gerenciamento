// ── shared.js — base compartilhada do frontend ─────────────────────────────
// Responsável por shell, navegação, ícones, toasts e utilitários globais.

const USUARIO_STORAGE_KEY = 'usuario';

function parseUsuario(raw) {
  if (!raw) return null;
  try {
    return JSON.parse(raw);
  } catch (error) {
    return null;
  }
}

function persistirUsuario(usuarioAtual) {
  if (!usuarioAtual) return;
  const payload = JSON.stringify(usuarioAtual);
  [window.sessionStorage, window.localStorage].forEach(function (storage) {
    try {
      storage.setItem(USUARIO_STORAGE_KEY, payload);
    } catch (error) {}
  });
}

function limparUsuarioPersistido() {
  [window.sessionStorage, window.localStorage].forEach(function (storage) {
    try {
      storage.removeItem(USUARIO_STORAGE_KEY);
    } catch (error) {}
  });
}

function lerUsuarioPersistido() {
  const sessionUsuario = parseUsuario((function () {
    try {
      return window.sessionStorage.getItem(USUARIO_STORAGE_KEY);
    } catch (error) {
      return null;
    }
  })());
  if (sessionUsuario) {
    persistirUsuario(sessionUsuario);
    return sessionUsuario;
  }

  const localUsuario = parseUsuario((function () {
    try {
      return window.localStorage.getItem(USUARIO_STORAGE_KEY);
    } catch (error) {
      return null;
    }
  })());
  if (localUsuario) {
    persistirUsuario(localUsuario);
  }
  return localUsuario;
}

const usuario = lerUsuarioPersistido();
if (!usuario) window.location.href = '/login.html';

// ── X-User-RU header injection ──────────────────────────────────────────────
(function () {
  const originalFetch = window.fetch.bind(window);

  window.fetch = function (input, init) {
    const isRequestObject = input instanceof Request;
    const requestUrl = isRequestObject ? input.url : String(input || '');
    const isApiCall = requestUrl.startsWith('/api/') || requestUrl.includes('/api/');

    if (usuario && isApiCall) {
      const headers = new Headers(
        isRequestObject ? input.headers : (init && init.headers ? init.headers : {})
      );

      if (isRequestObject && init && init.headers) {
        new Headers(init.headers).forEach(function (value, key) {
          headers.set(key, value);
        });
      }

      headers.set('X-User-RU', String(usuario.ru));

      if (isRequestObject) {
        const requestComCabecalho = new Request(input, Object.assign({}, init || {}, { headers }));
        return originalFetch(requestComCabecalho);
      }

      init = Object.assign({}, init || {}, { headers });
    }

    return originalFetch(input, init);
  };
})();

// ── Permissões ──────────────────────────────────────────────────────────────
const PERMISSOES = {
  VER_ESTOQUE: 'VER_ESTOQUE',
  EDITAR_ESTOQUE: 'EDITAR_ESTOQUE',
  VER_COMPRAS: 'VER_COMPRAS',
  VER_VENDAS: 'VER_VENDAS',
  VER_FINANCAS: 'VER_FINANCAS',
  GERENCIAR_FUNCIONARIOS: 'GERENCIAR_FUNCIONARIOS',
  VER_LOGS: 'VER_LOGS',
};

const PERFIL_PERMISSOES = {
  SUPERIOR: Object.values(PERMISSOES),
  GERENTE_ESTOQUE: [
    'VER_ESTOQUE', 'EDITAR_ESTOQUE', 'VER_COMPRAS',
    'VER_FINANCAS', 'GERENCIAR_FUNCIONARIOS', 'VER_LOGS'
  ],
  ESTOQUISTA: ['VER_ESTOQUE', 'EDITAR_ESTOQUE'],
  CAIXA: ['VER_VENDAS', 'VER_ESTOQUE'],
};

function getPermissoes() {
  if (Array.isArray(usuario.permissoes) && usuario.permissoes.length > 0) {
    return usuario.permissoes;
  }
  if (usuario.perfil === 'ADMIN') return PERFIL_PERMISSOES.SUPERIOR;
  return PERFIL_PERMISSOES[usuario.nomeClasse] || PERFIL_PERMISSOES.ESTOQUISTA;
}

function temPermissao(perm) {
  return getPermissoes().includes(perm);
}

function temAlgumaPermissao(perms) {
  return Array.isArray(perms) && perms.some(temPermissao);
}

function logout() {
  limparUsuarioPersistido();
  window.location.href = '/login.html';
}

// ── Iconografia compartilhada ───────────────────────────────────────────────
const ICONS = {
  menu: '<path d="M4 6h16"/><path d="M4 12h16"/><path d="M4 18h16"/>',
  grid: '<rect x="4" y="4" width="6" height="6" rx="1.2"/><rect x="14" y="4" width="6" height="6" rx="1.2"/><rect x="4" y="14" width="6" height="6" rx="1.2"/><rect x="14" y="14" width="6" height="6" rx="1.2"/>',
  box: '<path d="M12 3l8 4-8 4-8-4 8-4Z"/><path d="M4 7v10l8 4 8-4V7"/><path d="M12 11v10"/>',
  truck: '<path d="M10 17H6a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h8v11Z"/><path d="M10 10h5l3 3v4h-8"/><circle cx="7.5" cy="17.5" r="1.5"/><circle cx="16.5" cy="17.5" r="1.5"/>',
  receipt: '<path d="M6 3h12v18l-3-2-3 2-3-2-3 2V3Z"/><path d="M8 8h8"/><path d="M8 12h8"/><path d="M8 16h6"/>',
  wallet: '<rect x="3" y="6" width="18" height="12" rx="2"/><path d="M15 12h6"/><circle cx="15" cy="12" r="1"/><path d="M6 9h6"/>',
  chart: '<path d="M4 19h16"/><path d="M7 15l4-4 3 3 5-6"/>',
  users: '<path d="M16 19v-1a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v1"/><circle cx="10" cy="8" r="3"/><path d="M20 19v-1a4 4 0 0 0-3-3.87"/><path d="M16 5.13a3 3 0 0 1 0 5.74"/>',
  clipboard: '<rect x="6" y="4" width="12" height="16" rx="2"/><path d="M9 4.5h6v3H9z"/><path d="M9 10h6"/><path d="M9 14h6"/>',
  search: '<circle cx="11" cy="11" r="6"/><path d="m20 20-3.5-3.5"/>',
  logout: '<path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><path d="M16 17l5-5-5-5"/><path d="M21 12H9"/>',
  close: '<path d="M6 6l12 12"/><path d="M18 6 6 18"/>',
  arrowRight: '<path d="M5 12h14"/><path d="m13 6 6 6-6 6"/>',
  info: '<circle cx="12" cy="12" r="9"/><path d="M12 10v6"/><path d="M12 7h.01"/>',
  checkCircle: '<circle cx="12" cy="12" r="9"/><path d="m8.5 12 2.5 2.5 4.5-5"/>',
  alertTriangle: '<path d="m12 3 9 16H3l9-16Z"/><path d="M12 9v4"/><path d="M12 16h.01"/>',
  refresh: '<path d="M21 12a9 9 0 0 1-15.5 6.4"/><path d="M3 12A9 9 0 0 1 18.5 5.6"/><path d="m7 21-1.5-2.6L3 20"/><path d="m17 3 1.5 2.6L21 4"/>',
  shield: '<path d="M12 3l7 3v6c0 4.5-3 7-7 9-4-2-7-4.5-7-9V6l7-3Z"/>',
  user: '<circle cx="12" cy="8" r="3"/><path d="M6 19a6 6 0 0 1 12 0"/>',
  lock: '<rect x="5" y="11" width="14" height="10" rx="2"/><path d="M8 11V8a4 4 0 1 1 8 0v3"/>',
  cart: '<circle cx="9" cy="19" r="1.5"/><circle cx="17" cy="19" r="1.5"/><path d="M5 5h2l2 9h9l2-7H8"/><path d="M9 11h9"/>',
  plus: '<path d="M12 5v14"/><path d="M5 12h14"/>',
  minus: '<path d="M5 12h14"/>',
};

function iconMarkup(name) {
  const content = ICONS[name] || ICONS.info;
  return '<svg class="icon-svg" viewBox="0 0 24 24" aria-hidden="true" focusable="false">' +
    content +
    '</svg>';
}

window.iconMarkup = iconMarkup;

function emptyStateMarkup(config) {
  const options = config || {};
  const compact = options.compact ? ' empty-state--compact' : '';
  const title = options.title ? '<div class="empty-state__title">' + escapeHtml(options.title) + '</div>' : '';
  const copy = options.copy ? '<div class="empty-state__copy">' + escapeHtml(options.copy) + '</div>' : '';
  const actions = options.actions ? '<div class="empty-state__actions">' + options.actions + '</div>' : '';

  return '<div class="empty-state' + compact + '">' +
    '<div class="empty-state__icon" aria-hidden="true">' + iconMarkup(options.icon || 'info') + '</div>' +
    title +
    copy +
    actions +
    '</div>';
}

function legendItemMarkup(config) {
  const options = config || {};
  const shapeClass = options.square ? ' legend-inline__swatch--square' : '';
  const tone = options.tone || 'muted';
  return '<span class="legend-inline__item">' +
    '<span class="legend-inline__swatch legend-inline__swatch--' + tone + shapeClass + '"></span>' +
    '<span>' + escapeHtml(options.label || '') + ':</span>' +
    '<strong class="legend-inline__value legend-inline__value--' + tone + '">' + escapeHtml(String(options.value || 0)) + '</strong>' +
    '</span>';
}

window.emptyStateMarkup = emptyStateMarkup;
window.legendItemMarkup = legendItemMarkup;

function aplicarIconesEstaticos(root) {
  const scope = root || document;

  scope.querySelectorAll('.sidebar-logo-icon').forEach(function (elemento) {
    elemento.innerHTML = iconMarkup('box');
    elemento.setAttribute('aria-hidden', 'true');
  });

  scope.querySelectorAll('.search-icon').forEach(function (elemento) {
    elemento.innerHTML = iconMarkup('search');
    elemento.setAttribute('aria-hidden', 'true');
  });

  scope.querySelectorAll('.btn-logout').forEach(function (elemento) {
    elemento.innerHTML = iconMarkup('logout');
    if (!elemento.getAttribute('aria-label')) elemento.setAttribute('aria-label', 'Sair');
  });

  scope.querySelectorAll('.modal-close').forEach(function (elemento) {
    elemento.innerHTML = iconMarkup('close');
    if (!elemento.getAttribute('aria-label')) elemento.setAttribute('aria-label', 'Fechar');
  });

  scope.querySelectorAll('[data-icon]').forEach(function (elemento) {
    elemento.innerHTML = iconMarkup(elemento.getAttribute('data-icon'));
    elemento.setAttribute('aria-hidden', 'true');
  });
}

// ── Toast notification system ───────────────────────────────────────────────
function showToast(message, type) {
  const status = type || 'info';
  const iconName = {
    success: 'checkCircle',
    error: 'alertTriangle',
    info: 'info',
  }[status] || 'info';

  let container = document.getElementById('_toast_container');
  if (!container) {
    container = document.createElement('div');
    container.id = '_toast_container';
    container.className = 'toast-container';
    document.body.appendChild(container);
  }

  const toast = document.createElement('div');
  toast.className = 'toast toast--' + status;
  toast.innerHTML =
    '<span class="toast__icon">' + iconMarkup(iconName) + '</span>' +
    '<span>' + escapeHtml(message) + '</span>';
  container.appendChild(toast);

  setTimeout(function () {
    toast.classList.add('toast--closing');
    setTimeout(function () { toast.remove(); }, 300);
  }, 3500);
}

// ── Loading guard ────────────────────────────────────────────────────────────
function setLoading(btn, loading) {
  if (!btn) return;
  if (loading) {
    btn.dataset._origText = btn.textContent;
    btn.disabled = true;
    btn.textContent = btn.dataset._origText + '…';
  } else {
    btn.disabled = false;
    if (btn.dataset._origText !== undefined) {
      btn.textContent = btn.dataset._origText;
    }
  }
}

// ── HTML escaping ────────────────────────────────────────────────────────────
function escapeHtml(str) {
  if (str === null || str === undefined) return '';
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#x27;');
}

// ── Safe error parsing ──────────────────────────────────────────────────────
async function safeReadErrorMessage(response, fallbackMessage) {
  const fallback = fallbackMessage || ('HTTP ' + (response ? response.status : 'erro'));
  if (!response) return fallback;

  try {
    const contentType = (response.headers && response.headers.get('Content-Type')) || '';
    if (contentType.toLowerCase().includes('application/json')) {
      const json = await response.json();
      if (json && typeof json === 'object') {
        return json.erro || json.mensagem || json.message || fallback;
      }
    }
  } catch (error) {}

  try {
    const text = await response.text();
    if (text && text.trim()) return text.trim();
  } catch (error) {}

  return fallback;
}

// ── Page detection ───────────────────────────────────────────────────────────
function paginaAtual() {
  const path = window.location.pathname;
  if (path.includes('cardapio')) return 'cardapio';
  if (path.includes('estoque')) return 'estoque';
  if (path.includes('fornecedores')) return 'fornecedores';
  if (path.includes('notas')) return 'notas';
  if (path.includes('caixas')) return 'caixas';
  if (path.includes('cotacoes')) return 'cotacoes';
  if (path.includes('funcionarios')) return 'funcionarios';
  if (path.includes('logs')) return 'logs';
  return 'dashboard';
}

// ── Navigation ───────────────────────────────────────────────────────────────
const NAV_ITENS = [
  { icon: 'grid', label: 'Dashboard', permsAny: null, href: '/dashboard.html', id: 'dashboard' },
  { icon: 'box', label: 'Estoque', permsAny: [PERMISSOES.VER_ESTOQUE], href: '/estoque', id: 'estoque' },
  { icon: 'cart', label: 'Cardapio', permsAny: [PERMISSOES.EDITAR_ESTOQUE], href: '/cardapio.html', id: 'cardapio' },
  { icon: 'truck', label: 'Fornecedores', permsAny: [PERMISSOES.VER_COMPRAS], href: '/fornecedores.html', id: 'fornecedores' },
  { icon: 'receipt', label: 'Notas Fiscais', permsAny: [PERMISSOES.VER_COMPRAS], href: '/notas.html', id: 'notas' },
  { icon: 'wallet', label: 'Caixas', permsAny: [PERMISSOES.VER_VENDAS, PERMISSOES.VER_FINANCAS], href: '/caixas.html', id: 'caixas' },
  { icon: 'chart', label: 'Cotações', permsAny: [PERMISSOES.VER_FINANCAS], href: '/cotacoes.html', id: 'cotacoes' },
  { icon: 'users', label: 'Funcionários', permsAny: [PERMISSOES.GERENCIAR_FUNCIONARIOS], href: '/funcionarios.html', id: 'funcionarios' },
  { icon: 'clipboard', label: 'Logs', permsAny: [PERMISSOES.VER_LOGS], href: '/logs.html', id: 'logs' },
];

function renderSidebar() {
  const nav = document.getElementById('nav-items');
  if (!nav) return;
  const atual = paginaAtual();

  nav.innerHTML = NAV_ITENS
    .filter(function (item) { return !item.permsAny || temAlgumaPermissao(item.permsAny); })
    .map(function (item) {
      return '<a href="' + item.href + '" class="nav-item ' + (item.id === atual ? 'active' : '') + '">' +
        '<span class="nav-icon" aria-hidden="true">' + iconMarkup(item.icon) + '</span>' +
        '<span>' + item.label + '</span>' +
        '</a>';
    }).join('');
}

const PERMISSOES_POR_PAGINA = {
  cardapio: [PERMISSOES.EDITAR_ESTOQUE],
  estoque: [PERMISSOES.VER_ESTOQUE],
  fornecedores: [PERMISSOES.VER_COMPRAS],
  notas: [PERMISSOES.VER_COMPRAS],
  caixas: [PERMISSOES.VER_VENDAS, PERMISSOES.VER_FINANCAS],
  cotacoes: [PERMISSOES.VER_FINANCAS],
  funcionarios: [PERMISSOES.GERENCIAR_FUNCIONARIOS],
  logs: [PERMISSOES.VER_LOGS],
};

function garantirPermissaoDaPagina() {
  const pagina = paginaAtual();
  const permissoesNecessarias = PERMISSOES_POR_PAGINA[pagina];
  if (!permissoesNecessarias || temAlgumaPermissao(permissoesNecessarias)) {
    return true;
  }

  window.location.replace('/dashboard.html');
  return false;
}

function renderUserCard() {
  const avatar = document.getElementById('user-avatar');
  const name = document.getElementById('user-name');
  const role = document.getElementById('user-role');
  if (!avatar || !name || !role) return;

  avatar.textContent = (usuario.nome[0] + usuario.sobrenome[0]).toUpperCase();
  name.textContent = usuario.nome + ' ' + usuario.sobrenome;
  role.textContent = usuario.perfil === 'ADMIN'
    ? 'Superior'
    : (usuario.nomeClasse ? usuario.nomeClasse.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, function (letra) { return letra.toUpperCase(); }) : 'Operador');
}

// ── Shell mobile e acessibilidade global ────────────────────────────────────
let shellInicializado = false;

function isShellMobile() {
  return window.innerWidth <= 900;
}

function fecharModalAberto() {
  const overlays = Array.from(document.querySelectorAll('.modal-overlay.open'));
  const topo = overlays[overlays.length - 1];
  if (!topo) return false;

  if (typeof window.fecharModal === 'function' && topo.id) {
    window.fecharModal(topo.id);
  } else {
    topo.classList.remove('open');
  }
  return true;
}

function setupShell() {
  if (shellInicializado) return;

  const app = document.querySelector('.app');
  const sidebar = document.querySelector('.sidebar');
  const header = document.querySelector('.header');
  if (!app || !sidebar || !header) return;

  let headerMain = header.querySelector('.header-main');
  if (!headerMain) {
    const intro = header.firstElementChild;
    if (intro) {
      headerMain = document.createElement('div');
      headerMain.className = 'header-main';
      intro.classList.add('header-text');
      header.insertBefore(headerMain, intro);
      headerMain.appendChild(intro);
    }
  }

  let toggle = header.querySelector('.sidebar-toggle');
  if (!toggle) {
    toggle = document.createElement('button');
    toggle.type = 'button';
    toggle.className = 'sidebar-toggle';
    toggle.setAttribute('aria-label', 'Abrir navegação');
    toggle.setAttribute('aria-controls', 'app-sidebar');
    toggle.setAttribute('aria-expanded', 'false');
    toggle.innerHTML = iconMarkup('menu');
    sidebar.id = sidebar.id || 'app-sidebar';
    if (headerMain) {
      headerMain.insertBefore(toggle, headerMain.firstChild);
    } else {
      header.insertBefore(toggle, header.firstChild);
    }
  }

  let overlay = document.querySelector('.sidebar-overlay');
  if (!overlay) {
    overlay = document.createElement('button');
    overlay.type = 'button';
    overlay.className = 'sidebar-overlay';
    overlay.setAttribute('aria-label', 'Fechar navegação');
    document.body.appendChild(overlay);
  }

  function syncSidebar(isOpen) {
    const aberto = Boolean(isOpen) && isShellMobile();
    sidebar.classList.toggle('open', aberto);
    overlay.classList.toggle('open', aberto);
    document.body.classList.toggle('sidebar-open', aberto);
    toggle.setAttribute('aria-expanded', aberto ? 'true' : 'false');
  }

  function abrirSidebar() {
    syncSidebar(true);
  }

  function fecharSidebar() {
    syncSidebar(false);
  }

  toggle.addEventListener('click', function () {
    if (sidebar.classList.contains('open')) {
      fecharSidebar();
    } else {
      abrirSidebar();
    }
  });

  overlay.addEventListener('click', fecharSidebar);

  sidebar.addEventListener('click', function (event) {
    const alvo = event.target.closest('a');
    if (alvo && isShellMobile()) fecharSidebar();
  });

  window.addEventListener('resize', function () {
    if (!isShellMobile()) fecharSidebar();
  });

  document.addEventListener('keydown', function (event) {
    if (event.key !== 'Escape') return;
    if (sidebar.classList.contains('open')) {
      fecharSidebar();
      return;
    }
    fecharModalAberto();
  });

  document.querySelectorAll('.modal').forEach(function (modal) {
    modal.setAttribute('role', 'dialog');
    modal.setAttribute('aria-modal', 'true');
  });

  shellInicializado = true;
}

// ── Bootstrap ───────────────────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', function () {
  if (!garantirPermissaoDaPagina()) return;
  renderSidebar();
  renderUserCard();
  aplicarIconesEstaticos(document);
  setupShell();
});
