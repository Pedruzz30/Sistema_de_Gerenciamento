// ── funcionarios.js ────────────────────────────────────────────────────────
// Employee management: list, create, change class, deactivate.

let todosFuncionarios = [];
let filtroAtivo = 'todos';
let ruMudarClasse = null;

// Class display helpers
const CLASSE_LABEL = {
  SUPERIOR:        'Superior',
  GERENTE_ESTOQUE: 'Gerente de Estoque',
  ESTOQUISTA:      'Estoquista',
  CAIXA:           'Caixa'
};
const CLASSE_BADGE = {
  SUPERIOR:        'badge-purple',
  GERENTE_ESTOQUE: 'badge-cyan',
  ESTOQUISTA:      'badge-amber',
  CAIXA:           'badge-gray'
};

// ── Init ──────────────────────────────────────────────────────────────────

document.addEventListener('DOMContentLoaded', () => {
  carregarFuncionarios();
});

// ── API ───────────────────────────────────────────────────────────────────

async function carregarFuncionarios() {
  try {
    const resp = await fetch('/api/funcionarios');
    if (!resp.ok) throw new Error();
    todosFuncionarios = await resp.json();
    renderTabela();
  } catch {
    document.getElementById('tabela-funcionarios').innerHTML =
      '<tr><td colspan="6" class="empty">Erro ao carregar funcionários.</td></tr>';
  }
}

// ── Render ────────────────────────────────────────────────────────────────

function renderTabela() {
  const busca = document.getElementById('busca').value.toLowerCase();
  let lista = todosFuncionarios.filter(f => {
    const matchFiltro =
      filtroAtivo === 'todos' ||
      (filtroAtivo === 'ativos' && f.ativo) ||
      (filtroAtivo === 'inativos' && !f.ativo);
    const nomeCompleto = `${f.nome} ${f.sobrenome}`.toLowerCase();
    const matchBusca = !busca || nomeCompleto.includes(busca) || String(f.ru).includes(busca);
    return matchFiltro && matchBusca;
  });

  document.getElementById('table-count').textContent = `${lista.length} funcionário(s)`;

  const tbody = document.getElementById('tabela-funcionarios');
  if (lista.length === 0) {
    tbody.innerHTML = '<tr><td colspan="6" class="empty">Nenhum funcionário encontrado.</td></tr>';
    return;
  }

  tbody.innerHTML = lista.map(f => {
    const classeLabel = CLASSE_LABEL[f.nomeClasse] || f.nomeClasse || '—';
    const classeBadge = CLASSE_BADGE[f.nomeClasse] || 'badge-gray';
    const perms = Array.isArray(f.permissoes) ? f.permissoes.length : 0;

    return `
      <tr>
        <td style="color:var(--text-3); font-size:12px; font-weight:600">#${f.ru}</td>
        <td>
          <div class="produto-nome">${f.nome} ${f.sobrenome}</div>
          <div class="produto-id">${f.perfil || 'OPERADOR'}</div>
        </td>
        <td><span class="badge ${classeBadge}">${classeLabel}</span></td>
        <td style="color:var(--text-2); font-size:12px">${perms} permissão(ões)</td>
        <td>
          <span class="badge ${f.ativo ? 'badge-green' : 'badge-gray'}">
            ${f.ativo ? 'Ativo' : 'Inativo'}
          </span>
        </td>
        <td>
          <div class="acoes">
            ${f.ativo ? `
              <button class="btn-acao" onclick="abrirModalClasse(${f.ru}, '${f.nome} ${f.sobrenome}', '${f.nomeClasse || ''}')">Classe</button>
              <button class="btn-acao saida" onclick="desativar(${f.ru})">Desativar</button>
            ` : `
              <span style="color:var(--text-3); font-size:12px">Inativo</span>
            `}
          </div>
        </td>
      </tr>
    `;
  }).join('');
}

// ── Actions ───────────────────────────────────────────────────────────────

function setFiltro(tipo, btn) {
  filtroAtivo = tipo;
  document.querySelectorAll('.filter-btn').forEach(b => b.classList.remove('active'));
  btn.classList.add('active');
  renderTabela();
}

function filtrar() { renderTabela(); }

async function cadastrarFuncionario() {
  const nome     = document.getElementById('c-nome').value.trim();
  const sobrenome= document.getElementById('c-sobrenome').value.trim();
  const cpf      = document.getElementById('c-cpf').value.trim();
  const senha    = document.getElementById('c-senha').value;
  const nomeClasse = document.getElementById('c-classe').value;

  if (!nome)      { mostrarAlert('alert-cadastro', 'Nome é obrigatório.', 'error'); return; }
  if (!sobrenome) { mostrarAlert('alert-cadastro', 'Sobrenome é obrigatório.', 'error'); return; }
  if (!cpf)       { mostrarAlert('alert-cadastro', 'CPF é obrigatório.', 'error'); return; }
  if (!senha)     { mostrarAlert('alert-cadastro', 'Senha é obrigatória.', 'error'); return; }
  if (!nomeClasse){ mostrarAlert('alert-cadastro', 'Selecione uma classe.', 'error'); return; }

  const resp = await fetch('/api/funcionarios', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ nome, sobrenome, cpf, senha, nomeClasse })
  });
  const data = await resp.json();
  if (!resp.ok) {
    mostrarAlert('alert-cadastro', data.erro || 'Erro ao cadastrar.', 'error'); return;
  }
  fecharModal('modal-cadastro');
  await carregarFuncionarios();
}

function abrirModalClasse(ru, nomeCompleto, classeAtual) {
  ruMudarClasse = ru;
  document.getElementById('classe-func-nome').textContent = nomeCompleto;
  document.getElementById('classe-atual').textContent = `Classe atual: ${CLASSE_LABEL[classeAtual] || classeAtual || '—'}`;
  document.getElementById('nova-classe').value = '';
  limparAlert('alert-classe');
  abrirModal('modal-classe');
}

async function confirmarMudarClasse() {
  const nomeClasse = document.getElementById('nova-classe').value;
  if (!nomeClasse) { mostrarAlert('alert-classe', 'Selecione uma classe.', 'error'); return; }

  const resp = await fetch(`/api/funcionarios/${ruMudarClasse}/classe`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ nomeClasse })
  });
  const data = await resp.json();
  if (!resp.ok) {
    mostrarAlert('alert-classe', data.erro || 'Erro ao alterar classe.', 'error'); return;
  }
  fecharModal('modal-classe');
  await carregarFuncionarios();
}

async function desativar(ru) {
  if (!confirm('Desativar este funcionário?')) return;
  const resp = await fetch(`/api/funcionarios/${ru}/desativar`, { method: 'PUT' });
  const data = await resp.json();
  if (!resp.ok) { alert(data.erro || 'Erro ao desativar.'); return; }
  await carregarFuncionarios();
}

// ── Modal helpers ─────────────────────────────────────────────────────────

function abrirModalCadastro() {
  limparAlert('alert-cadastro');
  ['c-nome','c-sobrenome','c-cpf','c-senha'].forEach(id => document.getElementById(id).value = '');
  document.getElementById('c-classe').value = '';
  abrirModal('modal-cadastro');
}

function abrirModal(id) { document.getElementById(id).classList.add('open'); }
function fecharModal(id) { document.getElementById(id).classList.remove('open'); }
function fecharModalSeClicouFora(e, id) { if (e.target.id === id) fecharModal(id); }
function mostrarAlert(id, msg, tipo) {
  const el = document.getElementById(id);
  el.textContent = msg;
  el.className = `alert-modal ${tipo}`;
}
function limparAlert(id) {
  const el = document.getElementById(id);
  if (el) { el.textContent = ''; el.className = 'alert-modal'; }
}
