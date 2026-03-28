package controller;

import model.Caixa;
import model.FechamentoCaixa;
import model.MovimentacaoCaixa;
import model.Usuario;
import repository.CaixaRepository;
import service.CaixaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/caixas")
public class CaixaController {

    private final CaixaService caixaService;
    private final CaixaRepository caixaRepository;
    private final Usuario adminSuperior;

    public CaixaController(CaixaService caixaService,
                           CaixaRepository caixaRepository,
                           Usuario adminSuperior) {
        this.caixaService = caixaService;
        this.caixaRepository = caixaRepository;
        this.adminSuperior = adminSuperior;
    }

    // GET /api/caixas
    @GetMapping
    public ResponseEntity<List<CaixaResponse>> listarCaixas() {
        List<CaixaResponse> lista = caixaRepository.listarTodos().stream()
                .map(CaixaResponse::from)
                .toList();
        return ResponseEntity.ok(lista);
    }

    // GET /api/caixas/{numero}
    @GetMapping("/{numero}")
    public ResponseEntity<?> buscarCaixaPorNumero(@PathVariable int numero) {
        try {
            Caixa caixa = caixaService.buscarCaixaAberto(numero);
            return ResponseEntity.ok(CaixaResponse.from(caixa));
        } catch (IllegalStateException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // GET /api/caixas/{numero}/movimentacoes
    @GetMapping("/{numero}/movimentacoes")
    public ResponseEntity<?> listarMovimentacoesDoCaixa(@PathVariable int numero) {
        List<Caixa> historico = caixaRepository.buscarHistoricoPorNumero(numero);
        if (historico.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        // Returns movements from the most recent caixa for this number
        Caixa maisRecente = historico.stream()
                .max(Comparator.comparingLong(Caixa::getId))
                .orElse(historico.get(0));

        List<MovimentacaoCaixaResponse> movs = maisRecente.getMovimentacoes().stream()
                .map(MovimentacaoCaixaResponse::from)
                .toList();
        return ResponseEntity.ok(movs);
    }

    // POST /api/caixas/abrir
    @PostMapping("/abrir")
    public ResponseEntity<?> abrirCaixa(@RequestBody AbrirCaixaRequest request) {
        if (request.numeroCaixa() <= 0) {
            return ResponseEntity.badRequest().body(new ErroResponse("Número do caixa inválido."));
        }
        if (request.saldoInicial() < 0) {
            return ResponseEntity.badRequest().body(new ErroResponse("Saldo inicial não pode ser negativo."));
        }
        try {
            Caixa caixa = caixaService.abrirCaixa(adminSuperior, request.numeroCaixa(), request.saldoInicial());
            return ResponseEntity.ok(CaixaResponse.from(caixa));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErroResponse(e.getMessage()));
        }
    }

    // POST /api/caixas/venda
    @PostMapping("/venda")
    public ResponseEntity<?> registrarVenda(@RequestBody MovimentacaoRequest request) {
        ResponseEntity<?> erro = validarMovimentacao(request);
        if (erro != null) return erro;
        try {
            caixaService.registrarVenda(adminSuperior, request.numeroCaixa(), request.valor(), request.descricao());
            Caixa caixa = caixaService.buscarCaixaAberto(request.numeroCaixa());
            return ResponseEntity.ok(CaixaResponse.from(caixa));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErroResponse(e.getMessage()));
        }
    }

    // POST /api/caixas/sangria
    @PostMapping("/sangria")
    public ResponseEntity<?> registrarSangria(@RequestBody MovimentacaoRequest request) {
        ResponseEntity<?> erro = validarMovimentacao(request);
        if (erro != null) return erro;
        try {
            caixaService.registrarSangria(adminSuperior, request.numeroCaixa(), request.valor(), request.descricao());
            Caixa caixa = caixaService.buscarCaixaAberto(request.numeroCaixa());
            return ResponseEntity.ok(CaixaResponse.from(caixa));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErroResponse(e.getMessage()));
        }
    }

    // POST /api/caixas/suprimento
    @PostMapping("/suprimento")
    public ResponseEntity<?> registrarSuprimento(@RequestBody MovimentacaoRequest request) {
        ResponseEntity<?> erro = validarMovimentacao(request);
        if (erro != null) return erro;
        try {
            caixaService.registrarSuprimento(adminSuperior, request.numeroCaixa(), request.valor(), request.descricao());
            Caixa caixa = caixaService.buscarCaixaAberto(request.numeroCaixa());
            return ResponseEntity.ok(CaixaResponse.from(caixa));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErroResponse(e.getMessage()));
        }
    }

    // POST /api/caixas/encerrar
    @PostMapping("/encerrar")
    public ResponseEntity<?> encerrarCaixa(@RequestBody EncerrarCaixaRequest request) {
        if (request.numeroCaixa() <= 0) {
            return ResponseEntity.badRequest().body(new ErroResponse("Número do caixa inválido."));
        }
        try {
            FechamentoCaixa fechamento = caixaService.encerrarCaixa(adminSuperior, request.numeroCaixa());
            return ResponseEntity.ok(FechamentoResponse.from(fechamento));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErroResponse(e.getMessage()));
        }
    }

    // GET /api/caixas/consolidado
    @GetMapping("/consolidado")
    public ResponseEntity<ConsolidadoResponse> consolidadoDia() {
        String resumo = caixaService.consolidarDia(LocalDate.now());
        return ResponseEntity.ok(new ConsolidadoResponse(LocalDate.now().toString(), resumo));
    }

    // ── Validação auxiliar ────────────────────────────────────────────────────

    private ResponseEntity<?> validarMovimentacao(MovimentacaoRequest request) {
        if (request.numeroCaixa() <= 0) {
            return ResponseEntity.badRequest().body(new ErroResponse("Número do caixa inválido."));
        }
        if (request.valor() <= 0) {
            return ResponseEntity.badRequest().body(new ErroResponse("Valor deve ser maior que zero."));
        }
        return null;
    }

    // ── Records de request ────────────────────────────────────────────────────

    public record AbrirCaixaRequest(int numeroCaixa, double saldoInicial) {}

    public record MovimentacaoRequest(int numeroCaixa, double valor, String descricao) {}

    public record EncerrarCaixaRequest(int numeroCaixa) {}

    // ── Records de response ───────────────────────────────────────────────────

    public record CaixaResponse(
            long id,
            int numeroCaixa,
            String status,
            double saldoInicial,
            double saldoAtual,
            double totalVendas,
            double totalEntradas,
            double totalSaidas,
            String nomeOperador,
            String dataAbertura,
            String dataEncerramento
    ) {
        public static CaixaResponse from(Caixa c) {
            return new CaixaResponse(
                    c.getId(),
                    c.getNumeroCaixa(),
                    c.getStatus().name(),
                    c.getSaldoInicial(),
                    c.getSaldoAtual(),
                    c.calcularTotalVendas(),
                    c.calcularTotalEntradas(),
                    c.calcularTotalSaidas(),
                    c.getOperadorAtual() != null ? c.getOperadorAtual().getNomeCompleto() : null,
                    c.getDataAbertura() != null ? c.getDataAbertura().toString() : null,
                    c.getDataEncerramento() != null ? c.getDataEncerramento().toString() : null
            );
        }
    }

    public record MovimentacaoCaixaResponse(
            long id,
            String tipo,
            double valor,
            String descricao,
            String dataHora,
            String operador
    ) {
        public static MovimentacaoCaixaResponse from(MovimentacaoCaixa m) {
            return new MovimentacaoCaixaResponse(
                    m.getId(),
                    m.getTipo().name(),
                    m.getValor(),
                    m.getDescricao(),
                    m.getDataHora().toString(),
                    m.getOperador() != null ? m.getOperador().getNomeCompleto() : null
            );
        }
    }

    public record FechamentoResponse(
            int numeroCaixa,
            String data,
            double saldoInicial,
            double totalEntradas,
            double totalSaidas,
            double totalVendas,
            double saldoFinal,
            int quantidadeMovimentacoes,
            String nomeOperador
    ) {
        public static FechamentoResponse from(FechamentoCaixa f) {
            return new FechamentoResponse(
                    f.getNumeroCaixa(),
                    f.getData().toString(),
                    f.getSaldoInicial(),
                    f.getTotalEntradas(),
                    f.getTotalSaidas(),
                    f.getTotalVendas(),
                    f.getSaldoFinal(),
                    f.getQuantidadeMovimentacoes(),
                    f.getNomeOperador()
            );
        }
    }

    public record ConsolidadoResponse(String data, String resumo) {}

    public record ErroResponse(String erro) {}
}
