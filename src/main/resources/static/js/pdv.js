// ── PDV: Cardápio Digital — Pizzaria + Bebidas ───────────────────────────
//
// Fluxo:
//   1. abrirPDV(numeroCaixa)  → carrega bebidas do estoque + pizzas do catálogo, abre terminal
//   2. Seleciona categoria     → filtra cards do cardápio
//   3. Clica no item           → adiciona ao carrinho com animação
//   4. finalizarVendaPDV()    → POST /api/caixas/venda com total calculado

const CATEGORIAS_CARDAPIO = [
  {
    id:       'pizza',
    label:    'Pizzas',
    icone:    '🍕',
    cor:      '#f59e0b',         // âmbar
    corSoft:  'rgba(245,158,11,.15)',
  },
  {
    id:       'bebida',
    label:    'Bebidas',
    icone:    '🥤',
    cor:      '#06b6d4',         // cyan
    corSoft:  'rgba(6,182,212,.15)',
  },
];

// Categoria especial "Todos" — não tem palavras-chave, aparece sempre
const CATEGORIA_TODOS = { id: 'todos', label: 'Todos', icone: '⊞', cor: '#7c3aed', corSoft: 'rgba(124,58,237,.15)' };

// ── Estado ────────────────────────────────────────────────────────────────

let pdvNumeroCaixa    = null;
let pdvProdutos       = [];       // itens elegíveis e normalizados para o cardápio
let pdvCarrinho       = [];       // [{ produto, quantidade }]
let pdvCategoriaAtiva = 'todos';
let pdvCatalogo       = {
  items: [],
  byCategory: { pizza: [], bebida: [] },
  diagnostics: { hidden: [], legacy: [], unmapped: [] }
};
let pdvProdutosMap    = {};

// ── Abertura do terminal ──────────────────────────────────────────────────

async function abrirPDV(numeroCaixa) {
  pdvNumeroCaixa    = numeroCaixa;
  pdvCarrinho       = [];
  pdvCategoriaAtiva = 'todos';

  document.getElementById('pdv-caixa-label').textContent = `Caixa ${numeroCaixa}`;
  document.getElementById('pdv-busca').value = '';
  limparAlert('alert-pdv');

  abrirModal('modal-pdv');
  renderCarrinho();
  await carregarProdutosPDV();
}

// ── Carregamento de produtos ──────────────────────────────────────────────

async function carregarProdutosPDV() {
  const grid = document.getElementById('pdv-produtos-grid');
  grid.innerHTML = emptyStateMarkup({
    icon: 'cart',
    title: 'Carregando cardápio',
    copy: 'Buscando bebidas em estoque e pizzas sob demanda para este caixa.'
  });

  try {
    if (typeof normalizeRawProduct !== 'function' || typeof buildMenuCatalog !== 'function') {
      throw new Error('Adaptador do cardápio indisponível.');
    }

    const [produtosResp, pizzasResp] = await Promise.all([
      fetch('/api/produtos'),
      fetch('/api/cardapio/pizzas')
    ]);
    if (!produtosResp.ok || !pizzasResp.ok) throw new Error();

    const [produtosBrutos, pizzasBrutas] = await Promise.all([
      produtosResp.json(),
      pizzasResp.json()
    ]);

    // O PDV passa a considerar /api/produtos apenas para itens realmente
    // controlados por estoque, evitando que pizzas legadas em estoque fake
    // voltem a aparecer no cardápio principal.
    const bebidasNormalizadas = produtosBrutos
      .map(normalizeRawProduct)
      .filter(item => item.categoriaCardapio === 'bebida');
    const pizzasNormalizadas = pizzasBrutas
      .map(normalizeRawProduct)
      .filter(item => item.categoriaCardapio === 'pizza');

    pdvCatalogo = buildMenuCatalog([].concat(pizzasNormalizadas, bebidasNormalizadas));
    pdvProdutos = pdvCatalogo.items.slice();
    pdvProdutosMap = {};
    pdvProdutos.forEach(item => { pdvProdutosMap[item.productId] = item; });

    if (pdvCatalogo.diagnostics.hidden.length > 0) {
      console.warn('PDV: itens fora do cardápio principal.', pdvCatalogo.diagnostics.hidden.map(item => ({
        id: item.productId,
        nome: item.nome,
        diagnosticos: item.diagnostics
      })));
    }

    renderTabsCategorias();
    renderCardapio();
  } catch {
    grid.innerHTML = emptyStateMarkup({
      icon: 'alertTriangle',
      title: 'Erro ao carregar cardápio',
      copy: 'Verifique se o servidor está rodando e tente novamente.'
    });
  }
}

function getCategoriaConfig(id) {
  return CATEGORIAS_CARDAPIO.find(c => c.id === id)
    || { id, label: 'Cardápio', icone: '📦', cor: '#8899bb', corSoft: 'rgba(136,153,187,.12)' };
}

function getCategoriaClasse(id) {
  if (id === 'pizza') return 'pizza';
  if (id === 'bebida') return 'bebida';
  return 'todos';
}

function itemUsaEstoque(item) {
  return item && item.controladoPorEstoque !== false;
}

function itemDisponivel(item) {
  if (!item) return false;
  if (!itemUsaEstoque(item)) return item.disponivel !== false;
  return Number(item.quantidadeAtual || 0) > 0;
}

function itemStatusLabel(item) {
  if (!itemUsaEstoque(item)) return 'Preparada sob demanda';
  if (!itemDisponivel(item)) return 'Indisponível';
  return `${item.quantidadeAtual} em estoque`;
}

// ── Render: abas de categoria ─────────────────────────────────────────────

function renderTabsCategorias() {
  const container = document.getElementById('pdv-categorias');
  const tabs = [CATEGORIA_TODOS, ...CATEGORIAS_CARDAPIO];
  const contagem = {
    pizza: pdvCatalogo.byCategory.pizza.reduce((soma, grupo) => soma + grupo.items.length, 0),
    bebida: pdvCatalogo.byCategory.bebida.reduce((soma, grupo) => soma + grupo.items.length, 0)
  };
  const diagnosticos = pdvCatalogo.diagnostics.hidden.length;

  const botoes = tabs.map(cat => {
    const ativo = cat.id === pdvCategoriaAtiva;
    const qtd   = cat.id === 'todos'
      ? pdvProdutos.length
      : (contagem[cat.id] || 0);

    if (qtd === 0 && cat.id !== 'todos') return ''; // Oculta aba vazia

    return `<button
      type="button"
      class="pdv-category-tab pdv-category-tab--${getCategoriaClasse(cat.id)}${ativo ? ' pdv-category-tab--active' : ''}"
      onclick="selecionarCategoriaPDV('${cat.id}')"
    >
      <span aria-hidden="true">${cat.icone}</span>
      <span>${cat.label}</span>
      <span class="pdv-category-tab__count">${qtd}</span>
    </button>`;
  }).join('');

  const notaDiagnostico = diagnosticos > 0
    ? `<div class="pdv-category-note">
         ${diagnosticos} item(ns) fora do cardápio principal aguardando mapeamento.
       </div>`
    : '';

  container.innerHTML = botoes + notaDiagnostico;
}

function selecionarCategoriaPDV(catId) {
  pdvCategoriaAtiva = catId;
  renderTabsCategorias();
  renderCardapio();
}

// ── Render: cardápio ──────────────────────────────────────────────────────

function renderCardapio() {
  const busca = (document.getElementById('pdv-busca').value || '').toLowerCase().trim();
  const grid = document.getElementById('pdv-produtos-grid');
  const categorias = pdvCategoriaAtiva === 'todos'
    ? ['pizza', 'bebida']
    : [pdvCategoriaAtiva];

  const blocos = categorias.map(catId => {
    return (pdvCatalogo.byCategory[catId] || []).map(grupo => {
      const itens = grupo.items.filter(item => !busca || item.searchIndex.includes(busca));
      if (itens.length === 0) return '';

      return `
        <div class="pdv-group">
          <div class="pdv-group__header">
            <div>
              <div class="pdv-group__title">${escapeHtml(grupo.grupoTitulo)}</div>
              <div class="pdv-group__meta">${itens.length} variante(s) disponível(is)</div>
            </div>
            <div class="pdv-group__price">${formatarMoedaPDV(grupo.precoBase)}</div>
          </div>
          <div class="pdv-group__grid">
            ${itens.map(renderCardProduto).join('')}
          </div>
        </div>`;
    }).filter(Boolean).join('');
  }).filter(Boolean);

  if (blocos.length === 0) {
    grid.innerHTML = emptyStateMarkup({
      icon: 'search',
      title: busca ? 'Nenhum produto encontrado' : 'Cardápio vazio',
      copy: busca
        ? `Nenhum produto corresponde à busca "${busca}".`
        : 'Cadastre bebidas no estoque e mantenha o catálogo de pizzas disponível para o PDV.'
    });
    return;
  }

  grid.innerHTML = blocos.join('');
}

function renderCardProduto(item) {
  const produto = item.rawProduct;
  const semEstoque  = !itemDisponivel(item);
  const qtdCarrinho = (pdvCarrinho.find(i => i.produto.id === produto.id) || {}).quantidade || 0;
  const catClass    = getCategoriaClasse(item.categoriaCardapio);
  const preco       = formatarMoedaPDV(item.precoUnitario || parseFloat(produto.precoUnitario) || 0);
  const emoji       = item.icone || emojiCardapio(produto.nome);
  const linhaMeta   = item.categoriaCardapio === 'pizza'
    ? item.varianteTitulo
    : item.varianteTitulo + ' · ' + item.grupoTitulo;

  return `<button
    type="button"
    id="pdv-card-${produto.id}"
    class="pdv-card pdv-card--${catClass}${qtdCarrinho > 0 ? ' pdv-card--selected' : ''}${semEstoque ? ' pdv-card--disabled' : ''}"
    onclick="${semEstoque ? '' : `adicionarAoCarrinho(${produto.id})`}"
    ${semEstoque ? 'disabled' : ''}
  >
    <div class="pdv-card__accent"></div>
    ${qtdCarrinho > 0 ? `<span class="pdv-card__badge">${qtdCarrinho}</span>` : ''}
    <div class="pdv-card__body">
      <div class="pdv-card__icon" aria-hidden="true">${emoji}</div>
      <div class="pdv-card__title">${escapeHtml(produto.nome)}</div>
      <div class="pdv-card__meta">${escapeHtml(linhaMeta)}</div>
      <div class="pdv-card__price">${preco}</div>
      ${semEstoque
        ? '<div class="pdv-card__stock pdv-card__stock--unavailable">Indisponível</div>'
        : `<div class="pdv-card__stock">${escapeHtml(itemStatusLabel(item))}</div>`}
    </div>
  </button>`;
}

// ── Busca ─────────────────────────────────────────────────────────────────

function filtrarProdutosPDV() {
  renderCardapio();
}

// ── Carrinho ──────────────────────────────────────────────────────────────

function adicionarAoCarrinho(produtoId) {
  const menuItem = pdvProdutosMap[produtoId];
  const produto = menuItem && menuItem.rawProduct;
  if (!produto || !itemDisponivel(menuItem)) return;

  const existente = pdvCarrinho.find(i => i.produto.id === produtoId);
  if (existente) {
    existente.quantidade++;
  } else {
    pdvCarrinho.push({ produto, menuItem, quantidade: 1 });
  }

  // Micro-animação no card
  const card = document.getElementById(`pdv-card-${produtoId}`);
  if (card) {
    card.classList.remove('pdv-card--pulse');
    window.requestAnimationFrame(function () {
      card.classList.add('pdv-card--pulse');
      setTimeout(function () { card.classList.remove('pdv-card--pulse'); }, 160);
    });
  }

  renderCarrinho();
  // Re-renderiza só o card afetado para atualizar badge sem re-render completo
  const atualizado = document.getElementById(`pdv-card-${produtoId}`);
  if (atualizado && menuItem) atualizado.outerHTML = renderCardProduto(menuItem);
}

function removerDoCarrinho(produtoId) {
  const idx = pdvCarrinho.findIndex(i => i.produto.id === produtoId);
  if (idx === -1) return;
  if (pdvCarrinho[idx].quantidade > 1) {
    pdvCarrinho[idx].quantidade--;
  } else {
    pdvCarrinho.splice(idx, 1);
  }
  renderCarrinho();
  const item = pdvProdutosMap[produtoId];
  if (item) {
    const card = document.getElementById(`pdv-card-${produtoId}`);
    if (card) card.outerHTML = renderCardProduto(item);
  }
}

function removerItemCompleto(produtoId) {
  pdvCarrinho = pdvCarrinho.filter(i => i.produto.id !== produtoId);
  renderCarrinho();
  const item = pdvProdutosMap[produtoId];
  if (item) {
    const card = document.getElementById(`pdv-card-${produtoId}`);
    if (card) card.outerHTML = renderCardProduto(item);
  }
}

function limparCarrinho() {
  const ids = pdvCarrinho.map(i => i.produto.id);
  pdvCarrinho = [];
  renderCarrinho();
  // Atualiza os cards que tinham badge de quantidade
  ids.forEach(id => {
    const item = pdvProdutosMap[id];
    if (item) {
      const card = document.getElementById(`pdv-card-${id}`);
      if (card) card.outerHTML = renderCardProduto(item);
    }
  });
}

function calcularTotalCarrinho() {
  return pdvCarrinho.reduce((acc, i) => {
    const preco = (i.menuItem && i.menuItem.precoUnitario) || parseFloat(i.produto.precoUnitario) || 0;
    return acc + preco * i.quantidade;
  }, 0);
}

// ── Render: carrinho ──────────────────────────────────────────────────────

function renderCarrinho() {
  const container = document.getElementById('pdv-carrinho-itens');
  const empty     = document.getElementById('pdv-carrinho-empty');
  const btnFin    = document.getElementById('btn-pdv-finalizar');
  const elTotal   = document.getElementById('pdv-total');
  const elSub     = document.getElementById('pdv-subtotal');
  const elQtd     = document.getElementById('pdv-carrinho-qtd');

  const total     = calcularTotalCarrinho();
  const qtdItens  = pdvCarrinho.reduce((s, i) => s + i.quantidade, 0);

  // Atualiza badge de quantidade total no header do carrinho
  if (elQtd) elQtd.textContent = qtdItens > 0 ? qtdItens + ' ite' + (qtdItens === 1 ? 'm' : 'ns') : '';

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
  btnFin.disabled      = false;
  btnFin.classList.remove('pdv-submit--success');

  container.querySelectorAll('.pdv-item-row').forEach(el => el.remove());

  pdvCarrinho.forEach(item => {
    const menuItem = item.menuItem || pdvProdutosMap[item.produto.id];
    const preco    = (menuItem && menuItem.precoUnitario) || parseFloat(item.produto.precoUnitario) || 0;
    const subtotal = preco * item.quantidade;
    const emoji    = (menuItem && menuItem.icone) || emojiCardapio(item.produto.nome);
    const catClass = getCategoriaClasse(menuItem ? menuItem.categoriaCardapio : 'pizza');

    const row = document.createElement('div');
    row.className = 'pdv-item-row';
    row.innerHTML = `
      <div class="pdv-item-icon pdv-item-icon--${catClass}" aria-hidden="true">${emoji}</div>
      <div class="pdv-item-content">
        <div class="pdv-item-name">${escapeHtml(item.produto.nome)}</div>
        <div class="pdv-item-meta">
          ${menuItem && menuItem.varianteTitulo ? `${escapeHtml(menuItem.varianteTitulo)} · ` : ''}${formatarMoedaPDV(preco)} × ${item.quantidade}
        </div>
      </div>
      <div class="pdv-item-subtotal pdv-item-subtotal--${catClass}">
        ${formatarMoedaPDV(subtotal)}
      </div>
      <div class="pdv-item-controls">
        <button class="pdv-item-btn" onclick="removerDoCarrinho(${item.produto.id})" title="Remover 1" type="button">−</button>
        <span class="pdv-item-qty">${item.quantidade}</span>
        <button class="pdv-item-btn pdv-item-btn--${catClass}" onclick="adicionarAoCarrinho(${item.produto.id})" title="Adicionar 1" type="button">+</button>
        <button class="pdv-item-btn pdv-item-btn--remove" onclick="removerItemCompleto(${item.produto.id})" title="Remover item" type="button">×</button>
      </div>
    `;
    container.appendChild(row);
  });

  elTotal.textContent = formatarMoedaPDV(total);
  if (elSub) elSub.textContent = formatarMoedaPDV(total);
}

// ── Finalizar venda ───────────────────────────────────────────────────────

async function finalizarVendaPDV() {
  if (pdvCarrinho.length === 0) return;

  const btn       = document.getElementById('btn-pdv-finalizar');
  const total     = calcularTotalCarrinho();
  // Descrição legível no histórico: "2x Pizza Calabresa, 1x Coca-Cola Lata"
  const descricao = pdvCarrinho.map(i => `${i.quantidade}x ${i.produto.nome}`).join(', ');

  setLoading(btn, true);
  limparAlert('alert-pdv');

  try {
    const itens = pdvCarrinho.map(function (item) {
      if (item.menuItem && item.menuItem.cardapioItemId) {
        return { cardapioItemId: item.menuItem.cardapioItemId, quantidade: item.quantidade };
      }
      return { produtoId: item.produto.id, quantidade: item.quantidade };
    });

    const resp = await fetch('/api/caixas/venda', {
      method:  'POST',
      headers: { 'Content-Type': 'application/json' },
      body:    JSON.stringify({ numeroCaixa: pdvNumeroCaixa, valor: total, descricao, itens })
    });

    if (!resp.ok) {
      const msg = await safeReadErrorMessage(resp, 'Erro ao registrar venda.');
      mostrarAlert('alert-pdv', msg, 'error');
      return;
    }

    // Animação de sucesso antes de fechar
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

// ── Utilidades ────────────────────────────────────────────────────────────

function formatarMoedaPDV(valor) {
  return new Intl.NumberFormat('pt-BR', {
    style: 'currency', currency: 'BRL', minimumFractionDigits: 2
  }).format(valor || 0);
}

/**
 * Fallback visual para itens já mapeados pelo adaptador.
 * Mantido propositalmente simples para não reintroduzir lógica de negócio.
 */
function emojiCardapio(nome) {
  const n = nome.toLowerCase();

  // ── Pizzas ──
  if (n.includes('calabresa'))                      return '🍕';
  if (n.includes('margherita') || n.includes('marguerita')) return '🍕';
  if (n.includes('mussarela') || n.includes('mozzarella')) return '🍕';
  if (n.includes('portuguesa'))                     return '🍕';
  if (n.includes('frango') && n.includes('pizza')) return '🍕';
  if (n.includes('quatro queijos') || n.includes('4 queij')) return '🍕';
  if (n.includes('pepperoni'))                      return '🍕';
  if (n.includes('napolitana'))                     return '🍕';
  if (n.includes('vegetariana') && n.includes('pizza')) return '🍕';
  if (n.includes('pizza'))                          return '🍕';

  // ── Bebidas ──
  if (n.includes('coca'))                           return '🥤';
  if (n.includes('pepsi'))                          return '🥤';
  if (n.includes('guaraná') || n.includes('guarana')) return '🥤';
  if (n.includes('fanta'))                          return '🥤';
  if (n.includes('sprite'))                         return '🥤';
  if (n.includes('soda') || n.includes('refrigerante') || n.includes('refri')) return '🥤';
  if (n.includes('água') || n.includes('agua'))    return '💧';
  if (n.includes('suco'))                           return '🍹';
  if (n.includes('limonada'))                       return '🍋';
  if (n.includes('cerveja'))                        return '🍺';
  if (n.includes('chopp'))                          return '🍺';
  if (n.includes('vinho'))                          return '🍷';
  if (n.includes('café') || n.includes('cafe'))    return '☕';
  if (n.includes('chá') || n.includes('cha'))      return '🍵';
  if (n.includes('leite'))                          return '🥛';
  if (n.includes('milkshake') || n.includes('shake')) return '🥤';
  if (n.includes('isotônico') || n.includes('isotonico')) return '🏃';

  // ── Fallback ──
  return '🍽️';
}
