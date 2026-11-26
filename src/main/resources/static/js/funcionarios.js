/* SPA-isolated screen: funcionarios. Retains direct HTML navigation. */
(() => {
'use strict';
// ── funcionarios.js ────────────────────────────────────────────────────────
// Employee management: list, create, change class, deactivate.

let todosFuncionarios = [];
let filtroAtivo = 'todos';
let ruMudarClasse = null;

// Class display helpers
const CLASSE_LABEL = {
  SUPERIOR:        'Superior',
  GERENTE_ESTOQUE: 'Gerente de Estoque',
  ESTOQUISTA:      'Estoquista',
  CAIXA:           'Caixa'
};
const employeeNorm=v=>String(v??'').normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase().trim();
function employeeClassLabel(value){
  if(value==='GERENTE')return 'Gerente';
  return Object.prototype.hasOwnProperty.call(CLASSE_LABEL,value)?CLASSE_LABEL[value]:value||'Não informada';
}
function employeeListModel(records,filters={},page=1,pageSize=15){
  if(!Array.isArray(records))return null;
  const rows=records.map(f=>({ru:String(f.ru),name:[f.nome,f.sobrenome].filter(Boolean).join(' ').trim()||'Nome não informado',role:String(f.nomeClasse||''),profile:String(f.perfil||''),active:f.ativo===true,permissions:Array.isArray(f.permissoes)?[...new Set(f.permissoes.filter(p=>typeof p==='string'))]:null}));
  const active=rows.filter(f=>f.active).length;
  // Use actual returned permissions, not assumptions based on role names.
  const managers=rows.filter(f=>f.active&&f.permissions?.includes('GERENCIAR_FUNCIONARIOS')).length;
  const unknownPermissions=rows.filter(f=>f.active&&f.permissions===null).length;
  const classes=[...new Set(rows.map(f=>f.role))].sort((a,b)=>employeeClassLabel(a).localeCompare(employeeClassLabel(b),'pt-BR'));
  const term=employeeNorm(filters.query).replace(/^#/,'');
  const filtered=rows.filter(f=>(filters.status!=='ativos'||f.active)&&(filters.status!=='inativos'||!f.active)&&(!filters.role||(filters.role==='__unknown__'?f.role==='':f.role===filters.role))&&(!term||employeeNorm(f.name+' '+f.ru).includes(term))).sort((a,b)=>Number(b.active)-Number(a.active)||a.name.localeCompare(b.name,'pt-BR',{sensitivity:'base'})||a.ru.localeCompare(b.ru,'pt-BR',{numeric:true}));
  const size=[10,15,25,50].includes(Number(pageSize))?Number(pageSize):15,pages=Math.max(1,Math.ceil(filtered.length/size)),current=Math.min(pages,Math.max(1,Math.floor(Number(page)||1)));
  return {total:rows.length,active,inactive:rows.length-active,managers,unknownPermissions,classes,count:filtered.length,pages,page:current,from:filtered.length?(current-1)*size+1:0,to:Math.min(current*size,filtered.length),rows:filtered.slice((current-1)*size,current*size)};
}
let employeeListPage=1,employeeListVersion=0,employeeListReady=false,employeeListLoading=false;
const employeeListGet=id=>document.getElementById(id);
async function stockOSMount_funcionarios(){
  if(typeof paginaAtual==='function'&&paginaAtual()!=='funcionarios')return;
  if(!employeeListGet('tabela-funcionarios'))return;
  filtroAtivo='todos';employeeListPage=1;
  employeeListGet('employee-class-filter').onchange=filtrar;
  employeeListGet('employee-page-size').onchange=filtrar;
  employeeListGet('employee-refresh').onclick=()=>void carregarFuncionarios();
  employeeListGet('employee-clear').onclick=()=>{
    employeeListGet('busca').value='';employeeListGet('employee-class-filter').value='';
    setFiltro('todos',document.querySelector('.employee-directory [data-status="todos"]'));
  };
  employeeListGet('employee-prev').onclick=()=>{employeeListPage--;renderTabela();};
  employeeListGet('employee-next').onclick=()=>{employeeListPage++;renderTabela();};
  await carregarFuncionarios();
}
function employeePopulateClasses(){
  const select=employeeListGet('employee-class-filter'),old=select.value,model=employeeListModel(todosFuncionarios);
  select.replaceChildren();
  const add=(value,label)=>{const o=document.createElement('option');o.value=value;o.textContent=label;select.appendChild(o);};
  add('','Todas as classes');model.classes.forEach(v=>add(v||'__unknown__',employeeClassLabel(v)));
  select.value=[...select.options].some(o=>o.value===old)?old:'';
}
async function carregarFuncionarios(){
  const table=employeeListGet('tabela-funcionarios');if(!table)return;
  const version=++employeeListVersion,current=()=>employeeListGet('tabela-funcionarios')===table&&version===employeeListVersion;
  employeeListLoading=true;employeeListReady=false;renderTabela();
  try{
    const resp=await fetch('/api/funcionarios');if(!resp.ok)throw Error();
    const data=await resp.json();
    if(!current())return;
    if(!Array.isArray(data)||data.some(f=>!f||!/^\d+$/.test(String(f.ru))||typeof f.ativo!=='boolean'))throw Error('Resposta inválida');
    todosFuncionarios=data;employeePopulateClasses();employeeListReady=true;
  }catch{if(current())todosFuncionarios=[];}
  finally{if(current()){employeeListLoading=false;renderTabela();}}
}
function renderResumo(model){
  const container=employeeListGet('funcionario-summary');if(!container)return;
  const cards=[
    ['Total de funcionários',model?.total,'Todos os cadastros','neutral'],
    ['Equipe ativa',model?.active,'Contas habilitadas','success'],
    ['Contas inativas',model?.inactive,'Acesso desativado','neutral'],
    ['Gestão de funcionários',model?(model.unknownPermissions?model.managers+'+':model.managers):null,model?.unknownPermissions?'Contas ativas confirmadas; dados incompletos':'Ativos com permissão de gestão','neutral']
  ];
  container.innerHTML=cards.map(([label,value,sub,tone])=>'<div class="page-summary-card" data-tone="'+tone+'"><div class="page-summary-label">'+label+'</div><div class="page-summary-value">'+(value??'—')+'</div><div class="page-summary-sub">'+sub+'</div></div>').join('');
}
function renderTabela(){
  const get=employeeListGet,tbody=get('tabela-funcionarios');if(!tbody)return;
  const model=employeeListReady?employeeListModel(todosFuncionarios,{query:get('busca').value,status:filtroAtivo,role:get('employee-class-filter').value},employeeListPage,get('employee-page-size').value):null;
  renderResumo(model);
  get('employee-refresh').disabled=employeeListLoading;
  get('table-count').textContent=model?model.count+' de '+model.total:'—';
  get('employee-prev').disabled=!model||model.page<=1;get('employee-next').disabled=!model||model.page>=model.pages;
  get('employee-range').textContent=model?(model.count?model.from+'–'+model.to+' de '+model.count+' · página '+model.page+' de '+model.pages:'0 resultados'):'';
  if(!model){tbody.innerHTML='<tr><td colspan="6" class="empty">'+(employeeListLoading?'Carregando funcionários…':'Não foi possível carregar a equipe. Use Atualizar para tentar novamente.')+'</td></tr>';return;}
  employeeListPage=model.page;
  if(!model.rows.length){tbody.innerHTML='<tr><td colspan="6" class="empty">'+(model.total?'Nenhum funcionário corresponde aos filtros.':'Nenhum funcionário cadastrado.')+'</td></tr>';return;}
  const esc=escapeHtml;
  tbody.innerHTML=model.rows.map(f=>{
    const initials=f.name.split(/\s+/).slice(0,2).map(x=>Array.from(x)[0]||'').join('').toUpperCase();
    const canReset=String(f.ru)!==String(usuario?.ru)&&(f.profile!=='ADMIN'||usuario?.perfil==='ADMIN');
    const nameJs=esc(JSON.stringify(f.name)),roleJs=esc(JSON.stringify(f.role)),idJs=esc(JSON.stringify(f.ru));
    const own=String(f.ru)===String(usuario?.ru);
    return '<tr><td class="employee-ru">#'+esc(f.ru)+'</td><td><div class="employee-person"><span class="employee-initials" aria-hidden="true">'+esc(initials)+'</span><div><strong>'+esc(f.name)+'</strong><small>'+esc(f.profile||'Perfil não informado')+(own?' · Sua conta':'')+'</small></div></div></td><td><span class="employee-role-label">'+esc(employeeClassLabel(f.role))+'</span></td><td><span class="employee-permission-count">'+(f.permissions===null?'—':f.permissions.length)+'</span><small class="employee-permission-caption">'+(f.permissions===null?'Não informadas':f.permissions.includes('GERENCIAR_FUNCIONARIOS')?'Inclui gestão de equipe':f.permissions.length===1?'permissão':'permissões')+'</small></td><td><span class="employee-status" data-active="'+f.active+'"><span aria-hidden="true"></span>'+(f.active?'Ativo':'Inativo')+'</span></td><td><div class="acoes employee-row-actions">'+(f.active?'<button type="button" class="btn-acao" onclick="abrirModalClasse('+idJs+', '+nameJs+', '+roleJs+')">Alterar classe</button>'+(canReset?'<button type="button" class="btn-acao" data-redefinir-senha="'+esc(f.ru)+'" onclick="redefinirSenhaFuncionario('+idJs+', '+nameJs+')">Redefinir senha</button>':'')+'<button type="button" class="btn-acao saida" onclick="desativar('+idJs+')">Desativar</button>':'<span class="status-placeholder">Sem ações disponíveis</span>')+'</div></td></tr>';
  }).join('');
}
function setFiltro(tipo,btn){
  filtroAtivo=['todos','ativos','inativos'].includes(tipo)?tipo:'todos';
  document.querySelectorAll('.employee-directory .filter-btn').forEach(b=>{const selected=b===btn;b.classList.toggle('active',selected);b.setAttribute('aria-pressed',String(selected));});
  filtrar();
}
function filtrar(){employeeListPage=1;renderTabela();}

const EMPLOYEE_ROLE_ACCESS={
  SUPERIOR:{description:'Acesso total ao sistema. Reserve para pessoas autorizadas a administrar toda a operação.',permissions:['Consultar estoque','Editar estoque','Compras','Vendas','Finanças','Gerenciar funcionários','Logs de auditoria']},
  GERENTE_ESTOQUE:{description:'Gerencia estoque, compras e funções administrativas.',permissions:['Consultar estoque','Editar estoque','Compras','Finanças','Gerenciar funcionários','Logs de auditoria']},
  ESTOQUISTA:{description:'Consulta e movimenta itens de estoque.',permissions:['Consultar estoque','Editar estoque']},
  CAIXA:{description:'Opera vendas e consulta o estoque.',permissions:['Vendas','Consultar estoque']}
};
function employeeCpfMask(value){
  return String(value??'').replace(/\D/g,'').slice(0,11).replace(/^(\d{3})(\d)/,'$1.$2').replace(/^(\d{3}\.\d{3})(\d)/,'$1.$2').replace(/^(\d{3}\.\d{3}\.\d{3})(\d)/,'$1-$2');
}
function employeeCpfValid(value){
  const digits=String(value??'').replace(/\D/g,'');
  if(!/^\d{11}$/.test(digits)||/^(\d)\1+$/.test(digits))return false;
  for(const length of [9,10]){
    let sum=0;for(let i=0;i<length;i++)sum+=Number(digits[i])*(length+1-i);
    const remainder=(sum*10)%11;
    if(Number(digits[length])!==(remainder===10?0:remainder))return false;
  }
  return true;
}
function employeePasswordValid(value){
  return typeof value==='string'&&value.length>=6&&!!value.trim()&&new Set(value).size>1&&value!=='123456'&&new TextEncoder().encode(value).length<=72;
}
function employeeCreateInput(fields){
  const nome=String(fields.nome??'').trim(),sobrenome=String(fields.sobrenome??'').trim(),cpf=String(fields.cpf??'').replace(/\D/g,''),senha=fields.senha,nomeClasse=fields.nomeClasse;
  if(!nome||!sobrenome||!employeeCpfValid(cpf)||!employeePasswordValid(senha)||!Object.prototype.hasOwnProperty.call(EMPLOYEE_ROLE_ACCESS,nomeClasse)||(nomeClasse==='SUPERIOR'&&!fields.confirmed))return null;
  return {nome,sobrenome,cpf,senha,nomeClasse};
}
let employeeCreatePending=false,employeeCreateUncertain=false;
const employeeGet=id=>document.getElementById(id);
function employeeCreateFields(){
  return {nome:employeeGet('c-nome').value,sobrenome:employeeGet('c-sobrenome').value,cpf:employeeGet('c-cpf').value,senha:employeeGet('c-senha').value,nomeClasse:employeeGet('c-classe').value,confirmed:employeeGet('employee-elevated-confirm').checked};
}
function employeeHidePassword(){
  employeeGet('c-senha').type='password';
  const button=employeeGet('employee-password-toggle');button.textContent='Mostrar';button.setAttribute('aria-pressed','false');
}
function updateEmployeeCreate(){
  const fields=employeeCreateFields(),cpf=employeeGet('c-cpf'),help=employeeGet('employee-cpf-help'),digits=fields.cpf.replace(/\D/g,''),valid=employeeCpfValid(fields.cpf);
  help.textContent=digits.length===11?(valid?'CPF com dígitos verificadores válidos.':'CPF inválido. Confira os dígitos do documento.'):'Informe os 11 dígitos do documento.';
  cpf.setAttribute('aria-invalid',String(digits.length===11&&!valid));help.dataset.tone=digits.length===11?(valid?'success':'error'):'neutral';
  const password=employeeGet('employee-password-help'),passValid=employeePasswordValid(fields.senha);
  password.textContent=fields.senha&&!passValid?'Use de 6 caracteres a 72 bytes, sem sequências triviais como 123456 ou caracteres repetidos.':'Use pelo menos 6 caracteres. Entregue a senha somente ao funcionário.';
  password.dataset.tone=fields.senha&&!passValid?'error':'neutral';
  employeeGet('c-senha').setAttribute('aria-invalid',String(!!fields.senha&&!passValid));
  const elevated=fields.nomeClasse==='SUPERIOR';employeeGet('employee-elevated-wrap').hidden=!elevated;
  if(!elevated)employeeGet('employee-elevated-confirm').checked=false;
  employeeGet('employee-create-submit').disabled=employeeCreatePending||employeeCreateUncertain||!employeeCreateInput(fields);
}
function drawEmployeeRole(){
  const role=employeeGet('c-classe').value,preview=employeeGet('employee-role-preview'),item=Object.prototype.hasOwnProperty.call(EMPLOYEE_ROLE_ACCESS,role)?EMPLOYEE_ROLE_ACCESS[role]:null;
  preview.dataset.tone=role==='SUPERIOR'?'warning':'neutral';
  preview.innerHTML=item?'<strong>'+CLASSE_LABEL[role]+' · '+item.permissions.length+' permissões</strong><p>'+item.description+'</p><div class="employee-permission-list">'+item.permissions.map(p=>'<span>'+p+'</span>').join('')+'</div>':'<strong>Escolha uma classe</strong><p>As permissões correspondentes serão exibidas aqui.</p>';
}
function bindEmployeeCreate(){
  for(const id of ['c-nome','c-sobrenome','c-senha'])employeeGet(id).oninput=updateEmployeeCreate;
  employeeGet('c-cpf').oninput=()=>{const input=employeeGet('c-cpf'),pos=input.selectionStart??input.value.length,digits=input.value.slice(0,pos).replace(/\D/g,'').length;input.value=employeeCpfMask(input.value);let cursor=0,count=0;while(cursor<input.value.length&&count<digits){if(/\d/.test(input.value[cursor]))count++;cursor++;}input.setSelectionRange(cursor,cursor);updateEmployeeCreate();};
  employeeGet('c-classe').onchange=()=>{employeeGet('employee-elevated-confirm').checked=false;drawEmployeeRole();updateEmployeeCreate();};
  employeeGet('employee-elevated-confirm').onchange=updateEmployeeCreate;
  employeeGet('employee-password-toggle').onclick=()=>{const input=employeeGet('c-senha'),button=employeeGet('employee-password-toggle'),show=input.type==='password';input.type=show?'text':'password';button.textContent=show?'Ocultar':'Mostrar';button.setAttribute('aria-pressed',String(show));};
}
async function cadastrarFuncionario(){
  if(employeeCreatePending||employeeCreateUncertain)return;
  const payload=employeeCreateInput(employeeCreateFields());
  if(!payload){mostrarAlert('alert-cadastro','Confira os dados pessoais, senha e classe de acesso.','error');return;}
  const modal=employeeGet('modal-cadastro'),current=()=>employeeGet('modal-cadastro')===modal&&modal.classList.contains('open');
  const controls=[...modal.querySelectorAll('input,select,button')].map(el=>[el,el.disabled]);
  employeeCreatePending=true;limparAlert('alert-cadastro');employeeHidePassword();controls.forEach(([el])=>el.disabled=true);
  try{
    const resp=await fetch('/api/funcionarios',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(payload)});
    if(!current())return;
    if(!resp.ok){
      if(resp.status>=500||resp.status===409)throw Error('Resultado incerto');
      const err=await safeReadErrorMessage(resp,'Erro ao cadastrar.');if(current())mostrarAlert('alert-cadastro',err,'error');return;
    }
    // Do not retain credentials in the DOM after a successful registration.
    employeeGet('c-senha').value='';modal.classList.remove('open');
    showToast('Funcionário cadastrado.','success');await carregarFuncionarios();
  }catch{
    if(current()){employeeCreateUncertain=true;employeeGet('c-senha').value='';mostrarAlert('alert-cadastro','Não foi possível confirmar o cadastro. Feche a janela e atualize a lista de funcionários antes de tentar novamente.','error');}
  }finally{
    employeeCreatePending=false;controls.forEach(([el,disabled])=>{if(el.isConnected)el.disabled=disabled;});
    if(current())updateEmployeeCreate();
  }
}

const EMPLOYEE_PERMISSION_LABELS={
  VER_ESTOQUE:'Consultar estoque',EDITAR_ESTOQUE:'Editar estoque',VER_COMPRAS:'Compras',VER_VENDAS:'Vendas',VER_FINANCAS:'Finanças',GERENCIAR_FUNCIONARIOS:'Gerenciar funcionários',VER_LOGS:'Logs de auditoria'
};
const EMPLOYEE_CLASS_PERMISSIONS={
  SUPERIOR:Object.keys(EMPLOYEE_PERMISSION_LABELS),
  GERENTE_ESTOQUE:['VER_ESTOQUE','EDITAR_ESTOQUE','VER_COMPRAS','VER_FINANCAS','GERENCIAR_FUNCIONARIOS','VER_LOGS'],
  ESTOQUISTA:['VER_ESTOQUE','EDITAR_ESTOQUE'],
  CAIXA:['VER_VENDAS','VER_ESTOQUE']
};
function employeePermissionLabel(code){
  return Object.prototype.hasOwnProperty.call(EMPLOYEE_PERMISSION_LABELS,code)?EMPLOYEE_PERMISSION_LABELS[code]:code;
}
function employeeClassDiff(employee,target,actorRu){
  if(!employee||employee.ativo!==true||!Array.isArray(employee.permissoes)||employee.permissoes.some(p=>typeof p!=='string')||!Object.prototype.hasOwnProperty.call(EMPLOYEE_CLASS_PERMISSIONS,target)||target===employee.nomeClasse)return null;
  const before=[...new Set(employee.permissoes)],after=EMPLOYEE_CLASS_PERMISSIONS[target];
  const added=after.filter(p=>!before.includes(p)),removed=before.filter(p=>!after.includes(p)),kept=after.filter(p=>before.includes(p));
  const own=String(employee.ru)===String(actorRu),elevated=target==='SUPERIOR'||added.includes('GERENCIAR_FUNCIONARIOS')||added.includes('VER_FINANCAS')||added.includes('VER_LOGS');
  return {added,removed,kept,own,elevated,requiresConfirmation:own||elevated};
}
function employeeClassSnapshotEqual(a,b){
  if(!a||!b||String(a.ru)!==String(b.ru)||a.nomeClasse!==b.nomeClasse||a.ativo!==b.ativo||!Array.isArray(a.permissoes)||!Array.isArray(b.permissoes))return false;
  const before=[...new Set(a.permissoes)].sort(),after=[...new Set(b.permissoes)].sort();
  return before.length===after.length&&before.every((p,i)=>p===after[i]);
}
let employeeClassTarget=null,employeeClassPending=false,employeeClassUncertain=false;
function updateEmployeeClass(){
  const get=employeeGet,select=get('nova-classe'),target=select.value,person=employeeClassTarget;
  const model=employeeClassDiff(person,target,usuario?.ru);
  get('classe-atual').textContent=employeeClassLabel(person?.nomeClasse);
  get('employee-class-current-count').textContent=Array.isArray(person?.permissoes)?new Set(person.permissoes).size+' permissões atuais':'Permissões não informadas';
  get('employee-class-description').textContent=Object.prototype.hasOwnProperty.call(EMPLOYEE_ROLE_ACCESS,target)?EMPLOYEE_ROLE_ACCESS[target].description:'Escolha o novo nível de acesso.';
  const esc=escapeHtml;
  const group=(title,rows,tone)=>'<div class="employee-class-diff-group" data-tone="'+tone+'"><div><strong>'+title+'</strong><span>'+rows.length+'</span></div>'+(rows.length?'<ul>'+rows.map(p=>'<li>'+esc(employeePermissionLabel(p))+'</li>').join('')+'</ul>':'<p>Nenhuma</p>')+'</div>';
  get('employee-class-diff').innerHTML=model?'<div class="employee-class-diff-grid">'+group('Adicionadas',model.added,'added')+group('Removidas',model.removed,'removed')+group('Mantidas',model.kept,'kept')+'</div>':'<div class="employee-class-empty">'+(person&&!Array.isArray(person.permissoes)?'Permissões atuais indisponíveis. Atualize a equipe para comparar.':target===person?.nomeClasse?'A classe escolhida é igual à atual.':'Selecione uma nova classe para comparar os acessos.')+'</div>';
  const check=get('employee-class-confirm');
  get('employee-class-confirm-wrap').hidden=!model?.requiresConfirmation;
  if(!model?.requiresConfirmation)check.checked=false;
  get('employee-class-confirm-text').textContent=model?.own&&model?.elevated?'Confirmo a alteração da minha própria conta e a concessão de acesso administrativo.':model?.own?'Confirmo a alteração da minha própria conta; posso perder acesso a módulos.':'Confirmo a concessão de acesso administrativo ou financeiro para este funcionário.';
  select.disabled=employeeClassPending||employeeClassUncertain;
  check.disabled=employeeClassPending||employeeClassUncertain;
  get('employee-class-save').disabled=employeeClassPending||employeeClassUncertain||!model||(model.requiresConfirmation&&!check.checked);
  get('employee-class-reconcile').hidden=!employeeClassUncertain;
  get('employee-class-reconcile').disabled=employeeClassPending;
}
function abrirModalClasse(ru,nomeCompleto,classeAtual){
  if(employeeClassPending||!employeeListReady)return;
  const person=todosFuncionarios.find(f=>String(f.ru)===String(ru));
  if(!person||person.ativo!==true){showToast('Atualize a equipe antes de alterar a classe.','error');return;}
  ruMudarClasse=ru;employeeClassTarget=person;employeeClassUncertain=false;
  const get=employeeGet,name=[person.nome,person.sobrenome].filter(Boolean).join(' ')||'Funcionário';
  get('classe-func-nome').textContent=name;
  get('employee-class-initials').textContent=name.split(/\s+/).slice(0,2).map(x=>Array.from(x)[0]||'').join('').toUpperCase();
  get('employee-class-ru').textContent='RU #'+person.ru+(String(person.ru)===String(usuario?.ru)?' · Sua conta':'');
  const select=get('nova-classe');select.value='';
  for(const option of select.options)option.disabled=!!option.value&&option.value===person.nomeClasse;
  get('employee-class-confirm').checked=false;
  select.onchange=()=>{get('employee-class-confirm').checked=false;limparAlert('alert-classe');updateEmployeeClass();};
  get('employee-class-confirm').onchange=updateEmployeeClass;
  get('employee-class-reconcile').onclick=()=>void reconcileEmployeeClass();
  limparAlert('alert-classe');updateEmployeeClass();abrirModal('modal-classe');select.focus();
}
async function readEmployeeClass(ru){
  const resp=await fetch('/api/funcionarios');
  if(!resp.ok)throw Error('Falha na consulta');
  const records=await resp.json();
  if(!Array.isArray(records))throw Error('Resposta inválida');
  const person=records.find(f=>f&&String(f.ru)===String(ru));
  if(!person||typeof person.ativo!=='boolean'||!Array.isArray(person.permissoes))throw Error('Acesso indisponível');
  return person;
}
async function reconcileEmployeeClass(){
  if(employeeClassPending||!employeeClassUncertain||!employeeClassTarget)return;
  const get=employeeGet,modal=get('modal-classe'),ru=String(employeeClassTarget.ru),current=()=>get('modal-classe')===modal&&modal.classList.contains('open')&&String(employeeClassTarget?.ru)===ru;
  employeeClassPending=true;updateEmployeeClass();
  try{
    const person=await readEmployeeClass(ru);if(!current())return;
    employeeClassTarget=person;employeeClassUncertain=false;
    get('nova-classe').value='';for(const o of get('nova-classe').options)o.disabled=!!o.value&&o.value===person.nomeClasse;
    get('employee-class-confirm').checked=false;
    mostrarAlert('alert-classe',person.ativo?'Acesso atual consultado. Confira a classe antes de realizar outra alteração.':'O funcionário está inativo. Volte à listagem.','info');
    await carregarFuncionarios();
  }catch{if(current())mostrarAlert('alert-classe','Não foi possível consultar o acesso atual. Tente novamente ou volte à listagem.','error');}
  finally{employeeClassPending=false;if(current())updateEmployeeClass();}
}
async function confirmarMudarClasse(){
  if(employeeClassPending||employeeClassUncertain||!employeeClassTarget)return;
  const get=employeeGet,modal=get('modal-classe'),target=get('nova-classe').value,person=employeeClassTarget,ru=String(person.ru);
  const model=employeeClassDiff(person,target,usuario?.ru);
  if(!model||(model.requiresConfirmation&&!get('employee-class-confirm').checked))return;
  const current=()=>get('modal-classe')===modal&&modal.classList.contains('open')&&String(employeeClassTarget?.ru)===ru;
  const controls=[...modal.querySelectorAll('button')].map(el=>[el,el.disabled]);
  employeeClassPending=true;limparAlert('alert-classe');controls.forEach(([el])=>el.disabled=true);updateEmployeeClass();
  let submitted=false;
  try{
    const latest=await readEmployeeClass(ru);if(!current())return;
    if(!employeeClassSnapshotEqual(person,latest)){
      employeeClassUncertain=true;mostrarAlert('alert-classe','O acesso mudou desde a abertura. Consulte a situação atual antes de salvar.','error');return;
    }
    submitted=true;
    const resp=await fetch('/api/funcionarios/'+encodeURIComponent(ru)+'/classe',{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({nomeClasse:target})});
    if(!current())return;
    if(!resp.ok){
      if(resp.status>=500||resp.status===409)throw Error('Resultado incerto');
      const message=await safeReadErrorMessage(resp,'Erro ao alterar classe.');
      if(current())mostrarAlert('alert-classe',message,'error');return;
    }
    const result=await resp.json();
    if(!current())return;
    if(!result||String(result.ru)!==ru||result.nomeClasse!==target||!Array.isArray(result.permissoes))throw Error('Resposta inesperada');
    employeeClassTarget=null;modal.classList.remove('open');showToast('Classe alterada.','success');
    await carregarFuncionarios();
    if(model.own)showToast('Sua classe foi alterada. Entre novamente para atualizar os acessos.','info');
  }catch{
    if(current()){employeeClassUncertain=true;get('employee-class-confirm').checked=false;mostrarAlert('alert-classe',submitted?'Resultado não confirmado. Consulte o acesso atual antes de tentar novamente.':'Não foi possível conferir o acesso atual. Consulte a situação antes de salvar.','error');}
  }finally{
    employeeClassPending=false;controls.forEach(([el,disabled])=>{if(el.isConnected)el.disabled=disabled;});
    if(current())updateEmployeeClass();
  }
}

async function desativar(ru) {
  if (!confirm('Desativar este funcionário?')) return;
  const resp = await fetch(`/api/funcionarios/${ru}/desativar`, { method: 'PUT' });
  if (!resp.ok) {
    const err = await safeReadErrorMessage(resp, 'Erro ao desativar.');
    showToast(err, 'error'); return;
  }
  showToast('Funcionário desativado.', 'success');
  await carregarFuncionarios();
}

async function redefinirSenhaFuncionario(ru, nome) {
  if (!confirm(`Redefinir a senha de ${nome}? A senha atual para de valer na hora.`)) return;
  const resp = await fetch(`/api/funcionarios/${ru}/redefinir-senha`, { method: 'POST' });
  if (!resp.ok) {
    showToast(await safeReadErrorMessage(resp, 'Não foi possível redefinir a senha.'), 'error');
    return;
  }
  const { senhaTemporaria } = await resp.json();
  document.getElementById('senha-temp-nome').textContent = nome;
  document.getElementById('senha-temp-valor').textContent = senhaTemporaria;
  abrirModal('modal-senha-temporaria');
}

function fecharSenhaTemporaria() {
  document.getElementById('senha-temp-valor').textContent = '';
  fecharModal('modal-senha-temporaria');
}

// ── Modal helpers ─────────────────────────────────────────────────────────

function abrirModalCadastro() {
  if(employeeCreatePending)return;
  employeeCreateUncertain=false;limparAlert('alert-cadastro');
  ['c-nome','c-sobrenome','c-cpf','c-senha','c-classe'].forEach(id=>employeeGet(id).value='');
  employeeGet('employee-elevated-confirm').checked=false;
  employeeHidePassword();bindEmployeeCreate();drawEmployeeRole();updateEmployeeCreate();
  abrirModal('modal-cadastro');employeeGet('c-nome').focus();
}

function abrirModal(id) { document.getElementById(id).classList.add('open'); }
function fecharModal(id) {
  if(id==='modal-classe'){
    if(employeeClassPending)return;
    if(employeeClassUncertain)void carregarFuncionarios();
    employeeClassTarget=null;employeeClassUncertain=false;ruMudarClasse=null;
  }
  if(id==='modal-cadastro'){
    if(employeeCreatePending)return;
    employeeHidePassword();employeeGet('c-senha').value='';employeeGet('c-cpf').value='';
    if(employeeCreateUncertain)void carregarFuncionarios();
  }
  document.getElementById(id).classList.remove('open');
}
function fecharModalSeClicouFora(e, id) { if (e.target.id === id) fecharModal(id); }
function mostrarAlert(id, msg, tipo) {
  const el = document.getElementById(id);
  el.textContent = msg;
  el.className = `alert-modal ${tipo}`;
}
function limparAlert(id) {
  const el = document.getElementById(id);
  if (el) { el.textContent = ''; el.className = 'alert-modal'; }
}

/* Page-scoped bridge: legacy inline event handlers resolve only against the active page. */
window.StockOSPages = window.StockOSPages || {};
window.StockOSPages.funcionarios = {
  mount: stockOSMount_funcionarios,
  activate() {
    Object.assign(window, {
    carregarFuncionarios,
    renderResumo,
    renderTabela,
    setFiltro,
    filtrar,
    cadastrarFuncionario,
    abrirModalClasse,
    confirmarMudarClasse,
    desativar,
    redefinirSenhaFuncionario,
    fecharSenhaTemporaria,
    abrirModalCadastro,
    abrirModal,
    fecharModal,
    fecharModalSeClicouFora,
    mostrarAlert,
    limparAlert,
  });
  }
};
if (/^\/funcionarios(?:\.html)?\/?$/.test(location.pathname)) {
  window.StockOSPages.funcionarios.activate();
}
document.addEventListener('DOMContentLoaded', stockOSMount_funcionarios);

})();
