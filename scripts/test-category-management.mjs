import assert from 'node:assert/strict';
import vm from 'node:vm';
import {readFile} from 'node:fs/promises';
const source=await readFile('src/main/resources/static/js/cardapio.js','utf8');
new vm.Script(source);
const start=source.indexOf('function categoryManagementModel('),end=source.indexOf('let categoryMutationPending',start);
const api=vm.runInNewContext(source.slice(start,end)+'\n({categoryManagementModel,categoryOrderValue});');
const cats=[{id:2,nomeExibicao:'Bebidas',ordemExibicao:2,ativo:true},{id:1,nomeExibicao:'Pratos',ordemExibicao:1,ativo:false},{id:3,nomeExibicao:'Doces',ordemExibicao:2,ativo:true}];
const items=[{categoriaId:1,ativo:true},{categoriaId:'1',ativo:false},{categoriaId:2,ativo:true},{categoriaId:99,ativo:true}];
const rows=api.categoryManagementModel(cats,items);
assert.equal(rows.map(c=>c.id).join(','),'1,2,3');
assert.equal(rows[0].items.total,2);
assert.equal(rows[0].items.active,1);
assert.equal(rows[0].ativo,false);
assert.equal(rows[2].items.total,0);
assert.equal(cats[0].id,2);
assert.equal(api.categoryOrderValue('0'),0);
assert.equal(api.categoryOrderValue('2147483647'),2147483647);
for(const bad of ['','-1','1.5','1e3','2147483648'])assert.equal(api.categoryOrderValue(bad),null);
assert.match(source,/filter\.value=String\(id\);search\.value='';aplicarFiltrosItens\(\)/);
assert.match(source,/if\(categoryMutationPending\)return/);
assert.match(source,/save\.disabled=categoryMutationPending\|\|value==null\|\|value===original/);
console.log('PASS category management: counts, ordering, validation, links and mutation guard.');

const renderSource=source.slice(source.indexOf('function renderManagedCategories('),source.indexOf('function renderCategorias()'));
const render=vm.runInNewContext(source.slice(start,end)+'\n'+renderSource+'\nrenderManagedCategories;',{
  escapeHtml:s=>String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/"/g,'&quot;'),
  iconMarkup:()=>'<svg></svg>'
});
const tbody={innerHTML:'',querySelectorAll:()=>[]};
render(tbody,[{id:1,nomeExibicao:'Pratos',codigo:'PRATOS',ordemExibicao:1,ativo:true}],items);
assert.match(tbody.innerHTML,/2 itens/);
assert.match(tbody.innerHTML,/1 ativo · 1 inativo/);
assert.match(tbody.innerHTML,/aria-label="Ver itens de Pratos"/);
assert.match(tbody.innerHTML,/stockos-category-item-link">Ver itens/);
assert.match(tbody.innerHTML,/data-category-action="order" disabled/);
const layout=await readFile('src/main/resources/static/css/cardapio-refinement.css','utf8');
assert.match(layout,/table-layout:fixed;width:100%;min-width:760px/);
const widths=[...layout.matchAll(/:nth-child\([1-5]\) \{width:(\d+)%;\}/g)].map(m=>Number(m[1]));
assert.equal(widths.length,5);
assert.equal(widths.reduce((a,b)=>a+b,0),100);
console.log('PASS category layout: proportional columns, explicit item navigation, real item counts.');
