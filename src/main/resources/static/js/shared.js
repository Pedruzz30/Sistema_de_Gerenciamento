// ── shared.js — reutilizado em todas as páginas ──────────────
// Gerencia sidebar, navegação e user card

const usuario = JSON.parse(sessionStorage.getItem('usuario') || 'null');
if (!usuario) window.location.href = '/login.html';

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

// Descobre a página atual pelo pathname
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

const NAV_ITENS = [
  { icon: '⊞',  label: 'Dashboard',    perm: null,                        href: '/dashboard.html',    id: 'dashboard'    },
  { icon: '📦', label: 'Estoque',       perm: 'VER_ESTOQUE',               href: '/estoque.html',      id: 'estoque'      },
  { icon: '🚚', label: 'Fornecedores',  perm: 'VER_COMPRAS',               href: '/fornecedores.html', id: 'fornecedores' },
  { icon: '🧾', label: 'Notas Fiscais', perm: 'VER_COMPRAS',               href: '/notas.html',        id: 'notas'        },
  { icon: '💰', label: 'Caixas',        perm: 'VER_VENDAS',                href: '/caixas.html',       id: 'caixas'       },
  { icon: '📈', label: 'Cotações',      perm: 'VER_FINANCAS',              href: '/cotacoes.html',     id: 'cotacoes'     },
  { icon: '👥', label: 'Funcionários',  perm: 'GERENCIAR_FUNCIONARIOS',    href: '/funcionarios.html', id: 'funcionarios' },
  { icon: '📋', label: 'Logs',          perm: 'VER_LOGS',                  href: '/logs.html',         id: 'logs'         },
];

document.addEventListener('DOMContentLoaded', () => {
  renderSidebar();
  renderUserCard();
});

function renderSidebar() {
  const nav = document.getElementById('nav-items');
  if (!nav) return;
  const atual = paginaAtual();

  nav.innerHTML = NAV_ITENS
    .filter(item => !item.perm || temPermissao(item.perm))
    .map(item => `
      <a href="${item.href}" class="nav-item ${item.id === atual ? 'active' : ''}">
        <span class="nav-icon">${item.icon}</span>
        ${item.label}
      </a>
    `).join('');
}

function renderUserCard() {
  const avatar = document.getElementById('user-avatar');
  const name   = document.getElementById('user-name');
  const role   = document.getElementById('user-role');
  if (!avatar) return;
  avatar.textContent = `${usuario.nome[0]}${usuario.sobrenome[0]}`.toUpperCase();
  name.textContent   = `${usuario.nome} ${usuario.sobrenome}`;
  role.textContent   = usuario.perfil === 'ADMIN' ? 'Superior' : 'Operador';
}
