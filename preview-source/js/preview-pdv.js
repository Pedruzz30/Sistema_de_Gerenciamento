/* Browser-only PDV walkthrough. Does not execute real sales or payments. */
(() => {
  'use strict';
  const create=(tag,cls,text)=>{const e=document.createElement(tag);if(cls)e.className=cls;if(text!==undefined)e.textContent=text;return e;};
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
      overlay.append(modal);document.body.append(overlay);
      const cart=[];
      let pendingKey=null;
      let processing=false;
      let submitted=false;
      const render=()=>{rows.replaceChildren();let value=0;for(const c of cart){const p=products.find(x=>x.id===c.id);const row=create('div','stockos-preview-pdv__item',''+c.qty+' × '+p.nome+' — R$ '+(p.precoUnitario*c.qty).toFixed(2));row.style.padding='5px 0';rows.append(row);value+=c.qty*p.precoUnitario;}total.textContent='Total: R$ '+value.toFixed(2);
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
