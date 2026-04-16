package controller;

import model.CotacaoMensal;
import model.Permissao;
import model.Produto;
import model.ResultadoComparacao;
import model.Usuario;
import repository.UsuarioRepository;
import service.CotacaoService;
import service.EstoqueService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

/**
 * Controller REST para cotações mensais de preço de produtos.
 *
 * <h2>Endpoints</h2>
 * <pre>
 *   POST   /api/cotacoes                              — registrar cotação
 *   GET    /api/cotacoes/produto/{id}/historico       — histórico de preços
 *   GET    /api/cotacoes/produto/{id}/comparar?mes=   — comparar com mês anterior
 *   GET    /api/cotacoes/relatorio?mes=               — relatório mensal completo
 * </pre>
 *
 * <h2>Autenticação</h2>
 * Todas as operações exigem o header {@code X-User-RU} e a permissão
 * {@link Permissao#VER_FINANCAS}.
 *
 * <h2>Formato de mês</h2>
 * O parâmetro {@code mes} segue o padrão ISO 8601: {@code YYYY-MM}
 * (ex: {@code 2025-03}). A ausência do parâmetro usa o mês corrente.
 */
@RestController
@RequestMapping("/api/cotacoes")
public class CotacaoController {

    private final CotacaoService     cotacaoService;
    private final EstoqueService     estoqueService;
    private final UsuarioRepository  usuarioRepository;

    public CotacaoController(CotacaoService cotacaoService,
                             EstoqueService estoqueService,
                             UsuarioRepository usuarioRepository) {
        this.cotacaoService    = cotacaoService;
        this.estoqueService    = estoqueService;
        this.usuarioRepository = usuarioRepository;
    }

    // ── POST /api/cotacoes ────────────────────────────────────────────────────

    /**
     * Registra o preço de compra de um produto em um mês específico.
     *
     * <p>Se já existir cotação para o mesmo produto+mês, ela é substituída —
     * permitindo corrigir um preço registrado incorretamente.
     *
     * @param request  dados da cotação a registrar
     * @param ruHeader RU do usuário logado ({@code X-User-RU})
     * @return {@code 201 Created} com a cotação registrada, ou erro descritivo
     */
    @PostMapping
    public ResponseEntity<?> registrarCotacao(
            @RequestBody RegistrarCotacaoRequest request,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {

        // ── Validações de entrada ──────────────────────────────────────────
        if (request.produtoId() <= 0) {
            return badRequest("ID do produto inválido.");
        }
        if (request.precoUnitario() == null
                || request.precoUnitario().compareTo(BigDecimal.ZERO) <= 0) {
            return badRequest("Preço deve ser maior que zero.");
        }
        if (request.quantidadeComprada() <= 0) {
            return badRequest("Quantidade deve ser maior que zero.");
        }

        // ── Parse do mês ───────────────────────────────────────────────────
        Optional<YearMonth> mesOpt = parseMes(request.mes());
        if (mesOpt.isEmpty()) {
            return badRequest("Mês inválido ou ausente. Use o formato YYYY-MM (ex: 2025-03).");
        }

        // ── Busca do produto ───────────────────────────────────────────────
        Optional<Produto> produtoOpt = estoqueService.buscarProdutoPorId(request.produtoId());
        if (produtoOpt.isEmpty()) {
            return notFound("Produto não encontrado com id: " + request.produtoId());
        }

        // ── Execução ───────────────────────────────────────────────────────
        try {
            Usuario ator = resolveUser(ruHeader);
            CotacaoMensal cotacao = cotacaoService.registrarCotacao(
                    ator, produtoOpt.get(), mesOpt.get(),
                    request.precoUnitario(), request.quantidadeComprada()
            );
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(CotacaoResponse.from(cotacao));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErroResponse(e.getMessage()));
        }
    }

    // ── GET /api/cotacoes/produto/{produtoId}/historico ───────────────────────

    /**
     * Retorna o histórico de cotações de um produto, ordenado do mais recente
     * para o mais antigo.
     *
     * @param produtoId ID do produto
     * @return lista de cotações ou {@code 404} se o produto não existir
     */
    @GetMapping("/produto/{produtoId}/historico")
    public ResponseEntity<?> historico(@PathVariable int produtoId,
                                       @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        resolveUser(ruHeader);
        Optional<Produto> produtoOpt = estoqueService.buscarProdutoPorId(produtoId);
        if (produtoOpt.isEmpty()) {
            return notFound("Produto não encontrado com id: " + produtoId);
        }

        List<CotacaoResponse> historico = cotacaoService
                .buscarHistorico(produtoOpt.get())
                .stream()
                .map(CotacaoResponse::from)
                .toList();

        return ResponseEntity.ok(historico);
    }

    // ── GET /api/cotacoes/produto/{produtoId}/comparar?mes= ──────────────────

    /**
     * Compara o preço de um produto no mês informado com o mês anterior.
     *
     * <p>Se {@code mes} for omitido, usa o mês corrente.
     * Retorna {@code 200} com {@link SemDadosResponse} quando não há cotações
     * suficientes para comparação — sem lançar erro.
     *
     * @param produtoId ID do produto a comparar
     * @param mes       mês de referência no formato {@code YYYY-MM} (opcional)
     * @return resultado da comparação ou aviso de dados insuficientes
     */
    @GetMapping("/produto/{produtoId}/comparar")
    public ResponseEntity<?> comparar(
            @PathVariable int produtoId,
            @RequestParam(required = false) String mes,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        resolveUser(ruHeader);

        Optional<Produto> produtoOpt = estoqueService.buscarProdutoPorId(produtoId);
        if (produtoOpt.isEmpty()) {
            return notFound("Produto não encontrado com id: " + produtoId);
        }

        YearMonth mesAtual = parseMes(mes).orElse(YearMonth.now());

        Optional<ResultadoComparacao> resultado =
                cotacaoService.compararComMesAnterior(produtoOpt.get(), mesAtual);

        if (resultado.isEmpty()) {
            return ResponseEntity.ok(new SemDadosResponse(
                    "Dados insuficientes para comparação do produto '" +
                            produtoOpt.get().getNome() + "' em " + mesAtual +
                            ". Verifique se há cotações nos dois meses."
            ));
        }

        return ResponseEntity.ok(ComparacaoResponse.from(resultado.get()));
    }

    // ── GET /api/cotacoes/relatorio?mes= ─────────────────────────────────────

    /**
     * Gera o relatório mensal comparando todos os produtos com o mês anterior.
     *
     * <p>Produtos sem cotação em algum dos dois meses são excluídos
     * silenciosamente — o relatório contém apenas comparações completas.
     *
     * @param mes mês alvo no formato {@code YYYY-MM} (opcional; padrão: mês corrente)
     * @return {@link RelatorioResponse} com todas as comparações disponíveis
     */
    @GetMapping("/relatorio")
    public ResponseEntity<?> relatorioMensal(
            @RequestParam(required = false) String mes,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        resolveUser(ruHeader);

        YearMonth mesAlvo = parseMes(mes).orElse(YearMonth.now());

        List<Produto> todos = estoqueService.listarProdutos();
        List<ComparacaoResponse> comparacoes = cotacaoService
                .gerarRelatorioMensal(todos, mesAlvo)
                .stream()
                .map(ComparacaoResponse::from)
                .toList();

        return ResponseEntity.ok(new RelatorioResponse(mesAlvo.toString(), comparacoes));
    }

    // ── Records de request / response ────────────────────────────────────────

    /**
     * Payload para registrar uma cotação.
     *
     * <p>{@code precoUnitario} é {@link BigDecimal} — nunca {@code double} em
     * dados monetários. O Jackson deserializa {@code "4.99"} corretamente para
     * {@code BigDecimal} sem perda de precisão.
     */
    public record RegistrarCotacaoRequest(
            int        produtoId,
            String     mes,             // YYYY-MM
            BigDecimal precoUnitario,
            int        quantidadeComprada
    ) {}

    /**
     * Representação pública de uma {@link CotacaoMensal}.
     *
     * <p>Todos os valores monetários são {@link String} via
     * {@link BigDecimal#toPlainString()} — sem notação científica e sem
     * perda de precisão ao serializar para JSON.
     */
    public record CotacaoResponse(
            long   id,
            int    produtoId,
            String produtoNome,
            String mes,
            String precoUnitario,   // BigDecimal → String: sem notação científica
            int    quantidadeComprada,
            String gastoTotal
    ) {
        public static CotacaoResponse from(CotacaoMensal c) {
            return new CotacaoResponse(
                    c.getId(),
                    c.getProduto().getId(),
                    c.getProduto().getNome(),
                    c.getMesReferencia().toString(),
                    c.getPrecoUnitario().toPlainString(),
                    c.getQuantidadeComprada(),
                    c.calcularGastoTotal().toPlainString()
            );
        }
    }

    /**
     * Representação pública de um {@link ResultadoComparacao}.
     *
     * <p>Valores monetários e percentuais são {@link String} pelos mesmos
     * motivos de {@link CotacaoResponse}. O frontend JavaScript pode usar
     * {@code parseFloat()} quando precisar de cálculos, sem perder precisão
     * na serialização.
     */
    public record ComparacaoResponse(
            int    produtoId,
            String produtoNome,
            String mesAnterior,
            String mesAtual,
            String precoAnterior,       // BigDecimal → String
            String precoAtual,
            String variacaoAbsoluta,
            String variacaoPercentual,
            String tendencia,           // enum.name() para serialização
            String tendenciaLabel,      // ex: "subiu", "caiu" (legível)
            String mensagem
    ) {
        public static ComparacaoResponse from(ResultadoComparacao r) {
            return new ComparacaoResponse(
                    r.getProduto().getId(),
                    r.getProduto().getNome(),
                    r.getMesAnterior().toString(),
                    r.getMesAtual().toString(),
                    r.getPrecoAnterior().toPlainString(),
                    r.getPrecoAtual().toPlainString(),
                    r.getVariacaoAbsoluta().toPlainString(),
                    r.getVariacaoPercentual().toPlainString(),
                    r.getTendencia().name(),
                    r.getTendencia().getVerbo(),
                    r.gerarMensagem()
            );
        }
    }

    /** Envelope do relatório mensal. */
    public record RelatorioResponse(
            String mes,
            List<ComparacaoResponse> comparacoes
    ) {}

    /** Resposta quando não há dados suficientes para uma operação. */
    public record SemDadosResponse(String aviso) {}

    /** Resposta de erro padronizada — chave {@code "erro"} consistente com {@code GlobalExceptionHandler}. */
    public record ErroResponse(String erro) {}

    // ── Auxiliares privados ───────────────────────────────────────────────────

    /**
     * Resolve o usuário ator a partir do header {@code X-User-RU}.
     */
    private Usuario resolveUser(String ruHeader) {
        return ControllerUtils.resolveUserAndRequirePermission(
                ruHeader,
                usuarioRepository,
                Permissao.VER_FINANCAS,
                "Voce nao tem permissao para acessar cotacoes."
        );
    }

    /**
     * Tenta fazer o parse de uma string no formato {@code YYYY-MM}.
     *
     * <p>Centraliza o parse e elimina a duplicação que existia nos três
     * endpoints que aceitam {@code mes} como parâmetro. Retorna
     * {@link Optional#empty()} tanto para {@code null}/{@code blank}
     * quanto para strings mal formatadas — sem lançar exceção.
     *
     * @param mes string de entrada; pode ser {@code null}
     * @return {@code Optional} com o {@link YearMonth} parseado, ou vazio
     */
    private static Optional<YearMonth> parseMes(String mes) {
        if (mes == null || mes.isBlank()) return Optional.empty();
        try {
            return Optional.of(YearMonth.parse(mes.trim()));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    /** Atalho para {@code 400 Bad Request} com mensagem de erro. */
    private static ResponseEntity<ErroResponse> badRequest(String mensagem) {
        return ResponseEntity.badRequest().body(new ErroResponse(mensagem));
    }

    /** Atalho para {@code 404 Not Found} com mensagem de erro. */
    private static ResponseEntity<ErroResponse> notFound(String mensagem) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErroResponse(mensagem));
    }
}
