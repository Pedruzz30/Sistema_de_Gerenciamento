/*
 * Celular do atendente: entra com nome e PIN, abre a comanda pelo número ou pelo cartão,
 * monta o pedido e lança de uma vez. Tirar item e receber ficam no caixa.
 */
(() => {
'use strict';

const $ = id => document.getElementById(id);
let operadorEscolhido = null;
let atendente = null;
let cardapio = [];
let teclas = [];
let comanda = null;
let pedido = [];
let leitor = null;
let avisoTimer = null;

const moeda = valor => Number(valor || 0).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
const escapeHtml = texto => String(texto ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const semAcento = texto => String(texto || '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
const qtdTexto = q => String(Number(q)).replace('.', ',');

async function api(caminho, opcoes) {
  const resp = await fetch(caminho, Object.assign({ headers: { 'Content-Type': 'application/json' }, credentials: 'same-origin' }, opcoes || {}));
  if (resp.status === 401) {
    atendente = null;
    mostrarTela('entrar');
    carregarOperadores();
    const erro = new Error('Entre de novo com o seu PIN.');
    erro.status = 401;
    throw erro;
  }
  if (!resp.ok) {
    let corpo = {};
    try { corpo = await resp.json(); } catch (e) { corpo = {}; }
    const erro = new Error(corpo.erro || corpo.mensagem || 'Não deu certo. Tente de novo.');
    erro.status = resp.status;
    erro.codigo = corpo.codigo;
    throw erro;
  }
  return resp.status === 204 ? null : resp.json();
}

function aviso(texto, tipo) {
  const el = $('aviso');
  clearTimeout(avisoTimer);
  el.textContent = texto || '';
  el.className = 'aviso aviso--' + (tipo || 'info');
  el.hidden = !texto;
  if (texto && tipo === 'ok') avisoTimer = setTimeout(() => { el.hidden = true; }, 5000);
}

function mostrarTela(nome) {
  ['entrar', 'comandas', 'comanda'].forEach(tela => { $('tela-' + tela).hidden = tela !== nome; });
  $('quem-box').hidden = !atendente;
  window.scrollTo(0, 0);
}

// ── Entrar ──────────────────────────────────────────────────────────────

async function carregarOperadores() {
  try {
    const lista = await api('/api/auth/celular/operadores');
    $('operadores').innerHTML = lista.length
      ? lista.map(o => `<button type="button" class="operador" data-ru="${o.ru}">${escapeHtml(o.nome)}</button>`).join('')
      : '<p class="nota">Ninguém tem PIN ainda. No notebook, cada atendente cria o seu PIN ao entrar.</p>';
  } catch (erro) {
    $('operadores').innerHTML = `<p class="nota">${escapeHtml(erro.message)}</p>`;
  }
  $('pin-form').hidden = true;
  operadorEscolhido = null;
}

function escolherOperador(botao) {
  document.querySelectorAll('.operador').forEach(b => b.classList.toggle('operador--escolhido', b === botao));
  operadorEscolhido = Number(botao.dataset.ru);
  $('pin-nome').textContent = botao.textContent;
  $('pin').value = '';
  $('pin-form').hidden = false;
  $('pin').focus();
}

async function entrar(event) {
  event.preventDefault();
  const pin = $('pin').value.trim();
  if (!operadorEscolhido || !/^\d{4,6}$/.test(pin)) {
    aviso('Digite o seu PIN (4 a 6 números).', 'erro');
    return;
  }
  $('btn-entrar').disabled = true;
  try {
    const usuario = await api('/api/auth/celular/entrar', { method: 'POST', body: JSON.stringify({ ru: operadorEscolhido, pin }) });
    atendente = { ru: usuario.ru, nome: `${usuario.nome} ${usuario.sobrenome || ''}`.trim() };
    aviso('');
    await iniciar();
  } catch (erro) {
    $('pin').value = '';
    aviso(erro.message, 'erro');
    $('pin').focus();
  } finally {
    $('btn-entrar').disabled = false;
  }
}

async function iniciar() {
  $('quem').textContent = atendente.nome;
  const [opcoes, rapidas] = await Promise.all([api('/api/pdv/teclas/opcoes'), api('/api/pdv/teclas')]);
  cardapio = opcoes;
  teclas = rapidas;
  mostrarTela('comandas');
  await carregarComandas();
}

async function sair() {
  if (pedido.length && !confirm('O pedido ainda não foi lançado. Sair mesmo assim?')) return;
  try { await fetch('/api/auth/logout', { method: 'POST', credentials: 'same-origin' }); } catch (e) { /* sai mesmo assim */ }
  atendente = null;
  comanda = null;
  pedido = [];
  aviso('');
  mostrarTela('entrar');
  carregarOperadores();
}

// ── Comandas ────────────────────────────────────────────────────────────

async function carregarComandas() {
  try {
    const lista = (await api('/api/comandas')).sort((a, b) => a.numero - b.numero);
    const agora = Date.now();
    $('comandas-lista').innerHTML = lista.length ? lista.map(c => {
      const minutos = Math.max(0, Math.round((agora - new Date(c.abertaEm).getTime()) / 60000));
      const tempo = minutos < 60 ? `${minutos} min` : `${Math.floor(minutos / 60)} h ${minutos % 60} min`;
      return `<button type="button" class="comanda-card" data-numero="${c.numero}">
        <span>Comanda ${c.numero}</span>${c.cliente ? `<em>${escapeHtml(c.cliente)}</em>` : ''}<strong>${moeda(c.total)}</strong><small>${qtdTexto(c.itens)} itens · há ${tempo}</small></button>`;
    }).join('') : '<p class="nota">Nenhuma comanda aberta.</p>';
  } catch (erro) {
    if (erro.status !== 401) aviso(erro.message, 'erro');
  }
}

function numeroDigitado(texto) {
  const limpo = String(texto || '').trim();
  if (/^\d{1,4}$/.test(limpo) && Number(limpo) >= 1) return Number(limpo);
  return numeroDoCartao(limpo);
}

/** Cartão da comanda: EAN-13 que começa com 29, número nos 10 dígitos seguintes. */
function numeroDoCartao(codigo) {
  if (!/^29\d{11}$/.test(codigo)) return null;
  let soma = 0;
  for (let i = 0; i < 12; i++) soma += Number(codigo[i]) * (i % 2 === 0 ? 1 : 3);
  if ((10 - (soma % 10)) % 10 !== Number(codigo[12])) return null;
  const numero = Number(codigo.slice(2, 12));
  return numero >= 1 && numero <= 9999 ? numero : null;
}

async function abrirComanda(numero) {
  if (!numero) {
    aviso('Digite o número da comanda, de 1 a 9999.', 'erro');
    return;
  }
  try {
    comanda = await api(`/api/comandas/${numero}`);
  } catch (erro) {
    if (erro.codigo !== 'COMANDA_NAO_ENCONTRADA') {
      if (erro.status !== 401) aviso(erro.message, 'erro');
      return;
    }
    // Ainda não existe: abre de verdade quando o primeiro pedido for lançado.
    comanda = { numero, total: 0, itens: 0, linhas: [], nova: true };
  }
  pedido = [];
  $('numero-comanda').value = '';
  $('busca').value = '';
  $('cliente-comanda').value = comanda.cliente || '';
  aviso(comanda.nova ? `Comanda ${numero} nova: monte o pedido e lance.` : '', 'info');
  mostrarTela('comanda');
  renderComanda();
}

function voltarComandas() {
  if (pedido.length && !confirm('O pedido ainda não foi lançado. Voltar mesmo assim?')) return;
  pedido = [];
  comanda = null;
  aviso('');
  mostrarTela('comandas');
  carregarComandas();
}

// ── Uma comanda ─────────────────────────────────────────────────────────

function renderComanda() {
  $('comanda-titulo').textContent = `Comanda ${comanda.numero}`;
  if (document.activeElement !== $('cliente-comanda')) $('cliente-comanda').value = comanda.cliente || '';
  $('comanda-total').textContent = moeda(comanda.total);
  const linhas = comanda.linhas || [];
  $('lancados-resumo').textContent = linhas.length ? `Já lançado: ${qtdTexto(comanda.itens)} itens` : 'Nada lançado ainda';
  $('comanda-linhas').innerHTML = linhas.map(l => `<li><span>${qtdTexto(l.quantidade)} × ${escapeHtml(l.nome)}${l.variacao ? ' <small>' + escapeHtml(l.variacao) + '</small>' : ''}</span><span>${moeda(l.subtotal)}</span></li>`).join('');
  $('teclas').innerHTML = teclas.map(t => `<button type="button" class="tecla" data-variacao="${t.variacaoId}">+ ${escapeHtml(t.itemNome)}</button>`).join('');
  renderResultados();
  renderPedido();
}

function nomeDe(v) {
  return v.variacaoNome ? `${v.itemNome} · ${v.variacaoNome}` : v.itemNome;
}

function renderResultados() {
  const busca = semAcento($('busca').value.trim());
  if (!busca) { $('resultados').innerHTML = ''; return; }
  const achados = cardapio.filter(v => semAcento(v.itemNome + ' ' + v.variacaoNome).includes(busca)).slice(0, 20);
  $('resultados').innerHTML = achados.length
    ? achados.map(v => `<button type="button" class="produto" data-variacao="${v.variacaoId}"><span>${escapeHtml(v.itemNome)} <small>${escapeHtml(v.variacaoNome)}</small></span><strong>${moeda(v.precoVenda)}</strong></button>`).join('')
    : '<p class="nota">Nada com esse nome.</p>';
}

function adicionar(variacaoId) {
  const variacao = cardapio.find(v => Number(v.variacaoId) === Number(variacaoId))
    || teclas.find(v => Number(v.variacaoId) === Number(variacaoId));
  if (!variacao) return;
  const existente = pedido.find(p => p.variacaoId === Number(variacaoId));
  if (existente) existente.quantidade += 1;
  else pedido.push({ variacaoId: Number(variacaoId), nome: variacao.itemNome, variacao: variacao.variacaoNome, preco: Number(variacao.precoVenda), quantidade: 1 });
  if (navigator.vibrate) navigator.vibrate(15);
  renderPedido();
}

function mudarQuantidade(variacaoId, delta) {
  const item = pedido.find(p => p.variacaoId === Number(variacaoId));
  if (!item) return;
  item.quantidade += delta;
  if (item.quantidade <= 0) pedido = pedido.filter(p => p !== item);
  renderPedido();
}

function renderPedido() {
  $('pedido').innerHTML = pedido.length ? pedido.map(p => `
    <li><span class="nome">${escapeHtml(p.nome)}<small>${escapeHtml(p.variacao)} · ${moeda(p.preco * p.quantidade)}</small></span>
      <span class="qtd"><button type="button" data-menos="${p.variacaoId}" aria-label="Menos ${escapeHtml(p.nome)}">−</button><span>${p.quantidade}</span><button type="button" data-mais="${p.variacaoId}" aria-label="Mais ${escapeHtml(p.nome)}">+</button></span></li>`).join('')
    : '<li class="pedido-vazio">Toque nos produtos para montar o pedido.</li>';
  const quantos = pedido.reduce((s, p) => s + p.quantidade, 0);
  const valor = pedido.reduce((s, p) => s + p.preco * p.quantidade, 0);
  $('btn-lancar').disabled = quantos === 0;
  $('btn-lancar').textContent = quantos ? `Lançar ${quantos} ${quantos === 1 ? 'item' : 'itens'} · ${moeda(valor)}` : 'Lançar na comanda';
}

async function lancar() {
  if (!pedido.length || !comanda) return;
  const botao = $('btn-lancar');
  botao.disabled = true;
  const quantos = pedido.reduce((s, p) => s + p.quantidade, 0);
  try {
    comanda = await api(`/api/comandas/${comanda.numero}/lancar`, {
      method: 'POST',
      // O nome vai junto do pedido; em branco, fica o que já estava anotado.
      body: JSON.stringify({ itens: pedido.map(p => ({ variacaoId: p.variacaoId, quantidade: p.quantidade })), cliente: $('cliente-comanda').value.trim() })
    });
    pedido = [];
    $('busca').value = '';
    renderComanda();
    aviso(`${quantos} ${quantos === 1 ? 'item lançado' : 'itens lançados'} na comanda ${comanda.numero}.`, 'ok');
  } catch (erro) {
    if (erro.status !== 401) aviso(erro.message, 'erro');
  } finally {
    renderPedido();
  }
}

// ── Câmera: ler o cartão ────────────────────────────────────────────────

function carregarLeitor() {
  if (window.ZXing) return Promise.resolve();
  return new Promise((resolve, reject) => {
    const script = document.createElement('script');
    script.src = '/vendor/zxing-0.21.3.min.js';
    script.onload = resolve;
    script.onerror = () => reject(new Error('Não foi possível carregar o leitor.'));
    document.head.appendChild(script);
  });
}

async function lerCartao() {
  if (!window.isSecureContext || !navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
    aviso('A câmera precisa da conexão segura (veja "Primeira vez neste celular?"). Digite o número.', 'erro');
    return;
  }
  try {
    await carregarLeitor();
    const dicas = new Map([[ZXing.DecodeHintType.POSSIBLE_FORMATS, [ZXing.BarcodeFormat.EAN_13]]]);
    leitor = new ZXing.BrowserMultiFormatReader(dicas);
    $('camera').hidden = false;
    await leitor.decodeFromConstraints({ video: { facingMode: 'environment' } }, 'camera-video', resultado => {
      if (!resultado) return;
      const numero = numeroDoCartao(resultado.getText());
      if (!numero) return;
      fecharCamera();
      abrirComanda(numero);
    });
  } catch (erro) {
    fecharCamera();
    aviso('Câmera indisponível: ' + (erro.message || 'sem permissão') + '. Digite o número.', 'erro');
  }
}

function fecharCamera() {
  if (leitor) { try { leitor.reset(); } catch (e) { /* já parou */ } }
  leitor = null;
  $('camera').hidden = true;
}

// ── Guia do certificado: mostra o do aparelho que está abrindo ───────────

function prepararGuia() {
  const ua = navigator.userAgent || '';
  const iphone = /iPhone|iPad|iPod/.test(ua) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
  const android = /Android/.test(ua);
  // Chrome, Firefox e Edge do iPhone não instalam perfil: só o Safari.
  const foraDoSafari = iphone && /CriOS|FxiOS|EdgiOS|OPiOS|GSA\//.test(ua);
  $('ajuda-iphone').hidden = android;
  $('ajuda-android').hidden = iphone;
  $('aviso-safari').hidden = !foraDoSafari;
  $('link-perfil-iphone').hidden = foraDoSafari;
}

// ── Ligações ────────────────────────────────────────────────────────────

prepararGuia();
$('operadores').addEventListener('click', event => {
  const botao = event.target.closest('.operador');
  if (botao) escolherOperador(botao);
});
$('pin-form').addEventListener('submit', entrar);
$('btn-sair').addEventListener('click', sair);
$('abrir-form').addEventListener('submit', event => { event.preventDefault(); abrirComanda(numeroDigitado($('numero-comanda').value)); });
$('btn-ler-cartao').addEventListener('click', lerCartao);
$('btn-fechar-camera').addEventListener('click', fecharCamera);
$('comandas-lista').addEventListener('click', event => {
  const card = event.target.closest('.comanda-card');
  if (card) abrirComanda(Number(card.dataset.numero));
});
$('btn-voltar').addEventListener('click', voltarComandas);
$('busca').addEventListener('input', renderResultados);
$('teclas').addEventListener('click', event => {
  const botao = event.target.closest('[data-variacao]');
  if (botao) adicionar(botao.dataset.variacao);
});
$('resultados').addEventListener('click', event => {
  const botao = event.target.closest('[data-variacao]');
  if (botao) adicionar(botao.dataset.variacao);
});
$('pedido').addEventListener('click', event => {
  const menos = event.target.closest('[data-menos]');
  const mais = event.target.closest('[data-mais]');
  if (menos) mudarQuantidade(menos.dataset.menos, -1);
  if (mais) mudarQuantidade(mais.dataset.mais, 1);
});
$('btn-lancar').addEventListener('click', lancar);

// Já entrou antes neste celular? Continua de onde estava.
(async () => {
  try {
    const sessao = await api('/api/auth/celular/sessao');
    atendente = { ru: sessao.ru, nome: sessao.nome };
    await iniciar();
  } catch (erro) {
    if (erro.status !== 401) carregarOperadores();
  }
})();
})();
