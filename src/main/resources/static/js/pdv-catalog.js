(function () {
  // Temporary compatibility layer:
  // the current backend still exposes generic stock products, not menu metadata.
  // Centralize all name-based interpretation here so the rest of the PDV stops
  // making menu decisions from scattered magic strings.

  const PIZZA_CATEGORY = 'pizza';
  const DRINK_CATEGORY = 'bebida';

  const LEGACY_BURGER_PATTERNS = [
    /\bhamburguer\b/,
    /\bhamburger\b/,
    /\bburguer\b/,
    /\bburger\b/,
    /\bx-/,
    /\bsmash\b/,
    /\bcheeseburguer\b/
  ];

  const PIZZA_SIZE_RULES = [
    { id: 'brotinho', label: 'Brotinho', order: 10, aliases: ['brotinho'] },
    { id: 'broto',    label: 'Broto',    order: 20, aliases: ['broto'] },
    { id: 'media',    label: 'Media',    order: 30, aliases: ['media', 'média'] },
    { id: 'grande',   label: 'Grande',   order: 40, aliases: ['grande'] },
    { id: 'gigante',  label: 'Gigante',  order: 50, aliases: ['gigante', 'familia', 'família', 'gg'] }
  ];

  const PIZZA_FLAVOR_RULES = [
    { id: 'pizza_calabresa',       label: 'Calabresa',        order: 10, aliases: ['calabresa'] },
    { id: 'pizza_mussarela',       label: 'Mussarela',        order: 20, aliases: ['mussarela', 'mozarela', 'mozzarella', 'mussarela'] },
    { id: 'pizza_margherita',      label: 'Margherita',       order: 30, aliases: ['margherita', 'marguerita', 'margarita'] },
    { id: 'pizza_portuguesa',      label: 'Portuguesa',       order: 40, aliases: ['portuguesa'] },
    { id: 'pizza_frango_catupiry', label: 'Frango c/ Catupiry', order: 50, aliases: ['frango catupiry', 'frango c catupiry', 'frango com catupiry'] },
    { id: 'pizza_quatro_queijos',  label: 'Quatro Queijos',   order: 60, aliases: ['quatro queijos', '4 queijos', 'quatro queijo'] },
    { id: 'pizza_pepperoni',       label: 'Pepperoni',        order: 70, aliases: ['pepperoni'] },
    { id: 'pizza_napolitana',      label: 'Napolitana',       order: 80, aliases: ['napolitana'] },
    { id: 'pizza_vegetariana',     label: 'Vegetariana',      order: 90, aliases: ['vegetariana'] }
  ];

  const DRINK_FAMILY_RULES = [
    {
      id: 'refrigerante',
      label: 'Refrigerante',
      order: 10,
      icon: '🥤',
      aliases: ['refrigerante', 'refri', 'coca', 'cola', 'fanta', 'sprite', 'pepsi', 'guarana', 'guaraná', 'soda']
    },
    {
      id: 'agua',
      label: 'Agua',
      order: 20,
      icon: '💧',
      aliases: ['agua', 'água']
    },
    {
      id: 'suco',
      label: 'Suco',
      order: 30,
      icon: '🍹',
      aliases: ['suco', 'nectar', 'néctar']
    },
    {
      id: 'cha',
      label: 'Cha',
      order: 40,
      icon: '🍵',
      aliases: ['cha', 'chá', 'ice tea']
    },
    {
      id: 'cafe',
      label: 'Cafe',
      order: 50,
      icon: '☕',
      aliases: ['cafe', 'café']
    }
  ];

  const DRINK_PACKAGING_RULES = [
    { id: 'lata',      label: 'Lata',      order: 10, aliases: ['lata', 'can'] },
    { id: 'long_neck', label: 'Long Neck', order: 20, aliases: ['long neck', 'longneck'] },
    { id: 'garrafa',   label: 'Garrafa',   order: 30, aliases: ['garrafa', 'vidro'] },
    { id: 'pet',       label: 'PET',       order: 40, aliases: ['pet'] },
    { id: 'copo',      label: 'Copo',      order: 50, aliases: ['copo'] },
    { id: 'jarra',     label: 'Jarra',     order: 60, aliases: ['jarra'] }
  ];

  const DRINK_VARIANT_RULES = {
    refrigerante: [
      { id: 'coca_cola', label: 'Coca-Cola', order: 10, aliases: ['coca cola', 'coca-cola', 'coca'] },
      { id: 'coca_zero', label: 'Coca-Cola Zero', order: 20, aliases: ['coca zero', 'coca-cola zero'] },
      { id: 'fanta_laranja', label: 'Fanta Laranja', order: 30, aliases: ['fanta laranja', 'fanta orange'] },
      { id: 'fanta_uva', label: 'Fanta Uva', order: 40, aliases: ['fanta uva', 'fanta grape'] },
      { id: 'sprite', label: 'Sprite', order: 50, aliases: ['sprite'] },
      { id: 'pepsi', label: 'Pepsi', order: 60, aliases: ['pepsi'] },
      { id: 'guarana', label: 'Guarana', order: 70, aliases: ['guarana', 'guaraná'] }
    ],
    agua: [
      { id: 'agua_mineral', label: 'Agua Mineral', order: 10, aliases: ['agua mineral', 'água mineral', 'agua'] }
    ]
  };

  const GENERIC_NAME_TOKENS = [
    'pizza', 'bebida', 'refrigerante', 'refri', 'agua', 'água', 'suco',
    'cha', 'chá', 'cafe', 'café', 'com', 'sem', 'gas', 'gás'
  ];

  function normalizeText(value) {
    return String(value || '')
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, ' ')
      .trim();
  }

  function slugify(value) {
    return normalizeText(value).replace(/\s+/g, '_');
  }

  function parseNumber(value) {
    if (value === null || value === undefined || value === '') return null;
    if (typeof value === 'number') return Number.isFinite(value) ? value : null;

    const normalized = String(value).replace(',', '.').trim();
    const parsed = Number(normalized);
    return Number.isFinite(parsed) ? parsed : null;
  }

  function priceKey(value) {
    return Number.isFinite(value) ? value.toFixed(2) : '0.00';
  }

  function detectRule(text, rules) {
    return rules.find(function (rule) {
      return rule.aliases.some(function (alias) {
        return text.includes(normalizeText(alias));
      });
    }) || null;
  }

  function detectVolumeMl(text) {
    const mlMatch = text.match(/(\d{2,4})\s*ml\b/);
    if (mlMatch) return Number(mlMatch[1]);

    const litroMatch = text.match(/(\d(?:[.,]\d)?)\s*l\b/);
    if (litroMatch) {
      return Math.round(Number(litroMatch[1].replace(',', '.')) * 1000);
    }

    return null;
  }

  function formatVolumeLabel(volumeMl) {
    if (!volumeMl) return null;
    if (volumeMl >= 1000 && volumeMl % 1000 === 0) {
      return (volumeMl / 1000) + 'L';
    }
    if (volumeMl >= 1000) {
      return (volumeMl / 1000).toLocaleString('pt-BR', {
        minimumFractionDigits: 1,
        maximumFractionDigits: 1
      }) + 'L';
    }
    return volumeMl + 'ml';
  }

  function hasBurgerLegacyMarker(text) {
    return LEGACY_BURGER_PATTERNS.some(function (pattern) {
      return pattern.test(text);
    });
  }

  function inferDrinkFamily(normalizedProduct) {
    if ((normalizedProduct.categoriaEstoque || '') === 'BEBIDAS') {
      const byName = detectRule(normalizedProduct.searchText, DRINK_FAMILY_RULES);
      return byName || DRINK_FAMILY_RULES[0];
    }
    return detectRule(normalizedProduct.searchText, DRINK_FAMILY_RULES);
  }

  function inferDrinkPackaging(text) {
    return detectRule(text, DRINK_PACKAGING_RULES);
  }

  function inferDrinkSparkling(normalizedProduct, familyRule) {
    const text = normalizedProduct.searchText;
    if (!familyRule) return null;

    if (familyRule.id === 'agua') {
      if (text.includes('com gas') || text.includes('comgas') || text.includes('gaseificada')) return true;
      if (text.includes('sem gas') || text.includes('semgas') || text.includes('natural')) return false;
      return false;
    }

    if (familyRule.id === 'refrigerante') return true;
    return null;
  }

  function buildGenericVariant(text, fallbackLabel, order) {
    const base = normalizeText(fallbackLabel || text)
      .split(' ')
      .filter(function (token) {
        return token && GENERIC_NAME_TOKENS.indexOf(token) === -1;
      })
      .join(' ')
      .trim();

    const label = base
      ? base.replace(/\b\w/g, function (char) { return char.toUpperCase(); })
      : (fallbackLabel || 'Tradicional');

    return {
      id: slugify(label),
      label: label,
      order: order || 999
    };
  }

  function inferDrinkVariant(normalizedProduct, familyRule) {
    const rules = DRINK_VARIANT_RULES[familyRule.id] || [];
    const matched = detectRule(normalizedProduct.searchText, rules);
    if (matched) return matched;

    if (familyRule.id === 'agua') {
      return {
        id: normalizedProduct.attributes.sparkling ? 'agua_com_gas' : 'agua_sem_gas',
        label: normalizedProduct.attributes.sparkling ? 'Com gas' : 'Sem gas',
        order: normalizedProduct.attributes.sparkling ? 20 : 10
      };
    }

    return buildGenericVariant(normalizedProduct.searchText, normalizedProduct.nome, 999);
  }

  function resolveExplicitMetadata(normalizedProduct) {
    const raw = normalizedProduct.rawProduct || {};
    const explicitCategory = raw.categoriaCardapio || raw.menuCategory || null;
    const explicitGroup = raw.grupoCardapio || raw.menuGroup || null;
    const explicitVariant = raw.varianteCardapio || raw.menuVariant || null;

    if (!explicitCategory || !explicitGroup || !explicitVariant) return null;

    return {
      categoriaCardapio: explicitCategory,
      grupoCardapio: explicitGroup,
      varianteCardapio: explicitVariant,
      ordemExibicao: Number(raw.ordemExibicao) || 999,
      ordemVariante: Number(raw.ordemVariante) || 999,
      ativoNoCardapio: raw.ativoNoCardapio !== false,
      grupoTitulo: raw.grupoTitulo || explicitGroup.replace(/_/g, ' '),
      varianteTitulo: raw.varianteTitulo || explicitVariant.replace(/_/g, ' '),
      icone: raw.menuIcone || (explicitCategory === PIZZA_CATEGORY ? '🍕' : '🥤'),
      attributes: Object.assign({}, raw.attributes || {})
    };
  }

  function parsePizzaMetadata(normalizedProduct) {
    const explicit = resolveExplicitMetadata(normalizedProduct);
    if (explicit && explicit.categoriaCardapio === PIZZA_CATEGORY) {
      return explicit;
    }

    const text = normalizedProduct.searchText;
    if (!text.includes('pizza')) return null;

    const flavorRule = detectRule(text, PIZZA_FLAVOR_RULES);
    const sizeRule = detectRule(text, PIZZA_SIZE_RULES);

    if (!flavorRule || !sizeRule) return null;

    return {
      categoriaCardapio: PIZZA_CATEGORY,
      grupoCardapio: flavorRule.id,
      varianteCardapio: sizeRule.id,
      ordemExibicao: flavorRule.order,
      ordemVariante: sizeRule.order,
      ativoNoCardapio: true,
      grupoTitulo: flavorRule.label,
      varianteTitulo: sizeRule.label,
      icone: '🍕',
      attributes: {
        size: sizeRule.id,
        family: 'pizza',
        flavor: flavorRule.label
      }
    };
  }

  function parseDrinkMetadata(normalizedProduct) {
    const explicit = resolveExplicitMetadata(normalizedProduct);
    if (explicit && explicit.categoriaCardapio === DRINK_CATEGORY) {
      return explicit;
    }

    const familyRule = inferDrinkFamily(normalizedProduct);
    if (!familyRule) return null;

    const packagingRule = inferDrinkPackaging(normalizedProduct.searchText);
    const volumeMl = detectVolumeMl(normalizedProduct.searchText);
    const sparkling = inferDrinkSparkling(normalizedProduct, familyRule);

    if (!packagingRule || !volumeMl) return null;

    normalizedProduct.attributes.packaging = packagingRule.id;
    normalizedProduct.attributes.volume = volumeMl;
    normalizedProduct.attributes.sparkling = sparkling;
    normalizedProduct.attributes.family = familyRule.id;

    const variantRule = inferDrinkVariant(normalizedProduct, familyRule);
    const volumeLabel = formatVolumeLabel(volumeMl);

    let groupId = familyRule.id + '_' + packagingRule.id + '_' + volumeMl;
    let groupTitle = familyRule.label + ' ' + packagingRule.label + ' ' + volumeLabel;

    if (familyRule.id === 'agua') {
      groupId = 'agua_' + (sparkling ? 'com_gas' : 'sem_gas') + '_' + volumeMl;
      groupTitle = 'Agua ' + (sparkling ? 'com gas' : 'sem gas') + ' ' + volumeLabel;
    }

    return {
      categoriaCardapio: DRINK_CATEGORY,
      grupoCardapio: groupId,
      varianteCardapio: variantRule.id,
      ordemExibicao: familyRule.order * 100 + packagingRule.order,
      ordemVariante: variantRule.order || 999,
      ativoNoCardapio: true,
      grupoTitulo: groupTitle,
      varianteTitulo: variantRule.label,
      icone: familyRule.icon,
      attributes: {
        family: familyRule.id,
        packaging: packagingRule.id,
        volume: volumeMl,
        volumeLabel: volumeLabel,
        sparkling: sparkling,
        flavor: variantRule.label
      }
    };
  }

  function normalizeRawProduct(rawProduct) {
    const raw = rawProduct || {};
    const nome = String(raw.nome || '').trim();
    const precoUnitario = parseNumber(raw.precoUnitario);
    const quantidadeAtual = parseNumber(raw.quantidadeAtual);
    const quantidadeMinima = parseNumber(raw.quantidadeMinima);
    const controladoPorEstoque = raw.controladoPorEstoque !== false;
    const searchText = normalizeText(nome);
    const diagnostics = [];
    const disponivel = typeof raw.disponivel === 'boolean'
      ? raw.disponivel
      : (controladoPorEstoque ? !Number.isFinite(quantidadeAtual) || quantidadeAtual > 0 : true);

    const normalized = {
      rawProduct: raw,
      productId: raw.id,
      cardapioItemId: raw.cardapioItemId || null,
      nome: nome,
      precoUnitario: precoUnitario,
      quantidadeAtual: Number.isFinite(quantidadeAtual) ? quantidadeAtual : null,
      quantidadeMinima: Number.isFinite(quantidadeMinima) ? quantidadeMinima : null,
      categoriaEstoque: raw.categoriaEstoque || null,
      categoriaCardapio: null,
      grupoCardapio: null,
      varianteCardapio: null,
      ordemExibicao: Number(raw.ordemExibicao) || 999,
      ordemVariante: Number(raw.ordemVariante) || 999,
      ativoNoCardapio: raw.ativoNoCardapio !== false,
      grupoTitulo: null,
      varianteTitulo: null,
      icone: '🍽️',
      controladoPorEstoque: controladoPorEstoque,
      disponivel: disponivel,
      attributes: {},
      diagnostics: diagnostics,
      searchText: searchText,
      searchIndex: searchText
    };

    if (!nome) diagnostics.push({ code: 'nome_ausente', detail: 'Produto sem nome.' });
    if (!Number.isFinite(precoUnitario) || precoUnitario <= 0) {
      diagnostics.push({ code: 'preco_invalido', detail: 'Preco unitario invalido.' });
    }

    if (hasBurgerLegacyMarker(searchText)) {
      diagnostics.push({ code: 'legacy_burger', detail: 'Produto legado de hamburguer removido do cardapio principal.' });
      normalized.ativoNoCardapio = false;
      return normalized;
    }

    const pizzaMetadata = parsePizzaMetadata(normalized);
    const drinkMetadata = pizzaMetadata ? null : parseDrinkMetadata(normalized);
    const resolved = pizzaMetadata || drinkMetadata;

    if (!resolved) {
      diagnostics.push({ code: 'nao_mapeado', detail: 'Produto nao mapeado para o cardapio de pizzaria.' });
      normalized.ativoNoCardapio = false;
      return normalized;
    }

    normalized.categoriaCardapio = resolved.categoriaCardapio;
    normalized.grupoCardapio = resolved.grupoCardapio;
    normalized.varianteCardapio = resolved.varianteCardapio;
    normalized.ordemExibicao = resolved.ordemExibicao;
    normalized.ordemVariante = resolved.ordemVariante;
    normalized.ativoNoCardapio = resolved.ativoNoCardapio !== false && normalized.ativoNoCardapio;
    normalized.grupoTitulo = resolved.grupoTitulo;
    normalized.varianteTitulo = resolved.varianteTitulo;
    normalized.icone = resolved.icone || normalized.icone;
    normalized.attributes = Object.assign({}, resolved.attributes || {});

    if (!normalized.grupoCardapio || !normalized.varianteCardapio) {
      diagnostics.push({ code: 'metadata_incompleta', detail: 'Metadado de cardapio incompleto.' });
    }

    normalized.searchIndex = normalizeText([
      normalized.nome,
      normalized.grupoTitulo,
      normalized.varianteTitulo,
      normalized.attributes.family,
      normalized.attributes.flavor,
      normalized.attributes.volumeLabel
    ].filter(Boolean).join(' '));

    return normalized;
  }

  function isMenuEligible(normalizedProduct) {
    if (!normalizedProduct) return false;
    if (normalizedProduct.ativoNoCardapio === false) return false;
    if (!(normalizedProduct.categoriaCardapio === PIZZA_CATEGORY || normalizedProduct.categoriaCardapio === DRINK_CATEGORY)) return false;
    if (!normalizedProduct.grupoCardapio || !normalizedProduct.varianteCardapio) return false;
    if (!Number.isFinite(normalizedProduct.precoUnitario) || normalizedProduct.precoUnitario <= 0) return false;

    if (normalizedProduct.categoriaCardapio === DRINK_CATEGORY) {
      if (!normalizedProduct.attributes.family) return false;
      if (!normalizedProduct.attributes.packaging) return false;
      if (!normalizedProduct.attributes.volume) return false;
    }

    if (normalizedProduct.categoriaCardapio === PIZZA_CATEGORY && !normalizedProduct.attributes.size) {
      return false;
    }

    return true;
  }

  function buildMenuCatalog(normalizedProducts) {
    const catalog = {
      items: [],
      byCategory: {
        pizza: [],
        bebida: []
      },
      diagnostics: {
        hidden: [],
        legacy: [],
        unmapped: []
      }
    };

    const groupMap = new Map();

    (normalizedProducts || []).forEach(function (item) {
      if (!isMenuEligible(item)) {
        catalog.diagnostics.hidden.push(item);
        if (item.diagnostics.some(function (diag) { return diag.code === 'legacy_burger'; })) {
          catalog.diagnostics.legacy.push(item);
        } else {
          catalog.diagnostics.unmapped.push(item);
        }
        return;
      }

      const groupKey = item.categoriaCardapio === DRINK_CATEGORY
        ? [item.categoriaCardapio, item.grupoCardapio, priceKey(item.precoUnitario)].join('|')
        : [item.categoriaCardapio, item.grupoCardapio].join('|');

      if (!groupMap.has(groupKey)) {
        groupMap.set(groupKey, {
          key: groupKey,
          categoriaCardapio: item.categoriaCardapio,
          grupoCardapio: item.grupoCardapio,
          grupoTitulo: item.grupoTitulo,
          ordemExibicao: item.ordemExibicao,
          precoBase: item.precoUnitario,
          attributes: Object.assign({}, item.attributes || {}),
          items: []
        });
      }

      groupMap.get(groupKey).items.push(item);
      catalog.items.push(item);
    });

    const groups = Array.from(groupMap.values()).map(function (group) {
      group.items.sort(function (a, b) {
        if (a.ordemVariante !== b.ordemVariante) return a.ordemVariante - b.ordemVariante;
        if (a.precoUnitario !== b.precoUnitario) return a.precoUnitario - b.precoUnitario;
        return a.nome.localeCompare(b.nome, 'pt-BR');
      });
      return group;
    });

    groups.sort(function (a, b) {
      if (a.categoriaCardapio !== b.categoriaCardapio) {
        return a.categoriaCardapio.localeCompare(b.categoriaCardapio, 'pt-BR');
      }
      if (a.ordemExibicao !== b.ordemExibicao) return a.ordemExibicao - b.ordemExibicao;
      return a.grupoTitulo.localeCompare(b.grupoTitulo, 'pt-BR');
    });

    catalog.byCategory.pizza = groups.filter(function (group) {
      return group.categoriaCardapio === PIZZA_CATEGORY;
    });
    catalog.byCategory.bebida = groups.filter(function (group) {
      return group.categoriaCardapio === DRINK_CATEGORY;
    });

    return catalog;
  }

  window.normalizeRawProduct = normalizeRawProduct;
  window.isMenuEligible = isMenuEligible;
  window.parsePizzaMetadata = parsePizzaMetadata;
  window.parseDrinkMetadata = parseDrinkMetadata;
  window.buildMenuCatalog = buildMenuCatalog;
  window.PdvCatalogAdapter = {
    normalizeRawProduct: normalizeRawProduct,
    isMenuEligible: isMenuEligible,
    parsePizzaMetadata: parsePizzaMetadata,
    parseDrinkMetadata: parseDrinkMetadata,
    buildMenuCatalog: buildMenuCatalog
  };
})();
