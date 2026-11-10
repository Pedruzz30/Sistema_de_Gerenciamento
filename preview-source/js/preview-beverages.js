/* StockOS static preview: fictional beverage wholesaler, never connected to real databases. */
(function () {
  'use strict';
  const money = value => Math.round(value * 100) / 100;
  const day = date => [date.getFullYear(),String(date.getMonth()+1).padStart(2,'0'),String(date.getDate()).padStart(2,'0')].join('-');
  const stamp = (date,hour,minute) => day(date)+'T'+String(hour).padStart(2,'0')+':'+String(minute).padStart(2,'0')+':00';
  const level = (qty,min) => qty <= 0 ? 'SEM_ESTOQUE' : qty <= min*.25 ? 'CRITICO' : qty <= min*.5 ? 'BAIXO' : qty <= min*.75 ? 'MODERADO' : 'ADEQUADO';
  const prices = [35,50,65,80,95,115,135,160,190,235,315,525];
  const beverages = [
    ['Água mineral sem gás','BEBIDAS',2.5],['Água mineral com gás','BEBIDAS',3.5],
    ['Refrigerante cola','BEBIDAS',5],['Refrigerante guaraná','BEBIDAS',4.5],
    ['Refrigerante limão','BEBIDAS',4.5],['Refrigerante laranja','BEBIDAS',4.5],
    ['Suco de uva','BEBIDAS',7],['Suco de laranja','BEBIDAS',7],
    ['Suco de maçã','BEBIDAS',7.5],['Chá gelado','BEBIDAS',6],
    ['Isotônico','BEBIDAS',8],['Energético','BEBIDAS',11],
    ['Água de coco','BEBIDAS',6],['Tônica','BEBIDAS',6.5],
    ['Cerveja pilsen','BEBIDAS',5],['Cerveja puro malte','BEBIDAS',7.5],
    ['Cerveja sem álcool','BEBIDAS',6],['Cerveja trigo','BEBIDAS',9],
    ['Cerveja IPA','BEBIDAS',12],['Cerveja lager','BEBIDAS',6],
    ['Bebida mista pronta','BEBIDAS',11],['Vinho tinto de mesa','BEBIDAS',26],
    ['Vinho branco de mesa','BEBIDAS',27],['Espumante','BEBIDAS',38],
    ['Bebida destilada','BEBIDAS',39]
  ];
  const packs = ['lata 269ml','lata 350ml','garrafa 500ml','garrafa 600ml',
    'garrafa 1L','garrafa 2L','fardo 6 unidades','fardo 12 unidades',
    'caixa 12 unidades','caixa 24 unidades','pack 4 unidades','pack 8 unidades',
    'retornável 600ml','retornável 1L','barril 5L','garrafa 750ml'];
  const brands = ['Linha Clássica','Linha Select','Linha Econômica','Linha Premium'];
  const staff = ['Marina Gomes','André Costa','Beatriz Souza','Rafael Oliveira','Carolina Lima',
    'Lucas Alves','Júlia Martins','Thiago Castro','Natália Mendes','Felipe Rocha',
    'Camila Pereira','Diego Santos','Larissa Ribeiro','Henrique Nunes','Bruna Carvalho',
    'João Ferreira','Gabriela Monteiro','Victor Azevedo','Isabela Duarte','Renato Teixeira',
    'Bianca Pires','Rodrigo Lopes','Paula Barbosa','Leonardo Cunha','Daniela Freitas',
    'Mateus Vieira','Amanda Cardoso','Caio Moreira','Priscila Almeida','Eduardo Correia',
    'Vanessa Melo','Gustavo Reis','Helena Silveira','Samuel Batista','Luana Campos',
    'Leandro Soares','Aline Rodrigues','Miguel Pinto','Patrícia Andrade','Fernando Neves',
    'Clara Fonseca','Otávio Moura','Fernanda Matos','Igor Tavares','Renata Faria',
    'Elisa Borges','Carlos Dias','Luiza Barros','Pedro Carvalho'];
  const supplierRoots=['Distribuidora Litoral','Distribuidora Serra','Atacado Central',
    'Centro de Bebidas Modelo','Bebidas Horizonte','Logística Rio','Distribuição Sul',
    'Depósito União','Bebidas da Costa','Distribuidora Interior','Atacado Vale',
    'Depósito Comercial','Bebidas Atlântico','Bebidas do Porto','Distribuição Norte'];
  function allocate(days,total) {
    const weights=days.map(d=>[1.22,.76,.88,.97,1.13,1.43,1.65][d.getDay()]);
    const sum=weights.reduce((a,b)=>a+b,0);
    const raw=weights.map(w=>w*total/sum);
    const counts=raw.map(Math.floor);
    let left=total-counts.reduce((a,b)=>a+b,0);
    const sorted=raw.map((v,i)=>({i,f:v-counts[i]})).sort((a,b)=>b.f-a.f||a.i-b.i);
    for(let k=0;k<left;k++)counts[sorted[k].i]++;
    return counts;
  }
  function create(base) {
    const db=JSON.parse(JSON.stringify(base));
    let seed=20261104;
    const rand=()=>((seed=(Math.imul(seed,1664525)+1013904223)>>>0)/4294967296);
    const now=new Date();
    const days=Array.from({length:30},(_,i)=>new Date(now.getFullYear(),now.getMonth(),now.getDate()-30+i));
    db.produtos=[];
    // 25 families x 16 packaging types x four in-house brands = 1,600 real catalog positions.
    for(let group=0;group<beverages.length;group++)for(let pack=0;pack<packs.length;pack++)for(let brand=0;brand<brands.length;brand++){
      const id=db.produtos.length+1;
      const [label,category,unitPrice]=beverages[group];
      const min=24+(id%8)*12;
      const qty=id%37===0?0:id%29===0?Math.ceil(min*.2):id%23===0?Math.ceil(min*.45):160+(id*37)%1500;
      const factor=pack>=6?5+(pack%5)*2:pack>=4?2.4:1;
      const price=money(unitPrice*factor*(1+brand*.13));
      db.produtos.push({
        id,nome:label+' · '+packs[pack]+' · '+brands[brand],
        categoriaEstoque:category,quantidadeAtual:qty,quantidadeMinima:min,
        precoUnitario:price,precoCusto:money(price*.70),
        nivelEstoque:level(qty,min),controladoPorEstoque:true,ativo:true,
        unidadeBaseCodigo:'UN',politicaFalta:'BLOQUEAR'
      });
    }
    db.fornecedores=Array.from({length:65},(_,i)=>({
      id:i+1,nome:'DEMO · '+supplierRoots[i%supplierRoots.length]+' '+(1+Math.floor(i/supplierRoots.length)),
      cnpj:String(99000000000000+i+1),telefone:'(21) 99999-'+String(i+1).padStart(4,'0'),
      ativo:i%19!==0,produtos:Array.from({length:6},(_,j)=>db.produtos[(i*41+j*13)%db.produtos.length])
    }));
    db.funcionarios=[
      {ru:1,nome:'Admin',sobrenome:'Demo',nomeClasse:'SUPERIOR',perfil:'ADMIN',ativo:true,
        permissoes:['VER_ESTOQUE','EDITAR_ESTOQUE','VER_COMPRAS','VER_VENDAS','VER_FINANCAS','GERENCIAR_FUNCIONARIOS','VER_LOGS']},
      ...Array.from({length:79},(_,i)=>{
        const [nome,sobrenome]=staff[i%staff.length].split(' ');
        const manager=i<7,operator=i<47;
        return {ru:i+2,nome,sobrenome:sobrenome+(i>=staff.length?' '+(i+1):''),
          nomeClasse:manager?'GERENTE':operator?'CAIXA':'ESTOQUISTA',
          perfil:manager?'GERENTE':'OPERADOR',ativo:i%31!==0,
          permissoes:manager?['VER_ESTOQUE','EDITAR_ESTOQUE','VER_COMPRAS','VER_VENDAS','VER_FINANCAS']:
            operator?['VER_VENDAS','VER_ESTOQUE']:['VER_ESTOQUE','EDITAR_ESTOQUE']};
      })
    ];
    // 12k sales, 1k at each of 12 price points = exactly R$2,000,000.
    // Each receipt is a mixed-size beverage order with product linkage.
    const receipts=12000;
    const counts=allocate(days,receipts);
    const summary=[];
    const bySku=new Map();
    const methods={PIX:0,CARTAO:0,DINHEIRO:0};
    db.vendasPreview=[];
    let cursor=0;
    const productsForPrice=prices.map((p,i)=>db.produtos[200+i*70]);
    productsForPrice.forEach((product,i)=>{
      product.precoUnitario=prices[i];
      product.precoCusto=money(prices[i]*.70);
    });
    function addSale(date,priceIndex,hour,minute,live) {
      const price=prices[priceIndex];
      const roll=rand();
      const payment=roll<.51?'PIX':roll<.88?'CARTAO':'DINHEIRO';
      const box=1+Math.floor(rand()*8);
      const prod=productsForPrice[priceIndex];
      const sale={id:db.vendasPreview.length+1,numeroCaixa:box,valor:price,
        criadaEm:stamp(date,hour,minute),formaPagamento:payment,
        itens:[{idProduto:prod.id,quantidade:1}],demonstracao:true,historico:!live};
      db.vendasPreview.push(sale);
      if(!live) {
        const prior=bySku.get(prod.id)||{qty:0,revenue:0};
        prior.qty++;prior.revenue+=price;bySku.set(prod.id,prior);
        methods[payment]++;
      }
      return sale;
    }
    for(let i=0;i<days.length;i++){
      let total=0;
      for(let j=0;j<counts[i];j++){
        const index=cursor++%prices.length;
        total+=prices[index];
        addSale(days[i],index,8+Math.floor(rand()*15),Math.floor(rand()*60),false);
      }
      summary.push({data:day(days[i]),pedidos:counts[i],faturamento:total});
    }
    const historicalTotal=summary.reduce((a,b)=>a+b.faturamento,0);
    if(cursor!==receipts||historicalTotal!==2000000)throw Error('Beverage revenue fixture inconsistent');
    const available=Math.max(0,(now.getHours()-8)*60+now.getMinutes());
    const liveCount=Math.max(0,Math.min(390,Math.floor(available*.48)));
    const liveSales=[];
    for(let i=0;i<liveCount;i++){
      const minuteOfDay=8*60+Math.floor(rand()*Math.max(1,available));
      liveSales.push(addSale(now,cursor++%prices.length,Math.floor(minuteOfDay/60),
        minuteOfDay%60,true));
    }
    const boxTotals=Array(8).fill(0);
    for(const sale of liveSales)boxTotals[sale.numeroCaixa-1]+=sale.valor;
    db.caixas=boxTotals.map((revenue,i)=>({
      id:i+1,caixaId:i+1,numeroCaixa:i+1,status:'ABERTO',
      totalVendas:revenue,totalEntradas:revenue,totalSaidas:0,
      saldoInicial:500,saldoAtual:revenue+500,nomeOperador:staff[i],
      dataAbertura:stamp(now,8,0)
    }));
    db.movimentacoesCaixa=liveSales.map((sale,i)=>({
      id:i+1,caixaId:sale.numeroCaixa,numeroCaixa:sale.numeroCaixa,
      tipo:'VENDA',valor:sale.valor,descricao:'Pedido fictício #'+sale.id,
      dataHora:sale.criadaEm,operador:staff[sale.numeroCaixa-1]
    })).sort((a,b)=>b.dataHora.localeCompare(a.dataHora));
    db.notas=Array.from({length:420},(_,i)=>{
      const supplier=db.fornecedores[i%db.fornecedores.length];
      const date=days[i%days.length];
      const items=Array.from({length:5},(_,j)=>{
        const prod=db.produtos[(i*29+j*61)%db.produtos.length];
        const qty=20+(i*3+j*11)%180;
        const price=prod.precoCusto;
        return {id:j+1,produtoId:prod.id,produtoNome:prod.nome,
          quantidade:qty,precoUnitario:price,subtotal:money(qty*price)};
      });
      return {id:i+1,numero:'DEMO-BEB-'+String(i+1).padStart(5,'0'),
        fornecedorId:supplier.id,fornecedorNome:supplier.nome,
        dataEmissao:stamp(date,8+i%12,i%60),
        status:i%17===0?'PENDENTE':'CONFIRMADA',
        itens:items,total:money(items.reduce((a,b)=>a+b.subtotal,0))};
    }).reverse();
    db.movimentacoes=Array.from({length:4200},(_,i)=>{
      const date=days[Math.floor(i/140)];
      const prod=db.produtos[(i*37+17)%db.produtos.length];
      return {id:i+1,tipo:i%4===0?'ENTRADA':'SAIDA',
        produto:prod.nome,quantidade:1+i%90,
        dataHora:stamp(date,8+i%15,i%60)};
    }).reverse();
    db.logs=Array.from({length:800},(_,i)=>({
      id:i+1,usuarioRu:1+i%80,
      acao:['VENDA_REGISTRADA','ESTOQUE_CONFERIDO','NOTA_CONFIRMADA','CAIXA_ABERTO'][i%4],
      descricao:'Evento fictício · Central Bebidas 2M',
      dataHora:stamp(days[Math.floor(i/27)%30],8+i%15,i%60)
    })).reverse();
    const ranking=[...bySku].map(([id,sale])=>{
      const prod=db.produtos[id-1],cost=money(prod.precoUnitario*.7);
      // Demo revenue is based on receipt price, while cost follows the simulated pack mix.
      const unitPrice=sale.revenue/sale.qty;
      const unitCost=money(unitPrice*.70);
      return {produtoId:id,nome:prod.nome,precoVenda:unitPrice,
        precoCusto:unitCost,lucroUnitario:money(unitPrice-unitCost),
        margemPercentual:30,margemValida:true,unidadesVendidas:sale.qty,
        receitaEstimada:sale.revenue,lucroTotalEstimado:money(sale.revenue*.30),
        statusLabel:'Estimativa fictícia'};
    }).sort((a,b)=>b.lucroTotalEstimado-a.lucroTotalEstimado);
    ranking.forEach((p,i)=>{p.posicao=i+1;});
    db.rentabilidadePreview={
      itens:ranking,margemMediaPercentual:30,
      lucroTotalEstimado:600000,receitaTotalEstimada:historicalTotal
    };
    db.demoEscala={
      nome:'Central Bebidas 2M — Depósito & Atacado',
      inicio:day(days[0]),fim:day(days[29]),faturamento:historicalTotal,
      pedidos:receipts,ticketMedio:historicalTotal/receipts,
      produtos:db.produtos.length,fornecedores:db.fornecedores.length,
      funcionarios:db.funcionarios.length,notas:db.notas.length,
      vendasHoje:liveSales.reduce((a,b)=>a+b.valor,0),pedidosHoje:liveSales.length,
      formasPagamento:methods,dias:summary,
      observacao:'Faturamento de 30 dias. Custos e margens ilustrativos, não auditados.'
    };
    return db;
  }
  window.StockOSCreateBeverageDemo=create;
})();