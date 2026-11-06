/* StockOS Preview - isolated browser-only data. Never bundled in the real Spring application. */
(function () {
  'use strict';
  const MODE_KEY = 'stockos-preview-scenario-v1';
  const search = new URL(location.href).searchParams;
  const requested = search.get('scenario');
  if ((requested === 'scale' || requested === 'basic') && typeof window.StockOSCreateScaleDemo === 'function') {
    try { localStorage.setItem(MODE_KEY, requested); } catch (_) {}
  }
  let mode = 'basic';
  if (typeof window.StockOSCreateScaleDemo === 'function') {
    try { mode = localStorage.getItem(MODE_KEY) === 'basic' ? 'basic' : 'scale'; }
    catch (_) { mode = 'scale'; }
  }
  const KEY = mode === 'scale' ? 'stockos-preview-scale-v1' : 'stockos-preview-v1';
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
  if (!db || !Array.isArray(db.produtos) || !Array.isArray(db.fornecedores) || !Array.isArray(db.movimentacoesCaixa) ||
      (mode === 'scale' && (!db.demoEscala || db.demoEscala.fim !== (() => {
        const d = new Date(); d.setDate(d.getDate()-1);
        return [d.getFullYear(),String(d.getMonth()+1).padStart(2,'0'),String(d.getDate()).padStart(2,'0')].join('-');
      })()))) {
    db = mode === 'scale' ? window.StockOSCreateScaleDemo(initial()) : initial();
  }
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
    // Demonstração do cadastro rápido. O sistema real cria variações que baixam
    // o mesmo estoque; aqui o fardo vira um produto fictício separado.
    const codigoLido=path.match(/^\/api\/pdv\/codigos\/([^/]+)$/);
    if(codigoLido && method==='GET'){
      const codigo=decodeURIComponent(codigoLido[1]);
      const dono=db.produtos.find(p=>p.codigoBarras===codigo);
      if(!dono)return respond({erro:'Produto não cadastrado para o código '+codigo+'.'},404);
      return respond({codigo,variacao:{variacaoId:dono.id,variacaoNome:'Unidade',itemNome:dono.nome,precoVenda:dono.precoUnitario},alternativas:[]});
    }
    if(path==='/api/produtos/revenda' && method==='POST'){
      const nome=String(body.nome||'').trim().slice(0,120);
      const precoVenda=Number(body.precoVenda),precoGelado=body.precoGelado==null?null:Number(body.precoGelado);
      const custo=Number(body.custoUnitario);
      if(!nome)return respond({erro:'Nome do produto é obrigatório.'},400);
      if(!(precoVenda>0)||!(custo>0)||(precoGelado!==null&&!(precoGelado>0)))
        return respond({erro:'Informe preço e custo maiores que zero.'},400);
      if(db.produtos.some(p=>String(p.nome).toLowerCase()===nome.toLowerCase()))
        return respond({erro:'Já existe um produto chamado '+nome+'.'},409);
      const codigos=[body.codigoBarras,body.fardo&&body.fardo.codigoBarras].map(c=>String(c||'')).filter(Boolean);
      const usado=codigos.find(c=>db.produtos.some(p=>p.codigoBarras===c));
      if(usado)return respond({erro:'O código '+usado+' já está cadastrado.'},409);
      if(new Set(codigos).size!==codigos.length)return respond({erro:'A unidade e o fardo precisam ter códigos diferentes.'},400);
      const novoProduto=(nomeProduto,quantidade,preco,custoProduto,codigo)=>{
        const id=Math.max(0,...db.produtos.map(p=>p.id))+1;
        const minima=Math.max(0,Number(body.quantidadeMinima)||0);
        const produto={id,nome:nomeProduto,quantidadeAtual:quantidade,quantidadeMinima:minima,precoUnitario:preco,precoCusto:custoProduto,
          ativo:true,controladoPorEstoque:true,categoriaEstoque:body.categoriaEstoque||'BEBIDAS',
          nivelEstoque:level(quantidade,minima),codigoBarras:codigo||null};
        db.produtos.push(produto);
        return produto;
      };
      const quantidade=Math.max(0,Math.trunc(Number(body.quantidadeInicial)||0));
      const unidade=novoProduto(nome,quantidade,precoGelado??precoVenda,custo,String(body.codigoBarras||''));
      const variacoes=precoGelado!==null
        ?[{id:unidade.id,nome:'Gelada',precoVenda:precoGelado,padrao:true},{id:unidade.id,nome:'Natural',precoVenda,padrao:false}]
        :[{id:unidade.id,nome:'Unidade',precoVenda,padrao:true}];
      if(body.fardo){
        const n=Number(body.fardo.quantidadeUnidades),precoFardo=Number(body.fardo.preco);
        if(!Number.isInteger(n)||n<2||!(precoFardo>0)){
          db.produtos=db.produtos.filter(p=>p.id!==unidade.id);
          return respond({erro:'Fardo inválido.'},400);
        }
        const fardo=novoProduto(nome+' · Fardo '+n,Math.floor(quantidade/n),precoFardo,+(custo*n).toFixed(2),String(body.fardo.codigoBarras||''));
        variacoes.push({id:fardo.id,nome:'Fardo '+n,precoVenda:precoFardo,padrao:false});
      }
      save();
      return respond({produtoId:unidade.id,nome,quantidadeAtual:quantidade,itemCardapioId:unidade.id,variacoes,codigos});
    }
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
    // Static demo only: validate the entire cart before applying one coherent
    // in-memory snapshot. This is NOT a backend payment/sales transaction.
    if(path==='/api/preview/transacoes/venda' && method==='POST'){
      const key=String(body.idempotencyKey||'');
      const numero=Number(body.numeroCaixa);
      const itens=body.itens;
      if(!/^[a-zA-Z0-9_-]{8,100}$/.test(key))return respond({erro:'Identificador da venda inválido.'},400);
      if(!Number.isInteger(numero)||numero<=0||!Array.isArray(itens)||!itens.length||itens.length>100)
        return respond({erro:'Carrinho ou caixa inválido.'},400);
      const grouped=new Map();
      for(const item of itens){
        const id=Number(item.idProduto),quantity=Number(item.quantidade);
        if(!Number.isInteger(id)||id<=0||!Number.isInteger(quantity)||quantity<=0)
          return respond({erro:'Produto ou quantidade inválidos.'},400);
        grouped.set(id,(grouped.get(id)||0)+quantity);
      }
      const pedido=[...grouped].sort((a,b)=>a[0]-b[0]).map(([idProduto,quantidade])=>({idProduto,quantidade}));
      const fingerprint=JSON.stringify({numeroCaixa:numero,itens:pedido});
      const prior=Array.isArray(db.vendasPreview)?db.vendasPreview.find(v=>v.idempotencyKey===key):null;
      if(prior)return prior.fingerprint===fingerprint
        ? respond(prior,200) : respond({erro:'Identificador já usado em outra venda.'},409);
      const caixa=db.caixas.find(c=>c.numeroCaixa===numero && c.status==='ABERTO');
      if(!caixa)return respond({erro:'Abra o caixa antes de registrar a venda fictícia.'},409);
      let totalCentavos=0;
      for(const item of pedido){
        const p=db.produtos.find(x=>x.id===item.idProduto);
        if(!p||p.ativo===false||!Number.isInteger(p.quantidadeAtual)||p.quantidadeAtual<item.quantidade)
          return respond({erro:'Estoque insuficiente ou produto indisponível.'},409);
        const preco=Number(p.precoUnitario);
        if(!Number.isFinite(preco)||preco<0)return respond({erro:'Preço de produto inválido.'},409);
        totalCentavos+=Math.round(preco*100)*item.quantidade;
      }
      if(!Number.isSafeInteger(totalCentavos)||totalCentavos<=0)
        return respond({erro:'Total da venda inválido.'},400);
      // Formas de pagamento (sem informar = dinheiro, como antes).
      const FORMAS_OK=['DINHEIRO','PIX','DEBITO','CREDITO','VOUCHER'];
      const pagamentosInformados=Array.isArray(body.pagamentos)&&body.pagamentos.length
        ? body.pagamentos : [{forma:'DINHEIRO',valor:totalCentavos/100}];
      let somaPagCentavos=0;
      const pagamentosVenda=[];
      for(const pg of pagamentosInformados){
        const formaPg=String(pg&&pg.forma||'').toUpperCase();
        const valorPg=Math.round(Number(pg&&pg.valor)*100);
        if(!FORMAS_OK.includes(formaPg)||!Number.isSafeInteger(valorPg)||valorPg<=0)
          return respond({erro:'Pagamento inválido.'},400);
        const recebidoPg=pg.valorRecebido==null?null:Math.round(Number(pg.valorRecebido)*100);
        if(recebidoPg!=null&&(recebidoPg<valorPg||(recebidoPg>valorPg&&formaPg!=='DINHEIRO')))
          return respond({erro:'Troco só é permitido em dinheiro e o recebido não pode ser menor que o valor.'},400);
        somaPagCentavos+=valorPg;
        pagamentosVenda.push({forma:formaPg,valor:valorPg/100,valorRecebido:recebidoPg==null?null:recebidoPg/100});
      }
      if(somaPagCentavos!==totalCentavos)
        return respond({erro:'A soma dos pagamentos precisa ser igual ao total da venda.'},400);
      const next=JSON.parse(JSON.stringify(db));
      const selected=next.caixas.find(c=>c.numeroCaixa===numero&&c.status==='ABERTO');
      const instant=new Date().toISOString();
      const lastMovement=Math.max(0,...next.movimentacoes.map(x=>Number(x.id)||0));
      const lastCash=Math.max(0,...next.movimentacoesCaixa.map(x=>Number(x.id)||0));
      let index=0;
      for(const item of pedido){
        const p=next.produtos.find(x=>x.id===item.idProduto);
        const before=p.quantidadeAtual;
        p.quantidadeAtual-=item.quantidade;
        p.nivelEstoque=level(p.quantidadeAtual,p.quantidadeMinima);
        next.movimentacoes.unshift({
          id:lastMovement+(++index),tipo:'SAIDA',produto:p.nome,quantidade:item.quantidade,
          dataHora:instant,saldoAnterior:before,saldoPosterior:p.quantidadeAtual
        });
      }
      const valor=totalCentavos/100;
      if(!Array.isArray(next.vendasPreview))next.vendasPreview=[];
      const saleId=next.vendasPreview.length+1;
      selected.totalVendas=+(Number(selected.totalVendas||0)+valor).toFixed(2);
      let movIndex=0;
      for(const pg of pagamentosVenda){
        // Pix e cartão não passam pela gaveta: só o dinheiro muda o saldo.
        if(pg.forma==='DINHEIRO')selected.saldoAtual=+(Number(selected.saldoAtual||0)+pg.valor).toFixed(2);
        next.movimentacoesCaixa.unshift({
          id:lastCash+(++movIndex),caixaId:selected.id,numeroCaixa:numero,tipo:'VENDA',
          valor:pg.valor,descricao:'Venda fictícia do PDV #'+saleId,dataHora:instant,operador:'Admin Demo',
          formaPagamento:pg.forma,vendaId:saleId
        });
      }
      const sale={
        id:saleId,idempotencyKey:key,fingerprint,numeroCaixa:numero,
        itens:pedido,valor,criadaEm:instant,demonstracao:true,
        formaPagamento:pagamentosVenda.length===1?pagamentosVenda[0].forma:'MISTO',pagamentos:pagamentosVenda
      };
      next.vendasPreview.push(sale);
      db=next;
      save();
      return respond(sale,201);
    }
    if(path==='/api/caixas' && method==='GET')return respond(db.caixas.map(enriquecerCaixa));
    if(path==='/api/caixas/metricas/hoje'){
      const t=db.caixas.reduce((a,c)=>a+Number(c.totalVendas||0),0);
      const n=db.caixas.filter(c=>c.status==='ABERTO').length;
      const count=db.demoEscala
        ? db.movimentacoesCaixa.filter(m=>m.tipo==='VENDA'&&String(m.dataHora).startsWith(today())).length
        : 12;
      return respond({totalVendas:Math.round(t*100)/100,caixasAbertos:n,
        totalMovimentacoesVenda:count,ticketMedio:count?Math.round(t/count*100)/100:0});
    }
    if(path==='/api/rentabilidade/ranking')return respond(db.rentabilidadePreview||[]);
    if(path==='/api/preview/cenario' && method==='GET')return respond(db.demoEscala||{nome:'Demonstração simples',demonstracao:true});
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
      const contado=Number(body.valorContado);
      if(!Number.isFinite(contado)||contado<0)return respond({erro:'Valor contado é obrigatório.'},400);
      const esperado=+Number(c.saldoAtual||0).toFixed(2);
      c.status='ENCERRADO';c.dataEncerramento=new Date().toISOString();save();
      return respond({caixaId:c.id,numeroCaixa:c.numeroCaixa,status:c.status,
        abertoPor:c.nomeOperador,fechadoPor:'Admin Demo',timestamp:c.dataEncerramento,
        saldoInicial:c.saldoInicial||0,totalVendas:c.totalVendas||0,totalEntradas:c.totalEntradas||0,totalSaidas:c.totalSaidas||0,
        valorSistema:esperado,valorContado:+contado.toFixed(2),divergencia:+(contado-esperado).toFixed(2),
        observacao:String(body.observacao||'')});
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
      db.movimentacoesCaixa.unshift({id:Date.now(),caixaId:c.id,numeroCaixa:c.numeroCaixa,tipo:tipo.toUpperCase(),valor,descricao:String(body.descricao||'Operação fictícia'),dataHora:new Date().toISOString(),operador:'Admin Demo',formaPagamento:tipo==='venda'?'DINHEIRO':null});
      save();return respond(c);
    }
    const cashMov=path.match(/^\/api\/caixas\/sessoes\/(\d+)\/movimentacoes$/);
    if(cashMov && method==='GET')return respond(db.movimentacoesCaixa.filter(m=>m.caixaId===Number(cashMov[1])));
    // Never pretend that unsupported workflows succeeded.
    return respond({erro:'Função indisponível nesta demonstração. Use o ambiente real para concluir esta operação.'},501);
  }
  function enriquecerCaixa(c){
    const movs=(db.movimentacoesCaixa||[]).filter(m=>m.caixaId===c.id);
    const porForma={DINHEIRO:0,PIX:0,DEBITO:0,CREDITO:0,VOUCHER:0};
    const vendas=new Set();let avulsas=0;
    for(const m of movs){
      if(m.tipo!=='VENDA'&&m.tipo!=='ESTORNO_VENDA')continue;
      const forma=m.formaPagamento||'DINHEIRO';
      porForma[forma]=+((porForma[forma]||0)+(m.tipo==='VENDA'?1:-1)*Number(m.valor||0)).toFixed(2);
      if(m.tipo==='VENDA'){if(m.vendaId)vendas.add(m.vendaId);else avulsas++;}
    }
    const semMovimento=movs.length===0&&Number(c.totalVendas||0)>0;
    if(semMovimento)porForma.DINHEIRO=Number(c.totalVendas||0);
    const quantidade=semMovimento?0:vendas.size+avulsas;
    const total=Number(c.totalVendas||0);
    return {...c,dinheiroNaGaveta:c.saldoAtual,vendasPorForma:porForma,quantidadeVendas:quantidade,
      ticketMedio:quantidade?+(total/quantidade).toFixed(2):0};
  }
  const nativeFetch = window.fetch.bind(window);
  window.fetch = function(input,init) {
    const raw = input instanceof Request ? input.url : String(input);
    const url = new URL(raw,location.href);
    const method = String(init?.method || (input instanceof Request ? input.method : 'GET')).toUpperCase();
    // The SPA pilot may fetch its own canonical HTML. No external network or
    // unsupported preview API is granted additional access.
    if (url.origin === location.origin && method === 'GET' &&
        /^\/(?:dashboard|estoque|cardapio|fornecedores|notas|caixas|cotacoes|funcionarios|logs)\.html$/.test(url.pathname)) {
      return nativeFetch(input,init);
    }
    return handle(input,init);
  };
  // No deploy público, nunca confundir o preview com o backend real.
  document.addEventListener('DOMContentLoaded',()=>{
    if(location.pathname.endsWith('/caixas.html')) {
      window.abrirPDV=()=>window.alert('O PDV completo é integrado ao backend. Esta prévia simula estoque e operação básica de caixa.');
    }
  });
  const banner=document.createElement('div');
  banner.setAttribute('role','status');
  banner.style.cssText='position:fixed;right:12px;bottom:12px;z-index:2147483647;padding:10px 12px;border-radius:9px;background:#182c22;color:#d7ffe0;border:1px solid #58b68a;font:600 12px system-ui;box-shadow:0 6px 20px #0004;display:flex;align-items:center;flex-wrap:wrap;gap:8px;max-width:min(92vw,620px)';
  if (typeof window.StockOSCreateScaleDemo === 'function') {
    const caption=document.createElement('span');
    caption.textContent= mode==='scale'
      ? 'DEMONSTRAÇÃO · Estação 30 · R$ 300 mil / 30 dias · 7.500 pedidos'
      : 'DEMONSTRAÇÃO · dados fictícios (simples)';
    banner.appendChild(caption);
    const selector=document.createElement('select');
    selector.setAttribute('aria-label','Cenário de dados da demonstração');
    selector.style.cssText='font:600 12px system-ui;border-radius:7px;border:1px solid #78ad8e;background:#102318;color:#fff;padding:6px;max-width:100%';
    for(const optionData of [['scale','Empresa R$ 300 mil'],['basic','Demonstração simples']]){
      const option=document.createElement('option');option.value=optionData[0];option.textContent=optionData[1];
      selector.appendChild(option);
    }
    selector.value=mode;
    selector.addEventListener('change',()=>{
      if(selector.value!==mode){
        try {localStorage.setItem(MODE_KEY,selector.value);}catch(_){}
        location.reload();
      }
    });
    banner.appendChild(selector);
    const reset=document.createElement('button');reset.type='button';
    reset.textContent='Reiniciar';
    reset.title='Recriar apenas os dados fictícios deste cenário';
    reset.style.cssText='font:600 12px system-ui;border-radius:7px;border:1px solid #78ad8e;background:transparent;color:#fff;padding:6px;cursor:pointer';
    reset.addEventListener('click',()=>{
      try {localStorage.removeItem(KEY);localStorage.removeItem('stockos-preview-notes-'+mode+'-v1');}
      catch (_) {}
      location.reload();
    });
    banner.appendChild(reset);
  } else {
    banner.textContent='DEMONSTRAÇÃO · dados fictícios';
  }
  function mountScaleSummary() {
    if(!db.demoEscala || !/\/dashboard(?:\.html)?\/?$/.test(location.pathname))return;
    const anchor=document.getElementById('summary-grid');
    if(!anchor || document.getElementById('stockos-volume-summary'))return;
    const data=db.demoEscala;
    const formatCurrency=n=>new Intl.NumberFormat('pt-BR',{style:'currency',currency:'BRL'}).format(n);
    const root=document.createElement('section');
    root.id='stockos-volume-summary';
    root.setAttribute('aria-label','Cenário de operação mensal fictícia');
    root.style.cssText='background:linear-gradient(135deg,#162a21,#243a2a);color:#e9f9ee;border:1px solid #41654f;border-radius:12px;padding:16px;margin:0 0 16px;min-width:0';
    const title=document.createElement('div');
    title.style.cssText='font:600 15px system-ui;margin-bottom:5px';
    title.textContent=data.nome+' · empresa fictícia';
    root.appendChild(title);
    const note=document.createElement('div');
    note.style.cssText='font:12px system-ui;color:#add2b8;margin-bottom:13px';
    note.textContent='Período concluído: '+data.inicio+' a '+data.fim+' · Histórico apenas ilustrativo';
    root.appendChild(note);
    const metrics=document.createElement('div');
    metrics.style.cssText='display:grid;grid-template-columns:repeat(auto-fit,minmax(125px,1fr));gap:8px;margin-bottom:14px';
    for(const [label,value] of [
      ['Faturamento / 30 dias',formatCurrency(data.faturamento)],
      ['Pedidos',new Intl.NumberFormat('pt-BR').format(data.pedidos)],
      ['Ticket médio',formatCurrency(data.ticketMedio)],
      ['Produtos / fornecedores',data.produtos+' / '+data.fornecedores]
    ]) {
      const card=document.createElement('div');
      card.style.cssText='background:#ffffff0c;border:1px solid #ffffff19;border-radius:8px;padding:10px;min-width:0';
      const small=document.createElement('div');
      small.style.cssText='font:11px system-ui;color:#b8d7c3;margin-bottom:5px';
      small.textContent=label;
      const strong=document.createElement('div');
      strong.style.cssText='font:700 clamp(15px,2.5vw,21px) system-ui;overflow-wrap:anywhere';
      strong.textContent=value;
      card.append(small,strong);
      metrics.appendChild(card);
    }
    root.appendChild(metrics);
    const chartTitle=document.createElement('div');
    chartTitle.textContent='Faturamento diário simulado (30 dias)';
    chartTitle.style.cssText='font:12px system-ui;margin-bottom:8px;color:#d5efdb';
    root.appendChild(chartTitle);
    const chart=document.createElement('div');
    chart.setAttribute('role','img');
    chart.setAttribute('aria-label','Faturamento diário fictício durante 30 dias, total de R$ 300 mil');
    chart.style.cssText='display:grid;grid-template-columns:repeat(30,minmax(3px,1fr));align-items:end;gap:3px;height:54px';
    const maximum=Math.max(...data.dias.map(v=>v.faturamento),1);
    for(const day of data.dias) {
      const bar=document.createElement('div');
      bar.title=day.data+' · '+formatCurrency(day.faturamento)+' · '+day.pedidos+' pedidos';
      bar.style.cssText='background:#72c993;border-radius:3px 3px 0 0;height:'+Math.max(5,day.faturamento/maximum*100)+'%;min-width:0';
      chart.appendChild(bar);
    }
    root.appendChild(chart);
    anchor.parentNode.insertBefore(root,anchor);
  }
  window.StockOSPreviewHooks=window.StockOSPreviewHooks||{};
  window.StockOSPreviewHooks.dashboard=mountScaleSummary;
  document.addEventListener('DOMContentLoaded',mountScaleSummary);
  document.addEventListener('DOMContentLoaded',()=>document.body.appendChild(banner));
  window.StockOsPreview={
    reset:()=>{try{localStorage.removeItem(KEY);}catch(_){}location.reload();},
    mode:'mock',scenario:mode, getSeedNotes:()=>JSON.parse(JSON.stringify(db.notas))
  };
})();
