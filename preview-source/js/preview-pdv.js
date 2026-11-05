/* Browser-only PDV walkthrough. Does not execute real sales or payments.
 *
 * Fluxo de frente de caixa (balcão):
 *   1. digitar/bipar o código (nº do produto na demo) ou parte do nome + Enter
 *      ("6*12" lança 6 unidades do produto 12);
 *   2. conferir o carrinho (mudar quantidade, remover item);
 *   3. F10: escolher a forma de pagamento; no dinheiro, digitar o recebido e ver o troco;
 *   4. Enter: registra e o PDV já fica pronto para o próximo cliente.
 */
(() => {
  'use strict';
  const BRL = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
  const moeda = v => BRL.format(Number(v || 0));
  const FORMAS = [
    ['DINHEIRO', 'Dinheiro', '1'], ['PIX', 'Pix', '2'], ['DEBITO', 'Débito', '3'], ['CREDITO', 'Crédito', '4']
  ];
  const el = (tag, attrs = {}, ...children) => {
    const e = document.createElement(tag);
    for (const [k, v] of Object.entries(attrs)) {
      if (k === 'style') e.style.cssText = v;
      else if (k.startsWith('on')) e[k] = v;
      else if (v !== undefined && v !== null) e.setAttribute(k, v);
    }
    for (const c of children) e.append(c instanceof Node ? c : document.createTextNode(String(c)));
    return e;
  };
  const normalizar = t => String(t || '').normalize('NFD').replace(/\p{M}/gu, '').toLowerCase();

  function mountPreviewPdv() {
    if (!/\/caixas(?:\.html)?\/?$/.test(location.pathname)) return;
    window.abrirPDV = async numero => {
      const boxes = await (await fetch('/api/caixas')).json();
      if (!boxes.some(c => c.numeroCaixa === numero && c.status === 'ABERTO')) {
        alert('Abra o caixa antes de simular uma venda.');
        return;
      }
      document.getElementById('stockos-preview-pdv')?.remove();
      let products = (await (await fetch('/api/produtos')).json()).filter(p => p.ativo !== false);
      const cart = [];
      let forma = 'DINHEIRO';
      let pagando = false;

      const overlay = el('div', { id: 'stockos-preview-pdv', class: 'stockos-preview-pdv',
        style: 'position:fixed;inset:0;z-index:2147483600;background:#000a;display:grid;place-items:center;padding:16px;font:14px system-ui' });
      const modal = el('div', { class: 'stockos-preview-pdv__dialog', role: 'dialog', 'aria-label': 'PDV de demonstração',
        style: 'width:min(820px,100%);max-height:92vh;overflow:auto;background:#142236;color:#f4f7fa;border:1px solid #5c7087;border-radius:14px;padding:20px;box-shadow:0 15px 50px #0008;display:grid;gap:12px' });
      const title = el('h2', { class: 'stockos-preview-pdv__title', style: 'margin:0' }, 'PDV de demonstração · Caixa ' + numero);
      const close = el('button', { type: 'button', class: 'stockos-preview-pdv__close' }, 'Fechar (Esc)');
      modal.append(el('div', { style: 'display:flex;justify-content:space-between;gap:16px;align-items:center' }, title, close));
      modal.append(el('p', { class: 'stockos-preview-pdv__info', style: 'opacity:.8;margin:0' },
        'Estoque fictício. Nenhuma venda, pagamento ou nota fiscal é real. Na loja, o leitor de código de barras digita o código e aperta Enter.'));

      const scan = el('input', { class: 'stockos-preview-pdv__scan', type: 'text', autocomplete: 'off',
        'aria-label': 'Código ou nome do produto',
        placeholder: 'Código (nº do produto) ou nome · 6*12 = 6 unidades do produto 12 · Enter',
        style: 'width:100%;font-size:20px;padding:12px;border-radius:10px;border:2px solid #e8a33d;background:#0d1828;color:#fff' });
      const sugestoes = el('div', { class: 'stockos-preview-pdv__sugestoes', style: 'display:grid;gap:4px' });
      modal.append(scan, sugestoes);

      const feedback = el('div', { class: 'stockos-preview-pdv__feedback', role: 'status', 'aria-live': 'polite',
        style: 'min-height:20px;padding:0;border-radius:8px;font-weight:600' });
      const rows = el('div', { class: 'stockos-preview-pdv__rows', style: 'display:grid;gap:6px' });
      const totalEl = el('strong', { class: 'stockos-preview-pdv__total', style: 'display:block;font-size:26px' }, 'Total: ' + moeda(0));
      modal.append(rows, totalEl);

      const pagar = el('button', { type: 'button', class: 'stockos-preview-pdv__pagar',
        style: 'padding:12px 14px;background:#e8a33d;color:#1b1b1b;border:0;border-radius:8px;font-weight:700;cursor:pointer' }, 'Pagamento (F10)');
      const painel = el('div', { class: 'stockos-preview-pdv__pagamento', hidden: '', style: 'display:grid;gap:8px;padding:12px;border:1px solid #5c7087;border-radius:10px' });
      const botoesForma = FORMAS.map(([codigo, rotulo, tecla]) => el('button', { type: 'button', 'data-forma': codigo,
        style: 'padding:10px;border-radius:8px;border:1px solid #5c7087;background:#0d1828;color:#fff;cursor:pointer',
        onclick: () => escolherForma(codigo) }, `${tecla} · ${rotulo}`));
      const recebido = el('input', { type: 'number', step: '0.01', min: '0', inputmode: 'decimal',
        'aria-label': 'Valor recebido em dinheiro', placeholder: 'Recebido (ex.: 50,00)',
        style: 'font-size:18px;padding:10px;border-radius:8px;width:100%' });
      const troco = el('div', { class: 'stockos-preview-pdv__troco', style: 'font-size:28px;font-weight:800;color:#22a05b' }, 'Troco: ' + moeda(0));
      const finish = el('button', { type: 'button', class: 'stockos-preview-pdv__finish',
        style: 'padding:12px 14px;background:#2c7557;color:white;border:0;border-radius:8px;font-weight:700;cursor:pointer' }, 'Registrar venda fictícia (Enter)');
      const voltar = el('button', { type: 'button', style: 'background:none;border:0;color:#9fc1e6;cursor:pointer' }, 'Voltar aos itens (Esc)');
      painel.append(el('div', { style: 'display:grid;grid-template-columns:repeat(4,1fr);gap:6px' }, ...botoesForma), recebido, troco, finish, voltar);
      modal.append(pagar, painel, feedback);
      overlay.append(modal);
      document.body.append(overlay);

      const avisar = (texto, tipo) => {
        feedback.textContent = texto || '';
        feedback.style.padding = texto ? '10px' : '0';
        feedback.style.background = !texto ? 'transparent' : tipo === 'ok' ? '#1f5a43' : '#7a2330';
        feedback.style.color = '#fff';
      };
      const total = () => cart.reduce((s, c) => s + c.qty * Number(products.find(p => p.id === c.id)?.precoUnitario || 0), 0);
      const render = () => {
        rows.replaceChildren();
        for (const c of cart) {
          const p = products.find(x => x.id === c.id);
          const menos = el('button', { type: 'button', 'aria-label': 'Diminuir', onclick: () => alterar(c.id, -1) }, '−');
          const mais = el('button', { type: 'button', 'aria-label': 'Aumentar', onclick: () => alterar(c.id, +1) }, '+');
          const remover = el('button', { type: 'button', 'aria-label': 'Remover item', onclick: () => alterar(c.id, -c.qty) }, '✕');
          rows.append(el('div', { class: 'stockos-preview-pdv__item',
            style: 'display:grid;grid-template-columns:1fr auto auto auto auto;gap:8px;align-items:center;padding:6px 8px;background:rgba(127,127,127,.12);color:inherit;border-radius:8px;font-size:14px' },
          `${c.qty} × ${p.nome}`, menos, mais, moeda(Number(p.precoUnitario) * c.qty), remover));
        }
        totalEl.textContent = 'Total: ' + moeda(total());
        pagar.disabled = cart.length === 0;
        atualizarTroco();
      };
      const alterar = (id, delta) => {
        const item = cart.find(c => c.id === id);
        const p = products.find(x => x.id === id);
        if (!item || !p) return;
        const novo = item.qty + delta;
        if (novo > p.quantidadeAtual) { avisar('Estoque insuficiente: só há ' + p.quantidadeAtual + ' de ' + p.nome + '.'); return; }
        if (novo <= 0) cart.splice(cart.indexOf(item), 1); else item.qty = novo;
        avisar('');
        render();
        scan.focus();
      };
      const adicionar = (p, n) => {
        const existing = cart.find(c => c.id === p.id);
        const atual = existing?.qty || 0;
        if (atual + n > p.quantidadeAtual) { avisar('Estoque insuficiente: só há ' + p.quantidadeAtual + ' de ' + p.nome + '.'); return; }
        if (existing) existing.qty += n; else cart.push({ id: p.id, qty: n });
        avisar('');
        render();
      };
      const buscar = texto => {
        const alvo = normalizar(texto);
        if (!alvo) return [];
        return products.filter(p => normalizar(p.nome).includes(alvo)).slice(0, 8);
      };
      const mostrarSugestoes = () => {
        sugestoes.replaceChildren();
        const texto = scan.value.replace(/^\d{1,3}\*/, '');
        if (/^\d+$/.test(texto)) return;
        for (const p of buscar(texto)) {
          sugestoes.append(el('button', { type: 'button',
            style: 'text-align:left;padding:8px;border-radius:6px;border:1px solid #2f4560;background:#0d1828;color:#fff;cursor:pointer',
            onclick: () => { lerEntrada(String(p.id)); } },
          `#${p.id} · ${p.nome} · ${moeda(p.precoUnitario)} · ${p.quantidadeAtual} em estoque`));
        }
      };
      const lerEntrada = valorForcado => {
        const bruto = (valorForcado != null ? (scan.value.match(/^\d{1,3}\*/)?.[0] || '') + valorForcado : scan.value).trim();
        scan.value = '';
        sugestoes.replaceChildren();
        if (!bruto) return;
        const m = bruto.match(/^(\d{1,3})\*(.+)$/);
        const n = m ? Number(m[1]) : 1;
        const termo = m ? m[2].trim() : bruto;
        if (!Number.isInteger(n) || n <= 0) { avisar('Quantidade inválida.'); return; }
        let p = /^\d+$/.test(termo) ? products.find(x => x.id === Number(termo)) : null;
        if (!p) {
          const achados = buscar(termo);
          if (achados.length === 1) p = achados[0];
          else if (achados.length > 1) { scan.value = (m ? m[1] + '*' : '') + termo; mostrarSugestoes(); avisar('Mais de um produto: escolha na lista.'); return; }
        }
        if (!p) { avisar('Produto não encontrado para "' + termo + '".'); return; }
        adicionar(p, n);
        scan.focus();
      };
      const escolherForma = codigo => {
        forma = codigo;
        botoesForma.forEach(b => { b.style.background = b.dataset.forma === codigo ? '#e8a33d' : '#0d1828'; b.style.color = b.dataset.forma === codigo ? '#1b1b1b' : '#fff'; });
        recebido.hidden = codigo !== 'DINHEIRO';
        recebido.value = '';
        atualizarTroco();
        if (codigo === 'DINHEIRO') recebido.focus();
      };
      const atualizarTroco = () => {
        const r = Number(String(recebido.value).replace(',', '.'));
        const t = forma === 'DINHEIRO' && recebido.value !== '' ? Math.max(r - total(), 0) : 0;
        troco.textContent = 'Troco: ' + moeda(t);
        troco.hidden = forma !== 'DINHEIRO';
      };
      const abrirPagamento = () => {
        if (!cart.length) { avisar('Adicione produtos ao carrinho.'); return; }
        pagando = true;
        painel.hidden = false;
        pagar.hidden = true;
        escolherForma('DINHEIRO');
      };
      const fecharPagamento = () => {
        pagando = false;
        painel.hidden = true;
        pagar.hidden = false;
        scan.focus();
      };
      const registrar = async () => {
        if (!cart.length) { avisar('Adicione produtos ao carrinho.'); return; }
        const valorTotal = Math.round(total() * 100) / 100;
        let valorRecebido = null;
        if (forma === 'DINHEIRO' && recebido.value !== '') {
          valorRecebido = Math.round(Number(String(recebido.value).replace(',', '.')) * 100) / 100;
          if (!(valorRecebido >= valorTotal)) { avisar('O valor recebido é menor que o total.'); return; }
        }
        finish.disabled = true;
        try {
          // Um comando de demonstração valida e grava a venda de uma vez.
          const key = window.crypto?.randomUUID?.() || ('pdv_' + Date.now() + '_' + Math.random().toString(36).slice(2));
          const response = await fetch('/api/preview/transacoes/venda', {
            method: 'POST', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
              idempotencyKey: key, numeroCaixa: numero,
              itens: cart.map(c => ({ idProduto: c.id, quantidade: c.qty })),
              pagamentos: [{ forma, valor: valorTotal, valorRecebido }]
            })
          });
          if (!response.ok) {
            const error = await response.json().catch(() => ({}));
            throw Error(error.erro || 'Não foi possível registrar a venda fictícia.');
          }
          const trocoFinal = valorRecebido != null ? valorRecebido - valorTotal : 0;
          // Próximo cliente: carrinho limpo e foco de volta no campo de leitura.
          cart.splice(0, cart.length);
          products = (await (await fetch('/api/produtos')).json()).filter(p => p.ativo !== false);
          fecharPagamento();
          render();
          avisar(`Venda fictícia de ${moeda(valorTotal)} registrada (${FORMAS.find(f => f[0] === forma)[1]})`
            + (trocoFinal > 0 ? ` · TROCO ${moeda(trocoFinal)}` : '') + '. Pronto para o próximo cliente.', 'ok');
          if (typeof carregarCaixas === 'function') await carregarCaixas();
        } catch (e) {
          avisar(e.message);
        } finally {
          finish.disabled = false;
        }
      };
      const sair = () => {
        if (cart.length && !confirm('Sair do PDV? Os itens do carrinho serão descartados.')) { scan.focus(); return; }
        document.removeEventListener('keydown', teclas, true);
        overlay.remove();
      };
      const teclas = e => {
        if (!document.body.contains(overlay)) { document.removeEventListener('keydown', teclas, true); return; }
        if (e.key === 'Escape') { e.preventDefault(); if (pagando) fecharPagamento(); else sair(); return; }
        if (e.key === 'F10') { e.preventDefault(); if (!pagando) abrirPagamento(); return; }
        if (pagando) {
          const f = FORMAS.find(x => x[2] === e.key);
          if (f && e.target !== recebido) { e.preventDefault(); escolherForma(f[0]); return; }
          if (e.key === 'Enter') { e.preventDefault(); registrar(); }
          return;
        }
        if (e.key === 'Enter' && e.target === scan) { e.preventDefault(); lerEntrada(); }
      };
      document.addEventListener('keydown', teclas, true);
      scan.addEventListener('input', mostrarSugestoes);
      recebido.addEventListener('input', atualizarTroco);
      pagar.onclick = abrirPagamento;
      voltar.onclick = fecharPagamento;
      finish.onclick = registrar;
      close.onclick = sair;
      // Sem fechar ao clicar fora: no balcão, um clique errado perdia a venda.
      render();
      scan.focus();
      // O clique no botão PDV pode roubar o foco: garante o campo de leitura pronto.
      setTimeout(() => scan.focus(), 60);
    };
  }
  window.StockOSPreviewHooks = window.StockOSPreviewHooks || {};
  window.StockOSPreviewHooks.caixas = mountPreviewPdv;
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', mountPreviewPdv);
  else mountPreviewPdv();
})();
