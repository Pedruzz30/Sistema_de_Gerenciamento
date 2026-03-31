// Cashier page UI with frontend permission gating aligned to backend checks.

let caixas = [];
let tipoMovAtual = null;
let numeroCaixaAtual = null;
let numeroCaixaEncerrar = null;

const PODE_OPERAR_CAIXA = temPermissao(PERMISSOES.VER_VENDAS);
const PODE_GERIR_FINANCAS_CAIXA = temPermissao(PERMISSOES.VER_FINANCAS);

document.addEventListener('DOMContentLoaded', () => {
  aplicarPermissoesUI();
  carregarCaixas();
});

function aplicarPermissoesUI() {
  const btnAbrir = document.querySelector('.header-right .btn-primary');
  if (btnAbrir && !PODE_OPERAR_CAIXA) {
    btnAbrir.style.display = 'none';
  }

  const consolidadoCard = document.getElementById('consolidado-card');
  if (consolidadoCard && !PODE_GERIR_FINANCAS_CAIXA) {
    consolidadoCard.style.display = 'none';
  }
}

function podeExecutarMovimentacao(tipo) {
  if (tipo === 'venda') return PODE_OPERAR_CAIXA;
  if (tipo === 'sangria' || tipo === 'suprimento') return PODE_GERIR_FINANCAS_CAIXA;
  return false;
}

function mensagemSemPermissao(tipo) {
  if (tipo === 'venda' || tipo === 'abrir') {
    return 'Voce nao tem permissao para operar vendas no caixa.';
  }
  if (tipo === 'sangria' || tipo === 'suprimento' || tipo === 'encerrar' || tipo === 'consolidado') {
    return 'Voce nao tem permissao financeira para esta acao.';
  }
  return 'Voce nao tem permissao para esta acao.';
}

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

async function registrarMovimentacao(tipo, numero, valor, descricao) {
  return fetch(`/api/caixas/${tipo}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ numeroCaixa: numero, valor, descricao: descricao || '' })
  });
}

async function encerrarCaixa(numero) {
  return fetch('/api/caixas/encerrar', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ numeroCaixa: numero })
  });
}

async function carregarConsolidado() {
  if (!PODE_GERIR_FINANCAS_CAIXA) return;
  try {
    const resp = await fetch('/api/caixas/consolidado');
    if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
    const data = await resp.json();
    document.getElementById('consolidado-texto').textContent = data.resumo || 'Sem dados';
    document.getElementById('consolidado-card').style.display = 'block';
  } catch (err) {
    console.error('Erro ao carregar consolidado:', err);
  }
}

function renderCaixas() {
  const grid = document.getElementById('caixas-grid');
  if (caixas.length === 0) {
    grid.innerHTML = `
      <div style="padding:48px 20px;text-align:center;grid-column:1/-1">
        <div style="font-size:40px;margin-bottom:12px">[ ]</div>
        <div style="color:var(--text-2);font-weight:500;margin-bottom:6px">Nenhum caixa registrado</div>
        <div style="color:var(--text-3);font-size:13px;margin-bottom:16px">Abra um caixa para registrar vendas e movimentacoes.</div>
        ${PODE_OPERAR_CAIXA
          ? '<button class="btn-primary" onclick="abrirModalAbrirCaixa()">+ Abrir Caixa</button>'
          : '<div style="color:var(--text-3);font-size:12px">Sem permissao para abrir caixa.</div>'}
      </div>`;
    return;
  }

  grid.innerHTML = caixas.map(c => {
    const statusClass = c.status.toLowerCase();
    const statusLabel = { ABERTO: 'Aberto', FECHADO: 'Fechado', ENCERRADO: 'Encerrado' }[c.status] || c.status;
    const badgeClass = c.status === 'ABERTO' ? 'badge-green' : c.status === 'ENCERRADO' ? 'badge-gray' : 'badge-amber';
    const operador = escapeHtml(c.nomeOperador || '-');
    const abertura = c.dataAbertura
      ? new Date(c.dataAbertura).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })
      : '-';

    const botoesAbertos = [
      PODE_OPERAR_CAIXA
        ? `<button class="btn-sm success" onclick="abrirModalMov('venda', ${c.numeroCaixa}, ${c.saldoAtual})">Venda</button>`
        : '',
      PODE_GERIR_FINANCAS_CAIXA
        ? `<button class="btn-sm" onclick="abrirModalMov('sangria', ${c.numeroCaixa}, ${c.saldoAtual})">Sangria</button>`
        : '',
      PODE_GERIR_FINANCAS_CAIXA
        ? `<button class="btn-sm" onclick="abrirModalMov('suprimento', ${c.numeroCaixa}, ${c.saldoAtual})">Suprimento</button>`
        : '',
      `<button class="btn-sm" onclick="abrirModalMovsLista(${c.numeroCaixa})">Ver movs</button>`,
      PODE_GERIR_FINANCAS_CAIXA
        ? `<button class="btn-sm danger" onclick="abrirModalEncerrar(${c.numeroCaixa})">Encerrar</button>`
        : ''
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
        <div class="caixa-actions">
          ${c.status === 'ABERTO'
            ? botoesAbertos
            : `<button class="btn-sm" onclick="abrirModalMovsLista(${c.numeroCaixa})">Ver movs</button>`}
        </div>
      </div>
    `;
  }).join('');

  if (PODE_GERIR_FINANCAS_CAIXA) {
    carregarConsolidado();
  }
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

function abrirModalAbrirCaixa() {
  if (!PODE_OPERAR_CAIXA) {
    showToast(mensagemSemPermissao('abrir'), 'error');
    return;
  }
  limparAlert('alert-abrir');
  document.getElementById('abrir-numero').value = '';
  document.getElementById('abrir-saldo').value = '';
  abrirModal('modal-abrir');
}

async function confirmarAbrirCaixa() {
  if (!PODE_OPERAR_CAIXA) {
    mostrarAlert('alert-abrir', mensagemSemPermissao('abrir'), 'error');
    return;
  }

  const numero = parseInt(document.getElementById('abrir-numero').value, 10);
  const saldo = parseFloat(document.getElementById('abrir-saldo').value) || 0;

  if (!numero || numero <= 0) {
    mostrarAlert('alert-abrir', 'Informe um numero de caixa valido.', 'error');
    return;
  }

  const resp = await abrirCaixa(numero, saldo);
  if (!resp.ok) {
    const err = await safeReadErrorMessage(resp, 'Erro ao abrir caixa.');
    mostrarAlert('alert-abrir', err, 'error');
    return;
  }

  fecharModal('modal-abrir');
  await carregarCaixas();
}

function abrirModalMov(tipo, numero, saldoAtual) {
  if (!podeExecutarMovimentacao(tipo)) {
    showToast(mensagemSemPermissao(tipo), 'error');
    return;
  }

  tipoMovAtual = tipo;
  numeroCaixaAtual = numero;
  limparAlert('alert-mov');
  document.getElementById('mov-valor').value = '';
  document.getElementById('mov-descricao').value = '';

  const titulos = { venda: 'Registrar Venda', sangria: 'Sangria', suprimento: 'Suprimento' };
  document.getElementById('modal-mov-titulo').textContent = titulos[tipo] || 'Movimentacao';
  document.getElementById('mov-caixa-info').textContent = `Caixa ${numero}`;
  document.getElementById('mov-saldo-atual').textContent = `R$${saldoAtual.toFixed(2)}`;
  abrirModal('modal-mov');
}

async function confirmarMovimentacao() {
  if (!podeExecutarMovimentacao(tipoMovAtual)) {
    mostrarAlert('alert-mov', mensagemSemPermissao(tipoMovAtual), 'error');
    return;
  }

  const valor = parseFloat(document.getElementById('mov-valor').value);
  const descricao = document.getElementById('mov-descricao').value.trim();

  if (!valor || valor <= 0) {
    mostrarAlert('alert-mov', 'Informe um valor maior que zero.', 'error');
    return;
  }

  const resp = await registrarMovimentacao(tipoMovAtual, numeroCaixaAtual, valor, descricao);
  if (!resp.ok) {
    const err = await safeReadErrorMessage(resp, 'Erro ao registrar movimentacao.');
    mostrarAlert('alert-mov', err, 'error');
    return;
  }

  fecharModal('modal-mov');
  await carregarCaixas();
}

function abrirModalEncerrar(numero) {
  if (!PODE_GERIR_FINANCAS_CAIXA) {
    showToast(mensagemSemPermissao('encerrar'), 'error');
    return;
  }

  numeroCaixaEncerrar = numero;
  document.getElementById('encerrar-caixa-num').textContent = `Caixa ${numero}`;
  abrirModal('modal-encerrar');
}

async function confirmarEncerrar() {
  if (!PODE_GERIR_FINANCAS_CAIXA) {
    mostrarFechamento(null, mensagemSemPermissao('encerrar'));
    return;
  }

  const resp = await encerrarCaixa(numeroCaixaEncerrar);
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
  document.getElementById('modal-movs-titulo').textContent = `Movimentacoes - Caixa ${numero}`;
  document.getElementById('movs-lista').innerHTML = '<div class="loading">Carregando</div>';
  abrirModal('modal-movs-lista');

  try {
    const resp = await fetch(`/api/caixas/${numero}/movimentacoes`);
    if (!resp.ok) throw new Error();
    const movs = await resp.json();
    const container = document.getElementById('movs-lista');

    if (movs.length === 0) {
      container.innerHTML = '<div class="empty">Nenhuma movimentacao registrada.</div>';
      return;
    }

    container.innerHTML = movs.map(m => {
      const hora = new Date(m.dataHora).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
      const isEntrada = ['VENDA', 'SUPRIMENTO', 'ENTRADA'].includes(m.tipo);
      return `
        <div class="mov-caixa-item">
          <span class="mov-caixa-badge ${m.tipo.toLowerCase()}">${escapeHtml(m.tipo)}</span>
          <span class="mov-caixa-desc">${escapeHtml(m.descricao || '-')}</span>
          <span class="mov-caixa-val" style="color:${isEntrada ? 'var(--green)' : 'var(--red)'}">
            ${isEntrada ? '+' : '-'}R$${m.valor.toFixed(2)}
          </span>
          <span class="mov-caixa-time">${hora}</span>
        </div>
      `;
    }).join('');
  } catch {
    document.getElementById('movs-lista').innerHTML =
      '<div class="empty">Erro ao carregar movimentacoes.</div>';
  }
}

function mostrarFechamento(data, erroMsg) {
  const container = document.getElementById('fechamento-container');
  if (erroMsg) {
    container.innerHTML = `
      <div class="alert-modal error" style="display:block; margin-bottom:16px">
        ${escapeHtml(erroMsg)}
      </div>`;
  } else {
    container.innerHTML = `
      <div class="fechamento-card" style="margin-bottom:16px">
        <div class="fechamento-title">Caixa ${data.numeroCaixa} Encerrado</div>
        <div class="fechamento-rows">
          <div class="fechamento-row">
            <span class="label">Operador</span>
            <span class="value">${escapeHtml(data.nomeOperador || '-')}</span>
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
            <span class="label">Total Saidas</span>
            <span class="value" style="color:var(--red)">R$${data.totalSaidas.toFixed(2)}</span>
          </div>
          <div class="fechamento-row">
            <span class="label">Movimentacoes</span>
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
