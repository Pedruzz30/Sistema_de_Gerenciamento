/* StockOS volume scenario. Loaded ONLY by the isolated static preview build.
 * Synthetic data, deterministic prices, no backend requests or real payments. */
(function () {
  'use strict';
  const prices = [25, 28, 30, 32, 35, 38, 40, 45, 55, 72];
  const menuNames = [
    'Sanduíche da casa', 'Burger clássico', 'Prato do dia',
    'Executivo com bebida', 'Porção especial', 'Burger artesanal',
    'Pizza individual', 'Combo duplo', 'Jantar da casa', 'Combo família'
  ];
  const catalog = [
    ['Refrigerante artesanal', 'BEBIDAS', 8], ['Água de coco', 'BEBIDAS', 7],
    ['Suco natural', 'BEBIDAS', 12], ['Chá gelado', 'BEBIDAS', 9],
    ['Água tônica', 'BEBIDAS', 8], ['Energético', 'BEBIDAS', 13],
    ['Farinha especial', 'SECO', 15], ['Arroz parboilizado', 'SECO', 12],
    ['Feijão carioca', 'SECO', 10], ['Molho da casa', 'SECO', 15],
    ['Macarrão', 'SECO', 11], ['Azeite culinário', 'SECO', 26],
    ['Queijo prato', 'FRIO', 27], ['Queijo minas', 'FRIO', 25],
    ['Manteiga', 'FRIO', 18], ['Iogurte natural', 'FRIO', 12],
    ['Creme de leite', 'FRIO', 9], ['Polpa de fruta', 'CONGELADO', 14],
    ['Frango temperado', 'CONGELADO', 24], ['Batata palito', 'CONGELADO', 20],
    ['Carne para preparo', 'CONGELADO', 38]
  ];
  const sizes = {
    BEBIDAS: ['269ml','350ml','500ml','600ml','1L','2L'],
    SECO: ['250g','500g','1kg','2kg','5kg','10kg'],
    FRIO: ['200g','500g','1kg','2kg','3kg','5kg'],
    CONGELADO: ['200g','400g','1kg','2kg','3kg','5kg']
  };
  const suppliers = [
    'Distribuidora Atlântico','Hortifruti Central','Atacado Vista Sul',
    'Frigorífico Modelo','Laticínios Serra','Bebidas Horizonte',
    'Grãos e Cia','Congelados Ponto Frio','Embalagens Costa',
    'Temperos da Vila','Distribuição Norte','Bebidas Estação',
    'Atacado do Porto','Frios Santa Luz','Hortifruti Colheita',
    'Alimentos Monte Verde','Granel Logística','Distribuidora Modelo 18'
  ];
  const people = [
    'Marina Lopes','Lucas Ferreira','Ana Oliveira','Bruno Costa','Camila Martins',
    'Rafael Souza','Beatriz Lima','Felipe Alves','Julia Mendes','Thiago Rocha',
    'Larissa Pereira','Diego Silva','Natália Barbosa','Victor Nunes','Carolina Gomes',
    'Henrique Castro','Gabriela Santos','André Ribeiro','Patrícia Araújo',
    'Renato Moreira','Isabela Carvalho'
  ];
  const money = n => Math.round(n * 100) / 100;
  const dayString = d => [
    d.getFullYear(), String(d.getMonth() + 1).padStart(2, '0'),
    String(d.getDate()).padStart(2, '0')
  ].join('-');
  const stamp = (d, hour, minute) => dayString(d) + 'T' +
    String(hour).padStart(2, '0') + ':' + String(minute).padStart(2, '0') + ':00';
  const stockLevel = (qty, min) =>
    qty <= 0 ? 'SEM_ESTOQUE' : qty <= min * .25 ? 'CRITICO' :
    qty <= min * .5 ? 'BAIXO' : qty <= min * .75 ? 'MODERADO' : 'ADEQUADO';
  function allocation(days, total) {
    const factors = [1.2, .70, .83, .9, 1.04, 1.20, 1.35];
    const weights = days.map(d => factors[d.getDay()]);
    const sum = weights.reduce((a,b) => a+b, 0);
    const precise = weights.map(w => total*w/sum);
    const counts = precise.map(Math.floor);
    let remainder = total - counts.reduce((a,b) => a+b, 0);
    const order = precise.map((v,i) => [i,v-counts[i]])
      .sort((a,b) => b[1]-a[1] || a[0]-b[0]);
    for(let i=0; i<remainder; i++) counts[order[i][0]]++;
    return counts;
  }
  function create(base) {
    const db = JSON.parse(JSON.stringify(base));
    const now = new Date();
    let seed = 27041995;
    function random() {
      seed = (Math.imul(seed, 1664525) + 1013904223) >>> 0;
      return seed / 4294967296;
    }
    const menu = prices.map((price,i) => ({
      id: 25+i, nome: menuNames[i], categoriaEstoque:'PREPARADOS',
      quantidadeAtual: 95+i*3, quantidadeMinima: 15, precoUnitario:price,
      precoCusto:money(price*.54), nivelEstoque:'ADEQUADO',
      controladoPorEstoque:true, ativo:true, unidadeBaseCodigo:'UN',
      politicaFalta:'BLOQUEAR'
    }));
    db.produtos.push(...menu);
    for (const [name, category, basePrice] of catalog) {
      for (let i=0; i<6; i++) {
        const id = db.produtos.length+1;
        const minimo = 10+(id%5)*5;
        const quantidade = id%19===0 ? 0 :
          id%13===0 ? Math.ceil(minimo*.2) :
          id%11===0 ? Math.ceil(minimo*.5) : 35+(id*17)%170;
        const price = money(basePrice*(.75+i*.20));
        db.produtos.push({
          id, nome: name+' '+sizes[category][i], categoriaEstoque:category,
          quantidadeAtual:quantidade, quantidadeMinima:minimo, precoUnitario:price,
          precoCusto:money(price*.62), nivelEstoque:stockLevel(quantidade,minimo),
          controladoPorEstoque:true, ativo:true, unidadeBaseCodigo:'UN',
          politicaFalta:'BLOQUEAR'
        });
      }
    }
    db.fornecedores = suppliers.map((name,i)=>({
      id:i+1, nome:'DEMO · '+name, cnpj:String(99000000000000+i+1),
      telefone:'(21) 99999-'+String(i+1).padStart(4,'0'), ativo:true,
      produtos: Array.from({length:5},(_,j)=>db.produtos[24+(i*7+j*11)%136])
    }));
    db.funcionarios = [
      {ru:1,nome:'Admin',sobrenome:'Demo',nomeClasse:'SUPERIOR',
       perfil:'ADMIN',ativo:true,permissoes:['VER_ESTOQUE','EDITAR_ESTOQUE',
       'VER_COMPRAS','VER_VENDAS','VER_FINANCAS','GERENCIAR_FUNCIONARIOS','VER_LOGS']},
      ...people.map((full,i)=>{
        const parts=full.split(' ');
        const perfil=i<2?'GERENTE':i<10?'CAIXA':'ESTOQUISTA';
        return {ru:i+2,nome:parts[0],sobrenome:parts[1],nomeClasse:perfil,
          perfil:i<2?'GERENTE':'OPERADOR',ativo:true,
          permissoes:i<2?['VER_ESTOQUE','EDITAR_ESTOQUE','VER_VENDAS',
            'VER_FINANCAS','VER_COMPRAS']:i<10?['VER_VENDAS','VER_ESTOQUE']:
            ['VER_ESTOQUE','EDITAR_ESTOQUE']};
      })
    ];
    // Thirty completed business days ending yesterday. The current partial day
    // is separate, so a morning visit never displays sales from the future.
    const days=Array.from({length:30},(_,i)=>
      new Date(now.getFullYear(),now.getMonth(),now.getDate()-30+i));
    const counts=allocation(days,7500);
    db.vendasPreview=[];
    const totalsByProduct=prices.map(()=>({qtd:0,receita:0}));
    const methods={PIX:0,CARTAO:0,DINHEIRO:0};
    const daysSummary=[];
    let ticketIndex=0;
    function addSale(day,hour,minute,priceIndex,live) {
      const price=prices[priceIndex];
      const payment= random()<.48?'PIX':random()<.71?'CARTAO':'DINHEIRO';
      const box=1+Math.floor(random()*3);
      const item=menu[priceIndex];
      const sale={
        id:db.vendasPreview.length+1, numeroCaixa:box, valor:price,
        criadaEm:stamp(day,hour,minute), formaPagamento:payment,
        itens:[{idProduto:item.id,quantidade:1}], demonstracao:true,
        historico:!live
      };
      db.vendasPreview.push(sale);
      if(!live) {
        totalsByProduct[priceIndex].qtd++;
        totalsByProduct[priceIndex].receita+=price;
        methods[payment]++;
      }
      return sale;
    }
    for(let d=0; d<days.length; d++) {
      let total=0;
      for(let n=0; n<counts[d]; n++) {
        const priceIndex=ticketIndex++%prices.length;
        const hour=9+Math.floor(random()*14);
        const minute=Math.floor(random()*60);
        total+=prices[priceIndex];
        addSale(days[d],hour,minute,priceIndex,false);
      }
      daysSummary.push({data:dayString(days[d]),pedidos:counts[d],faturamento:total});
    }
    const todayCount=Math.max(0,Math.min(230,
      Math.floor((now.getHours()+now.getMinutes()/60-9)*19)));
    const todaySales=[];
    for(let i=0; i<todayCount; i++) {
      const availableMinutes=Math.max(1,(now.getHours()-9)*60+now.getMinutes());
      const minuteOfDay=9*60+Math.floor(random()*availableMinutes);
      todaySales.push(addSale(now,Math.floor(minuteOfDay/60),minuteOfDay%60,
        ticketIndex++%prices.length,true));
    }
    const todayTotal=todaySales.reduce((s,v)=>s+v.valor,0);
    const historicalTotal=daysSummary.reduce((s,v)=>s+v.faturamento,0);
    if(historicalTotal!==300000||ticketIndex!==7500+todayCount)
      throw new Error('Cenário de demonstração inconsistente');
    const dailyBoxTotal=[0,0,0];
    const dailyBoxCash=[0,0,0];
    const formaCaixa=pagamento=>pagamento==='CARTAO'?'DEBITO':pagamento;
    for(const sale of todaySales){
      dailyBoxTotal[sale.numeroCaixa-1]+=sale.valor;
      if(sale.formaPagamento==='DINHEIRO')dailyBoxCash[sale.numeroCaixa-1]+=sale.valor;
    }
    // Gaveta = fundo + só o que foi pago em dinheiro (Pix e cartão não passam por ela).
    db.caixas=dailyBoxTotal.map((value,i)=>({
      id:i+1, caixaId:i+1, numeroCaixa:i+1, status:'ABERTO',
      totalVendas:value, totalEntradas:value, totalSaidas:0,
      saldoInicial:300, saldoAtual:dailyBoxCash[i]+300, nomeOperador:people[i],
      dataAbertura:stamp(now,9,0)
    }));
    db.movimentacoesCaixa=todaySales.map((sale,i)=>({
      id:i+1,caixaId:sale.numeroCaixa,numeroCaixa:sale.numeroCaixa,
      tipo:'VENDA',valor:sale.valor,descricao:'Pedido demo #'+sale.id,
      dataHora:sale.criadaEm,operador:people[sale.numeroCaixa-1],
      formaPagamento:formaCaixa(sale.formaPagamento),vendaId:sale.id
    })).sort((a,b)=>b.dataHora.localeCompare(a.dataHora));
    db.notas=Array.from({length:120},(_,i)=>{
      const issued=days[i%days.length];
      const fornecedor=db.fornecedores[i%suppliers.length];
      const itens=Array.from({length:4},(_,j)=>{
        const produto=db.produtos[34+(i*5+j*13)%126];
        const quantidade=6+(i+j*7)%32;
        const price=produto.precoCusto;
        return {id:j+1,produtoId:produto.id,produtoNome:produto.nome,
          quantidade,precoUnitario:price,subtotal:money(quantidade*price)};
      });
      return {id:i+1,numero:'DEMO-NF-'+String(i+1).padStart(4,'0'),
        fornecedorId:fornecedor.id,fornecedorNome:fornecedor.nome,
        status:i%13===0?'PENDENTE':'CONFIRMADA',
        dataEmissao:stamp(issued,8+i%10,i%60),
        total:money(itens.reduce((s,v)=>s+v.subtotal,0)),itens};
    }).reverse();
    db.movimentacoes=Array.from({length:1500},(_,i)=>{
      const date=days[Math.floor(i/50)];
      const produto=db.produtos[34+(i*23)%126];
      const tipo=i%4===0?'ENTRADA':'SAIDA';
      return {id:i+1,tipo,produto:produto.nome,quantidade:1+(i%24),
        dataHora:stamp(date,8+i%15,i%60)};
    }).reverse();
    db.logs=Array.from({length:300},(_,i)=>({
      id:i+1, usuarioRu:1+(i%22),
      acao:['VENDA_REGISTRADA','ESTOQUE_CONFERIDO','NOTA_CONFIRMADA','CAIXA_ABERTO'][i%4],
      descricao:'Registro fictício de operação · Estação 30',
      dataHora:stamp(days[Math.floor(i/10)],9+i%13,i%60)
    })).reverse();
    const items=menu.map((p,i)=>{
      const qtd=totalsByProduct[i].qtd, receita=totalsByProduct[i].receita;
      const gross=money(qtd*(p.precoUnitario-p.precoCusto));
      return {produtoId:p.id,nome:p.nome,precoVenda:p.precoUnitario,
        precoCusto:p.precoCusto,lucroUnitario:money(p.precoUnitario-p.precoCusto),
        margemPercentual:money(100*(p.precoUnitario-p.precoCusto)/p.precoUnitario),
        margemValida:true,unidadesVendidas:qtd,receitaEstimada:receita,
        lucroTotalEstimado:gross,statusLabel:'Estimativa fictícia'};
    }).sort((a,b)=>b.lucroTotalEstimado-a.lucroTotalEstimado);
    items.forEach((item,i)=>{item.posicao=i+1;});
    db.rentabilidadePreview={
      itens:items, margemMediaPercentual:money(
        items.reduce((s,v)=>s+v.margemPercentual,0)/items.length),
      lucroTotalEstimado:money(items.reduce((s,v)=>s+v.lucroTotalEstimado,0)),
      receitaTotalEstimada:historicalTotal
    };
    db.demoEscala={
      nome:'Estação 30 — Restaurante & Bar',
      inicio:dayString(days[0]),fim:dayString(days[29]),
      faturamento:historicalTotal,pedidos:7500,ticketMedio:40,
      produtos:db.produtos.length,fornecedores:db.fornecedores.length,
      funcionarios:db.funcionarios.length,notas:db.notas.length,
      vendasHoje:todayTotal,pedidosHoje:todaySales.length,
      formasPagamento:methods,dias:daysSummary
    };
    return db;
  }
  window.StockOSCreateScaleDemo=create;
})();