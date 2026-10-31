/* SPA-isolated screen: cardapio. Retains direct HTML navigation. */
(() => {
'use strict';
let cardapioCategorias = [];
let cardapioItens = [];
let cardapioProdutos = [];
let cardapioItensVisiveis = [];
let cardapioDisponibilidadePorItem = {};
let cardapioGruposModificadores = [];
let cardapioVariacoes = [];
let cardapioUnidades = [];
let cardapioOpcoesPorGrupo = {};
let cardapioComponentesPorOpcao = {};

async function stockOSMount_cardapio() {
  if (typeof paginaAtual === 'function' && paginaAtual() !== 'cardapio') return;
  await recarregarDados();
}

async function recarregarDados() {
  limparAlertaPagina();

  try {
    const contexto = contextoOperacionalCardapioAdmin();
    const [categoriasResp, itensResp, produtosResp, gruposResp, unidadesResp, disponibilidadeResp] = await Promise.all([
      fetch('/api/cardapio/admin/categorias'),
      fetch('/api/cardapio/admin/itens'),
      fetch('/api/produtos'),
      fetch('/api/cardapio/grupos-modificadores'),
      fetch('/api/unidades'),
      fetch(urlDisponibilidadeCardapioAdmin(contexto))
    ]);

    if (!categoriasResp.ok || !itensResp.ok || !produtosResp.ok || !gruposResp.ok || !unidadesResp.ok) {
      const erros = await Promise.all([
        categoriasResp.ok ? Promise.resolve('') : safeReadErrorMessage(categoriasResp, 'Erro ao carregar categorias.'),
        itensResp.ok ? Promise.resolve('') : safeReadErrorMessage(itensResp, 'Erro ao carregar itens.'),
        produtosResp.ok ? Promise.resolve('') : safeReadErrorMessage(produtosResp, 'Erro ao carregar produtos.'),
        gruposResp.ok ? Promise.resolve('') : safeReadErrorMessage(gruposResp, 'Erro ao carregar modificadores.'),
        unidadesResp.ok ? Promise.resolve('') : safeReadErrorMessage(unidadesResp, 'Erro ao carregar unidades.')
      ]);
      throw new Error(erros.filter(Boolean).join(' '));
    }

    cardapioCategorias = await categoriasResp.json();
    cardapioItens = await itensResp.json();
    cardapioDisponibilidadePorItem = disponibilidadeResp.ok
      ? normalizarDisponibilidadeCardapio(await disponibilidadeResp.json())
      : {};
    cardapioItens.forEach(function (item) {
      item.disponibilidadeOperacional = cardapioDisponibilidadePorItem[item.id] || null;
    });
    cardapioProdutos = (await produtosResp.json()).filter(function (produto) {
      return produto && produto.controladoPorEstoque;
    });
    cardapioGruposModificadores = await gruposResp.json();
    cardapioUnidades = await unidadesResp.json();
    cardapioVariacoes = await carregarVariacoesCardapioAdmin();

    preencherSelectCategorias();
    preencherSelectProdutos();
    preencherSelectsModificadores();
    renderResumo();
    renderCategorias();
    renderModificadores();
    aplicarFiltrosItens();
  } catch (error) {
    console.error('Erro ao carregar gestao de cardapio:', error);
    mostrarAlertaPagina(error.message || 'Nao foi possivel carregar a gestao de cardapio.', 'warning');
    document.getElementById('tabela-categorias').innerHTML =
      '<tr><td colspan="4" class="empty">Erro ao carregar categorias.</td></tr>';
    document.getElementById('tabela-itens').innerHTML =
      '<tr><td colspan="6" class="empty">Erro ao carregar itens.</td></tr>';
    const tabelaMod = document.getElementById('tabela-modificadores');
    if (tabelaMod) tabelaMod.innerHTML = '<tr><td colspan="4" class="empty">Erro ao carregar modificadores.</td></tr>';
  }
}

async function carregarVariacoesCardapioAdmin() {
  const listas = await Promise.all(cardapioItens.map(function (item) {
    return fetch('/api/cardapio/itens/' + item.id + '/variacoes')
      .then(function (resp) { return resp.ok ? resp.json() : []; })
      .catch(function () { return []; });
  }));
  return listas.flat();
}

function renderResumo() {
  const totalCategorias = cardapioCategorias.length;
  const categoriasAtivas = cardapioCategorias.filter(function (categoria) { return categoria.ativo; }).length;
  const totalItens = cardapioItens.length;
  const itensAtivos = cardapioItens.filter(function (item) { return item.ativo; }).length;
  const itensBloqueados = cardapioItens.filter(function (item) {
    const operacional = item.disponibilidadeOperacional;
    return !item.disponivel || (operacional && !operacional.resumo.itemDisponivel);
  }).length;

  const cards = [
    {
      label: 'Categorias ativas',
      value: categoriasAtivas + '/' + totalCategorias,
      sub: 'Estrutura visivel para o PDV.',
      icon: 'clipboard',
      tone: 'accent'
    },
    {
      label: 'Itens ativos',
      value: itensAtivos + '/' + totalItens,
      sub: 'Itens cadastrados no dominio de venda.',
      icon: 'cart',
      tone: 'green'
    },
    {
      label: 'Itens bloqueados',
      value: String(itensBloqueados),
      sub: 'Indisponiveis por pausa manual, categoria ou estoque.',
      icon: 'alertTriangle',
      tone: 'amber'
    }
  ];

  document.getElementById('cardapio-summary').innerHTML = cards.map(function (card) {
    return '<div class="page-summary-card cardapio-summary-card">' +
      '<div>' +
        '<div class="page-summary-label">' + escapeHtml(card.label) + '</div>' +
        '<div class="page-summary-value">' + escapeHtml(card.value) + '</div>' +
      '</div>' +
      '<div class="cardapio-summary-icon cardapio-summary-icon--' + escapeHtml(card.tone) + '" aria-hidden="true">' + iconMarkup(card.icon) + '</div>' +
      '<div class="page-summary-sub">' + escapeHtml(card.sub) + '</div>' +
      '</div>';
  }).join('');
}

function actionButton(icon, label, toneClass, onClick) {
  const classes = ['action-icon', toneClass].filter(Boolean).join(' ');
  return '<button class="' + classes + '" type="button" onclick="' + onClick + '" title="' + escapeHtml(label) + '" aria-label="' + escapeHtml(label) + '">' +
    iconMarkup(icon) +
    '</button>';
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
        actions: '<button class="btn-primary" type="button" onclick="abrirModalCategoria()">' + iconMarkup('plus') + 'Nova Categoria</button>'
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
          actionButton('checkCircle', 'Salvar ordem da categoria', 'action-icon--muted', 'salvarOrdemCategoria(' + categoria.id + ')') +
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
          actionButton('clipboard', 'Editar categoria', 'action-icon--muted', 'abrirModalCategoria(' + categoria.id + ')') +
          actionButton(categoria.ativo ? 'close' : 'checkCircle', toggleLabel + ' categoria', categoria.ativo ? 'action-icon--danger' : 'action-icon--success', 'alternarCategoriaAtiva(' + categoria.id + ', ' + (!categoria.ativo) + ')') +
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
          actions: '<button class="btn-primary" type="button" onclick="abrirModalItem()">' + iconMarkup('plus') + 'Novo Item</button>'
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
          actionButton('checkCircle', 'Salvar ordem do item', 'action-icon--muted', 'salvarOrdemItem(' + item.id + ')') +
        '</div>' +
      '</td>' +
      '<td>' +
        '<div class="actions-grid actions-grid--stack">' +
          actionButton('clipboard', 'Editar item', 'action-icon--muted', 'abrirModalItem(' + item.id + ')') +
          actionButton(item.ativo ? 'close' : 'checkCircle', toggleAtivoLabel + ' item', item.ativo ? 'action-icon--danger' : 'action-icon--success', 'alternarItemAtivo(' + item.id + ', ' + (!item.ativo) + ')') +
          actionButton(item.disponivelParaVenda ? 'minus' : 'plus', toggleDisponivelLabel + ' item', item.disponivelParaVenda ? 'action-icon--warning' : 'action-icon--success', 'alternarDisponibilidadeItem(' + item.id + ', ' + (!item.disponivelParaVenda) + ')') +
        '</div>' +
      '</td>' +
    '</tr>';
  }).join('');
}

function resolverDisponibilidadeItem(item) {
  const manualMeta = item.disponivelParaVenda ? 'Manual liberado.' : 'Manual pausado.';
  const operacional = item.disponibilidadeOperacional;
  if (!item.ativo) {
    return { badgeClass: 'badge-gray', label: 'Inativo', meta: manualMeta + ' Nao listado no PDV.' };
  }
  if (!item.categoriaAtiva) {
    return { badgeClass: 'badge-gray', label: 'Bloqueado', meta: manualMeta + ' Categoria inativa.' };
  }
  if (!item.disponivelParaVenda) {
    return { badgeClass: 'badge-amber', label: 'Pausa manual', meta: manualMeta + ' Disponibilidade desligada no admin.' };
  }
  if (operacional && !operacional.resumo.itemDisponivel) {
    return {
      badgeClass: 'badge-red',
      label: 'Indisponivel agora',
      meta: manualMeta + ' ' + resumoOperacionalCardapio(operacional)
    };
  }
  if (operacional) {
    const alerta = primeiroAlertaCardapio(operacional);
    const capacidade = capacidadeCardapio(operacional);
    if (alerta) {
      return {
        badgeClass: 'badge-amber',
        label: 'Disponivel com alerta',
        meta: alerta.mensagem || 'Alerta operacional na disponibilidade.'
      };
    }
    return {
      badgeClass: 'badge-green',
      label: 'Disponivel agora',
      meta: typeof capacidade === 'number'
        ? 'Capacidade estimada isolada: ' + capacidade + '.'
        : 'Consulta operacional sem reserva.'
    };
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

function normalizarDisponibilidadeCardapio(payload) {
  const porItem = {};
  const itens = Array.isArray(payload && payload.itens) ? payload.itens : [];
  const variacoes = Array.isArray(payload && payload.variacoes) ? payload.variacoes : [];
  itens.forEach(function (resumo) {
    porItem[resumo.itemCardapioId] = {
      resumo: resumo,
      variacoes: variacoes.filter(function (variacao) {
        return variacao.itemCardapioId === resumo.itemCardapioId;
      })
    };
  });
  return porItem;
}

function resumoOperacionalCardapio(operacional) {
  const variacao = (operacional.variacoes || []).find(function (v) { return !v.disponivel; }) || {};
  const motivo = Array.isArray(variacao.motivos) && variacao.motivos.length ? variacao.motivos[0] : null;
  if (!motivo) return 'Sem variacao disponivel para o canal.';
  if (motivo.codigo === 'ESTOQUE_INSUFICIENTE' && motivo.produtoNome) {
    return 'Falta de ' + motivo.produtoNome + '.';
  }
  return motivo.mensagem || motivo.codigo || 'Sem variacao disponivel para o canal.';
}

function primeiroAlertaCardapio(operacional) {
  const variacao = (operacional.variacoes || []).find(function (v) {
    return v.disponivel && Array.isArray(v.alertas) && v.alertas.length;
  });
  return variacao ? variacao.alertas[0] : null;
}

function capacidadeCardapio(operacional) {
  const capacidades = (operacional.variacoes || [])
    .filter(function (v) { return v.disponivel && typeof v.capacidadeEstimada === 'number'; })
    .map(function (v) { return v.capacidadeEstimada; });
  if (!capacidades.length) return null;
  return Math.max.apply(null, capacidades);
}

function contextoOperacionalCardapioAdmin() {
  if (typeof resolverContextoOperacionalCardapio === 'function') {
    return resolverContextoOperacionalCardapio();
  }
  return { filialId: 1, canal: 'BALCAO' };
}

function urlDisponibilidadeCardapioAdmin(contexto) {
  const filialId = encodeURIComponent(contexto.filialId);
  const canal = encodeURIComponent(contexto.canal);
  return '/api/cardapio/disponibilidade?filialId=' + filialId + '&canal=' + canal;
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

function preencherSelectsModificadores() {
  const variacaoOptions = cardapioVariacoes
    .slice()
    .sort(function (a, b) { return String(a.itemNome + a.nome).localeCompare(String(b.itemNome + b.nome), 'pt-BR'); })
    .map(function (variacao) {
      return '<option value="' + variacao.id + '">' + escapeHtml(variacao.itemNome + ' - ' + variacao.nome) + '</option>';
    }).join('');
  const grupoOptions = cardapioGruposModificadores
    .map(function (grupo) { return '<option value="' + grupo.id + '">' + escapeHtml(grupo.nome) + '</option>'; })
    .join('');
  const produtoOptions = '<option value="">Nenhum</option>' + cardapioProdutos
    .map(function (produto) { return '<option value="' + produto.id + '">' + escapeHtml(produto.nome + (produto.unidadeBaseCodigo ? ' (' + produto.unidadeBaseCodigo + ')' : '')) + '</option>'; })
    .join('');
  const unidadeOptions = '<option value="">Selecione</option>' + cardapioUnidades
    .map(function (unidade) { return '<option value="' + unidade.id + '">' + escapeHtml(unidade.codigo + ' - ' + unidade.nome) + '</option>'; })
    .join('');

  ['modificador-variacao-preview', 'assoc-variacao'].forEach(function (id) {
    const el = document.getElementById(id);
    if (el) el.innerHTML = (id === 'modificador-variacao-preview' ? '<option value="">Selecione uma variacao para previa</option>' : '') + variacaoOptions;
  });
  const assocGrupo = document.getElementById('assoc-grupo');
  if (assocGrupo) assocGrupo.innerHTML = grupoOptions;
  ['componente-produto-origem', 'componente-produto-destino'].forEach(function (id) {
    const el = document.getElementById(id);
    if (el) el.innerHTML = produtoOptions;
  });
  const unidade = document.getElementById('componente-unidade');
  if (unidade) unidade.innerHTML = unidadeOptions;
}

function renderModificadores() {
  const tbody = document.getElementById('tabela-modificadores');
  if (!tbody) return;
  if (!cardapioGruposModificadores.length) {
    tbody.innerHTML = '<tr><td colspan="4" class="table-empty-cell">' +
      emptyStateMarkup({
        icon: 'clipboard',
        title: 'Nenhum modificador cadastrado',
        copy: 'Crie grupos como ponto, adicionais, remocoes e molhos para montar itens no PDV.',
        actions: '<button class="btn-primary" type="button" onclick="abrirModalGrupoModificador()">' + iconMarkup('plus') + 'Novo Grupo</button>'
      }) +
      '</td></tr>';
    return;
  }
  tbody.innerHTML = cardapioGruposModificadores.map(function (grupo) {
    const status = grupo.ativo ? '<span class="badge badge-green">Ativo</span>' : '<span class="badge badge-gray">Inativo</span>';
    return '<tr>' +
      '<td><div class="table-cell-stack"><span class="table-cell-strong">' + escapeHtml(grupo.nome) + '</span><span class="table-cell-caption">' + escapeHtml(grupo.codigo || '') + '</span></div></td>' +
      '<td><div class="table-cell-stack"><span class="badge badge-cyan">' + escapeHtml(grupo.tipoSelecao) + '</span><span class="table-cell-caption">' + (grupo.obrigatorio ? 'Obrigatorio' : 'Opcional') + ' · min ' + grupo.quantidadeMinima + ' · max ' + grupo.quantidadeMaxima + '</span></div></td>' +
      '<td>' + status + '</td>' +
      '<td><div class="actions-grid">' +
      actionButton('clipboard', 'Editar grupo', 'action-icon--muted', 'abrirModalGrupoModificador(' + grupo.id + ')') +
      actionButton('plus', 'Opcoes', 'action-icon--success', 'abrirModalOpcoesModificador(' + grupo.id + ')') +
      actionButton(grupo.ativo ? 'close' : 'checkCircle', grupo.ativo ? 'Inativar grupo' : 'Ativar grupo', grupo.ativo ? 'action-icon--danger' : 'action-icon--success', 'alternarGrupoModificador(' + grupo.id + ', ' + (!grupo.ativo) + ')') +
      '</div></td>' +
      '</tr>';
  }).join('');
}

function abrirModalGrupoModificador(id) {
  limparAlertaModal('alert-grupo-modificador');
  const grupo = id ? cardapioGruposModificadores.find(function (entry) { return entry.id === id; }) : null;
  document.getElementById('modal-grupo-modificador-titulo').textContent = grupo ? 'Editar Grupo' : 'Novo Grupo';
  document.getElementById('grupo-modificador-id').value = grupo ? grupo.id : '';
  document.getElementById('grupo-modificador-nome').value = grupo ? grupo.nome : '';
  document.getElementById('grupo-modificador-codigo').value = grupo ? (grupo.codigo || '') : '';
  document.getElementById('grupo-modificador-descricao').value = grupo ? (grupo.descricao || '') : '';
  document.getElementById('grupo-modificador-tipo').value = grupo ? grupo.tipoSelecao : 'UNICA';
  document.getElementById('grupo-modificador-min').value = grupo ? grupo.quantidadeMinima : 0;
  document.getElementById('grupo-modificador-max').value = grupo ? grupo.quantidadeMaxima : 1;
  document.getElementById('grupo-modificador-ordem').value = grupo ? grupo.ordemExibicao : 0;
  document.getElementById('grupo-modificador-obrigatorio').checked = grupo ? grupo.obrigatorio : false;
  document.getElementById('grupo-modificador-repeticao').checked = grupo ? grupo.permiteRepeticao : false;
  document.getElementById('grupo-modificador-ativo').checked = grupo ? grupo.ativo : true;
  document.getElementById('modal-grupo-modificador').classList.add('open');
}

async function salvarGrupoModificador() {
  const id = document.getElementById('grupo-modificador-id').value;
  const payload = {
    nome: document.getElementById('grupo-modificador-nome').value.trim(),
    codigo: document.getElementById('grupo-modificador-codigo').value.trim() || null,
    descricao: document.getElementById('grupo-modificador-descricao').value.trim() || null,
    tipoSelecao: document.getElementById('grupo-modificador-tipo').value,
    obrigatorio: document.getElementById('grupo-modificador-obrigatorio').checked,
    quantidadeMinima: Number(document.getElementById('grupo-modificador-min').value || 0),
    quantidadeMaxima: Number(document.getElementById('grupo-modificador-max').value || 0),
    permiteRepeticao: document.getElementById('grupo-modificador-repeticao').checked,
    ativo: document.getElementById('grupo-modificador-ativo').checked,
    ordemExibicao: Number(document.getElementById('grupo-modificador-ordem').value || 0)
  };
  if (!payload.nome || payload.quantidadeMaxima < payload.quantidadeMinima || (payload.tipoSelecao === 'UNICA' && payload.quantidadeMaxima > 1)) {
    mostrarAlertaModal(document.getElementById('alert-grupo-modificador'), 'error', 'Revise nome, minimo e maximo do grupo.');
    return;
  }
  const btn = document.getElementById('btn-salvar-grupo-modificador');
  setLoading(btn, true);
  try {
    const resp = await fetch(id ? '/api/cardapio/grupos-modificadores/' + id : '/api/cardapio/grupos-modificadores', {
      method: id ? 'PUT' : 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });
    if (!resp.ok) throw new Error(await safeReadErrorMessage(resp, 'Erro ao salvar grupo.'));
    showToast(id ? 'Grupo atualizado.' : 'Grupo criado.', 'success');
    fecharModal('modal-grupo-modificador');
    await recarregarDados();
  } catch (error) {
    mostrarAlertaModal(document.getElementById('alert-grupo-modificador'), 'error', error.message);
  } finally {
    setLoading(btn, false);
  }
}

async function alternarGrupoModificador(id, ativo) {
  await atualizarToggle('/api/cardapio/grupos-modificadores/' + id + '/status', { ativo: ativo }, ativo ? 'Grupo ativado.' : 'Grupo inativado.');
}

async function abrirModalOpcoesModificador(grupoId) {
  document.getElementById('opcao-grupo-id').value = grupoId;
  document.getElementById('modal-opcoes-modificador-titulo').textContent = 'Opcoes - ' + ((cardapioGruposModificadores.find(function (g) { return g.id === grupoId; }) || {}).nome || '');
  limparFormularioOpcaoModificador();
  await carregarOpcoesGrupoModificador(grupoId);
  document.getElementById('modal-opcoes-modificador').classList.add('open');
}

async function carregarOpcoesGrupoModificador(grupoId) {
  const resp = await fetch('/api/cardapio/grupos-modificadores/' + grupoId + '/opcoes');
  cardapioOpcoesPorGrupo[grupoId] = resp.ok ? await resp.json() : [];
  renderOpcoesGrupoModificador(grupoId);
}

function renderOpcoesGrupoModificador(grupoId) {
  const lista = document.getElementById('lista-opcoes-modificador');
  const opcoes = cardapioOpcoesPorGrupo[grupoId] || [];
  lista.innerHTML = opcoes.length ? opcoes.map(function (opcao) {
    return '<div class="modifier-list-row">' +
      '<div><strong>' + escapeHtml(opcao.nome) + '</strong><span>' + escapeHtml(opcao.codigo || '') + ' · +' + formatarNumero(opcao.acrescimoPreco) + '</span></div>' +
      '<div class="actions-grid">' +
      actionButton('clipboard', 'Editar opcao', 'action-icon--muted', 'editarOpcaoModificador(' + grupoId + ', ' + opcao.id + ')') +
      actionButton('box', 'Componentes', 'action-icon--success', 'abrirModalComponentesModificador(' + opcao.id + ')') +
      '</div></div>';
  }).join('') : '<div class="empty">Nenhuma opcao cadastrada.</div>';
}

function limparFormularioOpcaoModificador() {
  document.getElementById('opcao-id').value = '';
  document.getElementById('opcao-nome').value = '';
  document.getElementById('opcao-codigo').value = '';
  document.getElementById('opcao-acrescimo').value = '0';
  document.getElementById('opcao-max').value = '1';
  document.getElementById('opcao-padrao').checked = false;
  document.getElementById('opcao-ativa').checked = true;
  limparAlertaModal('alert-opcao-modificador');
}

function editarOpcaoModificador(grupoId, opcaoId) {
  const opcao = (cardapioOpcoesPorGrupo[grupoId] || []).find(function (entry) { return entry.id === opcaoId; });
  if (!opcao) return;
  document.getElementById('opcao-id').value = opcao.id;
  document.getElementById('opcao-nome').value = opcao.nome || '';
  document.getElementById('opcao-codigo').value = opcao.codigo || '';
  document.getElementById('opcao-acrescimo').value = Number(opcao.acrescimoPreco || 0).toFixed(2);
  document.getElementById('opcao-max').value = opcao.quantidadeMaximaPorItem || 1;
  document.getElementById('opcao-padrao').checked = opcao.selecionadaPorPadrao;
  document.getElementById('opcao-ativa').checked = opcao.ativa;
}

async function salvarOpcaoModificador() {
  const grupoId = Number(document.getElementById('opcao-grupo-id').value);
  const id = document.getElementById('opcao-id').value;
  const payload = {
    nome: document.getElementById('opcao-nome').value.trim(),
    codigo: document.getElementById('opcao-codigo').value.trim() || null,
    descricao: null,
    acrescimoPreco: Number(document.getElementById('opcao-acrescimo').value || 0),
    quantidadeMaximaPorItem: Number(document.getElementById('opcao-max').value || 1),
    selecionadaPorPadrao: document.getElementById('opcao-padrao').checked,
    ativa: document.getElementById('opcao-ativa').checked,
    ordemExibicao: 0
  };
  if (!payload.nome || payload.acrescimoPreco < 0 || payload.quantidadeMaximaPorItem < 1) {
    mostrarAlertaModal(document.getElementById('alert-opcao-modificador'), 'error', 'Revise nome, preco e limite da opcao.');
    return;
  }
  const resp = await fetch(id ? '/api/cardapio/opcoes-modificadores/' + id : '/api/cardapio/grupos-modificadores/' + grupoId + '/opcoes', {
    method: id ? 'PUT' : 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload)
  });
  if (!resp.ok) {
    mostrarAlertaModal(document.getElementById('alert-opcao-modificador'), 'error', await safeReadErrorMessage(resp, 'Erro ao salvar opcao.'));
    return;
  }
  showToast('Opcao salva.', 'success');
  limparFormularioOpcaoModificador();
  await carregarOpcoesGrupoModificador(grupoId);
}

async function abrirModalComponentesModificador(opcaoId) {
  document.getElementById('componente-opcao-id').value = opcaoId;
  limparFormularioComponenteModificador();
  await carregarComponentesOpcaoModificador(opcaoId);
  document.getElementById('modal-componente-modificador').classList.add('open');
}

async function carregarComponentesOpcaoModificador(opcaoId) {
  const resp = await fetch('/api/cardapio/opcoes-modificadores/' + opcaoId + '/componentes');
  cardapioComponentesPorOpcao[opcaoId] = resp.ok ? await resp.json() : [];
  renderComponentesOpcaoModificador(opcaoId);
}

function renderComponentesOpcaoModificador(opcaoId) {
  const lista = document.getElementById('lista-componentes-modificador');
  const componentes = cardapioComponentesPorOpcao[opcaoId] || [];
  lista.innerHTML = componentes.length ? componentes.map(function (comp) {
    return '<div class="modifier-list-row"><div><strong>' + escapeHtml(comp.tipoEfeito) + '</strong><span>' +
      escapeHtml(nomeProdutoAdmin(comp.produtoOrigemId) || nomeProdutoAdmin(comp.produtoDestinoId) || 'Sem estoque') +
      ' · ' + escapeHtml(comp.quantidadeBase || '') + '</span></div>' +
      '<div class="actions-grid">' + actionButton('clipboard', 'Editar componente', 'action-icon--muted', 'editarComponenteModificador(' + opcaoId + ', ' + comp.id + ')') + '</div></div>';
  }).join('') : '<div class="empty">Nenhum componente cadastrado.</div>';
}

function limparFormularioComponenteModificador() {
  document.getElementById('componente-id').value = '';
  document.getElementById('componente-efeito').value = 'ADICIONAR';
  document.getElementById('componente-produto-origem').value = '';
  document.getElementById('componente-produto-destino').value = '';
  document.getElementById('componente-quantidade').value = '0';
  document.getElementById('componente-politica').value = 'HERDAR_PRODUTO';
  document.getElementById('componente-observacao').value = '';
  limparAlertaModal('alert-componente-modificador');
  aplicarRegrasComponenteAdmin();
}

function editarComponenteModificador(opcaoId, componenteId) {
  const comp = (cardapioComponentesPorOpcao[opcaoId] || []).find(function (entry) { return entry.id === componenteId; });
  if (!comp) return;
  document.getElementById('componente-id').value = comp.id;
  document.getElementById('componente-efeito').value = comp.tipoEfeito;
  document.getElementById('componente-produto-origem').value = comp.produtoOrigemId || '';
  document.getElementById('componente-produto-destino').value = comp.produtoDestinoId || '';
  document.getElementById('componente-quantidade').value = comp.quantidadeBase || 0;
  document.getElementById('componente-unidade').value = comp.unidadeBaseId || '';
  document.getElementById('componente-politica').value = comp.politicaFalta || 'HERDAR_PRODUTO';
  document.getElementById('componente-observacao').value = comp.observacao || '';
  aplicarRegrasComponenteAdmin();
}

function aplicarRegrasComponenteAdmin() {
  const efeito = document.getElementById('componente-efeito').value;
  const semEstoque = efeito === 'SEM_IMPACTO_ESTOQUE';
  ['componente-produto-origem', 'componente-produto-destino', 'componente-quantidade', 'componente-unidade'].forEach(function (id) {
    const el = document.getElementById(id);
    if (el) el.disabled = semEstoque;
  });
}

async function salvarComponenteModificador() {
  const opcaoId = Number(document.getElementById('componente-opcao-id').value);
  const id = document.getElementById('componente-id').value;
  const efeito = document.getElementById('componente-efeito').value;
  const quantidade = Number(document.getElementById('componente-quantidade').value || 0);
  const origem = document.getElementById('componente-produto-origem').value;
  const destino = document.getElementById('componente-produto-destino').value;
  const payload = {
    tipoEfeito: efeito,
    produtoOrigemId: origem ? Number(origem) : null,
    produtoDestinoId: destino ? Number(destino) : null,
    quantidadeBase: efeito === 'SEM_IMPACTO_ESTOQUE' ? null : quantidade,
    unidadeBaseId: efeito === 'SEM_IMPACTO_ESTOQUE' ? null : Number(document.getElementById('componente-unidade').value || 0),
    multiplicador: null,
    politicaFalta: document.getElementById('componente-politica').value,
    observacao: document.getElementById('componente-observacao').value.trim() || null,
    ordem: 0
  };
  if (!validarComponenteAdmin(payload)) return;
  const resp = await fetch(id ? '/api/cardapio/opcoes-modificadores/' + opcaoId + '/componentes/' + id : '/api/cardapio/opcoes-modificadores/' + opcaoId + '/componentes', {
    method: id ? 'PUT' : 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload)
  });
  if (!resp.ok) {
    mostrarAlertaModal(document.getElementById('alert-componente-modificador'), 'error', await safeReadErrorMessage(resp, 'Erro ao salvar componente.'));
    return;
  }
  showToast('Componente salvo.', 'success');
  limparFormularioComponenteModificador();
  await carregarComponentesOpcaoModificador(opcaoId);
}

function validarComponenteAdmin(payload) {
  if (payload.tipoEfeito === 'SEM_IMPACTO_ESTOQUE') return true;
  if (!payload.quantidadeBase || payload.quantidadeBase <= 0 || !payload.unidadeBaseId) {
    mostrarAlertaModal(document.getElementById('alert-componente-modificador'), 'error', 'Quantidade e unidade sao obrigatorias para impacto de estoque.');
    return false;
  }
  if (payload.tipoEfeito === 'ADICIONAR' && !payload.produtoDestinoId) {
    mostrarAlertaModal(document.getElementById('alert-componente-modificador'), 'error', 'ADICIONAR exige produto destino.');
    return false;
  }
  if (payload.tipoEfeito === 'REMOVER' && !payload.produtoOrigemId) {
    mostrarAlertaModal(document.getElementById('alert-componente-modificador'), 'error', 'REMOVER exige produto origem.');
    return false;
  }
  if (payload.tipoEfeito === 'SUBSTITUIR' && (!payload.produtoOrigemId || !payload.produtoDestinoId)) {
    mostrarAlertaModal(document.getElementById('alert-componente-modificador'), 'error', 'SUBSTITUIR exige origem e destino.');
    return false;
  }
  return true;
}

function abrirModalAssociarGrupo() {
  preencherSelectsModificadores();
  limparAlertaModal('alert-assoc-modificador');
  document.getElementById('assoc-min').value = '';
  document.getElementById('assoc-max').value = '';
  document.getElementById('assoc-obrigatorio').checked = false;
  document.getElementById('assoc-ativo').checked = true;
  document.getElementById('modal-associar-modificador').classList.add('open');
}

async function salvarAssociacaoModificador() {
  const variacaoId = Number(document.getElementById('assoc-variacao').value);
  const grupoId = Number(document.getElementById('assoc-grupo').value);
  const payload = {
    grupoId: grupoId,
    obrigatorioOverride: document.getElementById('assoc-obrigatorio').checked,
    quantidadeMinimaOverride: document.getElementById('assoc-min').value ? Number(document.getElementById('assoc-min').value) : null,
    quantidadeMaximaOverride: document.getElementById('assoc-max').value ? Number(document.getElementById('assoc-max').value) : null,
    ordemExibicao: 0,
    ativo: document.getElementById('assoc-ativo').checked
  };
  const resp = await fetch('/api/cardapio/variacoes/' + variacaoId + '/grupos-modificadores', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload)
  });
  if (!resp.ok) {
    mostrarAlertaModal(document.getElementById('alert-assoc-modificador'), 'error', await safeReadErrorMessage(resp, 'Erro ao associar grupo.'));
    return;
  }
  showToast('Grupo associado a variacao.', 'success');
  fecharModal('modal-associar-modificador');
  await carregarConfiguracaoVariacaoAdmin();
}

async function carregarConfiguracaoVariacaoAdmin() {
  const variacaoId = document.getElementById('modificador-variacao-preview').value;
  const panel = document.getElementById('modificador-preview');
  if (!variacaoId) return;
  const contexto = contextoOperacionalCardapioAdmin();
  const resp = await fetch('/api/cardapio/variacoes/' + variacaoId + '/configuracao?filialId=' + contexto.filialId + '&canal=' + contexto.canal);
  if (!resp.ok) {
    panel.innerHTML = '<div class="empty">Erro ao carregar previa.</div>';
    return;
  }
  const config = await resp.json();
  panel.innerHTML = '<div class="modifier-preview-head"><strong>' + escapeHtml(config.nome) + '</strong><span>Hash ' + escapeHtml(String(config.configuracaoVersao || '').slice(0, 10)) + '</span></div>' +
    (config.grupos || []).map(function (grupo) {
      return '<div class="modifier-preview-group"><strong>' + escapeHtml(grupo.nome) + '</strong><span>' + (grupo.obrigatorio ? 'Obrigatorio' : 'Opcional') + ' · ' + grupo.tipoSelecao + '</span>' +
        '<div>' + (grupo.opcoes || []).map(function (opcao) {
          return '<span class="badge ' + (opcao.disponivel ? 'badge-green' : 'badge-red') + '">' + escapeHtml(opcao.nome) + (Number(opcao.acrescimo || 0) ? ' +' + formatarNumero(opcao.acrescimo) : '') + '</span>';
        }).join(' ') + '</div></div>';
    }).join('');
}

function nomeProdutoAdmin(produtoId) {
  const produto = cardapioProdutos.find(function (entry) { return Number(entry.id) === Number(produtoId); });
  return produto ? produto.nome : '';
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

/* Page-scoped bridge: legacy inline event handlers resolve only against the active page. */
window.StockOSPages = window.StockOSPages || {};
window.StockOSPages.cardapio = {
  mount: stockOSMount_cardapio,
  activate() {
    Object.assign(window, {
    recarregarDados,
    carregarVariacoesCardapioAdmin,
    renderResumo,
    actionButton,
    renderCategorias,
    aplicarFiltrosItens,
    renderItens,
    resolverDisponibilidadeItem,
    normalizarDisponibilidadeCardapio,
    resumoOperacionalCardapio,
    primeiroAlertaCardapio,
    capacidadeCardapio,
    contextoOperacionalCardapioAdmin,
    urlDisponibilidadeCardapioAdmin,
    preencherSelectCategorias,
    preencherSelectProdutos,
    abrirModalCategoria,
    salvarCategoria,
    abrirModalItem,
    aplicarRegrasTipoItem,
    salvarItem,
    alternarCategoriaAtiva,
    alternarItemAtivo,
    alternarDisponibilidadeItem,
    salvarOrdemCategoria,
    salvarOrdemItem,
    atualizarToggle,
    preencherSelectsModificadores,
    renderModificadores,
    abrirModalGrupoModificador,
    salvarGrupoModificador,
    alternarGrupoModificador,
    abrirModalOpcoesModificador,
    carregarOpcoesGrupoModificador,
    renderOpcoesGrupoModificador,
    limparFormularioOpcaoModificador,
    editarOpcaoModificador,
    salvarOpcaoModificador,
    abrirModalComponentesModificador,
    carregarComponentesOpcaoModificador,
    renderComponentesOpcaoModificador,
    limparFormularioComponenteModificador,
    editarComponenteModificador,
    aplicarRegrasComponenteAdmin,
    salvarComponenteModificador,
    validarComponenteAdmin,
    abrirModalAssociarGrupo,
    salvarAssociacaoModificador,
    carregarConfiguracaoVariacaoAdmin,
    nomeProdutoAdmin,
    fecharModal,
    fecharModalSeClicouFora,
    mostrarAlertaModal,
    limparAlertaModal,
    mostrarAlertaPagina,
    limparAlertaPagina,
    formatarNumero,
    normalizarTexto,
  });
  }
};
if (/^\/cardapio(?:\.html)?\/?$/.test(location.pathname)) {
  window.StockOSPages.cardapio.activate();
}
document.addEventListener('DOMContentLoaded', stockOSMount_cardapio);

})();
