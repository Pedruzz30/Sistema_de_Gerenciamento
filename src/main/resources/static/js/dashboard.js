/* SPA-isolated screen: dashboard. Prevent cross-page handler overwrites. */
(() => {
'use strict';
// ── Inicialização ──────────────────────────────────────────
const PODE_VER_ESTOQUE_DASH = temPermissao(PERMISSOES.VER_ESTOQUE);
const PODE_VER_FINANCEIRO_DASH = temAlgumaPermissao([
  PERMISSOES.VER_VENDAS,
  PERMISSOES.VER_FINANCAS,
]);
const PODE_VER_RENTABILIDADE_DASH = temPermissao(PERMISSOES.VER_FINANCAS);

// Contagem e lista usam exatamente as mesmas regras de alerta.
function obterAlertasEstoque(produtos) {
  const ordem = { SEM_ESTOQUE: 0, CRITICO: 1, BAIXO: 2, MODERADO: 3 };
  return (produtos || [])
    .filter(p => p && p.ativo !== false && Object.prototype.hasOwnProperty.call(ordem, p.nivelEstoque))
    .sort((a, b) => ordem[a.nivelEstoque] - ordem[b.nivelEstoque] || String(a.nome || '').localeCompare(String(b.nome || ''), 'pt-BR'));
}

function dataLocalHojeIso() {
  const agora = new Date();
  return [agora.getFullYear(), String(agora.getMonth() + 1).padStart(2, '0'), String(agora.getDate()).padStart(2, '0')].join('-');
}

// ── Backup da loja: só aparece na instalação local, para quem cuida do financeiro.
function descreverDataBackup(iso) {
  const data = new Date(iso);
  const hora = data.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
  const hoje = new Date();
  if (data.toDateString() === hoje.toDateString()) return 'hoje às ' + hora;
  return data.toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' }) + ' às ' + hora;
}

function renderSituacaoBackup(situacao) {
  const painel = document.getElementById('backup-painel');
  if (!painel) return;
  if (!situacao || !situacao.monitorado) { painel.hidden = true; return; }

  const ultimo = situacao.ultimoBackup ? 'Último backup: ' + descreverDataBackup(situacao.ultimoBackup) + '.' : 'Ainda não há backup.';
  let nivel = 'ok';
  let titulo = 'Backup em dia.';
  let detalhe = situacao.ultimaCopiaExterna
    ? 'Cópia fora do notebook: ' + descreverDataBackup(situacao.ultimaCopiaExterna) + ' em ' + situacao.destinoExterno + '.'
    : '';
  if (situacao.atrasado) {
    nivel = 'erro';
    titulo = 'O backup está com problema.';
    detalhe = situacao.ultimoErro || 'O último backup já passou de 12 horas.';
  } else if (situacao.erroCopia) {
    nivel = 'atencao';
    titulo = 'A cópia do backup não foi gravada.';
    detalhe = situacao.erroCopia;
  } else if (situacao.semCopiaExterna) {
    nivel = 'atencao';
    titulo = 'Os backups estão só neste notebook.';
    detalhe = 'Se ele quebrar ou for roubado, perde tudo. Crie uma pasta chamada StockOS-Backups em um pen drive ou no OneDrive e deixe conectado: a cópia é automática.';
  }

  painel.className = 'backup-painel backup-painel--' + nivel;
  painel.innerHTML = '<div class="backup-painel__texto"><strong>' + escapeHtml(titulo) + '</strong> '
    + escapeHtml(ultimo) + (detalhe ? '<br>' + escapeHtml(detalhe) : '') + '</div>'
    + '<button class="btn btn-secondary btn-sm" type="button" id="btn-backup-agora">Fazer backup agora</button>';
  painel.hidden = false;
  document.getElementById('btn-backup-agora').addEventListener('click', fazerBackupAgora);
}

// Relógio da loja: a hora vem do sistema, no fuso da loja (não do fuso do Windows) e anda
// sozinha a cada segundo; acerta de novo com o sistema a cada 10 minutos.
let relogioDiferenca = 0;
let relogioFuso = 'America/Sao_Paulo';
let relogioNomeFuso = 'Horário de Brasília';

async function sincronizarRelogio() {
  try {
    const antes = Date.now();
    const resp = await fetch('/api/sistema/hora');
    if (!resp.ok) return;
    const hora = await resp.json();
    const depois = Date.now();
    relogioDiferenca = hora.agora - Math.round((antes + depois) / 2);
    relogioFuso = hora.fuso || relogioFuso;
    relogioNomeFuso = hora.nomeFuso || relogioNomeFuso;
  } catch {
    // Sem resposta agora: segue com a última diferença conhecida.
  }
}

function desenharRelogio() {
  const horaEl = document.getElementById('relogio-hora');
  if (!horaEl) return;
  const agora = new Date(Date.now() + relogioDiferenca);
  horaEl.textContent = agora.toLocaleTimeString('pt-BR', { timeZone: relogioFuso, hour: '2-digit', minute: '2-digit', second: '2-digit' });
  const data = agora.toLocaleDateString('pt-BR', { timeZone: relogioFuso, weekday: 'long', day: '2-digit', month: '2-digit' });
  document.getElementById('relogio-data').textContent = `${data.charAt(0).toUpperCase()}${data.slice(1)} · ${relogioNomeFuso}`;
}

function iniciarRelogioLoja() {
  if (!document.getElementById('relogio-hora')) return;
  desenharRelogio();
  if (window.__stockosRelogio) return;
  window.__stockosRelogio = setInterval(desenharRelogio, 1000);
  sincronizarRelogio().then(desenharRelogio);
  setInterval(sincronizarRelogio, 10 * 60 * 1000);
}

async function carregarSituacaoBackup() {
  if (!PODE_VER_RENTABILIDADE_DASH) return;
  const relatorio = document.getElementById('btn-relatorio-dia');
  if (relatorio) relatorio.hidden = !!window.STOCKOS_PREVIEW && !!document.querySelector('.app.stockos-shell-v2');
  try {
    const resp = await fetch('/api/sistema/backup');
    if (resp.ok) renderSituacaoBackup(await resp.json());
  } catch {
    // Sem servidor de backup (demonstração): o painel continua escondido.
  }
}

// Celular do atendente: o endereço da porta segura, com QR para abrir no celular.
async function carregarCelular() {
  if (!PODE_VER_RENTABILIDADE_DASH) return;
  const painel = document.getElementById('celular-painel');
  const atalho = document.getElementById('btn-celular-painel');
  if (!painel) return;
  if (atalho) atalho.hidden = true;
  let situacao = null;
  try {
    const resp = await fetch('/api/sistema/celular');
    if (resp.ok) situacao = await resp.json();
  } catch {
    situacao = null;
  }
  if (painel !== document.getElementById('celular-painel') || paginaAtual() !== 'dashboard') return;
  if (!situacao || !situacao.ativo || !(situacao.enderecos || []).length) { painel.hidden = true; return; }
  const principal = situacao.enderecos[0];
  const outros = situacao.enderecos.slice(1);
  painel.innerHTML = '<div class="celular-painel__qr" id="celular-qr" aria-hidden="true"></div>'
    + '<div class="celular-painel__texto"><strong>Celular do atendente</strong>'
    + '<br>No Wi-Fi da loja, aponte a câmera do celular para o código ou abra '
    + '<span class="celular-painel__url" id="celular-url">' + escapeHtml(principal.url) + '</span>'
    + (outros.length ? '<br><small>Outras redes deste notebook: ' + outros.map(e => escapeHtml(e.url) + ' (' + escapeHtml(e.rede) + ')').join(', ') + '</small>' : '')
    + '<br><small>Primeira vez no celular: toque em "Primeira vez neste celular?" na tela que abrir. Se o celular não abrir, rode uma vez "Liberar celular" na pasta do StockOS.</small></div>';
  painel.hidden = false;
  if (atalho && document.querySelector('.app.stockos-shell-v2')) {
    atalho.hidden = false;
    atalho.onclick = () => {
      painel.scrollIntoView({ block: 'center', behavior: 'instant' });
      painel.focus({ preventScroll: true });
    };
  }
  try {
    if (!window.ZXing) {
      await new Promise((resolve, reject) => {
        const script = document.createElement('script');
        script.src = '/vendor/zxing-0.21.3.min.js';
        script.onload = resolve;
        script.onerror = reject;
        document.head.appendChild(script);
      });
    }
    const svg = new ZXing.BrowserQRCodeSvgWriter().write(principal.url, 132, 132);
    document.getElementById('celular-qr').appendChild(svg);
  } catch {
    // Sem o QR, o endereço escrito continua valendo.
  }
}

async function fazerBackupAgora() {
  const botao = document.getElementById('btn-backup-agora');
  botao.disabled = true;
  botao.textContent = 'Fazendo backup...';
  try {
    const resp = await fetch('/api/sistema/backup', { method: 'POST' });
    if (resp.status === 409) showToast('Já há um backup em andamento.', 'info');
    const situacao = await resp.json();
    renderSituacaoBackup(situacao);
    if (resp.ok && !situacao.ultimoErro) showToast('Backup concluído.', 'success');
  } catch {
    showToast('Não foi possível fazer o backup.', 'error');
    botao.disabled = false;
    botao.textContent = 'Fazer backup agora';
  }
}

let dashboardLoadVersion = 0;

async function carregarDados() {
  const version = ++dashboardLoadVersion;
  carregarSituacaoBackup();
  carregarCelular();
  iniciarRelogioLoja();
  const pedir = async (url, fallback) => {
    const inicio = performance.now();
    try {
      const resp = await fetch(url);
      if (!resp.ok) throw new Error('HTTP ' + resp.status);
      return await resp.json();
    } catch (error) {
      console.error('StockOS: falha em ' + url, error);
      return fallback;
    } finally {
      console.debug('[StockOS] ' + url + ': ' + Math.round(performance.now() - inicio) + 'ms');
    }
  };
  // Consultas independentes devem rodar em paralelo, não em cascata.
  // Ranking is not required for the first screen. Fetch only if charts open.
  const [produtos, caixas, movimentacoes, metricas] = await Promise.all([
    PODE_VER_ESTOQUE_DASH ? pedir('/api/produtos', null) : Promise.resolve([]),
    PODE_VER_FINANCEIRO_DASH ? pedir('/api/caixas', null) : Promise.resolve([]),
    PODE_VER_ESTOQUE_DASH ? pedir('/api/movimentacoes', null) : Promise.resolve([]),
    PODE_VER_FINANCEIRO_DASH ? pedir('/api/caixas/metricas/hoje', null) : Promise.resolve(null)
  ]);

  // Ignore old requests and responses after navigation.
  if (version !== dashboardLoadVersion || paginaAtual() !== 'dashboard') return;
  const produtosDisponiveis = Array.isArray(produtos);
  const caixasDisponiveis = Array.isArray(caixas);
  const produtosAtuais = produtosDisponiveis ? produtos : [];
  const caixasAtuais = caixasDisponiveis ? caixas : [];
  renderSummaryCards(produtosAtuais, caixasAtuais, metricas, { produtosDisponiveis, caixasDisponiveis });
  const reference = document.getElementById('stockos-reference-sales');
  const novo = !!document.querySelector('.app.stockos-shell-v2');
  if (novo && reference) {
    renderReferenceDashboard(produtos, caixasAtuais, metricas, pedir, version);
  } else if (!reference) {
    renderDashboardOverview(caixasAtuais, metricas, caixasDisponiveis);
  }
  if (novo) {
    // Keep backup warnings prominent; move the optional device setup below the operation.
    const devicePanel = document.getElementById('celular-painel');
    const content = document.querySelector('.main-body.content');
    if (devicePanel && content) content.appendChild(devicePanel);
    if (window.STOCKOS_PREVIEW) {
      const tools = document.getElementById('stockos-demo-tools');
      if (tools) {
        document.querySelectorAll('.comandas-demo-entry,.caixa-completo-entry,.stockos-caixa-completo-entry').forEach(node => tools.appendChild(node));
        // Keep the mock-only links available even if a build changes their wrapper class.
        for (const link of content.querySelectorAll('a[href="/caixas-completo.html"],a[href="/caixa-completo.html"],a[href="/comandas-demo.html"]')) {
          if (link.closest('.stockos-demo-tools')) continue;
          const wrapper=link.closest('div');
          if (wrapper && wrapper !== content) tools.appendChild(wrapper);
        }
        tools.hidden = !tools.children.length;
      }
    }
  }
  renderAcoesOperacionais(produtosAtuais, caixasAtuais, { produtosDisponiveis, caixasDisponiveis });
  renderAlertas(produtos);
  renderMovimentacoes(movimentacoes);
  renderFinanceiro(caixasAtuais, metricas, caixasDisponiveis);
  window._dadosProdutos = produtosAtuais;
  window._dadosMov = Array.isArray(movimentacoes) ? movimentacoes : [];
  window._dadosCaixas = caixasAtuais;
  window._dadosRentabilidade = null;
  window._graficosRendered = false;
  if (document.getElementById('tabpanel-graficos')?.classList.contains('active')) {
    void abrirTabDash('graficos');
  }
}

function organizarCabecalhoDashboard() {
  if (!document.querySelector('.app.stockos-shell-v2')) return;
  const utilities = document.getElementById('stockos-dashboard-utilities');
  if (!utilities) return;
  const report = document.getElementById('btn-relatorio-dia');
  const slot = document.getElementById('stockos-report-slot');
  if (report && slot) {
    slot.appendChild(report);
    report.className = 'stockos-report-link';
    report.textContent = 'Ver relatório';
  }
  ['relogio-loja', 'btn-celular-painel'].forEach(id => {
    const node = document.getElementById(id);
    if (node && node.parentElement !== utilities) utilities.appendChild(node);
  });
  utilities.hidden = false;
}

function iniciarDashboardSPA() {
  if (paginaAtual() !== 'dashboard') return;
  organizarCabecalhoDashboard();
  renderHeader();
  renderPermissionSections();
  window._dashboardReady = carregarDados();
}

window.StockOSPages = window.StockOSPages || {};
window.StockOSPages.dashboard = {
  mount: iniciarDashboardSPA,
  deactivate() { ++dashboardLoadVersion; destroyReferenceSalesChart(); },
  activate() {
    Object.assign(window, {
      obterAlertasEstoque,
      dataLocalHojeIso,
      carregarDados,
      renderHeader,
      renderSummaryCards,
      renderAcoesOperacionais,
      renderPermissionSections,
      renderAlertas,
      renderMovimentacoes,
      renderFinanceiro,
      carregarChartJsSobDemanda,
      carregarRentabilidadeSobDemanda,
      abrirTabDash,
      parseNumeroSeguro,
      resolverCampoNumerico,
      formatarMoedaBR,
      formatarPercentual,
      resumirTexto,
      obterClassificacaoMargem,
      normalizarProdutoLucratividade,
      gerarAnaliseLucratividade,
      gerarAnaliseLucratividadeApi,
      atualizarResumoLucratividade,
      renderGraficoLucratividade,
      renderTabelaLucratividade,
      renderAnaliseLucratividade,
      renderGraficos,
      renderGraficoEstoque,
      renderGraficoMovimentacoes,
      renderGraficoCaixas,
      renderGraficoAlertas,
    });
  }
};
document.addEventListener('DOMContentLoaded', iniciarDashboardSPA);

// ── Header ────────────────────────────────────────────────
function renderHeader() {
  const status = document.getElementById('dashboard-context-status');
  if (status) status.textContent = 'Visão de hoje · ' + new Date().toLocaleDateString('pt-BR', {
    day: 'numeric', month: 'short', year: 'numeric'
  });
  const hora = new Date().getHours();
  const saudacao = hora < 12 ? 'Bom dia' : hora < 18 ? 'Boa tarde' : 'Boa noite';
  document.getElementById('header-title').textContent =
    document.querySelector('.app.stockos-shell-v2') ? 'Visão geral' : saudacao + ', ' + usuario.nome + ' 👋';
  document.getElementById('header-sub').textContent =
    new Date().toLocaleDateString('pt-BR', {
      weekday: 'long', day: 'numeric', month: 'long', year: 'numeric'
    });
}

// ── Summary cards (Improvement 5: clickable cards) ────────
function renderSummaryCards(produtos, caixas, metricasHoje, status = { produtosDisponiveis: true, caixasDisponiveis: true }) {
  var totalProdutos = produtos.length;
  var listaAlertas = obterAlertasEstoque(produtos);
  var alertas = listaAlertas.length;
  var criticos = listaAlertas.filter(p => p.nivelEstoque === 'CRITICO' || p.nivelEstoque === 'SEM_ESTOQUE').length;
  var vendasDia = metricasHoje?.totalVendas != null ? Number(metricasHoje.totalVendas) : null;
  var ticketMedio = metricasHoje?.ticketMedio != null ? Number(metricasHoje.ticketMedio) : null;
  var caixasAbertos = new Set(caixas.filter(c => c.status === 'ABERTO').map(c => c.numeroCaixa)).size;
  var totalCaixas = new Set(caixas.map(function (c) { return c.numeroCaixa; })).size || '—';

  var cards = [
    {
      key: 'products',
      label: 'Total de Produtos',
      compactLabel: 'Produtos',
      detail: status.produtosDisponiveis ? 'Cadastrados no estoque' : 'Dados indisponíveis',
      state: status.produtosDisponiveis ? 'neutral' : 'unavailable',
      value: status.produtosDisponiveis ? totalProdutos : '—',
      icon: 'box',
      tone: 'purple',
      trend: 'neutral',
      trendText: 'cadastrados',
      perm: PERMISSOES.VER_ESTOQUE,
      href: '/estoque',
    },
    {
      key: 'alerts',
      label: 'Alertas de Estoque',
      compactLabel: 'Alertas de estoque',
      detail: !status.produtosDisponiveis ? 'Dados indisponíveis' : !alertas ? 'Nenhum alerta' : criticos ? criticos + ' críticos · ' + (alertas - criticos) + ' outros' : alertas + ' precisam de atenção',
      state: !status.produtosDisponiveis ? 'unavailable' : criticos ? 'danger' : alertas ? 'warning' : 'success',
      value: status.produtosDisponiveis ? alertas : '—',
      icon: 'alertTriangle',
      tone: 'red',
      trend: alertas > 0 ? 'down' : 'up',
      trendText: alertas > 0 ? 'precisam de atenção' : 'tudo em ordem',
      perm: PERMISSOES.VER_ESTOQUE,
      href: criticos > 0 ? '/estoque.html?filtro=critico' : alertas > 0 ? '/estoque.html?filtro=baixo' : '/estoque.html',
    },
    {
      key: 'sales',
      label: 'Vendas do Dia',
      compactLabel: 'Vendas de hoje',
      detail: !Number.isFinite(vendasDia) ? 'Dados indisponíveis' : Number.isFinite(ticketMedio) ? 'Ticket médio ' + formatarMoedaBR(ticketMedio) : 'Total líquido de hoje',
      state: Number.isFinite(vendasDia) ? 'neutral' : 'unavailable',
      value: Number.isFinite(vendasDia) ? formatarMoedaBR(vendasDia) : '—',
      icon: 'wallet',
      tone: 'green',
      trend: 'up',
      trendText: Number.isFinite(vendasDia) ? 'hoje' : 'indisponível',
      permsAny: [PERMISSOES.VER_VENDAS, PERMISSOES.VER_FINANCAS],
      href: '/caixas.html',
    },
    {
      key: 'registers',
      label: 'Caixas Abertos',
      compactLabel: 'Caixas abertos',
      detail: !status.caixasDisponiveis ? 'Dados indisponíveis' : caixasAbertos === 0 ? 'Nenhum em operação' : caixasAbertos === 1 ? '1 em operação' : caixasAbertos + ' em operação',
      state: !status.caixasDisponiveis ? 'unavailable' : caixasAbertos > 0 ? 'success' : 'neutral',
      compactValue: status.caixasDisponiveis ? String(caixasAbertos) : '—',
      suffix: status.caixasDisponiveis && typeof totalCaixas === 'number' ? ' / ' + totalCaixas : '',
      value: status.caixasDisponiveis ? caixasAbertos + '/' + totalCaixas : '—',
      icon: 'chart',
      tone: 'cyan',
      trend: 'neutral',
      trendText: 'em operação',
      permsAny: [PERMISSOES.VER_VENDAS, PERMISSOES.VER_FINANCAS],
      href: '/caixas.html',
    },
  ];

  var grid = document.getElementById('summary-grid');
  const compact = !!(grid.closest('.stockos-reference-grid') && document.querySelector('.app.stockos-shell-v2'));
  grid.innerHTML = cards
    .filter(function (c) { return (!c.perm || temPermissao(c.perm)) && (!c.permsAny || temAlgumaPermissao(c.permsAny)); })
    .map(function (c, i) {
      if (compact) {
        return '<a class="summary-card summary-card--link stockos-kpi stockos-kpi--' + c.key +
          '" data-kpi="' + c.key + '" data-state="' + c.state + '" href="' + c.href + '">' +
          '<div class="stockos-kpi-heading"><span class="stockos-kpi-icon" aria-hidden="true">' + iconMarkup(c.icon) + '</span>' +
          '<span class="summary-label">' + c.compactLabel + '</span><span class="stockos-kpi-arrow" aria-hidden="true">' + iconMarkup('arrowRight') + '</span></div>' +
          '<div class="summary-value">' + (c.compactValue ?? c.value) + (c.suffix ? '<span class="stockos-kpi-suffix">' + c.suffix + '</span>' : '') + '</div>' +
          '<div class="stockos-kpi-detail">' + (c.state === 'success' || c.state === 'danger' || c.state === 'warning' ? '<span class="stockos-kpi-dot" aria-hidden="true"></span>' : '') + c.detail + '</div></a>';
      }
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

// No additional API or chart dependency is needed on the first screen.
// Keep all existing analysis charts lazy-loaded in the visual analysis tab.
function renderDashboardOverview(caixas, metricas, disponivel) {
  const grid = document.getElementById('summary-grid');
  if (!grid || !document.querySelector('.app.stockos-shell-v2')) return;
  let overview = grid.closest('.stockos-overview-grid');
  if (!overview) {
    overview = document.createElement('section');
    overview.className = 'stockos-overview-grid';
    grid.before(overview);
    overview.appendChild(grid);
  }
  const previous = overview.querySelector('.stockos-sales-panel');
  if (previous) previous.remove();
  if (!PODE_VER_FINANCEIRO_DASH) return;

  const element = (tag, cls, content) => {
    const node = document.createElement(tag);
    node.className = cls;
    if (content != null) node.textContent = content;
    return node;
  };
  const panel = element('section', 'stockos-sales-panel');
  panel.setAttribute('aria-label', 'Vendas por caixa');
  panel.append(element('h2', 'stockos-sales-title', 'Vendas por caixa'),
    element('p', 'stockos-sales-sub', 'Total líquido de vendas de hoje'));
  const total = metricas && metricas.totalVendas != null ? Number(metricas.totalVendas) : NaN;
  panel.appendChild(element('div', 'stockos-sales-total', Number.isFinite(total) ? formatarMoedaBR(total) : '—'));

  panel.appendChild(element('p', 'stockos-sales-caption', 'Barras: vendas líquidas acumuladas por caixa, conforme o período registrado de cada caixa.'));
  const rows = (disponivel && Array.isArray(caixas) ? caixas : [])
    .filter(c => c && c.totalVendas != null && Number.isFinite(Number(c.totalVendas)) &&
      (Number(c.totalVendas) !== 0 || c.status === 'ABERTO'))
    .map(c => ({ label: 'Caixa ' + c.numeroCaixa, value: Number(c.totalVendas) }));
  if (!rows.length) {
    panel.appendChild(element('p', 'stockos-sales-empty', disponivel ?
      'Nenhum caixa com dados de venda ainda.' : 'Não foi possível carregar os dados dos caixas.'));
  } else {
    const max = rows.reduce((highest, row) => Math.max(highest, Math.abs(row.value)), 1);
    const hasNegative = rows.some(row => row.value < 0);
    const chart = element('div', 'stockos-sales-chart');
    chart.setAttribute('role', 'list');
    chart.setAttribute('aria-label', 'Totais líquidos acumulados por caixa');
    if (hasNegative) chart.classList.add('stockos-sales-chart--signed');
    chart.tabIndex = 0;
    rows.forEach(row => {
      const column = element('div', 'stockos-sales-column');
      column.setAttribute('role', 'listitem');
      column.setAttribute('aria-label', row.label + ': ' + formatarMoedaBR(row.value));
      const track = element('div', 'stockos-sales-track');
      track.setAttribute('aria-hidden', 'true');
      const bar = element('div', 'stockos-sales-bar');
      const height = Math.abs(row.value) / max * (hasNegative ? 50 : 100);
      bar.style.setProperty('--bar-height', height + '%');
      if (row.value < 0) bar.classList.add('stockos-sales-bar--negative');
      track.appendChild(bar);
      column.append(element('span', 'stockos-sales-value', formatarMoedaBR(row.value)), track,
        element('span', 'stockos-sales-label', row.label));
      chart.appendChild(column);
    });
    panel.appendChild(chart);
  }
  overview.prepend(panel);
}

// Read-only report, same permission as the real report page. Never block first paint.

/* Reference layout: explicit HTML slots, truthful period data, no new backend routes. */
function referenceDate(offset) {
  const d = new Date(); d.setHours(12,0,0,0); d.setDate(d.getDate()+offset);
  return [d.getFullYear(),String(d.getMonth()+1).padStart(2,'0'),String(d.getDate()).padStart(2,'0')].join('-');
}
function referenceNode(tag, cls, value) {
  const n=document.createElement(tag); if(cls)n.className=cls;
  if(value!=null)n.textContent=String(value); return n;
}
function referenceMessage(node, message) {
  node.replaceChildren(referenceNode('p','stockos-reference-empty',message));
}
function referenceStockDistribution(products) {
  if(!Array.isArray(products))return null;
  const active=products.filter(p=>p&&p.ativo!==false);
  const rows=[
    {key:'adequate',label:'Adequado',value:0,color:'var(--ok)'},
    {key:'attention',label:'Baixo / moderado',value:0,color:'var(--warn)',href:'/estoque.html?filtro=baixo'},
    {key:'critical',label:'Crítico / sem estoque',value:0,color:'var(--bad)',href:'/estoque.html?filtro=critico'},
    {key:'unknown',label:'Sem classificação',value:0,color:'var(--ink-4)'}
  ];
  active.forEach(p=>{
    const level=p.nivelEstoque;
    rows[level==='ADEQUADO'?0:level==='BAIXO'||level==='MODERADO'?1:level==='CRITICO'||level==='SEM_ESTOQUE'?2:3].value++;
  });
  return {rows:rows.filter(r=>r.key!=='unknown'||r.value>0),total:active.length,
    adequate:active.length?rows[0].value/active.length*100:null};
}
function referencePaymentDistribution(report) {
  if(!Array.isArray(report?.porForma))return null;
  const colors={DINHEIRO:'var(--accent)',PIX:'var(--ok)',CARTAO:'var(--info)',DEBITO:'var(--info)',CREDITO:'#8876ad',CARTAO_DEBITO:'var(--info)',CARTAO_CREDITO:'#8876ad',VOUCHER:'var(--warn)'};
  const palette=['#8876ad','#a58b66','#708394','#a17e83'];
  const rows=[];
  for(const p of report.porForma){
    if(!p||p.valor==null||!Number.isFinite(Number(p.valor))||!p.rotulo)return null;
    const key=String(p.forma||p.rotulo).toUpperCase().normalize('NFD').replace(/[\u0300-\u036f]/g,'').replace(/\s+/g,'_');
    const value=Number(p.valor);
    const existing=rows.find(r=>r.key===key);
    if(existing)existing.value+=value;
    else rows.push({key,label:String(p.rotulo),value,color:colors[key]||palette[rows.length%palette.length]});
  }
  return rows.filter(r=>r.value!==0).sort((a,b)=>b.value-a.value||a.label.localeCompare(b.label,'pt-BR'));
}
function referenceDonutModel(rows) {
  if(!Array.isArray(rows)||rows.some(r=>!Number.isFinite(r.value)))return null;
  const total=rows.reduce((sum,r)=>sum+r.value,0);
  const comparable=total>0&&rows.every(r=>r.value>=0);
  const entries=rows.map(r=>({...r,share:comparable?r.value/total*100:null}));
  return {total,entries,comparable};
}
function referenceDonut(node, rows, format, options={}) {
  node.replaceChildren();
  const model=referenceDonutModel(rows);
  if(!model){referenceMessage(node,options.unavailable||'Dados indisponíveis.');return;}
  const pct=n=>new Intl.NumberFormat('pt-BR',{maximumFractionDigits:1}).format(n)+'%';
  if(!model.entries.length||model.entries.every(r=>r.value===0)){
    referenceMessage(node,options.empty||'Sem registros no período.');return;
  }
  const wrap=referenceNode('div','stockos-donut-layout');
  const legend=referenceNode('div','stockos-donut-legend');
  if(model.comparable){
    const ring=referenceNode('div','stockos-donut');
    let position=0;
    const stops=model.entries.filter(r=>r.value>0).map(r=>{
      const from=position;position+=r.share;
      return r.color+' '+from+'% '+position+'%';
    });
    ring.style.background='conic-gradient('+stops.join(',')+')';
    const inner=referenceNode('div','stockos-donut-inner');
    inner.append(referenceNode('strong','',options.centerValue??format(model.total)),referenceNode('span','',options.centerLabel||'Total líquido'));
    ring.appendChild(inner);
    ring.setAttribute('aria-hidden','true');
    wrap.appendChild(ring);
  }
  model.entries.forEach(r=>{
    const line=referenceNode(r.href?'a':'div','stockos-payment-row stockos-donut-row'+(r.href?' stockos-donut-row--link':''));
    if(r.href)line.href=r.href;
    const dot=referenceNode('span','stockos-donut-dot');dot.style.background=r.color;dot.setAttribute('aria-hidden','true');
    const label=referenceNode('span','stockos-donut-label',r.label);
    const value=referenceNode('strong','stockos-donut-value',format(r.value));
    line.append(dot,label,value,referenceNode('span','stockos-donut-percent',r.share==null?'—':pct(r.share)));
    if(r.href)line.setAttribute('aria-label',r.label+': '+format(r.value)+(r.share==null?'':', '+pct(r.share))+'. Ver produtos no estoque');
    legend.appendChild(line);
  });
  wrap.appendChild(legend);node.appendChild(wrap);
  if(!model.comparable)node.appendChild(referenceNode('p','stockos-reference-note','Percentuais indisponíveis: valores líquidos negativos ou total não positivo.'));
  else if(options.footer)node.appendChild(referenceNode('p','stockos-donut-footer',options.footer));
}

let referenceSalesChart = null;
function destroyReferenceSalesChart() {
  if (referenceSalesChart) {
    referenceSalesChart.destroy();
    referenceSalesChart = null;
  }
}
function referenceScale(rows, compare) {
  const values=rows.flatMap(r=>compare?[r.value,r.previous]:[r.value]).filter(Number.isFinite);
  const low=Math.min(0,...values), high=Math.max(0,...values);
  const raw=(high-low||1)/4;
  const magnitude=Math.pow(10,Math.floor(Math.log10(raw)));
  const step=([1,2,2.5,5,10].find(n=>n*magnitude>=raw)||10)*magnitude;
  return {min:Math.floor(low/step)*step,max:Math.max(step,Math.ceil(high/step)*step),step};
}
function referenceAxisMoney(value) {
  const n=Number(value);
  return Math.abs(n)>=1000
    ? 'R$ '+new Intl.NumberFormat('pt-BR',{notation:'compact',maximumFractionDigits:1}).format(n)
    : new Intl.NumberFormat('pt-BR',{style:'currency',currency:'BRL',maximumFractionDigits:2,minimumFractionDigits:0}).format(n);
}
function referenceComparison(rows, compare) {
  if(!compare || !rows.length || !rows.every(r=>Number.isFinite(r.value)&&Number.isFinite(r.previous)))return null;
  const current=rows.reduce((s,r)=>s+r.value,0), previous=rows.reduce((s,r)=>s+r.previous,0);
  if(previous<=0)return null;
  return {current,previous,percent:(current-previous)/previous*100};
}
function referenceFullDate(date) {
  if(!date)return '';
  const [y,m,d]=date.split('-');
  return d+'/'+m+'/'+y;
}
function referenceRowTitle(row, previous=false) {
  if(previous&&row.previousDate)return referenceFullDate(row.previousDate);
  if(row.date)return referenceFullDate(row.date)+(row.hourLabel?' · '+row.hourLabel:'');
  return row.label;
}
function referenceChartColors(node) {
  const style=getComputedStyle(node);
  const probe=document.createElement('canvas');probe.width=probe.height=1;
  const ctx=probe.getContext('2d',{willReadFrequently:true});
  const color=(token,fallback,opacity=1)=>{
    const v=style.getPropertyValue(token).trim();
    if(!v||!ctx)return fallback;
    // Normalize modern CSS colors (including OKLCH) to sRGB for Chart.js.
    ctx.clearRect(0,0,1,1);ctx.fillStyle=fallback;ctx.fillStyle=v;ctx.fillRect(0,0,1,1);
    const [r,g,b,a]=ctx.getImageData(0,0,1,1).data;
    return a?'rgba('+r+','+g+','+b+','+(a/255*opacity)+')':fallback;
  };
  return {accent:color('--accent','#c45120'),previous:color('--accent','#dfb7a4',.28),
    grid:color('--rule','#e4dfda',.75),muted:color('--ink-3','#817a74'),ink:color('--ink','#25211e'),
    paper:color('--paper-raised','#fff')};
}
function referenceChartConfig(rows,compare,palette) {
  const scale=referenceScale(rows,compare);
  const datasets=[{label:'Vendas líquidas',data:rows.map(r=>Number.isFinite(r.value)?r.value:null),
    backgroundColor:palette.accent,hoverBackgroundColor:palette.accent}];
  if(compare)datasets.push({label:'Período anterior',data:rows.map(r=>Number.isFinite(r.previous)?r.previous:null),
    backgroundColor:palette.previous,hoverBackgroundColor:palette.previous});
  return {type:'bar',data:{labels:rows.map(r=>r.label),datasets},options:{
    responsive:true,maintainAspectRatio:false,
    animation:typeof matchMedia==='function'&&matchMedia('(prefers-reduced-motion: reduce)').matches?false:{duration:250},
    interaction:{mode:'index',intersect:false,axis:'x'},
    layout:{padding:{top:4,right:4,bottom:2}},
    datasets:{bar:{borderRadius:4,borderSkipped:'start',categoryPercentage:rows.length>15?.88:.72,barPercentage:.85,maxBarThickness:34}},
    plugins:{legend:{display:false},tooltip:{
      enabled:true,mode:'index',intersect:false,displayColors:true,backgroundColor:palette.ink,titleColor:palette.paper,bodyColor:palette.paper,
      cornerRadius:8,padding:12,bodySpacing:5,titleMarginBottom:8,
      callbacks:{
        title:items=>items.length?referenceRowTitle(rows[items[0].dataIndex]):'',
        label:item=>{
          const row=rows[item.dataIndex], prior=item.datasetIndex===1;
          return item.dataset.label+(prior&&row.previousDate?' ('+referenceFullDate(row.previousDate)+')':'')+': '+formatarMoedaBR(item.parsed.y);
        }
      }
    }},
    scales:{
      x:{offset:true,grid:{display:false},border:{display:false},ticks:{color:palette.muted,font:{size:10},autoSkip:true,maxTicksLimit:rows.length>15?7:rows.length,maxRotation:0,minRotation:0,padding:6}},
      y:{min:scale.min,max:scale.max,grid:{color:palette.grid,drawTicks:false},border:{display:false,dash:[3,4]},
        ticks:{stepSize:scale.step,color:palette.muted,font:{size:10},padding:8,callback:referenceAxisMoney}}
    }
  }};
}
// Immediate offline fallback. Uses the same zero baseline and scale as Chart.js,
// with centered bars, complete labels and native hover descriptions.
function referenceFallback(rows,compare,plot) {
  const ns='http://www.w3.org/2000/svg';
  const width=Math.max(300,Math.round(plot.parentElement?.clientWidth||600)), height=184;
  const left=72,right=5,top=5,bottom=24,inner=width-left-right,h=height-top-bottom;
  const {min,max,step}=referenceScale(rows,compare);
  const y=value=>top+(max-value)/(max-min)*h;
  const svg=document.createElementNS(ns,'svg');
  svg.setAttribute('viewBox','0 0 '+width+' '+height);
  svg.setAttribute('role','img');svg.setAttribute('aria-label','Vendas por período; consulte os valores abaixo');
  const el=(tag,attrs,content,parent=svg)=>{
    const n=document.createElementNS(ns,tag);
    for(const [k,v] of Object.entries(attrs))n.setAttribute(k,String(v));
    if(content!=null)n.textContent=content;parent.appendChild(n);return n;
  };
  for(let i=0;i<=Math.round((max-min)/step);i++){
    const value=min+i*step, yy=y(value);
    el('line',{x1:left,x2:width-right,y1:yy,y2:yy,stroke:'var(--rule)','stroke-dasharray':'3 4'});
    el('text',{x:left-8,y:yy+4,'text-anchor':'end',fill:'var(--ink-3)','font-size':10},referenceAxisMoney(value));
  }
  const band=inner/rows.length,bar=Math.min(34,band*(compare?.31:.69));
  const gap=Math.min(4,band*.06);
  const labelEvery=Math.max(1,Math.ceil(rows.length/Math.max(2,Math.floor(inner/48))));
  rows.forEach((r,i)=>{
    const x=left+band*(i+.5), values=compare?[r.value,r.previous]:[r.value];
    values.forEach((v,j)=>{
      if(!Number.isFinite(v))return;
      const bx=compare?x+(j?gap/2:-bar-gap/2):x-bar/2;
      const rect=el('rect',{x:bx,y:Math.min(y(0),y(v)),width:bar,height:Math.abs(y(v)-y(0)),rx:Math.min(4,bar/3),fill:'var(--accent)',opacity:j?.28:1});
      el('title',{},referenceRowTitle(r,j===1)+': '+formatarMoedaBR(v),rect);
    });
    if(i%labelEvery===0 || i===rows.length-1 && (i%labelEvery)>labelEvery/2)
      el('text',{x,y:height-5,'text-anchor':'middle',fill:'var(--ink-3)','font-size':10},r.label);
  });
  plot.appendChild(svg);return svg;
}
function referenceSeries(node, rows, compare, isCurrent=()=>true) {
  destroyReferenceSalesChart();
  node.replaceChildren();
  if(!rows.length){referenceMessage(node,'Sem dados para o período.');return;}
  if(!rows.some(r=>Number.isFinite(r.value)||compare&&Number.isFinite(r.previous))){
    referenceMessage(node,'Histórico indisponível.');return;
  }
  const plot=referenceNode('div','stockos-reference-canvas');
  node.appendChild(plot);
  const fallback=referenceFallback(rows,compare,plot);
  const legend=referenceNode('div','stockos-reference-legend');
  legend.append(referenceNode('span','current','Vendas líquidas'));
  if(compare)legend.append(referenceNode('span','previous','Período anterior'));
  node.appendChild(legend);
  // The values remain keyboard/screen-reader accessible, even without Chart.js.
  const details=referenceNode('details','stockos-chart-details');
  details.appendChild(referenceNode('summary','','Ver valores do gráfico'));
  const wrap=referenceNode('div','stockos-chart-data-wrap'),table=referenceNode('table','stockos-reference-table');
  const head=referenceNode('thead'),hr=referenceNode('tr');
  ['Período','Vendas líquidas',...(compare?['Data anterior','Valor anterior']:[])].forEach(name=>{
    const th=referenceNode('th','',name);th.scope='col';hr.appendChild(th);
  });
  head.appendChild(hr);const body=referenceNode('tbody');
  rows.forEach(r=>{
    const tr=referenceNode('tr');
    tr.append(referenceNode('td','',referenceRowTitle(r)),referenceNode('td','',Number.isFinite(r.value)?formatarMoedaBR(r.value):'Indisponível'));
    if(compare)tr.append(referenceNode('td','',referenceRowTitle(r,true)),referenceNode('td','',Number.isFinite(r.previous)?formatarMoedaBR(r.previous):'Indisponível'));
    body.appendChild(tr);
  });
  table.append(head,body);wrap.appendChild(table);details.appendChild(wrap);node.appendChild(details);
  // First paint does not depend on CDN availability. Upgrade in place once loaded.
  void carregarChartJsSobDemanda().then(()=>{
    if(!isCurrent()||!node.isConnected||plot.parentElement!==node)return;
    const canvas=referenceNode('canvas','');
    canvas.setAttribute('role','img');
    canvas.setAttribute('aria-label','Gráfico de vendas; valores disponíveis na tabela abaixo');
    const config=referenceChartConfig(rows,compare,referenceChartColors(node));
    plot.replaceChildren(canvas);
    try {referenceSalesChart=new Chart(canvas,config);}
    catch(error){Chart.getChart?.(canvas)?.destroy();plot.replaceChildren(fallback);console.warn('StockOS: gráfico local mantido',error);}
  }).catch(error=>console.warn('StockOS: gráfico local mantido',error));
}
function renderReferenceDashboard(produtos, caixas, metricas, pedir, version) {
  const chartPanel=document.getElementById('stockos-reference-sales');
  const rankPanel=document.getElementById('stockos-daily-ranking');
  const distributions=document.getElementById('stockos-distributions');
  const paymentPanel=document.getElementById('stockos-daily-payments');
  const stockPanel=document.getElementById('stockos-stock-distribution');
  const alive=()=>version===dashboardLoadVersion && paginaAtual()==='dashboard' && chartPanel===document.getElementById('stockos-reference-sales');
  chartPanel.hidden=!PODE_VER_FINANCEIRO_DASH;
  rankPanel.hidden=!PODE_VER_RENTABILIDADE_DASH;
  const reportLink=rankPanel.querySelector('.stockos-report-link');
  if(reportLink)reportLink.hidden=!!window.STOCKOS_PREVIEW;
  paymentPanel.hidden=!PODE_VER_RENTABILIDADE_DASH;
  stockPanel.hidden=!PODE_VER_ESTOQUE_DASH;
  distributions.hidden=paymentPanel.hidden&&stockPanel.hidden;
  if(PODE_VER_ESTOQUE_DASH){
    const stock=referenceStockDistribution(produtos);
    const percent=stock?.adequate==null?'—':new Intl.NumberFormat('pt-BR',{maximumFractionDigits:1}).format(stock.adequate)+'%';
    const stockMeta=document.getElementById('stockos-stock-meta');
    if(stockMeta)stockMeta.textContent=stock?stock.total+' produtos ativos':'Indisponível';
    referenceDonut(document.getElementById('stockos-stock-content'),stock?.rows??null,n=>String(n),{
      centerValue:percent,centerLabel:'Adequado',unavailable:'Estoque indisponível.',empty:'Nenhum produto ativo.'
    });
  }
  if(!PODE_VER_FINANCEIRO_DASH)return;
  const chart=document.getElementById('stockos-period-chart');
  const total=document.getElementById('stockos-period-total');
  const label=document.getElementById('stockos-period-label');
  const trend=document.getElementById('stockos-period-trend');
  const buttons=[...chartPanel.querySelectorAll('[data-stockos-period]')];
  const reportPromise=PODE_VER_RENTABILIDADE_DASH?pedir('/api/relatorios/dia',null):Promise.resolve(null);
  const cache=new Map();
  let request=0;
  async function period(days) {
    const id=++request;
    buttons.forEach(b=>b.setAttribute('aria-pressed',String(Number(b.dataset.stockosPeriod)===days)));
    label.textContent=days===1?'Hoje · por hora':'Últimos '+days+' dias · incluindo hoje';
    destroyReferenceSalesChart();
    trend.hidden=true;trend.textContent='';trend.className='stockos-period-trend';
    total.textContent='—';referenceMessage(chart,'Carregando vendas…');
    if(!PODE_VER_RENTABILIDADE_DASH){
      label.textContent='Totais acumulados por caixa';
      const values=caixas.filter(c=>c.totalVendas!=null&&Number.isFinite(Number(c.totalVendas))).map(c=>({label:'Caixa '+c.numeroCaixa,value:Number(c.totalVendas)}));
      total.textContent=values.length?formatarMoedaBR(values.reduce((s,r)=>s+r.value,0)):'—';
      referenceSeries(chart,values,false,()=>alive()&&id===request);
      buttons.forEach(b=>{b.hidden=true;});return;
    }
    let rows=[],compare=false;
    if(days===1){
      const report=await reportPromise;
      if(Array.isArray(report?.porHora)){
        const hours=new Map(report.porHora.filter(r=>Number.isInteger(Number(r.hora))&&Number(r.hora)>=0&&Number(r.hora)<=23)
          .map(r=>[Number(r.hora),r.valor==null?null:Number(r.valor)]));
        if(hours.size){
          const first=Math.min(...hours.keys()),last=Math.max(...hours.keys());
          rows=Array.from({length:last-first+1},(_,i)=>{
            const h=first+i,hourLabel=String(h).padStart(2,'0')+'h';
            return {label:hourLabel,hourLabel,date:report.data||referenceDate(0),value:hours.has(h)?hours.get(h):0};
          });
        }
      }
    }else{
      const dates=Array.from({length:days},(_,i)=>referenceDate(i-days+1));
      const previous=Array.from({length:days},(_,i)=>referenceDate(i-2*days+1));
      const months=[...new Set([...dates,...previous].map(d=>d.slice(0,7)))];
      await Promise.all(months.map(m=>{
        if(!cache.has(m))cache.set(m,pedir('/api/relatorios/mes?mes='+encodeURIComponent(m),null));
        return cache.get(m);
      }));
      const byMonth=new Map();
      for(const m of months)byMonth.set(m,await cache.get(m));
      const value=d=>{
        const report=byMonth.get(d.slice(0,7));
        if(!Array.isArray(report?.dias))return null;
        const day=report.dias.find(v=>v.data===d);
        // Preview history has a finite known window. Missing preview dates are unknown.
        if(!day && window.STOCKOS_PREVIEW)return null;
        return day?(day.liquido==null?null:Number(day.liquido)):0;
      };
      rows=dates.map((d,i)=>({label:d.slice(8)+'/'+d.slice(5,7),date:d,previousDate:previous[i],value:value(d),previous:value(previous[i])}));
      compare=rows.every(r=>Number.isFinite(r.previous));
    }
    if(!alive()||id!==request)return;
    const complete=rows.length&&rows.every(r=>Number.isFinite(r.value));
    total.textContent=complete?formatarMoedaBR(rows.reduce((s,r)=>s+r.value,0)):'—';
    const change=referenceComparison(rows,compare);
    if(change){
      const pct=new Intl.NumberFormat('pt-BR',{maximumFractionDigits:1,signDisplay:'exceptZero'}).format(change.percent)+'%';
      const direction=change.percent>0?'positive':change.percent<0?'negative':'neutral';
      const first=rows[0].previousDate,last=rows[rows.length-1].previousDate;
      trend.classList.add(direction);
      trend.textContent=pct+' vs. '+referenceFullDate(first)+' a '+referenceFullDate(last)+' · hoje parcial';
      trend.hidden=false;
    }else if(days>1 && rows.some(r=>Number.isFinite(r.value))){
      trend.textContent=complete?'Comparativo indisponível':'Histórico parcial · total indisponível';
      trend.hidden=false;
    }
    referenceSeries(chart,rows,compare,()=>alive()&&id===request);
  }
  buttons.forEach(b=>{b.hidden=false;b.onclick=()=>void period(Number(b.dataset.stockosPeriod));});
  void period(PODE_VER_RENTABILIDADE_DASH?7:1);
  if(PODE_VER_RENTABILIDADE_DASH)void reportPromise.then(report=>{
    if(!alive())return;
    renderReferenceRanking(rankPanel,report);
    const payments=referencePaymentDistribution(report);
    const model=referenceDonutModel(payments);
    const leader=model?.comparable?model.entries[0]:null;
    const footer=leader?leader.label+' concentra '+new Intl.NumberFormat('pt-BR',{maximumFractionDigits:1}).format(leader.share)+'% dos recebimentos líquidos.':null;
    referenceDonut(document.getElementById('stockos-payment-content'),payments,formatarMoedaBR,{
      centerLabel:'Total líquido',unavailable:'Pagamentos indisponíveis.',empty:'Nenhum pagamento registrado hoje.',footer
    });
  });
}


function referenceRankingModel(report, order='quantity', limit=5) {
  if(!Array.isArray(report?.produtos))return null;
  const valid=report.produtos.filter(p=>p?.nome && p.quantidade!=null && p.valor!=null &&
    Number.isFinite(Number(p.quantidade)) && Number.isFinite(Number(p.valor)) && Number(p.quantidade)>0)
    .map(p=>({name:String(p.nome),quantity:Number(p.quantidade),revenue:Number(p.valor)}));
  const total=valid.reduce((sum,p)=>sum+p.revenue,0);
  const partial=!!report.produtosParcial || valid.length!==report.produtos.length;
  // Share refers to revenue from reported products, not necessarily all cash receipts.
  const shareAvailable=total>0 && !valid.some(p=>p.revenue<0);
  const sorted=valid.slice().sort((a,b)=>(order==='revenue'?b.revenue-a.revenue||b.quantity-a.quantity:b.quantity-a.quantity||b.revenue-a.revenue)||a.name.localeCompare(b.name,'pt-BR'));
  const rows=sorted.slice(0,limit).map((p,i)=>({...p,position:i+1,share:shareAvailable?p.revenue/total*100:null}));
  const topRevenue=rows.reduce((sum,p)=>sum+p.revenue,0);
  return {rows,total,count:valid.length,partial,topRevenue,topShare:shareAvailable?topRevenue/total*100:null};
}
function renderReferenceRanking(panel,report) {
  const rank=panel.querySelector('#stockos-ranking-content');
  const buttons=[...panel.querySelectorAll('[data-stockos-rank-order]')];
  const subtitle=panel.querySelector('#stockos-ranking-subtitle');
  const pct=v=>new Intl.NumberFormat('pt-BR',{maximumFractionDigits:1}).format(v)+'%';
  function render(order) {
    buttons.forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.stockosRankOrder===order)));
    subtitle.textContent='Hoje · por '+(order==='revenue'?'receita':'quantidade');
    const model=referenceRankingModel(report,order);
    rank.replaceChildren();
    if(!model || !model.rows.length){
      referenceMessage(rank,report?.produtosParcial?'Vendas registradas sem detalhamento de produtos.':model?'Nenhum produto vendido hoje.':'Ranking indisponível.');
      buttons.forEach(b=>b.disabled=true);return;
    }
    buttons.forEach(b=>b.disabled=false);
    const wrap=referenceNode('div','stockos-movements-wrap');
    const table=referenceNode('table','stockos-reference-table stockos-ranking-table');
    table.setAttribute('aria-label','Ranking de produtos por '+(order==='revenue'?'receita':'quantidade'));
    const head=referenceNode('thead'),hr=referenceNode('tr');
    ['#','Produto','Qtd.','Receita','Participação'].forEach(t=>{
      const th=referenceNode('th','',t);th.scope='col';
      if(t===(order==='revenue'?'Receita':'Qtd.'))th.setAttribute('aria-sort','descending');
      if(t==='Participação')th.title='Participação na receita dos produtos informados';
      hr.appendChild(th);
    });head.appendChild(hr);
    const body=referenceNode('tbody');
    model.rows.forEach(p=>{
      const tr=referenceNode('tr'),name=referenceNode('td');
      const position=referenceNode('td','stockos-rank-position',String(p.position).padStart(2,'0'));
      const item=referenceNode('div','stockos-product-cell');
      const mark=referenceNode('span','stockos-product-mark');mark.innerHTML=iconMarkup('box');
      item.append(mark,referenceNode('span','',p.name));name.appendChild(item);
      const share=referenceNode('td','stockos-rank-share');
      if(p.share!=null){
        const meter=referenceNode('div','stockos-rank-meter');
        meter.setAttribute('aria-hidden','true');
        const fill=referenceNode('span','');fill.style.width=p.share+'%';meter.appendChild(fill);
        share.append(meter,referenceNode('span','stockos-rank-percent',pct(p.share)));
      }else share.textContent='—';
      tr.append(position,name,referenceNode('td','',p.quantity.toLocaleString('pt-BR')),referenceNode('td','stockos-rank-revenue',formatarMoedaBR(p.revenue)),share);
      body.appendChild(tr);
    });
    table.append(head,body);wrap.appendChild(table);rank.appendChild(wrap);
    const footer=referenceNode('div','stockos-rank-footer');
    const context=referenceNode('div','stockos-rank-footer-context');
    context.append(referenceNode('span','','Top '+model.rows.length+' de '+model.count+' produtos'),referenceNode('strong','',formatarMoedaBR(model.topRevenue)));
    footer.appendChild(context);
    if(model.topShare!=null)footer.appendChild(referenceNode('span','stockos-rank-concentration',pct(model.topShare)+' da receita dos itens'+(model.partial?' informados':'')));
    rank.appendChild(footer);
    if(model.partial)rank.appendChild(referenceNode('p','stockos-reference-note','Ranking parcial: nem todos os registros possuem detalhamento válido. Os percentuais consideram apenas os itens informados.'));
  }
  buttons.forEach(b=>b.onclick=()=>render(b.dataset.stockosRankOrder));
  const selected=buttons.find(b=>b.getAttribute('aria-pressed')==='true');
  render(selected?.dataset.stockosRankOrder||'quantity');
}

async function carregarRelatorioDashboard(pedir, version) {
  const movements = document.getElementById('section-movimentacoes');
  const alerts = document.getElementById('section-alertas');
  if (!movements || !alerts) return;
  const current = document.querySelector('.main-body.content');
  const report = await pedir('/api/relatorios/dia', null);
  if (version !== dashboardLoadVersion || paginaAtual() !== 'dashboard' || current !== document.querySelector('.main-body.content')) return;
  const previous = current.querySelectorAll('.stockos-daily-report-panel');
  previous.forEach(panel => panel.remove());
  const el = (tag, cls, value) => {
    const node = document.createElement(tag);
    if (cls) node.className = cls;
    if (value != null) node.textContent = value;
    return node;
  };
  const makePanel = (title, subtitle) => {
    const panel = el('section', 'card stockos-daily-report-panel');
    panel.append(el('h2', 'stockos-sales-title', title), el('p', 'stockos-sales-sub', subtitle));
    return panel;
  };
  const rank = makePanel('Produtos mais vendidos', 'Hoje · ordenados por quantidade vendida');
  rank.id = 'stockos-daily-ranking';
  const products = Array.isArray(report?.produtos) ? report.produtos.filter(p => p && p.nome &&
    p.quantidade != null && Number.isFinite(Number(p.quantidade)) && Number(p.quantidade) > 0 &&
    p.valor != null && Number.isFinite(Number(p.valor))) : [];
  products.sort((a,b) => Number(b.quantidade) - Number(a.quantidade) || String(a.nome).localeCompare(String(b.nome),'pt-BR'));
  if (!products.length) {
    rank.appendChild(el('p', 'stockos-sales-empty', report && Array.isArray(report.produtos) ? 'Nenhum produto vendido hoje.' : 'Relatório de vendas indisponível.'));
  } else {
    const wrap = el('div', 'stockos-movements-wrap');
    const table = el('table','stockos-movements-table');
    table.setAttribute('aria-label','Produtos mais vendidos hoje');
    const head = el('thead'), row = el('tr');
    ['Produto','Qtd.','Vendido'].forEach(label => {const th=el('th','',label); th.scope='col'; row.appendChild(th);});
    head.appendChild(row);
    const body = el('tbody');
    products.slice(0,5).forEach(p => {
      const tr=el('tr');
      tr.append(el('td','',String(p.nome)),el('td','',Number(p.quantidade).toLocaleString('pt-BR')),el('td','',formatarMoedaBR(Number(p.valor))));
      body.appendChild(tr);
    });
    table.append(head,body);
    wrap.appendChild(table);
    rank.appendChild(wrap);
  }
  const link = el('a','stockos-report-link','Ver relatório completo →');
  link.href='/relatorios.html';
  rank.appendChild(link);
  movements.before(rank);

  const payments = makePanel('Formas de pagamento','Hoje · valores líquidos do relatório');
  payments.id='stockos-daily-payments';
  const forms = Array.isArray(report?.porForma) ? report.porForma.filter(p => p && p.rotulo &&
    p.valor != null && Number.isFinite(Number(p.valor)) && Number(p.valor) !== 0) : [];
  const max=forms.reduce((value,p)=>Math.max(value,Math.abs(Number(p.valor))),1);
  if (!forms.length) {
    payments.appendChild(el('p','stockos-sales-empty',report && Array.isArray(report.porForma)?'Nenhum pagamento registrado hoje.':'Dados de pagamento indisponíveis.'));
  } else {
    const list=el('div','stockos-payment-list');
    forms.forEach(p=>{
      const item=el('div','stockos-payment-row');
      const labels=el('div','stockos-payment-label');
      labels.append(el('span','',String(p.rotulo)),el('strong','',formatarMoedaBR(Number(p.valor))));
      const track=el('div','stockos-payment-track');
      track.setAttribute('aria-hidden','true');
      const bar=el('div',Number(p.valor)<0?'stockos-payment-bar negative':'stockos-payment-bar');
      bar.style.width=(Math.abs(Number(p.valor))/max*100)+'%';
      track.appendChild(bar);
      item.append(labels,track);
      list.appendChild(item);
    });
    payments.appendChild(list);
  }
  alerts.after(payments);
}

function renderAcoesOperacionais(produtos, caixas, status) {
  const panel = document.getElementById('daily-actions');
  const list = document.getElementById('daily-actions-list');
  const count = document.getElementById('daily-actions-count');
  if (!panel || !list) return;
  const podeEstoque = PODE_VER_ESTOQUE_DASH;
  const podeCaixas = PODE_VER_FINANCEIRO_DASH;
  if (!podeEstoque && !podeCaixas) {
    panel.hidden = true;
    return;
  }

  const compact = !!document.querySelector?.('.app.stockos-shell-v2');
  const tarefas = [];
  if (podeEstoque) {
    if (!status.produtosDisponiveis) {
      tarefas.push({
        prioridade: 'warning', tipo: 'connection', titulo: 'Estoque indisponível',
        detalhe: 'Não foi possível consultar os níveis. Confira a conexão antes de movimentar produtos.',
        acao: 'Consultar estoque', href: '/estoque.html'
      });
    } else {
      const alertas = obterAlertasEstoque(produtos);
      const criticos = alertas.filter(p => p.nivelEstoque === 'SEM_ESTOQUE' || p.nivelEstoque === 'CRITICO');
      const baixos = alertas.filter(p => p.nivelEstoque === 'BAIXO' || p.nivelEstoque === 'MODERADO');
      if (criticos.length) tarefas.push({
        prioridade: 'critical', tipo: 'critical', quantidade: criticos.length, titulo: criticos.length === 1 ? '1 produto em situação crítica' : criticos.length + ' produtos em situação crítica',
        detalhe: 'Revise primeiro os itens sem estoque ou com nível crítico.',
        acao: 'Ver críticos', href: '/estoque.html?filtro=critico'
      });
      if (baixos.length) tarefas.push({
        prioridade: 'warning', tipo: 'low', quantidade: baixos.length, titulo: baixos.length === 1 ? '1 produto precisa de atenção' : baixos.length + ' produtos precisam de atenção',
        detalhe: 'Confira a reposição dos itens com estoque baixo ou moderado.',
        acao: 'Ver baixos', href: '/estoque.html?filtro=baixo'
      });
      if (!alertas.length) tarefas.push({
        prioridade: 'normal', tipo: 'stock-ok', titulo: 'Estoque sem alertas',
        detalhe: 'Nenhum item cadastrado apresenta nível de atenção no momento.',
        acao: 'Consultar produtos', href: '/estoque.html'
      });
    }
  }

  if (podeCaixas) {
    if (!status.caixasDisponiveis) {
      tarefas.push({
        prioridade: 'warning', tipo: 'connection', titulo: 'Caixas indisponíveis',
        detalhe: 'Não foi possível consultar o estado dos caixas. Verifique a conexão.',
        acao: 'Consultar caixas', href: '/caixas.html'
      });
    } else {
      const abertos = new Set(caixas.filter(c => c.status === 'ABERTO').map(c => c.numeroCaixa)).size;
      if (!abertos) tarefas.push({
        prioridade: 'normal', tipo: 'cash-closed', quantidade: 0, titulo: 'Nenhum caixa aberto',
        detalhe: 'Confira o início do turno antes de registrar vendas.',
        acao: 'Ir para caixas', href: '/caixas.html'
      });
      else tarefas.push({
        prioridade: 'normal', tipo: 'cash-open', quantidade: abertos, titulo: abertos === 1 ? '1 caixa em operação' : abertos + ' caixas em operação',
        detalhe: 'Acesse um caixa aberto para consultar ou registrar vendas.',
        acao: 'Ver caixas', href: '/caixas.html'
      });
    }
  }

  if (compact) {
    const priorityOrder = {critical:0,warning:1,normal:2};
    const ordered = tarefas.slice().sort((a,b)=>priorityOrder[a.prioridade]-priorityOrder[b.prioridade]);
    const pending = ordered.filter(t=>t.prioridade!=='normal').length;
    const styles = {
      critical:{label:'Prioridade alta',icon:'alertTriangle',title:'Estoque crítico',unit:'produtos',detail:'Sem estoque ou em nível crítico',action:'Revisar estoque'},
      low:{label:'Atenção',icon:'box',title:'Reposição de estoque',unit:'produtos',detail:'Estoque baixo ou moderado',action:'Conferir reposição'},
      'cash-open':{label:'Em operação',icon:'wallet',title:'Caixas abertos',unit:'caixas',detail:'Disponíveis para atendimento',action:'Acessar caixas'},
      'cash-closed':{label:'Turno',icon:'wallet',title:'Caixas abertos',unit:'caixas',detail:'Nenhum caixa em operação',action:'Consultar caixas'},
      'stock-ok':{label:'Sem alertas',icon:'box',title:'Estoque',detail:'Nenhum alerta de estoque identificado',action:'Consultar produtos'},
      connection:{label:'Consulta indisponível',icon:'refresh'}
    };
    list.innerHTML=ordered.map(t=>{
      const view=styles[t.tipo];
      const title=view.title||t.titulo;
      return '<a class="operations-item stockos-priority-item operations-item--'+t.prioridade+'" data-operation="'+t.tipo+'" href="'+t.href+'">'+
        '<span class="stockos-priority-top"><span class="stockos-priority-icon" aria-hidden="true">'+iconMarkup(view.icon)+'</span>'+
        '<span class="stockos-priority-status"><span aria-hidden="true"></span>'+escapeHtml(view.label)+'</span></span>'+
        '<span class="stockos-priority-title">'+escapeHtml(title)+'</span>'+
        (t.quantidade!=null?'<span class="stockos-priority-number">'+t.quantidade+' <small>'+escapeHtml(t.tipo==='cash-open'||t.tipo==='cash-closed'?(t.quantidade===1?'caixa':'caixas'):(t.quantidade===1?'produto':'produtos'))+'</small></span>':'')+
        '<span class="stockos-priority-description">'+escapeHtml(view.detail||t.detalhe)+'</span>'+
        '<span class="stockos-priority-action">'+escapeHtml(view.action||t.acao)+iconMarkup('arrowRight')+'</span></a>';
    }).join('');
    if(count)count.textContent=pending===0?'Nenhuma prioridade pendente':pending===1?'1 prioridade pendente':pending+' prioridades pendentes';
    panel.hidden=false;
    return;
  }
  list.innerHTML = tarefas.map(tarefa => {
    return '<a class="operations-item operations-item--' + tarefa.prioridade + '" href="' + tarefa.href + '">' +
      '<span class="operations-item__indicator" aria-hidden="true"></span>' +
      '<span class="operations-item__copy"><strong>' + escapeHtml(tarefa.titulo) + '</strong>' +
      '<small>' + escapeHtml(tarefa.detalhe) + '</small></span>' +
      '<span class="operations-item__action">' + escapeHtml(tarefa.acao) + ' →</span></a>';
  }).join('');
  if (count) count.textContent = tarefas.length + (tarefas.length === 1 ? ' ação' : ' ações');
  panel.hidden = false;
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
  if (PODE_VER_RENTABILIDADE_DASH) {
    var rentabilidadeSection = document.getElementById('section-rentabilidade');
    if (rentabilidadeSection) rentabilidadeSection.classList.add('visible');
  }
}

// ── Alertas de estoque ─────────────────────────────────────

function referenceAlertModel(products, filter='all', query='', page=1, pageSize=6) {
  if(!Array.isArray(products))return null;
  const levels={SEM_ESTOQUE:{order:0,label:'Sem estoque',group:'critical'},CRITICO:{order:1,label:'Crítico',group:'critical'},BAIXO:{order:2,label:'Baixo',group:'attention'},MODERADO:{order:3,label:'Moderado',group:'attention'}};
  const number=v=>v!=null && String(v).trim()!=='' && Number.isFinite(Number(v))?Number(v):null;
  const normalize=v=>String(v??'').normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLocaleLowerCase('pt-BR').trim();
  const alerts=products.filter(p=>p&&p.ativo!==false&&Object.prototype.hasOwnProperty.call(levels,p.nivelEstoque)).map(p=>{
    const current=number(p.quantidadeAtual), minimum=number(p.quantidadeMinima);
    const valid=current!=null&&minimum!=null&&minimum>=0;
    return {name:String(p.nome||'Produto sem nome'),level:p.nivelEstoque,...levels[p.nivelEstoque],current,minimum,
      deficit:valid?Math.max(0,minimum-current):null,
      coverage:valid&&minimum>0?Math.max(0,Math.min(100,current/minimum*100)):null};
  }).sort((a,b)=>a.order-b.order||((a.coverage??Infinity)-(b.coverage??Infinity))||a.name.localeCompare(b.name,'pt-BR'));
  const counts={all:alerts.length,critical:alerts.filter(p=>p.group==='critical').length,attention:alerts.filter(p=>p.group==='attention').length};
  const term=normalize(query);
  const filtered=alerts.filter(p=>(filter==='all'||p.group===filter)&&normalize(p.name).includes(term));
  const size=Math.max(1,Math.floor(Number(pageSize)||6));
  const pages=Math.max(1,Math.ceil(filtered.length/size));
  const currentPage=Math.max(1,Math.min(pages,Math.floor(Number(page)||1)));
  return {counts,total:filtered.length,page:currentPage,pages,from:filtered.length?(currentPage-1)*size+1:0,to:Math.min(currentPage*size,filtered.length),
    rows:filtered.slice((currentPage-1)*size,currentPage*size)};
}
function renderReferenceAlerts(list,products) {
  const panel=list.closest('#section-alertas');
  const toolbar=panel.querySelector('#stockos-alert-tools');
  const filters=[...toolbar.querySelectorAll('[data-stockos-alert-filter]')];
  const search=toolbar.querySelector('input');
  const footer=panel.querySelector('#stockos-alert-footer');
  const range=footer.querySelector('#stockos-alert-range');
  const previous=footer.querySelector('[data-alert-page="previous"]');
  const next=footer.querySelector('[data-alert-page="next"]');
  const destination=toolbar.querySelector('#stockos-alert-destination');
  let filter=filters.find(b=>b.getAttribute('aria-pressed')==='true')?.dataset.stockosAlertFilter||'all';
  let page=Number(footer.dataset.page)||1;
  const quantity=v=>v==null?'—':v.toLocaleString('pt-BR',{maximumFractionDigits:3});
  function draw(){
    const model=referenceAlertModel(products,filter,search.value,page);
    list.replaceChildren();
    if(!model){
      toolbar.hidden=true;footer.hidden=true;
      referenceMessage(list,'Não foi possível consultar o estoque. Atualize a página para tentar novamente.');
      return;
    }
    toolbar.hidden=model.counts.all===0;
    footer.hidden=model.counts.all===0;
    filters.forEach(b=>{
      b.setAttribute('aria-pressed',String(b.dataset.stockosAlertFilter===filter));
      b.querySelector('strong').textContent=model.counts[b.dataset.stockosAlertFilter];
    });
    destination.href='/estoque.html'+(filter==='critical'?'?filtro=critico':filter==='attention'?'?filtro=baixo':'');
    destination.textContent=filter==='critical'?'Ver críticos no estoque':filter==='attention'?'Ver baixos no estoque':'Abrir estoque';
    page=model.page;footer.dataset.page=String(page);
    range.textContent=model.total?model.from+'–'+model.to+' de '+model.total:'0 resultados';
    previous.disabled=page<=1;
    next.disabled=page>=model.pages;
    if(!model.rows.length){
      referenceMessage(list,model.counts.all===0?'Nenhum alerta de estoque identificado.':'Nenhum produto corresponde aos filtros.');
      return;
    }
    const rows=referenceNode('ul','stockos-alert-rows');
    rows.setAttribute('aria-label','Produtos que precisam de atenção');
    model.rows.forEach(p=>{
      const item=referenceNode('li','stockos-alert-row');
      item.dataset.level=p.level;
      const identity=referenceNode('div','stockos-alert-identity');
      const status=referenceNode('span','stockos-alert-status',p.label);
      const dot=referenceNode('span','stockos-alert-dot');dot.setAttribute('aria-hidden','true');
      status.prepend(dot);
      identity.append(referenceNode('strong','stockos-alert-product',p.name),status);
      const numbers=referenceNode('div','stockos-alert-numbers');
      const balance=referenceNode('div','stockos-alert-balance');
      balance.append(referenceNode('strong','',quantity(p.current)),referenceNode('span','',' / '+quantity(p.minimum)));
      balance.setAttribute('aria-label','Quantidade atual: '+quantity(p.current)+', mínimo: '+quantity(p.minimum));
      numbers.appendChild(balance);
      if(p.coverage!=null){
        const track=referenceNode('div','stockos-alert-track');
        track.setAttribute('aria-hidden','true');
        const fill=referenceNode('span','');fill.style.width=p.coverage+'%';track.appendChild(fill);numbers.appendChild(track);
      }
      const detail=p.deficit==null?'Déficit indisponível':p.deficit>0?'Faltam '+quantity(p.deficit)+' un.':'Mínimo atingido';
      numbers.appendChild(referenceNode('small','stockos-alert-deficit',detail));
      item.append(identity,numbers);rows.appendChild(item);
    });
    list.appendChild(rows);
  }
  filters.forEach(b=>b.onclick=()=>{filter=b.dataset.stockosAlertFilter;page=1;draw();});
  search.oninput=()=>{page=1;draw();};
  previous.onclick=()=>{page--;draw();};
  next.onclick=()=>{page++;draw();};
  draw();
}

function renderAlertas(produtos) {
  var list = document.getElementById('alerta-list');
  var badge = document.getElementById('alerta-count');
  if (!list) return;
  if (document.querySelector?.('.app.stockos-shell-v2') && list.closest?.('#section-alertas')?.querySelector('#stockos-alert-tools')) {
    const model=referenceAlertModel(produtos);
    if(badge)badge.textContent=model?model.counts.all+(model.counts.all===1?' alerta':' alertas'):'—';
    renderReferenceAlerts(list,produtos);
    return;
  }
  if (!Array.isArray(produtos)) {
    if (badge) badge.textContent = '—';
    list.innerHTML = emptyStateMarkup({ icon: 'box', title: 'Alertas indisponíveis', copy: 'Não foi possível consultar o estoque. Atualize a página para tentar novamente.', compact: true });
    return;
  }
  var alertas = obterAlertasEstoque(produtos);
  if (badge) badge.textContent = alertas.length + (alertas.length === 1 ? ' alerta' : ' alertas');

  if (alertas.length === 0) {
    list.innerHTML = '<div class="empty"><span class="btn-inline-icon" aria-hidden="true">' +
      iconMarkup('checkCircle') +
      '</span>Todos os produtos estão em nível adequado.</div>';
    return;
  }

  var nivelClass = { SEM_ESTOQUE: 'critico', CRITICO: 'critico', BAIXO: 'baixo', MODERADO: 'moderado' };
  var nivelLabel = { SEM_ESTOQUE: 'Sem estoque', CRITICO: 'Crítico', BAIXO: 'Baixo', MODERADO: 'Moderado' };

  list.innerHTML = alertas.slice(0, 8).map(function (p) {
    return '<div class="alert-item ' + (nivelClass[p.nivelEstoque] || '') + '">' +
           '<div class="alert-dot"></div>' +
           '<span class="alert-name">' + escapeHtml(p.nome) + '</span>' +
           '<span class="alert-qty">' + p.quantidadeAtual + '/' + p.quantidadeMinima + ' un.</span>' +
           '<span class="alert-level">' + (nivelLabel[p.nivelEstoque] || p.nivelEstoque) + '</span>' +
           '</div>';
  }).join('');
  if (alertas.length > 8) {
    list.innerHTML += '<div class="empty">Exibindo 8 de ' + alertas.length + ' alertas. Consulte Estoque para ver os demais.</div>';
  }
}

// ── Movimentações recentes (Improvement 6) ────────────────
function renderMovimentacoes(movimentacoes) {
  var list = document.getElementById('mov-list');
  if (!list) return;
  const compact = !!document.querySelector?.('.app.stockos-shell-v2');
  const count = document.getElementById('stockos-movement-count');
  if (compact && count) count.textContent = Array.isArray(movimentacoes) ? 'Hoje' : 'Indisponível';
  if (!Array.isArray(movimentacoes)) {
    list.innerHTML = emptyStateMarkup({ icon: 'box', title: 'Movimentações indisponíveis', copy: 'Não foi possível carregar o histórico. Atualize a página para tentar novamente.', compact: true });
    return;
  }
  // Histórico preservado no banco: na visão geral entram apenas os registros de hoje.
  var hoje = dataLocalHojeIso();
  var recentesHoje = (movimentacoes || [])
    .filter(m => m && String(m.dataHora || '').slice(0, 10) === hoje)
    .sort((a, b) => String(b.dataHora).localeCompare(String(a.dataHora)));
  if (compact && count) count.textContent = recentesHoje.length
    ? recentesHoje.length + ' registro' + (recentesHoje.length===1?'':'s') + ' hoje'
    : 'Hoje';
  if (recentesHoje.length === 0) {
    list.innerHTML = emptyStateMarkup({
      icon: 'box',
      title: 'Nenhuma movimentação hoje',
      copy: 'O histórico permanece disponível na página de Estoque.',
      compact: true
    });
    return;
  }

  if (compact) {
    renderRecentMovementTable(list, recentesHoje.slice(0, 10));
    if (recentesHoje.length>10) {
      const note=document.createElement('p');note.className='stockos-reference-note';note.textContent='Exibindo 10 dos '+recentesHoje.length+' registros retornados de hoje.';list.appendChild(note);
    }
    return;
  }
  list.innerHTML = recentesHoje.slice(0, 10).map(function (m) {
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
           '<span class="mov-badge ' + (m.tipo === 'ENTRADA' ? 'entrada' : m.tipo === 'SAIDA' ? 'saida' : '') + '">' + escapeHtml(String(m.tipo || '—')) + '</span>' +
           '<span class="mov-produto">' + escapeHtml(String(m.produto || '—')) + '</span>' +
           '<span class="mov-qty">' + (m.tipo === 'ENTRADA' ? '+' : m.tipo === 'SAIDA' ? '-' : '') + escapeHtml(String(m.quantidade ?? '—')) + ' un.</span>' +
           '<span class="mov-time">' + escapeHtml(hora) + '</span>' +
           '</div>';
  }).join('');
}

// Render as a real accessible table; do not use stock exits as a sales ranking.
function renderRecentMovementTable(list, rows) {
  const wrap = document.createElement('div');
  wrap.className = 'stockos-movements-wrap';
  const table = document.createElement('table');
  table.className = 'stockos-movements-table';
  table.setAttribute('aria-label', 'Movimentações recentes de hoje');
  const thead = document.createElement('thead');
  const header = document.createElement('tr');
  ['Produto', 'Tipo', 'Quantidade', 'Hora'].forEach(label => {
    const th = document.createElement('th');
    th.scope = 'col';
    th.textContent = label;
    header.appendChild(th);
  });
  thead.appendChild(header);
  const tbody = document.createElement('tbody');
  rows.forEach(m => {
    const tr = document.createElement('tr');
    const product = document.createElement('td');
    product.textContent = String(m.produto || '—');
    const type = document.createElement('td');
    const badge = document.createElement('span');
    badge.className = 'mov-badge ' + (m.tipo === 'ENTRADA' ? 'entrada' : m.tipo === 'SAIDA' ? 'saida' : '');
    badge.textContent = String(m.tipo || '—');
    type.appendChild(badge);
    const qty = document.createElement('td');
    const amount = m.quantidade != null && Number.isFinite(Number(m.quantidade)) ? Number(m.quantidade) : null;
    qty.textContent = amount == null ? '—' : (m.tipo === 'ENTRADA' ? '+' : m.tipo === 'SAIDA' ? '−' : '') + amount + ' un.';
    const time = document.createElement('td');
    time.textContent = /T\d{2}:\d{2}/.test(String(m.dataHora || '')) ? String(m.dataHora).split('T')[1].slice(0, 5) : '—';
    tr.append(product, type, qty, time);
    tbody.appendChild(tr);
  });
  table.append(thead, tbody);
  wrap.appendChild(table);
  list.replaceChildren(wrap);
}

// ── Resumo financeiro (BUG 2: correct ticket médio) ───────
function referenceCashSessions(caixas, hoje) {
  if(!Array.isArray(caixas))return null;
  const sessions=caixas.filter(c=>c && (c.status==='ABERTO' ||
    [c.dataAbertura,c.dataEncerramento].some(d=>String(d||'').slice(0,10)===hoje)))
    .map(c=>({
      number:String(c.numeroCaixa??'—'),operator:String(c.nomeOperador||'Não informado'),
      open:c.status==='ABERTO',status:String(c.status||'—'),
      date:String(c.dataAbertura||'').slice(0,10),time:String(c.dataAbertura||'').slice(11,16),
      sales:c.totalVendas!=null&&Number.isFinite(Number(c.totalVendas))?Number(c.totalVendas):null,
      quantity:c.quantidadeVendas!=null&&Number.isFinite(Number(c.quantidadeVendas))?Number(c.quantidadeVendas):null
    })).sort((a,b)=>Number(b.open)-Number(a.open)||a.number.localeCompare(b.number,'pt-BR',{numeric:true})||
      (b.date+b.time).localeCompare(a.date+a.time));
  return {sessions,open:new Set(sessions.filter(s=>s.open).map(s=>s.number)).size};
}
function renderReferenceCashSessions(list, caixas, available) {
  const model=referenceCashSessions(available?caixas:null,dataLocalHojeIso());
  const meta=document.getElementById('stockos-cash-meta');
  if(meta)meta.textContent=model?model.open+' em operação':'Indisponível';
  if(!model||!model.sessions.length){
    referenceMessage(list,model?'Nenhuma sessão aberta ou registrada hoje.':'Não foi possível consultar os caixas.');
    return;
  }
  const wrap=referenceNode('div','stockos-movements-wrap');
  const table=referenceNode('table','stockos-reference-table stockos-cash-table');
  table.setAttribute('aria-label','Sessões abertas e sessões registradas hoje');
  const head=referenceNode('thead'),hr=referenceNode('tr');
  ['Caixa / operador','Status','Vendas','Total da sessão'].forEach(label=>{
    const th=referenceNode('th','',label);th.scope='col';hr.appendChild(th);
  });
  head.appendChild(hr);
  const body=referenceNode('tbody');
  model.sessions.slice(0,8).forEach(s=>{
    const tr=referenceNode('tr'),identity=referenceNode('td','stockos-cash-identity');
    identity.append(referenceNode('strong','','Caixa '+s.number),referenceNode('span','',s.operator));
    if(s.date)identity.appendChild(referenceNode('small','','Abertura: '+s.date.split('-').reverse().join('/')+(s.time?' · '+s.time:'')));
    const state=referenceNode('td','');
    state.appendChild(referenceNode('span','stockos-cash-state'+(s.open?' is-open':''),s.status==='ABERTO'?'Aberto':s.status==='ENCERRADO'||s.status==='FECHADO'?'Encerrado':s.status));
    tr.append(identity,state,referenceNode('td','',s.quantity==null?'—':s.quantity.toLocaleString('pt-BR')),
      referenceNode('td','stockos-cash-total',s.sales==null?'—':formatarMoedaBR(s.sales)));
    body.appendChild(tr);
  });
  table.append(head,body);wrap.appendChild(table);list.replaceChildren(wrap);
  list.appendChild(referenceNode('p','stockos-reference-note',(model.sessions.length>8?'Exibindo 8 de '+model.sessions.length+' sessões. ':'')+'Valores líquidos acumulados por sessão, inclusive quando iniciada antes de hoje.'));
}
function renderFinanceiro(caixas, metricasHoje, caixasDisponiveis = true) {
  var totalVendas = metricasHoje?.totalVendas != null ? Number(metricasHoje.totalVendas) : null;
  var caixasAbertos = metricasHoje ? metricasHoje.caixasAbertos : caixas.filter(function (c) { return c.status === 'ABERTO'; }).length;
  var ticketMedio = metricasHoje?.ticketMedio != null ? Number(metricasHoje.ticketMedio) : null;

  document.getElementById('fin-total-vendas').textContent = Number.isFinite(totalVendas) ? formatarMoedaBR(totalVendas) : '—';
  document.getElementById('fin-caixas-abertos').textContent = caixasAbertos;
  document.getElementById('fin-ticket-medio').textContent = Number.isFinite(ticketMedio) ? formatarMoedaBR(ticketMedio) : '—';

  var caixaList = document.getElementById('caixa-list');
  if (document.querySelector?.('.app.stockos-shell-v2')) {
    renderReferenceCashSessions(caixaList, caixas, caixasDisponiveis);
    return;
  }
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
           '<span class="caixa-valor">' + (c.status === 'ABERTO' ? formatarMoedaBR(c.totalVendas || 0) : '—') + '</span>' +
           '</div>';
  }).join('');
}


// ── Navegação de abas do dashboard ────────────────────────────────────────

const CHART_JS_URL = 'https://cdnjs.cloudflare.com/ajax/libs/Chart.js/4.4.1/chart.umd.min.js';
let chartJsPromise = null;
let rentabilidadePromise = null;

function carregarChartJsSobDemanda() {
  if (typeof Chart !== 'undefined') return Promise.resolve();
  if (chartJsPromise) return chartJsPromise;
  chartJsPromise = new Promise((resolve, reject) => {
    const script = document.createElement('script');
    script.src = CHART_JS_URL;
    script.async = true;
    script.onload = () => typeof Chart !== 'undefined'
      ? resolve()
      : reject(new Error('Chart.js carregou sem disponibilizar a API'));
    script.onerror = () => reject(new Error('Falha ao baixar Chart.js'));
    document.head.appendChild(script);
  }).catch(error => {
    chartJsPromise = null;
    throw error;
  });
  return chartJsPromise;
}

function carregarRentabilidadeSobDemanda() {
  if (!PODE_VER_RENTABILIDADE_DASH) return Promise.resolve(null);
  if (rentabilidadePromise) return rentabilidadePromise;
  rentabilidadePromise = fetch('/api/rentabilidade/ranking')
    .then(resp => {
      if (!resp.ok) throw new Error('HTTP ' + resp.status);
      return resp.json();
    })
    .catch(error => {
      console.warn('StockOS: ranking de rentabilidade indisponível', error);
      rentabilidadePromise = null;
      return null; // Fallback to local ranking.
    });
  return rentabilidadePromise;
}

async function abrirTabDash(aba) {
  document.querySelectorAll('#tab-visao, #tab-graficos').forEach(b => b.classList.remove('active'));
  document.querySelectorAll('#tabpanel-visao, #tabpanel-graficos').forEach(p => p.classList.remove('active'));
  document.getElementById('tab-' + aba).classList.add('active');
  document.getElementById('tabpanel-' + aba).classList.add('active');

  if (aba !== 'graficos' || window._graficosRendered || window._graficosRendering) return;
  window._graficosRendering = true;
  const badge = document.getElementById('badge-g-estoque');
  if (badge) badge.textContent = 'Carregando análise…';
  try {
    // Waiting for the dashboard API keeps the chart data complete on slow devices.
    const [, rentabilidade] = await Promise.all([
      carregarChartJsSobDemanda(),
      carregarRentabilidadeSobDemanda(),
      window._dashboardReady || Promise.resolve()
    ]);
    if (!document.getElementById('tabpanel-graficos').classList.contains('active')) return;
    window._dadosRentabilidade = rentabilidade;
    renderGraficos(
      window._dadosProdutos || [],
      window._dadosMov || [],
      window._dadosCaixas || [],
      rentabilidade
    );
    window._graficosRendered = true;
  } catch (error) {
    console.error('StockOS: falha ao carregar Análise Visual', error);
    if (badge) badge.textContent = 'Análise indisponível';
  } finally {
    window._graficosRendering = false;
  }
}

// ── Cache de dados para os gráficos ──────────────────────────────────────
// Os dados são buscados em carregarDados() e guardados globalmente para
// que renderGraficos() possa usá-los quando a aba for aberta pela 1ª vez.

window._dadosProdutos = [];
window._dadosMov      = [];
window._dadosCaixas   = [];
window._dadosRentabilidade = null;
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
        label: !custoValido ? 'Custo pendente' : 'Preço inválido',
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
    unidadesVendidas: null,
    receitaEstimada: null,
    lucroTotalEstimado: null,
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
    lucroTotalEstimado: null,
    receitaTotalEstimada: null,
    unidadesVendidasTotal: null,
    rankingEstimado: false,
  };
}

function gerarAnaliseLucratividadeApi(payload) {
  if (!payload || !Array.isArray(payload.itens)) return null;

  var itens = payload.itens.map(function (apiItem, indice) {
    var precoVenda = parseNumeroSeguro(apiItem.precoVenda);
    var precoCusto = parseNumeroSeguro(apiItem.precoCusto);
    var lucroUnitario = parseNumeroSeguro(apiItem.lucroUnitario);
    var margem = parseNumeroSeguro(apiItem.margemPercentual);
    var receitaEstimada = parseNumeroSeguro(apiItem.receitaEstimada);
    var lucroTotalEstimado = parseNumeroSeguro(apiItem.lucroTotalEstimado);
    var temMargemValida = apiItem.margemValida === true && Number.isFinite(margem);
    var classificacao = temMargemValida
      ? obterClassificacaoMargem(margem)
      : {
          chart: '#94a3b8',
          chartSoft: 'rgba(148,163,184,.18)',
          table: 'pending',
          status: 'pending',
          label: apiItem.statusLabel || 'Pendente',
        };

    if (apiItem.statusLabel) {
      classificacao.label = apiItem.statusLabel;
    }

    return {
      id: apiItem.produtoId !== undefined ? apiItem.produtoId : (indice + 1),
      nome: String(apiItem.nome || ('Produto ' + (indice + 1))).trim(),
      precoVenda: precoVenda,
      precoCusto: precoCusto,
      lucroUnitario: lucroUnitario,
      margem: margem,
      vendaValida: Number.isFinite(precoVenda) && precoVenda > 0,
      custoValido: Number.isFinite(precoCusto) && precoCusto >= 0,
      temMargemValida: temMargemValida,
      classificacao: classificacao,
      produtoOriginal: apiItem,
      posicao: apiItem.posicao || null,
      unidadesVendidas: Number(apiItem.unidadesVendidas || 0),
      receitaEstimada: Number.isFinite(receitaEstimada) ? receitaEstimada : 0,
      lucroTotalEstimado: Number.isFinite(lucroTotalEstimado) ? lucroTotalEstimado : 0,
    };
  });

  var validos = itens.filter(function (item) { return item.temMargemValida; });
  var pendentesCusto = itens.filter(function (item) { return !item.custoValido; });
  var margemMedia = parseNumeroSeguro(payload.margemMediaPercentual);
  var menorMargem = validos.length
    ? validos.slice().sort(function (a, b) { return a.margem - b.margem; })[0]
    : null;

  return {
    itens: itens,
    validos: validos,
    pendentesCusto: pendentesCusto,
    mediaMargem: Number.isFinite(margemMedia) ? margemMedia : null,
    maisLucrativo: validos.length ? validos[0] : null,
    menorMargem: menorMargem,
    lucroTotalEstimado: parseNumeroSeguro(payload.lucroTotalEstimado),
    receitaTotalEstimada: parseNumeroSeguro(payload.receitaTotalEstimada),
    unidadesVendidasTotal: Number(payload.unidadesVendidasTotal || 0),
    rankingEstimado: payload.estimado === true,
    modoCalculo: payload.modoCalculo || '',
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

  var lucroTotalValor = Number.isFinite(analise.lucroTotalEstimado)
    ? '<div class="profit-summary-value">' + formatarMoedaBR(analise.lucroTotalEstimado) + '</div>'
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
      label: 'Lucro Estimado',
      icon: 'wallet',
      tone: 'green',
      valueHtml: lucroTotalValor,
      meta: Number.isFinite(analise.receitaTotalEstimada)
        ? 'Receita estimada ' + formatarMoedaBR(analise.receitaTotalEstimada) +
          ' · ' + (analise.unidadesVendidasTotal || 0) + ' un. vendidas.'
        : 'Disponível quando o ranking enriquecido carrega.',
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
    } else if (analise.rankingEstimado) {
      badge.textContent = analise.validos.length + ' produto(s) · estimado';
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
    var lucroTotalClasse = item.temMargemValida && Number.isFinite(item.lucroTotalEstimado)
      ? (item.lucroTotalEstimado >= 0 ? 'positive' : 'negative')
      : '';
    var unidadesVendidas = Number.isFinite(item.unidadesVendidas) ? item.unidadesVendidas : null;
    var lucroTotal = Number.isFinite(item.lucroTotalEstimado) ? item.lucroTotalEstimado : null;

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
              '<td>' + (unidadesVendidas !== null ? unidadesVendidas + ' un.' : '—') + '</td>' +
              '<td class="profit-money ' + lucroTotalClasse + '">' +
                (lucroTotal !== null ? formatarMoedaBR(lucroTotal) : '—') +
              '</td>' +
              '<td><span class="profit-status ' + item.classificacao.status + '">' +
                escapeHtml(item.classificacao.label) +
              '</span></td>' +
            '</tr>';
  }).join('');
}

function renderAnaliseLucratividade(produtos, rentabilidade) {
  var analise = gerarAnaliseLucratividadeApi(rentabilidade) || gerarAnaliseLucratividade(produtos);
  atualizarResumoLucratividade(analise);
  renderGraficoLucratividade(analise);
  renderTabelaLucratividade(analise);
}

// ── Ponto de entrada ──────────────────────────────────────────────────────

function renderGraficos(produtos, movimentacoes, caixas, rentabilidade) {
  if (PODE_VER_RENTABILIDADE_DASH) {
    renderAnaliseLucratividade(produtos, rentabilidade);
  }
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
          callbacks: { label: ctx => ` ${ctx.dataset.label}: ${formatarMoedaBR(ctx.raw)}` },
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
// Expose inline HTML handlers for direct navigation as well as SPA mounts.
if (/^\/dashboard(?:\.html)?\/?$/.test(location.pathname)) {
  window.StockOSPages.dashboard.activate();
}
})();
