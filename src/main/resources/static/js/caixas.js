/* SPA-isolated screen: caixas. Retains direct HTML navigation. */
(() => {
'use strict';
// ── caixas.js ─────────────────────────────────────────────────────────────
// Gerencia o ciclo de vida dos caixas PDV:
// abertura, PDV de vendas, sangria, suprimento e encerramento.

// ── Estado ────────────────────────────────────────────────────────────────

let caixas               = [];
let tipoMovAtual         = null;
let numeroCaixaAtual     = null;
let numeroCaixaEncerrar  = null;

const FORMATO_MOEDA = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });

/** R$ 1.234,56 (pt-BR). */
function moeda(valor) {
  return FORMATO_MOEDA.format(Number(valor || 0));
}

const ROTULOS_FORMA = { DINHEIRO: 'Dinheiro', PIX: 'Pix', DEBITO: 'Débito', CREDITO: 'Crédito', VOUCHER: 'Voucher' };

function buttonIcon(name) {
  return '<span class="btn-inline-icon" aria-hidden="true">' + iconMarkup(name) + '</span>';
}

// ── Permissões ────────────────────────────────────────────────────────────

const PODE_OPERAR_CAIXA         = temPermissao(PERMISSOES.VER_VENDAS);
const PODE_GERIR_FINANCAS_CAIXA = temPermissao(PERMISSOES.VER_FINANCAS);

// ── Init ──────────────────────────────────────────────────────────────────

async function stockOSMount_caixas() {
  if (typeof paginaAtual === 'function' && paginaAtual() !== 'caixas') return;
  aplicarPermissoesUI();
    carregarCaixas();
}

// ── Permissões de UI ──────────────────────────────────────────────────────

function aplicarPermissoesUI() {
  const btnAbrir = document.querySelector('.header-right .btn-primary');
  if (btnAbrir && !PODE_OPERAR_CAIXA) btnAbrir.hidden = true;

  const consolidadoCard = document.getElementById('consolidado-card');
  if (consolidadoCard && !PODE_GERIR_FINANCAS_CAIXA) consolidadoCard.hidden = true;
}

function podeExecutarMovimentacao(tipo) {
  // Sangria e suprimento: o operador de caixa também pode, com a senha do
  // gerente na hora (o backend confere).
  if (['venda', 'sangria', 'suprimento'].includes(tipo)) return PODE_OPERAR_CAIXA;
  return false;
}

/** Operador sem permissão financeira precisa da autorização do gerente. */
const PRECISA_AUTORIZACAO_GERENTE = PODE_OPERAR_CAIXA && !PODE_GERIR_FINANCAS_CAIXA;

function prepararAutorizacao(prefixo) {
  const bloco = document.getElementById(prefixo + '-autorizacao');
  if (!bloco) return;
  bloco.hidden = !PRECISA_AUTORIZACAO_GERENTE;
  ['nome', 'sobrenome', 'senha'].forEach(campo => {
    const el = document.getElementById(prefixo + '-gerente-' + campo);
    if (el) el.value = '';
  });
}

function lerAutorizacao(prefixo) {
  if (!PRECISA_AUTORIZACAO_GERENTE) return null;
  const valor = campo => (document.getElementById(prefixo + '-gerente-' + campo)?.value || '').trim();
  const nome = valor('nome');
  const senha = document.getElementById(prefixo + '-gerente-senha')?.value || '';
  if (!nome || !senha) return undefined; // faltou preencher
  return { nome, sobrenome: valor('sobrenome'), senha };
}

function mensagemSemPermissao(tipo) {
  if (tipo === 'venda' || tipo === 'abrir')
    return 'Você não tem permissão para operar vendas no caixa.';
  if (['sangria','suprimento','encerrar','consolidado'].includes(tipo))
    return 'Você não tem permissão financeira para esta ação.';
  return 'Você não tem permissão para esta ação.';
}

// ── API ───────────────────────────────────────────────────────────────────

async function carregarCaixas() {
  try {
    const resp = await fetch('/api/caixas');
    if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
    caixas = await resp.json();
    renderCaixas();
    renderResumo();
  } catch (err) {
    document.getElementById('caixas-grid').innerHTML =
      '<div class="empty">Erro ao carregar caixas. Verifique o servidor.</div>';
    console.error(err);
  }
}

async function abrirCaixa(numero, saldoInicial) {
  return fetch('/api/caixas/abrir', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ numeroCaixa: numero, saldoInicial })
  });
}

async function registrarMovimentacao(tipo, numero, valor, descricao, autorizacao) {
  return fetch(`/api/caixas/${tipo}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ numeroCaixa: numero, valor, descricao: descricao || '', autorizacao: autorizacao || null })
  });
}

async function encerrarCaixa(numero, valorContado, observacao, autorizacao) {
  return fetch('/api/caixas/encerrar', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ numeroCaixa: numero, valorContado, observacao: observacao || '', autorizacao: autorizacao || null })
  });
}

async function carregarConsolidado() {
  if (!PODE_GERIR_FINANCAS_CAIXA) return;
  try {
    const resp = await fetch('/api/caixas/consolidado');
    if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
    const data = await resp.json();
    document.getElementById('consolidado-texto').textContent = data.resumo || 'Sem dados';
    document.getElementById('consolidado-card').hidden = false;
  } catch (err) {
    console.error('Erro ao carregar consolidado:', err);
  }
}

// ── Render: grid de caixas ────────────────────────────────────────────────

function renderCaixas() {
  const grid = document.getElementById('caixas-grid');

  if (caixas.length === 0) {
    grid.innerHTML = `
      <div class="empty-state grid-span-full">
        <div class="empty-state__icon" aria-hidden="true">${iconMarkup('wallet')}</div>
        <div class="empty-state__title">Nenhum caixa registrado</div>
        <div class="empty-state__copy">Abra um caixa para registrar vendas, suprimentos e fechamento diário.</div>
        ${PODE_OPERAR_CAIXA
          ? '<button class="btn-primary" onclick="abrirModalAbrirCaixa()">Abrir Caixa</button>'
          : '<div class="status-placeholder">Sem permissão para abrir caixa.</div>'}
      </div>`;
    return;
  }

  grid.innerHTML = caixas.map(c => {
    const caixaId = Number(c.caixaId ?? c.id ?? 0);
    const statusClass = c.status.toLowerCase();
    const statusLabel = { ABERTO: 'Aberto', FECHADO: 'Fechado', ENCERRADO: 'Encerrado' }[c.status] || c.status;
    const badgeClass  = c.status === 'ABERTO' ? 'badge-green' : c.status === 'ENCERRADO' ? 'badge-gray' : 'badge-amber';
    const operador    = escapeHtml(c.nomeOperador || '—');
    // Data + hora: um caixa esquecido aberto de ontem não pode parecer de hoje.
    const abertura    = c.dataAbertura
      ? new Date(c.dataAbertura).toLocaleString('pt-BR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' })
      : '—';
    const porForma    = c.vendasPorForma || {};
    const eletronico  = ['PIX', 'DEBITO', 'CREDITO', 'VOUCHER']
      .reduce((soma, forma) => soma + Number(porForma[forma] || 0), 0);
    const detalheFormas = ['DINHEIRO', 'PIX', 'DEBITO', 'CREDITO', 'VOUCHER']
      .filter(forma => Number(porForma[forma] || 0) !== 0)
      .map(forma => `${ROTULOS_FORMA[forma]} ${moeda(porForma[forma])}`)
      .join(' · ');
    const qtdVendas   = Number(c.quantidadeVendas || 0);

    const botoesAbertos = [
      // PDV substitui o modal simples de venda
      PODE_OPERAR_CAIXA
        ? `<button class="btn-sm success" onclick="abrirPDV(${c.numeroCaixa})">${buttonIcon('cart')}PDV</button>`
        : '',
      PODE_OPERAR_CAIXA
        ? `<button class="btn-sm" onclick="abrirModalMov('sangria', ${c.numeroCaixa}, ${c.saldoAtual})">${buttonIcon('minus')}Sangria</button>`
        : '',
      PODE_OPERAR_CAIXA
        ? `<button class="btn-sm" onclick="abrirModalMov('suprimento', ${c.numeroCaixa}, ${c.saldoAtual})">${buttonIcon('plus')}Suprimento</button>`
        : '',
      `<button class="btn-sm" onclick="abrirModalMovsLista(${caixaId}, ${c.numeroCaixa})">${buttonIcon('clipboard')}Ver movs</button>`,
      PODE_OPERAR_CAIXA
        ? `<button class="btn-sm danger" onclick="abrirModalEncerrar(${c.numeroCaixa})">${buttonIcon('lock')}Encerrar</button>`
        : '',
    ].filter(Boolean).join('');

    return `
      <div class="caixa-card ${statusClass}">
        <div class="caixa-card-header">
          <span class="caixa-num">Caixa ${c.numeroCaixa}</span>
          <span class="badge ${badgeClass}">${statusLabel}</span>
        </div>
        <div class="caixa-details">
          <div class="caixa-detail-row">
            <span class="caixa-detail-label">Operador</span>
            <span class="caixa-detail-value">${operador}</span>
          </div>
          <div class="caixa-detail-row">
            <span class="caixa-detail-label">Saldo Inicial</span>
            <span class="caixa-detail-value">${moeda(c.saldoInicial)}</span>
          </div>
          <div class="caixa-detail-row">
            <span class="caixa-detail-label" title="Só dinheiro em espécie: fundo + vendas em dinheiro + suprimentos − sangrias − devoluções">Dinheiro na gaveta</span>
            <span class="caixa-detail-value text-success">
              ${moeda(c.dinheiroNaGaveta ?? c.saldoAtual)}
            </span>
          </div>
          <div class="caixa-detail-row">
            <span class="caixa-detail-label">Pix e cartões</span>
            <span class="caixa-detail-value">${moeda(eletronico)}</span>
          </div>
          <div class="caixa-detail-row">
            <span class="caixa-detail-label">Total Vendas</span>
            <span class="caixa-detail-value" title="${escapeHtml(detalheFormas || 'Sem vendas')}">${moeda(c.totalVendas)}</span>
          </div>
          <div class="caixa-detail-row">
            <span class="caixa-detail-label">Vendas · ticket médio</span>
            <span class="caixa-detail-value">${qtdVendas} · ${moeda(c.ticketMedio)}</span>
          </div>
          <div class="caixa-detail-row">
            <span class="caixa-detail-label">Abertura</span>
            <span class="caixa-detail-value">${abertura}</span>
          </div>
        </div>
        <div class="caixa-actions">
          ${c.status === 'ABERTO'
            ? botoesAbertos
            : `<button class="btn-sm" onclick="abrirModalMovsLista(${caixaId}, ${c.numeroCaixa})">${buttonIcon('clipboard')}Ver movs</button>`}
        </div>
      </div>`;
  }).join('');

  if (PODE_GERIR_FINANCAS_CAIXA) carregarConsolidado();
}

// ── Render: resumo ────────────────────────────────────────────────────────

function renderResumo() {
  const totalVendas   = caixas.reduce((s, c) => s + (c.totalVendas  || 0), 0);
  const caixasAbertos = caixas.filter(c => c.status === 'ABERTO').length;
  const saldoTotal    = caixas.filter(c => c.status === 'ABERTO')
                              .reduce((s, c) => s + (c.saldoAtual || 0), 0);

  document.getElementById('total-vendas-dia').textContent     = `${moeda(totalVendas)}`;
  document.getElementById('total-caixas-abertos').textContent  = caixasAbertos;
  document.getElementById('saldo-total').textContent           = `${moeda(saldoTotal)}`;
  document.getElementById('resumo-dia').hidden                 = false;
}

// ── Modal: Abrir Caixa ────────────────────────────────────────────────────

function abrirModalAbrirCaixa() {
  if (!PODE_OPERAR_CAIXA) { showToast(mensagemSemPermissao('abrir'), 'error'); return; }
  limparAlert('alert-abrir');
  document.getElementById('abrir-numero').value = '';
  document.getElementById('abrir-saldo').value  = '';
  abrirModal('modal-abrir');
}

async function confirmarAbrirCaixa() {
  if (!PODE_OPERAR_CAIXA) {
    mostrarAlert('alert-abrir', mensagemSemPermissao('abrir'), 'error'); return;
  }
  const numero = parseInt(document.getElementById('abrir-numero').value, 10);
  const saldo  = parseFloat(document.getElementById('abrir-saldo').value) || 0;
  if (!numero || numero <= 0) {
    mostrarAlert('alert-abrir', 'Informe um número de caixa válido.', 'error'); return;
  }
  const resp = await abrirCaixa(numero, saldo);
  if (!resp.ok) {
    mostrarAlert('alert-abrir', await safeReadErrorMessage(resp, 'Erro ao abrir caixa.'), 'error');
    return;
  }
  fecharModal('modal-abrir');
  showToast(`Caixa ${numero} aberto com sucesso.`, 'success');
  await carregarCaixas();
}

// ── Modal: Movimentação manual (sangria / suprimento) ─────────────────────

function abrirModalMov(tipo, numero, saldoAtual) {
  if (!podeExecutarMovimentacao(tipo)) { showToast(mensagemSemPermissao(tipo), 'error'); return; }
  tipoMovAtual     = tipo;
  numeroCaixaAtual = numero;
  limparAlert('alert-mov');
  document.getElementById('mov-valor').value     = '';
  document.getElementById('mov-descricao').value = '';
  const titulos = { venda: 'Registrar Venda', sangria: 'Sangria', suprimento: 'Suprimento' };
  document.getElementById('modal-mov-titulo').textContent = titulos[tipo] || 'Movimentação';
  document.getElementById('mov-caixa-info').textContent   = `Caixa ${numero}`;
  prepararAutorizacao('mov');
  document.getElementById('mov-saldo-atual').textContent  = `${moeda(saldoAtual)}`;
  abrirModal('modal-mov');
}

async function confirmarMovimentacao() {
  if (!podeExecutarMovimentacao(tipoMovAtual)) {
    mostrarAlert('alert-mov', mensagemSemPermissao(tipoMovAtual), 'error'); return;
  }
  const valor     = parseFloat(document.getElementById('mov-valor').value);
  const descricao = document.getElementById('mov-descricao').value.trim();
  if (!valor || valor <= 0) {
    mostrarAlert('alert-mov', 'Informe um valor maior que zero.', 'error'); return;
  }
  const autorizacao = lerAutorizacao('mov');
  if (autorizacao === undefined) {
    mostrarAlert('alert-mov', 'Peça ao gerente para digitar nome e senha para autorizar.', 'error'); return;
  }
  const resp = await registrarMovimentacao(tipoMovAtual, numeroCaixaAtual, valor, descricao, autorizacao);
  if (!resp.ok) {
    mostrarAlert('alert-mov', await safeReadErrorMessage(resp, 'Erro ao registrar movimentação.'), 'error');
    return;
  }
  fecharModal('modal-mov');
  await carregarCaixas();
}

// ── Modal: Encerrar Caixa ─────────────────────────────────────────────────

function abrirModalEncerrar(numero) {
  if (!PODE_OPERAR_CAIXA) { showToast(mensagemSemPermissao('encerrar'), 'error'); return; }
  numeroCaixaEncerrar = numero;
  limparAlert('alert-encerrar');
  document.getElementById('encerrar-caixa-num').textContent = `Caixa ${numero}`;
  // Fechamento cego: o valor esperado NÃO aparece e o campo começa vazio.
  // A diferença só é mostrada depois de encerrar.
  document.getElementById('encerrar-valor-contado').value = '';
  document.getElementById('encerrar-observacao').value = '';
  prepararAutorizacao('encerrar');
  abrirModal('modal-encerrar');
  setTimeout(() => document.getElementById('encerrar-valor-contado')?.focus(), 50);
}

async function confirmarEncerrar() {
  if (!PODE_OPERAR_CAIXA) {
    mostrarFechamento(null, mensagemSemPermissao('encerrar')); return;
  }
  const campoContado = document.getElementById('encerrar-valor-contado').value.trim();
  if (campoContado === '') {
    mostrarAlert('alert-encerrar', 'Conte o dinheiro da gaveta e digite o valor.', 'error');
    return;
  }
  const valorContado = parseFloat(campoContado.replace(',', '.'));
  const observacao = document.getElementById('encerrar-observacao').value.trim();
  if (Number.isNaN(valorContado) || valorContado < 0) {
    mostrarAlert('alert-encerrar', 'Informe um valor contado valido.', 'error');
    return;
  }
  const autorizacao = lerAutorizacao('encerrar');
  if (autorizacao === undefined) {
    mostrarAlert('alert-encerrar', 'Peça ao gerente para digitar nome e senha para autorizar.', 'error');
    return;
  }
  const resp = await encerrarCaixa(numeroCaixaEncerrar, valorContado, observacao, autorizacao);
  if (resp.status === 403) {
    // Senha do gerente errada: mantém o modal aberto para tentar de novo.
    mostrarAlert('alert-encerrar', await safeReadErrorMessage(resp, 'Autorização recusada.'), 'error');
    return;
  }
  fecharModal('modal-encerrar');
  if (!resp.ok) {
    mostrarFechamento(null, await safeReadErrorMessage(resp, 'Erro ao encerrar caixa.'));
  } else {
    mostrarFechamento(await resp.json());
  }
  await carregarCaixas();
}

// ── Modal: Movimentações do caixa ─────────────────────────────────────────

async function abrirModalMovsLista(caixaId, numeroCaixa) {
  document.getElementById('modal-movs-titulo').textContent =
    `Movimentações — Caixa ${numeroCaixa} (Sessao ${caixaId})`;
  document.getElementById('movs-lista').innerHTML = '<div class="loading">Carregando</div>';
  abrirModal('modal-movs-lista');
  try {
    const resp = await fetch(`/api/caixas/sessoes/${caixaId}/movimentacoes`);
    if (!resp.ok) throw new Error();
    const movs      = await resp.json();
    const container = document.getElementById('movs-lista');
    if (movs.length === 0) {
      container.innerHTML = '<div class="empty">Nenhuma movimentação registrada.</div>';
      return;
    }
    container.innerHTML = movs.map(m => {
      const hora      = new Date(m.dataHora).toLocaleTimeString('pt-BR', { hour:'2-digit', minute:'2-digit' });
      const isEntrada = ['VENDA','SUPRIMENTO','ENTRADA'].includes(m.tipo);
      const forma     = m.formaPagamento ? ` · ${escapeHtml(ROTULOS_FORMA[m.formaPagamento] || m.formaPagamento)}` : '';
      const quem      = m.operador ? ` · ${escapeHtml(m.operador)}` : '';
      return `
        <div class="mov-caixa-item">
          <span class="mov-caixa-badge ${m.tipo.toLowerCase()}">${escapeHtml(m.tipo === 'ESTORNO_VENDA' ? 'ESTORNO' : m.tipo)}</span>
          <span class="mov-caixa-desc">${escapeHtml(m.descricao || '—')}<small class="mov-caixa-meta">${forma}${quem}</small></span>
          <span class="mov-caixa-val ${isEntrada ? 'text-success' : 'text-danger'}">
            ${isEntrada ? '+' : '−'}${moeda(m.valor)}
          </span>
          <span class="mov-caixa-time">${hora}</span>
        </div>`;
    }).join('');
  } catch {
    document.getElementById('movs-lista').innerHTML =
      '<div class="empty">Erro ao carregar movimentações.</div>';
  }
}

// ── Card de fechamento ────────────────────────────────────────────────────

function mostrarFechamento(data, erroMsg) {
  const container = document.getElementById('fechamento-container');
  if (erroMsg) {
    container.innerHTML = `
      <div class="alert-modal error alert-modal--static">
        <span class="btn-inline-icon" aria-hidden="true">${iconMarkup('alertTriangle')}</span>
        ${escapeHtml(erroMsg)}
      </div>`;
  } else {
    const abertoPor = escapeHtml(data.abertoPor || data.nomeOperador || '—');
    const fechadoPor = escapeHtml(data.fechadoPor || '—');
    const observacao = escapeHtml(data.observacao || '—');
    const timestamp = data.timestamp
      ? new Date(data.timestamp).toLocaleString('pt-BR')
      : escapeHtml(data.data || '—');
    const valorSistema = Number(data.valorSistema ?? data.saldoFinal ?? 0);
    const valorContado = Number(data.valorContado ?? 0);
    const divergencia = Number(data.divergencia ?? 0);
    const divergenciaClass = divergencia === 0 ? '' : (divergencia > 0 ? 'text-success' : 'text-danger');
    container.innerHTML = `
      <div class="fechamento-card">
        <div class="fechamento-title">
          <span class="btn-inline-icon" aria-hidden="true">${iconMarkup('checkCircle')}</span>
          Caixa ${data.numeroCaixa} Encerrado
        </div>
        <div class="fechamento-rows">
          <div class="fechamento-row"><span class="label">Aberto por</span>
            <span class="value">${abertoPor}</span></div>
          <div class="fechamento-row"><span class="label">Fechado por</span>
            <span class="value">${fechadoPor}</span></div>
          <div class="fechamento-row"><span class="label">Data/Hora</span>
            <span class="value">${timestamp}</span></div>
          <div class="fechamento-row"><span class="label">Saldo Inicial</span>
            <span class="value">${moeda(data.saldoInicial)}</span></div>
          <div class="fechamento-row"><span class="label">Total Vendas</span>
            <span class="value">${moeda(data.totalVendas)}</span></div>
          <div class="fechamento-row"><span class="label">Total Entradas</span>
            <span class="value">${moeda(data.totalEntradas)}</span></div>
          <div class="fechamento-row"><span class="label">Total Saídas</span>
            <span class="value text-danger">${moeda(data.totalSaidas)}</span></div>
          <div class="fechamento-row"><span class="label">Movimentações</span>
            <span class="value">${data.quantidadeMovimentacoes??'—'}</span></div>
          <div class="fechamento-row"><span class="label">Dinheiro esperado</span>
            <span class="value">${moeda(valorSistema)}</span></div>
          <div class="fechamento-row"><span class="label">Dinheiro contado</span>
            <span class="value">${moeda(valorContado)}</span></div>
          <div class="fechamento-row"><span class="label">Divergência</span>
            <span class="value ${divergenciaClass}">${moeda(divergencia)}</span></div>
          <div class="fechamento-row total"><span class="label">Observação</span>
            <span class="value">${observacao}</span></div>
        </div>
      </div>`;
  }
  container.hidden = false;
  container.scrollIntoView({ behavior: 'smooth' });
}

// ── Helpers ───────────────────────────────────────────────────────────────

function abrirModal(id)  { document.getElementById(id).classList.add('open');    }
function fecharModal(id) {
  document.getElementById(id).classList.remove('open');
  if (id === 'modal-abrir') limparAlert('alert-abrir');
  if (id === 'modal-mov')   limparAlert('alert-mov');
  if (id === 'modal-encerrar') limparAlert('alert-encerrar');
}
function fecharModalSeClicouFora(e, id) { if (e.target.id === id) fecharModal(id); }

function mostrarAlert(id, msg, tipo) {
  const el = document.getElementById(id);
  if (!el) return;
  el.textContent = msg;
  el.className   = `alert-modal ${tipo}`;
}
function limparAlert(id) {
  const el = document.getElementById(id);
  if (!el) return;
  el.textContent = '';
  el.className   = 'alert-modal';
}

/**
 * Sanitiza strings antes de inserir em innerHTML.
 * Previne XSS em dados vindos do servidor (nome de operador, descrições, etc.)
 */
function escapeHtml(str) {
  if (str == null) return '';
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

/* Page-scoped bridge: legacy inline event handlers resolve only against the active page. */
window.StockOSPages = window.StockOSPages || {};
window.StockOSPages.caixas = {
  mount: stockOSMount_caixas,
  activate() {
    Object.assign(window, {
    buttonIcon,
    moeda,
    aplicarPermissoesUI,
    podeExecutarMovimentacao,
    mensagemSemPermissao,
    carregarCaixas,
    abrirCaixa,
    registrarMovimentacao,
    encerrarCaixa,
    carregarConsolidado,
    renderCaixas,
    renderResumo,
    abrirModalAbrirCaixa,
    confirmarAbrirCaixa,
    abrirModalMov,
    confirmarMovimentacao,
    abrirModalEncerrar,
    confirmarEncerrar,
    abrirModalMovsLista,
    mostrarFechamento,
    abrirModal,
    fecharModal,
    fecharModalSeClicouFora,
    mostrarAlert,
    limparAlert,
    escapeHtml,
  });
  }
};
if (/^\/caixas(?:\.html)?\/?$/.test(location.pathname)) {
  window.StockOSPages.caixas.activate();
}
document.addEventListener('DOMContentLoaded', stockOSMount_caixas);

})();
