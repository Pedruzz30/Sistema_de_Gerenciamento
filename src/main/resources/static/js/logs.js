// ── logs.js ────────────────────────────────────────────────────────────────
// System audit log viewer: fetch, display, filter.

let todosLogs = [];

// ── Init ──────────────────────────────────────────────────────────────────

document.addEventListener('DOMContentLoaded', () => {
  carregarLogs();
});

// ── API ───────────────────────────────────────────────────────────────────

async function carregarLogs() {
  document.getElementById('tabela-logs').innerHTML =
    '<tr><td colspan="5" class="empty">Carregando logs...</td></tr>';

  try {
    const resp = await fetch('/api/logs');
    if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
    todosLogs = await resp.json();
    renderTabela();
  } catch (err) {
    document.getElementById('tabela-logs').innerHTML =
      '<tr><td colspan="5" class="empty">Erro ao carregar logs. Verifique o servidor.</td></tr>';
    console.error(err);
  }
}

// ── Render ────────────────────────────────────────────────────────────────

function renderResumo(listaVisivel) {
  const container = document.getElementById('logs-summary');
  if (!container) return;

  const usuariosUnicos = new Set(todosLogs.map(log => String(log.usuarioRu || 'sem-ru'))).size;
  const maisRecente = todosLogs.length > 0
    ? new Date(todosLogs[0].dataHora)
    : null;

  container.innerHTML = [
    {
      label: 'Registros totais',
      value: todosLogs.length,
      sub: 'Histórico carregado nesta sessão.'
    },
    {
      label: 'Usuários distintos',
      value: usuariosUnicos,
      sub: 'RUs diferentes presentes na auditoria.'
    },
    {
      label: 'Última atualização',
      value: maisRecente ? maisRecente.toLocaleDateString('pt-BR') : '—',
      sub: `${listaVisivel.length} registro(s) visível(is) com o filtro atual.`
    }
  ].map(card => `
    <div class="page-summary-card">
      <div class="page-summary-label">${card.label}</div>
      <div class="page-summary-value">${card.value}</div>
      <div class="page-summary-sub">${card.sub}</div>
    </div>
  `).join('');
}

function renderTabela() {
  const busca = document.getElementById('busca').value.toLowerCase();
  const lista = busca
    ? todosLogs.filter(l =>
        (l.acao || '').toLowerCase().includes(busca) ||
        (l.descricao || '').toLowerCase().includes(busca) ||
        String(l.usuarioRu || '').includes(busca)
      )
    : todosLogs;

  document.getElementById('table-count').textContent = `${lista.length} registro(s)`;
  renderResumo(lista);

  const tbody = document.getElementById('tabela-logs');
  if (lista.length === 0) {
    if (todosLogs.length === 0) {
      tbody.innerHTML = `
        <tr><td colspan="5" class="table-empty-cell">
          ${emptyStateMarkup({
            icon: 'clipboard',
            title: 'Nenhuma ação registrada',
            copy: 'Os logs de auditoria aparecerão aqui conforme o sistema for utilizado.'
          })}
        </td></tr>`;
    } else {
      tbody.innerHTML = '<tr><td colspan="5" class="empty">Nenhum log encontrado para a busca aplicada.</td></tr>';
    }
    return;
  }

  tbody.innerHTML = lista.map(l => {
    const dt = new Date(l.dataHora);
    const data = dt.toLocaleDateString('pt-BR', { day:'2-digit', month:'2-digit', year:'numeric' });
    const hora = dt.toLocaleTimeString('pt-BR', { hour:'2-digit', minute:'2-digit', second:'2-digit' });

    return `
      <tr>
        <td class="table-cell-subtle">${l.id}</td>
        <td>
          <div class="table-cell-stack">
            <span class="table-cell-strong">${data}</span>
            <span class="table-cell-caption">${hora}</span>
          </div>
        </td>
        <td class="table-cell-muted table-cell-subtle">
          ${l.usuarioRu != null ? `RU ${l.usuarioRu}` : '—'}
        </td>
        <td><span class="log-acao">${l.acao || '—'}</span></td>
        <td class="table-cell-muted">${escapeHtml(l.descricao || '—')}</td>
      </tr>
    `;
  }).join('');
}

function filtrar() {
  renderTabela();
}
