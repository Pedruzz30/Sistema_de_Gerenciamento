/* Folha de códigos para o balcão: um código de barras por item que não tem código de fábrica. */
let folhaCodigos = [];

function ehCodigoDaLoja(codigo) {
  return codigo.tipo === 'INTERNO' || (codigo.tipo === 'EAN13' && codigo.codigo.startsWith('2'));
}

function moedaFolha(valor) {
  return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(Number(valor || 0));
}

function mostrarAlertaFolha(mensagem, tipo) {
  const alerta = document.getElementById('alert-folha');
  alerta.textContent = mensagem;
  alerta.className = `alert-modal ${tipo}`;
}

async function carregarFolhaCodigos() {
  try {
    const [respCodigos, respFaltando] = await Promise.all([
      fetch('/api/pdv/codigos'),
      fetch('/api/pdv/codigos/sem-codigo')
    ]);
    if (!respCodigos.ok) throw new Error(await safeReadErrorMessage(respCodigos, 'Não foi possível carregar os códigos.'));
    folhaCodigos = (await respCodigos.json()).filter(c => c.padrao);
    const faltando = respFaltando.ok ? await respFaltando.json() : [];
    renderFaltando(faltando);
    renderFolhaCodigos();
  } catch (error) {
    mostrarAlertaFolha(error.message, 'error');
  }
}

function renderFaltando(faltando) {
  const secao = document.getElementById('folha-faltando');
  if (!faltando.length) { secao.hidden = true; return; }
  const nomes = faltando.slice(0, 6).map(v => `${v.itemNome} ${v.variacaoNome}`).join(', ');
  document.getElementById('folha-faltando-texto').textContent =
    `${faltando.length} ${faltando.length === 1 ? 'item ainda não tem código' : 'itens ainda não têm código'}: ${nomes}${faltando.length > 6 ? '…' : ''}.`;
  const podeCriar = temPermissao(PERMISSOES.EDITAR_ESTOQUE);
  document.getElementById('btn-gerar-codigos').hidden = !podeCriar;
  secao.hidden = false;
}

function renderFolhaCodigos() {
  const todos = document.getElementById('folha-todos').checked;
  const lista = folhaCodigos
    .filter(c => todos || ehCodigoDaLoja(c))
    .sort((a, b) => `${a.variacao.itemNome} ${a.variacao.variacaoNome}`.localeCompare(`${b.variacao.itemNome} ${b.variacao.variacaoNome}`, 'pt-BR'));
  const area = document.getElementById('folha-etiquetas');
  if (lista.length === 0) {
    area.innerHTML = '<p class="inline-note">Nenhum código para mostrar. Itens sem código aparecem no aviso acima.</p>';
    return;
  }
  area.innerHTML = lista.map(c => {
    const barras = window.StockOSCodigoBarras.ehEan13(c.codigo)
      ? window.StockOSCodigoBarras.svgEan13(c.codigo)
      : `<div class="etiqueta-digitar">Digite <strong>${escapeHtml(c.codigo)}</strong></div>`;
    return `<article class="etiqueta" data-codigo="${escapeHtml(c.codigo)}">
        <div class="etiqueta-nome">${escapeHtml(c.variacao.itemNome)}</div>
        <div class="etiqueta-detalhe">${escapeHtml(c.variacao.variacaoNome)} · ${moedaFolha(c.variacao.precoVenda)}</div>
        ${barras}
      </article>`;
  }).join('');
}

async function gerarCodigosFaltando() {
  const botao = document.getElementById('btn-gerar-codigos');
  setLoading(botao, true);
  try {
    const resp = await fetch('/api/pdv/codigos/gerar-internos', { method: 'POST' });
    if (!resp.ok) throw new Error(await safeReadErrorMessage(resp, 'Não foi possível criar os códigos.'));
    const criados = await resp.json();
    mostrarAlertaFolha(`${criados.length} ${criados.length === 1 ? 'código criado' : 'códigos criados'}. Já podem ser bipados no caixa.`, 'success');
    await carregarFolhaCodigos();
  } catch (error) {
    mostrarAlertaFolha(error.message, 'error');
  } finally {
    setLoading(botao, false);
  }
}

document.addEventListener('DOMContentLoaded', carregarFolhaCodigos);
