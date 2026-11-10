
package controller;

import model.CotacaoMensal;
import model.Permissao;
import model.Produto;
import model.ResultadoComparacao;
import model.Usuario;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import repository.UsuarioRepository;
import service.CotacaoService;
import service.EstoqueService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * API de cotacoes mensais de produtos do StockOS.
 *
 * Permite:
 * - Registrar cotacoes mensais;
 * - Consultar historico de precos;
 * - Comparar cotacoes entre meses;
 * - Gerar relatorio comparativo mensal.
 *
 * Os valores monetarios das respostas permanecem
 * como String para preservar a precisao e o
 * contrato JSON existente.
 *
 * SEGURANCA:
 * A identidade X-User-RU e legada e nao comprova
 * autenticacao nem acesso a estabelecimento.
 */
@RestController
@RequestMapping("/api/cotacoes")
public class CotacaoController {

    private static final Pattern PADRAO_MES =
            Pattern.compile("[0-9]{4}-(0[1-9]|1[0-2])");

    private static final int PRECISAO_MONETARIA = 19;
    private static final int ESCALA_MONETARIA = 2;

    private static final String ERRO_PERMISSAO =
            "Voce nao tem permissao para acessar cotacoes.";

    private static final String ERRO_MES =
            "Mes invalido. Utilize o formato YYYY-MM.";

    private final CotacaoService cotacaoService;
    private final EstoqueService estoqueService;
    private final UsuarioRepository usuarioRepository;

    public CotacaoController(
            CotacaoService cotacaoService,
            EstoqueService estoqueService,
            UsuarioRepository usuarioRepository
    ) {
        this.cotacaoService = cotacaoService;
        this.estoqueService = estoqueService;
        this.usuarioRepository = usuarioRepository;
    }

    // =====================================================
    // REGISTRAR COTACAO
    // =====================================================

    /**
     * Registra ou atualiza uma cotacao mensal.
     *
     * POST /api/cotacoes
     *
     * A autorizacao acontece antes de qualquer
     * consulta ao estoque.
     */
    @PostMapping
    public ResponseEntity<?> registrarCotacao(
            @RequestBody(required = false)
            RegistrarCotacaoRequest request,

            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        Usuario ator = resolverUsuario(ruHeader);

        if (request == null) {
            return badRequest(
                    "Dados da cotacao sao obrigatorios."
            );
        }

        if (request.produtoId() <= 0) {
            return badRequest(
                    "ID do produto invalido."
            );
        }

        if (request.quantidadeComprada() <= 0) {
            return badRequest(
                    "Quantidade comprada deve ser maior que zero."
            );
        }

        String erroPreco = validarPreco(
                request.precoUnitario()
        );

        if (erroPreco != null) {
            return badRequest(erroPreco);
        }

        Optional<YearMonth> mesOpt =
                parseMes(request.mes());

        if (mesOpt.isEmpty()) {
            return badRequest(
                    "Mes invalido ou ausente. "
                            + "Utilize YYYY-MM."
            );
        }

        Optional<Produto> produtoOpt =
                estoqueService.buscarProdutoPorId(
                        request.produtoId()
                );

        if (produtoOpt.isEmpty()) {
            return notFound(
                    "Produto nao encontrado com ID: "
                            + request.produtoId()
            );
        }

        CotacaoMensal cotacao =
                cotacaoService.registrarCotacao(
                        ator,
                        produtoOpt.get(),
                        mesOpt.get(),
                        request.precoUnitario(),
                        request.quantidadeComprada()
                );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CotacaoResponse.from(cotacao));
    }

    // =====================================================
    // HISTORICO DE COTACOES
    // =====================================================

    /**
     * Retorna cotacoes de um produto.
     *
     * GET /api/cotacoes/produto/{produtoId}/historico
     *
     * Ordenacao: mais recente primeiro.
     */
    @GetMapping("/produto/{produtoId}/historico")
    public ResponseEntity<?> historico(
            @PathVariable int produtoId,

            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        resolverUsuario(ruHeader);

        if (produtoId <= 0) {
            return badRequest(
                    "ID do produto invalido."
            );
        }

        Optional<Produto> produtoOpt =
                estoqueService.buscarProdutoPorId(produtoId);

        if (produtoOpt.isEmpty()) {
            return notFound(
                    "Produto nao encontrado com ID: "
                            + produtoId
            );
        }

        List<CotacaoResponse> historico =
                cotacaoService.buscarHistorico(
                                produtoOpt.get()
                        )
                        .stream()
                        .sorted(
                                Comparator.comparing(
                                        CotacaoMensal::getMesReferencia
                                ).reversed()
                        )
                        .map(CotacaoResponse::from)
                        .toList();

        return ResponseEntity.ok(historico);
    }

    // =====================================================
    // COMPARACAO MENSAL
    // =====================================================

    /**
     * Compara a cotacao de um produto com
     * a cotacao do mes anterior.
     *
     * GET /api/cotacoes/produto/{produtoId}/comparar
     *
     * Se mes for omitido, utiliza o mes atual.
     * Se for informado e invalido, retorna 400.
     */
    @GetMapping("/produto/{produtoId}/comparar")
    public ResponseEntity<?> comparar(
            @PathVariable int produtoId,

            @RequestParam(required = false)
            String mes,

            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        resolverUsuario(ruHeader);

        if (produtoId <= 0) {
            return badRequest(
                    "ID do produto invalido."
            );
        }

        Optional<YearMonth> mesOpt = parseMes(mes);

        if (mesFoiInformado(mes) && mesOpt.isEmpty()) {
            return badRequest(ERRO_MES);
        }

        YearMonth mesAtual = mesOpt.orElseGet(
                YearMonth::now
        );

        Optional<Produto> produtoOpt =
                estoqueService.buscarProdutoPorId(produtoId);

        if (produtoOpt.isEmpty()) {
            return notFound(
                    "Produto nao encontrado com ID: "
                            + produtoId
            );
        }

        Produto produto = produtoOpt.get();

        Optional<ResultadoComparacao> resultado =
                cotacaoService.compararComMesAnterior(
                        produto,
                        mesAtual
                );

        if (resultado.isEmpty()) {
            return ResponseEntity.ok(
                    new SemDadosResponse(
                            "Dados insuficientes para comparacao "
                                    + "do produto '"
                                    + produto.getNome()
                                    + "' em "
                                    + mesAtual
                                    + ". Verifique as cotacoes "
                                    + "dos dois meses."
                    )
            );
        }

        return ResponseEntity.ok(
                ComparacaoResponse.from(resultado.get())
        );
    }

    // =====================================================
    // RELATORIO MENSAL
    // =====================================================

    /**
     * Gera o relatorio comparativo mensal.
     *
     * GET /api/cotacoes/relatorio
     *
     * Apenas comparacoes com dados dos
     * dois meses sao retornadas pelo service.
     */
    @GetMapping("/relatorio")
    public ResponseEntity<?> relatorioMensal(
            @RequestParam(required = false)
            String mes,

            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        resolverUsuario(ruHeader);

        Optional<YearMonth> mesOpt = parseMes(mes);

        if (mesFoiInformado(mes) && mesOpt.isEmpty()) {
            return badRequest(ERRO_MES);
        }

        YearMonth mesAlvo = mesOpt.orElseGet(
                YearMonth::now
        );

        List<Produto> produtos =
                estoqueService.listarProdutos();

        List<ComparacaoResponse> comparacoes =
                cotacaoService.gerarRelatorioMensal(
                                produtos,
                                mesAlvo
                        )
                        .stream()
                        .map(ComparacaoResponse::from)
                        .toList();

        return ResponseEntity.ok(
                new RelatorioResponse(
                        mesAlvo.toString(),
                        comparacoes
                )
        );
    }

    // =====================================================
    // AUTORIZACAO LEGADA
    // =====================================================

    /**
     * Exige permissao financeira.
     *
     * Esta validacao nao substitui
     * autenticacao por credencial verificada.
     */
    private Usuario resolverUsuario(
            String ruHeader
    ) {
        return ControllerUtils
                .resolveUserAndRequirePermission(
                        ruHeader,
                        usuarioRepository,
                        Permissao.VER_FINANCAS,
                        ERRO_PERMISSAO
                );
    }

    // =====================================================
    // VALIDACAO MONETARIA
    // =====================================================

    /**
     * Valida o preco considerando a escala
     * utilizada pela entidade CotacaoMensal.
     *
     * A entidade arredonda para duas casas
     * decimais com HALF_UP.
     */
    private static String validarPreco(
            BigDecimal preco
    ) {
        if (preco == null
                || preco.compareTo(BigDecimal.ZERO) <= 0) {
            return "Preco deve ser maior que zero.";
        }

        BigDecimal precoArredondado =
                preco.setScale(
                        ESCALA_MONETARIA,
                        RoundingMode.HALF_UP
                );

        if (precoArredondado
                .compareTo(BigDecimal.ZERO) <= 0) {
            return "Preco e muito pequeno para "
                    + "a precisao monetaria utilizada.";
        }

        if (precoArredondado.precision()
                > PRECISAO_MONETARIA) {
            return "Preco excede o limite "
                    + "monetario permitido.";
        }

        return null;
    }

    // =====================================================
    // VALIDACAO DO MES
    // =====================================================

    /**
     * Diferencia um mes omitido de um mes
     * informado incorretamente.
     */
    private static boolean mesFoiInformado(
            String mes
    ) {
        return mes != null && !mes.isBlank();
    }

    /**
     * Aceita apenas YYYY-MM.
     *
     * Retorna Optional.empty() para campos
     * ausentes ou valores invalidos.
     */
    private static Optional<YearMonth> parseMes(
            String mes
    ) {
        if (mes == null || mes.isBlank()) {
            return Optional.empty();
        }

        String valor = mes.trim();

        if (!PADRAO_MES.matcher(valor).matches()) {
            return Optional.empty();
        }

        try {
            return Optional.of(
                    YearMonth.parse(valor)
            );
        } catch (DateTimeParseException ex) {
            return Optional.empty();
        }
    }

    // =====================================================
    // RESPOSTAS DE ERRO
    // =====================================================

    /**
     * Mantem o campo "erro" usado pelo frontend
     * nas respostas locais de validacao.
     *
     * Excecoes de seguranca e de negocio sao
     * tratadas pelo GlobalExceptionHandler.
     */
    private static ResponseEntity<ErroResponse> badRequest(
            String mensagem
    ) {
        return ResponseEntity.badRequest()
                .body(new ErroResponse(mensagem));
    }

    private static ResponseEntity<ErroResponse> notFound(
            String mensagem
    ) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErroResponse(mensagem));
    }

    // =====================================================
    // REQUEST DTO
    // =====================================================

    public record RegistrarCotacaoRequest(
            int produtoId,
            String mes,
            BigDecimal precoUnitario,
            int quantidadeComprada
    ) {
    }

    // =====================================================
    // RESPONSE DTO - COTACAO
    // =====================================================

    public record CotacaoResponse(
            long id,
            int produtoId,
            String produtoNome,
            String mes,
            String precoUnitario,
            int quantidadeComprada,
            String gastoTotal
    ) {

        public static CotacaoResponse from(
                CotacaoMensal cotacao
        ) {
            Produto produto = cotacao.getProduto();

            return new CotacaoResponse(
                    cotacao.getId(),
                    produto.getId(),
                    produto.getNome(),
                    cotacao.getMesReferencia().toString(),
                    cotacao.getPrecoUnitario().toPlainString(),
                    cotacao.getQuantidadeComprada(),
                    cotacao.calcularGastoTotal().toPlainString()
            );
        }
    }

    // =====================================================
    // RESPONSE DTO - COMPARACAO
    // =====================================================

    public record ComparacaoResponse(
            int produtoId,
            String produtoNome,
            String mesAnterior,
            String mesAtual,
            String precoAnterior,
            String precoAtual,
            String variacaoAbsoluta,
            String variacaoPercentual,
            String tendencia,
            String tendenciaLabel,
            String mensagem
    ) {

        public static ComparacaoResponse from(
                ResultadoComparacao resultado
        ) {
            Produto produto = resultado.getProduto();

            return new ComparacaoResponse(
                    produto.getId(),
                    produto.getNome(),
                    resultado.getMesAnterior().toString(),
                    resultado.getMesAtual().toString(),
                    resultado.getPrecoAnterior().toPlainString(),
                    resultado.getPrecoAtual().toPlainString(),
                    resultado.getVariacaoAbsoluta().toPlainString(),
                    resultado.getVariacaoPercentual().toPlainString(),
                    resultado.getTendencia().name(),
                    resultado.getTendencia().getVerbo(),
                    resultado.gerarMensagem()
            );
        }
    }

    // =====================================================
    // RESPONSE DTO - RELATORIO
    // =====================================================

    public record RelatorioResponse(
            String mes,
            List<ComparacaoResponse> comparacoes
    ) {
    }

    // =====================================================
    // RESPONSE DTO - SEM DADOS
    // =====================================================

    public record SemDadosResponse(
            String aviso
    ) {
    }

    // =====================================================
    // RESPONSE DTO - ERRO
    // =====================================================

    public record ErroResponse(
            String erro
    ) {
    }
}

