/* SPA-isolated screen: cotacoes. */
(() => {
'use strict';
const quoteNorm=v=>String(v??'').normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase().trim();
function quoteNumber(v){
  if(v==null||!['number','string'].includes(typeof v)||String(v).trim()==='')return null;
  if(typeof v==='string'&&!/^[+-]?\d+(?:\.\d+)?$/.test(v.trim()))return null;
  const n=Number(v);return Number.isFinite(n)?n:null;
}
function quoteMonth(v){return typeof v==='string'&&/^\d{4}-(0[1-9]|1[0-2])$/.test(v);}
function quotePrevious(v){
  if(!quoteMonth(v))return '';
  const [y,m]=v.split('-').map(Number);
  return m===1?String(y-1).padStart(4,'0')+'-12':String(y).padStart(4,'0')+'-'+String(m-1).padStart(2,'0');
}
function formatarMes(v){
  if(!quoteMonth(v))return '—';
  const [y,m]=v.split('-');return ['Jan','Fev','Mar','Abr','Mai','Jun','Jul','Ago','Set','Out','Nov','Dez'][Number(m)-1]+'/'+y;
}
const quoteMoney=n=>n==null?'—':n.toLocaleString('pt-BR',{style:'currency',currency:'BRL',maximumFractionDigits:2});
const quotePercent=n=>n==null?'—':(n>0?'+':'')+n.toLocaleString('pt-BR',{maximumFractionDigits:2})+'%';
const quoteInt=n=>n==null?'—':n.toLocaleString('pt-BR');
function quoteReportModel(records,query='',trend='',sort='high',page=1){
  if(!Array.isArray(records))return null;
  const all=records.map(c=>({id:String(c.produtoId??''),name:String(c.produtoNome||'Produto não informado'),previous:quoteNumber(c.precoAnterior),current:quoteNumber(c.precoAtual),absolute:quoteNumber(c.variacaoAbsoluta),percent:quoteNumber(c.variacaoPercentual),trend:['SUBIU','CAIU','ESTAVEL'].includes(String(c.tendencia).toUpperCase())?String(c.tendencia).toUpperCase():'UNKNOWN'}));
  const counts={SUBIU:0,CAIU:0,ESTAVEL:0,UNKNOWN:0};all.forEach(c=>counts[c.trend]++);
  const term=quoteNorm(query);
  const filtered=all.filter(c=>(!trend||c.trend===trend)&&quoteNorm(c.name+' '+c.id).includes(term));
  filtered.sort((a,b)=>sort==='name'?a.name.localeCompare(b.name,'pt-BR'):a.percent==null?(b.percent==null?0:1):b.percent==null?-1:sort==='low'?a.percent-b.percent:b.percent-a.percent);
  const size=15,pages=Math.max(1,Math.ceil(filtered.length/size)),current=Math.min(pages,Math.max(1,Math.floor(Number(page)||1)));
  return {total:all.length,counts,count:filtered.length,pages,page:current,from:filtered.length?(current-1)*size+1:0,to:Math.min(current*size,filtered.length),rows:filtered.slice((current-1)*size,current*size)};
}
function quoteHistoryModel(records){
  if(!Array.isArray(records))return null;
  const rows=records.filter(c=>quoteMonth(c.mes)).map(c=>({month:c.mes,price:quoteNumber(c.precoUnitario),qty:quoteNumber(c.quantidadeComprada),total:quoteNumber(c.gastoTotal)})).sort((a,b)=>b.month.localeCompare(a.month));
  const latest=rows[0],previous=latest?rows.find(r=>r.month===quotePrevious(latest.month)):null;
  const change=latest?.price!=null&&previous?.price>0?(latest.price-previous.price)/previous.price*100:null;
  return {rows,latest,previous,change,count:rows.length};
}
function quoteProducts(records,query=''){
  const term=quoteNorm(query);
  return (Array.isArray(records)?records:[]).filter(p=>Number.isSafeInteger(Number(p.id))&&Number(p.id)>0&&quoteNorm([p.nome,p.id,p.codigoBarras].join(' ')).includes(term)).sort((a,b)=>String(a.nome).localeCompare(String(b.nome),'pt-BR'));
}
function quoteInput(product,month,price,qty){
  const id=quoteNumber(product),p=quoteNumber(price),q=quoteNumber(qty);
  if(!Number.isSafeInteger(id)||id<=0||id>2147483647||!quoteMonth(month)||p==null||p<=0||!Number.isSafeInteger(q)||q<=0||q>2147483647)return null;
  // Preserve the entered decimal for Jackson BigDecimal, avoid binary arithmetic in the payload.
  if(!/^\d+(?:\.\d{1,2})?$/.test(String(price).trim()))return null;
  return {produtoId:id,mes:month,precoUnitario:String(price).trim(),quantidadeComprada:q};
}
let todosProdutos=null,report=null,history=null,reportPage=1,reportVersion=0,historyVersion=0,productVersion=0,registerPending=false,registerUncertain=false;
const get=id=>document.getElementById(id);
const currentMonth=()=>{const d=new Date();return d.getFullYear()+'-'+String(d.getMonth()+1).padStart(2,'0');};
function emptyMarkup(title,copy){return '<div class="quote-empty"><strong>'+escapeHtml(title)+'</strong><span>'+escapeHtml(copy)+'</span></div>';}
function fillProducts(selectId,searchId){
  const select=get(selectId),search=get(searchId);if(!select||!search)return;
  const old=select.value,rows=quoteProducts(todosProdutos,search.value);
  if(old&&!rows.some(p=>String(p.id)===old)){const selected=todosProdutos?.find(p=>String(p.id)===old);if(selected)rows.unshift(selected);}
  select.replaceChildren();
  const add=(v,t)=>{const o=document.createElement('option');o.value=v;o.textContent=t;select.appendChild(o);};
  add('',todosProdutos==null?'Catálogo indisponível':rows.length?'Selecione um produto':'Nenhum produto encontrado');
  rows.forEach(p=>add(String(p.id),p.nome+' · #'+p.id));select.value=old;
}
async function carregarProdutos(){
  const el=get('c-produto'),version=++productVersion;if(!el)return;
  try{const resp=await fetch('/api/produtos');if(!resp.ok)throw Error();const data=await resp.json();if(!Array.isArray(data))throw Error();if(version!==productVersion||get('c-produto')!==el)return;todosProdutos=data;}
  catch{if(version!==productVersion||get('c-produto')!==el)return;todosProdutos=null;}
  fillProducts('c-produto','quote-register-search');fillProducts('historico-produto','quote-history-search');renderResumo();updateRegister();
}
function abrirAba(id){
  document.querySelectorAll('.tab-panel').forEach(p=>p.classList.toggle('active',p.id===id));
  document.querySelectorAll('.tabs [role="tab"]').forEach(t=>{const active=t.getAttribute('aria-controls')===id;t.classList.toggle('active',active);t.setAttribute('aria-selected',String(active));});
  renderResumo();
}
function renderResumo(){
  const el=get('cotacao-summary');if(!el)return;
  const m=report?.month===get('relatorio-mes').value?quoteReportModel(report.records):null;
  const cards=[['Produtos no catálogo',todosProdutos==null?'—':quoteInt(todosProdutos.length),'Base cadastrada','neutral'],['Produtos comparados',m?quoteInt(m.total):'—',m?formatarMes(report.month)+' × '+formatarMes(quotePrevious(report.month)):'Consulte um período','neutral'],['Preços em alta',m?quoteInt(m.counts.SUBIU):'—',m?quoteInt(m.counts.ESTAVEL)+' estáveis':'Aumentos no custo unitário','warning'],['Preços em queda',m?quoteInt(m.counts.CAIU):'—','Reduções no custo unitário','success']];
  el.innerHTML=cards.map(([label,value,sub,tone])=>'<div class="page-summary-card" data-tone="'+tone+'"><div class="page-summary-label">'+escapeHtml(label)+'</div><div class="page-summary-value">'+escapeHtml(value)+'</div><div class="page-summary-sub">'+escapeHtml(sub)+'</div></div>').join('');
}
function renderComparacao(c){
  const label={SUBIU:'↑ Alta',CAIU:'↓ Queda',ESTAVEL:'Estável',UNKNOWN:'—'}[c.trend];
  return '<tr><td><strong>'+escapeHtml(c.name)+'</strong><small>#'+escapeHtml(c.id)+'</small></td><td>'+quoteMoney(c.previous)+'</td><td>'+quoteMoney(c.current)+'</td><td>'+ (c.absolute!=null&&c.absolute>0?'+':'')+quoteMoney(c.absolute)+'</td><td><span class="quote-trend" data-trend="'+c.trend+'">'+quotePercent(c.percent)+'</span><small>'+label+'</small></td></tr>';
}
function drawReport(){
  const el=get('relatorio-container');if(!el)return;
  const valid=report&&report.month===get('relatorio-mes').value;
  get('quote-filters').hidden=get('quote-pagination').hidden=!valid;
  if(!valid)return;
  const m=quoteReportModel(report.records,get('quote-search').value,get('quote-trend').value,get('quote-sort').value,reportPage);reportPage=m.page;
  get('quote-period').textContent=formatarMes(report.month)+' comparado a '+formatarMes(quotePrevious(report.month))+' · somente produtos com dados nos dois meses.';
  el.innerHTML=m.rows.length?'<div class="quote-table-wrap"><table class="quote-table"><thead><tr><th scope="col">Produto</th><th scope="col">Mês anterior</th><th scope="col">Mês atual</th><th scope="col">Diferença unitária</th><th scope="col">Variação</th></tr></thead><tbody>'+m.rows.map(renderComparacao).join('')+'</tbody></table></div>':emptyMarkup(m.total?'Nenhum resultado':'Nenhuma comparação disponível',m.total?'Altere os filtros para consultar outros produtos.':'São necessárias cotações do mesmo produto em dois meses consecutivos.');
  get('quote-range').textContent=m.count?m.from+'–'+m.to+' de '+m.count+' produtos · página '+m.page+' de '+m.pages:'0 resultados';
  get('quote-prev').disabled=m.page<=1;get('quote-next').disabled=m.page>=m.pages;
}
function invalidateReport(){
  reportVersion++;report=null;reportPage=1;get('quote-load-report').disabled=false;get('quote-filters').hidden=get('quote-pagination').hidden=true;
  get('quote-period').textContent='Compare os preços de compra em dois meses consecutivos.';
  get('relatorio-container').innerHTML=emptyMarkup('Consulte um período','Escolha o mês e consulte a comparação com o mês anterior.');renderResumo();
}
async function carregarRelatorio(){
  const month=get('relatorio-mes').value;if(!quoteMonth(month)){showToast('Selecione um mês válido.','error');return;}
  const el=get('relatorio-container'),version=++reportVersion,current=()=>get('relatorio-container')===el&&version===reportVersion&&get('relatorio-mes').value===month;
  report=null;reportPage=1;get('quote-filters').hidden=get('quote-pagination').hidden=true;get('quote-load-report').disabled=true;renderResumo();el.innerHTML=emptyMarkup('Consultando cotações…','Aguarde a consulta do período.');
  try{const resp=await fetch('/api/cotacoes/relatorio?mes='+encodeURIComponent(month));if(!resp.ok)throw Error();const data=await resp.json();if(!Array.isArray(data.comparacoes)||(data.mes!=null&&data.mes!==month))throw Error();if(!current())return;report={month,records:data.comparacoes};drawReport();renderResumo();}
  catch{if(current())el.innerHTML=emptyMarkup('Não foi possível consultar o relatório','Tente consultar o mês novamente.');}
  finally{if(current())get('quote-load-report').disabled=false;}
}
function invalidateHistory(){
  historyVersion++;history=null;get('quote-load-history').disabled=false;get('historico-container').innerHTML=emptyMarkup('Consulte um produto','Selecione o produto e consulte seu histórico.');
}
async function carregarHistorico(){
  const id=get('historico-produto').value;if(!/^\d+$/.test(id)){showToast('Selecione um produto.','error');return;}
  const el=get('historico-container'),version=++historyVersion,current=()=>get('historico-container')===el&&version===historyVersion&&get('historico-produto').value===id;
  history=null;get('quote-load-history').disabled=true;el.innerHTML=emptyMarkup('Consultando histórico…','Carregando os registros do produto.');
  try{
    const resp=await fetch('/api/cotacoes/produto/'+encodeURIComponent(id)+'/historico');if(!resp.ok)throw Error();const data=await resp.json();const m=quoteHistoryModel(data);if(!m||m.count!==data.length)throw Error();if(!current())return;history={id,records:data};
    if(!m.count){el.innerHTML=emptyMarkup('Nenhuma cotação registrada','Registre uma cotação para acompanhar o histórico.');return;}
    const name=todosProdutos?.find(p=>String(p.id)===id)?.nome||data[0]?.produtoNome||'Produto';
    el.innerHTML='<div class="quote-history-heading"><strong>'+escapeHtml(name)+'</strong><span>'+m.count+' registros</span></div><div class="quote-history-metrics">'+[['Último preço registrado',quoteMoney(m.latest.price),formatarMes(m.latest.month)],['Variação mensal',quotePercent(m.change),m.previous?formatarMes(m.previous.month):'Sem mês anterior'],['Último gasto registrado',quoteMoney(m.latest.total),formatarMes(m.latest.month)]].map(([l,v,s])=>'<div><span>'+l+'</span><strong>'+v+'</strong><small>'+s+'</small></div>').join('')+'</div><div class="table-wrap"><table class="quote-table"><thead><tr><th scope="col">Mês</th><th scope="col">Preço unitário</th><th scope="col">Quantidade comprada</th><th scope="col">Gasto total</th></tr></thead><tbody>'+m.rows.map(c=>'<tr><td>'+formatarMes(c.month)+'</td><td>'+quoteMoney(c.price)+'</td><td>'+quoteInt(c.qty)+'</td><td>'+quoteMoney(c.total)+'</td></tr>').join('')+'</tbody></table></div>';
  }catch{if(current())el.innerHTML=emptyMarkup('Não foi possível consultar o histórico','Tente consultar o produto novamente.');}
  finally{if(current())get('quote-load-history').disabled=false;}
}
function updateRegister(){
  if(!get('quote-submit'))return;
  const input=quoteInput(get('c-produto').value,get('c-mes').value,get('c-preco').value,get('c-qtd').value);
  get('quote-submit').disabled=registerPending||registerUncertain||!input||!todosProdutos?.some(p=>Number(p.id)===input.produtoId);
  get('quote-estimate').textContent=input?quoteMoney(Number(input.precoUnitario)*input.quantidadeComprada):'—';
}
async function registrarCotacao(){
  if(registerPending||registerUncertain)return;
  const input=quoteInput(get('c-produto').value,get('c-mes').value,get('c-preco').value,get('c-qtd').value);
  if(!input||!todosProdutos?.some(p=>Number(p.id)===input.produtoId)){mostrarAlert('alert-cotacao','Confira produto, mês, preço positivo e quantidade inteira.','error');return;}
  if(!confirm('Registrar a cotação de '+formatarMes(input.mes)+'? Se já existir um registro deste produto no mês, ele será substituído.'))return;
  const modal=get('modal-cotacao'),current=()=>get('modal-cotacao')===modal;
  const controls=[...modal.querySelectorAll('input,select,button')].map(el=>[el,el.disabled]);registerPending=true;controls.forEach(([el])=>el.disabled=true);
  limparAlert('alert-cotacao');
  try{
    const resp=await fetch('/api/cotacoes',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(input)});
    if(!current())return;
    if(!resp.ok){if(resp.status>=500)throw Error();mostrarAlert('alert-cotacao',await safeReadErrorMessage(resp,'Erro ao registrar.'),'error');return;}
    modal.classList.remove('open');showToast('Cotação registrada.','success');
    if(report)invalidateReport();if(history)invalidateHistory();
  }catch{
    if(current()){registerUncertain=true;mostrarAlert('alert-cotacao','Resultado não confirmado. Feche a janela e consulte o histórico do produto antes de registrar novamente.','error');if(report)invalidateReport();if(history)invalidateHistory();}
  }finally{registerPending=false;controls.forEach(([el,disabled])=>{if(el.isConnected)el.disabled=disabled;});if(current())updateRegister();}
}
function abrirModalCotacao(){
  if(registerPending)return;registerUncertain=false;limparAlert('alert-cotacao');
  get('quote-register-search').value='';get('c-produto').value='';get('c-preco').value='';get('c-qtd').value='';get('c-mes').value=get('relatorio-mes').value||currentMonth();fillProducts('c-produto','quote-register-search');abrirModal('modal-cotacao');updateRegister();
}
function abrirAba_r(id){abrirAba(id);}
function abrirModal(id){get(id).classList.add('open');}
function fecharModal(id){if(!registerPending)get(id).classList.remove('open');}
function fecharModalSeClicouFora(e,id){if(e.target.id===id)fecharModal(id);}
function mostrarAlert(id,msg,tipo){const el=get(id);el.textContent=msg;el.className=tipo?'alert-modal '+tipo:'alert-modal';}
function limparAlert(id){const el=get(id);if(el){el.textContent='';el.className='alert-modal';}}
async function stockOSMount_cotacoes(){
  if(typeof paginaAtual==='function'&&paginaAtual()!=='cotacoes')return;
  if(!get('relatorio-mes'))return;
  if(!get('relatorio-mes').value)get('relatorio-mes').value=currentMonth();
  get('relatorio-mes').onchange=invalidateReport;
  get('quote-search').oninput=()=>{reportPage=1;drawReport();};
  ['quote-trend','quote-sort'].forEach(id=>get(id).onchange=()=>{reportPage=1;drawReport();});
  get('quote-clear').onclick=()=>{get('quote-search').value='';get('quote-trend').value='';get('quote-sort').value='high';reportPage=1;drawReport();};
  get('quote-prev').onclick=()=>{reportPage--;drawReport();};get('quote-next').onclick=()=>{reportPage++;drawReport();};
  get('quote-history-search').oninput=()=>fillProducts('historico-produto','quote-history-search');
  get('historico-produto').onchange=invalidateHistory;
  get('quote-register-search').oninput=()=>{fillProducts('c-produto','quote-register-search');updateRegister();};
  ['c-produto','c-mes','c-preco','c-qtd'].forEach(id=>{get(id).oninput=updateRegister;get(id).onchange=updateRegister;});
  invalidateReport();invalidateHistory();renderResumo();await carregarProdutos();
}
window.StockOSPages=window.StockOSPages||{};
window.StockOSPages.cotacoes={mount:stockOSMount_cotacoes,activate(){Object.assign(window,{carregarProdutos,abrirAba,renderResumo,carregarRelatorio,renderComparacao,carregarHistorico,registrarCotacao,formatarMes,abrirModalCotacao,abrirAba_r,abrirModal,fecharModal,fecharModalSeClicouFora,mostrarAlert,limparAlert});}};
if(/^\/cotacoes(?:\.html)?\/?$/.test(location.pathname))window.StockOSPages.cotacoes.activate();
document.addEventListener('DOMContentLoaded',stockOSMount_cotacoes);
})();
