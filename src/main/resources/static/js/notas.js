/* SPA-isolated screen: notas. Retains direct HTML navigation. */
(() => {
'use strict';
let fornecedoresAtivos = [];
let todosOsProdutos = [];
let notaAtual = null;
let stepAtual = 1;

async function stockOSMount_notas() {
  if (typeof paginaAtual === 'function' && paginaAtual() !== 'notas') return;
  await Promise.all([
      carregarNotas(),
      carregarFornecedores(),
      carregarTodosProdutos()
    ]);
}

const NOTE_LIST_STATUS=['PENDENTE','CONFIRMADA','PAGA','CANCELADA'];
function noteListModel(records,filters={},page=1,pageSize=10){
  if(!Array.isArray(records))return null;
  const norm=v=>String(v??'').normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase().trim();
  const cents=v=>{
    if(v==null||!['number','string'].includes(typeof v)||String(v).trim()==='')return null;
    const n=Number(v),c=Math.round(n*100);
    return Number.isFinite(n)&&n>=0&&Number.isSafeInteger(c)&&Math.abs(n*100-c)<1e-6?c:null;
  };
  const dateKey=v=>{
    if(typeof v!=='string'||!v)return '';
    const d=new Date(v);if(!Number.isFinite(d.getTime()))return '';
    return d.getFullYear()+'-'+String(d.getMonth()+1).padStart(2,'0')+'-'+String(d.getDate()).padStart(2,'0');
  };
  const all=records.map(n=>({id:String(n.id),supplier:String(n.fornecedorNome||'—'),status:NOTE_LIST_STATUS.includes(n.status)?n.status:'UNKNOWN',items:Array.isArray(n.itens)?n.itens.length:null,cents:cents(n.total),date:n.dataEmissao,dateKey:dateKey(n.dataEmissao),nfe:String(n.numeroNfe||''),key:String(n.chaveAcesso||'')}));
  const groups=Object.fromEntries([...NOTE_LIST_STATUS,'UNKNOWN'].map(s=>[s,{count:0,cents:0}]));
  for(const n of all){const g=groups[n.status];g.count++;g.cents=g.cents==null||n.cents==null||!Number.isSafeInteger(g.cents+n.cents)?null:g.cents+n.cents;}
  const {query='',status='',from='',to=''}=filters,invalid=!!(from&&to&&from>to),term=norm(query);
  const filtered=invalid?[]:all.filter(n=>(!status||n.status===status)&&(!from||n.dateKey&&n.dateKey>=from)&&(!to||n.dateKey&&n.dateKey<=to)&&norm([n.id,'#'+n.id,n.supplier,n.nfe,n.key].join(' ')).includes(term))
    .sort((a,b)=>(Date.parse(b.date)||0)-(Date.parse(a.date)||0)||b.id.localeCompare(a.id,'pt-BR',{numeric:true}));
  const size=[10,25,50].includes(Number(pageSize))?Number(pageSize):10,pages=Math.max(1,Math.ceil(filtered.length/size)),current=Math.min(pages,Math.max(1,Math.floor(Number(page)||1)));
  return {total:all.length,groups,invalid,count:filtered.length,page:current,pages,from:filtered.length?(current-1)*size+1:0,to:Math.min(current*size,filtered.length),rows:filtered.slice((current-1)*size,current*size)};
}
let noteListRecords=null,noteListVersion=0,noteListPage=1,noteListLoading=false,noteListFailed=false,noteListMutation=false,noteListUncertain=false;
function bindNoteListControls(){
  const get=id=>document.getElementById(id);
  get('note-list-search').oninput=()=>{noteListPage=1;drawNoteList();};
  ['note-list-status','note-list-from','note-list-to','note-list-size'].forEach(id=>get(id).onchange=()=>{noteListPage=1;drawNoteList();});
  get('note-list-prev').onclick=()=>{noteListPage--;drawNoteList();};
  get('note-list-next').onclick=()=>{noteListPage++;drawNoteList();};
  get('note-list-clear').onclick=()=>{['note-list-search','note-list-status','note-list-from','note-list-to'].forEach(id=>get(id).value='');noteListPage=1;drawNoteList();};
  get('note-list-refresh').onclick=()=>void carregarNotas();
}
async function carregarNotas(){
  const tbody=document.getElementById('tabela-notas');if(!tbody)return false;
  const version=++noteListVersion,current=()=>version===noteListVersion&&document.getElementById('tabela-notas')===tbody;
  noteListLoading=true;noteListFailed=false;bindNoteListControls();drawNoteList();
  try{
    const resp=await fetch('/api/notas');if(!resp.ok)throw Error('Falha na consulta');
    const notas=await resp.json();if(!Array.isArray(notas)||notas.some(n=>!n||!(/^\d+$/.test(String(n.id)))))throw Error('Resposta inválida');
    if(!current())return false;noteListRecords=notas;noteListUncertain=false;return true;
  }catch{if(current()){noteListRecords=null;noteListFailed=true;}return false;}
  finally{if(current()){noteListLoading=false;drawNoteList();}}
}

function noteSupplierModel(records,query='',page=1,pageSize=5){
  if(!Array.isArray(records))return null;
  const norm=s=>String(s??'').normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase().trim();
  const unique=new Map();
  for(const f of records){
    const id=Number(f?.id);
    if(Number.isSafeInteger(id)&&id>0&&f.ativo!==false)unique.set(id,{id,name:String(f.nome||'Fornecedor sem nome'),cnpj:String(f.cnpj||''),phone:String(f.telefone||''),count:Array.isArray(f.produtos)?new Set(f.produtos.filter(p=>p?.id!=null).map(p=>String(p.id))).size:null});
  }
  const all=[...unique.values()].sort((a,b)=>a.name.localeCompare(b.name,'pt-BR')||a.id-b.id);
  const term=norm(query),digits=term.replace(/\D/g,'');
  const numeric=/^[0-9.\s\/-]+$/.test(term)&&digits.length>0;
  const filtered=all.filter(f=>!term||norm([f.name,f.cnpj,f.id].join(' ')).includes(term)||(numeric&&f.cnpj.replace(/\D/g,'').includes(digits)));
  const size=Math.max(1,Math.floor(Number(pageSize)||5)),pages=Math.max(1,Math.ceil(filtered.length/size)),current=Math.min(pages,Math.max(1,Math.floor(Number(page)||1)));
  return {all,total:all.length,count:filtered.length,page:current,pages,from:filtered.length?(current-1)*size+1:0,to:Math.min(current*size,filtered.length),rows:filtered.slice((current-1)*size,current*size)};
}
let noteSupplierPage=1,noteSupplierLoadVersion=0,noteSupplierLoading=false,noteSupplierFailed=false,noteOpening=false,noteOpeningUncertain=false;
function noteSupplierCnpj(raw){
  return /^\d{14}$/.test(raw)?raw.replace(/^(\d{2})(\d{3})(\d{3})(\d{4})(\d{2})$/,'$1.$2.$3/$4-$5'):raw||'—';
}
// Reuse the same action nodes, IDs and handlers in either shell.
function placeNoteSupplierActions(managed, get){
  const block=get('note-supplier-action-block');
  const target=get(managed?'note-supplier-action-slot':'note-supplier-legacy-slot');
  if(block&&target&&block.parentElement!==target)target.appendChild(block);
}
function drawNoteSuppliers(){
  const get=id=>document.getElementById(id),workspace=get('note-supplier-workspace'),select=get('s1-fornecedor');
  if(!workspace||!select)return;
  const managed=!!document.querySelector('.app.stockos-shell-v2');
  workspace.hidden=!managed;get('note-supplier-native').hidden=managed;
  placeNoteSupplierActions(managed,get);
  const ready=!noteSupplierLoading&&!noteSupplierFailed;
  const model=noteSupplierModel(fornecedoresAtivos,get('note-supplier-search').value,noteSupplierPage);
  const selected=model?.all.find(f=>String(f.id)===select.value);
  const locked=noteOpening||noteOpeningUncertain;
  get('btn-step1-avancar').disabled=!ready||!selected||locked;
  select.disabled=!ready||locked;
  if(!managed)return;
  get('note-supplier-count').textContent=ready?model.total+' cadastrados':'—';
  get('note-supplier-search').disabled=!ready||locked;
  get('note-supplier-refresh').disabled=noteSupplierLoading||noteOpening;
  get('note-supplier-prev').disabled=!ready||locked||model.page<=1;
  get('note-supplier-next').disabled=!ready||locked||model.page>=model.pages;
  get('note-supplier-range').textContent=ready?(model.count?model.from+'–'+model.to+' de '+model.count+' · página '+model.page+' de '+model.pages:'0 resultados'):'';
  const results=get('note-supplier-results'),summary=get('note-supplier-selected'),esc=escapeHtml;
  if(!ready){results.textContent=noteSupplierLoading?'Consultando fornecedores ativos…':'Não foi possível carregar os fornecedores. Use Atualizar.';summary.textContent='Seleção indisponível.';return;}
  noteSupplierPage=model.page;
  summary.innerHTML=selected?'<div class="note-supplier-selection-state"><span aria-hidden="true">✓</span> Fornecedor selecionado</div><div class="note-supplier-selected-name">'+esc(selected.name)+'</div><div class="note-supplier-summary-meta"><span>CNPJ</span><strong>'+esc(noteSupplierCnpj(selected.cnpj))+'</strong><span>Telefone comercial</span><strong>'+esc(selected.phone||'—')+'</strong><span>Produtos vinculados</span><strong>'+(selected.count??'—')+'</strong></div>':'<div class="note-supplier-empty-selection"><strong>Escolha um fornecedor</strong><span>Selecione uma empresa na lista para conferir seus dados e abrir a nota.</span></div>';
  if(!model.rows.length){results.textContent=model.total?'Nenhum fornecedor corresponde à pesquisa.':'Nenhum fornecedor ativo cadastrado.';return;}
  results.innerHTML='<table class="note-supplier-table"><thead><tr><th scope="col">Fornecedor</th><th scope="col">Vínculos</th><th scope="col">Seleção</th></tr></thead><tbody>'+model.rows.map(f=>{
    const active=selected?.id===f.id;
    return '<tr'+(active?' class="is-selected"':'')+'><td><strong>'+esc(f.name)+'</strong><small>'+esc(noteSupplierCnpj(f.cnpj))+' · #'+f.id+'</small></td><td>'+(f.count??'—')+'</td><td><button type="button" data-note-supplier="'+f.id+'" aria-pressed="'+active+'" aria-label="Selecionar '+esc(f.name)+'"'+(locked?' disabled':'')+'>'+(active?'✓ Selecionado':'Selecionar')+'</button></td></tr>';
  }).join('')+'</tbody></table>';
  results.querySelectorAll('[data-note-supplier]').forEach(b=>b.onclick=()=>{
    if(noteOpening||noteOpeningUncertain)return;
    select.value=b.dataset.noteSupplier;drawNoteSuppliers();
    results.querySelector('[data-note-supplier="'+b.dataset.noteSupplier+'"]')?.focus({preventScroll:true});
  });
}
function bindNoteSupplierControls(){
  const get=id=>document.getElementById(id);
  get('note-supplier-search').oninput=()=>{noteSupplierPage=1;drawNoteSuppliers();};
  get('s1-fornecedor').onchange=drawNoteSuppliers;
  get('note-supplier-prev').onclick=()=>{noteSupplierPage--;drawNoteSuppliers();};
  get('note-supplier-next').onclick=()=>{noteSupplierPage++;drawNoteSuppliers();};
  get('note-supplier-refresh').onclick=()=>void carregarFornecedores();
}
async function carregarFornecedores() {
  const select=document.getElementById('s1-fornecedor');
  if(!select)return;
  const version=++noteSupplierLoadVersion;
  const current=()=>version===noteSupplierLoadVersion&&document.getElementById('s1-fornecedor')===select;
  noteSupplierLoading=true;noteSupplierFailed=false;
  bindNoteSupplierControls();drawNoteSuppliers();
  try {
    const resp=await fetch('/api/fornecedores/ativos');
    if(!resp.ok)throw Error('Falha na consulta');
    const data=await resp.json();
    if(!Array.isArray(data))throw Error('Resposta inválida');
    if(!current())return;
    const selected=notaAtual?String(notaAtual.fornecedorId):select.value;
    fornecedoresAtivos=data;
    const model=noteSupplierModel(data);
    select.replaceChildren();
    const add=(value,label)=>{const opt=document.createElement('option');opt.value=value;opt.textContent=label;select.appendChild(opt);};
    add('',model.total?'Selecione um fornecedor':'Nenhum fornecedor ativo cadastrado');
    model.all.forEach(f=>add(String(f.id),f.name+' ('+(f.count??'—')+' produtos)'));
    select.value=selected;
  }catch{
    if(!current())return;
    fornecedoresAtivos=[];noteSupplierFailed=true;
    select.innerHTML='<option value="">Erro ao carregar fornecedores</option>';
  }finally{
    if(current()){noteSupplierLoading=false;drawNoteSuppliers();}
  }
}

async function carregarTodosProdutos() {
  try {
    const resp = await fetch('/api/produtos');
    todosOsProdutos = await resp.json();
  } catch {
    todosOsProdutos = [];
  }

  if (notaAtual) {
    atualizarAutocompleteProdutos();
    atualizarAvisoVinculo();
  }
}

function renderTabelaNotas(notas){noteListRecords=Array.isArray(notas)?notas:null;noteListFailed=!noteListRecords;drawNoteList();}
function drawNoteList(){
  const get=id=>document.getElementById(id),tbody=get('tabela-notas');if(!tbody)return;
  const model=noteListModel(noteListRecords,{query:get('note-list-search').value,status:get('note-list-status').value,from:get('note-list-from').value,to:get('note-list-to').value},noteListPage,get('note-list-size').value);
  const ready=!!model&&!noteListLoading&&!noteListFailed,money=c=>c==null?'—':noteDetailMoney(c/100);
  const metrics=ready?[
    ['Notas cadastradas',model.total,'Toda a base · '+model.groups.CANCELADA.count+' canceladas','neutral'],
    ['Aguardando confirmação',model.groups.PENDENTE.count,'Rascunhos pendentes','warning'],
    ['Confirmadas, não pagas',money(model.groups.CONFIRMADA.cents),model.groups.CONFIRMADA.count+' notas','warning'],
    ['Pagamentos registrados',money(model.groups.PAGA.cents),model.groups.PAGA.count+' notas pagas','success']
  ]:[['Notas cadastradas','—','Toda a base','neutral'],['Aguardando confirmação','—','Rascunhos pendentes','warning'],['Confirmadas, não pagas','—','Valor registrado','warning'],['Pagamentos registrados','—','Valor registrado','success']];
  get('note-list-summary').innerHTML=metrics.map(([label,value,note,tone])=>'<div data-tone="'+tone+'"><span>'+label+'</span><strong>'+value+'</strong><small>'+note+'</small></div>').join('');
  get('notas-count').textContent=ready?model.count+' de '+model.total+' notas':'—';
  get('note-list-refresh').disabled=noteListLoading||noteListMutation;
  get('note-list-prev').disabled=!ready||model.page<=1;get('note-list-next').disabled=!ready||model.page>=model.pages;
  get('note-list-range').textContent=ready?(model.count?model.from+'–'+model.to+' de '+model.count+' notas':'0 resultados'):'';
  const err=get('note-list-filter-error');err.hidden=!(ready&&model.invalid);err.textContent=err.hidden?'':'A data inicial não pode ser posterior à data final.';
  if(!ready){tbody.innerHTML='<tr><td colspan="7" class="empty">'+(noteListLoading?'Carregando notas…':'Não foi possível carregar as notas. Use Atualizar.')+'</td></tr>';return;}
  noteListPage=model.page;
  if(!model.rows.length){tbody.innerHTML='<tr><td colspan="7" class="empty">'+(model.total?'Nenhuma nota corresponde aos filtros.':'Nenhuma nota fiscal registrada.')+'</td></tr>';return;}
  const esc=escapeHtml,disabled=noteListMutation||noteListUncertain?' disabled':'';
  tbody.innerHTML=model.rows.map(n=>{
    const state=NOTE_DETAIL_STATES[n.status]||{label:'Não informado',tone:'neutral'};
    return '<tr><td><strong class="note-list-id">#'+esc(n.id)+'</strong>'+(n.nfe?'<small>NF-e '+esc(n.nfe)+'</small>':'')+'</td><td><strong class="note-list-supplier">'+esc(n.supplier)+'</strong></td><td><span class="note-list-state" data-tone="'+state.tone+'"><span aria-hidden="true"></span>'+state.label+'</span></td><td>'+(n.items??'—')+'</td><td class="note-list-money">'+money(n.cents)+'</td><td class="note-list-date">'+esc(noteDetailDate(n.date))+'</td><td><div class="acoes note-list-row-actions"><button type="button" class="btn-acao" data-note-view="'+esc(n.id)+'">Detalhes</button>'+
    (n.status==='CONFIRMADA'?'<button type="button" class="btn-acao entrada" data-note-pay="'+esc(n.id)+'"'+disabled+'>Registrar pagamento</button>':'')+
    (n.status==='PENDENTE'?'<button type="button" class="btn-acao saida" data-note-cancel="'+esc(n.id)+'"'+disabled+'>Cancelar</button>':'')+'</div></td></tr>';
  }).join('');
  tbody.querySelectorAll('[data-note-view]').forEach(b=>b.onclick=()=>void verDetalhe(b.dataset.noteView));
  tbody.querySelectorAll('[data-note-pay]').forEach(b=>b.onclick=()=>void pagarNota(b.dataset.notePay));
  tbody.querySelectorAll('[data-note-cancel]').forEach(b=>b.onclick=()=>void confirmarCancelarNota(b.dataset.noteCancel));
}

async function avancarStep1() {
  if(noteOpening||noteOpeningUncertain||stepAtual!==1||notaAtual)return;
  const btn=document.getElementById('btn-step1-avancar'),select=document.getElementById('s1-fornecedor');
  const fornecedorId=Number(select.value);
  if(noteSupplierLoading||noteSupplierFailed||!noteSupplierModel(fornecedoresAtivos).all.some(f=>f.id===fornecedorId)){
    mostrarAlert('alert-s1','Selecione um fornecedor ativo.', 'error');return;
  }
  const current=()=>document.getElementById('s1-fornecedor')===select;
  noteOpening=true;limparAlert('alert-s1');setLoading(btn,true);drawNoteSuppliers();
  try{
    const resp=await fetch('/api/notas',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({fornecedorId})});
    if(!current())return;
    if(!resp.ok){
      if(resp.status>=500)throw Error('Resultado incerto');
      mostrarAlert('alert-s1',await safeReadErrorMessage(resp,'Erro ao abrir nota.'),'error');return;
    }
    const result=await resp.json();
    if(!current())return;
    if(!result||!result.id)throw Error('Resposta inválida');
    notaAtual=result;
    limparAlert('alert-s1');irParaStep(2);prepararStep2();
  }catch{
    if(current()){
      noteOpeningUncertain=true;
      mostrarAlert('alert-s1','Não foi possível confirmar a abertura. Confira Todas as Notas antes de iniciar outra nota.', 'error');
    }
  }finally{
    noteOpening=false;setLoading(btn,false);
    if(current())drawNoteSuppliers();
  }
}

function noteItemNumber(value,integer=false){
  if(value==null||String(value).trim()==='')return null;
  const n=Number(value);
  if(!Number.isFinite(n)||n<0)return null;
  if(integer)return Number.isInteger(n)&&n<=2147483647?n:null;
  const cents=Math.round(n*100);
  return Number.isSafeInteger(cents)&&Math.abs(n*100-cents)<1e-6?cents/100:null;
}
function noteItemMatches(products,linked,query,limit=6){
  const norm=v=>String(v??'').normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase().trim();
  const term=norm(query),ids=new Set((linked||[]).map(p=>String(p.id)));
  return (Array.isArray(products)?products:[]).filter(p=>Number.isSafeInteger(Number(p.id))&&Number(p.id)>0&&(!term||norm([p.nome,p.id,p.codigoBarras].join(' ')).includes(term)))
    .sort((a,b)=>Number(ids.has(String(b.id)))-Number(ids.has(String(a.id)))||String(a.nome).localeCompare(String(b.nome),'pt-BR')||Number(a.id)-Number(b.id)).slice(0,limit);
}
let noteItemPending=false,noteItemUncertain=false;
function updateNoteItemComposer(){
  const get=id=>document.getElementById(id),input=get('s2-produto-busca'),matches=get('note-item-matches');
  if(!input||!matches)return;
  const managed=!!document.querySelector('.app.stockos-shell-v2'),creating=!get('painel-novo-produto').hidden;
  const selected=creating?null:resolveProdutoSelecionado();
  input.disabled=creating||noteItemPending||noteItemUncertain;
  if(managed)input.removeAttribute('list');else input.setAttribute('list','s2-produtos-list');
  const qty=noteItemNumber(get('s2-qtd').value,true),price=noteItemNumber(get('s2-preco').value);
  const subtotal=qty>0&&price!=null?noteItemNumber(qty*price):null;
  get('note-item-estimate').textContent=noteDetailMoney(subtotal);
  get('note-item-selection').textContent=creating?'':selected?(produtoVinculadoAoFornecedor(selected.id)?'Produto selecionado · já vinculado':'Produto selecionado · vínculo automático ao salvar'):'Escolha um produto do catálogo.';
  get('btn-adicionar-item').disabled=noteItemPending||noteItemUncertain||!notaAtual||!(qty>0)||price==null||(creating?!get('s2-novo-nome').value.trim():!selected);
  get('btn-step2-avancar').disabled=noteItemPending||noteItemUncertain||!notaAtual?.itens?.length;
  get('note-item-discard').disabled=noteItemPending;
  matches.hidden=!managed||creating||!!selected||noteItemPending||noteItemUncertain||!input.value.trim();
  if(matches.hidden){matches.replaceChildren();return;}
  const rows=noteItemMatches(todosOsProdutos,fornecedorAtual()?.produtos,input.value);
  matches.replaceChildren();
  if(!rows.length){matches.textContent='Nenhum produto encontrado. Refine a busca ou cadastre um novo.';return;}
  for(const p of rows){
    const button=document.createElement('button');button.type='button';
    const name=document.createElement('strong');name.textContent=p.nome;
    const meta=document.createElement('small');meta.textContent='#'+p.id+(produtoVinculadoAoFornecedor(p.id)?' · Vinculado':' · Vínculo ao salvar');
    button.append(name,meta);button.onclick=()=>{input.value=formatarProdutoBusca(p);atualizarAvisoVinculo();get('s2-qtd').focus();};
    matches.appendChild(button);
  }
}
function bindNoteItemComposer(){
  ['s2-qtd','s2-preco'].forEach(id=>document.getElementById(id).oninput=updateNoteItemComposer);
}

function prepararStep2() {
  bindNoteItemComposer();
  document.getElementById('s2-fornecedor-badge').textContent = notaAtual.fornecedorNome;
  atualizarAutocompleteProdutos();
  atualizarAvisoVinculo();
  renderItensStep2();
}

function fornecedorAtual() {
  if (!notaAtual) return null;
  return fornecedoresAtivos.find((fornecedor) => fornecedor.id === notaAtual.fornecedorId) || null;
}

function produtoVinculadoAoFornecedor(produtoId) {
  const fornecedor = fornecedorAtual();
  if (!fornecedor) return false;
  return fornecedor.produtos.some((produto) => produto.id === produtoId);
}

function formatarProdutoBusca(produto) {
  return `${produto.nome} (ID ${produto.id})`;
}

function atualizarAutocompleteProdutos() {
  const datalist = document.getElementById('s2-produtos-list');
  if (!datalist) return;

  const fornecedor = fornecedorAtual();
  const vinculados = new Set((fornecedor?.produtos || []).map((produto) => produto.id));

  const produtosOrdenados = [...todosOsProdutos].sort((a, b) => {
    const prioridadeA = vinculados.has(a.id) ? 0 : 1;
    const prioridadeB = vinculados.has(b.id) ? 0 : 1;
    if (prioridadeA !== prioridadeB) return prioridadeA - prioridadeB;
    return a.nome.localeCompare(b.nome, 'pt-BR');
  });

  datalist.innerHTML = produtosOrdenados.map((produto) => {
    const valor = escapeHtml(formatarProdutoBusca(produto));
    const label = vinculados.has(produto.id)
      ? 'Já vinculado a este fornecedor'
      : 'Vínculo automático ao salvar a nota';
    return `<option value="${valor}" label="${escapeHtml(label)}"></option>`;
  }).join('');
}

function resolveProdutoSelecionado() {
  const valor = document.getElementById('s2-produto-busca').value.trim();
  if (!valor) return null;

  const matchId = valor.match(/\(ID\s*(\d+)\)$/i) || valor.match(/^(\d+)\s*[-:]/) || valor.match(/^(\d+)$/);
  if (matchId) {
    const id = parseInt(matchId[1], 10);
    return todosOsProdutos.find((produto) => produto.id === id) || null;
  }

  const nome = valor.replace(/\(ID\s*\d+\)/i, '').trim().toLowerCase();
  const correspondencias = todosOsProdutos.filter((produto) => produto.nome.trim().toLowerCase() === nome);
  if (correspondencias.length === 1) {
    return correspondencias[0];
  }
  return null;
}

function toggleNovoProduto(forceOpen) {
  const painel = document.getElementById('painel-novo-produto');
  const botao = document.getElementById('btn-toggle-novo-produto');
  const abrir = typeof forceOpen === 'boolean'
    ? forceOpen
    : painel.hidden;

  painel.hidden = !abrir;
  botao.setAttribute('aria-expanded',String(abrir));
  botao.innerHTML = abrir
    ? 'Usar Produto Existente'
    : '<span class="btn-inline-icon" aria-hidden="true">' + iconMarkup('plus') + '</span>Novo Produto';

  if (abrir) {
    document.getElementById('s2-produto-busca').value = '';
  } else {
    limparFormularioNovoProduto();
    limparAlert('alert-s2-novo');
  }

  atualizarAvisoVinculo();
}

function atualizarAvisoVinculo() {
  updateNoteItemComposer();
  limparAlert('alert-s2-link');

  if (!notaAtual) return;

  const painelNovoProduto = !document.getElementById('painel-novo-produto').hidden;
  const nomeNovoProduto = document.getElementById('s2-novo-nome').value.trim();

  if (painelNovoProduto && nomeNovoProduto) {
      mostrarAlert(
        'alert-s2-link',
        'Este produto será criado dentro da nota e vinculado automaticamente ao fornecedor quando o item for salvo.',
        'info'
      );
    return;
  }

  const produto = resolveProdutoSelecionado();
  if (produto && !produtoVinculadoAoFornecedor(produto.id)) {
      mostrarAlert(
        'alert-s2-link',
        'Este produto ainda não está vinculado a este fornecedor. O vínculo será criado automaticamente ao salvar a nota.',
        'info'
      );
  }
}

function renderItensStep2(){
  const lista=document.getElementById('itens-lista'),get=id=>document.getElementById(id),esc=escapeHtml;
  const model=noteDetailModel(notaAtual);
  const items=Array.isArray(notaAtual?.itens)?notaAtual.itens:[];
  get('note-item-count').textContent=items.length+' '+(items.length===1?'item':'itens');
  get('note-item-lines').textContent=items.length;
  get('note-item-units').textContent=model?noteDetailQuantity(model.units):'0';
  get('note-item-draft-id').textContent=notaAtual?'#'+notaAtual.id:'—';
  get('s2-total').textContent=noteDetailMoney(model?model.total:0);
  lista.innerHTML=items.length?'<table class="note-detail-table note-item-table"><thead><tr><th scope="col">Produto</th><th scope="col">Qtd.</th><th scope="col">Preço unitário</th><th scope="col">Subtotal</th></tr></thead><tbody>'+items.map(item=>'<tr><td><strong>'+esc(item.produtoNome||'—')+'</strong></td><td>'+noteDetailQuantity(noteDetailNumber(item.quantidade))+'</td><td>'+noteDetailMoney(noteDetailNumber(item.precoUnitario))+'</td><td>'+noteDetailMoney(noteDetailNumber(item.subtotal))+'</td></tr>').join('')+'</tbody></table>':'<div class="empty"><strong>Nenhum item lançado</strong><span>Selecione um produto acima, informe a quantidade e o preço de compra.</span></div>';
  updateNoteItemComposer();
}

function limparFormularioNovoProduto() {
  document.getElementById('s2-novo-nome').value = '';
  document.getElementById('s2-novo-categoria').value = '';
  document.getElementById('s2-novo-minimo').value = '0';
  document.getElementById('s2-novo-preco-base').value = '';
}

function limparFormularioItem() {
  document.getElementById('s2-produto-busca').value = '';
  document.getElementById('s2-qtd').value = '';
  document.getElementById('s2-preco').value = '';
  limparFormularioNovoProduto();
  limparAlert('alert-s2');
  limparAlert('alert-s2-novo');
  limparAlert('alert-s2-link');
  toggleNovoProduto(false);
}

function construirNovoProdutoPayload() {
  const nome = document.getElementById('s2-novo-nome').value.trim();
  if (!nome) return null;

  const minimoBruto = document.getElementById('s2-novo-minimo').value.trim();
  const precoBaseBruto = document.getElementById('s2-novo-preco-base').value.trim();
  const categoria = document.getElementById('s2-novo-categoria').value;

  return {
    nome,
    quantidadeMinima: minimoBruto === '' ? 0 : (noteItemNumber(minimoBruto,true)??-1),
    precoUnitarioBase: precoBaseBruto === '' ? null : (noteItemNumber(precoBaseBruto)??-1),
    categoriaEstoque: categoria || null
  };
}

async function adicionarItem() {
  const btn = document.getElementById('btn-adicionar-item');
  if(noteItemPending||noteItemUncertain||!notaAtual||stepAtual!==2)return;
  const quantidade = noteItemNumber(document.getElementById('s2-qtd').value,true);
  const preco = noteItemNumber(document.getElementById('s2-preco').value);
  const usandoNovoProduto = !document.getElementById('painel-novo-produto').hidden;
  const produtoExistente = usandoNovoProduto?null:resolveProdutoSelecionado();

  if (!quantidade || quantidade <= 0) {
    mostrarAlert('alert-s2', 'Informe uma quantidade inteira maior que zero.', 'error');
    return;
  }

  if (preco == null) {
    mostrarAlert('alert-s2', 'Informe um preço unitário válido.', 'error');
    return;
  }

  if (usandoNovoProduto && produtoExistente) {
    mostrarAlert('alert-s2', 'Escolha apenas um caminho: produto existente ou novo produto.', 'error');
    return;
  }

  if (!usandoNovoProduto && !produtoExistente) {
    mostrarAlert('alert-s2', 'Selecione um produto existente ou preencha os dados do novo produto.', 'error');
    return;
  }

  const novoProduto = usandoNovoProduto ? construirNovoProdutoPayload() : null;
  if (usandoNovoProduto && !novoProduto?.nome) {
    mostrarAlert('alert-s2-novo', 'Nome do novo produto é obrigatório.', 'error');
    return;
  }

  if(novoProduto&&(noteItemNumber(novoProduto.quantidadeMinima,true)==null||(novoProduto.precoUnitarioBase!=null&&noteItemNumber(novoProduto.precoUnitarioBase)==null))){
    mostrarAlert('alert-s2-novo','Confira o estoque mínimo e o preço base.','error');return;
  }
  const produtoPrecisavaVinculo = produtoExistente && !produtoVinculadoAoFornecedor(produtoExistente.id);
  const originalNote=notaAtual, current=()=>document.getElementById('btn-adicionar-item')===btn&&notaAtual?.id===originalNote.id;
  const controls=[...document.querySelectorAll('#painel-step2 .nota-inline-card input,#painel-step2 .nota-inline-card select,#btn-toggle-novo-produto')].map(el=>[el,el.disabled]);
  noteItemPending=true;controls.forEach(([el])=>el.disabled=true);
  setLoading(btn, true);updateNoteItemComposer();
  try {
    const payload = {
      produtoId: usandoNovoProduto ? null : produtoExistente.id,
      novoProduto,
      quantidade,
      precoUnitario: preco
    };

    const resp = await fetch(`/api/notas/${notaAtual.id}/itens`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });

    if(!current())return;
    if (!resp.ok) {
      if(resp.status>=500)throw Error('Resultado incerto');
      const mensagem = await safeReadErrorMessage(resp, 'Erro ao adicionar item.');
      if (usandoNovoProduto) {
        mostrarAlert('alert-s2-novo', mensagem, 'error');
      } else {
        mostrarAlert('alert-s2', mensagem, 'error');
      }
      return;
    }

    const result=await resp.json();
    if(!current())return;
    if(!result||String(result.id)!==String(originalNote.id)||!Array.isArray(result.itens))throw Error('Resposta inválida');
    notaAtual=result;
    await Promise.all([carregarFornecedores(), carregarTodosProdutos()]);
    if(!current())return;
    renderItensStep2();
    limparFormularioItem();

    if (usandoNovoProduto) {
      showToast('Produto criado e item adicionado à nota.', 'success');
    } else if (produtoPrecisavaVinculo) {
      showToast('Item adicionado. O vínculo com o fornecedor foi criado automaticamente.', 'success');
    }
  } catch {
    if(current()){
      noteItemUncertain=true;
      mostrarAlert('alert-s2','Não foi possível confirmar o lançamento. Confira o rascunho em Todas as notas antes de repetir a operação.','error');
    }
  } finally {
    noteItemPending=false;
    controls.forEach(([el,disabled])=>{if(el.isConnected)el.disabled=disabled;});
    setLoading(btn, false);
    if(current())updateNoteItemComposer();
  }
}

async function avancarStep2() {
  if(noteItemPending||noteItemUncertain)return;
  if (!notaAtual || notaAtual.itens.length === 0) {
    mostrarAlert('alert-s2', 'Adicione pelo menos um item antes de continuar.', 'error');
    return;
  }
  irParaStep(3);
  renderItensStep3();
}

async function descartarRascunho() {
  if(noteItemPending)return;
  if (!notaAtual) {
    resetWizard();
    return;
  }

  if (!confirm('Descartar este rascunho? A nota será excluída permanentemente.')) return;

  try {
    const resp = await fetch(`/api/notas/${notaAtual.id}`, { method: 'DELETE' });
    if (!resp.ok) {
      showToast(await safeReadErrorMessage(resp, 'Erro ao descartar rascunho.'), 'error');
      return;
    }
    showToast('Rascunho descartado.', 'success');
  } catch {
    showToast('Erro ao descartar rascunho.', 'error');
  }

  resetWizard();
  await carregarNotas();
}

function resetWizard() {
  if(noteItemPending)return;
  noteItemUncertain=false;
  notaAtual = null;
  irParaStep(1);
  document.getElementById('s1-fornecedor').value = '';
  document.getElementById('note-supplier-search').value='';
  noteSupplierPage=1;noteOpeningUncertain=false;drawNoteSuppliers();
  limparFormularioItem();
  limparAlert('alert-s1');
}

function renderItensStep3() {
  document.getElementById('s3-itens-lista').innerHTML = notaAtual.itens.map((item) => `
    <div class="item-nota-row">
      <span class="item-nota-nome">${escapeHtml(item.produtoNome)}</span>
      <span class="item-nota-qty">${item.quantidade} un.</span>
      <span class="item-nota-preco">× R$${Number(item.precoUnitario).toFixed(2)}</span>
      <span class="item-nota-subtotal">R$${Number(item.subtotal).toFixed(2)}</span>
    </div>`).join('');

  document.getElementById('s3-total').textContent = `R$${Number(notaAtual.total).toFixed(2)}`;
}

async function avancarStep3() {
  const btn = document.getElementById('btn-step3-avancar');
  setLoading(btn, true);
  try {
    const resp = await fetch(`/api/notas/${notaAtual.id}/confirmar`, { method: 'POST' });
    if (!resp.ok) {
      mostrarAlert('alert-s3', await safeReadErrorMessage(resp, 'Erro ao confirmar nota.'), 'error');
      return;
    }
    notaAtual = await resp.json();
    limparAlert('alert-s3');
    irParaStep(4);
    renderStep4();
  } finally {
    setLoading(btn, false);
  }
}

function renderStep4() {
  document.getElementById('s4-resumo').innerHTML = `
    <div class="info-card">
      <div class="info-label">Fornecedor</div>
      <div class="table-cell-strong">${escapeHtml(notaAtual.fornecedorNome)}</div>
    </div>
    <div class="info-card">
      <div class="info-label">Itens na Nota</div>
      <div class="info-value">${notaAtual.itens.length}</div>
    </div>
    <div class="info-card">
      <div class="info-label">Valor Total</div>
      <div class="info-value green">R$${Number(notaAtual.total).toFixed(2)}</div>
    </div>`;
}

async function avancarStep4() {
  const btn = document.getElementById('btn-step4-avancar');
  setLoading(btn, true);
  try {
    const resp = await fetch(`/api/notas/${notaAtual.id}/pagamento`, { method: 'POST' });
    if (!resp.ok) {
      mostrarAlert('alert-s4', await safeReadErrorMessage(resp, 'Erro ao registrar pagamento.'), 'error');
      return;
    }
    notaAtual = await resp.json();
    irParaStep(5);
    await carregarNotas();
  } finally {
    setLoading(btn, false);
  }
}

function irParaStep(step) {
  stepAtual = step;

  for (let i = 1; i <= 4; i++) {
    const el = document.getElementById(`step-${i}`);
    const conn = document.getElementById(`conn-${i}`);
    el.className = 'wizard-step' + (i < step ? ' done' : i === step ? ' active' : '');
    if(i===step)el.setAttribute('aria-current','step');else el.removeAttribute('aria-current');
    if (conn) conn.className = 'wizard-connector' + (i < step ? ' done' : '');
  }

  ['painel-step1', 'painel-step2', 'painel-step3', 'painel-step4', 'painel-done']
    .forEach((id, index) => {
      document.getElementById(id).hidden =
        !(index + 1 === step || (step === 5 && id === 'painel-done'));
    });
}

function abrirAba(abaId) {
  if(noteOpening||noteItemPending)return;
  if(abaId==='aba-lista'&&noteOpeningUncertain)void carregarNotas();
  document.querySelectorAll('.tab-panel').forEach((painel) => painel.classList.remove('active'));
  document.querySelectorAll('.tab-btn').forEach((botao) => botao.classList.remove('active'));

  document.getElementById(abaId).classList.add('active');
  if (abaId === 'aba-lista') document.getElementById('tab-lista').classList.add('active');
  if (abaId === 'aba-nova') document.getElementById('tab-nova').classList.add('active');
}

function iniciarNovaNota() {
  if(noteOpening||noteItemPending)return;
  if(noteOpeningUncertain&&document.getElementById('aba-nova').classList.contains('active')){
    mostrarAlert('alert-s1','Confira Todas as Notas antes de reiniciar a abertura.','error');return;
  }
  resetWizard();
  carregarFornecedores();
  carregarTodosProdutos();
  carregarNotas();
}

async function mutateListedNote(id,action){
  if(noteListMutation||noteListUncertain||noteListLoading||noteListFailed)return;
  const note=noteListRecords?.find(n=>String(n.id)===String(id)),payment=action==='pagamento';
  if(!note||note.status!==(payment?'CONFIRMADA':'PENDENTE')){showToast('Atualize a listagem antes de continuar.','error');return;}
  const value=noteDetailNumber(note.total);
  if(payment&&value==null){showToast('Valor da nota indisponível. Confira os detalhes.','error');return;}
  const message=payment?'Registrar o pagamento da nota #'+note.id+' ('+noteDetailMoney(value)+') de '+note.fornecedorNome+'? Confirme apenas se o pagamento já foi realizado. O StockOS não efetua transferências.':'Cancelar a nota #'+note.id+'? Esta ação não pode ser desfeita.';
  if(!confirm(message))return;
  const tbody=document.getElementById('tabela-notas'),current=()=>document.getElementById('tabela-notas')===tbody;
  noteListMutation=true;drawNoteList();
  try{
    const resp=await fetch('/api/notas/'+encodeURIComponent(String(id))+'/'+action,{method:'POST'});
    if(!current())return;
    if(!resp.ok){
      if(resp.status>=500)throw Error('Resultado incerto');
      showToast(await safeReadErrorMessage(resp,payment?'Erro ao registrar pagamento.':'Erro ao cancelar nota.'),'error');await carregarNotas();return;
    }
    showToast(payment?'Pagamento registrado com sucesso.':'Nota cancelada com sucesso.','success');await carregarNotas();
    if(current()&&noteDetailState?.id===String(id))fecharModal('modal-detalhe');
  }catch{if(current()){noteListUncertain=true;showToast('Resultado não confirmado. Confira a situação da nota após atualizar a lista.','error');await carregarNotas();}}
  finally{noteListMutation=false;if(document.getElementById('tabela-notas'))drawNoteList();}
}
async function confirmarCancelarNota(id){return mutateListedNote(id,'cancelar');}

const NOTE_DETAIL_STATES={
  PENDENTE:{label:'Pendente',note:'Aguardando confirmação da compra.',tone:'warning'},
  CONFIRMADA:{label:'Confirmada',note:'Compra confirmada; pagamento ainda não registrado.',tone:'info'},
  PAGA:{label:'Paga',note:'Pagamento registrado no StockOS.',tone:'success'},
  CANCELADA:{label:'Cancelada',note:'Nota cancelada.',tone:'neutral'}
};
function noteDetailNumber(raw){
  if(raw==null||!['number','string'].includes(typeof raw)||String(raw).trim()==='')return null;
  const n=Number(raw);
  return Number.isFinite(n)&&n>=0?n:null;
}
function noteDetailModel(note,query='',page=1,pageSize=8){
  if(!note||!Array.isArray(note.itens))return null;
  const norm=s=>String(s??'').normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase().trim();
  const items=note.itens.map((item,index)=>({index,id:item?.produtoId??null,name:String(item?.produtoNome||'Produto não informado'),qty:noteDetailNumber(item?.quantidade),price:noteDetailNumber(item?.precoUnitario),subtotal:noteDetailNumber(item?.subtotal)}));
  const sumQty=items.every(i=>Number.isSafeInteger(i.qty))?items.reduce((s,i)=>s+i.qty,0):null;
  const exactCents=n=>n!=null&&Number.isSafeInteger(Math.round(n*100))&&Math.abs(n*100-Math.round(n*100))<1e-6?Math.round(n*100):null;
  const cents=items.map(i=>exactCents(i.subtotal)),sum=cents.every(n=>n!=null)?cents.reduce((s,n)=>s+n,0):null;
  const total=noteDetailNumber(note.total),totalCents=exactCents(total);
  const subtotalSum=Number.isSafeInteger(sum)?sum/100:null;
  const difference=Number.isSafeInteger(sum)&&totalCents!=null&&Number.isSafeInteger(totalCents-sum)?(totalCents-sum)/100:null;
  const filtered=items.filter(i=>norm([i.name,i.id??''].join(' ')).includes(norm(query)));
  const size=Math.max(1,Math.floor(Number(pageSize)||8)),pages=Math.max(1,Math.ceil(filtered.length/size)),current=Math.min(pages,Math.max(1,Math.floor(Number(page)||1)));
  const status=Object.prototype.hasOwnProperty.call(NOTE_DETAIL_STATES,note.status)?NOTE_DETAIL_STATES[note.status]:{label:'Não informado',note:'Situação não reconhecida.',tone:'neutral'};
  return {status,total,subtotalSum,difference,units:Number.isSafeInteger(sumQty)?sumQty:null,totalItems:items.length,count:filtered.length,page:current,pages,from:filtered.length?(current-1)*size+1:0,to:Math.min(current*size,filtered.length),rows:filtered.slice((current-1)*size,current*size)};
}
const noteDetailMoney=n=>n==null?'—':n.toLocaleString('pt-BR',{style:'currency',currency:'BRL'});
const noteDetailQuantity=n=>n==null?'—':n.toLocaleString('pt-BR');
function noteDetailDate(raw){
  if(!raw)return '—';
  const d=new Date(raw);
  return Number.isFinite(d.getTime())?d.toLocaleString('pt-BR',{day:'2-digit',month:'2-digit',year:'numeric',hour:'2-digit',minute:'2-digit'}):'—';
}
let noteDetailState=null;
function noteDetailCurrent(state){
  return noteDetailState===state&&document.getElementById('modal-detalhe')===state.modal&&state.modal.classList.contains('open');
}
function drawNoteDetail(state){
  if(!noteDetailCurrent(state))return;
  const get=id=>document.getElementById(id),esc=escapeHtml,model=noteDetailModel(state.note,state.query,state.page);
  const info=get('detalhe-info'),items=get('note-detail-items'),total=get('note-detail-total');
  items.hidden=total.hidden=!model;
  if(!model){info.textContent=state.loading?'Consultando detalhes da nota…':'Detalhes indisponíveis.';return;}
  const note=state.note;
  state.page=model.page;
  get('modal-detalhe-titulo').textContent='Nota #'+note.id;
  info.innerHTML='<div class="note-detail-identity"><div><span>Fornecedor</span><strong>'+esc(note.fornecedorNome||'—')+'</strong><small>Emissão: '+esc(noteDetailDate(note.dataEmissao))+'</small></div><div class="note-detail-status" data-tone="'+model.status.tone+'"><span class="note-detail-status-label"><span aria-hidden="true"></span>'+model.status.label+'</span><small>'+model.status.note+'</small></div></div>'+
    (note.numeroNfe||note.chaveAcesso?'<div class="note-detail-fiscal">'+(note.numeroNfe?'<div><span>Número NF-e</span><strong>'+esc(note.numeroNfe)+'</strong></div>':'')+(note.chaveAcesso?'<div><span>Chave de acesso</span><strong>'+esc(note.chaveAcesso)+'</strong></div>':'')+'</div>':'')+
    '<div class="note-detail-metrics"><div><span>Itens registrados</span><strong>'+model.totalItems+'</strong></div><div><span>Unidades</span><strong>'+noteDetailQuantity(model.units)+'</strong></div><div><span>Valor total da nota</span><strong>'+noteDetailMoney(model.total)+'</strong></div></div>';
  get('note-detail-count').textContent=model.totalItems+' '+(model.totalItems===1?'registro':'registros');
  const results=get('detalhe-itens');
  results.innerHTML=model.rows.length?'<table class="note-detail-table"><thead><tr><th scope="col">Produto</th><th scope="col">Qtd.</th><th scope="col">Preço unitário</th><th scope="col">Subtotal</th></tr></thead><tbody>'+model.rows.map(i=>'<tr><td><strong>'+esc(i.name)+'</strong>'+(i.id!=null?'<small>#'+esc(i.id)+'</small>':'')+'</td><td>'+noteDetailQuantity(i.qty)+'</td><td>'+noteDetailMoney(i.price)+'</td><td>'+noteDetailMoney(i.subtotal)+'</td></tr>').join('')+'</tbody></table>':'<div class="note-detail-empty">'+(model.totalItems?'Nenhum item corresponde à pesquisa.':'Nenhum item registrado nesta nota.')+'</div>';
  get('note-detail-range').textContent=model.count?model.from+'–'+model.to+' de '+model.count+' itens':'0 resultados';
  get('note-detail-prev').disabled=model.page<=1;get('note-detail-next').disabled=model.page>=model.pages;
  total.innerHTML='<div><span>Total registrado da nota</span><small>Valor informado pelo sistema, incluindo todos os itens.</small></div><strong>'+noteDetailMoney(model.total)+'</strong>'+
    (model.difference!=null&&model.difference!==0?'<p class="note-detail-difference">A soma dos subtotais ('+noteDetailMoney(model.subtotalSum)+') difere do total registrado. Confira a nota na origem.</p>':'');
}
async function verDetalhe(id) {
  const modal=document.getElementById('modal-detalhe');
  if(!modal)return;
  const state={modal,id:String(id),note:null,query:'',page:1,loading:true};
  noteDetailState=state;
  const get=id=>document.getElementById(id);
  get('modal-detalhe-titulo').textContent='Detalhes da nota';
  get('note-detail-search').value='';
  get('note-detail-search').oninput=()=>{state.query=get('note-detail-search').value;state.page=1;drawNoteDetail(state);};
  get('note-detail-prev').onclick=()=>{state.page--;drawNoteDetail(state);};
  get('note-detail-next').onclick=()=>{state.page++;drawNoteDetail(state);};
  limparAlert('alert-detalhe');abrirModal('modal-detalhe');drawNoteDetail(state);
  try{
    const resp=await fetch('/api/notas/'+encodeURIComponent(state.id));
    if(!resp.ok)throw Error('Falha na consulta');
    const note=await resp.json();
    if(!noteDetailModel(note)||String(note.id)!==state.id)throw Error('Resposta inválida');
    if(!noteDetailCurrent(state))return;
    state.note=note;
  }catch{
    if(noteDetailCurrent(state))mostrarAlert('alert-detalhe','Não foi possível carregar os detalhes da nota. Feche e tente novamente.','error');
  }finally{
    state.loading=false;drawNoteDetail(state);
  }
}

async function pagarNota(id){return mutateListedNote(id,'pagamento');}

function abrirModal(id) {
  document.getElementById(id).classList.add('open');
}

function fecharModal(id) {
  if(id==='modal-detalhe')noteDetailState=null;
  document.getElementById(id).classList.remove('open');
}

function fecharModalSeClicouFora(e, id) {
  if (e.target.id === id) fecharModal(id);
}

function mostrarAlert(id, msg, tipo) {
  const el = document.getElementById(id);
  if (!el) return;
  el.textContent = msg;
  el.className = `alert-modal ${tipo}`;
}

function limparAlert(id) {
  const el = document.getElementById(id);
  if (el) {
    el.textContent = '';
    el.className = 'alert-modal';
  }
}

/* Page-scoped bridge: legacy inline event handlers resolve only against the active page. */
// ── Importar XML da NF-e ──────────────────────────────────────────────────
// Dois passos: a prévia não grava nada. Quantidades e valores vêm do XML; aqui
// a pessoa só decide o que fazer com cada item e confere as unidades por embalagem.

let nfeAtual = null;
let nfeImportando = false;
const NFE_MOEDA = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
const NFE_MAX_BYTES = 5 * 1024 * 1024;

function abrirModalXml() {
  nfeAtual = null;
  limparAlert('alert-xml');
  document.getElementById('xml-input').value = '';
  document.getElementById('xml-arquivo').hidden = false;
  document.getElementById('xml-conferencia').hidden = true;
  document.getElementById('btn-importar-xml').hidden = true;
  abrirModal('modal-xml');
}

function fecharModalXml() {
  if (nfeAtual && !window.confirm('Sair sem importar? A conferência desta nota será perdida.')) return;
  nfeAtual = null;
  fecharModal('modal-xml');
}

/** Respeita o encoding declarado no arquivo (há notas em ISO-8859-1). */
function decodificarXml(buffer) {
  const inicio = new TextDecoder('latin1').decode(buffer.slice(0, 200));
  const declarado = inicio.match(/encoding=["']([\w-]+)["']/i);
  try {
    return new TextDecoder(declarado ? declarado[1] : 'utf-8').decode(buffer);
  } catch (_) {
    return new TextDecoder('utf-8').decode(buffer);
  }
}

async function lerArquivoXml(input) {
  const arquivo = input.files && input.files[0];
  if (!arquivo) return;
  limparAlert('alert-xml');
  if (arquivo.size > NFE_MAX_BYTES) {
    mostrarAlert('alert-xml', 'Arquivo grande demais para uma NF-e.', 'error');
    return;
  }
  await processarTextoNfe(decodificarXml(await arquivo.arrayBuffer()));
}

async function processarTextoNfe(xml) {
  limparAlert('alert-xml');
  try {
    const resp = await fetch('/api/notas/importacao/previa', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ xml })
    });
    if (!resp.ok) throw new Error(await safeReadErrorMessage(resp, 'Não foi possível ler o arquivo.'));
    const previa = await resp.json();
    await carregarTodosProdutos();
    nfeAtual = { xml, previa };
    renderPreviaNfe(previa);
  } catch (e) {
    nfeAtual = null;
    mostrarAlert('alert-xml', e.message, 'error');
  }
}

function nfeUnidades(item, fator) {
  return Number(item.quantidadeComercial) * fator;
}

function nfeEhInteiro(valor) {
  return Math.abs(valor - Math.round(valor)) < 0.000001 && valor > 0;
}

function renderPreviaNfe(previa) {
  const emissao = previa.dataEmissao ? new Date(previa.dataEmissao).toLocaleDateString('pt-BR') : '—';
  const situacaoFornecedor = previa.fornecedorId
    ? (previa.fornecedorInativo ? 'inativo: reative antes de importar' : 'já cadastrado')
    : 'novo: será cadastrado';
  document.getElementById('xml-resumo').innerHTML = `
    <div><strong>NF-e ${escapeHtml(previa.numero)}</strong> · série ${escapeHtml(previa.serie)} · emitida em ${emissao} · total ${NFE_MOEDA.format(Number(previa.valorTotal))}</div>
    <div>Fornecedor: <strong>${escapeHtml(previa.fornecedorNome)}</strong> (${situacaoFornecedor})</div>
    <div>Nota emitida para: ${escapeHtml(previa.destinatarioNome || '—')}. Confira se é a sua loja.</div>`;

  const avisos = [...(previa.avisos || [])];
  const bloqueada = Boolean(previa.notaExistenteId) || previa.fornecedorInativo;
  if (previa.notaExistenteId) avisos.unshift(`Esta nota já foi importada (nota #${previa.notaExistenteId}). Não pode entrar de novo.`);
  document.getElementById('xml-avisos').innerHTML = avisos.map(a => `<li>${escapeHtml(a)}</li>`).join('');

  const opcoesProduto = [...todosOsProdutos]
    .sort((a, b) => String(a.nome).localeCompare(String(b.nome), 'pt-BR'))
    .map(p => `<option value="${p.id}">${escapeHtml(p.nome)}</option>`)
    .join('');

  document.getElementById('xml-itens').innerHTML = previa.itens.map(item => {
    const n = item.numero;
    const origem = item.origem === 'VINCULO' ? 'Reconhecido deste fornecedor'
      : item.origem === 'CODIGO_BARRAS' ? 'Reconhecido pelo código de barras' : 'Produto novo';
    return `
      <article class="xml-item" data-item="${n}">
        <header>
          <strong>${n}. ${escapeHtml(item.descricao)}</strong>
          <span class="badge ${item.origem ? 'badge-green' : 'badge-amber'}">${origem}</span>
        </header>
        <p class="inline-note">Cód. ${escapeHtml(item.codigoFornecedor)} · NCM ${escapeHtml(item.ncm || '—')} · Código de barras ${escapeHtml(item.eanTributavel || item.eanComercial || 'sem GTIN')}</p>
        <div class="xml-conta ${item.conferir ? 'xml-conta--conferir' : ''}">
          <span>${Number(item.quantidadeComercial).toLocaleString('pt-BR')} ${escapeHtml(item.unidadeComercial)} ×</span>
          <input class="input" type="number" id="xml-fator-${n}" min="1" max="1000" step="1" value="${item.unidadesPorEmbalagem}" aria-label="Unidades por embalagem" oninput="atualizarContaXml(${n})">
          <span id="xml-conta-${n}"></span>
        </div>
        ${item.conferir ? '<p class="xml-conferir">Confira: a nota não diz quantas unidades vêm nesta embalagem.</p>' : ''}
        <div class="xml-decisao">
          <label>O que fazer
            <select class="select" id="xml-acao-${n}" onchange="atualizarDecisaoXml(${n})">
              <option value="CRIAR">Cadastrar produto novo</option>
              <option value="VINCULAR">Já tenho este produto</option>
              <option value="IGNORAR">Não dar entrada</option>
            </select>
          </label>
          <label class="xml-vincular" id="xml-vincular-${n}">Produto
            <select class="select" id="xml-produto-${n}"><option value="">Escolha…</option>${opcoesProduto}</select>
          </label>
          <div class="xml-novo" id="xml-novo-${n}">
            <label class="xml-novo__nome">Nome no sistema
              <input class="input" type="text" id="xml-nome-${n}" value="${escapeHtml(item.descricao)}">
            </label>
            <label>Preço gelada (R$)
              <input class="input" type="number" id="xml-gelado-${n}" min="0.01" step="0.01" placeholder="Vazio = não vende gelada">
            </label>
            <label>Preço natural / unidade (R$)
              <input class="input" type="number" id="xml-preco-${n}" min="0.01" step="0.01">
            </label>
            <label id="xml-fardo-wrap-${n}">Preço do fardo (R$)
              <input class="input" type="number" id="xml-fardo-${n}" min="0.01" step="0.01" placeholder="Vazio = não vende fechado">
            </label>
            <label>Categoria
              <select class="select" id="xml-categoria-${n}">
                <option value="bebidas">Bebidas</option>
                <option value="petiscos">Petiscos</option>
              </select>
            </label>
          </div>
        </div>
      </article>`;
  }).join('');

  previa.itens.forEach(item => {
    document.getElementById(`xml-acao-${item.numero}`).value = item.produtoId ? 'VINCULAR' : 'CRIAR';
    if (item.produtoId) document.getElementById(`xml-produto-${item.numero}`).value = String(item.produtoId);
    atualizarDecisaoXml(item.numero);
    atualizarContaXml(item.numero);
  });

  document.getElementById('xml-arquivo').hidden = true;
  document.getElementById('xml-conferencia').hidden = false;
  const botao = document.getElementById('btn-importar-xml');
  botao.hidden = false;
  botao.disabled = bloqueada;
}

function nfeItem(numero) {
  return nfeAtual && nfeAtual.previa.itens.find(i => i.numero === numero);
}

function atualizarDecisaoXml(numero) {
  const acao = document.getElementById(`xml-acao-${numero}`).value;
  document.getElementById(`xml-vincular-${numero}`).hidden = acao !== 'VINCULAR';
  document.getElementById(`xml-novo-${numero}`).hidden = acao !== 'CRIAR';
  document.querySelector(`.xml-item[data-item="${numero}"]`).classList.toggle('xml-item--ignorado', acao === 'IGNORAR');
  atualizarContaXml(numero);
}

function atualizarContaXml(numero) {
  const item = nfeItem(numero);
  if (!item) return;
  const fator = Number(document.getElementById(`xml-fator-${numero}`).value);
  const unidades = nfeUnidades(item, fator);
  const conta = document.getElementById(`xml-conta-${numero}`);
  if (!Number.isInteger(fator) || fator < 1 || !nfeEhInteiro(unidades)) {
    conta.textContent = 'não dá unidades inteiras';
    conta.classList.add('xml-conta__erro');
  } else {
    const custo = Number(item.custoTotal) / unidades;
    conta.textContent = `un = ${unidades.toLocaleString('pt-BR')} unidades · custo ${NFE_MOEDA.format(custo)}/un`;
    conta.classList.remove('xml-conta__erro');
  }
  document.getElementById(`xml-fardo-wrap-${numero}`).hidden = !(Number.isInteger(fator) && fator >= 2);
}

function numeroXml(id) {
  const texto = document.getElementById(id).value.trim().replace(',', '.');
  return texto === '' ? null : Number(texto);
}

function montarDecisoesXml() {
  const decisoes = [];
  for (const item of nfeAtual.previa.itens) {
    const n = item.numero;
    const acao = document.getElementById(`xml-acao-${n}`).value;
    const fator = Number(document.getElementById(`xml-fator-${n}`).value);
    if (acao === 'IGNORAR') {
      decisoes.push({ numeroItem: n, acao, unidadesPorEmbalagem: 1 });
      continue;
    }
    if (!Number.isInteger(fator) || fator < 1 || !nfeEhInteiro(nfeUnidades(item, fator))) {
      return { erro: `Item ${n}: confira as unidades por embalagem.`, campo: `xml-fator-${n}` };
    }
    if (acao === 'VINCULAR') {
      const produtoId = Number(document.getElementById(`xml-produto-${n}`).value);
      if (!produtoId) return { erro: `Item ${n}: escolha o produto.`, campo: `xml-produto-${n}` };
      decisoes.push({ numeroItem: n, acao, produtoId, unidadesPorEmbalagem: fator });
      continue;
    }
    const nome = document.getElementById(`xml-nome-${n}`).value.trim();
    const precoVenda = numeroXml(`xml-preco-${n}`);
    const precoGelado = numeroXml(`xml-gelado-${n}`);
    const precoFardo = fator >= 2 ? numeroXml(`xml-fardo-${n}`) : null;
    if (!nome) return { erro: `Item ${n}: informe o nome.`, campo: `xml-nome-${n}` };
    if (!(precoVenda > 0)) return { erro: `Item ${n}: informe o preço de venda.`, campo: `xml-preco-${n}` };
    const categoria = document.getElementById(`xml-categoria-${n}`);
    decisoes.push({
      numeroItem: n,
      acao,
      unidadesPorEmbalagem: fator,
      nome,
      precoVenda,
      precoGelado,
      precoFardo,
      categoriaCardapioCodigo: categoria.value,
      categoriaCardapioNome: categoria.options[categoria.selectedIndex].textContent,
      categoriaEstoque: categoria.value === 'bebidas' ? 'BEBIDAS' : 'SECO'
    });
  }
  return { decisoes };
}

async function confirmarImportacaoXml() {
  if (!nfeAtual || nfeImportando) return;
  limparAlert('alert-xml');
  const montagem = montarDecisoesXml();
  if (montagem.erro) {
    mostrarAlert('alert-xml', montagem.erro, 'error');
    document.getElementById(montagem.campo)?.focus();
    return;
  }
  nfeImportando = true;
  const botao = document.getElementById('btn-importar-xml');
  botao.disabled = true;
  try {
    const resp = await fetch('/api/notas/importacao', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ xml: nfeAtual.xml, itens: montagem.decisoes })
    });
    if (!resp.ok) throw new Error(await safeReadErrorMessage(resp, 'Não foi possível importar a nota.'));
    const r = await resp.json();
    nfeAtual = null;
    fecharModal('modal-xml');
    const novos = r.produtosCriados ? `, ${r.produtosCriados} produto(s) novo(s)` : '';
    showToast(`Nota importada: ${Number(r.unidades).toLocaleString('pt-BR')} unidades no estoque${novos}.`, 'success');
    await Promise.all([carregarNotas(), carregarTodosProdutos()]);
  } catch (e) {
    mostrarAlert('alert-xml', e.message, 'error');
    document.querySelector('#modal-xml .modal-body')?.scrollTo({ top: 0 });
    botao.disabled = false;
  } finally {
    nfeImportando = false;
  }
}

window.StockOSPages = window.StockOSPages || {};
window.StockOSPages.notas = {
  mount: stockOSMount_notas,
  activate() {
    Object.assign(window, {
    abrirModalXml,
    fecharModalXml,
    lerArquivoXml,
    processarTextoNfe,
    atualizarDecisaoXml,
    atualizarContaXml,
    confirmarImportacaoXml,
    carregarNotas,
    carregarFornecedores,
    carregarTodosProdutos,
    renderTabelaNotas,
    avancarStep1,
    prepararStep2,
    fornecedorAtual,
    produtoVinculadoAoFornecedor,
    formatarProdutoBusca,
    atualizarAutocompleteProdutos,
    resolveProdutoSelecionado,
    toggleNovoProduto,
    atualizarAvisoVinculo,
    renderItensStep2,
    limparFormularioNovoProduto,
    limparFormularioItem,
    construirNovoProdutoPayload,
    adicionarItem,
    avancarStep2,
    descartarRascunho,
    resetWizard,
    renderItensStep3,
    avancarStep3,
    renderStep4,
    avancarStep4,
    irParaStep,
    abrirAba,
    iniciarNovaNota,
    confirmarCancelarNota,
    verDetalhe,
    pagarNota,
    abrirModal,
    fecharModal,
    fecharModalSeClicouFora,
    mostrarAlert,
    limparAlert,
  });
  }
};
if (/^\/notas(?:\.html)?\/?$/.test(location.pathname)) {
  window.StockOSPages.notas.activate();
}
document.addEventListener('DOMContentLoaded', stockOSMount_notas);

})();
