import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';

execFileSync(process.execPath,['scripts/build-preview.mjs'],{stdio:'inherit'});
const script = await readFile('dist-preview/js/spa-navigation.js','utf8');
const dashboardHtml = await readFile('dist-preview/dashboard.html','utf8');
const estoqueHtml = await readFile('dist-preview/estoque.html','utf8');
for (const page of ["dashboard","estoque","cardapio","fornecedores","notas","caixas","cotacoes","funcionarios","logs"]) {
  const html = await readFile('dist-preview/'+page+'.html','utf8');
  assert.ok(html.includes('/js/spa-navigation.js'),page+' missing SPA router');
}
for (const route of ["dashboard","estoque","cardapio","fornecedores","notas","caixas","cotacoes","funcionarios","logs"]) {
  assert.ok(script.includes(route+'.html'),route+' route missing');
}
assert.ok(script.includes('history.pushState'));
assert.ok(script.includes('window.addEventListener(\'popstate\''));
assert.ok(script.includes('document.addEventListener(\'click\''));
assert.ok(script.includes('credentials: \'same-origin\''));
assert.ok(script.includes('return fallback(href)'));
assert.ok(!script.includes('eval('));
assert.ok(!script.includes('new Function('));
assert.ok(!script.includes('innerHTML = await response.text()'));

const handlers = new Map();
const app = {
  querySelector: () => null
};
const doc = {
  readyState: 'complete',
  title: 'StockOS Dashboard',
  querySelector: selector => selector === '.app > .main.page' ? app :
    selector === '.app > .sidebar' || selector === '#nav-items' ? {} : null,
  body: {children:[],classList:{contains(){return true;}}},
  addEventListener(event,fn){handlers.set(event,fn);}
};
const location = {href:'https://example.com/dashboard.html',origin:'https://example.com'};
const sandbox = {document:doc,location,window:{},URL,console,Map,Promise};
vm.runInNewContext(script,sandbox);
assert.equal(sandbox.window.StockOSSPA,undefined,'No router when header missing');
// Full runtime navigation requires a browser DOM. This test enforces script
// syntax, preview integration, route allowlist and safe fallback contract.
execFileSync(process.execPath,['--check','dist-preview/js/spa-navigation.js'],{stdio:'inherit'});
execFileSync(process.execPath,['--check','dist-preview/js/dashboard.js'],{stdio:'inherit'});
execFileSync(process.execPath,['--check','dist-preview/js/estoque.js'],{stdio:'inherit'});
for (const route of ["cardapio","fornecedores","notas","caixas","cotacoes","funcionarios","logs"]) {
  const js = await readFile('dist-preview/js/'+route+'.js','utf8');
  assert.match(js,new RegExp('StockOSPages\\.'+route));
  assert.match(js,/activate\(\)/);
  assert.match(js,/mount:/);
  execFileSync(process.execPath,['--check','dist-preview/js/'+route+'.js'],{stdio:'inherit'});
}
const pdv = await readFile('dist-preview/js/pdv.js','utf8');
assert.match(pdv,/window\.StockOSPDV/);
execFileSync(process.execPath,['--check','dist-preview/js/pdv.js'],{stdio:'inherit'});
const demo = await readFile('dist-preview/js/preview-mock.js','utf8');
for (const route of ["dashboard","estoque","cardapio","fornecedores","notas","caixas","cotacoes","funcionarios","logs"]) assert.match(demo,new RegExp(route));
assert.match(await readFile('dist-preview/js/preview-notes.js','utf8'),/StockOSPreviewHooks\.notas/);
assert.match(await readFile('dist-preview/js/preview-pdv.js','utf8'),/StockOSPreviewHooks\.caixas/);

// Simulate all seven legacy controllers in one JS realm. They must not collide
// on top-level lexical names, and each route must reactivate its own actions.
const ctx = {
  window: { StockOSPages: {} },
  document: { addEventListener() {} },
  location: { pathname: '/dashboard.html' },
  console,
  temPermissao: () => true,
  PERMISSOES: {VER_VENDAS: 'VER_VENDAS', VER_FINANCAS: 'VER_FINANCAS'},
  setTimeout,
  clearTimeout
};
vm.createContext(ctx);
for (const route of ["cardapio","fornecedores","notas","caixas","cotacoes","funcionarios","logs"]) {
  vm.runInContext(await readFile('dist-preview/js/'+route+'.js','utf8'),ctx,{filename:route+'.js'});
  assert.equal(typeof ctx.window.StockOSPages[route]?.mount,'function',route+' missing mount');
  assert.equal(typeof ctx.window.StockOSPages[route]?.activate,'function',route+' missing activation');
}
ctx.window.StockOSPages.fornecedores.activate();
const supplierTable = ctx.window.renderTabela;
ctx.window.StockOSPages.funcionarios.activate();
assert.notEqual(ctx.window.renderTabela,supplierTable,'Duplicate table renderer was not switched');
ctx.window.StockOSPages.fornecedores.activate();
assert.equal(ctx.window.renderTabela,supplierTable,'Supplier handlers failed to restore');
vm.runInContext(await readFile('dist-preview/js/pdv.js','utf8'),ctx,{filename:'pdv.js'});
assert.equal(typeof ctx.window.StockOSPDV.activate,'function');
ctx.window.StockOSPDV.activate();
assert.equal(typeof ctx.window.finalizarVendaPDV,'function');

console.log('PASS SPA: 9 routes, modular controllers, POS bridge, mocks, fallback and syntax');
