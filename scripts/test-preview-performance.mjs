import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { readFile } from 'node:fs/promises';

execFileSync(process.execPath,['scripts/build-preview.mjs'],{stdio:'inherit'});
const pages = {
  'login.html': ['preview-mock','preview-design-review'],
  'dashboard.html': ['preview-mock','preview-design-review'],
  'estoque.html': ['preview-mock','preview-design-review'],
  'fornecedores.html': ['preview-mock','preview-design-review'],
  'notas.html': ['preview-mock','preview-notes','preview-design-review'],
  'caixas.html': ['preview-mock','preview-pdv','preview-design-review'],
  'cotacoes.html': ['preview-mock','preview-design-review'],
  'funcionarios.html': ['preview-mock','preview-design-review'],
  'logs.html': ['preview-mock','preview-design-review'],
  'cardapio.html': ['preview-mock','preview-cardapio','preview-design-review']
};
for (const [page, expected] of Object.entries(pages)) {
  const html = await readFile('dist-preview/'+page,'utf8');
  const actual = [...html.matchAll(/<script src="\/js\/(preview-[\w-]+)\.js"><\/script>/g)].map(m=>m[1]);
  assert.deepEqual(actual,expected,page+' should only load its preview mocks');
  assert.doesNotMatch(html,/<link rel="stylesheet" href="\/css\/[a-z-]+-refinement\.css">/,'Preview refinements load through the design toggle');
  assert.match(html,/<link rel="stylesheet" href="app.css">/,'Canonical styles remain');
}
const page = await readFile('dist-preview/dashboard.html','utf8');
assert.doesNotMatch(page,/chart\.umd\.min\.js/,'Chart.js is lazy');
const dashboard = await readFile('dist-preview/js/dashboard.js','utf8');
assert.match(dashboard,/carregarChartJsSobDemanda/);
assert.match(dashboard,/carregarRentabilidadeSobDemanda/);
const css = await readFile('dist-preview/css/stockos-design.bundle.css','utf8');
assert.equal((css.match(/@import/g)||[]).length,1);
assert.ok(css.length>150000,'CSS bundle is unexpectedly empty');
console.log('PASS performance: selective demo scripts, single CSS bundle, no duplicated refinements, lazy charts');