package service;

import model.EstoqueProduto;
import model.LogAcao;
import model.MovimentoEstoque;
import model.Permissao;
import model.RegistroIdempotencia;
import model.StatusVenda;
import model.StatusVendaItem;
import model.TipoMovimentoEstoque;
import model.Usuario;
import model.Venda;
import model.VendaItem;
import model.VendaItemConsumo;
import model.VendaItemConsumoEstorno;
import model.VendaItemParte;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import repository.EstoqueProdutoRepository;
import repository.LogRepository;
import repository.MovimentoEstoqueRepository;
import repository.RegistroIdempotenciaRepository;
import repository.VendaItemConsumoEstornoRepository;
import repository.VendaItemConsumoRepository;
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

@Transactional(readOnly = true)
public class CancelamentoVendaService {

    private static final String OPERACAO_CANCELAR_VENDA = "CANCELAR_VENDA";
    private static final String OPERACAO_CANCELAR_ITEM_VENDA = "CANCELAR_ITEM_VENDA";
    private static final int ESCALA_DINHEIRO = 4;

    private final VendaRepository vendaRepository;
    private final VendaItemRepository itemRepository;
    private final VendaItemConsumoRepository consumoRepository;
    private final VendaItemConsumoEstornoRepository consumoEstornoRepository;
    private final VendaItemParteRepository vendaItemParteRepository;
    private final EstoqueProdutoRepository estoqueRepository;
    private final MovimentoEstoqueRepository movimentoRepository;
    private final RegistroIdempotenciaRepository idempotenciaRepository;
    private final IntegracaoCaixaLegadoService integracaoCaixaLegadoService;
    private final LogRepository logRepository;

    public CancelamentoVendaService(VendaRepository vendaRepository,
                                    VendaItemRepository itemRepository,
                                    VendaItemConsumoRepository consumoRepository,
                                    VendaItemConsumoEstornoRepository consumoEstornoRepository,
                                    VendaItemParteRepository vendaItemParteRepository,
                                    EstoqueProdutoRepository estoqueRepository,
                                    MovimentoEstoqueRepository movimentoRepository,
                                    RegistroIdempotenciaRepository idempotenciaRepository,
                                    IntegracaoCaixaLegadoService integracaoCaixaLegadoService,
                                    LogRepository logRepository) {
        this.vendaRepository = vendaRepository;
        this.itemRepository = itemRepository;
        this.consumoRepository = consumoRepository;
        this.consumoEstornoRepository = consumoEstornoRepository;
        this.vendaItemParteRepository = vendaItemParteRepository;
        this.estoqueRepository = estoqueRepository;
        this.movimentoRepository = movimentoRepository;
        this.idempotenciaRepository = idempotenciaRepository;
        this.integracaoCaixaLegadoService = integracaoCaixaLegadoService;
        this.logRepository = logRepository;
    }

    @Transactional
    public Venda cancelarVenda(Usuario usuario, long vendaId, String motivo, String idempotencyKey) {
        validarPermissaoCancelamento(usuario);
        validarMotivo(motivo);
        String chave = normalizarIdempotencyKey(idempotencyKey, "cancelar venda");
        Venda venda = vendaRepository.buscarPorIdComLock(vendaId)
                .orElseThrow(() -> new ErroNegocioException("VENDA_NAO_ENCONTRADA", "Venda nao encontrada."));
        String payloadHash = calcularPayloadHash("venda=" + vendaId + "|motivo=" + motivo.trim());
        RegistroIdempotencia existente = idempotenciaRepository
                .buscar(venda.getFilialId(), OPERACAO_CANCELAR_VENDA, chave)
                .orElse(null);
        if (existente != null) {
            return resolverRegistroIdempotente(existente, payloadHash);
        }
        RegistroIdempotencia registro = reservarIdempotencia(venda.getFilialId(), OPERACAO_CANCELAR_VENDA, chave, payloadHash, venda.getId());

        if (venda.getStatus() == StatusVenda.CANCELADA) {
            throw new ErroNegocioException("VENDA_JA_CANCELADA", "Venda ja esta cancelada.");
        }
        if (venda.getStatus() != StatusVenda.FINALIZADA && venda.getStatus() != StatusVenda.PARCIALMENTE_CANCELADA) {
            throw new ErroNegocioException("CANCELAMENTO_NAO_PERMITIDO", "Apenas venda finalizada pode ser cancelada.");
        }

        List<VendaItem> itensAtivos = itemRepository.listarAtivosPorVenda(venda.getId());
        if (itensAtivos.isEmpty()) {
            throw new ErroNegocioException("VENDA_JA_CANCELADA", "Venda nao possui itens ativos para cancelar.");
        }
        ResultadoCancelamento resultado = cancelarItensAtivos(venda, itensAtivos, usuario, motivo, chave);
        venda.cancelar(usuario == null ? null : usuario.getRu(), motivo);
        venda = vendaRepository.salvar(venda);
        integracaoCaixaLegadoService.registrarCancelamentoConsolidado(
                usuario,
                venda.getNumeroCaixaLegado(),
                venda.getId(),
                resultado.valorCancelado()
        );
        registro.concluir(venda.getId());
        idempotenciaRepository.salvar(registro);
        auditar(usuario, "VENDA_CANCELADA", "Venda " + venda.getId() + " cancelada.");
        return vendaRepository.buscarPorId(venda.getId()).orElseThrow();
    }

    @Transactional
    public Venda cancelarItem(Usuario usuario,
                              long vendaId,
                              long itemId,
                              String motivo,
                              String idempotencyKey) {
        validarPermissaoCancelamento(usuario);
        validarMotivo(motivo);
        String chave = normalizarIdempotencyKey(idempotencyKey, "cancelar item de venda");
        Venda venda = vendaRepository.buscarPorIdComLock(vendaId)
                .orElseThrow(() -> new ErroNegocioException("VENDA_NAO_ENCONTRADA", "Venda nao encontrada."));
        String payloadHash = calcularPayloadHash("venda=" + vendaId + "|item=" + itemId + "|motivo=" + motivo.trim());
        RegistroIdempotencia existente = idempotenciaRepository
                .buscar(venda.getFilialId(), OPERACAO_CANCELAR_ITEM_VENDA, chave)
                .orElse(null);
        if (existente != null) {
            return resolverRegistroIdempotente(existente, payloadHash);
        }
        RegistroIdempotencia registro = reservarIdempotencia(venda.getFilialId(), OPERACAO_CANCELAR_ITEM_VENDA, chave, payloadHash, venda.getId());

        if (venda.getStatus() == StatusVenda.CANCELADA) {
            throw new ErroNegocioException("VENDA_JA_CANCELADA", "Venda ja esta cancelada.");
        }
        if (venda.getStatus() != StatusVenda.FINALIZADA && venda.getStatus() != StatusVenda.PARCIALMENTE_CANCELADA) {
            throw new ErroNegocioException("CANCELAMENTO_NAO_PERMITIDO", "Apenas venda finalizada pode ter item cancelado.");
        }

        VendaItem item = itemRepository.buscarPorId(itemId)
                .orElseThrow(() -> new ErroNegocioException("ITEM_VENDA_NAO_ENCONTRADO", "Item da venda nao encontrado."));
        if (item.getVenda().getId() != venda.getId()) {
            throw new ErroNegocioException("ITEM_VENDA_NAO_ENCONTRADO", "Item nao pertence a venda informada.");
        }
        if (item.getStatus() == StatusVendaItem.CANCELADO) {
            throw new ErroNegocioException("ITEM_VENDA_JA_CANCELADO", "Item da venda ja esta cancelado.");
        }

        ResultadoCancelamento resultado = cancelarItensAtivos(venda, List.of(item), usuario, motivo, chave);

        List<VendaItem> ativosRestantes = itemRepository.listarAtivosPorVenda(venda.getId()).stream()
                .filter(ativo -> ativo.getId() != item.getId())
                .toList();
        if (ativosRestantes.isEmpty()) {
            venda.cancelar(usuario == null ? null : usuario.getRu(), motivo);
        } else {
            venda.marcarParcialmenteCancelada(usuario == null ? null : usuario.getRu(), motivo);
        }
        venda = vendaRepository.salvar(venda);
        integracaoCaixaLegadoService.registrarCancelamentoConsolidado(
                usuario,
                venda.getNumeroCaixaLegado(),
                venda.getId(),
                resultado.valorCancelado()
        );
        registro.concluir(venda.getId());
        idempotenciaRepository.salvar(registro);
        auditar(usuario, "VENDA_ITEM_CANCELADO", "Item " + itemId + " cancelado da venda " + vendaId + ".");
        return vendaRepository.buscarPorId(venda.getId()).orElseThrow();
    }

    private ResultadoCancelamento cancelarItensAtivos(Venda venda,
                                                       List<VendaItem> itensAtivos,
                                                       Usuario usuario,
                                                       String motivo,
                                                       String chave) {
        if (itensAtivos == null || itensAtivos.isEmpty()) {
            throw new ErroNegocioException("VENDA_JA_CANCELADA", "Venda nao possui itens ativos para cancelar.");
        }

        List<Long> itemIds = itensAtivos.stream().map(VendaItem::getId).toList();
        List<VendaItemConsumo> consumos = consumoRepository.listarPorVendaItens(itemIds);
        criarEstornos(venda, consumos, usuario, motivo, chave);

        for (VendaItem item : itensAtivos) {
            item.cancelar(usuario == null ? null : usuario.getRu(), motivo);
            itemRepository.salvar(item);
            for (VendaItemParte parte : vendaItemParteRepository.listarPorItemEStatus(item.getId(), StatusVendaItem.ATIVO)) {
                parte.cancelar();
                vendaItemParteRepository.salvar(parte);
            }
        }

        BigDecimal valorCancelado = itensAtivos.stream()
                .map(VendaItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(ESCALA_DINHEIRO, RoundingMode.HALF_UP);
        return new ResultadoCancelamento(valorCancelado);
    }

    private void criarEstornos(Venda venda,
                               List<VendaItemConsumo> consumos,
                               Usuario usuario,
                               String motivo,
                               String chave) {
        List<ConsumoEstornar> consumosPendentes = consumosPendentes(consumos);
        if (consumosPendentes.isEmpty()) {
            throw new ErroNegocioException(
                    "MOVIMENTO_TOTALMENTE_ESTORNADO",
                    "Os consumos informados ja foram totalmente estornados."
            );
        }

        Map<Long, GrupoEstornoPlanejado> gruposPorMovimento = new LinkedHashMap<>();
        for (ConsumoEstornar consumoEstornar : consumosPendentes) {
            MovimentoEstoque original = consumoEstornar.consumo().getMovimentoEstoque();
            validarMovimentoOriginalDeVenda(original);
            gruposPorMovimento
                    .computeIfAbsent(original.getId(), id -> new GrupoEstornoPlanejado(original))
                    .adicionar(consumoEstornar);
        }

        List<GrupoEstornoPlanejado> grupos = new ArrayList<>(gruposPorMovimento.values());
        validarQuantidadesAindaEstornaveis(grupos);
        grupos.sort(Comparator.comparingInt(grupo -> grupo.original.getProduto().getId()));
        for (GrupoEstornoPlanejado grupo : grupos) {
            MovimentoEstoque estorno = criarMovimentoEstorno(venda, grupo, usuario, motivo, chave);
            for (ConsumoEstornar consumoEstornar : grupo.consumos) {
                BigDecimal valorVinculado = consumoEstornar.quantidade()
                        .multiply(grupo.original.getCustoUnitario())
                        .setScale(ESCALA_DINHEIRO, RoundingMode.HALF_UP);
                consumoEstornoRepository.salvar(new VendaItemConsumoEstorno(
                        0,
                        consumoEstornar.consumo(),
                        estorno,
                        consumoEstornar.quantidade(),
                        grupo.original.getCustoUnitario(),
                        valorVinculado
                ));
            }
        }
    }

    private List<ConsumoEstornar> consumosPendentes(List<VendaItemConsumo> consumos) {
        if (consumos == null || consumos.isEmpty()) {
            throw new ErroNegocioException("CANCELAMENTO_NAO_PERMITIDO", "Item nao possui consumos para estornar.");
        }
        List<Long> consumoIds = consumos.stream().map(VendaItemConsumo::getId).toList();
        Map<Long, BigDecimal> jaEstornadoPorConsumo = new LinkedHashMap<>();
        for (VendaItemConsumoEstorno estorno : consumoEstornoRepository.listarPorConsumos(consumoIds)) {
            jaEstornadoPorConsumo.merge(
                    estorno.getVendaItemConsumo().getId(),
                    estorno.getQuantidadeBaseEstornada(),
                    (a, b) -> a.add(b).setScale(EstoqueProduto.ESCALA_QUANTIDADE, RoundingMode.HALF_UP)
            );
        }

        List<ConsumoEstornar> pendentes = new ArrayList<>();
        for (VendaItemConsumo consumo : consumos) {
            if (consumo.getMovimentoEstoque() == null) {
                throw new ErroNegocioException(
                        "CANCELAMENTO_NAO_PERMITIDO",
                        "Consumo da venda nao possui movimento de estoque vinculado.",
                        Map.of("consumoId", consumo.getId())
                );
            }
            BigDecimal jaEstornado = jaEstornadoPorConsumo
                    .getOrDefault(consumo.getId(), BigDecimal.ZERO)
                    .setScale(EstoqueProduto.ESCALA_QUANTIDADE, RoundingMode.HALF_UP);
            BigDecimal pendente = consumo.getQuantidadeBaseTotal()
                    .subtract(jaEstornado)
                    .setScale(EstoqueProduto.ESCALA_QUANTIDADE, RoundingMode.HALF_UP);
            if (pendente.compareTo(BigDecimal.ZERO) < 0) {
                throw new ErroNegocioException(
                        "QUANTIDADE_ESTORNO_EXCEDE_DISPONIVEL",
                        "Quantidade estornada do consumo excede o consumo original.",
                        Map.of(
                                "consumoId", consumo.getId(),
                                "quantidadeOriginal", consumo.getQuantidadeBaseTotal(),
                                "quantidadeJaEstornada", jaEstornado
                        )
                );
            }
            if (pendente.compareTo(BigDecimal.ZERO) > 0) {
                pendentes.add(new ConsumoEstornar(consumo, pendente));
            }
        }
        return pendentes;
    }

    private void validarMovimentoOriginalDeVenda(MovimentoEstoque original) {
        if (original == null || original.getTipo() != TipoMovimentoEstoque.SAIDA_VENDA) {
            throw new ErroNegocioException(
                    "CANCELAMENTO_NAO_PERMITIDO",
                    "Cancelamento de venda exige movimento original SAIDA_VENDA."
            );
        }
    }

    private void validarQuantidadesAindaEstornaveis(List<GrupoEstornoPlanejado> grupos) {
        for (GrupoEstornoPlanejado grupo : grupos) {
            BigDecimal quantidadeOriginal = grupo.original.getQuantidadeBase()
                    .setScale(EstoqueProduto.ESCALA_QUANTIDADE, RoundingMode.HALF_UP);
            BigDecimal jaEstornada = movimentoRepository.listarEstornosDoMovimento(grupo.original.getId()).stream()
                    .map(MovimentoEstoque::getQuantidadeBase)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(EstoqueProduto.ESCALA_QUANTIDADE, RoundingMode.HALF_UP);
            BigDecimal aindaEstornavel = quantidadeOriginal.subtract(jaEstornada)
                    .setScale(EstoqueProduto.ESCALA_QUANTIDADE, RoundingMode.HALF_UP);
            if (aindaEstornavel.compareTo(BigDecimal.ZERO) <= 0) {
                throw new ErroNegocioException(
                        "MOVIMENTO_TOTALMENTE_ESTORNADO",
                        "Movimento original ja foi totalmente estornado.",
                        Map.of(
                                "movimentoId", grupo.original.getId(),
                                "quantidadeOriginal", quantidadeOriginal,
                                "quantidadeJaEstornada", jaEstornada
                        )
                );
            }
            if (grupo.quantidade.compareTo(aindaEstornavel) > 0) {
                throw new ErroNegocioException(
                        "QUANTIDADE_ESTORNO_EXCEDE_DISPONIVEL",
                        "Quantidade solicitada para estorno excede o saldo estornavel do movimento.",
                        Map.of(
                                "movimentoId", grupo.original.getId(),
                                "quantidadeSolicitada", grupo.quantidade,
                                "quantidadeAindaEstornavel", aindaEstornavel,
                                "quantidadeJaEstornada", jaEstornada
                        )
                );
            }
        }
    }

    private MovimentoEstoque criarMovimentoEstorno(Venda venda,
                                                   GrupoEstornoPlanejado grupo,
                                                   Usuario usuario,
                                                   String motivo,
                                                   String chave) {
        MovimentoEstoque original = grupo.original;
        EstoqueProduto estoque = estoqueRepository
                .buscarPorFilialEProdutoComLock(venda.getFilialId(), original.getProduto().getId())
                .orElseThrow(() -> new ErroNegocioException(
                        "ESTOQUE_PRODUTO_NAO_CONFIGURADO",
                        "Produto sem estoque decimal para estorno de venda.",
                        Map.of("produtoId", original.getProduto().getId())
                ));
        BigDecimal saldoAnterior = estoque.getQuantidadeAtual();
        BigDecimal custoMedioAnterior = estoque.getCustoMedio();
        BigDecimal valorAnterior = estoque.valorEstoque();
        BigDecimal valorDevolvido = grupo.quantidade.multiply(original.getCustoUnitario())
                .setScale(ESCALA_DINHEIRO, RoundingMode.HALF_UP);
        BigDecimal novaQuantidade = saldoAnterior.add(grupo.quantidade)
                .setScale(EstoqueProduto.ESCALA_QUANTIDADE, RoundingMode.HALF_UP);
        BigDecimal novoValor = valorAnterior.add(valorDevolvido)
                .setScale(ESCALA_DINHEIRO, RoundingMode.HALF_UP);
        BigDecimal novoCustoMedio = novaQuantidade.compareTo(BigDecimal.ZERO) > 0
                ? novoValor.divide(novaQuantidade, EstoqueProduto.ESCALA_CUSTO, RoundingMode.HALF_UP)
                : custoMedioAnterior;
        estoque.redefinirQuantidadeECusto(novaQuantidade, novoCustoMedio);
        BigDecimal saldoPosterior = estoque.getQuantidadeAtual();
        BigDecimal custoMedioPosterior = estoque.getCustoMedio();
        BigDecimal valorPosterior = estoque.valorEstoque();
        estoqueRepository.salvar(estoque);
        return movimentoRepository.salvar(new MovimentoEstoque(
                0,
                venda.getFilialId(),
                original.getProduto(),
                TipoMovimentoEstoque.ESTORNO_VENDA,
                grupo.quantidade,
                saldoAnterior,
                saldoPosterior,
                original.getCustoUnitario(),
                valorDevolvido,
                custoMedioAnterior,
                custoMedioPosterior,
                valorAnterior,
                valorPosterior,
                "VENDA",
                venda.getId(),
                usuario == null ? null : usuario.getRu(),
                motivo,
                "Estorno de venda #" + venda.getId(),
                chave,
                original
        ));
    }

    private RegistroIdempotencia reservarIdempotencia(Long filialId,
                                                      String operacao,
                                                      String chave,
                                                      String payloadHash,
                                                      long vendaId) {
        try {
            return idempotenciaRepository.salvarEFlush(RegistroIdempotencia.iniciar(
                    filialId,
                    operacao,
                    chave,
                    payloadHash,
                    vendaId
            ));
        } catch (DataIntegrityViolationException e) {
            throw new ErroNegocioException(
                    "CONCORRENCIA_ESTOQUE",
                    "A chave de idempotencia foi registrada por outra transacao concorrente. Reenvie a mesma requisicao."
            );
        }
    }

    private Venda resolverRegistroIdempotente(RegistroIdempotencia registro, String payloadHash) {
        if (!registro.mesmoPayload(payloadHash)) {
            throw new ConflitoIdempotenciaException("IDEMPOTENCY_KEY_REUSED: mesma chave de idempotencia com payload diferente.");
        }
        if (registro.estaProcessando()) {
            throw new ErroNegocioException(
                    "CONCORRENCIA_ESTOQUE",
                    "Operacao idempotente ainda esta em processamento. Reenvie a mesma requisicao em instantes."
            );
        }
        return vendaRepository.buscarPorId(registro.getRecursoId())
                .orElseThrow(() -> new IllegalStateException("Venda idempotente nao encontrada."));
    }

    private void validarPermissaoCancelamento(Usuario usuario) {
        boolean permitido = usuario != null
                && usuario.getClasse() != null
                && (usuario.getClasse().possuiPermissao(Permissao.VER_FINANCAS)
                || usuario.getClasse().possuiPermissao(Permissao.EDITAR_ESTOQUE));
        if (!permitido) {
            throw new SecurityException("Voce nao tem permissao para cancelar venda.");
        }
    }

    private static void validarMotivo(String motivo) {
        if (motivo == null || motivo.isBlank()) {
            throw new ErroNegocioException("CANCELAMENTO_SEM_MOTIVO", "Motivo de cancelamento e obrigatorio.");
        }
    }

    private static String normalizarIdempotencyKey(String idempotencyKey, String operacao) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key e obrigatoria para " + operacao + ".");
        }
        return idempotencyKey.trim();
    }

    private static String calcularPayloadHash(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel para idempotencia.", e);
        }
    }

    private void auditar(Usuario usuario, String acao, String descricao) {
        logRepository.salvar(new LogAcao(0, usuario == null ? null : usuario.getRu(), acao, descricao));
    }

    private record ResultadoCancelamento(BigDecimal valorCancelado) {}

    private record ConsumoEstornar(VendaItemConsumo consumo, BigDecimal quantidade) {}

    private static final class GrupoEstornoPlanejado {
        private final MovimentoEstoque original;
        private final List<ConsumoEstornar> consumos = new ArrayList<>();
        private BigDecimal quantidade = BigDecimal.ZERO.setScale(
                EstoqueProduto.ESCALA_QUANTIDADE,
                RoundingMode.HALF_UP
        );

        private GrupoEstornoPlanejado(MovimentoEstoque original) {
            this.original = original;
        }

        private void adicionar(ConsumoEstornar consumo) {
            consumos.add(consumo);
            quantidade = quantidade.add(consumo.quantidade())
                    .setScale(EstoqueProduto.ESCALA_QUANTIDADE, RoundingMode.HALF_UP);
        }
    }
}
