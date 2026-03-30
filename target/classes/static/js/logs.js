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

  const tbody = document.getElementById('tabela-logs');
  if (lista.length === 0) {
    if (todosLogs.length === 0) {
      tbody.innerHTML = `
        <tr><td colspan="5">
          <div style="padding:40px;text-align:center">
            <div style="font-size:40px;margin-bottom:12px">📋</div>
            <div style="color:var(--text-2);font-weight:500;margin-bottom:6px">Nenhuma ação registrada</div>
            <div style="color:var(--text-3);font-size:13px">Os logs de auditoria aparecerão aqui conforme o sistema for utilizado.</div>
          </div>
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
        <td style="color:var(--text-3); font-size:12px">${l.id}</td>
        <td>
          <div style="font-weight:500; font-size:13px">${data}</div>
          <div style="color:var(--text-3); font-size:11px">${hora}</div>
        </td>
        <td style="color:var(--text-2); font-size:12px">
          ${l.usuarioRu != null ? `RU ${l.usuarioRu}` : '—'}
        </td>
        <td><span class="log-acao">${l.acao || '—'}</span></td>
        <td style="color:var(--text-2); font-size:13px">${l.descricao || '—'}</td>
      </tr>
    `;
  }).join('');
}

function filtrar() {
  renderTabela();
}
