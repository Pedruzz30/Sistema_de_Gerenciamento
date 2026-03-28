package controller;

import model.CotacaoMensal;
import model.Produto;
import model.ResultadoComparacao;
import model.Usuario;
import service.CotacaoService;
import service.EstoqueService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/cotacoes")
public class CotacaoController {

    private final CotacaoService cotacaoService;
    private final EstoqueService estoqueService;
    private final Usuario adminSuperior;

    public CotacaoController(CotacaoService cotacaoService,
                             EstoqueService estoqueService,
                             Usuario adminSuperior) {
        this.cotacaoService = cotacaoService;
        this.estoqueService = estoqueService;
        this.adminSuperior = adminSuperior;
    }

    // POST /api/cotacoes — register monthly quote
    @PostMapping
    public ResponseEntity<?> registrarCotacao(@RequestBody RegistrarCotacaoRequest request) {
        if (request.produtoId() <= 0) {
            return ResponseEntity.badRequest().body(new ErroResponse("ID do produto inválido."));
        }
        if (request.mes() == null || request.mes().isBlank()) {
            return ResponseEntity.badRequest().body(new ErroResponse("Mês é obrigatório (formato: YYYY-MM)."));
        }
        if (request.precoUnitario() <= 0) {
            return ResponseEntity.badRequest().body(new ErroResponse("Preço deve ser maior que zero."));
        }
        if (request.quantidadeComprada() <= 0) {
            return ResponseEntity.badRequest().body(new ErroResponse("Quantidade deve ser maior que zero."));
        }

        YearMonth mes;
        try {
            mes = YearMonth.parse(request.mes());
        } catch (DateTimeParseException e) {
            return ResponseEntity.badRequest().body(new ErroResponse("Formato de mês inválido. Use YYYY-MM (ex: 2025-03)."));
        }

        Optional<Produto> produtoOpt = estoqueService.buscarProdutoPorId(request.produtoId());
        if (produtoOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErroResponse("Produto não encontrado com id: " + request.produtoId()));
        }

        try {
            CotacaoMensal cotacao = cotacaoService.registrarCotacao(
                    adminSuperior, produtoOpt.get(), mes,
                    request.precoUnitario(), request.quantidadeComprada()
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(CotacaoResponse.from(cotacao));
        } catch (IllegalArgumentException | SecurityException e) {
            return ResponseEntity.badRequest().body(new ErroResponse(e.getMessage()));
        }
    }

    // GET /api/cotacoes/produto/{produtoId}/historico — price history for a product
    @GetMapping("/produto/{produtoId}/historico")
    public ResponseEntity<?> historico(@PathVariable int produtoId) {
        Optional<Produto> produtoOpt = estoqueService.buscarProdutoPorId(produtoId);
        if (produtoOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErroResponse("Produto não encontrado com id: " + produtoId));
        }
        List<CotacaoResponse> historico = cotacaoService.buscarHistorico(produtoOpt.get()).stream()
                .map(CotacaoResponse::from)
                .toList();
        return ResponseEntity.ok(historico);
    }

    // GET /api/cotacoes/produto/{produtoId}/comparar?mes=YYYY-MM
    // Compare the given month with the previous month. If mes is omitted, uses current month.
    @GetMapping("/produto/{produtoId}/comparar")
    public ResponseEntity<?> comparar(@PathVariable int produtoId,
                                       @RequestParam(required = false) String mes) {
        Optional<Produto> produtoOpt = estoqueService.buscarProdutoPorId(produtoId);
        if (produtoOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErroResponse("Produto não encontrado com id: " + produtoId));
        }

        YearMonth mesAtual;
        if (mes != null && !mes.isBlank()) {
            try {
                mesAtual = YearMonth.parse(mes);
            } catch (DateTimeParseException e) {
                return ResponseEntity.badRequest().body(new ErroResponse("Formato inválido. Use YYYY-MM."));
            }
        } else {
            mesAtual = YearMonth.now();
        }

        Optional<ResultadoComparacao> resultado = cotacaoService.compararComMesAnterior(produtoOpt.get(), mesAtual);
        if (resultado.isEmpty()) {
            return ResponseEntity.ok(new SemDadosResponse(
                    "Dados insuficientes para comparação. Verifique se há cotações nos dois meses."));
        }
        return ResponseEntity.ok(ComparacaoResponse.from(resultado.get()));
    }

    // GET /api/cotacoes/relatorio?mes=YYYY-MM — monthly report comparing all products
    @GetMapping("/relatorio")
    public ResponseEntity<?> relatorioMensal(@RequestParam(required = false) String mes) {
        YearMonth mesAlvo;
        if (mes != null && !mes.isBlank()) {
            try {
                mesAlvo = YearMonth.parse(mes);
            } catch (DateTimeParseException e) {
                return ResponseEntity.badRequest().body(new ErroResponse("Formato inválido. Use YYYY-MM."));
            }
        } else {
            mesAlvo = YearMonth.now();
        }

        List<Produto> todosProdutos = estoqueService.listarProdutos();
        List<ComparacaoResponse> resultados = cotacaoService.gerarRelatorioMensal(todosProdutos, mesAlvo)
                .stream()
                .map(ComparacaoResponse::from)
                .toList();

        return ResponseEntity.ok(new RelatorioResponse(mesAlvo.toString(), resultados));
    }

    // ── Records ───────────────────────────────────────────────────────────────

    public record RegistrarCotacaoRequest(
            int produtoId,
            String mes,
            double precoUnitario,
            int quantidadeComprada
    ) {}

    public record CotacaoResponse(
            long id,
            int produtoId,
            String produtoNome,
            String mes,
            double precoUnitario,
            int quantidadeComprada,
            double gastoTotal
    ) {
        public static CotacaoResponse from(CotacaoMensal c) {
            return new CotacaoResponse(
                    c.getId(),
                    c.getProduto().getId(),
                    c.getProduto().getNome(),
                    c.getMesReferencia().toString(),
                    c.getPrecoUnitario(),
                    c.getQuantidadeComprada(),
                    c.calcularGastoTotal()
            );
        }
    }

    public record ComparacaoResponse(
            int produtoId,
            String produtoNome,
            String mesAnterior,
            String mesAtual,
            double precoAnterior,
            double precoAtual,
            double variacaoAbsoluta,
            double variacaoPercentual,
            String tendencia,
            String mensagem
    ) {
        public static ComparacaoResponse from(ResultadoComparacao r) {
            return new ComparacaoResponse(
                    r.getProduto().getId(),
                    r.getProduto().getNome(),
                    r.getMesAnterior().toString(),
                    r.getMesAtual().toString(),
                    r.getPrecoAnterior(),
                    r.getPrecoAtual(),
                    r.getVariacaoAbsoluta(),
                    r.getVariacaoPercentual(),
                    r.getTendencia().name(),
                    r.gerarMensagem()
            );
        }
    }

    public record RelatorioResponse(String mes, List<ComparacaoResponse> comparacoes) {}

    public record SemDadosResponse(String aviso) {}

    public record ErroResponse(String erro) {}
}
