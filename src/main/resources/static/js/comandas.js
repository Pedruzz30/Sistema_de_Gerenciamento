/* Comandas abertas: o que cada mesa já consumiu, ao vivo, e o atalho para cobrar no caixa. */
(() => {
'use strict';

let comandas = [];
let selecionada = null;
let atualizador = null;

// Apoio próprio: esta tela não carrega o caixas.js, onde ficam os do caixa.
const escapeHtml = texto => String(texto ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
function mostrarAlert(id, mensagem, tipo) {
  const el = document.getElementById(id);
  if (el) { el.textContent = mensagem; el.className = `alert-modal ${tipo}`; }
}
function limparAlert(id) {
  const el = document.getElementById(id);
  if (el) { el.textContent = ''; el.className = 'alert-modal'; }
}
async function lerErro(resp, padrao) {
  try { const corpo = await resp.json(); return corpo.erro || corpo.mensagem || padrao; } catch { return padrao; }
}

// Nome de variação que não diz nada (item de uma variação só).
const VARIACOES_GENERICAS = new Set(['unidade', 'padrão', 'padrao', 'única', 'unica', 'normal']);
const moeda = valor => Number(valor || 0).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
const quantidade = q => String(Number(q)).replace('.', ',');

function tempoAberta(abertaEm) {
  const minutos = Math.max(0, Math.round((Date.now() - new Date(abertaEm).getTime()) / 60000));
  if (minutos < 1) return 'agora há pouco';
  if (minutos < 60) return `há ${minutos} min`;
  return `há ${Math.floor(minutos / 60)} h ${String(minutos % 60).padStart(2, '0')} min`;
}

async function stockOSMount_comandas() {
  if (typeof paginaAtual === 'function' && paginaAtual() !== 'comandas') return;
  await carregarComandas();
  if (!atualizador) atualizador = setInterval(() => { if (!document.hidden) carregarComandas(); }, 10000);
}

async function carregarComandas() {
  try {
    const resp = await fetch('/api/comandas');
    if (!resp.ok) throw new Error(await lerErro(resp, 'Não foi possível carregar as comandas.'));
    comandas = (await resp.json()).sort((a, b) => a.numero - b.numero);
    limparAlert('alert-comandas');
    renderResumo();
    renderGrade();
    if (selecionada && !comandas.some(c => c.numero === selecionada)) {
      selecionada = null;
      mostrarDetalhe(null);
    } else if (selecionada) {
      await carregarDetalhe(selecionada);
    }
    document.getElementById('comandas-atualizado').textContent =
      'Atualizado às ' + new Date().toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
  } catch (error) {
    mostrarAlert('alert-comandas', error.message, 'error');
  }
}

function renderResumo() {
  const total = comandas.reduce((soma, c) => soma + Number(c.total || 0), 0);
  const itens = comandas.reduce((soma, c) => soma + Number(c.itens || 0), 0);
  const maisAntiga = comandas.reduce((antiga, c) => (!antiga || new Date(c.abertaEm) < new Date(antiga.abertaEm) ? c : antiga), null);
  document.getElementById('comandas-resumo').innerHTML = [
    { label: 'Abertas', value: comandas.length, sub: comandas.length === 1 ? 'mesa consumindo agora' : 'mesas consumindo agora' },
    { label: 'Em aberto', value: moeda(total), sub: 'a receber quando pagarem' },
    { label: 'Itens lançados', value: quantidade(itens), sub: 'em todas as comandas abertas' },
    { label: 'Mais antiga', value: maisAntiga ? `Comanda ${maisAntiga.numero}` : '—', sub: maisAntiga ? 'aberta ' + tempoAberta(maisAntiga.abertaEm) : 'nenhuma aberta' }
  ].map(card => `
    <div class="page-summary-card">
      <div class="page-summary-label">${card.label}</div>
      <div class="page-summary-value">${escapeHtml(String(card.value))}</div>
      <div class="page-summary-sub">${escapeHtml(card.sub)}</div>
    </div>`).join('');
}

function renderGrade() {
  document.getElementById('comandas-contagem').textContent = `${comandas.length} aberta${comandas.length === 1 ? '' : 's'}`;
  const grade = document.getElementById('comandas-grade');
  if (!comandas.length) {
    grade.innerHTML = '<div class="empty">Nenhuma comanda aberta. Para abrir uma, use "+ Nova comanda", bipe o cartão no caixa ou digite o número no celular do atendente.</div>';
    return;
  }
  grade.innerHTML = comandas.map(c => `
    <button type="button" class="comanda-cartao${c.numero === selecionada ? ' comanda-cartao--ativa' : ''}" data-comanda="${c.numero}">
      <span class="comanda-cartao__numero">Comanda ${c.numero}</span>
      <strong class="comanda-cartao__total">${moeda(c.total)}</strong>
      <span class="comanda-cartao__info">${quantidade(c.itens)} ${Number(c.itens) === 1 ? 'item' : 'itens'} · aberta ${tempoAberta(c.abertaEm)}</span>
    </button>`).join('');
}

async function carregarDetalhe(numero) {
  const resp = await fetch(`/api/comandas/${numero}`);
  if (!resp.ok) {
    mostrarDetalhe(null);
    return;
  }
  mostrarDetalhe(await resp.json());
}

function mostrarDetalhe(detalhe) {
  document.getElementById('comandas-detalhe-vazio').hidden = !!detalhe;
  document.getElementById('comandas-detalhe-conteudo').hidden = !detalhe;
  if (!detalhe) return;
  document.getElementById('comandas-detalhe-titulo').textContent = `Comanda ${detalhe.numero}`;
  document.getElementById('comandas-detalhe-total').textContent = moeda(detalhe.total);
  const hora = new Date(detalhe.abertaEm).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
  document.getElementById('comandas-detalhe-info').textContent = `Aberta às ${hora} (${tempoAberta(detalhe.abertaEm)}) · ${quantidade(detalhe.itens)} itens`;
  document.getElementById('comandas-detalhe-linhas').innerHTML = (detalhe.linhas || []).length
    ? detalhe.linhas.map(l => `<li><span>${quantidade(l.quantidade)} × ${escapeHtml(l.nome)}${l.variacao && !VARIACOES_GENERICAS.has(l.variacao.toLowerCase()) ? ` <small>${escapeHtml(l.variacao)}</small>` : ''}</span><span>${moeda(l.subtotal)}</span></li>`).join('')
    : '<li class="inline-note">Nada lançado ainda.</li>';
}

async function selecionarComanda(numero) {
  selecionada = numero;
  renderGrade();
  await carregarDetalhe(numero);
}

// ── Nova comanda ─────────────────────────────────────────────────────────
// Com caixa aberto, vai direto para o PDV já na comanda nova (para bipar o pedido).
// Sem caixa aberto, a comanda abre vazia aqui; os itens entram pelo celular ou depois no caixa.

let caixaAbertoNovaComanda = null;

async function abrirNovaComanda() {
  limparAlert('alert-nova-comanda');
  const campo = document.getElementById('nova-comanda-numero');
  const ocupados = new Set(comandas.map(c => c.numero));
  let livre = 1;
  while (ocupados.has(livre) && livre < 9999) livre++;
  campo.value = String(livre);
  caixaAbertoNovaComanda = null;
  try {
    const resp = await fetch('/api/caixas');
    if (resp.ok) {
      const abertos = (await resp.json()).filter(c => c.status === 'ABERTO').sort((a, b) => a.numeroCaixa - b.numeroCaixa);
      caixaAbertoNovaComanda = abertos.length ? abertos[0].numeroCaixa : null;
    }
  } catch (e) {
    caixaAbertoNovaComanda = null;
  }
  document.getElementById('nova-comanda-explica').textContent = caixaAbertoNovaComanda
    ? `Abre o PDV no Caixa ${caixaAbertoNovaComanda} já com a comanda, para bipar o que o cliente pedir.`
    : 'Nenhum caixa aberto: a comanda fica aberta aqui, vazia. Os itens entram pelo celular do atendente ou quando abrirem um caixa.';
  document.getElementById('modal-nova-comanda').classList.add('open');
  campo.focus();
  campo.select();
}

function fecharNovaComanda() {
  document.getElementById('modal-nova-comanda').classList.remove('open');
}

async function criarComanda() {
  limparAlert('alert-nova-comanda');
  const numero = Number(document.getElementById('nova-comanda-numero').value);
  if (!Number.isInteger(numero) || numero < 1 || numero > 9999) {
    mostrarAlert('alert-nova-comanda', 'Digite o número do cartão, de 1 a 9999.', 'error');
    return;
  }
  if (comandas.some(c => c.numero === numero)) {
    mostrarAlert('alert-nova-comanda', `A comanda ${numero} já está aberta. Escolha outro número ou toque nela na lista.`, 'error');
    return;
  }
  if (caixaAbertoNovaComanda) {
    window.location.href = `caixas.html?comanda=${numero}`;
    return;
  }
  const botao = document.getElementById('btn-criar-comanda');
  botao.disabled = true;
  try {
    const resp = await fetch(`/api/comandas/${numero}/abrir`, { method: 'POST' });
    if (!resp.ok) throw new Error(await lerErro(resp, 'Não foi possível abrir a comanda.'));
    fecharNovaComanda();
    await carregarComandas();
    await selecionarComanda(numero);
    mostrarAlert('alert-comandas', `Comanda ${numero} aberta. Para lançar, use o celular do atendente ou abra um caixa.`, 'success');
  } catch (error) {
    mostrarAlert('alert-nova-comanda', error.message, 'error');
  } finally {
    botao.disabled = false;
  }
}

document.addEventListener('keydown', event => {
  if (!document.getElementById('modal-nova-comanda')?.classList.contains('open')) return;
  if (event.key === 'Enter' && event.target && event.target.id === 'nova-comanda-numero') { event.preventDefault(); criarComanda(); }
  if (event.key === 'Escape') fecharNovaComanda();
});

function cobrarComanda() {
  if (!selecionada) return;
  window.location.href = `caixas.html?comanda=${selecionada}`;
}

document.addEventListener('click', event => {
  const cartao = event.target.closest && event.target.closest('[data-comanda]');
  if (cartao && document.getElementById('comandas-grade')?.contains(cartao)) selecionarComanda(Number(cartao.dataset.comanda));
});

window.StockOSPages = window.StockOSPages || {};
window.StockOSPages.comandas = {
  mount: stockOSMount_comandas,
  activate() {
    Object.assign(window, { carregarComandas, cobrarComanda, selecionarComanda, abrirNovaComanda, fecharNovaComanda, criarComanda });
  }
};
if (/^\/comandas(?:\.html)?\/?$/.test(location.pathname)) {
  window.StockOSPages.comandas.activate();
}
document.addEventListener('DOMContentLoaded', stockOSMount_comandas);
})();
