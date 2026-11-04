import assert from 'node:assert/strict';
import vm from 'node:vm';
import {readFile} from 'node:fs/promises';
import {execFileSync} from 'node:child_process';

const sourceFile='src/main/resources/static/js/pdv.js';
execFileSync(process.execPath,['--check',sourceFile],{stdio:'inherit'});
const original=await readFile(sourceFile,'utf8');
assert.match(original,/pdvTentativaFinalizacao\.chave/);
assert.doesNotMatch(original,/Promise\.all\(ids\.map\(id => fetch/);

const instrumentation=[
  'window.__testPDV={',
  '  setSale(sale) { pdvVendaAtual=sale; pdvCarrinho=sale.itens.filter(i=>i.status!==\'CANCELADO\'); },',
  '  getSale() { return pdvVendaAtual; },',
  '  getItems() { return pdvCarrinho; },',
  '  getKey() { return pdvTentativaFinalizacao?.chave; }',
  '};',
  'renderCarrinho=()=>{ pdvCarrinho=itensAtivosVendaPDV(); };',
  'renderCardapio=()=>{};'
].join('\n');
const code=original.replace(/\}\)\(\);\s*$/,instrumentation+'\n})();\n');
assert.ok(code.includes('window.__testPDV'),'PDV test instrumentation could not be inserted');
const alerts=[],requests=[],toasts=[];
let strategy=async () => {throw new Error('request unexpectedly called')};
const btn={disabled:false,classList:{add(){},remove(){}},textContent:'Finalizar'};
const doc={
  getElementById:id=>id==='btn-pdv-finalizar'?btn:null,
  querySelector:()=>({disabled:false}),
  addEventListener(){}
};
const ctx={
  window:{StockOSPages:{}},
  document:doc,location:{pathname:'/caixas.html'},
  fetch:(...args)=>{requests.push(args);return strategy(...args);},
  setLoading(){},limparAlert(){},mostrarAlert:(...args)=>alerts.push(args),
  fecharModal(){},showToast:(...args)=>toasts.push(args),carregarCaixas(){},
  temPermissao(){return true},temAlgumaPermissao(){return true},PERMISSOES:{},
  console,setTimeout,clearTimeout,Date,Math,JSON,Promise,Intl
};
vm.createContext(ctx);
vm.runInContext(code,ctx,{filename:sourceFile});
ctx.window.__testPDV.setSale({id:77,status:'ABERTA',itens:[{id:901,itemCardapioId:2,quantidade:2,subtotal:20,status:'ATIVO'}]});
let attempts=0;
strategy=async()=>{attempts++;if(attempts===1)throw Error('Rede indisponível');return {ok:true,json:async()=>({id:77,status:'FINALIZADA',itens:[]})};};
await ctx.window.finalizarVendaPDV();
assert.equal(attempts,1);
assert.equal(alerts.length,1,'Ambiguous transport failure should be visible');
assert.ok(ctx.window.__testPDV.getSale(),'Unconfirmed sale must remain available for retry');
await ctx.window.finalizarVendaPDV();
assert.equal(attempts,2);
assert.equal(requests[0][1].headers['Idempotency-Key'],requests[1][1].headers['Idempotency-Key'],
  'Retry of the same sale must send the same idempotency key');
assert.equal(ctx.window.__testPDV.getSale(),null,'Confirmed success can release cart');
assert.equal(toasts.length,1);

requests.length=0;alerts.length=0;
ctx.window.__testPDV.setSale({id:88,status:'ABERTA',itens:[
  {id:101,status:'ATIVO',quantidade:1,subtotal:5},
  {id:102,status:'ATIVO',quantidade:2,subtotal:10}
]});
strategy=async(url,init)=>{
  if(init?.method==='DELETE' && url.endsWith('/101'))return {ok:true};
  if(init?.method==='DELETE' && url.endsWith('/102'))return {ok:false,status:500};
  if(url==='/api/vendas/88')return {ok:true,json:async()=>({id:88,status:'ABERTA',itens:[
    {id:101,status:'CANCELADO',quantidade:1,subtotal:5},
    {id:102,status:'ATIVO',quantidade:2,subtotal:10}
  ]})};
  throw Error('Unexpected '+url);
};
await ctx.window.limparCarrinho();
assert.equal(requests.filter(([url,opt])=>opt?.method==='DELETE').length,2);
assert.ok(ctx.window.__testPDV.getSale(),'Partially deleted sale should not be forgotten');
assert.equal(ctx.window.__testPDV.getItems().length,1,'Remaining server-side item must stay visible');
assert.ok(alerts.some(a=>String(a[1]).includes('Não foi possível limpar')),'Partial failure must alert operator');

const inventory=await readFile('src/main/resources/static/js/estoque.js','utf8');
const stockCss=await readFile('src/main/resources/static/css/estoque-refinement.css','utf8');
assert.match(inventory,/data-label="Quantidade atual"/);
assert.match(inventory,/function resetarFiltrosEstoque\(\)/);
assert.match(inventory,/normalize\('NFD'\)/);
assert.match(stockCss,/@media \(max-width: 640px\)/);
assert.match(stockCss,/td::before \{content:attr\(data-label\)/);
console.log('PASS UX: PDV safe retry and partial cart failures; inventory mobile cards and filters');
