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
  // Salva dados para uso quando a aba de gráficos for aberta
  window._dadosProdutos = produtos;
  window._dadosMov      = movimentacoes;
  window._dadosCaixas   = caixas;
  window._graficosRendered = false; // força re-render se dados recarregarem
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
           '<span class="alert-name">' + p.nome + '</span>' +
           '<span class="alert-qty">' + p.quantidadeAtual + '/' + p.quantidadeMinima + ' un.</span>' +
           '<span class="alert-level">' + (nivelLabel[p.nivelEstoque] || p.nivelEstoque) + '</span>' +
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
           '<span class="mov-badge ' + (m.tipo || '').toLowerCase() + '">' + m.tipo + '</span>' +
           '<span class="mov-produto">' + m.produto + '</span>' +
           '<span class="mov-qty">' + (m.tipo === 'ENTRADA' ? '+' : '-') + m.quantidade + ' un.</span>' +
           '<span class="mov-time">' + hora + '</span>' +
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
           '<span class="caixa-name">Caixa ' + c.numeroCaixa + (c.nomeOperador ? ' — ' + c.nomeOperador : '') + '</span>' +
           '<span class="caixa-status ' + c.status.toLowerCase() + '">' + c.status + '</span>' +
           '<span class="caixa-valor">' + (c.status === 'ABERTO' ? 'R$' + (c.totalVendas || 0).toFixed(2) : '—') + '</span>' +
           '</div>';
  }).join('');
}


// ── Navegação de abas do dashboard ────────────────────────────────────────

function abrirTabDash(aba) {
  document.querySelectorAll('#tab-visao, #tab-graficos').forEach(b => b.classList.remove('active'));
  document.querySelectorAll('#tabpanel-visao, #tabpanel-graficos').forEach(p => p.classList.remove('active'));
  document.getElementById('tab-' + aba).classList.add('active');
  document.getElementById('tabpanel-' + aba).classList.add('active');

  // Renderiza gráficos na primeira vez que a aba for aberta
  if (aba === 'graficos' && !window._graficosRendered) {
    window._graficosRendered = true;
    renderGraficos(window._dadosProdutos || [], window._dadosMov || [], window._dadosCaixas || []);
  }
}

// ── Cache de dados para os gráficos ──────────────────────────────────────
// Os dados são buscados em carregarDados() e guardados globalmente para
// que renderGraficos() possa usá-los quando a aba for aberta pela 1ª vez.

window._dadosProdutos = [];
window._dadosMov      = [];
window._dadosCaixas   = [];
window._graficosRendered = false;

// ── Instâncias Chart.js (para .destroy() ao recarregar) ──────────────────

let chartEstoque = null;
let chartMov     = null;
let chartCaixas  = null;
let chartAlertas = null;

// ── Paleta de cores (valores fixos — CSS vars não funcionam em <canvas>) ──

const COR = {
  roxo:        '#7c3aed',
  verde:       '#10b981',
  verdeSoft:   'rgba(16,185,129,.22)',
  cyan:        '#06b6d4',
  cyanSoft:    'rgba(6,182,212,.22)',
  ambar:       '#f59e0b',
  ambarSoft:   'rgba(245,158,11,.22)',
  vermelho:    '#f43f5e',
  vermelhoSoft:'rgba(244,63,94,.22)',
  texto:       '#8899bb',
  borda:       '#1e2d4a',
};

const TOOLTIP_BASE = {
  backgroundColor: '#0d1526',
  borderColor:     '#1e2d4a',
  borderWidth:     1,
  titleColor:      '#f0f4ff',
  bodyColor:       '#8899bb',
  padding:         10,
};

// ── Ponto de entrada ──────────────────────────────────────────────────────

function renderGraficos(produtos, movimentacoes, caixas) {
  if (typeof Chart === 'undefined') {
    console.warn('Chart.js não carregou — gráficos indisponíveis.');
    return;
  }
  renderGraficoEstoque(produtos);
  renderGraficoMovimentacoes(movimentacoes);
  renderGraficoCaixas(caixas);
  renderGraficoAlertas(produtos);
}

// ── Gráfico 1: Nível de estoque por produto ───────────────────────────────
// Barras horizontais, ordenadas do mais crítico ao mais saudável.
// Barra colorida = qtd atual; barra tracejada = qtd mínima.

function renderGraficoEstoque(produtos) {
  const canvas = document.getElementById('grafico-estoque');
  const empty  = document.getElementById('empty-g-estoque');
  const wrap   = document.getElementById('wrap-g-estoque');
  if (!canvas) return;
  if (chartEstoque) { chartEstoque.destroy(); chartEstoque = null; }

  const COR_NIVEL = {
    SEM_ESTOQUE: COR.vermelho,
    CRITICO:     COR.vermelho,
    BAIXO:       COR.ambar,
    MODERADO:    COR.cyan,
    ADEQUADO:    COR.verde,
  };

  const lista = [...produtos]
    .filter(p => p.quantidadeMinima > 0 || p.quantidadeAtual > 0)
    .sort((a, b) => {
      const pa = a.quantidadeMinima > 0 ? a.quantidadeAtual / a.quantidadeMinima : 1;
      const pb = b.quantidadeMinima > 0 ? b.quantidadeAtual / b.quantidadeMinima : 1;
      return pa - pb;
    })
    .slice(0, 14);

  if (lista.length === 0) {
    if (wrap)  wrap.style.display  = 'none';
    if (empty) empty.style.display = 'block';
    return;
  }

  if (wrap)  wrap.style.height  = Math.max(180, lista.length * 34) + 'px';
  if (empty) empty.style.display = 'none';

  chartEstoque = new Chart(canvas, {
    type: 'bar',
    data: {
      labels: lista.map(p => p.nome),
      datasets: [
        {
          label:           'Qtd atual',
          data:            lista.map(p => p.quantidadeAtual),
          backgroundColor: lista.map(p => (COR_NIVEL[p.nivelEstoque] || COR.texto) + 'aa'),
          borderColor:     lista.map(p =>  COR_NIVEL[p.nivelEstoque] || COR.texto),
          borderWidth: 1, borderRadius: 3,
        },
        {
          label:           'Mínimo',
          data:            lista.map(p => p.quantidadeMinima),
          backgroundColor: 'transparent',
          borderColor:     COR.borda,
          borderWidth:     1.5,
          borderRadius:    3,
          // Chart.js 4 não suporta borderDash por dataset em bar, mas
          // a barra transparente serve como referência visual
        },
      ],
    },
    options: {
      responsive: true, maintainAspectRatio: false,
      indexAxis: 'y',
      plugins: {
        legend: {
          display: true, position: 'bottom',
          labels: { color: COR.texto, boxWidth: 10, padding: 14, font: { size: 11 } },
        },
        tooltip: {
          ...TOOLTIP_BASE,
          callbacks: {
            label: ctx => {
              const p = lista[ctx.dataIndex];
              if (ctx.datasetIndex === 0) {
                const pct = p.quantidadeMinima > 0
                  ? Math.round(p.quantidadeAtual / p.quantidadeMinima * 100) : 100;
                return ` Atual: ${p.quantidadeAtual} un. (${pct}% do mínimo)`;
              }
              return ` Mínimo: ${p.quantidadeMinima} un.`;
            },
          },
        },
      },
      scales: {
        x: {
          beginAtZero: true,
          grid:  { color: COR.borda + '55' },
          ticks: { color: COR.texto, font: { size: 11 } },
        },
        y: {
          grid:  { display: false },
          ticks: { color: COR.texto, font: { size: 11 } },
        },
      },
    },
  });

  const badge = document.getElementById('badge-g-estoque');
  if (badge) badge.textContent = lista.length + ' produto(s)';
}

// ── Gráfico 2: Entradas vs Saídas (doughnut) ─────────────────────────────

function renderGraficoMovimentacoes(movimentacoes) {
  const canvas = document.getElementById('grafico-mov');
  const empty  = document.getElementById('empty-g-mov');
  const legend = document.getElementById('legend-g-mov');
  if (!canvas) return;
  if (chartMov) { chartMov.destroy(); chartMov = null; }

  const entradas = movimentacoes.filter(m => m.tipo === 'ENTRADA').length;
  const saidas   = movimentacoes.filter(m => m.tipo === 'SAIDA').length;
  const total    = entradas + saidas;

  if (total === 0) {
    canvas.style.display = 'none';
    if (empty)  empty.style.display  = 'block';
    if (legend) legend.innerHTML = '';
    return;
  }
  canvas.style.display = 'block';
  if (empty) empty.style.display = 'none';

  chartMov = new Chart(canvas, {
    type: 'doughnut',
    data: {
      labels: ['Entradas', 'Saídas'],
      datasets: [{
        data:            [entradas, saidas],
        backgroundColor: [COR.verdeSoft, COR.vermelhoSoft],
        borderColor:     [COR.verde,     COR.vermelho],
        borderWidth:     2,
        hoverOffset:     6,
      }],
    },
    options: {
      responsive: true, maintainAspectRatio: false,
      cutout: '68%',
      plugins: {
        legend: { display: false },
        tooltip: {
          ...TOOLTIP_BASE,
          callbacks: {
            label: ctx => ` ${ctx.label}: ${ctx.raw} (${Math.round(ctx.raw / total * 100)}%)`,
          },
        },
      },
    },
  });

  if (legend) {
    legend.innerHTML =
      `<span style="display:flex;align-items:center;gap:5px">
         <span style="width:9px;height:9px;border-radius:50%;background:${COR.verde};flex-shrink:0"></span>
         Entradas: <strong style="color:${COR.verde}">${entradas}</strong>
       </span>` +
      `<span style="display:flex;align-items:center;gap:5px">
         <span style="width:9px;height:9px;border-radius:50%;background:${COR.vermelho};flex-shrink:0"></span>
         Saídas: <strong style="color:${COR.vermelho}">${saidas}</strong>
       </span>`;
  }
}

// ── Gráfico 3: Vendas por caixa (barras verticais agrupadas) ─────────────

function renderGraficoCaixas(caixas) {
  const canvas = document.getElementById('grafico-caixas');
  const empty  = document.getElementById('empty-g-caixas');
  if (!canvas) return;
  if (chartCaixas) { chartCaixas.destroy(); chartCaixas = null; }

  const comDados = caixas.filter(c => (parseFloat(c.totalVendas) || 0) > 0 || c.status === 'ABERTO');

  if (comDados.length === 0) {
    canvas.style.display = 'none';
    if (empty) empty.style.display = 'block';
    return;
  }
  canvas.style.display = 'block';
  if (empty) empty.style.display = 'none';

  chartCaixas = new Chart(canvas, {
    type: 'bar',
    data: {
      labels: comDados.map(c => 'Caixa ' + c.numeroCaixa),
      datasets: [
        {
          label:           'Vendas',
          data:            comDados.map(c => parseFloat(c.totalVendas)   || 0),
          backgroundColor: COR.verdeSoft,
          borderColor:     COR.verde,
          borderWidth:     1.5, borderRadius: 4,
        },
        {
          label:           'Entradas',
          data:            comDados.map(c => parseFloat(c.totalEntradas) || 0),
          backgroundColor: COR.cyanSoft,
          borderColor:     COR.cyan,
          borderWidth:     1.5, borderRadius: 4,
        },
        {
          label:           'Saídas',
          data:            comDados.map(c => parseFloat(c.totalSaidas)   || 0),
          backgroundColor: COR.vermelhoSoft,
          borderColor:     COR.vermelho,
          borderWidth:     1.5, borderRadius: 4,
        },
      ],
    },
    options: {
      responsive: true, maintainAspectRatio: false,
      plugins: {
        legend: {
          display: true, position: 'bottom',
          labels: { color: COR.texto, boxWidth: 10, padding: 14, font: { size: 11 } },
        },
        tooltip: {
          ...TOOLTIP_BASE,
          callbacks: { label: ctx => ` ${ctx.dataset.label}: R$${ctx.raw.toFixed(2)}` },
        },
      },
      scales: {
        x: { grid: { display: false }, ticks: { color: COR.texto, font: { size: 12 } } },
        y: {
          beginAtZero: true,
          grid:  { color: COR.borda + '55' },
          ticks: { color: COR.texto, font: { size: 11 }, callback: v => 'R$' + v },
        },
      },
    },
  });
}

// ── Gráfico 4: Distribuição de alertas (doughnut) ────────────────────────

function renderGraficoAlertas(produtos) {
  const canvas = document.getElementById('grafico-alertas');
  const legend = document.getElementById('legend-g-alertas');
  if (!canvas) return;
  if (chartAlertas) { chartAlertas.destroy(); chartAlertas = null; }

  const NIVEIS = [
    { chave: 'SEM_ESTOQUE', label: 'Sem estoque', cor: COR.vermelho },
    { chave: 'CRITICO',     label: 'Crítico',     cor: '#ff6b35'    },
    { chave: 'BAIXO',       label: 'Baixo',       cor: COR.ambar    },
    { chave: 'MODERADO',    label: 'Moderado',     cor: COR.cyan     },
    { chave: 'ADEQUADO',    label: 'Adequado',     cor: COR.verde    },
  ];

  const contagem = {};
  NIVEIS.forEach(n => { contagem[n.chave] = 0; });
  produtos.forEach(p => { if (contagem[p.nivelEstoque] !== undefined) contagem[p.nivelEstoque]++; });

  const ativos = NIVEIS.filter(n => contagem[n.chave] > 0);
  if (ativos.length === 0) return;

  chartAlertas = new Chart(canvas, {
    type: 'doughnut',
    data: {
      labels:   ativos.map(n => n.label),
      datasets: [{
        data:            ativos.map(n => contagem[n.chave]),
        backgroundColor: ativos.map(n => n.cor + '44'),
        borderColor:     ativos.map(n => n.cor),
        borderWidth:     2,
        hoverOffset:     6,
      }],
    },
    options: {
      responsive: true, maintainAspectRatio: false,
      cutout: '60%',
      plugins: {
        legend: { display: false },
        tooltip: {
          ...TOOLTIP_BASE,
          callbacks: { label: ctx => ` ${ctx.label}: ${ctx.raw} produto(s)` },
        },
      },
    },
  });

  if (legend) {
    legend.innerHTML = ativos.map(n => `
      <span style="display:flex;align-items:center;gap:6px">
        <span style="width:8px;height:8px;border-radius:2px;background:${n.cor};flex-shrink:0"></span>
        ${n.label}: <strong style="color:${n.cor}">${contagem[n.chave]}</strong>
      </span>`).join('');
  }
}
