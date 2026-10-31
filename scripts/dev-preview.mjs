// Local-only StockOS preview: serves the same mock build used by Netlify.
// No Java, credentials, database or third-party dependencies are required.
import { watch } from 'node:fs';
import { readFile, stat } from 'node:fs/promises';
import { createServer } from 'node:http';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { resolve, join, extname, sep } from 'node:path';

const run = promisify(execFile);
const root = resolve('dist-preview');
const onlyWatch = process.argv.includes('--watch-only');
const port = Number(process.env.PORT ?? 5173);
if (!Number.isInteger(port) || port < 0 || port > 65535) {
  throw new Error('PORT deve ser um inteiro entre 0 e 65535.');
}

const aliases = new Set(['login','dashboard','estoque','caixas','fornecedores',
  'notas','cotacoes','funcionarios','logs','cardapio']);
const mime = {
  '.html':'text/html; charset=utf-8', '.js':'text/javascript; charset=utf-8',
  '.css':'text/css; charset=utf-8', '.json':'application/json; charset=utf-8',
  '.svg':'image/svg+xml', '.png':'image/png', '.jpg':'image/jpeg', '.jpeg':'image/jpeg',
  '.gif':'image/gif', '.webp':'image/webp', '.ico':'image/x-icon',
  '.woff':'font/woff', '.woff2':'font/woff2', '.ttf':'font/ttf', '.txt':'text/plain; charset=utf-8'
};
const listeners = new Set();
const reloadClient = '<script>\n' +
  '(() => { const source = new EventSource("/__stockos_local_events"); ' +
  'source.addEventListener("reload", () => location.reload()); })();\n' +
  '</script>';
function broadcast() {
  for (const client of listeners) {
    try { client.write('event: reload\\ndata: updated\\n\\n'.replaceAll('\\n', '\n')); }
    catch { listeners.delete(client); }
  }
}

let busy = false;
let pending = false;
let debounce;
async function build() {
  const started = Date.now();
  const { stdout, stderr } = await run(process.execPath, ['scripts/build-preview.mjs'], {
    cwd: process.cwd(), maxBuffer: 1024 * 1024
  });
  if (stdout.trim()) process.stdout.write(stdout);
  if (stderr.trim()) process.stderr.write(stderr);
  console.log('[StockOS local] Preview gerado em ' + (Date.now() - started) + 'ms');
}
async function rebuild() {
  pending = true;
  if (busy) return;
  busy = true;
  try {
    while (pending) {
      pending = false;
      try {
        await build();
        broadcast();
      } catch (error) {
        console.error('[StockOS local] Falha no build; corrija os arquivos e salve novamente:', error.message);
      }
    }
  } finally {
    busy = false;
  }
}
function schedule() {
  clearTimeout(debounce);
  debounce = setTimeout(() => { void rebuild(); }, 180);
}
await build();

const roots = ['src/main/resources/static', 'preview-source'];
const watchers = roots.map(path => watch(resolve(path), { recursive: true }, schedule));
watchers.push(watch(resolve('scripts'), (eventType, filename) => {
  if (!filename || String(filename) === 'build-preview.mjs') schedule();
}));
watchers.forEach(w => w.on('error', e => console.error('[StockOS local] Falha ao observar arquivos:', e)));
console.log('[StockOS local] Monitorando alterações do frontend e dos mocks.');

if (onlyWatch) {
  console.log('[StockOS local] Modo Live Server: abra dist-preview/login.html pela extensão Go Live.');
} else {
  const server = createServer(async (req, res) => {
    res.setHeader('Cache-Control', 'no-store');
    if (req.method !== 'GET' && req.method !== 'HEAD') {
      res.writeHead(405); res.end('Método indisponível no preview local.'); return;
    }
    let path;
    try { path = decodeURIComponent(new URL(req.url, 'http://localhost').pathname); }
    catch { res.writeHead(400); res.end('URL inválida'); return; }
    if (path === '/__stockos_local_events') {
      res.writeHead(200, { 'Content-Type':'text/event-stream; charset=utf-8',
        'Connection':'keep-alive', 'X-Accel-Buffering':'no' });
      res.write(': connected\n\n');
      listeners.add(res);
      req.on('close', () => listeners.delete(res));
      return;
    }
    if (path === '/') {
      res.writeHead(302, { Location:'/login.html' }); res.end(); return;
    }
    if (path.includes('\\') || path.includes('\0') || path.split('/').includes('..')) {
      res.writeHead(403); res.end('Caminho proibido'); return;
    }
    if (aliases.has(path.slice(1))) path += '.html';
    const full = resolve(root, '.' + path);
    if (full === root || !full.startsWith(root + sep)) {
      res.writeHead(403); res.end('Caminho proibido'); return;
    }
    try {
      const info = await stat(full);
      if (!info.isFile() || !mime[extname(full).toLowerCase()]) {
        res.writeHead(404); res.end('Arquivo indisponível'); return;
      }
      let body = await readFile(full);
      const extension = extname(full).toLowerCase();
      if (extension === '.html') {
        const html = body.toString('utf8');
        body = Buffer.from(html.replace('</body>', reloadClient + '\n</body>'));
      }
      res.writeHead(200, { 'Content-Type':mime[extension] });
      res.end(req.method === 'HEAD' ? undefined : body);
    } catch (error) {
      res.writeHead(error.code === 'ENOENT' ? 404 : 500);
      res.end(error.code === 'ENOENT' ? 'Página não encontrada' : 'Erro de leitura do preview');
    }
  });
  await new Promise((done, fail) => {
    server.once('error', fail);
    server.listen(port, '127.0.0.1', done);
  });
  console.log('[StockOS local] http://127.0.0.1:' + server.address().port + '/login.html');
}
