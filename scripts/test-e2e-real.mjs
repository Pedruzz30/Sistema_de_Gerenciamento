// Teste de ponta a ponta: navegador de verdade + Java + PostgreSQL, em modo de produção.
// Faz o caminho da loja usando as telas, e cobre o que os testes de API não veem.
//
// Precisa do sistema rodando em E2E_URL (padrão http://localhost:8080) com banco vazio,
// porque cria a senha dos usuários padrão. Nunca aponte para o banco de uma loja.
// Roda no GitHub pelo workflow stockos-e2e.yml. Grava um print de cada passo em e2e-artifacts/.
import assert from 'node:assert/strict';
import { mkdir, readdir, readFile, writeFile } from 'node:fs/promises';
import { isIP } from 'node:net';
import { join } from 'node:path';
import tls from 'node:tls';
import { chromium, devices, webkit } from 'playwright';

const BASE = process.env.E2E_URL || 'http://localhost:8080';
// Porta do celular do atendente (https). Sem ela, os passos do celular ficam de fora.
const CELULAR = (process.env.E2E_CELULAR || '').replace(/\/$/, '');
// Pasta onde o servidor grava o papel da cozinha nos testes. Sem ela, os passos da cozinha ficam de fora.
const COZINHA = process.env.E2E_COZINHA || '';

/** Conecta como o celular: confia só no certificado da loja (ou em nada) e confere o nome. */
async function conferirCertificado(url, arquivoCa) {
  const { hostname, port } = new URL(url);
  let ca;
  if (arquivoCa) {
    const der = await readFile(arquivoCa);
    ca = '-----BEGIN CERTIFICATE-----\n' + der.toString('base64').match(/.{1,64}/g).join('\n') + '\n-----END CERTIFICATE-----\n';
  }
  await new Promise((resolve, reject) => {
    const conexao = tls.connect({ host: hostname, port: Number(port), ca: ca ? [ca] : [], servername: isIP(hostname) ? undefined : hostname }, () => {
      conexao.end();
      resolve();
    });
    conexao.on('error', reject);
  });
}
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
const EAN_GELO = gtin('789100010030');
const EAN_SKOL_OUTRO = gtin('789100010040');

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

/** Lê o código de barras de uma imagem como um leitor leria (ZXing), para conferir o que vai impresso. */
async function lerCodigoDeBarras(png) {
  const modPng = await import('pngjs');
  const PNG = modPng.PNG || modPng.default.PNG;
  const modZxing = await import('@zxing/library');
  const zxing = modZxing.MultiFormatReader ? modZxing : modZxing.default;
  const imagem = PNG.sync.read(png);
  const cinza = new Uint8ClampedArray(imagem.width * imagem.height);
  for (let i = 0; i < cinza.length; i++) {
    const [r, g, b] = [imagem.data[i * 4], imagem.data[i * 4 + 1], imagem.data[i * 4 + 2]];
    cinza[i] = (r * 299 + g * 587 + b * 114) / 1000;
  }
  const leitor = new zxing.MultiFormatReader();
  leitor.setHints(new Map([
    [zxing.DecodeHintType.POSSIBLE_FORMATS, [zxing.BarcodeFormat.EAN_13]],
    [zxing.DecodeHintType.TRY_HARDER, true]
  ]));
  const fonte = new zxing.RGBLuminanceSource(cinza, imagem.width, imagem.height);
  return leitor.decode(new zxing.BinaryBitmap(new zxing.HybridBinarizer(fonte))).getText();
}

/** Copia o desenho para uma área livre, grande, e lê como um leitor: nada por cima atrapalha. */
async function lerCodigoDaTela(pagina, svg) {
  await svg.evaluate(el => {
    const area = document.createElement('div');
    area.id = 'leitura-teste';
    area.style.cssText = 'position:fixed;top:0;left:0;z-index:99999;background:#fff;padding:20px;width:452px';
    const copia = el.cloneNode(true);
    copia.style.cssText = 'display:block;width:452px;height:auto';
    area.append(copia);
    document.body.append(area);
  });
  try {
    return (await lerCodigoDeBarras(await pagina.locator('#leitura-teste svg').screenshot())).padStart(13, '0');
  } finally {
    await pagina.evaluate(() => document.getElementById('leitura-teste')?.remove());
  }
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

  await passo(admin, 'Codigo novo se cadastra no caixa e entra na venda', async () => {
    await admin.focus('#pdv-leitura');
    await bipar(admin, EAN_GELO);
    await esperarTexto(admin, '#alert-pdv', 'não cadastrado');
    await naTela(admin, '#btn-pdv-cadastrar-codigo');
    await admin.click('#btn-pdv-cadastrar-codigo');
    await admin.waitForSelector('#modal-pdv-novo-codigo.open');
    await esperarTexto(admin, '#pdv-novo-codigo', EAN_GELO);
    assert.equal(await admin.locator('#pdv-novo-autorizacao').isVisible(), false, 'O dono cadastra sem pedir o gerente');
    await admin.waitForFunction(() => document.activeElement.id === 'pdv-novo-nome');
    await admin.keyboard.type('Gelo 5kg');
    await admin.fill('#pdv-novo-preco', '10');
    await admin.fill('#pdv-novo-custo', '6');
    await admin.focus('#pdv-novo-qtd');
    await admin.keyboard.type('20');
    await admin.keyboard.press('Enter');
    await esperarTexto(admin, '#alert-pdv', 'cadastrado e lançado');
    await esperarTexto(admin, '#pdv-total', 'R$ 62,40');
    const { corpo } = await api(admin, '/api/produtos');
    assert.equal(corpo.find(p => p.nome === 'Gelo 5kg').quantidadeAtual, 20);
    // Sai da venda para o roteiro seguir com os mesmos valores.
    await admin.locator('.pdv-item-row', { hasText: 'Gelo 5kg' }).locator('.pdv-item-btn--remove').click();
    await esperarTexto(admin, '#pdv-total', 'R$ 52,40');
    await admin.focus('#pdv-leitura');
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

  await passo(admin, 'F8 imprime o cupom so com a bobina', async () => {
    await admin.evaluate(() => {
      window.__impressoes = [];
      window.print = () => window.__impressoes.push({
        texto: document.getElementById('cupom-impressao').textContent,
        soCupom: document.body.classList.contains('imprimindo-cupom')
      });
    });
    await admin.focus('#pdv-leitura');
    await admin.keyboard.press('F8');
    const impressoes = await admin.evaluate(() => window.__impressoes);
    assert.equal(impressoes.length, 1, 'F8 manda o último cupom para a impressora');
    assert.ok(impressoes[0].soCupom, 'Só o cupom vai para o papel');
    const texto = limpar(impressoes[0].texto);
    for (const valor of ['CUPOM NÃO FISCAL', 'Skol lata 350ml', 'Fardo', 'R$ 52,40', 'Dinheiro', 'R$ 100,00', 'TROCO', 'R$ 47,60', 'Não é documento fiscal']) {
      assert.ok(texto.includes(valor), `${valor} no cupom: ${texto}`);
    }
    await admin.evaluate(() => document.body.classList.add('imprimindo-cupom'));
    await admin.emulateMedia({ media: 'print' });
    const cupom = admin.locator('#cupom-impressao');
    assert.equal(await cupom.isVisible(), true);
    assert.ok((await cupom.boundingBox()).width <= 305, 'Cabe na bobina de 80 mm');
    assert.equal(await admin.locator('#pdv-total').isVisible(), false, 'O resto da tela não sai no papel');
    await print(admin, 'cupom-no-papel');
    await admin.emulateMedia({ media: 'screen' });
    await admin.evaluate(() => document.body.classList.remove('imprimindo-cupom'));
    await admin.focus('#pdv-leitura');
  });

  await passo(admin, 'F4 vende a natural e F6 paga no Pix', async () => {
    await admin.check('#pdv-cupom-auto');
    await admin.focus('#pdv-leitura');
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
    const impressoes = await admin.evaluate(() => window.__impressoes);
    assert.equal(impressoes.length, 2, 'Com cupom automático, sai sozinho ao finalizar');
    assert.ok(limpar(impressoes[1].texto).includes('Pix'), 'Cupom do Pix');
    await admin.uncheck('#pdv-cupom-auto');
    await admin.focus('#pdv-leitura');
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

  await passo(admin, 'Codigo novo se liga a um produto que ja existe, com o gerente', async () => {
    await admin.focus('#pdv-leitura');
    await bipar(admin, EAN_SKOL_OUTRO);
    await esperarTexto(admin, '#alert-pdv', 'não cadastrado');
    await admin.click('#btn-pdv-cadastrar-codigo');
    await admin.waitForSelector('#modal-pdv-novo-codigo.open');
    await admin.click('#pdv-novo-aba-vincular');
    await admin.fill('#pdv-novo-busca', 'skol');
    await admin.waitForSelector('[data-variacao-novo]');
    await admin.locator('.pdv-novo-produto', { hasText: 'Skol lata 350ml' })
      .locator('[data-variacao-novo]', { hasText: 'Natural' }).click();
    assert.equal(await admin.locator('#pdv-novo-autorizacao').isVisible(), true, 'A Ana precisa do gerente');
    await admin.click('#btn-pdv-novo-salvar');
    await esperarTexto(admin, '#alert-pdv-novo', 'gerente');
    await admin.fill('#pdv-novo-gerente-nome', 'Admin');
    await admin.fill('#pdv-novo-gerente-sobrenome', 'Superior');
    await admin.fill('#pdv-novo-gerente-senha', SENHA_ADMIN);
    await naTela(admin, '#btn-pdv-novo-salvar');
    await admin.click('#btn-pdv-novo-salvar');
    await esperarTexto(admin, '#alert-pdv', 'cadastrado e lançado');
    await esperarTexto(admin, '#pdv-total', 'R$ 3,99');
    await admin.locator('.pdv-item-row', { hasText: 'Skol' }).locator('.pdv-item-btn--remove').click();
    await esperarTexto(admin, '#pdv-total', 'R$ 0,00');
    await admin.focus('#pdv-leitura');
  });

  await passo(admin, 'Desconto pede o gerente e fecha no valor pedido', async () => {
    await bipar(admin, `2*${EAN_LATA}`);
    await esperarTexto(admin, '#pdv-total', 'R$ 9,00');
    await admin.keyboard.press('F3');
    await admin.waitForSelector('#pdv-desconto-form:not([hidden])');
    await admin.waitForFunction(() => document.activeElement.id === 'pdv-desconto-entrada');
    await admin.keyboard.type('=8');
    await admin.keyboard.press('Enter');
    await esperarTexto(admin, '#alert-pdv', 'gerente');
    await admin.fill('#pdv-desconto-gerente-nome', 'Admin');
    await admin.fill('#pdv-desconto-gerente-sobrenome', 'Superior');
    await admin.fill('#pdv-desconto-gerente-senha', SENHA_ADMIN);
    await admin.keyboard.press('Enter');
    await esperarTexto(admin, '#alert-pdv', 'Desconto de R$ 1,00 aplicado');
    await esperarTexto(admin, '#pdv-total', 'R$ 8,00');
    await esperarTexto(admin, '#pdv-desconto-valor', 'R$ 1,00');
    await esperarTexto(admin, '#pdv-pag-restante', 'R$ 8,00');
    await naTela(admin, '#pdv-desconto-linha');
    await naTela(admin, '#btn-pdv-confirmar-pagamento');
  });

  await passo(admin, 'Voltar aos itens retira o desconto', async () => {
    await admin.keyboard.press('Escape');
    await admin.waitForSelector('#pdv-pagamento', { state: 'hidden' });
    await esperarTexto(admin, '#alert-pdv', 'Desconto retirado');
    await esperarTexto(admin, '#pdv-total', 'R$ 9,00');
    assert.equal(await admin.locator('#pdv-desconto-linha').isVisible(), false);
  });

  await passo(admin, 'Venda com desconto entra no caixa pelo valor com desconto', async () => {
    await admin.focus('#pdv-leitura');
    await admin.keyboard.press('F3');
    await admin.waitForSelector('#pdv-pagamento:not([hidden])');
    await admin.waitForFunction(() => document.activeElement.id === 'pdv-desconto-entrada');
    await admin.keyboard.type('1');
    await admin.keyboard.press('Enter');
    await esperarTexto(admin, '#alert-pdv', 'gerente');
    await admin.fill('#pdv-desconto-gerente-nome', 'Admin');
    await admin.fill('#pdv-desconto-gerente-sobrenome', 'Superior');
    await admin.fill('#pdv-desconto-gerente-senha', SENHA_ADMIN);
    await admin.click('#btn-pdv-aplicar-desconto');
    await esperarTexto(admin, '#pdv-total', 'R$ 8,00');
    await admin.waitForFunction(() => document.activeElement.id === 'pdv-pag-recebido');
    await admin.keyboard.type('10');
    await esperarTexto(admin, '#pdv-pag-troco', 'R$ 2,00');
    await admin.keyboard.press('Enter');
    await esperarTexto(admin, '#alert-pdv', 'TROCO R$ 2,00');
    const { corpo } = await api(admin, '/api/caixas');
    const caixa = corpo.find(c => c.numeroCaixa === 1);
    assert.equal(caixa.dinheiroNaGaveta, 160.4);
    assert.equal(caixa.totalVendas, 64.39);
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
    assert.equal(await linhaDoCard(admin, 1, 'Dinheiro na gaveta'), 'R$ 160,40');
    assert.equal(await linhaDoCard(admin, 1, 'Pix e cartões'), 'R$ 3,99');
    assert.equal(await linhaDoCard(admin, 1, 'Total Vendas'), 'R$ 64,39');
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
    await admin.waitForFunction(() => document.querySelector('.caixa-card')?.textContent.replace(/ /g, ' ').includes('R$ 110,40'));
  });

  await passo(admin, 'Fechamento cego so mostra a diferenca no fim', async () => {
    await cardDoCaixa(admin, 1).locator('button:has-text("Encerrar")').click();
    await admin.waitForSelector('#modal-encerrar.open');
    // A janela põe o cursor no dinheiro logo depois de abrir: espera, para não digitar em outro campo.
    await admin.waitForFunction(() => document.activeElement && document.activeElement.id === 'encerrar-valor-contado');
    assert.equal(await admin.inputValue('#encerrar-valor-contado'), '', 'O campo começa vazio');
    assert.ok(!limpar(await admin.locator('#modal-encerrar .modal-body').textContent()).includes('110,40'), 'Não pode mostrar o valor esperado');
    await admin.fill('#encerrar-valor-contado', '100');
    await admin.fill('#encerrar-gerente-nome', 'Admin');
    await admin.fill('#encerrar-gerente-sobrenome', 'Superior');
    await admin.fill('#encerrar-gerente-senha', SENHA_ADMIN);
    await admin.click('#btn-confirmar-encerrar');
    await esperarTexto(admin, '#fechamento-container', 'R$ 110,40');
    await esperarTexto(admin, '#fechamento-container', '-R$ 10,40');
  });

  await passo(admin, 'Fechamento mostra quanto cada pessoa movimentou', async () => {
    await admin.waitForSelector('#fechamento-operadores .resumo-operadores');
    const linhas = admin.locator('#fechamento-operadores tbody tr');
    assert.equal(await linhas.count(), 2);
    const dono = limpar(await linhas.nth(0).textContent());
    assert.match(dono, /^Admin Superior/, 'Quem mais vendeu vem primeiro');
    for (const valor of ['R$ 56,39', 'R$ 52,40', 'R$ 3,99']) assert.ok(dono.includes(valor), `${valor} na linha do dono: ${dono}`);
    const ana = limpar(await linhas.nth(1).textContent());
    for (const valor of ['R$ 12,50', 'R$ 10,50', 'R$ 2,00', '1 · R$ 4,50', 'R$ 1,00', 'R$ 50,00']) {
      assert.ok(ana.includes(valor), `${valor} na linha da Ana: ${ana}`);
    }
  });

  await passo(admin, 'Impressao do fechamento sai sem o resto da tela', async () => {
    await admin.evaluate(() => document.body.classList.add('imprimindo-fechamento'));
    await admin.emulateMedia({ media: 'print' });
    assert.equal(await admin.locator('#fechamento-container .fechamento-card').isVisible(), true);
    assert.equal(await admin.locator('aside').first().isVisible(), false, 'O menu não sai no papel');
    assert.equal(await admin.locator('.caixa-card').first().isVisible(), false, 'Os cards dos caixas não saem no papel');
    assert.equal(await admin.locator('.fechamento-imprimir').isVisible(), false, 'O botão não sai no papel');
    await print(admin, 'como-sai-no-papel');
    await admin.emulateMedia({ media: 'screen' });
    await admin.evaluate(() => document.body.classList.remove('imprimindo-fechamento'));
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
    assert.equal(produtos.corpo.find(p => p.nome === 'Skol lata 350ml').quantidadeAtual, 30, 'A lata da venda cancelada voltou, e 2 saíram com desconto');
    await esperarTexto(dono, '#summary-grid', 'R$ 64,39');
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

  let senhaTemporariaAna = '';
  await passo(dono, 'Gerente redefine a senha esquecida da Ana', async () => {
    await dono.goto(`${BASE}/funcionarios.html`);
    await botoesLigados(dono);
    const linha = dono.locator('tr', { hasText: 'Ana' }).filter({ hasText: 'Caixa' }).first();
    await linha.locator('button:has-text("Classe")').click();
    await dono.waitForSelector('#modal-classe.open');
    await esperarTexto(dono, '#classe-func-nome', 'Ana Caixa');
    await dono.evaluate(() => fecharModal('modal-classe'));
    assert.equal(await dono.locator('tr', { hasText: 'Admin' }).locator('[data-redefinir-senha]').count(), 0, 'A própria senha não se redefine por aqui');
    dono.once('dialog', dialogo => dialogo.accept());
    await linha.locator('[data-redefinir-senha]').click();
    await dono.waitForSelector('#modal-senha-temporaria.open');
    await naTela(dono, '#senha-temp-valor');
    senhaTemporariaAna = limpar(await dono.textContent('#senha-temp-valor'));
    assert.match(senhaTemporariaAna, /^[A-HJ-NP-Z2-9]{8}$/);
    await dono.click('#modal-senha-temporaria .btn-primary');
    assert.equal(await dono.textContent('#senha-temp-valor'), '', 'A senha some da tela ao fechar');
  });

  await passo(dono, 'Ana entra com a temporaria e cria outra senha', async () => {
    const outra = await novaPagina(navegador);
    await entrar(outra, 'Ana', 'Caixa', SENHA_ANA);
    await esperarTexto(outra, '#alertMsg', 'incorretos');
    await entrar(outra, 'Ana', 'Caixa', senhaTemporariaAna);
    await criarSenha(outra, 'OutraDaAna2', PIN_ANA);
    await outra.context().close();
  });

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
    assert.equal(corpo.find(p => p.nome === 'Skol lata 350ml').quantidadeAtual, 90, '30 + 60');
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

  await passo(dono, 'Folha de codigos cria o codigo de quem nao tem e o leitor le', async () => {
    await dono.goto(`${BASE}/estoque.html`);
    await dono.click('#btn-revenda');
    await dono.waitForSelector('#modal-revenda.open');
    await dono.fill('#r-nome', 'Copo de gelo');
    await dono.fill('#r-preco', '2');
    await dono.fill('#r-custo', '0.5');
    await dono.fill('#r-qtd', '100');
    await dono.focus('#r-nome');
    await dono.keyboard.press('Control+Enter');
    await esperarTexto(dono, '#alert-revenda', 'Copo de gelo cadastrado');
    await dono.goto(`${BASE}/estoque.html`);
    await dono.click('#btn-folha-codigos');
    await dono.waitForURL('**/folha-codigos.html');
    await botoesLigados(dono);
    await esperarTexto(dono, '#folha-faltando-texto', 'Copo de gelo');
    await dono.click('#btn-gerar-codigos');
    await esperarTexto(dono, '#alert-folha', 'criado');
    const etiqueta = dono.locator('.etiqueta', { hasText: 'Copo de gelo' });
    await etiqueta.waitFor();
    const codigo = await etiqueta.getAttribute('data-codigo');
    assert.match(codigo, /^20\d{11}$/, 'Código da loja na faixa 20');
    assert.equal(await dono.locator('.etiqueta', { hasText: 'Skol' }).count(), 0, 'Quem tem código de fábrica não ocupa a folha');

    // O desenho precisa ser lido por um leitor de verdade, não só parecer um código de barras.
    assert.equal(await lerCodigoDaTela(dono, etiqueta.locator('svg')), codigo);

    const leitura = await api(dono, `/api/pdv/codigos/${codigo}`);
    assert.equal(leitura.corpo.variacao.itemNome, 'Copo de gelo', 'O código da folha vende no caixa');
  });

  await passo(dono, 'Folha de codigos sai no papel so com as etiquetas', async () => {
    await dono.emulateMedia({ media: 'print' });
    assert.equal(await dono.locator('#btn-imprimir-folha').isVisible(), false);
    assert.equal(await dono.locator('.etiqueta', { hasText: 'Copo de gelo' }).isVisible(), true);
    await print(dono, 'folha-de-codigos-no-papel');
    await dono.emulateMedia({ media: 'screen' });
  });

  let codigoComanda7 = '';
  await passo(dono, 'Folha imprime os cartoes de comanda e o leitor le', async () => {
    await dono.fill('#comanda-de', '7');
    await dono.fill('#comanda-ate', '8');
    await dono.click('#btn-cartoes-comanda');
    const cartao = dono.locator('.etiqueta--comanda[data-comanda="7"]');
    await cartao.waitFor();
    await esperarTexto(dono, '.etiqueta--comanda[data-comanda="7"]', 'COMANDA 7');
    codigoComanda7 = await cartao.getAttribute('data-codigo');
    assert.match(codigoComanda7, /^29\d{11}$/, 'Cartão na faixa 29, separada dos produtos');
    assert.equal(await lerCodigoDaTela(dono, cartao.locator('svg')), codigoComanda7);
  });

  await passo(dono, 'Comanda abre pelo cartao e guarda o que o cliente pede', async () => {
    await dono.goto(`${BASE}/caixas.html`);
    await dono.click('button:has-text("Abrir Caixa")');
    await dono.fill('#abrir-numero', '2');
    await dono.fill('#abrir-saldo', '0');
    await dono.click('#modal-abrir .btn-primary');
    await cardDoCaixa(dono, 2).waitFor();
    await cardDoCaixa(dono, 2).locator('button:has-text("PDV")').click();
    await dono.waitForSelector('#modal-pdv.open');
    await dono.waitForFunction(() => document.activeElement && document.activeElement.id === 'pdv-leitura');
    await bipar(dono, codigoComanda7);
    await esperarTexto(dono, '#pdv-comanda-label', 'Comanda 7');
    await esperarTexto(dono, '#alert-pdv', 'Comanda 7 aberta');
    await naTela(dono, '#pdv-comanda-label');
    await bipar(dono, `2*${EAN_LATA}`);
    await esperarTexto(dono, '#pdv-total', 'R$ 9,00');
    await dono.keyboard.press('Escape');
    await esperarTexto(dono, '#alert-pdv', 'Comanda 7 guardada com R$ 9,00');
    assert.equal(await dono.locator('#pdv-comanda-label').isVisible(), false);
    assert.equal(await dono.locator('#modal-pdv.open').count(), 1, 'Guardar a comanda não fecha o PDV');
    await esperarTexto(dono, '#pdv-total', 'R$ 0,00');
    const { corpo } = await api(dono, '/api/produtos');
    assert.equal(corpo.find(p => p.nome === 'Skol lata 350ml').quantidadeAtual, 90, 'Consumo ainda não pago não baixa o estoque');
  });

  await passo(dono, 'F7 mostra a comanda e o caixa fecha a conta', async () => {
    await dono.focus('#pdv-leitura');
    await dono.keyboard.press('F7');
    await dono.waitForSelector('#modal-pdv-comandas.open');
    await esperarTexto(dono, '#pdv-comandas-lista', 'Comanda 7');
    await esperarTexto(dono, '#pdv-comandas-lista', 'R$ 9,00');
    await dono.waitForFunction(() => document.activeElement.id === 'pdv-comanda-numero');
    await dono.keyboard.type('7');
    await dono.keyboard.press('Enter');
    await dono.waitForSelector('#modal-pdv-comandas:not(.open)', { state: 'attached' });
    await esperarTexto(dono, '#pdv-comanda-label', 'Comanda 7');
    await esperarTexto(dono, '#pdv-total', 'R$ 9,00');
    await bipar(dono, EAN_LATA);
    await esperarTexto(dono, '#pdv-total', 'R$ 13,50');
    await dono.evaluate(() => {
      window.__impressoes = [];
      window.print = () => window.__impressoes.push(document.getElementById('cupom-impressao').textContent);
    });
    await dono.keyboard.press('F8');
    const conferencia = limpar((await dono.evaluate(() => window.__impressoes))[0] || '');
    for (const valor of ['CONFERÊNCIA DA COMANDA', 'COMANDA 7', 'Skol lata 350ml', 'R$ 13,50', 'Confira os itens']) {
      assert.ok(conferencia.includes(valor), `${valor} na conferência: ${conferencia}`);
    }
    assert.ok(!conferencia.includes('Obrigado') && conferencia.includes('Ainda não pago'), 'Conferência não é recibo de pagamento');
    await esperarTexto(dono, '#alert-pdv', 'Conferência da comanda 7');
    await dono.focus('#pdv-leitura');
    await dono.keyboard.press('F10');
    await dono.waitForSelector('#pdv-pagamento:not([hidden])');

    // Cada um paga o que consumiu: a pessoa 1 tomou 2 latas, no Pix.
    await dono.keyboard.press('F9');
    await dono.waitForSelector('#modal-pdv-dividir.open');
    await dono.click('#pdv-dividir-aba-itens');
    await dono.locator('.pdv-dividir-linha', { hasText: 'Skol' }).locator('input').fill('2');
    await esperarTexto(dono, '#pdv-dividir-soma', 'R$ 9,00');
    await dono.keyboard.press('Enter');
    await dono.waitForSelector('#modal-pdv-dividir:not(.open)', { state: 'attached' });
    assert.equal(await dono.inputValue('#pdv-pag-valor'), '9.00');
    await esperarTexto(dono, '#pdv-divisao-info', 'pessoa 1');
    await dono.keyboard.press('F6');
    await dono.waitForSelector('[data-forma-pdv="PIX"].pdv-forma--ativa');
    await dono.keyboard.press('Enter');
    await esperarTexto(dono, '#alert-pdv', 'Pessoa 1 pagou R$ 9,00');
    await esperarTexto(dono, '#alert-pdv', 'Falta R$ 4,50');

    // A pessoa 2 fica com a lata que falta, em dinheiro, com troco.
    await dono.keyboard.press('F9');
    await dono.waitForSelector('#modal-pdv-dividir.open');
    await esperarTexto(dono, '#pdv-dividir-lista', 'falta 1');
    await dono.locator('.pdv-dividir-linha', { hasText: 'Skol' }).locator('input').fill('1');
    await esperarTexto(dono, '#pdv-dividir-soma', 'R$ 4,50');
    await dono.keyboard.press('Enter');
    await dono.waitForSelector('#modal-pdv-dividir:not(.open)', { state: 'attached' });
    await dono.keyboard.press('F5');
    await dono.fill('#pdv-pag-recebido', '5');
    await esperarTexto(dono, '#pdv-pag-troco', 'R$ 0,50');
    await dono.keyboard.press('Enter');
    await esperarTexto(dono, '#alert-pdv', 'Comanda 7 paga');
    await esperarTexto(dono, '#alert-pdv', 'TROCO R$ 0,50');
    await dono.focus('#pdv-leitura');
    await dono.keyboard.press('F8');
    const cupom = limpar((await dono.evaluate(() => window.__impressoes)).at(-1) || '');
    for (const valor of ['Pessoa 1 · Pix', 'R$ 9,00', 'Pessoa 2 · Dinheiro', 'R$ 4,50', 'Troco']) {
      assert.ok(cupom.includes(valor), `${valor} no cupom dividido: ${cupom}`);
    }
    assert.equal(await dono.locator('#pdv-comanda-label').isVisible(), false);
    const produtos = await api(dono, '/api/produtos');
    assert.equal(produtos.corpo.find(p => p.nome === 'Skol lata 350ml').quantidadeAtual, 87, 'Pagou: baixa as 3 latas');
    const comandas = await api(dono, '/api/comandas');
    assert.equal(comandas.corpo.some(c => c.numero === 7), false, 'Comanda paga sai da lista');
  });

  await passo(dono, 'Comanda dividida em partes iguais fecha no centavo', async () => {
    await dono.focus('#pdv-leitura');
    await bipar(dono, 'c8');
    await esperarTexto(dono, '#pdv-comanda-label', 'Comanda 8');
    await bipar(dono, `3*${EAN_LATA}`);
    await esperarTexto(dono, '#pdv-total', 'R$ 13,50');
    await dono.keyboard.press('F10');
    await dono.waitForSelector('#pdv-pagamento:not([hidden])');
    await dono.keyboard.press('F9');
    await dono.waitForSelector('#modal-pdv-dividir.open');
    await dono.waitForFunction(() => document.activeElement.id === 'pdv-dividir-pessoas');
    await dono.keyboard.type('4');
    await esperarTexto(dono, '#pdv-dividir-previa', '3 × R$ 3,37 e a última R$ 3,39');
    await dono.keyboard.press('Enter');
    await dono.waitForSelector('#modal-pdv-dividir:not(.open)', { state: 'attached' });
    for (const pessoa of [1, 2, 3]) {
      await esperarTexto(dono, '#pdv-divisao-info', `pessoa ${pessoa} de 4`);
      assert.equal(await dono.inputValue('#pdv-pag-valor'), '3.37');
      await dono.keyboard.press('F6');
      await dono.keyboard.press('Enter');
      await esperarTexto(dono, '#alert-pdv', `Pessoa ${pessoa} pagou R$ 3,37`);
    }
    assert.equal(await dono.inputValue('#pdv-pag-valor'), '3.39', 'A última fica com a sobra');
    await dono.keyboard.press('Enter');
    await esperarTexto(dono, '#alert-pdv', 'Comanda 8 paga');
    const produtos = await api(dono, '/api/produtos');
    assert.equal(produtos.corpo.find(p => p.nome === 'Skol lata 350ml').quantidadeAtual, 84);
  });

  await passo(dono, 'Transferir parte do balcao e juntar comandas', async () => {
    // Bipou no balcão, mas o cliente vai consumir: 1 das 2 latas vai para a comanda 9.
    await dono.focus('#pdv-leitura');
    await bipar(dono, `2*${EAN_LATA}`);
    await esperarTexto(dono, '#pdv-total', 'R$ 9,00');
    await dono.keyboard.press('F6');
    await dono.waitForSelector('#modal-pdv-transferir.open');
    await dono.waitForFunction(() => document.activeElement.id === 'pdv-transferir-destino');
    await dono.keyboard.type('9');
    await dono.locator('#pdv-transferir-lista input').first().fill('1');
    await dono.click('#btn-pdv-confirmar-transferencia');
    await esperarTexto(dono, '#alert-pdv', '1 item transferido para a comanda 9');
    await esperarTexto(dono, '#pdv-total', 'R$ 4,50');

    // O resto também.
    await dono.keyboard.press('F6');
    await dono.waitForSelector('#modal-pdv-transferir.open');
    await dono.keyboard.type('9');
    await dono.keyboard.press('Enter');
    await esperarTexto(dono, '#alert-pdv', 'O caixa voltou ao balcão');
    await esperarTexto(dono, '#pdv-total', 'R$ 0,00');

    // A comanda 10 é juntada na 9 e fecha.
    await dono.focus('#pdv-leitura');
    await bipar(dono, 'c10');
    await esperarTexto(dono, '#pdv-comanda-label', 'Comanda 10');
    await bipar(dono, EAN_LATA);
    await esperarTexto(dono, '#pdv-total', 'R$ 4,50');
    await dono.click('#btn-pdv-transferir');
    await dono.waitForSelector('#modal-pdv-transferir.open');
    await esperarTexto(dono, '#pdv-transferir-titulo', 'comanda 10');
    await dono.keyboard.type('9');
    await dono.keyboard.press('Enter');
    await esperarTexto(dono, '#alert-pdv', 'Comanda 10 juntada na 9');
    assert.equal(await dono.locator('#pdv-comanda-label').isVisible(), false);

    const { corpo } = await api(dono, '/api/comandas');
    assert.equal(corpo.find(c => c.numero === 9).total, 13.5, 'A comanda 9 ficou com as 3 latas');
    assert.equal(corpo.some(c => c.numero === 10), false, 'A comanda juntada fecha');
    assert.equal(await dono.evaluate(() => document.querySelectorAll('.pdv-card__badge').length), 0, 'O catálogo não mostra item no pedido vazio');
    const produtos = await api(dono, '/api/produtos');
    assert.equal(produtos.corpo.find(p => p.nome === 'Skol lata 350ml').quantidadeAtual, 84, 'Transferir não mexe no estoque');
  });

  let precoGelo = 0;
  const reais = valor => 'R$ ' + valor.toFixed(2).replace('.', ',');
  const logsDe = async (pagina, acao) => {
    const { corpo } = await api(pagina, `/api/logs?acao=${acao}`);
    return (Array.isArray(corpo) ? corpo : (corpo && (corpo.logs || corpo.content)) || []).map(l => l.descricao);
  };

  await passo(dono, 'Teclas rapidas lancam o que nao tem codigo', async () => {
    await esperarTexto(dono, '#pdv-teclas', 'Criar teclas rápidas');
    await dono.click('#pdv-teclas .pdv-tecla--editar');
    await dono.waitForSelector('#modal-pdv-teclas.open');
    await dono.waitForSelector('[data-tecla-posicao="1"] option:nth-child(2)', { state: 'attached' });
    assert.equal(await dono.locator('#pdv-teclas-autorizacao').isVisible(), false, 'O dono muda sem pedir o gerente');
    const gelo = await dono.locator('[data-tecla-posicao="1"] option', { hasText: 'Gelo 5kg' }).first().getAttribute('value');
    await dono.selectOption('[data-tecla-posicao="1"]', gelo);
    await naTela(dono, '#modal-pdv-teclas .modal');
    await dono.click('#btn-pdv-salvar-teclas');
    await esperarTexto(dono, '#alert-pdv', 'Teclas rápidas salvas');
    await esperarTexto(dono, '#pdv-teclas', 'Alt+1');
    await esperarTexto(dono, '#pdv-teclas', 'Gelo 5kg');
    const { corpo: teclas } = await api(dono, '/api/pdv/teclas');
    precoGelo = Number(teclas[0].precoVenda);
    assert.ok(precoGelo > 0, 'A tecla traz o preço');

    // Na comanda 9: digita 2 e aperta Alt+1, sem código de barras.
    await dono.focus('#pdv-leitura');
    await bipar(dono, 'c9');
    await esperarTexto(dono, '#pdv-comanda-label', 'Comanda 9');
    await esperarTexto(dono, '#pdv-total', 'R$ 13,50');
    await dono.keyboard.type('2');
    await dono.keyboard.press('Alt+1');
    await esperarTexto(dono, '#pdv-total', reais(13.5 + 2 * precoGelo));
    assert.equal(await dono.inputValue('#pdv-leitura'), '', 'A quantidade digitada foi usada pela tecla');
    await naTela(dono, '#pdv-teclas');
    await dono.keyboard.press('Escape');
    await esperarTexto(dono, '#alert-pdv', 'Comanda 9 guardada');
  });

  await passo(dono, 'Operadora so tira item da comanda com o gerente e o motivo', async () => {
    const operadora = await novaPagina(navegador);
    try {
      await entrar(operadora, 'Ana', 'Caixa', 'OutraDaAna2');
      await operadora.waitForURL(url => !url.pathname.endsWith('/login.html'));
      await operadora.goto(`${BASE}/caixas.html`);
      await cardDoCaixa(operadora, 2).locator('button:has-text("PDV")').click();
      await operadora.waitForSelector('#modal-pdv.open');
      await esperarTexto(operadora, '#pdv-teclas', 'Gelo 5kg');
      await operadora.waitForFunction(() => document.activeElement && document.activeElement.id === 'pdv-leitura');
      await bipar(operadora, 'c9');
      await esperarTexto(operadora, '#pdv-comanda-label', 'Comanda 9');
      await operadora.locator('.pdv-item-row', { hasText: 'Gelo 5kg' }).locator('.pdv-item-btn--remove').click();
      await operadora.waitForSelector('#modal-pdv-retirar.open');
      assert.equal(await operadora.locator('#pdv-retirar-autorizacao').isVisible(), true, 'A operadora precisa do gerente');
      // Dos 2 gelos, sai 1: o campo da quantidade já vem selecionado.
      await operadora.waitForFunction(() => document.activeElement && document.activeElement.matches('[data-retirar-item]'));
      await operadora.keyboard.type('1');
      await operadora.click('#btn-pdv-confirmar-retirada');
      await esperarTexto(operadora, '#alert-pdv-retirar', 'motivo');
      await operadora.fill('#pdv-retirar-motivo', 'Lançado errado');
      await operadora.keyboard.press('Enter');
      await esperarTexto(operadora, '#alert-pdv-retirar', 'gerente precisa digitar');
      await operadora.fill('#pdv-retirar-gerente-nome', 'Admin');
      await operadora.fill('#pdv-retirar-gerente-sobrenome', 'Superior');
      await operadora.fill('#pdv-retirar-gerente-senha', 'errada');
      await operadora.click('#btn-pdv-confirmar-retirada');
      await esperarTexto(operadora, '#alert-pdv-retirar', 'invalido');
      await naTela(operadora, '#modal-pdv-retirar .modal');
      await operadora.fill('#pdv-retirar-gerente-senha', SENHA_ADMIN);
      await operadora.click('#btn-pdv-confirmar-retirada');
      await esperarTexto(operadora, '#alert-pdv', `${reais(precoGelo)} tirados da comanda 9`);
      await esperarTexto(operadora, '#pdv-total', reais(13.5 + precoGelo));
      // Limpar a comanda também passa pelo gerente.
      await operadora.click('.pdv-clear-btn:has-text("Limpar")');
      await operadora.waitForSelector('#modal-pdv-retirar.open');
      await esperarTexto(operadora, '#pdv-retirar-titulo', 'Limpar a comanda 9');
      assert.equal(await operadora.locator('[data-retirar-item]').count(), await operadora.locator('.pdv-item-row').count(), 'Limpar lista todos os itens');
      await operadora.keyboard.press('Escape');
      await operadora.waitForSelector('#modal-pdv-retirar:not(.open)', { state: 'attached' });
      await operadora.keyboard.press('Escape');
      await esperarTexto(operadora, '#alert-pdv', 'Comanda 9 guardada');
    } finally {
      await operadora.context().close();
    }
    const { corpo } = await api(dono, '/api/comandas');
    assert.equal(corpo.find(c => c.numero === 9).total, Number((13.5 + precoGelo).toFixed(2)), 'A comanda 9 ficou com 3 latas e 1 gelo');
    const logs = await logsDe(dono, 'COMANDA_ITEM_RETIRADO');
    assert.ok(logs.some(l => l.includes('Comanda 9') && l.includes('Motivo: Lançado errado') && l.includes('autorizado por Admin Superior')), logs.join(' | '));
  });

  await passo(dono, 'Encerrar o caixa avisa da comanda sem pagar', async () => {
    await dono.goto(`${BASE}/caixas.html`);
    const { corpo: caixas } = await api(dono, '/api/caixas');
    const gaveta = caixas.find(c => c.numeroCaixa === 2).dinheiroNaGaveta;
    await cardDoCaixa(dono, 2).locator('button:has-text("Encerrar")').click();
    await dono.waitForSelector('#modal-encerrar.open');
    await dono.waitForFunction(() => document.activeElement && document.activeElement.id === 'encerrar-valor-contado');
    await esperarTexto(dono, '#encerrar-comandas-texto', 'A comanda 9 ainda não foi paga');
    await dono.fill('#encerrar-valor-contado', String(gaveta));
    await dono.click('#btn-confirmar-encerrar');
    await esperarTexto(dono, '#alert-encerrar', 'marque que elas ficam abertas');
    await naTela(dono, '#encerrar-comandas');
    await dono.check('#encerrar-deixar-comandas');
    await dono.click('#btn-confirmar-encerrar');
    await dono.waitForSelector('#fechamento-operadores .resumo-operadores');
    const logs = await logsDe(dono, 'CAIXA_ENCERRADO_COM_COMANDAS');
    assert.ok(logs.some(l => l.includes('Caixa 2') && l.includes(': 9 ')), logs.join(' | '));
    const { corpo } = await api(dono, '/api/comandas');
    assert.ok(corpo.some(c => c.numero === 9), 'A comanda continua aberta para o próximo caixa');
  });

  if (CELULAR) {
    const contextoCelular = await navegador.newContext({ ...devices['Pixel 7'], ignoreHTTPSErrors: true, locale: 'pt-BR' });
    const cel = await contextoCelular.newPage();
    cel.setDefaultTimeout(15000);
    cel.on('pageerror', erro => errosDePagina.push(`celular ${cel.url()}: ${erro.message}`));

    await passo(dono, 'Celular: conexao segura confia so no certificado da loja', async () => {
      await conferirCertificado(CELULAR, process.env.E2E_CELULAR_CA);
      await assert.rejects(conferirCertificado(CELULAR, null), 'Sem o certificado da loja, o celular recusa');
      await dono.goto(`${BASE}/dashboard.html`);
      await dono.waitForSelector('#celular-painel:not([hidden]) #celular-qr svg');
      await esperarTexto(dono, '#celular-url', `:${new URL(CELULAR).port}/`);
      await naTela(dono, '#celular-painel');
    });

    await passo(cel, 'Celular: pela rede so abre a tela de comanda', async () => {
      await cel.goto(`${CELULAR}/`);
      await cel.waitForURL('**/celular.html');
      assert.equal((await cel.request.get(`${CELULAR}/estoque.html`)).status(), 404);
      assert.equal((await cel.request.get(`${CELULAR}/api/produtos`)).status(), 404);
      assert.equal((await cel.request.post(`${CELULAR}/api/auth/login`, {
        data: { nome: 'Admin', sobrenome: 'Superior', senha: SENHA_ADMIN } })).status(), 404, 'Senha não entra pela rede');
      const certificado = await cel.request.get(`${CELULAR}/certificado`);
      assert.equal(certificado.status(), 200);
      assert.match(certificado.headers()['content-type'], /x509/);
      await esperarTexto(cel, '#operadores', 'Ana Caixa');
      await naTela(cel, '#operadores');
    });

    await passo(cel, 'Celular: atendente entra com o PIN', async () => {
      await cel.locator('.operador', { hasText: 'Ana Caixa' }).click();
      await cel.fill('#pin', '0000');
      await cel.click('#btn-entrar');
      await esperarTexto(cel, '#aviso', 'PIN incorreto');
      await cel.fill('#pin', PIN_ANA);
      await cel.click('#btn-entrar');
      await cel.waitForSelector('#tela-comandas:not([hidden])');
      await esperarTexto(cel, '#quem', 'Ana Caixa');
      await esperarTexto(cel, '#comandas-lista', 'Comanda 9');
      await naTela(cel, '#comandas-lista');
      // A sessão do celular não abre o resto, nem pela rota da venda.
      const tentativa = await cel.evaluate(async () => (await fetch('/api/vendas/1/itens', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{}' })).status);
      assert.equal(tentativa, 404);
    });

    await passo(cel, 'Celular: pedido lancado de uma vez na comanda', async () => {
      await cel.fill('#numero-comanda', '11');
      await cel.click('#btn-abrir-comanda');
      await esperarTexto(cel, '#comanda-titulo', 'Comanda 11');
      await esperarTexto(cel, '#aviso', 'Comanda 11 nova');
      await cel.fill('#busca', 'skol');
      const gelada = cel.locator('#resultados .produto', { hasText: 'Gelada' }).first();
      await gelada.click();
      await gelada.click();
      await cel.locator('#teclas .tecla', { hasText: 'Gelo 5kg' }).click();
      await esperarTexto(cel, '#btn-lancar', 'Lançar 3 itens');
      // Errou o gelo: tira do pedido antes de lançar, sem gerente.
      await cel.locator('#pedido [data-menos]').last().click();
      await esperarTexto(cel, '#btn-lancar', 'Lançar 2 itens · R$ 9,00');
      await cel.locator('#teclas .tecla', { hasText: 'Gelo 5kg' }).click();
      await naTela(cel, '#btn-lancar');
      await cel.click('#btn-lancar');
      await esperarTexto(cel, '#aviso', '3 itens lançados na comanda 11');
      await esperarTexto(cel, '#comanda-total', reais(9 + precoGelo));
      // Mais uma Skol depois: soma na mesma linha.
      await cel.fill('#busca', 'skol');
      await cel.locator('#resultados .produto', { hasText: 'Gelada' }).first().click();
      await cel.click('#btn-lancar');
      await esperarTexto(cel, '#aviso', '1 item lançado na comanda 11');
      await esperarTexto(cel, '#comanda-total', reais(13.5 + precoGelo));
      assert.equal(await cel.locator('#comanda-linhas li').count(), 2, 'A Skol somou na mesma linha');
      await esperarTexto(cel, '#comanda-linhas', '3 × Skol lata 350ml');
      await print(cel, 'celular-comanda');
      await cel.click('#btn-voltar');
      await esperarTexto(cel, '#comandas-lista', 'Comanda 11');
      await cel.click('#btn-sair');
      await cel.waitForSelector('#tela-entrar:not([hidden])');
      assert.equal((await cel.evaluate(async () => (await fetch('/api/comandas')).status)), 401, 'Saiu de verdade');
    });

    await passo(dono, 'Celular: o caixa ve o que o atendente lancou', async () => {
      const { corpo } = await api(dono, '/api/comandas');
      assert.equal(corpo.find(c => c.numero === 11).total, Number((13.5 + precoGelo).toFixed(2)));
      const logs = await logsDe(dono, 'COMANDA_ITENS_LANCADOS');
      assert.ok(logs.some(l => l.includes('Comanda 11') && l.includes('Ana Caixa')), logs.join(' | '));
      const entradas = await logsDe(dono, 'LOGIN_CELULAR_FALHOU');
      assert.ok(entradas.some(l => l.includes('Ana Caixa')), 'PIN errado no celular fica registrado');
    });
    await contextoCelular.close();

    // iPhone: o motor do Safari (WebKit), com a tela e o jeito de tocar de um iPhone 13.
    const safari = await webkit.launch();
    try {
      const iphone = devices['iPhone 13'];
      const contextoIphone = await safari.newContext({ ...iphone, ignoreHTTPSErrors: true, locale: 'pt-BR' });
      const ip = await contextoIphone.newPage();
      ip.setDefaultTimeout(15000);
      ip.on('pageerror', erro => errosDePagina.push(`iphone ${ip.url()}: ${erro.message}`));

      await passo(ip, 'iPhone: guia proprio e perfil do certificado', async () => {
        await ip.goto(`${CELULAR}/`);
        await ip.waitForURL('**/celular.html');
        await ip.click('#ajuda-certificado summary');
        assert.equal(await ip.locator('#ajuda-iphone').isVisible(), true, 'Mostra o passo a passo do iPhone');
        assert.equal(await ip.locator('#ajuda-android').isVisible(), false, 'Esconde o do Android');
        assert.equal(await ip.locator('#aviso-safari').isVisible(), false, 'No Safari não pede para trocar de navegador');
        const perfil = await ip.request.get(`${CELULAR}/certificado.mobileconfig`);
        assert.equal(perfil.status(), 200);
        assert.match(perfil.headers()['content-type'], /apple-aspen-config/);
        assert.ok((await perfil.text()).includes('com.apple.security.root'));
        // O guia é mais alto que a tela: rola até o botão do perfil, como a pessoa faria.
        await ip.locator('#link-perfil-iphone').scrollIntoViewIfNeeded();
        await naTela(ip, '#link-perfil-iphone');
      });

      await passo(ip, 'iPhone: atendente lanca na comanda pelo Safari', async () => {
        await ip.click('#ajuda-certificado summary');
        await ip.locator('.operador', { hasText: 'Ana Caixa' }).tap();
        await ip.fill('#pin', PIN_ANA);
        await ip.locator('#btn-entrar').tap();
        await ip.waitForSelector('#tela-comandas:not([hidden])');
        await ip.fill('#numero-comanda', '12');
        await ip.locator('#btn-abrir-comanda').tap();
        await esperarTexto(ip, '#comanda-titulo', 'Comanda 12');
        await ip.fill('#busca', 'skol');
        await ip.locator('#resultados .produto', { hasText: 'Gelada' }).first().tap();
        await esperarTexto(ip, '#btn-lancar', 'Lançar 1 item · R$ 4,50');
        await ip.locator('#btn-lancar').tap();
        await esperarTexto(ip, '#aviso', '1 item lançado na comanda 12');
        await esperarTexto(ip, '#comanda-total', 'R$ 4,50');
        await naTela(ip, '#comanda-total');
        await ip.locator('#btn-sair').tap();
        await ip.waitForSelector('#tela-entrar:not([hidden])');
      });
      await contextoIphone.close();

      // Chrome do iPhone não instala perfil: a tela manda abrir no Safari.
      const contextoChromeIphone = await safari.newContext({
        ...iphone, userAgent: iphone.userAgent.replace('Version/', 'CriOS/120.0.6099.119 Version/'), ignoreHTTPSErrors: true, locale: 'pt-BR' });
      const chromeIphone = await contextoChromeIphone.newPage();
      await passo(chromeIphone, 'iPhone: no Chrome pede para abrir no Safari', async () => {
        await chromeIphone.goto(`${CELULAR}/celular.html`);
        await chromeIphone.click('#ajuda-certificado summary');
        assert.equal(await chromeIphone.locator('#aviso-safari').isVisible(), true);
        assert.equal(await chromeIphone.locator('#link-perfil-iphone').isVisible(), false);
      });
      await contextoChromeIphone.close();
    } finally {
      await safari.close();
    }

    await passo(dono, 'iPhone: o caixa ve o que foi lancado', async () => {
      const { corpo } = await api(dono, '/api/comandas');
      assert.equal(corpo.find(c => c.numero === 12).total, 4.5);
    });

    // Cozinha: o servidor grava cada papel em E2E_COZINHA em vez de mandar para a térmica.
    if (COZINHA) {
      const papeis = async () => {
        try { return (await readdir(COZINHA)).filter(nome => nome.endsWith('.txt')); } catch { return []; }
      };
      const esperarPapel = async (condicao, descricao) => {
        const limite = Date.now() + 20000;
        while (Date.now() < limite) {
          for (const nome of await papeis()) {
            const texto = await readFile(join(COZINHA, nome), 'utf8');
            if (condicao(texto)) return texto;
          }
          await new Promise(r => setTimeout(r, 300));
        }
        throw new Error('O papel da cozinha não saiu: ' + descricao);
      };

      await passo(dono, 'Cozinha: dono escolhe o que vai para a cozinha', async () => {
        const criado = await dono.evaluate(async () => (await fetch('/api/produtos/revenda', {
          method: 'POST', headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ nome: 'Porção de batata', categoriaCardapioCodigo: 'porcoes', categoriaCardapioNome: 'Porções',
            categoriaEstoque: 'FRIO', codigoBarras: '', precoVenda: 25, custoUnitario: 10, quantidadeInicial: 10, quantidadeMinima: 0 })
        })).status);
        assert.ok(criado < 300, `Cadastro da porção: ${criado}`);
        await dono.goto(`${BASE}/caixas.html`);
        await botoesLigados(dono);
        await dono.waitForSelector('#btn-cozinha:not([hidden])');
        await dono.click('#btn-cozinha');
        await dono.waitForSelector('#modal-cozinha.open');
        await dono.waitForSelector('#cozinha-config:not([hidden]) #cozinha-categorias input', { state: 'attached' });
        if (!(await dono.$eval('#cozinha-config', d => d.open))) await dono.click('#cozinha-config summary');
        await dono.locator('#cozinha-categorias label', { hasText: 'Porções' }).locator('input').check();
        await dono.click('#btn-cozinha-salvar');
        await esperarTexto(dono, '#alert-cozinha', 'Cozinha salva');
        await esperarTexto(dono, '#cozinha-situacao', 'arquivo de teste');
        await print(dono, 'cozinha-configurada');
        await dono.click('#modal-cozinha .modal-footer .btn');
      });

      await passo(dono, 'Cozinha: pedido do celular imprime so o que e da cozinha', async () => {
        const contexto = await navegador.newContext({ ...devices['Pixel 7'], ignoreHTTPSErrors: true, locale: 'pt-BR' });
        const garcom = await contexto.newPage();
        garcom.setDefaultTimeout(15000);
        try {
          await garcom.goto(`${CELULAR}/celular.html`);
          await garcom.locator('.operador', { hasText: 'Ana Caixa' }).click();
          await garcom.fill('#pin', PIN_ANA);
          await garcom.click('#btn-entrar');
          await garcom.waitForSelector('#tela-comandas:not([hidden])');
          await garcom.fill('#numero-comanda', '13');
          await garcom.click('#btn-abrir-comanda');
          await esperarTexto(garcom, '#comanda-titulo', 'Comanda 13');
          await garcom.fill('#busca', 'porcao');
          const porcao = garcom.locator('#resultados .produto', { hasText: 'Porção de batata' }).first();
          await porcao.click();
          await porcao.click();
          await garcom.fill('#busca', 'skol');
          await garcom.locator('#resultados .produto', { hasText: 'Gelada' }).first().click();
          await garcom.click('#btn-lancar');
          await esperarTexto(garcom, '#aviso', '3 itens lançados na comanda 13');
        } finally {
          await contexto.close();
        }
        const papel = await esperarPapel(t => t.includes('## COZINHA') && t.includes('COMANDA 13'), 'pedido da comanda 13');
        assert.ok(papel.includes('2 x Porção de batata'), papel);
        assert.ok(!papel.includes('Skol'), 'Bebida não vai para a cozinha');
        assert.ok(papel.includes('Ana Caixa'), papel);
      });

      await passo(dono, 'Cozinha: tirar a porcao manda o aviso de cancelar', async () => {
        const { corpo: comanda } = await api(dono, '/api/comandas/13');
        const { corpo: venda } = await api(dono, `/api/vendas/${comanda.vendaId}`);
        const item = venda.itens.find(i => /Porção/.test(i.item) && i.status !== 'CANCELADO');
        assert.ok(item, JSON.stringify(venda.itens));
        const status = await dono.evaluate(async itemId => (await fetch('/api/comandas/13/retirar', {
          method: 'POST', headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ itens: [{ itemId, quantidade: 1 }], motivo: 'Cliente desistiu' })
        })).status, item.id);
        assert.equal(status, 200);
        const papel = await esperarPapel(t => t.includes('CANCELAR') && t.includes('COMANDA 13'), 'cancelamento da comanda 13');
        assert.ok(papel.includes('1 x Porção de batata'), papel);
      });

      await passo(dono, 'Cozinha: tela mostra os pedidos e reimprime', async () => {
        await dono.click('#btn-cozinha');
        await dono.waitForSelector('#modal-cozinha.open');
        await esperarTexto(dono, '#cozinha-pedidos', 'Cancelamento · Comanda 13');
        await esperarTexto(dono, '#cozinha-pedidos', 'Pedido · Comanda 13');
        await esperarTexto(dono, '#cozinha-pedidos', 'Impresso');
        // A lista precisa aparecer de verdade, não só existir na página.
        await naTela(dono, '#cozinha-pedidos .cozinha-pedido');
        const antes = (await papeis()).length;
        await dono.locator('.cozinha-pedido', { hasText: 'Pedido · Comanda 13' }).first().locator('[data-reimprimir]').click();
        await esperarTexto(dono, '#alert-cozinha', 'Mandado de novo');
        const limite = Date.now() + 15000;
        while ((await papeis()).length <= antes && Date.now() < limite) await new Promise(r => setTimeout(r, 300));
        assert.ok((await papeis()).length > antes, 'A reimpressão gera outro papel');
        await print(dono, 'cozinha-pedidos');
        await dono.click('#modal-cozinha .modal-footer .btn');
      });
    }
  }

  await passo(dono, 'Relatorio do dia confere com o caixa', async () => {
    await dono.goto(`${BASE}/dashboard.html`);
    await dono.click('#btn-relatorio-dia');
    await dono.waitForURL('**/relatorios.html');
    await botoesLigados(dono);
    await dono.waitForSelector('.relatorio-card');
    const cards = limpar(await dono.textContent('#rel-cards'));
    for (const valor of ['FaturamentoR$ 91,39', 'já sem R$ 4,50 cancelados', 'Vendas5', '1 cancelada', 'Ticket médioR$ 18,28', 'DescontosR$ 1,00', 'Comandas pagas2']) {
      assert.ok(cards.includes(valor), `${valor} nos cartões: ${cards}`);
    }
    const formas = limpar(await dono.textContent('#rel-formas'));
    assert.ok(formas.includes('DinheiroR$ 64,90') && formas.includes('PixR$ 26,49'), formas);
    const primeiro = limpar(await dono.locator('#rel-produtos tbody tr').first().textContent());
    assert.ok(primeiro.startsWith('Skol lata 350ml Gelada') && primeiro.includes('11') && primeiro.includes('R$ 48,50'), primeiro);
    const operadores = limpar(await dono.locator('#rel-operadores tbody tr').first().textContent());
    assert.ok(operadores.startsWith('Admin Superior'), operadores);
    await naTela(dono, '#rel-cards');
  });

  await passo(dono, 'Relatorio do mes mostra o dia e imprime sem botoes', async () => {
    await dono.click('#rel-modo-mes');
    await dono.waitForSelector('#rel-dias tbody tr');
    assert.ok(limpar(await dono.textContent('#rel-dias')).includes('R$ 91,39'));
    await dono.emulateMedia({ media: 'print' });
    assert.equal(await dono.locator('#btn-imprimir-relatorio').isVisible(), false);
    assert.equal(await dono.locator('#rel-cards').isVisible(), true);
    await print(dono, 'relatorio-no-papel');
    await dono.emulateMedia({ media: 'screen' });
  });

  await passo(dono, 'Curva ABC mostra a classe e para quantos dias da o estoque', async () => {
    await dono.click('#rel-modo-abc');
    await dono.waitForSelector('#rel-abc-tabela tbody tr');
    const linha = limpar(await dono.locator('#rel-abc-tabela tbody tr').first().textContent());
    // Hoje saíram 24 latas (11 geladas, 1 natural, 1 fardo de 12): 0,8 por dia em 30 dias; 84 em estoque dão 105 dias.
    for (const valor of ['ASkol lata 350ml', 'R$ 91,39', '100%', '84', '105 dias']) {
      assert.ok(linha.includes(valor), `${valor} na curva ABC: ${linha}`);
    }
    assert.ok(limpar(await dono.textContent('#rel-cards')).includes('Classe A1 produto'));
    await dono.selectOption('#rel-abc-criterio', 'margem');
    await esperarTexto(dono, '#rel-titulo', 'por margem');
    await naTela(dono, '#rel-abc-tabela');
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
