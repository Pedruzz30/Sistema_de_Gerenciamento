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
const CLASSE_BADGE = {
  SUPERIOR:        'badge-purple',
  GERENTE_ESTOQUE: 'badge-cyan',
  ESTOQUISTA:      'badge-amber',
  CAIXA:           'badge-gray'
};

// ── Init ──────────────────────────────────────────────────────────────────

async function stockOSMount_funcionarios() {
  if (typeof paginaAtual === 'function' && paginaAtual() !== 'funcionarios') return;
  carregarFuncionarios();
}

// ── API ───────────────────────────────────────────────────────────────────

async function carregarFuncionarios() {
  const table=document.getElementById('tabela-funcionarios');
  if(!table)return;
  try {
    const resp = await fetch('/api/funcionarios');
    if (!resp.ok) throw new Error();
    const data=await resp.json();
    if(document.getElementById('tabela-funcionarios')!==table)return;
    if(!Array.isArray(data))throw Error('Resposta inválida');
    todosFuncionarios=data;
    renderTabela();
  } catch {
    if(document.getElementById('tabela-funcionarios')!==table)return;
    table.innerHTML =
      '<tr><td colspan="6" class="empty">Erro ao carregar funcionários.</td></tr>';
  }
}

// ── Render ────────────────────────────────────────────────────────────────

function renderResumo(listaVisivel) {
  const container = document.getElementById('funcionario-summary');
  if (!container) return;

  const ativos = todosFuncionarios.filter(f => f.ativo).length;
  const gestores = todosFuncionarios.filter(f => ['SUPERIOR', 'GERENTE_ESTOQUE'].includes(f.nomeClasse)).length;

  container.innerHTML = [
    {
      label: 'Total de funcionários',
      value: todosFuncionarios.length,
      sub: 'Cadastros com acesso potencial ao sistema.'
    },
    {
      label: 'Equipe ativa',
      value: ativos,
      sub: 'Usuários atualmente habilitados.'
    },
    {
      label: 'Perfis de gestão',
      value: gestores,
      sub: `${listaVisivel.length} resultado(s) visível(is) no filtro atual.`
    }
  ].map(card => `
    <div class="page-summary-card">
      <div class="page-summary-label">${card.label}</div>
      <div class="page-summary-value">${card.value}</div>
      <div class="page-summary-sub">${card.sub}</div>
    </div>
  `).join('');
}

function renderTabela() {
  const busca = document.getElementById('busca').value.toLowerCase();
  let lista = todosFuncionarios.filter(f => {
    const matchFiltro =
      filtroAtivo === 'todos' ||
      (filtroAtivo === 'ativos' && f.ativo) ||
      (filtroAtivo === 'inativos' && !f.ativo);
    const nomeCompleto = `${f.nome} ${f.sobrenome}`.toLowerCase();
    const matchBusca = !busca || nomeCompleto.includes(busca) || String(f.ru).includes(busca);
    return matchFiltro && matchBusca;
  });

  document.getElementById('table-count').textContent = `${lista.length} funcionário(s)`;
  renderResumo(lista);

  const tbody = document.getElementById('tabela-funcionarios');
  if (lista.length === 0) {
    if (todosFuncionarios.length === 0) {
      tbody.innerHTML = `
        <tr><td colspan="6" class="table-empty-cell">
          ${emptyStateMarkup({
            icon: 'users',
            title: 'Nenhum funcionário cadastrado',
            copy: 'Adicione funcionários para distribuir classes, permissões e acesso operacional.',
            actions: '<button class="btn-primary" onclick="abrirModalCadastro()">Novo Funcionário</button>'
          })}
        </td></tr>`;
    } else {
      tbody.innerHTML = '<tr><td colspan="6" class="empty">Nenhum funcionário encontrado para os filtros aplicados.</td></tr>';
    }
    return;
  }

  tbody.innerHTML = lista.map(f => {
    const classeLabel = CLASSE_LABEL[f.nomeClasse] || f.nomeClasse || '—';
    const classeBadge = CLASSE_BADGE[f.nomeClasse] || 'badge-gray';
    const perms = Array.isArray(f.permissoes) ? f.permissoes.length : 0;
    // Escapado para caber dentro do atributo onclick, que usa aspas duplas.
    const nomeCompletoJs = escapeHtml(JSON.stringify(`${f.nome || ''} ${f.sobrenome || ''}`.trim()));
    const nomeClasseJs = escapeHtml(JSON.stringify(f.nomeClasse || ''));
    const podeRedefinir = f.ru !== usuario.ru && (f.perfil !== 'ADMIN' || usuario.perfil === 'ADMIN');

    return `
      <tr>
        <td class="table-cell-subtle table-cell-strong">#${f.ru}</td>
        <td>
          <div class="produto-nome">${escapeHtml(f.nome)} ${escapeHtml(f.sobrenome)}</div>
          <div class="produto-id">${escapeHtml(f.perfil || 'OPERADOR')}</div>
        </td>
        <td><span class="badge ${classeBadge}">${classeLabel}</span></td>
        <td class="table-cell-muted table-cell-subtle">${perms} permissão(ões)</td>
        <td>
          <span class="badge ${f.ativo ? 'badge-green' : 'badge-gray'}">
            ${f.ativo ? 'Ativo' : 'Inativo'}
          </span>
        </td>
        <td>
          <div class="acoes">
            ${f.ativo ? `
              <button class="btn-acao" onclick="abrirModalClasse(${f.ru}, ${nomeCompletoJs}, ${nomeClasseJs})">Classe</button>
              ${podeRedefinir ? `<button class="btn-acao" data-redefinir-senha="${f.ru}" onclick="redefinirSenhaFuncionario(${f.ru}, ${nomeCompletoJs})">Redefinir senha</button>` : ''}
              <button class="btn-acao saida" onclick="desativar(${f.ru})">Desativar</button>
            ` : `
              <span class="status-placeholder">Inativo</span>
            `}
          </div>
        </td>
      </tr>
    `;
  }).join('');
}

// ── Actions ───────────────────────────────────────────────────────────────

function setFiltro(tipo, btn) {
  filtroAtivo = tipo;
  document.querySelectorAll('.filter-btn').forEach(b => b.classList.remove('active'));
  btn.classList.add('active');
  renderTabela();
}

function filtrar() { renderTabela(); }

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

function abrirModalClasse(ru, nomeCompleto, classeAtual) {
  ruMudarClasse = ru;
  document.getElementById('classe-func-nome').textContent = nomeCompleto;
  document.getElementById('classe-atual').textContent = `Classe atual: ${CLASSE_LABEL[classeAtual] || classeAtual || '—'}`;
  document.getElementById('nova-classe').value = '';
  limparAlert('alert-classe');
  abrirModal('modal-classe');
}

async function confirmarMudarClasse() {
  const nomeClasse = document.getElementById('nova-classe').value;
  if (!nomeClasse) { mostrarAlert('alert-classe', 'Selecione uma classe.', 'error'); return; }

  const resp = await fetch(`/api/funcionarios/${ruMudarClasse}/classe`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ nomeClasse })
  });
  if (!resp.ok) {
    const err = await safeReadErrorMessage(resp, 'Erro ao alterar classe.');
    mostrarAlert('alert-classe', err, 'error'); return;
  }
  fecharModal('modal-classe');
  await carregarFuncionarios();
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
