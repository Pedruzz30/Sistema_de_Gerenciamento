/* SPA-isolated screen: notas. Retains direct HTML navigation. */
(() => {
'use strict';
let fornecedoresAtivos = [];
let todosOsProdutos = [];
let notaAtual = null;
let stepAtual = 1;

async function stockOSMount_notas() {
  if (typeof paginaAtual === 'function' && paginaAtual() !== 'notas') return;
  await Promise.all([
      carregarNotas(),
      carregarFornecedores(),
      carregarTodosProdutos()
    ]);
}

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
          actions: '<button class="btn-primary" onclick="iniciarNovaNota(); abrirAba(\'aba-nova\')">' +
            '<span class="btn-inline-icon" aria-hidden="true">' + iconMarkup('plus') + '</span>' +
            'Nova Nota</button>'
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
  botao.innerHTML = abrir
    ? 'Usar Produto Existente'
    : '<span class="btn-inline-icon" aria-hidden="true">' + iconMarkup('plus') + '</span>Novo Produto';

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

/* Page-scoped bridge: legacy inline event handlers resolve only against the active page. */
// ── Importar XML da NF-e ──────────────────────────────────────────────────
// Dois passos: a prévia não grava nada. Quantidades e valores vêm do XML; aqui
// a pessoa só decide o que fazer com cada item e confere as unidades por embalagem.

let nfeAtual = null;
let nfeImportando = false;
const NFE_MOEDA = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
const NFE_MAX_BYTES = 5 * 1024 * 1024;

function abrirModalXml() {
  nfeAtual = null;
  limparAlert('alert-xml');
  document.getElementById('xml-input').value = '';
  document.getElementById('xml-arquivo').hidden = false;
  document.getElementById('xml-conferencia').hidden = true;
  document.getElementById('btn-importar-xml').hidden = true;
  abrirModal('modal-xml');
}

function fecharModalXml() {
  if (nfeAtual && !window.confirm('Sair sem importar? A conferência desta nota será perdida.')) return;
  nfeAtual = null;
  fecharModal('modal-xml');
}

/** Respeita o encoding declarado no arquivo (há notas em ISO-8859-1). */
function decodificarXml(buffer) {
  const inicio = new TextDecoder('latin1').decode(buffer.slice(0, 200));
  const declarado = inicio.match(/encoding=["']([\w-]+)["']/i);
  try {
    return new TextDecoder(declarado ? declarado[1] : 'utf-8').decode(buffer);
  } catch (_) {
    return new TextDecoder('utf-8').decode(buffer);
  }
}

async function lerArquivoXml(input) {
  const arquivo = input.files && input.files[0];
  if (!arquivo) return;
  limparAlert('alert-xml');
  if (arquivo.size > NFE_MAX_BYTES) {
    mostrarAlert('alert-xml', 'Arquivo grande demais para uma NF-e.', 'error');
    return;
  }
  await processarTextoNfe(decodificarXml(await arquivo.arrayBuffer()));
}

async function processarTextoNfe(xml) {
  limparAlert('alert-xml');
  try {
    const resp = await fetch('/api/notas/importacao/previa', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ xml })
    });
    if (!resp.ok) throw new Error(await safeReadErrorMessage(resp, 'Não foi possível ler o arquivo.'));
    const previa = await resp.json();
    await carregarTodosProdutos();
    nfeAtual = { xml, previa };
    renderPreviaNfe(previa);
  } catch (e) {
    nfeAtual = null;
    mostrarAlert('alert-xml', e.message, 'error');
  }
}

function nfeUnidades(item, fator) {
  return Number(item.quantidadeComercial) * fator;
}

function nfeEhInteiro(valor) {
  return Math.abs(valor - Math.round(valor)) < 0.000001 && valor > 0;
}

function renderPreviaNfe(previa) {
  const emissao = previa.dataEmissao ? new Date(previa.dataEmissao).toLocaleDateString('pt-BR') : '—';
  const situacaoFornecedor = previa.fornecedorId
    ? (previa.fornecedorInativo ? 'inativo: reative antes de importar' : 'já cadastrado')
    : 'novo: será cadastrado';
  document.getElementById('xml-resumo').innerHTML = `
    <div><strong>NF-e ${escapeHtml(previa.numero)}</strong> · série ${escapeHtml(previa.serie)} · emitida em ${emissao} · total ${NFE_MOEDA.format(Number(previa.valorTotal))}</div>
    <div>Fornecedor: <strong>${escapeHtml(previa.fornecedorNome)}</strong> (${situacaoFornecedor})</div>
    <div>Nota emitida para: ${escapeHtml(previa.destinatarioNome || '—')}. Confira se é a sua loja.</div>`;

  const avisos = [...(previa.avisos || [])];
  const bloqueada = Boolean(previa.notaExistenteId) || previa.fornecedorInativo;
  if (previa.notaExistenteId) avisos.unshift(`Esta nota já foi importada (nota #${previa.notaExistenteId}). Não pode entrar de novo.`);
  document.getElementById('xml-avisos').innerHTML = avisos.map(a => `<li>${escapeHtml(a)}</li>`).join('');

  const opcoesProduto = [...todosOsProdutos]
    .sort((a, b) => String(a.nome).localeCompare(String(b.nome), 'pt-BR'))
    .map(p => `<option value="${p.id}">${escapeHtml(p.nome)}</option>`)
    .join('');

  document.getElementById('xml-itens').innerHTML = previa.itens.map(item => {
    const n = item.numero;
    const origem = item.origem === 'VINCULO' ? 'Reconhecido deste fornecedor'
      : item.origem === 'CODIGO_BARRAS' ? 'Reconhecido pelo código de barras' : 'Produto novo';
    return `
      <article class="xml-item" data-item="${n}">
        <header>
          <strong>${n}. ${escapeHtml(item.descricao)}</strong>
          <span class="badge ${item.origem ? 'badge-green' : 'badge-amber'}">${origem}</span>
        </header>
        <p class="inline-note">Cód. ${escapeHtml(item.codigoFornecedor)} · NCM ${escapeHtml(item.ncm || '—')} · Código de barras ${escapeHtml(item.eanTributavel || item.eanComercial || 'sem GTIN')}</p>
        <div class="xml-conta ${item.conferir ? 'xml-conta--conferir' : ''}">
          <span>${Number(item.quantidadeComercial).toLocaleString('pt-BR')} ${escapeHtml(item.unidadeComercial)} ×</span>
          <input class="input" type="number" id="xml-fator-${n}" min="1" max="1000" step="1" value="${item.unidadesPorEmbalagem}" aria-label="Unidades por embalagem" oninput="atualizarContaXml(${n})">
          <span id="xml-conta-${n}"></span>
        </div>
        ${item.conferir ? '<p class="xml-conferir">Confira: a nota não diz quantas unidades vêm nesta embalagem.</p>' : ''}
        <div class="xml-decisao">
          <label>O que fazer
            <select class="select" id="xml-acao-${n}" onchange="atualizarDecisaoXml(${n})">
              <option value="CRIAR">Cadastrar produto novo</option>
              <option value="VINCULAR">Já tenho este produto</option>
              <option value="IGNORAR">Não dar entrada</option>
            </select>
          </label>
          <label class="xml-vincular" id="xml-vincular-${n}">Produto
            <select class="select" id="xml-produto-${n}"><option value="">Escolha…</option>${opcoesProduto}</select>
          </label>
          <div class="xml-novo" id="xml-novo-${n}">
            <label class="xml-novo__nome">Nome no sistema
              <input class="input" type="text" id="xml-nome-${n}" value="${escapeHtml(item.descricao)}">
            </label>
            <label>Preço gelada (R$)
              <input class="input" type="number" id="xml-gelado-${n}" min="0.01" step="0.01" placeholder="Vazio = não vende gelada">
            </label>
            <label>Preço natural / unidade (R$)
              <input class="input" type="number" id="xml-preco-${n}" min="0.01" step="0.01">
            </label>
            <label id="xml-fardo-wrap-${n}">Preço do fardo (R$)
              <input class="input" type="number" id="xml-fardo-${n}" min="0.01" step="0.01" placeholder="Vazio = não vende fechado">
            </label>
            <label>Categoria
              <select class="select" id="xml-categoria-${n}">
                <option value="bebidas">Bebidas</option>
                <option value="petiscos">Petiscos</option>
              </select>
            </label>
          </div>
        </div>
      </article>`;
  }).join('');

  previa.itens.forEach(item => {
    document.getElementById(`xml-acao-${item.numero}`).value = item.produtoId ? 'VINCULAR' : 'CRIAR';
    if (item.produtoId) document.getElementById(`xml-produto-${item.numero}`).value = String(item.produtoId);
    atualizarDecisaoXml(item.numero);
    atualizarContaXml(item.numero);
  });

  document.getElementById('xml-arquivo').hidden = true;
  document.getElementById('xml-conferencia').hidden = false;
  const botao = document.getElementById('btn-importar-xml');
  botao.hidden = false;
  botao.disabled = bloqueada;
}

function nfeItem(numero) {
  return nfeAtual && nfeAtual.previa.itens.find(i => i.numero === numero);
}

function atualizarDecisaoXml(numero) {
  const acao = document.getElementById(`xml-acao-${numero}`).value;
  document.getElementById(`xml-vincular-${numero}`).hidden = acao !== 'VINCULAR';
  document.getElementById(`xml-novo-${numero}`).hidden = acao !== 'CRIAR';
  document.querySelector(`.xml-item[data-item="${numero}"]`).classList.toggle('xml-item--ignorado', acao === 'IGNORAR');
  atualizarContaXml(numero);
}

function atualizarContaXml(numero) {
  const item = nfeItem(numero);
  if (!item) return;
  const fator = Number(document.getElementById(`xml-fator-${numero}`).value);
  const unidades = nfeUnidades(item, fator);
  const conta = document.getElementById(`xml-conta-${numero}`);
  if (!Number.isInteger(fator) || fator < 1 || !nfeEhInteiro(unidades)) {
    conta.textContent = 'não dá unidades inteiras';
    conta.classList.add('xml-conta__erro');
  } else {
    const custo = Number(item.custoTotal) / unidades;
    conta.textContent = `un = ${unidades.toLocaleString('pt-BR')} unidades · custo ${NFE_MOEDA.format(custo)}/un`;
    conta.classList.remove('xml-conta__erro');
  }
  document.getElementById(`xml-fardo-wrap-${numero}`).hidden = !(Number.isInteger(fator) && fator >= 2);
}

function numeroXml(id) {
  const texto = document.getElementById(id).value.trim().replace(',', '.');
  return texto === '' ? null : Number(texto);
}

function montarDecisoesXml() {
  const decisoes = [];
  for (const item of nfeAtual.previa.itens) {
    const n = item.numero;
    const acao = document.getElementById(`xml-acao-${n}`).value;
    const fator = Number(document.getElementById(`xml-fator-${n}`).value);
    if (acao === 'IGNORAR') {
      decisoes.push({ numeroItem: n, acao, unidadesPorEmbalagem: 1 });
      continue;
    }
    if (!Number.isInteger(fator) || fator < 1 || !nfeEhInteiro(nfeUnidades(item, fator))) {
      return { erro: `Item ${n}: confira as unidades por embalagem.`, campo: `xml-fator-${n}` };
    }
    if (acao === 'VINCULAR') {
      const produtoId = Number(document.getElementById(`xml-produto-${n}`).value);
      if (!produtoId) return { erro: `Item ${n}: escolha o produto.`, campo: `xml-produto-${n}` };
      decisoes.push({ numeroItem: n, acao, produtoId, unidadesPorEmbalagem: fator });
      continue;
    }
    const nome = document.getElementById(`xml-nome-${n}`).value.trim();
    const precoVenda = numeroXml(`xml-preco-${n}`);
    const precoGelado = numeroXml(`xml-gelado-${n}`);
    const precoFardo = fator >= 2 ? numeroXml(`xml-fardo-${n}`) : null;
    if (!nome) return { erro: `Item ${n}: informe o nome.`, campo: `xml-nome-${n}` };
    if (!(precoVenda > 0)) return { erro: `Item ${n}: informe o preço de venda.`, campo: `xml-preco-${n}` };
    const categoria = document.getElementById(`xml-categoria-${n}`);
    decisoes.push({
      numeroItem: n,
      acao,
      unidadesPorEmbalagem: fator,
      nome,
      precoVenda,
      precoGelado,
      precoFardo,
      categoriaCardapioCodigo: categoria.value,
      categoriaCardapioNome: categoria.options[categoria.selectedIndex].textContent,
      categoriaEstoque: categoria.value === 'bebidas' ? 'BEBIDAS' : 'SECO'
    });
  }
  return { decisoes };
}

async function confirmarImportacaoXml() {
  if (!nfeAtual || nfeImportando) return;
  limparAlert('alert-xml');
  const montagem = montarDecisoesXml();
  if (montagem.erro) {
    mostrarAlert('alert-xml', montagem.erro, 'error');
    document.getElementById(montagem.campo)?.focus();
    return;
  }
  nfeImportando = true;
  const botao = document.getElementById('btn-importar-xml');
  botao.disabled = true;
  try {
    const resp = await fetch('/api/notas/importacao', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ xml: nfeAtual.xml, itens: montagem.decisoes })
    });
    if (!resp.ok) throw new Error(await safeReadErrorMessage(resp, 'Não foi possível importar a nota.'));
    const r = await resp.json();
    nfeAtual = null;
    fecharModal('modal-xml');
    const novos = r.produtosCriados ? `, ${r.produtosCriados} produto(s) novo(s)` : '';
    showToast(`Nota importada: ${Number(r.unidades).toLocaleString('pt-BR')} unidades no estoque${novos}.`, 'success');
    await Promise.all([carregarNotas(), carregarTodosProdutos()]);
  } catch (e) {
    mostrarAlert('alert-xml', e.message, 'error');
    document.querySelector('#modal-xml .modal-body')?.scrollTo({ top: 0 });
    botao.disabled = false;
  } finally {
    nfeImportando = false;
  }
}

window.StockOSPages = window.StockOSPages || {};
window.StockOSPages.notas = {
  mount: stockOSMount_notas,
  activate() {
    Object.assign(window, {
    abrirModalXml,
    fecharModalXml,
    lerArquivoXml,
    processarTextoNfe,
    atualizarDecisaoXml,
    atualizarContaXml,
    confirmarImportacaoXml,
    carregarNotas,
    carregarFornecedores,
    carregarTodosProdutos,
    renderTabelaNotas,
    avancarStep1,
    prepararStep2,
    fornecedorAtual,
    produtoVinculadoAoFornecedor,
    formatarProdutoBusca,
    atualizarAutocompleteProdutos,
    resolveProdutoSelecionado,
    toggleNovoProduto,
    atualizarAvisoVinculo,
    renderItensStep2,
    limparFormularioNovoProduto,
    limparFormularioItem,
    construirNovoProdutoPayload,
    adicionarItem,
    avancarStep2,
    descartarRascunho,
    resetWizard,
    renderItensStep3,
    avancarStep3,
    renderStep4,
    avancarStep4,
    irParaStep,
    abrirAba,
    iniciarNovaNota,
    confirmarCancelarNota,
    verDetalhe,
    pagarNota,
    abrirModal,
    fecharModal,
    fecharModalSeClicouFora,
    mostrarAlert,
    limparAlert,
  });
  }
};
if (/^\/notas(?:\.html)?\/?$/.test(location.pathname)) {
  window.StockOSPages.notas.activate();
}
document.addEventListener('DOMContentLoaded', stockOSMount_notas);

})();
