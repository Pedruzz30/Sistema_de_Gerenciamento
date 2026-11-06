/* StockOS tenant console: JWT verified on every API request. Staging only. */
(function (global) {
  'use strict';
  const BASE = '/api/v2/estabelecimentos';
  function uuid(value) {
    return typeof value === 'string' &&
      /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value);
  }
  function validStores(data) {
    if (!Array.isArray(data) || data.length > 100) throw new Error('Lista de estabelecimentos inválida');
    const unique = new Set();
    return data.map(store => {
      if (!store || !uuid(store.id) || typeof store.nome !== 'string' ||
          !['PROPRIETARIO', 'GERENTE', 'OPERADOR'].includes(store.papel) ||
          !Number.isInteger(store.ruLocal) || store.ruLocal <= 0 || unique.has(store.id)) {
        throw new Error('Estabelecimento inválido');
      }
      unique.add(store.id);
      return {id:store.id,nome:store.nome,papel:store.papel,ruLocal:store.ruLocal};
    });
  }
  function pathFor(storeId, resource) {
    if (!uuid(storeId) || !['catalogo/produtos','catalogo/fornecedores',
                             'estoque/saldos','estoque/movimentos'].includes(resource)) {
      throw new Error('Consulta fora do escopo');
    }
    return BASE + '/' + storeId + '/' + resource;
  }
  function createApi(fetcher, auth) {
    async function read(path) {
      const token = await auth.getAccessToken();
      if (!token) throw new Error('Sessão encerrada');
      const response = await fetcher(path, {
        method: 'GET',cache: 'no-store',credentials:'same-origin',
        headers: {Authorization:'Bearer '+token,Accept:'application/json'}
      });
      if (!response.ok) {
        if ([401,403].includes(response.status)) throw new Error('Acesso não autorizado ou vínculo revogado');
        throw new Error('Não foi possível consultar os dados');
      }
      const result = await response.json();
      if (!Array.isArray(result)) throw new Error('Resposta inesperada');
      return result;
    }
    return {
      stores: async () => validStores(await read(BASE)),
      load: async (id) => {
        // Recheck active membership on each selection/refresh, never rely on
        // cached store IDs or localStorage as an authorization decision.
        const stores = await validStores(await read(BASE));
        if (!stores.some(store=>store.id===id)) throw new Error('Estabelecimento não autorizado');
        const [products,suppliers,balances,movements] = await Promise.all([
          read(pathFor(id,'catalogo/produtos')),
          read(pathFor(id,'catalogo/fornecedores')),
          read(pathFor(id,'estoque/saldos')),
          read(pathFor(id,'estoque/movimentos'))
        ]);
        return {stores,products,suppliers,balances,movements};
      }
    };
  }
  global.StockosTenantConsole = {uuid,validStores,pathFor,createApi};
  if (typeof document === 'undefined') return;
  const $ = id => document.getElementById(id);
  const elements = ['tenant-selector','tenant-status','tenant-panels','tenant-refresh','tenant-logout'];
  if (!elements.every(id => $(id))) return;
  const selector = $('tenant-selector');
  const panels = $('tenant-panels');
  const refreshButton = $('tenant-refresh');
  const status = $('tenant-status');
  let api = null;
  let requestId = 0;
  let authorized = [];
  function notify(message, kind) {
    status.textContent=message;
    status.dataset.kind=kind||'info';
  }
  function clearPanels() {
    panels.hidden=true;
    for (const id of ['tenant-products','tenant-balances','tenant-suppliers','tenant-movements']) $(id).replaceChildren();
    for (const id of ['count-products','count-balances','count-suppliers']) $(id).textContent='0';
  }
  function line(parent, name, note) {
    const row=document.createElement('div');
    row.className='row';
    const main=document.createElement('strong');
    main.textContent=String(name ?? 'Sem descrição');
    const extra=document.createElement('span');
    extra.className='minor';
    extra.textContent=String(note ?? '');
    row.append(main,extra);
    parent.append(row);
  }
  function rows(id, items, render) {
    const parent=$(id);
    parent.replaceChildren();
    if (!items.length) {line(parent,'Nenhum registro disponível','');return;}
    items.forEach(item => render(parent,item));
  }
  function render(data) {
    $('count-products').textContent=String(data.products.length);
    $('count-balances').textContent=String(data.balances.length);
    $('count-suppliers').textContent=String(data.suppliers.length);
    rows('tenant-products',data.products,(p,item)=>line(p,item.nome,'#'+item.id));
    rows('tenant-suppliers',data.suppliers,(p,item)=>line(p,item.nome,'#'+item.id));
    rows('tenant-balances',data.balances,(p,item)=>line(p,item.nomeProduto,
      'Qtd: '+String(item.quantidade??0)+' · Reserva: '+String(item.reservada??0)));
    rows('tenant-movements',data.movements,(p,item)=>line(p,item.tipo,
      'Produto #'+item.produtoId+' · '+String(item.quantidade??0)));
    panels.hidden=false;
  }
  function populate(stores, selected) {
    selector.replaceChildren();
    const placeholder=document.createElement('option');
    placeholder.value='';
    placeholder.textContent=stores.length?'Selecione sua loja':'Nenhuma loja ativa disponível';
    selector.append(placeholder);
    for (const store of stores) {
      const option=document.createElement('option');
      option.value=store.id;
      option.textContent=store.nome+' · '+store.papel;
      selector.append(option);
    }
    selector.disabled=!stores.length;
    selector.value=stores.some(s=>s.id===selected)?selected:'';
  }
  async function fail(error) {
    clearPanels();
    selector.disabled=true;
    refreshButton.disabled=true;
    notify(error?.message||'Falha de acesso. Entre novamente.','error');
  }
  async function loadStore(id) {
    const seq=++requestId;
    clearPanels();
    if (!id) {notify('Escolha um estabelecimento autorizado.');return;}
    refreshButton.disabled=true;
    notify('Consultando dados autorizados…');
    try {
      const data=await api.load(id);
      if (seq!==requestId) return;
      authorized=data.stores;
      populate(authorized,id);
      render(data);
      notify('Dados atualizados para a loja selecionada.','ok');
    } catch(error) {
      if (seq!==requestId) return;
      await fail(error);
    } finally {
      if (seq===requestId) refreshButton.disabled=false;
    }
  }
  async function init() {
    clearPanels();
    selector.disabled=true;
    notify('Validando sessão…');
    try {
      if (global.STOCKOS_PREVIEW || !global.StockosAuth) throw new Error('Console de homologação indisponível nesta demonstração');
      const mode=await global.StockosAuth.discover();
      if (mode.mode!=='supabase') throw new Error('Somente autenticação Supabase é permitida');
      const token=await global.StockosAuth.getAccessToken();
      if (!token) throw new Error('Sessão não encontrada. Faça login novamente');
      api=createApi(global.fetch.bind(global),global.StockosAuth);
      authorized=await api.stores();
      populate(authorized,'');
      notify(authorized.length?'Escolha o estabelecimento para consultar os dados.':'Sua conta não possui estabelecimentos ativos.');
      refreshButton.disabled=false;
    } catch(error) {
      await fail(error);
    }
  }
  selector.addEventListener('change',()=>void loadStore(selector.value));
  refreshButton.addEventListener('click',()=>void loadStore(selector.value));
  $('tenant-logout').addEventListener('click',async()=>{
    ++requestId;
    clearPanels();
    try {await global.StockosAuth?.signOut();} catch(_) {}
    try {global.sessionStorage?.removeItem('usuario');global.localStorage?.removeItem('usuario');}catch(_){}
    global.location.assign('/login.html');
  });
  if (document.readyState==='loading') document.addEventListener('DOMContentLoaded',()=>void init(),{once:true});
  else void init();
})(typeof window === 'undefined' ? globalThis : window);
