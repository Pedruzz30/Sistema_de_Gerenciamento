import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { once } from 'node:events';

const child = spawn(process.execPath, ['scripts/dev-preview.mjs'], {
  cwd: process.cwd(),
  env: { ...process.env, PORT: '0' },
  stdio: ['ignore', 'pipe', 'pipe']
});
let output = '';
let errors = '';
child.stderr.on('data', chunk => { errors += chunk.toString(); });
child.stdout.on('data', chunk => { output += chunk.toString(); });
const deadline = Date.now() + 25000;
async function waitForAddress() {
  while (Date.now() < deadline) {
    const m = output.match(/http:\/\/127\.0\.0\.1:(\d+)\/login\.html/);
    if (m) return 'http://127.0.0.1:' + m[1];
    if (child.exitCode !== null) throw Error('Dev preview encerrou: ' + errors + output);
    await new Promise(resolve => setTimeout(resolve, 80));
  }
  throw Error('Servidor preview não iniciou: ' + errors + output);
}
try {
  const base = await waitForAddress();
  const root = await fetch(base + '/', { redirect:'manual' });
  assert.equal(root.status, 302);
  assert.equal(root.headers.get('location'), '/login.html');
  const login = await fetch(base + '/login.html');
  assert.equal(login.status, 200);
  const html = await login.text();
  assert.match(html, /\/js\/preview-mock\.js/);
  assert.match(html, /__stockos_local_events/);
  assert.match(html, /function handleLogin/);

  const dashboard = await fetch(base + '/dashboard');
  assert.equal(dashboard.status, 200);
  assert.match(await dashboard.text(), /preview-mock\.js/);

  const script = await fetch(base + '/js/preview-mock.js');
  assert.equal(script.status, 200);
  assert.match(await script.text(), /stockos-preview-v1/);

  const css = await fetch(base + '/app.css');
  assert.equal(css.status, 200);
  assert.match(css.headers.get('content-type'), /text\/css/);

  const missing = await fetch(base + '/application.properties');
  assert.equal(missing.status, 404);
  const unsupported = await fetch(base + '/api/auth/login', {method:'POST'});
  assert.equal(unsupported.status, 405);

  const ac = new AbortController();
  const events = await fetch(base + '/__stockos_local_events', {signal:ac.signal});
  assert.equal(events.status, 200);
  assert.match(events.headers.get('content-type'), /text\/event-stream/);
  ac.abort();

  console.log('PASS: dev preview iniciado, login mock, rotas, CSS, arquivos protegidos e SSE');
} finally {
  child.kill('SIGTERM');
  if (child.exitCode === null) {
    await Promise.race([once(child,'exit'),new Promise(resolve=>setTimeout(resolve,1500))]);
  }
}
