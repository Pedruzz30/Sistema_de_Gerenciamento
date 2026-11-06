/* SPA-isolated screen: estoque. Prevent cross-page handler overwrites. */
(() => {
'use strict';
// Stock screen: listing, category filtering, product create/edit, and movements.

let todosOsProdutos = [];
let produtosVisiveis = [];
const PRODUTOS_POR_PAGINA = 60;
let paginaEstoque = 1;
let filtroNivelAtivo = 'todos';
let filtroCategoriaAtiva = 'todas';
let produtoSelecionado = null;
let produtoEmEdicao = null;
let tipoMovAtual = 'ENTRADA';

const CATEGORIAS_ESTOQUE = [
  { value: '', label: 'Não definida' },
  { value: 'BEBIDAS', label: 'Bebidas' },
  { value: 'SECO', label: 'Estoque seco' },
  { value: 'FRIO', label: 'Estoque frio' },
  { value: 'CONGELADO', label: 'Estoque congelado' }
];

async function iniciarEstoqueSPA() {
  if (paginaAtual() !== 'estoque') return;
  if (!temPermissao(PERMISSOES.EDITAR_ESTOQUE)) {
    const btn = document.getElementById('btn-novo-produto');
    if (btn) btn.hidden = true;
    const btnRevenda = document.getElementById('btn-revenda');
    if (btnRevenda) btnRevenda.hidden = true;
  }

  preencherSelectCategorias('c-categoria');
  preencherSelectCategorias('e-categoria');
  await carregarProdutos();
}

window.StockOSPages = window.StockOSPages || {};
window.StockOSPages.estoque = {
  mount: iniciarEstoqueSPA,
  activate() {
    Object.assign(window, {
      carregarProdutos,
      cadastrarProduto,
      abrirModalRevenda,
      salvarRevenda,
      teclaRevenda,
      atualizarProduto,
      confirmarMovimentacao,
      renderTabela,
      nivelLabel,
      getProdutosVisiveis,
      categoriaLabel,
      renderCategoriaBadge,
      calcularMetricasCategorias,
      renderResumoCategorias,
      renderGraficoCategorias,
      atualizarEstadoExportacao,
      exportarCsvProdutos,
      preencherSelectCategorias,
      filtrar,
      filtrarCategoria,
      filtrarProdutos,
      abrirModalCadastro,
      abrirModalEdicao,
      abrirModalMov,
      fecharModal,
      fecharModalSeClicouFora,
      setTipo,
      limparFormCadastro,
      mostrarAlertaModal,
    });
  }
};
document.addEventListener('DOMContentLoaded', iniciarEstoqueSPA);

async function carregarProdutos() {
  try {
    const res = await fetch('/api/produtos');
    if (!res.ok) throw new Error('Falha ao carregar produtos');
    const produtos = await res.json();
    if (paginaAtual() !== 'estoque') return;
    todosOsProdutos = produtos;
    renderTabela();
  } catch (e) {
    if (paginaAtual() !== 'estoque') return;
    document.getElementById('tabela-produtos').innerHTML =
      '<tr><td colspan="7" class="empty">Erro ao carregar produtos. Verifique a conexão.</td></tr>';
  }
}

async function cadastrarProduto() {
  const nome = document.getElementById('c-nome').value.trim();
  const qtdInicial = parseInt(document.getElementById('c-qtd-inicial').value, 10);
  const qtdMinima = parseInt(document.getElementById('c-qtd-minima').value, 10);
  const preco = parseFloat(document.getElementById('c-preco').value);
  const categoriaEstoque = document.getElementById('c-categoria').value || null;
  const alertEl = document.getElementById('alert-cadastro');

  if (!nome) return mostrarAlertaModal(alertEl, 'error', 'Nome do produto é obrigatório.');
  if (isNaN(qtdInicial) || qtdInicial < 0) return mostrarAlertaModal(alertEl, 'error', 'Quantidade inicial inválida.');
  if (isNaN(qtdMinima) || qtdMinima < 0) return mostrarAlertaModal(alertEl, 'error', 'Quantidade mínima inválida.');
  if (isNaN(preco) || preco < 0) return mostrarAlertaModal(alertEl, 'error', 'Preço inválido.');

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

  if (!nome) return mostrarAlertaModal(alertEl, 'error', 'Nome do produto é obrigatório.');
  if (isNaN(qtdMinima) || qtdMinima < 0) return mostrarAlertaModal(alertEl, 'error', 'Quantidade mínima inválida.');
  if (isNaN(preco) || preco < 0) return mostrarAlertaModal(alertEl, 'error', 'Preço inválido.');

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
        `Estoque insuficiente. Disponível: ${produtoSelecionado.quantidadeAtual} unidades.`
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
      const err = await safeReadErrorMessage(res, 'Erro ao registrar movimentação.');
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
  const oldNav = document.getElementById('paginacao-estoque');
  if (oldNav) oldNav.remove();
  const tbody = document.getElementById('tabela-produtos');
  const countEl = document.getElementById('table-count');
  const podeEditar = temPermissao(PERMISSOES.EDITAR_ESTOQUE);
  const produtos = getProdutosVisiveis();
  produtosVisiveis = produtos;
  const paginas = Math.max(1, Math.ceil(produtos.length / PRODUTOS_POR_PAGINA));
  paginaEstoque = Math.min(Math.max(1, paginaEstoque), paginas);
  const inicio = (paginaEstoque - 1) * PRODUTOS_POR_PAGINA;
  const itensPagina = produtos.slice(inicio, inicio + PRODUTOS_POR_PAGINA);

  countEl.textContent = `${produtos.length} produto${produtos.length !== 1 ? 's' : ''}`;
  atualizarEstadoExportacao();
  renderResumoCategorias(produtos);
  renderGraficoCategorias(produtos);

  if (produtos.length === 0) {
    if (todosOsProdutos.length === 0) {
      tbody.innerHTML = `
        <tr><td colspan="7" class="table-empty-cell">
          ${emptyStateMarkup({
            icon: 'box',
            title: 'Nenhum produto cadastrado',
            copy: 'Cadastre produtos para começar a gerenciar o estoque.',
            actions: podeEditar ? '<button class="btn-primary" onclick="abrirModalCadastro()">Novo Produto</button>' : ''
          })}
        </td></tr>`;
    } else {
      tbody.innerHTML = '<tr><td colspan="7" class="empty">Nenhum produto encontrado para os filtros aplicados.</td></tr>';
    }
    return;
  }

  tbody.innerHTML = itensPagina.map(p => `
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
            ${podeEditar ? `<button class="btn-acao saida" onclick="abrirModalMov(${p.id}, 'SAIDA')">Saída</button>` : ''}
            ${!podeEditar ? '<span class="status-placeholder">Sem permissão</span>' : ''}
          </div>
        </td>
      </tr>
  `).join('');
  if (paginas > 1) {
    const nav = document.createElement('div');
    nav.id = 'paginacao-estoque';
    nav.className = 'table-pagination';
    nav.style.cssText = 'display:flex;justify-content:center;align-items:center;gap:16px;padding:16px';
    const anterior = document.createElement('button');
    anterior.type = 'button'; anterior.textContent = 'Anterior'; anterior.disabled = paginaEstoque <= 1;
    anterior.addEventListener('click', () => { paginaEstoque--; renderTabela(); });
    const info = document.createElement('span');
    info.textContent = 'Página ' + paginaEstoque + ' de ' + paginas;
    const proximo = document.createElement('button');
    proximo.type = 'button'; proximo.textContent = 'Próxima'; proximo.disabled = paginaEstoque >= paginas;
    proximo.addEventListener('click', () => { paginaEstoque++; renderTabela(); });
    nav.append(anterior, info, proximo);
    tbody.closest('table').insertAdjacentElement('afterend', nav);
  }
}

function nivelLabel(nivel) {
  const labels = {
    SEM_ESTOQUE: 'Sem Estoque',
    CRITICO: 'Crítico',
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
      <div class="categoria-summary-sub">produto(s) visíveis</div>
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
    const largura = ((valor / maximo) * 100).toFixed(2);
    return `
      <div class="categoria-chart-row">
        <div class="categoria-chart-label">${cat.label}</div>
        <div class="categoria-chart-bar">
          <div class="categoria-chart-fill categoria-${cat.key}" data-width="${largura}"></div>
        </div>
        <div class="categoria-chart-value">${valor}</div>
      </div>
    `;
  }).join('');

  container.querySelectorAll('.categoria-chart-fill').forEach((item) => {
    item.style.width = `${item.dataset.width}%`;
  });
}

function atualizarEstadoExportacao() {
  const btn = document.getElementById('btn-exportar-csv');
  if (!btn) return;
  btn.disabled = produtosVisiveis.length === 0;
}

function exportarCsvProdutos() {
  if (!produtosVisiveis.length) {
    showToast('Não há linhas visíveis para exportar.', 'info');
    return;
  }

  const linhas = [
    ['Produto', 'Categoria', 'Quantidade', 'Nível do estoque'],
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
  paginaEstoque = 1;
  filtroNivelAtivo = tipo;
  document.querySelectorAll('[data-filter-group="nivel"] .filter-btn').forEach(b => b.classList.remove('active'));
  btn.classList.add('active');
  renderTabela();
}

function filtrarCategoria(tipo, btn) {
  paginaEstoque = 1;
  filtroCategoriaAtiva = tipo;
  document.querySelectorAll('[data-filter-group="categoria"] .filter-btn').forEach(b => b.classList.remove('active'));
  btn.classList.add('active');
  renderTabela();
}

function filtrarProdutos() {
  paginaEstoque = 1;
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
    tipo === 'ENTRADA' ? 'Registrar Entrada' : 'Registrar Saída';
  document.getElementById('modal-mov').classList.add('open');
}

function fecharModal(id) {
  const modal = document.getElementById(id);
  if (modal) modal.classList.remove('open');
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
// ── Bebida rápida: cadastro com leitor de código de barras ────────────────
// O leitor digita o código e aperta Enter, então Enter aqui passa para o
// próximo campo e nunca salva sozinho. Ctrl+Enter salva.

const REVENDA_ORDEM = ['r-ean', 'r-nome', 'r-preco-gelado', 'r-preco', 'r-custo', 'r-qtd', 'r-min',
  'r-fardo-qtd', 'r-fardo-preco', 'r-fardo-ean'];
const REVENDA_CATEGORIAS_PADRAO = [
  { codigo: 'bebidas', nome: 'Bebidas' },
  { codigo: 'petiscos', nome: 'Petiscos' }
];
const REVENDA_MOEDA = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
let revendaSalvando = false;

async function abrirModalRevenda() {
  limparFormRevenda();
  document.getElementById('alert-revenda').className = 'alert-modal';
  document.getElementById('modal-revenda').classList.add('open');
  document.getElementById('r-ean').focus();
  await carregarCategoriasRevenda();
}

async function carregarCategoriasRevenda() {
  const select = document.getElementById('r-categoria');
  const selecionada = select.value || 'bebidas';
  const categorias = [...REVENDA_CATEGORIAS_PADRAO];
  try {
    const resp = await fetch('/api/cardapio/categorias');
    if (resp.ok) {
      for (const categoria of await resp.json()) {
        const codigo = String(categoria.codigo || '').toLowerCase();
        if (codigo && !categorias.some(c => c.codigo === codigo)) {
          categorias.push({ codigo, nome: categoria.nomeExibicao || categoria.codigo });
        }
      }
    }
  } catch (_) { /* segue com as categorias padrão */ }
  select.replaceChildren(...categorias.map(categoria => {
    const option = document.createElement('option');
    option.value = categoria.codigo;
    option.textContent = categoria.nome;
    return option;
  }));
  select.value = categorias.some(c => c.codigo === selecionada) ? selecionada : 'bebidas';
}

function limparFormRevenda() {
  REVENDA_ORDEM.forEach(id => { document.getElementById(id).value = ''; });
  statusCodigoRevenda('r-ean', 'Sem código? Deixe vazio ou digite um código curto, como 101.');
  statusCodigoRevenda('r-fardo-ean', '');
}

function statusCodigoRevenda(campoId, texto, erro) {
  const el = document.getElementById(campoId + '-status');
  if (!el) return;
  el.textContent = texto;
  el.classList.toggle('revenda-status--erro', Boolean(erro));
}

function normalizarCodigoRevenda(valor) {
  return String(valor || '').replace(/\s+/g, '');
}

/** Dígito verificador GS1 (EAN-8, UPC-A, EAN-13, GTIN-14). Códigos internos passam. */
function codigoBarrasConfere(codigo) {
  if (!/^\d+$/.test(codigo) || ![8, 12, 13, 14].includes(codigo.length)) return true;
  let soma = 0;
  let peso = 3;
  for (let i = codigo.length - 2; i >= 0; i--) {
    soma += Number(codigo[i]) * peso;
    peso = peso === 3 ? 1 : 3;
  }
  return (10 - (soma % 10)) % 10 === Number(codigo[codigo.length - 1]);
}

/** Confere o código lido. Retorna true se pode seguir para o próximo campo. */
async function conferirCodigoRevenda(campoId) {
  const campo = document.getElementById(campoId);
  const codigo = normalizarCodigoRevenda(campo.value);
  campo.value = codigo;
  if (!codigo) return true;
  if (!codigoBarrasConfere(codigo)) {
    statusCodigoRevenda(campoId, 'Código inválido: o dígito verificador não confere. Bipe de novo.', true);
    campo.select();
    return false;
  }
  const outroId = campoId === 'r-ean' ? 'r-fardo-ean' : 'r-ean';
  if (codigo === normalizarCodigoRevenda(document.getElementById(outroId).value)) {
    statusCodigoRevenda(campoId, 'A unidade e o fardo precisam ter códigos diferentes.', true);
    campo.select();
    return false;
  }
  try {
    const resp = await fetch('/api/pdv/codigos/' + encodeURIComponent(codigo));
    if (resp.ok) {
      const leitura = await resp.json();
      const dono = leitura.variacao ? `${leitura.variacao.itemNome} (${leitura.variacao.variacaoNome})` : 'outro produto';
      statusCodigoRevenda(campoId, `Este código já é de ${dono}.`, true);
      campo.select();
      return false;
    }
  } catch (_) { /* sem conexão: o servidor confere ao salvar */ }
  statusCodigoRevenda(campoId, 'Código lido.');
  return true;
}

async function teclaRevenda(event) {
  if (event.key !== 'Enter' || event.target.tagName === 'BUTTON') return;
  event.preventDefault();
  if (event.ctrlKey) { salvarRevenda(); return; }
  const id = event.target.id;
  if ((id === 'r-ean' || id === 'r-fardo-ean') && !(await conferirCodigoRevenda(id))) return;
  const proximo = REVENDA_ORDEM[REVENDA_ORDEM.indexOf(id) + 1];
  if (proximo) document.getElementById(proximo).focus();
}

function numeroRevenda(id) {
  const texto = document.getElementById(id).value.trim().replace(',', '.');
  return texto === '' ? null : Number(texto);
}

function montarCadastroRevenda() {
  const nome = document.getElementById('r-nome').value.trim();
  const precoVenda = numeroRevenda('r-preco');
  const precoGelado = numeroRevenda('r-preco-gelado');
  const custoUnitario = numeroRevenda('r-custo');
  const fardoQtd = numeroRevenda('r-fardo-qtd');
  const fardoPreco = numeroRevenda('r-fardo-preco');
  const fardoCodigo = normalizarCodigoRevenda(document.getElementById('r-fardo-ean').value);
  const select = document.getElementById('r-categoria');

  if (!nome) return { erro: 'Informe o nome do produto.', campo: 'r-nome' };
  if (!(precoVenda > 0)) return { erro: 'Informe o preço de venda.', campo: 'r-preco' };
  if (precoGelado !== null && !(precoGelado > 0)) return { erro: 'Preço da gelada inválido.', campo: 'r-preco-gelado' };
  if (!(custoUnitario > 0)) return { erro: 'Informe o custo da unidade: o caixa não vende produto sem custo.', campo: 'r-custo' };
  const temFardo = fardoQtd !== null || fardoPreco !== null || fardoCodigo !== '';
  if (temFardo && !(Number.isInteger(fardoQtd) && fardoQtd >= 2)) return { erro: 'Informe quantas unidades vêm no fardo.', campo: 'r-fardo-qtd' };
  if (temFardo && !(fardoPreco > 0)) return { erro: 'Informe o preço do fardo.', campo: 'r-fardo-preco' };

  const codigo = select.value || 'bebidas';
  return {
    dados: {
      nome,
      categoriaCardapioCodigo: codigo,
      categoriaCardapioNome: select.options[select.selectedIndex]?.textContent || null,
      categoriaEstoque: codigo === 'bebidas' ? 'BEBIDAS' : 'SECO',
      codigoBarras: normalizarCodigoRevenda(document.getElementById('r-ean').value),
      precoVenda,
      precoGelado,
      custoUnitario,
      quantidadeInicial: Math.max(0, Math.trunc(numeroRevenda('r-qtd') || 0)),
      quantidadeMinima: Math.max(0, Math.trunc(numeroRevenda('r-min') || 0)),
      fardo: temFardo ? { quantidadeUnidades: fardoQtd, preco: fardoPreco, codigoBarras: fardoCodigo } : null
    }
  };
}

async function salvarRevenda() {
  if (revendaSalvando) return;
  const alerta = document.getElementById('alert-revenda');
  const montagem = montarCadastroRevenda();
  if (montagem.erro) {
    mostrarAlertaModal(alerta, 'error', montagem.erro);
    document.getElementById(montagem.campo).focus();
    return;
  }
  if (!(await conferirCodigoRevenda('r-ean'))) return;
  if (!(await conferirCodigoRevenda('r-fardo-ean'))) return;

  revendaSalvando = true;
  document.getElementById('btn-salvar-revenda').disabled = true;
  try {
    const resp = await fetch('/api/produtos/revenda', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(montagem.dados)
    });
    if (!resp.ok) throw new Error(await safeReadErrorMessage(resp, 'Erro ao cadastrar o produto.'));
    const criado = await resp.json();
    const resumo = (criado.variacoes || [])
      .map(v => `${v.nome} ${REVENDA_MOEDA.format(Number(v.precoVenda))}`)
      .join(' · ');
    mostrarAlertaModal(alerta, 'success', `${criado.nome} cadastrado: ${resumo}. Pode bipar o próximo.`);
    limparFormRevenda();
    await carregarProdutos();
    document.getElementById('r-ean').focus();
  } catch (e) {
    mostrarAlertaModal(alerta, 'error', e.message);
  } finally {
    revendaSalvando = false;
    document.getElementById('btn-salvar-revenda').disabled = false;
  }
}

// Expose inline HTML handlers for direct navigation as well as SPA mounts.
if (/^\/estoque(?:\.html)?\/?$/.test(location.pathname)) {
  window.StockOSPages.estoque.activate();
}
})();
