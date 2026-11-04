import assert from 'node:assert/strict';
import vm from 'node:vm';
import { readFile } from 'node:fs/promises';

const [fixture, mock] = await Promise.all([
  readFile('preview-source/js/preview-scale.js', 'utf8'),
  readFile('preview-source/js/preview-mock.js', 'utf8')
]);
const kv = new Map();
const localStorage = {
  getItem(key) { return kv.get(key) ?? null; },
  setItem(key, value) { kv.set(key, String(value)); },
  removeItem(key) { kv.delete(key); }
};
const location = {
  href: 'https://demo.local/dashboard.html',
  origin: 'https://demo.local',
  pathname: '/dashboard.html',
  reload() {}
};
const element = () => ({
  style: {}, children: [],
  setAttribute() {}, addEventListener() {}, appendChild(child) { this.children.push(child); },
  append(...items) { this.children.push(...items); }
});
const document = {
  createElement: element,
  addEventListener() {},
  getElementById() { return null; },
  body: { appendChild() {} }
};
const window = { fetch: async () => { throw Error('Preview must not use external network'); } };
const context = vm.createContext({
  window, document, location, localStorage, Response, Request, URL,
  Date, Math, Intl, console, Map, JSON
});
vm.runInContext(fixture, context, { filename: 'preview-scale.js' });
const startMock = () => vm.runInContext(mock, context, { filename: 'preview-mock.js' });
const request = async path => {
  const response = await window.fetch(path);
  assert.equal(response.status, 200, path);
  return response.json();
};

startMock();
const summary = await request('/api/preview/cenario');
assert.equal(window.StockOsPreview.scenario, 'scale');
assert.equal(summary.nome, 'Estação 30 — Restaurante & Bar');
assert.equal(summary.pedidos, 7500);
assert.equal(summary.faturamento, 300000);
assert.equal(summary.ticketMedio, 40);
assert.equal(summary.dias.length, 30);
assert.equal(summary.dias.reduce((s, day) => s + day.faturamento, 0), 300000);
assert.equal((await request('/api/produtos')).length, 160);
assert.equal((await request('/api/fornecedores')).length, 18);
assert.equal((await request('/api/funcionarios')).length, 22);
assert.equal((await request('/api/notas')).length, 120);
assert.equal((await request('/api/movimentacoes')).length, 1500);
assert.equal((await request('/api/logs')).length, 300);
assert.equal((await request('/api/rentabilidade/ranking')).receitaTotalEstimada, 300000);
assert.equal(window.StockOsPreview.getSeedNotes().length, 120);
assert.equal(JSON.parse(localStorage.getItem('stockos-preview-scale-v1'))
  .vendasPreview.filter(sale => sale.historico).length, 7500);
assert.ok(localStorage.getItem('stockos-preview-scale-v1').length < 4_000_000,
  'Browser state should remain below common localStorage quota');
assert.equal(localStorage.getItem('stockos-preview-v1'), null,
  'Volume fixture must not overwrite the basic preview');

localStorage.setItem('stockos-preview-scenario-v1', 'basic');
startMock();
assert.equal(window.StockOsPreview.scenario, 'basic');
assert.equal((await request('/api/produtos')).length, 24);
assert.equal((await request('/api/preview/cenario')).demonstracao, true);
assert.ok(localStorage.getItem('stockos-preview-scale-v1'),
  'Switching to basic must not erase the volume fixture');
assert.equal((await window.fetch('https://example.com/api/produtos')).status, 403,
  'Preview must block external requests');
console.log('PASS: R$300k demo, 7500 orders, separated storage, fixtures and offline isolation');
