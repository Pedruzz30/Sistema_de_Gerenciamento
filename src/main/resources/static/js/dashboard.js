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
  if (relatorio) relatorio.hidden = false;
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
  renderFinanceiro(caixasAtuais, metricas);
  window._dadosProdutos = produtosAtuais;
  window._dadosMov = Array.isArray(movimentacoes) ? movimentacoes : [];
  window._dadosCaixas = caixasAtuais;
  window._dadosRentabilidade = null;
  window._graficosRendered = false;
  if (document.getElementById('tabpanel-graficos')?.classList.contains('active')) {
    void abrirTabDash('graficos');
  }
}

function iniciarDashboardSPA() {
  if (paginaAtual() !== 'dashboard') return;
  renderHeader();
  renderPermissionSections();
  window._dashboardReady = carregarDados();
}

window.StockOSPages = window.StockOSPages || {};
window.StockOSPages.dashboard = {
  mount: iniciarDashboardSPA,
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
  var alertas = obterAlertasEstoque(produtos).length;
  var vendasDia = metricasHoje ? Number(metricasHoje.totalVendas) : null;
  var caixasAbertos = caixas.filter(function (c) { return c.status === 'ABERTO'; }).length;
  var totalCaixas = new Set(caixas.map(function (c) { return c.numeroCaixa; })).size || '—';

  var cards = [
    {
      label: 'Total de Produtos',
      value: status.produtosDisponiveis ? totalProdutos : '—',
      icon: 'box',
      tone: 'purple',
      trend: 'neutral',
      trendText: 'cadastrados',
      perm: PERMISSOES.VER_ESTOQUE,
      href: '/estoque',
    },
    {
      label: 'Alertas de Estoque',
      value: status.produtosDisponiveis ? alertas : '—',
      icon: 'alertTriangle',
      tone: 'red',
      trend: alertas > 0 ? 'down' : 'up',
      trendText: alertas > 0 ? 'precisam de atenção' : 'tudo em ordem',
      perm: PERMISSOES.VER_ESTOQUE,
      href: alertas > 0 ? '/estoque.html?filtro=critico' : '/estoque.html',
    },
    {
      label: 'Vendas do Dia',
      value: Number.isFinite(vendasDia) ? formatarMoedaBR(vendasDia) : '—',
      icon: 'wallet',
      tone: 'green',
      trend: 'up',
      trendText: Number.isFinite(vendasDia) ? 'hoje' : 'indisponível',
      permsAny: [PERMISSOES.VER_VENDAS, PERMISSOES.VER_FINANCAS],
      href: '/caixas.html',
    },
    {
      label: 'Caixas Abertos',
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
  grid.innerHTML = cards
    .filter(function (c) { return (!c.perm || temPermissao(c.perm)) && (!c.permsAny || temAlgumaPermissao(c.permsAny)); })
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
function referenceDonut(node, rows, center, unavailable) {
  node.replaceChildren();
  if (unavailable) {referenceMessage(node,unavailable);return;}
  const valid=rows.filter(r=>Number.isFinite(r.value)&&r.value!==0);
  if(!valid.length){referenceMessage(node,'Sem registros no período.');return;}
  const total=valid.reduce((sum,r)=>sum+r.value,0);
  const colors=['var(--accent)','#42866c','#c69b55','#8793a5','#9e766e'];
  const wrap=referenceNode('div','stockos-donut-layout');
  const legend=referenceNode('div','stockos-donut-legend');
  const signed=valid.some(r=>r.value<0);
  const ring=referenceNode('div','stockos-donut');
  if(!signed && total>0){
    let position=0;
    const stops=valid.map((r,i)=>{const from=position;position+=r.value/total*100;return colors[i%colors.length]+' '+from+'% '+position+'%';});
    ring.style.background='conic-gradient('+stops.join(',')+')';
    const inner=referenceNode('div','stockos-donut-inner');
    inner.append(referenceNode('span','','Total'),referenceNode('strong','',center(total)));
    ring.appendChild(inner);ring.setAttribute('aria-hidden','true');wrap.appendChild(ring);
  }
  valid.forEach((r,i)=>{
    const line=referenceNode('div','stockos-payment-row stockos-donut-row');
    const dot=referenceNode('span','stockos-donut-dot');dot.style.background=colors[i%colors.length];
    line.append(dot,referenceNode('span','',r.label),referenceNode('strong','',center(r.value)));
    legend.appendChild(line);
  });
  wrap.appendChild(legend);node.appendChild(wrap);
}
function referenceSeries(node, rows, compare) {
  node.replaceChildren();
  if(!rows.length){referenceMessage(node,'Sem dados para o período.');return;}
  const values=rows.flatMap(r=>compare?[r.value,r.previous]:[r.value]).filter(Number.isFinite);
  if(!values.length){referenceMessage(node,'Histórico indisponível.');return;}
  const min=Math.min(0,...values), max=Math.max(1,...values), span=max-min;
  const zero=(0-min)/span*100;
  const grid=referenceNode('div','stockos-reference-chart-grid');
  const axis=referenceNode('div','stockos-reference-axis');
  for(let i=0;i<=4;i++)axis.appendChild(referenceNode('span','',new Intl.NumberFormat('pt-BR',{notation:'compact',maximumFractionDigits:1}).format(max-span*i/4)));
  const plot=referenceNode('div','stockos-reference-plot');plot.style.setProperty('--series-count',String(rows.length));
  rows.forEach((r,i)=>{
    const col=referenceNode('div','stockos-reference-column');
    const bars=referenceNode('div','stockos-reference-bars');
    const label=r.label+': '+(Number.isFinite(r.value)?formatarMoedaBR(r.value):'indisponível')+(compare?', anterior: '+(Number.isFinite(r.previous)?formatarMoedaBR(r.previous):'indisponível'):'');
    col.setAttribute('aria-label',label);col.title=label;
    [r.value,...(compare?[r.previous]:[])].forEach((v,j)=>{
      if(!Number.isFinite(v))return;
      const bar=referenceNode('span','stockos-reference-bar'+(j?' previous':''));
      bar.style.height=Math.abs(v)/span*100+'%';
      bar.style.bottom=(v>=0?zero:zero-Math.abs(v)/span*100)+'%';
      bars.appendChild(bar);
    });
    col.append(bars,referenceNode('span','stockos-reference-x',rows.length>15 && i%5!==0 && i!==rows.length-1?'':r.label));
    plot.appendChild(col);
  });
  grid.append(axis,plot);node.appendChild(grid);
  const legend=referenceNode('div','stockos-reference-legend');
  legend.append(referenceNode('span','current','Vendas líquidas'));
  if(compare)legend.append(referenceNode('span','previous','Período anterior'));
  node.appendChild(legend);
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
  paymentPanel.hidden=!PODE_VER_RENTABILIDADE_DASH;
  stockPanel.hidden=!PODE_VER_ESTOQUE_DASH;
  distributions.hidden=paymentPanel.hidden&&stockPanel.hidden;
  if(PODE_VER_ESTOQUE_DASH){
    const active=Array.isArray(produtos)?produtos.filter(p=>p&&p.ativo!==false):[];
    const alerts=obterAlertasEstoque(active).length;
    referenceDonut(document.getElementById('stockos-stock-content'),[
      {label:'Adequado',value:active.length-alerts},{label:'Atenção',value:alerts}
    ],n=>String(n),Array.isArray(produtos)?null:'Estoque indisponível.');
  }
  if(!PODE_VER_FINANCEIRO_DASH)return;
  const chart=document.getElementById('stockos-period-chart');
  const total=document.getElementById('stockos-period-total');
  const label=document.getElementById('stockos-period-label');
  const buttons=[...chartPanel.querySelectorAll('[data-stockos-period]')];
  const reportPromise=PODE_VER_RENTABILIDADE_DASH?pedir('/api/relatorios/dia',null):Promise.resolve(null);
  const cache=new Map();
  let request=0;
  async function period(days) {
    const id=++request;
    buttons.forEach(b=>b.setAttribute('aria-pressed',String(Number(b.dataset.stockosPeriod)===days)));
    label.textContent=days===1?'Hoje · por hora':'Últimos '+days+' dias · incluindo hoje';
    total.textContent='—';referenceMessage(chart,'Carregando vendas…');
    if(!PODE_VER_RENTABILIDADE_DASH){
      label.textContent='Totais acumulados por caixa';
      total.textContent=metricas?.totalVendas!=null?formatarMoedaBR(Number(metricas.totalVendas)):'—';
      referenceSeries(chart,caixas.filter(c=>c.totalVendas!=null).map(c=>({label:'Caixa '+c.numeroCaixa,value:Number(c.totalVendas)})),false);
      buttons.forEach(b=>{b.hidden=true;});return;
    }
    let rows=[],compare=false;
    if(days===1){
      const report=await reportPromise;
      if(Array.isArray(report?.porHora))rows=report.porHora.map(r=>({label:String(r.hora).padStart(2,'0')+'h',value:Number(r.valor)}));
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
        return day?Number(day.liquido):0;
      };
      rows=dates.map((d,i)=>({label:d.slice(8)+'/'+d.slice(5,7),value:value(d),previous:value(previous[i])}));
      compare=rows.every(r=>Number.isFinite(r.previous));
    }
    if(!alive()||id!==request)return;
    const complete=rows.length&&rows.every(r=>Number.isFinite(r.value));
    total.textContent=complete?formatarMoedaBR(rows.reduce((s,r)=>s+r.value,0)):'—';
    referenceSeries(chart,rows,compare);
  }
  buttons.forEach(b=>{b.hidden=false;b.onclick=()=>void period(Number(b.dataset.stockosPeriod));});
  void period(PODE_VER_RENTABILIDADE_DASH?7:1);
  if(PODE_VER_RENTABILIDADE_DASH)void reportPromise.then(report=>{
    if(!alive())return;
    const rank=document.getElementById('stockos-ranking-content');
    rank.replaceChildren();
    const products=Array.isArray(report?.produtos)?report.produtos.filter(p=>p?.nome&&Number(p.quantidade)>0&&Number.isFinite(Number(p.valor))).sort((a,b)=>Number(b.quantidade)-Number(a.quantidade)).slice(0,5):[];
    if(!products.length)referenceMessage(rank,Array.isArray(report?.produtos)?'Nenhum produto vendido hoje.':'Ranking indisponível.');
    else{
      const wrap=referenceNode('div','stockos-movements-wrap');
      const table=referenceNode('table','stockos-reference-table');
      const head=referenceNode('thead'),hr=referenceNode('tr');
      ['Produto','Qtd.','Receita'].forEach(t=>{const th=referenceNode('th','',t);th.scope='col';hr.appendChild(th);});head.appendChild(hr);
      const body=referenceNode('tbody');
      products.forEach(p=>{
        const tr=referenceNode('tr'), name=referenceNode('td');
        const item=referenceNode('div','stockos-product-cell');
        const mark=referenceNode('span','stockos-product-mark');mark.innerHTML=iconMarkup('box');
        item.append(mark,referenceNode('span','',p.nome));name.appendChild(item);
        tr.append(name,referenceNode('td','',Number(p.quantidade).toLocaleString('pt-BR')),referenceNode('td','',formatarMoedaBR(Number(p.valor))));body.appendChild(tr);
      });
      table.append(head,body);wrap.appendChild(table);rank.appendChild(wrap);
    }
    referenceDonut(document.getElementById('stockos-payment-content'),Array.isArray(report?.porForma)?report.porForma.map(p=>({label:String(p.rotulo),value:Number(p.valor)})):[],formatarMoedaBR,Array.isArray(report?.porForma)?null:'Pagamentos indisponíveis.');
  });
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

  const tarefas = [];
  if (podeEstoque) {
    if (!status.produtosDisponiveis) {
      tarefas.push({
        prioridade: 'warning', titulo: 'Estoque indisponível',
        detalhe: 'Não foi possível consultar os níveis. Confira a conexão antes de movimentar produtos.',
        acao: 'Consultar estoque', href: '/estoque.html'
      });
    } else {
      const alertas = obterAlertasEstoque(produtos);
      const criticos = alertas.filter(p => p.nivelEstoque === 'SEM_ESTOQUE' || p.nivelEstoque === 'CRITICO');
      const baixos = alertas.filter(p => p.nivelEstoque === 'BAIXO' || p.nivelEstoque === 'MODERADO');
      if (criticos.length) tarefas.push({
        prioridade: 'critical', titulo: criticos.length === 1 ? '1 produto em situação crítica' : criticos.length + ' produtos em situação crítica',
        detalhe: 'Revise primeiro os itens sem estoque ou com nível crítico.',
        acao: 'Ver críticos', href: '/estoque.html?filtro=critico'
      });
      if (baixos.length) tarefas.push({
        prioridade: 'warning', titulo: baixos.length === 1 ? '1 produto precisa de atenção' : baixos.length + ' produtos precisam de atenção',
        detalhe: 'Confira a reposição dos itens com estoque baixo ou moderado.',
        acao: 'Ver baixos', href: '/estoque.html?filtro=baixo'
      });
      if (!alertas.length) tarefas.push({
        prioridade: 'normal', titulo: 'Estoque sem alertas',
        detalhe: 'Nenhum item cadastrado apresenta nível de atenção no momento.',
        acao: 'Consultar produtos', href: '/estoque.html'
      });
    }
  }

  if (podeCaixas) {
    if (!status.caixasDisponiveis) {
      tarefas.push({
        prioridade: 'warning', titulo: 'Caixas indisponíveis',
        detalhe: 'Não foi possível consultar o estado dos caixas. Verifique a conexão.',
        acao: 'Consultar caixas', href: '/caixas.html'
      });
    } else {
      const abertos = caixas.filter(c => c.status === 'ABERTO').length;
      if (!abertos) tarefas.push({
        prioridade: 'normal', titulo: 'Nenhum caixa aberto',
        detalhe: 'Confira o início do turno antes de registrar vendas.',
        acao: 'Ir para caixas', href: '/caixas.html'
      });
      else tarefas.push({
        prioridade: 'normal', titulo: abertos === 1 ? '1 caixa em operação' : abertos + ' caixas em operação',
        detalhe: 'Acesse um caixa aberto para consultar ou registrar vendas.',
        acao: 'Ver caixas', href: '/caixas.html'
      });
    }
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
function renderAlertas(produtos) {
  var list = document.getElementById('alerta-list');
  var badge = document.getElementById('alerta-count');
  if (!list) return;
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
  if (!Array.isArray(movimentacoes)) {
    list.innerHTML = emptyStateMarkup({ icon: 'box', title: 'Movimentações indisponíveis', copy: 'Não foi possível carregar o histórico. Atualize a página para tentar novamente.', compact: true });
    return;
  }
  // Histórico preservado no banco: na visão geral entram apenas os registros de hoje.
  var hoje = dataLocalHojeIso();
  var recentesHoje = (movimentacoes || [])
    .filter(m => m && String(m.dataHora || '').slice(0, 10) === hoje)
    .sort((a, b) => String(b.dataHora).localeCompare(String(a.dataHora)));
  if (recentesHoje.length === 0) {
    list.innerHTML = emptyStateMarkup({
      icon: 'box',
      title: 'Nenhuma movimentação hoje',
      copy: 'O histórico permanece disponível na página de Estoque.',
      compact: true
    });
    return;
  }

  if (document.querySelector('.app.stockos-shell-v2')) {
    renderRecentMovementTable(list, recentesHoje.slice(0, 10));
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
function renderFinanceiro(caixas, metricasHoje) {
  var totalVendas = metricasHoje ? Number(metricasHoje.totalVendas) : null;
  var caixasAbertos = metricasHoje ? metricasHoje.caixasAbertos : caixas.filter(function (c) { return c.status === 'ABERTO'; }).length;
  var ticketMedio = metricasHoje ? Number(metricasHoje.ticketMedio) : null;

  document.getElementById('fin-total-vendas').textContent = Number.isFinite(totalVendas) ? formatarMoedaBR(totalVendas) : '—';
  document.getElementById('fin-caixas-abertos').textContent = caixasAbertos;
  document.getElementById('fin-ticket-medio').textContent = Number.isFinite(ticketMedio) ? formatarMoedaBR(ticketMedio) : '—';

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
