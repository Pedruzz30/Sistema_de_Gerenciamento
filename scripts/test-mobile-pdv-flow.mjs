import assert from 'node:assert/strict';
import vm from 'node:vm';
import {readFile} from 'node:fs/promises';
import {execFileSync} from 'node:child_process';

const real=await readFile('src/main/resources/static/js/pdv.js','utf8');
const demo=await readFile('preview-source/js/preview-pdv.js','utf8');
const html=await readFile('src/main/resources/static/caixas.html','utf8');
const css=await readFile('src/main/resources/static/css/pdv.css','utf8');
for(const file of ['src/main/resources/static/js/pdv.js','preview-source/js/preview-pdv.js'])
  execFileSync(process.execPath,['--check',file],{stdio:'inherit'});
for(const id of ['pdv-mobile-produtos','pdv-mobile-pedido','pdv-mobile-cart-badge'])
  assert.ok(html.includes('id="'+id+'"'),'Missing mobile navigation control '+id);
assert.match(css,/data-mobile-panel="pedido"/);
assert.match(css,/pdv-mobile-navigation__button:focus-visible/);
assert.match(real,/mostrarPainelPDVMobile\('produtos'\)/);
assert.match(real,/mobileBadge\.textContent/);
assert.match(demo,/stockos-preview-pdv__navigation/);
assert.match(demo,/stockos-preview-pdv__catalog/);
assert.match(demo,/stockos-preview-pdv__cart/);
assert.match(demo,/updateQuantity\(c\.id,-1\)/);
assert.match(demo,/updateQuantity\(c\.id,1\)/);
assert.match(demo,/if\(!pendingKey\)pendingKey=/);
assert.match(demo,/if\(processing\|\|submitted\)return/);
assert.match(demo,/Nenhum pagamento foi realizado/);

const buttons={};
for(const id of ['pdv-mobile-produtos','pdv-mobile-pedido']){
  buttons[id]={attrs:{},classList:{
    values:new Set(),
    toggle(cls,active){if(active)this.values.add(cls);else this.values.delete(cls);}
  },setAttribute(name,value){this.attrs[name]=value;}};
}
const shell={dataset:{}};
const context={
  window:{StockOSPages:{}}, location:{pathname:'/nao-ativar'},
  document:{getElementById:id=>id==='pdv-shell'?shell:buttons[id],addEventListener(){}},
  console
};
vm.createContext(context);
vm.runInContext(real,context,{filename:'pdv.js'});
context.window.StockOSPDV.activate();
context.window.mostrarPainelPDVMobile('pedido');
assert.equal(shell.dataset.mobilePanel,'pedido');
assert.equal(buttons['pdv-mobile-pedido'].attrs['aria-pressed'],'true');
assert.equal(buttons['pdv-mobile-produtos'].attrs['aria-pressed'],'false');
context.window.mostrarPainelPDVMobile('produtos');
assert.equal(shell.dataset.mobilePanel,'produtos');
console.log('PASS PDV mobile: panels, item controls, safe demo retry and accessible navigation');
