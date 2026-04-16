let fornecedoresAtivos = [];
let todosOsProdutos = [];
let notaAtual = null;
let stepAtual = 1;

document.addEventListener('DOMContentLoaded', async () => {
  await Promise.all([
    carregarNotas(),
    carregarFornecedores(),
    carregarTodosProdutos()
  ]);
});

async function carregarNotas() {
  try {
    const resp = await fetch('/api/notas');
    const notas = await resp.json();
    renderTabelaNotas(notas);
  } catch {
    document.getElementById('tabela-notas').innerHTML =
      '<tr><td colspan="7" class="empty">Erro ao carregar notas.</td></tr>';
  }
}

async function carregarFornecedores() {
  try {
    const resp = await fetch('/api/fornecedores/ativos');
    fornecedoresAtivos = await resp.json();

    const select = document.getElementById('s1-fornecedor');
    const valorSelecionado = notaAtual ? String(notaAtual.fornecedorId) : (select.value || '');

    if (fornecedoresAtivos.length === 0) {
      select.innerHTML = '<option value="">Nenhum fornecedor ativo cadastrado</option>';
      return;
    }

    select.innerHTML =
      '<option value="">— Selecione um fornecedor —</option>' +
      fornecedoresAtivos.map((fornecedor) =>
        `<option value="${fornecedor.id}">${escapeHtml(fornecedor.nome)} (${fornecedor.produtos.length} produto(s))</option>`
      ).join('');

    if (valorSelecionado) {
      select.value = valorSelecionado;
    }
  } catch {
    document.getElementById('s1-fornecedor').innerHTML =
      '<option value="">Erro ao carregar fornecedores</option>';
  }
}

async function carregarTodosProdutos() {
  try {
    const resp = await fetch('/api/produtos');
    todosOsProdutos = await resp.json();
  } catch {
    todosOsProdutos = [];
  }

  if (notaAtual) {
    atualizarAutocompleteProdutos();
    atualizarAvisoVinculo();
  }
}

function renderTabelaNotas(notas) {
  document.getElementById('notas-count').textContent = `${notas.length} nota(s)`;
  const tbody = document.getElementById('tabela-notas');

  if (notas.length === 0) {
    tbody.innerHTML = `
      <tr><td colspan="7" class="table-empty-cell">
        ${emptyStateMarkup({
          icon: 'receipt',
          title: 'Nenhuma nota fiscal registrada',
          copy: 'Crie uma nova nota para registrar compras de fornecedores e entrada de estoque.',
          actions: '<button class="btn-primary" onclick="iniciarNovaNota(); abrirAba(\'aba-nova\')">Nova Nota</button>'
        })}
      </td></tr>`;
    return;
  }

  const statusBadge = {
    PENDENTE: 'badge-amber',
    CONFIRMADA: 'badge-cyan',
    PAGA: 'badge-green',
    CANCELADA: 'badge-gray'
  };

  const statusLabel = {
    PENDENTE: 'Pendente',
    CONFIRMADA: 'Confirmada',
    PAGA: 'Paga',
    CANCELADA: 'Cancelada'
  };

  tbody.innerHTML = notas.map((nota) => {
    const data = new Date(nota.dataEmissao).toLocaleString('pt-BR', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });

    return `
      <tr>
        <td><strong>#${nota.id}</strong></td>
        <td>${escapeHtml(nota.fornecedorNome)}</td>
        <td><span class="badge ${statusBadge[nota.status] || 'badge-gray'}">${statusLabel[nota.status] || nota.status}</span></td>
        <td>${nota.itens.length} item(ns)</td>
        <td class="table-cell-success">R$${Number(nota.total).toFixed(2)}</td>
        <td class="table-cell-subtle">${data}</td>
        <td>
          <div class="acoes">
            <button class="btn-acao" onclick="verDetalhe(${nota.id})">Ver</button>
            ${nota.status === 'CONFIRMADA'
              ? `<button class="btn-acao entrada" onclick="pagarNota(${nota.id})">Pagar</button>`
              : ''}
            ${nota.status === 'PENDENTE'
              ? `<button class="btn-acao saida" onclick="confirmarCancelarNota(${nota.id})">Cancelar</button>`
              : ''}
          </div>
        </td>
      </tr>`;
  }).join('');
}

async function avancarStep1() {
  const btn = document.getElementById('btn-step1-avancar');
  const fornecedorId = parseInt(document.getElementById('s1-fornecedor').value, 10);

  if (!fornecedorId) {
    mostrarAlert('alert-s1', 'Selecione um fornecedor ativo.', 'error');
    return;
  }

  setLoading(btn, true);
  try {
    const resp = await fetch('/api/notas', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ fornecedorId })
    });

    if (!resp.ok) {
      mostrarAlert('alert-s1', await safeReadErrorMessage(resp, 'Erro ao abrir nota.'), 'error');
      return;
    }

    notaAtual = await resp.json();
    limparAlert('alert-s1');
    irParaStep(2);
    prepararStep2();
  } finally {
    setLoading(btn, false);
  }
}

function prepararStep2() {
  document.getElementById('s2-fornecedor-badge').textContent = notaAtual.fornecedorNome;
  atualizarAutocompleteProdutos();
  atualizarAvisoVinculo();
  renderItensStep2();
}

function fornecedorAtual() {
  if (!notaAtual) return null;
  return fornecedoresAtivos.find((fornecedor) => fornecedor.id === notaAtual.fornecedorId) || null;
}

function produtoVinculadoAoFornecedor(produtoId) {
  const fornecedor = fornecedorAtual();
  if (!fornecedor) return false;
  return fornecedor.produtos.some((produto) => produto.id === produtoId);
}

function formatarProdutoBusca(produto) {
  return `${produto.nome} (ID ${produto.id})`;
}

function atualizarAutocompleteProdutos() {
  const datalist = document.getElementById('s2-produtos-list');
  if (!datalist) return;

  const fornecedor = fornecedorAtual();
  const vinculados = new Set((fornecedor?.produtos || []).map((produto) => produto.id));

  const produtosOrdenados = [...todosOsProdutos].sort((a, b) => {
    const prioridadeA = vinculados.has(a.id) ? 0 : 1;
    const prioridadeB = vinculados.has(b.id) ? 0 : 1;
    if (prioridadeA !== prioridadeB) return prioridadeA - prioridadeB;
    return a.nome.localeCompare(b.nome, 'pt-BR');
  });

  datalist.innerHTML = produtosOrdenados.map((produto) => {
    const valor = escapeHtml(formatarProdutoBusca(produto));
    const label = vinculados.has(produto.id)
      ? 'Já vinculado a este fornecedor'
      : 'Vínculo automático ao salvar a nota';
    return `<option value="${valor}" label="${escapeHtml(label)}"></option>`;
  }).join('');
}

function resolveProdutoSelecionado() {
  const valor = document.getElementById('s2-produto-busca').value.trim();
  if (!valor) return null;

  const matchId = valor.match(/\(ID\s*(\d+)\)/i) || valor.match(/^(\d+)\s*[-:]/);
  if (matchId) {
    const id = parseInt(matchId[1], 10);
    return todosOsProdutos.find((produto) => produto.id === id) || null;
  }

  const nome = valor.replace(/\(ID\s*\d+\)/i, '').trim().toLowerCase();
  const correspondencias = todosOsProdutos.filter((produto) => produto.nome.trim().toLowerCase() === nome);
  if (correspondencias.length === 1) {
    return correspondencias[0];
  }
  return null;
}

function toggleNovoProduto(forceOpen) {
  const painel = document.getElementById('painel-novo-produto');
  const botao = document.getElementById('btn-toggle-novo-produto');
  const abrir = typeof forceOpen === 'boolean'
    ? forceOpen
    : painel.hidden;

  painel.hidden = !abrir;
  botao.textContent = abrir ? 'Usar Produto Existente' : '+ Novo Produto';

  if (abrir) {
    document.getElementById('s2-produto-busca').value = '';
  } else {
    limparFormularioNovoProduto();
    limparAlert('alert-s2-novo');
  }

  atualizarAvisoVinculo();
}

function atualizarAvisoVinculo() {
  limparAlert('alert-s2-link');

  if (!notaAtual) return;

  const painelNovoProduto = !document.getElementById('painel-novo-produto').hidden;
  const nomeNovoProduto = document.getElementById('s2-novo-nome').value.trim();

  if (painelNovoProduto && nomeNovoProduto) {
      mostrarAlert(
        'alert-s2-link',
        'Este produto será criado dentro da nota e vinculado automaticamente ao fornecedor quando o item for salvo.',
        'info'
      );
    return;
  }

  const produto = resolveProdutoSelecionado();
  if (produto && !produtoVinculadoAoFornecedor(produto.id)) {
      mostrarAlert(
        'alert-s2-link',
        'Este produto ainda não está vinculado a este fornecedor. O vínculo será criado automaticamente ao salvar a nota.',
        'info'
      );
  }
}

function renderItensStep2() {
  const lista = document.getElementById('itens-lista');
  if (!notaAtual || notaAtual.itens.length === 0) {
    lista.innerHTML = '<div class="empty">Nenhum item adicionado.</div>';
    document.getElementById('s2-total').textContent = 'R$0,00';
    document.getElementById('btn-step2-avancar').disabled = true;
    return;
  }

  lista.innerHTML = notaAtual.itens.map((item) => `
    <div class="item-nota-row">
      <span class="item-nota-nome">${escapeHtml(item.produtoNome)}</span>
      <span class="item-nota-qty">${item.quantidade} un.</span>
      <span class="item-nota-preco">× R$${Number(item.precoUnitario).toFixed(2)}</span>
      <span class="item-nota-subtotal">R$${Number(item.subtotal).toFixed(2)}</span>
    </div>`).join('');

  document.getElementById('s2-total').textContent = `R$${Number(notaAtual.total).toFixed(2)}`;
  document.getElementById('btn-step2-avancar').disabled = false;
}

function limparFormularioNovoProduto() {
  document.getElementById('s2-novo-nome').value = '';
  document.getElementById('s2-novo-categoria').value = '';
  document.getElementById('s2-novo-minimo').value = '0';
  document.getElementById('s2-novo-preco-base').value = '';
}

function limparFormularioItem() {
  document.getElementById('s2-produto-busca').value = '';
  document.getElementById('s2-qtd').value = '';
  document.getElementById('s2-preco').value = '';
  limparFormularioNovoProduto();
  limparAlert('alert-s2');
  limparAlert('alert-s2-novo');
  limparAlert('alert-s2-link');
  toggleNovoProduto(false);
}

function construirNovoProdutoPayload() {
  const nome = document.getElementById('s2-novo-nome').value.trim();
  if (!nome) return null;

  const minimoBruto = document.getElementById('s2-novo-minimo').value.trim();
  const precoBaseBruto = document.getElementById('s2-novo-preco-base').value.trim();
  const categoria = document.getElementById('s2-novo-categoria').value;

  return {
    nome,
    quantidadeMinima: minimoBruto === '' ? 0 : parseInt(minimoBruto, 10),
    precoUnitarioBase: precoBaseBruto === '' ? null : parseFloat(precoBaseBruto),
    categoriaEstoque: categoria || null
  };
}

async function adicionarItem() {
  const btn = document.getElementById('btn-adicionar-item');
  const quantidade = parseInt(document.getElementById('s2-qtd').value, 10);
  const preco = parseFloat(document.getElementById('s2-preco').value);
  const usandoNovoProduto = !document.getElementById('painel-novo-produto').hidden
    && document.getElementById('s2-novo-nome').value.trim() !== '';
  const produtoExistente = resolveProdutoSelecionado();

  if (!quantidade || quantidade <= 0) {
    mostrarAlert('alert-s2', 'Quantidade deve ser maior que zero.', 'error');
    return;
  }

  if (Number.isNaN(preco) || preco < 0) {
    mostrarAlert('alert-s2', 'Informe um preço unitário válido.', 'error');
    return;
  }

  if (usandoNovoProduto && produtoExistente) {
    mostrarAlert('alert-s2', 'Escolha apenas um caminho: produto existente ou novo produto.', 'error');
    return;
  }

  if (!usandoNovoProduto && !produtoExistente) {
    mostrarAlert('alert-s2', 'Selecione um produto existente ou preencha os dados do novo produto.', 'error');
    return;
  }

  const novoProduto = usandoNovoProduto ? construirNovoProdutoPayload() : null;
  if (usandoNovoProduto && !novoProduto?.nome) {
    mostrarAlert('alert-s2-novo', 'Nome do novo produto é obrigatório.', 'error');
    return;
  }

  const produtoPrecisavaVinculo = produtoExistente && !produtoVinculadoAoFornecedor(produtoExistente.id);

  setLoading(btn, true);
  try {
    const payload = {
      produtoId: usandoNovoProduto ? null : produtoExistente.id,
      novoProduto,
      quantidade,
      precoUnitario: preco
    };

    const resp = await fetch(`/api/notas/${notaAtual.id}/itens`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });

    if (!resp.ok) {
      const mensagem = await safeReadErrorMessage(resp, 'Erro ao adicionar item.');
      if (usandoNovoProduto) {
        mostrarAlert('alert-s2-novo', mensagem, 'error');
      } else {
        mostrarAlert('alert-s2', mensagem, 'error');
      }
      return;
    }

    notaAtual = await resp.json();
    await Promise.all([carregarFornecedores(), carregarTodosProdutos()]);
    renderItensStep2();
    limparFormularioItem();

    if (usandoNovoProduto) {
      showToast('Produto criado e item adicionado à nota.', 'success');
    } else if (produtoPrecisavaVinculo) {
      showToast('Item adicionado. O vínculo com o fornecedor foi criado automaticamente.', 'success');
    }
  } finally {
    setLoading(btn, false);
  }
}

async function avancarStep2() {
  if (!notaAtual || notaAtual.itens.length === 0) {
    mostrarAlert('alert-s2', 'Adicione pelo menos um item antes de continuar.', 'error');
    return;
  }
  irParaStep(3);
  renderItensStep3();
}

async function descartarRascunho() {
  if (!notaAtual) {
    resetWizard();
    return;
  }

  if (!confirm('Descartar este rascunho? A nota será excluída permanentemente.')) return;

  try {
    const resp = await fetch(`/api/notas/${notaAtual.id}`, { method: 'DELETE' });
    if (!resp.ok) {
      showToast(await safeReadErrorMessage(resp, 'Erro ao descartar rascunho.'), 'error');
      return;
    }
    showToast('Rascunho descartado.', 'success');
  } catch {
    showToast('Erro ao descartar rascunho.', 'error');
  }

  resetWizard();
  await carregarNotas();
}

function resetWizard() {
  notaAtual = null;
  irParaStep(1);
  document.getElementById('s1-fornecedor').value = '';
  limparFormularioItem();
  limparAlert('alert-s1');
}

function renderItensStep3() {
  document.getElementById('s3-itens-lista').innerHTML = notaAtual.itens.map((item) => `
    <div class="item-nota-row">
      <span class="item-nota-nome">${escapeHtml(item.produtoNome)}</span>
      <span class="item-nota-qty">${item.quantidade} un.</span>
      <span class="item-nota-preco">× R$${Number(item.precoUnitario).toFixed(2)}</span>
      <span class="item-nota-subtotal">R$${Number(item.subtotal).toFixed(2)}</span>
    </div>`).join('');

  document.getElementById('s3-total').textContent = `R$${Number(notaAtual.total).toFixed(2)}`;
}

async function avancarStep3() {
  const btn = document.getElementById('btn-step3-avancar');
  setLoading(btn, true);
  try {
    const resp = await fetch(`/api/notas/${notaAtual.id}/confirmar`, { method: 'POST' });
    if (!resp.ok) {
      mostrarAlert('alert-s3', await safeReadErrorMessage(resp, 'Erro ao confirmar nota.'), 'error');
      return;
    }
    notaAtual = await resp.json();
    limparAlert('alert-s3');
    irParaStep(4);
    renderStep4();
  } finally {
    setLoading(btn, false);
  }
}

function renderStep4() {
  document.getElementById('s4-resumo').innerHTML = `
    <div class="info-card">
      <div class="info-label">Fornecedor</div>
      <div class="table-cell-strong">${escapeHtml(notaAtual.fornecedorNome)}</div>
    </div>
    <div class="info-card">
      <div class="info-label">Itens na Nota</div>
      <div class="info-value">${notaAtual.itens.length}</div>
    </div>
    <div class="info-card">
      <div class="info-label">Valor Total</div>
      <div class="info-value green">R$${Number(notaAtual.total).toFixed(2)}</div>
    </div>`;
}

async function avancarStep4() {
  const btn = document.getElementById('btn-step4-avancar');
  setLoading(btn, true);
  try {
    const resp = await fetch(`/api/notas/${notaAtual.id}/pagamento`, { method: 'POST' });
    if (!resp.ok) {
      mostrarAlert('alert-s4', await safeReadErrorMessage(resp, 'Erro ao registrar pagamento.'), 'error');
      return;
    }
    notaAtual = await resp.json();
    irParaStep(5);
    await carregarNotas();
  } finally {
    setLoading(btn, false);
  }
}

function irParaStep(step) {
  stepAtual = step;

  for (let i = 1; i <= 4; i++) {
    const el = document.getElementById(`step-${i}`);
    const conn = document.getElementById(`conn-${i}`);
    el.className = 'wizard-step' + (i < step ? ' done' : i === step ? ' active' : '');
    if (conn) conn.className = 'wizard-connector' + (i < step ? ' done' : '');
  }

  ['painel-step1', 'painel-step2', 'painel-step3', 'painel-step4', 'painel-done']
    .forEach((id, index) => {
      document.getElementById(id).hidden =
        !(index + 1 === step || (step === 5 && id === 'painel-done'));
    });
}

function abrirAba(abaId) {
  document.querySelectorAll('.tab-panel').forEach((painel) => painel.classList.remove('active'));
  document.querySelectorAll('.tab-btn').forEach((botao) => botao.classList.remove('active'));

  document.getElementById(abaId).classList.add('active');
  if (abaId === 'aba-lista') document.getElementById('tab-lista').classList.add('active');
  if (abaId === 'aba-nova') document.getElementById('tab-nova').classList.add('active');
}

function iniciarNovaNota() {
  resetWizard();
  carregarFornecedores();
  carregarTodosProdutos();
  carregarNotas();
}

async function confirmarCancelarNota(id) {
  if (!confirm('Cancelar esta nota fiscal? Esta ação não pode ser desfeita.')) return;
  try {
    const resp = await fetch(`/api/notas/${id}/cancelar`, { method: 'POST' });
    if (!resp.ok) {
      showToast(await safeReadErrorMessage(resp, 'Erro ao cancelar nota.'), 'error');
      return;
    }
    showToast('Nota cancelada com sucesso.', 'success');
    await carregarNotas();
  } catch {
    showToast('Erro ao cancelar nota.', 'error');
  }
}

async function verDetalhe(id) {
  try {
    const resp = await fetch(`/api/notas/${id}`);
    const nota = await resp.json();
    const statusLabel = {
      PENDENTE: 'Pendente',
      CONFIRMADA: 'Confirmada',
      PAGA: 'Paga',
      CANCELADA: 'Cancelada'
    };
    const statusBadge = {
      PENDENTE: 'badge-amber',
      CONFIRMADA: 'badge-cyan',
      PAGA: 'badge-green',
      CANCELADA: 'badge-gray'
    };

    document.getElementById('modal-detalhe-titulo').textContent = `Nota #${nota.id}`;
    document.getElementById('detalhe-info').innerHTML = `
      <div class="detail-meta">
        <div class="detail-meta-item">
          <span class="detail-meta-label">Fornecedor</span>
          <strong class="detail-meta-value">${escapeHtml(nota.fornecedorNome)}</strong>
        </div>
        <div class="detail-meta-item">
          <span class="detail-meta-label">Status</span>
          <span class="badge ${statusBadge[nota.status]}">${statusLabel[nota.status]}</span></div>
        <div class="detail-meta-item">
          <span class="detail-meta-label">Total</span>
          <strong class="detail-meta-value detail-meta-value--success">R$${Number(nota.total).toFixed(2)}</strong>
        </div>
      </div>`;

    document.getElementById('detalhe-itens').innerHTML = nota.itens.length === 0
      ? '<div class="empty">Sem itens.</div>'
      : '<div class="itens-nota-list">' + nota.itens.map((item) => `
          <div class="item-nota-row">
            <span class="item-nota-nome">${escapeHtml(item.produtoNome)}</span>
            <span class="item-nota-qty">${item.quantidade} un.</span>
            <span class="item-nota-preco">× R$${Number(item.precoUnitario).toFixed(2)}</span>
            <span class="item-nota-subtotal">R$${Number(item.subtotal).toFixed(2)}</span>
          </div>`).join('') + '</div>';

    limparAlert('alert-detalhe');
    abrirModal('modal-detalhe');
  } catch {
    showToast('Erro ao carregar detalhes da nota.', 'error');
  }
}

async function pagarNota(id) {
  const btn = event?.target ?? null;
  setLoading(btn, true);
  try {
    const resp = await fetch(`/api/notas/${id}/pagamento`, { method: 'POST' });
    if (!resp.ok) {
      showToast(await safeReadErrorMessage(resp, 'Erro ao registrar pagamento.'), 'error');
      return;
    }
    showToast('Pagamento registrado com sucesso.', 'success');
    await carregarNotas();
    fecharModal('modal-detalhe');
  } finally {
    setLoading(btn, false);
  }
}

function abrirModal(id) {
  document.getElementById(id).classList.add('open');
}

function fecharModal(id) {
  document.getElementById(id).classList.remove('open');
}

function fecharModalSeClicouFora(e, id) {
  if (e.target.id === id) fecharModal(id);
}

function mostrarAlert(id, msg, tipo) {
  const el = document.getElementById(id);
  if (!el) return;
  el.textContent = msg;
  el.className = `alert-modal ${tipo}`;
}

function limparAlert(id) {
  const el = document.getElementById(id);
  if (el) {
    el.textContent = '';
    el.className = 'alert-modal';
  }
}
