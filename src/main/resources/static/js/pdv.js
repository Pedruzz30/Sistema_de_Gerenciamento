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

async function abrirPDV(numeroCaixa) {
  pdvNumeroCaixa = numeroCaixa;
  pdvCarrinho = [];
  pdvVendaAtual = null;
  pdvDraftPayloadPorItemVenda = {};
  pdvCategoriaAtiva = 'todos';

  document.getElementById('pdv-caixa-label').textContent = `Caixa ${numeroCaixa}`;
  document.getElementById('pdv-busca').value = '';
  limparAlert('alert-pdv');

  abrirModal('modal-pdv');
  atualizarOperadorPDV();
  cancelarPagamentoPDV();
  pdvProximoAlternativo = false;
  atualizarIndicadorAlternativoPDV();
  renderCarrinho();
  focarLeituraPDV();
  await Promise.all([carregarCardapioPDV(), carregarCodigosPDV()]);
  focarLeituraPDV();
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
  if (!pdvVendaAtual) return;
  const resp = await fetch(`/api/vendas/${pdvVendaAtual.id}/itens/${itemVendaId}`, { method: 'DELETE' });
  if (!resp.ok) {
    mostrarAlert('alert-pdv', await safeReadErrorMessage(resp, 'Erro ao remover item.'), 'error');
    return;
  }
  delete pdvDraftPayloadPorItemVenda[itemVendaId];
  await recarregarVendaAtualPDV();
}

function limparCarrinho() {
  const ids = itensAtivosVendaPDV().map(item => item.id);
  if (!ids.length) return;
  Promise.all(ids.map(id => fetch(`/api/vendas/${pdvVendaAtual.id}/itens/${id}`, { method: 'DELETE' }).catch(() => null)))
    .then(() => {
      pdvVendaAtual = null;
      pdvCarrinho = [];
      pdvDraftPayloadPorItemVenda = {};
      renderCarrinho();
      renderCardapio();
    });
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

  elTotal.textContent = formatarMoedaPDV(total);
  if (elSub) elSub.textContent = formatarMoedaPDV(total);
  if (!document.getElementById('pdv-pagamento')?.hidden) renderPagamentoPDV();
}

async function finalizarVendaPDV() {
  if (!pdvVendaAtual || pdvCarrinho.length === 0) return;

  const btn = document.getElementById('btn-pdv-confirmar-pagamento') || document.getElementById('btn-pdv-finalizar');
  const total = calcularTotalCarrinho();
  const troco = trocoPDV();
  const pagamentos = pdvPagamentos.map(p => ({
    forma: p.forma,
    valor: arred2(p.valor).toFixed(2),
    valorRecebido: p.valorRecebido == null ? null : arred2(p.valorRecebido).toFixed(2)
  }));
  setLoading(btn, true);
  limparAlert('alert-pdv');

  try {
    const resp = await fetch(`/api/vendas/${pdvVendaAtual.id}/finalizar`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Idempotency-Key': gerarIdempotencyKeyPDV('finalizar')
      },
      body: JSON.stringify({ numeroCaixaLegado: pdvNumeroCaixa, pagamentos })
    });

    if (!resp.ok) {
      const msg = await safeReadErrorMessage(resp, 'Erro ao finalizar venda.');
      mostrarAlert('alert-pdv', msg, 'error');
      bipPDV(false);
      await recarregarDisponibilidadePDV();
      renderCardapio();
      return;
    }

    await resp.json();
    // Próximo cliente: o PDV continua aberto, com carrinho limpo e foco na leitura.
    pdvCarrinho = [];
    pdvVendaAtual = null;
    pdvDraftPayloadPorItemVenda = {};
    cancelarPagamentoPDV();
    renderCarrinho();
    const mensagemTroco = troco > 0 ? ` · TROCO ${formatarMoedaPDV(troco)}` : '';
    mostrarAlert('alert-pdv', `Venda de ${formatarMoedaPDV(total)} registrada${mensagemTroco}.`, 'success');
    showToast(`${formatarMoedaPDV(total)} registrado no Caixa ${pdvNumeroCaixa}${mensagemTroco}`, 'success');
    carregarCaixas();
    focarLeituraPDV();
  } finally {
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
      mostrarAlert('alert-pdv', `Produto não cadastrado para o código ${codigo}. Peça ao gerente para cadastrar no Cardápio/Estoque.`, 'error');
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
async function adicionarVariacaoPorCodigoPDV(variacao, quantidade) {
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
  pdvPagamentos = [];
  selecionarFormaPDV('DINHEIRO');
  document.getElementById('pdv-pagamento').hidden = false;
  document.getElementById('btn-pdv-finalizar').hidden = true;
  renderPagamentoPDV();
  setTimeout(() => document.getElementById('pdv-pag-recebido')?.focus(), 30);
}

function cancelarPagamentoPDV() {
  pdvPagamentos = [];
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
  if (valor) valor.value = restantePDV() > 0 ? restantePDV().toFixed(2) : '';
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
  pdvPagamentos.push({ forma: pdvFormaSelecionada, valor, valorRecebido: recebido });
  selecionarFormaPDV(pdvFormaSelecionada);
  renderPagamentoPDV();
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
      <span>${escapeHtml(rotulo(p.forma))}</span>
      <span>${formatarMoedaPDV(p.valor)}${p.valorRecebido != null && p.valorRecebido > p.valor ? ` <small>(recebido ${formatarMoedaPDV(p.valorRecebido)})</small>` : ''}</span>
      <button type="button" class="pdv-item-btn pdv-item-btn--remove" onclick="removerPagamentoPDV(${i})" aria-label="Remover pagamento">x</button>
    </div>`).join('');
  document.getElementById('pdv-pag-restante').textContent = formatarMoedaPDV(Math.max(restantePDV(), 0));

  // Troco previsto enquanto a operadora digita o valor recebido.
  let trocoPrevisto = trocoPDV();
  const recebidoDigitado = lerNumeroCampo('pdv-pag-recebido');
  const valorDigitado = lerNumeroCampo('pdv-pag-valor');
  if (pdvFormaSelecionada === 'DINHEIRO' && recebidoDigitado != null) {
    const base = valorDigitado == null ? restantePDV() : valorDigitado;
    trocoPrevisto = arred2(trocoPrevisto + Math.max(recebidoDigitado - base, 0));
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
    mostrarAlert('alert-pdv', `Ainda falta ${formatarMoedaPDV(restantePDV())}. Escolha outra forma para o restante.`, 'error');
    return;
  }
  await finalizarVendaPDV();
}

function fecharPDV() {
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
  if (document.getElementById('modal-pdv-configuracao')?.classList.contains('open')) return;
  const pagamentoAberto = !document.getElementById('pdv-pagamento')?.hidden;

  if (event.key === 'F9') { event.preventDefault(); if (!pagamentoAberto) abrirVendasPDV(); return; }

  if (/^F[5-8]$/.test(event.key)) event.preventDefault();
  if (event.key === 'F12') { event.preventDefault(); abrirTrocaOperadorPDV(); return; }
  if (event.key === 'F10') { event.preventDefault(); if (!pagamentoAberto) abrirPagamentoPDV(); return; }
  if (event.key === 'F4') { event.preventDefault(); alternarProximoAlternativoPDV(); return; }
  if (event.key === 'F2') { event.preventDefault(); document.getElementById('pdv-busca')?.focus(); return; }
  if (event.key === 'Escape') {
    event.preventDefault();
    if (pagamentoAberto) cancelarPagamentoPDV(); else fecharPDV();
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
    if (event.key === 'Enter') { event.preventDefault(); confirmarPagamentoPDV(); }
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
      fecharVendasPDV,
      confirmarCancelamentoPDV,
      fecharTrocaOperadorPDV,
      confirmarTrocaOperadorPDV,
      alternarDefinirPinPDV,
      salvarMeuPinPDV,
      alternarProximoAlternativoPDV,
      abrirPagamentoPDV,
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
