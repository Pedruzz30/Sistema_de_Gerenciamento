
package controller;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

import model.Caixa;
import model.FechamentoCaixa;
import model.MovimentacaoCaixa;
import model.Permissao;
import model.TipoMovimentacaoCaixa;
import model.Usuario;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import repository.CaixaRepository;
import repository.UsuarioRepository;

import service.CaixaService;
import service.VendaItemInput;

/**
 * API REST do caixa legado do StockOS.
 *
 * Mantém os endpoints e formatos de resposta utilizados
 * pelo frontend.
 *
 * ATENÇÃO: X-User-RU não autentica identidade.
 * A API deve permanecer isolada até existir validação
 * server-side de token e contexto de estabelecimento.
 */
@RestController
@RequestMapping("/api/caixas")
public class CaixaController {

    private static final String MENSAGEM_ACESSO_CAIXA =
            "Voce nao tem permissao para visualizar caixas.";

    private static final String MENSAGEM_ACESSO_METRICAS =
            "Voce nao tem permissao para visualizar metricas de caixa.";

    private static final String MENSAGEM_ACESSO_MOVIMENTACOES =
            "Voce nao tem permissao para visualizar movimentacoes de caixa.";

    private final CaixaService caixaService;
    private final CaixaRepository caixaRepository;
    private final UsuarioRepository usuarioRepository;

    public CaixaController(
            CaixaService caixaService,
            CaixaRepository caixaRepository,
            UsuarioRepository usuarioRepository
    ) {
        this.caixaService = caixaService;
        this.caixaRepository = caixaRepository;
        this.usuarioRepository = usuarioRepository;
    }

    // =========================================================
    // CONSULTAS DE CAIXAS
    // =========================================================

    @GetMapping
    public ResponseEntity<List<CaixaResponse>> listarCaixas(
            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        exigirAcessoLeitura(ruHeader, MENSAGEM_ACESSO_CAIXA);

        List<CaixaResponse> caixas = caixaRepository
                .listarTodos()
                .stream()
                .map(CaixaResponse::from)
                .toList();

        return ResponseEntity.ok(caixas);
    }

    // =========================================================
    // METRICAS
    // =========================================================

    @GetMapping("/metricas")
    public ResponseEntity<MetricasResponse> metricas(
            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        exigirAcessoLeitura(ruHeader, MENSAGEM_ACESSO_METRICAS);

        return ResponseEntity.ok(
                calcularMetricas(caixaRepository.listarTodos(), null)
        );
    }

    @GetMapping("/metricas/hoje")
    public ResponseEntity<MetricasResponse> metricasHoje(
            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        exigirAcessoLeitura(ruHeader, MENSAGEM_ACESSO_METRICAS);

        LocalDate hoje = LocalDate.now();

        return ResponseEntity.ok(
                calcularMetricasDia(
                        caixaRepository.listarTodos(),
                        hoje
                )
        );
    }

    /**
     * Mantém compatibilidade com testes existentes.
     *
     * O número de caixas abertos representa a quantidade
     * aberta no momento da consulta.
     */
    static MetricasResponse calcularMetricasDia(
            List<Caixa> caixas,
            LocalDate dia
    ) {
        if (dia == null) {
            throw new IllegalArgumentException(
                    "Data de referencia e obrigatoria."
            );
        }

        return calcularMetricas(caixas, dia);
    }

    /**
     * Calcula vendas, número de operações e ticket médio.
     *
     * Quando dia == null, considera todo o histórico.
     */
    private static MetricasResponse calcularMetricas(
            List<Caixa> caixas,
            LocalDate dia
    ) {
        BigDecimal totalVendas = BigDecimal.ZERO;

        long quantidadeVendas = 0L;
        long caixasAbertos = 0L;

        for (Caixa caixa : caixas) {

            if (caixa.getStatus() == Caixa.Status.ABERTO) {
                caixasAbertos++;
            }

            for (MovimentacaoCaixa movimento : caixa.getMovimentacoes()) {

                if (movimento.getTipo() != TipoMovimentacaoCaixa.VENDA) {
                    continue;
                }

                if (dia != null &&
                        (movimento.getDataHora() == null ||
                         !dia.equals(movimento.getDataHora().toLocalDate()))) {
                    continue;
                }

                totalVendas = totalVendas.add(movimento.getValor());
                quantidadeVendas++;
            }
        }

        totalVendas = totalVendas.setScale(
                2,
                RoundingMode.HALF_UP
        );

        BigDecimal ticketMedio = quantidadeVendas == 0
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : totalVendas.divide(
                        BigDecimal.valueOf(quantidadeVendas),
                        2,
                        RoundingMode.HALF_UP
                );

        return new MetricasResponse(
                totalVendas,
                caixasAbertos,
                quantidadeVendas,
                ticketMedio
        );
    }

    // =========================================================
    // CONSULTA POR NUMERO
    // =========================================================

    @GetMapping("/{numero}")
    public ResponseEntity<CaixaResponse> buscarCaixaPorNumero(
            @PathVariable int numero,
            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        exigirAcessoLeitura(ruHeader, MENSAGEM_ACESSO_CAIXA);

        return caixaRepository.buscarAbertoporNumero(numero)
                .map(caixa -> ResponseEntity.ok(
                        CaixaResponse.from(caixa)
                ))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{numero}/movimentacoes")
    public ResponseEntity<List<MovimentacaoCaixaResponse>>
    listarMovimentacoesDoCaixa(
            @PathVariable int numero,
            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        exigirAcessoLeitura(
                ruHeader,
                MENSAGEM_ACESSO_MOVIMENTACOES
        );

        return caixaRepository.buscarAbertoporNumero(numero)
                .map(caixa -> ResponseEntity.ok(
                        mapearMovimentacoes(caixa)
                ))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // =========================================================
    // CONSULTAS POR SESSAO
    // =========================================================

    @GetMapping("/sessoes/{caixaId}")
    public ResponseEntity<CaixaResponse> buscarCaixaPorId(
            @PathVariable long caixaId,
            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        exigirAcessoLeitura(ruHeader, MENSAGEM_ACESSO_CAIXA);

        return caixaRepository.buscarPorId(caixaId)
                .map(caixa -> ResponseEntity.ok(
                        CaixaResponse.from(caixa)
                ))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/sessoes/{caixaId}/movimentacoes")
    public ResponseEntity<List<MovimentacaoCaixaResponse>>
    listarMovimentacoesDoCaixaPorId(
            @PathVariable long caixaId,
            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        exigirAcessoLeitura(
                ruHeader,
                MENSAGEM_ACESSO_MOVIMENTACOES
        );

        return caixaRepository.buscarPorId(caixaId)
                .map(caixa -> ResponseEntity.ok(
                        mapearMovimentacoes(caixa)
                ))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/sessoes/{caixaId}/historico")
    public ResponseEntity<List<CaixaResponse>> listarHistoricoPorCaixaId(
            @PathVariable long caixaId,
            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        exigirAcessoLeitura(
                ruHeader,
                "Voce nao tem permissao para visualizar historico de caixas."
        );

        return caixaRepository.buscarPorId(caixaId)
                .map(caixa -> ResponseEntity.ok(
                        caixaRepository
                                .buscarHistoricoPorNumero(caixa.getNumeroCaixa())
                                .stream()
                                .map(CaixaResponse::from)
                                .toList()
                ))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // =========================================================
    // ABERTURA DE CAIXA
    // =========================================================

    @PostMapping("/abrir")
    public ResponseEntity<?> abrirCaixa(
            @RequestBody(required = false) AbrirCaixaRequest request,
            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        Usuario ator = resolverOperador(ruHeader);

        if (request == null || request.numeroCaixa() <= 0) {
            return respostaErro("Numero do caixa invalido.");
        }

        if (!Double.isFinite(request.saldoInicial())
                || request.saldoInicial() < 0) {
            return respostaErro(
                    "Saldo inicial deve ser um numero finito e nao negativo."
            );
        }

        if (!possuiNoMaximoDuasCasas(request.saldoInicial())) {
            return respostaErro(
                    "Saldo inicial deve ter no maximo duas casas decimais."
            );
        }

        try {
            Caixa caixa = caixaService.abrirCaixa(
                    ator,
                    request.numeroCaixa(),
                    request.saldoInicial()
            );

            return ResponseEntity.ok(CaixaResponse.from(caixa));

        } catch (IllegalStateException | IllegalArgumentException ex) {
            return respostaErro(ex.getMessage());
        }
    }

    // =========================================================
    // REGISTRO DE VENDA
    // =========================================================

    @PostMapping("/venda")
    public ResponseEntity<?> registrarVenda(
            @RequestBody(required = false) MovimentacaoRequest request,
            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        Usuario ator = resolverOperador(ruHeader);

        String mensagemValidacao = validarVenda(request);

        if (mensagemValidacao != null) {
            return respostaErro(mensagemValidacao);
        }

        List<VendaItemInput> itens = request.itens() == null
                ? List.of()
                : request.itens()
                        .stream()
                        .map(item -> new VendaItemInput(
                                item.produtoId(),
                                item.itemCardapioId(),
                                item.quantidade()
                        ))
                        .toList();

        try {
            caixaService.registrarVenda(
                    ator,
                    request.numeroCaixa(),
                    BigDecimal.valueOf(request.valor()),
                    request.descricao(),
                    itens
            );

            Caixa caixa = caixaService.buscarCaixaAberto(
                    request.numeroCaixa()
            );

            return ResponseEntity.ok(CaixaResponse.from(caixa));

        } catch (IllegalStateException | IllegalArgumentException ex) {
            return respostaErro(ex.getMessage());
        }
    }

    // =========================================================
    // SANGRIA
    // =========================================================

    @PostMapping("/sangria")
    public ResponseEntity<?> registrarSangria(
            @RequestBody(required = false) MovimentacaoRequest request,
            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        Usuario ator = resolverOperador(ruHeader);

        String mensagemValidacao = validarMovimentacaoFinanceira(request);

        if (mensagemValidacao != null) {
            return respostaErro(mensagemValidacao);
        }

        try {
            caixaService.registrarSangria(
                    ator,
                    request.numeroCaixa(),
                    request.valor(),
                    request.descricao()
            );

            Caixa caixa = caixaService.buscarCaixaAberto(
                    request.numeroCaixa()
            );

            return ResponseEntity.ok(CaixaResponse.from(caixa));

        } catch (IllegalStateException | IllegalArgumentException ex) {
            return respostaErro(ex.getMessage());
        }
    }

    // =========================================================
    // SUPRIMENTO
    // =========================================================

    @PostMapping("/suprimento")
    public ResponseEntity<?> registrarSuprimento(
            @RequestBody(required = false) MovimentacaoRequest request,
            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        Usuario ator = resolverOperador(ruHeader);

        String mensagemValidacao = validarMovimentacaoFinanceira(request);

        if (mensagemValidacao != null) {
            return respostaErro(mensagemValidacao);
        }

        try {
            caixaService.registrarSuprimento(
                    ator,
                    request.numeroCaixa(),
                    request.valor(),
                    request.descricao()
            );

            Caixa caixa = caixaService.buscarCaixaAberto(
                    request.numeroCaixa()
            );

            return ResponseEntity.ok(CaixaResponse.from(caixa));

        } catch (IllegalStateException | IllegalArgumentException ex) {
            return respostaErro(ex.getMessage());
        }
    }

    // =========================================================
    // ENCERRAMENTO
    // =========================================================

    @PostMapping("/encerrar")
    public ResponseEntity<?> encerrarCaixa(
            @RequestBody(required = false) EncerrarCaixaRequest request,
            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        Usuario ator = resolverOperador(ruHeader);

        if (request == null || request.numeroCaixa() <= 0) {
            return respostaErro("Numero do caixa invalido.");
        }

        if (request.valorContado() == null) {
            return respostaErro("Valor contado e obrigatorio.");
        }

        if (request.valorContado().signum() < 0) {
            return respostaErro("Valor contado nao pode ser negativo.");
        }

        if (request.valorContado().stripTrailingZeros().scale() > 2) {
            return respostaErro(
                    "Valor contado deve ter no maximo duas casas decimais."
            );
        }

        try {
            FechamentoCaixa fechamento = caixaService.encerrarCaixa(
                    ator,
                    request.numeroCaixa(),
                    request.valorContado(),
                    request.observacao()
            );

            return ResponseEntity.ok(
                    FechamentoResponse.from(fechamento)
            );

        } catch (IllegalStateException | IllegalArgumentException ex) {
            return respostaErro(ex.getMessage());
        }
    }

    // =========================================================
    // CONSOLIDADO
    // =========================================================

    @GetMapping("/consolidado")
    public ResponseEntity<ConsolidadoResponse> consolidadoDia(
            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        ControllerUtils.resolveUserAndRequirePermission(
                ruHeader,
                usuarioRepository,
                Permissao.VER_FINANCAS,
                "Voce nao tem permissao para acessar o consolidado diario."
        );

        LocalDate hoje = LocalDate.now();

        String resumo = caixaService.consolidarDia(hoje);

        return ResponseEntity.ok(
                new ConsolidadoResponse(hoje.toString(), resumo)
        );
    }

    // =========================================================
    // AUTORIZACAO LEGADA
    // =========================================================

    private void exigirAcessoLeitura(
            String ruHeader,
            String mensagemErro
    ) {
        ControllerUtils.resolveUserAndRequireAnyPermission(
                ruHeader,
                usuarioRepository,
                mensagemErro,
                Permissao.VER_VENDAS,
                Permissao.VER_FINANCAS
        );
    }

    private Usuario resolverOperador(String ruHeader) {
        return ControllerUtils.resolveUser(
                ruHeader,
                usuarioRepository
        );
    }

    // =========================================================
    // MAPEAMENTO DE MOVIMENTACOES
    // =========================================================

    private static List<MovimentacaoCaixaResponse> mapearMovimentacoes(
            Caixa caixa
    ) {
        return caixa.getMovimentacoes()
                .stream()
                .map(movimento -> MovimentacaoCaixaResponse.from(
                        caixa.getId(),
                        caixa.getNumeroCaixa(),
                        movimento
                ))
                .toList();
    }

    // =========================================================
    // VALIDACOES
    // =========================================================

    /**
     * Para venda com itens, o valor pode ser zero,
     * porque o CaixaService calcula o preço dos produtos.
     *
     * Para venda sem itens, exige valor positivo.
     */
    private static String validarVenda(
            MovimentacaoRequest request
    ) {
        if (request == null || request.numeroCaixa() <= 0) {
            return "Numero do caixa invalido.";
        }

        if (!Double.isFinite(request.valor())
                || request.valor() < 0) {
            return "Valor da venda deve ser um numero finito e nao negativo.";
        }

        boolean possuiItens =
                request.itens() != null && !request.itens().isEmpty();

        if (!possuiItens) {

            if (request.valor() <= 0) {
                return "Valor deve ser maior que zero.";
            }

            if (!possuiNoMaximoDuasCasas(request.valor())) {
                return "Valor deve ter no maximo duas casas decimais.";
            }

        } else if (request.itens()
                .stream()
                .anyMatch(item ->
                        item == null || !item.possuiReferenciaValida()
                )) {

            return "Itens da venda devem informar produtoId ou "
                    + "itemCardapioId com quantidade positiva.";
        }

        return null;
    }

    /**
     * Sangrias e suprimentos não aceitam itens.
     * O valor deve ser finito e maior que zero.
     */
    private static String validarMovimentacaoFinanceira(
            MovimentacaoRequest request
    ) {
        if (request == null || request.numeroCaixa() <= 0) {
            return "Numero do caixa invalido.";
        }

        if (request.itens() != null && !request.itens().isEmpty()) {
            return "Sangria e suprimento nao aceitam itens de venda.";
        }

        if (!Double.isFinite(request.valor()) || request.valor() <= 0) {
            return "Valor deve ser um numero finito e maior que zero.";
        }

        if (!possuiNoMaximoDuasCasas(request.valor())) {
            return "Valor deve ter no maximo duas casas decimais.";
        }

        return null;
    }

    private static boolean possuiNoMaximoDuasCasas(double valor) {
        return Double.isFinite(valor)
                && BigDecimal.valueOf(valor)
                        .stripTrailingZeros()
                        .scale() <= 2;
    }

    private static ResponseEntity<ErroResponse> respostaErro(
            String mensagem
    ) {
        return ResponseEntity.badRequest()
                .body(new ErroResponse(mensagem));
    }

    // =========================================================
    // REQUEST DTOs
    // =========================================================

    public record AbrirCaixaRequest(
            int numeroCaixa,
            double saldoInicial
    ) {
    }

    public record MovimentacaoRequest(
            int numeroCaixa,
            double valor,
            String descricao,
            List<ItemVendaRequest> itens
    ) {
    }

    public record ItemVendaRequest(
            Integer produtoId,
            Integer itemCardapioId,
            int quantidade
    ) {
        boolean possuiReferenciaValida() {
            boolean possuiProduto =
                    produtoId != null && produtoId > 0;

            boolean possuiItemCardapio =
                    itemCardapioId != null && itemCardapioId > 0;

            return quantidade > 0
                    && possuiProduto != possuiItemCardapio;
        }
    }

    public record EncerrarCaixaRequest(
            int numeroCaixa,
            BigDecimal valorContado,
            String observacao
    ) {
    }

    // =========================================================
    // RESPONSE DTOs
    // =========================================================

    public record CaixaResponse(
            long caixaId,
            long id,
            int numeroCaixa,
            String status,
            BigDecimal saldoInicial,
            BigDecimal saldoAtual,
            BigDecimal totalVendas,
            BigDecimal totalEntradas,
            BigDecimal totalSaidas,
            String nomeOperador,
            String dataAbertura,
            String dataEncerramento
    ) {
        public static CaixaResponse from(Caixa c) {
            return new CaixaResponse(
                    c.getId(),
                    c.getId(),
                    c.getNumeroCaixa(),
                    c.getStatus().name(),
                    c.getSaldoInicial(),
                    c.getSaldoAtual(),
                    c.calcularTotalVendas(),
                    c.calcularTotalEntradas(),
                    c.calcularTotalSaidas(),
                    c.getOperadorAtual() != null
                            ? c.getOperadorAtual().getNomeCompleto()
                            : null,
                    c.getDataAbertura() != null
                            ? c.getDataAbertura().toString()
                            : null,
                    c.getDataEncerramento() != null
                            ? c.getDataEncerramento().toString()
                            : null
            );
        }
    }

    public record MovimentacaoCaixaResponse(
            long caixaId,
            int numeroCaixa,
            long id,
            String tipo,
            BigDecimal valor,
            String descricao,
            String dataHora,
            String operador
    ) {
        public static MovimentacaoCaixaResponse from(
                long caixaId,
                int numeroCaixa,
                MovimentacaoCaixa m
        ) {
            return new MovimentacaoCaixaResponse(
                    caixaId,
                    numeroCaixa,
                    m.getId(),
                    m.getTipo().name(),
                    m.getValor(),
                    m.getDescricao(),
                    m.getDataHora().toString(),
                    m.getOperador() != null
                            ? m.getOperador().getNomeCompleto()
                            : null
            );
        }
    }

    public record FechamentoResponse(
            long caixaId,
            int numeroCaixa,
            String data,
            String timestamp,
            BigDecimal saldoInicial,
            BigDecimal totalEntradas,
            BigDecimal totalSaidas,
            BigDecimal totalVendas,
            BigDecimal saldoFinal,
            BigDecimal valorSistema,
            BigDecimal valorContado,
            BigDecimal divergencia,
            int quantidadeMovimentacoes,
            String nomeOperador,
            String abertoPor,
            String fechadoPor,
            String observacao
    ) {
        public static FechamentoResponse from(FechamentoCaixa f) {
            return new FechamentoResponse(
                    f.getCaixaId(),
                    f.getNumeroCaixa(),
                    f.getData().toString(),
                    f.getTimestamp().toString(),
                    f.getSaldoInicial(),
                    f.getTotalEntradas(),
                    f.getTotalSaidas(),
                    f.getTotalVendas(),
                    f.getSaldoFinal(),
                    f.getValorSistema(),
                    f.getValorContado(),
                    f.getDivergencia(),
                    f.getQuantidadeMovimentacoes(),
                    f.getNomeOperador(),
                    f.getAbertoPor(),
                    f.getFechadoPor(),
                    f.getObservacao()
            );
        }
    }

    public record MetricasResponse(
            BigDecimal totalVendas,
            long caixasAbertos,
            long totalMovimentacoesVenda,
            BigDecimal ticketMedio
    ) {
    }

    public record ConsolidadoResponse(
            String data,
            String resumo
    ) {
    }

    public record ErroResponse(
            String erro
    ) {
    }
}

