/* Relatório do dia e do mês. Os números vêm prontos do servidor; aqui só se monta a página. */
let modoRelatorio = 'dia';

const moedaRel = valor => new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(Number(valor || 0));
const numeroRel = valor => String(Number(valor || 0)).replace('.', ',');
const doisDigitos = n => String(n).padStart(2, '0');

function hojeLocal() {
  const d = new Date();
  return `${d.getFullYear()}-${doisDigitos(d.getMonth() + 1)}-${doisDigitos(d.getDate())}`;
}

function escolherModoRelatorio(modo) {
  modoRelatorio = modo;
  document.getElementById('rel-modo-dia').classList.toggle('pdv-novo-aba--ativa', modo === 'dia');
  document.getElementById('rel-modo-mes').classList.toggle('pdv-novo-aba--ativa', modo === 'mes');
  document.getElementById('rel-data').hidden = modo !== 'dia';
  document.getElementById('rel-mes').hidden = modo !== 'mes';
  document.getElementById('rel-bloco-horas').hidden = modo !== 'dia';
  document.getElementById('rel-bloco-operadores').hidden = modo !== 'dia';
  document.getElementById('rel-bloco-dias').hidden = modo !== 'mes';
  carregarRelatorio();
}

function andarRelatorio(passo) {
  if (modoRelatorio === 'dia') {
    const campo = document.getElementById('rel-data');
    const [a, m, d] = campo.value.split('-').map(Number);
    const data = new Date(a, m - 1, d + passo);
    campo.value = `${data.getFullYear()}-${doisDigitos(data.getMonth() + 1)}-${doisDigitos(data.getDate())}`;
  } else {
    const campo = document.getElementById('rel-mes');
    const [a, m] = campo.value.split('-').map(Number);
    const data = new Date(a, m - 1 + passo, 1);
    campo.value = `${data.getFullYear()}-${doisDigitos(data.getMonth() + 1)}`;
  }
  carregarRelatorio();
}

async function carregarRelatorio() {
  const alerta = document.getElementById('alert-relatorio');
  alerta.className = 'alert-modal';
  alerta.textContent = '';
  const url = modoRelatorio === 'dia'
    ? `/api/relatorios/dia?data=${encodeURIComponent(document.getElementById('rel-data').value)}`
    : `/api/relatorios/mes?mes=${encodeURIComponent(document.getElementById('rel-mes').value)}`;
  try {
    const resp = await fetch(url);
    if (!resp.ok) throw new Error(await safeReadErrorMessage(resp, 'Não foi possível montar o relatório.'));
    renderRelatorio(await resp.json());
  } catch (error) {
    alerta.textContent = error.message;
    alerta.className = 'alert-modal error';
  }
}

function tabela(cabecalhos, linhas, numericas = []) {
  if (!linhas.length) return '<p class="inline-note">Nada neste período.</p>';
  const th = cabecalhos.map((c, i) => `<th class="${numericas.includes(i) ? 'num' : ''}">${escapeHtml(c)}</th>`).join('');
  const tr = linhas.map(l => '<tr>' + l.map((c, i) => `<td class="${numericas.includes(i) ? 'num' : ''}">${c}</td>`).join('') + '</tr>').join('');
  return `<div class="resumo-operadores-rolagem"><table class="resumo-operadores"><thead><tr>${th}</tr></thead><tbody>${tr}</tbody></table></div>`;
}

function renderRelatorio(dados) {
  const r = dados.resumo;
  const titulo = modoRelatorio === 'dia'
    ? new Date(dados.data + 'T12:00:00').toLocaleDateString('pt-BR', { weekday: 'long', day: '2-digit', month: 'long', year: 'numeric' })
    : new Date(dados.mes + '-15T12:00:00').toLocaleDateString('pt-BR', { month: 'long', year: 'numeric' });
  document.getElementById('rel-titulo').textContent = titulo.charAt(0).toUpperCase() + titulo.slice(1);

  const vendidoItens = Number(r.margemEstimada) + Number(r.custoEstimado);
  const percentualMargem = vendidoItens > 0 ? Math.round(Number(r.margemEstimada) / vendidoItens * 100) : 0;
  const validas = r.vendas - r.cancelamentos;
  const cards = [
    ['Faturamento', moedaRel(r.liquido), r.cancelado > 0 ? `já sem ${moedaRel(r.cancelado)} cancelados` : 'vendas menos cancelamentos'],
    ['Vendas', String(validas), r.cancelamentos > 0 ? `${r.cancelamentos} cancelada${r.cancelamentos === 1 ? '' : 's'}` : 'nenhuma cancelada'],
    ['Ticket médio', moedaRel(r.ticketMedio), 'por venda'],
    ['Margem estimada', moedaRel(r.margemEstimada), `${percentualMargem}% do vendido, pelo custo médio`],
    ['Descontos', moedaRel(r.descontos), 'dados no caixa'],
    ['Comandas pagas', String(r.comandasPagas), 'consumo na loja']
  ];
  document.getElementById('rel-cards').innerHTML = cards.map(([rotulo, valor, detalhe]) => `
    <div class="relatorio-card"><span>${escapeHtml(rotulo)}</span><strong>${escapeHtml(valor)}</strong><small>${escapeHtml(detalhe)}</small></div>`).join('');

  document.getElementById('rel-formas').innerHTML = tabela(['Forma', 'Valor'],
    dados.porForma.map(f => [escapeHtml(f.rotulo), moedaRel(f.valor)]), [1]);

  document.getElementById('rel-extras').innerHTML = tabela(['', 'Valor'], [
    ['Vendido', moedaRel(r.bruto)],
    ['Cancelado (devolvido)', r.cancelado > 0 ? '-' + moedaRel(r.cancelado) : moedaRel(0)],
    ['Sangrias', moedaRel(r.sangrias)],
    ['Suprimentos', moedaRel(r.suprimentos)]
  ], [1]);

  if (modoRelatorio === 'dia') {
    const maior = Math.max(1, ...dados.porHora.map(h => Number(h.valor)));
    document.getElementById('rel-horas').innerHTML = dados.porHora.length
      ? dados.porHora.map(h => `<div class="relatorio-hora"><span>${doisDigitos(h.hora)}h</span>
          <div class="relatorio-barra"><i style="width:${Math.round(Number(h.valor) / maior * 100)}%"></i></div>
          <span>${h.vendas} · ${moedaRel(h.valor)}</span></div>`).join('')
      : '<p class="inline-note">Nada neste período.</p>';
    document.getElementById('rel-operadores').innerHTML = tabela(
      ['Operador', 'Vendas', 'Vendido', 'Dinheiro', 'Pix e cartões', 'Cancelou', 'Sangrias'],
      dados.porOperador.map(o => [escapeHtml(o.operador), o.vendas, moedaRel(o.vendido), moedaRel(o.dinheiro), moedaRel(o.pixECartoes),
        o.cancelamentos > 0 ? `${o.cancelamentos} · ${moedaRel(o.cancelado)}` : '—', Number(o.sangrias) > 0 ? moedaRel(o.sangrias) : '—']),
      [1, 2, 3, 4, 5, 6]);
  } else {
    document.getElementById('rel-dias').innerHTML = tabela(['Dia', 'Vendas', 'Faturamento'],
      dados.dias.map(d => [new Date(d.data + 'T12:00:00').toLocaleDateString('pt-BR', { weekday: 'short', day: '2-digit', month: '2-digit' }),
        d.vendas, moedaRel(d.liquido)]), [1, 2]);
  }

  document.getElementById('rel-produtos').innerHTML = tabela(['Produto', 'Quantidade', 'Vendido', 'Margem estimada'],
    dados.produtos.slice(0, 30).map(p => {
      const margem = Number(p.valor) - Number(p.custo);
      return [escapeHtml(p.nome), numeroRel(p.quantidade), moedaRel(p.valor), Number(p.custo) > 0 ? moedaRel(margem) : '—'];
    }), [1, 2, 3]);
}

document.addEventListener('DOMContentLoaded', () => {
  const hoje = hojeLocal();
  document.getElementById('rel-data').value = hoje;
  document.getElementById('rel-mes').value = hoje.slice(0, 7);
  carregarRelatorio();
});
