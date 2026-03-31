// ── Inicialização ──────────────────────────────────────────
async function carregarDados() {
  let produtos = [];
  let caixas = [];
  let movimentacoes = [];
  let metricas = null;

  try {
    const resp = await fetch('/api/produtos');
    if (!resp.ok) throw new Error('HTTP ' + resp.status);
    produtos = await resp.json();
  } catch (err) {
    console.error('Erro ao carregar produtos:', err);
  }

  try {
    const resp = await fetch('/api/caixas');
    if (!resp.ok) throw new Error('HTTP ' + resp.status);
    caixas = await resp.json();
  } catch (err) {
    console.error('Erro ao carregar caixas:', err);
  }

  try {
    const resp = await fetch('/api/movimentacoes');
    if (!resp.ok) throw new Error('HTTP ' + resp.status);
    movimentacoes = await resp.json();
  } catch (err) {
    console.error('Erro ao carregar movimentações:', err);
  }

  // BUG 2 fix: use dedicated metrics endpoint for correct ticket médio
  try {
    const resp = await fetch('/api/caixas/metricas');
    if (!resp.ok) throw new Error('HTTP ' + resp.status);
    metricas = await resp.json();
  } catch (err) {
    console.error('Erro ao carregar métricas:', err);
  }

  renderSummaryCards(produtos, caixas);
  renderAlertas(produtos);
  renderMovimentacoes(movimentacoes);
  renderFinanceiro(caixas, metricas);
}

document.addEventListener('DOMContentLoaded', function () {
  renderHeader();
  renderPermissionSections();
  carregarDados();
});

// ── Header ────────────────────────────────────────────────
function renderHeader() {
  const hora = new Date().getHours();
  const saudacao = hora < 12 ? 'Bom dia' : hora < 18 ? 'Boa tarde' : 'Boa noite';
  document.getElementById('header-title').textContent =
    saudacao + ', ' + usuario.nome + ' 👋';
  document.getElementById('header-sub').textContent =
    new Date().toLocaleDateString('pt-BR', {
      weekday: 'long', day: 'numeric', month: 'long', year: 'numeric'
    });
}

// ── Summary cards (Improvement 5: clickable cards) ────────
function renderSummaryCards(produtos, caixas) {
  var totalProdutos = produtos.length;
  var alertas = produtos.filter(function (p) {
    return p.nivelEstoque === 'CRITICO' || p.nivelEstoque === 'BAIXO';
  }).length;
  var vendasDia = caixas.reduce(function (s, c) { return s + (c.totalVendas || 0); }, 0);
  var caixasAbertos = caixas.filter(function (c) { return c.status === 'ABERTO'; }).length;
  var totalCaixas = caixas.length || '—';

  var cards = [
    {
      label: 'Total de Produtos',
      value: totalProdutos,
      icon: '📦',
      color: 'rgba(124,58,237,.15)',
      trend: 'neutral',
      trendText: 'cadastrados',
      perm: PERMISSOES.VER_ESTOQUE,
      href: '/estoque',
    },
    {
      label: 'Alertas de Estoque',
      value: alertas,
      icon: '⚠️',
      color: 'rgba(244,63,94,.12)',
      trend: alertas > 0 ? 'down' : 'up',
      trendText: alertas > 0 ? 'precisam de atenção' : 'tudo em ordem',
      perm: PERMISSOES.VER_ESTOQUE,
      href: '/estoque',
    },
    {
      label: 'Vendas do Dia',
      value: 'R$' + vendasDia.toFixed(2),
      icon: '💰',
      color: 'rgba(16,185,129,.12)',
      trend: 'up',
      trendText: 'hoje',
      perm: PERMISSOES.VER_VENDAS,
      href: '/caixas.html',
    },
    {
      label: 'Caixas Abertos',
      value: caixasAbertos + '/' + totalCaixas,
      icon: '🖥️',
      color: 'rgba(6,182,212,.12)',
      trend: 'neutral',
      trendText: 'em operação',
      perm: PERMISSOES.VER_VENDAS,
      href: '/caixas.html',
    },
  ];

  var grid = document.getElementById('summary-grid');
  grid.innerHTML = cards
    .filter(function (c) { return !c.perm || temPermissao(c.perm); })
    .map(function (c, i) {
      var trendIcon = c.trend === 'up' ? '↑' : c.trend === 'down' ? '↓' : '—';
      return '<div class="summary-card fade-up fade-up-' + (i + 1) + '" ' +
             'style="cursor:pointer;position:relative" ' +
             'onclick="window.location.href=\'' + c.href + '\'" ' +
             'onmouseover="this.style.transform=\'translateY(-2px)\'" ' +
             'onmouseout="this.style.transform=\'\'">' +
             '<div style="position:absolute;top:10px;right:10px;color:var(--text-3);font-size:11px">→</div>' +
             '<div class="summary-card-top">' +
             '<span class="summary-label">' + c.label + '</span>' +
             '<div class="summary-icon" style="background:' + c.color + '">' + c.icon + '</div>' +
             '</div>' +
             '<div class="summary-value">' + c.value + '</div>' +
             '<div class="summary-trend trend-' + c.trend + '">' +
             trendIcon + ' ' + c.trendText +
             '</div>' +
             '</div>';
    }).join('');
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
  var alertas = produtos.filter(function (p) { return p.nivelEstoque !== 'ADEQUADO'; });
  var list = document.getElementById('alerta-list');

  if (alertas.length === 0) {
    list.innerHTML = '<div class="empty">✓ Todos os produtos estão em nível adequado.</div>';
    return;
  }

  var nivelClass = { CRITICO: 'critico', BAIXO: 'baixo', MODERADO: 'moderado' };
  var nivelLabel = { CRITICO: 'Crítico', BAIXO: 'Baixo', MODERADO: 'Moderado' };

  list.innerHTML = alertas.map(function (p) {
    return '<div class="alert-item ' + (nivelClass[p.nivelEstoque] || '') + '">' +
           '<div class="alert-dot"></div>' +
           '<span class="alert-name">' + escapeHtml(p.nome) + '</span>' +
           '<span class="alert-qty">' + p.quantidadeAtual + '/' + p.quantidadeMinima + ' un.</span>' +
           '<span class="alert-level">' + (nivelLabel[p.nivelEstoque] || escapeHtml(p.nivelEstoque)) + '</span>' +
           '</div>';
  }).join('');
}

// ── Movimentações recentes (Improvement 6) ────────────────
function renderMovimentacoes(movimentacoes) {
  var list = document.getElementById('mov-list');
  if (!movimentacoes || movimentacoes.length === 0) {
    list.innerHTML =
      '<div style="padding:24px;text-align:center">' +
      '<div style="font-size:28px;margin-bottom:8px">📦</div>' +
      '<div style="color:var(--text-3);font-size:13px">Nenhuma movimentação registrada ainda.</div>' +
      '</div>';
    return;
  }

  list.innerHTML = movimentacoes.slice(0, 10).map(function (m) {
    // Extract HH:mm directly from the LocalDateTime string (e.g. "2025-03-01T10:30:00")
    var hora = '—';
    try {
      var s = m.dataHora || '';
      var tIdx = s.indexOf('T');
      if (tIdx !== -1) {
        hora = s.substring(tIdx + 1, tIdx + 6); // HH:mm
      } else {
        hora = new Date(s).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
      }
    } catch (e) {}
    return '<div class="mov-item">' +
           '<span class="mov-badge ' + (m.tipo || '').toLowerCase() + '">' + escapeHtml(m.tipo) + '</span>' +
           '<span class="mov-produto">' + escapeHtml(m.produto) + '</span>' +
           '<span class="mov-qty">' + (m.tipo === 'ENTRADA' ? '+' : '-') + m.quantidade + ' un.</span>' +
           '<span class="mov-time">' + escapeHtml(hora) + '</span>' +
           '</div>';
  }).join('');
}

// ── Resumo financeiro (BUG 2: correct ticket médio) ───────
function renderFinanceiro(caixas, metricas) {
  var totalVendas    = metricas ? metricas.totalVendas    : caixas.reduce(function (s, c) { return s + (c.totalVendas || 0); }, 0);
  var caixasAbertos  = metricas ? metricas.caixasAbertos  : caixas.filter(function (c) { return c.status === 'ABERTO'; }).length;
  var ticketMedio    = metricas ? metricas.ticketMedio    : 0;

  document.getElementById('fin-total-vendas').textContent   = 'R$' + totalVendas.toFixed(2);
  document.getElementById('fin-caixas-abertos').textContent = caixasAbertos;
  document.getElementById('fin-ticket-medio').textContent   = 'R$' + ticketMedio.toFixed(2);

  var caixaList = document.getElementById('caixa-list');
  if (caixas.length === 0) {
    caixaList.innerHTML =
      '<div style="padding:16px;text-align:center">' +
      '<div style="color:var(--text-3);font-size:13px">Nenhum caixa registrado.</div>' +
      '</div>';
    return;
  }
  caixaList.innerHTML = caixas.map(function (c) {
    return '<div class="caixa-item">' +
           '<span class="caixa-name">Caixa ' + c.numeroCaixa + (c.nomeOperador ? ' — ' + escapeHtml(c.nomeOperador) : '') + '</span>' +
           '<span class="caixa-status ' + escapeHtml(c.status.toLowerCase()) + '">' + escapeHtml(c.status) + '</span>' +
           '<span class="caixa-valor">' + (c.status === 'ABERTO' ? 'R$' + (c.totalVendas || 0).toFixed(2) : '—') + '</span>' +
           '</div>';
  }).join('');
}
