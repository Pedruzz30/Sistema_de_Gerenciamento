import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {execFileSync} from 'node:child_process';

const paths=['src/main/resources/static/js/shared.js','src/main/resources/static/js/quick-actions.js'];
for(const path of paths) execFileSync(process.execPath,['--check',path],{stdio:'inherit'});
const shared=await readFile(paths[0],'utf8');
const css=await readFile('src/main/resources/static/css/quick-actions.css','utf8');
assert.match(shared,/return window\.innerWidth <= 980/);
assert.match(shared,/if \(overlay\.parentElement !== app\) app\.appendChild\(overlay\)/);
assert.doesNotMatch(shared,/document\.body\.appendChild\(overlay\)/);
assert.match(shared,/closeSidebarButton\.addEventListener\('click', fecharSidebar\)/);
assert.match(shared,/window\.StockosAuth && window\.StockosAuth\.isSupabase\(\)/);
assert.match(shared,/script\.src = '\/js\/quick-actions\.js'/);
assert.match(css,/\.app > \.sidebar-overlay \{ z-index: 39; \}/);
assert.match(css,/\.app > \.sidebar \{ z-index: 50; \}/);
assert.match(css,/\.sidebar-close/);
assert.match(css,/\.stockos-quick-mobile/);
console.log('PASS mobile shell: 980px breakpoint, overlay ordering, accessible close and shortcut isolation');
