import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {execFileSync} from 'node:child_process';

execFileSync(process.execPath,['scripts/build-preview.mjs'],{stdio:'inherit'});
for(const path of [
  'src/main/resources/static/js/shared.js',
  'dist-preview/js/shared.js',
  'dist-preview/js/preview-design-review.js',
  'dist-preview/js/preview-mock.js',
]) execFileSync(process.execPath,['--check',path],{stdio:'inherit'});
const shell=await readFile('src/main/resources/static/js/shared.js','utf8');
const quickCss=await readFile('src/main/resources/static/css/quick-actions.css','utf8');
const preview=await readFile('dist-preview/js/shared.js','utf8');
const design=await readFile('dist-preview/js/preview-design-review.js','utf8');
const banner=await readFile('dist-preview/js/preview-mock.js','utf8');
assert.match(shell,/if \(overlay\.parentElement !== app\) app\.appendChild\(overlay\)/,
  'Navigation overlay must share app stacking context with sidebar');
assert.doesNotMatch(shell,/document\.body\.appendChild\(overlay\)/,
  'Body overlay would cover the menu when app creates a stacking context');
assert.match(shell,/closeSidebarButton\.addEventListener\('click', fecharSidebar\)/);
assert.match(shell,/return window\.innerWidth <= 980/);
assert.match(quickCss,/\.app > \.sidebar-overlay \{ z-index: 39; \}/);
assert.match(quickCss,/\.app > \.sidebar \{ z-index: 50; \}/);
assert.match(quickCss,/\.app > \.sidebar\.open \{ display: flex; \}/);
assert.match(quickCss,/body\.sidebar-open \[data-stockos-design-toggle\]/);
assert.match(design,/control\.dataset\.stockosDesignToggle = 'true'/);
assert.match(banner,/banner\.className = 'stockos-preview-banner'/);
assert.match(preview,/overlay\.parentElement !== app/);
console.log('PASS mobile navigation: overlay below sidebar, close button, mobile breakpoint, floating controls');
