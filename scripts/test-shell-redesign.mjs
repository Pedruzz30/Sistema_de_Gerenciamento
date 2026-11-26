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

assert.match(dashboard,/referenceSeries\(chart,rows,compare,/);
assert.match(css,/stockos-reference-grid/);
assert.match(dashboard,/rows.every\(r=>Number.isFinite\(r.previous\)\)/);

assert.match(dashboard,/report\.produtosParcial/);
assert.match(dashboard,/values\.reduce\(\(s,r\)=>s\+r\.value,0\)/);

assert.match(dashboard,/organizarCabecalhoDashboard\(\)/);
assert.match(dashboard,/slot\.appendChild\(report\)/);
assert.match(dashboard,/utilities\.appendChild\(node\)/);
assert.match(css,/\.topbar \.topbar-right \{display:none!important;\}/);

const salesChartCode=extract(dashboard,'function referenceScale(','// Immediate offline fallback.');
const salesChartApi=vm.runInNewContext(salesChartCode+'\n({referenceScale,referenceComparison,referenceChartConfig,referenceAxisMoney})',{
 formatarMoedaBR:n=>'R$ '+Number(n).toFixed(2)
});
const chartRows=[{label:'20/11',date:'2026-11-20',previousDate:'2026-11-13',value:997,previous:500},{label:'21/11',date:'2026-11-21',previousDate:'2026-11-14',value:250,previous:500}];
const scale=salesChartApi.referenceScale(chartRows,true);
assert.equal(scale.min,0);assert.equal(scale.max,1000);assert.equal(scale.step,250);
const signedScale=salesChartApi.referenceScale([{value:-251},{value:999}],false);
assert.ok(signedScale.min<=-251 && signedScale.max>=999 && signedScale.step>0);
assert.equal(salesChartApi.referenceComparison(chartRows,true).percent,24.7);
assert.equal(salesChartApi.referenceComparison([{value:1,previous:0}],true),null);
assert.equal(salesChartApi.referenceComparison([{value:null,previous:1}],true),null);
const colors={accent:'#c45120',previous:'#dfb7a4',grid:'#ddd',muted:'#777',ink:'#111',paper:'#fff'};
const config=salesChartApi.referenceChartConfig(chartRows,true,colors);
assert.equal(config.type,'bar');assert.equal(config.data.datasets.length,2);
assert.equal(config.options.scales.x.offset,true);
assert.equal(config.options.scales.x.ticks.maxRotation,0);
assert.equal(config.options.scales.y.ticks.stepSize,250);
assert.equal(config.options.plugins.tooltip.callbacks.title([{dataIndex:0}]),'20/11/2026');
assert.match(config.options.plugins.tooltip.callbacks.label({dataIndex:0,datasetIndex:1,dataset:config.data.datasets[1],parsed:{y:500}}),/13\/11\/2026.*500\.00/);
assert.equal(salesChartApi.referenceChartConfig([{label:'09h',value:1}],false,colors).data.datasets.length,1);
assert.match(dashboard,/destroyReferenceSalesChart\(\)/);
assert.match(dashboard,/referenceFallback\(rows,compare,plot\)/);
assert.match(dashboard,/if\(!isCurrent\(\)\|\|!node.isConnected/);
console.log('PASS sales chart: rounded monetary scales, signed values, centered groups, complete comparisons, exact tooltip dates and offline fallback.');

const kpiGrid={innerHTML:'',closest:()=>({})};
const kpiContext={
 document:{getElementById:()=>kpiGrid,querySelector:()=>({})},PERMISSOES:permissions,
 obterAlertasEstoque:arr=>arr.filter(p=>['CRITICO','SEM_ESTOQUE','BAIXO','MODERADO'].includes(p.nivelEstoque)),
 temPermissao:()=>true,temAlgumaPermissao:()=>true,iconMarkup:()=>'<svg></svg>',
 formatarMoedaBR:n=>'R$ '+Number(n).toFixed(2)
};
const kpiRender=vm.runInNewContext(extract(dashboard,'function renderSummaryCards(','// No additional API')+'\nrenderSummaryCards;',kpiContext);
const kpiStock=[{nivelEstoque:'SEM_ESTOQUE'},{nivelEstoque:'CRITICO'},{nivelEstoque:'BAIXO'},{nivelEstoque:'ADEQUADO'}];
kpiRender(kpiStock,[{numeroCaixa:1,status:'ENCERRADO'},{numeroCaixa:1,status:'ABERTO'},{numeroCaixa:2,status:'ABERTO'}],{totalVendas:4053,ticketMedio:39.74});
assert.equal((kpiGrid.innerHTML.match(/data-kpi=/g)||[]).length,4);
assert.match(kpiGrid.innerHTML,/2 críticos · 1 outros/);
assert.match(kpiGrid.innerHTML,/Ticket médio R\$ 39\.74/);
assert.match(kpiGrid.innerHTML,/data-kpi="registers" data-state="success"/);
assert.match(kpiGrid.innerHTML,/stockos-kpi-suffix"> \/ 2/);
assert.doesNotMatch(kpiGrid.innerHTML,/↑|↓/);
kpiRender([],[],{totalVendas:null,ticketMedio:null},{produtosDisponiveis:false,caixasDisponiveis:false});
assert.equal((kpiGrid.innerHTML.match(/Dados indisponíveis/g)||[]).length,4);
assert.doesNotMatch(kpiGrid.innerHTML,/R\$ 0\.00|Nenhum alerta/);
kpiRender([{nivelEstoque:'BAIXO'}],[],null);
assert.match(kpiGrid.innerHTML,/data-kpi="alerts" data-state="warning"/);
assert.match(kpiGrid.innerHTML,/filtro=baixo/);
kpiRender([],[],{totalVendas:0,ticketMedio:0});
assert.match(kpiGrid.innerHTML,/Nenhum alerta/);
assert.match(kpiGrid.innerHTML,/R\$ 0\.00/);
kpiContext.temPermissao=()=>false;kpiContext.temAlgumaPermissao=()=>true;
// Evaluate a fresh context to ensure permission filtering in the VM.
vm.runInNewContext(extract(dashboard,'function renderSummaryCards(','// No additional API')+'\nrenderSummaryCards([],[],null);',kpiContext);
assert.doesNotMatch(kpiGrid.innerHTML,/data-kpi="products"|data-kpi="alerts"/);
assert.match(kpiGrid.innerHTML,/data-kpi="sales"/);
assert.match(css,/stockos-kpi-heading/);
console.log('PASS compact KPI panel: factual statuses, permission filtering, deduplicated registers and unavailable values.');

const rankingApi=vm.runInNewContext(extract(dashboard,'function referenceRankingModel(','function renderReferenceRanking(')+'\nreferenceRankingModel;',{});
const rankSample={produtos:[{nome:'A',quantidade:11,valor:275},{nome:'B',quantidade:11,valor:308},{nome:'C',quantidade:10,valor:350},{nome:'D',quantidade:1,valor:67}]};
const byQty=rankingApi(rankSample,'quantity',2),byRevenue=rankingApi(rankSample,'revenue',2);
assert.equal(byQty.rows.map(p=>p.name).join(','),'B,A');
assert.equal(byRevenue.rows.map(p=>p.name).join(','),'C,B');
assert.equal(byQty.total,1000);
assert.equal(byQty.topRevenue,583);
assert.ok(Math.abs(byQty.topShare-58.3)<1e-8);
assert.equal(byQty.rows[0].position,1);
assert.equal(rankingApi(null),null);
assert.equal(rankingApi({produtos:[]}).topShare,null);
assert.equal(rankingApi({produtos:[{nome:'A',quantidade:1,valor:-1},{nome:'B',quantidade:1,valor:2}]}).topShare,null);
assert.equal(rankingApi({...rankSample,produtosParcial:true}).partial,true);
assert.equal(rankingApi({produtos:[{nome:'X',quantidade:1,valor:null}]}).partial,true);
assert.match(dashboard,/renderReferenceRanking\(rankPanel,report\)/);
console.log('PASS product ranking: stable ordering, full denominator, partial data and unavailable shares.');

const distributionsApi=vm.runInNewContext(extract(dashboard,'function referenceStockDistribution(','function referenceDonut(node,')+'\n({referenceStockDistribution,referencePaymentDistribution,referenceDonutModel});',{});
const stockBreakdown=distributionsApi.referenceStockDistribution([{nivelEstoque:'ADEQUADO'},{nivelEstoque:'BAIXO'},{nivelEstoque:'MODERADO'},{nivelEstoque:'SEM_ESTOQUE'},{nivelEstoque:'CRITICO'},{nivelEstoque:null},{ativo:false,nivelEstoque:'CRITICO'}]);
assert.equal(stockBreakdown.total,6);
assert.equal(stockBreakdown.rows.map(r=>r.value).join(','),'1,2,2,1');
assert.equal(stockBreakdown.rows[0].color,'var(--ok)');
assert.equal(stockBreakdown.rows[2].color,'var(--bad)');
assert.equal(distributionsApi.referenceStockDistribution(null),null);
assert.equal(distributionsApi.referenceStockDistribution([]).adequate,null);
const paymentsBreakdown=distributionsApi.referencePaymentDistribution({porForma:[{forma:'DINHEIRO',rotulo:'Dinheiro',valor:798},{forma:'PIX',rotulo:'Pix',valor:1777},{forma:'CARTAO',rotulo:'Cartão',valor:1478}]});
assert.equal(paymentsBreakdown.map(r=>r.key).join(','),'PIX,CARTAO,DINHEIRO');
const donutModel=distributionsApi.referenceDonutModel(paymentsBreakdown);
assert.equal(donutModel.total,4053);
assert.ok(Math.abs(donutModel.entries.reduce((s,r)=>s+r.share,0)-100)<1e-8);
assert.equal(distributionsApi.referencePaymentDistribution({porForma:[{rotulo:'Pix',valor:null}]}),null);
assert.equal(distributionsApi.referenceDonutModel([{value:-1},{value:2}]).comparable,false);
assert.equal(distributionsApi.referenceDonutModel([{value:0}]).entries[0].share,null);
assert.equal(distributionsApi.referenceDonutModel(null),null);
assert.match(css,/stockos-donut-percent/);
console.log('PASS distribution panel: semantic inventory levels, stable payment colors, exact totals, shares and signed values.');

const priorityNodes={'daily-actions':{hidden:true},'daily-actions-list':{innerHTML:''},'daily-actions-count':{textContent:''}};
const priorityContext={
 document:{getElementById:id=>priorityNodes[id],querySelector:()=>({})},
 PODE_VER_ESTOQUE_DASH:true,PODE_VER_FINANCEIRO_DASH:true,
 obterAlertasEstoque:arr=>arr.filter(p=>p.ativo!==false&&['CRITICO','SEM_ESTOQUE','BAIXO','MODERADO'].includes(p.nivelEstoque)),
 escapeHtml:String,iconMarkup:()=>'<svg></svg>'
};
const priorityCode=extract(dashboard,'function renderAcoesOperacionais(','// ── Seções por permissão');
const priorityRender=vm.runInNewContext(priorityCode+'\nrenderAcoesOperacionais;',priorityContext);
priorityRender([{nivelEstoque:'SEM_ESTOQUE'},{nivelEstoque:'BAIXO'}],[{numeroCaixa:1,status:'ABERTO'},{numeroCaixa:1,status:'ABERTO'}],{produtosDisponiveis:true,caixasDisponiveis:true});
assert.equal(priorityNodes['daily-actions-count'].textContent,'2 prioridades pendentes');
assert.match(priorityNodes['daily-actions-list'].innerHTML,/data-operation="critical"/);
assert.match(priorityNodes['daily-actions-list'].innerHTML,/data-operation="low"/);
assert.match(priorityNodes['daily-actions-list'].innerHTML,/data-operation="cash-open"/);
assert.match(priorityNodes['daily-actions-list'].innerHTML,/1 <small>caixa<\/small>/);
assert.ok(priorityNodes['daily-actions-list'].innerHTML.indexOf('data-operation="critical"')<priorityNodes['daily-actions-list'].innerHTML.indexOf('data-operation="low"'));
priorityRender([],[],{produtosDisponiveis:true,caixasDisponiveis:true});
assert.equal(priorityNodes['daily-actions-count'].textContent,'Nenhuma prioridade pendente');
assert.match(priorityNodes['daily-actions-list'].innerHTML,/Nenhum caixa em operação/);
priorityRender([],[],{produtosDisponiveis:false,caixasDisponiveis:false});
assert.equal(priorityNodes['daily-actions-count'].textContent,'2 prioridades pendentes');
assert.doesNotMatch(priorityNodes['daily-actions-list'].innerHTML,/Sem alertas|data-operation="cash-closed"/);
priorityContext.PODE_VER_ESTOQUE_DASH=false;
vm.runInNewContext(priorityCode+'\nrenderAcoesOperacionais([],[],{produtosDisponiveis:false,caixasDisponiveis:true});',priorityContext);
assert.doesNotMatch(priorityNodes['daily-actions-list'].innerHTML,/estoque\.html/);
assert.match(css,/stockos-priority-action/);
console.log('PASS operational priorities: semantic urgency, unique open registers, unavailable states and permissions.');

const cashSessionsApi=vm.runInNewContext(extract(dashboard,'function referenceCashSessions(','function renderReferenceCashSessions(')+'\nreferenceCashSessions;',{});
const cashSample=[
 {numeroCaixa:1,status:'ABERTO',dataAbertura:'2026-11-25T09:00:00',totalVendas:1081,quantidadeVendas:20,nomeOperador:'Marina'},
 {numeroCaixa:2,status:'ENCERRADO',dataAbertura:'2026-11-26T09:00:00',totalVendas:1428,quantidadeVendas:30},
 {numeroCaixa:3,status:'ENCERRADO',dataAbertura:'2026-11-25T09:00:00',dataEncerramento:'2026-11-25T18:00:00',totalVendas:999},
 {numeroCaixa:4,status:'ABERTO',totalVendas:null,quantidadeVendas:null}
];
const cashModel=cashSessionsApi(cashSample,'2026-11-26');
assert.equal(cashModel.sessions.length,3);
assert.equal(cashModel.open,2);
assert.equal(cashModel.sessions.map(s=>s.number).join(','),'1,4,2');
assert.equal(cashModel.sessions[0].sales,1081);
assert.equal(cashModel.sessions[1].sales,null);
assert.equal(cashModel.sessions[1].quantity,null);
assert.equal(cashSessionsApi(null,'2026-11-26'),null);
assert.equal(cashSessionsApi([],'2026-11-26').open,0);
assert.equal(cashSessionsApi([{numeroCaixa:1,status:'ENCERRADO',dataAbertura:'2026-11-25T10:00:00',dataEncerramento:'2026-11-26T09:00:00'}],'2026-11-26').sessions.length,1);
assert.match(dashboard,/renderFinanceiro\(caixasAtuais, metricas, caixasDisponiveis\)/);
assert.match(css,/stockos-cash-table/);
console.log('PASS secondary dashboard: relevant cash sessions, no historical mislabeling, nullable data and open register counts.');
