package service;

import model.GrupoModificador;
import model.IncompatibilidadeOpcaoModificador;
import model.OpcaoModificador;
import model.OpcaoModificadorComponente;
import model.OrigemComposicaoVenda;
import model.PoliticaFalta;
import model.PoliticaFaltaIngrediente;
import model.Produto;
import model.TipoEfeitoModificador;
import model.TipoSelecaoModificador;
import model.UnidadeMedida;
import model.VariacaoGrupoModificador;
import model.VariacaoItemCardapio;
import model.VendaItemModificador;
import repository.IncompatibilidadeOpcaoModificadorRepository;
import repository.OpcaoModificadorComponenteRepository;
import repository.OpcaoModificadorRepository;
import repository.VariacaoGrupoModificadorRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class MontagemConfiguracaoItemService {

    public static final String TIPO_CAPACIDADE_CONFIGURACAO_ISOLADA = "ESTIMATIVA_CONFIGURACAO_ISOLADA";

    private static final int ESCALA_QUANTIDADE = 6;
    private static final int ESCALA_DINHEIRO = 4;

    private final VariacaoGrupoModificadorRepository associacaoRepository;
    private final OpcaoModificadorRepository opcaoRepository;
    private final OpcaoModificadorComponenteRepository componenteRepository;
    private final IncompatibilidadeOpcaoModificadorRepository incompatibilidadeRepository;
    private final ResolucaoComposicaoVariacaoService resolucaoComposicaoService;

    public MontagemConfiguracaoItemService(VariacaoGrupoModificadorRepository associacaoRepository,
                                           OpcaoModificadorRepository opcaoRepository,
                                           OpcaoModificadorComponenteRepository componenteRepository,
                                           IncompatibilidadeOpcaoModificadorRepository incompatibilidadeRepository,
                                           ResolucaoComposicaoVariacaoService resolucaoComposicaoService) {
        this.associacaoRepository = associacaoRepository;
        this.opcaoRepository = opcaoRepository;
        this.componenteRepository = componenteRepository;
        this.incompatibilidadeRepository = incompatibilidadeRepository;
        this.resolucaoComposicaoService = resolucaoComposicaoService;
    }

    public ConfiguracaoVariacao configurar(VariacaoItemCardapio variacao) {
        Contexto contexto = carregarContexto(variacao);
        return new ConfiguracaoVariacao(
                variacao,
                contexto.hashConfiguracao(),
                contexto.associacoes().stream()
                        .filter(VariacaoGrupoModificador::isAtivo)
                        .map(associacao -> grupoConfiguracao(associacao, contexto))
                        .toList()
        );
    }

    public List<VariacaoGrupoModificador> associacoesAtivasPorVariacoes(List<Long> variacaoIds) {
        if (variacaoIds == null || variacaoIds.isEmpty()) {
            return List.of();
        }
        return associacaoRepository.listarAtivasPorVariacoes(variacaoIds);
    }

    public List<OpcaoModificador> opcoesPorGrupos(List<Long> grupoIds) {
        if (grupoIds == null || grupoIds.isEmpty()) {
            return List.of();
        }
        return opcaoRepository.listarPorGrupos(grupoIds);
    }

    public MontagemConfiguracao montar(VariacaoItemCardapio variacao,
                                       BigDecimal quantidadeItem,
                                       List<SelecaoModificadorInput> selecoes,
                                       boolean aplicarPadroes) {
        validarQuantidadeItem(quantidadeItem);
        Contexto contexto = carregarContexto(variacao);
        ResolucaoComposicaoVariacaoService.ResolucaoComposicao resolucao =
                resolucaoComposicaoService.resolverObrigatoria(variacao);
        List<SelecaoAplicada> selecoesAplicadas = validarEAplicarSelecoes(contexto, selecoes, aplicarPadroes);
        List<ImpactoPlanejado> impactos = resolverImpactos(selecoesAplicadas, contexto);
        List<ConsumoConfigurado> consumos = aplicarDeltas(resolucao, impactos);
        BigDecimal acrescimoUnitario = selecoesAplicadas.stream()
                .map(SelecaoAplicada::acrescimoTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(ESCALA_DINHEIRO, RoundingMode.HALF_UP);

        return new MontagemConfiguracao(
                variacao,
                contexto.hashConfiguracao(),
                resolucao,
                List.copyOf(selecoesAplicadas),
                List.copyOf(consumos),
                List.copyOf(impactos),
                acrescimoUnitario
        );
    }

    public void validarHashObservado(String hashObservado, String hashAtual) {
        String observado = normalizar(hashObservado);
        if (observado != null && !observado.equals(hashAtual)) {
            throw new ErroNegocioException(
                    "MODIFICADOR_CONFIGURACAO_ALTERADA",
                    "Configuracao de modificadores foi alterada desde a inclusao no rascunho.",
                    Map.of("configuracaoObservada", observado, "configuracaoAtual", hashAtual)
            );
        }
    }

    private GrupoConfiguracao grupoConfiguracao(VariacaoGrupoModificador associacao, Contexto contexto) {
        GrupoModificador grupo = associacao.getGrupoModificador();
        List<OpcaoConfiguracao> opcoes = contexto.opcoesPorGrupo()
                .getOrDefault(grupo.getId(), List.of())
                .stream()
                .map(opcao -> new OpcaoConfiguracao(
                        opcao.getId(),
                        opcao.getNome(),
                        opcao.getCodigo(),
                        opcao.getDescricao(),
                        opcao.getAcrescimoPreco(),
                        opcao.getQuantidadeMaximaPorItem(),
                        opcao.isSelecionadaPorPadrao(),
                        opcao.isAtiva(),
                        opcao.getOrdemExibicao()
                ))
                .toList();
        return new GrupoConfiguracao(
                associacao.getId(),
                grupo.getId(),
                grupo.getNome(),
                grupo.getCodigo(),
                grupo.getDescricao(),
                grupo.getTipoSelecao(),
                associacao.obrigatorioEfetivo(),
                associacao.minimoEfetivo(),
                associacao.maximoEfetivo(),
                grupo.isPermiteRepeticao(),
                associacao.isAtivo() && grupo.isAtivo(),
                associacao.getOrdemExibicao(),
                opcoes
        );
    }

    private Contexto carregarContexto(VariacaoItemCardapio variacao) {
        if (variacao == null) {
            throw new ErroNegocioException("VARIACAO_NAO_ENCONTRADA", "Variacao de cardapio nao encontrada.");
        }
        List<VariacaoGrupoModificador> associacoes = associacaoRepository.listarPorVariacao(variacao.getId());
        List<Long> grupoIds = associacoes.stream()
                .map(a -> a.getGrupoModificador().getId())
                .distinct()
                .toList();
        Map<Long, List<OpcaoModificador>> opcoesPorGrupo = opcaoRepository.listarPorGrupos(grupoIds).stream()
                .collect(Collectors.groupingBy(
                        opcao -> opcao.getGrupoModificador().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        List<Long> opcaoIds = opcoesPorGrupo.values().stream()
                .flatMap(List::stream)
                .map(OpcaoModificador::getId)
                .distinct()
                .toList();
        Map<Long, List<OpcaoModificadorComponente>> componentesPorOpcao =
                componenteRepository.listarPorOpcoes(opcaoIds).stream()
                        .collect(Collectors.groupingBy(
                                componente -> componente.getOpcaoModificador().getId(),
                                LinkedHashMap::new,
                                Collectors.toList()
                        ));
        List<IncompatibilidadeOpcaoModificador> incompatibilidades =
                incompatibilidadeRepository.listarAtivasPorOpcoes(opcaoIds);
        String hash = hashOperacional(associacoes, opcoesPorGrupo, componentesPorOpcao, incompatibilidades);
        return new Contexto(variacao, associacoes, opcoesPorGrupo, componentesPorOpcao, incompatibilidades, hash);
    }

    private List<SelecaoAplicada> validarEAplicarSelecoes(Contexto contexto,
                                                          List<SelecaoModificadorInput> selecoes,
                                                          boolean aplicarPadroes) {
        Map<Long, VariacaoGrupoModificador> associacaoPorGrupo = contexto.associacoes().stream()
                .filter(VariacaoGrupoModificador::isAtivo)
                .filter(a -> a.getGrupoModificador().isAtivo())
                .collect(Collectors.toMap(a -> a.getGrupoModificador().getId(), Function.identity()));
        Map<Long, OpcaoModificador> opcaoPorId = contexto.opcoesPorGrupo().values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toMap(OpcaoModificador::getId, Function.identity()));
        List<SelecaoModificadorInput> entradas = selecoes == null ? List.of() : selecoes;
        List<SelecaoAplicada> aplicadas = new ArrayList<>();
        Set<Long> gruposComSelecaoExplicita = new HashSet<>();
        Set<Long> opcoesExplicitas = new HashSet<>();

        for (SelecaoModificadorInput entrada : entradas) {
            int quantidade = entrada.quantidade() == null ? 1 : entrada.quantidade();
            if (quantidade <= 0) {
                throw new ErroNegocioException("SELECAO_MODIFICADOR_INVALIDA", "Quantidade da selecao deve ser maior que zero.");
            }
            VariacaoGrupoModificador associacao = associacaoPorGrupo.get(entrada.grupoId());
            if (associacao == null) {
                throw new ErroNegocioException("GRUPO_NAO_ASSOCIADO_VARIACAO", "Grupo nao esta associado a variacao.");
            }
            OpcaoModificador opcao = opcaoPorId.get(entrada.opcaoId());
            if (opcao == null) {
                throw new ErroNegocioException("OPCAO_MODIFICADOR_NAO_ENCONTRADA", "Opcao de modificador nao encontrada.");
            }
            if (!opcao.getGrupoModificador().equals(associacao.getGrupoModificador())) {
                throw new ErroNegocioException("OPCAO_MODIFICADOR_NAO_PERTENCE_GRUPO", "Opcao nao pertence ao grupo informado.");
            }
            if (!opcao.isAtiva()) {
                throw new ErroNegocioException("OPCAO_MODIFICADOR_INATIVA", "Opcao de modificador inativa.");
            }
            if (!opcao.getGrupoModificador().isAtivo()) {
                throw new ErroNegocioException("GRUPO_MODIFICADOR_INATIVO", "Grupo de modificador inativo.");
            }
            if (!opcoesExplicitas.add(opcao.getId()) && !associacao.getGrupoModificador().isPermiteRepeticao()) {
                throw new ErroNegocioException("REPETICAO_NAO_PERMITIDA", "Opcao repetida sem permissao de repeticao.");
            }
            validarQuantidadeOpcao(associacao, opcao, quantidade);
            gruposComSelecaoExplicita.add(associacao.getGrupoModificador().getId());
            aplicadas.add(new SelecaoAplicada(associacao, opcao, quantidade, false));
        }

        if (aplicarPadroes) {
            for (VariacaoGrupoModificador associacao : associacaoPorGrupo.values()) {
                if (gruposComSelecaoExplicita.contains(associacao.getGrupoModificador().getId())) {
                    continue;
                }
                List<OpcaoModificador> padroes = contexto.opcoesPorGrupo()
                        .getOrDefault(associacao.getGrupoModificador().getId(), List.of())
                        .stream()
                        .filter(OpcaoModificador::isAtiva)
                        .filter(OpcaoModificador::isSelecionadaPorPadrao)
                        .filter(opcao -> opcao.getAcrescimoPreco().compareTo(BigDecimal.ZERO) == 0)
                        .toList();
                for (OpcaoModificador padrao : padroes) {
                    validarQuantidadeOpcao(associacao, padrao, 1);
                    aplicadas.add(new SelecaoAplicada(associacao, padrao, 1, true));
                }
            }
        }

        validarMinMaxPorGrupo(associacaoPorGrupo, aplicadas);
        validarIncompatibilidades(contexto.incompatibilidades(), aplicadas);
        return aplicadas;
    }

    private void validarQuantidadeOpcao(VariacaoGrupoModificador associacao,
                                        OpcaoModificador opcao,
                                        int quantidade) {
        GrupoModificador grupo = associacao.getGrupoModificador();
        if (quantidade > 1 && !grupo.isPermiteRepeticao()) {
            throw new ErroNegocioException("REPETICAO_NAO_PERMITIDA", "Grupo nao permite repeticao de opcao.");
        }
        if (opcao.getQuantidadeMaximaPorItem() != null && quantidade > opcao.getQuantidadeMaximaPorItem()) {
            throw new ErroNegocioException("QUANTIDADE_OPCAO_EXCEDIDA", "Quantidade maxima da opcao excedida.");
        }
        if (grupo.getTipoSelecao() == TipoSelecaoModificador.UNICA && quantidade > 1) {
            throw new ErroNegocioException("QUANTIDADE_MAXIMA_EXCEDIDA", "Grupo de selecao unica permite apenas uma escolha.");
        }
    }

    private void validarMinMaxPorGrupo(Map<Long, VariacaoGrupoModificador> associacaoPorGrupo,
                                       List<SelecaoAplicada> aplicadas) {
        Map<Long, Integer> totalPorGrupo = new LinkedHashMap<>();
        for (SelecaoAplicada aplicada : aplicadas) {
            totalPorGrupo.merge(
                    aplicada.associacao().getGrupoModificador().getId(),
                    aplicada.quantidade(),
                    Integer::sum
            );
        }
        for (VariacaoGrupoModificador associacao : associacaoPorGrupo.values()) {
            int total = totalPorGrupo.getOrDefault(associacao.getGrupoModificador().getId(), 0);
            if (associacao.obrigatorioEfetivo() && total == 0) {
                throw new ErroNegocioException("MODIFICADOR_OBRIGATORIO_AUSENTE", "Grupo obrigatorio sem escolha.");
            }
            if (total < associacao.minimoEfetivo()) {
                throw new ErroNegocioException("QUANTIDADE_MINIMA_NAO_ATINGIDA", "Quantidade minima do grupo nao atingida.");
            }
            if (total > associacao.maximoEfetivo()) {
                throw new ErroNegocioException("QUANTIDADE_MAXIMA_EXCEDIDA", "Quantidade maxima do grupo excedida.");
            }
        }
    }

    private void validarIncompatibilidades(List<IncompatibilidadeOpcaoModificador> incompatibilidades,
                                           List<SelecaoAplicada> aplicadas) {
        Set<Long> selecionadas = aplicadas.stream()
                .map(aplicada -> aplicada.opcao().getId())
                .collect(Collectors.toSet());
        for (IncompatibilidadeOpcaoModificador incompatibilidade : incompatibilidades) {
            long origem = incompatibilidade.getOpcaoOrigem().getId();
            long incompativel = incompatibilidade.getOpcaoIncompativel().getId();
            if (selecionadas.contains(origem) && selecionadas.contains(incompativel)) {
                throw new ErroNegocioException("OPCOES_INCOMPATIVEIS",
                        incompatibilidade.getMotivo() == null
                                ? "Opcoes selecionadas sao incompativeis."
                                : incompatibilidade.getMotivo());
            }
        }
    }

    private List<ImpactoPlanejado> resolverImpactos(List<SelecaoAplicada> selecoes, Contexto contexto) {
        List<ImpactoPlanejado> impactos = new ArrayList<>();
        for (SelecaoAplicada selecao : selecoes) {
            List<OpcaoModificadorComponente> componentes = contexto.componentesPorOpcao()
                    .getOrDefault(selecao.opcao().getId(), List.of());
            for (OpcaoModificadorComponente componente : componentes) {
                if (componente.getTipoEfeito() == TipoEfeitoModificador.SEM_IMPACTO_ESTOQUE) {
                    continue;
                }
                BigDecimal fator = BigDecimal.valueOf(selecao.quantidade());
                if (componente.getMultiplicador() != null) {
                    fator = fator.multiply(componente.getMultiplicador());
                }
                BigDecimal quantidade = componente.getQuantidadeBase()
                        .multiply(fator)
                        .setScale(ESCALA_QUANTIDADE, RoundingMode.HALF_UP);
                if (componente.getTipoEfeito() == TipoEfeitoModificador.ADICIONAR) {
                    impactos.add(impacto(selecao, componente, componente.getProdutoDestino(), quantidade));
                } else if (componente.getTipoEfeito() == TipoEfeitoModificador.REMOVER) {
                    impactos.add(impacto(selecao, componente, componente.getProdutoOrigem(), quantidade.negate()));
                } else if (componente.getTipoEfeito() == TipoEfeitoModificador.SUBSTITUIR) {
                    if (componente.getProdutoOrigem() != null) {
                        impactos.add(impacto(selecao, componente, componente.getProdutoOrigem(), quantidade.negate()));
                    }
                    if (componente.getProdutoDestino() != null) {
                        impactos.add(impacto(selecao, componente, componente.getProdutoDestino(), quantidade));
                    }
                }
            }
        }
        return impactos;
    }

    private ImpactoPlanejado impacto(SelecaoAplicada selecao,
                                     OpcaoModificadorComponente componente,
                                     Produto produto,
                                     BigDecimal delta) {
        if (produto == null || produto.getUnidadeBase() == null) {
            throw new ErroNegocioException("EFEITO_MODIFICADOR_INVALIDO",
                    "Componente de modificador sem produto ou unidade-base configurada.");
        }
        return new ImpactoPlanejado(
                selecao,
                componente,
                produto,
                produto.getUnidadeBase(),
                componente.getTipoEfeito(),
                delta.setScale(ESCALA_QUANTIDADE, RoundingMode.HALF_UP),
                resolverPolitica(componente, produto)
        );
    }

    private List<ConsumoConfigurado> aplicarDeltas(ResolucaoComposicaoVariacaoService.ResolucaoComposicao resolucao,
                                                   List<ImpactoPlanejado> impactos) {
        Map<Integer, BigDecimal> basePorProduto = new LinkedHashMap<>();
        Map<Integer, Produto> produtoPorId = new LinkedHashMap<>();
        Map<Integer, UnidadeMedida> unidadePorProduto = new LinkedHashMap<>();
        Map<Integer, OrigemComposicaoVenda> origemBasePorProduto = new LinkedHashMap<>();
        for (ResolucaoComposicaoVariacaoService.ComponenteComposicao componente : resolucao.componentes()) {
            Produto produto = componente.produto();
            produtoPorId.put(produto.getId(), produto);
            unidadePorProduto.put(produto.getId(), componente.unidadeBase());
            origemBasePorProduto.putIfAbsent(produto.getId(), componente.origem());
            basePorProduto.merge(
                    produto.getId(),
                    componente.quantidadeBasePorUnidadeComercial(),
                    (a, b) -> a.add(b).setScale(ESCALA_QUANTIDADE, RoundingMode.HALF_UP)
            );
        }

        Map<Integer, BigDecimal> negativosPorProduto = new LinkedHashMap<>();
        Map<Integer, List<ImpactoPlanejado>> positivosPorProduto = new LinkedHashMap<>();
        for (ImpactoPlanejado impacto : impactos) {
            produtoPorId.put(impacto.produto().getId(), impacto.produto());
            unidadePorProduto.put(impacto.produto().getId(), impacto.unidadeBase());
            if (impacto.quantidadeDeltaBase().compareTo(BigDecimal.ZERO) < 0) {
                negativosPorProduto.merge(
                        impacto.produto().getId(),
                        impacto.quantidadeDeltaBase(),
                        (a, b) -> a.add(b).setScale(ESCALA_QUANTIDADE, RoundingMode.HALF_UP)
                );
            } else {
                positivosPorProduto.computeIfAbsent(impacto.produto().getId(), ignored -> new ArrayList<>()).add(impacto);
            }
        }

        Set<Integer> produtoIds = new HashSet<>();
        produtoIds.addAll(basePorProduto.keySet());
        produtoIds.addAll(negativosPorProduto.keySet());
        produtoIds.addAll(positivosPorProduto.keySet());
        List<ConsumoConfigurado> consumos = new ArrayList<>();
        for (Integer produtoId : produtoIds.stream().sorted().toList()) {
            BigDecimal base = escala(basePorProduto.getOrDefault(produtoId, BigDecimal.ZERO));
            BigDecimal negativos = escala(negativosPorProduto.getOrDefault(produtoId, BigDecimal.ZERO));
            BigDecimal positivos = positivosPorProduto.getOrDefault(produtoId, List.of()).stream()
                    .map(ImpactoPlanejado::quantidadeDeltaBase)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(ESCALA_QUANTIDADE, RoundingMode.HALF_UP);
            BigDecimal finalProduto = base.add(negativos).add(positivos).setScale(ESCALA_QUANTIDADE, RoundingMode.HALF_UP);
            if (finalProduto.compareTo(BigDecimal.ZERO) < 0) {
                throw new ErroNegocioException(
                        "COMPOSICAO_FINAL_NEGATIVA",
                        "Modificadores resultaram em quantidade negativa de produto.",
                        Map.of("produtoId", produtoId, "quantidadeFinal", finalProduto)
                );
            }
            BigDecimal baseRestante = base.add(negativos).setScale(ESCALA_QUANTIDADE, RoundingMode.HALF_UP);
            if (baseRestante.compareTo(BigDecimal.ZERO) < 0) {
                throw new ErroNegocioException(
                        "PRODUTO_ORIGEM_AUSENTE_NA_COMPOSICAO",
                        "Opcao tentou remover mais produto do que a composicao base possui.",
                        Map.of("produtoId", produtoId, "quantidadeBase", base, "quantidadeRemovida", negativos.abs())
                );
            }
            if (baseRestante.compareTo(BigDecimal.ZERO) > 0) {
                consumos.add(new ConsumoConfigurado(
                        produtoPorId.get(produtoId),
                        unidadePorProduto.get(produtoId),
                        baseRestante,
                        origemBasePorProduto.getOrDefault(produtoId, OrigemComposicaoVenda.FICHA_TECNICA),
                        null
                ));
            }
            for (ImpactoPlanejado positivo : positivosPorProduto.getOrDefault(produtoId, List.of())) {
                OrigemComposicaoVenda origem = positivo.tipoEfeito() == TipoEfeitoModificador.SUBSTITUIR
                        ? OrigemComposicaoVenda.MODIFICADOR_SUBSTITUICAO
                        : OrigemComposicaoVenda.MODIFICADOR_ADICAO;
                consumos.add(new ConsumoConfigurado(
                        positivo.produto(),
                        positivo.unidadeBase(),
                        positivo.quantidadeDeltaBase(),
                        origem,
                        positivo.componente()
                ));
            }
        }
        return consumos;
    }

    private PoliticaFalta resolverPolitica(OpcaoModificadorComponente componente, Produto produto) {
        PoliticaFaltaIngrediente politica = componente.getPoliticaFalta();
        if (politica == null || politica == PoliticaFaltaIngrediente.HERDAR_PRODUTO) {
            return produto.getPoliticaFalta();
        }
        return switch (politica) {
            case BLOQUEAR -> PoliticaFalta.BLOQUEAR;
            case ALERTAR -> PoliticaFalta.ALERTAR;
            case IGNORAR -> PoliticaFalta.IGNORAR;
            case HERDAR_PRODUTO -> produto.getPoliticaFalta();
        };
    }

    private String hashOperacional(List<VariacaoGrupoModificador> associacoes,
                                   Map<Long, List<OpcaoModificador>> opcoesPorGrupo,
                                   Map<Long, List<OpcaoModificadorComponente>> componentesPorOpcao,
                                   List<IncompatibilidadeOpcaoModificador> incompatibilidades) {
        StringBuilder sb = new StringBuilder();
        associacoes.stream()
                .sorted(Comparator
                        .comparingInt(VariacaoGrupoModificador::getOrdemExibicao)
                        .thenComparing(associacao -> codigoOperacional(associacao.getGrupoModificador().getCodigo()))
                        .thenComparingLong(VariacaoGrupoModificador::getId))
                .forEach(associacao -> {
                    GrupoModificador grupo = associacao.getGrupoModificador();
                    sb.append("A|").append(codigoOperacional(grupo.getCodigo())).append('|').append(associacao.isAtivo())
                            .append('|').append(associacao.getObrigatorioOverride())
                            .append('|').append(associacao.getQuantidadeMinimaOverride())
                            .append('|').append(associacao.getQuantidadeMaximaOverride())
                            .append('|').append(associacao.getOrdemExibicao()).append('\n');
                    sb.append("G|").append(codigoOperacional(grupo.getCodigo())).append('|')
                            .append(grupo.getTipoSelecao()).append('|').append(grupo.isObrigatorio()).append('|')
                            .append(grupo.getQuantidadeMinima()).append('|').append(grupo.getQuantidadeMaxima()).append('|')
                            .append(grupo.isPermiteRepeticao()).append('|').append(grupo.isAtivo()).append('|')
                            .append(grupo.getOrdemExibicao()).append('\n');
                    opcoesPorGrupo.getOrDefault(grupo.getId(), List.of()).stream()
                            .sorted(Comparator
                                    .comparingInt(OpcaoModificador::getOrdemExibicao)
                                    .thenComparing(opcao -> codigoOperacional(opcao.getCodigo()))
                                    .thenComparingLong(OpcaoModificador::getId))
                            .forEach(opcao -> {
                                sb.append("O|").append(codigoOpcao(opcao)).append('|')
                                        .append(dinheiroHash(opcao.getAcrescimoPreco())).append('|')
                                        .append(opcao.getQuantidadeMaximaPorItem()).append('|')
                                        .append(opcao.isSelecionadaPorPadrao()).append('|')
                                        .append(opcao.isAtiva()).append('|')
                                        .append(opcao.getOrdemExibicao()).append('\n');
                                componentesPorOpcao.getOrDefault(opcao.getId(), List.of()).stream()
                                        .sorted(Comparator
                                                .comparing(MontagemConfiguracaoItemService::chaveComponenteOperacional)
                                                .thenComparingLong(OpcaoModificadorComponente::getId))
                                        .forEach(componente -> sb.append("C|").append(chaveComponenteOperacional(componente)).append('|')
                                                .append(componente.getTipoEfeito()).append('|')
                                                .append(idProduto(componente.getProdutoOrigem())).append('|')
                                                .append(idProduto(componente.getProdutoDestino())).append('|')
                                                .append(quantidadeHash(componente.getQuantidadeBase(), OpcaoModificadorComponente.ESCALA_QUANTIDADE)).append('|')
                                                .append(codigoUnidade(componente.getUnidadeBase()))
                                                .append('|').append(quantidadeHash(componente.getMultiplicador(), OpcaoModificadorComponente.ESCALA_MULTIPLICADOR)).append('|')
                                                .append(componente.getPoliticaFalta()).append('|')
                                                .append(componente.getOrdem()).append('\n'));
                            });
                });
        incompatibilidades.stream()
                .sorted(Comparator
                        .comparing(MontagemConfiguracaoItemService::chaveIncompatibilidadeOperacional)
                        .thenComparingLong(IncompatibilidadeOpcaoModificador::getId))
                .forEach(incompatibilidade -> sb.append("I|")
                        .append(codigoOpcao(incompatibilidade.getOpcaoOrigem()))
                        .append('|').append(codigoOpcao(incompatibilidade.getOpcaoIncompativel()))
                        .append('|').append(incompatibilidade.isAtiva()).append('\n'));
        return sha256(sb.toString());
    }

    private static String chaveComponenteOperacional(OpcaoModificadorComponente componente) {
        return componente.getOrdem() + "|"
                + componente.getTipoEfeito() + "|"
                + idProduto(componente.getProdutoOrigem()) + "|"
                + idProduto(componente.getProdutoDestino()) + "|"
                + quantidadeHash(componente.getQuantidadeBase(), OpcaoModificadorComponente.ESCALA_QUANTIDADE) + "|"
                + codigoUnidade(componente.getUnidadeBase()) + "|"
                + quantidadeHash(componente.getMultiplicador(), OpcaoModificadorComponente.ESCALA_MULTIPLICADOR) + "|"
                + componente.getPoliticaFalta();
    }

    private static String chaveIncompatibilidadeOperacional(IncompatibilidadeOpcaoModificador incompatibilidade) {
        String origem = codigoOpcao(incompatibilidade.getOpcaoOrigem());
        String destino = codigoOpcao(incompatibilidade.getOpcaoIncompativel());
        return origem.compareTo(destino) <= 0
                ? origem + "|" + destino
                : destino + "|" + origem;
    }

    private static Integer idProduto(Produto produto) {
        return produto == null ? null : produto.getId();
    }

    private static String codigoOpcao(OpcaoModificador opcao) {
        if (opcao == null) {
            return "";
        }
        return codigoOperacional(opcao.getGrupoModificador().getCodigo()) + "." + codigoOperacional(opcao.getCodigo());
    }

    private static String codigoOperacional(String valor) {
        return valor == null ? "" : valor.trim().toUpperCase();
    }

    private static String codigoUnidade(UnidadeMedida unidade) {
        return unidade == null ? "" : codigoOperacional(unidade.getCodigo());
    }

    private static String dinheiroHash(BigDecimal valor) {
        return (valor == null ? BigDecimal.ZERO : valor).setScale(ESCALA_DINHEIRO, RoundingMode.HALF_UP).toPlainString();
    }

    private static String quantidadeHash(BigDecimal valor, int escala) {
        return valor == null ? "" : valor.setScale(escala, RoundingMode.HALF_UP).toPlainString();
    }

    private static String sha256(String texto) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel para hash de modificadores.", e);
        }
    }

    private static void validarQuantidadeItem(BigDecimal quantidadeItem) {
        if (quantidadeItem == null || quantidadeItem.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ErroNegocioException("CONFIGURACAO_ITEM_INVALIDA", "Quantidade do item deve ser maior que zero.");
        }
    }

    private static BigDecimal escala(BigDecimal valor) {
        return (valor == null ? BigDecimal.ZERO : valor).setScale(ESCALA_QUANTIDADE, RoundingMode.HALF_UP);
    }

    private static String normalizar(String valor) {
        if (valor == null) return null;
        String texto = valor.trim();
        return texto.isEmpty() ? null : texto;
    }

    public record SelecaoModificadorInput(Long grupoId, Long opcaoId, Integer quantidade) {
    }

    public record ConfiguracaoVariacao(
            VariacaoItemCardapio variacao,
            String configuracaoHash,
            List<GrupoConfiguracao> grupos
    ) {
    }

    public record GrupoConfiguracao(
            long associacaoId,
            long grupoId,
            String nome,
            String codigo,
            String descricao,
            TipoSelecaoModificador tipoSelecao,
            boolean obrigatorio,
            int minimo,
            int maximo,
            boolean permiteRepeticao,
            boolean ativo,
            int ordemExibicao,
            List<OpcaoConfiguracao> opcoes
    ) {
    }

    public record OpcaoConfiguracao(
            long opcaoId,
            String nome,
            String codigo,
            String descricao,
            BigDecimal acrescimo,
            Integer quantidadeMaximaPorItem,
            boolean selecionadaPorPadrao,
            boolean ativa,
            int ordemExibicao
    ) {
    }

    public record MontagemConfiguracao(
            VariacaoItemCardapio variacao,
            String configuracaoHash,
            ResolucaoComposicaoVariacaoService.ResolucaoComposicao resolucaoBase,
            List<SelecaoAplicada> selecoesAplicadas,
            List<ConsumoConfigurado> consumos,
            List<ImpactoPlanejado> impactos,
            BigDecimal acrescimoUnitario
    ) {
        public Map<Long, VendaItemModificador> modificadoresPorOpcao(List<VendaItemModificador> modificadores) {
            return modificadores.stream().collect(Collectors.toMap(
                    modificador -> modificador.getOpcaoModificador().getId(),
                    Function.identity(),
                    (a, b) -> a,
                    LinkedHashMap::new
            ));
        }
    }

    public record SelecaoAplicada(
            VariacaoGrupoModificador associacao,
            OpcaoModificador opcao,
            int quantidade,
            boolean selecaoPadrao
    ) {
        BigDecimal acrescimoTotal() {
            return opcao.getAcrescimoPreco()
                    .multiply(BigDecimal.valueOf(quantidade))
                    .setScale(ESCALA_DINHEIRO, RoundingMode.HALF_UP);
        }
    }

    public record ConsumoConfigurado(
            Produto produto,
            UnidadeMedida unidadeBase,
            BigDecimal quantidadeBasePorUnidade,
            OrigemComposicaoVenda origem,
            OpcaoModificadorComponente componenteModificador
    ) {
    }

    public record ImpactoPlanejado(
            SelecaoAplicada selecao,
            OpcaoModificadorComponente componente,
            Produto produto,
            UnidadeMedida unidadeBase,
            TipoEfeitoModificador tipoEfeito,
            BigDecimal quantidadeDeltaBase,
            PoliticaFalta politicaFalta
    ) {
    }

    private record Contexto(
            VariacaoItemCardapio variacao,
            List<VariacaoGrupoModificador> associacoes,
            Map<Long, List<OpcaoModificador>> opcoesPorGrupo,
            Map<Long, List<OpcaoModificadorComponente>> componentesPorOpcao,
            List<IncompatibilidadeOpcaoModificador> incompatibilidades,
            String hashConfiguracao
    ) {
    }
}
