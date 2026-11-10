/* SPA-isolated POS module. Its actions are exported only when Caixas is active. */
(() => {
'use strict';
const PDV_CATEGORY_TONES = {
  todos: { icon: 'grid', accent: '#4f46e5', soft: 'rgba(79,70,229,.12)' },
  entradas: { icon: 'plus', accent: '#d97706', soft: 'rgba(217,119,6,.12)' },
  massas_classicas: { icon: 'box', accent: '#c2410c', soft: 'rgba(194,65,12,.12)' },
  massas_especiais: { icon: 'box', accent: '#b91c1c', soft: 'rgba(185,28,28,.12)' },
  pizzas_artesanais: { icon: 'box', accent: '#ea580c', soft: 'rgba(234,88,12,.12)' },
  pratos_executivos: { icon: 'clipboard', accent: '#0f766e', soft: 'rgba(15,118,110,.12)' },
  sobremesas: { icon: 'checkCircle', accent: '#db2777', soft: 'rgba(219,39,119,.12)' },
  bebidas: { icon: 'cart', accent: '#0891b2', soft: 'rgba(8,145,178,.12)' }
};

let pdvNumeroCaixa = null;
let pdvCategorias = [];
let pdvItens = [];
let pdvItensMap = {};
let pdvDisponibilidadePorItem = {};
let pdvCarrinho = [];
let pdvCategoriaAtiva = 'todos';
let pdvVendaAtual = null;
let pdvVariacoesPorItem = {};
let pdvVariacoesPorId = {};
let pdvDraftPayloadPorItemVenda = {};
let pdvConfiguracaoAtual = null;
let pdvFinalizando = false;
let pdvLimpandoCarrinho = false;
let pdvTentativaFinalizacao = null;

async function abrirPDV(numeroCaixa) {
  pdvNumeroCaixa = numeroCaixa;
  pdvCarrinho = [];
  pdvVendaAtual = null;
  pdvComandaAtual = null;
  pdvUltimoCupom = null;
  atualizarComandaPDV();
  const cupomAuto = document.getElementById('pdv-cupom-auto');
  if (cupomAuto) cupomAuto.checked = cupomAutomaticoPDV();
  pdvDraftPayloadPorItemVenda = {};
  pdvCategoriaAtiva = 'todos';
  pdvFinalizando = false;
  pdvLimpandoCarrinho = false;
  pdvTentativaFinalizacao = null;

  document.getElementById('pdv-caixa-label').textContent = `Caixa ${numeroCaixa}`;
  document.getElementById('pdv-busca').value = '';
  limparAlert('alert-pdv');

  abrirModal('modal-pdv');
  atualizarOperadorPDV();
  cancelarPagamentoPDV();
  pdvProximoAlternativo = false;
  atualizarIndicadorAlternativoPDV();
  mostrarPainelPDVMobile('produtos');
  renderCarrinho();
  focarLeituraPDV();
  await Promise.all([carregarCardapioPDV(), carregarCodigosPDV(), carregarTeclasPDV()]);
  focarLeituraPDV();
}

function mostrarPainelPDVMobile(painel) {
  if (painel !== 'produtos' && painel !== 'pedido') return;
  const shell = document.getElementById('pdv-shell');
  if (!shell) return;
  shell.dataset.mobilePanel = painel;
  for (const [id, active] of [
    ['pdv-mobile-produtos', painel === 'produtos'],
    ['pdv-mobile-pedido', painel === 'pedido']
  ]) {
    const button = document.getElementById(id);
    if (!button) continue;
    button.classList.toggle('is-active', active);
    button.setAttribute('aria-pressed', String(active));
  }
}

async function carregarCardapioPDV() {
  const grid = document.getElementById('pdv-produtos-grid');
  grid.innerHTML = emptyStateMarkup({
    icon: 'cart',
    title: 'Carregando cardapio',
    copy: 'Buscando categorias e itens do menu deste caixa.'
  });

  try {
    const contexto = contextoOperacionalPDV();
    const [categoriasResp, itensResp, disponibilidadeResp] = await Promise.all([
      fetch('/api/cardapio/categorias'),
      fetch('/api/cardapio/itens'),
      fetch(urlDisponibilidadePDV(contexto))
    ]);
    if (!categoriasResp.ok || !itensResp.ok) throw new Error();

    pdvCategorias = await categoriasResp.json();
    pdvItens = await itensResp.json();
    pdvDisponibilidadePorItem = disponibilidadeResp.ok
      ? normalizarDisponibilidadePDV(await disponibilidadeResp.json())
      : {};
    pdvItensMap = {};
    pdvItens.forEach(item => {
      item.disponibilidadeOperacional = pdvDisponibilidadePorItem[item.id] || null;
      pdvItensMap[item.id] = item;
    });

    renderTabsCategorias();
    renderCardapio();
  } catch (error) {
    console.error('Erro ao carregar cardapio do PDV:', error);
    grid.innerHTML = emptyStateMarkup({
      icon: 'alertTriangle',
      title: 'Erro ao carregar cardapio',
      copy: 'Verifique se o servidor esta rodando e tente novamente.'
    });
  }
}

async function carregarVariacoesItemPDV(itemId) {
  if (pdvVariacoesPorItem[itemId]) return pdvVariacoesPorItem[itemId];
  const resp = await fetch(`/api/cardapio/itens/${itemId}/variacoes`);
  if (!resp.ok) {
    throw new Error(await safeReadErrorMessage(resp, 'Nao foi possivel carregar variacoes.'));
  }
  const variacoes = await resp.json();
  pdvVariacoesPorItem[itemId] = variacoes;
  variacoes.forEach(variacao => {
    pdvVariacoesPorId[variacao.id] = variacao;
  });
  return variacoes;
}

function getCategoriaConfig(codigo) {
  return PDV_CATEGORY_TONES[codigo] || { icon: 'box', accent: '#64748b', soft: 'rgba(100,116,139,.12)' };
}

function getCategoriaByCodigo(codigo) {
  return pdvCategorias.find(categoria => categoria.codigo === codigo)
    || { codigo, nomeExibicao: 'Cardapio', ordemExibicao: 999 };
}

function countItensCategoria(codigo) {
  return pdvItens.filter(item => item.categoriaCodigo === codigo).length;
}

function renderTabsCategorias() {
  const container = document.getElementById('pdv-categorias');
  const tabs = [{ codigo: 'todos', nomeExibicao: 'Todos' }].concat(pdvCategorias);

  container.innerHTML = tabs.map(categoria => {
    const config = getCategoriaConfig(categoria.codigo);
    const ativo = categoria.codigo === pdvCategoriaAtiva;
    const total = categoria.codigo === 'todos' ? pdvItens.length : countItensCategoria(categoria.codigo);

    return `<button
      type="button"
      class="pdv-category-tab${ativo ? ' pdv-category-tab--active' : ''}"
      style="--pdv-accent:${config.accent};--pdv-soft:${config.soft};"
      onclick="selecionarCategoriaPDV('${categoria.codigo}')"
    >
      <span class="pdv-category-tab__icon">${iconMarkup(config.icon)}</span>
      <span>${escapeHtml(categoria.nomeExibicao)}</span>
      <span class="pdv-category-tab__count">${total}</span>
    </button>`;
  }).join('');
}

function selecionarCategoriaPDV(codigo) {
  pdvCategoriaAtiva = codigo;
  renderTabsCategorias();
  renderCardapio();
}

function getBuscaAtualPDV() {
  return (document.getElementById('pdv-busca').value || '').toLowerCase().trim();
}

function itemCombinaBusca(item, busca) {
  if (!busca) return true;
  const indice = [
    item.nome,
    item.descricaoCurta,
    item.categoriaNomeExibicao,
    item.tipoItem
  ].filter(Boolean).join(' ').toLowerCase();
  return indice.includes(busca);
}

function itensFiltradosPDV() {
  const busca = getBuscaAtualPDV();

  return pdvItens.filter(item => {
    const mesmaCategoria = pdvCategoriaAtiva === 'todos' || item.categoriaCodigo === pdvCategoriaAtiva;
    return mesmaCategoria && itemCombinaBusca(item, busca);
  });
}

function renderCardapio() {
  const itensFiltrados = itensFiltradosPDV();
  const busca = getBuscaAtualPDV();
  const grid = document.getElementById('pdv-produtos-grid');

  if (itensFiltrados.length === 0) {
    grid.innerHTML = emptyStateMarkup({
      icon: 'search',
      title: busca ? 'Nenhum item encontrado' : 'Cardapio vazio',
      copy: busca
        ? `Nenhum item corresponde a busca "${busca}".`
        : 'Cadastre itens de cardapio para iniciar vendas neste caixa.'
    });
    return;
  }

  if (pdvCategoriaAtiva !== 'todos') {
    grid.innerHTML = `<div class="pdv-section pdv-section--single">${itensFiltrados.map(renderCardProduto).join('')}</div>`;
    return;
  }

  const secoes = pdvCategorias.map(categoria => {
    const itensCategoria = itensFiltrados.filter(item => item.categoriaCodigo === categoria.codigo);
    if (itensCategoria.length === 0) return '';

    return `
      <section class="pdv-section">
        <div class="pdv-section__header">
          <div class="pdv-section__title">${escapeHtml(categoria.nomeExibicao)}</div>
          <div class="pdv-section__meta">${itensCategoria.length} item(ns)</div>
        </div>
        <div class="pdv-section__grid">
          ${itensCategoria.map(renderCardProduto).join('')}
        </div>
      </section>`;
  }).filter(Boolean);

  grid.innerHTML = secoes.join('');
}

function getBadgeTipo(item) {
  return item.tipoItem === 'PREPARADO_SOB_DEMANDA'
    ? { label: 'Feito na hora', tone: 'demand' }
    : { label: 'Estoque', tone: 'stock' };
}

function getStatusItem(item) {
  const operacional = item.disponibilidadeOperacional;
  if (operacional && !operacional.resumo.itemDisponivel) {
    return resumoMotivoIndisponibilidade(operacional);
  }
  if (operacional) {
    const alerta = primeiroAlertaDisponibilidade(operacional);
    if (alerta) return alerta.mensagem || 'Disponivel com alerta operacional';
    const capacidade = capacidadeDisponivelItem(operacional);
    if (typeof capacidade === 'number') return `Disponivel - capacidade estimada ${capacidade}`;
  }
  if (item.tipoItem === 'PREPARADO_SOB_DEMANDA') return 'Preparo sob demanda';
  if (!item.disponivel) {
    return item.controladoPorEstoque ? 'Sem estoque' : 'Indisponivel';
  }
  if (typeof item.quantidadeAtual === 'number') return `${item.quantidadeAtual} em estoque`;
  return item.controladoPorEstoque ? 'Estoque vinculado' : 'Disponibilidade manual';
}

function renderCardProduto(item) {
  const categoria = getCategoriaByCodigo(item.categoriaCodigo);
  const config = getCategoriaConfig(categoria.codigo);
  const badgeTipo = getBadgeTipo(item);
  const semEstoque = !itemDisponivelParaSelecaoPDV(item);
  const quantidadeCarrinho = quantidadeItemNoCarrinhoPDV(item.id);

  return `<button
    type="button"
    id="pdv-card-${item.id}"
    class="pdv-card${quantidadeCarrinho > 0 ? ' pdv-card--selected' : ''}${semEstoque ? ' pdv-card--disabled' : ''}"
    style="--pdv-accent:${config.accent};--pdv-soft:${config.soft};"
    onclick="${semEstoque ? '' : `adicionarAoCarrinho(${item.id})`}"
    ${semEstoque ? 'disabled' : ''}
  >
    <div class="pdv-card__accent"></div>
    ${quantidadeCarrinho > 0 ? `<span class="pdv-card__badge">${quantidadeCarrinho}</span>` : ''}
    <div class="pdv-card__body">
      <div class="pdv-card__topline">
        <span class="pdv-card__pill pdv-card__pill--${badgeTipo.tone}">${escapeHtml(badgeTipo.label)}</span>
      </div>
      <div class="pdv-card__title">${escapeHtml(item.nome)}</div>
      <div class="pdv-card__meta">${escapeHtml(item.descricaoCurta || categoria.nomeExibicao)}</div>
      <div class="pdv-card__price">${formatarMoedaPDV(item.precoVenda)}</div>
      <div class="pdv-card__status${semEstoque ? ' pdv-card__status--danger' : ''}">
        ${escapeHtml(getStatusItem(item))}
      </div>
    </div>
  </button>`;
}

function filtrarProdutosPDV() {
  renderCardapio();
}

async function adicionarAoCarrinho(itemId) {
  if (pdvFinalizando || pdvLimpandoCarrinho || pdvTentativaFinalizacao) {
    mostrarAlert('alert-pdv', 'Resolva a tentativa de venda pendente antes de alterar os itens.', 'error');
    return;
  }
  const item = pdvItensMap[itemId];
  if (!item || !itemDisponivelParaSelecaoPDV(item)) return;

  try {
    const variacao = await selecionarVariacaoPadraoPDV(item);
    if (!variacao) return;
    await abrirConfiguracaoItemPDV(item, variacao);
  } catch (error) {
    mostrarAlert('alert-pdv', error.message || 'Nao foi possivel montar o item.', 'error');
  }
}

async function selecionarVariacaoPadraoPDV(item) {
  const variacoes = await carregarVariacoesItemPDV(item.id);
  const disponiveis = variacoes
    .filter(variacao => variacao.ativa)
    .filter(variacao => variacaoDisponivelPDV(variacao.id));

  if (!disponiveis.length) {
    mostrarAlert('alert-pdv', `${item.nome}: sem variacao disponivel agora.`, 'error');
    return null;
  }

  const padrao = disponiveis.find(variacao => variacao.padrao) || disponiveis[0];
  if (disponiveis.length === 1) return padrao;

  await abrirSeletorVariacaoPDV(item, disponiveis);
  return null;
}

async function abrirSeletorVariacaoPDV(item, variacoes) {
  const modal = garantirModalConfiguracaoPDV();
  pdvConfiguracaoAtual = { modo: 'selecionar-variacao', item, variacoes };
  modal.querySelector('#pdv-config-title').textContent = item.nome;
  modal.querySelector('#pdv-config-subtitle').textContent = 'Escolha a variacao para continuar.';
  modal.querySelector('#pdv-config-body').innerHTML = `
    <div class="pdv-config-variation-list">
      ${variacoes.map(variacao => `
        <button type="button" class="pdv-config-variation" onclick="selecionarVariacaoConfiguracaoPDV(${item.id}, ${variacao.id})">
          <span>
            <strong>${escapeHtml(variacao.nome)}</strong>
            <small>${escapeHtml(variacao.codigo || '')}</small>
          </span>
          <span>${formatarMoedaPDV(variacao.precoVenda)}</span>
        </button>
      `).join('')}
    </div>`;
  modal.querySelector('#pdv-config-summary').innerHTML = '';
  modal.querySelector('#btn-pdv-config-confirmar').disabled = true;
  abrirModal('modal-pdv-configuracao');
}

async function selecionarVariacaoConfiguracaoPDV(itemId, variacaoId) {
  const item = pdvItensMap[itemId];
  const variacao = pdvVariacoesPorId[variacaoId];
  if (!item || !variacao) return;
  await abrirConfiguracaoItemPDV(item, variacao);
}

async function abrirConfiguracaoItemPDV(item, variacao, itemVendaExistente) {
  const modal = garantirModalConfiguracaoPDV();
  limparAlert('alert-pdv-configuracao');
  const contexto = contextoOperacionalPDV();
  modal.querySelector('#pdv-config-title').textContent = item.nome;
  modal.querySelector('#pdv-config-subtitle').textContent = `${variacao.nome} - ${formatarMoedaPDV(variacao.precoVenda)}`;
  modal.querySelector('#pdv-config-body').innerHTML = emptyStateMarkup({
    icon: 'refresh',
    title: 'Carregando configuracao',
    copy: 'Buscando grupos, opcoes e disponibilidade.'
  });
  modal.querySelector('#pdv-config-summary').innerHTML = '';
  modal.querySelector('#btn-pdv-config-confirmar').disabled = true;
  abrirModal('modal-pdv-configuracao');

  const url = `/api/cardapio/variacoes/${variacao.id}/configuracao?filialId=${encodeURIComponent(contexto.filialId)}&canal=${encodeURIComponent(contexto.canal)}`;
  const resp = await fetch(url);
  if (!resp.ok) {
    throw new Error(await safeReadErrorMessage(resp, 'Nao foi possivel carregar configuracao.'));
  }
  const configuracao = await resp.json();
  const selecoesIniciais = itemVendaExistente
    ? selecoesDoItemVendaPDV(itemVendaExistente)
    : selecoesPadraoConfiguracaoPDV(configuracao);
  pdvConfiguracaoAtual = {
    modo: itemVendaExistente ? 'editar' : 'novo',
    item,
    variacao,
    itemVendaExistente,
    configuracao,
    quantidadeItem: itemVendaExistente ? Number(itemVendaExistente.quantidade || 1) : 1,
    observacao: itemVendaExistente ? (itemVendaExistente.observacao || '') : '',
    selecoes: selecoesIniciais
  };

  renderConfiguracaoPDV();
  await validarConfiguracaoVisualPDV();
}

function selecoesPadraoConfiguracaoPDV(configuracao) {
  const selecoes = [];
  (configuracao.grupos || []).forEach(grupo => {
    (grupo.opcoes || []).forEach(opcao => {
      if (opcao.selecionadaPorPadrao && opcao.aplicacaoAutomaticaPermitida) {
        selecoes.push({ grupoId: grupo.grupoId, opcaoId: opcao.opcaoId, quantidade: 1, padrao: true });
      }
    });
  });
  return selecoes;
}

function selecoesDoItemVendaPDV(itemVenda) {
  if (pdvDraftPayloadPorItemVenda[itemVenda.id]) {
    return (pdvDraftPayloadPorItemVenda[itemVenda.id].selecoes || []).map(selecao => Object.assign({}, selecao));
  }
  return (itemVenda.modificadores || []).map(modificador => ({
    grupoId: modificador.grupoModificadorId,
    opcaoId: modificador.opcaoModificadorId,
    quantidade: modificador.quantidadeSelecionada || 1,
    padrao: !!modificador.selecaoPadrao
  }));
}

function renderConfiguracaoPDV() {
  const estado = pdvConfiguracaoAtual;
  if (!estado || !estado.configuracao) return;
  const config = estado.configuracao;
  const body = document.getElementById('pdv-config-body');
  const grupos = config.grupos || [];

  const variacoesMarkup = estado.item && pdvVariacoesPorItem[estado.item.id] && pdvVariacoesPorItem[estado.item.id].length > 1
    ? `<label class="pdv-config-field">
        <span>Variacao</span>
        <select id="pdv-config-variacao" onchange="trocarVariacaoConfiguracaoPDV()">
          ${pdvVariacoesPorItem[estado.item.id].filter(v => v.ativa).map(v => `
            <option value="${v.id}" ${v.id === estado.variacao.id ? 'selected' : ''}>${escapeHtml(v.nome)} - ${formatarMoedaPDV(v.precoVenda)}</option>
          `).join('')}
        </select>
      </label>`
    : '';

  body.innerHTML = `
    <div class="pdv-config-form">
      <div class="pdv-config-top">
        ${variacoesMarkup}
        <label class="pdv-config-field">
          <span>Quantidade</span>
          <input id="pdv-config-quantidade" type="number" min="1" step="1" value="${estado.quantidadeItem}" oninput="alterarQuantidadeConfiguracaoPDV(this.value)">
        </label>
      </div>
      ${grupos.length ? grupos.map(renderGrupoConfiguracaoPDV).join('') : '<div class="pdv-config-empty">Sem modificadores configurados para esta variacao.</div>'}
      <label class="pdv-config-field">
        <span>Observacao</span>
        <textarea id="pdv-config-observacao" rows="2" oninput="alterarObservacaoConfiguracaoPDV(this.value)">${escapeHtml(estado.observacao || '')}</textarea>
      </label>
    </div>`;
  aplicarIconesEstaticos(body);
  atualizarResumoConfiguracaoPDV();
}

function renderGrupoConfiguracaoPDV(grupo) {
  const obrigatorio = grupo.obrigatorio ? '<span class="pdv-config-required">Obrigatorio</span>' : '';
  const meta = `${grupo.minimo} min. / ${grupo.maximo} max.`;
  return `<section class="pdv-config-group" data-grupo-id="${grupo.grupoId}">
    <div class="pdv-config-group__head">
      <div>
        <strong>${escapeHtml(grupo.nome)}</strong>
        <span>${escapeHtml(meta)}</span>
      </div>
      ${obrigatorio}
    </div>
    <div class="pdv-config-options">
      ${(grupo.opcoes || []).map(opcao => renderOpcaoConfiguracaoPDV(grupo, opcao)).join('')}
    </div>
  </section>`;
}

function renderOpcaoConfiguracaoPDV(grupo, opcao) {
  const selecionada = selecaoAtualPDV(grupo.grupoId, opcao.opcaoId);
  const quantidade = selecionada ? selecionada.quantidade : 0;
  const indisponivel = !opcao.disponivel || !opcao.ativa;
  const motivo = motivoOpcaoPDV(opcao);
  const classe = 'pdv-config-option' + (selecionada ? ' pdv-config-option--selected' : '') + (indisponivel ? ' pdv-config-option--disabled' : '');
  const acrescimo = Number(opcao.acrescimo || 0);
  const preco = acrescimo ? `+ ${formatarMoedaPDV(acrescimo)}` : '';
  const tipo = grupo.tipoSelecao;
  const controle = tipo === 'QUANTIDADE'
    ? `<div class="pdv-config-stepper">
        <button type="button" onclick="alterarQuantidadeOpcaoPDV(${grupo.grupoId}, ${opcao.opcaoId}, -1)" ${indisponivel ? 'disabled' : ''}>-</button>
        <span>${quantidade}</span>
        <button type="button" onclick="alterarQuantidadeOpcaoPDV(${grupo.grupoId}, ${opcao.opcaoId}, 1)" ${indisponivel ? 'disabled' : ''}>+</button>
      </div>`
    : `<input type="${tipo === 'UNICA' ? 'radio' : 'checkbox'}" ${selecionada ? 'checked' : ''} ${indisponivel ? 'disabled' : ''} aria-label="${escapeHtml(opcao.nome)}">`;
  return `<button type="button" class="${classe}" onclick="selecionarOpcaoPDV(${grupo.grupoId}, ${opcao.opcaoId})" ${indisponivel ? 'disabled' : ''}>
    <span class="pdv-config-option__control">${controle}</span>
    <span class="pdv-config-option__body">
      <strong>${escapeHtml(opcao.nome)}</strong>
      <small>${escapeHtml(motivo || opcao.codigo || '')}</small>
    </span>
    <span class="pdv-config-option__price">${escapeHtml(preco)}</span>
  </button>`;
}

function selecaoAtualPDV(grupoId, opcaoId) {
  const estado = pdvConfiguracaoAtual;
  if (!estado) return null;
  return (estado.selecoes || []).find(selecao => Number(selecao.grupoId) === Number(grupoId) && Number(selecao.opcaoId) === Number(opcaoId)) || null;
}

function grupoConfiguracaoPDV(grupoId) {
  return ((pdvConfiguracaoAtual && pdvConfiguracaoAtual.configuracao && pdvConfiguracaoAtual.configuracao.grupos) || [])
    .find(grupo => Number(grupo.grupoId) === Number(grupoId));
}

function opcaoConfiguracaoPDV(grupo, opcaoId) {
  return ((grupo && grupo.opcoes) || []).find(opcao => Number(opcao.opcaoId) === Number(opcaoId));
}

function motivoOpcaoPDV(opcao) {
  const motivo = Array.isArray(opcao.motivos) && opcao.motivos.length ? opcao.motivos[0] : null;
  const alerta = Array.isArray(opcao.alertas) && opcao.alertas.length ? opcao.alertas[0] : null;
  if (motivo) return motivo.mensagem || motivo.codigo;
  if (alerta) return alerta.mensagem || alerta.codigo;
  if (opcao.requerConfirmacao) return 'Requer selecao explicita';
  return '';
}

function selecionarOpcaoPDV(grupoId, opcaoId) {
  const grupo = grupoConfiguracaoPDV(grupoId);
  const opcao = opcaoConfiguracaoPDV(grupo, opcaoId);
  if (!grupo || !opcao || !opcao.disponivel || !opcao.ativa) return;
  const atual = selecaoAtualPDV(grupoId, opcaoId);
  if (grupo.tipoSelecao === 'QUANTIDADE') {
    alterarQuantidadeOpcaoPDV(grupoId, opcaoId, atual ? -atual.quantidade : 1);
    return;
  }
  if (grupo.tipoSelecao === 'UNICA') {
    pdvConfiguracaoAtual.selecoes = (pdvConfiguracaoAtual.selecoes || [])
      .filter(selecao => Number(selecao.grupoId) !== Number(grupoId));
    pdvConfiguracaoAtual.selecoes.push({ grupoId, opcaoId, quantidade: 1, padrao: false });
  } else if (atual) {
    pdvConfiguracaoAtual.selecoes = pdvConfiguracaoAtual.selecoes
      .filter(selecao => !(Number(selecao.grupoId) === Number(grupoId) && Number(selecao.opcaoId) === Number(opcaoId)));
  } else {
    pdvConfiguracaoAtual.selecoes.push({ grupoId, opcaoId, quantidade: 1, padrao: false });
  }
  renderConfiguracaoPDV();
  validarConfiguracaoVisualPDV();
}

function alterarQuantidadeOpcaoPDV(grupoId, opcaoId, delta) {
  const grupo = grupoConfiguracaoPDV(grupoId);
  const opcao = opcaoConfiguracaoPDV(grupo, opcaoId);
  if (!grupo || !opcao || !opcao.disponivel || !opcao.ativa) return;
  const atual = selecaoAtualPDV(grupoId, opcaoId);
  const maxOpcao = opcao.quantidadeMaximaPorItem || grupo.maximo || 99;
  const nova = Math.max(0, Math.min(maxOpcao, (atual ? atual.quantidade : 0) + delta));
  pdvConfiguracaoAtual.selecoes = (pdvConfiguracaoAtual.selecoes || [])
    .filter(selecao => !(Number(selecao.grupoId) === Number(grupoId) && Number(selecao.opcaoId) === Number(opcaoId)));
  if (nova > 0) {
    pdvConfiguracaoAtual.selecoes.push({ grupoId, opcaoId, quantidade: nova, padrao: false });
  }
  renderConfiguracaoPDV();
  validarConfiguracaoVisualPDV();
}

function alterarQuantidadeConfiguracaoPDV(valor) {
  const quantidade = Math.max(1, parseInt(valor, 10) || 1);
  if (pdvConfiguracaoAtual) pdvConfiguracaoAtual.quantidadeItem = quantidade;
  atualizarResumoConfiguracaoPDV();
  validarConfiguracaoVisualPDV();
}

function alterarObservacaoConfiguracaoPDV(valor) {
  if (pdvConfiguracaoAtual) pdvConfiguracaoAtual.observacao = valor || '';
}

async function trocarVariacaoConfiguracaoPDV() {
  const select = document.getElementById('pdv-config-variacao');
  if (!select || !pdvConfiguracaoAtual) return;
  const variacao = pdvVariacoesPorId[Number(select.value)];
  if (!variacao) return;
  await abrirConfiguracaoItemPDV(pdvConfiguracaoAtual.item, variacao, pdvConfiguracaoAtual.itemVendaExistente);
}

function validarConfiguracaoLocalPDV() {
  const estado = pdvConfiguracaoAtual;
  if (!estado || !estado.configuracao) return { valido: false, mensagem: 'Configuracao nao carregada.' };
  for (const grupo of (estado.configuracao.grupos || [])) {
    const totalGrupo = (estado.selecoes || [])
      .filter(selecao => Number(selecao.grupoId) === Number(grupo.grupoId))
      .reduce((soma, selecao) => soma + Number(selecao.quantidade || 0), 0);
    if (grupo.obrigatorio && totalGrupo < grupo.minimo) {
      return { valido: false, mensagem: `${grupo.nome}: selecione ao menos ${grupo.minimo}.` };
    }
    if (totalGrupo < grupo.minimo) {
      return { valido: false, mensagem: `${grupo.nome}: minimo ${grupo.minimo}.` };
    }
    if (totalGrupo > grupo.maximo) {
      return { valido: false, mensagem: `${grupo.nome}: maximo ${grupo.maximo}.` };
    }
    for (const selecao of estado.selecoes || []) {
      if (Number(selecao.grupoId) !== Number(grupo.grupoId)) continue;
      const opcao = opcaoConfiguracaoPDV(grupo, selecao.opcaoId);
      if (!opcao || !opcao.ativa || !opcao.disponivel) {
        return { valido: false, mensagem: `${grupo.nome}: opcao indisponivel.` };
      }
    }
  }
  return { valido: true, mensagem: '' };
}

function validarConfiguracaoVisualPDV() {
  const resultado = validarConfiguracaoLocalPDV();
  const btn = document.getElementById('btn-pdv-config-confirmar');
  if (btn) btn.disabled = !resultado.valido;
  const alerta = document.getElementById('alert-pdv-configuracao');
  if (resultado.valido) {
    limparAlert('alert-pdv-configuracao');
  } else if (alerta) {
    mostrarAlert('alert-pdv-configuracao', resultado.mensagem, 'info');
  }
  return resultado;
}

function atualizarResumoConfiguracaoPDV(validacaoBackend) {
  const estado = pdvConfiguracaoAtual;
  if (!estado || !estado.configuracao) return;
  const linhas = [];
  let acrescimos = 0;
  (estado.configuracao.grupos || []).forEach(grupo => {
    const selecionadas = (estado.selecoes || [])
      .filter(selecao => Number(selecao.grupoId) === Number(grupo.grupoId))
      .map(selecao => {
        const opcao = opcaoConfiguracaoPDV(grupo, selecao.opcaoId);
        if (!opcao) return null;
        const total = Number(opcao.acrescimo || 0) * Number(selecao.quantidade || 1);
        acrescimos += total;
        return `${opcao.nome}${Number(selecao.quantidade || 1) > 1 ? ' x' + selecao.quantidade : ''}${total ? ' +' + formatarMoedaPDV(total) : ''}`;
      })
      .filter(Boolean);
    if (selecionadas.length) linhas.push(`<div><strong>${escapeHtml(grupo.nome)}:</strong> ${escapeHtml(selecionadas.join(', '))}</div>`);
  });
  const precoUnitario = validacaoBackend && validacaoBackend.precoUnitarioFinal !== undefined
    ? Number(validacaoBackend.precoUnitarioFinal)
    : Number(estado.configuracao.precoBase || 0) + acrescimos;
  const quantidade = Number(estado.quantidadeItem || 1);
  document.getElementById('pdv-config-summary').innerHTML = `
    <div class="pdv-config-summary-box">
      <div class="pdv-config-summary-list">${linhas.join('') || '<div>Sem escolhas adicionais.</div>'}</div>
      <div class="pdv-config-total">
        <span>Total unitario</span>
        <strong>${formatarMoedaPDV(precoUnitario)}</strong>
      </div>
      <div class="pdv-config-total pdv-config-total--sub">
        <span>Total do item</span>
        <strong>${formatarMoedaPDV(precoUnitario * quantidade)}</strong>
      </div>
    </div>`;
}

async function confirmarConfiguracaoPDV() {
  if (pdvFinalizando || pdvLimpandoCarrinho || pdvTentativaFinalizacao) {
    mostrarAlert('alert-pdv-configuracao', 'Existe uma operação pendente. Verifique o pedido antes de alterar itens.', 'error');
    return;
  }
  const estado = pdvConfiguracaoAtual;
  if (!estado || !estado.configuracao) return;
  const local = validarConfiguracaoVisualPDV();
  if (!local.valido) return;

  const btn = document.getElementById('btn-pdv-config-confirmar');
  setLoading(btn, true);
  limparAlert('alert-pdv-configuracao');
  try {
    const contexto = contextoOperacionalPDV();
    const payloadValidacao = payloadItemVendaPDV();
    const validacao = await validarConfiguracaoBackendPDV(estado.variacao.id, contexto, payloadValidacao);
    if (!validacao.valida || !validacao.disponivel) {
      mostrarAlert('alert-pdv-configuracao', mensagemValidacaoPDV(validacao), 'error');
      return;
    }
    atualizarResumoConfiguracaoPDV(validacao);
    await persistirItemConfiguradoPDV(payloadValidacao);
    fecharModal('modal-pdv-configuracao');
  } catch (error) {
    mostrarAlert('alert-pdv-configuracao', error.message || 'Nao foi possivel adicionar o item.', 'error');
  } finally {
    setLoading(btn, false);
  }
}

function payloadItemVendaPDV() {
  const estado = pdvConfiguracaoAtual;
  return {
    variacaoId: estado.variacao.id,
    quantidade: String(estado.quantidadeItem || 1),
    observacao: estado.observacao || null,
    configuracaoVersao: estado.configuracao.configuracaoVersao,
    selecoes: (estado.selecoes || []).map(selecao => ({
      grupoId: selecao.grupoId,
      opcaoId: selecao.opcaoId,
      quantidade: selecao.quantidade || 1
    }))
  };
}

async function validarConfiguracaoBackendPDV(variacaoId, contexto, itemPayload) {
  const resp = await fetch(`/api/cardapio/variacoes/${variacaoId}/validar-configuracao`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      filialId: contexto.filialId,
      canal: contexto.canal,
      quantidadeItem: itemPayload.quantidade,
      configuracaoVersao: itemPayload.configuracaoVersao,
      selecoes: itemPayload.selecoes
    })
  });
  if (!resp.ok) {
    throw new Error(await safeReadErrorMessage(resp, 'Configuracao invalida.'));
  }
  return resp.json();
}

async function persistirItemConfiguradoPDV(itemPayload) {
  if (pdvConfiguracaoAtual.modo === 'editar' && pdvVendaAtual && pdvConfiguracaoAtual.itemVendaExistente) {
    const itemId = pdvConfiguracaoAtual.itemVendaExistente.id;
    const resp = await fetch(`/api/vendas/${pdvVendaAtual.id}/itens/${itemId}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(itemPayload)
    });
    if (!resp.ok) throw new Error(await safeReadErrorMessage(resp, 'Erro ao editar item.'));
    pdvDraftPayloadPorItemVenda[itemId] = itemPayload;
    await recarregarVendaAtualPDV();
    return;
  }

  if (!pdvVendaAtual) {
    const contexto = contextoOperacionalPDV();
    const resp = await fetch('/api/vendas', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ filialId: contexto.filialId, canal: contexto.canal, itens: [itemPayload] })
    });
    if (!resp.ok) throw new Error(await safeReadErrorMessage(resp, 'Erro ao criar venda.'));
    pdvVendaAtual = await resp.json();
    registrarPayloadNovoItemPDV(itemPayload);
  } else {
    const resp = await fetch(`/api/vendas/${pdvVendaAtual.id}/itens`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(itemPayload)
    });
    if (!resp.ok) throw new Error(await safeReadErrorMessage(resp, 'Erro ao adicionar item.'));
    const itemCriado = await resp.json();
    pdvDraftPayloadPorItemVenda[itemCriado.id] = itemPayload;
    await recarregarVendaAtualPDV();
  }
  pdvCarrinho = itensAtivosVendaPDV();
  renderCarrinho();
  renderCardapio();
}

function registrarPayloadNovoItemPDV(itemPayload) {
  const candidatos = itensAtivosVendaPDV().filter(item => Number(item.variacaoId) === Number(itemPayload.variacaoId));
  const item = candidatos[candidatos.length - 1];
  if (item) pdvDraftPayloadPorItemVenda[item.id] = itemPayload;
}

async function recarregarVendaAtualPDV() {
  if (!pdvVendaAtual) return;
  const resp = await fetch(`/api/vendas/${pdvVendaAtual.id}`);
  if (resp.ok) pdvVendaAtual = await resp.json();
  pdvCarrinho = itensAtivosVendaPDV();
  renderCarrinho();
  renderCardapio();
}

function itensAtivosVendaPDV() {
  return ((pdvVendaAtual && pdvVendaAtual.itens) || []).filter(item => item.status !== 'CANCELADO');
}

async function editarItemCarrinhoPDV(itemVendaId) {
  const itemVenda = itensAtivosVendaPDV().find(item => Number(item.id) === Number(itemVendaId));
  if (!itemVenda) return;
  const item = pdvItensMap[itemVenda.itemCardapioId];
  const variacao = pdvVariacoesPorId[itemVenda.variacaoId] || (await carregarVariacoesItemPDV(itemVenda.itemCardapioId)).find(v => v.id === itemVenda.variacaoId);
  if (!item || !variacao) return;
  await abrirConfiguracaoItemPDV(item, variacao, itemVenda);
}

async function removerItemCompleto(itemVendaId) {
  if (!pdvVendaAtual || pdvLimpandoCarrinho || pdvFinalizando || pdvTentativaFinalizacao) return;
  // Item de comanda pode já ter sido servido: sai com o gerente e com motivo.
  if (pdvComandaAtual) { abrirRetiradaPDV([Number(itemVendaId)]); return; }
  const resp = await fetch(`/api/vendas/${pdvVendaAtual.id}/itens/${itemVendaId}`, { method: 'DELETE' });
  if (!resp.ok) {
    mostrarAlert('alert-pdv', await safeReadErrorMessage(resp, 'Erro ao remover item.'), 'error');
    return;
  }
  delete pdvDraftPayloadPorItemVenda[itemVendaId];
  await recarregarVendaAtualPDV();
}

async function limparCarrinho() {
  if (!pdvVendaAtual || pdvLimpandoCarrinho || pdvFinalizando || pdvTentativaFinalizacao) return;
  const vendaId = pdvVendaAtual.id;
  const ids = itensAtivosVendaPDV().map(item => item.id);
  if (!ids.length) return;
  if (pdvComandaAtual) { abrirRetiradaPDV(ids.map(Number)); return; }

  pdvLimpandoCarrinho = true;
  const button = document.getElementById('btn-pdv-limpar');
  if (button) button.disabled = true;
  let falha = false;
  try {
    // Mutação sequencial: interromper na primeira falha sem apagar o carrinho local.
    for (const id of ids) {
      const resposta = await fetch(`/api/vendas/${vendaId}/itens/${id}`, { method: 'DELETE' });
      if (!resposta.ok) {
        falha = true;
        break;
      }
      delete pdvDraftPayloadPorItemVenda[id];
    }
  } catch (error) {
    falha = true;
    console.warn('PDV: falha ao limpar carrinho', error);
  }
  try {
    // Em todos os casos consultar o servidor; resposta GET não-OK não é confirmação.
    const atualizada = await fetch(`/api/vendas/${vendaId}`);
    if (!atualizada.ok) throw new Error('Não foi possível confirmar o estado do pedido.');
    pdvVendaAtual = await atualizada.json();
    pdvCarrinho = itensAtivosVendaPDV();
    if (pdvCarrinho.length > 0) falha = true;
    if (!pdvCarrinho.length) pdvDraftPayloadPorItemVenda = {};
    renderCarrinho();
    renderCardapio();
  } catch (error) {
    falha = true;
    console.warn('PDV: carrinho não sincronizado', error);
  } finally {
    pdvLimpandoCarrinho = false;
    if (button) button.disabled = false;
  }
  if (falha) {
    mostrarAlert('alert-pdv', 'Não foi possível confirmar a limpeza completa. Confira os itens antes de prosseguir.', 'error');
  }
}

function quantidadeItemNoCarrinhoPDV(itemId) {
  return itensAtivosVendaPDV()
    .filter(item => Number(item.itemCardapioId) === Number(itemId))
    .reduce((soma, item) => soma + Number(item.quantidade || 0), 0);
}

function resumoModificadoresCarrinhoPDV(itemVenda) {
  return (itemVenda.modificadores || [])
    .map(modificador => `${escapeHtml(modificador.opcao)}${modificador.quantidadeSelecionada > 1 ? ' x' + modificador.quantidadeSelecionada : ''}`)
    .join(' · ');
}

function mensagemValidacaoPDV(validacao) {
  const motivo = Array.isArray(validacao.motivos) && validacao.motivos.length ? validacao.motivos[0] : null;
  return motivo ? (motivo.mensagem || motivo.codigo) : 'Configuracao indisponivel.';
}

function variacaoDisponivelPDV(variacaoId) {
  const operacional = Object.values(pdvDisponibilidadePorItem)
    .flatMap(entry => entry.variacoes || [])
    .find(variacao => Number(variacao.variacaoId) === Number(variacaoId));
  return !operacional || operacional.disponivel;
}

function gerarIdempotencyKeyPDV(prefixo) {
  return `pdv-${prefixo}-${pdvNumeroCaixa || 'caixa'}-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function garantirModalConfiguracaoPDV() {
  let modal = document.getElementById('modal-pdv-configuracao');
  if (modal) return modal;
  modal = document.createElement('div');
  modal.className = 'modal-overlay';
  modal.id = 'modal-pdv-configuracao';
  modal.setAttribute('onclick', "fecharModalSeClicouFora(event,'modal-pdv-configuracao')");
  modal.innerHTML = `
    <div class="modal modal--wide pdv-config-modal">
      <div class="modal-header">
        <div>
          <h3 class="modal-title" id="pdv-config-title">Montar item</h3>
          <div class="modal-sub" id="pdv-config-subtitle"></div>
        </div>
        <button class="modal-close" type="button" onclick="fecharModal('modal-pdv-configuracao')" aria-label="Fechar"></button>
      </div>
      <div class="modal-body modal-body--scroll">
        <div id="pdv-config-body"></div>
        <div id="pdv-config-summary"></div>
        <div class="alert-modal" id="alert-pdv-configuracao"></div>
      </div>
      <div class="modal-footer">
        <button class="btn-secondary" type="button" onclick="fecharModal('modal-pdv-configuracao')">Cancelar</button>
        <button class="btn-primary" type="button" id="btn-pdv-config-confirmar" onclick="confirmarConfiguracaoPDV()">Adicionar</button>
      </div>
    </div>`;
  document.body.appendChild(modal);
  aplicarIconesEstaticos(modal);
  return modal;
}

function calcularTotalCarrinho() {
  return pdvCarrinho.reduce((total, entry) => total + Number(entry.subtotal || 0), 0);
}

function renderCarrinho() {
  const container = document.getElementById('pdv-carrinho-itens');
  const empty = document.getElementById('pdv-carrinho-empty');
  const btnFin = document.getElementById('btn-pdv-finalizar');
  const elTotal = document.getElementById('pdv-total');
  const elSub = document.getElementById('pdv-subtotal');
  const elQtd = document.getElementById('pdv-carrinho-qtd');

  pdvCarrinho = itensAtivosVendaPDV();
  const total = calcularTotalCarrinho();
  const qtdItens = pdvCarrinho.reduce((soma, entry) => soma + Number(entry.quantidade || 0), 0);

  if (elQtd) elQtd.textContent = qtdItens > 0 ? `${qtdItens} ite${qtdItens === 1 ? 'm' : 'ns'}` : '';
  const mobileBadge = document.getElementById('pdv-mobile-cart-badge');
  if (mobileBadge) mobileBadge.textContent = `${qtdItens} ite${qtdItens === 1 ? 'm' : 'ns'}`;

  if (pdvCarrinho.length === 0 && typeof cancelarPagamentoPDV === 'function'
      && !document.getElementById('pdv-pagamento')?.hidden) {
    cancelarPagamentoPDV();
  }

  if (pdvCarrinho.length === 0) {
    empty.hidden = false;
    container.querySelectorAll('.pdv-item-row').forEach(el => el.remove());
    elTotal.textContent = 'R$ 0,00';
    if (elSub) elSub.textContent = 'R$ 0,00';
    btnFin.disabled = true;
    btnFin.classList.remove('pdv-submit--success');
    return;
  }

  empty.hidden = true;
  btnFin.disabled = false;
  btnFin.classList.remove('pdv-submit--success');
  container.querySelectorAll('.pdv-item-row').forEach(el => el.remove());

  pdvCarrinho.forEach(entry => {
    const item = pdvItensMap[entry.itemCardapioId];
    const categoria = item ? getCategoriaByCodigo(item.categoriaCodigo) : { codigo: 'todos', nomeExibicao: 'Cardapio' };
    const config = getCategoriaConfig(categoria.codigo);
    const resumo = resumoModificadoresCarrinhoPDV(entry);
    const row = document.createElement('div');
    row.className = 'pdv-item-row pdv-item-row--configured';
    row.style.setProperty('--pdv-accent', config.accent);
    row.style.setProperty('--pdv-soft', config.soft);
    row.innerHTML = `
      <div class="pdv-item-icon">${iconMarkup(config.icon)}</div>
      <div class="pdv-item-content">
        <div class="pdv-item-name">${escapeHtml(entry.item)}</div>
        <div class="pdv-item-meta">${escapeHtml(entry.variacao)} - ${formatarMoedaPDV(entry.precoUnitarioFinal)} x ${Number(entry.quantidade || 0)}</div>
        ${resumo ? `<div class="pdv-item-mods">${resumo}</div>` : ''}
      </div>
      <div class="pdv-item-subtotal">${formatarMoedaPDV(entry.subtotal)}</div>
      <div class="pdv-item-controls">
        <button class="pdv-item-btn" onclick="editarItemCarrinhoPDV(${entry.id})" title="Editar item" type="button">${iconMarkup('clipboard')}</button>
        <button class="pdv-item-btn pdv-item-btn--remove" onclick="removerItemCompleto(${entry.id})" title="Remover item" type="button">x</button>
      </div>`;
    container.appendChild(row);
  });

  const desconto = descontoAtualPDV();
  elTotal.textContent = formatarMoedaPDV(total);
  if (elSub) elSub.textContent = formatarMoedaPDV(total + desconto);
  const linhaDesconto = document.getElementById('pdv-desconto-linha');
  if (linhaDesconto) {
    linhaDesconto.hidden = desconto <= 0;
    document.getElementById('pdv-desconto-valor').textContent = '-' + formatarMoedaPDV(desconto);
  }
  if (!document.getElementById('pdv-pagamento')?.hidden) renderPagamentoPDV();
}

// ── Desconto no pagamento (F3) ────────────────────────────────────────────
// Vale para o total e é dado depois de bipar tudo. Voltar aos itens retira o desconto,
// para ele não mudar sozinho quando a quantidade de um item mudar.

function descontoAtualPDV() {
  return arred2(itensAtivosVendaPDV().reduce((soma, item) =>
    soma + Number(item.descontoUnitario || 0) * Number(item.quantidade || 0), 0));
}

function lerDescontoPDV(texto) {
  const limpo = String(texto || '').replace(/\s/g, '').replace(',', '.');
  if (!limpo) return { valor: 0 };
  if (limpo.endsWith('%')) {
    const percentual = Number(limpo.slice(0, -1));
    return Number.isFinite(percentual) ? { percentual } : null;
  }
  if (limpo.startsWith('=')) {
    const alvo = Number(limpo.slice(1));
    return Number.isFinite(alvo) ? { valor: arred2(calcularTotalCarrinho() + descontoAtualPDV() - alvo) } : null;
  }
  const valor = Number(limpo);
  return Number.isFinite(valor) ? { valor } : null;
}

function abrirDescontoPDV() {
  if (document.getElementById('pdv-pagamento').hidden) return;
  limparAlert('alert-pdv');
  document.getElementById('pdv-desconto-form').hidden = false;
  document.getElementById('pdv-desconto-autorizacao').hidden = true;
  ['entrada', 'gerente-nome', 'gerente-sobrenome', 'gerente-senha'].forEach(campo => {
    document.getElementById('pdv-desconto-' + campo).value = '';
  });
  document.getElementById('pdv-desconto-entrada').focus();
}

function fecharDescontoPDV() {
  const form = document.getElementById('pdv-desconto-form');
  if (form) form.hidden = true;
  document.getElementById('pdv-pag-recebido')?.focus();
}

async function enviarDescontoPDV(corpo) {
  const resp = await fetch(`/api/vendas/${pdvVendaAtual.id}/desconto`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(corpo)
  });
  if (resp.ok) {
    pdvVendaAtual = await resp.json();
    pdvPagamentos = [];
    pdvDivisao = null;
    renderCarrinho();
  }
  return resp;
}

async function aplicarDescontoPDV() {
  if (!pdvVendaAtual) return;
  limparAlert('alert-pdv');
  const pedido = lerDescontoPDV(document.getElementById('pdv-desconto-entrada').value);
  if (!pedido) {
    mostrarAlert('alert-pdv', 'Digite o desconto: 5 para R$ 5, 10% ou =50 para fechar em R$ 50.', 'error');
    return;
  }
  const corpo = { ...pedido };
  const comDesconto = (pedido.valor || pedido.percentual || 0) > 0;
  const autorizacao = document.getElementById('pdv-desconto-autorizacao');
  if (comDesconto && !temPermissao(PERMISSOES.VER_FINANCAS)) {
    if (autorizacao.hidden) {
      autorizacao.hidden = false;
      mostrarAlert('alert-pdv', 'O desconto precisa do gerente: ele digita o login aqui.', 'error');
      document.getElementById('pdv-desconto-gerente-nome').focus();
      return;
    }
    const nome = document.getElementById('pdv-desconto-gerente-nome').value.trim();
    const sobrenome = document.getElementById('pdv-desconto-gerente-sobrenome').value.trim();
    const senha = document.getElementById('pdv-desconto-gerente-senha').value;
    if (!nome || !senha) {
      mostrarAlert('alert-pdv', 'O gerente precisa digitar nome, sobrenome e senha.', 'error');
      return;
    }
    corpo.autorizacao = { nome, sobrenome, senha };
  }
  const botao = document.getElementById('btn-pdv-aplicar-desconto');
  setLoading(botao, true);
  try {
    const resp = await enviarDescontoPDV(corpo);
    if (!resp.ok) {
      const mensagem = await safeReadErrorMessage(resp, 'Não foi possível aplicar o desconto.');
      mostrarAlert('alert-pdv', /gerente|permiss|autoriz/i.test(mensagem) && corpo.autorizacao
        ? 'Login do gerente não confere ou ele não pode dar desconto.' : mensagem, 'error');
      document.getElementById('pdv-desconto-gerente-senha').value = '';
      return;
    }
    selecionarFormaPDV(pdvFormaSelecionada);
    fecharDescontoPDV();
    const desconto = descontoAtualPDV();
    mostrarAlert('alert-pdv', desconto > 0
      ? `Desconto de ${formatarMoedaPDV(desconto)} aplicado. Total: ${formatarMoedaPDV(calcularTotalCarrinho())}.`
      : 'Desconto retirado.', 'success');
  } finally {
    setLoading(botao, false);
  }
}

async function retirarDescontoPDV() {
  if (!pdvVendaAtual || descontoAtualPDV() <= 0) return;
  const resp = await enviarDescontoPDV({ valor: 0 });
  if (resp.ok) {
    mostrarAlert('alert-pdv', 'Desconto retirado. Se ainda valer, aplique de novo no pagamento (F3).', 'info');
  } else {
    mostrarAlert('alert-pdv', await safeReadErrorMessage(resp, 'Não foi possível retirar o desconto.'), 'error');
  }
}

async function finalizarVendaPDV() {
  if (!pdvVendaAtual || pdvCarrinho.length === 0 || pdvFinalizando || pdvLimpandoCarrinho) return;

  const vendaId = pdvVendaAtual.id;
  // Uma chave por tentativa de uma mesma venda. Reutilizar quando a
  // resposta for incerta; a API verifica idempotência no PostgreSQL.
  if (!pdvTentativaFinalizacao || pdvTentativaFinalizacao.vendaId !== vendaId) {
    pdvTentativaFinalizacao = { vendaId, chave: gerarIdempotencyKeyPDV('finalizar') };
  }

  const btn = document.getElementById('btn-pdv-confirmar-pagamento') || document.getElementById('btn-pdv-finalizar');
  const total = calcularTotalCarrinho();
  const troco = trocoPDV();
  const pagamentos = pdvPagamentos.map(p => ({
    forma: p.forma,
    valor: arred2(p.valor).toFixed(2),
    valorRecebido: p.valorRecebido == null ? null : arred2(p.valorRecebido).toFixed(2)
  }));
  pdvFinalizando = true;
  setLoading(btn, true);
  limparAlert('alert-pdv');

  try {
    const resp = await fetch(`/api/vendas/${vendaId}/finalizar`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Idempotency-Key': pdvTentativaFinalizacao.chave
      },
      body: JSON.stringify({ numeroCaixaLegado: pdvNumeroCaixa, pagamentos })
    });
    if (!resp.ok) {
      const msg = await safeReadErrorMessage(resp, 'Erro ao finalizar venda.');
      // Recusa clara (4xx): a venda não foi feita, a próxima tentativa usa outra chave.
      // Erro do servidor: o resultado é incerto, a próxima tentativa reusa a mesma chave.
      if (resp.status >= 400 && resp.status < 500) pdvTentativaFinalizacao = null;
      mostrarAlert('alert-pdv', resp.status >= 500
        ? msg + ' Mantenha o pedido e verifique o resultado antes de alterá-lo.' : msg, 'error');
      bipPDV(false);
      await recarregarDisponibilidadePDV();
      renderCardapio();
      return;
    }

    const vendaFinalizada = await resp.json();
    if (vendaFinalizada.status !== 'FINALIZADA') {
      mostrarAlert('alert-pdv', 'O servidor não confirmou a finalização. Tente novamente sem alterar o pedido.', 'error');
      return;
    }
    pdvTentativaFinalizacao = null;
    // Venda confirmada: libera o pedido antes de tudo; o resto é tela e cupom.
    const comandaPaga = pdvComandaAtual;
    const pagamentosCupom = pdvPagamentos.map(p => ({ ...p }));
    pdvComandaAtual = null;
    pdvCarrinho = [];
    pdvVendaAtual = null;
    pdvDraftPayloadPorItemVenda = {};
    showToast(`${formatarMoedaPDV(total)} registrado no Caixa ${pdvNumeroCaixa}${troco > 0 ? ` · TROCO ${formatarMoedaPDV(troco)}` : ''}`, 'success');
    pdvUltimoCupom = dadosCupomPDV({
      titulo: 'CUPOM NÃO FISCAL',
      venda: vendaFinalizada,
      comanda: comandaPaga,
      pagamentos: pagamentosCupom,
      troco
    });
    // Próximo cliente: o PDV continua aberto, com carrinho limpo e foco na leitura.
    atualizarComandaPDV();
    cancelarPagamentoPDV();
    renderCarrinho();
    const mensagemTroco = troco > 0 ? ` · TROCO ${formatarMoedaPDV(troco)}` : '';
    const rotulo = comandaPaga ? `Comanda ${comandaPaga.numero} paga: ` : 'Venda de ';
    mostrarAlert('alert-pdv', `${rotulo}${formatarMoedaPDV(total)} registrada${mensagemTroco}.`, 'success');
    if (cupomAutomaticoPDV()) imprimirCupomPDV(pdvUltimoCupom);
    else acrescentarBotaoAlertaPDV('Imprimir cupom (F8)', () => imprimirCupomPDV(pdvUltimoCupom));
    void carregarCaixas();
    focarLeituraPDV();
  } catch (error) {
    console.warn('PDV: resultado de finalização não confirmado', error);
    if (pdvVendaAtual) {
      mostrarAlert('alert-pdv',
        'A resposta da venda não foi confirmada. Não inicie outra venda antes de conferir o resultado; tente novamente com o mesmo pedido.',
        'error');
    }
  } finally {
    pdvFinalizando = false;
    setLoading(btn, false);
  }
}

function formatarMoedaPDV(valor) {
  return new Intl.NumberFormat('pt-BR', {
    style: 'currency',
    currency: 'BRL',
    minimumFractionDigits: 2
  }).format(Number(valor || 0));
}

async function recarregarDisponibilidadePDV() {
  const resp = await fetch(urlDisponibilidadePDV(contextoOperacionalPDV()));
  if (!resp.ok) return;
  pdvDisponibilidadePorItem = normalizarDisponibilidadePDV(await resp.json());
  pdvItens.forEach(item => {
    item.disponibilidadeOperacional = pdvDisponibilidadePorItem[item.id] || null;
  });
}

function contextoOperacionalPDV() {
  if (typeof getFilialOperacionalAtual === 'function' && typeof getCanalVendaAtual === 'function') {
    return { filialId: getFilialOperacionalAtual(), canal: getCanalVendaAtual() };
  }
  if (typeof resolverContextoOperacionalCardapio === 'function') {
    return resolverContextoOperacionalCardapio();
  }
  console.warn('Contexto operacional indisponivel no PDV. Usando fallback temporario.');
  return { filialId: 1, canal: 'BALCAO' };
}

function urlDisponibilidadePDV(contexto) {
  const filialId = encodeURIComponent(contexto.filialId);
  const canal = encodeURIComponent(contexto.canal);
  return `/api/cardapio/disponibilidade?filialId=${filialId}&canal=${canal}`;
}

function normalizarDisponibilidadePDV(payload) {
  const porItem = {};
  const itens = Array.isArray(payload && payload.itens) ? payload.itens : [];
  const variacoes = Array.isArray(payload && payload.variacoes) ? payload.variacoes : [];

  itens.forEach(resumo => {
    porItem[resumo.itemCardapioId] = {
      resumo,
      variacoes: variacoes.filter(variacao => variacao.itemCardapioId === resumo.itemCardapioId)
    };
  });
  return porItem;
}

function itemDisponivelParaSelecaoPDV(item) {
  if (!item || !item.disponivel) return false;
  const operacional = item.disponibilidadeOperacional;
  return !operacional || operacional.resumo.itemDisponivel;
}

function resumoMotivoIndisponibilidade(operacional) {
  const variacao = (operacional.variacoes || []).find(v => !v.disponivel) || {};
  const motivo = Array.isArray(variacao.motivos) && variacao.motivos.length ? variacao.motivos[0] : null;
  if (!motivo) return 'Indisponivel agora';
  if (motivo.codigo === 'ESTOQUE_INSUFICIENTE' && motivo.produtoNome) {
    return `Indisponivel - falta de ${motivo.produtoNome}`;
  }
  return motivo.mensagem || motivo.codigo || 'Indisponivel agora';
}

function primeiroAlertaDisponibilidade(operacional) {
  const variacao = (operacional.variacoes || []).find(v => v.disponivel && Array.isArray(v.alertas) && v.alertas.length);
  return variacao ? variacao.alertas[0] : null;
}

function capacidadeDisponivelItem(operacional) {
  const capacidades = (operacional.variacoes || [])
    .filter(v => v.disponivel && typeof v.capacidadeEstimada === 'number')
    .map(v => v.capacidadeEstimada);
  if (!capacidades.length) return null;
  return Math.max.apply(null, capacidades);
}

// ── Varejo de balcão: leitura de código de barras ─────────────────────────
// O leitor USB funciona como teclado: "digita" o código e aperta Enter.
// O campo #pdv-leitura fica sempre com o foco. "6*789..." lança 6 unidades.

let pdvCodigosCache = {};
let pdvProximoAlternativo = false;
let pdvPagamentos = [];
let pdvFormaSelecionada = 'DINHEIRO';

const PDV_FORMAS = [
  { codigo: 'DINHEIRO', rotulo: 'Dinheiro', tecla: 'F5' },
  { codigo: 'PIX', rotulo: 'Pix', tecla: 'F6' },
  { codigo: 'DEBITO', rotulo: 'Débito', tecla: 'F7' },
  { codigo: 'CREDITO', rotulo: 'Crédito', tecla: 'F8' }
];

function focarLeituraPDV() {
  const campo = document.getElementById('pdv-leitura');
  if (campo && !document.getElementById('modal-pdv-configuracao')?.classList.contains('open')) {
    campo.focus();
  }
}

let pdvAudio = null;
function bipPDV(sucesso) {
  try {
    pdvAudio = pdvAudio || new (window.AudioContext || window.webkitAudioContext)();
    const osc = pdvAudio.createOscillator();
    const ganho = pdvAudio.createGain();
    osc.frequency.value = sucesso ? 1320 : 220;
    ganho.gain.value = 0.08;
    osc.connect(ganho);
    ganho.connect(pdvAudio.destination);
    osc.start();
    osc.stop(pdvAudio.currentTime + (sucesso ? 0.07 : 0.35));
  } catch (_) { /* sem áudio: segue sem som */ }
}

async function carregarCodigosPDV() {
  pdvCodigosCache = {};
  try {
    const resp = await fetch('/api/pdv/codigos');
    if (!resp.ok) return;
    const lista = await resp.json();
    lista.forEach(item => {
      const atual = pdvCodigosCache[item.codigo] || { codigo: item.codigo, tipo: item.tipo, variacao: null, alternativas: [] };
      if (item.padrao && !atual.variacao) atual.variacao = item.variacao;
      else atual.alternativas.push(item.variacao);
      pdvCodigosCache[item.codigo] = atual;
    });
    Object.values(pdvCodigosCache).forEach(leitura => {
      if (!leitura.variacao && leitura.alternativas.length) leitura.variacao = leitura.alternativas.shift();
    });
  } catch (_) {
    // Sem cache: cada leitura consulta o servidor.
  }
}

function interpretarLeituraPDV(texto) {
  const limpo = String(texto || '').replace(/\s+/g, '');
  const partes = limpo.match(/^(\d{1,3})\*(.+)$/);
  if (partes) return { quantidade: Number(partes[1]), codigo: partes[2] };
  return { quantidade: 1, codigo: limpo };
}

async function buscarCodigoPDV(codigo) {
  if (pdvCodigosCache[codigo]) return pdvCodigosCache[codigo];
  const resp = await fetch(`/api/pdv/codigos/${encodeURIComponent(codigo)}`);
  if (resp.status === 404) return null;
  if (!resp.ok) throw new Error(await safeReadErrorMessage(resp, 'Falha ao consultar o código.'));
  const leitura = await resp.json();
  pdvCodigosCache[codigo] = leitura;
  return leitura;
}

async function lerCodigoPDV(event) {
  if (event && event.key !== 'Enter') return;
  if (event) event.preventDefault();
  const campo = document.getElementById('pdv-leitura');
  const { quantidade, codigo } = interpretarLeituraPDV(campo.value);
  campo.value = '';
  if (!codigo) return;
  const numeroComanda = numeroComandaPDV(codigo);
  if (numeroComanda) {
    try {
      await abrirComandaPDV(numeroComanda);
    } finally {
      focarLeituraPDV();
    }
    return;
  }
  if (!quantidade || quantidade <= 0) {
    mostrarAlert('alert-pdv', 'Quantidade inválida.', 'error');
    bipPDV(false);
    return;
  }
  limparAlert('alert-pdv');
  try {
    const leitura = await buscarCodigoPDV(codigo);
    if (!leitura || !leitura.variacao) {
      bipPDV(false);
      avisarCodigoDesconhecidoPDV(codigo, quantidade);
      return;
    }
    const usarAlternativa = pdvProximoAlternativo && (leitura.alternativas || []).length > 0;
    const variacao = usarAlternativa ? leitura.alternativas[0] : leitura.variacao;
    pdvProximoAlternativo = false;
    atualizarIndicadorAlternativoPDV();
    await adicionarVariacaoPorCodigoPDV(variacao, quantidade);
    bipPDV(true);
  } catch (error) {
    bipPDV(false);
    mostrarAlert('alert-pdv', error.message || 'Não foi possível adicionar o item.', 'error');
  } finally {
    focarLeituraPDV();
  }
}

/**
 * Lança a variação lida no carrinho. Se o mesmo item (sem adicionais) já
 * está no carrinho, soma a quantidade na mesma linha. Itens com adicionais
 * obrigatórios abrem a tela de configuração, como no cardápio.
 */
// Venda pendente (finalização sem resposta clara ou limpeza em andamento): nada muda nos itens.
function operacaoPendentePDV() {
  if (!pdvFinalizando && !pdvLimpandoCarrinho && !pdvTentativaFinalizacao) return false;
  mostrarAlert('alert-pdv', 'Resolva a tentativa de venda pendente antes de alterar os itens.', 'error');
  return true;
}

async function adicionarVariacaoPorCodigoPDV(variacao, quantidade) {
  if (pdvFinalizando || pdvLimpandoCarrinho || pdvTentativaFinalizacao) {
    throw new Error('Resolva a tentativa de venda pendente antes de alterar os itens.');
  }
  const contexto = contextoOperacionalPDV();
  const resp = await fetch(`/api/cardapio/variacoes/${variacao.variacaoId}/configuracao?filialId=${encodeURIComponent(contexto.filialId)}&canal=${encodeURIComponent(contexto.canal)}`);
  if (!resp.ok) throw new Error(await safeReadErrorMessage(resp, 'Item indisponível para venda agora.'));
  const configuracao = await resp.json();
  const variacaoObj = { id: variacao.variacaoId, nome: variacao.variacaoNome, precoVenda: variacao.precoVenda, ativa: true };
  const item = pdvItensMap[variacao.itemCardapioId] || { id: variacao.itemCardapioId, nome: variacao.itemNome };

  if ((configuracao.grupos || []).length > 0) {
    await abrirConfiguracaoItemPDV(item, variacaoObj);
    if (pdvConfiguracaoAtual) {
      pdvConfiguracaoAtual.quantidadeItem = quantidade;
      renderConfiguracaoPDV();
    }
    return;
  }

  const existente = pdvCarrinho.find(entry =>
    Number(entry.variacaoId) === Number(variacao.variacaoId) && !resumoModificadoresCarrinhoPDV(entry));
  const quantidadeFinal = existente ? Number(existente.quantidade || 0) + quantidade : quantidade;
  const payload = {
    variacaoId: variacao.variacaoId,
    quantidade: String(quantidadeFinal),
    observacao: null,
    configuracaoVersao: configuracao.configuracaoVersao,
    selecoes: []
  };
  pdvConfiguracaoAtual = existente
    ? { modo: 'editar', item, variacao: variacaoObj, itemVendaExistente: existente, configuracao }
    : { modo: 'novo', item, variacao: variacaoObj, configuracao };
  await persistirItemConfiguradoPDV(payload);
  pdvConfiguracaoAtual = null;
}

function alternarProximoAlternativoPDV() {
  pdvProximoAlternativo = !pdvProximoAlternativo;
  atualizarIndicadorAlternativoPDV();
  focarLeituraPDV();
}

function atualizarIndicadorAlternativoPDV() {
  const el = document.getElementById('pdv-indicador-alternativo');
  if (!el) return;
  el.textContent = pdvProximoAlternativo
    ? 'Próximo item: variação alternativa (ex.: Natural)'
    : 'Próximo item: variação padrão (ex.: Gelada) · F4 troca';
  el.classList.toggle('pdv-indicador--ativo', pdvProximoAlternativo);
}

// ── Pagamento: formas, troco e divisão ────────────────────────────────────

function arred2(valor) {
  return Math.round(Number(valor || 0) * 100) / 100;
}

function totalPagoPDV() {
  return arred2(pdvPagamentos.reduce((soma, p) => soma + Number(p.valor || 0), 0));
}

function restantePDV() {
  return arred2(calcularTotalCarrinho() - totalPagoPDV());
}

function abrirPagamentoPDV() {
  if (!pdvVendaAtual || pdvCarrinho.length === 0) return;
  // Aviso antigo (ex.: código não cadastrado) ocuparia o espaço do troco.
  limparAlert('alert-pdv');
  pdvPagamentos = [];
  pdvDivisao = null;
  selecionarFormaPDV('DINHEIRO');
  document.getElementById('pdv-pagamento').hidden = false;
  document.getElementById('btn-pdv-finalizar').hidden = true;
  renderPagamentoPDV();
  setTimeout(() => document.getElementById('pdv-pag-recebido')?.focus(), 30);
}

function cancelarPagamentoPDV() {
  pdvPagamentos = [];
  pdvDivisao = null;
  const formDesconto = document.getElementById('pdv-desconto-form');
  if (formDesconto) formDesconto.hidden = true;
  retirarDescontoPDV();
  document.getElementById('pdv-pagamento').hidden = true;
  document.getElementById('btn-pdv-finalizar').hidden = false;
  focarLeituraPDV();
}

function selecionarFormaPDV(codigo) {
  pdvFormaSelecionada = codigo;
  document.querySelectorAll('[data-forma-pdv]').forEach(btn => {
    btn.classList.toggle('pdv-forma--ativa', btn.dataset.formaPdv === codigo);
  });
  const recebidoWrap = document.getElementById('pdv-pag-recebido-wrap');
  if (recebidoWrap) recebidoWrap.hidden = codigo !== 'DINHEIRO';
  const valor = document.getElementById('pdv-pag-valor');
  const sugerido = valorSugeridoPDV();
  if (valor) valor.value = sugerido > 0 ? sugerido.toFixed(2) : '';
  const recebido = document.getElementById('pdv-pag-recebido');
  if (recebido) recebido.value = '';
  renderPagamentoPDV();
}

function lerNumeroCampo(id) {
  const texto = (document.getElementById(id)?.value || '').trim().replace(',', '.');
  return texto === '' ? null : Number(texto);
}

/** Adiciona o pagamento digitado. Retorna false se não fechou a conta. */
function adicionarPagamentoPDV() {
  limparAlert('alert-pdv');
  const restante = restantePDV();
  let valor = lerNumeroCampo('pdv-pag-valor');
  if (valor == null || Number.isNaN(valor)) valor = restante;
  valor = arred2(valor);
  if (valor <= 0) { mostrarAlert('alert-pdv', 'Informe o valor do pagamento.', 'error'); return false; }
  if (valor > restante) {
    mostrarAlert('alert-pdv', `O valor passa do que falta pagar (${formatarMoedaPDV(restante)}). Para dar troco, use o campo "Recebido" no dinheiro.`, 'error');
    return false;
  }
  let recebido = null;
  if (pdvFormaSelecionada === 'DINHEIRO') {
    recebido = lerNumeroCampo('pdv-pag-recebido');
    if (recebido != null) {
      recebido = arred2(recebido);
      if (recebido < valor) { mostrarAlert('alert-pdv', 'O valor recebido é menor que o valor a pagar.', 'error'); return false; }
    }
  }
  const pagamento = { forma: pdvFormaSelecionada, valor, valorRecebido: recebido };
  if (pdvDivisao) {
    pagamento.pessoa = `Pessoa ${pdvPagamentos.length + 1}`;
    if (pdvDivisao.modo === 'itens') {
      pagamento.itens = pdvDivisao.pendente || {};
      pdvDivisao.pendente = null;
      pdvDivisao.valorPendente = null;
    }
  }
  pdvPagamentos.push(pagamento);
  selecionarFormaPDV(pdvFormaSelecionada);
  renderPagamentoPDV();
  if (pdvDivisao && restantePDV() > 0) {
    const troco = pagamento.valorRecebido != null ? arred2(pagamento.valorRecebido - pagamento.valor) : 0;
    const proximo = pdvDivisao.modo === 'iguais'
      ? `Pessoa ${pdvPagamentos.length + 1} de ${pdvDivisao.pessoas}: escolha a forma.`
      : `Escolha os itens da pessoa ${pdvPagamentos.length + 1} em "Dividir a conta" (F9).`;
    mostrarAlert('alert-pdv', `${pagamento.pessoa} pagou ${formatarMoedaPDV(pagamento.valor)}`
      + (troco > 0 ? ` · TROCO ${formatarMoedaPDV(troco)}` : '')
      + `. Falta ${formatarMoedaPDV(restantePDV())}. ${proximo}`, 'success');
  }
  return true;
}

function removerPagamentoPDV(indice) {
  pdvPagamentos.splice(indice, 1);
  selecionarFormaPDV(pdvFormaSelecionada);
}

function trocoPDV() {
  return arred2(pdvPagamentos.reduce((soma, p) =>
    soma + (p.valorRecebido != null ? Number(p.valorRecebido) - Number(p.valor) : 0), 0));
}

function renderPagamentoPDV() {
  const lista = document.getElementById('pdv-pag-lista');
  if (!lista) return;
  const rotulo = codigo => (PDV_FORMAS.find(f => f.codigo === codigo) || { rotulo: codigo }).rotulo;
  lista.innerHTML = pdvPagamentos.map((p, i) => `
    <div class="pdv-pag-linha">
      <span>${p.pessoa ? escapeHtml(p.pessoa) + ' · ' : ''}${escapeHtml(rotulo(p.forma))}</span>
      <span>${formatarMoedaPDV(p.valor)}${p.valorRecebido != null && p.valorRecebido > p.valor ? ` <small>(recebido ${formatarMoedaPDV(p.valorRecebido)})</small>` : ''}</span>
      <button type="button" class="pdv-item-btn pdv-item-btn--remove" onclick="removerPagamentoPDV(${i})" aria-label="Remover pagamento">x</button>
    </div>`).join('');
  document.getElementById('pdv-pag-restante').textContent = formatarMoedaPDV(Math.max(restantePDV(), 0));

  // Troco previsto enquanto a operadora digita o valor recebido.
  // Dividindo, o troco mostrado é o de quem está pagando agora.
  let trocoPrevisto = pdvDivisao ? 0 : trocoPDV();
  const recebidoDigitado = lerNumeroCampo('pdv-pag-recebido');
  const valorDigitado = lerNumeroCampo('pdv-pag-valor');
  if (pdvFormaSelecionada === 'DINHEIRO' && recebidoDigitado != null) {
    const base = valorDigitado == null ? restantePDV() : valorDigitado;
    trocoPrevisto = arred2(trocoPrevisto + Math.max(recebidoDigitado - base, 0));
  }
  const info = document.getElementById('pdv-divisao-info');
  if (info) {
    info.hidden = !pdvDivisao || restantePDV() <= 0;
    if (pdvDivisao) {
      const pessoa = pdvPagamentos.length + 1;
      info.textContent = pdvDivisao.modo === 'iguais'
        ? `Dividindo em ${pdvDivisao.pessoas}: pessoa ${pessoa} de ${pdvDivisao.pessoas}`
        : `Dividindo pelos itens: pessoa ${pessoa}${pdvDivisao.valorPendente != null ? ' · ' + formatarMoedaPDV(pdvDivisao.valorPendente) : ' · escolha os itens (F9)'}`;
    }
  }
  document.getElementById('pdv-pag-troco').textContent = formatarMoedaPDV(trocoPrevisto);
  document.getElementById('btn-pdv-confirmar-pagamento').textContent =
    restantePDV() <= 0 ? 'Finalizar venda (Enter)' : 'Lançar pagamento e finalizar (Enter)';
}

async function confirmarPagamentoPDV() {
  if (restantePDV() > 0) {
    const ok = adicionarPagamentoPDV();
    if (!ok) return;
  }
  if (restantePDV() > 0) {
    // Dividindo, o aviso de quem pagou e de quem vem depois já está na tela.
    if (!pdvDivisao) mostrarAlert('alert-pdv', `Ainda falta ${formatarMoedaPDV(restantePDV())}. Escolha outra forma para o restante.`, 'error');
    return;
  }
  await finalizarVendaPDV();
}

function fecharPDV() {
  // A comanda continua aberta no servidor: sair do PDV só a guarda.
  if (pdvComandaAtual) guardarComandaPDV();
  if (pdvCarrinho.length > 0
      && !window.confirm('Sair do PDV? A venda em andamento fica guardada como rascunho e não é registrada no caixa.')) {
    focarLeituraPDV();
    return;
  }
  cancelarPagamentoPDV();
  fecharModal('modal-pdv');
  if (pdvOperadorTrocado) window.location.reload();
}

// ── Troca de operador por PIN ─────────────────────────────────────────────

let pdvOperadorTrocado = false;

// ── Transferir itens para uma comanda (F6) ───────────────────────────────
// Mesa errada, cliente que mudou de mesa ou duas comandas que viram uma.

function abrirTransferenciaPDV() {
  if (operacaoPendentePDV()) return;
  if (!pdvVendaAtual || pdvCarrinho.length === 0) {
    mostrarAlert('alert-pdv', 'Não há itens para transferir.', 'info');
    return;
  }
  if (!document.getElementById('pdv-pagamento').hidden) {
    mostrarAlert('alert-pdv', 'Volte do pagamento (Esc) antes de transferir.', 'error');
    return;
  }
  limparAlert('alert-pdv-transferir');
  document.getElementById('pdv-transferir-titulo').textContent = pdvComandaAtual
    ? `Transferir itens da comanda ${pdvComandaAtual.numero}` : 'Lançar estes itens numa comanda';
  document.getElementById('pdv-transferir-destino').value = '';
  document.getElementById('pdv-transferir-lista').innerHTML = pdvCarrinho.map(item => `
    <label class="pdv-dividir-linha">
      <span>${escapeHtml(item.item)}${item.variacao ? ' ' + escapeHtml(item.variacao) : ''}<br><small>${formatarMoedaPDV(item.subtotal)}</small></span>
      <small>de ${String(Number(item.quantidade)).replace('.', ',')}</small>
      <input class="input" type="text" inputmode="decimal" data-transferir-item="${item.id}" data-maximo="${Number(item.quantidade)}" value="${Number(item.quantidade)}">
    </label>`).join('');
  abrirModal('modal-pdv-transferir');
  document.getElementById('pdv-transferir-destino').focus();
}

function marcarTransferenciaPDV(tudo) {
  document.querySelectorAll('[data-transferir-item]').forEach(campo => { campo.value = tudo ? campo.dataset.maximo : '0'; });
}

function fecharTransferenciaPDV() {
  fecharModal('modal-pdv-transferir');
  focarLeituraPDV();
}

async function confirmarTransferenciaPDV() {
  limparAlert('alert-pdv-transferir');
  const texto = document.getElementById('pdv-transferir-destino').value.trim();
  const destino = /^\d{1,4}$/.test(texto) ? Number(texto) : numeroComandaPDV(texto);
  if (!destino) {
    mostrarAlert('alert-pdv-transferir', 'Digite o número da comanda de destino, de 1 a 9999.', 'error');
    document.getElementById('pdv-transferir-destino').focus();
    return;
  }
  if (pdvComandaAtual && pdvComandaAtual.numero === destino) {
    mostrarAlert('alert-pdv-transferir', `Os itens já estão na comanda ${destino}.`, 'error');
    return;
  }
  const itens = [];
  let erro = null;
  document.querySelectorAll('[data-transferir-item]').forEach(campo => {
    const qtd = Number(campo.value.trim().replace(',', '.') || '0');
    if (!Number.isFinite(qtd) || qtd < 0 || qtd > Number(campo.dataset.maximo)) erro = 'Quantidade inválida em um dos itens.';
    else if (qtd > 0) itens.push({ itemId: Number(campo.dataset.transferirItem), quantidade: qtd });
  });
  if (erro || itens.length === 0) {
    mostrarAlert('alert-pdv-transferir', erro || 'Escolha pelo menos um item.', 'error');
    return;
  }
  const botao = document.getElementById('btn-pdv-confirmar-transferencia');
  setLoading(botao, true);
  try {
    const origem = pdvComandaAtual;
    const resp = await fetch(`/api/comandas/${destino}/transferir`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ vendaOrigemId: pdvVendaAtual.id, itens })
    });
    if (!resp.ok) {
      mostrarAlert('alert-pdv-transferir', await safeReadErrorMessage(resp, 'Não foi possível transferir.'), 'error');
      return;
    }
    const resultado = await resp.json();
    fecharModal('modal-pdv-transferir');
    if (resultado.origemVazia) {
      pdvComandaAtual = null;
      pdvVendaAtual = null;
      pdvCarrinho = [];
      pdvDraftPayloadPorItemVenda = {};
      atualizarComandaPDV();
      renderCarrinho();
    } else {
      await recarregarVendaAtualPDV();
      pdvCarrinho = itensAtivosVendaPDV();
      renderCarrinho();
    }
    renderCardapio();
    const quantos = `${resultado.itensMovidos} ${resultado.itensMovidos === 1 ? 'item transferido' : 'itens transferidos'} para a comanda ${destino}`;
    mostrarAlert('alert-pdv', resultado.comandaOrigemFechada
      ? `Comanda ${resultado.comandaOrigemFechada} juntada na ${destino}: ${quantos}. A ${resultado.comandaOrigemFechada} foi fechada.`
      : `${quantos}.${resultado.origemVazia && !origem ? ' O caixa voltou ao balcão.' : ''}`, 'success');
  } finally {
    setLoading(botao, false);
    focarLeituraPDV();
  }
}

function atalhosTransferenciaPDV(event) {
  if (event.key === 'Escape') { event.preventDefault(); fecharTransferenciaPDV(); return; }
  if (/^F\d+$/.test(event.key)) { event.preventDefault(); return; }
  if (event.key === 'Enter' && event.target && event.target.tagName === 'INPUT') {
    event.preventDefault();
    confirmarTransferenciaPDV();
  }
}

// ── Teclas rápidas: o que não tem código (gelo, copo, caldo, porção) ────
// Um toque ou Alt+1 a Alt+8. Lança como se o código tivesse sido bipado.

let pdvTeclas = [];

async function carregarTeclasPDV() {
  try {
    const resp = await fetch('/api/pdv/teclas');
    pdvTeclas = resp.ok ? await resp.json() : [];
  } catch (error) {
    pdvTeclas = [];
  }
  renderTeclasPDV();
}

function renderTeclasPDV() {
  const barra = document.getElementById('pdv-teclas');
  if (!barra) return;
  barra.innerHTML = pdvTeclas.map(tecla => `
    <button type="button" class="pdv-tecla" data-tecla="${tecla.posicao}" onclick="lancarTeclaPDV(${tecla.posicao})" title="${escapeHtml(tecla.itemNome + ' ' + tecla.variacaoNome)}">
      <kbd>Alt+${tecla.posicao}</kbd><span>${escapeHtml(tecla.itemNome)}</span><small>${formatarMoedaPDV(tecla.precoVenda)}</small>
    </button>`).join('')
    + `<button type="button" class="pdv-tecla pdv-tecla--editar" onclick="abrirTeclasPDV()">${pdvTeclas.length ? 'Editar teclas' : 'Criar teclas rápidas (gelo, copo, porção)'}</button>`;
}

async function lancarTeclaPDV(posicao) {
  const tecla = pdvTeclas.find(t => Number(t.posicao) === Number(posicao));
  if (!tecla) return;
  if (!document.getElementById('pdv-pagamento').hidden) {
    mostrarAlert('alert-pdv', 'Volte do pagamento (Esc) antes de lançar itens.', 'error');
    return;
  }
  // Quantidade digitada antes (ex.: 3 ou 3*) vale para a tecla.
  const campo = document.getElementById('pdv-leitura');
  const digitado = campo.value.trim().replace(/\*$/, '').replace(',', '.');
  let quantidade = 1;
  if (/^\d{1,3}(\.\d+)?$/.test(digitado) && Number(digitado) > 0) {
    quantidade = Number(digitado);
    campo.value = '';
  }
  limparAlert('alert-pdv');
  try {
    await adicionarVariacaoPorCodigoPDV(tecla, quantidade);
    bipPDV(true);
  } catch (error) {
    bipPDV(false);
    mostrarAlert('alert-pdv', error.message || 'Não foi possível adicionar o item.', 'error');
  } finally {
    focarLeituraPDV();
  }
}

async function abrirTeclasPDV() {
  limparAlert('alert-pdv-teclas');
  const lista = document.getElementById('pdv-teclas-lista');
  lista.innerHTML = '<div class="loading">Carregando</div>';
  document.getElementById('pdv-teclas-autorizacao').hidden = temPermissao(PERMISSOES.EDITAR_ESTOQUE);
  ['nome', 'sobrenome', 'senha'].forEach(campo => { document.getElementById('pdv-teclas-gerente-' + campo).value = ''; });
  abrirModal('modal-pdv-teclas');
  let opcoes = [];
  try {
    const resp = await fetch('/api/pdv/teclas/opcoes');
    if (!resp.ok) throw new Error(await safeReadErrorMessage(resp, 'Não foi possível carregar os produtos.'));
    opcoes = await resp.json();
  } catch (error) {
    lista.innerHTML = '';
    mostrarAlert('alert-pdv-teclas', error.message, 'error');
    return;
  }
  const opcoesHtml = opcoes.map(o => `<option value="${o.variacaoId}">${escapeHtml(o.itemNome)} · ${escapeHtml(o.variacaoNome)} · ${formatarMoedaPDV(o.precoVenda)}</option>`).join('');
  lista.innerHTML = Array.from({ length: 8 }, (_, i) => i + 1).map(posicao => `
    <label class="pdv-tecla-config"><kbd>Alt+${posicao}</kbd>
      <select class="input" data-tecla-posicao="${posicao}" aria-label="Tecla Alt+${posicao}"><option value="">(vazia)</option>${opcoesHtml}</select>
    </label>`).join('');
  pdvTeclas.forEach(tecla => {
    const seletor = lista.querySelector(`[data-tecla-posicao="${tecla.posicao}"]`);
    if (seletor) seletor.value = String(tecla.variacaoId);
  });
  lista.querySelector('select')?.focus();
}

function fecharTeclasPDV() {
  fecharModal('modal-pdv-teclas');
  focarLeituraPDV();
}

async function salvarTeclasPDV() {
  limparAlert('alert-pdv-teclas');
  const teclas = [];
  document.querySelectorAll('[data-tecla-posicao]').forEach(seletor => {
    if (seletor.value) teclas.push({ posicao: Number(seletor.dataset.teclaPosicao), variacaoId: Number(seletor.value) });
  });
  const corpo = { teclas };
  if (!document.getElementById('pdv-teclas-autorizacao').hidden) {
    const nome = document.getElementById('pdv-teclas-gerente-nome').value.trim();
    const sobrenome = document.getElementById('pdv-teclas-gerente-sobrenome').value.trim();
    const senha = document.getElementById('pdv-teclas-gerente-senha').value;
    if (!nome || !senha) {
      mostrarAlert('alert-pdv-teclas', 'O gerente precisa digitar nome e senha.', 'error');
      document.getElementById(nome ? 'pdv-teclas-gerente-senha' : 'pdv-teclas-gerente-nome').focus();
      return;
    }
    corpo.autorizacao = { nome, sobrenome, senha };
  }
  const botao = document.getElementById('btn-pdv-salvar-teclas');
  setLoading(botao, true);
  try {
    const resp = await fetch('/api/pdv/teclas', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(corpo)
    });
    if (!resp.ok) {
      mostrarAlert('alert-pdv-teclas', await safeReadErrorMessage(resp, 'Não foi possível salvar as teclas.'), 'error');
      if (resp.status === 403 && corpo.autorizacao) document.getElementById('pdv-teclas-gerente-senha').value = '';
      return;
    }
    pdvTeclas = await resp.json();
    renderTeclasPDV();
    fecharModal('modal-pdv-teclas');
    mostrarAlert('alert-pdv', 'Teclas rápidas salvas.', 'success');
    focarLeituraPDV();
  } finally {
    setLoading(botao, false);
  }
}

function atalhosTeclasPDV(event) {
  if (event.key === 'Escape') { event.preventDefault(); fecharTeclasPDV(); return; }
  if (/^F\d+$/.test(event.key)) event.preventDefault();
}

// ── Cozinha: pedidos impressos, reimpressão e configuração ──────────────
// A comanda guardada e a venda paga mandam para a cozinha o que é dela (o servidor imprime).

let cozinhaLigada = false;
let cozinhaPodeConfigurar = false;

function enviarCozinhaPDV(numero) {
  if (!cozinhaLigada || !numero) return;
  fetch(`/api/comandas/${numero}/cozinha`, { method: 'POST' })
    .then(resp => (resp.ok ? resp.json() : null))
    .then(resultado => {
      if (resultado && resultado.enviado) showToast(`Pedido da comanda ${numero} mandado para a cozinha.`, 'success');
    })
    .catch(() => {});
}

async function atualizarSituacaoCozinha() {
  if (document.hidden) return;
  try {
    const resp = await fetch('/api/cozinha/situacao');
    if (!resp.ok) return;
    const situacao = await resp.json();
    cozinhaLigada = situacao.ligada;
    const mostrar = situacao.ligada || temAlgumaPermissao([PERMISSOES.VER_FINANCAS, PERMISSOES.EDITAR_ESTOQUE]);
    const botao = document.getElementById('btn-cozinha');
    if (botao) botao.hidden = !mostrar;
    const noPdv = document.getElementById('pdv-cozinha');
    if (noPdv) noPdv.hidden = !situacao.ligada;
    const texto = situacao.comErro > 0 ? `${situacao.comErro} não saiu` : '';
    ['cozinha-alerta', 'pdv-cozinha-alerta'].forEach(id => {
      const alerta = document.getElementById(id);
      if (!alerta) return;
      alerta.textContent = texto;
      alerta.hidden = !texto;
    });
  } catch (e) {
    // Sem servidor agora: tenta na próxima.
  }
}

async function abrirCozinha() {
  limparAlert('alert-cozinha');
  abrirModal('modal-cozinha');
  await Promise.all([carregarPedidosCozinha(), carregarConfigCozinha()]);
}

function fecharCozinha() {
  fecharModal('modal-cozinha');
  if (document.getElementById('modal-pdv')?.classList.contains('open')) focarLeituraPDV();
}

async function carregarPedidosCozinha() {
  const lista = document.getElementById('cozinha-pedidos');
  try {
    const [respSituacao, respPedidos] = await Promise.all([fetch('/api/cozinha/situacao'), fetch('/api/cozinha/pedidos')]);
    if (!respSituacao.ok || !respPedidos.ok) throw new Error(await safeReadErrorMessage(respPedidos.ok ? respSituacao : respPedidos, 'Não foi possível carregar a cozinha.'));
    const situacao = await respSituacao.json();
    const pedidos = await respPedidos.json();
    cozinhaLigada = situacao.ligada;
    document.getElementById('cozinha-situacao').textContent = situacao.ligada
      ? `Impressora: ${situacao.impressora}.` + (situacao.comErro ? ` ${situacao.comErro} pedido(s) não saíram: confira se a impressora está ligada e com papel.` : '')
      : 'Pedido da cozinha desligado: escolha a impressora abaixo.';
    lista.innerHTML = pedidos.length ? pedidos.map(p => {
      const hora = new Date(p.criadoEm).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
      const onde = p.comanda ? `Comanda ${p.comanda}` : (p.vendaId ? `Balcão, venda ${p.vendaId}` : 'Teste');
      const tipo = p.tipo === 'CANCELAMENTO' ? 'Cancelamento' : (p.tipo === 'TESTE' ? 'Teste' : 'Pedido');
      const situacaoTexto = p.impressoEm ? 'Impresso' : (p.tentativas ? 'Não saiu: ' + (p.erro || 'erro') : 'Aguardando');
      const classe = p.impressoEm ? 'ok' : (p.tentativas ? 'erro' : 'espera');
      const texto = String(p.texto || '').replace(/^## /gm, '');
      return `<div class="cozinha-pedido cozinha-pedido--${classe}" data-pedido="${p.id}">
        <div class="cozinha-pedido__topo"><strong>${escapeHtml(tipo)} · ${escapeHtml(onde)}</strong> <span>${hora} · ${escapeHtml(situacaoTexto)}</span>
          <button type="button" class="btn btn-secondary btn-sm" data-reimprimir="${p.id}">Reimprimir</button></div>
        <pre class="cozinha-pedido__texto">${escapeHtml(texto)}</pre></div>`;
    }).join('') : '<p class="inline-note">Nenhum pedido mandado para a cozinha ainda.</p>';
  } catch (error) {
    mostrarAlert('alert-cozinha', error.message, 'error');
  }
}

async function carregarConfigCozinha() {
  const bloco = document.getElementById('cozinha-config');
  const resp = await fetch('/api/cozinha/config').catch(() => null);
  cozinhaPodeConfigurar = !!(resp && resp.ok);
  bloco.hidden = !cozinhaPodeConfigurar;
  if (!cozinhaPodeConfigurar) return;
  const config = await resp.json();
  const seletor = document.getElementById('cozinha-impressora');
  const nomes = [...config.impressoras];
  if (config.impressora && !nomes.includes(config.impressora)) nomes.unshift(config.impressora);
  seletor.innerHTML = '<option value="">Nenhuma (desligado)</option>' + nomes.map(nome =>
    `<option value="${escapeHtml(nome)}">${escapeHtml(nome)}${config.impressoras.includes(nome) ? '' : ' (não encontrada)'}</option>`).join('');
  seletor.value = config.impressora || '';
  document.getElementById('cozinha-colunas').value = config.colunas <= 32 ? '32' : '48';
  document.getElementById('cozinha-categorias').innerHTML = config.categoriasDisponiveis.map(c => `
    <label class="cozinha-categoria"><input type="checkbox" value="${escapeHtml(c.codigo)}" ${config.categorias.includes(String(c.codigo).toLowerCase()) ? 'checked' : ''}> ${escapeHtml(c.nome)}</label>`).join('');
  if (!config.impressora && !cozinhaLigada) bloco.open = true;
}

async function salvarCozinha() {
  limparAlert('alert-cozinha');
  const corpo = {
    impressora: document.getElementById('cozinha-impressora').value,
    colunas: Number(document.getElementById('cozinha-colunas').value),
    categorias: [...document.querySelectorAll('#cozinha-categorias input:checked')].map(c => c.value)
  };
  const botao = document.getElementById('btn-cozinha-salvar');
  setLoading(botao, true);
  try {
    const resp = await fetch('/api/cozinha/config', { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(corpo) });
    if (!resp.ok) throw new Error(await safeReadErrorMessage(resp, 'Não foi possível salvar.'));
    mostrarAlert('alert-cozinha', 'Cozinha salva.', 'success');
    await atualizarSituacaoCozinha();
    await carregarPedidosCozinha();
  } catch (error) {
    mostrarAlert('alert-cozinha', error.message, 'error');
  } finally {
    setLoading(botao, false);
  }
}

async function testarCozinha() {
  limparAlert('alert-cozinha');
  const resp = await fetch('/api/cozinha/teste', { method: 'POST' });
  if (!resp.ok) {
    mostrarAlert('alert-cozinha', await safeReadErrorMessage(resp, 'Não foi possível testar.'), 'error');
    return;
  }
  mostrarAlert('alert-cozinha', 'Teste mandado. Se não sair em alguns segundos, confira se a impressora está ligada e com papel.', 'info');
  setTimeout(carregarPedidosCozinha, 4000);
}

if (typeof document.addEventListener === 'function') document.addEventListener('click', async event => {
  const botao = event.target.closest && event.target.closest('[data-reimprimir]');
  if (!botao) return;
  botao.disabled = true;
  const resp = await fetch(`/api/cozinha/pedidos/${botao.dataset.reimprimir}/reimprimir`, { method: 'POST' });
  if (!resp.ok) mostrarAlert('alert-cozinha', await safeReadErrorMessage(resp, 'Não foi possível reimprimir.'), 'error');
  else mostrarAlert('alert-cozinha', 'Mandado de novo para a impressora da cozinha.', 'success');
  setTimeout(carregarPedidosCozinha, 3000);
});

// Só na tela do caixa de verdade: os testes rodam este arquivo sem página.
if (typeof document.getElementById === 'function'
    && (document.getElementById('btn-cozinha') || document.getElementById('pdv-cozinha'))) {
  atualizarSituacaoCozinha();
  setInterval(atualizarSituacaoCozinha, 20000);
}

// ── Tirar item já lançado numa comanda ──────────────────────────────────
// Na comanda o item pode já ter sido servido: só sai com o gerente e com motivo.

function abrirRetiradaPDV(ids) {
  if (operacaoPendentePDV()) return;
  if (!pdvComandaAtual || !pdvVendaAtual) return;
  const itens = pdvCarrinho.filter(item => ids.includes(Number(item.id)));
  if (!itens.length) return;
  if (!document.getElementById('pdv-pagamento').hidden) {
    mostrarAlert('alert-pdv', 'Volte do pagamento (Esc) antes de tirar itens.', 'error');
    return;
  }
  limparAlert('alert-pdv-retirar');
  document.getElementById('pdv-retirar-titulo').textContent = itens.length === 1
    ? `Tirar da comanda ${pdvComandaAtual.numero}` : `Limpar a comanda ${pdvComandaAtual.numero}`;
  document.getElementById('pdv-retirar-lista').innerHTML = itens.map(item => `
    <label class="pdv-dividir-linha">
      <span>${escapeHtml(item.item)}${item.variacao ? ' ' + escapeHtml(item.variacao) : ''}<br><small>${formatarMoedaPDV(item.subtotal)}</small></span>
      <small>de ${String(Number(item.quantidade)).replace('.', ',')}</small>
      <input class="input" type="text" inputmode="decimal" data-retirar-item="${item.id}" data-maximo="${Number(item.quantidade)}" value="${String(Number(item.quantidade)).replace('.', ',')}">
    </label>`).join('');
  document.getElementById('pdv-retirar-motivo').value = '';
  document.getElementById('pdv-retirar-autorizacao').hidden = temPermissao(PERMISSOES.VER_FINANCAS);
  ['nome', 'sobrenome', 'senha'].forEach(campo => { document.getElementById('pdv-retirar-gerente-' + campo).value = ''; });
  abrirModal('modal-pdv-retirar');
  const quantidade = itens.length === 1 && Number(itens[0].quantidade) > 1 ? document.querySelector('[data-retirar-item]') : null;
  if (quantidade) { quantidade.focus(); quantidade.select(); }
  else document.getElementById('pdv-retirar-motivo').focus();
}

function fecharRetiradaPDV() {
  fecharModal('modal-pdv-retirar');
  focarLeituraPDV();
}

async function confirmarRetiradaPDV() {
  limparAlert('alert-pdv-retirar');
  if (!pdvComandaAtual) return;
  const itens = [];
  let erro = null;
  document.querySelectorAll('[data-retirar-item]').forEach(campo => {
    const qtd = Number(campo.value.trim().replace(',', '.') || '0');
    if (!Number.isFinite(qtd) || qtd < 0 || qtd > Number(campo.dataset.maximo)) erro = 'Quantidade inválida em um dos itens.';
    else if (qtd > 0) itens.push({ itemId: Number(campo.dataset.retirarItem), quantidade: qtd, inteiro: qtd === Number(campo.dataset.maximo) });
  });
  if (erro || itens.length === 0) {
    mostrarAlert('alert-pdv-retirar', erro || 'Diga quanto tirar.', 'error');
    return;
  }
  const motivo = document.getElementById('pdv-retirar-motivo').value.trim();
  if (motivo.length < 3) {
    mostrarAlert('alert-pdv-retirar', 'Diga o motivo de tirar o item.', 'error');
    document.getElementById('pdv-retirar-motivo').focus();
    return;
  }
  const corpo = { itens: itens.map(i => ({ itemId: i.itemId, quantidade: i.quantidade })), motivo };
  if (!document.getElementById('pdv-retirar-autorizacao').hidden) {
    const nome = document.getElementById('pdv-retirar-gerente-nome').value.trim();
    const sobrenome = document.getElementById('pdv-retirar-gerente-sobrenome').value.trim();
    const senha = document.getElementById('pdv-retirar-gerente-senha').value;
    if (!nome || !senha) {
      mostrarAlert('alert-pdv-retirar', 'O gerente precisa digitar nome e senha.', 'error');
      document.getElementById(nome ? 'pdv-retirar-gerente-senha' : 'pdv-retirar-gerente-nome').focus();
      return;
    }
    corpo.autorizacao = { nome, sobrenome, senha };
  }
  const botao = document.getElementById('btn-pdv-confirmar-retirada');
  setLoading(botao, true);
  const numero = pdvComandaAtual.numero;
  try {
    const resp = await fetch(`/api/comandas/${numero}/retirar`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(corpo)
    });
    if (!resp.ok) {
      mostrarAlert('alert-pdv-retirar', await safeReadErrorMessage(resp, 'Não foi possível tirar o item.'), 'error');
      if (resp.status === 403 && corpo.autorizacao) {
        document.getElementById('pdv-retirar-gerente-senha').value = '';
        document.getElementById('pdv-retirar-gerente-senha').focus();
      }
      return;
    }
    const resultado = await resp.json();
    fecharModal('modal-pdv-retirar');
    itens.filter(i => i.inteiro).forEach(i => { delete pdvDraftPayloadPorItemVenda[i.itemId]; });
    if (resultado.comandaFechada) {
      pdvComandaAtual = null;
      pdvVendaAtual = null;
      pdvCarrinho = [];
      pdvDraftPayloadPorItemVenda = {};
      atualizarComandaPDV();
      renderCarrinho();
    } else {
      await recarregarVendaAtualPDV();
    }
    renderCardapio();
    mostrarAlert('alert-pdv', `${formatarMoedaPDV(resultado.valor)} tirados da comanda ${numero}.`
      + (resultado.comandaFechada ? ' A comanda ficou vazia e foi fechada.' : ''), 'success');
  } finally {
    setLoading(botao, false);
    if (!document.getElementById('modal-pdv-retirar').classList.contains('open')) focarLeituraPDV();
  }
}

function atalhosRetiradaPDV(event) {
  if (event.key === 'Escape') { event.preventDefault(); fecharRetiradaPDV(); return; }
  if (/^F\d+$/.test(event.key)) { event.preventDefault(); return; }
  if (event.key === 'Enter' && event.target && event.target.tagName === 'INPUT') {
    event.preventDefault();
    confirmarRetiradaPDV();
  }
}

// ── Dividir a conta entre pessoas ────────────────────────────────────────
// Continua sendo uma venda só: cada pessoa vira um pagamento, com a forma e o troco dela.

let pdvDivisao = null;
let pdvAbaDivisao = 'iguais';

function valorSugeridoPDV() {
  const restante = restantePDV();
  if (!pdvDivisao || restante <= 0) return restante;
  if (pdvDivisao.modo === 'iguais') {
    const faltam = pdvDivisao.pessoas - pdvPagamentos.length;
    if (faltam <= 1) return restante;
    // Arredonda para baixo; a última pessoa fica com a sobra dos centavos.
    return Math.min(restante, Math.floor(calcularTotalCarrinho() / pdvDivisao.pessoas * 100) / 100);
  }
  return pdvDivisao.valorPendente != null ? Math.min(restante, pdvDivisao.valorPendente) : restante;
}

/** Quanto de cada item já foi pago por alguém, nesta divisão. */
function itensJaPagosPDV() {
  const pagos = {};
  pdvPagamentos.forEach(p => Object.entries(p.itens || {}).forEach(([id, qtd]) => {
    pagos[id] = (pagos[id] || 0) + Number(qtd);
  }));
  return pagos;
}

function abrirDivisaoPDV() {
  if (document.getElementById('pdv-pagamento').hidden) return;
  limparAlert('alert-pdv-dividir');
  document.getElementById('pdv-dividir-pessoas').value = pdvDivisao && pdvDivisao.modo === 'iguais' ? String(pdvDivisao.pessoas) : '';
  previaDivisaoIgualPDV();
  renderItensDivisaoPDV();
  document.getElementById('btn-pdv-parar-divisao').hidden = !pdvDivisao;
  abrirModal('modal-pdv-dividir');
  // Venda nova começa em partes iguais; no meio de uma divisão, continua no mesmo modo.
  escolherAbaDivisaoPDV(pdvDivisao ? pdvDivisao.modo : 'iguais');
}

function escolherAbaDivisaoPDV(aba) {
  pdvAbaDivisao = aba;
  document.getElementById('pdv-dividir-iguais').hidden = aba !== 'iguais';
  document.getElementById('pdv-dividir-itens').hidden = aba !== 'itens';
  document.getElementById('pdv-dividir-aba-iguais').classList.toggle('pdv-novo-aba--ativa', aba === 'iguais');
  document.getElementById('pdv-dividir-aba-itens').classList.toggle('pdv-novo-aba--ativa', aba === 'itens');
  document.getElementById('btn-pdv-confirmar-divisao').textContent = aba === 'iguais' ? 'Dividir (Enter)' : 'Cobrar desta pessoa (Enter)';
  const foco = aba === 'iguais' ? document.getElementById('pdv-dividir-pessoas') : document.querySelector('[data-dividir-item]');
  foco?.focus();
}

function previaDivisaoIgualPDV() {
  const pessoas = Number(document.getElementById('pdv-dividir-pessoas').value);
  const previa = document.getElementById('pdv-dividir-previa');
  const total = calcularTotalCarrinho();
  if (!Number.isInteger(pessoas) || pessoas < 2) { previa.textContent = `Total: ${formatarMoedaPDV(total)}.`; return; }
  const parte = Math.floor(total / pessoas * 100) / 100;
  const ultima = arred2(total - parte * (pessoas - 1));
  previa.textContent = ultima === parte
    ? `${pessoas} × ${formatarMoedaPDV(parte)}`
    : `${pessoas - 1} × ${formatarMoedaPDV(parte)} e a última ${formatarMoedaPDV(ultima)}`;
}

function renderItensDivisaoPDV() {
  const pagos = itensJaPagosPDV();
  const linhas = pdvCarrinho.map(item => {
    const restante = arred2(Number(item.quantidade || 0) - (pagos[item.id] || 0));
    if (restante <= 0) return '';
    return `<label class="pdv-dividir-linha">
        <span>${escapeHtml(item.item)}${item.variacao ? ' ' + escapeHtml(item.variacao) : ''}<br><small>${formatarMoedaPDV(Number(item.subtotal) / Number(item.quantidade))} cada</small></span>
        <small>falta ${String(restante).replace('.', ',')}</small>
        <input class="input" type="text" inputmode="decimal" data-dividir-item="${item.id}" data-maximo="${restante}" value="" placeholder="0" oninput="somarItensDivisaoPDV()">
      </label>`;
  }).join('');
  document.getElementById('pdv-dividir-lista').innerHTML = linhas || '<p class="inline-note">Todos os itens já foram pagos.</p>';
  somarItensDivisaoPDV();
}

function lerItensDivisaoPDV() {
  const escolhidos = {};
  let erro = null;
  document.querySelectorAll('[data-dividir-item]').forEach(campo => {
    const texto = campo.value.trim().replace(',', '.');
    if (!texto) return;
    const qtd = Number(texto);
    if (!Number.isFinite(qtd) || qtd < 0 || qtd > Number(campo.dataset.maximo)) {
      erro = `Quantidade inválida: no máximo ${String(campo.dataset.maximo).replace('.', ',')}.`;
    } else if (qtd > 0) {
      escolhidos[campo.dataset.dividirItem] = qtd;
    }
  });
  return { escolhidos, erro };
}

function valorItensDivisaoPDV(escolhidos) {
  const pagos = itensJaPagosPDV();
  let valor = 0;
  let cobreTudo = true;
  pdvCarrinho.forEach(item => {
    const restante = arred2(Number(item.quantidade || 0) - (pagos[item.id] || 0));
    const qtd = Number(escolhidos[item.id] || 0);
    valor += qtd * Number(item.subtotal) / Number(item.quantidade);
    if (restante > 0 && qtd < restante) cobreTudo = false;
  });
  // Quem fica com tudo o que faltava paga o restante exato, sem sobra de centavo.
  return cobreTudo ? restantePDV() : Math.min(arred2(valor), restantePDV());
}

function somarItensDivisaoPDV() {
  const { escolhidos, erro } = lerItensDivisaoPDV();
  document.getElementById('pdv-dividir-soma').textContent = erro ? '—' : formatarMoedaPDV(valorItensDivisaoPDV(escolhidos));
}

function confirmarDivisaoPDV() {
  limparAlert('alert-pdv-dividir');
  if (pdvAbaDivisao === 'iguais') {
    const pessoas = Number(document.getElementById('pdv-dividir-pessoas').value);
    if (!Number.isInteger(pessoas) || pessoas < 2 || pessoas > 50) {
      mostrarAlert('alert-pdv-dividir', 'Digite de 2 a 50 pessoas.', 'error');
      return;
    }
    if (pdvPagamentos.length > 0) {
      mostrarAlert('alert-pdv-dividir', 'Já há pagamento lançado. Tire os pagamentos (x) antes de dividir em partes iguais.', 'error');
      return;
    }
    pdvDivisao = { modo: 'iguais', pessoas };
  } else {
    const { escolhidos, erro } = lerItensDivisaoPDV();
    if (erro) { mostrarAlert('alert-pdv-dividir', erro, 'error'); return; }
    if (Object.keys(escolhidos).length === 0) {
      mostrarAlert('alert-pdv-dividir', 'Marque quanto desta pessoa em pelo menos um item.', 'error');
      return;
    }
    const valor = valorItensDivisaoPDV(escolhidos);
    pdvDivisao = { modo: 'itens', pendente: escolhidos, valorPendente: valor };
  }
  fecharModal('modal-pdv-dividir');
  selecionarFormaPDV(pdvFormaSelecionada);
  limparAlert('alert-pdv');
  document.getElementById('pdv-pag-valor').focus();
}

function pararDivisaoPDV() {
  pdvDivisao = null;
  fecharModal('modal-pdv-dividir');
  selecionarFormaPDV(pdvFormaSelecionada);
  document.getElementById('pdv-pag-valor').focus();
}

function fecharDivisaoPDV() {
  fecharModal('modal-pdv-dividir');
  document.getElementById('pdv-pag-valor')?.focus();
}

function atalhosDivisaoPDV(event) {
  if (event.key === 'Escape') { event.preventDefault(); fecharDivisaoPDV(); return; }
  if (/^F\d+$/.test(event.key)) { event.preventDefault(); return; }
  if (event.key === 'Enter' && event.target && event.target.tagName === 'INPUT') {
    event.preventDefault();
    confirmarDivisaoPDV();
  }
}

// ── Cupom de 80 mm ───────────────────────────────────────────────────────
// Impresso pelo navegador, na impressora padrão do Windows. Não é documento fiscal.

let pdvUltimoCupom = null;
const CHAVE_CUPOM_AUTOMATICO = 'stockos.pdv.cupomAutomatico';

function cupomAutomaticoPDV() {
  try { return window.localStorage.getItem(CHAVE_CUPOM_AUTOMATICO) === '1'; } catch { return false; }
}

function alternarCupomAutomaticoPDV() {
  const ligado = document.getElementById('pdv-cupom-auto').checked;
  try { window.localStorage.setItem(CHAVE_CUPOM_AUTOMATICO, ligado ? '1' : '0'); } catch { /* sem preferência salva */ }
  focarLeituraPDV();
}

function acrescentarBotaoAlertaPDV(texto, acao) {
  const botao = document.createElement('button');
  botao.type = 'button';
  botao.className = 'btn btn-secondary btn-sm pdv-alerta-acao';
  botao.textContent = texto;
  botao.addEventListener('click', () => { acao(); focarLeituraPDV(); });
  document.getElementById('alert-pdv').append(botao);
}

/** Retrato da venda para o papel: itens, totais e pagamentos. */
function dadosCupomPDV({ titulo, venda, comanda, pagamentos, troco, conferencia = false }) {
  const itens = ((venda && venda.itens) || []).filter(i => i.status !== 'CANCELADO').map(i => ({
    nome: `${i.item}${i.variacao ? ' ' + i.variacao : ''}`,
    quantidade: Number(i.quantidade || 0),
    unitario: Number(i.precoUnitarioBase || 0) + Number(i.acrescimoUnitario || 0),
    subtotalBruto: (Number(i.precoUnitarioBase || 0) + Number(i.acrescimoUnitario || 0)) * Number(i.quantidade || 0),
    desconto: Number(i.descontoUnitario || 0) * Number(i.quantidade || 0)
  }));
  const subtotal = arred2(itens.reduce((s, i) => s + i.subtotalBruto, 0));
  const desconto = arred2(itens.reduce((s, i) => s + i.desconto, 0));
  return {
    titulo,
    conferencia,
    vendaId: venda ? venda.id : null,
    comanda: comanda ? comanda.numero : null,
    quando: new Date(),
    caixa: pdvNumeroCaixa,
    operador: nomeDoOperadorAtual(),
    itens,
    subtotal,
    desconto,
    total: arred2(subtotal - desconto),
    pagamentos: pagamentos || [],
    troco: troco || 0
  };
}

function htmlCupomPDV(c) {
  const linha = (esquerda, direita, classe = '') =>
    `<div class="cupom-linha ${classe}"><span>${escapeHtml(esquerda)}</span><span>${escapeHtml(direita)}</span></div>`;
  const rotulo = codigo => (PDV_FORMAS.find(f => f.codigo === codigo) || { rotulo: codigo }).rotulo;
  const data = c.quando.toLocaleString('pt-BR', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' });
  let html = `<h2>${escapeHtml(c.titulo)}</h2>`
    + `<p class="cupom-centro">${escapeHtml(data)} · Caixa ${escapeHtml(String(c.caixa ?? '—'))}</p>`
    + (c.comanda ? `<p class="cupom-centro"><strong>COMANDA ${c.comanda}</strong></p>` : '')
    + (c.vendaId && !c.conferencia ? `<p class="cupom-centro">Venda #${c.vendaId} · ${escapeHtml(c.operador)}</p>` : `<p class="cupom-centro">${escapeHtml(c.operador)}</p>`)
    + '<hr>';
  c.itens.forEach(i => {
    html += `<p>${escapeHtml(i.nome)}</p>`
      + linha(`  ${String(i.quantidade).replace('.', ',')} x ${formatarMoedaPDV(i.unitario)}`, formatarMoedaPDV(i.subtotalBruto));
  });
  html += '<hr>' + linha('Subtotal', formatarMoedaPDV(c.subtotal));
  if (c.desconto > 0) html += linha('Desconto', '-' + formatarMoedaPDV(c.desconto));
  html += linha('TOTAL', formatarMoedaPDV(c.total), 'cupom-total');
  if (c.pagamentos.length) {
    html += '<hr>';
    c.pagamentos.forEach(p => {
      html += linha((p.pessoa ? p.pessoa + ' · ' : '') + rotulo(p.forma), formatarMoedaPDV(p.valor));
      if (p.valorRecebido != null && p.valorRecebido > p.valor) {
        html += linha('  Recebido', formatarMoedaPDV(p.valorRecebido));
        if (p.pessoa) html += linha('  Troco', formatarMoedaPDV(p.valorRecebido - p.valor));
      }
    });
    if (c.troco > 0) html += linha('TROCO', formatarMoedaPDV(c.troco), 'cupom-total');
  }
  html += '<hr><p class="cupom-centro">Não é documento fiscal.</p>'
    + (c.conferencia ? '<p class="cupom-centro">Ainda não pago. Confira os itens antes de pagar.</p>' : '<p class="cupom-centro">Obrigado e volte sempre!</p>');
  return html;
}

function imprimirCupomPDV(dados) {
  if (!dados) return false;
  document.getElementById('cupom-impressao').innerHTML = htmlCupomPDV(dados);
  document.body.classList.add('imprimindo-cupom');
  try {
    window.print();
  } finally {
    document.body.classList.remove('imprimindo-cupom');
  }
  return true;
}

/** F8: com comanda aberta, a conferência para o cliente; senão, o último cupom de novo. */
function imprimirConferenciaOuUltimoCupomPDV() {
  if (pdvComandaAtual && pdvVendaAtual) {
    imprimirCupomPDV(dadosCupomPDV({ titulo: 'CONFERÊNCIA DA COMANDA', venda: pdvVendaAtual, comanda: pdvComandaAtual, conferencia: true }));
    mostrarAlert('alert-pdv', `Conferência da comanda ${pdvComandaAtual.numero} enviada para a impressora.`, 'info');
    return;
  }
  if (!imprimirCupomPDV(pdvUltimoCupom)) {
    mostrarAlert('alert-pdv', 'Ainda não há cupom para imprimir neste caixa.', 'info');
  }
}

// ── Comandas: quem consome na loja e paga no fim ─────────────────────────
// O cartão traz um EAN-13 começando com 29 (faixa de uso interno) com o número.
// Digitar "c15" também abre a comanda 15.

let pdvComandaAtual = null;

function numeroComandaPDV(codigo) {
  const texto = String(codigo || '').trim();
  const digitado = texto.match(/^[cC](\d{1,4})$/);
  if (digitado) return Number(digitado[1]) || null;
  if (/^29\d{11}$/.test(texto) && window.StockOSCodigoBarras && window.StockOSCodigoBarras.ehEan13(texto)) {
    const numero = Number(texto.slice(2, 12));
    return numero >= 1 && numero <= 9999 ? numero : null;
  }
  return null;
}

function atualizarComandaPDV() {
  const selo = document.getElementById('pdv-comanda-label');
  if (!selo) return;
  selo.hidden = !pdvComandaAtual;
  selo.textContent = pdvComandaAtual ? `Comanda ${pdvComandaAtual.numero}` : '';
}

async function abrirComandaPDV(numero) {
  limparAlert('alert-pdv');
  if (operacaoPendentePDV()) return false;
  if (pdvComandaAtual && pdvComandaAtual.numero === numero) {
    mostrarAlert('alert-pdv', `A comanda ${numero} já está aberta aqui.`, 'info');
    return true;
  }
  if (!document.getElementById('pdv-pagamento').hidden) {
    mostrarAlert('alert-pdv', 'Termine ou volte do pagamento (Esc) antes de abrir uma comanda.', 'error');
    bipPDV(false);
    return false;
  }
  if (!pdvComandaAtual && pdvCarrinho.length > 0) {
    mostrarAlert('alert-pdv', 'Termine ou limpe a venda de balcão antes de abrir uma comanda.', 'error');
    bipPDV(false);
    return false;
  }
  // Trocando de comanda: a anterior manda para a cozinha o que ainda não foi.
  if (pdvComandaAtual) enviarCozinhaPDV(pdvComandaAtual.numero);
  const resp = await fetch(`/api/comandas/${numero}/abrir`, { method: 'POST' });
  if (!resp.ok) {
    mostrarAlert('alert-pdv', await safeReadErrorMessage(resp, 'Não foi possível abrir a comanda.'), 'error');
    bipPDV(false);
    return false;
  }
  const comanda = await resp.json();
  const respVenda = await fetch(`/api/vendas/${comanda.vendaId}`);
  if (!respVenda.ok) {
    mostrarAlert('alert-pdv', await safeReadErrorMessage(respVenda, 'Não foi possível carregar a comanda.'), 'error');
    return false;
  }
  pdvVendaAtual = await respVenda.json();
  pdvDraftPayloadPorItemVenda = {};
  pdvComandaAtual = { numero };
  atualizarComandaPDV();
  renderCarrinho();
  renderCardapio();
  bipPDV(true);
  const quantidade = pdvCarrinho.reduce((soma, item) => soma + Number(item.quantidade || 0), 0);
  mostrarAlert('alert-pdv', quantidade > 0
    ? `Comanda ${numero}: ${quantidade} ${quantidade === 1 ? 'item' : 'itens'}, ${formatarMoedaPDV(calcularTotalCarrinho())}. Bipe o que o cliente pedir, F10 fecha a conta e Esc guarda a comanda.`
    : `Comanda ${numero} aberta. Bipe o que o cliente pedir. Esc guarda a comanda.`, 'success');
  return true;
}

function guardarComandaPDV() {
  if (!pdvComandaAtual) return;
  const { numero } = pdvComandaAtual;
  enviarCozinhaPDV(numero);
  const total = calcularTotalCarrinho();
  pdvComandaAtual = null;
  pdvVendaAtual = null;
  pdvCarrinho = [];
  pdvDraftPayloadPorItemVenda = {};
  atualizarComandaPDV();
  renderCarrinho();
  renderCardapio();
  mostrarAlert('alert-pdv', `Comanda ${numero} guardada com ${formatarMoedaPDV(total)}. O caixa voltou ao balcão.`, 'info');
  focarLeituraPDV();
}

async function abrirComandasPDV() {
  limparAlert('alert-pdv-comandas');
  document.getElementById('pdv-comanda-numero').value = '';
  const guardar = document.getElementById('btn-pdv-guardar-comanda');
  guardar.hidden = !pdvComandaAtual;
  if (pdvComandaAtual) guardar.textContent = `Guardar a comanda ${pdvComandaAtual.numero} e voltar ao balcão`;
  abrirModal('modal-pdv-comandas');
  document.getElementById('pdv-comanda-numero').focus();
  const lista = document.getElementById('pdv-comandas-lista');
  lista.innerHTML = '<p class="inline-note">Carregando...</p>';
  try {
    const resp = await fetch('/api/comandas');
    if (!resp.ok) throw new Error(await safeReadErrorMessage(resp, 'Não foi possível listar as comandas.'));
    const comandas = await resp.json();
    if (comandas.length === 0) {
      lista.innerHTML = '<p class="inline-note">Nenhuma comanda aberta.</p>';
      return;
    }
    lista.innerHTML = comandas.map(c => {
      const itens = Number(c.itens || 0);
      return `<div class="pdv-comanda-linha">
          <button type="button" class="pdv-venda-linha" data-comanda-pdv="${c.numero}">
            <span class="pdv-venda-linha__hora">${escapeHtml(horaPDV(c.abertaEm))}</span>
            <span>Comanda ${c.numero} · ${itens} ${itens === 1 ? 'item' : 'itens'}</span>
            <strong>${formatarMoedaPDV(c.total)}</strong>
          </button>
          ${itens === 0 ? `<button type="button" class="pdv-pag-link" data-excluir-comanda="${c.numero}">Excluir vazia</button>` : ''}
        </div>`;
    }).join('');
    lista.querySelectorAll('[data-comanda-pdv]').forEach(botao => botao.addEventListener('click', async () => {
      if (await abrirComandaPDV(Number(botao.dataset.comandaPdv))) fecharComandasPDV();
    }));
    lista.querySelectorAll('[data-excluir-comanda]').forEach(botao => botao.addEventListener('click', async () => {
      const resp = await fetch(`/api/comandas/${botao.dataset.excluirComanda}`, { method: 'DELETE' });
      if (!resp.ok) mostrarAlert('alert-pdv-comandas', await safeReadErrorMessage(resp, 'Não foi possível excluir.'), 'error');
      else abrirComandasPDV();
    }));
  } catch (error) {
    lista.innerHTML = '';
    mostrarAlert('alert-pdv-comandas', error.message, 'error');
  }
}

async function abrirComandaDigitadaPDV() {
  const texto = document.getElementById('pdv-comanda-numero').value.trim();
  const numero = /^\d{1,4}$/.test(texto) ? Number(texto) : numeroComandaPDV(texto);
  if (!numero) {
    mostrarAlert('alert-pdv-comandas', 'Digite o número da comanda, de 1 a 9999.', 'error');
    return;
  }
  if (await abrirComandaPDV(numero)) fecharComandasPDV();
  else mostrarAlert('alert-pdv-comandas', document.getElementById('alert-pdv').textContent, 'error');
}

function fecharComandasPDV() {
  fecharModal('modal-pdv-comandas');
  focarLeituraPDV();
}

function atalhosComandasPDV(event) {
  if (event.key === 'Escape') { event.preventDefault(); fecharComandasPDV(); return; }
  if (/^F\d+$/.test(event.key)) { event.preventDefault(); return; }
  if (event.key === 'Enter' && event.target && event.target.id === 'pdv-comanda-numero') {
    event.preventDefault();
    abrirComandaDigitadaPDV();
  }
}

// ── Código bipado que não existe: cadastrar ou ligar a um produto, sem sair do caixa ──

let pdvCodigoNovo = null;
let pdvNovoAba = 'produto';
let pdvNovoVariacaoId = null;

function avisarCodigoDesconhecidoPDV(codigo, quantidade) {
  pdvCodigoNovo = { codigo, quantidade };
  mostrarAlert('alert-pdv', `Código ${codigo} não cadastrado.`, 'error');
  const botao = document.createElement('button');
  botao.type = 'button';
  botao.className = 'btn btn-secondary btn-sm pdv-alerta-acao';
  botao.id = 'btn-pdv-cadastrar-codigo';
  botao.textContent = 'Cadastrar este código';
  botao.addEventListener('click', abrirNovoCodigoPDV);
  document.getElementById('alert-pdv').append(botao);
}

function abrirNovoCodigoPDV() {
  if (!pdvCodigoNovo) return;
  limparAlert('alert-pdv-novo');
  document.getElementById('pdv-novo-codigo').textContent = pdvCodigoNovo.codigo;
  ['nome', 'preco-gelado', 'preco', 'custo', 'qtd', 'busca', 'gerente-nome', 'gerente-sobrenome', 'gerente-senha']
    .forEach(campo => { document.getElementById('pdv-novo-' + campo).value = ''; });
  document.getElementById('pdv-novo-resultados').innerHTML = '';
  pdvNovoVariacaoId = null;
  document.getElementById('pdv-novo-autorizacao').hidden = temPermissao(PERMISSOES.EDITAR_ESTOQUE);
  abrirModal('modal-pdv-novo-codigo');
  escolherAbaNovoCodigoPDV('produto');
}

function escolherAbaNovoCodigoPDV(aba) {
  pdvNovoAba = aba;
  document.getElementById('pdv-novo-produto').hidden = aba !== 'produto';
  document.getElementById('pdv-novo-vincular').hidden = aba !== 'vincular';
  document.getElementById('pdv-novo-aba-produto').classList.toggle('pdv-novo-aba--ativa', aba === 'produto');
  document.getElementById('pdv-novo-aba-vincular').classList.toggle('pdv-novo-aba--ativa', aba === 'vincular');
  document.getElementById(aba === 'produto' ? 'pdv-novo-nome' : 'pdv-novo-busca').focus();
}

function fecharNovoCodigoPDV() {
  fecharModal('modal-pdv-novo-codigo');
  focarLeituraPDV();
}

function normalizarBuscaPDV(texto) {
  return String(texto || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();
}

async function buscarProdutoNovoCodigoPDV() {
  const termo = normalizarBuscaPDV(document.getElementById('pdv-novo-busca').value.trim());
  const area = document.getElementById('pdv-novo-resultados');
  pdvNovoVariacaoId = null;
  if (termo.length < 2) { area.innerHTML = ''; return; }
  const achados = pdvItens.filter(item => normalizarBuscaPDV(item.nome).includes(termo)).slice(0, 8);
  if (achados.length === 0) {
    area.innerHTML = '<p class="inline-note">Nenhum produto com esse nome. Use "Produto novo".</p>';
    return;
  }
  const blocos = await Promise.all(achados.map(async item => {
    const variacoes = (await carregarVariacoesItemPDV(item.id).catch(() => [])).filter(v => v.ativa);
    const botoes = variacoes.map(v => `<button type="button" class="pdv-novo-variacao" data-variacao-novo="${v.id}">`
      + `${escapeHtml(v.nome)} · ${formatarMoedaPDV(v.precoVenda)}</button>`).join('');
    return `<div class="pdv-novo-produto"><strong>${escapeHtml(item.nome)}</strong><div class="pdv-novo-variacoes">${botoes}</div></div>`;
  }));
  if (normalizarBuscaPDV(document.getElementById('pdv-novo-busca').value.trim()) !== termo) return;
  area.innerHTML = blocos.join('');
  area.querySelectorAll('[data-variacao-novo]').forEach(botao => botao.addEventListener('click', () => {
    pdvNovoVariacaoId = Number(botao.dataset.variacaoNovo);
    area.querySelectorAll('[data-variacao-novo]').forEach(b => b.classList.toggle('pdv-novo-variacao--escolhida', b === botao));
  }));
}

function numeroNovoPDV(id) {
  const texto = document.getElementById(id).value.trim().replace(',', '.');
  return texto === '' ? null : Number(texto);
}

async function salvarNovoCodigoPDV() {
  if (!pdvCodigoNovo) return;
  limparAlert('alert-pdv-novo');
  let url;
  let corpo;
  if (pdvNovoAba === 'produto') {
    const nome = document.getElementById('pdv-novo-nome').value.trim();
    const precoGelado = numeroNovoPDV('pdv-novo-preco-gelado');
    const precoVenda = numeroNovoPDV('pdv-novo-preco');
    const custo = numeroNovoPDV('pdv-novo-custo');
    const quantidade = numeroNovoPDV('pdv-novo-qtd');
    if (!nome || !(precoVenda > 0) || !(custo > 0) || quantidade == null || !(quantidade >= 0)
        || (precoGelado != null && !(precoGelado > 0))) {
      mostrarAlert('alert-pdv-novo', 'Preencha nome, preço, custo e quantos tem na loja.', 'error');
      return;
    }
    url = '/api/pdv/cadastro/produto';
    corpo = { produto: {
      nome, categoriaCardapioCodigo: 'bebidas', categoriaCardapioNome: 'Bebidas', categoriaEstoque: 'BEBIDAS',
      codigoBarras: pdvCodigoNovo.codigo, precoVenda, precoGelado, custoUnitario: custo,
      quantidadeInicial: Math.trunc(quantidade), quantidadeMinima: 0, fardo: null
    } };
  } else {
    if (!pdvNovoVariacaoId) {
      mostrarAlert('alert-pdv-novo', 'Procure o produto e escolha qual versão este código vende.', 'error');
      return;
    }
    url = '/api/pdv/cadastro/codigo';
    corpo = { codigo: pdvCodigoNovo.codigo, variacaoId: pdvNovoVariacaoId };
  }
  if (!document.getElementById('pdv-novo-autorizacao').hidden) {
    const nome = document.getElementById('pdv-novo-gerente-nome').value.trim();
    const sobrenome = document.getElementById('pdv-novo-gerente-sobrenome').value.trim();
    const senha = document.getElementById('pdv-novo-gerente-senha').value;
    if (!nome || !senha) {
      mostrarAlert('alert-pdv-novo', 'O gerente precisa digitar nome, sobrenome e senha.', 'error');
      document.getElementById('pdv-novo-gerente-nome').focus();
      return;
    }
    corpo.autorizacao = { nome, sobrenome, senha };
  }

  const botao = document.getElementById('btn-pdv-novo-salvar');
  setLoading(botao, true);
  try {
    const resp = await fetch(url, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(corpo) });
    if (!resp.ok) {
      const mensagem = await safeReadErrorMessage(resp, 'Não foi possível cadastrar.');
      mostrarAlert('alert-pdv-novo', /gerente|autoriz/i.test(mensagem) && corpo.autorizacao
        ? 'Login do gerente não confere ou ele não pode cadastrar produtos.' : mensagem, 'error');
      document.getElementById('pdv-novo-gerente-senha').value = '';
      return;
    }
    const { codigo, quantidade } = pdvCodigoNovo;
    pdvCodigoNovo = null;
    fecharModal('modal-pdv-novo-codigo');
    delete pdvCodigosCache[codigo];
    if (pdvNovoAba === 'produto') await carregarCardapioPDV();
    const leitura = await buscarCodigoPDV(codigo);
    if (leitura && leitura.variacao) await adicionarVariacaoPorCodigoPDV(leitura.variacao, quantidade);
    bipPDV(true);
    mostrarAlert('alert-pdv', `Código ${codigo} cadastrado e lançado na venda.`, 'success');
  } catch (error) {
    mostrarAlert('alert-pdv', error.message || 'Cadastrado, mas não foi possível lançar o item. Bipe de novo.', 'error');
  } finally {
    setLoading(botao, false);
    focarLeituraPDV();
  }
}

function atalhosNovoCodigoPDV(event) {
  if (event.key === 'Escape') { event.preventDefault(); fecharNovoCodigoPDV(); return; }
  if (/^F\d+$/.test(event.key)) { event.preventDefault(); return; }
  if (event.key === 'Enter' && event.target && event.target.tagName === 'INPUT') {
    event.preventDefault();
    salvarNovoCodigoPDV();
  }
}

// ── Vendas deste caixa e cancelamento (F9) ───────────────────────────────
// A lista sai das movimentações da sessão: cada venda tem uma linha por forma paga,
// e um cancelamento aparece como estorno da mesma venda.

let pdvVendasDoCaixa = [];
let pdvVendaSelecionada = null;
let pdvChaveCancelamento = null;

const ROTULO_FORMA_PDV = { DINHEIRO: 'Dinheiro', PIX: 'Pix', DEBITO: 'Débito', CREDITO: 'Crédito', VOUCHER: 'Voucher' };

function horaPDV(iso) {
  const data = new Date(iso);
  return Number.isNaN(data.getTime()) ? '' : data.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
}

function agruparVendasPDV(movimentos) {
  const porVenda = new Map();
  for (const m of movimentos || []) {
    if (!m.vendaId || (m.tipo !== 'VENDA' && m.tipo !== 'ESTORNO_VENDA')) continue;
    const venda = porVenda.get(m.vendaId)
      || { vendaId: m.vendaId, total: 0, estornado: 0, formas: {}, operador: m.operador, dataHora: m.dataHora };
    const valor = Number(m.valor) || 0;
    if (m.tipo === 'VENDA') {
      venda.total += valor;
      venda.formas[m.formaPagamento] = (venda.formas[m.formaPagamento] || 0) + valor;
      venda.operador = m.operador;
      if (String(m.dataHora) < String(venda.dataHora)) venda.dataHora = m.dataHora;
    } else {
      venda.estornado += valor;
    }
    porVenda.set(m.vendaId, venda);
  }
  return [...porVenda.values()]
    .filter(v => v.total > 0)
    .sort((a, b) => String(b.dataHora).localeCompare(String(a.dataHora)) || b.vendaId - a.vendaId);
}

function situacaoVendaPDV(venda) {
  if (venda.estornado >= venda.total - 0.005) return 'Cancelada';
  if (venda.estornado > 0) return 'Cancelada em parte';
  return '';
}

// Sempre na mesma ordem, seja qual for a ordem em que as formas foram lançadas.
function descreverFormasPDV(formas) {
  const ordem = Object.keys(ROTULO_FORMA_PDV);
  return Object.entries(formas)
    .sort(([a], [b]) => (ordem.indexOf(a) + 1 || 99) - (ordem.indexOf(b) + 1 || 99))
    .map(([forma, valor]) => `${ROTULO_FORMA_PDV[forma] || forma} ${formatarMoedaPDV(valor)}`)
    .join(' · ');
}

async function abrirVendasPDV() {
  pdvVendaSelecionada = null;
  limparAlert('alert-pdv-vendas');
  document.getElementById('pdv-venda-detalhe').hidden = true;
  document.getElementById('btn-pdv-cancelar-venda').hidden = true;
  const lista = document.getElementById('pdv-vendas-lista');
  lista.innerHTML = '<p class="inline-note">Carregando as vendas...</p>';
  abrirModal('modal-pdv-vendas');
  try {
    const respCaixas = await fetch('/api/caixas');
    if (!respCaixas.ok) throw new Error(await safeReadErrorMessage(respCaixas, 'Não foi possível ver o caixa.'));
    const caixa = (await respCaixas.json()).find(c => c.numeroCaixa === pdvNumeroCaixa && c.status === 'ABERTO');
    if (!caixa) throw new Error('Este caixa não está aberto.');
    const resp = await fetch(`/api/caixas/sessoes/${caixa.caixaId}/movimentacoes`);
    if (!resp.ok) throw new Error(await safeReadErrorMessage(resp, 'Não foi possível carregar as vendas.'));
    pdvVendasDoCaixa = agruparVendasPDV(await resp.json());
    renderVendasPDV();
  } catch (error) {
    lista.innerHTML = '';
    mostrarAlert('alert-pdv-vendas', error.message, 'error');
  }
}

function renderVendasPDV() {
  const lista = document.getElementById('pdv-vendas-lista');
  if (pdvVendasDoCaixa.length === 0) {
    lista.innerHTML = '<p class="inline-note">Nenhuma venda neste caixa ainda.</p>';
    return;
  }
  lista.innerHTML = pdvVendasDoCaixa.map(v => {
    const situacao = situacaoVendaPDV(v);
    return `<button type="button" class="pdv-venda-linha${situacao ? ' pdv-venda-linha--cancelada' : ''}" data-venda-pdv="${v.vendaId}">`
      + `<span class="pdv-venda-linha__hora">${escapeHtml(horaPDV(v.dataHora))}</span>`
      + `<span>Venda #${v.vendaId} · ${escapeHtml(v.operador || '—')}${situacao ? `<span class="pdv-venda-selo">${situacao}</span>` : ''}</span>`
      + `<strong>${formatarMoedaPDV(v.total)}</strong>`
      + `<span class="pdv-venda-linha__formas">${escapeHtml(descreverFormasPDV(v.formas))}</span>`
      + '</button>';
  }).join('');
  lista.querySelectorAll('[data-venda-pdv]').forEach(botao => {
    botao.addEventListener('click', () => selecionarVendaPDV(Number(botao.dataset.vendaPdv)));
  });
  lista.querySelector('[data-venda-pdv]')?.focus();
}

async function selecionarVendaPDV(vendaId) {
  const venda = pdvVendasDoCaixa.find(v => v.vendaId === vendaId);
  if (!venda) return;
  pdvVendaSelecionada = venda;
  pdvChaveCancelamento = (window.crypto && crypto.randomUUID) ? crypto.randomUUID() : `cancelar-${vendaId}-${Date.now()}`;
  limparAlert('alert-pdv-vendas');
  document.querySelectorAll('[data-venda-pdv]').forEach(b => b.classList.toggle('pdv-venda-linha--ativa', Number(b.dataset.vendaPdv) === vendaId));

  const detalhe = document.getElementById('pdv-venda-detalhe');
  const resumo = document.getElementById('pdv-venda-resumo');
  const campos = document.getElementById('pdv-cancelar-campos');
  const botao = document.getElementById('btn-pdv-cancelar-venda');
  resumo.innerHTML = '<p class="inline-note">Carregando os itens...</p>';
  detalhe.hidden = false;

  let itens = [];
  try {
    const resp = await fetch(`/api/vendas/${vendaId}`);
    if (!resp.ok) throw new Error(await safeReadErrorMessage(resp, 'Não foi possível abrir a venda.'));
    itens = ((await resp.json()).itens || []).filter(i => i.status !== 'CANCELADO');
  } catch (error) {
    mostrarAlert('alert-pdv-vendas', error.message, 'error');
  }
  if (pdvVendaSelecionada !== venda) return;

  const situacao = situacaoVendaPDV(venda);
  const linhas = itens.map(i => `<li><span>${escapeHtml(String(Number(i.quantidade)))} × ${escapeHtml(i.item)}${i.variacao ? ' (' + escapeHtml(i.variacao) + ')' : ''}</span><span>${formatarMoedaPDV(i.subtotal)}</span></li>`).join('');
  const dinheiro = venda.formas.DINHEIRO || 0;
  resumo.innerHTML = `<strong>Venda #${venda.vendaId} · ${escapeHtml(horaPDV(venda.dataHora))} · ${formatarMoedaPDV(venda.total)}</strong>`
    + (linhas ? `<ul class="pdv-venda-itens">${linhas}</ul>` : '')
    + (situacao
      ? `<p class="pdv-venda-devolver">Esta venda já foi ${situacao.toLowerCase()}.</p>`
      : `<p class="pdv-venda-devolver">Devolver ao cliente: ${escapeHtml(descreverFormasPDV(venda.formas))}.`
        + (dinheiro > 0 ? ` ${formatarMoedaPDV(dinheiro)} sai da gaveta.` : ' Nada sai da gaveta.') + ' Os itens voltam para o estoque.</p>');

  const podeCancelar = !situacao;
  campos.hidden = !podeCancelar;
  botao.hidden = !podeCancelar;
  if (!podeCancelar) return;
  document.getElementById('pdv-cancelar-motivo').value = '';
  const precisaGerente = !temAlgumaPermissao([PERMISSOES.VER_FINANCAS, PERMISSOES.EDITAR_ESTOQUE]);
  document.getElementById('pdv-cancelar-autorizacao').hidden = !precisaGerente;
  ['nome', 'sobrenome', 'senha'].forEach(campo => { document.getElementById('pdv-cancelar-gerente-' + campo).value = ''; });
  document.getElementById('pdv-cancelar-motivo').focus();
}

async function confirmarCancelamentoPDV() {
  const venda = pdvVendaSelecionada;
  if (!venda) return;
  limparAlert('alert-pdv-vendas');
  const motivo = document.getElementById('pdv-cancelar-motivo').value.trim();
  if (!motivo) {
    mostrarAlert('alert-pdv-vendas', 'Informe o motivo do cancelamento.', 'error');
    document.getElementById('pdv-cancelar-motivo').focus();
    return;
  }
  const corpo = { motivo };
  if (!document.getElementById('pdv-cancelar-autorizacao').hidden) {
    const nome = document.getElementById('pdv-cancelar-gerente-nome').value.trim();
    const sobrenome = document.getElementById('pdv-cancelar-gerente-sobrenome').value.trim();
    const senha = document.getElementById('pdv-cancelar-gerente-senha').value;
    if (!nome || !senha) {
      mostrarAlert('alert-pdv-vendas', 'O gerente precisa digitar nome, sobrenome e senha.', 'error');
      document.getElementById('pdv-cancelar-gerente-nome').focus();
      return;
    }
    corpo.autorizacao = { nome, sobrenome, senha };
  }

  const botao = document.getElementById('btn-pdv-cancelar-venda');
  setLoading(botao, true);
  try {
    const resp = await fetch(`/api/vendas/${venda.vendaId}/cancelar`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Idempotency-Key': pdvChaveCancelamento },
      body: JSON.stringify(corpo)
    });
    if (!resp.ok) {
      const mensagem = await safeReadErrorMessage(resp, 'Não foi possível cancelar a venda.');
      // Nova tentativa é outro pedido: a mesma chave com outro gerente seria recusada.
      pdvChaveCancelamento = (window.crypto && crypto.randomUUID) ? crypto.randomUUID() : `cancelar-${venda.vendaId}-${Date.now()}`;
      mostrarAlert('alert-pdv-vendas', /gerente|permiss/i.test(mensagem) ? 'Login do gerente não confere ou ele não pode cancelar vendas.' : mensagem, 'error');
      const senha = document.getElementById('pdv-cancelar-gerente-senha');
      if (senha) senha.value = '';
      return;
    }
    const dinheiro = venda.formas.DINHEIRO || 0;
    const aviso = dinheiro > 0 ? ` Devolva ${formatarMoedaPDV(dinheiro)} em dinheiro ao cliente.` : '';
    showToast(`Venda #${venda.vendaId} cancelada.${aviso}`, 'success');
    if (typeof carregarCaixas === 'function') carregarCaixas();
    await abrirVendasPDV();
    mostrarAlert('alert-pdv-vendas', `Venda #${venda.vendaId} cancelada.${aviso}`, 'success');
  } finally {
    setLoading(botao, false);
  }
}

function fecharVendasPDV() {
  fecharModal('modal-pdv-vendas');
  pdvVendaSelecionada = null;
  focarLeituraPDV();
}

function atalhosVendasPDV(event) {
  if (event.key === 'Escape') { event.preventDefault(); fecharVendasPDV(); return; }
  if (/^F\d+$/.test(event.key)) { event.preventDefault(); return; }
  const alvo = event.target;
  if (alvo && alvo.dataset && alvo.dataset.vendaPdv && (event.key === 'ArrowDown' || event.key === 'ArrowUp')) {
    event.preventDefault();
    const linhas = [...document.querySelectorAll('[data-venda-pdv]')];
    const posicao = linhas.indexOf(alvo) + (event.key === 'ArrowDown' ? 1 : -1);
    linhas[Math.max(0, Math.min(linhas.length - 1, posicao))]?.focus();
    return;
  }
  if (event.key === 'Enter' && alvo && alvo.tagName === 'INPUT' && alvo.closest('#pdv-cancelar-campos')) {
    event.preventDefault();
    confirmarCancelamentoPDV();
  }
}

function nomeDoOperadorAtual() {
  return [usuario.nome, usuario.sobrenome].filter(Boolean).join(' ') || '—';
}

function atualizarOperadorPDV() {
  const el = document.getElementById('pdv-operador-label');
  if (el) el.textContent = nomeDoOperadorAtual();
}

async function abrirTrocaOperadorPDV() {
  limparAlert('alert-pdv-operador');
  document.getElementById('pdv-operador-pin').value = '';
  document.getElementById('pdv-operador-trocar').hidden = false;
  document.getElementById('pdv-operador-definir').hidden = true;
  document.getElementById('btn-pdv-operador').hidden = false;
  document.getElementById('pdv-meu-pin-nome').textContent = nomeDoOperadorAtual();
  const select = document.getElementById('pdv-operador-select');
  select.replaceChildren();
  abrirModal('modal-pdv-operador');
  try {
    const resp = await fetch('/api/auth/operadores');
    if (!resp.ok) throw new Error(await safeReadErrorMessage(resp, 'Não foi possível listar os operadores.'));
    const operadores = (await resp.json()).filter(o => Number(o.ru) !== Number(usuario.ru));
    select.replaceChildren(...operadores.map(o => {
      const option = document.createElement('option');
      option.value = o.ru;
      option.textContent = o.nome;
      return option;
    }));
    if (operadores.length === 0) {
      mostrarAlert('alert-pdv-operador', 'Ninguém mais tem PIN. Cada pessoa define o seu ao entrar com a própria senha.', 'error');
    }
    document.getElementById('pdv-operador-pin').focus();
  } catch (error) {
    mostrarAlert('alert-pdv-operador', error.message, 'error');
  }
}

function fecharTrocaOperadorPDV() {
  fecharModal('modal-pdv-operador');
  focarLeituraPDV();
}

async function confirmarTrocaOperadorPDV() {
  limparAlert('alert-pdv-operador');
  const ru = Number(document.getElementById('pdv-operador-select').value);
  const campoPin = document.getElementById('pdv-operador-pin');
  if (!ru) return;
  try {
    const resp = await fetch('/api/auth/trocar-operador', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ ru, pin: campoPin.value })
    });
    if (!resp.ok) throw new Error(await safeReadErrorMessage(resp, 'Não foi possível trocar de operador.'));
    Object.assign(usuario, await resp.json());
    persistirUsuario(usuario);
    pdvOperadorTrocado = true;
    atualizarOperadorPDV();
    fecharTrocaOperadorPDV();
    showToast(`${nomeDoOperadorAtual()} está operando o caixa.`, 'success');
  } catch (error) {
    mostrarAlert('alert-pdv-operador', error.message, 'error');
    campoPin.value = '';
    campoPin.focus();
  }
}

function alternarDefinirPinPDV() {
  const definir = document.getElementById('pdv-operador-definir');
  definir.hidden = !definir.hidden;
  document.getElementById('pdv-operador-trocar').hidden = !definir.hidden;
  document.getElementById('btn-pdv-operador').hidden = !definir.hidden;
  if (!definir.hidden) document.getElementById('pdv-meu-pin-senha').focus();
}

async function salvarMeuPinPDV() {
  limparAlert('alert-pdv-operador');
  const senha = document.getElementById('pdv-meu-pin-senha');
  const pin = document.getElementById('pdv-meu-pin');
  try {
    const resp = await fetch('/api/auth/pin', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ senhaAtual: senha.value, pin: pin.value })
    });
    if (!resp.ok) throw new Error(await safeReadErrorMessage(resp, 'Não foi possível salvar o PIN.'));
    Object.assign(usuario, await resp.json());
    persistirUsuario(usuario);
    mostrarAlert('alert-pdv-operador', 'PIN salvo.', 'success');
    senha.value = '';
    pin.value = '';
    alternarDefinirPinPDV();
  } catch (error) {
    mostrarAlert('alert-pdv-operador', error.message, 'error');
  }
}

/** Atalhos do PDV (só com o terminal aberto). */
function atalhosPDV(event) {
  const modal = document.getElementById('modal-pdv');
  if (!modal || !modal.classList.contains('open')) return;
  // Com o PDV aberto, o Esc é só dele: o atalho geral da página fecharia o PDV junto
  // (ao voltar do pagamento, ao desistir de sair ou ao fechar uma janela por cima).
  if (event.key === 'Escape' && !document.getElementById('modal-pdv-configuracao')?.classList.contains('open')) {
    event.stopImmediatePropagation();
  }
  if (document.getElementById('modal-pdv-operador')?.classList.contains('open')) {
    if (event.key === 'Escape') { event.preventDefault(); fecharTrocaOperadorPDV(); return; }
    if (event.key === 'F12') { event.preventDefault(); return; }
    if (event.key === 'Enter' && event.target.tagName !== 'BUTTON') {
      event.preventDefault();
      if (document.getElementById('pdv-operador-definir').hidden) confirmarTrocaOperadorPDV();
      else salvarMeuPinPDV();
    }
    return;
  }
  if (document.getElementById('modal-pdv-vendas')?.classList.contains('open')) {
    atalhosVendasPDV(event);
    return;
  }
  if (document.getElementById('modal-pdv-novo-codigo')?.classList.contains('open')) {
    atalhosNovoCodigoPDV(event);
    return;
  }
  if (document.getElementById('modal-pdv-comandas')?.classList.contains('open')) {
    atalhosComandasPDV(event);
    return;
  }
  if (document.getElementById('modal-pdv-dividir')?.classList.contains('open')) {
    atalhosDivisaoPDV(event);
    return;
  }
  if (document.getElementById('modal-pdv-transferir')?.classList.contains('open')) {
    atalhosTransferenciaPDV(event);
    return;
  }
  if (document.getElementById('modal-pdv-retirar')?.classList.contains('open')) {
    atalhosRetiradaPDV(event);
    return;
  }
  if (document.getElementById('modal-pdv-teclas')?.classList.contains('open')) {
    atalhosTeclasPDV(event);
    return;
  }
  if (document.getElementById('modal-cozinha')?.classList.contains('open')) {
    if (event.key === 'Escape') { event.preventDefault(); fecharCozinha(); }
    else if (/^F\d+$/.test(event.key)) event.preventDefault();
    return;
  }
  if (document.getElementById('modal-pdv-configuracao')?.classList.contains('open')) return;
  const pagamentoAberto = !document.getElementById('pdv-pagamento')?.hidden;

  if (event.altKey && /^Digit[1-8]$/.test(event.code || '')) {
    event.preventDefault();
    lancarTeclaPDV(Number(event.code.slice(5)));
    return;
  }
  if (event.key === 'F9') { event.preventDefault(); if (pagamentoAberto) abrirDivisaoPDV(); else abrirVendasPDV(); return; }
  if (event.key === 'F7' && !pagamentoAberto) { event.preventDefault(); abrirComandasPDV(); return; }
  if (event.key === 'F6' && !pagamentoAberto) { event.preventDefault(); abrirTransferenciaPDV(); return; }
  if (event.key === 'F8' && !pagamentoAberto) { event.preventDefault(); imprimirConferenciaOuUltimoCupomPDV(); return; }

  if (/^F[5-8]$/.test(event.key)) event.preventDefault();
  if (event.key === 'F12') { event.preventDefault(); abrirTrocaOperadorPDV(); return; }
  if (event.key === 'F10') { event.preventDefault(); if (!pagamentoAberto) abrirPagamentoPDV(); return; }
  if (event.key === 'F4') { event.preventDefault(); alternarProximoAlternativoPDV(); return; }
  if (event.key === 'F2') { event.preventDefault(); document.getElementById('pdv-busca')?.focus(); return; }
  if (event.key === 'F3') {
    event.preventDefault();
    // O pagamento põe o foco no valor recebido logo depois de abrir; o desconto vem em seguida.
    if (!pagamentoAberto && pdvCarrinho.length > 0) { abrirPagamentoPDV(); setTimeout(abrirDescontoPDV, 60); }
    else abrirDescontoPDV();
    return;
  }
  if (event.key === 'Escape') {
    event.preventDefault();
    const descontoAberto = !document.getElementById('pdv-desconto-form')?.hidden;
    if (pagamentoAberto && descontoAberto) fecharDescontoPDV();
    else if (pagamentoAberto) cancelarPagamentoPDV();
    else if (pdvComandaAtual) guardarComandaPDV();
    else fecharPDV();
    return;
  }
  if (pagamentoAberto) {
    const forma = PDV_FORMAS.find(f => f.tecla === event.key);
    if (forma) {
      event.preventDefault();
      selecionarFormaPDV(forma.codigo);
      if (forma.codigo === 'DINHEIRO') document.getElementById('pdv-pag-recebido')?.focus();
      return;
    }
    if (event.key === 'Enter') {
      event.preventDefault();
      if (event.target && event.target.closest && event.target.closest('#pdv-desconto-form')) aplicarDescontoPDV();
      else confirmarPagamentoPDV();
    }
    return;
  }
  // Leitor bipando com o foco fora do campo: manda para o campo de leitura.
  const tag = event.target && event.target.tagName;
  if (/^[0-9*]$/.test(event.key) && tag !== 'INPUT' && tag !== 'TEXTAREA' && tag !== 'SELECT') {
    focarLeituraPDV();
  }
}
document.addEventListener('keydown', atalhosPDV);

window.StockOSPDV = {
  activate() {
    Object.assign(window, {
      abrirPDV,
      mostrarPainelPDVMobile,
      carregarCardapioPDV,
      carregarVariacoesItemPDV,
      getCategoriaConfig,
      getCategoriaByCodigo,
      countItensCategoria,
      renderTabsCategorias,
      selecionarCategoriaPDV,
      getBuscaAtualPDV,
      itemCombinaBusca,
      itensFiltradosPDV,
      renderCardapio,
      getBadgeTipo,
      getStatusItem,
      renderCardProduto,
      filtrarProdutosPDV,
      adicionarAoCarrinho,
      selecionarVariacaoPadraoPDV,
      abrirSeletorVariacaoPDV,
      selecionarVariacaoConfiguracaoPDV,
      abrirConfiguracaoItemPDV,
      selecoesPadraoConfiguracaoPDV,
      selecoesDoItemVendaPDV,
      renderConfiguracaoPDV,
      renderGrupoConfiguracaoPDV,
      renderOpcaoConfiguracaoPDV,
      selecaoAtualPDV,
      grupoConfiguracaoPDV,
      opcaoConfiguracaoPDV,
      motivoOpcaoPDV,
      selecionarOpcaoPDV,
      alterarQuantidadeOpcaoPDV,
      alterarQuantidadeConfiguracaoPDV,
      alterarObservacaoConfiguracaoPDV,
      trocarVariacaoConfiguracaoPDV,
      validarConfiguracaoLocalPDV,
      validarConfiguracaoVisualPDV,
      atualizarResumoConfiguracaoPDV,
      confirmarConfiguracaoPDV,
      payloadItemVendaPDV,
      validarConfiguracaoBackendPDV,
      persistirItemConfiguradoPDV,
      registrarPayloadNovoItemPDV,
      recarregarVendaAtualPDV,
      itensAtivosVendaPDV,
      editarItemCarrinhoPDV,
      removerItemCompleto,
      limparCarrinho,
      quantidadeItemNoCarrinhoPDV,
      resumoModificadoresCarrinhoPDV,
      mensagemValidacaoPDV,
      variacaoDisponivelPDV,
      gerarIdempotencyKeyPDV,
      garantirModalConfiguracaoPDV,
      calcularTotalCarrinho,
      renderCarrinho,
      finalizarVendaPDV,
      formatarMoedaPDV,
      lerCodigoPDV,
      focarLeituraPDV,
      abrirTrocaOperadorPDV,
      abrirVendasPDV,
      abrirTransferenciaPDV,
      marcarTransferenciaPDV,
      fecharTransferenciaPDV,
      confirmarTransferenciaPDV,
      abrirRetiradaPDV,
      lancarTeclaPDV,
      abrirCozinha,
      fecharCozinha,
      salvarCozinha,
      testarCozinha,
      abrirTeclasPDV,
      fecharTeclasPDV,
      salvarTeclasPDV,
      fecharRetiradaPDV,
      confirmarRetiradaPDV,
      abrirDivisaoPDV,
      escolherAbaDivisaoPDV,
      previaDivisaoIgualPDV,
      somarItensDivisaoPDV,
      confirmarDivisaoPDV,
      pararDivisaoPDV,
      fecharDivisaoPDV,
      alternarCupomAutomaticoPDV,
      abrirComandasPDV,
      abrirComandaDigitadaPDV,
      guardarComandaPDV,
      fecharComandasPDV,
      fecharNovoCodigoPDV,
      escolherAbaNovoCodigoPDV,
      buscarProdutoNovoCodigoPDV,
      salvarNovoCodigoPDV,
      fecharVendasPDV,
      confirmarCancelamentoPDV,
      fecharTrocaOperadorPDV,
      confirmarTrocaOperadorPDV,
      alternarDefinirPinPDV,
      salvarMeuPinPDV,
      alternarProximoAlternativoPDV,
      abrirPagamentoPDV,
      abrirDescontoPDV,
      aplicarDescontoPDV,
      cancelarPagamentoPDV,
      selecionarFormaPDV,
      adicionarPagamentoPDV,
      removerPagamentoPDV,
      renderPagamentoPDV,
      confirmarPagamentoPDV,
      fecharPDV,
      recarregarDisponibilidadePDV,
      contextoOperacionalPDV,
      urlDisponibilidadePDV,
      normalizarDisponibilidadePDV,
      itemDisponivelParaSelecaoPDV,
      resumoMotivoIndisponibilidade,
      primeiroAlertaDisponibilidade,
      capacidadeDisponivelItem,
    });
  }
};
if (/^\/caixas(?:\.html)?\/?$/.test(location.pathname)) window.StockOSPDV.activate();
})();
