let cardapioCategorias = [];
let cardapioItens = [];
let cardapioProdutos = [];
let cardapioItensVisiveis = [];

document.addEventListener('DOMContentLoaded', async function () {
  await recarregarDados();
});

async function recarregarDados() {
  limparAlertaPagina();

  try {
    const [categoriasResp, itensResp, produtosResp] = await Promise.all([
      fetch('/api/cardapio/admin/categorias'),
      fetch('/api/cardapio/admin/itens'),
      fetch('/api/produtos')
    ]);

    if (!categoriasResp.ok || !itensResp.ok || !produtosResp.ok) {
      const erros = await Promise.all([
        categoriasResp.ok ? Promise.resolve('') : safeReadErrorMessage(categoriasResp, 'Erro ao carregar categorias.'),
        itensResp.ok ? Promise.resolve('') : safeReadErrorMessage(itensResp, 'Erro ao carregar itens.'),
        produtosResp.ok ? Promise.resolve('') : safeReadErrorMessage(produtosResp, 'Erro ao carregar produtos.')
      ]);
      throw new Error(erros.filter(Boolean).join(' '));
    }

    cardapioCategorias = await categoriasResp.json();
    cardapioItens = await itensResp.json();
    cardapioProdutos = (await produtosResp.json()).filter(function (produto) {
      return produto && produto.controladoPorEstoque;
    });

    preencherSelectCategorias();
    preencherSelectProdutos();
    renderResumo();
    renderCategorias();
    aplicarFiltrosItens();
  } catch (error) {
    console.error('Erro ao carregar gestao de cardapio:', error);
    mostrarAlertaPagina(error.message || 'Nao foi possivel carregar a gestao de cardapio.', 'warning');
    document.getElementById('tabela-categorias').innerHTML =
      '<tr><td colspan="4" class="empty">Erro ao carregar categorias.</td></tr>';
    document.getElementById('tabela-itens').innerHTML =
      '<tr><td colspan="6" class="empty">Erro ao carregar itens.</td></tr>';
  }
}

function renderResumo() {
  const totalCategorias = cardapioCategorias.length;
  const categoriasAtivas = cardapioCategorias.filter(function (categoria) { return categoria.ativo; }).length;
  const totalItens = cardapioItens.length;
  const itensAtivos = cardapioItens.filter(function (item) { return item.ativo; }).length;
  const itensBloqueados = cardapioItens.filter(function (item) { return !item.disponivel; }).length;

  const cards = [
    {
      label: 'Categorias ativas',
      value: categoriasAtivas + '/' + totalCategorias,
      sub: 'Estrutura visivel para o PDV.'
    },
    {
      label: 'Itens ativos',
      value: itensAtivos + '/' + totalItens,
      sub: 'Itens cadastrados no dominio de venda.'
    },
    {
      label: 'Itens bloqueados',
      value: String(itensBloqueados),
      sub: 'Indisponiveis por pausa manual, categoria ou estoque.'
    }
  ];

  document.getElementById('cardapio-summary').innerHTML = cards.map(function (card) {
    return '<div class="page-summary-card">' +
      '<div class="page-summary-label">' + escapeHtml(card.label) + '</div>' +
      '<div class="page-summary-value">' + escapeHtml(card.value) + '</div>' +
      '<div class="page-summary-sub">' + escapeHtml(card.sub) + '</div>' +
      '</div>';
  }).join('');
}

function renderCategorias() {
  const tbody = document.getElementById('tabela-categorias');
  const countEl = document.getElementById('categorias-count');
  countEl.textContent = cardapioCategorias.length + ' categoria' + (cardapioCategorias.length === 1 ? '' : 's');

  if (!cardapioCategorias.length) {
    tbody.innerHTML = '<tr><td colspan="4" class="table-empty-cell">' +
      emptyStateMarkup({
        icon: 'clipboard',
        title: 'Nenhuma categoria cadastrada',
        copy: 'Crie a primeira categoria para estruturar o cardapio do PDV.',
        actions: '<button class="btn-primary" type="button" onclick="abrirModalCategoria()">Nova Categoria</button>'
      }) +
      '</td></tr>';
    return;
  }

  tbody.innerHTML = cardapioCategorias.map(function (categoria) {
    const badge = categoria.ativo
      ? '<span class="badge badge-green">Ativa</span>'
      : '<span class="badge badge-gray">Inativa</span>';
    const toggleLabel = categoria.ativo ? 'Desativar' : 'Ativar';

    return '<tr>' +
      '<td>' +
        '<div class="ordem-editor">' +
          '<input class="compact-control compact-control--order" id="categoria-ordem-' + categoria.id + '" type="number" min="0" step="1" value="' + categoria.ordemExibicao + '">' +
          '<button class="action-chip action-chip--muted" type="button" onclick="salvarOrdemCategoria(' + categoria.id + ')">Salvar</button>' +
        '</div>' +
      '</td>' +
      '<td>' +
        '<div class="table-cell-stack">' +
          '<span class="table-cell-strong">' + escapeHtml(categoria.nomeExibicao) + '</span>' +
          '<span class="table-cell-caption">' + escapeHtml(categoria.codigo) + '</span>' +
        '</div>' +
      '</td>' +
      '<td>' + badge + '</td>' +
      '<td>' +
        '<div class="actions-grid">' +
          '<button class="action-chip action-chip--muted" type="button" onclick="abrirModalCategoria(' + categoria.id + ')">Editar</button>' +
          '<button class="action-chip ' + (categoria.ativo ? 'action-chip--danger' : 'action-chip--success') + '" type="button" onclick="alternarCategoriaAtiva(' + categoria.id + ', ' + (!categoria.ativo) + ')">' + toggleLabel + '</button>' +
        '</div>' +
      '</td>' +
    '</tr>';
  }).join('');
}

function aplicarFiltrosItens() {
  const busca = normalizarTexto(document.getElementById('item-search').value || '');
  const categoriaId = document.getElementById('item-category-filter').value;

  cardapioItensVisiveis = cardapioItens.filter(function (item) {
    const matchCategoria = !categoriaId || String(item.categoriaId) === String(categoriaId);
    const indiceBusca = normalizarTexto([
      item.nome,
      item.codigo,
      item.descricaoCurta,
      item.categoriaNomeExibicao
    ].filter(Boolean).join(' '));
    const matchBusca = !busca || indiceBusca.includes(busca);
    return matchCategoria && matchBusca;
  });

  renderItens();
}

function renderItens() {
  const tbody = document.getElementById('tabela-itens');
  const countEl = document.getElementById('itens-count');
  countEl.textContent = cardapioItensVisiveis.length + ' item' + (cardapioItensVisiveis.length === 1 ? '' : 's');

  if (!cardapioItensVisiveis.length) {
    if (!cardapioItens.length) {
      tbody.innerHTML = '<tr><td colspan="6" class="table-empty-cell">' +
        emptyStateMarkup({
          icon: 'cart',
          title: 'Nenhum item cadastrado',
          copy: 'Cadastre itens de cardapio para disponibilizar vendas no PDV.',
          actions: '<button class="btn-primary" type="button" onclick="abrirModalItem()">Novo Item</button>'
        }) +
        '</td></tr>';
    } else {
      tbody.innerHTML = '<tr><td colspan="6" class="empty">Nenhum item encontrado para os filtros aplicados.</td></tr>';
    }
    return;
  }

  tbody.innerHTML = cardapioItensVisiveis.map(function (item) {
    const disponibilidade = resolverDisponibilidadeItem(item);
    const statusAtivo = item.ativo
      ? '<span class="badge badge-green">Ativo</span>'
      : '<span class="badge badge-gray">Inativo</span>';
    const statusManual = item.disponivelParaVenda
      ? '<span class="badge badge-cyan">Manual liberado</span>'
      : '<span class="badge badge-amber">Manual pausado</span>';
    const tipoBadge = item.tipoItem === 'PREPARADO_SOB_DEMANDA'
      ? '<span class="badge badge-purple">Sob demanda</span>'
      : '<span class="badge badge-cyan">Estoque direto</span>';
    const tipoMeta = item.tipoItem === 'PREPARADO_SOB_DEMANDA'
      ? 'Sem quantidade pronta em estoque.'
      : (item.produtoVinculadoNome ? 'Vinculado a ' + item.produtoVinculadoNome : 'Sem produto vinculado.');
    const toggleAtivoLabel = item.ativo ? 'Inativar' : 'Ativar';
    const toggleDisponivelLabel = item.disponivelParaVenda ? 'Pausar' : 'Liberar';
    const produtoMeta = item.produtoVinculadoNome
      ? 'Produto: ' + item.produtoVinculadoNome
      : (item.tipoItem === 'ESTOQUE_DIRETO' ? 'Sem produto vinculado' : 'Sem estoque vinculado');

    return '<tr>' +
      '<td>' +
        '<div class="table-cell-stack">' +
          '<span class="table-cell-strong">' + escapeHtml(item.nome) + '</span>' +
          '<span class="table-cell-caption">' + escapeHtml(item.codigo) + ' · R$ ' + formatarNumero(item.precoVenda) + '</span>' +
          '<span class="table-cell-subtle">' + escapeHtml(item.descricaoCurta || produtoMeta) + '</span>' +
          '<div class="status-pill-row">' + statusAtivo + statusManual + '</div>' +
        '</div>' +
      '</td>' +
      '<td>' +
        '<div class="table-cell-stack">' +
          '<span class="table-cell-strong">' + escapeHtml(item.categoriaNomeExibicao) + '</span>' +
          '<span class="table-cell-caption">' + escapeHtml(item.categoriaCodigo) + (item.categoriaAtiva ? '' : ' · inativa') + '</span>' +
        '</div>' +
      '</td>' +
      '<td>' +
        '<div class="table-cell-stack">' +
          tipoBadge +
          '<span class="table-cell-caption">' + escapeHtml(tipoMeta) + '</span>' +
        '</div>' +
      '</td>' +
      '<td>' +
        '<div class="table-cell-stack">' +
          '<span class="badge ' + disponibilidade.badgeClass + '">' + escapeHtml(disponibilidade.label) + '</span>' +
          '<span class="table-cell-caption">' + escapeHtml(disponibilidade.meta) + '</span>' +
        '</div>' +
      '</td>' +
      '<td>' +
        '<div class="ordem-editor">' +
          '<input class="compact-control compact-control--order" id="item-ordem-' + item.id + '" type="number" min="0" step="1" value="' + item.ordemExibicao + '">' +
          '<button class="action-chip action-chip--muted" type="button" onclick="salvarOrdemItem(' + item.id + ')">Salvar</button>' +
        '</div>' +
      '</td>' +
      '<td>' +
        '<div class="actions-grid actions-grid--stack">' +
          '<button class="action-chip action-chip--muted" type="button" onclick="abrirModalItem(' + item.id + ')">Editar</button>' +
          '<button class="action-chip ' + (item.ativo ? 'action-chip--danger' : 'action-chip--success') + '" type="button" onclick="alternarItemAtivo(' + item.id + ', ' + (!item.ativo) + ')">' + toggleAtivoLabel + '</button>' +
          '<button class="action-chip ' + (item.disponivelParaVenda ? 'action-chip--warning' : 'action-chip--success') + '" type="button" onclick="alternarDisponibilidadeItem(' + item.id + ', ' + (!item.disponivelParaVenda) + ')">' + toggleDisponivelLabel + '</button>' +
        '</div>' +
      '</td>' +
    '</tr>';
  }).join('');
}

function resolverDisponibilidadeItem(item) {
  const manualMeta = item.disponivelParaVenda ? 'Manual liberado.' : 'Manual pausado.';
  if (!item.ativo) {
    return { badgeClass: 'badge-gray', label: 'Inativo', meta: manualMeta + ' Nao listado no PDV.' };
  }
  if (!item.categoriaAtiva) {
    return { badgeClass: 'badge-gray', label: 'Bloqueado', meta: manualMeta + ' Categoria inativa.' };
  }
  if (!item.disponivelParaVenda) {
    return { badgeClass: 'badge-amber', label: 'Pausa manual', meta: manualMeta + ' Disponibilidade desligada no admin.' };
  }
  if (item.tipoItem === 'ESTOQUE_DIRETO' && item.controladoPorEstoque) {
    if (typeof item.quantidadeAtual === 'number' && item.quantidadeAtual <= 0) {
      return { badgeClass: 'badge-red', label: 'Sem estoque', meta: manualMeta + ' Produto vinculado esgotado.' };
    }
    return {
      badgeClass: 'badge-green',
      label: 'Disponivel',
      meta: manualMeta + ' ' + (typeof item.quantidadeAtual === 'number' ? item.quantidadeAtual + ' un. em estoque.' : 'Produto vinculado ao estoque.')
    };
  }
  if (item.tipoItem === 'ESTOQUE_DIRETO') {
    return { badgeClass: 'badge-cyan', label: 'Disponivel', meta: manualMeta + ' Controle sem produto vinculado.' };
  }
  return { badgeClass: 'badge-green', label: 'Disponivel', meta: manualMeta + ' Preparo sob demanda.' };
}

function preencherSelectCategorias() {
  const filtro = document.getElementById('item-category-filter');
  const campoCategoria = document.getElementById('item-categoria');
  const valorSelecionado = filtro.value;
  const opcoes = cardapioCategorias.map(function (categoria) {
    const complemento = categoria.ativo ? '' : ' (inativa)';
    return '<option value="' + categoria.id + '">' + escapeHtml(categoria.nomeExibicao + complemento) + '</option>';
  }).join('');

  filtro.innerHTML = '<option value="">Todas as categorias</option>' + opcoes;
  campoCategoria.innerHTML = opcoes;

  if (cardapioCategorias.some(function (categoria) { return String(categoria.id) === valorSelecionado; })) {
    filtro.value = valorSelecionado;
  }
}

function preencherSelectProdutos() {
  const campoProduto = document.getElementById('item-produto');
  const opcoesProdutos = cardapioProdutos
    .slice()
    .sort(function (a, b) { return String(a.nome || '').localeCompare(String(b.nome || ''), 'pt-BR'); })
    .map(function (produto) {
      return '<option value="' + produto.id + '">' + escapeHtml(produto.nome) + '</option>';
    }).join('');

  campoProduto.innerHTML = '<option value="">Sem produto vinculado</option>' + opcoesProdutos;
}

function abrirModalCategoria(categoriaId) {
  limparAlertaModal('alert-categoria');
  document.getElementById('categoria-id').value = '';
  document.getElementById('categoria-codigo').value = '';
  document.getElementById('categoria-nome').value = '';
  document.getElementById('categoria-ordem').value = '';

  if (categoriaId) {
    const categoria = cardapioCategorias.find(function (item) { return item.id === categoriaId; });
    if (!categoria) return;

    document.getElementById('modal-categoria-titulo').textContent = 'Editar Categoria';
    document.getElementById('categoria-id').value = categoria.id;
    document.getElementById('categoria-codigo').value = categoria.codigo || '';
    document.getElementById('categoria-nome').value = categoria.nomeExibicao || '';
    document.getElementById('categoria-ordem').value = categoria.ordemExibicao;
  } else {
    document.getElementById('modal-categoria-titulo').textContent = 'Nova Categoria';
  }

  document.getElementById('modal-categoria').classList.add('open');
}

async function salvarCategoria() {
  const id = document.getElementById('categoria-id').value;
  const nomeExibicao = document.getElementById('categoria-nome').value.trim();
  const codigo = document.getElementById('categoria-codigo').value.trim();
  const ordemExibicao = parseInt(document.getElementById('categoria-ordem').value, 10);
  const alertEl = document.getElementById('alert-categoria');
  const btn = document.getElementById('btn-salvar-categoria');

  if (!nomeExibicao) {
    mostrarAlertaModal(alertEl, 'error', 'Nome da categoria e obrigatorio.');
    return;
  }
  if (Number.isNaN(ordemExibicao) || ordemExibicao < 0) {
    mostrarAlertaModal(alertEl, 'error', 'Ordem de exibicao invalida.');
    return;
  }

  setLoading(btn, true);
  limparAlertaModal('alert-categoria');

  try {
    const payload = { codigo, nomeExibicao, ordemExibicao };
    const response = await fetch(id ? '/api/cardapio/admin/categorias/' + id : '/api/cardapio/admin/categorias', {
      method: id ? 'PUT' : 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });

    if (!response.ok) {
      throw new Error(await safeReadErrorMessage(response, 'Erro ao salvar categoria.'));
    }

    showToast(id ? 'Categoria atualizada.' : 'Categoria criada.', 'success');
    fecharModal('modal-categoria');
    await recarregarDados();
  } catch (error) {
    mostrarAlertaModal(alertEl, 'error', error.message || 'Erro ao salvar categoria.');
  } finally {
    setLoading(btn, false);
  }
}

function abrirModalItem(itemId) {
  if (!itemId && !cardapioCategorias.length) {
    showToast('Cadastre ao menos uma categoria antes de criar itens.', 'error');
    return;
  }

  limparAlertaModal('alert-item');
  document.getElementById('item-id').value = '';
  document.getElementById('item-codigo').value = '';
  document.getElementById('item-nome').value = '';
  document.getElementById('item-descricao').value = '';
  document.getElementById('item-preco').value = '';
  document.getElementById('item-ordem').value = '';
  document.getElementById('item-tipo').value = 'PREPARADO_SOB_DEMANDA';
  if (cardapioCategorias.length) {
    document.getElementById('item-categoria').value = String(cardapioCategorias[0].id);
  }
  document.getElementById('item-produto').value = '';

  if (itemId) {
    const item = cardapioItens.find(function (entry) { return entry.id === itemId; });
    if (!item) return;

    document.getElementById('modal-item-titulo').textContent = 'Editar Item';
    document.getElementById('item-id').value = item.id;
    document.getElementById('item-codigo').value = item.codigo || '';
    document.getElementById('item-nome').value = item.nome || '';
    document.getElementById('item-descricao').value = item.descricaoCurta || '';
    document.getElementById('item-preco').value = Number(item.precoVenda || 0).toFixed(2);
    document.getElementById('item-ordem').value = item.ordemExibicao;
    document.getElementById('item-tipo').value = item.tipoItem;
    document.getElementById('item-categoria').value = String(item.categoriaId);
    document.getElementById('item-produto').value = item.produtoVinculadoId ? String(item.produtoVinculadoId) : '';
  } else {
    document.getElementById('modal-item-titulo').textContent = 'Novo Item';
  }

  aplicarRegrasTipoItem();
  document.getElementById('modal-item').classList.add('open');
}

function aplicarRegrasTipoItem() {
  const tipo = document.getElementById('item-tipo').value;
  const campoProduto = document.getElementById('item-produto');
  const hint = document.getElementById('item-produto-hint');

  if (tipo === 'PREPARADO_SOB_DEMANDA') {
    campoProduto.value = '';
    campoProduto.disabled = true;
    hint.textContent = 'Itens sob demanda nao exigem produto vinculado nem quantidade pronta em estoque.';
    return;
  }

  campoProduto.disabled = false;
  hint.textContent = cardapioProdutos.length
    ? 'Itens de estoque podem permanecer em controle manual ou apontar para um produto do estoque.'
    : 'Nenhum produto controlado por estoque esta disponivel para vinculo no momento.';
}

async function salvarItem() {
  const id = document.getElementById('item-id').value;
  const nome = document.getElementById('item-nome').value.trim();
  const codigo = document.getElementById('item-codigo').value.trim();
  const descricaoCurta = document.getElementById('item-descricao').value.trim();
  const precoVenda = parseFloat(document.getElementById('item-preco').value);
  const categoriaId = parseInt(document.getElementById('item-categoria').value, 10);
  const tipoItem = document.getElementById('item-tipo').value;
  const ordemExibicao = parseInt(document.getElementById('item-ordem').value, 10);
  const produtoVinculadoId = document.getElementById('item-produto').value
    ? parseInt(document.getElementById('item-produto').value, 10)
    : null;
  const alertEl = document.getElementById('alert-item');
  const btn = document.getElementById('btn-salvar-item');

  if (!nome) {
    mostrarAlertaModal(alertEl, 'error', 'Nome do item e obrigatorio.');
    return;
  }
  if (Number.isNaN(categoriaId) || categoriaId <= 0) {
    mostrarAlertaModal(alertEl, 'error', 'Selecione uma categoria valida.');
    return;
  }
  if (!tipoItem) {
    mostrarAlertaModal(alertEl, 'error', 'Selecione um tipo de item.');
    return;
  }
  if (Number.isNaN(precoVenda) || precoVenda < 0) {
    mostrarAlertaModal(alertEl, 'error', 'Preco de venda invalido.');
    return;
  }
  if (Number.isNaN(ordemExibicao) || ordemExibicao < 0) {
    mostrarAlertaModal(alertEl, 'error', 'Ordem de exibicao invalida.');
    return;
  }

  setLoading(btn, true);
  limparAlertaModal('alert-item');

  try {
    const payload = {
      codigo,
      nome,
      descricaoCurta: descricaoCurta || null,
      precoVenda,
      categoriaId,
      tipoItem,
      ordemExibicao,
      produtoVinculadoId: tipoItem === 'PREPARADO_SOB_DEMANDA' ? null : produtoVinculadoId
    };

    const response = await fetch(id ? '/api/cardapio/admin/itens/' + id : '/api/cardapio/admin/itens', {
      method: id ? 'PUT' : 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });

    if (!response.ok) {
      throw new Error(await safeReadErrorMessage(response, 'Erro ao salvar item.'));
    }

    showToast(id ? 'Item atualizado.' : 'Item criado.', 'success');
    fecharModal('modal-item');
    await recarregarDados();
  } catch (error) {
    mostrarAlertaModal(alertEl, 'error', error.message || 'Erro ao salvar item.');
  } finally {
    setLoading(btn, false);
  }
}

async function alternarCategoriaAtiva(id, ativo) {
  await atualizarToggle('/api/cardapio/admin/categorias/' + id + '/ativo', { ativo: ativo }, ativo ? 'Categoria ativada.' : 'Categoria desativada.');
}

async function alternarItemAtivo(id, ativo) {
  await atualizarToggle('/api/cardapio/admin/itens/' + id + '/ativo', { ativo: ativo }, ativo ? 'Item ativado.' : 'Item inativado.');
}

async function alternarDisponibilidadeItem(id, disponivelParaVenda) {
  await atualizarToggle(
    '/api/cardapio/admin/itens/' + id + '/disponibilidade',
    { disponivelParaVenda: disponivelParaVenda },
    disponivelParaVenda ? 'Item liberado para venda.' : 'Item pausado manualmente.'
  );
}

async function salvarOrdemCategoria(id) {
  const valor = parseInt(document.getElementById('categoria-ordem-' + id).value, 10);
  if (Number.isNaN(valor) || valor < 0) {
    showToast('Ordem da categoria invalida.', 'error');
    return;
  }
  await atualizarToggle('/api/cardapio/admin/categorias/' + id + '/ordem', { ordemExibicao: valor }, 'Ordem da categoria atualizada.');
}

async function salvarOrdemItem(id) {
  const valor = parseInt(document.getElementById('item-ordem-' + id).value, 10);
  if (Number.isNaN(valor) || valor < 0) {
    showToast('Ordem do item invalida.', 'error');
    return;
  }
  await atualizarToggle('/api/cardapio/admin/itens/' + id + '/ordem', { ordemExibicao: valor }, 'Ordem do item atualizada.');
}

async function atualizarToggle(url, payload, successMessage) {
  try {
    const response = await fetch(url, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });

    if (!response.ok) {
      throw new Error(await safeReadErrorMessage(response, 'Erro ao atualizar registro.'));
    }

    showToast(successMessage, 'success');
    await recarregarDados();
  } catch (error) {
    showToast(error.message || 'Erro ao atualizar registro.', 'error');
  }
}

function fecharModal(id) {
  document.getElementById(id).classList.remove('open');
}

function fecharModalSeClicouFora(event, id) {
  if (event.target.id === id) {
    fecharModal(id);
  }
}

function mostrarAlertaModal(elemento, tipo, mensagem) {
  if (!elemento) return;
  elemento.className = 'alert-modal ' + tipo + ' alert-modal--spaced-top';
  elemento.textContent = mensagem;
}

function limparAlertaModal(id) {
  const elemento = document.getElementById(id);
  if (!elemento) return;
  elemento.className = 'alert-modal alert-modal--spaced-top';
  elemento.textContent = '';
}

function mostrarAlertaPagina(mensagem, tipo) {
  const container = document.getElementById('cardapio-page-alert');
  if (!container) return;
  const classes = tipo === 'warning' ? 'alert-inline' : 'alert-inline';
  container.innerHTML = '<div class="' + classes + '">' + escapeHtml(mensagem) + '</div>';
}

function limparAlertaPagina() {
  const container = document.getElementById('cardapio-page-alert');
  if (container) container.innerHTML = '';
}

function formatarNumero(valor) {
  return Number(valor || 0).toLocaleString('pt-BR', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  });
}

function normalizarTexto(valor) {
  return String(valor || '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .trim();
}
