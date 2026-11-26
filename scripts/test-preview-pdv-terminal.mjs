import assert from 'node:assert/strict';
import vm from 'node:vm';
import {readFile} from 'node:fs/promises';
const source=await readFile('preview-source/js/preview-pdv.js','utf8');
new vm.Script(source);
const start=source.indexOf('function previewTerminalProducts('),end=source.indexOf('let terminalOpening',start);
const api=vm.runInNewContext(source.slice(start,end)+'\n({previewTerminalProducts,terminalFilter,terminalCartTotal,terminalChange,terminalPayment})');
const products=api.previewTerminalProducts([
 {id:1,nome:'Água',categoriaEstoque:'BEBIDAS',codigoBarras:'123',precoUnitario:2.49,quantidadeAtual:2},
 {id:2,nome:'Lanche',categoriaEstoque:'PREPARADOS',precoUnitario:12.5,quantidadeAtual:3},
 {id:3,nome:'Sem estoque',precoUnitario:2,quantidadeAtual:0},
 {id:4,nome:'Inativo',ativo:false,precoUnitario:2,quantidadeAtual:3},
 {id:5,nome:'Sem preço',precoUnitario:null,quantidadeAtual:3}
]);
assert.equal(products.length,2);
assert.equal(api.terminalFilter(products,'all','agua')[0].id,1);
assert.equal(api.terminalFilter(products,'all','123')[0].id,1);
assert.equal(api.terminalFilter(products,'PREPARADOS','').length,1);
const cart=new Map();
assert.equal(api.terminalChange(products,cart,1,2),true);
assert.equal(api.terminalChange(products,cart,1,1),false);
assert.equal(api.terminalChange(products,cart,2,1),true);
assert.equal(api.terminalCartTotal(products,cart),1748);
assert.equal(api.terminalPayment(1748,'DINHEIRO','17'),null);
assert.equal(api.terminalPayment(1748,'DINHEIRO','20').valorRecebido,20);
assert.equal(api.terminalPayment(1748,'PIX','20').valor,17.48);
assert.equal(api.terminalPayment(1748,'PIX','20').valorRecebido,undefined);
assert.equal(api.terminalChange(products,cart,1,-2),true);
assert.equal(api.terminalCartTotal(products,cart),1250);
assert.equal(api.terminalPayment(0,'PIX',''),null);
assert.match(source,/body:JSON.stringify\(pending\)/);
assert.match(source,/const locked=\(\)=>processing\|\|submitted\|\|!!pending/);
assert.match(source,/if\(window.StockOsDesignReview\?\.mode!=='original'\)/);
console.log('PASS preview terminal: catalog, stock limits, cent arithmetic, payments, retry payload and original mode.');
