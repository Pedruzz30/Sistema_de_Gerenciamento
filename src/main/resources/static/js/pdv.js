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
let pdvCarrinho = [];
let pdvCategoriaAtiva = 'todos';

async function abrirPDV(numeroCaixa) {
  pdvNumeroCaixa = numeroCaixa;
  pdvCarrinho = [];
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
    const [categoriasResp, itensResp] = await Promise.all([
      fetch('/api/cardapio/categorias'),
      fetch('/api/cardapio/itens')
    ]);
    if (!categoriasResp.ok || !itensResp.ok) throw new Error();

    pdvCategorias = await categoriasResp.json();
    pdvItens = await itensResp.json();
    pdvItensMap = {};
    pdvItens.forEach(item => {
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
  const semEstoque = !item.disponivel;
  const quantidadeCarrinho = (pdvCarrinho.find(entry => entry.itemId === item.id) || {}).quantidade || 0;

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

function adicionarAoCarrinho(itemId) {
  const item = pdvItensMap[itemId];
  if (!item || !item.disponivel) return;

  const existente = pdvCarrinho.find(entry => entry.itemId === itemId);
  if (existente) {
    existente.quantidade += 1;
  } else {
    pdvCarrinho.push({ itemId, quantidade: 1 });
  }

  const card = document.getElementById(`pdv-card-${itemId}`);
  if (card) {
    card.classList.remove('pdv-card--pulse');
    window.requestAnimationFrame(() => {
      card.classList.add('pdv-card--pulse');
      setTimeout(() => card.classList.remove('pdv-card--pulse'), 180);
    });
  }

  renderCarrinho();
  atualizarCardCarrinho(itemId);
}

function removerDoCarrinho(itemId) {
  const idx = pdvCarrinho.findIndex(entry => entry.itemId === itemId);
  if (idx === -1) return;

  if (pdvCarrinho[idx].quantidade > 1) {
    pdvCarrinho[idx].quantidade -= 1;
  } else {
    pdvCarrinho.splice(idx, 1);
  }

  renderCarrinho();
  atualizarCardCarrinho(itemId);
}

function removerItemCompleto(itemId) {
  pdvCarrinho = pdvCarrinho.filter(entry => entry.itemId !== itemId);
  renderCarrinho();
  atualizarCardCarrinho(itemId);
}

function limparCarrinho() {
  const ids = pdvCarrinho.map(entry => entry.itemId);
  pdvCarrinho = [];
  renderCarrinho();
  ids.forEach(atualizarCardCarrinho);
}

function atualizarCardCarrinho(itemId) {
  const card = document.getElementById(`pdv-card-${itemId}`);
  const item = pdvItensMap[itemId];
  if (card && item) {
    card.outerHTML = renderCardProduto(item);
  }
}

function calcularTotalCarrinho() {
  return pdvCarrinho.reduce((total, entry) => {
    const item = pdvItensMap[entry.itemId];
    return total + ((item ? Number(item.precoVenda) : 0) * entry.quantidade);
  }, 0);
}

function renderCarrinho() {
  const container = document.getElementById('pdv-carrinho-itens');
  const empty = document.getElementById('pdv-carrinho-empty');
  const btnFin = document.getElementById('btn-pdv-finalizar');
  const elTotal = document.getElementById('pdv-total');
  const elSub = document.getElementById('pdv-subtotal');
  const elQtd = document.getElementById('pdv-carrinho-qtd');

  const total = calcularTotalCarrinho();
  const qtdItens = pdvCarrinho.reduce((soma, entry) => soma + entry.quantidade, 0);

  if (elQtd) {
    elQtd.textContent = qtdItens > 0 ? `${qtdItens} ite${qtdItens === 1 ? 'm' : 'ns'}` : '';
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
    const item = pdvItensMap[entry.itemId];
    if (!item) return;

    const categoria = getCategoriaByCodigo(item.categoriaCodigo);
    const config = getCategoriaConfig(categoria.codigo);
    const subtotal = Number(item.precoVenda) * entry.quantidade;

    const row = document.createElement('div');
    row.className = 'pdv-item-row';
    row.style.setProperty('--pdv-accent', config.accent);
    row.style.setProperty('--pdv-soft', config.soft);
    row.innerHTML = `
      <div class="pdv-item-icon">${iconMarkup(config.icon)}</div>
      <div class="pdv-item-content">
        <div class="pdv-item-name">${escapeHtml(item.nome)}</div>
        <div class="pdv-item-meta">${escapeHtml(categoria.nomeExibicao)} - ${formatarMoedaPDV(item.precoVenda)} x ${entry.quantidade}</div>
      </div>
      <div class="pdv-item-subtotal">${formatarMoedaPDV(subtotal)}</div>
      <div class="pdv-item-controls">
        <button class="pdv-item-btn" onclick="removerDoCarrinho(${item.id})" title="Remover 1" type="button">-</button>
        <span class="pdv-item-qty">${entry.quantidade}</span>
        <button class="pdv-item-btn pdv-item-btn--accent" onclick="adicionarAoCarrinho(${item.id})" title="Adicionar 1" type="button">+</button>
        <button class="pdv-item-btn pdv-item-btn--remove" onclick="removerItemCompleto(${item.id})" title="Remover item" type="button">x</button>
      </div>
    `;
    container.appendChild(row);
  });

  elTotal.textContent = formatarMoedaPDV(total);
  if (elSub) elSub.textContent = formatarMoedaPDV(total);
}

async function finalizarVendaPDV() {
  if (pdvCarrinho.length === 0) return;

  const btn = document.getElementById('btn-pdv-finalizar');
  const total = calcularTotalCarrinho();
  const descricao = pdvCarrinho
    .map(entry => `${entry.quantidade}x ${(pdvItensMap[entry.itemId] || {}).nome || 'Item'}`)
    .join(', ');

  setLoading(btn, true);
  limparAlert('alert-pdv');

  try {
    const itens = pdvCarrinho.map(entry => ({
      itemCardapioId: entry.itemId,
      quantidade: entry.quantidade
    }));

    const resp = await fetch('/api/caixas/venda', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ numeroCaixa: pdvNumeroCaixa, valor: total, descricao, itens })
    });

    if (!resp.ok) {
      const msg = await safeReadErrorMessage(resp, 'Erro ao registrar venda.');
      mostrarAlert('alert-pdv', msg, 'error');
      return;
    }

    btn.textContent = 'Venda registrada!';
    btn.classList.add('pdv-submit--success');
    setTimeout(() => {
      fecharModal('modal-pdv');
      pdvCarrinho = [];
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
