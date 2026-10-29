/* Browser-only PDV walkthrough. Does not execute real sales or payments. */
(() => {
  'use strict';
  const create=(tag,cls,text)=>{const e=document.createElement(tag);if(cls)e.className=cls;if(text!==undefined)e.textContent=text;return e;};
  document.addEventListener('DOMContentLoaded',()=>{
    if(!location.pathname.endsWith('/caixas.html'))return;
    window.abrirPDV=async numero=>{
      const boxes=await (await fetch('/api/caixas')).json();
      if(!boxes.some(c=>c.numeroCaixa===numero&&c.status==='ABERTO')){alert('Abra o caixa antes de simular uma venda.');return;}
      document.getElementById('stockos-preview-pdv')?.remove();
      const overlay=create('div');overlay.id='stockos-preview-pdv';
      overlay.style.cssText='position:fixed;inset:0;z-index:2147483600;background:#000a;display:grid;place-items:center;padding:16px;font:14px system-ui';
      const modal=create('div');modal.style.cssText='width:min(700px,100%);max-height:85vh;overflow:auto;background:#142236;color:#f4f7fa;border:1px solid #5c7087;border-radius:14px;padding:20px;box-shadow:0 15px 50px #0008';
      const head=create('div');head.style.cssText='display:flex;justify-content:space-between;gap:16px;align-items:center';
      const title=create('h2','','PDV de demonstração · Caixa '+numero);title.style.margin='0';head.append(title);
      const close=create('button','','Fechar');close.type='button';close.onclick=()=>overlay.remove();head.append(close);modal.append(head);
      const info=create('p','','Escolha produtos do estoque fictício. Nenhuma venda, pagamento ou emissão fiscal é real.');info.style.opacity='.8';modal.append(info);
      const products=(await (await fetch('/api/produtos')).json()).filter(p=>p.ativo!==false&&p.quantidadeAtual>0);
      const pick=create('select');pick.style.cssText='width:100%;margin:8px 0;padding:10px';for(const p of products){const o=create('option','',p.nome+' · R$ '+Number(p.precoUnitario).toFixed(2)+' · '+p.quantidadeAtual+' em estoque');o.value=p.id;pick.append(o);}modal.append(pick);
      const qty=create('input');qty.type='number';qty.min='1';qty.step='1';qty.value='1';qty.style.cssText='width:85px;margin-right:8px;padding:10px';
      const add=create('button','','Adicionar ao carrinho');add.type='button';const controls=create('div');controls.append(qty,add);modal.append(controls);
      const rows=create('div');rows.style.margin='14px 0';modal.append(rows);
      const total=create('strong','','Total: R$ 0,00');total.style.display='block';modal.append(total);
      const finish=create('button','','Registrar venda fictícia');finish.type='button';finish.style.cssText='padding:10px 14px;margin-top:14px;background:#2c7557;color:white;border:0;border-radius:8px;cursor:pointer';modal.append(finish);
      const feedback=create('p');modal.append(feedback);
      overlay.append(modal);document.body.append(overlay);
      const cart=[];
      const render=()=>{rows.replaceChildren();let value=0;for(const c of cart){const p=products.find(x=>x.id===c.id);const row=create('div','',''+c.qty+' × '+p.nome+' — R$ '+(p.precoUnitario*c.qty).toFixed(2));row.style.padding='5px 0';rows.append(row);value+=c.qty*p.precoUnitario;}total.textContent='Total: R$ '+value.toFixed(2);return value;};
      add.onclick=()=>{
        const id=Number(pick.value),n=Number(qty.value),p=products.find(x=>x.id===id);if(!p||!Number.isInteger(n)||n<=0){feedback.textContent='Quantidade inválida.';return;}
        const existing=cart.find(c=>c.id===id);const current=existing?.qty||0;
        if(current+n>p.quantidadeAtual){feedback.textContent='Estoque insuficiente.';return;}
        if(existing)existing.qty+=n;else cart.push({id,qty:n});
        feedback.textContent='';render();
      };
      finish.onclick=async()=>{
        if(cart.length===0){feedback.textContent='Adicione produtos ao carrinho.';return;}
        const value=render();
        finish.disabled=true;
        try{
          const cash=await (await fetch('/api/caixas')).json();if(!cash.some(c=>c.numeroCaixa===numero&&c.status==='ABERTO'))throw Error('Caixa encerrado.');
          const current=await (await fetch('/api/produtos')).json();
          if(cart.some(c=>{const p=current.find(p=>p.id===c.id);return !p||p.quantidadeAtual<c.qty;}))throw Error('Saldo de produto mudou; atualize o PDV.');
          // The local preview deliberately processes only simulated stock and cash.
          // Neither backend API nor payment gateway is contacted.
          for(const c of cart){
            const r=await fetch('/api/movimentacoes',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({idProduto:c.id,quantidade:c.qty,tipo:'SAIDA'})});
            if(!r.ok)throw Error('Falha em baixa fictícia de estoque.');
          }
          const sale=await fetch('/api/caixas/venda',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({numeroCaixa:numero,valor:value,descricao:'Venda demonstrativa'})});
          if(!sale.ok)throw Error('Falha ao registrar venda no caixa fictício.');
          feedback.textContent='Venda fictícia registrada. Nenhum pagamento foi realizado.';
          finish.disabled=true;
          if(typeof carregarCaixas==='function')await carregarCaixas();
        }catch(e){feedback.textContent=e.message;finish.disabled=false;}
      };
      overlay.addEventListener('click',e=>{if(e.target===overlay)overlay.remove();});
    };
  });
})();
