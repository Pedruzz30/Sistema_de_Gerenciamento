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
    return {
      produtos,
      fornecedores: [
        {id:1,nome:'Distribuidora Modelo',cnpj:'11222333000181',telefone:'(21) 99999-0001',ativo:true,produtos:[produtos[5],produtos[6]]},
        {id:2,nome:'Hortifruti Exemplo',cnpj:'11444777000161',telefone:'(21) 99999-0002',ativo:true,produtos:[produtos[18],produtos[19]]},
        {id:3,nome:'Atacado Demonstração',cnpj:'27865757000102',telefone:'(21) 99999-0003',ativo:true,produtos:[produtos[0],produtos[1]]}
      ],
      funcionarios: [
        {ru:1,nome:'Admin',sobrenome:'Demo',nomeClasse:'SUPERIOR',perfil:'ADMIN',ativo:true,permissoes:['VER_ESTOQUE','EDITAR_ESTOQUE','VER_VENDAS','VER_FINANCAS','VER_COMPRAS']},
        {ru:2,nome:'Julia',sobrenome:'Exemplo',nomeClasse:'CAIXA',perfil:'OPERADOR',ativo:true,permissoes:['VER_VENDAS','VER_ESTOQUE']},
        {ru:3,nome:'Mateus',sobrenome:'Teste',nomeClasse:'ESTOQUISTA',perfil:'OPERADOR',ativo:true,permissoes:['VER_ESTOQUE','EDITAR_ESTOQUE']}
      ],
      notas: [{id:1,numero:'DEMO-001',fornecedorId:1,fornecedorNome:'Distribuidora Modelo',status:'CONFIRMADA',dataEmissao:stamp(10,0),total:189.90,itens:[{id:1,produtoId:6,produtoNome:produtos[5].nome,quantidade:15,precoUnitario:12.66,subtotal:189.90}]}],
      logs: [{id:1,usuarioRu:1,acao:'PREVIEW_INICIADO',descricao:'Ambiente fictício carregado para demonstração.',dataHora:stamp(8,0)}],
      movimentacoesCaixa: [
        {id:1,caixaId:1,numeroCaixa:1,tipo:'VENDA',valor:168.50,descricao:'Vendas de exemplo',dataHora:stamp(11,10),operador:'Operador Demo'},
        {id:2,caixaId:1,numeroCaixa:1,tipo:'VENDA',valor:100,descricao:'Venda fictícia',dataHora:stamp(12,15),operador:'Operador Demo'}
      ],
      movimentacoes:[
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
  if (!db || !Array.isArray(db.produtos) || !Array.isArray(db.fornecedores) || !Array.isArray(db.movimentacoesCaixa)) db=initial();
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
    if(path==='/api/rentabilidade/ranking')return respond([]);
    if(path==='/api/fornecedores' && method==='GET')return respond(db.fornecedores);
    if(path==='/api/fornecedores/ativos' && method==='GET')return respond(db.fornecedores.filter(f=>f.ativo));
    if(path==='/api/fornecedores' && method==='POST'){
      const nome=String(body.nome||'').trim(),cnpj=String(body.cnpj||'').replace(/\D/g,'');
      if(!nome||!cnpj)return respond({erro:'Informe nome e CNPJ.'},400);
      if(db.fornecedores.some(f=>f.cnpj===cnpj))return respond({erro:'CNPJ já cadastrado.'},409);
      const id=Math.max(0,...db.fornecedores.map(f=>f.id))+1;
      const f={id,nome,cnpj,telefone:String(body.telefone||''),ativo:true,produtos:[]};
      db.fornecedores.push(f);save();return respond(f);
    }
    const supplierAction=path.match(/^\/api\/fornecedores\/(\d+)\/(desativar|reativar|vincular-produto)$/);
    if(supplierAction && (method==='PUT'||method==='POST')){
      const f=db.fornecedores.find(v=>v.id===Number(supplierAction[1]));
      if(!f)return respond({erro:'Fornecedor não encontrado.'},404);
      if(supplierAction[2]==='desativar')f.ativo=false;
      if(supplierAction[2]==='reativar')f.ativo=true;
      if(supplierAction[2]==='vincular-produto'){
        const id=Number(body.produtoId);
        const p=db.produtos.find(v=>v.id===id);
        if(!p)return respond({erro:'Produto não encontrado.'},404);
        if(!f.produtos.some(v=>v.id===id))f.produtos.push(p);
      }
      save();return respond(f);
    }
    if(path==='/api/funcionarios' && method==='GET')return respond(db.funcionarios);
    if(path==='/api/logs' && method==='GET')return respond(db.logs);
    if(path==='/api/notas' && method==='GET')return respond(db.notas);
    if(path==='/api/cardapio/admin/categorias')return respond([
      {id:1,codigo:'PRATOS',nomeExibicao:'Pratos',ordemExibicao:1,ativo:true},
      {id:2,codigo:'BEBIDAS',nomeExibicao:'Bebidas',ordemExibicao:2,ativo:true}
    ]);
    if(path==='/api/cardapio/admin/itens')return respond([]);
    if(path==='/api/cardapio/grupos-modificadores')return respond([]);
    if(path==='/api/unidades')return respond([{id:1,codigo:'UN',nome:'Unidade',sigla:'un'}]);
    if(path==='/api/cardapio/disponibilidade')return respond({itens:[],variacoes:[]});
    if(path==='/api/caixas/consolidado')return respond({data:today(),resumo:'Relatório de demonstração — valores fictícios.'});
    if(path==='/api/caixas/abrir' && method==='POST'){
      const numero=Number(body.numeroCaixa);
      if(!Number.isInteger(numero)||numero<=0)return respond({erro:'Número do caixa inválido.'},400);
      if(db.caixas.some(c=>c.numeroCaixa===numero&&c.status==='ABERTO'))return respond({erro:'Caixa já aberto.'},409);
      const id=Math.max(0,...db.caixas.map(c=>c.id))+1;
      const c={id,caixaId:id,numeroCaixa:numero,status:'ABERTO',totalVendas:0,totalEntradas:0,totalSaidas:0,saldoInicial:Number(body.saldoInicial)||0,saldoAtual:Number(body.saldoInicial)||0,nomeOperador:'Admin Demo',dataAbertura:new Date().toISOString()};
      db.caixas.push(c);save();return respond(c);
    }
    if(path==='/api/caixas/encerrar' && method==='POST'){
      const c=db.caixas.find(v=>v.numeroCaixa===Number(body.numeroCaixa)&&v.status==='ABERTO');
      if(!c)return respond({erro:'Caixa não encontrado ou já encerrado.'},404);
      c.status='ENCERRADO';c.dataEncerramento=new Date().toISOString();save();return respond({caixaId:c.id,numeroCaixa:c.numeroCaixa,status:c.status});
    }
    const cashChange=path.match(/^\/api\/caixas\/(sangria|suprimento|venda)$/);
    if(cashChange && method==='POST'){
      const c=db.caixas.find(v=>v.numeroCaixa===Number(body.numeroCaixa)&&v.status==='ABERTO');
      const valor=Number(body.valor),tipo=cashChange[1];
      if(!c||!Number.isFinite(valor)||valor<=0)return respond({erro:'Caixa ou valor inválido.'},400);
      if(tipo==='sangria'&&c.saldoAtual<valor)return respond({erro:'Saldo insuficiente.'},400);
      const direcao=tipo==='sangria'?-1:1;
      c.saldoAtual=+(c.saldoAtual+valor*direcao).toFixed(2);
      if(tipo==='venda')c.totalVendas=+(c.totalVendas+valor).toFixed(2);
      else if(direcao>0)c.totalEntradas=+(c.totalEntradas+valor).toFixed(2);
      else c.totalSaidas=+(c.totalSaidas+valor).toFixed(2);
      db.movimentacoesCaixa.unshift({id:Date.now(),caixaId:c.id,numeroCaixa:c.numeroCaixa,tipo:tipo.toUpperCase(),valor,descricao:String(body.descricao||'Operação fictícia'),dataHora:new Date().toISOString(),operador:'Admin Demo'});
      save();return respond(c);
    }
    const cashMov=path.match(/^\/api\/caixas\/sessoes\/(\d+)\/movimentacoes$/);
    if(cashMov && method==='GET')return respond(db.movimentacoesCaixa.filter(m=>m.caixaId===Number(cashMov[1])));
    // Never pretend that unsupported workflows succeeded.
    return respond({erro:'Função indisponível nesta demonstração. Use o ambiente real para concluir esta operação.'},501);
  }
  window.fetch=function(input,init){return handle(input,init);};
  // No deploy público, nunca confundir o preview com o backend real.
  document.addEventListener('DOMContentLoaded',()=>{
    if(location.pathname.endsWith('/caixas.html')) {
      window.abrirPDV=()=>window.alert('O PDV completo é integrado ao backend. Esta prévia simula estoque e operação básica de caixa.');
    }
  });
  const banner=document.createElement('div');
  banner.setAttribute('role','status');
  banner.style.cssText='position:fixed;right:12px;bottom:12px;z-index:2147483647;padding:10px 14px;border-radius:9px;background:#182c22;color:#d7ffe0;border:1px solid #58b68a;font:600 12px system-ui;box-shadow:0 6px 20px #0004;pointer-events:none';
  banner.textContent='DEMONSTRAÇÃO · dados fictícios';
  document.addEventListener('DOMContentLoaded',()=>document.body.appendChild(banner));
  window.StockOsPreview={reset:()=>{localStorage.removeItem(KEY);location.reload();},mode:'mock'};
})();
