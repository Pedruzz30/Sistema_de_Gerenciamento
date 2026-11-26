/* SPA-isolated screen: estoque. Prevent cross-page handler overwrites. */
(() => {
'use strict';
// Stock screen: listing, category filtering, product create/edit, and movements.

let todosOsProdutos = [];
let produtosVisiveis = [];
let PRODUTOS_POR_PAGINA = 25;
let stockSort="original",stockLoadVersion=0;
let paginaEstoque = 1;
let filtroNivelAtivo = 'todos';
let filtroCategoriaAtiva = 'todas';
let produtoSelecionado = null;
let produtoEmEdicao = null;
let tipoMovAtual = 'ENTRADA';

const CATEGORIAS_ESTOQUE = [
  { value: '', label: 'Não definida' },
  { value: 'BEBIDAS', label: 'Bebidas' },
  { value: 'SECO', label: 'Estoque seco' },
  { value: 'FRIO', label: 'Estoque frio' },
  { value: 'CONGELADO', label: 'Estoque congelado' }
];

async function iniciarEstoqueSPA() {
  if (paginaAtual() !== 'estoque') return;
  if (!temPermissao(PERMISSOES.EDITAR_ESTOQUE)) {
    const btn = document.getElementById('btn-novo-produto');
    if (btn) btn.hidden = true;
    const btnRevenda = document.getElementById('btn-revenda');
    if (btnRevenda) btnRevenda.hidden = true;
  }

  filtroNivelAtivo='todos';filtroCategoriaAtiva='todas';paginaEstoque=1;stockSort='original';PRODUTOS_POR_PAGINA=25;
  document.getElementById('stock-sort').onchange=e=>{stockSort=e.target.value;paginaEstoque=1;renderTabela();};
  document.getElementById('stock-size').onchange=e=>{PRODUTOS_POR_PAGINA=[15,25,50,60].includes(Number(e.target.value))?Number(e.target.value):25;paginaEstoque=1;renderTabela();};
  preencherSelectCategorias('c-categoria');
  preencherSelectCategorias('e-categoria');
  // Link do Dashboard apenas filtra a listagem; não movimenta estoque.
  const filtroUrl = new URLSearchParams(window.location.search).get('filtro');
  if (filtroUrl === 'critico' || filtroUrl === 'baixo') {
    filtroNivelAtivo = filtroUrl;
    filtroCategoriaAtiva = 'todas';
    paginaEstoque = 1;
    document.querySelectorAll('[data-filter-group="nivel"] .filter-btn').forEach(btn => {
      const tipo = btn.getAttribute('onclick') || '';
      btn.classList.toggle('active', tipo.includes("'" + filtroUrl + "'"));
    });
  }
  await carregarProdutos();
}

window.StockOSPages = window.StockOSPages || {};
window.StockOSPages.estoque = {
  mount: iniciarEstoqueSPA,
  activate() {
    Object.assign(window, {
      carregarProdutos,
      cadastrarProduto,
      abrirModalRevenda,
      salvarRevenda,
      teclaRevenda,
      atualizarProduto,
      confirmarMovimentacao,
      renderTabela,
      nivelLabel,
      getProdutosVisiveis,
      categoriaLabel,
      renderCategoriaBadge,
      calcularMetricasCategorias,
      renderResumoCategorias,
      renderGraficoCategorias,
      atualizarEstadoExportacao,
      exportarCsvProdutos,
      preencherSelectCategorias,
      filtrar,
      filtrarCategoria,
      filtrarProdutos,
      resetarFiltrosEstoque,
      abrirModalCadastro,
      abrirModalEdicao,
      abrirModalMov,
      fecharModal,
      fecharModalSeClicouFora,
      setTipo,
      limparFormCadastro,
      mostrarAlertaModal,
    });
  }
};
document.addEventListener('DOMContentLoaded', iniciarEstoqueSPA);

async function carregarProdutos() {
  const tbody=document.getElementById('tabela-produtos'),version=++stockLoadVersion;
  if(!tbody)return;
  const current=()=>paginaAtual()==='estoque'&&document.getElementById('tabela-produtos')===tbody&&version===stockLoadVersion;
  try {
    const res = await fetch('/api/produtos');
    if (!res.ok) throw new Error('Falha ao carregar produtos');
    const produtos = await res.json();
    if (!current()) return;
    if(!Array.isArray(produtos))throw Error('Resposta inválida');
    todosOsProdutos = produtos;
    renderTabela();
  } catch (e) {
    if (!current()) return;
    todosOsProdutos=[];produtosVisiveis=[];atualizarEstadoExportacao();
    document.getElementById('categoria-summary').textContent='Indicadores indisponíveis';
    document.getElementById('categoria-chart').textContent='Distribuição indisponível';
    document.getElementById('table-count').textContent='—';
    document.getElementById('paginacao-estoque')?.remove();
    tbody.innerHTML='<tr><td colspan="7" class="empty">Erro ao carregar produtos. <button type="button" class="btn btn-secondary" onclick="carregarProdutos()">Tentar novamente</button></td></tr>';
  }
}

// Registration uses exact decimal text for the BigDecimal API and integer stock.
function productCreateQuantity(value){
  const s=String(value??'').trim();
  if(!/^\d+$/.test(s))return null;
  const n=Number(s);return Number.isSafeInteger(n)&&n<=2147483647?n:null;
}
function productCreatePrice(value){
  const s=String(value??'').trim().replace(',','.');
  if(!/^\d{1,13}(?:\.\d{1,2})?$/.test(s))return null;
  return s;
}
function productCreateStock(initial,minimum){
  if(initial==null||minimum==null)return null;
  const pct=minimum===0?(initial>0?100:0):initial*100/minimum;
  return {level:pct===0?'SEM_ESTOQUE':pct<=25?'CRITICO':pct<=50?'BAIXO':pct<=75?'MODERADO':'ADEQUADO',deficit:Math.max(0,minimum-initial)};
}
function productCreateInput(fields){
  const nome=String(fields.nome??'').trim(),quantidadeInicial=productCreateQuantity(fields.initial),quantidadeMinima=productCreateQuantity(fields.minimum),precoUnitario=productCreatePrice(fields.price),category=String(fields.category??'');
  if(!nome||nome.length>120||quantidadeInicial==null||quantidadeMinima==null||precoUnitario==null||!CATEGORIAS_ESTOQUE.some(c=>c.value===category))return null;
  return {nome,quantidadeInicial,quantidadeMinima,precoUnitario,categoriaEstoque:category||null};
}
let productCreatePending=false,productCreateUncertain=false;
const productCreateGet=id=>document.getElementById(id);
function productCreateFields(){
  const get=productCreateGet;return {nome:get('c-nome').value,initial:get('c-qtd-inicial').value,minimum:get('c-qtd-minima').value,price:get('c-preco').value,category:get('c-categoria').value};
}
function updateProductCreate(){
  const get=productCreateGet;if(!get('product-create-submit'))return;
  const fields=productCreateFields(),initial=productCreateQuantity(fields.initial),minimum=productCreateQuantity(fields.minimum),price=productCreatePrice(fields.price),stock=productCreateStock(initial,minimum);
  const levels={SEM_ESTOQUE:'Sem estoque',CRITICO:'Crítico',BAIXO:'Baixo',MODERADO:'Moderado',ADEQUADO:'Adequado'};
  get('product-preview-level').textContent=stock?levels[stock.level]:'—';
  get('product-preview-level').dataset.level=stock?.level||'';
  get('product-preview-deficit').textContent=stock?stock.deficit.toLocaleString('pt-BR')+' un.':'—';
  get('product-preview-price').textContent=price==null?'—':Number(price).toLocaleString('pt-BR',{style:'currency',currency:'BRL'});
  get('product-initial-help').textContent=initial==null&&fields.initial!==''?'Informe uma quantidade inteira entre 0 e 2.147.483.647.':'Saldo físico disponível agora.';
  get('product-min-help').textContent=minimum==null&&fields.minimum!==''?'Informe uma quantidade inteira entre 0 e 2.147.483.647.':minimum===0?'Com mínimo zero, um saldo positivo é considerado adequado.':'Referência para os níveis de estoque.';
  get('product-price-help').textContent=price==null&&fields.price!==''?'Informe um preço não negativo, com até duas casas decimais.':'Preço unitário do produto. Não representa necessariamente o custo de aquisição.';
  for(const [id,invalid] of [['c-qtd-inicial',initial==null&&fields.initial!==''],['c-qtd-minima',minimum==null&&fields.minimum!==''],['c-preco',price==null&&fields.price!=='']])get(id).setAttribute('aria-invalid',String(invalid));
  get('product-create-submit').disabled=productCreatePending||productCreateUncertain||!productCreateInput(fields)||!temPermissao(PERMISSOES.EDITAR_ESTOQUE);
  get('product-check-stock').hidden=!productCreateUncertain;
  get('product-check-stock').disabled=productCreatePending;
}
function bindProductCreate(){
  const get=productCreateGet;
  ['c-nome','c-qtd-inicial','c-qtd-minima','c-preco'].forEach(id=>get(id).oninput=updateProductCreate);
  get('c-categoria').onchange=updateProductCreate;
  get('product-check-stock').onclick=()=>fecharModal('modal-cadastro');
}
async function cadastrarProduto(){
  if(productCreatePending||productCreateUncertain||!temPermissao(PERMISSOES.EDITAR_ESTOQUE))return;
  const get=productCreateGet,payload=productCreateInput(productCreateFields()),alertEl=get('alert-cadastro'),modal=get('modal-cadastro');
  if(!payload){mostrarAlertaModal(alertEl,'error','Confira o nome, as quantidades inteiras, a categoria e o preço.');return;}
  const current=()=>get('modal-cadastro')===modal&&modal.classList.contains('open');
  const controls=[...modal.querySelectorAll('input,select,button')].map(el=>[el,el.disabled]);
  productCreatePending=true;alertEl.className='alert-modal';controls.forEach(([el])=>el.disabled=true);
  try{
    const res=await fetch('/api/produtos',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(payload)});
    if(!current())return;
    if(!res.ok){
      if(res.status>=500||res.status===409)throw Error('Resultado incerto');
      const message=await safeReadErrorMessage(res,'Erro ao cadastrar produto.');
      if(current())mostrarAlertaModal(alertEl,'error',message);return;
    }
    const result=await res.json();
    if(!current())return;
    if(!result||!Number.isSafeInteger(Number(result.id))||Number(result.id)<=0||typeof result.nome!=='string'||result.nome.trim()!==payload.nome||Number(result.quantidadeAtual)!==payload.quantidadeInicial)throw Error('Resposta inesperada');
    modal.classList.remove('open');limparFormCadastro();showToast('Produto cadastrado com sucesso.','success');await carregarProdutos();
  }catch{
    if(current()){productCreateUncertain=true;mostrarAlertaModal(alertEl,'error','Não foi possível confirmar o cadastro. Volte ao estoque e verifique se o produto já existe antes de cadastrar novamente.');}
  }finally{
    productCreatePending=false;controls.forEach(([el,disabled])=>{if(el.isConnected)el.disabled=disabled;});
    if(current())updateProductCreate();
  }
}

let stockMutationPending=false,stockMutationUncertain=false;
function stockMovementModel(product,type,raw){
  const qtd=productCreateQuantity(raw),before=Number(product?.quantidadeAtual);
  if(!product||!Number.isSafeInteger(before)||before<0||qtd==null||qtd===0||!['ENTRADA','SAIDA'].includes(type))return null;
  const delta=type==='ENTRADA'?qtd:-qtd,after=before+delta;
  if(after<0||after>2147483647)return null;
  return {qtd,before,delta,after,level:productCreateStock(after,Number(product.quantidadeMinima))?.level};
}
function stockEditPayload(){
  const get=productCreateGet;
  if(!produtoEmEdicao)return null;
  const data=productCreateInput({nome:get('e-nome').value,initial:'0',minimum:get('e-qtd-minima').value,price:get('e-preco').value,category:get('e-categoria').value});
  if(!data)return null;
  const {quantidadeInicial,...payload}=data;
  return payload;
}
function updateStockEdit(){
  const get=productCreateGet,p=produtoEmEdicao,payload=stockEditPayload();
  if(!p)return;
  const changed=payload&&(payload.nome!==p.nome||payload.quantidadeMinima!==Number(p.quantidadeMinima)||Number(payload.precoUnitario)!==Number(p.precoUnitario)||payload.categoriaEstoque!==(p.categoriaEstoque||null));
  get('stock-edit-level').textContent=payload?nivelLabel(productCreateStock(Number(p.quantidadeAtual),payload.quantidadeMinima).level):'—';
  get('stock-edit-save').disabled=stockMutationPending||stockMutationUncertain||!changed||!temPermissao(PERMISSOES.EDITAR_ESTOQUE);
}
function updateStockMovement(){
  const get=productCreateGet,p=produtoSelecionado;if(!p)return;
  const raw=get('mov-qtd').value,model=stockMovementModel(p,tipoMovAtual,raw),format=v=>v.toLocaleString('pt-BR')+' un.';
  get('stock-mov-before').textContent=format(Number(p.quantidadeAtual));
  get('stock-mov-delta').textContent=model?(model.delta>0?'+':'−')+format(Math.abs(model.delta)):'—';
  get('stock-mov-after').textContent=model?format(model.after):'—';
  get('stock-mov-help').textContent=raw&&!model?'Quantidade inválida ou saldo fora do limite.':'Informe uma quantidade inteira positiva.';
  get('stock-mov-level').textContent=model?'Nível previsto: '+nivelLabel(model.level)+'. O saldo final depende da validação do servidor.':'O saldo final será validado pelo servidor.';
  get('btn-confirmar-mov').textContent=tipoMovAtual==='ENTRADA'?'Confirmar entrada':'Confirmar saída';
  get('btn-confirmar-mov').disabled=stockMutationPending||stockMutationUncertain||!model||!temPermissao(PERMISSOES.EDITAR_ESTOQUE);
}
async function stockMutation(id,alertId,url,method,payload,productId,update){
  if(stockMutationPending||stockMutationUncertain||!temPermissao(PERMISSOES.EDITAR_ESTOQUE))return;
  const get=productCreateGet,modal=get(id),alert=get(alertId);if(!modal?.classList.contains('open'))return;
  const current=()=>get(id)===modal&&modal.classList.contains('open');
  const controls=[...modal.querySelectorAll('input,select,button')].map(el=>[el,el.disabled]);
  stockMutationPending=true;controls.forEach(([el])=>el.disabled=true);alert.className='alert-modal';
  try{
    const res=await fetch(url,{method,headers:{'Content-Type':'application/json'},body:JSON.stringify(payload)});
    if(!current())return;
    if(!res.ok){
      if(res.status>=500||res.status===409)throw Error('Resultado incerto');
      const message=await safeReadErrorMessage(res,'Não foi possível concluir a operação.');if(current())mostrarAlertaModal(alert,'error',message);return;
    }
    const result=await res.json();if(!current())return;
    if(!result||Number(result.id)!==Number(productId)||!Number.isInteger(result.quantidadeAtual))throw Error('Resposta inesperada');
    modal.classList.remove('open');showToast(method==='PUT'?'Produto atualizado.':'Movimentação registrada.','success');await carregarProdutos();
  }catch{
    if(current()){stockMutationUncertain=true;mostrarAlertaModal(alert,'error','Resultado não confirmado. Feche esta janela e confira o estoque atualizado antes de repetir a operação.');}
  }finally{stockMutationPending=false;controls.forEach(([el,disabled])=>{if(el.isConnected)el.disabled=disabled;});if(current())update();}
}
async function atualizarProduto(){
  const payload=stockEditPayload();if(!payload||!produtoEmEdicao||productCreateGet('stock-edit-save').disabled)return;
  await stockMutation('modal-edicao','alert-edicao','/api/produtos/'+encodeURIComponent(produtoEmEdicao.id),'PUT',payload,produtoEmEdicao.id,updateStockEdit);
}
async function confirmarMovimentacao(){
  const model=stockMovementModel(produtoSelecionado,tipoMovAtual,productCreateGet('mov-qtd').value);
  if(!model||!produtoSelecionado)return;
  await stockMutation('modal-mov','alert-mov','/api/movimentacoes','POST',{idProduto:produtoSelecionado.id,quantidade:model.qtd,tipo:tipoMovAtual},produtoSelecionado.id,updateStockMovement);
}

function renderTabela() {
  const oldNav = document.getElementById('paginacao-estoque');
  if (oldNav) oldNav.remove();
  const tbody = document.getElementById('tabela-produtos');
  const countEl = document.getElementById('table-count');
  const podeEditar = temPermissao(PERMISSOES.EDITAR_ESTOQUE);
  const produtos = getProdutosVisiveis();
  produtosVisiveis = produtos;
  const paginas = Math.max(1, Math.ceil(produtos.length / PRODUTOS_POR_PAGINA));
  paginaEstoque = Math.min(Math.max(1, paginaEstoque), paginas);
  const inicio = (paginaEstoque - 1) * PRODUTOS_POR_PAGINA;
  const itensPagina = produtos.slice(inicio, inicio + PRODUTOS_POR_PAGINA);

  countEl.textContent = produtos.length.toLocaleString('pt-BR')+' de '+todosOsProdutos.length.toLocaleString('pt-BR')+' produtos';
  atualizarEstadoExportacao();
  renderResumoCategorias(todosOsProdutos);
  renderGraficoCategorias(produtos);

  if (produtos.length === 0) {
    if (todosOsProdutos.length === 0) {
      tbody.innerHTML = `
        <tr><td colspan="7" class="table-empty-cell">
          ${emptyStateMarkup({
            icon: 'box',
            title: 'Nenhum produto cadastrado',
            copy: 'Cadastre produtos para começar a gerenciar o estoque.',
            actions: podeEditar ? '<button class="btn-primary" onclick="abrirModalCadastro()">Novo Produto</button>' : ''
          })}
        </td></tr>`;
    } else {
      tbody.innerHTML = '<tr><td colspan="7" class="empty">Nenhum produto encontrado. <button type="button" class="btn btn-secondary" onclick="resetarFiltrosEstoque()">Limpar filtros</button></td></tr>';
    }
    return;
  }

  tbody.innerHTML = itensPagina.map(p => `
    <tr>
      <td data-label="Produto">
        <div class="produto-nome">${escapeHtml(p.nome)}</div>
        <div class="produto-id">#${p.id}</div>
      </td>
      <td data-label="Categoria">${renderCategoriaBadge(p.categoriaEstoque)}</td>
      <td data-label="Quantidade atual"><strong>${p.quantidadeAtual}</strong> un.</td>
      <td data-label="Mínimo">${p.quantidadeMinima} un.</td>
      <td data-label="Nível">
        <div class="nivel-wrap nivel-${p.nivelEstoque}">
          <div class="nivel-bar">
            <div class="nivel-bar-fill" style="width:${stockCoverage(p.quantidadeAtual,p.quantidadeMinima)}%"></div>
          </div>
          <span class="nivel-badge">${nivelLabel(p.nivelEstoque)}</span>
        </div>
      </td>
      <td data-label="Preço">${stockMoney(p.precoUnitario)}</td>
      <td data-label="Ações">
          <div class="acoes">
            ${podeEditar ? `<button class="btn-acao editar" onclick="abrirModalEdicao(${p.id})">Editar</button>` : ''}
            ${podeEditar ? `<button class="btn-acao entrada" onclick="abrirModalMov(${p.id}, 'ENTRADA')">Entrada</button>` : ''}
            ${podeEditar ? `<button class="btn-acao saida" onclick="abrirModalMov(${p.id}, 'SAIDA')">Saída</button>` : ''}
            ${!podeEditar ? '<span class="status-placeholder">Sem permissão</span>' : ''}
          </div>
        </td>
      </tr>
  `).join('');
  if (produtos.length > 0) {
    const nav = document.createElement('div');
    nav.id = 'paginacao-estoque';
    nav.className = 'table-pagination';
    nav.style.cssText = 'display:flex;justify-content:center;align-items:center;gap:16px;padding:16px';
    const anterior = document.createElement('button');
    anterior.type = 'button'; anterior.textContent = 'Anterior'; anterior.disabled = paginaEstoque <= 1;
    anterior.addEventListener('click', () => { paginaEstoque--; renderTabela(); });
    const info = document.createElement('span');
    info.textContent = (inicio+1)+'–'+(inicio+itensPagina.length)+' de '+produtos.length+' · Página '+paginaEstoque+' de '+paginas;
    const proximo = document.createElement('button');
    proximo.type = 'button'; proximo.textContent = 'Próxima'; proximo.disabled = paginaEstoque >= paginas;
    proximo.addEventListener('click', () => { paginaEstoque++; renderTabela(); });
    nav.append(anterior, info, proximo);
    tbody.closest('table').insertAdjacentElement('afterend', nav);
  }
}

function nivelLabel(nivel) {
  const labels = {
    SEM_ESTOQUE: 'Sem Estoque',
    CRITICO: 'Crítico',
    BAIXO: 'Baixo',
    MODERADO: 'Moderado',
    ADEQUADO: 'Adequado'
  };
  return labels[nivel] || nivel;
}

function getProdutosVisiveis() {
  const normalizarBusca = texto => String(texto || '').normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '').toLowerCase().trim();
  const busca = normalizarBusca(document.getElementById('busca').value);

  const result = todosOsProdutos.filter(p => {
    const nome = normalizarBusca(p.nome);
    const categoria = p.categoriaEstoque || '';

    const matchBusca = nome.includes(busca) || (busca!=='' && /^#?\d+$/.test(busca) && String(p.id)===busca.replace(/^#/,''));
    const matchNivel =
      filtroNivelAtivo === 'todos' ? true :
      filtroNivelAtivo === 'critico' ? p.nivelEstoque === 'CRITICO' || p.nivelEstoque === 'SEM_ESTOQUE' :
      filtroNivelAtivo === 'baixo' ? p.nivelEstoque === 'BAIXO' || p.nivelEstoque === 'MODERADO' :
      filtroNivelAtivo === 'adequado' ? p.nivelEstoque === 'ADEQUADO' :
      true;
    const matchCategoria = filtroCategoriaAtiva === 'todas' ? true : filtroCategoriaAtiva==='sem-categoria'?!categoria:categoria === filtroCategoriaAtiva;

    return matchBusca && matchNivel && matchCategoria;
  });
  return stockSortRows(result,stockSort);
}

function categoriaLabel(categoria) {
  const labels = {
    BEBIDAS: 'Bebidas',
    SECO: 'Estoque seco',
    FRIO: 'Estoque frio',
    CONGELADO: 'Estoque congelado'
  };
  return labels[categoria] || 'N\u00e3o definida';
}

function renderCategoriaBadge(categoria) {
  const classe = categoria ? `categoria-${categoria}` : 'categoria-nao-definida';
  return `<span class="categoria-badge ${classe}">${categoriaLabel(categoria)}</span>`;
}

function calcularMetricasCategorias(produtos) {
  const metricas = {
    BEBIDAS: 0,
    SECO: 0,
    FRIO: 0,
    CONGELADO: 0,
    INDEFINIDA: 0
  };

  produtos.forEach(p => {
    const key=['BEBIDAS','SECO','FRIO','CONGELADO'].includes(p.categoriaEstoque)?p.categoriaEstoque:'INDEFINIDA';
    metricas[key]++;
  });

  return metricas;
}

function stockMoney(value){return value!=null&&value!==''&&Number.isFinite(Number(value))?Number(value).toLocaleString('pt-BR',{style:'currency',currency:'BRL'}):'—';}
function stockCoverage(q,m){return Number(m)>0?Math.max(0,Math.min(100,Number(q)*100/Number(m))):Number(q)>0?100:0;}
function stockMetrics(rows){
  return {total:rows.length,empty:rows.filter(p=>p.nivelEstoque==='SEM_ESTOQUE').length,attention:rows.filter(p=>['CRITICO','BAIXO','MODERADO'].includes(p.nivelEstoque)).length,adequate:rows.filter(p=>p.nivelEstoque==='ADEQUADO').length};
}
function stockSortRows(rows,sort){
  const rank=['SEM_ESTOQUE','CRITICO','BAIXO','MODERADO','ADEQUADO'],risk=p=>rank.includes(p.nivelEstoque)?rank.indexOf(p.nivelEstoque):rank.length;
  if(sort==='risk')return rows.slice().sort((a,b)=>risk(a)-risk(b)||Number(a.quantidadeAtual)-Number(b.quantidadeAtual));
  if(sort==='name')return rows.slice().sort((a,b)=>String(a.nome).localeCompare(String(b.nome),'pt-BR'));
  if(sort==='qty')return rows.slice().sort((a,b)=>Number(a.quantidadeAtual)-Number(b.quantidadeAtual));
  return rows;
}
function renderResumoCategorias(produtos) {
  const m=stockMetrics(produtos),cards=[['Produtos cadastrados',m.total,'Catálogo carregado','neutral'],['Sem estoque',m.empty,'Saldo zerado','bad'],['Em atenção',m.attention,'Crítico, baixo ou moderado','warn'],['Adequados',m.adequate,'Nível saudável','ok']];
  document.getElementById('categoria-summary').innerHTML=cards.map(([label,value,sub,tone])=>'<div class="categoria-summary-card" data-tone="'+tone+'"><div class="categoria-summary-label">'+label+'</div><div class="categoria-summary-value">'+value.toLocaleString('pt-BR')+'</div><div class="categoria-summary-sub">'+sub+'</div></div>').join('');
}

function renderGraficoCategorias(produtos) {
  const metricas = calcularMetricasCategorias(produtos);
  const categorias = [
    { key: 'BEBIDAS', label: 'Bebidas' },
    { key: 'SECO', label: 'Estoque seco' },
    { key: 'FRIO', label: 'Estoque frio' },
    { key: 'CONGELADO', label: 'Estoque congelado' },
    { key: 'INDEFINIDA', label: 'Não definida' }
  ];
  const maximo = Math.max(1, produtos.length);
  const container = document.getElementById('categoria-chart');

  container.innerHTML = categorias.map(cat => {
    const valor = metricas[cat.key];
    const largura = ((valor / maximo) * 100).toFixed(2);
    return `
      <div class="categoria-chart-row">
        <div class="categoria-chart-label">${cat.label}</div>
        <div class="categoria-chart-bar">
          <div class="categoria-chart-fill categoria-${cat.key}" data-width="${largura}"></div>
        </div>
        <div class="categoria-chart-value">${valor.toLocaleString('pt-BR')} · ${Number(largura).toLocaleString('pt-BR',{maximumFractionDigits:1})}%</div>
      </div>
    `;
  }).join('');

  container.querySelectorAll('.categoria-chart-fill').forEach((item) => {
    item.style.width = `${item.dataset.width}%`;
  });
}

function atualizarEstadoExportacao() {
  const btn = document.getElementById('btn-exportar-csv');
  if (!btn) return;
  btn.disabled = produtosVisiveis.length === 0;
}

function exportarCsvProdutos() {
  if (!produtosVisiveis.length) {
    showToast('Não há linhas visíveis para exportar.', 'info');
    return;
  }

  const linhas = [
    ['Produto', 'Categoria', 'Quantidade', 'Nível do estoque'],
    ...produtosVisiveis.map(p => [
      p.nome || '',
      categoriaLabel(p.categoriaEstoque),
      String(p.quantidadeAtual ?? ''),
      nivelLabel(p.nivelEstoque)
    ])
  ];

  const csv = '\uFEFF' + linhas
    .map(colunas => colunas.map(valor => `"${String(valor).replace(/"/g, '""')}"`).join(','))
    .join('\r\n');

  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  const data = new Date().toISOString().slice(0, 10);

  link.href = url;
  link.download = `estoque-${data}.csv`;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

function preencherSelectCategorias(selectId) {
  const select = document.getElementById(selectId);
  if (!select) return;

  select.innerHTML = CATEGORIAS_ESTOQUE.map(c =>
    `<option value="${c.value}">${c.label}</option>`
  ).join('');
}

function filtrar(tipo, btn) {
  paginaEstoque = 1;
  filtroNivelAtivo = tipo;
  document.querySelectorAll('[data-filter-group="nivel"] .filter-btn').forEach(b => b.classList.remove('active'));
  btn.classList.add('active');
  renderTabela();
}

function filtrarCategoria(tipo, btn) {
  paginaEstoque = 1;
  filtroCategoriaAtiva = tipo;
  document.querySelectorAll('[data-filter-group="categoria"] .filter-btn').forEach(b => b.classList.remove('active'));
  btn.classList.add('active');
  renderTabela();
}

function filtrarProdutos() {
  paginaEstoque = 1;
  renderTabela();
}

function resetarFiltrosEstoque() {
  paginaEstoque = 1;
  filtroNivelAtivo = 'todos';
  filtroCategoriaAtiva = 'todas';
  const busca = document.getElementById('busca');
  if (busca) busca.value = '';
  document.querySelectorAll('[data-filter-group]').forEach(group => {
    group.querySelectorAll('.filter-btn').forEach((button, index) => {
      button.classList.toggle('active', index === 0);
    });
  });
  renderTabela();
  busca?.focus();
}

function abrirModalCadastro() {
  if(productCreatePending||!temPermissao(PERMISSOES.EDITAR_ESTOQUE))return;
  productCreateUncertain=false;limparFormCadastro();bindProductCreate();updateProductCreate();
  document.getElementById('modal-cadastro').classList.add('open');
  document.getElementById('c-nome').focus();
}

function abrirModalEdicao(idProduto) {
  if(stockMutationPending||!temPermissao(PERMISSOES.EDITAR_ESTOQUE))return;
  stockMutationUncertain=false;
  produtoEmEdicao = todosOsProdutos.find(p => p.id === idProduto);
  if (!produtoEmEdicao) return;

  document.getElementById('e-nome').value = produtoEmEdicao.nome || '';
  document.getElementById('e-qtd-minima').value = produtoEmEdicao.quantidadeMinima;
  document.getElementById('e-preco').value = produtoEmEdicao.precoUnitario;
  document.getElementById('e-categoria').value = produtoEmEdicao.categoriaEstoque || '';
  document.getElementById('alert-edicao').className = 'alert-modal';
  const get=productCreateGet;
  get('stock-edit-id').textContent='#'+produtoEmEdicao.id;
  get('stock-edit-current').textContent=produtoEmEdicao.quantidadeAtual.toLocaleString('pt-BR')+' un.';
  get('stock-edit-price').textContent=stockMoney(produtoEmEdicao.precoUnitario);
  ['e-nome','e-qtd-minima','e-preco'].forEach(id=>get(id).oninput=updateStockEdit);get('e-categoria').onchange=updateStockEdit;
  updateStockEdit();get('modal-edicao').classList.add('open');get('e-nome').focus();
}

function abrirModalMov(idProduto, tipo) {
  if(stockMutationPending||!temPermissao(PERMISSOES.EDITAR_ESTOQUE))return;
  stockMutationUncertain=false;
  produtoSelecionado = todosOsProdutos.find(p => p.id === idProduto);
  if (!produtoSelecionado) return;

  document.getElementById('mov-produto-nome').textContent = produtoSelecionado.nome;
  document.getElementById('mov-produto-qtd').textContent = `${produtoSelecionado.quantidadeAtual} unidades`;
  document.getElementById('mov-qtd').value = '';
  document.getElementById('alert-mov').className = 'alert-modal';

  setTipo(tipo);
  document.getElementById('modal-mov-titulo').textContent =
    tipo === 'ENTRADA' ? 'Registrar Entrada' : 'Registrar Saída';
  document.getElementById('mov-qtd').oninput=updateStockMovement;
  updateStockMovement();document.getElementById('modal-mov').classList.add('open');document.getElementById('mov-qtd').focus();
}

function fecharModal(id) {
  if(id==='modal-cadastro'&&productCreatePending)return;
  if((id==='modal-edicao'||id==='modal-mov')&&stockMutationPending)return;
  if(id==='modal-revenda'&&revendaSalvando)return;
  const modal = document.getElementById(id);
  if (modal) modal.classList.remove('open');
  if(id==='modal-cadastro'&&productCreateUncertain){productCreateUncertain=false;void carregarProdutos();}
  if((id==='modal-edicao'||id==='modal-mov')&&stockMutationUncertain){stockMutationUncertain=false;void carregarProdutos();}
  if(id==='modal-revenda'&&revendaUncertain){revendaUncertain=false;void carregarProdutos();}
}

function fecharModalSeClicouFora(event, id) {
  if (event.target.id === id) fecharModal(id);
}

function setTipo(tipo) {
  if(stockMutationPending||stockMutationUncertain||!['ENTRADA','SAIDA'].includes(tipo))return;
  tipoMovAtual = tipo;
  document.getElementById('modal-mov-titulo').textContent=tipo==='ENTRADA'?'Registrar entrada':'Registrar saída';
  document.getElementById('btn-entrada').setAttribute('aria-pressed',String(tipo==='ENTRADA'));
  document.getElementById('btn-saida').setAttribute('aria-pressed',String(tipo==='SAIDA'));
  document.getElementById('btn-entrada').classList.toggle('active', tipo === 'ENTRADA');
  document.getElementById('btn-saida').classList.toggle('active', tipo === 'SAIDA');
  updateStockMovement();
}

function limparFormCadastro() {
  ['c-nome','c-preco'].forEach(id=>document.getElementById(id).value='');
  ['c-qtd-inicial','c-qtd-minima'].forEach(id=>document.getElementById(id).value='0');
  document.getElementById('c-categoria').value = '';
  document.getElementById('alert-cadastro').className = 'alert-modal';
}

function mostrarAlertaModal(el, tipo, msg) {
  el.className = `alert-modal ${tipo}`;
  el.textContent = msg;
}
// ── Bebida rápida: cadastro com leitor de código de barras ────────────────
// O leitor digita o código e aperta Enter, então Enter aqui passa para o
// próximo campo e nunca salva sozinho. Ctrl+Enter salva.

const REVENDA_ORDEM = ['r-ean', 'r-nome', 'r-preco-gelado', 'r-preco', 'r-custo', 'r-qtd', 'r-min',
  'r-fardo-qtd', 'r-fardo-preco', 'r-fardo-ean'];
const REVENDA_CATEGORIAS_PADRAO = [
  { codigo: 'bebidas', nome: 'Bebidas' },
  { codigo: 'petiscos', nome: 'Petiscos' }
];
const REVENDA_MOEDA = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
let revendaSalvando = false,revendaUncertain=false;

async function abrirModalRevenda() {
  if(revendaSalvando||!temPermissao(PERMISSOES.EDITAR_ESTOQUE))return;
  revendaUncertain=false;
  limparFormRevenda();
  REVENDA_ORDEM.forEach(id=>document.getElementById(id).oninput=updateRevendaPreview);
  document.getElementById('btn-salvar-revenda').disabled=false;
  updateRevendaPreview();
  document.getElementById('alert-revenda').className = 'alert-modal';
  document.getElementById('modal-revenda').classList.add('open');
  document.getElementById('r-ean').focus();
  await carregarCategoriasRevenda();
}

async function carregarCategoriasRevenda() {
  const select = document.getElementById('r-categoria');
  const selecionada = select.value || 'bebidas';
  const categorias = [...REVENDA_CATEGORIAS_PADRAO];
  try {
    const resp = await fetch('/api/cardapio/categorias');
    if (resp.ok) {
      for (const categoria of await resp.json()) {
        const codigo = String(categoria.codigo || '').toLowerCase();
        if (codigo && !categorias.some(c => c.codigo === codigo)) {
          categorias.push({ codigo, nome: categoria.nomeExibicao || categoria.codigo });
        }
      }
    }
  } catch (_) { /* segue com as categorias padrão */ }
  select.replaceChildren(...categorias.map(categoria => {
    const option = document.createElement('option');
    option.value = categoria.codigo;
    option.textContent = categoria.nome;
    return option;
  }));
  select.value = categorias.some(c => c.codigo === selecionada) ? selecionada : 'bebidas';
}

function limparFormRevenda() {
  REVENDA_ORDEM.forEach(id => { document.getElementById(id).value = ''; });
  statusCodigoRevenda('r-ean', 'Sem código? Deixe vazio ou digite um código curto, como 101.');
  statusCodigoRevenda('r-fardo-ean', '');
}

function statusCodigoRevenda(campoId, texto, erro) {
  const el = document.getElementById(campoId + '-status');
  if (!el) return;
  el.textContent = texto;
  el.classList.toggle('revenda-status--erro', Boolean(erro));
}

function normalizarCodigoRevenda(valor) {
  return String(valor || '').replace(/\s+/g, '');
}

/** Dígito verificador GS1 (EAN-8, UPC-A, EAN-13, GTIN-14). Códigos internos passam. */
function codigoBarrasConfere(codigo) {
  if (!/^\d+$/.test(codigo) || ![8, 12, 13, 14].includes(codigo.length)) return true;
  let soma = 0;
  let peso = 3;
  for (let i = codigo.length - 2; i >= 0; i--) {
    soma += Number(codigo[i]) * peso;
    peso = peso === 3 ? 1 : 3;
  }
  return (10 - (soma % 10)) % 10 === Number(codigo[codigo.length - 1]);
}

/** Confere o código lido. Retorna true se pode seguir para o próximo campo. */
async function conferirCodigoRevenda(campoId) {
  const campo = document.getElementById(campoId);
  const codigo = normalizarCodigoRevenda(campo.value);
  campo.value = codigo;
  if (!codigo) return true;
  if (!codigoBarrasConfere(codigo)) {
    statusCodigoRevenda(campoId, 'Código inválido: o dígito verificador não confere. Bipe de novo.', true);
    campo.select();
    return false;
  }
  const outroId = campoId === 'r-ean' ? 'r-fardo-ean' : 'r-ean';
  if (codigo === normalizarCodigoRevenda(document.getElementById(outroId).value)) {
    statusCodigoRevenda(campoId, 'A unidade e o fardo precisam ter códigos diferentes.', true);
    campo.select();
    return false;
  }
  try {
    const resp = await fetch('/api/pdv/codigos/' + encodeURIComponent(codigo));
    if (resp.ok) {
      const leitura = await resp.json();
      const dono = leitura.variacao ? `${leitura.variacao.itemNome} (${leitura.variacao.variacaoNome})` : 'outro produto';
      statusCodigoRevenda(campoId, `Este código já é de ${dono}.`, true);
      campo.select();
      return false;
    }
  } catch (_) { /* sem conexão: o servidor confere ao salvar */ }
  statusCodigoRevenda(campoId, 'Código lido.');
  return true;
}

async function teclaRevenda(event) {
  if (event.isComposing||event.key !== 'Enter' || event.target.tagName === 'BUTTON') return;
  if(revendaSalvando||revendaUncertain){event.preventDefault();return;}
  event.preventDefault();
  if (event.ctrlKey) { salvarRevenda(); return; }
  const id = event.target.id;
  if ((id === 'r-ean' || id === 'r-fardo-ean') && !(await conferirCodigoRevenda(id))) return;
  const proximo = REVENDA_ORDEM[REVENDA_ORDEM.indexOf(id) + 1];
  if (proximo) document.getElementById(proximo).focus();
}

function numeroRevenda(id) {
  const texto = document.getElementById(id).value.trim().replace(',', '.');
  if(texto==='')return null;
  return productCreatePrice(texto)===null?NaN:Number(texto);
}

function montarCadastroRevenda() {
  const nome = document.getElementById('r-nome').value.trim();
  const precoVenda = numeroRevenda('r-preco');
  const precoGelado = numeroRevenda('r-preco-gelado');
  const custoUnitario = numeroRevenda('r-custo');
  const fardoQtd = numeroRevenda('r-fardo-qtd');
  const fardoPreco = numeroRevenda('r-fardo-preco');
  const fardoCodigo = normalizarCodigoRevenda(document.getElementById('r-fardo-ean').value);
  const select = document.getElementById('r-categoria');

  if (!nome||nome.length>120) return { erro: 'Informe o nome do produto (até 120 caracteres).', campo: 'r-nome' };
  for(const id of ['r-qtd','r-min']){
    const raw=document.getElementById(id).value;
    if(raw!==''&&productCreateQuantity(raw)===null)return {erro:'Informe uma quantidade inteira não negativa.',campo:id};
  }
  if (!(precoVenda > 0)) return { erro: 'Informe o preço de venda.', campo: 'r-preco' };
  if (precoGelado !== null && !(precoGelado > 0)) return { erro: 'Preço da gelada inválido.', campo: 'r-preco-gelado' };
  if (!(custoUnitario > 0)) return { erro: 'Informe o custo da unidade: o caixa não vende produto sem custo.', campo: 'r-custo' };
  const temFardo = fardoQtd !== null || fardoPreco !== null || fardoCodigo !== '';
  if (temFardo && !(Number.isInteger(fardoQtd) && fardoQtd >= 2 && fardoQtd <= 2147483647)) return { erro: 'Informe quantas unidades vêm no fardo.', campo: 'r-fardo-qtd' };
  if (temFardo && !(fardoPreco > 0)) return { erro: 'Informe o preço do fardo.', campo: 'r-fardo-preco' };

  const codigo = select.value || 'bebidas';
  return {
    dados: {
      nome,
      categoriaCardapioCodigo: codigo,
      categoriaCardapioNome: select.options[select.selectedIndex]?.textContent || null,
      categoriaEstoque: codigo === 'bebidas' ? 'BEBIDAS' : 'SECO',
      codigoBarras: normalizarCodigoRevenda(document.getElementById('r-ean').value),
      precoVenda,
      precoGelado,
      custoUnitario,
      quantidadeInicial: productCreateQuantity(document.getElementById('r-qtd').value||'0'),
      quantidadeMinima: productCreateQuantity(document.getElementById('r-min').value||'0'),
      fardo: temFardo ? { quantidadeUnidades: fardoQtd, preco: fardoPreco, codigoBarras: fardoCodigo } : null
    }
  };
}

function revendaMargin(price,cost){
  return Number.isFinite(price)&&Number.isFinite(cost)&&price>0&&cost>0?{profit:price-cost,margin:(price-cost)/price*100}:null;
}
function updateRevendaPreview(){
  const el=document.getElementById('revenda-preview-values');if(!el)return;
  const cost=numeroRevenda('r-custo'),n=numeroRevenda('r-fardo-qtd'),rows=[['Natural / unidade',numeroRevenda('r-preco'),cost],['Gelada',numeroRevenda('r-preco-gelado'),cost],['Fardo',numeroRevenda('r-fardo-preco'),Number.isInteger(n)&&n>=2&&cost>0?n*cost:null]];
  el.innerHTML=rows.map(([label,price,c])=>{const model=revendaMargin(price,c);return '<div><span>'+label+'</span><strong>'+stockMoney(price)+'</strong><small>'+(model?stockMoney(model.profit)+' de margem bruta · '+model.margin.toLocaleString('pt-BR',{maximumFractionDigits:1})+'%':'Margem —')+'</small></div>';}).join('');
}
async function salvarRevenda(){
  if(revendaSalvando||revendaUncertain||!temPermissao(PERMISSOES.EDITAR_ESTOQUE))return;
  const get=productCreateGet,modal=get('modal-revenda'),alerta=get('alert-revenda');
  if(!modal?.classList.contains('open'))return;
  const montagem=montarCadastroRevenda();
  if(montagem.erro){mostrarAlertaModal(alerta,'error',montagem.erro);get(montagem.campo).focus();return;}
  const current=()=>get('modal-revenda')===modal&&modal.classList.contains('open');
  const controls=[...modal.querySelectorAll('input,select,button')].map(el=>[el,el.disabled]);
  revendaSalvando=true;controls.forEach(([el])=>el.disabled=true);
  let submitted=false,focusId='r-ean';
  try{
    if(!(await conferirCodigoRevenda('r-ean')))return;
    if(!current())return;
    if(!(await conferirCodigoRevenda('r-fardo-ean'))){focusId='r-fardo-ean';return;}
    if(!current()||!temPermissao(PERMISSOES.EDITAR_ESTOQUE))return;
    submitted=true;
    const resp=await fetch('/api/produtos/revenda',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(montagem.dados)});
    if(!current())return;
    if(!resp.ok){if(resp.status>=500)throw Error('Resultado incerto');const msg=await safeReadErrorMessage(resp,'Erro ao cadastrar o produto.');if(current())mostrarAlertaModal(alerta,'error',msg);return;}
    const criado=await resp.json();if(!current())return;
    if(!criado||!Number.isInteger(Number(criado.produtoId))||Number(criado.produtoId)<=0||!Array.isArray(criado.variacoes))throw Error('Resposta inesperada');
    const resumo=criado.variacoes.map(v=>v.nome+' '+REVENDA_MOEDA.format(Number(v.precoVenda))).join(' · ');
    mostrarAlertaModal(alerta,'success',criado.nome+' cadastrado: '+resumo+'. Pode bipar o próximo.');
    limparFormRevenda();updateRevendaPreview();await carregarProdutos();
  }catch{
    if(current()){revendaUncertain=submitted;mostrarAlertaModal(alerta,'error',submitted?'Cadastro não confirmado. Feche e confira o estoque antes de repetir.':'Não foi possível conferir os códigos. Tente novamente.');}
  }finally{
    revendaSalvando=false;controls.forEach(([el,disabled])=>{if(el.isConnected)el.disabled=disabled;});
    if(current()){get('btn-salvar-revenda').disabled=revendaUncertain;if(!revendaUncertain)get(focusId).focus();}
  }
}

// Expose inline HTML handlers for direct navigation as well as SPA mounts.
if (/^\/estoque(?:\.html)?\/?$/.test(location.pathname)) {
  window.StockOSPages.estoque.activate();
}
})();
