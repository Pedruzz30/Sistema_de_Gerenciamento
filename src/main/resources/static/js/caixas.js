/* SPA-isolated screen: caixas. Retains direct HTML navigation. */
(() => {
'use strict';
// ── caixas.js ─────────────────────────────────────────────────────────────
// Gerencia o ciclo de vida dos caixas PDV:
// abertura, PDV de vendas, sangria, suprimento e encerramento.

// ── Estado ────────────────────────────────────────────────────────────────

let caixas               = [];
let tipoMovAtual         = null;
let numeroCaixaAtual     = null;
let numeroCaixaEncerrar  = null;

const FORMATO_MOEDA = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });

/** R$ 1.234,56 (pt-BR). */
function moeda(valor) {
  return FORMATO_MOEDA.format(Number(valor || 0));
}

const ROTULOS_FORMA = { DINHEIRO: 'Dinheiro', PIX: 'Pix', DEBITO: 'Débito', CREDITO: 'Crédito', VOUCHER: 'Voucher' };

function buttonIcon(name) {
  return '<span class="btn-inline-icon" aria-hidden="true">' + iconMarkup(name) + '</span>';
}

// ── Permissões ────────────────────────────────────────────────────────────

const PODE_OPERAR_CAIXA         = temPermissao(PERMISSOES.VER_VENDAS);
const PODE_GERIR_FINANCAS_CAIXA = temPermissao(PERMISSOES.VER_FINANCAS);

// ── Init ──────────────────────────────────────────────────────────────────

async function stockOSMount_caixas() {
  if (typeof paginaAtual === 'function' && paginaAtual() !== 'caixas') return;
  aplicarPermissoesUI();
  await carregarCaixas();
  await cobrarComandaDaTela();
}

// Vindo da tela Comandas ("Cobrar no caixa"): abre o PDV no caixa aberto, já com a comanda.
async function cobrarComandaDaTela() {
  const numero = Number(new URLSearchParams(location.search).get('comanda'));
  if (!numero) return;
  history.replaceState(null, '', location.pathname);
  const abertos = caixas.filter(c => c.status === 'ABERTO').sort((a, b) => a.numeroCaixa - b.numeroCaixa);
  if (!abertos.length) {
    showToast(`Abra um caixa para cobrar a comanda ${numero}.`, 'error');
    return;
  }
  await abrirPDV(abertos[0].numeroCaixa);
  await abrirComandaPDV(numero);
}

// ── Permissões de UI ──────────────────────────────────────────────────────

function aplicarPermissoesUI() {
  const btnAbrir = document.querySelector('.header-right .btn-primary');
  if (btnAbrir && !PODE_OPERAR_CAIXA) btnAbrir.hidden = true;

  const consolidadoCard = document.getElementById('consolidado-card');
  if (consolidadoCard && !PODE_GERIR_FINANCAS_CAIXA) consolidadoCard.hidden = true;
}

function podeExecutarMovimentacao(tipo) {
  // Sangria e suprimento: o operador de caixa também pode, com a senha do
  // gerente na hora (o backend confere).
  if (['venda', 'sangria', 'suprimento'].includes(tipo)) return PODE_OPERAR_CAIXA;
  return false;
}

/** Operador sem permissão financeira precisa da autorização do gerente. */
function precisaAutorizacaoGerente() {
  return !temPermissao(PERMISSOES.VER_FINANCAS);
}

function prepararAutorizacao(prefixo) {
  const bloco = document.getElementById(prefixo + '-autorizacao');
  if (!bloco) return;
  bloco.hidden = !precisaAutorizacaoGerente();
  ['nome', 'sobrenome', 'senha'].forEach(campo => {
    const el = document.getElementById(prefixo + '-gerente-' + campo);
    if (el) el.value = '';
  });
}

function lerAutorizacao(prefixo) {
  if (!precisaAutorizacaoGerente()) return null;
  const valor = campo => (document.getElementById(prefixo + '-gerente-' + campo)?.value || '').trim();
  const nome = valor('nome');
  const senha = document.getElementById(prefixo + '-gerente-senha')?.value || '';
  if (!nome || !senha) return undefined; // faltou preencher
  return { nome, sobrenome: valor('sobrenome'), senha };
}

function mensagemSemPermissao(tipo) {
  if (tipo === 'venda' || tipo === 'abrir')
    return 'Você não tem permissão para operar vendas no caixa.';
  if (['sangria','suprimento','encerrar','consolidado'].includes(tipo))
    return 'Você não tem permissão financeira para esta ação.';
  return 'Você não tem permissão para esta ação.';
}

// ── API ───────────────────────────────────────────────────────────────────

async function carregarCaixas() {
  try {
    const resp = await fetch('/api/caixas');
    if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
    caixas = await resp.json();
    renderCaixas();
    renderResumo();
  } catch (err) {
    document.getElementById('caixas-grid').innerHTML =
      '<div class="empty">Erro ao carregar caixas. Verifique o servidor.</div>';
    console.error(err);
  }
}

async function abrirCaixa(numero, saldoInicial) {
  return fetch('/api/caixas/abrir', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ numeroCaixa: numero, saldoInicial })
  });
}

async function registrarMovimentacao(tipo, numero, valor, descricao, autorizacao) {
  return fetch(`/api/caixas/${tipo}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ numeroCaixa: numero, valor, descricao: descricao || '', autorizacao: autorizacao || null })
  });
}

async function encerrarCaixa(numero, valorContado, observacao, autorizacao, deixarComandasAbertas) {
  return fetch('/api/caixas/encerrar', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      numeroCaixa: numero, valorContado, observacao: observacao || '', autorizacao: autorizacao || null,
      deixarComandasAbertas: !!deixarComandasAbertas
    })
  });
}

async function carregarConsolidado() {
  if (!PODE_GERIR_FINANCAS_CAIXA) return;
  try {
    const resp = await fetch('/api/caixas/consolidado');
    if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
    const data = await resp.json();
    document.getElementById('consolidado-texto').textContent = data.resumo || 'Sem dados';
    document.getElementById('consolidado-card').hidden = false;
  } catch (err) {
    console.error('Erro ao carregar consolidado:', err);
  }
}

// ── Render: grid de caixas ────────────────────────────────────────────────


function cashCardMetrics(c) {
  const number=v=>v!=null&&String(v).trim()!==''&&Number.isFinite(Number(v))?Number(v):null;
  const forms=c.vendasPorForma;
  const keys=['PIX','DEBITO','CREDITO','VOUCHER'];
  const electronic=forms&&typeof forms==='object'&&!Array.isArray(forms)&&keys.every(k=>forms[k]==null||number(forms[k])!=null)
    ? keys.reduce((sum,k)=>sum+(number(forms[k])??0),0):null;
  return {cash:number(c.dinheiroNaGaveta??c.saldoAtual),initial:number(c.saldoInicial),sales:number(c.totalVendas),
    electronic,hasVoucher:(number(forms?.VOUCHER)??0)!==0,count:number(c.quantidadeVendas),ticket:number(c.ticketMedio)};
}
function renderCompactCashDetails(c,operador,abertura,detalheFormas) {
  const m=cashCardMetrics(c);
  const money=v=>v==null?'—':moeda(v);
  const number=v=>v==null?'—':v.toLocaleString('pt-BR');
  const row=(label,value)=>'<div class="caixa-detail-row"><span class="caixa-detail-label">'+label+'</span><span class="caixa-detail-value">'+value+'</span></div>';
  return `
    <div class="caixa-details stockos-cash-details">
      <div class="stockos-cash-card-metrics">
        <div class="caixa-detail-row stockos-cash-metric stockos-cash-metric--physical">
          <span class="caixa-detail-label" title="Fundo + vendas em dinheiro + suprimentos − sangrias − devoluções">Dinheiro na gaveta</span>
          <span class="caixa-detail-value">${money(m.cash)}</span><small>Saldo físico da sessão</small>
        </div>
        <div class="caixa-detail-row stockos-cash-metric">
          <span class="caixa-detail-label">Total Vendas</span>
          <span class="caixa-detail-value" title="${escapeHtml(detalheFormas||'Detalhamento indisponível')}">${money(m.sales)}</span><small>Acumulado da sessão</small>
        </div>
      </div>
      <div class="stockos-cash-card-breakdown">
        ${row('Saldo Inicial',money(m.initial))}
        ${row('Pix e cartões'+(m.hasVoucher?' / voucher':''),money(m.electronic))}
        ${row('Vendas registradas',number(m.count))}
        ${row('Ticket médio',money(m.ticket))}
      </div>
      <div class="stockos-cash-card-session">
        ${row('Operador',operador)}
        ${row('Abertura',escapeHtml(abertura))}
      </div>
    </div>`;
}

function renderCaixas() {
  const grid = document.getElementById('caixas-grid');

  if (caixas.length === 0) {
    grid.innerHTML = `
      <div class="empty-state grid-span-full">
        <div class="empty-state__icon" aria-hidden="true">${iconMarkup('wallet')}</div>
        <div class="empty-state__title">Nenhum caixa registrado</div>
        <div class="empty-state__copy">Abra um caixa para registrar vendas, suprimentos e fechamento diário.</div>
        ${PODE_OPERAR_CAIXA
          ? '<button class="btn-primary" onclick="abrirModalAbrirCaixa()">Abrir Caixa</button>'
          : '<div class="status-placeholder">Sem permissão para abrir caixa.</div>'}
      </div>`;
    return;
  }

  grid.innerHTML = caixas.map(c => {
    const caixaId = Number(c.caixaId ?? c.id ?? 0);
    const statusClass = c.status.toLowerCase();
    const statusLabel = { ABERTO: 'Aberto', FECHADO: 'Fechado', ENCERRADO: 'Encerrado' }[c.status] || c.status;
    const badgeClass  = c.status === 'ABERTO' ? 'badge-green' : c.status === 'ENCERRADO' ? 'badge-gray' : 'badge-amber';
    const operador    = escapeHtml(c.nomeOperador || '—');
    // Data + hora: um caixa esquecido aberto de ontem não pode parecer de hoje.
    const abertura    = c.dataAbertura
      ? new Date(c.dataAbertura).toLocaleString('pt-BR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' })
      : '—';
    const porForma    = c.vendasPorForma || {};
    const eletronico  = ['PIX', 'DEBITO', 'CREDITO', 'VOUCHER']
      .reduce((soma, forma) => soma + Number(porForma[forma] || 0), 0);
    const detalheFormas = ['DINHEIRO', 'PIX', 'DEBITO', 'CREDITO', 'VOUCHER']
      .filter(forma => Number(porForma[forma] || 0) !== 0)
      .map(forma => `${ROTULOS_FORMA[forma]} ${moeda(porForma[forma])}`)
      .join(' · ');
    const qtdVendas   = Number(c.quantidadeVendas || 0);

    const shellNovo = !!document.querySelector('.app.stockos-shell-v2');
    const botaoPDV = PODE_OPERAR_CAIXA && c.status === 'ABERTO'
      ? `<button class="btn-sm success stockos-pdv-entry" onclick="abrirPDV(${c.numeroCaixa})">${buttonIcon('cart')}PDV</button>`
      : '';
    const botoesAbertos = [
      shellNovo ? '' : botaoPDV,
      PODE_OPERAR_CAIXA
        ? `<button class="btn-sm" onclick="abrirModalMov('sangria', ${c.numeroCaixa}, ${c.dinheiroNaGaveta ?? c.saldoAtual ?? 'null'})">${buttonIcon('minus')}Sangria</button>`
        : '',
      PODE_OPERAR_CAIXA
        ? `<button class="btn-sm" onclick="abrirModalMov('suprimento', ${c.numeroCaixa}, ${c.dinheiroNaGaveta ?? c.saldoAtual ?? 'null'})">${buttonIcon('plus')}Suprimento</button>`
        : '',
      `<button class="btn-sm" onclick="abrirModalMovsLista(${caixaId}, ${c.numeroCaixa})">${buttonIcon('clipboard')}${shellNovo ? 'Movimentações' : 'Ver movs'}</button>`,
      PODE_OPERAR_CAIXA
        ? `<button class="btn-sm danger" onclick="abrirModalEncerrar(${c.numeroCaixa})">${buttonIcon('lock')}Encerrar</button>`
        : '',
    ].filter(Boolean).join('');

    return `
      <div class="caixa-card ${statusClass}${shellNovo ? ' stockos-cash-card' : ''}">
        <div class="caixa-card-header">
          ${shellNovo ? '<div class="stockos-cash-card-identity">' : ''}
          <span class="caixa-num">Caixa ${c.numeroCaixa}</span>
          <span class="badge ${badgeClass}">${statusLabel}</span>
          ${shellNovo ? '</div>' : ''}
          ${shellNovo ? botaoPDV : ''}
        </div>
        ${shellNovo ? renderCompactCashDetails(c, operador, abertura, detalheFormas) : `
        <div class="caixa-details">
          <div class="caixa-detail-row">
            <span class="caixa-detail-label">Operador</span>
            <span class="caixa-detail-value">${operador}</span>
          </div>
          <div class="caixa-detail-row">
            <span class="caixa-detail-label">Saldo Inicial</span>
            <span class="caixa-detail-value">${moeda(c.saldoInicial)}</span>
          </div>
          <div class="caixa-detail-row caixa-detail-row--cash">
            <span class="caixa-detail-label" title="Só dinheiro em espécie: fundo + vendas em dinheiro + suprimentos − sangrias − devoluções">Dinheiro na gaveta</span>
            <span class="caixa-detail-value text-success">
              ${moeda(c.dinheiroNaGaveta ?? c.saldoAtual)}
            </span>
          </div>
          <div class="caixa-detail-row">
            <span class="caixa-detail-label">Pix e cartões</span>
            <span class="caixa-detail-value">${moeda(eletronico)}</span>
          </div>
          <div class="caixa-detail-row">
            <span class="caixa-detail-label">Total Vendas</span>
            <span class="caixa-detail-value" title="${escapeHtml(detalheFormas || 'Sem vendas')}">${moeda(c.totalVendas)}</span>
          </div>
          <div class="caixa-detail-row">
            <span class="caixa-detail-label">Vendas · ticket médio</span>
            <span class="caixa-detail-value">${qtdVendas} · ${moeda(c.ticketMedio)}</span>
          </div>
          <div class="caixa-detail-row">
            <span class="caixa-detail-label">Abertura</span>
            <span class="caixa-detail-value">${abertura}</span>
          </div>
        </div>`}
        <div class="caixa-actions">
          ${c.status === 'ABERTO'
            ? botoesAbertos
            : `<button class="btn-sm" onclick="abrirModalMovsLista(${caixaId}, ${c.numeroCaixa})">${buttonIcon('clipboard')}${shellNovo ? 'Movimentações' : 'Ver movs'}</button>`}
        </div>
      </div>`;
  }).join('');

  if (PODE_GERIR_FINANCAS_CAIXA) carregarConsolidado();
}

// ── Render: resumo ────────────────────────────────────────────────────────

function renderResumo() {
  const totalVendas   = caixas.reduce((s, c) => s + (c.totalVendas  || 0), 0);
  const caixasAbertos = caixas.filter(c => c.status === 'ABERTO').length;
  const saldoTotal    = caixas.filter(c => c.status === 'ABERTO')
                              .reduce((s, c) => s + (c.saldoAtual || 0), 0);

  document.getElementById('total-vendas-dia').textContent     = `${moeda(totalVendas)}`;
  document.getElementById('total-caixas-abertos').textContent  = caixasAbertos;
  document.getElementById('saldo-total').textContent           = `${moeda(saldoTotal)}`;
  document.getElementById('resumo-dia').hidden                 = false;
}

// ── Modal: Abrir Caixa ────────────────────────────────────────────────────

function abrirModalAbrirCaixa() {
  if (!PODE_OPERAR_CAIXA) { showToast(mensagemSemPermissao('abrir'), 'error'); return; }
  limparAlert('alert-abrir');
  document.getElementById('abrir-numero').value = '';
  document.getElementById('abrir-saldo').value  = '';
  abrirModal('modal-abrir');
}

async function confirmarAbrirCaixa() {
  if (!PODE_OPERAR_CAIXA) {
    mostrarAlert('alert-abrir', mensagemSemPermissao('abrir'), 'error'); return;
  }
  const numero = parseInt(document.getElementById('abrir-numero').value, 10);
  const saldo  = parseFloat(document.getElementById('abrir-saldo').value) || 0;
  if (!numero || numero <= 0) {
    mostrarAlert('alert-abrir', 'Informe um número de caixa válido.', 'error'); return;
  }
  const resp = await abrirCaixa(numero, saldo);
  if (!resp.ok) {
    mostrarAlert('alert-abrir', await safeReadErrorMessage(resp, 'Erro ao abrir caixa.'), 'error');
    return;
  }
  fecharModal('modal-abrir');
  showToast(`Caixa ${numero} aberto com sucesso.`, 'success');
  await carregarCaixas();
}

// ── Modal: Movimentação manual (sangria / suprimento) ─────────────────────


let saldoMovSnapshot = null;
let movEnviando = false;
function movementPreviewModel(tipo, saldo, rawValue) {
  const text=String(rawValue??'').trim();
  const valid=/^\d+(?:\.\d{1,2})?$/.test(text);
  const cents=valid?Math.round(Number(text)*100):null;
  const amount=Number.isSafeInteger(cents)&&cents>0?cents:null;
  const balance=saldo!=null&&String(saldo).trim()!==''&&Number.isFinite(Number(saldo))?Math.round(Number(saldo)*100):null;
  const known=Number.isSafeInteger(balance);
  const direction=tipo==='sangria'?-1:1;
  const after=known&&amount!=null?balance+direction*amount:null;
  return {amount,balance:known?balance:null,after:Number.isSafeInteger(after)?after:null,
    insufficient:tipo==='sangria'&&known&&amount!=null&&amount>balance,
    invalid:text!==''&&amount==null,direction};
}
function atualizarPreviaMovimentacao() {
  const input=document.getElementById('mov-valor');
  const state=movementPreviewModel(tipoMovAtual,saldoMovSnapshot,input.value);
  const preview=document.getElementById('mov-preview');
  if(!preview)return state;
  const format=v=>v==null?'—':moeda(v/100);
  document.getElementById('mov-preview-delta').textContent=state.amount==null?'—':(state.direction<0?'− ':'+ ')+format(state.amount);
  document.getElementById('mov-preview-total').textContent=format(state.after);
  const note=document.getElementById('mov-preview-note');
  note.textContent=state.invalid?'Informe um valor positivo com até duas casas decimais.':state.insufficient?'O valor informado supera o dinheiro disponível na gaveta.':state.balance==null?'Saldo atual indisponível; a confirmação depende do servidor.':'Estimativa antes da confirmação. O saldo pode mudar.';
  preview.dataset.state=state.insufficient||state.invalid?'error':'normal';
  input.setAttribute('aria-invalid',String(state.insufficient||state.invalid));
  // The server still validates permissions and the current balance atomically.
  // Do not reject a valid request merely because its cached balance is unknown.
  return state;
}
function abrirModalMov(tipo, numero, saldoAtual) {
  if (movEnviando) return;
  if (!podeExecutarMovimentacao(tipo)) { showToast(mensagemSemPermissao(tipo), 'error'); return; }
  tipoMovAtual=tipo;
  numeroCaixaAtual=numero;
  saldoMovSnapshot=saldoAtual;
  limparAlert('alert-mov');
  const field=document.getElementById('mov-valor'),desc=document.getElementById('mov-descricao');
  field.value='';desc.value='';
  const titulos={venda:'Registrar Venda',sangria:'Sangria',suprimento:'Suprimento'};
  const labels={sangria:'Retirada de dinheiro da gaveta',suprimento:'Entrada de dinheiro na gaveta',venda:'Entrada de venda em dinheiro'};
  const examples={sangria:'Ex: Retirada para depósito',suprimento:'Ex: Reforço de troco',venda:'Ex: Venda em dinheiro'};
  const overlay=document.getElementById('modal-mov');
  overlay.dataset.operation=tipo;
  document.getElementById('modal-mov-titulo').textContent=titulos[tipo]||'Movimentação';
  document.getElementById('mov-caixa-info').textContent='Caixa '+numero;
  document.getElementById('mov-symbol').innerHTML=iconMarkup(tipo==='sangria'?'minus':'plus');
  document.getElementById('mov-subtitle').textContent=labels[tipo]||'Movimentação de caixa';
  document.getElementById('mov-preview-label').textContent=tipo==='sangria'?'Retirada':'Entrada';
  desc.placeholder=examples[tipo]||'Motivo da movimentação';
  const snapshot=movementPreviewModel(tipo,saldoAtual,'');
  document.getElementById('mov-saldo-atual').textContent=snapshot.balance==null?'—':moeda(snapshot.balance/100);
  const confirm=document.getElementById('btn-confirmar-mov');
  confirm.textContent=tipo==='sangria'?'Confirmar sangria':tipo==='suprimento'?'Confirmar suprimento':'Confirmar venda';
  confirm.disabled=false;
  prepararAutorizacao('mov');
  field.oninput=atualizarPreviaMovimentacao;
  atualizarPreviaMovimentacao();
  abrirModal('modal-mov');
}
async function confirmarMovimentacao() {
  if(movEnviando)return;
  if (!podeExecutarMovimentacao(tipoMovAtual)) {
    mostrarAlert('alert-mov', mensagemSemPermissao(tipoMovAtual), 'error'); return;
  }
  const state=atualizarPreviaMovimentacao();
  const descricao=document.getElementById('mov-descricao').value.trim();
  if(state.amount==null){
    mostrarAlert('alert-mov','Informe um valor maior que zero, com até duas casas decimais.','error');return;
  }
  if(state.insufficient){
    mostrarAlert('alert-mov','O valor supera o saldo físico informado. Atualize os dados do caixa se necessário.','error');return;
  }
  const autorizacao=lerAutorizacao('mov');
  if(autorizacao===undefined){
    mostrarAlert('alert-mov','Peça ao gerente para digitar nome e senha para autorizar.','error');return;
  }
  const button=document.getElementById('btn-confirmar-mov');
  const label=button.textContent;
  movEnviando=true;button.disabled=true;button.textContent='Registrando…';
  try {
    const resp=await registrarMovimentacao(tipoMovAtual,numeroCaixaAtual,state.amount/100,descricao,autorizacao);
    if(!resp.ok){
      mostrarAlert('alert-mov',await safeReadErrorMessage(resp,'Erro ao registrar movimentação.'),'error');return;
    }
    fecharModal('modal-mov');
    await carregarCaixas();
  }catch(error){
    // A lost response may have committed. Do not automatically repeat a money movement.
    mostrarAlert('alert-mov','Não foi possível confirmar o resultado. Confira as movimentações do caixa antes de tentar novamente.','error');
    await carregarCaixas();
  }finally{
    movEnviando=false;button.disabled=false;button.textContent=label;
  }
}

// ── Modal: Encerrar Caixa ─────────────────────────────────────────────────


const CLOSE_DENOMINATIONS=[20000,10000,5000,2000,1000,500,200,100,50,25,10,5,1];
function countedCashCents(raw){
  const value=String(raw??'').trim().replace(',','.');
  if(!/^\d+(?:\.\d{1,2})?$/.test(value))return null;
  const [whole,fraction='']=value.split('.');
  const cents=Number(whole)*100+Number(fraction.padEnd(2,'0'));
  return Number.isSafeInteger(cents)&&cents>=0?cents:null;
}
function denominationTotal(entries){
  let total=0;
  for(const [denom,raw] of entries){
    if(!CLOSE_DENOMINATIONS.includes(denom))return null;
    const s=String(raw??'').trim();
    if(s==='')continue;
    if(!/^\d+$/.test(s))return null;
    const qty=Number(s);
    if(!Number.isSafeInteger(qty)||!Number.isSafeInteger(total+qty*denom))return null;
    total+=qty*denom;
  }
  return total;
}
function prepararContadorEncerramento(){
  const details=document.getElementById('encerrar-counter');
  const grid=document.getElementById('encerrar-denoms');
  const total=document.getElementById('encerrar-denom-total');
  const use=document.getElementById('encerrar-usar-contagem');
  details.hidden=!document.querySelector('.app.stockos-shell-v2');details.open=false;
  grid.replaceChildren();
  const fields=CLOSE_DENOMINATIONS.map(denom=>{
    const label=document.createElement('label'),text=document.createElement('span'),input=document.createElement('input');
    text.textContent=moeda(denom/100);input.type='number';input.min='0';input.step='1';input.inputMode='numeric';
    input.placeholder='0';input.setAttribute('aria-label','Quantidade de '+moeda(denom/100));input.className='input';
    label.append(text,input);grid.appendChild(label);
    return [denom,input];
  });
  const read=()=>denominationTotal(fields.map(([d,input])=>[d,input.validity.badInput?'invalid':input.value]));
  const update=()=>{const cents=read();total.textContent=cents==null?'Contagem inválida':moeda(cents/100);use.disabled=cents==null;};
  fields.forEach(([,input])=>input.oninput=update);
  use.onclick=()=>{const cents=read();if(cents==null)return;const counted=document.getElementById('encerrar-valor-contado');counted.value=(cents/100).toFixed(2);details.open=false;counted.focus();};
  update();
}
let closeSending=false,closeUncertain=false,closeVersion=0;

function abrirModalEncerrar(numero) {
  if (!PODE_OPERAR_CAIXA) { showToast(mensagemSemPermissao('encerrar'), 'error'); return; }
  if(closeSending)return;
  ++closeVersion;
  closeUncertain=false;
  numeroCaixaEncerrar = numero;
  limparAlert('alert-encerrar');
  const button=document.getElementById('btn-confirmar-encerrar');
  button.disabled=false;button.textContent='Confirmar encerramento';
  document.getElementById('encerrar-caixa-num').textContent = `Caixa ${numero}`;
  // Fechamento cego: o valor esperado NÃO aparece e o campo começa vazio.
  // A diferença só é mostrada depois de encerrar.
  document.getElementById('encerrar-valor-contado').value = '';
  document.getElementById('encerrar-observacao').value = '';
  prepararContadorEncerramento();
  prepararAutorizacao('encerrar');
  prepararAvisoComandas();
  abrirModal('modal-encerrar');
  setTimeout(() => document.getElementById('encerrar-valor-contado')?.focus(), 50);
}

// Comanda aberta é cliente que ainda não pagou: o fechamento avisa e pede confirmação.
async function prepararAvisoComandas(lista) {
  const version=closeVersion;
  const aviso = document.getElementById('encerrar-comandas');
  document.getElementById('encerrar-deixar-comandas').checked = false;
  let abertas = lista;
  if (!abertas) {
    aviso.hidden = true;
    try {
      const resp = await fetch('/api/comandas');
      abertas = resp.ok ? (await resp.json()).filter(c => Number(c.itens) > 0) : [];
    } catch (e) {
      abertas = [];
    }
  }
  if(version!==closeVersion)return;
  aviso.hidden = abertas.length === 0;
  if (!abertas.length) return;
  const total = abertas.reduce((soma, c) => soma + Number(c.total || 0), 0)
    .toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  const numeros = abertas.map(c => c.numero).join(', ');
  document.getElementById('encerrar-comandas-texto').textContent = abertas.length === 1
    ? `A comanda ${numeros} ainda não foi paga (${total}).`
    : `${abertas.length} comandas ainda não foram pagas: ${numeros} (${total}).`;
}

async function confirmarEncerrar() {
  if(closeSending||closeUncertain)return;
  if (!PODE_OPERAR_CAIXA) {
    mostrarFechamento(null, mensagemSemPermissao('encerrar')); return;
  }
  const campoContado = document.getElementById('encerrar-valor-contado').value.trim();
  if (campoContado === '') {
    mostrarAlert('alert-encerrar', 'Conte o dinheiro da gaveta e digite o valor.', 'error');
    return;
  }
  const cents=countedCashCents(campoContado);
  const observacao = document.getElementById('encerrar-observacao').value.trim();
  if (cents==null) {
    mostrarAlert('alert-encerrar', 'Informe um valor não negativo com até duas casas decimais.', 'error');
    return;
  }
  const autorizacao = lerAutorizacao('encerrar');
  if (autorizacao === undefined) {
    mostrarAlert('alert-encerrar', 'Peça ao gerente para digitar nome e senha para autorizar.', 'error');
    return;
  }
  const avisoComandas = document.getElementById('encerrar-comandas');
  const deixarComandas = document.getElementById('encerrar-deixar-comandas').checked;
  if (!avisoComandas.hidden && !deixarComandas) {
    mostrarAlert('alert-encerrar', 'Há comandas sem pagar. Receba antes, ou marque que elas ficam abertas.', 'error');
    avisoComandas.scrollIntoView({ block: 'nearest' });
    return;
  }
  const button=document.getElementById('btn-confirmar-encerrar');
  const label=button.textContent;
  closeSending=true;button.disabled=true;button.textContent='Encerrando…';
  try {
    const resp=await encerrarCaixa(numeroCaixaEncerrar,cents/100,observacao,autorizacao,deixarComandas);
    if(resp.status===409){
      const corpo=await resp.json().catch(()=>({}));
      await prepararAvisoComandas(corpo.comandasAbertas||[]);
      mostrarAlert('alert-encerrar',corpo.erro||'Há comandas abertas.','error');
      document.getElementById('encerrar-comandas').scrollIntoView({block:'nearest'});
      return;
    }
    if(resp.status===403){
      mostrarAlert('alert-encerrar',await safeReadErrorMessage(resp,'Autorização recusada.'),'error');return;
    }
    if(!resp.ok){
      if(resp.status>=500)throw Error('Resultado incerto');
      mostrarAlert('alert-encerrar',await safeReadErrorMessage(resp,'Erro ao encerrar caixa.'),'error');return;
    }
    const fechamento=await resp.json();
    fecharModal('modal-encerrar');
    mostrarFechamento(fechamento);
    await carregarCaixas();
  }catch(error){
    closeUncertain=true;
    mostrarAlert('alert-encerrar','Não foi possível confirmar o resultado. Consulte a situação do caixa antes de tentar novamente.','error');
    await carregarCaixas();
  }finally{
    closeSending=false;button.disabled=closeUncertain;button.textContent=closeUncertain?'Verifique a situação do caixa':label;
  }
}

// ── Modal: Movimentações do caixa ─────────────────────────────────────────


const EXTRACT_TYPES={
  VENDA:{label:'Venda',direction:1},SUPRIMENTO:{label:'Suprimento',direction:1},
  ENTRADA:{label:'Entrada',direction:1},SANGRIA:{label:'Sangria',direction:-1},
  DESPESA:{label:'Despesa',direction:-1},ESTORNO_VENDA:{label:'Estorno',direction:-1}
};
function cashExtractModel(records,filter='all',query='',payment='all',page=1,pageSize=8) {
  if(!Array.isArray(records))return null;
  const norm=v=>String(v??'').normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase().trim();
  const rows=records.map((m,index)=>{
    const type=String(m?.tipo||'');
    const kind=Object.prototype.hasOwnProperty.call(EXTRACT_TYPES,type)?EXTRACT_TYPES[type]:{label:type||'Não informado',direction:0};
    const amount=m?.valor!=null&&String(m.valor).trim()!==''?Math.round(Number(m.valor)*100):null;
    const cents=Number.isSafeInteger(amount)&&amount>=0?amount:null;
    const date=new Date(m?.dataHora||'');
    return {index,type,label:kind.label,direction:kind.direction,cents,date:Number.isFinite(date.getTime())?date:null,
      description:String(m?.descricao||'Sem descrição'),operator:String(m?.operador||'—'),payment:String(m?.formaPagamento||''),
      sale:m?.vendaId==null?'':String(m.vendaId)};
  }).sort((a,b)=>(b.date?.getTime()??-Infinity)-(a.date?.getTime()??-Infinity)||a.index-b.index);
  const sum=items=>items.every(r=>r.cents!=null)&&Number.isSafeInteger(items.reduce((s,r)=>s+r.cents,0))?items.reduce((s,r)=>s+r.cents,0):null;
  const totals={sales:sum(rows.filter(r=>r.type==='VENDA')),refunds:sum(rows.filter(r=>r.type==='ESTORNO_VENDA')),
    supplies:sum(rows.filter(r=>r.type==='SUPRIMENTO')),withdrawals:sum(rows.filter(r=>r.type==='SANGRIA'))};
  const counts={all:rows.length,VENDA:0,SUPRIMENTO:0,SANGRIA:0,ESTORNO_VENDA:0,other:0};
  for(const r of rows){if(['VENDA','SUPRIMENTO','SANGRIA','ESTORNO_VENDA'].includes(r.type))counts[r.type]++;else counts.other++;}
  const term=norm(query);
  const filtered=rows.filter(r=>(filter==='all'||(filter==='other'? !['VENDA','SUPRIMENTO','SANGRIA','ESTORNO_VENDA'].includes(r.type):r.type===filter))&&
    (payment==='all'||(payment==='none'?!r.payment:r.payment===payment))&&norm([r.description,r.operator,r.sale,r.label].join(' ')).includes(term));
  const size=Math.max(1,Math.floor(Number(pageSize)||8)),pages=Math.max(1,Math.ceil(filtered.length/size));
  const current=Math.min(pages,Math.max(1,Math.floor(Number(page)||1)));
  return {totals,counts,payments:[...new Set(rows.map(r=>r.payment).filter(Boolean))].sort(),
    hasNoPayment:rows.some(r=>!r.payment),count:filtered.length,pages,page:current,
    from:filtered.length?(current-1)*size+1:0,to:Math.min(current*size,filtered.length),
    rows:filtered.slice((current-1)*size,current*size)};
}
function renderCashExtract(records) {
  const summary=document.getElementById('stockos-extract-summary'),tools=document.getElementById('stockos-extract-tools'),
    container=document.getElementById('movs-lista'),pages=document.getElementById('stockos-extract-pages');
  const search=document.getElementById('extract-search'),payment=document.getElementById('extract-payment');
  const prev=document.getElementById('extract-prev'),next=document.getElementById('extract-next'),range=document.getElementById('extract-range');
  const buttons=[...tools.querySelectorAll('[data-extract-type]')];
  const initial=cashExtractModel(records);
  if(!initial){container.textContent='Não foi possível carregar as movimentações.';return;}
  const money=v=>v==null?'—':moeda(v/100);
  const esc=escapeHtml;
  summary.innerHTML=[
    ['Vendas registradas',initial.totals.sales,'neutral','Antes dos estornos'],
    ['Suprimentos',initial.totals.supplies,'in','Entradas de dinheiro'],
    ['Sangrias',initial.totals.withdrawals,'out','Retiradas de dinheiro'],
    ['Estornos',initial.totals.refunds,'out','Devoluções registradas']
  ].map(([label,value,state,note])=>'<div class="stockos-extract-metric" data-state="'+state+'"><span>'+label+'</span><strong>'+money(value)+'</strong><small>'+note+'</small></div>').join('');
  summary.hidden=false;tools.hidden=pages.hidden=initial.count===0;
  search.value='';payment.replaceChildren();
  const addOption=(value,label)=>{const option=document.createElement('option');option.value=value;option.textContent=label;payment.appendChild(option);};
  addOption('all','Todas as formas');
  initial.payments.forEach(p=>addOption(p,ROTULOS_FORMA[p]||p));
  if(initial.hasNoPayment)addOption('none','Sem forma informada');
  payment.value='all';
  let filter='all',page=1;
  function draw(){
    const model=cashExtractModel(records,filter,search.value,payment.value,page);
    page=model.page;
    buttons.forEach(b=>{const key=b.dataset.extractType;b.setAttribute('aria-pressed',String(key===filter));b.textContent=({all:'Todas',VENDA:'Vendas',SUPRIMENTO:'Suprimentos',SANGRIA:'Sangrias',ESTORNO_VENDA:'Estornos',other:'Outras'}[key])+' · '+model.counts[key];});
    range.textContent=model.count?model.from+'–'+model.to+' de '+model.count+' registros':'0 registros';
    prev.disabled=page<=1;next.disabled=page>=model.pages;
    if(!model.rows.length){container.innerHTML='<div class="stockos-extract-empty">'+(initial.count?'Nenhuma movimentação corresponde aos filtros.':'Nenhuma movimentação registrada nesta sessão.')+'</div>';return;}
    container.innerHTML='<div class="stockos-extract-table-wrap"><table class="stockos-extract-table"><caption class="stockos-extract-caption">Movimentações da sessão, da mais recente à mais antiga</caption><thead><tr><th scope="col">Movimentação</th><th scope="col">Operador</th><th scope="col">Pagamento</th><th scope="col">Data / hora</th><th scope="col">Valor</th></tr></thead><tbody>'+
    model.rows.map(r=>{
      const state=r.direction===1?'in':r.direction===-1?'out':'neutral';
      const date=r.date?r.date.toLocaleDateString('pt-BR'):'—',time=r.date?r.date.toLocaleTimeString('pt-BR',{hour:'2-digit',minute:'2-digit'}):'—';
      return '<tr><td><span class="stockos-extract-kind" data-state="'+state+'">'+esc(r.label)+'</span><strong class="stockos-extract-desc">'+esc(r.description)+'</strong>'+
        (r.sale?'<small>Venda #'+esc(r.sale)+'</small>':'')+'</td><td>'+esc(r.operator)+'</td><td>'+esc(ROTULOS_FORMA[r.payment]||r.payment||'—')+'</td><td><span>'+esc(date)+'</span><small>'+esc(time)+'</small></td><td class="stockos-extract-value" data-state="'+state+'">'+(r.cents==null?'—':(r.direction===1?'+':r.direction===-1?'−':'')+money(r.cents))+'</td></tr>';
    }).join('')+'</tbody></table></div>';
  }
  buttons.forEach(b=>b.onclick=()=>{filter=b.dataset.extractType;page=1;draw();});
  search.oninput=payment.onchange=()=>{page=1;draw();};
  prev.onclick=()=>{page--;draw();};next.onclick=()=>{page++;draw();};draw();
}
let cashExtractVersion=0;

async function abrirModalMovsLista(caixaId, numeroCaixa) {
  const version=++cashExtractVersion;
  const compact=!!document.querySelector('.app.stockos-shell-v2');
  const operators=document.getElementById('stockos-extract-operators');
  const operatorContent=document.getElementById('movs-operadores');
  const containerInitial=document.getElementById('movs-lista');
  const current=()=>version===cashExtractVersion&&document.getElementById('movs-lista')===containerInitial;
  ['stockos-extract-summary','stockos-extract-tools','stockos-extract-pages'].forEach(id=>document.getElementById(id).hidden=true);
  operators.hidden=!compact;operators.open=false;
  if(compact)operators.appendChild(operatorContent);else operators.after(operatorContent);
  operatorContent.innerHTML='';
  document.getElementById('modal-movs-titulo').textContent =
    `Extrato de caixa — Caixa ${numeroCaixa} · Sessão ${caixaId}`;
  document.getElementById('movs-lista').innerHTML = '<div class="loading">Carregando</div>';
  abrirModal('modal-movs-lista');
  if(compact){
    operators.ontoggle=()=>{
      if(operators.open&&current()&&!operatorContent.dataset.loaded){
        operatorContent.dataset.loaded='true';
        void preencherResumoOperadores('movs-operadores',caixaId,current);
      }
    };
    delete operatorContent.dataset.loaded;
  }else void preencherResumoOperadores('movs-operadores',caixaId,current);
  try {
    const resp = await fetch(`/api/caixas/sessoes/${caixaId}/movimentacoes`);
    if (!resp.ok) throw new Error();
    const movs      = await resp.json();
    if(!current())return;
    if(!Array.isArray(movs))throw Error('Invalid movements');
    if(compact){renderCashExtract(movs);return;}
    const container = document.getElementById('movs-lista');
    if (movs.length === 0) {
      container.innerHTML = '<div class="empty">Nenhuma movimentação registrada.</div>';
      return;
    }
    container.innerHTML = movs.map(m => {
      const hora      = new Date(m.dataHora).toLocaleTimeString('pt-BR', { hour:'2-digit', minute:'2-digit' });
      const isEntrada = ['VENDA','SUPRIMENTO','ENTRADA'].includes(m.tipo);
      const forma     = m.formaPagamento ? ` · ${escapeHtml(ROTULOS_FORMA[m.formaPagamento] || m.formaPagamento)}` : '';
      const quem      = m.operador ? ` · ${escapeHtml(m.operador)}` : '';
      return `
        <div class="mov-caixa-item">
          <span class="mov-caixa-badge ${m.tipo.toLowerCase()}">${escapeHtml(m.tipo === 'ESTORNO_VENDA' ? 'ESTORNO' : m.tipo)}</span>
          <span class="mov-caixa-desc">${escapeHtml(m.descricao || '—')}<small class="mov-caixa-meta">${forma}${quem}</small></span>
          <span class="mov-caixa-val ${isEntrada ? 'text-success' : 'text-danger'}">
            ${isEntrada ? '+' : '−'}${moeda(m.valor)}
          </span>
          <span class="mov-caixa-time">${hora}</span>
        </div>`;
    }).join('');
  } catch {
    if(!current())return;
    document.getElementById('movs-lista').innerHTML =
      '<div class="empty">Erro ao carregar movimentações.</div>';
  }
}

// ── Resumo por operador (fechamento e movimentações) ─────────────────────

async function carregarResumoOperadores(caixaId) {
  const resp = await fetch(`/api/caixas/sessoes/${caixaId}/resumo-operadores`);
  if (!resp.ok) throw new Error(await safeReadErrorMessage(resp, 'Não foi possível montar o resumo por operador.'));
  return resp.json();
}

function tabelaResumoOperadores(linhas) {
  if (!linhas || linhas.length === 0) return '<p class="inline-note">Nenhuma movimentação nesta sessão.</p>';
  const valor = v => Number(v || 0) > 0 ? moeda(v) : '—';
  const corpo = linhas.map(l => `
      <tr>
        <td>${escapeHtml(l.operador)}</td>
        <td class="num">${l.vendas}</td>
        <td class="num">${moeda(l.vendido)}</td>
        <td class="num">${valor(l.dinheiro)}</td>
        <td class="num">${valor(l.pixECartoes)}</td>
        <td class="num">${l.cancelamentos > 0 ? `${l.cancelamentos} · ${moeda(l.cancelado)}` : '—'}</td>
        <td class="num">${valor(l.descontos)}</td>
        <td class="num">${valor(l.sangrias)}</td>
      </tr>`).join('');
  return `
    <div class="resumo-operadores-rolagem">
      <table class="resumo-operadores">
        <thead><tr>
          <th>Operador</th><th class="num">Vendas</th><th class="num">Vendido</th><th class="num">Dinheiro</th>
          <th class="num">Pix e cartões</th><th class="num">Cancelou</th><th class="num">Descontos</th><th class="num">Sangrias</th>
        </tr></thead>
        <tbody>${corpo}</tbody>
      </table>
    </div>
    <p class="inline-note">Venda conta para quem finalizou; cancelamento e sangria, para quem fez. "Vendido" é antes dos cancelamentos.</p>`;
}

async function preencherResumoOperadores(destinoId, caixaId, current=()=>true) {
  const destino = document.getElementById(destinoId);
  if (!destino) return;
  destino.innerHTML = '<div class="loading">Carregando</div>';
  try {
    const linhas=await carregarResumoOperadores(caixaId);
    if(current()&&document.getElementById(destinoId)===destino)destino.innerHTML=tabelaResumoOperadores(linhas);
  } catch (error) {
    if(current()&&document.getElementById(destinoId)===destino)destino.innerHTML='<p class="inline-note">'+escapeHtml(error.message)+'</p>';
  }
}

/** Imprime só o fechamento, em qualquer impressora instalada no Windows. */
function imprimirFechamento() {
  document.body.classList.add('imprimindo-fechamento');
  window.addEventListener('afterprint', () => document.body.classList.remove('imprimindo-fechamento'), { once: true });
  window.print();
}

// ── Card de fechamento ────────────────────────────────────────────────────

function mostrarFechamento(data, erroMsg) {
  const container = document.getElementById('fechamento-container');
  if (erroMsg) {
    container.innerHTML = `
      <div class="alert-modal error alert-modal--static">
        <span class="btn-inline-icon" aria-hidden="true">${iconMarkup('alertTriangle')}</span>
        ${escapeHtml(erroMsg)}
      </div>`;
  } else {
    const abertoPor = escapeHtml(data.abertoPor || data.nomeOperador || '—');
    const fechadoPor = escapeHtml(data.fechadoPor || '—');
    const observacao = escapeHtml(data.observacao || '—');
    const timestamp = data.timestamp
      ? new Date(data.timestamp).toLocaleString('pt-BR')
      : escapeHtml(data.data || '—');
    const valorSistema = Number(data.valorSistema ?? data.saldoFinal ?? 0);
    const valorContado = Number(data.valorContado ?? 0);
    const divergencia = Number(data.divergencia ?? 0);
    const divergenciaClass = divergencia === 0 ? '' : (divergencia > 0 ? 'text-success' : 'text-danger');
    container.innerHTML = `
      <div class="fechamento-card">
        <div class="fechamento-title">
          <span class="btn-inline-icon" aria-hidden="true">${iconMarkup('checkCircle')}</span>
          Caixa ${data.numeroCaixa} Encerrado
        </div>
        <div class="fechamento-rows">
          <div class="fechamento-row"><span class="label">Aberto por</span>
            <span class="value">${abertoPor}</span></div>
          <div class="fechamento-row"><span class="label">Fechado por</span>
            <span class="value">${fechadoPor}</span></div>
          <div class="fechamento-row"><span class="label">Data/Hora</span>
            <span class="value">${timestamp}</span></div>
          <div class="fechamento-row"><span class="label">Saldo Inicial</span>
            <span class="value">${moeda(data.saldoInicial)}</span></div>
          <div class="fechamento-row"><span class="label">Total Vendas</span>
            <span class="value">${moeda(data.totalVendas)}</span></div>
          <div class="fechamento-row"><span class="label">Total Entradas</span>
            <span class="value">${moeda(data.totalEntradas)}</span></div>
          <div class="fechamento-row"><span class="label">Total Saídas</span>
            <span class="value text-danger">${moeda(data.totalSaidas)}</span></div>
          <div class="fechamento-row"><span class="label">Movimentações</span>
            <span class="value">${data.quantidadeMovimentacoes??'—'}</span></div>
          <div class="fechamento-row"><span class="label">Dinheiro esperado</span>
            <span class="value">${moeda(valorSistema)}</span></div>
          <div class="fechamento-row"><span class="label">Dinheiro contado</span>
            <span class="value">${moeda(valorContado)}</span></div>
          <div class="fechamento-row"><span class="label">Divergência</span>
            <span class="value ${divergenciaClass}">${moeda(divergencia)}</span></div>
          <div class="fechamento-row total"><span class="label">Observação</span>
            <span class="value">${observacao}</span></div>
        </div>
        <h4 class="fechamento-subtitulo">Por operador</h4>
        <div id="fechamento-operadores"></div>
        <button class="btn btn-secondary btn-sm fechamento-imprimir" type="button" onclick="imprimirFechamento()">Imprimir fechamento</button>
      </div>`;
    if (data.caixaId) preencherResumoOperadores('fechamento-operadores', data.caixaId);
  }
  container.hidden = false;
  container.scrollIntoView({ behavior: 'smooth' });
}

// ── Helpers ───────────────────────────────────────────────────────────────

function abrirModal(id)  { document.getElementById(id).classList.add('open');    }
function fecharModal(id) {
  document.getElementById(id).classList.remove('open');
  if (id === 'modal-abrir') limparAlert('alert-abrir');
  if (id === 'modal-mov')   limparAlert('alert-mov');
  if (id === 'modal-encerrar') limparAlert('alert-encerrar');
}
function fecharModalSeClicouFora(e, id) { if (e.target.id === id) fecharModal(id); }

function mostrarAlert(id, msg, tipo) {
  const el = document.getElementById(id);
  if (!el) return;
  el.textContent = msg;
  el.className   = `alert-modal ${tipo}`;
}
function limparAlert(id) {
  const el = document.getElementById(id);
  if (!el) return;
  el.textContent = '';
  el.className   = 'alert-modal';
}

/**
 * Sanitiza strings antes de inserir em innerHTML.
 * Previne XSS em dados vindos do servidor (nome de operador, descrições, etc.)
 */
function escapeHtml(str) {
  if (str == null) return '';
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

/* Page-scoped bridge: legacy inline event handlers resolve only against the active page. */
window.StockOSPages = window.StockOSPages || {};
window.StockOSPages.caixas = {
  mount: stockOSMount_caixas,
  activate() {
    Object.assign(window, {
    buttonIcon,
    moeda,
    aplicarPermissoesUI,
    podeExecutarMovimentacao,
    mensagemSemPermissao,
    carregarCaixas,
    abrirCaixa,
    registrarMovimentacao,
    encerrarCaixa,
    carregarConsolidado,
    renderCaixas,
    renderResumo,
    abrirModalAbrirCaixa,
    confirmarAbrirCaixa,
    abrirModalMov,
    confirmarMovimentacao,
    abrirModalEncerrar,
    confirmarEncerrar,
    abrirModalMovsLista,
    imprimirFechamento,
    mostrarFechamento,
    abrirModal,
    fecharModal,
    fecharModalSeClicouFora,
    mostrarAlert,
    limparAlert,
    escapeHtml,
  });
  }
};
if (/^\/caixas(?:\.html)?\/?$/.test(location.pathname)) {
  window.StockOSPages.caixas.activate();
}
document.addEventListener('DOMContentLoaded', stockOSMount_caixas);

})();
