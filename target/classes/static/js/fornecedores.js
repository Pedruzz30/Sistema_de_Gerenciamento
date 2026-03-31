// ── fornecedores.js ────────────────────────────────────────────────────────
// Supplier management: CRUD, activate/deactivate, link products.

let todosFornecedores = [];
let todosProdutos = [];
let filtroAtivo = 'todos';
let fornecedorVincularId = null;

// ── Init ──────────────────────────────────────────────────────────────────

document.addEventListener('DOMContentLoaded', () => {
  carregarTudo();
});

async function carregarTudo() {
  await Promise.all([carregarFornecedores(), carregarProdutos()]);
}

// ── API ───────────────────────────────────────────────────────────────────

async function carregarFornecedores() {
  try {
    const resp = await fetch('/api/fornecedores');
    if (!resp.ok) throw new Error();
    todosFornecedores = await resp.json();
    renderTabela();
  } catch {
    document.getElementById('tabela-fornecedores').innerHTML =
      '<tr><td colspan="6" class="empty">Erro ao carregar fornecedores.</td></tr>';
  }
}

async function carregarProdutos() {
  try {
    const resp = await fetch('/api/produtos');
    if (!resp.ok) throw new Error();
    todosProdutos = await resp.json();
  } catch {
    todosProdutos = [];
  }
}

// ── Render ────────────────────────────────────────────────────────────────

function renderTabela() {
  const busca = document.getElementById('busca').value.toLowerCase();
  let lista = todosFornecedores.filter(f => {
    const matchFiltro =
      filtroAtivo === 'todos' ||
      (filtroAtivo === 'ativos' && f.ativo) ||
      (filtroAtivo === 'inativos' && !f.ativo);
    const matchBusca = !busca ||
      f.nome.toLowerCase().includes(busca) ||
      f.cnpj.toLowerCase().includes(busca);
    return matchFiltro && matchBusca;
  });

  document.getElementById('table-count').textContent = `${lista.length} fornecedor(es)`;

  const tbody = document.getElementById('tabela-fornecedores');
  if (lista.length === 0) {
    if (todosFornecedores.length === 0) {
      tbody.innerHTML = `
        <tr><td colspan="6">
          <div style="padding:40px;text-align:center">
            <div style="font-size:40px;margin-bottom:12px">🚚</div>
            <div style="color:var(--text-2);font-weight:500;margin-bottom:6px">Nenhum fornecedor cadastrado</div>
            <div style="color:var(--text-3);font-size:13px;margin-bottom:16px">Adicione fornecedores para vincular a notas fiscais.</div>
            <button class="btn-primary" onclick="abrirModalCadastro()">+ Adicionar Fornecedor</button>
          </div>
        </td></tr>`;
    } else {
      tbody.innerHTML = '<tr><td colspan="6" class="empty">Nenhum fornecedor encontrado para os filtros aplicados.</td></tr>';
    }
    return;
  }

  tbody.innerHTML = lista.map(f => `
    <tr>
      <td>
        <div class="produto-nome">${f.nome}</div>
        <div class="produto-id">#${f.id}</div>
      </td>
      <td style="color:var(--text-2)">${f.cnpj}</td>
      <td style="color:var(--text-2)">${f.telefone || '—'}</td>
      <td>
        ${f.produtos.length > 0
          ? `<button class="btn-acao" onclick="verProdutos(${f.id})">${f.produtos.length} produto(s)</button>`
          : '<span style="color:var(--text-3);font-size:12px">Nenhum</span>'
        }
      </td>
      <td>
        <span class="badge ${f.ativo ? 'badge-green' : 'badge-gray'}">
          ${f.ativo ? 'Ativo' : 'Inativo'}
        </span>
      </td>
      <td>
        <div class="acoes">
          <button class="btn-acao" onclick="abrirModalVincular(${f.id})">Vincular Produto</button>
          ${f.ativo
            ? `<button class="btn-acao saida" onclick="desativar(${f.id})">Desativar</button>`
            : `<button class="btn-acao entrada" onclick="reativar(${f.id})">Reativar</button>`
          }
        </div>
      </td>
    </tr>
  `).join('');
}

// ── Actions ───────────────────────────────────────────────────────────────

function setFiltro(tipo, btn) {
  filtroAtivo = tipo;
  document.querySelectorAll('.filter-btn').forEach(b => b.classList.remove('active'));
  btn.classList.add('active');
  renderTabela();
}

function filtrar() {
  renderTabela();
}

async function cadastrarFornecedor() {
  const nome = document.getElementById('c-nome').value.trim();
  const cnpj = document.getElementById('c-cnpj').value.trim();
  const telefone = document.getElementById('c-telefone').value.trim();

  if (!nome) { mostrarAlert('alert-cadastro', 'Nome é obrigatório.', 'error'); return; }
  if (!cnpj) { mostrarAlert('alert-cadastro', 'CNPJ é obrigatório.', 'error'); return; }

  const btn = document.querySelector('#modal-cadastro .btn-primary');
  setLoading(btn, true);
  try {
    const resp = await fetch('/api/fornecedores', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ nome, cnpj, telefone })
    });
    if (!resp.ok) {
      const err = await safeReadErrorMessage(resp, 'Erro ao cadastrar.');
      mostrarAlert('alert-cadastro', err, 'error'); return;
    }
    fecharModal('modal-cadastro');
    showToast('Fornecedor cadastrado com sucesso!', 'success');
    await carregarFornecedores();
  } finally {
    setLoading(btn, false);
  }
}

async function desativar(id) {
  const resp = await fetch(`/api/fornecedores/${id}/desativar`, { method: 'PUT' });
  if (!resp.ok) {
    const err = await safeReadErrorMessage(resp, 'Erro ao desativar.');
    showToast(err, 'error'); return;
  }
  showToast('Fornecedor desativado.', 'success');
  await carregarFornecedores();
}

async function reativar(id) {
  const resp = await fetch(`/api/fornecedores/${id}/reativar`, { method: 'PUT' });
  if (!resp.ok) {
    const err = await safeReadErrorMessage(resp, 'Erro ao reativar.');
    showToast(err, 'error'); return;
  }
  showToast('Fornecedor reativado.', 'success');
  await carregarFornecedores();
}

function abrirModalVincular(id) {
  fornecedorVincularId = id;
  const fornecedor = todosFornecedores.find(f => f.id === id);
  limparAlert('alert-vincular');

  document.getElementById('vincular-fornecedor-nome').textContent = fornecedor.nome;
  const jaVinculados = fornecedor.produtos.map(p => p.id);
  const disponiveis = todosProdutos.filter(p => !jaVinculados.includes(p.id));

  document.getElementById('vincular-produtos-atuais').textContent =
    fornecedor.produtos.length > 0
      ? `Já vinculados: ${fornecedor.produtos.map(p => p.nome).join(', ')}`
      : 'Nenhum produto vinculado ainda.';

  const select = document.getElementById('vincular-produto-select');
  if (disponiveis.length === 0) {
    select.innerHTML = '<option value="">Todos os produtos já estão vinculados</option>';
  } else {
    select.innerHTML = `<option value="">— Selecione um produto —</option>` +
      disponiveis.map(p => `<option value="${p.id}">${p.nome} (R$${p.precoUnitario.toFixed(2)})</option>`).join('');
  }
  abrirModal('modal-vincular');
}

async function confirmarVincular() {
  const produtoId = parseInt(document.getElementById('vincular-produto-select').value);
  if (!produtoId) {
    mostrarAlert('alert-vincular', 'Selecione um produto.', 'error'); return;
  }
  const resp = await fetch(`/api/fornecedores/${fornecedorVincularId}/vincular-produto`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ produtoId })
  });
  if (!resp.ok) {
    const err = await safeReadErrorMessage(resp, 'Erro ao vincular.');
    mostrarAlert('alert-vincular', err, 'error'); return;
  }
  fecharModal('modal-vincular');
  await carregarFornecedores();
}

function verProdutos(id) {
  const fornecedor = todosFornecedores.find(f => f.id === id);
  document.getElementById('modal-produtos-titulo').textContent = `Produtos — ${fornecedor.nome}`;
  const lista = document.getElementById('produtos-lista');
  if (fornecedor.produtos.length === 0) {
    lista.innerHTML = '<div class="empty">Nenhum produto vinculado.</div>';
  } else {
    lista.innerHTML = `
      <div class="itens-nota-list">
        ${fornecedor.produtos.map(p => `
          <div class="item-nota-row">
            <span class="item-nota-nome">${p.nome}</span>
            <span class="item-nota-preco">R$${p.precoUnitario.toFixed(2)}</span>
          </div>
        `).join('')}
      </div>`;
  }
  abrirModal('modal-produtos');
}

// ── Modal helpers ─────────────────────────────────────────────────────────

function abrirModalCadastro() {
  limparAlert('alert-cadastro');
  document.getElementById('c-nome').value = '';
  document.getElementById('c-cnpj').value = '';
  document.getElementById('c-telefone').value = '';
  abrirModal('modal-cadastro');
}

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
  el.textContent = '';
  el.className = 'alert-modal';
}
