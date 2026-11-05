/* Operação demonstrativa e isolada: sem endpoints de pagamento ou de venda. */
(() => {
'use strict';
const KEY='stockos-preview-comandas-v1';
const $=id=>document.getElementById(id);
const fmt=n=>(n/100).toLocaleString('pt-BR',{style:'currency',currency:'BRL'});
const normal=s=>String(s||'').normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase();
const node=(tag,cls,content)=>{
 const e=document.createElement(tag);
 if(cls)e.className=cls;
 if(content!==undefined)e.textContent=content;
 return e;
};
const products=[
 {id:6,nome:'Refrigerante cola lata 350ml',precoUnitario:6,quantidadeAtual:72,ativo:true},
 {id:7,nome:'Água mineral 500ml',precoUnitario:4,quantidadeAtual:60,ativo:true},
 {id:8,nome:'Suco de uva 1L',precoUnitario:12.9,quantidadeAtual:14,ativo:true},
 {id:14,nome:'Hambúrguer 180g',precoUnitario:11.9,quantidadeAtual:40,ativo:true}
];
let saved=[];
try {const x=JSON.parse(localStorage.getItem(KEY)||'[]');if(Array.isArray(x))saved=x;}
catch(error){console.warn('Armazenamento de demonstração indisponível',error);}
const model=window.StockOSComandasDemoModel.createModel(saved);
let selected=null,pane='produtos';
const persist=()=>{try{localStorage.setItem(KEY,JSON.stringify(model.list()));}catch(error){console.warn('Comanda não persistida',error);}};
function feedback(text,ok=false){$('feedback').textContent=text;$('feedback').classList.toggle('success',ok);}
function commit(fn,msg){
 try {const value=fn();persist();render();feedback(msg,true);return value;}
 catch(error){feedback(error.message);return null;}
}
function button(label,fn,aria){
 const e=node('button','',label);e.type='button';
 if(aria)e.setAttribute('aria-label',aria);
 e.addEventListener('click',fn);return e;
}
function select(number){selected=number;pane='produtos';render();}
function showPane(target){
 pane=target;$('workspace').dataset.panel=target;
 $('show-products').setAttribute('aria-pressed',String(target==='produtos'));
 $('show-order').setAttribute('aria-pressed',String(target==='pedido'));
}
function renderTabs(){
 const all=model.list(),root=$('tab-list');
 $('comandas-summary').textContent=all.length+' '+(all.length===1?'comanda':'comandas')+' no navegador';
 root.replaceChildren();
 if(!all.length)root.append(node('p','muted-empty','Nenhuma comanda. Abra o primeiro número para começar.'));
 for(const item of all){
  const e=button('',()=>select(item.numero),'Abrir comanda '+item.numero);
  e.className='tab-card'+(selected===item.numero?' selected':'');
  const info=node('span');
  info.append(node('strong','','Comanda '+String(item.numero).padStart(3,'0')),
    node('small','',item.status==='CONFERENCIA'?'Aguardando conferência':'Em atendimento'));
  e.append(info,node('span','value',fmt(item.totalCentavos)));
  root.append(e);
 }
}
function renderProducts(){
 const tab=model.get(selected),root=$('product-list');root.replaceChildren();
 const found=products.filter(p=>normal(p.nome).includes(normal($('product-search').value)));
 if(!found.length)root.append(node('p','muted-empty','Nenhum produto encontrado.'));
 for(const product of found){
  const e=button('',()=>{
    const result=commit(()=>model.add(selected,product,1),'Produto adicionado.');
    if(result&&window.innerWidth<=560)showPane('pedido');
  },'Adicionar '+product.nome);
  e.className='product-card';e.disabled=tab.status!=='ABERTA';
  const info=node('span');
  info.append(node('strong','',product.nome),
   node('small','',fmt(Math.round(product.precoUnitario*100))+' · '+product.quantidadeAtual+' unidades fictícias'));
  e.append(info,node('span','plus-icon','+'));root.append(e);
 }
}
function renderOrder(){
 const tab=model.get(selected),root=$('order-list');root.replaceChildren();
 if(!tab.itens.length)root.append(node('p','muted-empty','Nenhum consumo registrado. Toque em Produtos para adicionar.'));
 for(const item of tab.itens){
  const row=node('div','order-row'),info=node('div');
  info.append(node('strong','',item.nome),node('small','',fmt(item.precoCentavos*item.quantidade)));
  const controls=node('div','qty-controls');
  const available=products.find(p=>p.id===item.id)?.quantidadeAtual||0;
  const less=button('−',()=>commit(()=>model.change(selected,item.id,-1,available),'Quantidade reduzida.'),'Remover uma unidade de '+item.nome);
  const more=button('+',()=>commit(()=>model.change(selected,item.id,1,available),'Quantidade aumentada.'),'Adicionar uma unidade de '+item.nome);
  less.disabled=more.disabled=tab.status!=='ABERTA';
  controls.append(less,node('span','',String(item.quantidade)),more);
  row.append(info,controls);root.append(row);
 }
}
function render(){
 renderTabs();
 if(selected===null||!model.list().some(t=>t.numero===selected)){
  $('empty-detail').hidden=false;$('tab-detail').hidden=true;return;
 }
 const tab=model.get(selected);
 $('empty-detail').hidden=true;$('tab-detail').hidden=false;
 $('detail-title').textContent='Comanda '+String(tab.numero).padStart(3,'0');
 $('tab-status').textContent=tab.status==='CONFERENCIA'?'Conferência solicitada':'Aberta';
 $('tab-status').classList.toggle('review',tab.status==='CONFERENCIA');
 $('tab-total').textContent=fmt(tab.totalCentavos);
 $('tab-items').textContent=tab.quantidade+' '+(tab.quantidade===1?'item':'itens');
 $('order-badge').textContent=tab.quantidade;
 $('request-close').hidden=tab.status!=='ABERTA';
 $('request-close').disabled=tab.itens.length===0;
 $('resume-tab').hidden=tab.status!=='CONFERENCIA';
 $('closing-note').textContent=tab.status==='CONFERENCIA'
  ?'Conferência fictícia solicitada. Nenhum pagamento ou fechamento real.'
  :'Solicitar conferência apenas altera esta simulação, sem cobrar ou movimentar caixa.';
 renderProducts();renderOrder();showPane(pane);
}
$('open-tab').addEventListener('submit',e=>{
 e.preventDefault();const opened=commit(()=>model.open($('tab-number').value),'Comanda aberta.');
 if(opened){$('tab-number').value='';selected=opened.numero;pane='produtos';render();}
});
$('request-close').addEventListener('click',()=>commit(()=>model.requestClose(selected),'Conferência fictícia solicitada.'));
$('resume-tab').addEventListener('click',()=>commit(()=>model.resume(selected),'Atendimento retomado.'));
$('show-products').addEventListener('click',()=>showPane('produtos'));
$('show-order').addEventListener('click',()=>showPane('pedido'));
$('product-search').addEventListener('input',()=>{if(selected!==null)renderProducts();});
const existing=model.list();if(existing.length)selected=existing[0].numero;
render();
})();
