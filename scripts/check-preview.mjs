import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { readFile, access } from 'node:fs/promises';
import { join } from 'node:path';
execFileSync(process.execPath,['scripts/build-preview.mjs'],{stdio:'inherit'});
for (const p of ['login.html','dashboard.html','estoque.html','cardapio.html','caixas.html','js/preview-mock.js','js/preview-notes.js','js/preview-pdv.js','js/preview-cardapio.js','js/preview-design-review.js','css/palette-original.css','_redirects','_headers']) {
  await access(join('dist-preview',p));
}
const html=await readFile('dist-preview/dashboard.html','utf8');
const original=await readFile('src/main/resources/static/dashboard.html','utf8');
const canonicalCss=await readFile('src/main/resources/static/app.css','utf8');
const previewCss=await readFile('dist-preview/app.css','utf8');
assert.equal(canonicalCss,previewCss,'CSS must use canonical frontend');
assert.equal(await readFile('src/main/resources/static/js/dashboard.js','utf8'),
  await readFile('dist-preview/js/dashboard.js','utf8'),
  'Dashboard JS must come from canonical frontend');
assert.equal(original.slice(original.indexOf('<body')),
  html.slice(html.indexOf('<body')),
  'Dashboard HTML body must come from canonical frontend');
assert.ok(html.includes('/js/preview-mock.js'),'Mock injection absent');
assert.ok(!html.includes('/js/preview-notes.js'),'Notes demo should not run on dashboard');
assert.ok(!html.includes('/js/preview-pdv.js'),'PDV demo should not run on dashboard');
assert.ok(!html.includes('/js/preview-cardapio.js'),'Cardápio demo should not run on dashboard');
for (const [page, mock] of [
  ['notas.html','preview-notes.js'],
  ['caixas.html','preview-pdv.js'],
  ['cardapio.html','preview-cardapio.js']
]) assert.match(await readFile('dist-preview/'+page,'utf8'),new RegExp('/js/'+mock.replace('.','\\.') ));
assert.doesNotMatch(html,/chart\.umd\.min\.js/,'Chart.js must load on demand');
assert.ok(html.includes('/js/preview-design-review.js'),'Design review injection absent');
assert.ok(!html.includes('/css/dashboard-refinement.css'),'Preview should only apply refinements in new design');
assert.ok((await readFile('dist-preview/css/stockos-design.bundle.css','utf8')).includes('/* StockOS CSS: compat.css */'));
assert.ok(!original.includes('/js/preview-mock.js'),'Mock leaked into real Java frontend');
assert.ok(!original.includes('/js/preview-notes.js'),'Invoice mock leaked into Java frontend');
assert.ok(!original.includes('/js/preview-pdv.js'),'PDV mock leaked into Java frontend');
assert.ok(!original.includes('/js/preview-cardapio.js'),'Menu mock leaked into Java frontend');
assert.ok(!original.includes('/js/preview-design-review.js'),'Design preview leaked into Java frontend');
const palette=await readFile('dist-preview/css/palette-original.css','utf8');
assert.ok(palette.includes('--paper: oklch('));
assert.ok(palette.includes('--accent: oklch('));
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
for(const file of ['scripts/build-preview.mjs','dist-preview/js/preview-mock.js','dist-preview/js/preview-notes.js','dist-preview/js/preview-pdv.js','dist-preview/js/preview-cardapio.js','dist-preview/js/preview-design-review.js','dist-preview/js/dashboard.js','dist-preview/js/estoque.js']) {
  execFileSync(process.execPath,['--check',file],{stdio:'inherit'});
}
console.log('PASS: preview isolado, navegação, fontes canônicas e JS');
