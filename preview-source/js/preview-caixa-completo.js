/*
 * StockOS · Demonstração do caixa completo (só no site de demonstração, nunca no sistema Java).
 *
 * Simula no navegador as respostas do servidor que o PDV e as comandas de verdade usam:
 * caixas, cardápio, códigos de barras, vendas, pagamentos, comandas, teclas rápidas.
 * As telas são as mesmas do sistema; só o servidor é de mentira. Os dados ficam só neste
 * navegador (localStorage) e "Recomeçar" volta ao início.
 */
(function () {
  'use strict';
  const KEY = 'stockos-caixa-completo-v2'; // v2: comandas com o nome do cliente
  const BASE = '/caixa-completo/';
  const PIN = '1234';
  const SENHA_GERENTE = '1234';

  // ── Usuários da demonstração ────────────────────────────────────────────
  const TODAS = ['VER_FINANCAS', 'VER_ESTOQUE', 'VER_COMPRAS', 'GERENCIAR_FUNCIONARIOS', 'VER_LOGS', 'EDITAR_ESTOQUE', 'VER_VENDAS'];
  const USUARIOS = {
    1: { ru: 1, nome: 'Admin', sobrenome: 'Demo', nomeClasse: 'SUPERIOR', permissoes: TODAS, perfil: 'ADMIN', ativo: true, senhaProvisoria: false, temPin: true },
    2: { ru: 2, nome: 'Ana', sobrenome: 'Caixa', nomeClasse: 'CAIXA', permissoes: ['VER_VENDAS'], perfil: 'OPERADOR', ativo: true, senhaProvisoria: false, temPin: true }
  };
  const nomeDe = ru => `${USUARIOS[ru].nome} ${USUARIOS[ru].sobrenome}`;
  window.STOCKOS_PREVIEW = true;
  window.StockosPreviewUser = Object.assign({ viaPin: false }, USUARIOS[1]);

  // ── Dados de exemplo ────────────────────────────────────────────────────
  const hoje = () => { const d = new Date(); return [d.getFullYear(), String(d.getMonth() + 1).padStart(2, '0'), String(d.getDate()).padStart(2, '0')].join('-'); };
  const agora = () => { const d = new Date(); const p = n => String(n).padStart(2, '0'); return `${hoje()}T${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`; };
  const hora = (h, m) => `${hoje()}T${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:00`;
  const r2 = v => Math.round((Number(v) + Number.EPSILON) * 100) / 100;
  const r4 = v => Math.round((Number(v) + Number.EPSILON) * 10000) / 10000;

  function inicial() {
    const categorias = [
      { id: 1, codigo: 'bebidas', nomeExibicao: 'Bebidas', ordemExibicao: 0, ativo: true },
      { id: 2, codigo: 'gelo', nomeExibicao: 'Gelo', ordemExibicao: 1, ativo: true },
      { id: 3, codigo: 'porcoes', nomeExibicao: 'Porções', ordemExibicao: 2, ativo: true },
      { id: 4, codigo: 'caldos', nomeExibicao: 'Caldos', ordemExibicao: 3, ativo: true }
    ];
    // [nome, categoria, estoque, custo, [variação, preço, padrão]..., código de barras]
    const produtos = [
      ['Skol lata 350ml', 1, 96, 2.75, [['Gelada', 4.50, true], ['Natural', 3.99]], '7891000100103'],
      ['Brahma Duplo Malte lata 350ml', 1, 72, 3.05, [['Gelada', 4.99, true], ['Natural', 4.29]], '7891000100509'],
      ['Heineken long neck 330ml', 1, 48, 5.40, [['Gelada', 7.99, true], ['Natural', 7.49]], '7891000100608'],
      ['Coca-Cola lata 350ml', 1, 60, 3.20, [['Gelada', 5.50, true], ['Natural', 4.99]], '7891000101001'],
      ['Água mineral 500ml', 1, 60, 1.10, [['Gelada', 3.00, true], ['Natural', 2.50]], '7891000100806'],
      ['Gelo 5kg', 2, 20, 6.00, [['Unidade', 10.00, true]], '7891000100301'],
      ['Copo de gelo', 2, 100, 0.40, [['Unidade', 1.50, true]], null],
      ['Porção de batata frita', 3, 15, 9.00, [['Unidade', 25.00, true]], null],
      ['Caldo de mocotó', 4, 12, 5.00, [['Unidade', 14.00, true]], null]
    ];
    const itens = [];
    const variacoes = [];
    const codigos = [];
    let idVar = 1;
    produtos.forEach(([nome, cat, estoque, custo, vars, ean], i) => {
      const id = i + 1;
      const categoria = categorias.find(c => c.id === cat);
      itens.push({ id, codigo: 'demo_' + id, nome, descricaoCurta: null, precoVenda: vars[0][1], tipoItem: 'ESTOQUE_DIRETO', ativo: true,
        ordemExibicao: i, categoriaId: cat, categoriaCodigo: categoria.codigo, categoriaNomeExibicao: categoria.nomeExibicao,
        categoriaOrdemExibicao: categoria.ordemExibicao, produtoVinculadoId: id, quantidadeAtual: estoque, custo });
      vars.forEach(([vNome, preco, padrao], j) => {
        const v = { id: idVar++, itemCardapioId: id, nome: vNome, codigo: vNome.toUpperCase(), precoVenda: preco, padrao: !!padrao, ordemExibicao: j };
        variacoes.push(v);
        if (ean && padrao) codigos.push({ id: codigos.length + 1, codigo: ean, variacaoId: v.id, padrao: true });
      });
    });
    const db = { seq: { venda: 0, item: 0, mov: 0, caixa: 1 }, categorias, itens, variacoes, codigos,
      caixas: [{ caixaId: 1, numeroCaixa: 1, status: 'ABERTO', saldoInicial: 100, dataAbertura: hora(8, 0), dataEncerramento: null, nomeOperador: nomeDe(1) }],
      movs: [], vendas: {}, comandas: [], teclas: [], chaves: {}, operadorRu: 1 };
    const variacao = (nome, vNome) => variacoes.find(v => itens.find(i => i.id === v.itemCardapioId).nome === nome && v.nome === vNome).id;
    db.teclas = [
      { posicao: 1, variacaoId: variacao('Gelo 5kg', 'Unidade') },
      { posicao: 2, variacaoId: variacao('Copo de gelo', 'Unidade') },
      { posicao: 3, variacaoId: variacao('Porção de batata frita', 'Unidade') },
      { posicao: 4, variacaoId: variacao('Caldo de mocotó', 'Unidade') }
    ];
    // Duas mesas já consumindo, para a tela Comandas não começar vazia.
    abrirComandaComItens(db, 7, hora(18, 30), [[variacao('Skol lata 350ml', 'Gelada'), 2], [variacao('Porção de batata frita', 'Unidade'), 1]], 'Carlos');
    abrirComandaComItens(db, 12, hora(19, 5), [[variacao('Heineken long neck 330ml', 'Gelada'), 2], [variacao('Caldo de mocotó', 'Unidade'), 1]], 'Fernanda');
    return db;
  }

  function abrirComandaComItens(base, numero, quando, itens, cliente) {
    const venda = criarVenda(base, 'SALAO', quando);
    itens.forEach(([variacaoId, qtd]) => venda.itens.push(novoItem(base, variacaoId, qtd, null)));
    base.comandas.push({ numero, vendaId: venda.id, status: 'ABERTA', abertaEm: quando, cliente: cliente || null });
  }

  let db;
  try { db = JSON.parse(localStorage.getItem(KEY) || 'null'); } catch (e) { db = null; }
  if (!db || !db.seq || !Array.isArray(db.itens) || !db.vendas) db = inicial();
  // Depois de Recomeçar nada mais grava: uma atualização da tela que termine durante a troca de
  // página não pode devolver os dados antigos.
  let recomecando = false;
  const salvar = () => { if (recomecando) return; try { localStorage.setItem(KEY, JSON.stringify(db)); } catch (e) { /* sem armazenamento: fica só na memória */ } };
  salvar();

  // Quem está operando: o mesmo usuário que a tela guarda (troca de operador por PIN).
  try {
    const guardado = JSON.parse(sessionStorage.getItem('usuario') || 'null');
    db.operadorRu = guardado && USUARIOS[guardado.ru] ? guardado.ru : 1;
  } catch (e) { db.operadorRu = 1; }

  // ── Regras ──────────────────────────────────────────────────────────────
  function criarVenda(base, canal, quando) {
    const id = ++base.seq.venda;
    const venda = { id, numero: 'DEMO-' + id, status: 'RASCUNHO', canal: canal || 'BALCAO', criadaEm: quando || agora(), finalizadaEm: null, canceladaEm: null, motivoCancelamento: null, itens: [] };
    base.vendas[id] = venda;
    return venda;
  }

  function novoItem(base, variacaoId, quantidade, observacao) {
    const v = base.variacoes.find(x => x.id === Number(variacaoId));
    if (!v) throw erro(404, 'Produto não encontrado.', 'VARIACAO_NAO_ENCONTRADA');
    const item = base.itens.find(i => i.id === v.itemCardapioId);
    return { id: ++base.seq.item, itemCardapioId: item.id, item: item.nome, variacaoId: v.id, variacao: v.nome, codigoVariacao: v.codigo,
      tipoComposicao: 'PRODUTO_DIRETO', quantidade: Number(quantidade), precoUnitarioBase: v.precoVenda, descontoUnitario: 0, acrescimoUnitario: 0,
      custoUnitarioEstimado: item.custo, status: 'ATIVO', observacao: observacao || null, partes: [], modificadores: [], consumos: [] };
  }

  function itemResposta(i) {
    const final = r4(i.precoUnitarioBase - i.descontoUnitario + i.acrescimoUnitario);
    return Object.assign({}, i, { precoUnitarioFinal: final, subtotal: r4(final * i.quantidade), custoTotalEstimado: r4(i.custoUnitarioEstimado * i.quantidade),
      margemBrutaTotalEstimada: r4((final - i.custoUnitarioEstimado) * i.quantidade), composicaoMultipla: false });
  }

  function vendaResposta(v) {
    const itens = v.itens.map(itemResposta);
    const ativos = itens.filter(i => i.status === 'ATIVO');
    const soma = (lista, campo) => r2(lista.reduce((s, i) => s + i[campo], 0));
    const subtotal = r2(ativos.reduce((s, i) => s + i.precoUnitarioBase * i.quantidade, 0));
    const total = soma(ativos, 'subtotal');
    return { id: v.id, numero: v.numero, status: v.status, filialId: 1, canal: v.canal, subtotal, descontoTotal: r2(subtotal - total), acrescimoTotal: 0,
      valorTotal: total, valorAtivo: total, valorCancelado: soma(itens.filter(i => i.status !== 'ATIVO'), 'subtotal'),
      custoIngredientesTotal: soma(ativos, 'custoTotalEstimado'), margemBrutaEstimadaTotal: soma(ativos, 'margemBrutaTotalEstimada'),
      tipoAnalise: 'ESTIMATIVA_APENAS_CUSTO_ESTOQUE', criadaEm: v.criadaEm, finalizadaEm: v.finalizadaEm, canceladaEm: v.canceladaEm,
      motivoCancelamento: v.motivoCancelamento, itens };
  }

  const totalVenda = v => r2(v.itens.filter(i => i.status === 'ATIVO').reduce((s, i) => s + itemResposta(i).subtotal, 0));
  const ativosDe = v => v.itens.filter(i => i.status === 'ATIVO');
  const comandaAberta = numero => db.comandas.find(c => c.numero === Number(numero) && c.status === 'ABERTA');
  const comandaDaVenda = vendaId => db.comandas.find(c => c.vendaId === Number(vendaId) && c.status === 'ABERTA');

  function resumoComanda(c) {
    const v = db.vendas[c.vendaId];
    return { numero: c.numero, vendaId: c.vendaId, abertaEm: c.abertaEm, itens: ativosDe(v).reduce((s, i) => s + i.quantidade, 0), total: totalVenda(v), cliente: c.cliente || null };
  }

  function detalheComanda(c) {
    const v = db.vendas[c.vendaId];
    return Object.assign(resumoComanda(c), { linhas: ativosDe(v).map(i => ({ nome: i.item, variacao: i.variacao, quantidade: i.quantidade, subtotal: r2(itemResposta(i).subtotal) })) });
  }

  // Mesma regra do servidor: sem espaços sobrando, em branco é "sem nome", até 60 letras.
  function nomeCliente(texto) {
    const limpo = String(texto == null ? '' : texto).replace(/\s+/g, ' ').trim();
    if (limpo.length > 60) throw erro(400, 'O nome do cliente vai até 60 letras.', null);
    return limpo || null;
  }

  function abrirOuRetomar(numero, cliente) {
    if (!(numero >= 1 && numero <= 9999)) throw erro(400, 'O número da comanda vai de 1 a 9999.', 'COMANDA_NUMERO_INVALIDO');
    const nome = nomeCliente(cliente);
    const existente = comandaAberta(numero);
    if (existente) {
      if (nome) existente.cliente = nome;
      return existente;
    }
    const venda = criarVenda(db, 'SALAO');
    const nova = { numero, vendaId: venda.id, status: 'ABERTA', abertaEm: agora(), cliente: nome };
    db.comandas.push(nova);
    return nova;
  }

  function caixaResposta(c) {
    const movs = db.movs.filter(m => m.caixaId === c.caixaId);
    const soma = (filtro) => r2(movs.filter(filtro).reduce((s, m) => s + m.valor, 0));
    const vendas = soma(m => m.tipo === 'VENDA') - soma(m => m.tipo === 'ESTORNO_VENDA');
    const dinheiro = soma(m => m.tipo === 'VENDA' && m.formaPagamento === 'DINHEIRO') - soma(m => m.tipo === 'ESTORNO_VENDA' && m.formaPagamento === 'DINHEIRO');
    const suprimentos = soma(m => m.tipo === 'SUPRIMENTO');
    const sangrias = soma(m => m.tipo === 'SANGRIA');
    const porForma = {};
    ['DINHEIRO', 'PIX', 'DEBITO', 'CREDITO', 'VOUCHER'].forEach(f => {
      porForma[f] = r2(soma(m => m.tipo === 'VENDA' && m.formaPagamento === f) - soma(m => m.tipo === 'ESTORNO_VENDA' && m.formaPagamento === f));
    });
    const quantidade = new Set(movs.filter(m => m.tipo === 'VENDA').map(m => m.vendaId)).size;
    return { caixaId: c.caixaId, id: c.caixaId, numeroCaixa: c.numeroCaixa, status: c.status, saldoInicial: c.saldoInicial,
      saldoAtual: r2(c.saldoInicial + vendas + suprimentos - sangrias), totalVendas: r2(vendas), totalEntradas: r2(vendas + suprimentos),
      totalSaidas: sangrias, nomeOperador: c.nomeOperador, dataAbertura: c.dataAbertura, dataEncerramento: c.dataEncerramento,
      dinheiroNaGaveta: r2(c.saldoInicial + dinheiro + suprimentos - sangrias), vendasPorForma: porForma, quantidadeVendas: quantidade,
      ticketMedio: quantidade ? r2(vendas / quantidade) : 0 };
  }

  function registrarMov(caixa, tipo, valor, extra) {
    db.movs.push(Object.assign({ id: ++db.seq.mov, caixaId: caixa.caixaId, numeroCaixa: caixa.numeroCaixa, tipo, valor: r2(valor),
      descricao: '', dataHora: agora(), operador: nomeDe(db.operadorRu), formaPagamento: null, vendaId: null }, extra || {}));
  }

  const ROTULO = { DINHEIRO: 'Dinheiro', PIX: 'Pix', DEBITO: 'Débito', CREDITO: 'Crédito', VOUCHER: 'Voucher' };

  // Gerente: quem opera tem o financeiro, ou o pedido traz a senha do gerente (1234 na demonstração).
  function exigirGerente(corpo, acao) {
    if (USUARIOS[db.operadorRu].permissoes.includes('VER_FINANCAS')) return;
    const a = corpo && corpo.autorizacao;
    if (!a || !a.nome || !a.senha) throw erro(403, `${acao} precisa da autorização do gerente.`, 'AUTORIZACAO_NECESSARIA');
    if (String(a.senha) !== SENHA_GERENTE) throw erro(403, 'Login do gerente invalido.', 'ACESSO_NEGADO');
  }

  function erro(status, mensagem, codigo) {
    const e = new Error(mensagem);
    e.status = status;
    e.codigo = codigo || null;
    return e;
  }

  // ── Rotas ───────────────────────────────────────────────────────────────
  function rotear(metodo, caminho, url, corpo, cabecalhos) {
    let m;
    if (caminho === '/api/auth/config') return { mode: 'local' };
    if (caminho === '/api/auth/logout') return {};
    if (caminho === '/api/sistema/hora') return { agora: Date.now(), fuso: 'America/Sao_Paulo', nomeFuso: 'Horário de Brasília' };
    if (caminho === '/api/auth/operadores') return [{ ru: 1, nome: nomeDe(1) }, { ru: 2, nome: nomeDe(2) }];
    if (caminho === '/api/auth/pin') return Object.assign({ viaPin: false }, USUARIOS[db.operadorRu]);
    if (caminho === '/api/auth/trocar-operador' && metodo === 'POST') {
      if (!USUARIOS[corpo.ru]) throw erro(400, 'Operador nao encontrado.');
      if (String(corpo.pin) !== PIN) throw erro(400, 'PIN incorreto.');
      db.operadorRu = corpo.ru;
      return Object.assign({ viaPin: true }, USUARIOS[corpo.ru]);
    }

    // Cardápio
    if (caminho === '/api/cardapio/categorias') return db.categorias;
    if (caminho === '/api/cardapio/itens' && metodo === 'GET') {
      return db.itens.map(i => Object.assign({}, i, { nivelEstoque: i.quantidadeAtual > 0 ? 'ADEQUADO' : 'SEM_ESTOQUE', controladoPorEstoque: true, disponivel: true }));
    }
    if (caminho === '/api/cardapio/disponibilidade') {
      return { filialId: 1, canal: url.searchParams.get('canal') || 'BALCAO',
        itens: db.itens.map(i => ({ itemCardapioId: i.id, itemDisponivel: i.quantidadeAtual > 0,
          quantidadeVariacoesDisponiveis: i.quantidadeAtual > 0 ? db.variacoes.filter(v => v.itemCardapioId === i.id).length : 0, quantidadeVariacoesIndisponiveis: 0 })),
        variacoes: db.variacoes.map(v => { const i = db.itens.find(x => x.id === v.itemCardapioId); return { itemCardapioId: i.id, variacaoId: v.id, filialId: 1,
          disponivel: i.quantidadeAtual > 0, estadoPrincipal: i.quantidadeAtual > 0 ? 'DISPONIVEL' : 'SEM_ESTOQUE', capacidadeEstimada: i.quantidadeAtual,
          tipoCapacidade: 'ESTIMATIVA_ITEM_ISOLADO', garantia: false, motivos: [], alertas: [] }; }),
        calculadoEm: agora() };
    }
    if ((m = caminho.match(/^\/api\/cardapio\/itens\/(\d+)\/variacoes$/))) {
      const item = db.itens.find(i => i.id === Number(m[1]));
      return db.variacoes.filter(v => v.itemCardapioId === Number(m[1])).map(v => Object.assign({}, v, { itemCodigo: item.codigo, itemNome: item.nome,
        tipoComposicao: 'PRODUTO_DIRETO', produtoDiretoId: item.id, produtoDiretoNome: item.nome, quantidadeProdutoDiretoBase: 1, ativa: true, version: 0 }));
    }
    if ((m = caminho.match(/^\/api\/cardapio\/variacoes\/(\d+)\/configuracao$/))) {
      const v = db.variacoes.find(x => x.id === Number(m[1]));
      if (!v) throw erro(404, 'Produto não encontrado.');
      return { variacaoId: v.id, nome: v.nome, precoBase: v.precoVenda, configuracaoVersao: 'demo', grupos: [] };
    }
    if ((m = caminho.match(/^\/api\/cardapio\/variacoes\/(\d+)\/validar-configuracao$/))) {
      return { valida: true, disponivel: true, motivos: [], configuracaoVersao: 'demo', selecoes: [] };
    }

    // Códigos de barras e teclas rápidas
    const leituraVariacao = v => { const i = db.itens.find(x => x.id === v.itemCardapioId);
      return { variacaoId: v.id, variacaoNome: v.nome, itemCardapioId: i.id, itemNome: i.nome, precoVenda: v.precoVenda, produtoId: i.id, quantidadeProdutoBase: 1 }; };
    if (caminho === '/api/pdv/codigos' && metodo === 'GET') {
      return db.codigos.map(c => ({ id: c.id, codigo: c.codigo, tipo: 'EAN13', padrao: c.padrao, variacao: leituraVariacao(db.variacoes.find(v => v.id === c.variacaoId)) }));
    }
    if ((m = caminho.match(/^\/api\/pdv\/codigos\/(.+)$/))) {
      const codigo = decodeURIComponent(m[1]);
      const achado = db.codigos.find(c => c.codigo === codigo);
      if (!achado) throw erro(404, `Produto nao cadastrado para o codigo ${codigo}.`, 'CODIGO_VENDA_NAO_ENCONTRADO');
      const v = db.variacoes.find(x => x.id === achado.variacaoId);
      return { codigo, tipo: 'EAN13', variacao: leituraVariacao(v),
        alternativas: db.variacoes.filter(x => x.itemCardapioId === v.itemCardapioId && x.id !== v.id).map(leituraVariacao) };
    }
    if (caminho.startsWith('/api/pdv/cadastro')) throw erro(403, 'Na demonstração não dá para cadastrar produtos pelo caixa.', 'DEMONSTRACAO');
    const tecla = t => Object.assign({ posicao: t.posicao }, leituraVariacao(db.variacoes.find(v => v.id === t.variacaoId)));
    if (caminho === '/api/pdv/teclas' && metodo === 'GET') return db.teclas.map(tecla);
    if (caminho === '/api/pdv/teclas/opcoes') return db.variacoes.map(v => Object.assign({ posicao: 0 }, leituraVariacao(v)));
    if (caminho === '/api/pdv/teclas' && metodo === 'PUT') {
      exigirGerente(corpo, 'Mudar as teclas rápidas');
      db.teclas = (corpo.teclas || []).filter(t => t.posicao >= 1 && t.posicao <= 8).map(t => ({ posicao: t.posicao, variacaoId: t.variacaoId }));
      return db.teclas.map(tecla);
    }

    // Vendas
    if (caminho === '/api/vendas' && metodo === 'POST') {
      const venda = criarVenda(db, corpo.canal || 'BALCAO');
      (corpo.itens || []).forEach(i => venda.itens.push(novoItem(db, i.variacaoId, i.quantidade || i.quantidadeItem || 1, i.observacao)));
      return [201, vendaResposta(venda)];
    }
    if ((m = caminho.match(/^\/api\/vendas\/(\d+)$/)) && metodo === 'GET') {
      const v = db.vendas[m[1]];
      if (!v) throw erro(404, 'Venda nao encontrada.', 'VENDA_NAO_ENCONTRADA');
      return vendaResposta(v);
    }
    if ((m = caminho.match(/^\/api\/vendas\/(\d+)\/itens$/)) && metodo === 'POST') {
      const v = rascunho(m[1]);
      const item = novoItem(db, corpo.variacaoId, corpo.quantidade || 1, corpo.observacao);
      v.itens.push(item);
      return [201, itemResposta(item)];
    }
    if ((m = caminho.match(/^\/api\/vendas\/(\d+)\/itens\/(\d+)$/))) {
      const v = rascunho(m[1]);
      const item = v.itens.find(i => i.id === Number(m[2]) && i.status === 'ATIVO');
      if (!item) throw erro(404, 'Item da venda nao encontrado.', 'ITEM_VENDA_NAO_ENCONTRADO');
      if (metodo === 'DELETE') { v.itens = v.itens.filter(i => i !== item); return [204, null]; }
      if (metodo === 'PUT') {
        const novo = novoItem(db, corpo.variacaoId || item.variacaoId, corpo.quantidade || item.quantidade, corpo.observacao);
        Object.assign(item, novo, { id: item.id });
        return itemResposta(item);
      }
    }
    if ((m = caminho.match(/^\/api\/vendas\/(\d+)\/desconto$/)) && metodo === 'POST') {
      const v = rascunho(m[1]);
      const ativos = ativosDe(v);
      const bruto = ativos.reduce((s, i) => s + i.precoUnitarioBase * i.quantidade, 0);
      const desconto = corpo.percentual != null ? bruto * Number(corpo.percentual) / 100 : Number(corpo.valor || 0);
      if (desconto < 0 || desconto > bruto) throw erro(400, 'Desconto maior que o total da venda.', 'DESCONTO_INVALIDO');
      if (desconto > 0) exigirGerente(corpo, 'O desconto');
      ativos.forEach(i => { i.descontoUnitario = bruto ? r4(desconto * (i.precoUnitarioBase * i.quantidade / bruto) / i.quantidade) : 0; });
      return vendaResposta(v);
    }
    if ((m = caminho.match(/^\/api\/vendas\/(\d+)\/finalizar$/)) && metodo === 'POST') {
      const chave = cabecalhos['idempotency-key'];
      const v = db.vendas[m[1]];
      if (!v) throw erro(404, 'Venda nao encontrada.', 'VENDA_NAO_ENCONTRADA');
      if (chave && db.chaves[chave] === v.id && v.status === 'FINALIZADA') return vendaResposta(v);
      rascunho(m[1]);
      const caixa = db.caixas.find(c => c.numeroCaixa === Number(corpo.numeroCaixaLegado) && c.status === 'ABERTO');
      if (!caixa) throw erro(400, `O caixa ${corpo.numeroCaixaLegado} não está aberto.`, 'CAIXA_FECHADO');
      if (!ativosDe(v).length) throw erro(400, 'A venda não tem itens.', 'VENDA_SEM_ITENS');
      const total = totalVenda(v);
      const pagamentos = corpo.pagamentos || [];
      const pago = r2(pagamentos.reduce((s, p) => s + Number(p.valor || 0), 0));
      if (Math.abs(pago - total) > 0.009) {
        throw erro(400, `Os pagamentos somam R$ ${pago.toFixed(2).replace('.', ',')}, mas a venda é de R$ ${total.toFixed(2).replace('.', ',')}.`, 'PAGAMENTO_DIVERGENTE');
      }
      for (const i of ativosDe(v)) {
        const produto = db.itens.find(x => x.id === i.itemCardapioId);
        if (produto.quantidadeAtual < i.quantidade) throw erro(409, `Estoque insuficiente de ${produto.nome}.`, 'ESTOQUE_INSUFICIENTE');
      }
      ativosDe(v).forEach(i => { db.itens.find(x => x.id === i.itemCardapioId).quantidadeAtual -= i.quantidade; });
      pagamentos.forEach(p => registrarMov(caixa, 'VENDA', Number(p.valor), {
        formaPagamento: p.forma, vendaId: v.id, descricao: `Venda #${v.id} (${ROTULO[p.forma] || p.forma})` }));
      v.status = 'FINALIZADA';
      v.finalizadaEm = agora();
      v.caixaId = caixa.caixaId;
      if (chave) db.chaves[chave] = v.id;
      const comanda = comandaDaVenda(v.id);
      if (comanda) comanda.status = 'FECHADA';
      return vendaResposta(v);
    }
    if ((m = caminho.match(/^\/api\/vendas\/(\d+)\/cancelar$/)) && metodo === 'POST') {
      const v = db.vendas[m[1]];
      if (!v || v.status !== 'FINALIZADA') throw erro(400, 'Só dá para cancelar venda paga.', 'VENDA_NAO_FINALIZADA');
      if (!corpo.motivo || String(corpo.motivo).trim().length < 3) throw erro(400, 'Diga o motivo do cancelamento.', 'MOTIVO_OBRIGATORIO');
      exigirGerente(corpo, 'Cancelar venda');
      const caixa = db.caixas.find(c => c.caixaId === v.caixaId);
      db.movs.filter(x => x.vendaId === v.id && x.tipo === 'VENDA').forEach(x => registrarMov(caixa, 'ESTORNO_VENDA', x.valor, {
        formaPagamento: x.formaPagamento, vendaId: v.id, descricao: `Cancelamento da venda #${v.id}` }));
      ativosDe(v).forEach(i => { db.itens.find(x => x.id === i.itemCardapioId).quantidadeAtual += i.quantidade; });
      v.status = 'CANCELADA';
      v.canceladaEm = agora();
      v.motivoCancelamento = corpo.motivo;
      return vendaResposta(v);
    }

    // Comandas
    if (caminho === '/api/comandas' && metodo === 'GET') return db.comandas.filter(c => c.status === 'ABERTA').map(resumoComanda);
    if ((m = caminho.match(/^\/api\/comandas\/(\d+)$/))) {
      const c = comandaAberta(m[1]);
      if (!c) throw erro(404, `Nao ha comanda aberta com o numero ${m[1]}.`, 'COMANDA_NAO_ENCONTRADA');
      if (metodo === 'DELETE') {
        if (ativosDe(db.vendas[c.vendaId]).length) throw erro(422, `A comanda ${c.numero} tem itens. Tire os itens antes de excluir.`, 'COMANDA_COM_ITENS');
        c.status = 'CANCELADA';
        return [204, null];
      }
      return detalheComanda(c);
    }
    if ((m = caminho.match(/^\/api\/comandas\/(\d+)\/abrir$/))) {
      const c = abrirOuRetomar(Number(m[1]), corpo.cliente);
      return { numero: c.numero, vendaId: c.vendaId, status: 'ABERTA', abertaEm: c.abertaEm, cliente: c.cliente || null };
    }
    if ((m = caminho.match(/^\/api\/comandas\/(\d+)\/cliente$/)) && metodo === 'PUT') {
      const c = comandaAberta(m[1]);
      if (!c) throw erro(404, `Nao ha comanda aberta com o numero ${m[1]}.`, 'COMANDA_NAO_ENCONTRADA');
      c.cliente = nomeCliente(corpo.cliente);
      return { numero: c.numero, vendaId: c.vendaId, status: 'ABERTA', abertaEm: c.abertaEm, cliente: c.cliente || null };
    }
    if ((m = caminho.match(/^\/api\/comandas\/(\d+)\/lancar$/))) {
      nomeCliente(corpo.cliente);
      const c = abrirOuRetomar(Number(m[1]), corpo.cliente);
      const v = rascunho(c.vendaId);
      (corpo.itens || []).filter(i => Number(i.quantidade) > 0).forEach(i => {
        const mesmo = ativosDe(v).find(x => x.variacaoId === Number(i.variacaoId) && !x.observacao);
        if (mesmo) mesmo.quantidade += Number(i.quantidade);
        else v.itens.push(novoItem(db, i.variacaoId, i.quantidade, null));
      });
      return detalheComanda(c);
    }
    if ((m = caminho.match(/^\/api\/comandas\/(\d+)\/cozinha$/))) return { enviado: false };
    if ((m = caminho.match(/^\/api\/comandas\/(\d+)\/retirar$/))) {
      const c = comandaAberta(m[1]);
      if (!c) throw erro(404, `Nao ha comanda aberta com o numero ${m[1]}.`, 'COMANDA_NAO_ENCONTRADA');
      exigirGerente(corpo, 'Tirar item da comanda');
      if (!corpo.motivo || String(corpo.motivo).trim().length < 3) throw erro(400, 'Diga o motivo de tirar o item.', 'RETIRADA_SEM_MOTIVO');
      const v = rascunho(c.vendaId);
      let valor = 0;
      let retirados = 0;
      (corpo.itens || []).forEach(p => {
        const item = ativosDe(v).find(i => i.id === Number(p.itemId));
        const qtd = Number(p.quantidade);
        if (!item || !(qtd > 0)) return;
        if (qtd > item.quantidade) throw erro(400, `Só há ${item.quantidade} de ${item.item} na comanda.`, 'RETIRADA_QUANTIDADE');
        valor += itemResposta(item).subtotal * qtd / item.quantidade;
        if (qtd === item.quantidade) v.itens = v.itens.filter(i => i !== item); else item.quantidade -= qtd;
        retirados++;
      });
      const vazia = !ativosDe(v).length;
      if (vazia) c.status = 'CANCELADA';
      return { numero: c.numero, itensRetirados: retirados, valor: r2(valor), comandaFechada: vazia };
    }
    if ((m = caminho.match(/^\/api\/comandas\/(\d+)\/transferir$/))) {
      const destino = abrirOuRetomar(Number(m[1]));
      const origem = rascunho(corpo.vendaOrigemId);
      if (destino.vendaId === origem.id) throw erro(400, `Os itens já estão na comanda ${destino.numero}.`, 'TRANSFERENCIA_MESMA_COMANDA');
      const vDestino = rascunho(destino.vendaId);
      let movidos = 0;
      (corpo.itens || []).forEach(p => {
        const item = ativosDe(origem).find(i => i.id === Number(p.itemId));
        const qtd = Number(p.quantidade);
        if (!item || !(qtd > 0)) return;
        if (qtd > item.quantidade) throw erro(400, `Só há ${item.quantidade} de ${item.item} para transferir.`, 'TRANSFERENCIA_QUANTIDADE');
        if (qtd === item.quantidade) { origem.itens = origem.itens.filter(i => i !== item); vDestino.itens.push(item); }
        else { item.quantidade -= qtd; vDestino.itens.push(novoItem(db, item.variacaoId, qtd, item.observacao)); }
        movidos++;
      });
      const origemVazia = !ativosDe(origem).length;
      let fechada = null;
      const cOrigem = comandaDaVenda(origem.id);
      if (origemVazia && cOrigem) { cOrigem.status = 'CANCELADA'; fechada = cOrigem.numero; }
      return { destino: destino.numero, vendaDestinoId: destino.vendaId, itensMovidos: movidos, origemVazia, comandaOrigemFechada: fechada };
    }

    // Caixas
    if (caminho === '/api/caixas' && metodo === 'GET') return db.caixas.map(caixaResposta);
    if (caminho === '/api/caixas/consolidado') return { data: hoje(), resumo: 'Demonstração: os valores são fictícios e ficam só neste navegador.' };
    if (caminho === '/api/caixas/abrir' && metodo === 'POST') {
      const numero = Number(corpo.numeroCaixa);
      if (!(numero > 0)) throw erro(400, 'Numero do caixa invalido.');
      if (db.caixas.some(c => c.numeroCaixa === numero && c.status === 'ABERTO')) throw erro(400, `O caixa ${numero} já está aberto.`);
      const caixa = { caixaId: ++db.seq.caixa, numeroCaixa: numero, status: 'ABERTO', saldoInicial: r2(corpo.saldoInicial || 0), dataAbertura: agora(), dataEncerramento: null, nomeOperador: nomeDe(db.operadorRu) };
      db.caixas.push(caixa);
      return caixaResposta(caixa);
    }
    if ((m = caminho.match(/^\/api\/caixas\/(sangria|suprimento)$/)) && metodo === 'POST') {
      const caixa = db.caixas.find(c => c.numeroCaixa === Number(corpo.numeroCaixa) && c.status === 'ABERTO');
      if (!caixa) throw erro(400, 'Caixa não está aberto.');
      if (m[1] === 'sangria') exigirGerente(corpo, 'A sangria');
      registrarMov(caixa, m[1].toUpperCase(), Number(corpo.valor), { descricao: corpo.descricao || '' });
      return caixaResposta(caixa);
    }
    if (caminho === '/api/caixas/encerrar' && metodo === 'POST') {
      const caixa = db.caixas.find(c => c.numeroCaixa === Number(corpo.numeroCaixa) && c.status === 'ABERTO');
      if (!caixa) throw erro(400, 'Caixa não está aberto.');
      const abertas = db.comandas.filter(c => c.status === 'ABERTA').map(resumoComanda).filter(c => c.itens > 0);
      if (abertas.length && !corpo.deixarComandasAbertas) {
        const total = abertas.reduce((s, c) => s + c.total, 0);
        return [409, { erro: `${abertas.length === 1 ? 'A comanda ' + abertas[0].numero + ' está aberta' : 'As comandas ' + abertas.map(c => c.numero).join(', ') + ' estão abertas'}, somando R$ ${total.toFixed(2).replace('.', ',')}. Receba antes de encerrar, ou confirme que elas ficam abertas.`, comandasAbertas: abertas }];
      }
      const resposta = caixaResposta(caixa);
      caixa.status = 'ENCERRADO';
      caixa.dataEncerramento = agora();
      const contado = r2(corpo.valorContado || 0);
      return { caixaId: caixa.caixaId, numeroCaixa: caixa.numeroCaixa, data: hoje(), timestamp: agora(), saldoInicial: caixa.saldoInicial,
        totalEntradas: resposta.totalEntradas, totalSaidas: resposta.totalSaidas, totalVendas: resposta.totalVendas, saldoFinal: resposta.saldoAtual,
        valorSistema: resposta.dinheiroNaGaveta, valorContado: contado, divergencia: r2(contado - resposta.dinheiroNaGaveta),
        quantidadeMovimentacoes: db.movs.filter(x => x.caixaId === caixa.caixaId).length, nomeOperador: caixa.nomeOperador,
        abertoPor: caixa.nomeOperador, fechadoPor: nomeDe(db.operadorRu), observacao: corpo.observacao || '' };
    }
    if ((m = caminho.match(/^\/api\/caixas\/sessoes\/(\d+)\/movimentacoes$/))) return db.movs.filter(x => x.caixaId === Number(m[1]));
    if ((m = caminho.match(/^\/api\/caixas\/sessoes\/(\d+)\/resumo-operadores$/))) {
      const porOperador = {};
      db.movs.filter(x => x.caixaId === Number(m[1])).forEach(x => {
        const r = porOperador[x.operador] = porOperador[x.operador] || { operador: x.operador, vendas: 0, vendido: 0, dinheiro: 0, pixECartoes: 0, cancelamentos: 0, cancelado: 0, descontos: 0, sangrias: 0, suprimentos: 0 };
        if (x.tipo === 'VENDA') { r.vendido = r2(r.vendido + x.valor); if (x.formaPagamento === 'DINHEIRO') r.dinheiro = r2(r.dinheiro + x.valor); else r.pixECartoes = r2(r.pixECartoes + x.valor); }
        if (x.tipo === 'ESTORNO_VENDA') { r.cancelado = r2(r.cancelado + x.valor); }
        if (x.tipo === 'SANGRIA') r.sangrias = r2(r.sangrias + x.valor);
        if (x.tipo === 'SUPRIMENTO') r.suprimentos = r2(r.suprimentos + x.valor);
      });
      Object.values(porOperador).forEach(r => {
        r.vendas = new Set(db.movs.filter(x => x.caixaId === Number(m[1]) && x.operador === r.operador && x.tipo === 'VENDA').map(x => x.vendaId)).size;
        r.cancelamentos = new Set(db.movs.filter(x => x.caixaId === Number(m[1]) && x.operador === r.operador && x.tipo === 'ESTORNO_VENDA').map(x => x.vendaId)).size;
      });
      return Object.values(porOperador).sort((a, b) => b.vendido - a.vendido);
    }
    if ((m = caminho.match(/^\/api\/caixas\/(\d+)$/))) {
      const caixa = db.caixas.filter(c => c.numeroCaixa === Number(m[1])).pop();
      if (!caixa) throw erro(404, 'Caixa não encontrado.');
      return caixaResposta(caixa);
    }

    // Cozinha: a demonstração não tem impressora
    if (caminho === '/api/cozinha/situacao') return { ligada: false, impressora: '', aguardando: 0, comErro: 0 };
    if (caminho === '/api/cozinha/pedidos') return [];
    if (caminho === '/api/cozinha/config') {
      return { impressora: '', colunas: 48, categorias: [], impressoras: [], categoriasDisponiveis: db.categorias.map(c => ({ codigo: c.codigo, nome: c.nomeExibicao })) };
    }
    if (caminho.startsWith('/api/cozinha/')) throw erro(400, 'A demonstração não tem impressora de cozinha.', 'DEMONSTRACAO');

    throw erro(404, 'Função indisponível nesta demonstração.', 'DEMONSTRACAO');
  }

  function rascunho(vendaId) {
    const v = db.vendas[vendaId];
    if (!v) throw erro(404, 'Venda nao encontrada.', 'VENDA_NAO_ENCONTRADA');
    if (v.status !== 'RASCUNHO') throw erro(400, 'Esta conta já foi paga ou cancelada.', 'VENDA_NAO_ESTA_EM_RASCUNHO');
    return v;
  }

  // ── Intercepta o fetch: só /api/ desta origem; o resto segue normal ─────
  const fetchOriginal = window.fetch.bind(window);
  const responder = (status, valor) => new Response(valor == null ? null : JSON.stringify(valor),
    { status, headers: valor == null ? {} : { 'Content-Type': 'application/json' } });
  window.fetch = async function (entrada, opcoes) {
    const bruto = entrada instanceof Request ? entrada.url : String(entrada);
    const url = new URL(bruto, location.href);
    if (url.origin !== location.origin || !url.pathname.startsWith('/api/')) return fetchOriginal(entrada, opcoes);
    const init = opcoes || {};
    const metodo = String(init.method || (entrada instanceof Request ? entrada.method : 'GET')).toUpperCase();
    let corpo = {};
    try { corpo = typeof init.body === 'string' ? JSON.parse(init.body || '{}') : {}; } catch (e) { corpo = {}; }
    const cabecalhos = {};
    new Headers(init.headers || {}).forEach((valor, nome) => { cabecalhos[nome.toLowerCase()] = valor; });
    await new Promise(r => setTimeout(r, 60)); // um pouco de "rede", como no sistema de verdade
    try {
      const resultado = rotear(metodo, url.pathname, url, corpo, cabecalhos);
      salvar();
      if (Array.isArray(resultado) && resultado.length === 2 && typeof resultado[0] === 'number') return responder(resultado[0], resultado[1]);
      return responder(200, resultado);
    } catch (e) {
      salvar();
      return responder(e.status || 500, { erro: e.message || 'Erro na demonstração.', codigo: e.codigo || null, detalhes: {} });
    }
  };

  // ── Faixa da demonstração e menu só com as telas do caixa ───────────────
  function recomecar() {
    recomecando = true;
    try { localStorage.removeItem(KEY); sessionStorage.removeItem('usuario'); localStorage.removeItem('usuario'); } catch (e) { /* nada */ }
    location.href = BASE + 'caixas.html';
  }
  window.StockOSRecomecarCaixaCompleto = recomecar;

  function ajustarMenu() {
    const nav = document.getElementById('nav-items');
    if (!nav) return;
    nav.querySelectorAll('a.nav-item:not([data-demo-voltar])').forEach(link => {
      const destino = link.getAttribute('href') || '';
      if (/\/(caixas|comandas)\.html$/.test(destino)) {
        if (!destino.startsWith(BASE)) link.setAttribute('href', BASE + destino.split('/').pop());
      } else {
        link.remove();
      }
    });
    if (!nav.querySelector('[data-demo-voltar]')) {
      const voltar = document.createElement('a');
      voltar.href = '/dashboard.html';
      voltar.className = 'nav-item';
      voltar.dataset.demoVoltar = '1';
      voltar.innerHTML = '<span class="nav-icon" aria-hidden="true">←</span><span class="label">Voltar à demonstração</span>';
      nav.appendChild(voltar);
    }
  }

  function faixa() {
    if (document.getElementById('caixa-completo-faixa')) return;
    const div = document.createElement('div');
    div.id = 'caixa-completo-faixa';
    div.setAttribute('role', 'note');
    div.style.cssText = 'position:sticky;top:0;z-index:50;display:flex;flex-wrap:wrap;gap:8px 16px;align-items:center;justify-content:space-between;padding:8px 16px;background:#1e3a5f;color:#fff;font:13px/1.4 system-ui,sans-serif';
    div.innerHTML = '<span><strong>Demonstração do caixa completo</strong> · as telas são as do sistema; os dados são fictícios e ficam só neste navegador. '
      + 'Bipe ou digite <code style="background:#ffffff22;padding:1px 4px;border-radius:4px">7891000100103</code> (Skol). PIN da Ana e senha do gerente: <strong>1234</strong>.</span>'
      + '<span><button type="button" id="caixa-completo-recomecar" style="min-height:32px;padding:4px 10px;border-radius:6px;border:1px solid #ffffff66;background:transparent;color:#fff;cursor:pointer">Recomeçar</button></span>';
    document.body.prepend(div);
    document.getElementById('caixa-completo-recomecar').addEventListener('click', recomecar);
  }

  // Os scripts do caixa só ligam os botões quando o endereço é /caixas.html ou /comandas.html;
  // aqui a página fica em /caixa-completo/, então liga à mão (antes de o sistema montar a tela).
  document.addEventListener('DOMContentLoaded', () => {
    const pagina = location.pathname.split('/').pop();
    if (/^caixas(\.html)?$/.test(pagina)) {
      if (window.StockOSPages && window.StockOSPages.caixas) window.StockOSPages.caixas.activate();
      if (window.StockOSPDV) window.StockOSPDV.activate();
    }
    if (/^comandas(\.html)?$/.test(pagina) && window.StockOSPages && window.StockOSPages.comandas) window.StockOSPages.comandas.activate();
  }, true);

  document.addEventListener('DOMContentLoaded', () => {
    faixa();
    ajustarMenu();
    const nav = document.getElementById('nav-items');
    // O menu é desenhado de novo pelo sistema às vezes: ajusta outra vez, sem reagir às próprias mudanças.
    if (nav && window.MutationObserver) {
      const observador = new MutationObserver(() => { observador.disconnect(); ajustarMenu(); observador.observe(nav, { childList: true }); });
      observador.observe(nav, { childList: true });
    }
  });
})();
