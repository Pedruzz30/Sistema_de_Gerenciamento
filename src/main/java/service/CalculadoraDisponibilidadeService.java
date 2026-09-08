package service;

import model.CanalVenda;
import model.EstadoDisponibilidade;
import model.EstoqueProduto;
import model.FichaTecnica;
import model.FichaTecnicaVersao;
import model.ItemCardapio;
import model.OrigemComposicaoVenda;
import model.PausaCardapio;
import model.PoliticaFalta;
import model.PoliticaFaltaIngrediente;
import model.Produto;
import model.RegraHorarioCardapio;
import model.SeveridadeDisponibilidade;
import model.UnidadeMedida;
import model.VariacaoCanalDisponibilidade;
import model.VariacaoItemCardapio;
import repository.EstoqueProdutoRepository;
import repository.PausaCardapioRepository;
import repository.RegraHorarioCardapioRepository;
import repository.VariacaoCanalDisponibilidadeRepository;
import repository.VariacaoItemCardapioRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class CalculadoraDisponibilidadeService {

    private static final int ESCALA_QUANTIDADE = 6;
    private static final int LIMITE_ALERTA_PADRAO = 3;

    private final VariacaoItemCardapioRepository variacaoRepository;
    private final EstoqueProdutoRepository estoqueProdutoRepository;
    private final PausaCardapioRepository pausaRepository;
    private final RegraHorarioCardapioRepository regraHorarioRepository;
    private final VariacaoCanalDisponibilidadeRepository canalRepository;
    private final ResolucaoComposicaoVariacaoService resolucaoComposicaoService;
    private final CalculadoraCapacidadeService calculadoraCapacidadeService;
    private final CalculadoraDisponibilidadeConfiguracaoService disponibilidadeConfiguracaoService;
    private final Clock clock;
    private final int limiteAlertaPadrao;

    public CalculadoraDisponibilidadeService(VariacaoItemCardapioRepository variacaoRepository,
                                             EstoqueProdutoRepository estoqueProdutoRepository,
                                             PausaCardapioRepository pausaRepository,
                                             RegraHorarioCardapioRepository regraHorarioRepository,
                                             VariacaoCanalDisponibilidadeRepository canalRepository,
                                             ResolucaoComposicaoVariacaoService resolucaoComposicaoService,
                                             CalculadoraCapacidadeService calculadoraCapacidadeService,
                                             CalculadoraDisponibilidadeConfiguracaoService disponibilidadeConfiguracaoService,
                                             Clock clock,
                                             int limiteAlertaPadrao) {
        this.variacaoRepository = variacaoRepository;
        this.estoqueProdutoRepository = estoqueProdutoRepository;
        this.pausaRepository = pausaRepository;
        this.regraHorarioRepository = regraHorarioRepository;
        this.canalRepository = canalRepository;
        this.resolucaoComposicaoService = resolucaoComposicaoService;
        this.calculadoraCapacidadeService = calculadoraCapacidadeService;
        this.disponibilidadeConfiguracaoService = disponibilidadeConfiguracaoService;
        this.clock = clock;
        this.limiteAlertaPadrao = limiteAlertaPadrao < 0 ? LIMITE_ALERTA_PADRAO : limiteAlertaPadrao;
    }

    public DisponibilidadeDtos.ResultadoDisponibilidade consultar(long variacaoId, Long filialId, CanalVenda canal) {
        validarConsulta(filialId, canal);
        List<DisponibilidadeDtos.ResultadoDisponibilidade> resultados = consultarLote(filialId, canal, List.of(variacaoId));
        return resultados.stream()
                .filter(resultado -> resultado.variacaoId() == variacaoId)
                .findFirst()
                .orElseThrow(() -> new ErroNegocioException("VARIACAO_NAO_ENCONTRADA", "Variacao nao encontrada."));
    }

    public DisponibilidadeDtos.ConsultaLoteDisponibilidade consultarCardapio(Long filialId,
                                                                              CanalVenda canal,
                                                                              Collection<Long> variacaoIds) {
        List<DisponibilidadeDtos.ResultadoDisponibilidade> variacoes = consultarLote(filialId, canal, variacaoIds);
        Map<Integer, List<DisponibilidadeDtos.ResultadoDisponibilidade>> porItem = variacoes.stream()
                .collect(Collectors.groupingBy(
                        DisponibilidadeDtos.ResultadoDisponibilidade::itemCardapioId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        List<DisponibilidadeDtos.ResumoItemDisponibilidade> itens = porItem.entrySet().stream()
                .map(entry -> {
                    int disponiveis = (int) entry.getValue().stream()
                            .filter(DisponibilidadeDtos.ResultadoDisponibilidade::disponivel)
                            .count();
                    return new DisponibilidadeDtos.ResumoItemDisponibilidade(
                            entry.getKey(),
                            disponiveis > 0,
                            disponiveis,
                            entry.getValue().size() - disponiveis
                    );
                })
                .toList();
        LocalDateTime calculadoEm = variacoes.isEmpty()
                ? LocalDateTime.now(clock)
                : variacoes.get(0).calculadoEm();
        return new DisponibilidadeDtos.ConsultaLoteDisponibilidade(filialId, canal, itens, variacoes, calculadoEm);
    }

    public DisponibilidadeDtos.DiagnosticoDisponibilidade diagnosticar(long variacaoId, Long filialId, CanalVenda canal) {
        validarConsulta(filialId, canal);
        ContextoDisponibilidade contexto = prepararContexto(filialId, canal, List.of(variacaoId));
        VariacaoItemCardapio variacao = contexto.variacoes().stream()
                .filter(v -> v.getId() == variacaoId)
                .findFirst()
                .orElseThrow(() -> new ErroNegocioException("VARIACAO_NAO_ENCONTRADA", "Variacao nao encontrada."));
        AvaliacaoVariacao avaliacao = avaliarVariacao(variacao, contexto);
        ResolucaoComposicaoVariacaoService.ResolucaoComposicao resolucao =
                contexto.resolucaoPorVariacao().get(variacao.getId());
        FichaTecnica ficha = resolucao == null ? null : resolucao.fichaTecnica();
        FichaTecnicaVersao versao = resolucao == null ? null : resolucao.fichaTecnicaVersao();
        return new DisponibilidadeDtos.DiagnosticoDisponibilidade(
                avaliacao.resultado(),
                ficha == null ? null : ficha.getId(),
                versao == null ? null : versao.getId(),
                versao == null ? null : versao.getNumeroVersao(),
                avaliacao.produtos()
        );
    }

    public List<DisponibilidadeDtos.ResultadoDisponibilidade> consultarLote(Long filialId,
                                                                             CanalVenda canal,
                                                                             Collection<Long> variacaoIds) {
        validarConsulta(filialId, canal);
        ContextoDisponibilidade contexto = prepararContexto(filialId, canal, variacaoIds);
        return contexto.variacoes().stream()
                .map(variacao -> avaliarVariacao(variacao, contexto).resultado())
                .toList();
    }

    private ContextoDisponibilidade prepararContexto(Long filialId,
                                                     CanalVenda canal,
                                                     Collection<Long> variacaoIds) {
        List<VariacaoItemCardapio> variacoes = carregarVariacoes(variacaoIds);
        if (variacoes.isEmpty()) {
            return ContextoDisponibilidade.vazio(filialId, canal, LocalDateTime.now(clock));
        }
        LocalDateTime agora = LocalDateTime.now(clock);
        Map<Long, ResolucaoComposicaoVariacaoService.ResolucaoComposicao> resolucoes =
                resolucaoComposicaoService.resolverVariacoes(variacoes);
        List<Integer> produtoIds = resolucoes.values().stream()
                .filter(ResolucaoComposicaoVariacaoService.ResolucaoComposicao::completa)
                .flatMap(resolucao -> resolucao.componentes().stream())
                .map(componente -> componente.produto().getId())
                .distinct()
                .toList();
        Map<Integer, EstoqueProduto> estoquePorProduto = estoqueProdutoRepository
                .listarPorFilialEProdutos(filialId, produtoIds)
                .stream()
                .collect(Collectors.toMap(
                        estoque -> estoque.getProduto().getId(),
                        Function.identity(),
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
        List<Long> idsVariacoes = variacoes.stream()
                .map(VariacaoItemCardapio::getId)
                .toList();
        Map<Long, List<VariacaoCanalDisponibilidade>> regrasCanalPorVariacao =
                canalRepository.listarPorVariacoesFilial(idsVariacoes, filialId)
                        .stream()
                        .collect(Collectors.groupingBy(
                                regra -> regra.getVariacaoItemCardapio().getId(),
                                LinkedHashMap::new,
                                Collectors.toList()
                        ));
        Map<Long, List<DisponibilidadeDtos.MotivoDisponibilidade>> motivosGruposObrigatorios =
                disponibilidadeConfiguracaoService.avaliarGruposObrigatoriosEmLote(variacoes, filialId, canal);
        return new ContextoDisponibilidade(
                filialId,
                canal,
                agora,
                variacoes,
                resolucoes,
                estoquePorProduto,
                pausaRepository.listarAtivas(filialId, agora),
                regraHorarioRepository.listarAtivasPorFilial(filialId),
                regrasCanalPorVariacao,
                motivosGruposObrigatorios
        );
    }

    private List<VariacaoItemCardapio> carregarVariacoes(Collection<Long> variacaoIds) {
        if (variacaoIds == null || variacaoIds.isEmpty()) {
            return variacaoRepository.listarTodos();
        }
        List<Long> ids = variacaoIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return variacaoRepository.listarPorIds(ids);
    }

    private AvaliacaoVariacao avaliarVariacao(VariacaoItemCardapio variacao, ContextoDisponibilidade contexto) {
        List<DisponibilidadeDtos.MotivoDisponibilidade> motivos = new ArrayList<>();
        List<DisponibilidadeDtos.MotivoDisponibilidade> alertas = new ArrayList<>();
        List<DisponibilidadeDtos.ProdutoDiagnostico> diagnosticos = new ArrayList<>();
        ItemCardapio item = variacao.getItemCardapio();

        avaliarCadastro(variacao, item, motivos);
        avaliarCanal(variacao, contexto, motivos);
        avaliarPausas(variacao, contexto, motivos);
        avaliarHorario(variacao, contexto, motivos);

        ResolucaoComposicaoVariacaoService.ResolucaoComposicao resolucao =
                contexto.resolucaoPorVariacao().get(variacao.getId());
        List<CalculadoraCapacidadeService.EntradaCapacidade> entradasCapacidade = new ArrayList<>();
        if (resolucao == null || !resolucao.completa()) {
            adicionarMotivoConfiguracao(resolucao, motivos);
        } else {
            Map<Integer, ComponenteDisponibilidade> componentes = consolidarComponentes(resolucao.componentes());
            for (ComponenteDisponibilidade componente : componentes.values()) {
                avaliarComponente(componente, contexto, motivos, alertas, diagnosticos, entradasCapacidade);
            }
        }
        avaliarGruposObrigatorios(variacao, contexto, motivos);

        CalculadoraCapacidadeService.ResultadoCapacidade capacidade =
                calculadoraCapacidadeService.calcular(entradasCapacidade);
        adicionarAlertasCapacidade(variacao, capacidade, motivos, alertas);

        boolean disponivel = motivos.stream().noneMatch(DisponibilidadeDtos.MotivoDisponibilidade::bloqueante);
        EstadoDisponibilidade estado = estadoPrincipal(disponivel, motivos, alertas);
        return new AvaliacaoVariacao(
                new DisponibilidadeDtos.ResultadoDisponibilidade(
                        item == null ? 0 : item.getId(),
                        variacao.getId(),
                        contexto.filialId(),
                        contexto.canal(),
                        disponivel,
                        estado,
                        capacidade.capacidadeEstimada(),
                        DisponibilidadeDtos.TIPO_CAPACIDADE_ITEM_ISOLADO,
                        DisponibilidadeDtos.TIPO_VALIDACAO_SEM_RESERVA,
                        false,
                        capacidade.ingredienteLimitantePrincipal(),
                        capacidade.ingredientesLimitantes(),
                        List.copyOf(motivos),
                        List.copyOf(alertas),
                        contexto.agora()
                ),
                List.copyOf(diagnosticos)
        );
    }

    private void avaliarGruposObrigatorios(VariacaoItemCardapio variacao,
                                           ContextoDisponibilidade contexto,
                                           List<DisponibilidadeDtos.MotivoDisponibilidade> motivos) {
        motivos.addAll(contexto.motivosGruposObrigatoriosPorVariacao()
                .getOrDefault(variacao.getId(), List.of()));
    }

    private void avaliarCadastro(VariacaoItemCardapio variacao,
                                 ItemCardapio item,
                                 List<DisponibilidadeDtos.MotivoDisponibilidade> motivos) {
        if (item == null) {
            motivos.add(bloqueio("ITEM_CARDAPIO_NAO_ENCONTRADO", "Item de cardapio nao encontrado."));
            return;
        }
        if (!item.isAtivo()) {
            motivos.add(bloqueio("ITEM_INATIVO", "Item de cardapio inativo."));
        }
        if (!item.isDisponivel()) {
            motivos.add(bloqueio("ITEM_PAUSADO", "Item pausado pela disponibilidade manual legada."));
        }
        if (item.getCategoriaCardapio() != null && !item.getCategoriaCardapio().isAtivo()) {
            motivos.add(bloqueio("CATEGORIA_INATIVA", "Categoria de cardapio inativa."));
        }
        if (!variacao.isAtiva()) {
            motivos.add(bloqueio("VARIACAO_INATIVA", "Variacao inativa."));
        }
    }

    private void avaliarCanal(VariacaoItemCardapio variacao,
                              ContextoDisponibilidade contexto,
                              List<DisponibilidadeDtos.MotivoDisponibilidade> motivos) {
        List<VariacaoCanalDisponibilidade> regras = contexto.regrasCanalPorVariacao()
                .getOrDefault(variacao.getId(), List.of());
        Optional<VariacaoCanalDisponibilidade> regraCanal = regras.stream()
                .filter(regra -> regra.getCanal() == contexto.canal())
                .findFirst();
        if (regraCanal.isPresent() && !regraCanal.get().isPermitida()) {
            motivos.add(bloqueio("CANAL_NAO_PERMITIDO", "Variacao nao permitida para o canal informado."));
        }
    }

    private void avaliarPausas(VariacaoItemCardapio variacao,
                               ContextoDisponibilidade contexto,
                               List<DisponibilidadeDtos.MotivoDisponibilidade> motivos) {
        contexto.pausasAtivas().stream()
                .filter(pausa -> pausa.atinge(variacao, contexto.canal()))
                .findFirst()
                .ifPresent(pausa -> {
                    String codigo = pausa.getVariacaoItemCardapio() == null ? "ITEM_PAUSADO" : "VARIACAO_PAUSADA";
                    motivos.add(bloqueio(codigo, "Cardapio pausado: " + pausa.getMotivoCodigo().name() + "."));
                });
    }

    private void avaliarHorario(VariacaoItemCardapio variacao,
                                ContextoDisponibilidade contexto,
                                List<DisponibilidadeDtos.MotivoDisponibilidade> motivos) {
        List<RegraHorarioCardapio> aplicaveis = contexto.regrasHorario().stream()
                .filter(regra -> regra.atinge(variacao, contexto.canal()))
                .toList();
        if (aplicaveis.isEmpty()) {
            return;
        }

        DayOfWeek dia = contexto.agora().getDayOfWeek();
        LocalTime hora = contexto.agora().toLocalTime();
        boolean permitido = aplicaveis.stream().anyMatch(regra -> regraPermite(regra, dia, hora));
        if (!permitido) {
            boolean diaPossivel = aplicaveis.stream().anyMatch(regra -> regraPodeCobrirDia(regra, dia));
            motivos.add(bloqueio(
                    diaPossivel ? "FORA_DO_HORARIO" : "FORA_DO_DIA_PERMITIDO",
                    diaPossivel ? "Variacao fora do horario permitido." : "Variacao fora do dia permitido."
            ));
        }
    }

    private boolean regraPermite(RegraHorarioCardapio regra, DayOfWeek dia, LocalTime hora) {
        if (regra.permite(dia, hora)) {
            return true;
        }
        return atravessaMeiaNoite(regra)
                && regra.getDiaSemana().plus(1) == dia
                && hora.isBefore(regra.getHoraFim());
    }

    private boolean regraPodeCobrirDia(RegraHorarioCardapio regra, DayOfWeek dia) {
        return regra.getDiaSemana() == dia
                || (atravessaMeiaNoite(regra) && regra.getDiaSemana().plus(1) == dia);
    }

    private boolean atravessaMeiaNoite(RegraHorarioCardapio regra) {
        return regra.getHoraInicio().isAfter(regra.getHoraFim());
    }

    private void adicionarMotivoConfiguracao(ResolucaoComposicaoVariacaoService.ResolucaoComposicao resolucao,
                                             List<DisponibilidadeDtos.MotivoDisponibilidade> motivos) {
        String codigo = resolucao == null ? "CONFIGURACAO_INCOMPLETA" : resolucao.codigoErro();
        String mensagem = resolucao == null ? "Composicao da variacao nao encontrada." : resolucao.mensagemErro();
        if ("FICHA_TECNICA_NAO_ENCONTRADA".equals(codigo) || "FICHA_SEM_VERSAO_PUBLICADA".equals(codigo)) {
            motivos.add(bloqueio(codigo, mensagem));
        } else {
            motivos.add(bloqueio(codigo == null ? "CONFIGURACAO_INCOMPLETA" : codigo, mensagem));
        }
    }

    private Map<Integer, ComponenteDisponibilidade> consolidarComponentes(
            List<ResolucaoComposicaoVariacaoService.ComponenteComposicao> componentes) {
        Map<Integer, ComponenteDisponibilidade> consolidados = new LinkedHashMap<>();
        for (ResolucaoComposicaoVariacaoService.ComponenteComposicao componente : componentes) {
            Produto produto = componente.produto();
            PoliticaFalta politica = resolverPolitica(componente);
            ComponenteDisponibilidade atual = consolidados.get(produto.getId());
            if (atual == null) {
                consolidados.put(produto.getId(), new ComponenteDisponibilidade(
                        produto,
                        componente.unidadeBase(),
                        componente.quantidadeBasePorUnidadeComercial().setScale(ESCALA_QUANTIDADE, RoundingMode.HALF_UP),
                        politica,
                        componente.origem()
                ));
            } else {
                consolidados.put(produto.getId(), atual.somar(componente.quantidadeBasePorUnidadeComercial(), politica));
            }
        }
        return consolidados;
    }

    private PoliticaFalta resolverPolitica(ResolucaoComposicaoVariacaoService.ComponenteComposicao componente) {
        if (componente.origem() == OrigemComposicaoVenda.PRODUTO_DIRETO || componente.ingrediente() == null) {
            return componente.produto().getPoliticaFalta();
        }
        PoliticaFaltaIngrediente politicaIngrediente = componente.ingrediente().getPoliticaFalta();
        if (politicaIngrediente == null || politicaIngrediente == PoliticaFaltaIngrediente.HERDAR_PRODUTO) {
            return componente.produto().getPoliticaFalta();
        }
        return switch (politicaIngrediente) {
            case BLOQUEAR -> PoliticaFalta.BLOQUEAR;
            case ALERTAR -> PoliticaFalta.ALERTAR;
            case IGNORAR -> PoliticaFalta.IGNORAR;
            case HERDAR_PRODUTO -> componente.produto().getPoliticaFalta();
        };
    }

    private void avaliarComponente(ComponenteDisponibilidade componente,
                                   ContextoDisponibilidade contexto,
                                   List<DisponibilidadeDtos.MotivoDisponibilidade> motivos,
                                   List<DisponibilidadeDtos.MotivoDisponibilidade> alertas,
                                   List<DisponibilidadeDtos.ProdutoDiagnostico> diagnosticos,
                                   List<CalculadoraCapacidadeService.EntradaCapacidade> entradasCapacidade) {
        Produto produto = componente.produto();
        EstoqueProduto estoque = contexto.estoquePorProduto().get(produto.getId());
        BigDecimal disponivel = estoque == null ? null : estoque.quantidadeDisponivel();
        boolean bloqueante = componente.politicaFalta() == PoliticaFalta.BLOQUEAR;
        boolean produtoVendavel = produto.isAtivo() && produto.isControladoPorEstoque();

        if (!produto.isAtivo()) {
            motivos.add(motivoProduto("PRODUTO_INATIVO", SeveridadeDisponibilidade.BLOQUEIO, produto,
                    componente.unidadeBase(), componente.quantidadeBasePorUnidade(), disponivel,
                    "Produto inativo.", true));
        }
        if (!produto.isControladoPorEstoque()) {
            motivos.add(motivoProduto("CONFIGURACAO_INCOMPLETA", SeveridadeDisponibilidade.BLOQUEIO, produto,
                    componente.unidadeBase(), componente.quantidadeBasePorUnidade(), disponivel,
                    "Produto nao controlado por estoque.", true));
        }
        if (estoque == null) {
            DisponibilidadeDtos.MotivoDisponibilidade motivo = motivoProduto(
                    "ESTOQUE_PRODUTO_NAO_CONFIGURADO",
                    severidade(componente.politicaFalta()),
                    produto,
                    componente.unidadeBase(),
                    componente.quantidadeBasePorUnidade(),
                    null,
                    "Estoque decimal do produto nao configurado para a filial.",
                    bloqueante
            );
            adicionarPorPolitica(motivo, motivos, alertas);
        } else {
            avaliarEstoque(componente, estoque, motivos, alertas);
        }

        boolean entraNaCapacidade = bloqueante && produtoVendavel;
        entradasCapacidade.add(new CalculadoraCapacidadeService.EntradaCapacidade(
                produto.getId(),
                produto.getNome(),
                estoque == null ? BigDecimal.ZERO : disponivel,
                componente.quantidadeBasePorUnidade(),
                codigoUnidade(componente.unidadeBase()),
                entraNaCapacidade
        ));
        diagnosticos.add(new DisponibilidadeDtos.ProdutoDiagnostico(
                produto.getId(),
                produto.getNome(),
                componente.quantidadeBasePorUnidade(),
                estoque == null ? null : estoque.getQuantidadeAtual(),
                estoque == null ? null : estoque.getQuantidadeReservada(),
                disponivel,
                codigoUnidade(componente.unidadeBase()),
                componente.politicaFalta().name(),
                estoque == null || componente.quantidadeBasePorUnidade().compareTo(BigDecimal.ZERO) <= 0
                        ? null
                        : Math.max(0, inteiroSeguro(disponivel.divideToIntegralValue(componente.quantidadeBasePorUnidade()))),
                entraNaCapacidade
        ));
    }

    private void avaliarEstoque(ComponenteDisponibilidade componente,
                                EstoqueProduto estoque,
                                List<DisponibilidadeDtos.MotivoDisponibilidade> motivos,
                                List<DisponibilidadeDtos.MotivoDisponibilidade> alertas) {
        Produto produto = componente.produto();
        BigDecimal disponivel = estoque.quantidadeDisponivel();
        boolean bloqueante = componente.politicaFalta() == PoliticaFalta.BLOQUEAR;

        if (disponivel.compareTo(componente.quantidadeBasePorUnidade()) < 0) {
            DisponibilidadeDtos.MotivoDisponibilidade motivo = motivoProduto(
                    "ESTOQUE_INSUFICIENTE",
                    severidade(componente.politicaFalta()),
                    produto,
                    componente.unidadeBase(),
                    componente.quantidadeBasePorUnidade(),
                    disponivel,
                    "Estoque disponivel insuficiente para uma unidade.",
                    bloqueante
            );
            adicionarPorPolitica(motivo, motivos, alertas);
        }
        if (disponivel.compareTo(BigDecimal.ZERO) < 0) {
            alertas.add(motivoProduto("PRODUTO_COM_ESTOQUE_NEGATIVO", SeveridadeDisponibilidade.ALERTA, produto,
                    componente.unidadeBase(), componente.quantidadeBasePorUnidade(), disponivel,
                    "Produto esta com estoque disponivel negativo.", false));
        }
        BigDecimal minimo = BigDecimal.valueOf(produto.getQuantidadeMinima());
        if (minimo.compareTo(BigDecimal.ZERO) > 0 && estoque.getQuantidadeAtual().compareTo(minimo) < 0) {
            alertas.add(motivoProduto("PRODUTO_ABAIXO_ESTOQUE_MINIMO", SeveridadeDisponibilidade.ALERTA, produto,
                    componente.unidadeBase(), componente.quantidadeBasePorUnidade(), disponivel,
                    "Produto abaixo do estoque minimo cadastrado.", false));
        }
        if (componente.politicaFalta() == PoliticaFalta.ALERTAR && disponivel.compareTo(componente.quantidadeBasePorUnidade()) < 0) {
            alertas.add(motivoProduto("POLITICA_ALERTAR_ATIVADA", SeveridadeDisponibilidade.ALERTA, produto,
                    componente.unidadeBase(), componente.quantidadeBasePorUnidade(), disponivel,
                    "Politica ALERTAR permitiu manter o item disponivel.", false));
        }
    }

    private void adicionarPorPolitica(DisponibilidadeDtos.MotivoDisponibilidade motivo,
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

    private void adicionarAlertasCapacidade(VariacaoItemCardapio variacao,
                                            CalculadoraCapacidadeService.ResultadoCapacidade capacidade,
                                            List<DisponibilidadeDtos.MotivoDisponibilidade> motivos,
                                            List<DisponibilidadeDtos.MotivoDisponibilidade> alertas) {
        if (capacidade.capacidadeEstimada() == null) {
            return;
        }
        boolean possuiBloqueio = motivos.stream().anyMatch(DisponibilidadeDtos.MotivoDisponibilidade::bloqueante);
        if (possuiBloqueio) {
            return;
        }
        int limite = variacao.getLimiteAlertaCapacidade() == null
                ? limiteAlertaPadrao
                : variacao.getLimiteAlertaCapacidade();
        if (capacidade.capacidadeEstimada() <= limite) {
            alertas.add(new DisponibilidadeDtos.MotivoDisponibilidade(
                    "CAPACIDADE_BAIXA",
                    SeveridadeDisponibilidade.ALERTA,
                    capacidade.ingredienteLimitantePrincipal() == null ? null : capacidade.ingredienteLimitantePrincipal().produtoId(),
                    capacidade.ingredienteLimitantePrincipal() == null ? null : capacidade.ingredienteLimitantePrincipal().produto(),
                    capacidade.ingredienteLimitantePrincipal() == null ? null : capacidade.ingredienteLimitantePrincipal().quantidadeNecessariaPorUnidade(),
                    capacidade.ingredienteLimitantePrincipal() == null ? null : capacidade.ingredienteLimitantePrincipal().quantidadeDisponivel(),
                    capacidade.ingredienteLimitantePrincipal() == null ? null : capacidade.ingredienteLimitantePrincipal().unidade(),
                    "Restam ingredientes para aproximadamente " + capacidade.capacidadeEstimada() + " unidades.",
                    false
            ));
        }
    }

    private EstadoDisponibilidade estadoPrincipal(boolean disponivel,
                                                  List<DisponibilidadeDtos.MotivoDisponibilidade> motivos,
                                                  List<DisponibilidadeDtos.MotivoDisponibilidade> alertas) {
        if (disponivel) {
            return alertas.isEmpty() ? EstadoDisponibilidade.DISPONIVEL : EstadoDisponibilidade.DISPONIVEL_COM_ALERTA;
        }
        Set<String> codigos = motivos.stream()
                .filter(DisponibilidadeDtos.MotivoDisponibilidade::bloqueante)
                .map(DisponibilidadeDtos.MotivoDisponibilidade::codigo)
                .collect(Collectors.toSet());
        if (contemAlgum(codigos, "ITEM_INATIVO", "CATEGORIA_INATIVA", "VARIACAO_INATIVA")) {
            return EstadoDisponibilidade.INATIVO;
        }
        if (contemAlgum(codigos, "ITEM_PAUSADO", "VARIACAO_PAUSADA")) {
            return EstadoDisponibilidade.PAUSADO;
        }
        if (contemAlgum(codigos, "FORA_DO_HORARIO", "FORA_DO_DIA_PERMITIDO")) {
            return EstadoDisponibilidade.FORA_DO_HORARIO;
        }
        if (codigos.contains("CANAL_NAO_PERMITIDO")) {
            return EstadoDisponibilidade.CANAL_NAO_PERMITIDO;
        }
        if (contemAlgum(codigos, "FICHA_TECNICA_NAO_ENCONTRADA", "FICHA_SEM_VERSAO_PUBLICADA")) {
            return EstadoDisponibilidade.SEM_FICHA_PUBLICADA;
        }
        if (codigos.contains("ESTOQUE_INSUFICIENTE")) {
            return EstadoDisponibilidade.ESTOQUE_INSUFICIENTE;
        }
        return EstadoDisponibilidade.CONFIGURACAO_INCOMPLETA;
    }

    private static boolean contemAlgum(Set<String> codigos, String... candidatos) {
        for (String candidato : candidatos) {
            if (codigos.contains(candidato)) {
                return true;
            }
        }
        return false;
    }

    private static DisponibilidadeDtos.MotivoDisponibilidade bloqueio(String codigo, String mensagem) {
        return new DisponibilidadeDtos.MotivoDisponibilidade(
                codigo,
                SeveridadeDisponibilidade.BLOQUEIO,
                null,
                null,
                null,
                null,
                null,
                mensagem,
                true
        );
    }

    private static DisponibilidadeDtos.MotivoDisponibilidade motivoProduto(String codigo,
                                                                           SeveridadeDisponibilidade severidade,
                                                                           Produto produto,
                                                                           UnidadeMedida unidade,
                                                                           BigDecimal necessaria,
                                                                           BigDecimal disponivel,
                                                                           String mensagem,
                                                                           boolean bloqueante) {
        return new DisponibilidadeDtos.MotivoDisponibilidade(
                codigo,
                severidade,
                produto == null ? null : produto.getId(),
                produto == null ? null : produto.getNome(),
                escala(necessaria),
                escala(disponivel),
                codigoUnidade(unidade),
                mensagem,
                bloqueante
        );
    }

    private static SeveridadeDisponibilidade severidade(PoliticaFalta politica) {
        return switch (politica) {
            case BLOQUEAR -> SeveridadeDisponibilidade.BLOQUEIO;
            case ALERTAR -> SeveridadeDisponibilidade.ALERTA;
            case IGNORAR -> SeveridadeDisponibilidade.INFO;
        };
    }

    private static String codigoUnidade(UnidadeMedida unidade) {
        return unidade == null ? null : unidade.getCodigo();
    }

    private static BigDecimal escala(BigDecimal valor) {
        return valor == null ? null : valor.setScale(ESCALA_QUANTIDADE, RoundingMode.HALF_UP);
    }

    private static int inteiroSeguro(BigDecimal valor) {
        if (valor == null) {
            return 0;
        }
        BigDecimal maximo = BigDecimal.valueOf(Integer.MAX_VALUE);
        if (valor.compareTo(maximo) > 0) {
            return Integer.MAX_VALUE;
        }
        return Math.max(0, valor.intValue());
    }

    private static void validarConsulta(Long filialId, CanalVenda canal) {
        if (filialId == null || filialId <= 0) {
            throw new ErroNegocioException("FILIAL_INVALIDA", "Filial da consulta e obrigatoria.");
        }
        if (canal == null) {
            throw new ErroNegocioException("CANAL_INVALIDO", "Canal da consulta e obrigatorio.");
        }
    }

    private record ContextoDisponibilidade(
            Long filialId,
            CanalVenda canal,
            LocalDateTime agora,
            List<VariacaoItemCardapio> variacoes,
            Map<Long, ResolucaoComposicaoVariacaoService.ResolucaoComposicao> resolucaoPorVariacao,
            Map<Integer, EstoqueProduto> estoquePorProduto,
            List<PausaCardapio> pausasAtivas,
            List<RegraHorarioCardapio> regrasHorario,
            Map<Long, List<VariacaoCanalDisponibilidade>> regrasCanalPorVariacao,
            Map<Long, List<DisponibilidadeDtos.MotivoDisponibilidade>> motivosGruposObrigatoriosPorVariacao
    ) {
        static ContextoDisponibilidade vazio(Long filialId, CanalVenda canal, LocalDateTime agora) {
            return new ContextoDisponibilidade(
                    filialId,
                    canal,
                    agora,
                    List.of(),
                    Map.of(),
                    Map.of(),
                    List.of(),
                    List.of(),
                    Map.of(),
                    Map.of()
            );
        }
    }

    private record AvaliacaoVariacao(
            DisponibilidadeDtos.ResultadoDisponibilidade resultado,
            List<DisponibilidadeDtos.ProdutoDiagnostico> produtos
    ) {
    }

    private record ComponenteDisponibilidade(
            Produto produto,
            UnidadeMedida unidadeBase,
            BigDecimal quantidadeBasePorUnidade,
            PoliticaFalta politicaFalta,
            OrigemComposicaoVenda origem
    ) {
        ComponenteDisponibilidade somar(BigDecimal quantidade, PoliticaFalta politica) {
            return new ComponenteDisponibilidade(
                    produto,
                    unidadeBase,
                    quantidadeBasePorUnidade.add(quantidade).setScale(ESCALA_QUANTIDADE, RoundingMode.HALF_UP),
                    politicaMaisRestritiva(politicaFalta, politica),
                    origem
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
