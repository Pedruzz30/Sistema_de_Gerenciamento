/* SPA-isolated screen: fornecedores. Retains direct HTML navigation. */
(() => {
'use strict';
// ── fornecedores.js ────────────────────────────────────────────────────────
// Supplier management: CRUD, activate/deactivate, link products.

let todosFornecedores = [];
let todosProdutos = [];
let filtroAtivo = 'todos';
let fornecedorVincularId = null;

// ── Init ──────────────────────────────────────────────────────────────────

async function stockOSMount_fornecedores() {
  if (typeof paginaAtual === 'function' && paginaAtual() !== 'fornecedores') return;
  carregarTudo();
}

async function carregarTudo() {
  await Promise.all([carregarFornecedores(), carregarProdutos()]);
}

// ── API ───────────────────────────────────────────────────────────────────

async function carregarFornecedores() {
  try {
    const resp = await fetch('/api/fornecedores');
    if (!resp.ok) throw new Error();
    const dados=await resp.json();
    if(!Array.isArray(dados))throw new Error('Resposta inválida');
    todosFornecedores=dados;
    renderTabela();
  } catch {
    document.getElementById('tabela-fornecedores').innerHTML =
      '<tr><td colspan="6" class="empty">Erro ao carregar fornecedores.</td></tr>';
    document.getElementById('table-count').textContent='Indisponível';
    document.getElementById('fornecedor-summary').innerHTML='<div class="page-summary-card">Resumo indisponível. Atualize a página para tentar novamente.</div>';
  }
}

async function carregarProdutos() {
  try {
    const resp = await fetch('/api/produtos');
    if (!resp.ok) throw new Error();
    todosProdutos = await resp.json();
  } catch {
    todosProdutos = [];
  }
}

// ── Render ────────────────────────────────────────────────────────────────

function supplierDirectoryModel(records,filter='todos',query=''){
  const all=Array.isArray(records)?records:[];
  const norm=v=>String(v??'').normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase().trim();
  const digits=v=>String(v??'').replace(/\D/g,'');
  const term=norm(query),numeric=/^[\d\s().\/+-]+$/.test(term)&&digits(term);
  const active=all.filter(f=>f.ativo===true).length;
  const linked=all.filter(f=>Array.isArray(f.produtos)&&f.produtos.length>0).length;
  const unlinked=all.filter(f=>Array.isArray(f.produtos)&&f.produtos.length===0).length;
  const rows=all.filter(f=>{
    const state=filter==='todos'||(filter==='ativos'&&f.ativo===true)||(filter==='inativos'&&f.ativo===false)||(filter==='sem-produtos'&&Array.isArray(f.produtos)&&f.produtos.length===0);
    const search=!term||[f.nome,f.cnpj,f.telefone].some(v=>norm(v).includes(term))||(numeric&&[f.cnpj,f.telefone].some(v=>digits(v).includes(numeric)));
    return state&&search;
  });
  return {rows,total:all.length,active,inactive:all.filter(f=>f.ativo===false).length,linked,unlinked};
}
function renderResumo(listaVisivel) {
  const container=document.getElementById('fornecedor-summary');
  if(!container)return;
  const m=supplierDirectoryModel(todosFornecedores);
  const managed=!!document.querySelector('.app.stockos-shell-v2');
  const cards=managed?[
    {label:'Total de fornecedores',value:m.total,sub:'Cadastros na base',icon:'truck'},
    {label:'Ativos',value:m.active,sub:'Disponíveis para operação',icon:'check',tone:'active'},
    {label:'Inativos',value:m.inactive,sub:'Fora de operação',icon:'minus'},
    {label:'Com produtos vinculados',value:m.linked,sub:m.unlinked+' sem vínculos',icon:'package'}
  ]:[
    {label:'Total de fornecedores',value:m.total,sub:'Base geral cadastrada no sistema.'},
    {label:'Fornecedores ativos',value:m.active,sub:'Disponíveis para novas notas e vínculos.'},
    {label:'Com produtos vinculados',value:m.linked,sub:m.unlinked+' sem vínculos.'}
  ];
  container.classList.toggle('supplier-summary-compact',managed);
  container.innerHTML=cards.map(c=>'<div class="page-summary-card" data-tone="'+(c.tone||'neutral')+'"><div class="page-summary-label">'+c.label+'</div><div class="page-summary-value">'+c.value+'</div><div class="page-summary-sub">'+c.sub+'</div></div>').join('');
  const counts={todos:m.total,ativos:m.active,inativos:m.inactive,'sem-produtos':m.unlinked};
  const labels={todos:'Todos',ativos:'Ativos',inativos:'Inativos','sem-produtos':'Sem vínculos'};
  document.querySelectorAll('[data-supplier-filter]').forEach(b=>{
    const key=b.dataset.supplierFilter;
    b.textContent=labels[key]+(managed?' · '+counts[key]:'');
    b.classList.toggle('active',key===filtroAtivo);b.setAttribute('aria-pressed',String(key===filtroAtivo));
  });
}

function renderTabela() {
  const model=supplierDirectoryModel(todosFornecedores,filtroAtivo,document.getElementById('busca').value);
  const lista=model.rows;
  document.getElementById('table-count').textContent=lista.length===model.total?model.total+' '+(model.total===1?'fornecedor':'fornecedores'):lista.length+' de '+model.total+' fornecedores';
  renderResumo(lista);

  const tbody = document.getElementById('tabela-fornecedores');
  if (lista.length === 0) {
    if (todosFornecedores.length === 0) {
      tbody.innerHTML = `
        <tr><td colspan="6" class="table-empty-cell">
          ${emptyStateMarkup({
            icon: 'truck',
            title: 'Nenhum fornecedor cadastrado',
            copy: 'Adicione fornecedores para vincular produtos e centralizar o fluxo de compras.',
            actions: '<button class="btn-primary" onclick="abrirModalCadastro()">Novo Fornecedor</button>'
          })}
        </td></tr>`;
    } else {
      tbody.innerHTML = '<tr><td colspan="6" class="empty">Nenhum fornecedor encontrado para os filtros aplicados.</td></tr>';
    }
    return;
  }

  tbody.innerHTML = lista.map(f => `
    <tr>
      <td>
        <div class="produto-nome">${escapeHtml(f.nome)}</div>
        <div class="produto-id">#${f.id}</div>
      </td>
      <td class="table-cell-muted">${escapeHtml(f.cnpj)}</td>
      <td class="table-cell-muted">${escapeHtml(f.telefone || '—')}</td>
      <td>
        ${f.produtos.length > 0
          ? `<button class="btn-acao" onclick="verProdutos(${f.id})">${f.produtos.length} produto(s)</button>`
          : '<span class="status-placeholder">Nenhum</span>'
        }
      </td>
      <td>
        <span class="badge ${f.ativo ? 'badge-green' : 'badge-gray'}">
          ${f.ativo ? 'Ativo' : 'Inativo'}
        </span>
      </td>
      <td>
        <div class="acoes">
          <button class="btn-acao" onclick="abrirModalVincular(${f.id})">Vincular Produto</button>
          ${f.ativo
            ? `<button class="btn-acao saida" onclick="desativar(${f.id})">Desativar</button>`
            : `<button class="btn-acao entrada" onclick="reativar(${f.id})">Reativar</button>`
          }
        </div>
      </td>
    </tr>
  `).join('');
}

// ── Actions ───────────────────────────────────────────────────────────────

function setFiltro(tipo, btn) {
  filtroAtivo = tipo;
  document.querySelectorAll('[data-supplier-filter]').forEach(b => { b.classList.toggle('active',b===btn); b.setAttribute('aria-pressed',String(b===btn)); });
  renderTabela();
}

function filtrar() {
  renderTabela();
}

// Match the current numeric CNPJ contract. Never strip letters into another identity.
function supplierCnpjDigits(value){
  const raw=String(value??'').trim();
  return /^[0-9.\s\/-]+$/.test(raw)?raw.replace(/\D/g,''):'';
}
function supplierCnpjValid(digits){
  if(!/^\d{14}$/.test(digits)||/^(\d)\1{13}$/.test(digits))return false;
  const check=weights=>{
    const sum=weights.reduce((total,w,i)=>total+Number(digits[i])*w,0),remainder=sum%11;
    return Number(digits[weights.length])===(remainder<2?0:11-remainder);
  };
  return check([5,4,3,2,9,8,7,6,5,4,3,2])&&check([6,5,4,3,2,9,8,7,6,5,4,3,2]);
}
function supplierFormatCnpj(digits){
  return /^\d{14}$/.test(digits)?digits.replace(/^(\d{2})(\d{3})(\d{3})(\d{4})(\d{2})$/,'$1.$2.$3/$4-$5'):digits;
}
function supplierCreateModel(name,cnpj,phone,records=[]){
  const nome=String(name??'').trim(),raw=String(cnpj??'').trim(),telefone=String(phone??'').trim();
  const digits=supplierCnpjDigits(raw);
  const cnpjError=!raw?'Informe o CNPJ.':/[a-z]/i.test(raw)?'Esta versão aceita somente CNPJ numérico.':!digits||digits.length!==14?'Informe os 14 dígitos do CNPJ.':!supplierCnpjValid(digits)?'CNPJ inválido. Confira os dígitos verificadores.':'';
  const nameError=!nome?'Nome é obrigatório.':nome.length>160?'O nome pode ter até 160 caracteres.':'';
  const phoneError=telefone.length>40?'O telefone pode ter até 40 caracteres.':'';
  const duplicate=!cnpjError&&Array.isArray(records)&&records.some(r=>supplierCnpjDigits(r.cnpj)===digits);
  return {nome,cnpj:digits,telefone,cnpjError,nameError,phoneError,duplicate,error:nameError||cnpjError||phoneError};
}
let supplierCreatePending=false,supplierCreateUncertain=false;
function readSupplierCreate(){
  const get=id=>document.getElementById(id);
  return supplierCreateModel(get('c-nome').value,get('c-cnpj').value,get('c-telefone').value,todosFornecedores);
}
function updateSupplierCreate(){
  const model=readSupplierCreate(),input=document.getElementById('c-cnpj'),note=document.getElementById('supplier-cnpj-help');
  const entered=input.value.trim()!=='';
  note.textContent=!entered?'Informe o CNPJ numérico com ou sem pontuação.':model.cnpjError|| (model.duplicate?'CNPJ já consta na última consulta. Confira o cadastro existente.':'Dígitos verificadores válidos. Não é uma consulta à Receita Federal.');
  note.dataset.state=!entered?'neutral':model.cnpjError?'error':model.duplicate?'warning':'valid';
  input.setAttribute('aria-invalid',String(entered&&!!model.cnpjError));
  return model;
}
async function cadastrarFornecedor() {
  if(supplierCreatePending||supplierCreateUncertain)return;
  const model=updateSupplierCreate();
  if(model.error){mostrarAlert('alert-cadastro',model.error,'error');document.getElementById(model.nameError?'c-nome':model.cnpjError?'c-cnpj':'c-telefone').focus();return;}
  const modal=document.getElementById('modal-cadastro'),btn=document.getElementById('btn-cadastrar-fornecedor');
  const fields=['c-nome','c-cnpj','c-telefone'].map(id=>document.getElementById(id));
  const current=()=>document.getElementById('modal-cadastro')===modal;
  supplierCreatePending=true;
  fields.forEach(el=>el.disabled=true);
  setLoading(btn,true);limparAlert('alert-cadastro');
  try{
    const resp=await fetch('/api/fornecedores',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({nome:model.nome,cnpj:model.cnpj,telefone:model.telefone})});
    if(!current())return;
    if(!resp.ok){
      if(resp.status>=500)throw Error('Resultado incerto');
      mostrarAlert('alert-cadastro',await safeReadErrorMessage(resp,'Erro ao cadastrar.'),'error');return;
    }
    modal.classList.remove('open');
    showToast('Fornecedor cadastrado com sucesso!','success');
    await carregarFornecedores();
  }catch(error){
    if(current()){
      supplierCreateUncertain=true;
      mostrarAlert('alert-cadastro','Não foi possível confirmar o cadastro. Confira a listagem antes de tentar novamente.','error');
      await carregarFornecedores();
    }
  }finally{
    supplierCreatePending=false;
    fields.forEach(el=>el.disabled=false);
    setLoading(btn,false);
    if(supplierCreateUncertain){btn.disabled=true;btn.textContent='Confira a listagem';}
  }
}

async function desativar(id) {
  const resp = await fetch(`/api/fornecedores/${id}/desativar`, { method: 'PUT' });
  if (!resp.ok) {
    const err = await safeReadErrorMessage(resp, 'Erro ao desativar.');
    showToast(err, 'error'); return;
  }
  showToast('Fornecedor desativado.', 'success');
  await carregarFornecedores();
}

async function reativar(id) {
  const resp = await fetch(`/api/fornecedores/${id}/reativar`, { method: 'PUT' });
  if (!resp.ok) {
    const err = await safeReadErrorMessage(resp, 'Erro ao reativar.');
    showToast(err, 'error'); return;
  }
  showToast('Fornecedor reativado.', 'success');
  await carregarFornecedores();
}

// Supplier link workspace: one existing API operation at a time.
function supplierLinkModel(products,linked,tab='available',query='',category='',page=1,pageSize=7){
  if(!Array.isArray(products)||!Array.isArray(linked))return null;
  const norm=v=>String(v??'').normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase().trim();
  const validId=p=>Number.isSafeInteger(Number(p?.id))&&Number(p.id)>0;
  const catalog=new Map(products.filter(validId).map(p=>[Number(p.id),p]));
  const existing=new Map(linked.filter(validId).map(p=>[Number(p.id),p]));
  const normalize=(p,id,isLinked)=>{
    const current=catalog.get(id)||{};
    const item={...p,...current};
    const price=item.precoUnitario!=null&&String(item.precoUnitario).trim()!==''&&Number.isFinite(Number(item.precoUnitario))?Number(item.precoUnitario):null;
    return {id,name:String(item.nome||'Produto sem nome'),category:String(item.categoriaEstoque||''),code:String(item.codigoBarras||''),price,linked:isLinked,active:item.ativo!==false};
  };
  const available=[...catalog].filter(([id])=>!existing.has(id)).map(([id,p])=>normalize(p,id,false));
  const current=[...existing].map(([id,p])=>normalize(p,id,true));
  const all=tab==='linked'?current:available;
  const categories=[...new Set(all.map(p=>p.category).filter(Boolean))].sort((a,b)=>a.localeCompare(b,'pt-BR'));
  const term=norm(query);
  const filtered=all.filter(p=>(!category||p.category===category)&&norm([p.name,p.code,p.id].join(' ')).includes(term))
    .sort((a,b)=>a.name.localeCompare(b.name,'pt-BR')||a.id-b.id);
  const size=Math.max(1,Math.floor(Number(pageSize)||7)),pages=Math.max(1,Math.ceil(filtered.length/size)),currentPage=Math.min(pages,Math.max(1,Math.floor(Number(page)||1)));
  return {available:available.length,linked:current.length,categories,count:filtered.length,page:currentPage,pages,
    from:filtered.length?(currentPage-1)*size+1:0,to:Math.min(currentPage*size,filtered.length),rows:filtered.slice((currentPage-1)*size,currentPage*size),availableProducts:available};
}
let linkVersion=0,linkPending=false,linkState=null;
const supplierLinkPrice=p=>p==null?'—':p.toLocaleString('pt-BR',{style:'currency',currency:'BRL'});
const supplierLinkCategory=c=>c?c.replace(/_/g,' ').toLocaleLowerCase('pt-BR').replace(/^./,x=>x.toUpperCase()):'—';
function linkIsCurrent(state){
  return linkState===state&&state.version===linkVersion&&document.getElementById('modal-vincular')===state.modal&&state.modal.classList.contains('open');
}
function renderSupplierLinks(state){
  if(!linkIsCurrent(state))return;
  const get=id=>document.getElementById(id);
  const model=supplierLinkModel(state.products,state.linked,state.tab,state.query,state.category,state.page);
  const button=get('btn-confirmar-vincular'),results=get('supplier-link-results');
  const selected=state.selected!=null&&model?.availableProducts.find(p=>p.id===state.selected);
  const ready=!!model&&!state.loading&&!state.uncertain;
  button.disabled=linkPending||!ready||!selected;
  button.textContent=linkPending?'Vinculando…':'Vincular produto';
  get('supplier-link-refresh').disabled=linkPending||state.loading;
  get('supplier-link-search').disabled=get('supplier-link-category').disabled=!ready||linkPending;
  state.modal.querySelectorAll('[data-link-tab]').forEach(b=>{b.disabled=!ready||linkPending;b.setAttribute('aria-pressed',String(b.dataset.linkTab===state.tab));});
  get('supplier-link-selection').textContent=selected?'Selecionado: '+selected.name:'Nenhum produto selecionado.';
  get('supplier-available-count').textContent=ready?model.available:'—';
  get('supplier-linked-count').textContent=ready?model.linked:'—';
  get('vincular-produtos-atuais').textContent=ready?model.linked+' vinculados':'Vínculos não confirmados';
  get('supplier-link-prev').disabled=!ready||linkPending||model.page<=1;
  get('supplier-link-next').disabled=!ready||linkPending||model.page>=model.pages;
  get('supplier-link-range').textContent=ready?(model.count?model.from+'–'+model.to+' de '+model.count+' produtos':'0 resultados'):'';
  if(!ready){results.textContent=state.loading?'Consultando catálogo e vínculos…':'Atualize o catálogo para consultar os produtos.';return;}
  state.page=model.page;
  const category=get('supplier-link-category');
  category.replaceChildren();
  for(const [value,label] of [['','Todas as categorias'],...model.categories.map(c=>[c,supplierLinkCategory(c)])]){
    const opt=document.createElement('option');opt.value=value;opt.textContent=label;category.appendChild(opt);
  }
  category.value=state.category;
  if(!model.rows.length){
    results.textContent=state.query||state.category?'Nenhum produto corresponde aos filtros.':state.tab==='linked'?'Nenhum produto vinculado.':'Nenhum produto disponível para vincular.';
    return;
  }
  const esc=escapeHtml;
  results.innerHTML='<table class="supplier-link-table"><thead><tr><th scope="col">Produto</th><th scope="col">Categoria</th><th scope="col">Preço de cadastro</th><th scope="col">'+(state.tab==='linked'?'Vínculo':'Selecionar')+'</th></tr></thead><tbody>'+
    model.rows.map(p=>'<tr'+(p.id===state.selected?' class="is-selected"':'')+'><td><strong>'+esc(p.name)+'</strong><small>#'+p.id+(p.code?' · '+esc(p.code):'')+(p.active?'':' · Inativo')+'</small></td><td>'+esc(supplierLinkCategory(p.category))+'</td><td>'+esc(supplierLinkPrice(p.price))+'</td><td>'+
      (p.linked?'<span class="supplier-link-linked"><span aria-hidden="true"></span>Vinculado</span>':'<button type="button" data-link-product="'+p.id+'" aria-pressed="'+String(p.id===state.selected)+'" aria-label="Selecionar '+esc(p.name)+'"'+(linkPending?' disabled':'')+'>'+(p.id===state.selected?'Selecionado':'Selecionar')+'</button>')+'</td></tr>').join('')+'</tbody></table>';
  results.querySelectorAll('[data-link-product]').forEach(b=>b.onclick=()=>{
    if(linkPending)return;
    const id=Number(b.dataset.linkProduct);state.selected=state.selected===id?null:id;renderSupplierLinks(state);
    const next=results.querySelector('[data-link-product="'+id+'"]');next?.focus({preventScroll:true});
  });
}
async function refreshSupplierLinks(state){
  if(!linkIsCurrent(state)||state.loading)return;
  state.loading=true;state.selected=null;renderSupplierLinks(state);
  try{
    const [a,b]=await Promise.all([fetch('/api/fornecedores'),fetch('/api/produtos')]);
    if(!a.ok||!b.ok)throw Error('Não foi possível consultar catálogo e vínculos.');
    const [suppliers,products]=await Promise.all([a.json(),b.json()]);
    if(!Array.isArray(suppliers)||!Array.isArray(products))throw Error('Resposta inválida do catálogo.');
    const supplier=suppliers.find(f=>Number(f.id)===state.id);
    if(!supplier||!Array.isArray(supplier.produtos))throw Error('Fornecedor ou vínculos indisponíveis.');
    if(!linkIsCurrent(state))return;
    state.products=products;state.linked=supplier.produtos;state.uncertain=false;
    document.getElementById('vincular-fornecedor-nome').textContent=supplier.nome;
    if(!supplierLinkModel(products,state.linked,state.tab).categories.includes(state.category))state.category='';
    todosFornecedores=suppliers;todosProdutos=products;
    renderTabela();
  }catch(error){
    if(linkIsCurrent(state)){
      state.products=null;state.linked=null;
      mostrarAlert('alert-vincular',error.message||'Não foi possível carregar os produtos.','error');
    }
  }finally{
    state.loading=false;renderSupplierLinks(state);
  }
}
function abrirModalVincular(id){
  if(linkPending)return;
  const fornecedor=todosFornecedores.find(f=>Number(f.id)===Number(id));
  if(!fornecedor)return;
  fornecedorVincularId=Number(id);
  limparAlert('alert-vincular');
  document.getElementById('vincular-fornecedor-nome').textContent=fornecedor.nome;
  const modal=document.getElementById('modal-vincular'),workspace=document.getElementById('supplier-link-workspace'),legacy=document.getElementById('supplier-link-legacy');
  const managed=!!document.querySelector('.app.stockos-shell-v2');
  workspace.hidden=!managed;legacy.hidden=managed;
  const button=document.getElementById('btn-confirmar-vincular');button.disabled=true;
  const select=document.getElementById('vincular-produto-select');
  if(!managed){
    linkState=null;linkVersion++;
    const model=supplierLinkModel(todosProdutos,fornecedor.produtos);
    document.getElementById('vincular-produtos-atuais').textContent=fornecedor.produtos.length+' produtos vinculados';
    select.replaceChildren();
    const placeholder=document.createElement('option');placeholder.value='';placeholder.textContent='Selecione um produto';select.appendChild(placeholder);
    model.availableProducts.forEach(p=>{const opt=document.createElement('option');opt.value=String(p.id);opt.textContent=p.name+' ('+supplierLinkPrice(p.price)+')';select.appendChild(opt);});
    select.value='';select.onchange=()=>button.disabled=!select.value;
    abrirModal('modal-vincular');return;
  }
  const state={version:++linkVersion,id:Number(id),modal,tab:'available',query:'',category:'',page:1,selected:null,products:null,linked:null,loading:false,uncertain:false};
  linkState=state;
  const search=document.getElementById('supplier-link-search'),category=document.getElementById('supplier-link-category');
  search.value='';
  search.oninput=()=>{state.query=search.value;state.page=1;renderSupplierLinks(state);};
  category.onchange=()=>{state.category=category.value;state.page=1;renderSupplierLinks(state);};
  modal.querySelectorAll('[data-link-tab]').forEach(b=>b.onclick=()=>{state.tab=b.dataset.linkTab;state.category='';state.page=1;renderSupplierLinks(state);});
  document.getElementById('supplier-link-prev').onclick=()=>{state.page--;renderSupplierLinks(state);};
  document.getElementById('supplier-link-next').onclick=()=>{state.page++;renderSupplierLinks(state);};
  document.getElementById('supplier-link-refresh').onclick=()=>{limparAlert('alert-vincular');void refreshSupplierLinks(state);};
  abrirModal('modal-vincular');void refreshSupplierLinks(state);
}
async function confirmarVincular(){
  if(linkPending)return;
  const state=linkState,modal=document.getElementById('modal-vincular'),button=document.getElementById('btn-confirmar-vincular');
  const produtoId=state?state.selected:Number(document.getElementById('vincular-produto-select').value);
  if(!Number.isSafeInteger(produtoId)||produtoId<=0||(state&&(!linkIsCurrent(state)||state.loading||state.uncertain||!supplierLinkModel(state.products,state.linked)?.availableProducts.some(p=>p.id===produtoId)))){
    mostrarAlert('alert-vincular','Selecione um produto ainda não vinculado.','error');return;
  }
  const fornecedorId=fornecedorVincularId;
  linkPending=true;button.disabled=true;
  if(state)renderSupplierLinks(state);
  try{
    const resp=await fetch('/api/fornecedores/'+fornecedorId+'/vincular-produto',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({produtoId})});
    if(!resp.ok){
      if(resp.status>=500)throw Error('Resultado incerto.');
      mostrarAlert('alert-vincular',await safeReadErrorMessage(resp,'Erro ao vincular.'),'error');return;
    }
    if(document.getElementById('modal-vincular')!==modal)return;
    if(state&&linkIsCurrent(state)){
      state.selected=null;
      mostrarAlert('alert-vincular','Produto vinculado com sucesso.','success');
      await refreshSupplierLinks(state);
    }else if(!state){
      linkPending=false;fecharModal('modal-vincular');await carregarFornecedores();
    }
  }catch(error){
    if(document.getElementById('modal-vincular')!==modal)return;
    if(state){state.uncertain=true;state.selected=null;}
    mostrarAlert('alert-vincular','Não foi possível confirmar o resultado. Atualize os vínculos antes de tentar novamente.','error');
  }finally{
    linkPending=false;
    if(state)renderSupplierLinks(state);else button.disabled=!document.getElementById('vincular-produto-select')?.value;
  }
}

function verProdutos(id) {
  const fornecedor = todosFornecedores.find(f => f.id === id);
  document.getElementById('modal-produtos-titulo').textContent = `Produtos — ${fornecedor.nome}`;
  const lista = document.getElementById('produtos-lista');
  if (fornecedor.produtos.length === 0) {
    lista.innerHTML = '<div class="empty">Nenhum produto vinculado.</div>';
  } else {
    lista.innerHTML = `
      <div class="itens-nota-list">
        ${fornecedor.produtos.map(p => `
          <div class="item-nota-row">
            <span class="item-nota-nome">${escapeHtml(p.nome)}</span>
            <span class="item-nota-preco">R$${p.precoUnitario.toFixed(2)}</span>
          </div>
        `).join('')}
      </div>`;
  }
  abrirModal('modal-produtos');
}

// ── Modal helpers ─────────────────────────────────────────────────────────

function abrirModalCadastro() {
  if(supplierCreatePending)return;
  supplierCreateUncertain=false;
  limparAlert('alert-cadastro');
  const name=document.getElementById('c-nome'),cnpj=document.getElementById('c-cnpj'),phone=document.getElementById('c-telefone'),btn=document.getElementById('btn-cadastrar-fornecedor');
  name.value=cnpj.value=phone.value='';
  btn.disabled=false;btn.textContent='Cadastrar fornecedor';
  cnpj.oninput=updateSupplierCreate;
  cnpj.onblur=()=>{const digits=supplierCnpjDigits(cnpj.value);if(digits.length===14)cnpj.value=supplierFormatCnpj(digits);updateSupplierCreate();};
  updateSupplierCreate();
  abrirModal('modal-cadastro');name.focus();
}

function abrirModal(id) { document.getElementById(id).classList.add('open'); }
function fecharModal(id) { if(id==='modal-cadastro'&&supplierCreatePending)return; if(id==='modal-vincular'){if(linkPending)return;linkVersion++;linkState=null;} document.getElementById(id).classList.remove('open'); }
function fecharModalSeClicouFora(e, id) { if (e.target.id === id) fecharModal(id); }
function mostrarAlert(id, msg, tipo) {
  const el = document.getElementById(id);
  el.textContent = msg;
  el.className = `alert-modal ${tipo}`;
}
function limparAlert(id) {
  const el = document.getElementById(id);
  el.textContent = '';
  el.className = 'alert-modal';
}

/* Page-scoped bridge: legacy inline event handlers resolve only against the active page. */
window.StockOSPages = window.StockOSPages || {};
window.StockOSPages.fornecedores = {
  mount: stockOSMount_fornecedores,
  activate() {
    Object.assign(window, {
    carregarTudo,
    carregarFornecedores,
    carregarProdutos,
    renderResumo,
    renderTabela,
    setFiltro,
    filtrar,
    cadastrarFornecedor,
    desativar,
    reativar,
    abrirModalVincular,
    confirmarVincular,
    verProdutos,
    abrirModalCadastro,
    abrirModal,
    fecharModal,
    fecharModalSeClicouFora,
    mostrarAlert,
    limparAlert,
  });
  }
};
if (/^\/fornecedores(?:\.html)?\/?$/.test(location.pathname)) {
  window.StockOSPages.fornecedores.activate();
}
document.addEventListener('DOMContentLoaded', stockOSMount_fornecedores);

})();
