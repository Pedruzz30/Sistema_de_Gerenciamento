/* SPA-isolated, read-only audit workspace. */
(() => {
'use strict';
const AUDIT_LIMIT=2000;
const norm=v=>String(v??'').normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase().trim();
const pad=v=>String(v).padStart(2,'0');
function auditDate(value){
  if(typeof value!=='string')return null;
  const br=value.match(/^(\d{2})\/(\d{2})\/(\d{4}) (\d{2}):(\d{2}):(\d{2})$/);
  const iso=value.match(/^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})(?::(\d{2})(?:\.\d{1,9})?)?(Z|[+-]\d{2}:\d{2})?$/);
  if(!br&&!iso)return null;
  const [y,m,d,h,min,s]=br?[br[3],br[2],br[1],br[4],br[5],br[6]].map(Number):iso.slice(1,7).map(v=>Number(v||0));
  const check=new Date(0);check.setUTCFullYear(y,m-1,d);check.setUTCHours(h,min,s,0);
  if(check.getUTCFullYear()!==y||check.getUTCMonth()!==m-1||check.getUTCDate()!==d||h>23||min>59||s>59)return null;
  let parts=[y,m,d,h,min,s];
  if(iso?.[7]){const dt=new Date(value);if(!Number.isFinite(dt.getTime()))return null;parts=[dt.getFullYear(),dt.getMonth()+1,dt.getDate(),dt.getHours(),dt.getMinutes(),dt.getSeconds()];}
  const [Y,M,D,H,I,S]=parts,day=String(Y).padStart(4,'0')+'-'+pad(M)+'-'+pad(D),time=pad(H)+':'+pad(I)+':'+pad(S);
  return {day,time,date:pad(D)+'/'+pad(M)+'/'+String(Y).padStart(4,'0'),key:day+'T'+time};
}
function auditAction(code){
  const labels={CAIXA_ABERTO:'Caixa aberto',CAIXA_FECHADO:'Caixa fechado',NOTA_CONFIRMADA:'Nota confirmada',ESTOQUE_CONFERIDO:'Estoque conferido',VENDA_REGISTRADA:'Venda registrada',MUDAR_CLASSE_FUNCIONARIO:'Alteração de classe',DESATIVAR_FUNCIONARIO:'Desativação de funcionário',LOGIN_SUCESSO:'Login bem-sucedido',LOGIN_FALHA:'Falha de login'};
  return Object.prototype.hasOwnProperty.call(labels,code)?labels[code]:code?code.replace(/_/g,' ').toLowerCase().replace(/^./,v=>v.toUpperCase()):'Ação não informada';
}
function auditModel(records,filters={},page=1,size=15){
  if(!Array.isArray(records))return null;
  const all=records.map((v,index)=>({index,id:String(v.id??''),ru:v.usuarioRu!=null?String(v.usuarioRu):null,action:String(v.acao??''),description:String(v.descricao??''),rawDate:String(v.dataHora??''),date:auditDate(v.dataHora)}));
  const users=[...new Set(all.filter(v=>v.ru!==null).map(v=>v.ru))].sort((a,b)=>a.localeCompare(b,'pt-BR',{numeric:true}));
  const actions=[...new Set(all.map(v=>v.action).filter(Boolean))].sort((a,b)=>auditAction(a).localeCompare(auditAction(b),'pt-BR'));
  const order=(a,b)=>a.date&&b.date?(filters.sort==='old'?a.date.key.localeCompare(b.date.key):b.date.key.localeCompare(a.date.key))||b.id.localeCompare(a.id,'pt-BR',{numeric:true}):a.date?-1:b.date?1:a.index-b.index;
  const latest=all.filter(v=>v.date).sort((a,b)=>b.date.key.localeCompare(a.date.key))[0]?.date??null;
  const term=norm(filters.query),validRange=!(filters.from&&filters.to&&filters.from>filters.to);
  const rows=validRange?all.filter(v=>(!filters.action||v.action===filters.action)&&(!filters.user||(filters.user==='system'?v.ru===null:v.ru===filters.user))&&(!filters.from||(v.date&&v.date.day>=filters.from))&&(!filters.to||(v.date&&v.date.day<=filters.to))&&(!term||norm([v.id,'#'+v.id,v.ru,v.ru==null?'Sistema':'RU '+v.ru,v.action,auditAction(v.action),v.description].join(' ')).includes(term))).sort(order):[];
  size=[10,15,25,50].includes(Number(size))?Number(size):15;
  const pages=Math.max(1,Math.ceil(rows.length/size)),current=Math.min(pages,Math.max(1,Math.floor(Number(page)||1)));
  return {total:all.length,users,actions,system:all.filter(v=>v.ru===null).length,latest,validRange,count:rows.length,pages,page:current,from:rows.length?(current-1)*size+1:0,to:Math.min(current*size,rows.length),rows:rows.slice((current-1)*size,current*size)};
}
let todosLogs=[],ready=false,loading=false,version=0,page=1,refreshTime=null,visibleRows=[];
const get=id=>document.getElementById(id);
function fillOptions(id,values,label){
  const el=get(id),previous=el.value;
  el.replaceChildren();for(const [value,name] of [['',label],...values]){const option=document.createElement('option');option.value=value;option.textContent=name;el.appendChild(option);}
  el.value=[...el.options].some(o=>o.value===previous)?previous:'';
}
async function carregarLogs(){
  const tbody=get('tabela-logs');if(!tbody)return;
  const token=++version,current=()=>get('tabela-logs')===tbody&&token===version;
  loading=true;ready=false;refreshTime=null;renderTabela();
  try{
    const resp=await fetch('/api/logs?limit='+AUDIT_LIMIT);
    if(!resp.ok)throw Error('Consulta indisponível');
    const data=await resp.json();if(!current())return;
    if(!Array.isArray(data)||data.some(v=>!v||typeof v!=='object'||!['number','string'].includes(typeof v.id)))throw Error('Resposta inválida');
    todosLogs=data.slice(0,AUDIT_LIMIT);const model=auditModel(todosLogs);
    fillOptions('audit-action',model.actions.map(v=>[v,auditAction(v)+' · '+v]),'Todas as ações');
    fillOptions('audit-user',[...(model.system?[['system','Sistema / sem RU']]:[]),...model.users.map(v=>[v,'RU '+v])],'Todos');
    refreshTime=new Date();ready=true;
  }catch{if(current())todosLogs=[];}
  finally{if(current()){loading=false;renderTabela();}}
}
function renderResumo(model){
  const el=get('logs-summary');if(!el)return;
  const number=v=>v==null?'—':v.toLocaleString('pt-BR');
  const cards=[['Registros carregados',number(model?.total),'Até 2.000 eventos por consulta'],['Usuários identificados',number(model?.users.length),'RUs distintos nos registros'],['Tipos de ação',number(model?.actions.length),model?number(model.system)+' eventos sem RU':'Eventos sem RU: —'],['Evento mais recente',model?.latest?.date||'—',model?.latest?.time||'Sem data disponível']];
  el.innerHTML=cards.map(([l,v,s])=>'<div class="page-summary-card"><div class="page-summary-label">'+escapeHtml(l)+'</div><div class="page-summary-value">'+escapeHtml(v)+'</div><div class="page-summary-sub">'+escapeHtml(s)+'</div></div>').join('');
}
function renderTabela(){
  const tbody=get('tabela-logs');if(!tbody)return;
  const model=ready?auditModel(todosLogs,{query:get('busca').value,action:get('audit-action').value,user:get('audit-user').value,from:get('audit-from').value,to:get('audit-to').value,sort:get('audit-sort').value},page,get('audit-size').value):null;
  renderResumo(model);visibleRows=model?.rows||[];
  get('audit-refresh').disabled=loading;
  get('audit-refresh-time').textContent=refreshTime?'Consulta às '+refreshTime.toLocaleTimeString('pt-BR',{hour:'2-digit',minute:'2-digit'}):loading?'Consultando…':'Consulta indisponível';
  get('table-count').textContent=model?model.count+' de '+model.total+' carregados':'—';
  get('audit-filter-error').hidden=model?.validRange!==false;get('audit-filter-error').textContent=model?.validRange===false?'A data inicial não pode ser posterior à final.':'';
  get('audit-prev').disabled=!model||model.page<=1;get('audit-next').disabled=!model||model.page>=model.pages;
  get('audit-range').textContent=model?(model.count?model.from+'–'+model.to+' de '+model.count+' · página '+model.page+' de '+model.pages:'0 resultados'):'';
  if(!model||!model.rows.length){tbody.innerHTML='<tr><td colspan="6" class="empty">'+(!model?(loading?'Carregando registros…':'Não foi possível carregar a auditoria. Use Atualizar para tentar novamente.'):!model.validRange?'Corrija o intervalo de datas.':model.total?'Nenhum evento corresponde aos filtros.':'Nenhum evento registrado.')+'</td></tr>';return;}
  page=model.page;const esc=escapeHtml;
  tbody.innerHTML=model.rows.map((v,i)=>'<tr><td class="audit-id">#'+esc(v.id)+'</td><td><div class="table-cell-stack"><span class="table-cell-strong">'+esc(v.date?.date||'Data indisponível')+'</span><span class="table-cell-caption">'+esc(v.date?.time||'—')+'</span></div></td><td>'+esc(v.ru==null?'Sistema / sem RU':'RU '+v.ru)+'</td><td><div class="audit-event"><strong>'+esc(auditAction(v.action))+'</strong><small>'+esc(v.action||'—')+'</small></div></td><td><div class="audit-description">'+esc(v.description||'—')+'</div></td><td><button type="button" class="btn-secondary audit-view" data-audit-row="'+i+'" aria-label="Ver detalhes do evento '+esc(v.id)+'">Ver</button></td></tr>').join('');
  tbody.querySelectorAll('[data-audit-row]').forEach(button=>button.onclick=()=>openAuditDetail(visibleRows[Number(button.dataset.auditRow)],button));
}
let detailTrigger=null;
function openAuditDetail(row,button){
  if(!row)return;detailTrigger=button;
  const fields=[['ID do evento','#'+row.id],['Responsável',row.ru==null?'Sistema / sem RU':'RU '+row.ru],['Ação',auditAction(row.action)],['Código da ação',row.action||'—'],['Data e hora',row.date?row.date.date+' '+row.date.time:'Indisponível'],['Data original registrada',row.rawDate||'—']];
  get('audit-detail-fields').innerHTML=fields.map(([label,value])=>'<div><span>'+escapeHtml(label)+'</span><strong>'+escapeHtml(value)+'</strong></div>').join('');
  get('audit-detail-description').textContent=row.description||'Sem descrição registrada.';
  const modal=get('audit-detail');modal.classList.add('open');modal.querySelector('.modal-close')?.focus();
}
function fecharModal(id){if(id!=='audit-detail')return;get(id)?.classList.remove('open');if(detailTrigger?.isConnected)detailTrigger.focus();detailTrigger=null;}
function filtrar(){page=1;renderTabela();}
async function stockOSMount_logs(){
  if(typeof paginaAtual==='function'&&paginaAtual()!=='logs')return;if(!get('tabela-logs'))return;
  page=1;ready=false;detailTrigger=null;
  ['audit-action','audit-user','audit-from','audit-to','audit-sort','audit-size'].forEach(id=>get(id).onchange=filtrar);
  get('audit-clear').onclick=()=>{['busca','audit-action','audit-user','audit-from','audit-to'].forEach(id=>get(id).value='');get('audit-sort').value='new';filtrar();};
  get('audit-prev').onclick=()=>{page--;renderTabela();};get('audit-next').onclick=()=>{page++;renderTabela();};
  await carregarLogs();
}
window.StockOSPages=window.StockOSPages||{};
window.StockOSPages.logs={mount:stockOSMount_logs,activate(){Object.assign(window,{carregarLogs,renderResumo,renderTabela,filtrar,fecharModal});}};
if(/^\/logs(?:\.html)?\/?$/.test(location.pathname))window.StockOSPages.logs.activate();
document.addEventListener('DOMContentLoaded',stockOSMount_logs);
})();
