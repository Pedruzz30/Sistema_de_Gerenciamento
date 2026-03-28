// ── cotacoes.js ────────────────────────────────────────────────────────────
// Monthly price quotes: register, compare, history, reports.

let todosProdutos = [];

// ── Init ──────────────────────────────────────────────────────────────────

document.addEventListener('DOMContentLoaded', () => {
  // Default month: current
  const agora = new Date();
  const mesAtual = `${agora.getFullYear()}-${String(agora.getMonth()+1).padStart(2,'0')}`;
  document.getElementById('relatorio-mes').value = mesAtual;
  carregarProdutos();
});

async function carregarProdutos() {
  try {
    const resp = await fetch('/api/produtos');
    todosProdutos = await resp.json();

    const selectCotacao = document.getElementById('c-produto');
    const selectHistorico = document.getElementById('historico-produto');

    const options = `<option value="">— selecione um produto —</option>` +
      todosProdutos.map(p => `<option value="${p.id}">${p.nome}</option>`).join('');
    selectCotacao.innerHTML = options;
    selectHistorico.innerHTML = options;
  } catch {
    document.getElementById('c-produto').innerHTML = '<option value="">Erro ao carregar</option>';
  }
}

// ── Tab management ────────────────────────────────────────────────────────

function abrirAba(abaId) {
  document.querySelectorAll('.tab-panel').forEach(p => p.classList.remove('active'));
  document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
  document.getElementById(abaId).classList.add('active');
  if (abaId === 'aba-relatorio') document.getElementById('tab-relatorio').classList.add('active');
  if (abaId === 'aba-historico') document.getElementById('tab-historico').classList.add('active');
}

// ── Relatório mensal ──────────────────────────────────────────────────────

async function carregarRelatorio() {
  const mes = document.getElementById('relatorio-mes').value;
  if (!mes) { alert('Selecione um mês.'); return; }

  const container = document.getElementById('relatorio-container');
  container.innerHTML = '<div class="loading">Carregando relatório</div>';

  try {
    const resp = await fetch(`/api/cotacoes/relatorio?mes=${mes}`);
    const data = await resp.json();

    if (!resp.ok) {
      container.innerHTML = `<div class="empty">${data.erro || 'Erro ao carregar.'}</div>`; return;
    }

    const comparacoes = data.comparacoes || [];
    if (comparacoes.length === 0) {
      container.innerHTML = `
        <div class="alert-modal" style="display:block; background:rgba(245,158,11,.08); border:1px solid rgba(245,158,11,.2); color:#fbbf24; padding:14px; border-radius:8px;">
          ⚠ Sem dados suficientes para comparar ${formatarMes(mes)} com o mês anterior.
          Registre cotações para pelo menos dois meses consecutivos.
        </div>`;
      return;
    }

    container.innerHTML = `
      <p style="font-size:12px; color:var(--text-3); margin-bottom:12px">
        Comparando <strong>${formatarMes(mes)}</strong> com mês anterior — ${comparacoes.length} produto(s) com dados
      </p>
      <div>
        ${comparacoes.map(c => renderComparacao(c)).join('')}
      </div>`;
  } catch {
    container.innerHTML = '<div class="empty">Erro ao carregar relatório.</div>';
  }
}

function renderComparacao(c) {
  const tend = c.tendencia.toLowerCase();
  const icon = { subiu: '↑', caiu: '↓', estavel: '=' }[tend] || '=';
  const variacaoStr = c.variacaoAbsoluta >= 0
    ? `+R$${c.variacaoAbsoluta.toFixed(2)} (+${c.variacaoPercentual.toFixed(1)}%)`
    : `-R$${Math.abs(c.variacaoAbsoluta).toFixed(2)} (${c.variacaoPercentual.toFixed(1)}%)`;

  return `
    <div class="comparacao-item ${tend}">
      <span class="comp-produto">${c.produtoNome}</span>
      <span class="comp-preco">R$${c.precoAnterior.toFixed(2)} → R$${c.precoAtual.toFixed(2)}</span>
      <span class="comp-variacao ${tend}">${icon} ${variacaoStr}</span>
    </div>`;
}

// ── Histórico por produto ─────────────────────────────────────────────────

async function carregarHistorico() {
  const produtoId = document.getElementById('historico-produto').value;
  if (!produtoId) { alert('Selecione um produto.'); return; }

  const container = document.getElementById('historico-container');
  container.innerHTML = '<div class="loading">Carregando histórico</div>';

  try {
    const resp = await fetch(`/api/cotacoes/produto/${produtoId}/historico`);
    const historico = await resp.json();

    if (!resp.ok) {
      container.innerHTML = `<div class="empty">${historico.erro || 'Erro.'}</div>`; return;
    }

    if (historico.length === 0) {
      container.innerHTML = '<div class="empty">Nenhuma cotação registrada para este produto.</div>';
      return;
    }

    const produto = todosProdutos.find(p => p.id === parseInt(produtoId));
    container.innerHTML = `
      <p style="font-size:12px; color:var(--text-3); margin-bottom:14px">
        Histórico de <strong>${produto ? produto.nome : 'produto'}</strong> — ${historico.length} registro(s)
      </p>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Mês</th>
              <th>Preço Unit.</th>
              <th>Qtd. Comprada</th>
              <th>Gasto Total</th>
            </tr>
          </thead>
          <tbody>
            ${historico.map(c => `
              <tr>
                <td>${formatarMes(c.mes)}</td>
                <td style="color:var(--text-1); font-weight:500">R$${c.precoUnitario.toFixed(2)}</td>
                <td style="color:var(--text-2)">${c.quantidadeComprada} un.</td>
                <td style="color:var(--green); font-weight:600">R$${c.gastoTotal.toFixed(2)}</td>
              </tr>`).join('')}
          </tbody>
        </table>
      </div>`;
  } catch {
    container.innerHTML = '<div class="empty">Erro ao carregar histórico.</div>';
  }
}

// ── Registrar cotação ─────────────────────────────────────────────────────

async function registrarCotacao() {
  const produtoId = parseInt(document.getElementById('c-produto').value);
  const mes = document.getElementById('c-mes').value;
  const preco = parseFloat(document.getElementById('c-preco').value);
  const qtd = parseInt(document.getElementById('c-qtd').value);

  if (!produtoId) { mostrarAlert('alert-cotacao', 'Selecione um produto.', 'error'); return; }
  if (!mes)       { mostrarAlert('alert-cotacao', 'Informe o mês de referência.', 'error'); return; }
  if (!preco || preco <= 0) { mostrarAlert('alert-cotacao', 'Preço deve ser maior que zero.', 'error'); return; }
  if (!qtd || qtd <= 0)    { mostrarAlert('alert-cotacao', 'Quantidade deve ser maior que zero.', 'error'); return; }

  const resp = await fetch('/api/cotacoes', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ produtoId, mes, precoUnitario: preco, quantidadeComprada: qtd })
  });
  const data = await resp.json();
  if (!resp.ok) {
    mostrarAlert('alert-cotacao', data.erro || 'Erro ao registrar.', 'error'); return;
  }
  fecharModal('modal-cotacao');
  mostrarAlert('alert-cotacao', '', '');
}

// ── Helpers ───────────────────────────────────────────────────────────────

function formatarMes(mesStr) {
  try {
    const [ano, mes] = mesStr.split('-');
    const meses = ['Jan','Fev','Mar','Abr','Mai','Jun','Jul','Ago','Set','Out','Nov','Dez'];
    return `${meses[parseInt(mes)-1]}/${ano}`;
  } catch { return mesStr; }
}

function abrirModalCotacao() {
  limparAlert('alert-cotacao');
  document.getElementById('c-produto').value = '';
  document.getElementById('c-preco').value = '';
  document.getElementById('c-qtd').value = '';
  const agora = new Date();
  document.getElementById('c-mes').value =
    `${agora.getFullYear()}-${String(agora.getMonth()+1).padStart(2,'0')}`;
  abrirModal('modal-cotacao');
}

function abrirAba_r(id) { abrirAba(id); }

function abrirModal(id) { document.getElementById(id).classList.add('open'); }
function fecharModal(id) { document.getElementById(id).classList.remove('open'); }
function fecharModalSeClicouFora(e, id) { if (e.target.id === id) fecharModal(id); }
function mostrarAlert(id, msg, tipo) {
  const el = document.getElementById(id);
  el.textContent = msg;
  el.className = tipo ? `alert-modal ${tipo}` : 'alert-modal';
}
function limparAlert(id) {
  const el = document.getElementById(id);
  if (el) { el.textContent = ''; el.className = 'alert-modal'; }
}
