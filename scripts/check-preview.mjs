import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { readFile, access } from 'node:fs/promises';
import { join } from 'node:path';
execFileSync(process.execPath,['scripts/build-preview.mjs'],{stdio:'inherit'});
for (const p of ['login.html','dashboard.html','estoque.html','cardapio.html','caixas.html','js/preview-mock.js','js/preview-notes.js','js/preview-pdv.js','_redirects','_headers']) {
  await access(join('dist-preview',p));
}
const html=await readFile('dist-preview/dashboard.html','utf8');
const original=await readFile('src/main/resources/static/dashboard.html','utf8');
const canonicalCss=await readFile('src/main/resources/static/app.css','utf8');
const previewCss=await readFile('dist-preview/app.css','utf8');
assert.equal(canonicalCss,previewCss,'CSS must use canonical frontend');
assert.ok(html.includes('/js/preview-mock.js'),'Mock injection absent');
assert.ok(html.includes('/js/preview-notes.js'),'Invoice demo injection absent');
assert.ok(html.includes('/js/preview-pdv.js'),'PDV demo injection absent');
assert.ok(!original.includes('/js/preview-mock.js'),'Mock leaked into real Java frontend');
assert.ok(!original.includes('/js/preview-notes.js'),'Invoice mock leaked into Java frontend');
assert.ok(!original.includes('/js/preview-pdv.js'),'PDV mock leaked into Java frontend');
assert.ok(!html.includes('Turno ativo · 26 abr 2026'),'Stale dashboard date');
const routes=await readFile('dist-preview/_redirects','utf8');
assert.match(routes,/^\/dashboard \/dashboard.html 200$/m);
assert.match(routes,/^\/estoque \/estoque.html 200$/m);
for(const f of ['saas-admin.html','saas-comandas.html','application.properties']) {
  await assert.rejects(access(join('dist-preview',f)));
}
const mock=await readFile('dist-preview/js/preview-mock.js','utf8');
assert.ok(mock.includes('DEMONSTRAÇÃO · dados fictícios'));
assert.ok(mock.includes('Função indisponível nesta demonstração'));
for(const file of ['scripts/build-preview.mjs','dist-preview/js/preview-mock.js','dist-preview/js/preview-notes.js','dist-preview/js/preview-pdv.js','dist-preview/js/dashboard.js','dist-preview/js/estoque.js']) {
  execFileSync(process.execPath,['--check',file],{stdio:'inherit'});
}
console.log('PASS: preview isolado, navegação, fontes canônicas e JS');
