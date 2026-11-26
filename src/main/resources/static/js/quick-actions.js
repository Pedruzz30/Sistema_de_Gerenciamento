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

  const groups=[
    ['Visão geral',['dashboard']],
    ['Estoque e produtos',['estoque','buscar-produto','novo-produto','cardapio']],
    ['Vendas e caixa',['caixas','abrir-caixa','registrar-venda']],
    ['Compras e custos',['fornecedores','notas','cotacoes']],
    ['Administração',['funcionarios','logs']]
  ];
  function grouped(query){
    const found=search(query),q=normalize(query);
    if(q){
      const rank=a=>{const label=normalize(a.label);return label===q?0:label.startsWith(q)?1:label.includes(q)?2:3;};
      return found.length?[{label:'Resultados da busca',items:found.slice().sort((a,b)=>rank(a)-rank(b))}]:[];
    }
    return groups.map(([label,ids])=>({label,items:ids.map(id=>found.find(a=>a.id===id)).filter(Boolean)})).filter(g=>g.items.length);
  }
  function nextIndex(index,length,direction){return length>0?(index+direction+length)%length:0;}
  // Exposto para testes e para futuras extensões; não concede autorização.
  window.StockOSQuickActions = { search, permitted, grouped, nextIndex };

  function init() {
    const nav = document.querySelector('.sidebar #nav-items');
    if (!nav || document.getElementById('stockos-quick-trigger')) return;
    const trigger = document.createElement('button');
    trigger.type = 'button';
    trigger.id = 'stockos-quick-trigger';
    trigger.className = 'stockos-quick-trigger';
    trigger.setAttribute('aria-label', 'Abrir ações rápidas');
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
    heading.textContent = 'Ações rápidas';
    const dismiss = document.createElement('button');
    dismiss.type = 'button';
    dismiss.className = 'stockos-quick-close';
    dismiss.setAttribute('aria-label', 'Fechar ações rápidas');
    dismiss.textContent = '×';
    const headerCopy=document.createElement('div');
    const subtitle=document.createElement('p');subtitle.textContent='Encontre páginas e atalhos da operação.';
    headerCopy.append(heading,subtitle);top.append(headerCopy,dismiss);

    const input = document.createElement('input');
    input.type = 'search';
    input.id = 'stockos-quick-search';
    input.className = 'stockos-quick-search';
    input.placeholder = 'Buscar ações ou páginas...';
    input.setAttribute('aria-label', 'Buscar ações do StockOS');
    input.autocomplete='off';input.spellcheck=false;
    input.setAttribute('role','combobox');input.setAttribute('aria-autocomplete','list');
    input.setAttribute('aria-controls','stockos-quick-list');input.setAttribute('aria-expanded','false');
    const searchWrap=document.createElement('div');searchWrap.className='stockos-quick-search-wrap';
    const searchIcon=document.createElement('span');searchIcon.setAttribute('aria-hidden','true');searchIcon.innerHTML=window.iconMarkup('search');
    searchWrap.append(searchIcon,input);
    const results = document.createElement('div');
    results.className = 'stockos-quick-results';
    results.id='stockos-quick-list';results.setAttribute('role','listbox');
    results.setAttribute('aria-label','Ações disponíveis');
    const count=document.createElement('div');count.className='stockos-quick-count';count.setAttribute('role','status');
    const footer=document.createElement('div');footer.className='stockos-quick-footer';
    footer.innerHTML='<span><kbd>↑</kbd><kbd>↓</kbd> Navegar</span><span><kbd>Enter</kbd> Abrir</span><span><kbd>Esc</kbd> Fechar</span>';
    dialog.append(top,searchWrap,count,results,footer);
    overlay.appendChild(dialog);
    document.body.appendChild(overlay);

    let shown = [];
    let selected = 0;
    let previousFocus = null;
    let executing=false,buttons=[];
    function selectIndex(index,scroll=false){
      selected=index;
      buttons.forEach((btn,i)=>{btn.classList.toggle('is-selected',i===selected);btn.setAttribute('aria-selected',String(i===selected));});
      const active=buttons[selected];
      if(active){input.setAttribute('aria-activedescendant',active.id);if(scroll)active.scrollIntoView({block:'nearest'});}
      else input.removeAttribute('aria-activedescendant');
    }
    function paint(){
      const sections=grouped(input.value);
      shown=sections.flatMap(g=>g.items);selected=Math.min(selected,Math.max(0,shown.length-1));
      results.replaceChildren();buttons=[];
      count.textContent=shown.length+' '+(shown.length===1?'atalho disponível':'atalhos disponíveis');
      if(!shown.length){
        input.removeAttribute('aria-activedescendant');
        const empty=document.createElement('div');empty.className='stockos-quick-empty';empty.setAttribute('role','presentation');
        const title=document.createElement('strong');title.textContent='Nenhum atalho encontrado';
        const copy=document.createElement('p');copy.textContent='Tente pesquisar por outro nome ou ação.';
        empty.append(title,copy);results.appendChild(empty);return;
      }
      sections.forEach((section,groupIndex)=>{
        const group=document.createElement('div');group.className='stockos-quick-group';group.setAttribute('role','group');
        const label=document.createElement('div');label.className='stockos-quick-group-label';label.id='stockos-quick-group-'+groupIndex;
        label.textContent=section.label;group.setAttribute('aria-labelledby',label.id);group.appendChild(label);
        section.items.forEach(action=>{
          const index=buttons.length,btn=document.createElement('button');
          btn.type='button';btn.id='stockos-quick-option-'+action.id;btn.className='stockos-quick-result';btn.tabIndex=-1;btn.setAttribute('role','option');
          const icon=document.createElement('span');icon.className='stockos-quick-result-icon';icon.setAttribute('aria-hidden','true');icon.innerHTML=window.iconMarkup(action.icon);
          const copy=document.createElement('span');copy.className='stockos-quick-result-copy';
          const title=document.createElement('strong');title.textContent=action.label;
          const detail=document.createElement('small');detail.textContent=action.detail;copy.append(title,detail);
          const type=document.createElement('span');type.className='stockos-quick-type';type.textContent=action.effect?'Atalho':'Página';type.setAttribute('aria-hidden','true');
          const arrow=document.createElement('span');arrow.className='stockos-quick-result-arrow';arrow.setAttribute('aria-hidden','true');arrow.textContent='↵';
          btn.append(icon,copy,type,arrow);
          btn.addEventListener('pointerenter',e=>{if(e.pointerType==='mouse')selectIndex(index);});
          btn.addEventListener('mousedown',e=>e.preventDefault());
          btn.addEventListener('click',()=>{void execute(action);});
          group.appendChild(btn);buttons.push(btn);
        });
        results.appendChild(group);
      });
      selectIndex(selected);results.scrollTop=0;
    }
    function open(){
      if(!overlay.hidden||executing||document.querySelector('.modal-overlay.open')||(window.StockosAuth&&window.StockosAuth.isSupabase()))return;
      previousFocus=document.activeElement;input.value='';selected=0;paint();
      overlay.hidden=false;input.setAttribute('aria-expanded','true');document.body.classList.add('stockos-quick-active');input.focus();
    }
    function close(){
      if(overlay.hidden)return;
      overlay.hidden=true;input.setAttribute('aria-expanded','false');input.removeAttribute('aria-activedescendant');
      document.body.classList.remove('stockos-quick-active');
      if(previousFocus?.isConnected&&typeof previousFocus.focus==='function')previousFocus.focus();
      previousFocus=null;
    }
    function current() {
      return window.StockOSSPA && typeof window.StockOSSPA.current === 'function'
        ? window.StockOSSPA.current()
        : (typeof window.paginaAtual === 'function' ? window.paginaAtual() : '');
    }
    async function execute(action) {
      if (executing || !permitted(action) || (window.StockosAuth && window.StockosAuth.isSupabase())) return;
      executing=true;
      try {
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
      if (current() !== action.route || !action.effect || !permitted(action) || (window.StockosAuth && window.StockosAuth.isSupabase())) return;
      if (action.effect === 'focus-search') {
        document.getElementById('busca')?.focus();
      } else if (action.effect === 'new-product' && typeof window.abrirModalCadastro === 'function') {
        window.abrirModalCadastro();
        document.getElementById('c-nome')?.focus();
      } else if (action.effect === 'open-cash' && typeof window.abrirModalAbrirCaixa === 'function') {
        window.abrirModalAbrirCaixa();
        document.getElementById('abrir-numero')?.focus();
      }
      } catch {
        if(typeof window.showToast==='function')window.showToast('Não foi possível abrir o atalho.','error');
      } finally {executing=false;}
    }

    trigger.addEventListener('click', open);
    mobile.addEventListener('click', open);
    dismiss.addEventListener('click', close);
    overlay.addEventListener('click', e => { if (e.target === overlay) close(); });
    input.addEventListener('input', () => { selected = 0; paint(); });
    // Capture Escape before the shell listener: don't close an unrelated modal.
    document.addEventListener('keydown', e => {
      if(e.isComposing)return;
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
        if (!e.altKey && !e.shiftKey) {
          e.preventDefault();
          if(e.repeat)return;
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
        const focusables = [dismiss, input];
        if (e.shiftKey && document.activeElement === focusables[0]) {
          e.preventDefault(); focusables[focusables.length - 1].focus();
        } else if (!e.shiftKey && document.activeElement === focusables[focusables.length - 1]) {
          e.preventDefault(); focusables[0].focus();
        }
      } else if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
        if (!shown.length) return;
        e.preventDefault();
        selectIndex(nextIndex(selected,shown.length,e.key==='ArrowDown'?1:-1),true);
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