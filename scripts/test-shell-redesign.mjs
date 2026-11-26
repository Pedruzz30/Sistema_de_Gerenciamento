import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';

const root = new URL('../src/main/resources/static/', import.meta.url);
const shared = await readFile(new URL('js/shared.js', root), 'utf8');
const dashboard = await readFile(new URL('js/dashboard.js', root), 'utf8');
const css = await readFile(new URL('app.css', root), 'utf8');
new vm.Script(shared);
new vm.Script(dashboard);

function extract(source, start, end) {
  const i = source.indexOf(start), j = source.indexOf(end, i + 1);
  assert.ok(i >= 0 && j > i, 'Expected source contract is missing: ' + start);
  return source.slice(i, j);
}
const navCode = extract(shared, 'const NAV_ITENS = [', 'const PERMISSOES_POR_PAGINA =');
const names = ['VER_ESTOQUE','EDITAR_ESTOQUE','VER_COMPRAS','VER_VENDAS','VER_FINANCAS','GERENCIAR_FUNCIONARIOS','VER_LOGS'];
const permissions = Object.fromEntries(names.map(x => [x, x]));

function render({perms=names,pin=false,active='dashboard',collapsed=false,original=false}={}) {
  const nav = {innerHTML:'',querySelectorAll:()=>[]};
  const document = {getElementById:()=>nav,querySelector:()=>({classList:{contains:()=>collapsed}})};
  vm.runInNewContext(navCode+'\nrenderSidebar();', {
    PERMISSOES:permissions,document,window:{StockOsDesignReview:{mode:original?'original':'novo'}},
    usuario:{viaPin:pin},paginaAtual:()=>active,
    temAlgumaPermissao:arr=>arr.some(p=>perms.includes(p)),iconMarkup:()=>'<svg></svg>'
  });
  return nav.innerHTML;
}
const admin = render({active:'estoque'});
assert.equal((admin.match(/href=/g)||[]).length,10);
assert.match(admin,/aria-current="page"/);
assert.match(admin,/data-nav-group="Estoque e produtos" open/);
const restricted = render({perms:['VER_ESTOQUE']});
assert.match(restricted,/\/estoque\.html/);
assert.doesNotMatch(restricted,/\/cardapio\.html|\/caixas\.html|\/logs\.html/);
const pin = render({pin:true});
assert.equal((pin.match(/href=/g)||[]).length,2);
assert.match(pin,/\/comandas\.html/);
assert.match(pin,/\/caixas\.html/);
const collapsed = render({collapsed:true});
assert.doesNotMatch(collapsed,/<details/);
assert.equal((collapsed.match(/href=/g)||[]).length,10);
assert.match(collapsed,/aria-label="Estoque"/);
const original = render({original:true});
assert.doesNotMatch(original,/<details/);
assert.equal((original.match(/href=/g)||[]).length,10);

class Element {
  constructor(tag) {
    this.tag=tag; this.children=[]; this.attrs={}; this.className='';
    this.style={setProperty:(k,v)=>{this.style[k]=v}};
    this.classList={add:(name)=>{this.className+=' '+name}};
  }
  append(...nodes) { for(const n of nodes) this.appendChild(n); }
  appendChild(n) { if(n.parent)n.remove(); n.parent=this; this.children.push(n); return n; }
  prepend(n) { if(n.parent)n.remove(); n.parent=this; this.children.unshift(n); }
  remove() { if(this.parent){this.parent.children=this.parent.children.filter(x=>x!==this);this.parent=null;} }
  before(n) { n.parent=this.parent; this.parent.children.splice(this.parent.children.indexOf(this),0,n); }
  closest(sel) { let x=this; while(x){if('.'+x.className===sel)return x;x=x.parent;}return null; }
  querySelector(sel) {
    for(const n of this.children){if(n.className.split(' ').includes(sel.slice(1)))return n;const v=n.querySelector(sel);if(v)return v;}
    return null;
  }
  setAttribute(k,v) { this.attrs[k]=v; }
}
const dashCode = extract(dashboard,'function renderDashboardOverview(','function renderAcoesOperacionais(');
function makeDash(allow=true,enabled=true) {
  const parent=new Element('main'),grid=new Element('div');
  grid.className='summary-grid'; parent.appendChild(grid);
  const document={getElementById:()=>grid,querySelector:()=>enabled?parent:null,createElement:t=>new Element(t)};
  const fn=vm.runInNewContext(dashCode+'\nrenderDashboardOverview;',{
    document,PODE_VER_FINANCEIRO_DASH:allow,formatarMoedaBR:n=>'R$ '+n.toFixed(2)
  });
  return {parent,grid,fn};
}
const x=makeDash();
x.fn([{numeroCaixa:1,totalVendas:120,status:'ABERTO'},{numeroCaixa:2,totalVendas:60,status:'ABERTO'},{numeroCaixa:3,totalVendas:null,status:'ABERTO'},{numeroCaixa:4,totalVendas:-10,status:'ABERTO'}],{totalVendas:180},true);
const panel=x.parent.querySelector('.stockos-sales-panel'),chart=panel.querySelector('.stockos-sales-chart');
assert.equal(chart.children.length,3);
assert.equal(chart.children[1].querySelector('.stockos-sales-bar').style['--bar-height'],'25%');
assert.match(chart.children[2].querySelector('.stockos-sales-bar').className,/stockos-sales-bar--negative/);
assert.match(panel.querySelector('.stockos-sales-caption').textContent,/acumuladas por caixa/);
assert.equal(panel.querySelector('.stockos-sales-total').textContent,'R$ 180.00');
x.fn([],null,false);
assert.equal(x.parent.children.length,1);
assert.equal(x.parent.children[0].children.length,2);
assert.match(x.parent.querySelector('.stockos-sales-empty').textContent,/Não foi possível/);
assert.equal(x.parent.querySelector('.stockos-sales-total').textContent,'—');
const noPerm=makeDash(false);
noPerm.fn([{totalVendas:300}],{totalVendas:300},true);
assert.equal(noPerm.parent.querySelector('.stockos-sales-panel'),null);
const old=makeDash(true,false);
old.fn([],{totalVendas:4},true);
assert.equal(old.parent.children[0],old.grid);
assert.match(css,/\.app\.stockos-shell-v2\.is-collapsed/);
assert.match(css,/prefers-reduced-motion:reduce/);
console.log('PASS shell redesign: permission-aware menu, PIN, collapsed and original modes, financial data and unavailable state.');

function search(query, perms=names, pin=false) {
  return vm.runInNewContext(navCode+'\nstockosFindModules(query).map(item=>item.id);', {
    PERMISSOES:permissions,usuario:{viaPin:pin},query,
    temAlgumaPermissao:arr=>arr.some(p=>perms.includes(p))
  }).join(',');
}
assert.equal(search('funcionarios'),'funcionarios');
assert.equal(search('COTAÇÃO'),'cotacoes');
assert.equal(search('vendas caixa'),'caixas');
assert.equal(search('funcionarios',['VER_ESTOQUE']),'');
assert.equal(search('estoque',names,true),'');
assert.equal(search('pdv',names,true),'caixas');
assert.equal(search('sem correspondencia'),'');
const moveCode=extract(dashboard,'function renderRecentMovementTable(','// ── Resumo financeiro');
const list=new Element('div');
list.replaceChildren=function(...nodes){this.children=[];this.append(...nodes);};
const renderMoves=vm.runInNewContext(moveCode+'\nrenderRecentMovementTable;',{
  document:{createElement:t=>new Element(t)}
});
renderMoves(list,[{produto:'<img src=x onerror=alert(1)>',tipo:'SAIDA',quantidade:2,dataHora:'2026-11-26T10:35:00'}]);
const moveTable=list.children[0].children[0];
assert.equal(moveTable.tag,'table');
const moveRow=moveTable.children[1].children[0];
assert.equal(moveRow.children[0].textContent,'<img src=x onerror=alert(1)>');
assert.equal(moveRow.children[2].textContent,'−2 un.');
assert.equal(moveRow.children[3].textContent,'10:35');
assert.match(css,/stockos-module-results\[hidden\]/);
console.log('PASS module search authorization and normalization, semantic movement table and literal product text.');

assert.match(css,/--stockos-panel-radius:\s*10px/);
assert.match(css,/--stockos-control-height:\s*40px/);
assert.match(css,/stockos-filter-row/);
assert.match(dashboard,/atalho\.onclick = \(\) =>/);
assert.match(dashboard,/painel\.scrollIntoView\(/);
assert.match(dashboard,/painel\.focus\(\{ preventScroll: true \}\)/);
console.log('PASS shared management visual tokens and reachable device configuration.');

const comandasSource = await readFile(new URL('js/comandas.js', root), 'utf8');
const caixasSource = await readFile(new URL('js/caixas.js', root), 'utf8');
new vm.Script(comandasSource);
new vm.Script(caixasSource);
assert.match(comandasSource,/aria-pressed="\$\{c\.numero === selecionada\}"/);
assert.match(comandasSource,/if \(numero !== selecionada \|\| versao !== detalheVersao\) return;/);
assert.match(comandasSource,/if \(numero === selecionada && versao === detalheVersao\) mostrarDetalhe\(detalhe\)/);
assert.match(comandasSource,/painel\.scrollIntoView/);
assert.match(comandasSource,/focoAnterior/);
assert.match(caixasSource,/stockos-pdv-entry/);
assert.match(css,/stockos-pdv-entry/);
assert.match(css,/stockos-shell-v2\) #pdv-shell/);
console.log('PASS cash and tabs design contracts: selected state, stale response guard, accessible mobile detail and scoped PDV presentation.');

const relatoriosSource = await readFile(new URL('js/relatorios.js', root), 'utf8');
new vm.Script(relatoriosSource);
assert.match(css,/\.fornecedores-page,\.notas-page,\.cotacoes-page,\.funcionarios-page,\.logs-page/);
assert.match(css,/@media screen \{/);
assert.match(css,/body\.relatorio-page \.stockos-report-screen/);
assert.match(relatoriosSource,/const versao = \+\+versaoRelatorio/);
assert.match(relatoriosSource,/if \(versao !== versaoRelatorio\) return;/);
assert.match(relatoriosSource,/imprimir\.disabled = true/);
assert.match(relatoriosSource,/imprimir\.disabled = false/);
assert.match(relatoriosSource,/cards\.replaceChildren\(\)/);
console.log('PASS administrative design scope and report state contracts.');

assert.match(caixasSource,/shellNovo \? '' : botaoPDV/);
assert.match(caixasSource,/\$\{shellNovo \? botaoPDV : ''\}/);
assert.match(css,/\.caixa-card-header \.stockos-pdv-entry/);
const reportIds = ['alert-relatorio','rel-cards','btn-imprimir-relatorio','rel-titulo','rel-grade','rel-abc','rel-data','rel-mes','rel-abc-inicio','rel-abc-fim','rel-abc-criterio'];
const reportNodes = Object.fromEntries(reportIds.map(id => [id, {
  textContent:'old',value:'2026-11-26',hidden:false,disabled:false,attrs:{},
  replaceChildren(){this.textContent='';},setAttribute(k,v){this.attrs[k]=v;}
}]));
const reportPending=[], reportRendered=[];
const reportLoader=relatoriosSource.slice(relatoriosSource.indexOf('async function carregarRelatorio()'),relatoriosSource.indexOf('function tabela('));
const reportApi=vm.runInNewContext(`let modoRelatorio='dia',versaoRelatorio=0;\n${reportLoader}\n({load:carregarRelatorio,mode:m=>modoRelatorio=m})`,{
  document:{getElementById:id=>reportNodes[id]},
  fetch:url=>new Promise(resolve=>reportPending.push({url,resolve})),
  renderRelatorio:d=>reportRendered.push(d.id),
  renderAbc:d=>reportRendered.push(d.id),
  safeReadErrorMessage:async()=>'Falha'
});
const reportFirst=reportApi.load();
assert.equal(reportNodes['btn-imprimir-relatorio'].disabled,true);
reportApi.mode('mes');
const reportSecond=reportApi.load();
reportPending[1].resolve({ok:true,json:async()=>({id:'new'})});
await reportSecond;
reportPending[0].resolve({ok:true,json:async()=>({id:'old'})});
await reportFirst;
assert.equal(reportRendered.join(','),'new');
assert.equal(reportNodes['btn-imprimir-relatorio'].disabled,false);
const reportFailed=reportApi.load();
reportPending[2].resolve({ok:false});
await reportFailed;
assert.ok(reportNodes['btn-imprimir-relatorio'].disabled && reportNodes['rel-grade'].hidden && reportNodes['rel-abc'].hidden);
assert.equal(reportNodes['alert-relatorio'].textContent,'Falha');
console.log('PASS visible POS action placement and concurrent report requests.');

const comandasHtml = await readFile(new URL('comandas.html', root), 'utf8');
assert.ok(comandasHtml.indexOf('id="btn-cobrar-comanda"') < comandasHtml.indexOf('id="comandas-detalhe-linhas"'));
assert.match(comandasSource,/detalheConfirmado !== selecionada/);
assert.match(comandasSource,/mostrarDetalhe\(null\);\n  \}\n  selecionada = numero/);
const tabPending=[], tabRendered=[];
const detailLoader=comandasSource.slice(comandasSource.indexOf('async function carregarDetalhe('),comandasSource.indexOf('function mostrarDetalhe('));
const tabApi=vm.runInNewContext(`let selecionada=9,detalheVersao=0;\n${detailLoader}\n({load:carregarDetalhe,select:n=>selecionada=n})`,{
  fetch:url=>new Promise(resolve=>tabPending.push({url,resolve})),
  mostrarDetalhe:d=>tabRendered.push(d?.numero ?? null),
  mostrarAlert:()=>{},lerErro:async()=> 'Falha'
});
const tabFirst=tabApi.load(9);
tabApi.select(20);
const tabSecond=tabApi.load(20);
tabPending[1].resolve({ok:true,json:async()=>({numero:20})});
await tabSecond;
tabPending[0].resolve({ok:true,json:async()=>({numero:9})});
await tabFirst;
assert.equal(tabRendered.join(','),'20');
const tabOlder=tabApi.load(20);
const tabNewer=tabApi.load(20);
tabPending[3].resolve({ok:true,json:async()=>({numero:21})});
await tabNewer;
tabPending[2].resolve({ok:true,json:async()=>({numero:22})});
await tabOlder;
assert.equal(tabRendered.join(','),'20,21');
console.log('PASS tab primary action and out-of-order detail responses.');

assert.match(dashboard,/version !== dashboardLoadVersion \|\| paginaAtual\(\) !== 'dashboard'/);
assert.match(dashboard,/renderReferenceDashboard\(produtos, caixasAtuais, metricas, pedir, version\)/);
const dashboardAlertList={innerHTML:''},dashboardAlertBadge={textContent:''},dashboardMovList={innerHTML:''};
const dashboardStates=vm.runInNewContext(
  dashboard.slice(dashboard.indexOf('function renderAlertas('),dashboard.indexOf('// ── Resumo financeiro'))+'\n({renderAlertas,renderMovimentacoes})',
  {document:{getElementById:id=>({'alerta-list':dashboardAlertList,'alerta-count':dashboardAlertBadge,'mov-list':dashboardMovList}[id])},
   emptyStateMarkup:options=>options.title,obterAlertasEstoque:()=>[],iconMarkup:()=>'',dataLocalHojeIso:()=> '2026-11-26'}
);
dashboardStates.renderAlertas(null);
assert.equal(dashboardAlertList.innerHTML,'Alertas indisponíveis');
assert.equal(dashboardAlertBadge.textContent,'—');
dashboardStates.renderAlertas([]);
assert.match(dashboardAlertList.innerHTML,/Todos os produtos estão em nível adequado/);
dashboardStates.renderMovimentacoes(null);
assert.equal(dashboardMovList.innerHTML,'Movimentações indisponíveis');
dashboardStates.renderMovimentacoes([]);
assert.equal(dashboardMovList.innerHTML,'Nenhuma movimentação hoje');
console.log('PASS dashboard request ordering and honest unavailable states.');

assert.match(dashboard,/referenceSeries\(chart,rows,compare\)/);
assert.match(css,/stockos-reference-grid/);
assert.match(dashboard,/rows.every\(r=>Number.isFinite\(r.previous\)\)/);

assert.match(dashboard,/report\.produtosParcial/);
assert.match(dashboard,/values\.reduce\(\(s,r\)=>s\+r\.value,0\)/);
