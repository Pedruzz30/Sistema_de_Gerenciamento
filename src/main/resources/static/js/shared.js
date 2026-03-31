// ── shared.js — reutilizado em todas as páginas ──────────────
// Gerencia sidebar, navegação, user card, toast e utilitários

const usuario = JSON.parse(sessionStorage.getItem('usuario') || 'null');
if (!usuario) window.location.href = '/login.html';

// ── X-User-RU header injection ────────────────────────────────
// Intercepts all /api/ fetches and adds the logged-in user's RU header.
// Controllers fall back to adminSuperior when the header is absent.
(function () {
  const _fetch = window.fetch.bind(window);
  window.fetch = function (input, init) {
    const isRequestObject = input instanceof Request;
    const requestUrl = isRequestObject ? input.url : String(input || '');
    const isApiCall = requestUrl.startsWith('/api/')
      || requestUrl.includes('/api/');

    if (usuario && isApiCall) {
      const headers = new Headers(
        isRequestObject ? input.headers : (init && init.headers ? init.headers : {})
      );

      // If caller passed fetch(Request, init.headers), preserve those headers too.
      if (isRequestObject && init && init.headers) {
        new Headers(init.headers).forEach(function (value, key) {
          headers.set(key, value);
        });
      }

      headers.set('X-User-RU', String(usuario.ru));

      if (isRequestObject) {
        const requestComCabecalho = new Request(input, Object.assign({}, init || {}, { headers }));
        return _fetch(requestComCabecalho);
      }

      init = Object.assign({}, init || {}, { headers });
    }
    return _fetch(input, init);
  };
})();

// ── Permissions ───────────────────────────────────────────────
const PERMISSOES = {
  VER_ESTOQUE:            'VER_ESTOQUE',
  EDITAR_ESTOQUE:         'EDITAR_ESTOQUE',
  VER_COMPRAS:            'VER_COMPRAS',
  VER_VENDAS:             'VER_VENDAS',
  VER_FINANCAS:           'VER_FINANCAS',
  GERENCIAR_FUNCIONARIOS: 'GERENCIAR_FUNCIONARIOS',
  VER_LOGS:               'VER_LOGS',
};

const PERFIL_PERMISSOES = {
  SUPERIOR:       Object.values(PERMISSOES),
  GERENTE_ESTOQUE:[
    'VER_ESTOQUE','EDITAR_ESTOQUE','VER_COMPRAS',
    'VER_FINANCAS','GERENCIAR_FUNCIONARIOS','VER_LOGS'
  ],
  ESTOQUISTA:     ['VER_ESTOQUE','EDITAR_ESTOQUE'],
  CAIXA:          ['VER_VENDAS','VER_ESTOQUE'],
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

function logout() {
  sessionStorage.removeItem('usuario');
  window.location.href = '/login.html';
}

// ── Toast notification system ─────────────────────────────────
// No external dependency. Types: 'success' | 'error' | 'info'
function showToast(message, type) {
  type = type || 'info';
  const colors  = { success: '#10b981', error: '#f43f5e', info: '#06b6d4' };
  const icons   = { success: '✓', error: '✕', info: 'ℹ' };
  const color   = colors[type] || colors.info;
  const icon    = icons[type]  || icons.info;

  // Inject keyframe once
  if (!document.getElementById('_toast_style')) {
    const s = document.createElement('style');
    s.id = '_toast_style';
    s.textContent = '@keyframes _toastIn{from{opacity:0;transform:translateX(20px)}to{opacity:1;transform:translateX(0)}}';
    document.head.appendChild(s);
  }

  // Lazy container
  let container = document.getElementById('_toast_container');
  if (!container) {
    container = document.createElement('div');
    container.id = '_toast_container';
    container.style.cssText =
      'position:fixed;bottom:24px;right:24px;z-index:9999;' +
      'display:flex;flex-direction:column;gap:8px;pointer-events:none;';
    document.body.appendChild(container);
  }

  const toast = document.createElement('div');
  toast.style.cssText =
    'display:flex;align-items:center;gap:10px;' +
    'background:rgba(20,20,30,.97);' +
    'border:1px solid rgba(255,255,255,.08);' +
    'border-left:3px solid ' + color + ';' +
    'color:#e2e8f0;padding:12px 16px;border-radius:8px;' +
    'font-size:13px;font-family:inherit;' +
    'box-shadow:0 8px 24px rgba(0,0,0,.5);' +
    'max-width:320px;pointer-events:auto;' +
    'animation:_toastIn .2s ease;';
  toast.innerHTML =
    '<span style="color:' + color + ';font-weight:700;flex-shrink:0">' + icon + '</span>' +
    '<span>' + message + '</span>';
  container.appendChild(toast);

  setTimeout(function () {
    toast.style.transition = 'opacity .3s ease';
    toast.style.opacity = '0';
    setTimeout(function () { toast.remove(); }, 300);
  }, 3500);
}

// ── Loading guard ─────────────────────────────────────────────
// Disables a button while an async operation is in flight.
// Usage:  setLoading(btn, true)  before fetch
//         setLoading(btn, false) in finally block
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

// ── Safe error parsing ───────────────────────────────────────
// Reads API error payloads defensively:
// - prefers JSON { erro | mensagem | message } when available
// - falls back to text body
// - always returns a non-empty fallback string
async function safeReadErrorMessage(response, fallbackMessage) {
  var fallback = fallbackMessage || ('HTTP ' + (response ? response.status : 'erro'));
  if (!response) return fallback;

  try {
    var contentType = (response.headers && response.headers.get('Content-Type')) || '';
    if (contentType.toLowerCase().includes('application/json')) {
      var json = await response.json();
      if (json && typeof json === 'object') {
        return json.erro || json.mensagem || json.message || fallback;
      }
    }
  } catch (e) {}

  try {
    var text = await response.text();
    if (text && text.trim()) return text.trim();
  } catch (e) {}

  return fallback;
}

// ── Page detection ────────────────────────────────────────────
function paginaAtual() {
  const path = window.location.pathname;
  if (path.includes('estoque'))      return 'estoque';
  if (path.includes('fornecedores')) return 'fornecedores';
  if (path.includes('notas'))        return 'notas';
  if (path.includes('caixas'))       return 'caixas';
  if (path.includes('cotacoes'))     return 'cotacoes';
  if (path.includes('funcionarios')) return 'funcionarios';
  if (path.includes('logs'))         return 'logs';
  return 'dashboard';
}

// ── Navigation ────────────────────────────────────────────────
const NAV_ITENS = [
  { icon: '⊞',  label: 'Dashboard',    perm: null,                        href: '/dashboard.html',    id: 'dashboard'    },
  { icon: '📦', label: 'Estoque',       perm: 'VER_ESTOQUE',               href: '/estoque',           id: 'estoque'      },
  { icon: '🚚', label: 'Fornecedores',  perm: 'VER_COMPRAS',               href: '/fornecedores.html', id: 'fornecedores' },
  { icon: '🧾', label: 'Notas Fiscais', perm: 'VER_COMPRAS',               href: '/notas.html',        id: 'notas'        },
  { icon: '💰', label: 'Caixas',        perm: 'VER_VENDAS',                href: '/caixas.html',       id: 'caixas'       },
  { icon: '📈', label: 'Cotações',      perm: 'VER_FINANCAS',              href: '/cotacoes.html',     id: 'cotacoes'     },
  { icon: '👥', label: 'Funcionários',  perm: 'GERENCIAR_FUNCIONARIOS',    href: '/funcionarios.html', id: 'funcionarios' },
  { icon: '📋', label: 'Logs',          perm: 'VER_LOGS',                  href: '/logs.html',         id: 'logs'         },
];

document.addEventListener('DOMContentLoaded', function () {
  renderSidebar();
  renderUserCard();
});

function renderSidebar() {
  const nav = document.getElementById('nav-items');
  if (!nav) return;
  const atual = paginaAtual();

  nav.innerHTML = NAV_ITENS
    .filter(function (item) { return !item.perm || temPermissao(item.perm); })
    .map(function (item) {
      return '<a href="' + item.href + '" class="nav-item ' + (item.id === atual ? 'active' : '') + '">' +
             '<span class="nav-icon">' + item.icon + '</span>' + item.label + '</a>';
    }).join('');
}

function renderUserCard() {
  const avatar = document.getElementById('user-avatar');
  const name   = document.getElementById('user-name');
  const role   = document.getElementById('user-role');
  if (!avatar) return;
  avatar.textContent = (usuario.nome[0] + usuario.sobrenome[0]).toUpperCase();
  name.textContent   = usuario.nome + ' ' + usuario.sobrenome;
  role.textContent   = usuario.perfil === 'ADMIN' ? 'Superior' : 'Operador';
}
