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
  renderCarrinho();
  await carregarCardapioPDV();
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
}

async function finalizarVendaPDV() {
  if (!pdvVendaAtual || pdvCarrinho.length === 0) return;

  const btn = document.getElementById('btn-pdv-finalizar');
  const total = calcularTotalCarrinho();
  setLoading(btn, true);
  limparAlert('alert-pdv');

  try {
    const resp = await fetch(`/api/vendas/${pdvVendaAtual.id}/finalizar`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Idempotency-Key': gerarIdempotencyKeyPDV('finalizar')
      },
      body: JSON.stringify({ numeroCaixaLegado: pdvNumeroCaixa })
    });

    if (!resp.ok) {
      const msg = await safeReadErrorMessage(resp, 'Erro ao finalizar venda.');
      mostrarAlert('alert-pdv', msg, 'error');
      await recarregarDisponibilidadePDV();
      renderCardapio();
      return;
    }

    pdvVendaAtual = await resp.json();
    pdvCarrinho = itensAtivosVendaPDV();
    btn.textContent = 'Venda registrada!';
    btn.classList.add('pdv-submit--success');
    setTimeout(() => {
      fecharModal('modal-pdv');
      pdvCarrinho = [];
      pdvVendaAtual = null;
      pdvDraftPayloadPorItemVenda = {};
      showToast(`${formatarMoedaPDV(total)} registrado no Caixa ${pdvNumeroCaixa}!`, 'success');
      carregarCaixas();
    }, 700);
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
