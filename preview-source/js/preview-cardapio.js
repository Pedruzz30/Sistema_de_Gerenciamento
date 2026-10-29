/* StockOS cardápio DEMO: browser-only, no real sale or stock authority. */
(() => {
'use strict';
const key='stockos-preview-cardapio-v2';
const baseFetch=window.fetch.bind(window);
const now=()=>new Date().toISOString();
const initial=()=>({
 categories:[{id:1,codigo:'PRATOS',nomeExibicao:'Pratos',ordemExibicao:1,ativo:true},{id:2,codigo:'BEBIDAS',nomeExibicao:'Bebidas',ordemExibicao:2,ativo:true}],
 items:[{id:1,codigo:'BURGER-DEMO',nome:'Hambúrguer da casa',descricaoCurta:'Receita demonstrativa',precoVenda:32.90,tipoItem:'PREPARADO_SOB_DEMANDA',ativo:true,disponivelParaVenda:true,ordemExibicao:1,categoriaId:1,produtoVinculadoId:null},
 {id:2,codigo:'AGUA-DEMO',nome:'Água mineral',descricaoCurta:'500ml',precoVenda:4.00,tipoItem:'ESTOQUE_DIRETO',ativo:true,disponivelParaVenda:true,ordemExibicao:1,categoriaId:2,produtoVinculadoId:7}],
 variations:[{id:1,itemCardapioId:1,nome:'Padrão',codigo:'PADRAO',precoVenda:32.90,tipoComposicao:'SIMPLES',produtoDiretoId:null,quantidadeProdutoDiretoBase:null,ativa:true,padrao:true,ordemExibicao:1,version:1},{id:2,itemCardapioId:2,nome:'Unidade',codigo:'UN',precoVenda:4,tipoComposicao:'PRODUTO_DIRETO',produtoDiretoId:7,quantidadeProdutoDiretoBase:1,ativa:true,padrao:true,ordemExibicao:1,version:1}],
 groups:[{id:1,nome:'Adicionais',descricao:'Escolha complementos',codigo:'ADICIONAIS',tipoSelecao:'MULTIPLA',obrigatorio:false,quantidadeMinima:0,quantidadeMaxima:2,permiteRepeticao:false,ativo:true,ordemExibicao:1,version:1}],
 options:[{id:1,grupoId:1,nome:'Queijo extra',codigo:'QUEIJO',descricao:null,acrescimoPreco:4,quantidadeMaximaPorItem:1,selecionadaPorPadrao:false,ativa:true,ordemExibicao:1,version:1}],
 components:[], associations:[{id:1,variacaoId:1,grupoId:1,obrigatorioOverride:null,quantidadeMinimaOverride:null,quantidadeMaximaOverride:null,ordemExibicao:0,ativo:true,version:1}]
});
let data;try{data=JSON.parse(localStorage.getItem(key)||'null')}catch{}
if(!data||!Array.isArray(data.items)||!Array.isArray(data.categories))data=initial();
const save=()=>{try{localStorage.setItem(key,JSON.stringify(data))}catch{}};
save();
const reply=(obj,status=200)=>Promise.resolve(new Response(JSON.stringify(obj),{status,headers:{'Content-Type':'application/json'}}));
const fail=(reason,status=400)=>reply({erro:reason},status);
const next=items=>Math.max(0,...items.map(i=>Number(i.id)||0))+1;
const bodyOf=init=>{try{return JSON.parse(init.body||'{}')}catch{return {}}};
const enriched=async item=>{
 const category=data.categories.find(c=>c.id===item.categoriaId)||{};
 const products=await(await baseFetch('/api/produtos')).json();
 const prod=products.find(p=>p.id===item.produtoVinculadoId);
 return {...item,categoriaCodigo:category.codigo||'',categoriaNomeExibicao:category.nomeExibicao||'',categoriaAtiva:category.ativo??false,produtoVinculadoNome:prod?.nome||null,nivelEstoque:prod?.nivelEstoque||null,controladoPorEstoque:Boolean(prod?.controladoPorEstoque),quantidadeAtual:prod?.quantidadeAtual??null,disponivel:item.ativo&&item.disponivelParaVenda&&category.ativo&&(!prod||prod.quantidadeAtual>0)};
};
const variation=v=>({...v,itemCodigo:data.items.find(i=>i.id===v.itemCardapioId)?.codigo||'',itemNome:data.items.find(i=>i.id===v.itemCardapioId)?.nome||'',produtoDiretoNome:null});
const patch=(list,id,payload)=>{const x=list.find(i=>i.id===id);if(!x)return null;Object.assign(x,payload);x.version=(x.version||0)+1;save();return x;};
const gr=group=>({...group,quantidadeMinima:group.quantidadeMinima||0,quantidadeMaxima:group.quantidadeMaxima||0});
window.fetch=async (input,init={})=>{
 const u=new URL(input instanceof Request?input.url:String(input),location.href);
 if(u.origin!==location.origin||!u.pathname.startsWith('/api/cardapio/'))return baseFetch(input,init);
 const p=u.pathname,method=String(init.method||(input instanceof Request?input.method:'GET')).toUpperCase(),b=bodyOf(init);
 const match=(re)=>p.match(re);
 if(p==='/api/cardapio/admin/categorias'){
   if(method==='GET')return reply(data.categories);
   if(method==='POST'){if(!String(b.nomeExibicao||'').trim())return fail('Nome obrigatório');let c={id:next(data.categories),codigo:b.codigo||('CAT'+next(data.categories)),nomeExibicao:b.nomeExibicao,ordemExibicao:Number(b.ordemExibicao)||0,ativo:true};data.categories.push(c);save();return reply(c,201);}
 }
 let m=match(/^\/api\/cardapio\/admin\/categorias\/(\d+)(?:\/(ativo|ordem))?$/);
 if(m){const id=+m[1];if(m[2]){let c=patch(data.categories,id,b);return c?reply(c):fail('Categoria inexistente',404);}
 if(method==='PUT'){let c=patch(data.categories,id,b);return c?reply(c):fail('Categoria inexistente',404);}}
 if(p==='/api/cardapio/admin/itens'||p==='/api/cardapio/itens'){
   if(method==='GET')return reply(await Promise.all(data.items.map(enriched)));
   if(method==='POST'&&p.includes('/admin/')){if(!b.nome||!data.categories.some(c=>c.id===+b.categoriaId))return fail('Item/categoria inválido');let i={...b,id:next(data.items),categoriaId:+b.categoriaId,ativo:true,disponivelParaVenda:true};data.items.push(i);save();return reply(await enriched(i),201);}
 }
 m=match(/^\/api\/cardapio\/admin\/itens\/(\d+)(?:\/(ativo|disponibilidade|ordem))?$/);
 if(m){const id=+m[1];let i=patch(data.items,id,b);return i?reply(await enriched(i)):fail('Item inexistente',404);}
 if(p==='/api/cardapio/categorias'&&method==='GET')return reply(data.categories.filter(c=>c.ativo));
 if(p==='/api/cardapio/grupos-modificadores'){
   if(method==='GET')return reply(data.groups);
   if(method==='POST'){if(!b.nome)return fail('Nome obrigatório');let x={...b,id:next(data.groups),version:1};data.groups.push(x);save();return reply(x,201);}
 }
 m=match(/^\/api\/cardapio\/grupos-modificadores\/(\d+)(?:\/(status|opcoes))?$/);
 if(m){const id=+m[1];if(m[2]==='opcoes'){
   if(method==='GET')return reply(data.options.filter(x=>x.grupoId===id));
   if(method==='POST'){if(!data.groups.some(g=>g.id===id)||!b.nome)return fail('Grupo/opção inválidos');let x={...b,id:next(data.options),grupoId:id,version:1};data.options.push(x);save();return reply(x,201);}
 }
 if(method==='GET')return reply(data.groups.find(x=>x.id===id)||{});
 let x=patch(data.groups,id,b);return x?reply(x):fail('Grupo inexistente',404);
 }
 m=match(/^\/api\/cardapio\/opcoes-modificadores\/(\d+)(?:\/componentes(?:\/(\d+))?)?$/);
 if(m){const id=+m[1],comp=m[2];if(p.includes('componentes')){
   if(method==='GET')return reply(data.components.filter(x=>x.opcaoId===id));
   if(method==='POST'){if(!data.options.some(o=>o.id===id))return fail('Opção inexistente');const x={...b,id:next(data.components),opcaoId:id};data.components.push(x);save();return reply(x,201);}
   if(method==='DELETE'&&comp){data.components=data.components.filter(x=>x.id!==+comp);save();return reply({sucesso:true});}
   if(method==='PUT'&&comp){const x=patch(data.components,+comp,b);return x?reply(x):fail('Componente inexistente',404);}
 }else{const x=patch(data.options,id,b);return x?reply(x):fail('Opção inexistente',404);}
 }
 m=match(/^\/api\/cardapio\/itens\/(\d+)\/variacoes$/);
 if(m){const id=+m[1];if(method==='GET')return reply(data.variations.filter(v=>v.itemCardapioId===id).map(variation));
 if(method==='POST'){if(!data.items.some(i=>i.id===id))return fail('Item inexistente');const x={...b,id:next(data.variations),itemCardapioId:id,version:1,ativa:b.ativa!==false};data.variations.push(x);save();return reply(variation(x),201);}}
 m=match(/^\/api\/cardapio\/variacoes\/(\d+)(?:\/(status|grupos-modificadores|configuracao))?$/);
 if(m){const id=+m[1];if(m[2]==='grupos-modificadores'){
   if(method==='GET')return reply(data.associations.filter(a=>a.variacaoId===id));
   if(method==='POST'){if(!data.groups.some(g=>g.id===+b.grupoId))return fail('Grupo inexistente');const x={...b,id:next(data.associations),variacaoId:id,grupoId:+b.grupoId};data.associations.push(x);save();return reply(x,201);}
 }
 if(m[2]==='configuracao'&&method==='GET'){
   const v=data.variations.find(v=>v.id===id);if(!v)return fail('Variação inexistente',404);
   return reply({variacaoId:id,nome:v.nome,precoBase:v.precoVenda,configuracaoVersao:'DEMO-'+id,grupos:data.associations.filter(a=>a.variacaoId===id&&a.ativo).map(a=>{const g=data.groups.find(g=>g.id===a.grupoId);return g?{...g,associacaoId:a.id,minimo:a.quantidadeMinimaOverride??g.quantidadeMinima,maximo:a.quantidadeMaximaOverride??g.quantidadeMaxima,opcoes:data.options.filter(o=>o.grupoId===g.id).map(o=>({...o,opcaoId:o.id,acrescimo:o.acrescimoPreco,disponivel:o.ativa,estado:o.ativa?'DISPONIVEL':'INATIVA'}))}:null;}).filter(Boolean)});
 }
 const v=patch(data.variations,id,m[2]==='status'?{ativa:b.ativa}:b);return v?reply(variation(v)):fail('Variação inexistente',404);
 }
 if(p==='/api/cardapio/disponibilidade'&&method==='GET')return reply({itens:[],variacoes:[]});
 return fail('Operação avançada indisponível na demonstração.',501);
};
window.StockOsCardapioDemo={reset:()=>{localStorage.removeItem(key);location.reload();},status:'demo'};
})();
