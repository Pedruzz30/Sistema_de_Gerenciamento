// ── caixas.js ─────────────────────────────────────────────────────────────
// Manages full cashier lifecycle: open, sale, sangria, suprimento, close.

let caixas = [];
let tipoMovAtual = null;   // 'venda' | 'sangria' | 'suprimento'
let numeroCaixaAtual = null;
let numeroCaixaEncerrar = null;

// ── Init ──────────────────────────────────────────────────────────────────

document.addEventListener('DOMContentLoaded', () => {
  carregarCaixas();
});

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
  const resp = await fetch('/api/caixas/abrir', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ numeroCaixa: numero, saldoInicial })
  });
  return resp;
}

async function registrarMovimentacao(tipo, numero, valor, descricao) {
  const resp = await fetch(`/api/caixas/${tipo}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ numeroCaixa: numero, valor, descricao: descricao || '' })
  });
  return resp;
}

async function encerrarCaixa(numero) {
  const resp = await fetch('/api/caixas/encerrar', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ numeroCaixa: numero })
  });
  return resp;
}

async function carregarConsolidado() {
  try {
    const resp = await fetch('/api/caixas/consolidado');
    const data = await resp.json();
    document.getElementById('consolidado-texto').textContent = data.resumo || 'Sem dados';
    document.getElementById('consolidado-card').style.display = 'block';
  } catch (err) {
    console.error('Erro ao carregar consolidado:', err);
  }
}

// ── Render ────────────────────────────────────────────────────────────────

function renderCaixas() {
  const grid = document.getElementById('caixas-grid');
  if (caixas.length === 0) {
    grid.innerHTML = `
      <div style="padding:48px 20px;text-align:center;grid-column:1/-1">
        <div style="font-size:40px;margin-bottom:12px">🏧</div>
        <div style="color:var(--text-2);font-weight:500;margin-bottom:6px">Nenhum caixa registrado</div>
        <div style="color:var(--text-3);font-size:13px;margin-bottom:16px">Abra um caixa para registrar vendas e movimentações.</div>
        <button class="btn-primary" onclick="abrirModalAbrirCaixa()">+ Abrir Caixa</button>
      </div>`;
    return;
  }

  grid.innerHTML = caixas.map(c => {
    const statusClass = c.status.toLowerCase();
    const statusLabel = { ABERTO: 'Aberto', FECHADO: 'Fechado', ENCERRADO: 'Encerrado' }[c.status] || c.status;
    const badgeClass = c.status === 'ABERTO' ? 'badge-green' : c.status === 'ENCERRADO' ? 'badge-gray' : 'badge-amber';
    const operador = c.nomeOperador || '—';
    const abertura = c.dataAbertura ? new Date(c.dataAbertura).toLocaleTimeString('pt-BR', {hour:'2-digit',minute:'2-digit'}) : '—';

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
            <span class="caixa-detail-value">R$${c.saldoInicial.toFixed(2)}</span>
          </div>
          <div class="caixa-detail-row">
            <span class="caixa-detail-label">Saldo Atual</span>
            <span class="caixa-detail-value" style="color:var(--green);font-weight:600">R$${c.saldoAtual.toFixed(2)}</span>
          </div>
          <div class="caixa-detail-row">
            <span class="caixa-detail-label">Total Vendas</span>
            <span class="caixa-detail-value">R$${c.totalVendas.toFixed(2)}</span>
          </div>
          <div class="caixa-detail-row">
            <span class="caixa-detail-label">Abertura</span>
            <span class="caixa-detail-value">${abertura}</span>
          </div>
        </div>
        ${c.status === 'ABERTO' ? `
          <div class="caixa-actions">
            <button class="btn-sm success" onclick="abrirModalMov('venda', ${c.numeroCaixa}, ${c.saldoAtual})">💵 Venda</button>
            <button class="btn-sm" onclick="abrirModalMov('sangria', ${c.numeroCaixa}, ${c.saldoAtual})">📤 Sangria</button>
            <button class="btn-sm" onclick="abrirModalMov('suprimento', ${c.numeroCaixa}, ${c.saldoAtual})">📥 Suprimento</button>
            <button class="btn-sm" onclick="abrirModalMovsLista(${c.numeroCaixa})">📋 Ver movs</button>
            <button class="btn-sm danger" onclick="abrirModalEncerrar(${c.numeroCaixa})">🔒 Encerrar</button>
          </div>
        ` : `
          <div class="caixa-actions">
            <button class="btn-sm" onclick="abrirModalMovsLista(${c.numeroCaixa})">📋 Ver movs</button>
          </div>
        `}
      </div>
    `;
  }).join('');

  // Show consolidado button once there's data
  carregarConsolidado();
}

function renderResumo() {
  const totalVendas = caixas.reduce((s, c) => s + (c.totalVendas || 0), 0);
  const caixasAbertos = caixas.filter(c => c.status === 'ABERTO').length;
  const saldoTotal = caixas.filter(c => c.status === 'ABERTO').reduce((s, c) => s + (c.saldoAtual || 0), 0);

  document.getElementById('total-vendas-dia').textContent = `R$${totalVendas.toFixed(2)}`;
  document.getElementById('total-caixas-abertos').textContent = caixasAbertos;
  document.getElementById('saldo-total').textContent = `R$${saldoTotal.toFixed(2)}`;
  document.getElementById('resumo-dia').style.display = 'grid';
}

// ── Modals ────────────────────────────────────────────────────────────────

function abrirModalAbrirCaixa() {
  limparAlert('alert-abrir');
  document.getElementById('abrir-numero').value = '';
  document.getElementById('abrir-saldo').value = '';
  abrirModal('modal-abrir');
}

async function confirmarAbrirCaixa() {
  const numero = parseInt(document.getElementById('abrir-numero').value);
  const saldo  = parseFloat(document.getElementById('abrir-saldo').value) || 0;

  if (!numero || numero <= 0) {
    mostrarAlert('alert-abrir', 'Informe um número de caixa válido.', 'error'); return;
  }

  const resp = await abrirCaixa(numero, saldo);
    if (!resp.ok) {
      const err = await safeReadErrorMessage(resp, 'Erro ao abrir caixa.');
      mostrarAlert('alert-abrir', err, 'error'); return;
    }
    fecharModal('modal-abrir');
    await carregarCaixas();
  }


function abrirModalMov(tipo, numero, saldoAtual) {
  tipoMovAtual = tipo;
  numeroCaixaAtual = numero;
  limparAlert('alert-mov');
  document.getElementById('mov-valor').value = '';
  document.getElementById('mov-descricao').value = '';
  const titulos = { venda: '💵 Registrar Venda', sangria: '📤 Sangria', suprimento: '📥 Suprimento' };
  document.getElementById('modal-mov-titulo').textContent = titulos[tipo] || 'Movimentação';
  document.getElementById('mov-caixa-info').textContent = `Caixa ${numero}`;
  document.getElementById('mov-saldo-atual').textContent = `R$${saldoAtual.toFixed(2)}`;
  abrirModal('modal-mov');
}

async function confirmarMovimentacao() {
  const valor = parseFloat(document.getElementById('mov-valor').value);
  const descricao = document.getElementById('mov-descricao').value.trim();

  if (!valor || valor <= 0) {
    mostrarAlert('alert-mov', 'Informe um valor maior que zero.', 'error'); return;
  }

   const resp = await registrarMovimentacao(tipoMovAtual, numeroCaixaAtual, valor, descricao);
    if (!resp.ok) {
      const err = await safeReadErrorMessage(resp, 'Erro ao registrar movimentação.');
      mostrarAlert('alert-mov', err, 'error'); return;
    }
    fecharModal('modal-mov');
    await carregarCaixas();
  }

function abrirModalEncerrar(numero) {
  numeroCaixaEncerrar = numero;
  document.getElementById('encerrar-caixa-num').textContent = `Caixa ${numero}`;
  abrirModal('modal-encerrar');
}

async function confirmarEncerrar() {
  const resp = await encerrarCaixa(numeroCaixaEncerrar);
  const data = await resp.json();
  fecharModal('modal-encerrar');

  if (!resp.ok) {
      const err = await safeReadErrorMessage(resp, 'Erro ao encerrar caixa.');
      mostrarFechamento(null, err);
    } else {
      const data = await resp.json();
      mostrarFechamento(data);
    }
    await carregarCaixas();
  }

async function abrirModalMovsLista(numero) {
  document.getElementById('modal-movs-titulo').textContent = `Movimentações — Caixa ${numero}`;
  document.getElementById('movs-lista').innerHTML = '<div class="loading">Carregando</div>';
  abrirModal('modal-movs-lista');

  try {
    const resp = await fetch(`/api/caixas/${numero}/movimentacoes`);
    if (!resp.ok) throw new Error();
    const movs = await resp.json();
    const container = document.getElementById('movs-lista');
    if (movs.length === 0) {
      container.innerHTML = '<div class="empty">Nenhuma movimentação registrada.</div>';
      return;
    }
    container.innerHTML = movs.map(m => {
      const hora = new Date(m.dataHora).toLocaleTimeString('pt-BR', {hour:'2-digit',minute:'2-digit'});
      const isEntrada = ['VENDA','SUPRIMENTO','ENTRADA'].includes(m.tipo);
      return `
        <div class="mov-caixa-item">
          <span class="mov-caixa-badge ${m.tipo.toLowerCase()}">${m.tipo}</span>
          <span class="mov-caixa-desc">${m.descricao || '—'}</span>
          <span class="mov-caixa-val" style="color:${isEntrada ? 'var(--green)' : 'var(--red)'}">
            ${isEntrada ? '+' : '-'}R$${m.valor.toFixed(2)}
          </span>
          <span class="mov-caixa-time">${hora}</span>
        </div>
      `;
    }).join('');
  } catch {
    document.getElementById('movs-lista').innerHTML =
      '<div class="empty">Erro ao carregar movimentações.</div>';
  }
}

function mostrarFechamento(data, erroMsg) {
  const container = document.getElementById('fechamento-container');
  if (erroMsg) {
    container.innerHTML = `
      <div class="alert-modal error" style="display:block; margin-bottom:16px">
        ⚠ ${erroMsg}
      </div>`;
  } else {
    container.innerHTML = `
      <div class="fechamento-card" style="margin-bottom:16px">
        <div class="fechamento-title">✓ Caixa ${data.numeroCaixa} Encerrado</div>
        <div class="fechamento-rows">
          <div class="fechamento-row">
            <span class="label">Operador</span>
            <span class="value">${data.nomeOperador || '—'}</span>
          </div>
          <div class="fechamento-row">
            <span class="label">Saldo Inicial</span>
            <span class="value">R$${data.saldoInicial.toFixed(2)}</span>
          </div>
          <div class="fechamento-row">
            <span class="label">Total Vendas</span>
            <span class="value">R$${data.totalVendas.toFixed(2)}</span>
          </div>
          <div class="fechamento-row">
            <span class="label">Total Entradas</span>
            <span class="value">R$${data.totalEntradas.toFixed(2)}</span>
          </div>
          <div class="fechamento-row">
            <span class="label">Total Saídas</span>
            <span class="value" style="color:var(--red)">R$${data.totalSaidas.toFixed(2)}</span>
          </div>
          <div class="fechamento-row">
            <span class="label">Movimentações</span>
            <span class="value">${data.quantidadeMovimentacoes}</span>
          </div>
          <div class="fechamento-row total">
            <span class="label">Saldo Final</span>
            <span class="value">R$${data.saldoFinal.toFixed(2)}</span>
          </div>
        </div>
      </div>`;
  }
  container.style.display = 'block';
  container.scrollIntoView({ behavior: 'smooth' });
}

// ── Modal helpers ─────────────────────────────────────────────────────────

function abrirModal(id) {
  document.getElementById(id).classList.add('open');
}
function fecharModal(id) {
  document.getElementById(id).classList.remove('open');
  limparAlert('alert-abrir');
  limparAlert('alert-mov');
}
function fecharModalSeClicouFora(e, id) {
  if (e.target.id === id) fecharModal(id);
}
function mostrarAlert(id, msg, tipo) {
  const el = document.getElementById(id);
  el.textContent = msg;
  el.className = `alert-modal ${tipo}`;
}
function limparAlert(id) {
  const el = document.getElementById(id);
  el.textContent = '';
  el.className = 'alert-modal';
}
