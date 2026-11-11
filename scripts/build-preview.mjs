// Produces a static isolated demo; never deploys Java, database config or SaaS secrets.
import { cp, mkdir, readFile, writeFile, rm } from 'node:fs/promises';
import { resolve, join } from 'node:path';
const root = resolve('src/main/resources/static');
const out = resolve('dist-preview');
await rm(out, { recursive:true, force:true });
await mkdir(out, {recursive:true});
await cp(root, out, {recursive:true});
// The real Java frontend is the single source of truth. Mock is preview-only.
await cp(resolve('preview-source/js/preview-beverages.js'),join(out,'js/preview-beverages.js'));
await cp(resolve('preview-source/js/preview-scale.js'),join(out,'js/preview-scale.js'));
await cp(resolve('preview-source/js/preview-mock.js'),join(out,'js/preview-mock.js'));
// Nunca alterar o shared.js do Java. Somente o artefato mock usa
// identidade fictícia quando o armazenamento da sessão está indisponível.
const sharedPath=join(out,'js/shared.js');
const canonicalShared=await readFile(sharedPath,'utf8');
const loginMarker='const usuario = lerUsuarioPersistido();';
if(!canonicalShared.includes(loginMarker)) throw Error('Contrato do login canônico mudou; revisar build do mock.');
await writeFile(sharedPath,canonicalShared.replace(
  loginMarker,'const usuario = lerUsuarioPersistido() || window.StockosPreviewUser;'
));
await cp(resolve('preview-source/js/preview-notes.js'),join(out,'js/preview-notes.js'));
await cp(resolve('preview-source/js/preview-pdv.js'),join(out,'js/preview-pdv.js'));
await cp(resolve('preview-source/comandas-demo.html'),join(out,'comandas-demo.html'));
await cp(resolve('preview-source/js/comandas-demo-model.js'),join(out,'js/comandas-demo-model.js'));
await cp(resolve('preview-source/js/comandas-demo.js'),join(out,'js/comandas-demo.js'));
await cp(resolve('preview-source/css/comandas-demo.css'),join(out,'css/comandas-demo.css'));
await cp(resolve('preview-source/js/preview-cardapio.js'),join(out,'js/preview-cardapio.js'));
await cp(resolve('preview-source/js/preview-design-review.js'),join(out,'js/preview-design-review.js'));
await mkdir(join(out,'css'),{recursive:true});
await cp(resolve('preview-source/css/palette-original.css'),join(out,'css/palette-original.css'));
// One combined CSS request instead of 21 separate stylesheets.
// Import order is preserved to keep the approved visual design unchanged.
const designCssFiles = ["tokens","reset","typography","global","layout","sidebar","topbar","buttons","forms","table","badges","sheet","modal","toolbar","kpi","pages","estoque","cardapio","pdv","react-app","compat"];
const cssParts = await Promise.all(designCssFiles.map(name => readFile(join(out,'css',name+'.css'),'utf8')));
const isFontImport = line => /^\s*@import\s+url\(["']https:\/\/fonts\.googleapis\.com\//.test(line);
const fontImport = cssParts[0].split('\n').find(isFontImport);
if (!fontImport) throw new Error('StockOS: missing font import in CSS tokens');
const designBundle = [
  fontImport,
  ...designCssFiles.map((name,index) =>
    '\n/* StockOS CSS: '+name+'.css */\n' +
    cssParts[index].split('\n').filter(line=>!isFontImport(line)).join('\n')
  )
].join('\n');
await writeFile(join(out,'css/stockos-design.bundle.css'),designBundle);

// Dashboard and inventory both use their canonical source files in src/main/resources/static.
// Preview-only mock modules remain in preview-source/js/.

// SaaS, PDV legacy and miscellaneous HTML are not part of the public demo.
const allowed = new Set(['login.html','dashboard.html','estoque.html','fornecedores.html','notas.html','caixas.html','cotacoes.html','funcionarios.html','logs.html','cardapio.html','comandas-demo.html']);
const { readdir } = await import('node:fs/promises');
for (const entry of await readdir(out)) {
  if (entry.endsWith('.html') && !allowed.has(entry)) await rm(join(out,entry),{force:true});
}
// O menu do sistema tem "Comandas" (/comandas.html), que precisa do servidor Java.
// Na demonstração, esse endereço leva às comandas demonstrativas, em vez de "não encontrada".
await writeFile(join(out,'comandas.html'),
  '<!doctype html><html lang="pt-BR"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">' +
  '<meta http-equiv="refresh" content="0;url=/comandas-demo.html"><title>StockOS · Comandas (demonstração)</title></head>' +
  '<body><p>As comandas desta demonstração ficam em <a href="/comandas-demo.html">Comandas demonstrativas</a>.</p>' +
  '<script>location.replace("/comandas-demo.html")</script></body></html>\n');
const pages = ['login.html','dashboard.html','estoque.html','fornecedores.html','notas.html','caixas.html','cotacoes.html','funcionarios.html','logs.html','cardapio.html'];
// Only initialize browser-only mock extensions on pages that use them.
const previewScriptsFor = page => [
  'preview-scale',
  'preview-beverages',
  'preview-mock',
  ...(page==='notas.html' ? ['preview-notes'] : []),
  ...(page==='caixas.html' ? ['preview-pdv'] : []),
  ...(page==='cardapio.html' ? ['preview-cardapio'] : []),
  'preview-design-review'
];
for (const page of pages) {
  const p=join(out,page);
  let html=await readFile(p,'utf8');
  if(!html.includes('</head>')) throw new Error('HTML inválido: '+page);
  // The original source includes the refinement. In the preview it must
  // be injected only when design=novo, exactly once, after the palette.
  html=html.replace(/<link\s+rel="stylesheet"\s+href="\/css\/[a-z-]+-refinement\.css"\s*\/?>/g,'');
  const scripts = previewScriptsFor(page)
    .map(name=>'<script src="/js/'+name+'.js"></script>').join('\n');
  html=html.replace('</head>', '<script>window.STOCKOS_PREVIEW = true;</script>\n'+scripts+'\n</head>');
  if (page === 'dashboard.html' || page === 'caixas.html') {
    // A shortcut exists only in the published static demo, never in the Java app.
    const shortcut = '<div class="comandas-demo-entry" style="display:flex;flex-wrap:wrap;align-items:center;justify-content:space-between;gap:10px;margin:12px 0 18px;padding:13px;border:1px solid var(--rule,#53667a);border-radius:12px;background:var(--paper-raised,#172638)">' +
      '<span style="font-size:13px;color:var(--ink,#f4f7fa)"><strong>Comandas demonstrativas</strong> · Registre consumos sem movimentar valores reais.</span>' +
      '<a href="/comandas-demo.html" style="min-height:44px;display:inline-flex;align-items:center;padding:10px 15px;border-radius:9px;background:var(--accent,#dca062);color:#211607;text-decoration:none;font-weight:750;font-size:13px">Experimentar comandas</a></div>';
    if (!html.includes('<div class="main-body content">')) throw new Error('Missing main content section on '+page);
    html=html.replace('<div class="main-body content">','<div class="main-body content">'+shortcut);
  }
  if(page==='login.html'){
    const entry='<div class="stockos-demo-entry" style="padding:14px;text-align:center">' +
      '<a href="/dashboard.html" style="display:inline-flex;align-items:center;justify-content:center;min-height:48px;padding:10px 16px;border:1px solid var(--rule,#5c7087);border-radius:10px;text-decoration:none;color:var(--ink,#f4f7fa);background:var(--paper-raised,#142236);font-weight:700">Abrir demonstração sem login</a>' +
      '<p style="font-size:12px;margin:8px 0 0">Dados fictícios. Nenhuma conta real é necessária.</p></div>';
    html=html.replace('</form>','</form>'+entry);
  }
  await writeFile(p,html);
}
await writeFile(join(out,'_redirects'), [
  '/login /login.html 200',
  '/dashboard /dashboard.html 200',
  '/estoque /estoque.html 200',
  '/caixas /caixas.html 200',
  '/fornecedores /fornecedores.html 200',
  '/notas /notas.html 200',
  '/cotacoes /cotacoes.html 200',
  '/funcionarios /funcionarios.html 200',
  '/logs /logs.html 200',
  '/cardapio /cardapio.html 200',
  '/comandas-demo /comandas-demo.html 200',
  '/comandas /comandas-demo.html 200'
].join('\n')+'\n');
await writeFile(join(out,'index.html'), '<!doctype html><html lang="pt-BR"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><meta http-equiv="refresh" content="0;url=/dashboard.html"><title>StockOS · Demo</title></head><body><a href="/dashboard.html">Abrir StockOS Demo</a></body></html>\n');
await writeFile(join(out,'_headers'), '/*\n  X-Robots-Tag: noindex, nofollow\n  X-Content-Type-Options: nosniff\n  Referrer-Policy: no-referrer\n  Content-Security-Policy: connect-src \'self\'; frame-ancestors \'none\'\n');
await writeFile(join(out,'robots.txt'), 'User-agent: *\nDisallow: /\n');
console.log('StockOS mock preview pronto em dist-preview/');
