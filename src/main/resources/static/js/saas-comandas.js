import {createClient} from 'https://esm.sh/@supabase/supabase-js@2.48.1';
const $=id=>document.getElementById(id);
const fmt=n=>new Intl.NumberFormat('pt-BR',{style:'currency',currency:'BRL'}).format(Number(n||0));
let supabase, companies=[], branches=[], tabs=[], products=[], selected=null;
let shift=null;
const state={empresa:null,filial:null,busy:false,role:null,licenseAllowed:false};
function report(message,success=false){$('feedback').textContent=message||'';$('feedback').className=success?'success':'';}
function text(el,content){el.textContent=content;}
async function api(path,options={}){
 const {data:{session}}=await supabase.auth.getSession();
 if(!session?.access_token)throw new Error('Sessão expirada. Faça login novamente.');
 const headers={Authorization:'Bearer '+session.access_token,...(options.body?{'Content-Type':'application/json'}:{}),...options.headers};
 const response=await fetch(path,{...options,headers});
 if(!response.ok){const body=await response.json().catch(()=>({}));throw new Error(body.message||body.detail||body.error||'Erro HTTP '+response.status);}
 if(response.status===204)return null;
 return response.json();
}
function uri(){return '/api/saas/empresas/'+encodeURIComponent(state.empresa)+'/filiais/'+encodeURIComponent(state.filial)}
async function init(){
 try{
  const cfg=await fetch('/api/saas/config').then(r=>r.json());
  if(!cfg.supabaseUrl||!cfg.publishableKey)throw new Error('Configure a URL e a chave pública do projeto StockOS.');
  supabase=createClient(cfg.supabaseUrl,cfg.publishableKey,{auth:{persistSession:true,autoRefreshToken:true}});
  supabase.auth.onAuthStateChange((_event,session)=>{if(!session){$('login').hidden=false;$('workspace').hidden=true;$('logout').hidden=true;}});
  const {data:{session}}=await supabase.auth.getSession();
  if(session)await workspace();
 }catch(e){report(e.message);}
}
async function workspace(){
 $('login').hidden=true;$('workspace').hidden=false;$('logout').hidden=false;
 companies=await api('/api/saas/empresas');
 fill($('empresa'),companies.map(c=>({id:c.id,name:c.nome})));
 if(companies.length){state.empresa=companies[0].id;await loadBranches();}
 else report('Sua conta ainda não está vinculada a um estabelecimento.');
}
function fill(el,entries){el.replaceChildren();for(const v of entries){const opt=document.createElement('option');opt.value=v.id;text(opt,v.name);el.append(opt);}}
async function loadBranches(){
 state.empresa=$('empresa').value;
 state.role=companies.find(c=>c.id===state.empresa)?.papel||null;
 const licenca=await api('/api/saas/empresas/'+encodeURIComponent(state.empresa)+'/licenca');
 const allowed=licenca.acesso_liberado===true;
 state.licenseAllowed=allowed;
 const banner=$('license-banner');
 banner.hidden=false;
 banner.classList.toggle('alert',!allowed);
 text(banner,allowed
   ? 'Licença '+(licenca.plano||'')+' ativa até '+new Date(licenca.vigencia_fim).toLocaleDateString('pt-BR')+'.'
   : 'Operações bloqueadas: licença '+String(licenca.status||'inexistente')+'. Contate a administração do StockOS.');
 document.querySelector('.bar').hidden=!allowed;
 document.querySelector('.layout').hidden=!allowed;
 $('cash-panel').hidden=!allowed;
 branches=await api('/api/saas/empresas/'+encodeURIComponent(state.empresa)+'/filiais');
 fill($('filial'),branches.map(b=>({id:b.id,name:b.nome})));
 state.filial=branches[0]?.id||null;selected=null;renderDetail();
 if(state.filial&&allowed)await refresh();
 else if(!state.filial) report('Nenhuma filial ativa foi vinculada a esta empresa.');
}
async function refresh(){
 state.filial=$('filial').value;
 if(!state.filial)return;
 [tabs,products,shift]=await Promise.all([
   api(uri()+'/comandas'),api(uri()+'/produtos'),api(uri()+'/caixa/atual')
 ]);
 fill($('produto'),products.filter(p=>p.ativo).map(p=>({id:p.id,name:p.nome+' — '+fmt(p.preco_unitario)+' ('+p.estoque_atual+')'})));
 renderTabs();await renderDetail();await renderCash();await renderClosedTabs();await renderInventory();
}
function renderTabs(){
 const el=$('comandas');el.replaceChildren();
 if(!tabs.length){const p=document.createElement('p');text(p,'Nenhuma comanda aberta.');el.append(p);return;}
 for(const tab of tabs){
  const b=document.createElement('button');b.type='button';b.className='tab'+(selected===tab.id?' active':'');
  const title=document.createElement('strong');text(title,'#'+String(tab.numero).padStart(3,'0'));
  const summary=document.createElement('small');text(summary,fmt(tab.total)+' · Pago '+fmt(tab.pago));
  b.append(title,summary);b.onclick=async()=>{selected=tab.id;renderTabs();await renderDetail();};el.append(b);
 }
}
async function renderDetail(){
 const tab=tabs.find(t=>t.id===selected);$('detail').hidden=!tab;
 if(!tab){text($('detail-title'),'Selecione uma comanda');return;}
 text($('detail-title'),'Comanda #'+String(tab.numero).padStart(3,'0'));
 text($('total'),fmt(tab.total));text($('paid'),fmt(tab.pago));text($('restante'),fmt(Math.max(0,Number(tab.total)-Number(tab.pago))));
 const [items,payments]=await Promise.all([
   api(uri()+'/comandas/'+encodeURIComponent(tab.id)+'/itens'),
   api(uri()+'/comandas/'+encodeURIComponent(tab.id)+'/pagamentos')
 ]);
 const box=$('itens');box.replaceChildren();
 for(const item of items){const div=document.createElement('div');div.className='item';text(div,item.quantidade+' × '+item.nome_snapshot+' — '+fmt(Number(item.quantidade)*Number(item.preco_unitario_snapshot))+(item.cancelado_em?' (cancelado)':''));box.append(div);}
 const entries=$('payment-entries');entries.replaceChildren();
 if(!payments.length){const empty=document.createElement('p');empty.className='muted';text(empty,'Nenhum pagamento registrado.');entries.append(empty);}
 for(const payment of payments){
   const line=document.createElement('div');line.className='item payment-row';
   const description=document.createElement('span');
   text(description,payment.modalidade+' · '+fmt(payment.valor)+(payment.estorno_id?' · ESTORNADO':''));
   line.append(description);
   if(payment.estorno_id){
     const why=document.createElement('small');text(why,'Motivo: '+payment.estorno_motivo);line.append(why);
   }else if(isManager()){
     const reverse=document.createElement('button');reverse.type='button';reverse.className='outline';
     text(reverse,'Registrar estorno');
     reverse.onclick=()=>submit(async()=>{
       if(!shift||shift.status!=='ABERTO')throw Error('É preciso caixa aberto para estornar.');
       const reason=window.prompt('Informe o motivo do estorno (mínimo 8 caracteres):');
       if(reason===null)return;
       if(reason.trim().length<8)throw Error('Informe um motivo mais detalhado.');
       if(!window.confirm('Confirmar estorno integral de '+fmt(payment.valor)+'? A devolução externa deve ser feita separadamente.'))return;
       await api(uri()+'/comandas/'+selected+'/pagamentos/'+payment.id+'/estornos',{
         method:'POST',headers:{'Idempotency-Key':crypto.randomUUID()},
         body:JSON.stringify({motivo:reason.trim()})
       });
       await refresh();
     });
     line.append(reverse);
   }
   entries.append(line);
 }
}

const isManager=()=>state.role==='GERENTE'||state.role==='PROPRIETARIO';
const cashBase=()=>uri()+'/caixa';

function cashValue(id,value){text($(id),fmt(value));}
async function renderCash(){
 const open=shift?.status==='ABERTO';
 $('cash-open').hidden=open;
 $('cash-active').hidden=!open;
 $('cash-management').hidden=!open||!isManager();
 $('cash-history-panel').hidden=!isManager();
 text($('cash-status'),open?'ABERTO':'SEM TURNO ABERTO');
 $('cash-status').classList.toggle('open',open);
 if(open){
   cashValue('cash-expected',shift.dinheiro_esperado);
   cashValue('cash-money',shift.dinheiro);
   cashValue('cash-pix',shift.pix);
   cashValue('cash-debit',shift.debito);
   cashValue('cash-credit',shift.credito);
   text($('cash-adjustments'),fmt(shift.sangrias)+' / '+fmt(shift.suprimentos));
 }
 if(isManager()){
   const history=await api(cashBase()+'/historico');
   const host=$('cash-history');host.replaceChildren();
   if(!history.length){const p=document.createElement('p');p.className='muted';text(p,'Nenhum turno registrado.');host.append(p);}
   for(const row of history.slice(0,8)){
     const item=document.createElement('div');item.className='item';
     const date=new Date(row.aberto_em).toLocaleString('pt-BR');
     const diff=row.status==='FECHADO'?' · Diferença: '+fmt(row.diferenca):'';
     text(item,date+' · '+row.status+' · Abertura: '+fmt(row.valor_abertura)+diff);
     host.append(item);
   }
 }
}

async function renderClosedTabs(){
 const panel=$('refunds-panel');
 panel.hidden=!state.licenseAllowed||!state.filial||!isManager();
 if(panel.hidden)return;
 const closed=await api(uri()+'/comandas/fechadas');
 const select=$('closed-tab-select');
 select.replaceChildren();
 for(const tab of closed){
   const option=document.createElement('option');
   option.value=tab.id;
   option.textContent='#'+String(tab.numero).padStart(3,'0')+' · '+new Date(tab.fechada_em).toLocaleString('pt-BR');
   select.append(option);
 }
 await renderClosedPayments();
 await renderClosedStock();
}
async function renderClosedPayments(){
 const selectedClosed=$('closed-tab-select').value;
 const host=$('closed-tab-payments');host.replaceChildren();
 if(!selectedClosed)return;
 const payments=await api(uri()+'/comandas/'+selectedClosed+'/pagamentos-fechados');
 for(const payment of payments){
   const line=document.createElement('div');line.className='item';line.style.flexWrap='wrap';
   const span=document.createElement('span');
   text(span,payment.modalidade+' · '+fmt(payment.valor)+(payment.devolucao_id?' · DEVOLVIDO':''));
   line.append(span);
   if(!payment.devolucao_id){
     const button=document.createElement('button');
     button.type='button';button.className='outline';button.textContent='Registrar devolução';
     button.onclick=()=>submit(async()=>{
       if(shift?.status!=='ABERTO')throw Error('Abra o caixa atual para registrar uma devolução.');
       const motivo=window.prompt('Motivo da devolução (mínimo 8 caracteres):');
       if(motivo===null)return;
       const referencia=window.prompt('Referência do comprovante da devolução já realizada:');
       if(referencia===null)return;
       if(motivo.trim().length<8||referencia.trim().length<6)
         throw Error('Informe motivo (8 caracteres) e referência (6 caracteres).');
       if(!window.confirm('Você CONFIRMA que a devolução externa de '+fmt(payment.valor)+' JÁ FOI REALIZADA? O StockOS apenas registrará essa declaração.'))return;
       await api(uri()+'/comandas/'+selectedClosed+'/pagamentos/'+payment.id+'/devolucoes',{
         method:'POST',
         headers:{'Idempotency-Key':crypto.randomUUID()},
         body:JSON.stringify({motivo:motivo.trim(),comprovanteReferencia:referencia.trim(),devolucaoConfirmada:true})
       });
       await refresh();
     });
     line.append(button);
   } else {
     const note=document.createElement('small');
     text(note,'Comprovante: '+payment.comprovante_referencia+' · '+payment.devolucao_motivo);
     line.append(note);
   }
   host.append(line);
 }
}
async function renderClosedStock(){
 const tab=$('closed-tab-select').value;
 const host=$('closed-stock-items');host.replaceChildren();
 if(!tab)return;
 const items=await api(uri()+'/comandas/'+tab+'/itens-fechados');
 for(const item of items){
   const remaining=Number(item.quantidade)-Number(item.quantidade_devolvida);
   const line=document.createElement('div');line.className='item';line.style.flexWrap='wrap';
   const label=document.createElement('span');
   text(label,item.nome_snapshot+' · Vendidos: '+item.quantidade+' · Restantes: '+remaining);
   line.append(label);
   if(remaining>0){
     const button=document.createElement('button');
     button.type='button';button.className='outline';
     text(button,'Registrar retorno físico');
     button.onclick=()=>submit(async()=>{
       const raw=window.prompt('Quantas unidades foram efetivamente devolvidas? (máx. '+remaining+')','1');
       if(raw===null)return;
       const n=Number(raw);
       if(!Number.isInteger(n)||n<=0||n>remaining)throw Error('Quantidade inválida.');
       const condition=window.prompt('Destino: REESTOQUE (apto à venda) ou DESCARTE (danificado)','DESCARTE');
       if(condition===null)return;
       const destino=condition.trim().toUpperCase();
       if(!['REESTOQUE','DESCARTE'].includes(destino))throw Error('Selecione REESTOQUE ou DESCARTE.');
       const motivo=window.prompt('Motivo do retorno físico (mínimo 8 caracteres):');
       if(motivo===null)return;
       if(motivo.trim().length<8)throw Error('Motivo muito curto.');
       if(!window.confirm('Confirma '+n+' unidade(s) para '+destino+'? Isto NÃO faz reembolso financeiro.'))return;
       await api(uri()+'/comandas/'+tab+'/itens/'+item.id+'/devolucoes-estoque',{
         method:'POST',headers:{'Idempotency-Key':crypto.randomUUID()},
         body:JSON.stringify({quantidade:n,destino,motivo:motivo.trim()})
       });
       await refresh();
     });
     line.append(button);
   }
   host.append(line);
 }
}
$('closed-tab-select').onchange=()=>submit(async()=>{await renderClosedPayments();await renderClosedStock();});

async function renderInventory(){
 const panel=$('inventory-panel');
 panel.hidden=!state.licenseAllowed||!state.filial||!isManager();
 if(panel.hidden)return;
 const select=$('inventory-product');const selectedId=select.value;select.replaceChildren();
 for(const p of products){
   const option=document.createElement('option');option.value=p.id;option.textContent=p.nome+' · '+p.estoque_atual+' unidades';select.append(option);
 }
 if(products.some(p=>p.id===selectedId))select.value=selectedId;
 updateInventoryExpected();
 const history=await api(uri()+'/produtos/ajustes?limite=20');
 const box=$('inventory-history');box.replaceChildren();
 for(const item of history){
   const line=document.createElement('div');line.className='item';
   text(line,item.produto_nome+': '+item.estoque_anterior+' → '+item.estoque_contado+' · '+item.motivo);
   box.append(line);
 }
}
function updateInventoryExpected(){
 const p=products.find(p=>p.id===$('inventory-product').value);
 $('inventory-expected').value=p?.estoque_atual??'';
}
$('inventory-product').onchange=updateInventoryExpected;
$('inventory-form').onsubmit=e=>{e.preventDefault();submit(async()=>{
 if(!isManager())throw Error('Somente gerente ou proprietário pode conferir estoque.');
 const produto=$('inventory-product').value;
 const esperado=Number($('inventory-expected').value);
 const contado=Number($('inventory-counted').value);
 const motivo=$('inventory-reason').value.trim();
 if(!Number.isInteger(contado)||contado<0||motivo.length<8)throw Error('Contagem ou motivo inválido.');
 if(!window.confirm('Confirmar conferência de '+esperado+' para '+contado+' unidades?'))return;
 await api(uri()+'/produtos/'+produto+'/ajustes',{method:'POST',
   headers:{'Idempotency-Key':crypto.randomUUID()},
   body:JSON.stringify({estoqueEsperado:esperado,estoqueContado:contado,motivo})});
 $('inventory-counted').value='';$('inventory-reason').value='';await refresh();
});};

async function submit(action){
 if(state.busy)return;state.busy=true;
 for(const b of document.querySelectorAll('button'))b.disabled=true;
 try{await action();report('Operação registrada.',true);}catch(e){report(e.message);}finally{state.busy=false;for(const b of document.querySelectorAll('button'))b.disabled=false;}
}
$('login-form').onsubmit=e=>{e.preventDefault();submit(async()=>{const {error}=await supabase.auth.signInWithPassword({email:$('email').value,password:$('password').value});if(error)throw error;await workspace();});};
$('logout').onclick=()=>submit(async()=>{await supabase.auth.signOut();selected=null;report('Sessão encerrada.',true);});
$('empresa').onchange=()=>submit(loadBranches);
$('filial').onchange=()=>submit(async()=>{selected=null;await refresh();});
$('refresh').onclick=()=>submit(refresh);
$('open-form').onsubmit=e=>{e.preventDefault();submit(async()=>{await api(uri()+'/comandas',{method:'POST',body:JSON.stringify({numero:Number($('numero').value)})});$('numero').value='';await refresh();});};
$('item-form').onsubmit=e=>{e.preventDefault();submit(async()=>{await api(uri()+'/comandas/'+selected+'/itens',{method:'POST',headers:{'Idempotency-Key':crypto.randomUUID()},body:JSON.stringify({produtoId:$('produto').value,quantidade:Number($('quantidade').value)})});await refresh();});};
$('pay-form').onsubmit=e=>{e.preventDefault();submit(async()=>{await api(uri()+'/comandas/'+selected+'/pagamentos',{method:'POST',headers:{'Idempotency-Key':crypto.randomUUID()},body:JSON.stringify({modalidade:$('modalidade').value,valor:Number($('valor').value).toFixed(2)})});$('valor').value='';await refresh();});};
$('close-tab').onclick=()=>submit(async()=>{await api(uri()+'/comandas/'+selected+'/fechar',{method:'POST'});selected=null;await refresh();});

$('cash-open-form').onsubmit=e=>{e.preventDefault();submit(async()=>{
 if(!state.filial||!state.licenseAllowed)throw Error('Selecione uma filial com licença ativa.');
 await api(cashBase()+'/abrir',{method:'POST',body:JSON.stringify({valorAbertura:Number($('cash-opening').value).toFixed(2)})});
 await refresh();
});};
$('cash-move-form').onsubmit=e=>{e.preventDefault();submit(async()=>{
 if(!isManager())throw Error('Apenas gerente ou proprietário pode movimentar o caixa.');
 const tipo=$('cash-move-type').value;
 const valor=Number($('cash-move-value').value).toFixed(2);
 const motivo=$('cash-move-reason').value.trim();
 await api(cashBase()+'/movimentos',{
   method:'POST',headers:{'Idempotency-Key':crypto.randomUUID()},
   body:JSON.stringify({tipo,valor,motivo})
 });
 e.target.reset();await refresh();
});};
$('cash-close-form').onsubmit=e=>{e.preventDefault();submit(async()=>{
 if(!isManager())throw Error('Apenas gerente ou proprietário pode fechar o caixa.');
 const counted=Number($('cash-counted').value).toFixed(2);
 if(!window.confirm('Confirma o fechamento do turno com '+fmt(counted)+' em dinheiro contado?'))return;
 const closed=await api(cashBase()+'/fechar',{method:'POST',body:JSON.stringify({valorContado:counted})});
 $('cash-counted').value='';
 await refresh();
 report('Caixa fechado. Diferença: '+fmt(closed.diferenca)+(Number(closed.diferenca)===0?' (conferido).':'. Confira o histórico.'),true);
});};

init();
