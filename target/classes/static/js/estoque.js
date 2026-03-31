// ── estoque.js ────────────────────────────────────────────────
// Gerencia a tela de estoque: listagem, cadastro e movimentações

let todosOsProdutos = [];   // cache local dos produtos da API
let filtroAtivo     = 'todos';
let produtoSelecionado = null;
let tipoMovAtual    = 'ENTRADA';

// ── Carregamento inicial ───────────────────────────────────────
document.addEventListener('DOMContentLoaded', async () => {
  // Esconde botão de cadastro se não tiver permissão
  if (!temPermissao(PERMISSOES.EDITAR_ESTOQUE)) {
    const btn = document.getElementById('btn-novo-produto');
    if (btn) btn.style.display = 'none';
  }
  await carregarProdutos();
});

// ── API calls ──────────────────────────────────────────────────
async function carregarProdutos() {
  try {
    const res = await fetch('/api/produtos');
    if (!res.ok) throw new Error('Falha ao carregar produtos');
    todosOsProdutos = await res.json();
    renderTabela();
  } catch (e) {
    document.getElementById('tabela-produtos').innerHTML =
      `<tr><td colspan="6" class="empty">Erro ao carregar produtos. Verifique a conexão.</td></tr>`;
  }
}

async function cadastrarProduto() {
  const nome        = document.getElementById('c-nome').value.trim();
  const qtdInicial  = parseInt(document.getElementById('c-qtd-inicial').value);
  const qtdMinima   = parseInt(document.getElementById('c-qtd-minima').value);
  const preco       = parseFloat(document.getElementById('c-preco').value);
  const alertEl     = document.getElementById('alert-cadastro');

  // Validação local
  if (!nome) return mostrarAlertaModal(alertEl, 'error', 'Nome do produto é obrigatório.');
  if (isNaN(qtdInicial) || qtdInicial < 0) return mostrarAlertaModal(alertEl, 'error', 'Quantidade inicial inválida.');
  if (isNaN(qtdMinima)  || qtdMinima < 0)  return mostrarAlertaModal(alertEl, 'error', 'Quantidade mínima inválida.');
  if (isNaN(preco)      || preco < 0)      return mostrarAlertaModal(alertEl, 'error', 'Preço inválido.');

  try {
    const res = await fetch('/api/produtos', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ nome, quantidadeInicial: qtdInicial, quantidadeMinima: qtdMinima, precoUnitario: preco })
    });

    if (!res.ok) {
          const err = await safeReadErrorMessage(res, 'Erro ao cadastrar produto.');
          throw new Error(err);
        }

    mostrarAlertaModal(alertEl, 'success', `"${nome}" cadastrado com sucesso!`);
    limparFormCadastro();
    await carregarProdutos();

    setTimeout(() => fecharModal('modal-cadastro'), 1200);
  } catch (e) {
    mostrarAlertaModal(alertEl, 'error', e.message);
  }
}

async function confirmarMovimentacao() {
  const qtd     = parseInt(document.getElementById('mov-qtd').value);
  const alertEl = document.getElementById('alert-mov');

  if (!produtoSelecionado) return;
  if (isNaN(qtd) || qtd <= 0) return mostrarAlertaModal(alertEl, 'error', 'Quantidade deve ser maior que zero.');

  if (tipoMovAtual === 'SAIDA' && qtd > produtoSelecionado.quantidadeAtual) {
    return mostrarAlertaModal(alertEl, 'error',
      `Estoque insuficiente. Disponível: ${produtoSelecionado.quantidadeAtual} unidades.`);
  }

  try {
    const res = await fetch('/api/movimentacoes', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        idProduto: produtoSelecionado.id,
        quantidade: qtd,
        tipo: tipoMovAtual
      })
    });

    if (!res.ok) {
          const err = await safeReadErrorMessage(res, 'Erro ao registrar movimentação.');
          throw new Error(err);
        }

    const sinal = tipoMovAtual === 'ENTRADA' ? '+' : '-';
    mostrarAlertaModal(alertEl, 'success',
      `${sinal}${qtd} unidades registradas com sucesso!`);

    await carregarProdutos();
    setTimeout(() => fecharModal('modal-mov'), 1000);
  } catch (e) {
    mostrarAlertaModal(alertEl, 'error', e.message);
  }
}

// ── Renderização da tabela ─────────────────────────────────────
function renderTabela() {
  const tbody   = document.getElementById('tabela-produtos');
  const countEl = document.getElementById('table-count');
  const busca   = document.getElementById('busca').value.toLowerCase();

  let produtos = todosOsProdutos.filter(p => {
    const matchBusca = p.nome.toLowerCase().includes(busca);
    const matchFiltro =
      filtroAtivo === 'todos'    ? true :
      filtroAtivo === 'critico'  ? p.nivelEstoque === 'CRITICO' || p.nivelEstoque === 'SEM_ESTOQUE' :
      filtroAtivo === 'baixo'    ? p.nivelEstoque === 'BAIXO' || p.nivelEstoque === 'MODERADO' :
      filtroAtivo === 'adequado' ? p.nivelEstoque === 'ADEQUADO' : true;
    return matchBusca && matchFiltro;
  });

  countEl.textContent = `${produtos.length} produto${produtos.length !== 1 ? 's' : ''}`;

  const podeEditar = temPermissao(PERMISSOES.EDITAR_ESTOQUE);

  if (produtos.length === 0) {
    if (todosOsProdutos.length === 0) {
      tbody.innerHTML = `
        <tr><td colspan="6">
          <div style="padding:40px;text-align:center">
            <div style="font-size:40px;margin-bottom:12px">📦</div>
            <div style="color:var(--text-2);font-weight:500;margin-bottom:6px">Nenhum produto cadastrado</div>
            <div style="color:var(--text-3);font-size:13px;margin-bottom:16px">Cadastre produtos para começar a gerenciar seu estoque.</div>
            ${podeEditar ? '<button class="btn-primary" onclick="abrirModalCadastro()">+ Cadastrar Produto</button>' : ''}
          </div>
        </td></tr>`;
    } else {
      tbody.innerHTML = `<tr><td colspan="6" class="empty">Nenhum produto encontrado para os filtros aplicados.</td></tr>`;
    }
    return;
  }

  tbody.innerHTML = produtos.map(p => `
    <tr>
      <td>
        <div class="produto-nome">${p.nome}</div>
        <div class="produto-id">#${p.id}</div>
      </td>
      <td><strong>${p.quantidadeAtual}</strong> un.</td>
      <td>${p.quantidadeMinima} un.</td>
      <td>
        <div class="nivel-wrap nivel-${p.nivelEstoque}">
          <div class="nivel-bar">
            <div class="nivel-bar-fill"></div>
          </div>
          <span class="nivel-badge">${nivelLabel(p.nivelEstoque)}</span>
        </div>
      </td>
      <td>R$ ${p.precoUnitario.toFixed(2)}</td>
      <td>
        <div class="acoes">
          ${podeEditar ? `
            <button class="btn-acao entrada" onclick="abrirModalMov(${p.id}, 'ENTRADA')">↑ Entrada</button>
            <button class="btn-acao saida"   onclick="abrirModalMov(${p.id}, 'SAIDA')">↓ Saída</button>
          ` : '<span style="color:var(--text-3);font-size:12px">Sem permissão</span>'}
        </div>
      </td>
    </tr>
  `).join('');
}

function nivelLabel(nivel) {
  const labels = {
    SEM_ESTOQUE: 'Sem Estoque',
    CRITICO:     'Crítico',
    BAIXO:       'Baixo',
    MODERADO:    'Moderado',
    ADEQUADO:    'Adequado',
  };
  return labels[nivel] || nivel;
}

// ── Filtros ────────────────────────────────────────────────────
function filtrar(tipo, btn) {
  filtroAtivo = tipo;
  document.querySelectorAll('.filter-btn').forEach(b => b.classList.remove('active'));
  btn.classList.add('active');
  renderTabela();
}

function filtrarProdutos() {
  renderTabela();
}

// ── Modais ─────────────────────────────────────────────────────
function abrirModalCadastro() {
  limparFormCadastro();
  document.getElementById('modal-cadastro').classList.add('open');
}

function abrirModalMov(idProduto, tipo) {
  produtoSelecionado = todosOsProdutos.find(p => p.id === idProduto);
  if (!produtoSelecionado) return;

  document.getElementById('mov-produto-nome').textContent = produtoSelecionado.nome;
  document.getElementById('mov-produto-qtd').textContent  = `${produtoSelecionado.quantidadeAtual} unidades`;
  document.getElementById('mov-qtd').value = '';
  document.getElementById('alert-mov').className = 'alert-modal';

  setTipo(tipo);
  document.getElementById('modal-mov-titulo').textContent =
    tipo === 'ENTRADA' ? '↑ Registrar Entrada' : '↓ Registrar Saída';
  document.getElementById('modal-mov').classList.add('open');
}

function fecharModal(id) {
  document.getElementById(id).classList.remove('open');
}

function fecharModalSeClicouFora(event, id) {
  if (event.target.id === id) fecharModal(id);
}

function setTipo(tipo) {
  tipoMovAtual = tipo;
  document.getElementById('btn-entrada').classList.toggle('active', tipo === 'ENTRADA');
  document.getElementById('btn-saida').classList.toggle('active',   tipo === 'SAIDA');
}

function limparFormCadastro() {
  ['c-nome','c-qtd-inicial','c-qtd-minima','c-preco'].forEach(id => {
    document.getElementById(id).value = '';
  });
  document.getElementById('alert-cadastro').className = 'alert-modal';
}

// ── Utilitários ────────────────────────────────────────────────
function mostrarAlertaModal(el, tipo, msg) {
  el.className = `alert-modal ${tipo}`;
  el.textContent = msg;
}
