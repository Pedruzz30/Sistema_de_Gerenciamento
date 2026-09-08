package service;

import model.EstoqueProduto;
import model.LogAcao;
import model.MovimentoEstoque;
import model.Permissao;
import model.Produto;
import model.RegistroIdempotencia;
import model.TipoMovimentoEstoque;
import model.Usuario;
import model.Venda;
import model.VendaItem;
import model.VendaItemConsumo;
import model.VendaItemModificador;
import model.VendaItemModificadorImpacto;
import model.VendaItemParte;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import repository.EstoqueProdutoRepository;
import repository.LogRepository;
import repository.MovimentoEstoqueRepository;
import repository.RegistroIdempotenciaRepository;
import repository.VendaItemConsumoRepository;
import repository.VendaItemModificadorImpactoRepository;
import repository.VendaItemRepository;
import repository.VendaItemParteRepository;
import repository.VendaRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Transactional(readOnly = true)
public class FinalizacaoVendaService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FinalizacaoVendaService.class);
    private static final String OPERACAO_FINALIZAR_VENDA = "FINALIZAR_VENDA";
    private static final int ESCALA_DINHEIRO = 4;

    private final VendaRepository vendaRepository;
    private final VendaItemRepository vendaItemRepository;
    private final VendaItemConsumoRepository consumoRepository;
    private final EstoqueProdutoRepository estoqueProdutoRepository;
    private final MovimentoEstoqueRepository movimentoRepository;
    private final RegistroIdempotenciaRepository idempotenciaRepository;
    private final VendaItemModificadorImpactoRepository modificadorImpactoRepository;
    private final VendaItemParteRepository vendaItemParteRepository;
    private final MontagemComposicaoVendaService montagemService;
    private final CalculadoraCustoVendaService calculadoraCusto;
    private final IntegracaoCaixaLegadoService integracaoCaixaLegadoService;
    private final LogRepository logRepository;

    public FinalizacaoVendaService(VendaRepository vendaRepository,
                                   VendaItemRepository vendaItemRepository,
                                   VendaItemConsumoRepository consumoRepository,
                                   EstoqueProdutoRepository estoqueProdutoRepository,
                                   MovimentoEstoqueRepository movimentoRepository,
                                   RegistroIdempotenciaRepository idempotenciaRepository,
                                   VendaItemModificadorImpactoRepository modificadorImpactoRepository,
                                   VendaItemParteRepository vendaItemParteRepository,
                                   MontagemComposicaoVendaService montagemService,
                                   CalculadoraCustoVendaService calculadoraCusto,
                                   IntegracaoCaixaLegadoService integracaoCaixaLegadoService,
                                   LogRepository logRepository) {
        this.vendaRepository = vendaRepository;
        this.vendaItemRepository = vendaItemRepository;
        this.consumoRepository = consumoRepository;
        this.estoqueProdutoRepository = estoqueProdutoRepository;
        this.movimentoRepository = movimentoRepository;
        this.idempotenciaRepository = idempotenciaRepository;
        this.modificadorImpactoRepository = modificadorImpactoRepository;
        this.vendaItemParteRepository = vendaItemParteRepository;
        this.montagemService = montagemService;
        this.calculadoraCusto = calculadoraCusto;
        this.integracaoCaixaLegadoService = integracaoCaixaLegadoService;
        this.logRepository = logRepository;
    }

    @Transactional
    public Venda finalizar(Usuario usuario,
                           long vendaId,
                           String idempotencyKey,
                           Integer numeroCaixaLegado) {
        validarPermissaoVenda(usuario);
        String chave = normalizarIdempotencyKey(idempotencyKey);
        Venda venda = vendaRepository.buscarPorIdComLock(vendaId)
                .orElseThrow(() -> new ErroNegocioException("VENDA_NAO_ENCONTRADA", "Venda nao encontrada."));
        List<VendaItem> itens = vendaItemRepository.listarAtivosPorVenda(venda.getId());
        String payloadHash = calcularPayloadHash(criarPayloadFinalizacao(venda, itens, numeroCaixaLegado));

        LOGGER.info(
                "Iniciando finalizacao de venda. venda={} filial={} idempotencyKey={} itens={} caixaLegado={}",
                venda.getId(),
                venda.getFilialId(),
                chave,
                itens.size(),
                numeroCaixaLegado
        );

        RegistroIdempotencia registroExistente = idempotenciaRepository
                .buscar(venda.getFilialId(), OPERACAO_FINALIZAR_VENDA, chave)
                .orElse(null);
        if (registroExistente != null) {
            return resolverRegistroIdempotente(registroExistente, payloadHash);
        }
        RegistroIdempotencia registro = reservarIdempotencia(venda.getFilialId(), chave, payloadHash, venda.getId());

        try {
            venda.exigirRascunho();
        } catch (IllegalStateException e) {
            throw new ErroNegocioException("VENDA_JA_FINALIZADA", "Venda nao pode ser finalizada novamente.");
        }

        if (itens.isEmpty()) {
            throw new ErroNegocioException("VENDA_SEM_ITENS", "Venda precisa de pelo menos um item ativo.");
        }

        long inicioCalculo = System.nanoTime();
        List<MontagemComposicaoVendaService.ItemMontado> montados = itens.stream()
                .map(montagemService::montar)
                .toList();
        validarRascunhoContraEstadoAtual(montados);
        Map<Integer, BigDecimal> quantidadePorProduto = consolidarQuantidades(montados);
        long inicioLock = System.nanoTime();
        Map<Integer, EstoqueProduto> estoques = bloquearEstoques(venda.getFilialId(), quantidadePorProduto);
        long fimLock = System.nanoTime();
        validarEstoques(quantidadePorProduto, estoques);
        LOGGER.info(
                "Composicao de venda calculada. venda={} filial={} produtosConsolidados={} tempoCalculoMs={} tempoLockMs={}",
                venda.getId(),
                venda.getFilialId(),
                quantidadePorProduto.size(),
                (inicioLock - inicioCalculo) / 1_000_000,
                (fimLock - inicioLock) / 1_000_000
        );

        List<VendaItemConsumo> consumos = new ArrayList<>();
        Map<Long, BigDecimal> custoTotalPorItem = new LinkedHashMap<>();
        Map<Long, Map<Long, BigDecimal>> custoTotalPorPartePorItem = new LinkedHashMap<>();
        Map<Long, MontagemComposicaoVendaService.ItemMontado> montadoPorItem = new LinkedHashMap<>();
        for (MontagemComposicaoVendaService.ItemMontado montado : montados) {
            BigDecimal custoItem = dinheiro(BigDecimal.ZERO);
            Map<Long, BigDecimal> custoPorParte = new LinkedHashMap<>();
            for (MontagemComposicaoVendaService.ConsumoPlanejado planejado : montado.consumos()) {
                EstoqueProduto estoque = estoques.get(planejado.produto().getId());
                VendaItemConsumo consumo = calculadoraCusto.criarConsumo(montado, planejado, estoque);
                consumos.add(consumo);
                custoItem = custoItem.add(consumo.getCustoTotalSnapshot()).setScale(ESCALA_DINHEIRO, RoundingMode.HALF_UP);
                if (planejado.vendaItemParte() != null) {
                    custoPorParte.merge(
                            planejado.vendaItemParte().getId(),
                            consumo.getCustoTotalSnapshot(),
                            (a, b) -> a.add(b).setScale(ESCALA_DINHEIRO, RoundingMode.HALF_UP)
                    );
                }
            }
            custoTotalPorItem.put(montado.item().getId(), custoItem);
            custoTotalPorPartePorItem.put(montado.item().getId(), custoPorParte);
            montadoPorItem.put(montado.item().getId(), montado);
        }

        Map<Integer, MovimentoEstoque> movimentosPorProduto = criarMovimentosSaida(
                venda,
                quantidadePorProduto,
                estoques,
                chave,
                usuario
        );

        BigDecimal subtotal = dinheiro(BigDecimal.ZERO);
        BigDecimal descontoTotal = dinheiro(BigDecimal.ZERO);
        BigDecimal acrescimoTotal = dinheiro(BigDecimal.ZERO);
        BigDecimal custoTotalVenda = dinheiro(BigDecimal.ZERO);
        BigDecimal margemTotalVenda = dinheiro(BigDecimal.ZERO);
        for (VendaItem item : itens) {
            MontagemComposicaoVendaService.ItemMontado montado = montadoPorItem.get(item.getId());
            BigDecimal custoItem = custoTotalPorItem.getOrDefault(item.getId(), dinheiro(BigDecimal.ZERO));
            BigDecimal acrescimoUnitarioOficial = montado.modificadores().stream()
                    .map(VendaItemModificador::getAcrescimoTotalSnapshot)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(ESCALA_DINHEIRO, RoundingMode.HALF_UP);
            item.aplicarSnapshotComercialComPrecoBase(
                    item.getVariacaoItemCardapio(),
                    montado.precoUnitarioBaseOficial(),
                    item.getDescontoUnitario(),
                    acrescimoUnitarioOficial
            );
            BigDecimal margemItem = calculadoraCusto.margemBruta(item.getSubtotal(), custoItem);
            item.aplicarCusto(
                    montado.fichaTecnica(),
                    montado.fichaTecnicaVersao(),
                    montado.numeroVersaoFicha(),
                    custoItem,
                    margemItem
            );
            vendaItemRepository.salvar(item);
            atualizarCustosDasPartes(montado, custoTotalPorPartePorItem.getOrDefault(item.getId(), Map.of()));
            BigDecimal subtotalBaseItem = item.getPrecoUnitarioBase()
                    .multiply(item.getQuantidade())
                    .setScale(ESCALA_DINHEIRO, RoundingMode.HALF_UP);
            subtotal = subtotal.add(subtotalBaseItem).setScale(ESCALA_DINHEIRO, RoundingMode.HALF_UP);
            descontoTotal = descontoTotal.add(item.getDescontoUnitario().multiply(item.getQuantidade())).setScale(ESCALA_DINHEIRO, RoundingMode.HALF_UP);
            acrescimoTotal = acrescimoTotal.add(item.getAcrescimoUnitario().multiply(item.getQuantidade())).setScale(ESCALA_DINHEIRO, RoundingMode.HALF_UP);
            custoTotalVenda = custoTotalVenda.add(custoItem).setScale(ESCALA_DINHEIRO, RoundingMode.HALF_UP);
            margemTotalVenda = margemTotalVenda.add(margemItem).setScale(ESCALA_DINHEIRO, RoundingMode.HALF_UP);
        }

        for (VendaItemConsumo consumo : consumos) {
            MovimentoEstoque movimento = movimentosPorProduto.get(consumo.getProduto().getId());
            consumo.vincularMovimento(movimento);
            consumoRepository.salvar(consumo);
        }
        registrarImpactosModificadores(montados, estoques);

        BigDecimal valorTotal = subtotal.subtract(descontoTotal).add(acrescimoTotal).setScale(ESCALA_DINHEIRO, RoundingMode.HALF_UP);
        venda.finalizar(
                subtotal,
                descontoTotal,
                acrescimoTotal,
                valorTotal,
                custoTotalVenda,
                margemTotalVenda,
                chave,
                numeroCaixaLegado,
                usuario == null ? null : usuario.getRu()
        );
        venda = vendaRepository.salvar(venda);
        integracaoCaixaLegadoService.registrarVendaConsolidada(usuario, numeroCaixaLegado, venda.getId(), valorTotal);
        registro.concluir(venda.getId());
        idempotenciaRepository.salvar(registro);
        auditar(usuario, "VENDA_FINALIZADA", "Venda " + venda.getId() + " finalizada com baixa decimal.");
        LOGGER.info(
                "Venda finalizada com sucesso. venda={} filial={} idempotencyKey={} valorTotal={} custoTotal={}",
                venda.getId(),
                venda.getFilialId(),
                chave,
                valorTotal,
                custoTotalVenda
        );
        return buscarFinalizada(venda.getId());
    }

    private void atualizarCustosDasPartes(MontagemComposicaoVendaService.ItemMontado montado,
                                          Map<Long, BigDecimal> custoPorParte) {
        if (!montado.composicaoMultipla()) {
            return;
        }
        for (VendaItemParte parte : montado.partes()) {
            parte.atualizarCustoReferencia(custoPorParte.getOrDefault(parte.getId(), dinheiro(BigDecimal.ZERO)));
            vendaItemParteRepository.salvar(parte);
        }
    }

    private void registrarImpactosModificadores(List<MontagemComposicaoVendaService.ItemMontado> montados,
                                                Map<Integer, EstoqueProduto> estoques) {
        for (MontagemComposicaoVendaService.ItemMontado montado : montados) {
            Map<Long, VendaItemModificador> modificadorPorOpcao = montado.modificadores().stream()
                    .collect(java.util.stream.Collectors.toMap(
                            modificador -> modificador.getOpcaoModificador().getId(),
                            java.util.function.Function.identity(),
                            (a, b) -> a,
                            LinkedHashMap::new
                    ));
            for (MontagemConfiguracaoItemService.ImpactoPlanejado impacto : montado.impactos()) {
                VendaItemModificador modificador = modificadorPorOpcao.get(impacto.selecao().opcao().getId());
                if (modificador == null) {
                    continue;
                }
                EstoqueProduto estoque = estoques.get(impacto.produto().getId());
                BigDecimal custoUnitario = estoque == null || estoque.getCustoMedio() == null
                        ? BigDecimal.ZERO
                        : estoque.getCustoMedio().setScale(8, RoundingMode.HALF_UP);
                BigDecimal quantidadeTotal = impacto.quantidadeDeltaBase()
                        .multiply(montado.item().getQuantidade())
                        .setScale(6, RoundingMode.HALF_UP);
                BigDecimal valorImpacto = quantidadeTotal
                        .multiply(custoUnitario)
                        .setScale(ESCALA_DINHEIRO, RoundingMode.HALF_UP);
                modificadorImpactoRepository.salvar(new VendaItemModificadorImpacto(
                        0,
                        modificador,
                        null,
                        impacto.produto(),
                        impacto.tipoEfeito(),
                        quantidadeTotal,
                        custoUnitario,
                        valorImpacto
                ));
            }
        }
    }

    private Map<Integer, BigDecimal> consolidarQuantidades(List<MontagemComposicaoVendaService.ItemMontado> montados) {
        Map<Integer, BigDecimal> quantidadePorProduto = new TreeMap<>();
        for (MontagemComposicaoVendaService.ItemMontado montado : montados) {
            for (MontagemComposicaoVendaService.ConsumoPlanejado consumo : montado.consumos()) {
                quantidadePorProduto.merge(
                        consumo.produto().getId(),
                        consumo.quantidadeBaseTotal(),
                        (a, b) -> a.add(b).setScale(6, RoundingMode.HALF_UP)
                );
            }
        }
        return quantidadePorProduto;
    }

    private void validarRascunhoContraEstadoAtual(List<MontagemComposicaoVendaService.ItemMontado> montados) {
        for (MontagemComposicaoVendaService.ItemMontado montado : montados) {
            VendaItem item = montado.item();
            BigDecimal precoRascunho = dinheiro(item.getPrecoUnitarioBase());
            BigDecimal precoAtual = dinheiro(montado.precoUnitarioBaseOficial());
            if (precoRascunho.compareTo(precoAtual) != 0) {
                throw new ErroNegocioException(
                        item.isComposicaoMultipla() ? "CONFIGURACAO_MULTISSABOR_ALTERADA" : "PRECO_VARIACAO_ALTERADO",
                        item.isComposicaoMultipla()
                                ? "Preco oficial da composicao multissabor foi alterado desde a inclusao no rascunho."
                                : "Preco da variacao foi alterado desde a inclusao no rascunho.",
                        Map.of(
                                "variacaoId", item.getVariacaoItemCardapio().getId(),
                                "precoAnterior", precoRascunho,
                                "precoAtual", precoAtual
                        )
                );
            }

            if (montado.fichaTecnicaVersao() == null || item.getFichaTecnicaVersaoObservada() == null) {
                continue;
            }
            long versaoObservada = item.getFichaTecnicaVersaoObservada().getId();
            long versaoAtual = montado.fichaTecnicaVersao().getId();
            if (versaoObservada != versaoAtual) {
                throw new ErroNegocioException(
                        "FICHA_PUBLICADA_ALTERADA",
                        "Versao publicada da ficha tecnica foi alterada desde a inclusao no rascunho.",
                        Map.of(
                                "variacaoId", item.getVariacaoItemCardapio().getId(),
                                "fichaTecnicaId", montado.fichaTecnica().getId(),
                                "versaoObservadaId", versaoObservada,
                                "numeroVersaoObservada", item.getNumeroVersaoFichaObservada(),
                                "versaoAtualId", versaoAtual,
                                "numeroVersaoAtual", montado.numeroVersaoFicha()
                        )
                );
            }
        }
    }

    private Map<Integer, EstoqueProduto> bloquearEstoques(Long filialId, Map<Integer, BigDecimal> quantidadePorProduto) {
        Map<Integer, EstoqueProduto> estoques = new LinkedHashMap<>();
        quantidadePorProduto.keySet().stream().sorted().forEach(produtoId -> {
            EstoqueProduto estoque = estoqueProdutoRepository.buscarPorFilialEProdutoComLock(filialId, produtoId)
                    .orElseThrow(() -> new ErroNegocioException(
                            "ESTOQUE_PRODUTO_NAO_CONFIGURADO",
                            "Produto sem estoque decimal configurado para a filial.",
                            Map.of("produtoId", produtoId, "filialId", filialId)
                    ));
            estoques.put(produtoId, estoque);
        });
        return estoques;
    }

    private void validarEstoques(Map<Integer, BigDecimal> quantidadePorProduto,
                                 Map<Integer, EstoqueProduto> estoques) {
        for (Map.Entry<Integer, BigDecimal> entry : quantidadePorProduto.entrySet()) {
            EstoqueProduto estoque = estoques.get(entry.getKey());
            Produto produto = estoque.getProduto();
            BigDecimal necessaria = entry.getValue();
            if (estoque.getCustoMedio() == null || estoque.getCustoMedio().compareTo(BigDecimal.ZERO) <= 0) {
                throw new ErroNegocioException(
                        "CUSTO_MEDIO_INDISPONIVEL",
                        "Produto sem custo medio valido para venda.",
                        Map.of("produtoId", produto.getId(), "produto", produto.getNome())
                );
            }
            BigDecimal disponivel = estoque.quantidadeDisponivel();
            if (disponivel.compareTo(necessaria) < 0) {
                Map<String, Object> detalhes = new LinkedHashMap<>();
                detalhes.put("produtoId", produto.getId());
                detalhes.put("produto", produto.getNome());
                detalhes.put("quantidadeNecessaria", necessaria);
                detalhes.put("quantidadeDisponivel", disponivel);
                detalhes.put("unidade", produto.getUnidadeBase() == null ? null : produto.getUnidadeBase().getCodigo());
                LOGGER.warn(
                        "Estoque insuficiente na finalizacao. produto={} necessario={} disponivel={}",
                        produto.getId(),
                        necessaria,
                        disponivel
                );
                throw new ErroNegocioException(
                        "ESTOQUE_INSUFICIENTE",
                        "Estoque insuficiente para finalizar a venda.",
                        detalhes
                );
            }
        }
    }

    private Map<Integer, MovimentoEstoque> criarMovimentosSaida(Venda venda,
                                                                Map<Integer, BigDecimal> quantidadePorProduto,
                                                                Map<Integer, EstoqueProduto> estoques,
                                                                String chave,
                                                                Usuario usuario) {
        Map<Integer, MovimentoEstoque> movimentos = new LinkedHashMap<>();
        for (Integer produtoId : quantidadePorProduto.keySet().stream().sorted().toList()) {
            BigDecimal quantidade = quantidadePorProduto.get(produtoId);
            EstoqueProduto estoque = estoques.get(produtoId);
            BigDecimal saldoAnterior = estoque.getQuantidadeAtual();
            BigDecimal custoMedioAnterior = estoque.getCustoMedio();
            BigDecimal valorAnterior = estoque.valorEstoque();
            BigDecimal custoTotal = quantidade.multiply(custoMedioAnterior).setScale(ESCALA_DINHEIRO, RoundingMode.HALF_UP);
            estoque.aplicarSaida(quantidade, custoMedioAnterior, true);
            BigDecimal saldoPosterior = estoque.getQuantidadeAtual();
            BigDecimal custoMedioPosterior = estoque.getCustoMedio();
            BigDecimal valorPosterior = estoque.valorEstoque();
            estoqueProdutoRepository.salvar(estoque);
            MovimentoEstoque movimento = movimentoRepository.salvar(new MovimentoEstoque(
                    0,
                    venda.getFilialId(),
                    estoque.getProduto(),
                    TipoMovimentoEstoque.SAIDA_VENDA,
                    quantidade,
                    saldoAnterior,
                    saldoPosterior,
                    custoMedioAnterior,
                    custoTotal,
                    custoMedioAnterior,
                    custoMedioPosterior,
                    valorAnterior,
                    valorPosterior,
                    "VENDA",
                    venda.getId(),
                    usuario == null ? null : usuario.getRu(),
                    "Saida por venda #" + venda.getId(),
                    null,
                    chave,
                    null
            ));
            movimentos.put(produtoId, movimento);
        }
        return movimentos;
    }

    private Venda resolverRegistroIdempotente(RegistroIdempotencia registro, String payloadHash) {
        if (!registro.mesmoPayload(payloadHash)) {
            throw new ConflitoIdempotenciaException("Mesma chave de idempotencia foi reutilizada com payload diferente.");
        }
        if (registro.estaProcessando()) {
            throw new ErroNegocioException(
                    "VENDA_EM_PROCESSAMENTO",
                    "Operacao idempotente ainda esta em processamento. Reenvie a mesma requisicao em instantes."
            );
        }
        return vendaRepository.buscarPorId(registro.getRecursoId())
                .orElseThrow(() -> new IllegalStateException("Venda idempotente nao encontrada."));
    }

    private RegistroIdempotencia reservarIdempotencia(Long filialId, String chave, String payloadHash, long vendaId) {
        try {
            return idempotenciaRepository.salvarEFlush(RegistroIdempotencia.iniciar(
                    filialId,
                    OPERACAO_FINALIZAR_VENDA,
                    chave,
                    payloadHash,
                    vendaId
            ));
        } catch (DataIntegrityViolationException e) {
            throw new ErroNegocioException(
                    "VENDA_EM_PROCESSAMENTO",
                    "A chave de idempotencia foi registrada por outra transacao concorrente. Reenvie a mesma requisicao."
            );
        }
    }

    private Venda buscarFinalizada(long vendaId) {
        return vendaRepository.buscarPorId(vendaId)
                .orElseThrow(() -> new IllegalStateException("Venda finalizada nao encontrada."));
    }

    private void validarPermissaoVenda(Usuario usuario) {
        if (usuario == null
                || usuario.getClasse() == null
                || !usuario.getClasse().possuiPermissao(Permissao.VER_VENDAS)) {
            throw new SecurityException("Voce nao tem permissao para finalizar venda.");
        }
    }

    private static String normalizarIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key e obrigatoria para finalizar venda.");
        }
        return idempotencyKey.trim();
    }

    private static String criarPayloadFinalizacao(Venda venda, List<VendaItem> itens, Integer numeroCaixaLegado) {
        StringBuilder payload = new StringBuilder()
                .append("operacao=").append(OPERACAO_FINALIZAR_VENDA)
                .append("|venda=").append(venda.getId())
                .append("|filial=").append(venda.getFilialId())
                .append("|caixa=").append(numeroCaixaLegado == null ? "" : numeroCaixaLegado);
        itens.stream()
                .sorted(Comparator.comparingLong(VendaItem::getId))
                .forEach(item -> payload
                        .append("|item=").append(item.getId())
                        .append(",variacao=").append(item.getVariacaoItemCardapio().getId())
                        .append(",qtd=").append(item.getQuantidade())
                        .append(",preco=").append(item.getPrecoUnitarioBase())
                        .append(",fichaObs=").append(item.getFichaTecnicaVersaoObservada() == null ? "" : item.getFichaTecnicaVersaoObservada().getId())
                        .append(",modHash=").append(item.getConfiguracaoModificadoresHashObservada() == null ? "" : item.getConfiguracaoModificadoresHashObservada())
                        .append(",multi=").append(item.isComposicaoMultipla())
                        .append(",multiHash=").append(item.getConfiguracaoMultissaborHashObservada() == null ? "" : item.getConfiguracaoMultissaborHashObservada())
                        .append(",partes=").append(item.getResumoPartesSnapshot() == null ? "" : item.getResumoPartesSnapshot()));
        return payload.toString();
    }

    private static String calcularPayloadHash(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel para idempotencia.", e);
        }
    }

    private static BigDecimal dinheiro(BigDecimal valor) {
        return (valor == null ? BigDecimal.ZERO : valor).setScale(ESCALA_DINHEIRO, RoundingMode.HALF_UP);
    }

    private void auditar(Usuario usuario, String acao, String descricao) {
        logRepository.salvar(new LogAcao(0, usuario == null ? null : usuario.getRu(), acao, descricao));
    }
}
