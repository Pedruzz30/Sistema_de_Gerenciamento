/* Browser-only PDV walkthrough. Does not execute real sales or payments. */
(() => {
  'use strict';
  const create=(tag,cls,text)=>{const e=document.createElement(tag);if(cls)e.className=cls;if(text!==undefined)e.textContent=text;return e;};
  const PREVIEW_PDV_CSS = `
    .stockos-preview-pdv__dialog {display:flex;flex-direction:column;min-height:0;}
    .stockos-preview-pdv__navigation {display:none;}
    .stockos-preview-pdv__catalog {display:flex;flex-direction:column;min-width:0;}
    .stockos-preview-pdv__cart {display:flex;flex-direction:column;min-width:0;min-height:0;}
    .stockos-preview-pdv__select {min-height:46px;border-radius:8px;}
    .stockos-preview-pdv__controls {display:flex;gap:8px;align-items:center;}
    .stockos-preview-pdv__qty {min-height:46px;max-width:100px;}
    .stockos-preview-pdv__add {min-height:46px;padding:10px 14px;border:0;border-radius:8px;background:#c98b46;color:#20170a;font-weight:700;}
    .stockos-preview-pdv__item {display:flex;justify-content:space-between;align-items:center;gap:8px;border-bottom:1px solid #526b84;}
    .stockos-preview-pdv__item-text {flex:1;min-width:0;}
    .stockos-preview-pdv__item-actions {display:flex;align-items:center;gap:6px;}
    .stockos-preview-pdv__item-actions button {min-height:44px;min-width:44px;border:1px solid #607992;border-radius:8px;background:#263e57;color:#fff;}
    .stockos-preview-pdv__item-actions button:disabled {opacity:.5;}
    .stockos-preview-pdv__finish {min-height:48px;font-weight:700;}
    .stockos-preview-pdv__feedback {font-size:12px;line-height:1.5;}
    @media(max-width:640px) {
      .stockos-preview-pdv {padding:0 !important;}
      .stockos-preview-pdv__dialog {width:100vw !important;height:100dvh !important;max-height:100dvh !important;border-radius:0 !important;padding:14px !important;padding-top:max(14px,env(safe-area-inset-top,14px)) !important;overflow:hidden !important;}
      .stockos-preview-pdv__head {flex:0 0 auto;}
      .stockos-preview-pdv__title {font-size:16px;}
      .stockos-preview-pdv__close {min-height:44px;padding:8px 12px;}
      .stockos-preview-pdv__info {font-size:12px;flex:0 0 auto;}
      .stockos-preview-pdv__navigation {display:grid;grid-template-columns:1fr 1fr;gap:8px;flex:0 0 auto;margin:8px 0;}
      .stockos-preview-pdv__tab {min-height:48px;border-radius:9px;background:#2b4157;border:1px solid #617b91;color:white;font-weight:700;}
      .stockos-preview-pdv__tab[aria-pressed="true"] {background:#37684d;border-color:#97d3ab;}
      .stockos-preview-pdv__catalog,.stockos-preview-pdv__cart {min-height:0;flex:1 1 auto;overflow-y:auto;}
      .stockos-preview-pdv__dialog[data-mobile-panel="produtos"] .stockos-preview-pdv__cart {display:none;}
      .stockos-preview-pdv__dialog[data-mobile-panel="pedido"] .stockos-preview-pdv__catalog {display:none;}
      .stockos-preview-pdv__rows {flex:1 1 auto;overflow-y:auto;}
      .stockos-preview-pdv__total {margin-top:auto;padding:12px 0;border-top:1px solid #526b84;}
      .stockos-preview-pdv__finish {flex:0 0 auto;margin-bottom:max(10px,env(safe-area-inset-bottom,10px));}
    }
  `;

  const TERMINAL_CSS = `
    .stockos-terminal {position:fixed;inset:0;z-index:2147483600;background:#16131099;display:grid;place-items:center;padding:18px;}
    .stockos-terminal * {box-sizing:border-box;}
    .stockos-terminal__dialog {width:min(1240px,100%);height:min(790px,calc(100dvh - 36px));background:var(--paper,#f6f3ef);color:var(--ink,#25211e);border:1px solid var(--rule,#ddd);border-radius:14px;display:flex;flex-direction:column;overflow:hidden;font:14px system-ui;box-shadow:0 18px 65px #0003;}
    .stockos-terminal__header {display:flex;justify-content:space-between;align-items:center;gap:12px;padding:16px 22px;background:var(--paper-raised,#fff);border-bottom:1px solid var(--rule,#ddd);}
    .stockos-terminal__header h2 {font-size:20px;letter-spacing:-.035em;margin:0;font-weight:700;}
    .stockos-terminal__muted {font-size:12px;color:var(--ink-3,#777);margin:4px 0 0;}
    .stockos-terminal button,.stockos-terminal input {font:inherit;}
    .stockos-terminal button {cursor:pointer;min-height:40px;border:1px solid var(--rule,#ddd);border-radius:8px;background:var(--paper-raised,#fff);color:var(--ink,#222);padding:8px 12px;}
    .stockos-terminal button:disabled {opacity:.45;cursor:default;}
    .stockos-terminal button:focus-visible,.stockos-terminal input:focus-visible {outline:2px solid var(--accent,#c45120);outline-offset:2px;}
    .stockos-terminal__body {display:grid;grid-template-columns:minmax(0,1.65fr) minmax(300px,1fr);flex:1;min-height:0;}
    .stockos-terminal__catalog {padding:20px;display:flex;flex-direction:column;min-width:0;min-height:0;gap:12px;}
    .stockos-terminal__search {width:100%;min-width:0;min-height:44px;border:1px solid var(--rule,#ddd);border-radius:8px;background:var(--paper-raised,#fff);color:var(--ink,#222);padding:10px 14px;}
    .stockos-terminal__section-title {display:flex;align-items:center;justify-content:space-between;gap:8px;flex-wrap:wrap;}
    .stockos-terminal__section-title h3 {font-size:15px;margin:0;font-weight:650;}
    .stockos-terminal__categories {display:flex;gap:6px;overflow-x:auto;flex-shrink:0;padding:2px 1px 5px;}
    .stockos-terminal__categories button {white-space:nowrap;font-size:12px;flex-shrink:0;min-height:36px;}
    .stockos-terminal button[aria-pressed=true] {background:var(--accent-soft,#f9e5d8);border-color:var(--accent,#c45120);color:var(--accent-ink,#9f3608);font-weight:650;}
    .stockos-terminal__grid {display:grid;grid-template-columns:repeat(auto-fill,minmax(min(155px,100%),1fr));align-content:start;gap:10px;overflow:auto;min-height:0;flex:1;padding:2px 3px 5px;}
    .stockos-terminal button.stockos-terminal__product {display:flex;flex-direction:column;align-items:flex-start;justify-content:space-between;text-align:left;gap:8px;padding:12px;min-height:132px;min-width:0;}
    .stockos-terminal__product:not(:disabled):hover {border-color:var(--accent,#c45120);background:var(--paper-sunk,#f3eee8);}
    .stockos-terminal__product-top {display:flex;justify-content:space-between;align-items:center;width:100%;gap:8px;}
    .stockos-terminal__product-icon {display:grid;place-items:center;width:32px;height:32px;border-radius:8px;background:var(--paper-sunk,#eee);color:var(--ink-3,#777);}
    .stockos-terminal__product-icon svg {width:18px;height:18px;}
    .stockos-terminal__plus {font-size:20px;color:var(--accent-ink,#a43d11);}
    .stockos-terminal__product-name {font-size:13px;font-weight:650;line-height:1.3;overflow-wrap:anywhere;}
    .stockos-terminal__product-bottom {display:flex;flex-wrap:wrap;justify-content:space-between;align-items:baseline;gap:4px;width:100%;}
    .stockos-terminal__product-price {font-size:15px;font-weight:700;font-variant-numeric:tabular-nums;}
    .stockos-terminal__stock {font-size:10px;color:var(--ink-3,#777);}
    .stockos-terminal__cart {display:flex;flex-direction:column;min-width:0;min-height:0;background:var(--paper-raised,#fff);border-left:1px solid var(--rule,#ddd);}
    .stockos-terminal__cart-head {padding:16px 18px 12px;border-bottom:1px solid var(--rule,#ddd);}
    .stockos-terminal__rows {flex:1;min-height:0;overflow:auto;padding:0 18px;}
    .stockos-terminal__item {display:grid;grid-template-columns:minmax(0,1fr) auto;gap:8px;padding:12px 0;border-bottom:1px solid var(--rule,#ddd);}
    .stockos-terminal__item strong {font-size:13px;overflow-wrap:anywhere;}
    .stockos-terminal__item-info {display:flex;flex-direction:column;gap:5px;min-width:0;}
    .stockos-terminal__item-actions {display:flex;align-items:center;gap:7px;justify-content:flex-end;}
    .stockos-terminal__item-actions button {min-width:34px;min-height:34px;padding:4px;}
    .stockos-terminal__line-total {text-align:right;font-weight:650;font-variant-numeric:tabular-nums;}
    .stockos-terminal__empty {text-align:center;padding:30px 10px;color:var(--ink-3,#777);font-size:13px;}
    .stockos-terminal__empty strong {display:block;color:var(--ink,#222);margin-bottom:5px;}
    .stockos-terminal__footer {border-top:1px solid var(--rule,#ddd);padding:14px 18px;display:grid;gap:10px;flex-shrink:0;}
    .stockos-terminal__payment {display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:5px;}
    .stockos-terminal__payment button {font-size:11px;padding:7px 2px;}
    .stockos-terminal__received {display:flex;align-items:center;justify-content:space-between;gap:8px;font-size:12px;}
    .stockos-terminal__received input {width:110px;min-width:0;min-height:36px;padding:6px 8px;border:1px solid var(--rule,#ddd);border-radius:7px;background:var(--paper,#f6f3ef);color:var(--ink,#222);}
    .stockos-terminal__summary {display:flex;justify-content:space-between;align-items:center;gap:8px;font-size:12px;color:var(--ink-3,#777);}
    .stockos-terminal__summary strong {color:var(--ink,#222);font-variant-numeric:tabular-nums;}
    .stockos-terminal__summary--total {padding-top:8px;border-top:1px solid var(--rule,#ddd);font-size:15px;font-weight:650;color:var(--ink,#222);}
    .stockos-terminal__summary--total strong {font-size:26px;letter-spacing:-.04em;}
    .stockos-terminal button.stockos-terminal__finish {background:var(--ok,#287652);color:white;border:0;min-height:48px;font-weight:700;}
    .stockos-terminal__status {margin:0;padding:8px 20px;font-size:12px;background:var(--paper-sunk,#eee);color:var(--ink,#222);}
    .stockos-terminal__status:empty {display:none;}
    .stockos-terminal__nav {display:none;}
    .stockos-terminal [hidden] {display:none!important;}
    @media(max-width:780px) {
      .stockos-terminal {padding:0;}
      .stockos-terminal__dialog {width:100%;height:100dvh;border-radius:0;}
      .stockos-terminal__header {padding:12px 14px;}
      .stockos-terminal__header h2 {font-size:17px;}
      .stockos-terminal__nav {display:grid;grid-template-columns:1fr 1fr;gap:6px;padding:8px 12px;background:var(--paper-raised,#fff);}
      .stockos-terminal__body {display:flex;flex-direction:column;}
      .stockos-terminal__catalog,.stockos-terminal__cart {flex:1;min-height:0;border:0;}
      .stockos-terminal__catalog {padding:12px;}
      .stockos-terminal__dialog[data-panel="catalog"] .stockos-terminal__cart,.stockos-terminal__dialog[data-panel="cart"] .stockos-terminal__catalog {display:none;}
      .stockos-terminal__footer {padding:10px 12px max(12px,env(safe-area-inset-bottom));}
      .stockos-terminal__item-actions button {min-width:44px;min-height:44px;}
    }
    @media(max-height:600px) and (min-width:781px) {.stockos-terminal__footer {overflow:auto;max-height:64%;}.stockos-terminal__catalog {padding:12px;}}
  `;
  // Only the preview route uses this terminal. Prices are integer cents.
  function previewTerminalProducts(products) {
    return (Array.isArray(products)?products:[]).filter(p=>p&&p.ativo!==false&&Number.isInteger(Number(p.id))&&Number(p.id)>0&&
      p.precoUnitario!=null&&Number.isFinite(Number(p.precoUnitario))&&Number(p.precoUnitario)>=0&&Number.isSafeInteger(Math.round(Number(p.precoUnitario)*100))&&Number.isSafeInteger(Number(p.quantidadeAtual))&&Number(p.quantidadeAtual)>0)
      .map(p=>({id:Number(p.id),name:String(p.nome||'Produto'),category:String(p.categoriaEstoque||'OUTROS'),code:String(p.codigoBarras||''),price:Math.round(Number(p.precoUnitario)*100),stock:Number(p.quantidadeAtual)}))
      .sort((a,b)=>a.name.localeCompare(b.name,'pt-BR'));
  }
  const terminalMoney=cents=>(cents/100).toLocaleString('pt-BR',{style:'currency',currency:'BRL'});
  const terminalNormalize=value=>String(value||'').normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLocaleLowerCase('pt-BR').trim();
  function terminalFilter(products,category,query) {
    const q=terminalNormalize(query);
    return products.filter(p=>(category==='all'||p.category===category)&&terminalNormalize(p.name+' '+p.code).includes(q));
  }
  function terminalCartTotal(products,cart) {
    return [...cart].reduce((sum,[id,qty])=>sum+(products.find(p=>p.id===id)?.price||0)*qty,0);
  }
  function terminalChange(products,cart,id,delta) {
    const p=products.find(p=>p.id===id),next=(cart.get(id)||0)+delta;
    if(!p||!Number.isSafeInteger(next)||next<0||next>p.stock||(!cart.has(id)&&next>0&&cart.size>=100))return false;
    if(next===0)cart.delete(id);else cart.set(id,next);
    return true;
  }
  function terminalPayment(total,method,received) {
    if(!Number.isSafeInteger(total)||total<=0||!['DINHEIRO','PIX','DEBITO','CREDITO'].includes(method))return null;
    const cash=method==='DINHEIRO';
    const amount=cash&&String(received).trim()!==''?Math.round(Number(received)*100):total;
    if(!Number.isSafeInteger(amount)||amount<total)return null;
    return {forma:method,valor:total/100,...(cash?{valorRecebido:amount/100}:{})};
  }
  let terminalOpening=false;
  async function openPreviewTerminal(numero) {
    if(terminalOpening||document.getElementById('stockos-preview-pdv'))return;
    terminalOpening=true;
    let products;
    try {
      const [boxesResponse,productsResponse]=await Promise.all([fetch('/api/caixas'),fetch('/api/produtos')]);
      if(!boxesResponse.ok||!productsResponse.ok)throw Error('Não foi possível carregar caixa e estoque.');
      const boxes=await boxesResponse.json();
      if(!boxes.some(c=>c.numeroCaixa===numero&&c.status==='ABERTO'))throw Error('Abra o caixa antes de simular uma venda.');
      products=previewTerminalProducts(await productsResponse.json());
      if(!/\/caixas(?:\.html)?\/?$/.test(location.pathname))return;
    }catch(e){alert(e.message);return;}finally{terminalOpening=false;}
    const previousFocus=document.activeElement;
    const overlay=create('div','stockos-terminal');overlay.id='stockos-preview-pdv';
    const dialog=create('section','stockos-terminal__dialog');dialog.setAttribute('role','dialog');dialog.setAttribute('aria-modal','true');dialog.setAttribute('aria-labelledby','stockos-terminal-title');
    const make=(tag,name,text)=>create(tag,'stockos-terminal__'+name,text);
    const button=(name,text,fn)=>{const b=make('button',name,text);b.type='button';if(fn)b.onclick=fn;return b;};
    const header=make('header','header'),title=make('h2','title','PDV · Caixa '+numero);title.id='stockos-terminal-title';
    const intro=make('div','intro');intro.append(title,make('p','muted','Demonstração · nenhuma venda ou pagamento real'));
    const close=button('close','Fechar (Esc)',()=>dismiss());header.append(intro,close);
    const feedback=make('p','status','');feedback.setAttribute('role','status');feedback.setAttribute('aria-live','polite');
    const nav=make('nav','nav'),catalogTab=button('tab','Produtos',()=>switchPanel('catalog')),cartTab=button('tab','Pedido · 0 itens',()=>switchPanel('cart'));nav.append(catalogTab,cartTab);
    const body=make('div','body'),catalog=make('section','catalog'),cartPanel=make('aside','cart');
    const search=make('input','search');search.type='search';search.placeholder='Buscar por nome ou código de barras';search.setAttribute('aria-label','Buscar produtos');search.autocomplete='off';
    const catLabel=make('div','section-title');catLabel.append(make('h3','heading','Categorias'));
    const categories=make('div','categories');categories.setAttribute('role','group');categories.setAttribute('aria-label','Categorias de produtos');
    const productHeading=make('div','section-title'),productCount=make('span','muted');productHeading.append(make('h3','heading','Produtos'),productCount);
    const grid=make('div','grid');grid.setAttribute('aria-label','Catálogo de produtos');
    catalog.append(search,catLabel,categories,productHeading,grid);
    const cartHead=make('div','cart-head'),cartHeading=make('div','section-title'),cartCount=make('span','muted','0 itens'),clear=button('clear','Limpar',()=>{if(locked())return;if(cart.size&&confirm('Limpar o pedido atual?')){cart.clear();feedback.textContent='';render();}});
    cartHeading.append(make('h3','heading','Pedido atual'),cartCount,clear);cartHead.append(cartHeading);
    const rows=make('div','rows');
    const footer=make('div','footer'),subtotal=make('div','summary'),subtotalValue=make('strong','amount','R$ 0,00');subtotal.append(make('span','label','Subtotal'),subtotalValue);
    const paymentLabel=make('div','section-title');paymentLabel.append(make('h3','heading','Pagamento fictício'));
    const methods=make('div','payment');methods.setAttribute('role','group');methods.setAttribute('aria-label','Forma de pagamento fictícia');
    const receivedWrap=make('label','received');receivedWrap.append(make('span','label','Valor recebido (opcional)'));
    const received=make('input','received-input');received.type='number';received.min='0';received.step='0.01';received.inputMode='decimal';received.placeholder='Valor exato';receivedWrap.append(received);
    const changeRow=make('div','summary'),changeValue=make('strong','amount','R$ 0,00');changeRow.append(make('span','label','Troco'),changeValue);
    const totalRow=make('div','summary stockos-terminal__summary--total'),totalValue=make('strong','amount','R$ 0,00');totalRow.append(make('span','label','Total'),totalValue);
    const finish=button('finish','Registrar venda fictícia',()=>void submit());
    footer.append(subtotal,paymentLabel,methods,receivedWrap,changeRow,totalRow,finish);
    cartPanel.append(cartHead,rows,footer);body.append(catalog,cartPanel);dialog.append(header,feedback,nav,body);overlay.append(dialog);
    if(!document.getElementById('stockos-terminal-style')){const style=create('style');style.id='stockos-terminal-style';style.textContent=TERMINAL_CSS;document.head.appendChild(style);}
    document.body.appendChild(overlay);
    const cart=new Map();let category='all',method='DINHEIRO',processing=false,submitted=false,pending=null;
    const locked=()=>processing||submitted||!!pending;
    const labels={PREPARADOS:'Preparados',BEBIDAS:'Bebidas',ALIMENTOS:'Alimentos',CONGELADO:'Congelados',OUTROS:'Outros'};
    const categoryButtons=[['all','Todos'],...[...new Set(products.map(p=>p.category))].sort().map(c=>[c,labels[c]||c.replace(/_/g,' ').toLocaleLowerCase('pt-BR').replace(/^./,v=>v.toUpperCase())])].map(([key,label])=>{
      const b=button('category',label,()=>{category=key;renderCatalog();});b.dataset.category=key;categories.appendChild(b);return b;
    });
    const methodButtons=[['DINHEIRO','Dinheiro'],['PIX','Pix'],['DEBITO','Débito'],['CREDITO','Crédito']].map(([key,label])=>{
      const b=button('method',label,()=>{if(locked())return;method=key;received.value='';renderPayment();});b.dataset.method=key;methods.appendChild(b);return b;
    });
    function switchPanel(panel){dialog.dataset.panel=panel;catalogTab.setAttribute('aria-pressed',String(panel==='catalog'));cartTab.setAttribute('aria-pressed',String(panel==='cart'));}
    function dismiss(){
      if(processing)return;
      if(pending&&!submitted&&!confirm('O resultado da venda ainda não foi confirmado. Fechar sem tentar confirmar novamente?'))return;
      if(!pending&&!submitted&&cart.size&&!confirm('Descartar o pedido fictício em andamento?'))return;
      overlay.remove();document.removeEventListener('keydown',keyHandler);if(previousFocus?.isConnected)previousFocus.focus();
    }
    function keyHandler(e){
      if(!overlay.isConnected){document.removeEventListener('keydown',keyHandler);return;}
      if(e.key==='Escape'){e.preventDefault();e.stopImmediatePropagation();dismiss();return;}
      if(e.key==='Tab'){
        const focusables=[...dialog.querySelectorAll('button:not(:disabled),input:not(:disabled)')].filter(n=>n.getClientRects().length);
        const first=focusables[0],last=focusables[focusables.length-1];
        if(e.shiftKey&&document.activeElement===first){e.preventDefault();last?.focus();}
        else if(!e.shiftKey&&document.activeElement===last){e.preventDefault();first?.focus();}
      }
    }
    document.addEventListener('keydown',keyHandler,true);
    function change(id,delta){
      if(locked())return;
      if(!terminalChange(products,cart,id,delta)){feedback.textContent='Quantidade indisponível ou limite de 100 produtos diferentes atingido.';return;}
      feedback.textContent='';render();
    }
    function renderCatalog(){
      categoryButtons.forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.category===category)));
      const focused=document.activeElement?.dataset?.product;
      grid.replaceChildren();const visible=terminalFilter(products,category,search.value);productCount.textContent=visible.length+' produtos';
      if(!visible.length){grid.append(make('p','empty','Nenhum produto encontrado.'));return;}
      visible.forEach(p=>{
        const remaining=p.stock-(cart.get(p.id)||0);
        const b=button('product',undefined,()=>change(p.id,1));b.dataset.product=String(p.id);
        b.disabled=locked()||remaining<=0;
        const top=make('span','product-top'),mark=make('span','product-icon');
        // Static SVG, never interpolates catalog data.
        mark.innerHTML='<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" aria-hidden="true"><path d="m12 3 9 5-9 5-9-5 9-5Zm-9 5v8l9 5 9-5V8M12 13v8"/></svg>';
        top.append(mark,make('span','plus','+'));
        const bottom=make('span','product-bottom');bottom.append(make('strong','product-price',terminalMoney(p.price)),make('span','stock',remaining+' disponíveis'));
        b.append(top,make('span','product-name',p.name),bottom);grid.appendChild(b);
        if(focused===String(p.id)&&!b.disabled)b.focus({preventScroll:true});
      });
    }
    function renderPayment(){
      const total=terminalCartTotal(products,cart),payment=terminalPayment(total,method,received.value);
      methodButtons.forEach(b=>{b.setAttribute('aria-pressed',String(b.dataset.method===method));b.disabled=locked();});
      receivedWrap.hidden=changeRow.hidden=method!=='DINHEIRO';received.disabled=locked();
      changeValue.textContent=payment&&method==='DINHEIRO'?terminalMoney(Math.round(payment.valorRecebido*100)-total):'—';
      subtotalValue.textContent=totalValue.textContent=terminalMoney(total);
      finish.disabled=processing||submitted||(!pending&&(!cart.size||!payment));
      finish.textContent=submitted?'Venda registrada':processing?'Registrando…':pending?'Confirmar novamente':'Registrar venda fictícia';
      clear.disabled=locked()||!cart.size;close.disabled=processing;
    }
    function render(){
      const active=document.activeElement,focusKey=active?.dataset?.cartControl;
      rows.replaceChildren();const count=[...cart.values()].reduce((a,b)=>a+b,0);
      cartCount.textContent=count+' itens';cartTab.textContent='Pedido · '+count+' itens';
      if(!cart.size){const empty=make('div','empty');empty.append(make('strong','empty-title','Seu pedido está vazio'),make('span','muted','Selecione um produto do catálogo para adicionar.'));rows.appendChild(empty);}
      for(const [id,qty] of cart){
        const p=products.find(p=>p.id===id),row=make('div','item'),info=make('div','item-info'),actions=make('div','item-actions');
        info.append(make('strong','item-name',p.name),make('span','muted',terminalMoney(p.price)+' / un.'));
        const minus=button('minus','−',()=>change(id,-1)),plus=button('plus','+',()=>change(id,1));
        minus.setAttribute('aria-label','Remover uma unidade de '+p.name);plus.setAttribute('aria-label','Adicionar uma unidade de '+p.name);
        minus.dataset.cartControl=id+'-';plus.dataset.cartControl=id+'+';
        minus.disabled=locked();plus.disabled=locked()||qty>=p.stock;actions.append(minus,make('strong','quantity',String(qty)),plus);
        row.append(info,make('span','line-total',terminalMoney(p.price*qty)),actions);rows.appendChild(row);
      }
      renderCatalog();renderPayment();
      if(focusKey){const restored=[...rows.querySelectorAll('button')].find(b=>b.dataset.cartControl===focusKey&&!b.disabled);restored?.focus({preventScroll:true});}
    }
    search.oninput=renderCatalog;
    search.onkeydown=e=>{if(e.key==='Enter'){e.preventDefault();const matches=products.filter(p=>p.code&&p.code===search.value.trim());if(matches.length===1){change(matches[0].id,1);search.value='';renderCatalog();}}};
    received.oninput=renderPayment;
    async function submit(){
      if(processing||submitted)return;
      if(!pending){
        const total=terminalCartTotal(products,cart),payment=terminalPayment(total,method,received.value);
        if(!cart.size||!payment)return;
        pending={idempotencyKey:window.crypto?.randomUUID?.()||('pdv_'+Date.now()+'_'+Math.random().toString(36).slice(2)),numeroCaixa:numero,itens:[...cart].map(([id,qty])=>({idProduto:id,quantidade:qty})),pagamentos:[payment]};
      }
      processing=true;render();
      try{
        const response=await fetch('/api/preview/transacoes/venda',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(pending)});
        if(!response.ok){
          const error=await response.json().catch(()=>({}));
          // Known validation rejections did not commit; an ambiguous failure retains the exact payload.
          if([400,409].includes(response.status))pending=null;
          throw Error(error.erro||'Não foi possível confirmar a venda fictícia.');
        }
        submitted=true;pending=null;feedback.textContent='Venda fictícia registrada. Estoque e caixa atualizados; nenhum pagamento real foi realizado.';switchPanel('cart');
        if(typeof carregarCaixas==='function')try{await carregarCaixas();}catch(error){console.warn('Preview PDV: falha ao atualizar caixas',error);}
      }catch(error){feedback.textContent=error.message+(pending?' Tente confirmar novamente com o mesmo pedido.':' Confira os dados e tente novamente.');switchPanel('cart');}
      finally{processing=false;render();}
    }
    switchPanel('catalog');render();search.focus();
  }

  function mountPreviewPdv(){
    if(!/\/caixas(?:\.html)?\/?$/.test(location.pathname))return;
    if(window.StockOsDesignReview?.mode!=='original'){window.abrirPDV=openPreviewTerminal;return;}
    window.abrirPDV=async numero=>{
      const boxes=await (await fetch('/api/caixas')).json();
      if(!boxes.some(c=>c.numeroCaixa===numero&&c.status==='ABERTO')){alert('Abra o caixa antes de simular uma venda.');return;}
      document.getElementById('stockos-preview-pdv')?.remove();
      const overlay=create('div','stockos-preview-pdv');overlay.id='stockos-preview-pdv';
      overlay.style.cssText='position:fixed;inset:0;z-index:2147483600;background:#000a;display:grid;place-items:center;padding:16px;font:14px system-ui';
      const modal=create('div','stockos-preview-pdv__dialog');modal.style.cssText='width:min(700px,100%);max-height:85vh;overflow:auto;background:#142236;color:#f4f7fa;border:1px solid #5c7087;border-radius:14px;padding:20px;box-shadow:0 15px 50px #0008';
      const head=create('div','stockos-preview-pdv__head');head.style.cssText='display:flex;justify-content:space-between;gap:16px;align-items:center';
      const title=create('h2','stockos-preview-pdv__title','PDV de demonstração · Caixa '+numero);title.style.margin='0';head.append(title);
      const close=create('button','stockos-preview-pdv__close','Fechar');close.type='button';close.onclick=()=>overlay.remove();head.append(close);modal.append(head);
      const info=create('p','stockos-preview-pdv__info','Escolha produtos do estoque fictício. Nenhuma venda, pagamento ou emissão fiscal é real.');info.style.opacity='.8';modal.append(info);
      const products=(await (await fetch('/api/produtos')).json()).filter(p=>p.ativo!==false&&p.quantidadeAtual>0);
      const navigation=create('div','stockos-preview-pdv__navigation');
      const showCatalog=create('button','stockos-preview-pdv__tab','Produtos');
      const showCart=create('button','stockos-preview-pdv__tab','Pedido · 0 itens');
      showCatalog.type=showCart.type='button';
      navigation.append(showCatalog,showCart);
      modal.insertBefore(navigation,info);
      function switchPanel(name) {
        modal.dataset.mobilePanel=name;
        showCatalog.setAttribute('aria-pressed',String(name==='produtos'));
        showCart.setAttribute('aria-pressed',String(name==='pedido'));
      }
      showCatalog.addEventListener('click',()=>switchPanel('produtos'));
      showCart.addEventListener('click',()=>switchPanel('pedido'));

      const pick=create('select','stockos-preview-pdv__select');pick.setAttribute('aria-label','Produto');pick.style.cssText='width:100%;margin:8px 0;padding:10px';for(const p of products){const o=create('option','',p.nome+' · R$ '+Number(p.precoUnitario).toFixed(2)+' · '+p.quantidadeAtual+' em estoque');o.value=p.id;pick.append(o);}modal.append(pick);
      const qty=create('input','stockos-preview-pdv__qty');qty.type='number';qty.setAttribute('aria-label','Quantidade');qty.min='1';qty.step='1';qty.value='1';qty.style.cssText='width:85px;margin-right:8px;padding:10px';
      const add=create('button','stockos-preview-pdv__add','Adicionar ao carrinho');add.type='button';const controls=create('div','stockos-preview-pdv__controls');controls.append(qty,add);modal.append(controls);
      const rows=create('div','stockos-preview-pdv__rows');rows.style.margin='14px 0';modal.append(rows);
      const total=create('strong','stockos-preview-pdv__total','Total: R$ 0,00');total.style.display='block';modal.append(total);
      const finish=create('button','stockos-preview-pdv__finish','Registrar venda fictícia');finish.type='button';finish.style.cssText='padding:10px 14px;margin-top:14px;background:#2c7557;color:white;border:0;border-radius:8px;cursor:pointer';modal.append(finish);
      const feedback=create('p','stockos-preview-pdv__feedback');feedback.setAttribute('role','status');feedback.setAttribute('aria-live','polite');modal.append(feedback);
      // Dois painéis separados, com navegação por toque em telas estreitas.
      const catalogPanel=create('section','stockos-preview-pdv__catalog');
      const cartPanel=create('section','stockos-preview-pdv__cart');
      catalogPanel.append(pick,controls);
      cartPanel.append(rows,total,finish,feedback);
      modal.append(catalogPanel,cartPanel);
      if(!document.getElementById('stockos-preview-pdv-style')){
        const style=create('style');
        style.id='stockos-preview-pdv-style';
        style.textContent=PREVIEW_PDV_CSS;
        document.head.appendChild(style);
      }
      overlay.append(modal);document.body.append(overlay);
      const cart=[];
      let pendingKey=null;
      let processing=false;
      let submitted=false;
      const updateQuantity=(id,delta)=>{
        if(processing||submitted)return;
        const item=cart.find(c=>c.id===id),product=products.find(p=>p.id===id);
        if(!item||!product)return;
        const next=item.qty+delta;
        if(next>product.quantidadeAtual){feedback.textContent='Estoque insuficiente.';return;}
        if(next<=0)cart.splice(cart.indexOf(item),1);
        else item.qty=next;
        pendingKey=null;feedback.textContent='';render();
      };
      const render=()=>{rows.replaceChildren();let value=0;
        for(const c of cart){
          const p=products.find(x=>x.id===c.id);
          const row=create('div','stockos-preview-pdv__item');
          const label=create('span','stockos-preview-pdv__item-text',p.nome+' · R$ '+(p.precoUnitario*c.qty).toFixed(2));
          const actions=create('span','stockos-preview-pdv__item-actions');
          const less=create('button','','−'),more=create('button','','+');
          less.type=more.type='button';
          less.setAttribute('aria-label','Remover uma unidade de '+p.nome);
          more.setAttribute('aria-label','Adicionar uma unidade de '+p.nome);
          less.disabled=more.disabled=processing||submitted;
          less.addEventListener('click',()=>updateQuantity(c.id,-1));
          more.addEventListener('click',()=>updateQuantity(c.id,1));
          actions.append(less,create('strong','',String(c.qty)),more);
          row.append(label,actions);row.style.padding='5px 0';rows.append(row);
          value+=c.qty*p.precoUnitario;
        }
        if(!cart.length)rows.append(create('p','','Toque em Produtos e adicione itens ao pedido.'));
        total.textContent='Total: R$ '+value.toFixed(2);
        const itemCount=cart.reduce((sum,item)=>sum+item.qty,0);
        showCart.textContent='Pedido · '+itemCount+' '+(itemCount===1?'item':'itens');
        return value;};
      add.onclick=()=>{
        if(submitted||processing)return;
        const id=Number(pick.value),n=Number(qty.value),p=products.find(x=>x.id===id);if(!p||!Number.isInteger(n)||n<=0){feedback.textContent='Quantidade inválida.';return;}
        const existing=cart.find(c=>c.id===id);const current=existing?.qty||0;
        if(current+n>p.quantidadeAtual){feedback.textContent='Estoque insuficiente.';return;}
        if(existing)existing.qty+=n;else cart.push({id,qty:n});
        pendingKey=null;
        feedback.textContent='';render();
      };
      finish.onclick=async()=>{
        if(processing||submitted)return;
        if(cart.length===0){feedback.textContent='Adicione produtos ao carrinho.';return;}
        render();
        processing=true;
        finish.disabled=true;
        try{
          // One preview-only command validates and commits the sale atomically.
          // The idempotency key survives a retry of this same cart.
          if(!pendingKey)pendingKey=window.crypto?.randomUUID?.() || ('pdv_'+Date.now()+'_'+Math.random().toString(36).slice(2));
          const key=pendingKey;
          const response=await fetch('/api/preview/transacoes/venda',{
            method:'POST',headers:{'Content-Type':'application/json'},
            body:JSON.stringify({
              idempotencyKey:key,numeroCaixa:numero,
              itens:cart.map(c=>({idProduto:c.id,quantidade:c.qty}))
            })
          });
          if(!response.ok){
            const error=await response.json().catch(()=>({}));
            throw Error(error.erro||'Não foi possível registrar a venda fictícia.');
          }
          feedback.textContent='Venda fictícia registrada com estoque e caixa atualizados. Nenhum pagamento foi realizado.';
          submitted=true;
          finish.textContent='Venda fictícia registrada';
          switchPanel('pedido');
          finish.disabled=true;
          if(typeof carregarCaixas==='function') {
            try { await carregarCaixas(); }
            catch(error) { console.warn('Preview PDV: falha ao atualizar caixas',error); }
          }
        }catch(e){
          feedback.textContent=e.message+' Confira antes de tentar novamente.';
          switchPanel('pedido');finish.disabled=false;
        }
        finally{processing=false;}
      };
      overlay.addEventListener('click',e=>{if(e.target===overlay)overlay.remove();});
      switchPanel('produtos');
    };
  }
  window.StockOSPreviewHooks=window.StockOSPreviewHooks||{};
  window.StockOSPreviewHooks.caixas=mountPreviewPdv;
  if(document.readyState==='loading') document.addEventListener('DOMContentLoaded',mountPreviewPdv);
  else mountPreviewPdv();
})();
