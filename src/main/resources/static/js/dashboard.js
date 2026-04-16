// ── Inicialização ──────────────────────────────────────────
const PODE_VER_ESTOQUE_DASH = temPermissao(PERMISSOES.VER_ESTOQUE);
const PODE_VER_FINANCEIRO_DASH = temAlgumaPermissao([
  PERMISSOES.VER_VENDAS,
  PERMISSOES.VER_FINANCAS,
]);

async function carregarDados() {
  let produtos = [];
  let caixas = [];
  let movimentacoes = [];
  let metricas = null;

  if (PODE_VER_ESTOQUE_DASH) {
    try {
      const resp = await fetch('/api/produtos');
      if (!resp.ok) throw new Error('HTTP ' + resp.status);
      produtos = await resp.json();
    } catch (err) {
      console.error('Erro ao carregar produtos:', err);
    }
  }

  if (PODE_VER_FINANCEIRO_DASH) {
    try {
      const resp = await fetch('/api/caixas');
      if (!resp.ok) throw new Error('HTTP ' + resp.status);
      caixas = await resp.json();
    } catch (err) {
      console.error('Erro ao carregar caixas:', err);
    }
  }

  if (PODE_VER_ESTOQUE_DASH) {
    try {
      const resp = await fetch('/api/movimentacoes');
      if (!resp.ok) throw new Error('HTTP ' + resp.status);
      movimentacoes = await resp.json();
    } catch (err) {
      console.error('Erro ao carregar movimentações:', err);
    }
  }

  if (PODE_VER_FINANCEIRO_DASH) {
    // BUG 2 fix: use dedicated metrics endpoint for correct ticket médio
    try {
      const resp = await fetch('/api/caixas/metricas');
      if (!resp.ok) throw new Error('HTTP ' + resp.status);
      metricas = await resp.json();
    } catch (err) {
      console.error('Erro ao carregar métricas:', err);
    }
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
      icon: 'box',
      tone: 'purple',
      trend: 'neutral',
      trendText: 'cadastrados',
      perm: PERMISSOES.VER_ESTOQUE,
      href: '/estoque',
    },
    {
      label: 'Alertas de Estoque',
      value: alertas,
      icon: 'alertTriangle',
      tone: 'red',
      trend: alertas > 0 ? 'down' : 'up',
      trendText: alertas > 0 ? 'precisam de atenção' : 'tudo em ordem',
      perm: PERMISSOES.VER_ESTOQUE,
      href: '/estoque',
    },
    {
      label: 'Vendas do Dia',
      value: 'R$' + vendasDia.toFixed(2),
      icon: 'wallet',
      tone: 'green',
      trend: 'up',
      trendText: 'hoje',
      permsAny: [PERMISSOES.VER_VENDAS, PERMISSOES.VER_FINANCAS],
      href: '/caixas.html',
    },
    {
      label: 'Caixas Abertos',
      value: caixasAbertos + '/' + totalCaixas,
      icon: 'chart',
      tone: 'cyan',
      trend: 'neutral',
      trendText: 'em operação',
      permsAny: [PERMISSOES.VER_VENDAS, PERMISSOES.VER_FINANCAS],
      href: '/caixas.html',
    },
  ];

  var grid = document.getElementById('summary-grid');
  grid.innerHTML = cards
    .filter(function (c) { return !c.permsAny || temAlgumaPermissao(c.permsAny); })
    .map(function (c, i) {
      var trendIcon = c.trend === 'up' ? '↑' : c.trend === 'down' ? '↓' : '—';
      return '<a class="summary-card summary-card--link fade-up fade-up-' + (i + 1) + '" href="' + c.href + '">' +
             '<span class="summary-card__arrow" aria-hidden="true">' + iconMarkup('arrowRight') + '</span>' +
             '<div class="summary-card-top">' +
             '<span class="summary-label">' + c.label + '</span>' +
             '<div class="summary-icon summary-icon--' + c.tone + '" aria-hidden="true">' + iconMarkup(c.icon) + '</div>' +
             '</div>' +
             '<div class="summary-value">' + c.value + '</div>' +
             '<div class="summary-trend trend-' + c.trend + '">' +
             trendIcon + ' ' + c.trendText +
             '</div>' +
             '</a>';
    }).join('');
}

// ── Seções por permissão ───────────────────────────────────
function renderPermissionSections() {
  if (PODE_VER_ESTOQUE_DASH) {
    document.getElementById('section-alertas').classList.add('visible');
    document.getElementById('section-movimentacoes').classList.add('visible');
  }
  if (PODE_VER_FINANCEIRO_DASH) {
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
    list.innerHTML = emptyStateMarkup({
      icon: 'box',
      title: 'Nenhuma movimentação registrada',
      copy: 'As entradas e saídas recentes aparecerão aqui ao longo do dia.',
      compact: true
    });
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
    caixaList.innerHTML = emptyStateMarkup({
      icon: 'wallet',
      title: 'Nenhum caixa registrado',
      copy: 'Os caixas operacionais do dia serão listados aqui.',
      compact: true
    });
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
let chartLucratividade = null;

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

const CAMPOS_PRECO_VENDA = ['precoUnitario', 'precoVenda', 'valorVenda', 'preco', 'precoAtual'];
const CAMPOS_PRECO_CUSTO = [
  'custoUnitario',
  'precoCusto',
  'valorCusto',
  'precoCompra',
  'precoUnitarioBase',
  'custoMedio',
  'ultimoCusto',
  'custo'
];

const FORMATO_MOEDA_BR = new Intl.NumberFormat('pt-BR', {
  style: 'currency',
  currency: 'BRL',
});

function parseNumeroSeguro(valor) {
  if (valor === null || valor === undefined || valor === '') return null;
  if (typeof valor === 'number') return Number.isFinite(valor) ? valor : null;

  var texto = String(valor).trim();
  if (!texto) return null;
  texto = texto.replace(/R\$\s?/g, '');

  if (texto.includes(',') && texto.includes('.')) {
    texto = texto.replace(/\./g, '').replace(',', '.');
  } else {
    texto = texto.replace(',', '.');
  }

  var numero = Number(texto);
  return Number.isFinite(numero) ? numero : null;
}

function resolverCampoNumerico(obj, campos) {
  var encontrado = { key: null, raw: null, value: null };
  if (!obj) return encontrado;

  for (var i = 0; i < campos.length; i++) {
    var chave = campos[i];
    if (!Object.prototype.hasOwnProperty.call(obj, chave)) continue;

    var raw = obj[chave];
    var value = parseNumeroSeguro(raw);
    if (encontrado.key === null) {
      encontrado = { key: chave, raw: raw, value: value };
    }
    if (value !== null) {
      return { key: chave, raw: raw, value: value };
    }
  }

  return encontrado;
}

function formatarMoedaBR(valor) {
  return Number.isFinite(valor) ? FORMATO_MOEDA_BR.format(valor) : '—';
}

function formatarPercentual(valor) {
  if (!Number.isFinite(valor)) return '—';
  return valor.toLocaleString('pt-BR', {
    minimumFractionDigits: 1,
    maximumFractionDigits: 1,
  }) + '%';
}

function resumirTexto(texto, limite) {
  var base = String(texto || '');
  if (base.length <= limite) return base;
  return base.slice(0, Math.max(0, limite - 1)).trimEnd() + '…';
}

function obterClassificacaoMargem(margem) {
  if (margem >= 50) {
    return {
      chart: COR.verde,
      chartSoft: COR.verdeSoft,
      table: 'good',
      status: 'high',
      label: 'Alta margem',
    };
  }
  if (margem >= 25) {
    return {
      chart: COR.ambar,
      chartSoft: COR.ambarSoft,
      table: 'medium',
      status: 'medium',
      label: 'Margem saudável',
    };
  }
  return {
    chart: COR.vermelho,
    chartSoft: COR.vermelhoSoft,
    table: 'low',
    status: 'low',
    label: margem < 0 ? 'Margem negativa' : 'Baixa margem',
  };
}

function normalizarProdutoLucratividade(produto, indice) {
  var produtoBase = produto || {};
  var vendaInfo = resolverCampoNumerico(produtoBase, CAMPOS_PRECO_VENDA);
  var custoInfo = resolverCampoNumerico(produtoBase, CAMPOS_PRECO_CUSTO);
  var precoVenda = vendaInfo.value;
  var precoCusto = custoInfo.value;
  var custoZeroAssumidoComoPendente = precoCusto === 0
    && !(produtoBase.custoDefinido === true || produtoBase.temCusto === true || produtoBase.possuiCusto === true);
  var vendaValida = Number.isFinite(precoVenda) && precoVenda > 0;
  var custoValido = Number.isFinite(precoCusto) && precoCusto >= 0 && !custoZeroAssumidoComoPendente;
  var temMargemValida = vendaValida && custoValido;
  var lucroUnitario = temMargemValida ? precoVenda - precoCusto : null;
  var margem = temMargemValida && precoVenda > 0 ? (lucroUnitario / precoVenda) * 100 : null;
  var classificacao = temMargemValida
    ? obterClassificacaoMargem(margem)
    : {
        chart: '#94a3b8',
        chartSoft: 'rgba(148,163,184,.18)',
        table: 'pending',
        status: 'pending',
        label: !custoValido ? '⚠ pendente' : 'Preço inválido',
      };

  return {
    id: produtoBase.id !== undefined ? produtoBase.id : (indice + 1),
    nome: String(produtoBase.nome || ('Produto ' + (indice + 1))).trim(),
    precoVenda: precoVenda,
    precoCusto: precoCusto,
    lucroUnitario: lucroUnitario,
    margem: margem,
    vendaValida: vendaValida,
    custoValido: custoValido,
    temMargemValida: temMargemValida,
    classificacao: classificacao,
    produtoOriginal: produtoBase,
    vendaCampo: vendaInfo.key,
    custoCampo: custoInfo.key,
  };
}

function gerarAnaliseLucratividade(produtos) {
  var itens = (produtos || []).map(normalizarProdutoLucratividade);
  var ordenados = itens.slice().sort(function (a, b) {
    if (a.temMargemValida && b.temMargemValida) {
      if (b.margem !== a.margem) return b.margem - a.margem;
      if (b.lucroUnitario !== a.lucroUnitario) return b.lucroUnitario - a.lucroUnitario;
      return a.nome.localeCompare(b.nome, 'pt-BR');
    }
    if (a.temMargemValida) return -1;
    if (b.temMargemValida) return 1;

    var prioridadeA = a.custoValido ? 1 : 0;
    var prioridadeB = b.custoValido ? 1 : 0;
    if (prioridadeA !== prioridadeB) return prioridadeA - prioridadeB;
    return a.nome.localeCompare(b.nome, 'pt-BR');
  });

  var ranking = 0;
  ordenados.forEach(function (item) {
    item.posicao = item.temMargemValida ? ++ranking : null;
  });

  var validos = ordenados.filter(function (item) { return item.temMargemValida; });
  var pendentesCusto = ordenados.filter(function (item) { return !item.custoValido; });
  var mediaMargem = validos.length
    ? validos.reduce(function (soma, item) { return soma + item.margem; }, 0) / validos.length
    : null;

  return {
    itens: ordenados,
    validos: validos,
    pendentesCusto: pendentesCusto,
    mediaMargem: mediaMargem,
    maisLucrativo: validos.length ? validos[0] : null,
    menorMargem: validos.length ? validos[validos.length - 1] : null,
  };
}

function atualizarResumoLucratividade(analise) {
  var grid = document.getElementById('profit-summary-grid');
  if (!grid) return;

  var mediaValor = analise.mediaMargem !== null
    ? '<div class="profit-summary-value">' + formatarPercentual(analise.mediaMargem) + '</div>'
    : '<div class="profit-summary-value">—</div>';

  var maisValor = analise.maisLucrativo
    ? '<div class="profit-summary-value profit-summary-value--compact">' +
        escapeHtml(resumirTexto(analise.maisLucrativo.nome, 28)) + '</div>'
    : '<div class="profit-summary-value">—</div>';

  var menorValor = analise.menorMargem
    ? '<div class="profit-summary-value profit-summary-value--compact">' +
        escapeHtml(resumirTexto(analise.menorMargem.nome, 28)) + '</div>'
    : '<div class="profit-summary-value">—</div>';

  var cards = [
    {
      label: 'Margem Média',
      icon: 'chart',
      tone: 'green',
      valueHtml: mediaValor,
      meta: analise.validos.length
        ? analise.validos.length + ' produto(s) com margem válida.'
        : 'Sem base suficiente para média.',
    },
    {
      label: 'Mais Lucrativo',
      icon: 'checkCircle',
      tone: 'amber',
      valueHtml: maisValor,
      meta: analise.maisLucrativo
        ? formatarPercentual(analise.maisLucrativo.margem) + ' de margem · lucro/un. ' +
          formatarMoedaBR(analise.maisLucrativo.lucroUnitario)
        : 'Nenhum produto elegível para ranking.',
    },
    {
      label: 'Menor Margem',
      icon: 'alertTriangle',
      tone: 'red',
      valueHtml: menorValor,
      meta: analise.menorMargem
        ? formatarPercentual(analise.menorMargem.margem) + ' de margem · lucro/un. ' +
          formatarMoedaBR(analise.menorMargem.lucroUnitario)
        : 'Ainda não há margem válida para comparar.',
    },
    {
      label: 'Sem Custo Definido',
      icon: 'info',
      tone: 'slate',
      valueHtml: '<div class="profit-summary-value">' + analise.pendentesCusto.length + '</div>',
      meta: analise.itens.length
        ? analise.pendentesCusto.length + ' de ' + analise.itens.length + ' produto(s) aguardam custo.'
        : 'Nenhum produto cadastrado.',
    },
  ];

  grid.innerHTML = cards.map(function (card) {
    return '<div class="profit-summary-card">' +
             '<div class="profit-summary-top">' +
                '<span class="profit-summary-label">' + card.label + '</span>' +
                '<span class="profit-summary-icon ' + card.tone + '" aria-hidden="true">' + iconMarkup(card.icon) + '</span>' +
              '</div>' +
              card.valueHtml +
              '<div class="profit-summary-meta">' + card.meta + '</div>' +
           '</div>';
  }).join('');

  var badge = document.getElementById('profitability-badge');
  if (badge) {
    if (!analise.itens.length) {
      badge.textContent = 'sem produtos';
    } else if (!analise.validos.length) {
      badge.textContent = analise.pendentesCusto.length + ' custo(s) pendentes';
    } else {
      badge.textContent = analise.validos.length + ' produto(s) analisados';
    }
  }
}

function renderGraficoLucratividade(analise) {
  var canvas = document.getElementById('grafico-lucratividade');
  var empty = document.getElementById('profit-chart-empty');
  var scroll = document.getElementById('profit-chart-scroll');
  var wrap = document.getElementById('profit-chart-wrap');
  var badge = document.getElementById('profit-chart-badge');

  if (!canvas) return;
  if (chartLucratividade) { chartLucratividade.destroy(); chartLucratividade = null; }

  if (badge) {
    badge.textContent = analise.validos.length + ' produto(s) elegíveis';
  }

  if (!analise.validos.length || typeof Chart === 'undefined') {
    canvas.hidden = true;
    if (scroll) scroll.hidden = true;
    if (empty) {
      empty.hidden = false;
      if (typeof Chart === 'undefined') {
        empty.textContent = 'Chart.js não está disponível para renderizar o gráfico de margem.';
      }
    }
    return;
  }

  canvas.hidden = false;
  if (scroll) scroll.hidden = false;
  if (empty) empty.hidden = true;

  var minimo = Math.min.apply(null, analise.validos.map(function (item) { return item.margem; }));
  var maximo = Math.max.apply(null, analise.validos.map(function (item) { return item.margem; }));
  var eixoMin = Math.min(0, Math.floor(minimo / 10) * 10);
  var eixoMax = Math.max(10, Math.ceil(maximo / 10) * 10);

  if (wrap) {
    wrap.style.height = Math.max(280, analise.validos.length * 42) + 'px';
  }

  chartLucratividade = new Chart(canvas, {
    type: 'bar',
    data: {
      labels: analise.validos.map(function (item) { return resumirTexto(item.nome, 28); }),
      datasets: [{
        label: 'Margem',
        data: analise.validos.map(function (item) {
          return Number(item.margem.toFixed(2));
        }),
        backgroundColor: analise.validos.map(function (item) { return item.classificacao.chartSoft; }),
        borderColor: analise.validos.map(function (item) { return item.classificacao.chart; }),
        borderWidth: 1.5,
        borderRadius: 4,
        maxBarThickness: 22,
      }],
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      indexAxis: 'y',
      plugins: {
        legend: { display: false },
        tooltip: {
          ...TOOLTIP_BASE,
          callbacks: {
            title: function (items) {
              return items.length ? analise.validos[items[0].dataIndex].nome : '';
            },
            label: function (ctx) {
              var item = analise.validos[ctx.dataIndex];
              return [
                'Margem: ' + formatarPercentual(item.margem),
                'Preço de venda: ' + formatarMoedaBR(item.precoVenda),
                'Preço de custo: ' + formatarMoedaBR(item.precoCusto),
                'Lucro por unidade: ' + formatarMoedaBR(item.lucroUnitario),
              ];
            },
          },
        },
      },
      scales: {
        x: {
          min: eixoMin,
          max: eixoMax,
          grid: { color: COR.borda + '55' },
          ticks: {
            color: COR.texto,
            font: { size: 11 },
            callback: function (valor) {
              return valor.toLocaleString('pt-BR') + '%';
            },
          },
        },
        y: {
          grid: { display: false },
          ticks: { color: COR.texto, font: { size: 11 } },
        },
      },
    },
  });
}

function renderTabelaLucratividade(analise) {
  var tbody = document.getElementById('profit-ranking-body');
  var empty = document.getElementById('profit-table-empty');
  var wrap = document.getElementById('profit-table-wrap');
  var badge = document.getElementById('profit-table-badge');

  if (!tbody) return;
  if (badge) {
    badge.textContent = analise.validos.length + ' ranqueados · ' + analise.pendentesCusto.length + ' pendentes';
  }

  if (!analise.itens.length) {
    tbody.innerHTML = '';
    if (empty) empty.hidden = false;
    if (wrap) wrap.hidden = true;
    return;
  }

  if (empty) empty.hidden = true;
  if (wrap) wrap.hidden = false;

  tbody.innerHTML = analise.itens.map(function (item) {
    var produto = item.produtoOriginal || {};
    var meta = [];
    if (produto.quantidadeAtual !== undefined) meta.push('Estoque: ' + produto.quantidadeAtual + ' un.');
    if (produto.quantidadeMinima !== undefined) meta.push('Mínimo: ' + produto.quantidadeMinima + ' un.');
    if (!meta.length && item.vendaCampo) meta.push('Venda via campo "' + escapeHtml(item.vendaCampo) + '".');

    var lucroClasse = item.temMargemValida
      ? (item.lucroUnitario >= 0 ? 'positive' : 'negative')
      : '';

    return '<tr class="' + (item.temMargemValida ? '' : 'is-pending') + '">' +
             '<td class="profit-rank">' + (item.posicao ? item.posicao + 'º' : '—') + '</td>' +
             '<td>' +
               '<div class="profit-product-name">' + escapeHtml(item.nome) + '</div>' +
               '<div class="profit-product-meta">' + escapeHtml(meta.join(' · ') || 'Sem informações adicionais.') + '</div>' +
             '</td>' +
             '<td>' + (item.vendaValida ? formatarMoedaBR(item.precoVenda) : '—') + '</td>' +
             '<td>' + (item.custoValido ? formatarMoedaBR(item.precoCusto) : '—') + '</td>' +
             '<td class="profit-money ' + lucroClasse + '">' +
               (item.temMargemValida ? formatarMoedaBR(item.lucroUnitario) : '—') +
             '</td>' +
             '<td class="profit-margin ' + item.classificacao.table + '">' +
               (item.temMargemValida ? formatarPercentual(item.margem) : '—') +
             '</td>' +
             '<td><span class="profit-status ' + item.classificacao.status + '">' +
               item.classificacao.label +
             '</span></td>' +
           '</tr>';
  }).join('');
}

function renderAnaliseLucratividade(produtos) {
  var analise = gerarAnaliseLucratividade(produtos);
  atualizarResumoLucratividade(analise);
  renderGraficoLucratividade(analise);
  renderTabelaLucratividade(analise);
}

// ── Ponto de entrada ──────────────────────────────────────────────────────

function renderGraficos(produtos, movimentacoes, caixas) {
  renderAnaliseLucratividade(produtos);
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
    if (wrap) wrap.hidden = true;
    if (empty) empty.hidden = false;
    return;
  }

  if (wrap)  wrap.style.height  = Math.max(180, lista.length * 34) + 'px';
  if (wrap) wrap.hidden = false;
  if (empty) empty.hidden = true;

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
    canvas.hidden = true;
    if (empty) empty.hidden = false;
    if (legend) legend.innerHTML = '';
    return;
  }
  canvas.hidden = false;
  if (empty) empty.hidden = true;

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
      legendItemMarkup({ tone: 'green', label: 'Entradas', value: entradas }) +
      legendItemMarkup({ tone: 'red', label: 'Saídas', value: saidas });
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
    canvas.hidden = true;
    if (empty) empty.hidden = false;
    return;
  }
  canvas.hidden = false;
  if (empty) empty.hidden = true;

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
    { chave: 'SEM_ESTOQUE', label: 'Sem estoque', cor: COR.vermelho, tone: 'red' },
    { chave: 'CRITICO',     label: 'Crítico', cor: '#ff6b35', tone: 'orange' },
    { chave: 'BAIXO',       label: 'Baixo', cor: COR.ambar, tone: 'amber' },
    { chave: 'MODERADO',    label: 'Moderado', cor: COR.cyan, tone: 'cyan' },
    { chave: 'ADEQUADO',    label: 'Adequado', cor: COR.verde, tone: 'green' },
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
    legend.innerHTML = ativos.map(function (n) {
      return legendItemMarkup({
        tone: n.tone,
        label: n.label,
        value: contagem[n.chave],
        square: true
      });
    }).join('');
  }
}
