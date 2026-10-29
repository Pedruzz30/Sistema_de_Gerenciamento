/* Preview-only purchase invoice simulation. No backend, fiscal issuance or payment. */
(() => {
  'use strict';
  const original = window.fetch.bind(window);
  const KEY = 'stockos-preview-notes-v1';
  const load = () => { try { const v=JSON.parse(localStorage.getItem(KEY)||'null');return Array.isArray(v)?v:null; } catch {return null;} };
  let notas = load() || [];
  const save = () => { try {localStorage.setItem(KEY,JSON.stringify(notas));}catch{} };
  const answer = (data,status=200) => Promise.resolve(new Response(JSON.stringify(data),{status,headers:{'Content-Type':'application/json'}}));
  const error = (message,status=400) => answer({erro:message},status);
  const urlFor = input => new URL(input instanceof Request?input.url:String(input),location.href);
  const jsonBody = init => { try{return JSON.parse(init?.body||'{}');}catch{return {};} };
  const now = () => new Date().toISOString();
  const updateTotal = nota => {nota.total=+(nota.itens.reduce((s,i)=>s+i.subtotal,0)).toFixed(2);return nota;};
  window.fetch = async (input,init={}) => {
    const url=urlFor(input); const path=url.pathname;
    const method=String(init.method||(input instanceof Request?input.method:'GET')).toUpperCase();
    if(url.origin!==location.origin || !path.startsWith('/api/notas'))return original(input,init);
    const body=jsonBody(init);
    if(path==='/api/notas'){
      if(method==='GET')return answer(notas);
      if(method==='POST'){
        const resp=await original('/api/fornecedores/ativos');const fs=await resp.json();
        const f=fs.find(x=>x.id===Number(body.fornecedorId));
        if(!f)return error('Selecione um fornecedor ativo.');
        const id=Math.max(0,...notas.map(n=>n.id))+1;
        const nota={id,numero:'DEMO-'+String(id).padStart(4,'0'),fornecedorId:f.id,fornecedorNome:f.nome,status:'PENDENTE',dataEmissao:now(),itens:[],total:0};
        notas.unshift(nota);save();return answer(nota);
      }
    }
    const match=path.match(/^\/api\/notas\/(\d+)(?:\/(itens|confirmar|pagamento|cancelar))?$/);
    if(!match)return error('Rota de nota não disponível no preview.',501);
    const nota=notas.find(n=>n.id===Number(match[1]));
    if(!nota)return error('Nota de demonstração não encontrada.',404);
    const action=match[2];
    if(!action){
      if(method==='GET')return answer(nota);
      if(method==='DELETE'){
        if(nota.status!=='PENDENTE')return error('Somente rascunhos podem ser descartados.');
        notas=notas.filter(n=>n.id!==nota.id);save();return answer({sucesso:true});
      }
    }
    if(action==='itens' && method==='POST'){
      if(nota.status!=='PENDENTE')return error('Esta nota já foi confirmada.');
      const quantity=Number(body.quantidade),price=Number(body.precoUnitario);
      if(!Number.isInteger(quantity)||quantity<=0||!Number.isFinite(price)||price<0)return error('Quantidade ou preço inválidos.');
      let product;
      if(body.produtoId){
        const res=await original('/api/produtos');product=(await res.json()).find(p=>p.id===Number(body.produtoId));
      } else if(body.novoProduto?.nome){
        const res=await original('/api/produtos',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({nome:body.novoProduto.nome,quantidadeInicial:0,quantidadeMinima:body.novoProduto.quantidadeMinima||0,precoUnitario:body.novoProduto.precoUnitarioBase||price,categoriaEstoque:body.novoProduto.categoriaEstoque||null})});
        if(res.ok)product=await res.json();
      }
      if(!product)return error('Produto não encontrado.');
      const row={id:Math.max(0,...nota.itens.map(i=>i.id))+1,produtoId:product.id,produtoNome:product.nome,quantidade:quantity,precoUnitario:price,subtotal:+(quantity*price).toFixed(2)};
      nota.itens.push(row);updateTotal(nota);save();return answer(nota);
    }
    if(action==='confirmar' && method==='POST'){
      if(nota.status!=='PENDENTE'||!nota.itens.length)return error('Adicione itens ao rascunho antes de confirmar.');
      const stock=await original('/api/produtos');const products=await stock.json();
      // Preflight all products before writing to avoid partially confirmed invoices.
      if(nota.itens.some(i=>!products.some(p=>p.id===i.produtoId)))return error('Produto da nota não encontrado.');
      for(const item of nota.itens){
        const r=await original('/api/movimentacoes',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({idProduto:item.produtoId,quantidade:item.quantidade,tipo:'ENTRADA'})});
        if(!r.ok)return error('Não foi possível atualizar o estoque fictício.');
      }
      nota.status='CONFIRMADA';save();return answer(nota);
    }
    if(action==='pagamento' && method==='POST'){
      if(nota.status!=='CONFIRMADA')return error('Somente notas confirmadas podem receber pagamento.');
      nota.status='PAGA';save();return answer(nota);
    }
    if(action==='cancelar' && method==='POST'){
      if(nota.status!=='PENDENTE')return error('Esta demonstração cancela somente rascunhos.');
      nota.status='CANCELADA';save();return answer(nota);
    }
    return error('Operação indisponível na demonstração.',501);
  };
  // A public preview must never suggest that payment or invoice issuance was real.
  document.addEventListener('DOMContentLoaded',()=>{
    if(!location.pathname.endsWith('notas.html'))return;
    const banner=document.createElement('div');banner.setAttribute('role','note');
    banner.style.cssText='padding:10px 15px;margin:8px 16px;border:1px solid #a7bdd8;border-radius:8px;background:#122436;color:#e6f1ff;font:500 12px system-ui';
    banner.textContent='DEMO: notas, confirmação, estoque e pagamentos são apenas simulações locais. Nenhum documento fiscal é emitido.';
    document.querySelector('.main-content,main,.content')?.prepend(banner);
  });
})();
