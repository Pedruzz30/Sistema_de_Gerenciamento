// Teste no navegador da demonstração "Caixa completo" (Netlify): as telas reais do PDV e das
// comandas com o servidor simulado. Precisa da demonstração montada (npm run build:preview) e
// servida em DEMO_URL (padrão http://localhost:5174). Não usa Java nem banco.
import assert from 'node:assert/strict';
import { chromium } from 'playwright';

const BASE = (process.env.DEMO_URL || 'http://localhost:5174').replace(/\/$/, '');
const navegador = await chromium.launch(process.env.PW_CHANNEL ? { channel: process.env.PW_CHANNEL } : {});
const pagina = await (await navegador.newContext({ viewport: { width: 1366, height: 768 }, locale: 'pt-BR', timezoneId: 'America/Sao_Paulo' })).newPage();
pagina.setDefaultTimeout(15000);
const erros = [];
pagina.on('pageerror', erro => erros.push(erro.message));
const texto = async seletor => (await pagina.textContent(seletor) || '').replace(/\s+/g, ' ').trim();
const esperarTexto = (seletor, esperado) => pagina.waitForFunction(([s, t]) => {
  const el = document.querySelector(s);
  return el && el.textContent.replace(/\s+/g, ' ').includes(t);
}, [seletor, esperado]);
const bipar = async codigo => {
  await pagina.waitForFunction(() => document.activeElement && document.activeElement.id === 'pdv-leitura');
  await pagina.keyboard.type(codigo);
  await pagina.keyboard.press('Enter');
};

try {
  // A demonstração de sempre continua, com o atalho para o caixa completo.
  await pagina.goto(`${BASE}/dashboard.html`, { waitUntil: 'domcontentloaded' });
  await pagina.click('.caixa-completo-entry a');
  await pagina.waitForURL('**/caixa-completo/caixas.html', { waitUntil: 'domcontentloaded' });
  await pagina.waitForSelector('#caixa-completo-faixa');
  assert.deepEqual(await pagina.locator('#nav-items a').allTextContents().then(l => l.map(t => t.replace(/\s+/g, ' ').trim())),
    ['Caixas PDV', 'Comandas', '←Voltar à demonstração']);

  // Venda de balcão pelo PDV real: leitor, multiplicador, dinheiro e troco.
  await pagina.locator('button:has-text("PDV")').first().click();
  await pagina.waitForSelector('#modal-pdv.open');
  await esperarTexto('#pdv-produtos-grid', 'Skol lata');
  await bipar('2*7891000100103');
  await esperarTexto('#pdv-total', 'R$ 9,00');
  await bipar('7891000101001');
  await esperarTexto('#pdv-total', 'R$ 14,50');
  await pagina.keyboard.press('F10');
  await pagina.waitForFunction(() => document.activeElement && document.activeElement.id === 'pdv-pag-recebido');
  await pagina.keyboard.type('20');
  await esperarTexto('.pdv-troco', 'R$ 5,50');
  await pagina.keyboard.press('Enter');
  await esperarTexto('#alert-pdv', 'Venda de R$ 14,50 registrada');
  assert.equal(await pagina.locator('#modal-pdv.open').count(), 1, 'O PDV continua aberto para o próximo cliente');

  // Comanda pelo número e tecla rápida.
  await pagina.focus('#pdv-leitura');
  await bipar('c7');
  await esperarTexto('#pdv-comanda-label', 'Comanda 7 · Carlos');
  await esperarTexto('#pdv-total', 'R$ 34,00');
  await pagina.keyboard.press('Alt+1');
  await esperarTexto('#pdv-total', 'R$ 44,00');
  await pagina.keyboard.press('Escape');
  await esperarTexto('#alert-pdv', 'Comanda 7 · Carlos guardada');

  // Tela Comandas, Nova comanda e Cobrar no caixa (no Pix).
  await pagina.goto(`${BASE}/caixa-completo/comandas.html`, { waitUntil: 'domcontentloaded' });
  await pagina.locator('.comanda-cartao', { hasText: 'Comanda 7' }).waitFor();
  assert.ok((await texto('.comanda-cartao:has-text("Comanda 7")')).includes('R$ 44,00'));
  await pagina.click('#btn-nova-comanda');
  await pagina.waitForSelector('#modal-nova-comanda.open');
  await pagina.fill('#nova-comanda-numero', '30');
  await pagina.fill('#nova-comanda-cliente', '  Marina   Souza ');
  await pagina.press('#nova-comanda-cliente', 'Enter');
  await pagina.waitForURL(/\/caixa-completo\/caixas\.html/, { waitUntil: 'domcontentloaded' });
  await esperarTexto('#pdv-comanda-label', 'Comanda 30 · Marina Souza');
  await pagina.keyboard.press('Escape');
  await pagina.goto(`${BASE}/caixa-completo/comandas.html`, { waitUntil: 'domcontentloaded' });
  await pagina.locator('.comanda-cartao', { hasText: 'Comanda 12' }).click();
  await esperarTexto('#comandas-detalhe-titulo', 'Comanda 12');
  await pagina.click('#btn-cobrar-comanda');
  await pagina.waitForSelector('#modal-pdv.open');
  await esperarTexto('#pdv-comanda-label', 'Comanda 12');
  await pagina.keyboard.press('F10');
  await pagina.waitForSelector('#pdv-pagamento:not([hidden])');
  await pagina.keyboard.press('F6');
  await pagina.keyboard.press('Enter');
  await esperarTexto('#alert-pdv', 'Comanda 12 paga');

  await pagina.goto(`${BASE}/caixa-completo/comandas.html`, { waitUntil: 'domcontentloaded' });
  await pagina.locator('.comanda-cartao', { hasText: 'Comanda 30' }).waitFor();
  // Busca pelo nome e troca o nome pela tela Comandas.
  await pagina.fill('#comandas-busca', 'marina');
  assert.equal(await pagina.locator('.comanda-cartao').count(), 1, 'A busca pelo nome acha só a comanda da Marina');
  await pagina.click('.comanda-cartao:has-text("Marina Souza")');
  await esperarTexto('#comandas-detalhe-cliente', 'Marina Souza');
  await pagina.fill('#comandas-nome-input', 'Marina');
  await pagina.click('#btn-salvar-nome');
  await pagina.locator('.comanda-cartao', { hasText: 'Comanda 30' }).filter({ hasText: 'Marina' }).filter({ hasNotText: 'Souza' }).waitFor();
  await pagina.fill('#comandas-busca', '');

  // Recomeçar (na faixa do topo) volta aos dados de exemplo.
  // Na tela Comandas, que se atualiza sozinha: uma atualização no meio não pode desfazer o Recomeçar.
  await pagina.evaluate(() => { for (let i = 0; i < 5; i++) fetch('/api/comandas'); });
  await pagina.click('#caixa-completo-recomecar');
  await pagina.waitForURL('**/caixa-completo/caixas.html', { waitUntil: 'domcontentloaded' });
  await pagina.goto(`${BASE}/caixa-completo/comandas.html`, { waitUntil: 'domcontentloaded' });
  await pagina.locator('.comanda-cartao', { hasText: 'Comanda 12' }).waitFor();
  assert.ok((await texto('.comanda-cartao:has-text("Comanda 7")')).includes('R$ 34,00'), 'Recomeçar restaura as comandas de exemplo');
  assert.equal(await pagina.locator('.comanda-cartao', { hasText: 'Comanda 30' }).count(), 0, 'Recomeçar tira a comanda criada no teste');

  // A demonstração antiga não foi trocada.
  await pagina.goto(`${BASE}/caixas.html`, { waitUntil: 'domcontentloaded' });
  assert.equal(await pagina.locator('script[src="/js/preview-pdv.js"]').count(), 1, 'O PDV simulado do amigo continua em /caixas.html');

  assert.deepEqual(erros, [], 'Sem erro de JavaScript nas telas');
  console.log('PASS caixa completo: venda com troco, comanda por número, tecla rápida, Nova comanda, Cobrar no caixa e Recomeçar.');
} catch (erro) {
  await pagina.screenshot({ path: 'caixa-completo-falha.png' }).catch(() => {});
  console.error(erro);
  process.exitCode = 1;
} finally {
  await navegador.close();
}
