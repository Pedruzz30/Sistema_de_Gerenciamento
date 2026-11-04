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
  function mountPreviewPdv(){
    if(!/\/caixas(?:\.html)?\/?$/.test(location.pathname))return;
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
          if(typeof carregarCaixas==='function')await carregarCaixas();
        }catch(e){feedback.textContent=e.message;switchPanel('pedido');finish.disabled=false;}
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
