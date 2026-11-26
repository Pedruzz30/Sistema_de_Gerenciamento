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


const sent=[], elements=[];
let active=null, keyListener=null, attempts=0;
class Node {
  constructor(tag){this.tagName=tag;this.children=[];this.dataset={};this.attrs={};this.style={};this.value='';this.disabled=false;this.hidden=false;elements.push(this);}
  append(...nodes){for(const node of nodes){node.parent=this;this.children.push(node);}}
  appendChild(node){this.append(node);return node;}
  replaceChildren(...nodes){for(const n of this.children)n.parent=null;this.children=[];this.append(...nodes);}
  setAttribute(k,v){this.attrs[k]=v;}
  getAttribute(k){return this.attrs[k];}
  get isConnected(){return this===body||this===head||!!this.parent?.isConnected;}
  focus(){active=this;}
  remove(){if(this.parent){this.parent.children=this.parent.children.filter(n=>n!==this);this.parent=null;}}
  querySelectorAll(selector){const out=[];const walk=n=>{for(const c of n.children){if(selector==='button'&&c.tagName==='button')out.push(c);walk(c);}};walk(this);return out;}
}
const body=new Node('body'),head=new Node('head');
const doc={body,head,readyState:'complete',get activeElement(){return active;},
 createElement:tag=>new Node(tag),getElementById:id=>elements.find(n=>n.id===id&&n.isConnected)||null,
 addEventListener:(event,fn,capture)=>{if(event==='keydown')keyListener={fn,capture};},
 removeEventListener:(event,fn,capture)=>{if(event==='keydown'&&keyListener?.fn===fn&&keyListener.capture===capture)keyListener=null;}};
const window={crypto:{randomUUID:()=> 'preview_test_key'}};
const fetch=async(path,options)=>{
 if(path==='/api/caixas')return {ok:true,json:async()=>[{numeroCaixa:1,status:'ABERTO'}]};
 if(path==='/api/produtos')return {ok:true,json:async()=>[{id:1,nome:'Água',precoUnitario:2.49,quantidadeAtual:2,categoriaEstoque:'BEBIDAS'}]};
 if(path!=='/api/preview/transacoes/venda')throw Error('Unexpected endpoint '+path);
 sent.push(options.body);
 if(++attempts===1)throw Error('Resposta perdida');
 return {ok:true,json:async()=>({})};
};
vm.runInNewContext(source,{window,document:doc,location:{pathname:'/caixas.html'},fetch,alert:msg=>{throw Error(msg)},confirm:()=>true,console});
await window.abrirPDV(1);
const find=(cls,tag)=>elements.findLast(n=>n.isConnected&&n.className==='stockos-terminal__'+cls&&(!tag||n.tagName===tag));
assert.ok(doc.getElementById('stockos-preview-pdv'));
assert.equal(find('finish').disabled,true);
find('product').onclick();
assert.equal(find('finish').disabled,false);
find('plus','button').onclick();
assert.equal(find('product').disabled,true);
find('received-input').value='4';
find('received-input').oninput();
assert.equal(find('finish').disabled,true);
find('received-input').value='5';
find('received-input').oninput();
assert.equal(find('finish').disabled,false);
find('finish').onclick();
for(let i=0;i<8;i++)await Promise.resolve();
assert.equal(find('finish').textContent,'Confirmar novamente');
assert.equal(find('product').disabled,true);
assert.equal(find('method').disabled,true);
find('finish').onclick();
for(let i=0;i<8;i++)await Promise.resolve();
assert.equal(sent.length,2);
assert.equal(sent[0],sent[1]);
assert.equal(JSON.parse(sent[0]).pagamentos[0].valor,4.98);
assert.equal(JSON.parse(sent[0]).pagamentos[0].valorRecebido,5);
assert.equal(find('finish').textContent,'Venda registrada');
assert.equal(find('finish').disabled,true);
find('close').onclick();
assert.equal(doc.getElementById('stockos-preview-pdv'),null);
assert.equal(keyListener,null);
console.log('PASS terminal UI: mount, cart stock, cash, lost response retry, duplicate prevention and close cleanup.');
