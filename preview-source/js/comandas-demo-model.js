/* Preview-only domain model. No API writes, card charges or actual stock movements. */
(function () {
  'use strict';
  if (window.StockOSComandasDemoModel) return;

  const normalizeNumber = value => {
    const raw = String(value).trim();
    if (!/^[1-9][0-9]{0,3}$/.test(raw)) throw Error('Use um número de comanda entre 1 e 9999.');
    return Number(raw);
  };
  const clone = value => JSON.parse(JSON.stringify(value));
  const validQuantity = value => Number.isSafeInteger(value) && value > 0 && value <= 9999;
  const cents = value => {
    const number = Number(value);
    if (!Number.isFinite(number) || number < 0 || number > 1000000) throw Error('Preço inválido.');
    return Math.round(number * 100);
  };

  function createModel(saved) {
    const tabs = new Map();
    const raw = Array.isArray(saved) ? saved : [];
    raw.forEach(tab => {
      if (!tab || !['ABERTA', 'CONFERENCIA'].includes(tab.status)) return;
      let number;
      try { number = normalizeNumber(tab.numero); } catch { return; }
      if (tabs.has(number)) return;
      const items = Array.isArray(tab.itens) ? tab.itens : [];
      const validItems = items.filter(item =>
        item && Number.isSafeInteger(item.id) && item.id > 0 &&
        typeof item.nome === 'string' && item.nome.length <= 200 &&
        Number.isSafeInteger(item.precoCentavos) && item.precoCentavos >= 0 &&
        validQuantity(item.quantidade)
      ).map(item => ({
        id: item.id, nome: item.nome, precoCentavos: item.precoCentavos,
        quantidade: item.quantidade
      }));
      tabs.set(number, {
        numero: number, status: tab.status, itens: validItems,
        criadaEm: String(tab.criadaEm || '').slice(0, 35)
      });
    });

    function requireTab(numero) {
      const tab = tabs.get(normalizeNumber(numero));
      if (!tab) throw Error('Comanda não encontrada.');
      return tab;
    }
    function requireEditable(tab) {
      if (tab.status !== 'ABERTA') throw Error('Retome o atendimento antes de alterar esta comanda.');
    }
    function get(numero) {
      const tab = requireTab(numero);
      const result = clone(tab);
      result.totalCentavos = tab.itens.reduce((sum, item) =>
        sum + item.precoCentavos * item.quantidade, 0);
      result.quantidade = tab.itens.reduce((sum, item) => sum + item.quantidade, 0);
      return result;
    }
    function list() {
      return [...tabs.keys()].sort((a,b) => a-b).map(get);
    }
    function open(numero) {
      const number = normalizeNumber(numero);
      if (tabs.has(number)) throw Error('Esta comanda já existe. Selecione-a na lista.');
      tabs.set(number, {numero:number,status:'ABERTA',itens:[],criadaEm:new Date().toISOString()});
      return get(number);
    }
    function add(numero, produto, quantidade) {
      const tab = requireTab(numero);
      requireEditable(tab);
      const qty = Number(quantidade);
      if (!validQuantity(qty)) throw Error('A quantidade deve ser um inteiro positivo.');
      if (!produto || !Number.isSafeInteger(Number(produto.id)) || Number(produto.id) <= 0 ||
          produto.ativo === false) throw Error('Produto inválido.');
      const id = Number(produto.id);
      const max = Number(produto.quantidadeAtual);
      if (!Number.isSafeInteger(max) || max < 1) throw Error('Produto indisponível no estoque demonstrativo.');
      const existing = tab.itens.find(item => item.id === id);
      if ((existing ? existing.quantidade : 0) + qty > max) {
        throw Error('Quantidade acima do estoque demonstrativo disponível.');
      }
      if (existing) existing.quantidade += qty;
      else tab.itens.push({
        id, nome:String(produto.nome || 'Produto').slice(0,200),
        precoCentavos:cents(produto.precoUnitario), quantidade:qty
      });
      return get(numero);
    }
    function change(numero, id, delta, disponivel) {
      const tab = requireTab(numero);
      requireEditable(tab);
      const item = tab.itens.find(entry => entry.id === Number(id));
      if (!item) throw Error('Item não encontrado.');
      if (!Number.isSafeInteger(delta) || (delta !== 1 && delta !== -1)) {
        throw Error('Ajuste de quantidade inválido.');
      }
      const updated = item.quantidade + delta;
      if (updated > Number(disponivel)) throw Error('Quantidade acima do estoque demonstrativo disponível.');
      if (updated <= 0) tab.itens.splice(tab.itens.indexOf(item),1);
      else item.quantidade = updated;
      return get(numero);
    }
    function requestClose(numero) {
      const tab = requireTab(numero);
      requireEditable(tab);
      if (!tab.itens.length) throw Error('Adicione produtos antes de solicitar a conferência.');
      tab.status = 'CONFERENCIA';
      return get(numero);
    }
    function resume(numero) {
      const tab = requireTab(numero);
      if (tab.status !== 'CONFERENCIA') throw Error('A comanda já está aberta.');
      tab.status = 'ABERTA';
      return get(numero);
    }
    return {open, add, change, requestClose, resume, get, list};
  }

  window.StockOSComandasDemoModel = {createModel, normalizeNumber};
})();
