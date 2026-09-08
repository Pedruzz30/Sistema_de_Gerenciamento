package service;

import model.CanalVenda;
import model.EstoqueProduto;
import model.OpcaoModificadorComponente;
import model.PoliticaFalta;
import model.PoliticaFaltaIngrediente;
import model.Produto;
import model.SeveridadeDisponibilidade;
import model.TipoEfeitoModificador;
import model.VariacaoItemCardapio;
import repository.OpcaoModificadorComponenteRepository;
import repository.EstoqueProdutoRepository;
import repository.VariacaoItemCardapioRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class CalculadoraDisponibilidadeConfiguracaoService {

    private static final int ESCALA_DINHEIRO = 4;
    private static final int ESCALA_QUANTIDADE = 6;

    private final VariacaoItemCardapioRepository variacaoRepository;
    private final EstoqueProdutoRepository estoqueRepository;
    private final OpcaoModificadorComponenteRepository componenteRepository;
    private final MontagemConfiguracaoItemService montagemConfiguracaoItemService;
    private final CalculadoraCapacidadeService calculadoraCapacidadeService;
    private final Clock clock;

    public CalculadoraDisponibilidadeConfiguracaoService(VariacaoItemCardapioRepository variacaoRepository,
                                                          EstoqueProdutoRepository estoqueRepository,
                                                          OpcaoModificadorComponenteRepository componenteRepository,
                                                          MontagemConfiguracaoItemService montagemConfiguracaoItemService,
                                                          CalculadoraCapacidadeService calculadoraCapacidadeService,
                                                          Clock clock) {
        this.variacaoRepository = variacaoRepository;
        this.estoqueRepository = estoqueRepository;
        this.componenteRepository = componenteRepository;
        this.montagemConfiguracaoItemService = montagemConfiguracaoItemService;
        this.calculadoraCapacidadeService = calculadoraCapacidadeService;
        this.clock = clock;
    }

    public ResultadoValidacaoConfiguracao validar(long variacaoId,
                                                   Long filialId,
                                                   CanalVenda canal,
                                                   BigDecimal quantidadeItem,
                                                   String configuracaoVersao,
                                                   List<MontagemConfiguracaoItemService.SelecaoModificadorInput> selecoes) {
        validarConsulta(filialId, canal);
        VariacaoItemCardapio variacao = variacaoRepository.buscarPorId(variacaoId)
                .orElseThrow(() -> new ErroNegocioException("VARIACAO_NAO_ENCONTRADA", "Variacao nao encontrada."));
        MontagemConfiguracaoItemService.MontagemConfiguracao montagem =
                montagemConfiguracaoItemService.montar(variacao, quantidadeItem, selecoes, true);
        montagemConfiguracaoItemService.validarHashObservado(configuracaoVersao, montagem.configuracaoHash());

        Map<Integer, ProdutoConsumoConfiguracao> consumoPorProduto = consolidar(montagem);
        Map<Integer, EstoqueProduto> estoquePorProduto = estoqueRepository
                .listarPorFilialEProdutos(filialId, new ArrayList<>(consumoPorProduto.keySet()))
                .stream()
                .collect(Collectors.toMap(
                        estoque -> estoque.getProduto().getId(),
                        Function.identity(),
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        List<DisponibilidadeDtos.MotivoDisponibilidade> motivos = new ArrayList<>();
        List<DisponibilidadeDtos.MotivoDisponibilidade> alertas = new ArrayList<>();
        List<CalculadoraCapacidadeService.EntradaCapacidade> entradas = new ArrayList<>();
        for (ProdutoConsumoConfiguracao consumo : consumoPorProduto.values()) {
            EstoqueProduto estoque = estoquePorProduto.get(consumo.produto().getId());
            avaliarProduto(consumo, estoque, motivos, alertas, entradas);
        }
        CalculadoraCapacidadeService.ResultadoCapacidade capacidade = calculadoraCapacidadeService.calcular(entradas);
        BigDecimal precoBase = dinheiro(variacao.getPrecoVenda());
        BigDecimal acrescimos = dinheiro(montagem.acrescimoUnitario());
        BigDecimal precoUnitarioFinal = precoBase.add(acrescimos).setScale(ESCALA_DINHEIRO, RoundingMode.HALF_UP);
        boolean disponivel = motivos.stream().noneMatch(DisponibilidadeDtos.MotivoDisponibilidade::bloqueante);
        return new ResultadoValidacaoConfiguracao(
                disponivel,
                disponivel,
                precoBase,
                acrescimos,
                precoUnitarioFinal,
                capacidade.capacidadeEstimada(),
                MontagemConfiguracaoItemService.TIPO_CAPACIDADE_CONFIGURACAO_ISOLADA,
                DisponibilidadeDtos.TIPO_VALIDACAO_SEM_RESERVA,
                false,
                motivos,
                alertas,
                montagem.configuracaoHash(),
                LocalDateTime.now(clock)
        );
    }

    public Map<Long, OpcaoDisponibilidade> avaliarOpcoes(long variacaoId, Long filialId, CanalVenda canal) {
        validarConsulta(filialId, canal);
        VariacaoItemCardapio variacao = variacaoRepository.buscarPorId(variacaoId)
                .orElseThrow(() -> new ErroNegocioException("VARIACAO_NAO_ENCONTRADA", "Variacao nao encontrada."));
        return avaliarOpcoes(variacao, filialId, canal);
    }

    public Map<Long, OpcaoDisponibilidade> avaliarOpcoes(VariacaoItemCardapio variacao, Long filialId, CanalVenda canal) {
        return avaliarConfiguracaoOpcoes(variacao, filialId, canal).opcoesPorId();
    }

    public ConfiguracaoOpcoesDisponibilidade avaliarConfiguracaoOpcoes(VariacaoItemCardapio variacao, Long filialId, CanalVenda canal) {
        validarConsulta(filialId, canal);
        MontagemConfiguracaoItemService.ConfiguracaoVariacao configuracao =
                montagemConfiguracaoItemService.configurar(variacao);
        List<MontagemConfiguracaoItemService.OpcaoConfiguracao> opcoes = configuracao.grupos().stream()
                .flatMap(grupo -> grupo.opcoes().stream())
                .toList();
        if (opcoes.isEmpty()) {
            return new ConfiguracaoOpcoesDisponibilidade(configuracao, Map.of());
        }

        List<Long> opcaoIds = opcoes.stream()
                .map(MontagemConfiguracaoItemService.OpcaoConfiguracao::opcaoId)
                .distinct()
                .toList();
        Map<Long, List<OpcaoModificadorComponente>> componentesPorOpcao = componenteRepository
                .listarPorOpcoes(opcaoIds)
                .stream()
                .collect(Collectors.groupingBy(
                        componente -> componente.getOpcaoModificador().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        List<Integer> produtoIds = componentesPorOpcao.values().stream()
                .flatMap(List::stream)
                .flatMap(componente -> produtosParaDisponibilidade(componente).stream())
                .map(Produto::getId)
                .distinct()
                .toList();
        Map<Integer, EstoqueProduto> estoquePorProduto = estoqueRepository
                .listarPorFilialEProdutos(filialId, produtoIds)
                .stream()
                .collect(Collectors.toMap(
                        estoque -> estoque.getProduto().getId(),
                        Function.identity(),
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        Map<Long, OpcaoDisponibilidade> resultado = new LinkedHashMap<>();
        for (MontagemConfiguracaoItemService.OpcaoConfiguracao opcao : opcoes.stream()
                .sorted(Comparator
                        .comparingInt(MontagemConfiguracaoItemService.OpcaoConfiguracao::ordemExibicao)
                        .thenComparing(MontagemConfiguracaoItemService.OpcaoConfiguracao::codigo))
                .toList()) {
            resultado.put(opcao.opcaoId(), avaliarOpcao(opcao, componentesPorOpcao.getOrDefault(opcao.opcaoId(), List.of()), estoquePorProduto));
        }
        return new ConfiguracaoOpcoesDisponibilidade(configuracao, resultado);
    }

    public Map<Long, List<DisponibilidadeDtos.MotivoDisponibilidade>> avaliarGruposObrigatoriosEmLote(
            List<VariacaoItemCardapio> variacoes,
            Long filialId,
            CanalVenda canal) {
        validarConsulta(filialId, canal);
        if (variacoes == null || variacoes.isEmpty()) {
            return Map.of();
        }

        List<Long> variacaoIds = variacoes.stream()
                .map(VariacaoItemCardapio::getId)
                .distinct()
                .toList();
        List<model.VariacaoGrupoModificador> associacoesObrigatorias =
                montagemConfiguracaoItemService.associacoesAtivasPorVariacoes(variacaoIds)
                        .stream()
                        .filter(associacao -> associacao.getGrupoModificador().isAtivo())
                        .filter(model.VariacaoGrupoModificador::obrigatorioEfetivo)
                        .toList();
        if (associacoesObrigatorias.isEmpty()) {
            return Map.of();
        }

        List<Long> grupoIds = associacoesObrigatorias.stream()
                .map(associacao -> associacao.getGrupoModificador().getId())
                .distinct()
                .toList();
        Map<Long, List<model.OpcaoModificador>> opcoesPorGrupo = montagemConfiguracaoItemService
                .opcoesPorGrupos(grupoIds)
                .stream()
                .collect(Collectors.groupingBy(
                        opcao -> opcao.getGrupoModificador().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        List<Long> opcaoIds = opcoesPorGrupo.values().stream()
                .flatMap(List::stream)
                .filter(model.OpcaoModificador::isAtiva)
                .map(model.OpcaoModificador::getId)
                .distinct()
                .toList();
        Map<Long, List<OpcaoModificadorComponente>> componentesPorOpcao = componenteRepository
                .listarPorOpcoes(opcaoIds)
                .stream()
                .collect(Collectors.groupingBy(
                        componente -> componente.getOpcaoModificador().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        List<Integer> produtoIds = componentesPorOpcao.values().stream()
                .flatMap(List::stream)
                .flatMap(componente -> produtosParaDisponibilidade(componente).stream())
                .map(Produto::getId)
                .distinct()
                .toList();
        Map<Integer, EstoqueProduto> estoquePorProduto = estoqueRepository
                .listarPorFilialEProdutos(filialId, produtoIds)
                .stream()
                .collect(Collectors.toMap(
                        estoque -> estoque.getProduto().getId(),
                        Function.identity(),
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        Map<Long, OpcaoDisponibilidade> disponibilidadePorOpcao = new LinkedHashMap<>();
        for (model.OpcaoModificador opcao : opcoesPorGrupo.values().stream()
                .flatMap(List::stream)
                .filter(model.OpcaoModificador::isAtiva)
                .toList()) {
            disponibilidadePorOpcao.put(opcao.getId(), avaliarOpcao(
                    opcaoConfiguracao(opcao),
                    componentesPorOpcao.getOrDefault(opcao.getId(), List.of()),
                    estoquePorProduto
            ));
        }

        Map<Long, List<DisponibilidadeDtos.MotivoDisponibilidade>> motivosPorVariacao = new LinkedHashMap<>();
        for (model.VariacaoGrupoModificador associacao : associacoesObrigatorias) {
            boolean possuiOpcaoDisponivel = opcoesPorGrupo
                    .getOrDefault(associacao.getGrupoModificador().getId(), List.of())
                    .stream()
                    .filter(model.OpcaoModificador::isAtiva)
                    .anyMatch(opcao -> {
                        OpcaoDisponibilidade disponibilidade = disponibilidadePorOpcao.get(opcao.getId());
                        return disponibilidade != null && disponibilidade.disponivel();
                    });
            if (!possuiOpcaoDisponivel) {
                motivosPorVariacao
                        .computeIfAbsent(associacao.getVariacaoItemCardapio().getId(), ignored -> new ArrayList<>())
                        .add(new DisponibilidadeDtos.MotivoDisponibilidade(
                                "GRUPO_OBRIGATORIO_SEM_OPCAO_DISPONIVEL",
                                SeveridadeDisponibilidade.BLOQUEIO,
                                null,
                                null,
                                null,
                                null,
                                null,
                                "Grupo obrigatorio sem opcao ativa e disponivel: "
                                        + associacao.getGrupoModificador().getNome() + ".",
                                true
                        ));
            }
        }
        return motivosPorVariacao;
    }

    private Map<Integer, ProdutoConsumoConfiguracao> consolidar(MontagemConfiguracaoItemService.MontagemConfiguracao montagem) {
        Map<Integer, ProdutoConsumoConfiguracao> mapa = new LinkedHashMap<>();
        for (MontagemConfiguracaoItemService.ConsumoConfigurado consumo : montagem.consumos()) {
            Produto produto = consumo.produto();
            PoliticaFalta politica = produto.getPoliticaFalta();
            mapa.merge(
                    produto.getId(),
                    new ProdutoConsumoConfiguracao(
                            produto,
                            consumo.unidadeBase() == null ? null : consumo.unidadeBase().getCodigo(),
                            consumo.quantidadeBasePorUnidade(),
                            politica
                    ),
                    ProdutoConsumoConfiguracao::somar
            );
        }
        for (MontagemConfiguracaoItemService.ImpactoPlanejado impacto : montagem.impactos()) {
            if (impacto.quantidadeDeltaBase().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            ProdutoConsumoConfiguracao atual = mapa.get(impacto.produto().getId());
            if (atual != null) {
                mapa.put(impacto.produto().getId(), atual.comPolitica(impacto.politicaFalta()));
            }
        }
        return mapa;
    }

    private OpcaoDisponibilidade avaliarOpcao(MontagemConfiguracaoItemService.OpcaoConfiguracao opcao,
                                              List<OpcaoModificadorComponente> componentes,
                                              Map<Integer, EstoqueProduto> estoquePorProduto) {
        List<DisponibilidadeDtos.MotivoDisponibilidade> motivos = new ArrayList<>();
        List<DisponibilidadeDtos.MotivoDisponibilidade> alertas = new ArrayList<>();
        List<CalculadoraCapacidadeService.EntradaCapacidade> entradas = new ArrayList<>();
        if (!opcao.ativa()) {
            motivos.add(new DisponibilidadeDtos.MotivoDisponibilidade(
                    "OPCAO_MODIFICADOR_INATIVA",
                    SeveridadeDisponibilidade.BLOQUEIO,
                    null,
                    null,
                    null,
                    null,
                    null,
                    "Opcao de modificador inativa.",
                    true
            ));
        }
        for (ProdutoConsumoConfiguracao consumo : consolidarComponentesOpcao(componentes).values()) {
            EstoqueProduto estoque = estoquePorProduto.get(consumo.produto().getId());
            avaliarProduto(consumo, estoque, motivos, alertas, entradas);
        }
        CalculadoraCapacidadeService.ResultadoCapacidade capacidade = calculadoraCapacidadeService.calcular(entradas);
        boolean disponivel = motivos.stream().noneMatch(DisponibilidadeDtos.MotivoDisponibilidade::bloqueante);
        String estado = disponivel
                ? (alertas.isEmpty() ? "DISPONIVEL" : "DISPONIVEL_COM_ALERTA")
                : "INDISPONIVEL";
        return new OpcaoDisponibilidade(
                opcao.opcaoId(),
                disponivel,
                estado,
                capacidade.capacidadeEstimada(),
                motivos,
                alertas
        );
    }

    private static MontagemConfiguracaoItemService.OpcaoConfiguracao opcaoConfiguracao(model.OpcaoModificador opcao) {
        return new MontagemConfiguracaoItemService.OpcaoConfiguracao(
                opcao.getId(),
                opcao.getNome(),
                opcao.getCodigo(),
                opcao.getDescricao(),
                opcao.getAcrescimoPreco(),
                opcao.getQuantidadeMaximaPorItem(),
                opcao.isSelecionadaPorPadrao(),
                opcao.isAtiva(),
                opcao.getOrdemExibicao()
        );
    }

    private Map<Integer, ProdutoConsumoConfiguracao> consolidarComponentesOpcao(List<OpcaoModificadorComponente> componentes) {
        Map<Integer, ProdutoConsumoConfiguracao> mapa = new LinkedHashMap<>();
        for (OpcaoModificadorComponente componente : componentes) {
            if (componente.getTipoEfeito() == TipoEfeitoModificador.SEM_IMPACTO_ESTOQUE) {
                continue;
            }
            Produto produto = produtoDisponibilidade(componente);
            if (produto == null) {
                continue;
            }
            BigDecimal quantidade = componente.getQuantidadeBase();
            PoliticaFalta politica = resolverPolitica(componente, produto);
            mapa.merge(
                    produto.getId(),
                    new ProdutoConsumoConfiguracao(
                            produto,
                            produto.getUnidadeBase() == null ? null : produto.getUnidadeBase().getCodigo(),
                            quantidade.setScale(ESCALA_QUANTIDADE, RoundingMode.HALF_UP),
                            politica
                    ),
                    ProdutoConsumoConfiguracao::somar
            );
        }
        return mapa;
    }

    private static List<Produto> produtosParaDisponibilidade(OpcaoModificadorComponente componente) {
        Produto produto = produtoDisponibilidade(componente);
        return produto == null ? List.of() : List.of(produto);
    }

    private static Produto produtoDisponibilidade(OpcaoModificadorComponente componente) {
        if (componente.getTipoEfeito() == TipoEfeitoModificador.ADICIONAR) {
            return componente.getProdutoDestino();
        }
        if (componente.getTipoEfeito() == TipoEfeitoModificador.SUBSTITUIR) {
            return componente.getProdutoDestino();
        }
        return null;
    }

    private static PoliticaFalta resolverPolitica(OpcaoModificadorComponente componente, Produto produto) {
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

    private void avaliarProduto(ProdutoConsumoConfiguracao consumo,
                                EstoqueProduto estoque,
                                List<DisponibilidadeDtos.MotivoDisponibilidade> motivos,
                                List<DisponibilidadeDtos.MotivoDisponibilidade> alertas,
                                List<CalculadoraCapacidadeService.EntradaCapacidade> entradas) {
        Produto produto = consumo.produto();
        BigDecimal disponivel = estoque == null ? null : estoque.quantidadeDisponivel();
        boolean bloqueante = consumo.politicaFalta() == PoliticaFalta.BLOQUEAR;
        if (estoque == null) {
            DisponibilidadeDtos.MotivoDisponibilidade motivo = motivo(
                    "ESTOQUE_PRODUTO_NAO_CONFIGURADO",
                    consumo,
                    null,
                    bloqueante
            );
            adicionarPorPolitica(motivo, motivos, alertas);
        } else if (disponivel.compareTo(consumo.quantidadeBasePorUnidade()) < 0) {
            DisponibilidadeDtos.MotivoDisponibilidade motivo = motivo(
                    "ESTOQUE_INSUFICIENTE",
                    consumo,
                    disponivel,
                    bloqueante
            );
            adicionarPorPolitica(motivo, motivos, alertas);
        }
        entradas.add(new CalculadoraCapacidadeService.EntradaCapacidade(
                produto.getId(),
                produto.getNome(),
                disponivel == null ? BigDecimal.ZERO : disponivel,
                consumo.quantidadeBasePorUnidade(),
                consumo.unidade(),
                bloqueante
        ));
    }

    private static DisponibilidadeDtos.MotivoDisponibilidade motivo(String codigo,
                                                                    ProdutoConsumoConfiguracao consumo,
                                                                    BigDecimal disponivel,
                                                                    boolean bloqueante) {
        return new DisponibilidadeDtos.MotivoDisponibilidade(
                codigo,
                bloqueante ? SeveridadeDisponibilidade.BLOQUEIO : severidade(consumo.politicaFalta()),
                consumo.produto().getId(),
                consumo.produto().getNome(),
                consumo.quantidadeBasePorUnidade().setScale(ESCALA_QUANTIDADE, RoundingMode.HALF_UP),
                disponivel == null ? null : disponivel.setScale(ESCALA_QUANTIDADE, RoundingMode.HALF_UP),
                consumo.unidade(),
                "Disponibilidade da configuracao insuficiente para uma unidade.",
                bloqueante
        );
    }

    private static void adicionarPorPolitica(DisponibilidadeDtos.MotivoDisponibilidade motivo,
                                             List<DisponibilidadeDtos.MotivoDisponibilidade> motivos,
                                             List<DisponibilidadeDtos.MotivoDisponibilidade> alertas) {
        if (motivo.bloqueante()) {
            motivos.add(motivo);
        } else if (motivo.severidade() == SeveridadeDisponibilidade.ALERTA) {
            alertas.add(motivo);
        } else {
            motivos.add(motivo);
        }
    }

    private static SeveridadeDisponibilidade severidade(PoliticaFalta politica) {
        return switch (politica) {
            case BLOQUEAR -> SeveridadeDisponibilidade.BLOQUEIO;
            case ALERTAR -> SeveridadeDisponibilidade.ALERTA;
            case IGNORAR -> SeveridadeDisponibilidade.INFO;
        };
    }

    private static void validarConsulta(Long filialId, CanalVenda canal) {
        if (filialId == null || filialId <= 0) {
            throw new ErroNegocioException("FILIAL_INVALIDA", "Filial da consulta e obrigatoria.");
        }
        if (canal == null) {
            throw new ErroNegocioException("CANAL_INVALIDO", "Canal da consulta e obrigatorio.");
        }
    }

    private static BigDecimal dinheiro(BigDecimal valor) {
        return (valor == null ? BigDecimal.ZERO : valor).setScale(ESCALA_DINHEIRO, RoundingMode.HALF_UP);
    }

    public record ResultadoValidacaoConfiguracao(
            boolean valida,
            boolean disponivel,
            BigDecimal precoBase,
            BigDecimal acrescimos,
            BigDecimal precoUnitarioFinal,
            Integer capacidadeEstimadaConfiguracao,
            String tipoCapacidade,
            String tipoValidacao,
            boolean garantia,
            List<DisponibilidadeDtos.MotivoDisponibilidade> motivos,
            List<DisponibilidadeDtos.MotivoDisponibilidade> alertas,
            String configuracaoVersao,
            LocalDateTime calculadoEm
    ) {
    }

    public record OpcaoDisponibilidade(
            long opcaoId,
            boolean disponivel,
            String estado,
            Integer capacidadeEstimada,
            List<DisponibilidadeDtos.MotivoDisponibilidade> motivos,
            List<DisponibilidadeDtos.MotivoDisponibilidade> alertas
    ) {
    }

    public record ConfiguracaoOpcoesDisponibilidade(
            MontagemConfiguracaoItemService.ConfiguracaoVariacao configuracao,
            Map<Long, OpcaoDisponibilidade> opcoesPorId
    ) {
    }

    private record ProdutoConsumoConfiguracao(
            Produto produto,
            String unidade,
            BigDecimal quantidadeBasePorUnidade,
            PoliticaFalta politicaFalta
    ) {
        ProdutoConsumoConfiguracao somar(ProdutoConsumoConfiguracao outro) {
            return new ProdutoConsumoConfiguracao(
                    produto,
                    unidade,
                    quantidadeBasePorUnidade.add(outro.quantidadeBasePorUnidade())
                            .setScale(ESCALA_QUANTIDADE, RoundingMode.HALF_UP),
                    politicaMaisRestritiva(politicaFalta, outro.politicaFalta())
            );
        }

        ProdutoConsumoConfiguracao comPolitica(PoliticaFalta politica) {
            return new ProdutoConsumoConfiguracao(
                    produto,
                    unidade,
                    quantidadeBasePorUnidade,
                    politicaMaisRestritiva(politicaFalta, politica)
            );
        }

        private static PoliticaFalta politicaMaisRestritiva(PoliticaFalta atual, PoliticaFalta nova) {
            if (atual == PoliticaFalta.BLOQUEAR || nova == PoliticaFalta.BLOQUEAR) {
                return PoliticaFalta.BLOQUEAR;
            }
            if (atual == PoliticaFalta.ALERTAR || nova == PoliticaFalta.ALERTAR) {
                return PoliticaFalta.ALERTAR;
            }
            return PoliticaFalta.IGNORAR;
        }
    }
}
