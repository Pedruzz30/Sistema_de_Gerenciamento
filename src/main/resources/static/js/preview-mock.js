/* StockOS Preview - isolated browser-only data. Never bundled in the real Spring application. */
(function () {
  'use strict';
  const KEY = 'stockos-preview-v1';
  const today = () => { const d = new Date(); return [d.getFullYear(), String(d.getMonth()+1).padStart(2,'0'), String(d.getDate()).padStart(2,'0')].join('-'); };
  const stamp = (h, m) => today() + 'T' + String(h).padStart(2,'0') + ':' + String(m).padStart(2,'0') + ':00';
  const names = [
    ['Arroz tipo 1 5kg','SECO',42,10,24.90],['Farinha de trigo 5kg','SECO',12,10,18.50],
    ['Óleo de soja 900ml','SECO',26,8,8.90],['Açúcar 1kg','SECO',18,6,5.90],
    ['Molho de tomate 2kg','SECO',8,12,19.90],['Refrigerante cola lata 350ml','BEBIDAS',72,20,6],
    ['Água mineral 500ml','BEBIDAS',60,20,4],['Suco de uva 1L','BEBIDAS',14,8,12.90],
    ['Queijo muçarela 1kg','FRIO',4,8,38],['Presunto 1kg','FRIO',12,6,27],
    ['Carne bovina 1kg','CONGELADO',9,10,53],['Frango 1kg','CONGELADO',21,8,23],
    ['Batata congelada 2kg','CONGELADO',15,6,29.90],['Hambúrguer 180g','CONGELADO',40,12,11.90],
    ['Pão de hambúrguer pacote','SECO',24,10,15.90],['Café torrado 500g','SECO',18,6,19.90],
    ['Leite integral 1L','FRIO',0,12,5.90],['Chocolate 1kg','SECO',16,8,32],
    ['Cebola 1kg','FRIO',25,7,6.90],['Tomate 1kg','FRIO',9,8,8.50],
    ['Sal refinado 1kg','SECO',19,5,3.90],['Pimenta-do-reino 100g','SECO',7,4,9.90],
    ['Sorvete 2L','CONGELADO',12,5,25],['Água com gás 500ml','BEBIDAS',36,12,4.90]
  ];
  function level(q, min) { if(q<=0)return 'SEM_ESTOQUE';if(min<=0)return 'ADEQUADO';const p=q/min;return p<=.25?'CRITICO':p<=.5?'BAIXO':p<=.75?'MODERADO':'ADEQUADO'; }
  function initial() {
    const produtos = names.map(([nome,categoriaEstoque,quantidadeAtual,quantidadeMinima,precoUnitario],i)=>({
      id:i+1,nome,categoriaEstoque,quantidadeAtual,quantidadeMinima,precoUnitario,
      precoCusto: +(precoUnitario*.6).toFixed(2),nivelEstoque:level(quantidadeAtual,quantidadeMinima),
      controladoPorEstoque:true, ativo:true, unidadeBaseCodigo:'UN',politicaFalta:'BLOQUEAR'
    }));
    return { produtos, movimentacoes:[
      {id:1,tipo:'ENTRADA',produto:'Água mineral 500ml',quantidade:20,dataHora:stamp(9,15)},
      {id:2,tipo:'SAIDA',produto:'Queijo muçarela 1kg',quantidade:3,dataHora:stamp(10,40)},
      {id:3,tipo:'SAIDA',produto:'Refrigerante cola lata 350ml',quantidade:6,dataHora:stamp(12,5)}
    ], caixas:[
      {id:1,caixaId:1,numeroCaixa:1,status:'ABERTO',totalVendas:268.50,totalEntradas:268.50,totalSaidas:0,saldoAtual:368.50,nomeOperador:'Operador Demo',dataAbertura:stamp(8,0)},
      {id:2,caixaId:2,numeroCaixa:2,status:'ENCERRADO',totalVendas:120,totalEntradas:120,totalSaidas:0,saldoAtual:220,nomeOperador:'Operador Demo',dataAbertura:stamp(8,0),dataEncerramento:stamp(16,0)}
    ] };
  }
  let db;
  try { db=JSON.parse(localStorage.getItem(KEY)||'null'); } catch(e) {}
  if (!db || !Array.isArray(db.produtos)) db=initial();
  const save=()=>{try{localStorage.setItem(KEY,JSON.stringify(db));}catch(e){console.warn('Preview: armazenamento indisponível',e);}};
  save();
  const respond=(value,status=200)=>Promise.resolve(new Response(JSON.stringify(value),{status,headers:{'Content-Type':'application/json'}}));
  const user = {ru:1,nome:'Admin',sobrenome:'Demo',nomeClasse:'SUPERIOR',perfil:'ADMIN',ativo:true,permissoes:['VER_ESTOQUE','EDITAR_ESTOQUE','VER_COMPRAS','VER_VENDAS','VER_FINANCAS','GERENCIAR_FUNCIONARIOS','VER_LOGS']};
  async function handle(input, init={}) {
    const raw = input instanceof Request ? input.url : String(input);
    const url = new URL(raw, location.href);
    if(url.origin!==location.origin || !url.pathname.startsWith('/api/')) return respond({erro:'Preview isolado: conexão externa desativada'},403);
    const path=url.pathname;
    const method=String(init.method||(input instanceof Request?input.method:'GET')).toUpperCase();
    let body={};
    try{body=JSON.parse(init.body||'{}');}catch(e){}
    if(path==='/api/auth/login' && method==='POST')return respond(user);
    if(path==='/api/produtos' && method==='GET')return respond(db.produtos);
    if(path==='/api/produtos' && method==='POST'){
      const id=Math.max(0,...db.produtos.map(p=>p.id))+1;
      const quantidadeAtual=Math.max(0,Number(body.quantidadeInicial)||0);
      const quantidadeMinima=Math.max(0,Number(body.quantidadeMinima)||0);
      const p={id,nome:String(body.nome||'Produto de teste').slice(0,120),quantidadeAtual,quantidadeMinima,precoUnitario:Number(body.precoUnitario)||0,precoCusto:null,ativo:true,controladoPorEstoque:true,categoriaEstoque:body.categoriaEstoque||null,nivelEstoque:level(quantidadeAtual,quantidadeMinima)};
      db.produtos.push(p);save();return respond(p);
    }
    const item=path.match(/^\/api\/produtos\/(\d+)$/);
    if(item && method==='PUT'){
      const p=db.produtos.find(v=>v.id===Number(item[1]));
      if(!p)return respond({erro:'Produto não encontrado'},404);
      Object.assign(p,{nome:String(body.nome||p.nome),quantidadeMinima:Number(body.quantidadeMinima??p.quantidadeMinima),precoUnitario:Number(body.precoUnitario??p.precoUnitario),categoriaEstoque:body.categoriaEstoque??p.categoriaEstoque});
      p.nivelEstoque=level(p.quantidadeAtual,p.quantidadeMinima);save();return respond(p);
    }
    if(path==='/api/movimentacoes' && method==='GET')return respond(db.movimentacoes);
    if(path==='/api/movimentacoes' && method==='POST'){
      const p=db.produtos.find(v=>v.id===Number(body.idProduto));
      const qtd=Number(body.quantidade);
      if(!p||!Number.isInteger(qtd)||qtd<=0)return respond({erro:'Movimentação inválida'},400);
      const sinal=body.tipo==='ENTRADA'?1:body.tipo==='SAIDA'?-1:0;
      if(!sinal||p.quantidadeAtual+sinal*qtd<0)return respond({erro:'Estoque insuficiente ou tipo inválido'},400);
      const anterior=p.quantidadeAtual;p.quantidadeAtual+=sinal*qtd;p.nivelEstoque=level(p.quantidadeAtual,p.quantidadeMinima);
      db.movimentacoes.unshift({id:Date.now(),tipo:body.tipo,produto:p.nome,quantidade:qtd,dataHora:new Date().toLocaleString('sv-SE').replace(' ','T'),saldoAnterior:anterior,saldoPosterior:p.quantidadeAtual});save();return respond(p);
    }
    if(path==='/api/caixas' && method==='GET')return respond(db.caixas);
    if(path==='/api/caixas/metricas/hoje'){
      const t=db.caixas.reduce((a,c)=>a+Number(c.totalVendas||0),0);
      const n=db.caixas.filter(c=>c.status==='ABERTO').length;
      return respond({totalVendas:t,caixasAbertos:n,totalMovimentacoesVenda:12,ticketMedio: +(t/12).toFixed(2)});
    }
    if(path==='/api/rentabilidade/ranking')return respond({itens:[]});
    if(path==='/api/fornecedores'||path==='/api/fornecedores/ativos')return respond([]);
    if(path==='/api/notas'||path==='/api/cotacoes'||path==='/api/funcionarios'||path==='/api/logs')return respond([]);
    // Never pretend that unsupported workflows succeeded.
    return respond({erro:'Função indisponível nesta demonstração. Use o ambiente real para concluir esta operação.'},501);
  }
  window.fetch=function(input,init){return handle(input,init);};
  const banner=document.createElement('div');
  banner.setAttribute('role','status');
  banner.style.cssText='position:fixed;right:12px;bottom:12px;z-index:2147483647;padding:10px 14px;border-radius:9px;background:#182c22;color:#d7ffe0;border:1px solid #58b68a;font:600 12px system-ui;box-shadow:0 6px 20px #0004;pointer-events:none';
  banner.textContent='DEMONSTRAÇÃO · dados fictícios';
  document.addEventListener('DOMContentLoaded',()=>document.body.appendChild(banner));
  window.StockOsPreview={reset:()=>{localStorage.removeItem(KEY);location.reload();},mode:'mock'};
})();
