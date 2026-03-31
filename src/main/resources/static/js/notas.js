// ── notas.js ───────────────────────────────────────────────────────────────
// Invoice wizard: 4-step flow (open → items → confirm → payment).

let fornecedoresAtivos = [];
let notaAtual = null;       // current nota in progress
let stepAtual = 1;

// ── Init ──────────────────────────────────────────────────────────────────

document.addEventListener('DOMContentLoaded', () => {
  carregarNotas();
  carregarFornecedores();
});

// ── Tab management ────────────────────────────────────────────────────────

function abrirAba(abaId) {
  document.querySelectorAll('.tab-panel').forEach(p => p.classList.remove('active'));
  document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
  document.getElementById(abaId).classList.add('active');
  if (abaId === 'aba-lista') document.getElementById('tab-lista').classList.add('active');
  if (abaId === 'aba-nova')  document.getElementById('tab-nova').classList.add('active');
}

// ── API ───────────────────────────────────────────────────────────────────

async function carregarNotas() {
  try {
    const resp = await fetch('/api/notas');
    const notas = await resp.json();
    renderTabelaNotas(notas);
  } catch {
    document.getElementById('tabela-notas').innerHTML =
      '<tr><td colspan="7" class="empty">Erro ao carregar notas.</td></tr>';
  }
}

async function carregarFornecedores() {
  try {
    const resp = await fetch('/api/fornecedores/ativos');
    fornecedoresAtivos = await resp.json();
    const select = document.getElementById('s1-fornecedor');
    if (fornecedoresAtivos.length === 0) {
      select.innerHTML = '<option value="">Nenhum fornecedor ativo cadastrado</option>';
      return;
    }
    select.innerHTML = `<option value="">— Selecione um fornecedor —</option>` +
      fornecedoresAtivos.map(f =>
        `<option value="${f.id}">${f.nome} (${f.produtos.length} produto(s))</option>`
      ).join('');
  } catch {
    document.getElementById('s1-fornecedor').innerHTML = '<option value="">Erro ao carregar</option>';
  }
}

// ── Render lista ──────────────────────────────────────────────────────────

function renderTabelaNotas(notas) {
  document.getElementById('notas-count').textContent = `${notas.length} nota(s)`;
  const tbody = document.getElementById('tabela-notas');
  if (notas.length === 0) {
    tbody.innerHTML = `
      <tr><td colspan="7">
        <div style="padding:40px;text-align:center">
          <div style="font-size:40px;margin-bottom:12px">🧾</div>
          <div style="color:var(--text-2);font-weight:500;margin-bottom:6px">Nenhuma nota fiscal registrada</div>
          <div style="color:var(--text-3);font-size:13px;margin-bottom:16px">Crie uma nova nota para registrar compras de fornecedores.</div>
          <button class="btn-primary" onclick="abrirAba('aba-nova')">+ Nova Nota</button>
        </div>
      </td></tr>`;
    return;
  }

  const statusBadge = {
    PENDENTE:   'badge-amber',
    CONFIRMADA: 'badge-cyan',
    PAGA:       'badge-green',
    CANCELADA:  'badge-gray'
  };
  const statusLabel = {
    PENDENTE: 'Pendente', CONFIRMADA: 'Confirmada', PAGA: 'Paga', CANCELADA: 'Cancelada'
  };

  tbody.innerHTML = notas.map(n => {
    const data = new Date(n.dataEmissao).toLocaleString('pt-BR', {
      day:'2-digit', month:'2-digit', year:'numeric',
      hour:'2-digit', minute:'2-digit'
    });
    return `
      <tr>
        <td><strong>#${n.id}</strong></td>
        <td>${n.fornecedorNome}</td>
        <td><span class="badge ${statusBadge[n.status] || 'badge-gray'}">${statusLabel[n.status] || n.status}</span></td>
        <td>${n.itens.length} item(ns)</td>
        <td style="color:var(--green); font-weight:600">R$${n.total.toFixed(2)}</td>
        <td style="color:var(--text-2); font-size:12px">${data}</td>
        <td>
          <div class="acoes">
            <button class="btn-acao" onclick="verDetalhe(${n.id})">Ver</button>
            ${n.status === 'CONFIRMADA'
              ? `<button class="btn-acao entrada" onclick="pagarNota(${n.id})">Pagar</button>`
              : ''}
            ${n.status === 'PENDENTE'
              ? `<button class="btn-acao saida" onclick="confirmarCancelarNota(${n.id})">Cancelar</button>`
              : ''}
          </div>
        </td>
      </tr>
    `;
  }).join('');
}

// ── Wizard Step 1: Select supplier and open nota ──────────────────────────

async function avancarStep1() {
  const btn = document.getElementById('btn-step1-avancar');
  const fornecedorId = parseInt(document.getElementById('s1-fornecedor').value);
  if (!fornecedorId) {
    mostrarAlert('alert-s1', 'Selecione um fornecedor ativo.', 'error'); return;
  }

  setLoading(btn, true);
    try {
      const resp = await fetch('/api/notas', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ fornecedorId })
      });
      if (!resp.ok) {
        const err = await safeReadErrorMessage(resp, 'Erro ao abrir nota.');
        mostrarAlert('alert-s1', err, 'error'); return;
      }
      const data = await resp.json();

      notaAtual = data;
      limparAlert('alert-s1');
      irParaStep(2);
      prepararStep2();
    } finally {
      setLoading(btn, false);
    }
  }

    notaAtual = data;
    limparAlert('alert-s1');
    irParaStep(2);
    prepararStep2();
  } finally {
    setLoading(btn, false);
  }
}

// ── Wizard Step 2: Add items ──────────────────────────────────────────────

function prepararStep2() {
  const fornecedor = fornecedoresAtivos.find(f => f.id === notaAtual.fornecedorId);
  document.getElementById('s2-fornecedor-badge').textContent = notaAtual.fornecedorNome;

  const select = document.getElementById('s2-produto');
  if (!fornecedor || fornecedor.produtos.length === 0) {
    select.innerHTML = '<option value="">Nenhum produto vinculado a este fornecedor</option>';
  } else {
    select.innerHTML = `<option value="">— Selecione um produto —</option>` +
      fornecedor.produtos.map(p =>
        `<option value="${p.id}">${p.nome}</option>`
      ).join('');
  }
  renderItensStep2();
}

function renderItensStep2() {
  const lista = document.getElementById('itens-lista');
  if (!notaAtual || notaAtual.itens.length === 0) {
    lista.innerHTML = '<div class="empty" style="padding:20px;text-align:center;color:var(--text-3)">Nenhum item adicionado.</div>';
    document.getElementById('s2-total').textContent = 'R$0,00';
    document.getElementById('btn-step2-avancar').disabled = true;
    return;
  }
  lista.innerHTML = notaAtual.itens.map(i => `
    <div class="item-nota-row">
      <span class="item-nota-nome">${i.produtoNome}</span>
      <span class="item-nota-qty">${i.quantidade} un.</span>
      <span class="item-nota-preco">× R$${i.precoUnitario.toFixed(2)}</span>
      <span class="item-nota-subtotal">R$${i.subtotal.toFixed(2)}</span>
    </div>
  `).join('');
  document.getElementById('s2-total').textContent = `R$${notaAtual.total.toFixed(2)}`;
  document.getElementById('btn-step2-avancar').disabled = false;
}

async function adicionarItem() {
  const btn = document.getElementById('btn-adicionar-item');
  const produtoId = parseInt(document.getElementById('s2-produto').value);
  const qtd = parseInt(document.getElementById('s2-qtd').value);
  const preco = parseFloat(document.getElementById('s2-preco').value);

  if (!produtoId) { mostrarAlert('alert-s2', 'Selecione um produto.', 'error'); return; }
  if (!qtd || qtd <= 0) { mostrarAlert('alert-s2', 'Quantidade deve ser maior que zero.', 'error'); return; }
  if (!preco || preco < 0) { mostrarAlert('alert-s2', 'Informe um preço válido.', 'error'); return; }

  setLoading(btn, true);
    try {
      const resp = await fetch(`/api/notas/${notaAtual.id}/itens`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ produtoId, quantidade: qtd, precoUnitario: preco })
      });
      if (!resp.ok) {
        const err = await safeReadErrorMessage(resp, 'Erro ao adicionar item.');
        mostrarAlert('alert-s2', err, 'error'); return;
      }
      const data = await resp.json();
      notaAtual = data;
      limparAlert('alert-s2');
      document.getElementById('s2-produto').value = '';
      document.getElementById('s2-qtd').value = '';
      document.getElementById('s2-preco').value = '';
      renderItensStep2();
    } finally {
      setLoading(btn, false);
    }
  }

async function avancarStep2() {
  if (!notaAtual || notaAtual.itens.length === 0) {
    mostrarAlert('alert-s2', 'Adicione pelo menos um item antes de continuar.', 'error'); return;
  }
  irParaStep(3);
  renderItensStep3();
}

// ── Descartar rascunho (BUG 1) ────────────────────────────────────────────

async function descartarRascunho() {
  if (!notaAtual) { resetWizard(); return; }
  if (!confirm('Descartar este rascunho? A nota será excluída permanentemente.')) return;

   try {
      const resp = await fetch(`/api/notas/${notaAtual.id}`, { method: 'DELETE' });
      if (!resp.ok) {
        const err = await safeReadErrorMessage(resp, 'Erro ao descartar rascunho.');
        showToast(err, 'error'); return;
      }
    showToast('Rascunho descartado.', 'success');
  } catch {
    showToast('Erro ao descartar rascunho.', 'error');
  }
  resetWizard();
  await carregarNotas();
}

function resetWizard() {
  notaAtual = null;
  irParaStep(1);
  document.getElementById('s1-fornecedor').value = '';
  limparAlert('alert-s1');
}

// ── Wizard Step 3: Confirm ────────────────────────────────────────────────

function renderItensStep3() {
  const lista = document.getElementById('s3-itens-lista');
  lista.innerHTML = notaAtual.itens.map(i => `
    <div class="item-nota-row">
      <span class="item-nota-nome">${i.produtoNome}</span>
      <span class="item-nota-qty">${i.quantidade} un.</span>
      <span class="item-nota-preco">× R$${i.precoUnitario.toFixed(2)}</span>
      <span class="item-nota-subtotal">R$${i.subtotal.toFixed(2)}</span>
    </div>
  `).join('');
  document.getElementById('s3-total').textContent = `R$${notaAtual.total.toFixed(2)}`;
}

async function avancarStep3() {
  const btn = document.getElementById('btn-step3-avancar');
  setLoading(btn, true);
  try {
    const resp = await fetch(`/api/notas/${notaAtual.id}/confirmar`, { method: 'POST' });
    if (!resp.ok) {
      const err = await safeReadErrorMessage(resp, 'Erro ao confirmar nota.');
      mostrarAlert('alert-s3', err, 'error'); return;
    }
    const data = await resp.json();
    notaAtual = data;
    limparAlert('alert-s3');
    irParaStep(4);
    renderStep4();
  } finally {
    setLoading(btn, false);
  }
}

// ── Wizard Step 4: Payment ────────────────────────────────────────────────

function renderStep4() {
  document.getElementById('s4-resumo').innerHTML = `
    <div class="info-card">
      <div class="info-label">Fornecedor</div>
      <div style="font-size:14px; font-weight:600; margin-top:4px">${notaAtual.fornecedorNome}</div>
    </div>
    <div class="info-card">
      <div class="info-label">Itens na Nota</div>
      <div class="info-value">${notaAtual.itens.length}</div>
    </div>
    <div class="info-card">
      <div class="info-label">Valor Total</div>
      <div class="info-value green">R$${notaAtual.total.toFixed(2)}</div>
    </div>
  `;
}

async function avancarStep4() {
  const btn = document.getElementById('btn-step4-avancar');
  setLoading(btn, true);
  try {
    const resp = await fetch(`/api/notas/${notaAtual.id}/pagamento`, { method: 'POST' });
    if (!resp.ok) {
      const err = await safeReadErrorMessage(resp, 'Erro ao registrar pagamento.');
      mostrarAlert('alert-s4', err, 'error'); return;
    }
    const data = await resp.json();
    notaAtual = data;
    irParaStep(5); // done state
  } finally {
    setLoading(btn, false);
  }
}

// ── Wizard navigation ─────────────────────────────────────────────────────

function irParaStep(step) {
  stepAtual = step;
  for (let i = 1; i <= 4; i++) {
    const el = document.getElementById(`step-${i}`);
    const conn = document.getElementById(`conn-${i}`);
    el.className = 'wizard-step' + (i < step ? ' done' : i === step ? ' active' : '');
    if (conn) conn.className = 'wizard-connector' + (i < step ? ' done' : '');
  }
  document.getElementById('painel-step1').style.display = step === 1 ? 'block' : 'none';
  document.getElementById('painel-step2').style.display = step === 2 ? 'block' : 'none';
  document.getElementById('painel-step3').style.display = step === 3 ? 'block' : 'none';
  document.getElementById('painel-step4').style.display = step === 4 ? 'block' : 'none';
  document.getElementById('painel-done').style.display  = step === 5 ? 'block' : 'none';
}

function iniciarNovaNota() {
  notaAtual = null;
  irParaStep(1);
  carregarFornecedores();
  carregarNotas();
}

// ── Cancel nota from list ─────────────────────────────────────────────────

async function confirmarCancelarNota(id) {
  if (!confirm('Cancelar esta nota fiscal? Esta ação não pode ser desfeita.')) return;
  try {
    const resp = await fetch(`/api/notas/${id}/cancelar`, { method: 'POST' });
    if (!resp.ok) {
      const err = await safeReadErrorMessage(resp, 'Erro ao cancelar nota.');
      showToast(err, 'error'); return;
    }
    showToast('Nota cancelada com sucesso.', 'success');
    await carregarNotas();
  } catch {
    showToast('Erro ao cancelar nota.', 'error');
  }
}

// ── Nota detail from list ─────────────────────────────────────────────────

async function verDetalhe(id) {
  try {
    const resp = await fetch(`/api/notas/${id}`);
    const nota = await resp.json();
    const statusLabel = { PENDENTE: 'Pendente', CONFIRMADA: 'Confirmada', PAGA: 'Paga', CANCELADA: 'Cancelada' };
    const statusBadge = { PENDENTE: 'badge-amber', CONFIRMADA: 'badge-cyan', PAGA: 'badge-green', CANCELADA: 'badge-gray' };

    document.getElementById('modal-detalhe-titulo').textContent = `Nota #${nota.id}`;
    document.getElementById('detalhe-info').innerHTML = `
      <div style="display:flex; gap:16px; margin-bottom:12px; flex-wrap:wrap">
        <div><span style="font-size:11px;color:var(--text-3)">FORNECEDOR</span><br><strong>${nota.fornecedorNome}</strong></div>
        <div><span style="font-size:11px;color:var(--text-3)">STATUS</span><br><span class="badge ${statusBadge[nota.status]}">${statusLabel[nota.status]}</span></div>
        <div><span style="font-size:11px;color:var(--text-3)">TOTAL</span><br><strong style="color:var(--green)">R$${nota.total.toFixed(2)}</strong></div>
      </div>
    `;
    document.getElementById('detalhe-itens').innerHTML = nota.itens.length === 0
      ? '<div class="empty">Sem itens.</div>'
      : `<div class="itens-nota-list">` + nota.itens.map(i => `
          <div class="item-nota-row">
            <span class="item-nota-nome">${i.produtoNome}</span>
            <span class="item-nota-qty">${i.quantidade} un.</span>
            <span class="item-nota-preco">× R$${i.precoUnitario.toFixed(2)}</span>
            <span class="item-nota-subtotal">R$${i.subtotal.toFixed(2)}</span>
          </div>`).join('') + '</div>';

    limparAlert('alert-detalhe');
    abrirModal('modal-detalhe');
  } catch {
    showToast('Erro ao carregar detalhes da nota.', 'error');
  }
}

async function pagarNota(id) {
  const btn = event && event.target ? event.target : null;
  setLoading(btn, true);
  try {
    const resp = await fetch(`/api/notas/${id}/pagamento`, { method: 'POST' });
    if (!resp.ok) {
      const err = await safeReadErrorMessage(resp, 'Erro ao registrar pagamento.');
      showToast(err, 'error'); return;
    }
    showToast('Pagamento registrado com sucesso.', 'success');
    await carregarNotas();
    fecharModal('modal-detalhe');
  } finally {
    setLoading(btn, false);
  }
}

// ── Modal helpers ─────────────────────────────────────────────────────────

function abrirModal(id) { document.getElementById(id).classList.add('open'); }
function fecharModal(id) { document.getElementById(id).classList.remove('open'); }
function fecharModalSeClicouFora(e, id) { if (e.target.id === id) fecharModal(id); }
function mostrarAlert(id, msg, tipo) {
  const el = document.getElementById(id);
  el.textContent = msg;
  el.className = `alert-modal ${tipo}`;
}
function limparAlert(id) {
  const el = document.getElementById(id);
  if (el) { el.textContent = ''; el.className = 'alert-modal'; }
}
