import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';

const root = new URL('../src/main/resources/static/', import.meta.url);
const shared = await readFile(new URL('js/shared.js', root), 'utf8');
const dashboard = await readFile(new URL('js/dashboard.js', root), 'utf8');
const css = await readFile(new URL('app.css', root), 'utf8');
new vm.Script(shared);
new vm.Script(dashboard);

function extract(source, start, end) {
  const i = source.indexOf(start), j = source.indexOf(end, i + 1);
  assert.ok(i >= 0 && j > i, 'Expected source contract is missing: ' + start);
  return source.slice(i, j);
}
const navCode = extract(shared, 'const NAV_ITENS = [', 'const PERMISSOES_POR_PAGINA =');
const names = ['VER_ESTOQUE','EDITAR_ESTOQUE','VER_COMPRAS','VER_VENDAS','VER_FINANCAS','GERENCIAR_FUNCIONARIOS','VER_LOGS'];
const permissions = Object.fromEntries(names.map(x => [x, x]));

function render({perms=names,pin=false,active='dashboard',collapsed=false,original=false}={}) {
  const nav = {innerHTML:'',querySelectorAll:()=>[]};
  const document = {getElementById:()=>nav,querySelector:()=>({classList:{contains:()=>collapsed}})};
  vm.runInNewContext(navCode+'\nrenderSidebar();', {
    PERMISSOES:permissions,document,window:{StockOsDesignReview:{mode:original?'original':'novo'}},
    usuario:{viaPin:pin},paginaAtual:()=>active,
    temAlgumaPermissao:arr=>arr.some(p=>perms.includes(p)),iconMarkup:()=>'<svg></svg>'
  });
  return nav.innerHTML;
}
const admin = render({active:'estoque'});
assert.equal((admin.match(/href=/g)||[]).length,10);
assert.match(admin,/aria-current="page"/);
assert.match(admin,/data-nav-group="Estoque e produtos" open/);
const restricted = render({perms:['VER_ESTOQUE']});
assert.match(restricted,/\/estoque\.html/);
assert.doesNotMatch(restricted,/\/cardapio\.html|\/caixas\.html|\/logs\.html/);
const pin = render({pin:true});
assert.equal((pin.match(/href=/g)||[]).length,2);
assert.match(pin,/\/comandas\.html/);
assert.match(pin,/\/caixas\.html/);
const collapsed = render({collapsed:true});
assert.doesNotMatch(collapsed,/<details/);
assert.equal((collapsed.match(/href=/g)||[]).length,10);
assert.match(collapsed,/aria-label="Estoque"/);
const original = render({original:true});
assert.doesNotMatch(original,/<details/);
assert.equal((original.match(/href=/g)||[]).length,10);

class Element {
  constructor(tag) {
    this.tag=tag; this.children=[]; this.attrs={}; this.className='';
    this.style={setProperty:(k,v)=>{this.style[k]=v}};
  }
  append(...nodes) { for(const n of nodes) this.appendChild(n); }
  appendChild(n) { if(n.parent)n.remove(); n.parent=this; this.children.push(n); return n; }
  prepend(n) { if(n.parent)n.remove(); n.parent=this; this.children.unshift(n); }
  remove() { if(this.parent){this.parent.children=this.parent.children.filter(x=>x!==this);this.parent=null;} }
  before(n) { n.parent=this.parent; this.parent.children.splice(this.parent.children.indexOf(this),0,n); }
  closest(sel) { let x=this; while(x){if('.'+x.className===sel)return x;x=x.parent;}return null; }
  querySelector(sel) {
    for(const n of this.children){if('.'+n.className===sel)return n;const v=n.querySelector(sel);if(v)return v;}
    return null;
  }
  setAttribute(k,v) { this.attrs[k]=v; }
}
const dashCode = extract(dashboard,'function renderDashboardOverview(','function renderAcoesOperacionais(');
function makeDash(allow=true,enabled=true) {
  const parent=new Element('main'),grid=new Element('div');
  grid.className='summary-grid'; parent.appendChild(grid);
  const document={getElementById:()=>grid,querySelector:()=>enabled?parent:null,createElement:t=>new Element(t)};
  const fn=vm.runInNewContext(dashCode+'\nrenderDashboardOverview;',{
    document,PODE_VER_FINANCEIRO_DASH:allow,formatarMoedaBR:n=>'R$ '+n.toFixed(2)
  });
  return {parent,grid,fn};
}
const x=makeDash();
x.fn([{numeroCaixa:1,totalVendas:120,status:'ABERTO'},{numeroCaixa:2,totalVendas:60,status:'ABERTO'},{numeroCaixa:3,totalVendas:null,status:'ABERTO'},{numeroCaixa:4,totalVendas:-10,status:'ABERTO'}],{totalVendas:180},true);
const panel=x.parent.querySelector('.stockos-sales-panel'),chart=panel.querySelector('.stockos-sales-chart');
assert.equal(chart.children.length,2);
assert.equal(chart.children[1].querySelector('.stockos-sales-bar').style['--bar-height'],'50%');
assert.equal(panel.querySelector('.stockos-sales-total').textContent,'R$ 180.00');
x.fn([],null,false);
assert.equal(x.parent.children.length,1);
assert.equal(x.parent.children[0].children.length,2);
assert.match(x.parent.querySelector('.stockos-sales-empty').textContent,/Não foi possível/);
assert.equal(x.parent.querySelector('.stockos-sales-total').textContent,'—');
const noPerm=makeDash(false);
noPerm.fn([{totalVendas:300}],{totalVendas:300},true);
assert.equal(noPerm.parent.querySelector('.stockos-sales-panel'),null);
const old=makeDash(true,false);
old.fn([],{totalVendas:4},true);
assert.equal(old.parent.children[0],old.grid);
assert.match(css,/\.app\.stockos-shell-v2\.is-collapsed/);
assert.match(css,/prefers-reduced-motion:reduce/);
console.log('PASS shell redesign: permission-aware menu, PIN, collapsed and original modes, financial data and unavailable state.');
