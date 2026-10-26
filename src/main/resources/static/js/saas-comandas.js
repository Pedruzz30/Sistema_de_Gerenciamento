import {createClient} from 'https://esm.sh/@supabase/supabase-js@2.48.1';
const $=id=>document.getElementById(id);
const fmt=n=>new Intl.NumberFormat('pt-BR',{style:'currency',currency:'BRL'}).format(Number(n||0));
let supabase, companies=[], branches=[], tabs=[], products=[], selected=null;
const state={empresa:null,filial:null,busy:false};
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
 state.empresa=$('empresa').value;branches=await api('/api/saas/empresas/'+encodeURIComponent(state.empresa)+'/filiais');
 fill($('filial'),branches.map(b=>({id:b.id,name:b.nome})));
 state.filial=branches[0]?.id||null;selected=null;renderDetail();
 if(state.filial)await refresh();
 else report('Nenhuma filial ativa foi vinculada a esta empresa.');
}
async function refresh(){
 state.filial=$('filial').value;
 if(!state.filial)return;
 [tabs,products]=await Promise.all([api(uri()+'/comandas'),api(uri()+'/produtos')]);
 fill($('produto'),products.filter(p=>p.ativo).map(p=>({id:p.id,name:p.nome+' — '+fmt(p.preco_unitario)+' ('+p.estoque_atual+')'})));
 renderTabs();await renderDetail();
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
 const items=await api(uri()+'/comandas/'+encodeURIComponent(tab.id)+'/itens');const box=$('itens');box.replaceChildren();
 for(const item of items){const div=document.createElement('div');div.className='item';text(div,item.quantidade+' × '+item.nome_snapshot+' — '+fmt(Number(item.quantidade)*Number(item.preco_unitario_snapshot))+(item.cancelado_em?' (cancelado)':''));box.append(div);}
}
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
init();
