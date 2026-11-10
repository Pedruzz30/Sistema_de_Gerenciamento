/* StockOS - atalhos operacionais. UI only: nunca executa uma escrita na API. */
(function () {
  'use strict';
  if (window.StockOSQuickActions) return;
  // Staging Supabase usa console tenant-aware somente leitura. Não expor atalhos legados.
  if (window.StockosAuth && window.StockosAuth.isSupabase()) return;

  const actions = [
    { id: 'dashboard', label: 'Visão geral', detail: 'Resumo do turno e alertas', route: 'dashboard', icon: 'grid', terms: 'inicio indicadores' },
    { id: 'estoque', label: 'Consultar estoque', detail: 'Produtos e níveis de estoque', route: 'estoque', icon: 'box', perms: ['VER_ESTOQUE'], terms: 'quantidades itens inventario' },
    { id: 'buscar-produto', label: 'Buscar produto', detail: 'Ir direto para a pesquisa do estoque', route: 'estoque', icon: 'search', perms: ['VER_ESTOQUE'], effect: 'focus-search', terms: 'pesquisar encontrar' },
    { id: 'novo-produto', label: 'Cadastrar produto', detail: 'Abrir formulário de novo produto', route: 'estoque', icon: 'plus', perms: ['VER_ESTOQUE', 'EDITAR_ESTOQUE'], effect: 'new-product', terms: 'adicionar item cadastro' },
    { id: 'cardapio', label: 'Cardápio', detail: 'Itens disponíveis para venda', route: 'cardapio', icon: 'cart', perms: ['EDITAR_ESTOQUE'], terms: 'menu produtos' },
    { id: 'fornecedores', label: 'Fornecedores', detail: 'Consultar fornecedores', route: 'fornecedores', icon: 'truck', perms: ['VER_COMPRAS'], terms: 'compras fornecedor' },
    { id: 'notas', label: 'Notas fiscais', detail: 'Consultar entradas e notas', route: 'notas', icon: 'receipt', perms: ['VER_COMPRAS'], terms: 'recebimento nota compra' },
    { id: 'caixas', label: 'Caixas PDV', detail: 'Consultar caixas e vendas', route: 'caixas', icon: 'wallet', any: ['VER_VENDAS', 'VER_FINANCAS'], terms: 'venda pagamento terminal' },
    { id: 'abrir-caixa', label: 'Abrir caixa', detail: 'Abrir formulário de início do caixa', route: 'caixas', icon: 'plus', perms: ['VER_VENDAS'], effect: 'open-cash', terms: 'novo caixa iniciar turno' },
    { id: 'registrar-venda', label: 'Registrar venda', detail: 'Escolha um caixa aberto para vender', route: 'caixas', icon: 'cart', perms: ['VER_VENDAS'], terms: 'pdv vender pedido' },
    { id: 'cotacoes', label: 'Cotações', detail: 'Comparar preços e custos', route: 'cotacoes', icon: 'chart', perms: ['VER_FINANCAS'], terms: 'compras custo cotacao' },
    { id: 'funcionarios', label: 'Funcionários', detail: 'Gerenciar equipe e permissões', route: 'funcionarios', icon: 'users', perms: ['GERENCIAR_FUNCIONARIOS'], terms: 'colaboradores operadores' },
    { id: 'logs', label: 'Histórico de ações', detail: 'Consultar auditoria', route: 'logs', icon: 'clipboard', perms: ['VER_LOGS'], terms: 'logs eventos' }
  ];

  const normalize = value => String(value || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().trim();
  function permitted(action) {
    if (typeof window.temPermissao !== 'function') return !action.perms && !action.any;
    return (!action.perms || action.perms.every(p => window.temPermissao(p))) &&
      (!action.any || action.any.some(p => window.temPermissao(p)));
  }
  function search(query) {
    const tokens = normalize(query).split(/\s+/).filter(Boolean);
    return actions.filter(permitted).filter(action => {
      const haystack = normalize([action.label, action.detail, action.terms].join(' '));
      return tokens.every(token => haystack.includes(token));
    });
  }

  // Exposto para testes e para futuras extensões; não concede autorização.
  window.StockOSQuickActions = { search, permitted };

  function init() {
    const nav = document.querySelector('.sidebar #nav-items');
    if (!nav || document.getElementById('stockos-quick-trigger')) return;
    const trigger = document.createElement('button');
    trigger.type = 'button';
    trigger.id = 'stockos-quick-trigger';
    trigger.className = 'stockos-quick-trigger';
    trigger.setAttribute('aria-haspopup', 'dialog');
    trigger.setAttribute('aria-keyshortcuts', 'Control+K Meta+K');
    trigger.innerHTML = '<span aria-hidden="true">' + window.iconMarkup('search') + '</span><span>Ações rápidas</span><kbd>Ctrl K</kbd>';
    nav.parentNode.insertBefore(trigger, nav);

    const mobile = document.createElement('button');
    mobile.type = 'button';
    mobile.className = 'stockos-quick-mobile';
    mobile.setAttribute('aria-label', 'Abrir ações rápidas');
    mobile.textContent = 'Ações';
    document.body.appendChild(mobile);

    const overlay = document.createElement('div');
    overlay.id = 'stockos-quick-overlay';
    overlay.className = 'stockos-quick-overlay';
    overlay.hidden = true;
    const dialog = document.createElement('section');
    dialog.className = 'stockos-quick-dialog';
    dialog.setAttribute('role', 'dialog');
    dialog.setAttribute('aria-modal', 'true');
    dialog.setAttribute('aria-labelledby', 'stockos-quick-title');
    const top = document.createElement('div');
    top.className = 'stockos-quick-top';
    const heading = document.createElement('h2');
    heading.id = 'stockos-quick-title';
    heading.textContent = 'O que você precisa fazer?';
    const dismiss = document.createElement('button');
    dismiss.type = 'button';
    dismiss.className = 'stockos-quick-close';
    dismiss.setAttribute('aria-label', 'Fechar ações rápidas');
    dismiss.textContent = '×';
    top.append(heading, dismiss);

    const input = document.createElement('input');
    input.type = 'search';
    input.id = 'stockos-quick-search';
    input.className = 'stockos-quick-search';
    input.placeholder = 'Buscar ações ou páginas...';
    input.setAttribute('aria-label', 'Buscar ações do StockOS');
    input.autocomplete = 'off';
    const results = document.createElement('div');
    results.className = 'stockos-quick-results';
    results.setAttribute('aria-label', 'Ações disponíveis');
    const footer = document.createElement('p');
    footer.className = 'stockos-quick-footer';
    footer.textContent = '↑ ↓ para escolher · Enter para abrir · Esc para fechar';
    dialog.append(top, input, results, footer);
    overlay.appendChild(dialog);
    document.body.appendChild(overlay);

    let shown = [];
    let selected = 0;
    let previousFocus = null;
    function paint() {
      shown = search(input.value).slice(0, 10);
      selected = Math.min(selected, Math.max(0, shown.length - 1));
      results.replaceChildren();
      if (!shown.length) {
        const empty = document.createElement('p');
        empty.className = 'stockos-quick-empty';
        empty.textContent = 'Nenhuma ação disponível para essa busca.';
        results.appendChild(empty);
        return;
      }
      shown.forEach((action, index) => {
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'stockos-quick-result' + (index === selected ? ' is-selected' : '');
        const icon = document.createElement('span');
        icon.className = 'stockos-quick-result-icon';
        icon.setAttribute('aria-hidden', 'true');
        icon.innerHTML = window.iconMarkup(action.icon);
        const copy = document.createElement('span');
        const label = document.createElement('strong');
        label.textContent = action.label;
        const detail = document.createElement('small');
        detail.textContent = action.detail;
        copy.append(label, detail);
        btn.append(icon, copy);
        btn.addEventListener('click', () => { void execute(action); });
        results.appendChild(btn);
      });
    }
    function open() {
      if (document.querySelector('.modal-overlay.open') ||
          (window.StockosAuth && window.StockosAuth.isSupabase())) return;
      previousFocus = document.activeElement;
      input.value = '';
      selected = 0;
      paint();
      overlay.hidden = false;
      document.body.classList.add('stockos-quick-active');
      input.focus();
    }
    function close() {
      if (overlay.hidden) return;
      overlay.hidden = true;
      document.body.classList.remove('stockos-quick-active');
      if (previousFocus && previousFocus.isConnected && typeof previousFocus.focus === 'function') previousFocus.focus();
    }
    function current() {
      return window.StockOSSPA && typeof window.StockOSSPA.current === 'function'
        ? window.StockOSSPA.current()
        : (typeof window.paginaAtual === 'function' ? window.paginaAtual() : '');
    }
    async function execute(action) {
      if (!permitted(action)) return;
      close();
      const href = '/' + action.route + '.html';
      if (current() !== action.route) {
        if (window.StockOSSPA && typeof window.StockOSSPA.navigate === 'function') {
          await window.StockOSSPA.navigate(href);
        } else {
          window.location.assign(href); // Retorno seguro: navegação normal.
          return;
        }
      }
      if (current() !== action.route || !action.effect) return;
      if (action.effect === 'focus-search') {
        document.getElementById('busca')?.focus();
      } else if (action.effect === 'new-product' && typeof window.abrirModalCadastro === 'function') {
        window.abrirModalCadastro();
        document.getElementById('c-nome')?.focus();
      } else if (action.effect === 'open-cash' && typeof window.abrirModalAbrirCaixa === 'function') {
        window.abrirModalAbrirCaixa();
        document.getElementById('abrir-numero')?.focus();
      }
    }

    trigger.addEventListener('click', open);
    mobile.addEventListener('click', open);
    dismiss.addEventListener('click', close);
    overlay.addEventListener('click', e => { if (e.target === overlay) close(); });
    input.addEventListener('input', () => { selected = 0; paint(); });
    // Capture Escape before the shell listener: don't close an unrelated modal.
    document.addEventListener('keydown', e => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
        if (!e.altKey && !e.shiftKey) {
          e.preventDefault();
          if (overlay.hidden) open(); else close();
        }
        return;
      }
      if (overlay.hidden) return;
      if (e.key === 'Escape') {
        e.preventDefault();
        e.stopPropagation();
        close();
      } else if (e.key === 'Tab') {
        const focusables = [dismiss, input, ...results.querySelectorAll('button')];
        if (e.shiftKey && document.activeElement === focusables[0]) {
          e.preventDefault(); focusables[focusables.length - 1].focus();
        } else if (!e.shiftKey && document.activeElement === focusables[focusables.length - 1]) {
          e.preventDefault(); focusables[0].focus();
        }
      } else if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
        if (!shown.length) return;
        e.preventDefault();
        selected = (selected + (e.key === 'ArrowDown' ? 1 : -1) + shown.length) % shown.length;
        paint();
        input.focus();
      } else if (e.key === 'Enter' && document.activeElement === input && shown[selected]) {
        e.preventDefault();
        void execute(shown[selected]);
      }
    }, true);
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init, { once: true });
  else init();
})();