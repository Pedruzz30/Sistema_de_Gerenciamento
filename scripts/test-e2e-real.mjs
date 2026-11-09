// Teste de ponta a ponta: navegador de verdade + Java + PostgreSQL, em modo de produção.
// Faz o caminho da loja usando as telas, e cobre o que os testes de API não veem.
//
// Precisa do sistema rodando em E2E_URL (padrão http://localhost:8080) com banco vazio,
// porque cria a senha dos usuários padrão. Nunca aponte para o banco de uma loja.
// Roda no GitHub pelo workflow stockos-e2e.yml. Grava um print de cada passo em e2e-artifacts/.
import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import { chromium } from 'playwright';

const BASE = process.env.E2E_URL || 'http://localhost:8080';
const SAIDA = 'e2e-artifacts';
const SENHA_ADMIN = 'SenhaDoDono1';
const PIN_ADMIN = '7305';
const SENHA_ANA = 'SenhaDaAna1';
const PIN_ANA = '4821';

/** Dígito verificador GS1 a partir do código sem o último dígito. */
function gtin(base) {
  let soma = 0;
  let peso = 3;
  for (let i = base.length - 1; i >= 0; i--) {
    soma += Number(base[i]) * peso;
    peso = peso === 3 ? 1 : 3;
  }
  return base + ((10 - (soma % 10)) % 10);
}

function chaveNfe(numero) {
  const base = '35261111222333000181550010' + String(numero).padStart(17, '0');
  let soma = 0;
  let peso = 2;
  for (let i = base.length - 1; i >= 0; i--) {
    soma += Number(base[i]) * peso;
    peso = peso === 9 ? 2 : peso + 1;
  }
  const resto = soma % 11;
  return base + (resto < 2 ? 0 : 11 - resto);
}

const EAN_LATA = gtin('789100010010');
const EAN_FARDO = gtin('789100010011');
const EAN_NOVO = gtin('789100010020');

const limpar = texto => String(texto || '').replace(/ /g, ' ').replace(/\s+/g, ' ').trim();

let contador = 0;
const errosDePagina = [];

async function novaPagina(navegador) {
  const contexto = await navegador.newContext({ viewport: { width: 1366, height: 650 }, locale: 'pt-BR' });
  const pagina = await contexto.newPage();
  pagina.setDefaultTimeout(15000);
  pagina.on('pageerror', erro => errosDePagina.push(`${pagina.url()}: ${erro.message}`));
  return pagina;
}

async function print(pagina, nome) {
  contador += 1;
  const arquivo = join(SAIDA, `${String(contador).padStart(2, '0')}-${nome}.png`);
  await pagina.screenshot({ path: arquivo, fullPage: false }).catch(() => {});
}

async function passo(pagina, nome, acao) {
  process.stdout.write(`• ${nome} ... `);
  try {
    await acao();
    await print(pagina, nome.toLowerCase().normalize('NFD').replace(/[^a-z0-9]+/g, '-').slice(0, 50));
    console.log('ok');
  } catch (erro) {
    console.log('FALHOU');
    await print(pagina, 'FALHA');
    await writeFile(join(SAIDA, 'falha.html'), await pagina.content().catch(() => '')).catch(() => {});
    console.error(`\nPasso que falhou: ${nome}\nPágina: ${pagina.url()}\n${erro.stack || erro}`);
    throw erro;
  }
}

const api = (pagina, caminho) => pagina.evaluate(async p => {
  const resposta = await fetch(p);
  return { status: resposta.status, corpo: await resposta.json().catch(() => null) };
}, caminho);

async function textoDe(pagina, seletor) {
  return limpar(await pagina.locator(seletor).first().textContent());
}

/** Espera o texto aparecer; assim o erro mostra o que estava na tela. */
async function esperarTexto(pagina, seletor, esperado) {
  try {
    await pagina.waitForFunction(
      ([s, t]) => {
        const el = document.querySelector(s);
        return el && el.textContent.replace(/ /g, ' ').replace(/\s+/g, ' ').includes(t);
      },
      [seletor, esperado],
      { timeout: 15000 }
    );
  } catch {
    assert.fail(`Esperava "${esperado}" em ${seletor}, mas estava: "${await textoDe(pagina, seletor).catch(() => '(não encontrado)')}"`);
  }
}

/** O elemento precisa estar à vista: inteiro na janela, sem corte e sem nada por cima. */
async function naTela(pagina, seletor) {
  const problema = await pagina.evaluate(s => {
    const el = document.querySelector(s);
    if (!el) return 'não existe';
    const r = el.getBoundingClientRect();
    if (r.width < 1 || r.height < 1) return `sem tamanho (${r.width}x${r.height})`;
    let topo = 0;
    let base = window.innerHeight;
    for (let p = el.parentElement; p; p = p.parentElement) {
      const estilo = getComputedStyle(p);
      if (estilo.overflowY !== 'visible' || estilo.overflowX !== 'visible') {
        const q = p.getBoundingClientRect();
        topo = Math.max(topo, q.top);
        base = Math.min(base, q.bottom);
      }
    }
    if (r.top < topo - 1 || r.bottom > base + 1) return `cortado (de ${r.top} a ${r.bottom}, área visível de ${topo} a ${base})`;
    const meio = document.elementFromPoint(r.left + r.width / 2, r.top + r.height / 2);
    if (!meio || !(el === meio || el.contains(meio) || meio.contains(el))) return 'coberto por outro elemento';
    return null;
  }, seletor);
  assert.equal(problema, null, `${seletor} precisa estar à vista: ${problema}`);
}

/** Todo botão da tela chama uma função que existe: os scripts só expõem o que exportam. */
async function botoesLigados(pagina) {
  const faltando = await pagina.evaluate(() => [...new Set([...document.querySelectorAll('[onclick]')]
    .map(el => (el.getAttribute('onclick').match(/^\s*([A-Za-z_$][\w$]*)\s*\(/) || [])[1])
    .filter(nome => nome && typeof window[nome] !== 'function'))]);
  assert.deepEqual(faltando, [], `Botões de ${pagina.url()} chamam funções que não existem`);
}

async function entrar(pagina, nome, sobrenome, senha) {
  await pagina.goto(`${BASE}/login.html`);
  await pagina.waitForFunction(() => !document.getElementById('btnSubmit').disabled);
  await pagina.fill('#nome', nome);
  await pagina.fill('#sobrenome', sobrenome);
  await pagina.fill('#senha', senha);
  await pagina.click('#btnSubmit');
}

async function criarSenha(pagina, senha, pin) {
  await pagina.waitForSelector('#senhaForm:not([hidden])');
  await pagina.fill('#novaSenha', senha);
  await pagina.fill('#confirmarSenha', senha);
  await pagina.fill('#novoPin', pin);
  await pagina.click('#senhaForm button[type=submit]');
  await pagina.waitForURL('**/dashboard.html');
}

function cardDoCaixa(pagina, numero) {
  return pagina.locator('.caixa-card', { hasText: `Caixa ${numero}` }).first();
}

async function linhaDoCard(pagina, numero, rotulo) {
  const linha = cardDoCaixa(pagina, numero).locator('.caixa-detail-row', { hasText: rotulo }).first();
  return limpar(await linha.locator('.caixa-detail-value').textContent());
}

async function bipar(pagina, codigo) {
  await pagina.waitForFunction(() => document.activeElement && document.activeElement.id === 'pdv-leitura');
  await pagina.keyboard.type(codigo);
  await pagina.keyboard.press('Enter');
}

function notaXml(numero, linhas) {
  const dets = linhas.map((l, i) => `<det nItem="${i + 1}"><prod><cProd>${l.codigo}</cProd><cEAN>${l.eanCaixa}</cEAN><xProd>${l.descricao}</xProd>`
    + `<NCM>22030000</NCM><CEST>0302100</CEST><CFOP>5405</CFOP><uCom>${l.uCom}</uCom><qCom>${l.qCom}</qCom><vProd>${l.vProd}</vProd>`
    + `<cEANTrib>${l.eanUnidade}</cEANTrib><uTrib>${l.uTrib}</uTrib><qTrib>${l.qTrib}</qTrib></prod></det>`).join('');
  return `<?xml version="1.0" encoding="UTF-8"?><nfeProc xmlns="http://www.portalfiscal.inf.br/nfe" versao="4.00"><NFe>`
    + `<infNFe Id="NFe${chaveNfe(numero)}" versao="4.00"><ide><mod>55</mod><serie>1</serie><nNF>${numero}</nNF>`
    + `<dhEmi>2026-11-05T10:30:00-03:00</dhEmi><tpNF>1</tpNF><tpAmb>1</tpAmb><finNFe>1</finNFe></ide>`
    + `<emit><CNPJ>11222333000181</CNPJ><xNome>Distribuidora Exemplo LTDA</xNome></emit>`
    + `<dest><CNPJ>04252011000110</CNPJ><xNome>Deposito Exemplo</xNome></dest>${dets}</infNFe></NFe></nfeProc>`;
}

await mkdir(SAIDA, { recursive: true });
const navegador = await chromium.launch();
let falhou = false;

try {
  const admin = await novaPagina(navegador);

  await passo(admin, 'API recusa cabecalho forjado sem sessao', async () => {
    const resposta = await admin.request.get(`${BASE}/api/produtos`, { headers: { 'X-User-RU': '1' } });
    assert.equal(resposta.status(), 401, 'O sistema precisa rodar em modo de produção, sem o cabeçalho legado.');
  });

  await passo(admin, 'Tela de login libera o botao Entrar', async () => {
    await admin.goto(`${BASE}/login.html`);
    await admin.waitForFunction(() => !document.getElementById('btnSubmit').disabled);
  });

  await passo(admin, 'Senha errada mostra erro', async () => {
    await entrar(admin, 'Admin', 'Superior', 'errada');
    await esperarTexto(admin, '#alertMsg', 'incorretos');
  });

  await passo(admin, 'Primeiro login exige criar senha', async () => {
    await entrar(admin, 'Admin', 'Superior', '1234');
    await admin.waitForSelector('#senhaForm:not([hidden])');
    assert.equal(await admin.locator('#loginForm').isVisible(), false);
  });

  await passo(admin, 'Senhas diferentes sao recusadas', async () => {
    await admin.fill('#novaSenha', SENHA_ADMIN);
    await admin.fill('#confirmarSenha', 'outraSenha1');
    await admin.click('#senhaForm button[type=submit]');
    await esperarTexto(admin, '#senhaAviso', 'não são iguais');
  });

  await passo(admin, 'Criar senha e PIN entra no sistema', async () => {
    await criarSenha(admin, SENHA_ADMIN, PIN_ADMIN);
    const cookies = await admin.context().cookies();
    const sessao = cookies.find(c => c.name === 'STOCKOS_SESSAO');
    assert.ok(sessao && sessao.httpOnly, 'Cookie de sessão precisa ser HttpOnly');
    assert.equal(sessao.sameSite, 'Strict');
  });

  await passo(admin, 'Bebida rapida recusa codigo errado', async () => {
    await admin.goto(`${BASE}/estoque.html`);
    await botoesLigados(admin);
    await admin.click('#btn-revenda');
    await admin.waitForFunction(() => document.activeElement && document.activeElement.id === 'r-ean');
    await admin.keyboard.type(EAN_LATA.slice(0, 12) + ((Number(EAN_LATA[12]) + 1) % 10));
    await admin.keyboard.press('Enter');
    await esperarTexto(admin, '#r-ean-status', 'dígito verificador');
  });

  await passo(admin, 'Bebida rapida cadastra so pelo teclado', async () => {
    await admin.fill('#r-ean', '');
    await admin.focus('#r-ean');
    await admin.keyboard.type(EAN_LATA);
    await admin.keyboard.press('Enter');
    await admin.waitForFunction(() => document.activeElement.id === 'r-nome');
    for (const valor of ['Skol lata 350ml', '4.50', '3.99', '2.75', '48', '12', '12', '38.90', EAN_FARDO]) {
      await admin.keyboard.type(valor);
      await admin.keyboard.press('Enter');
    }
    await esperarTexto(admin, '#r-fardo-ean-status', 'Código lido');
    await admin.keyboard.press('Control+Enter');
    await esperarTexto(admin, '#alert-revenda', 'Skol lata 350ml cadastrado');
    await admin.waitForFunction(() => document.activeElement.id === 'r-ean' && document.getElementById('r-ean').value === '');
  });

  await passo(admin, 'Codigo repetido mostra de quem e', async () => {
    await admin.keyboard.type(EAN_LATA);
    await admin.keyboard.press('Enter');
    await esperarTexto(admin, '#r-ean-status', 'Skol lata 350ml');
    await admin.click('#modal-revenda .modal-footer .btn-secondary');
  });

  await passo(admin, 'Tela de Estoque mostra o saldo', async () => {
    await admin.reload();
    await admin.waitForSelector('text=Skol lata 350ml');
    const { corpo } = await api(admin, '/api/produtos');
    assert.equal(corpo.find(p => p.nome === 'Skol lata 350ml').quantidadeAtual, 48);
  });

  await passo(admin, 'Abrir o caixa', async () => {
    await admin.goto(`${BASE}/caixas.html`);
    await admin.click('button:has-text("Abrir Caixa")');
    await admin.fill('#abrir-numero', '1');
    await admin.fill('#abrir-saldo', '100');
    await admin.click('#modal-abrir .btn-primary');
    await cardDoCaixa(admin, 1).waitFor();
    assert.equal(await linhaDoCard(admin, 1, 'Dinheiro na gaveta'), 'R$ 100,00');
  });

  await passo(admin, 'Dono faz sangria sem pedir autorizacao', async () => {
    await cardDoCaixa(admin, 1).locator('button:has-text("Sangria")').click();
    await admin.waitForSelector('#modal-mov.open');
    assert.equal(await admin.locator('#mov-autorizacao').isVisible(), false);
    await admin.evaluate(() => fecharModal('modal-mov'));
  });

  await passo(admin, 'PDV abre com foco na leitura e nao fecha ao clicar fora', async () => {
    await botoesLigados(admin);
    await cardDoCaixa(admin, 1).locator('button:has-text("PDV")').click();
    await admin.waitForSelector('#modal-pdv.open');
    await admin.waitForFunction(() => document.activeElement && document.activeElement.id === 'pdv-leitura');
    await esperarTexto(admin, '#pdv-operador-label', 'Admin Superior');
    await admin.mouse.click(5, 5);
    assert.equal(await admin.locator('#modal-pdv.open').count(), 1, 'Clique fora não pode fechar o PDV');
    await admin.focus('#pdv-leitura');
    assert.equal(await admin.locator('#pdv-pagamento').isVisible(), false, 'Pagamento só aparece depois do F10');
  });

  await passo(admin, 'Bipar com multiplicador e fardo mostra os itens', async () => {
    await bipar(admin, `2*${EAN_LATA}`);
    await esperarTexto(admin, '#pdv-total', 'R$ 9,00');
    await bipar(admin, EAN_LATA);
    await esperarTexto(admin, '#pdv-total', 'R$ 13,50');
    assert.equal(await admin.locator('.pdv-item-row').count(), 1, 'Bipar de novo soma na mesma linha');
    await bipar(admin, EAN_FARDO);
    await esperarTexto(admin, '#pdv-total', 'R$ 52,40');
    assert.equal(await admin.locator('.pdv-item-row').count(), 2);
    await naTela(admin, '.pdv-item-row');
    await naTela(admin, '.pdv-item-row:last-child');
    await naTela(admin, '#btn-pdv-finalizar');
  });

  await passo(admin, 'Codigo desconhecido avisa a vista e F5 nao recarrega', async () => {
    await bipar(admin, EAN_NOVO);
    await esperarTexto(admin, '#alert-pdv', 'não cadastrado');
    await naTela(admin, '#alert-pdv');
    await esperarTexto(admin, '#pdv-total', 'R$ 52,40');
    await admin.evaluate(() => window.addEventListener('keydown', e => { window.__f5Bloqueado = e.defaultPrevented; }, { once: true }));
    await admin.keyboard.press('F5');
    assert.equal(await admin.evaluate(() => window.__f5Bloqueado), true, 'F5 não pode recarregar a página e perder a venda');
  });

  await passo(admin, 'Esc volta do pagamento e desistir de sair mantem o PDV', async () => {
    await admin.keyboard.press('F10');
    await admin.waitForSelector('#pdv-pagamento:not([hidden])');
    await admin.keyboard.press('Escape');
    await admin.waitForSelector('#pdv-pagamento', { state: 'hidden' });
    assert.equal(await admin.locator('#modal-pdv.open').count(), 1, 'Esc no pagamento não pode fechar o PDV');
    admin.once('dialog', dialogo => dialogo.dismiss());
    await admin.keyboard.press('Escape');
    await admin.waitForTimeout(300);
    assert.equal(await admin.locator('#modal-pdv.open').count(), 1, 'Quem desiste de sair continua no PDV');
    await esperarTexto(admin, '#pdv-total', 'R$ 52,40');
    await admin.waitForFunction(() => document.activeElement && document.activeElement.id === 'pdv-leitura');
  });

  await passo(admin, 'Pagar em dinheiro mostra o troco', async () => {
    await admin.keyboard.press('F10');
    await admin.waitForSelector('#pdv-pagamento:not([hidden])');
    await admin.waitForFunction(() => document.activeElement.id === 'pdv-pag-recebido');
    await admin.keyboard.type('100');
    await esperarTexto(admin, '#pdv-pag-troco', 'R$ 47,60');
    await naTela(admin, '#pdv-pag-troco');
    await naTela(admin, '#pdv-pag-recebido');
    await naTela(admin, '#btn-pdv-confirmar-pagamento');
    await naTela(admin, '[data-forma-pdv="CREDITO"]');
    await admin.keyboard.press('Enter');
    await esperarTexto(admin, '#alert-pdv', 'TROCO R$ 47,60');
    await naTela(admin, '#alert-pdv');
    await esperarTexto(admin, '#pdv-total', 'R$ 0,00');
    assert.equal(await admin.locator('#pdv-pagamento').isVisible(), false);
    await admin.waitForFunction(() => document.activeElement.id === 'pdv-leitura');
  });

  await passo(admin, 'F4 vende a natural e F6 paga no Pix', async () => {
    await admin.keyboard.press('F4');
    await esperarTexto(admin, '#pdv-indicador-alternativo', 'alternativa');
    await bipar(admin, EAN_LATA);
    await esperarTexto(admin, '#pdv-total', 'R$ 3,99');
    await admin.keyboard.press('F10');
    await admin.waitForSelector('#pdv-pagamento:not([hidden])');
    await admin.keyboard.press('F6');
    await admin.waitForSelector('[data-forma-pdv="PIX"].pdv-forma--ativa');
    await admin.keyboard.press('Enter');
    await esperarTexto(admin, '#pdv-total', 'R$ 0,00');
    await esperarTexto(admin, '#alert-pdv', 'R$ 3,99 registrada');
  });

  await passo(admin, 'Gaveta soma so o dinheiro', async () => {
    await admin.waitForFunction(async () => {
      const caixas = await (await fetch('/api/caixas')).json();
      return caixas.find(c => c.numeroCaixa === 1).quantidadeVendas === 2;
    });
    const { corpo } = await api(admin, '/api/caixas');
    const caixa = corpo.find(c => c.numeroCaixa === 1);
    assert.equal(caixa.dinheiroNaGaveta, 152.4);
    assert.equal(caixa.totalVendas, 56.39);
    assert.equal(caixa.vendasPorForma.PIX, 3.99);
    const produtos = await api(admin, '/api/produtos');
    assert.equal(produtos.corpo.find(p => p.nome === 'Skol lata 350ml').quantidadeAtual, 32, '48 - 3 - 12 - 1');
  });

  const ana = await novaPagina(navegador);
  await passo(ana, 'Operadora de caixa cria a propria senha e PIN', async () => {
    await entrar(ana, 'Ana', 'Caixa', '1234');
    await criarSenha(ana, SENHA_ANA, PIN_ANA);
  });
  await ana.context().close();

  await passo(admin, 'F12 com PIN errado nao troca', async () => {
    await admin.keyboard.press('F12');
    await admin.waitForSelector('#modal-pdv-operador.open');
    await admin.waitForSelector('#pdv-operador-select option', { state: 'attached' });
    await admin.selectOption('#pdv-operador-select', { label: 'Ana Caixa' });
    await admin.focus('#pdv-operador-pin');
    await admin.keyboard.type('0000');
    await admin.keyboard.press('Enter');
    await esperarTexto(admin, '#alert-pdv-operador', 'PIN incorreto');
  });

  await passo(admin, 'F12 com PIN certo troca o operador', async () => {
    await admin.keyboard.type(PIN_ANA);
    await admin.keyboard.press('Enter');
    await admin.waitForSelector('#modal-pdv-operador:not(.open)', { state: 'attached' });
    await esperarTexto(admin, '#pdv-operador-label', 'Ana Caixa');
  });

  await passo(admin, 'Venda dividida entre Pix e dinheiro fica com a Ana', async () => {
    await admin.focus('#pdv-leitura');
    await bipar(admin, EAN_LATA);
    await esperarTexto(admin, '#pdv-total', 'R$ 4,50');
    await admin.keyboard.press('F10');
    await admin.waitForSelector('#pdv-pagamento:not([hidden])');
    await admin.keyboard.press('F6');
    await admin.waitForSelector('[data-forma-pdv="PIX"].pdv-forma--ativa');
    await admin.fill('#pdv-pag-valor', '2.00');
    await admin.click('#pdv-pagamento .pdv-pag-dividir');
    await esperarTexto(admin, '#pdv-pag-restante', 'R$ 2,50');
    await naTela(admin, '#btn-pdv-confirmar-pagamento');
    await admin.keyboard.press('F5');
    await admin.waitForSelector('[data-forma-pdv="DINHEIRO"].pdv-forma--ativa');
    await admin.fill('#pdv-pag-recebido', '5');
    await esperarTexto(admin, '#pdv-pag-troco', 'R$ 2,50');
    await admin.keyboard.press('Enter');
    await esperarTexto(admin, '#alert-pdv', 'TROCO R$ 2,50');

    const { corpo } = await api(admin, '/api/caixas');
    const caixa = corpo.find(c => c.numeroCaixa === 1);
    assert.equal(caixa.dinheiroNaGaveta, 154.9);
    assert.equal(caixa.quantidadeVendas, 3, 'Venda em duas formas conta como uma');
    const movimentos = await api(admin, `/api/caixas/sessoes/${caixa.caixaId}/movimentacoes`);
    const daAna = movimentos.corpo.filter(m => /Ana/.test(m.operador || ''));
    assert.equal(daAna.length, 2, 'As duas formas da venda precisam estar no nome da Ana');
  });

  await passo(admin, 'Quem entrou por PIN fica so no caixa', async () => {
    const { status, corpo } = await api(admin, '/api/funcionarios');
    assert.equal(status, 403);
    assert.equal(corpo.codigo, 'ACESSO_RESTRITO_PIN');
  });

  await passo(admin, 'F9 mostra as vendas do caixa', async () => {
    await admin.focus('#pdv-leitura');
    await admin.keyboard.press('F9');
    await admin.waitForSelector('#modal-pdv-vendas.open');
    await admin.waitForSelector('[data-venda-pdv]');
    assert.equal(await admin.locator('[data-venda-pdv]').count(), 3);
    const primeira = limpar(await admin.locator('[data-venda-pdv]').first().textContent());
    assert.match(primeira, /Ana Caixa/, 'A mais nova aparece primeiro');
    assert.match(primeira, /R\$ 4,50/);
    assert.match(primeira, /Dinheiro R\$ 2,50 · Pix R\$ 2,00/);
    await naTela(admin, '[data-venda-pdv]');
  });

  await passo(admin, 'Cancelar venda pede motivo e o gerente', async () => {
    await admin.keyboard.press('Enter');
    await esperarTexto(admin, '#pdv-venda-resumo', 'Skol lata 350ml');
    await esperarTexto(admin, '#pdv-venda-resumo', 'R$ 2,50 sai da gaveta');
    assert.equal(await admin.locator('#pdv-cancelar-autorizacao').isVisible(), true, 'A Ana precisa do gerente');
    await admin.waitForFunction(() => document.activeElement.id === 'pdv-cancelar-motivo');
    await admin.keyboard.press('Enter');
    await esperarTexto(admin, '#alert-pdv-vendas', 'motivo');
    await admin.keyboard.type('Cliente desistiu');
    await admin.keyboard.press('Enter');
    await esperarTexto(admin, '#alert-pdv-vendas', 'gerente');
    await admin.fill('#pdv-cancelar-gerente-nome', 'Admin');
    await admin.fill('#pdv-cancelar-gerente-sobrenome', 'Superior');
    await admin.fill('#pdv-cancelar-gerente-senha', 'errada');
    await admin.click('#btn-pdv-cancelar-venda');
    await esperarTexto(admin, '#alert-pdv-vendas', 'não confere');
    const { corpo } = await api(admin, '/api/caixas');
    assert.equal(corpo.find(c => c.numeroCaixa === 1).dinheiroNaGaveta, 154.9, 'Senha errada não cancela');
  });

  await passo(admin, 'Cancelar com o gerente devolve dinheiro e estoque', async () => {
    await admin.fill('#pdv-cancelar-gerente-senha', SENHA_ADMIN);
    await naTela(admin, '#btn-pdv-cancelar-venda');
    await admin.click('#btn-pdv-cancelar-venda');
    await esperarTexto(admin, '#alert-pdv-vendas', 'Devolva R$ 2,50 em dinheiro');
    await esperarTexto(admin, '#pdv-vendas-lista', 'Cancelada');
    const { corpo } = await api(admin, '/api/caixas');
    const caixa = corpo.find(c => c.numeroCaixa === 1);
    assert.equal(caixa.dinheiroNaGaveta, 152.4, 'Sai da gaveta só a parte em dinheiro');
    assert.equal(caixa.totalVendas, 56.39);
  });

  await passo(admin, 'Venda cancelada nao cancela de novo e Esc volta ao caixa', async () => {
    await admin.locator('[data-venda-pdv]').first().click();
    await esperarTexto(admin, '#pdv-venda-resumo', 'já foi cancelada');
    assert.equal(await admin.locator('#btn-pdv-cancelar-venda').isVisible(), false);
    await admin.keyboard.press('Escape');
    await admin.waitForSelector('#modal-pdv-vendas:not(.open)', { state: 'attached' });
    assert.equal(await admin.locator('#modal-pdv.open').count(), 1, 'Esc na lista de vendas volta ao PDV, sem fechá-lo');
    await admin.waitForFunction(() => document.activeElement && document.activeElement.id === 'pdv-leitura');
  });

  await passo(admin, 'Sair do PDV deixa a tela no nome da Ana', async () => {
    await admin.focus('#pdv-leitura');
    const recarregou = admin.waitForEvent('load');
    await admin.keyboard.press('Escape');
    await recarregou;
    await esperarTexto(admin, '#user-name', 'Ana Caixa');
  });

  await passo(admin, 'Menu de quem entrou por PIN mostra so o caixa', async () => {
    await admin.waitForSelector('#nav-items a');
    assert.deepEqual((await admin.locator('#nav-items a').allTextContents()).map(limpar), ['Caixas PDV']);
    await admin.goto(`${BASE}/estoque.html`);
    await admin.waitForURL('**/caixas.html');
  });

  await passo(admin, 'Tela do caixa em portugues e com dinheiro separado', async () => {
    await cardDoCaixa(admin, 1).waitFor();
    assert.equal(await linhaDoCard(admin, 1, 'Dinheiro na gaveta'), 'R$ 152,40');
    assert.equal(await linhaDoCard(admin, 1, 'Pix e cartões'), 'R$ 3,99');
    assert.equal(await linhaDoCard(admin, 1, 'Total Vendas'), 'R$ 56,39');
    assert.match(await linhaDoCard(admin, 1, 'Abertura'), /^\d{2}\/\d{2}/, 'Abertura precisa mostrar a data');
  });

  await passo(admin, 'Sangria pede o gerente e recusa senha errada', async () => {
    await cardDoCaixa(admin, 1).locator('button:has-text("Sangria")').click();
    await admin.waitForSelector('#modal-mov.open');
    assert.equal(await admin.locator('#mov-autorizacao').isVisible(), true, 'A Ana precisa da autorização do gerente');
    await admin.fill('#mov-valor', '50');
    await admin.fill('#mov-gerente-nome', 'Admin');
    await admin.fill('#mov-gerente-sobrenome', 'Superior');
    await admin.fill('#mov-gerente-senha', 'errada');
    await admin.click('#btn-confirmar-mov');
    await esperarTexto(admin, '#alert-mov', 'gerente');
  });

  await passo(admin, 'Sangria com o gerente tira da gaveta', async () => {
    await admin.fill('#mov-gerente-senha', SENHA_ADMIN);
    await admin.click('#btn-confirmar-mov');
    await admin.waitForSelector('#modal-mov:not(.open)', { state: 'attached' });
    await admin.waitForFunction(() => document.querySelector('.caixa-card')?.textContent.replace(/ /g, ' ').includes('R$ 102,40'));
  });

  await passo(admin, 'Fechamento cego so mostra a diferenca no fim', async () => {
    await cardDoCaixa(admin, 1).locator('button:has-text("Encerrar")').click();
    await admin.waitForSelector('#modal-encerrar.open');
    assert.equal(await admin.inputValue('#encerrar-valor-contado'), '', 'O campo começa vazio');
    assert.ok(!limpar(await admin.locator('#modal-encerrar .modal-body').textContent()).includes('102,40'), 'Não pode mostrar o valor esperado');
    await admin.fill('#encerrar-valor-contado', '100');
    await admin.fill('#encerrar-gerente-nome', 'Admin');
    await admin.fill('#encerrar-gerente-sobrenome', 'Superior');
    await admin.fill('#encerrar-gerente-senha', SENHA_ADMIN);
    await admin.click('#btn-confirmar-encerrar');
    await esperarTexto(admin, '#fechamento-container', 'R$ 102,40');
    await esperarTexto(admin, '#fechamento-container', '-R$ 2,40');
  });

  await admin.context().close();
  const dono = await novaPagina(navegador);
  const xmlNota = notaXml(910001, [
    { codigo: 'AGUA500', descricao: 'AGUA MINERAL 500ML', eanCaixa: EAN_NOVO, eanUnidade: EAN_NOVO, uCom: 'UN', qCom: '24', uTrib: 'UN', qTrib: '24', vProd: '24.00' },
    { codigo: 'SKOL350', descricao: 'CERV SKOL LT 350ML', eanCaixa: EAN_FARDO, eanUnidade: EAN_LATA, uCom: 'CX', qCom: '5', uTrib: 'UN', qTrib: '60', vProd: '180.00' }
  ]);
  const arquivoNota = { name: 'nota.xml', mimeType: 'text/xml', buffer: Buffer.from(xmlNota) };

  await passo(dono, 'Dono entra com a senha nova e acesso completo', async () => {
    await entrar(dono, 'Admin', 'Superior', SENHA_ADMIN);
    await dono.waitForURL('**/dashboard.html');
    assert.equal((await api(dono, '/api/funcionarios')).status, 200);
    const produtos = await api(dono, '/api/produtos');
    assert.equal(produtos.corpo.find(p => p.nome === 'Skol lata 350ml').quantidadeAtual, 32, 'A lata da venda cancelada voltou ao estoque');
    await esperarTexto(dono, '#summary-grid', 'R$ 56,39');
  });

  if (process.env.E2E_BACKUP === '1') {
    await passo(dono, 'Dashboard mostra o backup e faz na hora', async () => {
      await dono.waitForSelector('#backup-painel:not([hidden])');
      await esperarTexto(dono, '#backup-painel', 'só neste notebook');
      await naTela(dono, '#btn-backup-agora');
      await dono.click('#btn-backup-agora');
      await esperarTexto(dono, '#backup-painel', 'Último backup: hoje');
      // O botão é trocado quando o backup termina: o texto acima já podia estar na tela.
      await esperarTexto(dono, '#btn-backup-agora', 'Fazer backup agora');
      assert.equal(await dono.isDisabled('#btn-backup-agora'), false);
    });
  } else {
    await passo(dono, 'Fora da loja o painel de backup nao aparece', async () => {
      assert.equal((await api(dono, '/api/sistema/backup')).corpo.monitorado, false);
      assert.equal(await dono.locator('#backup-painel').isVisible(), false);
    });
  }

  await passo(dono, 'Importar XML mostra a conferencia', async () => {
    await dono.goto(`${BASE}/notas.html`);
    await botoesLigados(dono);
    await dono.click('#btn-importar-nfe');
    await dono.setInputFiles('#xml-input', arquivoNota);
    await dono.waitForSelector('#xml-conferencia:not([hidden])');
    await esperarTexto(dono, '#xml-resumo', 'Distribuidora Exemplo LTDA');
    await esperarTexto(dono, '#xml-conta-1', '24 unidades');
    await esperarTexto(dono, '#xml-conta-2', '60 unidades');
    assert.equal(await dono.inputValue('#xml-acao-1'), 'CRIAR');
    assert.equal(await dono.inputValue('#xml-acao-2'), 'VINCULAR', 'A Skol precisa ser reconhecida pelo código de barras');
  });

  await passo(dono, 'Importar XML pede preco do produto novo', async () => {
    await dono.click('#btn-importar-xml');
    await esperarTexto(dono, '#alert-xml', 'Item 1');
  });

  await passo(dono, 'Importar XML da entrada no estoque', async () => {
    await dono.fill('#xml-nome-1', 'Agua Mineral 500ml');
    await dono.fill('#xml-preco-1', '3.00');
    await dono.click('#btn-importar-xml');
    await dono.waitForSelector('#modal-xml:not(.open)', { state: 'attached' });
    await dono.waitForSelector('text=Distribuidora Exemplo LTDA');
    const { corpo } = await api(dono, '/api/produtos');
    assert.equal(corpo.find(p => p.nome === 'Skol lata 350ml').quantidadeAtual, 92, '32 + 60');
    assert.equal(corpo.find(p => p.nome === 'Agua Mineral 500ml').quantidadeAtual, 24);
    const leitura = await api(dono, `/api/pdv/codigos/${EAN_NOVO}`);
    assert.equal(leitura.status, 200, 'O código da nota precisa funcionar no caixa');
  });

  await passo(dono, 'A mesma nota nao entra duas vezes', async () => {
    await dono.click('#btn-importar-nfe');
    await dono.setInputFiles('#xml-input', arquivoNota);
    await dono.waitForSelector('#xml-conferencia:not([hidden])');
    await esperarTexto(dono, '#xml-avisos', 'já foi importada');
    assert.equal(await dono.isDisabled('#btn-importar-xml'), true);
    dono.once('dialog', dialogo => dialogo.accept());
    await dono.click('#modal-xml .modal-footer .btn-secondary');
  });

  await passo(dono, 'Sair encerra a sessao', async () => {
    await dono.goto(`${BASE}/estoque.html`);
    // Sair troca de página: chamar depois de responder evita disputar com a navegação.
    await dono.evaluate(() => { setTimeout(() => logout(), 0); });
    await dono.waitForURL('**/login.html');
    const resposta = await dono.request.get(`${BASE}/api/caixas`);
    assert.equal(resposta.status(), 401);
  });

  assert.deepEqual(errosDePagina, [], 'Nenhuma tela pode ter erro de JavaScript');
  console.log('\nPASS: caminho completo da loja no navegador, com Java e PostgreSQL.');
} catch {
  falhou = true;
} finally {
  await navegador.close();
  if (errosDePagina.length) console.error('Erros de JavaScript nas telas:\n' + errosDePagina.join('\n'));
}
process.exit(falhou ? 1 : 0);
