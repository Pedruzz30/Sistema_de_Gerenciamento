import assert from 'node:assert/strict';
import vm from 'node:vm';
import { execFileSync } from 'node:child_process';
import { readFile } from 'node:fs/promises';

const shared = await readFile('src/main/resources/static/js/shared.js', 'utf8');
const mock = await readFile('preview-source/js/preview-mock.js', 'utf8');
const build = await readFile('scripts/build-preview.mjs', 'utf8');
const originalLogin = await readFile('src/main/resources/static/login.html', 'utf8');
execFileSync(process.execPath, ['--check', 'src/main/resources/static/js/shared.js']);
execFileSync(process.execPath, ['--check', 'preview-source/js/preview-mock.js']);
execFileSync(process.execPath, ['--check', 'scripts/build-preview.mjs']);
execFileSync(process.execPath, ['scripts/build-preview.mjs']);
const previewShared = await readFile('dist-preview/js/shared.js', 'utf8');
assert.match(previewShared,/const usuario = lerUsuarioPersistido\(\) \|\| window\.StockosPreviewUser/);
assert.doesNotMatch(shared,/const usuario = lerUsuarioPersistido\(\) \|\| window\.StockosPreviewUser/);

const demoUser = {
  ru:1, nome:'Admin', sobrenome:'Demo', nomeClasse:'SUPERIOR',
  perfil:'ADMIN', ativo:true, permissoes:['VER_ESTOQUE']
};
function environment(preview) {
  const redirects = [];
  const deniedStorage = {
    getItem() {throw new Error('Storage disabled');},
    setItem() {throw new Error('Storage disabled');},
    removeItem() {throw new Error('Storage disabled');}
  };
  const win = {
    STOCKOS_PREVIEW:preview,
    StockosPreviewUser:demoUser,
    localStorage:deniedStorage,
    sessionStorage:deniedStorage,
    fetch() {return Promise.resolve();},
    location:{
      pathname:'/dashboard.html',
      _href:'/dashboard.html',
      get href() {return this._href;},
      set href(destination) {redirects.push(destination);this._href=destination;},
      replace(destination) {redirects.push(destination);}
    },
    innerWidth:930
  };
  const context = vm.createContext({
    window:win, document:{addEventListener() {}},
    console, setTimeout, clearTimeout, Request, Headers
  });
  vm.runInContext(preview ? previewShared : shared,context,{filename:'shared.js'});
  return {context,redirects};
}

const demo = environment(true);
assert.equal(vm.runInContext('usuario.ru',demo.context),1);
assert.deepEqual(demo.redirects,[],'Preview with blocked storage must not loop to login');
assert.equal(vm.runInContext('isShellMobile()',demo.context),true,
  '930px viewport needs functioning sidebar toggle');
assert.equal(vm.runInContext('temPermissao("VER_ESTOQUE")',demo.context),true);
const real = environment(false);
assert.deepEqual(real.redirects,['/login.html'],
  'Only preview can use demo identity; real app still requires authentication');

const index = await readFile('dist-preview/index.html','utf8');
const login = await readFile('dist-preview/login.html','utf8');
const dashboard = await readFile('dist-preview/dashboard.html','utf8');
assert.match(index,/url=\/dashboard\.html/);
assert.match(index,/viewport/);
assert.match(login,/Abrir demonstração sem login/);
assert.doesNotMatch(originalLogin,/Abrir demonstração sem login/);
assert.match(dashboard,/window\.STOCKOS_PREVIEW = true/);
assert.match(mock,/window\.StockosPreviewUser = user/);
assert.match(mock,/left:12px;bottom:calc/);
assert.match(build,/page === 'login\.html'/);
console.log('PASS mobile: no-storage demo session, isolated auth, 930px sidebar, direct dashboard entry');