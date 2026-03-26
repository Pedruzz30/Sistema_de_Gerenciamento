// ── Estado global ──────────────────────────────────────────
const usuario = JSON.parse(sessionStorage.getItem('usuario') || 'null');

// Redireciona para login se não estiver autenticado
if (!usuario) {
  window.location.href = '/login.html';
}

// Permissões disponíveis (espelha o enum Java)
const PERMISSOES = {
  VER_ESTOQUE:           'VER_ESTOQUE',
  EDITAR_ESTOQUE:        'EDITAR_ESTOQUE',
  VER_COMPRAS:           'VER_COMPRAS',
  VER_VENDAS:            'VER_VENDAS',
  VER_FINANCAS:          'VER_FINANCAS',
  GERENCIAR_FUNCIONARIOS:'GERENCIAR_FUNCIONARIOS',
  VER_LOGS:              'VER_LOGS',
};

// Mapa de permissões por perfil (espelha SistemaGerenciamentoEstoque.java)
const PERFIL_PERMISSOES = {
  SUPERIOR: Object.values(PERMISSOES),
  GERENTE_ESTOQUE: [
    PERMISSOES.VER_ESTOQUE,
    PERMISSOES.EDITAR_ESTOQUE,
    PERMISSOES.VER_COMPRAS,
    PERMISSOES.VER_FINANCAS,
    PERMISSOES.GERENCIAR_FUNCIONARIOS,
    PERMISSOES.VER_LOGS,
  ],
  ESTOQUISTA: [
    PERMISSOES.VER_ESTOQUE,
    PERMISSOES.EDITAR_ESTOQUE,
  ],
  CAIXA: [
    PERMISSOES.VER_VENDAS,
    PERMISSOES.VER_ESTOQUE,
  ],
};

// Descobre as permissões do usuário logado pelo nome da classe
function getPermissoes() {
    if (Array.isArray(usuario.permissoes) && usuario.permissoes.length > 0) {
        return usuario.permissoes;
      }
  // A classe vem do campo 'classe' retornado pela API
  // Por ora, mapeamos pelo perfil (ADMIN = SUPERIOR, OPERADOR varia)
  if (usuario.perfil === 'ADMIN') return PERFIL_PERMISSOES.SUPERIOR;

  // Tenta achar pelo nome da classe se disponível
  const classe = usuario.nomeClasse || '';
  return PERFIL_PERMISSOES[classe] || PERFIL_PERMISSOES.ESTOQUISTA;
}

function temPermissao(perm) {
  return getPermissoes().includes(perm);
}

// TODO: conectar a /api/caixas quando o endpoint estiver pronto
const MOCK_CAIXAS = [
  { numero: 1, status: 'ABERTO',  vendas: 342.50, operador: 'Ana'  },
  { numero: 2, status: 'ABERTO',  vendas: 218.00, operador: 'João' },
  { numero: 3, status: 'FECHADO', vendas: 0,      operador: '—'    },
];

// TODO: conectar a /api/movimentacoes quando o endpoint estiver pronto
const MOCK_MOVIMENTACOES = [
  { tipo: 'ENTRADA', produto: 'Morango', qtd: 10, hora: '08:32' },
  { tipo: 'SAIDA',   produto: 'Laranja', qtd: 4,  hora: '09:15' },
  { tipo: 'ENTRADA', produto: 'Tomate',  qtd: 20, hora: '10:05' },
  { tipo: 'SAIDA',   produto: 'Alface',  qtd: 3,  hora: '11:40' },
  { tipo: 'SAIDA',   produto: 'Morango', qtd: 2,  hora: '13:20' },
];

// ── Inicialização ──────────────────────────────────────────
async function carregarDados() {
  let produtos = [];

  try {
    const resp = await fetch('/api/produtos');
    if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
    produtos = await resp.json();
  } catch (err) {
    console.error('Erro ao carregar produtos:', err);
  }

  renderSummaryCards(produtos);
  renderAlertas(produtos);
  renderMovimentacoes();
  renderFinanceiro();
}

document.addEventListener('DOMContentLoaded', () => {
  renderHeader();
  renderSidebar();
  renderUserCard();
  renderPermissionSections();
  carregarDados();
});

// ── Header ────────────────────────────────────────────────
function renderHeader() {
  const hora = new Date().getHours();
  const saudacao = hora < 12 ? 'Bom dia' : hora < 18 ? 'Boa tarde' : 'Boa noite';
  document.getElementById('header-title').textContent =
    `${saudacao}, ${usuario.nome} 👋`;
  document.getElementById('header-sub').textContent =
    new Date().toLocaleDateString('pt-BR', {
      weekday: 'long', day: 'numeric', month: 'long', year: 'numeric'
    });
}

// ── Sidebar ────────────────────────────────────────────────
function renderSidebar() {
  const nav = document.getElementById('nav-items');

  const itens = [
    { icon: '⊞',  label: 'Dashboard',     perm: null,                        id: 'dashboard'    },
    { icon: '📦', label: 'Estoque',        perm: PERMISSOES.VER_ESTOQUE,      id: 'estoque'      },
    { icon: '🚚', label: 'Fornecedores',   perm: PERMISSOES.VER_COMPRAS,      id: 'fornecedores' },
    { icon: '🧾', label: 'Notas Fiscais',  perm: PERMISSOES.VER_COMPRAS,      id: 'notas'        },
    { icon: '💰', label: 'Caixas',         perm: PERMISSOES.VER_VENDAS,       id: 'caixas'       },
    { icon: '📈', label: 'Cotações',       perm: PERMISSOES.VER_FINANCAS,     id: 'cotacoes'     },
    { icon: '👥', label: 'Funcionários',   perm: PERMISSOES.GERENCIAR_FUNCIONARIOS, id: 'funcionarios' },
    { icon: '📋', label: 'Logs',           perm: PERMISSOES.VER_LOGS,         id: 'logs'         },
  ];

  nav.innerHTML = itens
    .filter(item => !item.perm || temPermissao(item.perm))
    .map((item, i) => `
      <button class="nav-item ${i === 0 ? 'active' : ''}"
              onclick="navegarPara('${item.id}', this)">
        <span class="nav-icon">${item.icon}</span>
        ${item.label}
      </button>
    `).join('');
}

function navegarPara(id, el) {
  document.querySelectorAll('.nav-item').forEach(i => i.classList.remove('active'));
  el.classList.add('active');
  // Redireciona a navegação do menu para as rotas/páginas existentes.
    // Mantemos foco no fluxo de estoque: /estoque.
    if (id === 'dashboard') {
      window.location.href = '/dashboard.html';
      return;
    }

    if (id === 'estoque') {
      window.location.href = '/estoque';
      return;
    }

    window.location.href = `/${id}.html`;
  }

// ── User card ──────────────────────────────────────────────
function renderUserCard() {
  const initiais = `${usuario.nome[0]}${usuario.sobrenome[0]}`.toUpperCase();
  document.getElementById('user-avatar').textContent = initiais;
  document.getElementById('user-name').textContent   = usuario.nome + ' ' + usuario.sobrenome;
  document.getElementById('user-role').textContent   = usuario.perfil === 'ADMIN' ? 'Superior' : 'Operador';
}

function logout() {
  sessionStorage.removeItem('usuario');
  window.location.href = '/login.html';
}

// ── Summary cards ──────────────────────────────────────────
function renderSummaryCards(produtos) {
  const totalProdutos = produtos.length;
  const alertas = produtos.filter(p =>
    p.nivelEstoque === 'CRITICO' || p.nivelEstoque === 'BAIXO').length;
  const vendasDia = MOCK_CAIXAS.reduce((s, c) => s + c.vendas, 0);
  const caixasAbertos = MOCK_CAIXAS.filter(c => c.status === 'ABERTO').length;

  const cards = [
    {
      label: 'Total de Produtos',
      value: totalProdutos,
      icon: '📦',
      color: 'rgba(124,58,237,.15)',
      trend: 'neutral',
      trendText: 'cadastrados',
      perm: PERMISSOES.VER_ESTOQUE,
    },
    {
      label: 'Alertas de Estoque',
      value: alertas,
      icon: '⚠️',
      color: 'rgba(244,63,94,.12)',
      trend: alertas > 0 ? 'down' : 'up',
      trendText: alertas > 0 ? 'precisam de atenção' : 'tudo em ordem',
      perm: PERMISSOES.VER_ESTOQUE,
    },
    {
      label: 'Vendas do Dia',
      value: `R$${vendasDia.toFixed(2)}`,
      icon: '💰',
      color: 'rgba(16,185,129,.12)',
      trend: 'up',
      trendText: 'hoje',
      perm: PERMISSOES.VER_VENDAS,
    },
    {
      label: 'Caixas Abertos',
      value: `${caixasAbertos}/3`,
      icon: '🖥️',
      color: 'rgba(6,182,212,.12)',
      trend: 'neutral',
      trendText: 'em operação',
      perm: PERMISSOES.VER_VENDAS,
    },
  ];

  const grid = document.getElementById('summary-grid');
  grid.innerHTML = cards
    .filter(c => !c.perm || temPermissao(c.perm))
    .map((c, i) => `
      <div class="summary-card fade-up fade-up-${i+1}">
        <div class="summary-card-top">
          <span class="summary-label">${c.label}</span>
          <div class="summary-icon" style="background:${c.color}">${c.icon}</div>
        </div>
        <div class="summary-value">${c.value}</div>
        <div class="summary-trend trend-${c.trend}">
          ${c.trend === 'up' ? '↑' : c.trend === 'down' ? '↓' : '—'}
          ${c.trendText}
        </div>
      </div>
    `).join('');
}

// ── Seções por permissão ───────────────────────────────────
function renderPermissionSections() {
  if (temPermissao(PERMISSOES.VER_ESTOQUE)) {
    document.getElementById('section-alertas').classList.add('visible');
    document.getElementById('section-movimentacoes').classList.add('visible');
  }
  if (temPermissao(PERMISSOES.VER_FINANCAS) || temPermissao(PERMISSOES.VER_VENDAS)) {
    document.getElementById('section-financeiro').classList.add('visible');
  }
}

// ── Alertas de estoque ─────────────────────────────────────
function renderAlertas(produtos) {
  const alertas = produtos.filter(p => p.nivelEstoque !== 'ADEQUADO');
  const list = document.getElementById('alerta-list');

  if (alertas.length === 0) {
    list.innerHTML = '<div class="empty">✓ Todos os produtos estão em nível adequado.</div>';
    return;
  }

  const nivelClass = { CRITICO: 'critico', BAIXO: 'baixo', MODERADO: 'moderado' };
  const nivelLabel = { CRITICO: 'Crítico', BAIXO: 'Baixo', MODERADO: 'Moderado' };

  list.innerHTML = alertas.map(p => `
    <div class="alert-item ${nivelClass[p.nivelEstoque] || ''}">
      <div class="alert-dot"></div>
      <span class="alert-name">${p.nome}</span>
      <span class="alert-qty">${p.quantidadeAtual}/${p.quantidadeMinima} un.</span>
      <span class="alert-level">${nivelLabel[p.nivelEstoque] || p.nivelEstoque}</span>
    </div>
  `).join('');
}

// ── Movimentações recentes ─────────────────────────────────
function renderMovimentacoes() {
  const list = document.getElementById('mov-list');
  list.innerHTML = MOCK_MOVIMENTACOES.map(m => `
    <div class="mov-item">
      <span class="mov-badge ${m.tipo.toLowerCase()}">${m.tipo}</span>
      <span class="mov-produto">${m.produto}</span>
      <span class="mov-qty">${m.tipo === 'ENTRADA' ? '+' : '-'}${m.qtd} un.</span>
      <span class="mov-time">${m.hora}</span>
    </div>
  `).join('');
}

// ── Resumo financeiro ──────────────────────────────────────
function renderFinanceiro() {
  const totalVendas = MOCK_CAIXAS.reduce((s, c) => s + c.vendas, 0);
  const caixasAbertos = MOCK_CAIXAS.filter(c => c.status === 'ABERTO').length;

  document.getElementById('fin-total-vendas').textContent = `R$${totalVendas.toFixed(2)}`;
  document.getElementById('fin-caixas-abertos').textContent = caixasAbertos;
  document.getElementById('fin-ticket-medio').textContent =
    caixasAbertos > 0 ? `R$${(totalVendas / caixasAbertos).toFixed(2)}` : 'R$0,00';

  const caixaList = document.getElementById('caixa-list');
  caixaList.innerHTML = MOCK_CAIXAS.map(c => `
    <div class="caixa-item">
      <span class="caixa-name">Caixa ${c.numero} — ${c.operador}</span>
      <span class="caixa-status ${c.status.toLowerCase()}">${c.status}</span>
      <span class="caixa-valor">${c.status === 'ABERTO' ? 'R$' + c.vendas.toFixed(2) : '—'}</span>
    </div>
  `).join('');
}
