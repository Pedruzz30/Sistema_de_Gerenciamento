import assert from 'node:assert/strict';
import vm from 'node:vm';
import {readFile} from 'node:fs/promises';
import {execFileSync} from 'node:child_process';

const dash=await readFile('src/main/resources/static/js/dashboard.js','utf8');
const stock=await readFile('src/main/resources/static/js/estoque.js','utf8');
const html=await readFile('src/main/resources/static/dashboard.html','utf8');
const css=await readFile('src/main/resources/static/css/dashboard-refinement.css','utf8');
for(const path of ['src/main/resources/static/js/dashboard.js','src/main/resources/static/js/estoque.js'])
  execFileSync(process.execPath,['--check',path],{stdio:'inherit'});
assert.match(html,/id="daily-actions-list"/);
assert.match(css,/operations-item:focus-visible/);
assert.match(stock,/new URLSearchParams\(window\.location\.search\)/);
assert.match(stock,/filtroUrl === 'critico' \|\| filtroUrl === 'baixo'/);
assert.match(dash,/value: status\.produtosDisponiveis \? totalProdutos : '—'/);
assert.match(dash,/c\.perm \|\| temPermissao\(c\.perm\)/);
const start=dash.indexOf('function renderAcoesOperacionais(');
const end=dash.indexOf('// ── Seções por permissão',start);
assert.ok(start>=0 && end>start);
const nodes={
  'daily-actions':{hidden:true},
  'daily-actions-list':{innerHTML:''},
  'daily-actions-count':{textContent:''}
};
let canStock=true,canCash=true;
const ctx={
 document:{getElementById:id=>nodes[id]},
 escapeHtml:s=>String(s).replaceAll('&','&amp;').replaceAll('<','&lt;'),
 obterAlertasEstoque:arr=>arr.filter(p=>['SEM_ESTOQUE','CRITICO','BAIXO','MODERADO'].includes(p.nivelEstoque)),
 PODE_VER_ESTOQUE_DASH:true,
 PODE_VER_FINANCEIRO_DASH:true
};
vm.createContext(ctx);
vm.runInContext(dash.slice(start,end),ctx);
const render=ctx.renderAcoesOperacionais;
const samples=[
  {id:1,nome:'Refrigerante',nivelEstoque:'CRITICO'},
  {id:2,nome:'Água',nivelEstoque:'BAIXO'},
  {id:3,nome:'Arroz',nivelEstoque:'ADEQUADO'}
];
render(samples,[{status:'ABERTO'}],{produtosDisponiveis:true,caixasDisponiveis:true});
assert.equal(nodes['daily-actions'].hidden,false);
assert.match(nodes['daily-actions-list'].innerHTML,/\/estoque\.html\?filtro=critico/);
assert.match(nodes['daily-actions-list'].innerHTML,/\/estoque\.html\?filtro=baixo/);
assert.match(nodes['daily-actions-list'].innerHTML,/1 caixa em operação/);
assert.equal(nodes['daily-actions-count'].textContent,'3 ações');
render([],[],{produtosDisponiveis:false,caixasDisponiveis:false});
assert.match(nodes['daily-actions-list'].innerHTML,/Estoque indisponível/);
assert.match(nodes['daily-actions-list'].innerHTML,/Caixas indisponíveis/);
assert.doesNotMatch(nodes['daily-actions-list'].innerHTML,/Estoque sem alertas/);
ctx.PODE_VER_ESTOQUE_DASH=false;
ctx.PODE_VER_FINANCEIRO_DASH=true;
render(samples,[{status:'ABERTO'}],{produtosDisponiveis:true,caixasDisponiveis:true});
assert.doesNotMatch(nodes['daily-actions-list'].innerHTML,/estoque\.html/);
ctx.PODE_VER_FINANCEIRO_DASH=false;
render(samples,[],{produtosDisponiveis:true,caixasDisponiveis:true});
assert.equal(nodes['daily-actions'].hidden,true);
console.log('PASS dashboard operational actions: correct priorities, URL filters, unavailable states and permission gates');
