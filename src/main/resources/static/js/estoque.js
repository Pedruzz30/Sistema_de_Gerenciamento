// Stock screen: listing, category filtering, product create/edit, and movements.

let todosOsProdutos = [];
let produtosVisiveis = [];
let filtroNivelAtivo = 'todos';
let filtroCategoriaAtiva = 'todas';
let produtoSelecionado = null;
let produtoEmEdicao = null;
let tipoMovAtual = 'ENTRADA';

const CATEGORIAS_ESTOQUE = [
  { value: '', label: 'Nao definida' },
  { value: 'BEBIDAS', label: 'Bebidas' },
  { value: 'SECO', label: 'Estoque seco' },
  { value: 'FRIO', label: 'Estoque frio' },
  { value: 'CONGELADO', label: 'Estoque congelado' }
];

document.addEventListener('DOMContentLoaded', async () => {
  if (!temPermissao(PERMISSOES.EDITAR_ESTOQUE)) {
    const btn = document.getElementById('btn-novo-produto');
    if (btn) btn.style.display = 'none';
  }

  preencherSelectCategorias('c-categoria');
  preencherSelectCategorias('e-categoria');
  await carregarProdutos();
});

async function carregarProdutos() {
  try {
    const res = await fetch('/api/produtos');
    if (!res.ok) throw new Error('Falha ao carregar produtos');
    todosOsProdutos = await res.json();
    renderTabela();
  } catch (e) {
    document.getElementById('tabela-produtos').innerHTML =
      '<tr><td colspan="7" class="empty">Erro ao carregar produtos. Verifique a conexao.</td></tr>';
  }
}

async function cadastrarProduto() {
  const nome = document.getElementById('c-nome').value.trim();
  const qtdInicial = parseInt(document.getElementById('c-qtd-inicial').value, 10);
  const qtdMinima = parseInt(document.getElementById('c-qtd-minima').value, 10);
  const preco = parseFloat(document.getElementById('c-preco').value);
  const categoriaEstoque = document.getElementById('c-categoria').value || null;
  const alertEl = document.getElementById('alert-cadastro');

  if (!nome) return mostrarAlertaModal(alertEl, 'error', 'Nome do produto e obrigatorio.');
  if (isNaN(qtdInicial) || qtdInicial < 0) return mostrarAlertaModal(alertEl, 'error', 'Quantidade inicial invalida.');
  if (isNaN(qtdMinima) || qtdMinima < 0) return mostrarAlertaModal(alertEl, 'error', 'Quantidade minima invalida.');
  if (isNaN(preco) || preco < 0) return mostrarAlertaModal(alertEl, 'error', 'Preco invalido.');

  try {
    const res = await fetch('/api/produtos', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        nome,
        quantidadeInicial: qtdInicial,
        quantidadeMinima: qtdMinima,
        precoUnitario: preco,
        categoriaEstoque
      })
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

async function atualizarProduto() {
  if (!produtoEmEdicao) return;

  const nome = document.getElementById('e-nome').value.trim();
  const qtdMinima = parseInt(document.getElementById('e-qtd-minima').value, 10);
  const preco = parseFloat(document.getElementById('e-preco').value);
  const categoriaEstoque = document.getElementById('e-categoria').value || null;
  const alertEl = document.getElementById('alert-edicao');

  if (!nome) return mostrarAlertaModal(alertEl, 'error', 'Nome do produto e obrigatorio.');
  if (isNaN(qtdMinima) || qtdMinima < 0) return mostrarAlertaModal(alertEl, 'error', 'Quantidade minima invalida.');
  if (isNaN(preco) || preco < 0) return mostrarAlertaModal(alertEl, 'error', 'Preco invalido.');

  try {
    const res = await fetch(`/api/produtos/${produtoEmEdicao.id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        nome,
        quantidadeMinima: qtdMinima,
        precoUnitario: preco,
        categoriaEstoque
      })
    });

    if (!res.ok) {
      const err = await safeReadErrorMessage(res, 'Erro ao atualizar produto.');
      throw new Error(err);
    }

    mostrarAlertaModal(alertEl, 'success', `"${nome}" atualizado com sucesso!`);
    await carregarProdutos();
    setTimeout(() => fecharModal('modal-edicao'), 1000);
  } catch (e) {
    mostrarAlertaModal(alertEl, 'error', e.message);
  }
}

async function confirmarMovimentacao() {
  const qtd = parseInt(document.getElementById('mov-qtd').value, 10);
  const alertEl = document.getElementById('alert-mov');

  if (!produtoSelecionado) return;
  if (isNaN(qtd) || qtd <= 0) return mostrarAlertaModal(alertEl, 'error', 'Quantidade deve ser maior que zero.');

  if (tipoMovAtual === 'SAIDA' && qtd > produtoSelecionado.quantidadeAtual) {
    return mostrarAlertaModal(
      alertEl,
      'error',
      `Estoque insuficiente. Disponivel: ${produtoSelecionado.quantidadeAtual} unidades.`
    );
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
      const err = await safeReadErrorMessage(res, 'Erro ao registrar movimentacao.');
      throw new Error(err);
    }

    const sinal = tipoMovAtual === 'ENTRADA' ? '+' : '-';
    mostrarAlertaModal(alertEl, 'success', `${sinal}${qtd} unidades registradas com sucesso!`);

    await carregarProdutos();
    setTimeout(() => fecharModal('modal-mov'), 1000);
  } catch (e) {
    mostrarAlertaModal(alertEl, 'error', e.message);
  }
}

function renderTabela() {
  const tbody = document.getElementById('tabela-produtos');
  const countEl = document.getElementById('table-count');
  const podeEditar = temPermissao(PERMISSOES.EDITAR_ESTOQUE);
  const produtos = getProdutosVisiveis();
  produtosVisiveis = produtos;

  countEl.textContent = `${produtos.length} produto${produtos.length !== 1 ? 's' : ''}`;
  atualizarEstadoExportacao();
  renderResumoCategorias(produtos);
  renderGraficoCategorias(produtos);

  if (produtos.length === 0) {
    if (todosOsProdutos.length === 0) {
      tbody.innerHTML = `
        <tr><td colspan="7">
          <div style="padding:40px;text-align:center">
            <div style="font-size:40px;margin-bottom:12px">[ ]</div>
            <div style="color:var(--text-2);font-weight:500;margin-bottom:6px">Nenhum produto cadastrado</div>
            <div style="color:var(--text-3);font-size:13px;margin-bottom:16px">Cadastre produtos para comecar a gerenciar seu estoque.</div>
            ${podeEditar ? '<button class="btn-primary" onclick="abrirModalCadastro()">+ Cadastrar Produto</button>' : ''}
          </div>
        </td></tr>`;
    } else {
      tbody.innerHTML = '<tr><td colspan="7" class="empty">Nenhum produto encontrado para os filtros aplicados.</td></tr>';
    }
    return;
  }

  tbody.innerHTML = produtos.map(p => `
    <tr>
      <td>
        <div class="produto-nome">${escapeHtml(p.nome)}</div>
        <div class="produto-id">#${p.id}</div>
      </td>
      <td>${renderCategoriaBadge(p.categoriaEstoque)}</td>
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
          ${podeEditar ? `<button class="btn-acao editar" onclick="abrirModalEdicao(${p.id})">Editar</button>` : ''}
          ${podeEditar ? `<button class="btn-acao entrada" onclick="abrirModalMov(${p.id}, 'ENTRADA')">Entrada</button>` : ''}
          ${podeEditar ? `<button class="btn-acao saida" onclick="abrirModalMov(${p.id}, 'SAIDA')">Saida</button>` : ''}
          ${!podeEditar ? '<span style="color:var(--text-3);font-size:12px">Sem permissao</span>' : ''}
        </div>
      </td>
    </tr>
  `).join('');
}

function nivelLabel(nivel) {
  const labels = {
    SEM_ESTOQUE: 'Sem Estoque',
    CRITICO: 'Critico',
    BAIXO: 'Baixo',
    MODERADO: 'Moderado',
    ADEQUADO: 'Adequado'
  };
  return labels[nivel] || nivel;
}

function getProdutosVisiveis() {
  const busca = document.getElementById('busca').value.toLowerCase();

  return todosOsProdutos.filter(p => {
    const nome = String(p.nome || '').toLowerCase();
    const categoria = p.categoriaEstoque || '';

    const matchBusca = nome.includes(busca);
    const matchNivel =
      filtroNivelAtivo === 'todos' ? true :
      filtroNivelAtivo === 'critico' ? p.nivelEstoque === 'CRITICO' || p.nivelEstoque === 'SEM_ESTOQUE' :
      filtroNivelAtivo === 'baixo' ? p.nivelEstoque === 'BAIXO' || p.nivelEstoque === 'MODERADO' :
      filtroNivelAtivo === 'adequado' ? p.nivelEstoque === 'ADEQUADO' :
      true;
    const matchCategoria = filtroCategoriaAtiva === 'todas' ? true : categoria === filtroCategoriaAtiva;

    return matchBusca && matchNivel && matchCategoria;
  });
}

function categoriaLabel(categoria) {
  const labels = {
    BEBIDAS: 'Bebidas',
    SECO: 'Estoque seco',
    FRIO: 'Estoque frio',
    CONGELADO: 'Estoque congelado'
  };
  return labels[categoria] || 'N\u00e3o definida';
}

function renderCategoriaBadge(categoria) {
  const classe = categoria ? `categoria-${categoria}` : 'categoria-nao-definida';
  return `<span class="categoria-badge ${classe}">${categoriaLabel(categoria)}</span>`;
}

function calcularMetricasCategorias(produtos) {
  const metricas = {
    BEBIDAS: 0,
    SECO: 0,
    FRIO: 0,
    CONGELADO: 0
  };

  produtos.forEach(p => {
    if (metricas[p.categoriaEstoque] !== undefined) {
      metricas[p.categoriaEstoque] += 1;
    }
  });

  return metricas;
}

function renderResumoCategorias(produtos) {
  const metricas = calcularMetricasCategorias(produtos);
  const cards = [
    { key: 'BEBIDAS', label: 'Bebidas' },
    { key: 'SECO', label: 'Estoque seco' },
    { key: 'FRIO', label: 'Estoque frio' },
    { key: 'CONGELADO', label: 'Estoque congelado' }
  ];

  const container = document.getElementById('categoria-summary');
  container.innerHTML = cards.map(card => `
    <div class="categoria-summary-card">
      <div class="categoria-summary-label">${card.label}</div>
      <div class="categoria-summary-value">${metricas[card.key]}</div>
      <div class="categoria-summary-sub">produto(s) visiveis</div>
    </div>
  `).join('');
}

function renderGraficoCategorias(produtos) {
  const metricas = calcularMetricasCategorias(produtos);
  const categorias = [
    { key: 'BEBIDAS', label: 'Bebidas' },
    { key: 'SECO', label: 'Estoque seco' },
    { key: 'FRIO', label: 'Estoque frio' },
    { key: 'CONGELADO', label: 'Estoque congelado' }
  ];
  const maximo = Math.max(1, ...categorias.map(c => metricas[c.key]));
  const container = document.getElementById('categoria-chart');

  container.innerHTML = categorias.map(cat => {
    const valor = metricas[cat.key];
    const largura = `${(valor / maximo) * 100}%`;
    return `
      <div class="categoria-chart-row">
        <div class="categoria-chart-label">${cat.label}</div>
        <div class="categoria-chart-bar">
          <div class="categoria-chart-fill categoria-${cat.key}" style="width:${largura}"></div>
        </div>
        <div class="categoria-chart-value">${valor}</div>
      </div>
    `;
  }).join('');
}

function atualizarEstadoExportacao() {
  const btn = document.getElementById('btn-exportar-csv');
  if (!btn) return;
  btn.disabled = produtosVisiveis.length === 0;
}

function exportarCsvProdutos() {
  if (!produtosVisiveis.length) {
    showToast('Nao ha linhas visiveis para exportar.', 'info');
    return;
  }

  const linhas = [
    ['Produto', 'Categoria', 'Quantidade', 'Nivel do estoque'],
    ...produtosVisiveis.map(p => [
      p.nome || '',
      categoriaLabel(p.categoriaEstoque),
      String(p.quantidadeAtual ?? ''),
      nivelLabel(p.nivelEstoque)
    ])
  ];

  const csv = '\uFEFF' + linhas
    .map(colunas => colunas.map(valor => `"${String(valor).replace(/"/g, '""')}"`).join(','))
    .join('\r\n');

  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  const data = new Date().toISOString().slice(0, 10);

  link.href = url;
  link.download = `estoque-${data}.csv`;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

function preencherSelectCategorias(selectId) {
  const select = document.getElementById(selectId);
  if (!select) return;

  select.innerHTML = CATEGORIAS_ESTOQUE.map(c =>
    `<option value="${c.value}">${c.label}</option>`
  ).join('');
}

function filtrar(tipo, btn) {
  filtroNivelAtivo = tipo;
  document.querySelectorAll('[data-filter-group="nivel"] .filter-btn').forEach(b => b.classList.remove('active'));
  btn.classList.add('active');
  renderTabela();
}

function filtrarCategoria(tipo, btn) {
  filtroCategoriaAtiva = tipo;
  document.querySelectorAll('[data-filter-group="categoria"] .filter-btn').forEach(b => b.classList.remove('active'));
  btn.classList.add('active');
  renderTabela();
}

function filtrarProdutos() {
  renderTabela();
}

function abrirModalCadastro() {
  limparFormCadastro();
  document.getElementById('modal-cadastro').classList.add('open');
}

function abrirModalEdicao(idProduto) {
  produtoEmEdicao = todosOsProdutos.find(p => p.id === idProduto);
  if (!produtoEmEdicao) return;

  document.getElementById('e-nome').value = produtoEmEdicao.nome || '';
  document.getElementById('e-qtd-minima').value = produtoEmEdicao.quantidadeMinima;
  document.getElementById('e-preco').value = produtoEmEdicao.precoUnitario;
  document.getElementById('e-categoria').value = produtoEmEdicao.categoriaEstoque || '';
  document.getElementById('alert-edicao').className = 'alert-modal';
  document.getElementById('modal-edicao').classList.add('open');
}

function abrirModalMov(idProduto, tipo) {
  produtoSelecionado = todosOsProdutos.find(p => p.id === idProduto);
  if (!produtoSelecionado) return;

  document.getElementById('mov-produto-nome').textContent = produtoSelecionado.nome;
  document.getElementById('mov-produto-qtd').textContent = `${produtoSelecionado.quantidadeAtual} unidades`;
  document.getElementById('mov-qtd').value = '';
  document.getElementById('alert-mov').className = 'alert-modal';

  setTipo(tipo);
  document.getElementById('modal-mov-titulo').textContent =
    tipo === 'ENTRADA' ? 'Registrar Entrada' : 'Registrar Saida';
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
  document.getElementById('btn-saida').classList.toggle('active', tipo === 'SAIDA');
}

function limparFormCadastro() {
  ['c-nome', 'c-qtd-inicial', 'c-qtd-minima', 'c-preco'].forEach(id => {
    document.getElementById(id).value = '';
  });
  document.getElementById('c-categoria').value = '';
  document.getElementById('alert-cadastro').className = 'alert-modal';
}

function mostrarAlertaModal(el, tipo, msg) {
  el.className = `alert-modal ${tipo}`;
  el.textContent = msg;
}
