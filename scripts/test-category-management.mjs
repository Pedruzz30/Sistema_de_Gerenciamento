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

const modalStart=source.indexOf('function categoryModalModel('),modalEnd=source.indexOf('let categoryModalSaving',modalStart);
const editor=vm.runInNewContext(source.slice(modalStart,modalEnd)+'\n({categoryModalModel,suggestedCategoryOrder});',{categoryOrderValue:api.categoryOrderValue});
assert.equal(editor.categoryModalModel('  Pratos  ','','1').normalized,'pratos');
assert.equal(editor.categoryModalModel('Bebidas','  CAFÉ & Chá  ','2').normalized,'cafe_cha');
assert.equal(editor.categoryModalModel('Bebidas','OLD_CODE','0').codigo,'OLD_CODE');
assert.equal(editor.categoryModalModel('Bebidas','OLD_CODE','0').ordemExibicao,0);
assert.equal(editor.categoryModalModel('Bebidas','OLD_CODE','0').error,'');
assert.ok(editor.categoryModalModel('','','0').error);
assert.ok(editor.categoryModalModel('Pratos','!!!','0').error);
assert.ok(editor.categoryModalModel('Pratos','','1.5').error);
assert.ok(editor.categoryModalModel('x'.repeat(121),'ok','0').error);
assert.ok(editor.categoryModalModel('Pratos','x'.repeat(81),'0').error);
assert.equal(editor.suggestedCategoryOrder(cats),3);
assert.equal(editor.suggestedCategoryOrder([]),1);
assert.equal(editor.suggestedCategoryOrder([{ordemExibicao:2147483647}]),2147483647);
assert.match(source,/if\(categoryModalSaving\|\|categoryMutationPending\)return/);
assert.match(source,/codigo:model\.codigo,nomeExibicao:model\.nomeExibicao,ordemExibicao:model\.ordemExibicao/);
assert.match(layout,/stockos-category-modal-fields \{grid-template-columns:minmax\(0,1fr\)/);
const editorHtml=await readFile('src/main/resources/static/cardapio.html','utf8');
assert.match(editorHtml,/aria-labelledby="modal-categoria-titulo"/);
assert.match(editorHtml,/id="categoria-preview-name"/);
console.log('PASS category editor: normalization preview, field validation, order suggestion and existing payload.');

const statusCss=await readFile('src/main/resources/static/app.css','utf8');
assert.doesNotMatch(statusCss,/\.stockos-category-status\s*>\s*span\s*\{/);
assert.match(statusCss,/\.stockos-category-status>span\[aria-hidden="true"\]\{width:6px;height:6px;flex:0 0 6px;/);
assert.match(editorHtml,/id="categoria-context-state"[^>]*><span aria-hidden="true"><\/span><span id="categoria-context-state-text"><\/span>/);
assert.match(source,/class="stockos-category-status"[^<]*<span aria-hidden="true"><\/span>/);
assert.match(layout,/#categoria-context-state-text \{display:inline;width:auto;height:auto;background:none;/);
assert.match(layout,/#categoria-context-state \{flex:0 0 auto;white-space:nowrap;/);
console.log('PASS category status regression: only decorative dot is sized, label remains inline.');

const itemHelpers=source.slice(source.indexOf('function managedItemState('),source.indexOf('let itemMutationPending'));
const itemResolver=source.slice(source.indexOf('function resolverDisponibilidadeItem('),source.indexOf('function normalizarDisponibilidadeCardapio('));
const menuApi=vm.runInNewContext(itemResolver+'\n'+itemHelpers+'\n({managedItemState,managedItemRow});',{
 categoryOrderValue:api.categoryOrderValue,formatarNumero:n=>Number(n).toFixed(2),
 escapeHtml:s=>String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/"/g,'&quot;'),
 primeiroAlertaCardapio:()=>null,capacidadeCardapio:()=>5,resumoOperacionalCardapio:()=> 'Falta de ingrediente.'
});
const menuItem={id:1,nome:'Hambúrguer da casa',codigo:'BURGER',categoriaNomeExibicao:'Pratos',categoriaAtiva:true,ativo:true,disponivelParaVenda:true,tipoItem:'PREPARADO_SOB_DEMANDA',precoVenda:32.9,ordemExibicao:1};
assert.equal(menuApi.managedItemState(menuItem).group,'unverified');
assert.equal(menuApi.managedItemState({...menuItem,ativo:false}).group,'inactive');
assert.equal(menuApi.managedItemState({...menuItem,categoriaAtiva:false}).group,'blocked');
assert.equal(menuApi.managedItemState({...menuItem,disponivelParaVenda:false}).group,'paused');
assert.equal(menuApi.managedItemState({...menuItem,disponibilidadeOperacional:{resumo:{itemDisponivel:true}}}).group,'available');
assert.equal(menuApi.managedItemState({...menuItem,disponibilidadeOperacional:{resumo:{itemDisponivel:false}}}).group,'blocked');
assert.equal(menuApi.managedItemState({...menuItem,tipoItem:'ESTOQUE_DIRETO',controladoPorEstoque:true,quantidadeAtual:0}).group,'blocked');
const menuRow=menuApi.managedItemRow(menuItem);
assert.match(menuRow,/Pratos · BURGER/);
assert.match(menuRow,/R\$ 32\.90/);
assert.match(menuRow,/Sem confirmação operacional/);
assert.match(menuRow,/Pausar venda/);
assert.match(menuRow,/Inativar/);
assert.match(menuRow,/data-item-action="order" disabled/);
assert.match(menuApi.managedItemRow({...menuItem,nome:'<script>',precoVenda:null}),/&lt;script>/);
assert.doesNotMatch(menuApi.managedItemRow({...menuItem,precoVenda:null}),/R\$ 0\.00/);
assert.match(menuApi.managedItemRow({...menuItem,disponivelParaVenda:false,ativo:false}),/Liberar venda/);
assert.match(source,/managedItemState\(item\)\.group===state/);
assert.match(source,/if\(itemMutationPending\)return/);
assert.match(editorHtml,/id="item-status-filter"/);
assert.match(layout,/stockos-menu-item-status>span\[aria-hidden="true"\]/);
const itemWidths=[...layout.matchAll(/\.cardapio-items-table :is\(th,td\):nth-child\([1-6]\)\{width:(\d+)%;\}/g)].map(m=>Number(m[1]));
assert.equal(itemWidths.length,6);
assert.equal(itemWidths.reduce((a,b)=>a+b,0),100);
console.log('PASS menu items: operational distinctions, escaped content, prices, filters and proportional columns.');
