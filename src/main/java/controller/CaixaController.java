package controller;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import model.Caixa;
import model.FechamentoCaixa;
import model.MovimentacaoCaixa;
import model.TipoMovimentacaoCaixa;
import model.Usuario;
import repository.CaixaRepository;
import repository.UsuarioRepository;
import service.CaixaService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/caixas")
public class CaixaController {

    private final CaixaService caixaService;
    private final CaixaRepository caixaRepository;
    private final UsuarioRepository usuarioRepository;

    public CaixaController(CaixaService caixaService,
                           CaixaRepository caixaRepository,
                           UsuarioRepository usuarioRepository) {
        this.caixaService = caixaService;
        this.caixaRepository = caixaRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @GetMapping
    public ResponseEntity<List<CaixaResponse>> listarCaixas() {
        List<CaixaResponse> lista = caixaRepository.listarTodos().stream()
                .map(CaixaResponse::from)
                .toList();
        return ResponseEntity.ok(lista);
    }

    @GetMapping("/metricas")
    public ResponseEntity<?> metricas(
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Usuario ator = ControllerUtils.resolveUser(ruHeader, usuarioRepository);
        if (ator.getClasse() == null || !ator.getClasse().possuiPermissao(model.Permissao.VER_VENDAS)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErroResponse("Voce nao tem permissao para visualizar metricas de caixa."));
        }

        List<Caixa> todos = caixaRepository.listarTodos();
        BigDecimal totalVendas = todos.stream()
                .map(Caixa::calcularTotalVendas)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        long caixasAbertos = todos.stream()
                .filter(c -> c.getStatus() == Caixa.Status.ABERTO)
                .count();
        long totalMovimentacoesVenda = todos.stream()
                .flatMap(c -> c.getMovimentacoes().stream())
                .filter(m -> m.getTipo() == TipoMovimentacaoCaixa.VENDA)
                .count();
        BigDecimal ticketMedio = totalMovimentacoesVenda > 0
                ? totalVendas.divide(BigDecimal.valueOf(totalMovimentacoesVenda), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        return ResponseEntity.ok(new MetricasResponse(totalVendas, caixasAbertos, totalMovimentacoesVenda, ticketMedio));
    }

    @GetMapping("/{numero}")
    public ResponseEntity<?> buscarCaixaPorNumero(@PathVariable int numero) {
        try {
            Caixa caixa = caixaService.buscarCaixaAberto(numero);
            return ResponseEntity.ok(CaixaResponse.from(caixa));
        } catch (IllegalStateException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{numero}/movimentacoes")
    public ResponseEntity<?> listarMovimentacoesDoCaixa(@PathVariable int numero) {
        List<Caixa> historico = caixaRepository.buscarHistoricoPorNumero(numero);
        if (historico.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Caixa maisRecente = historico.stream()
                .max(Comparator.comparingLong(Caixa::getId))
                .orElse(historico.get(0));

        List<MovimentacaoCaixaResponse> movs = maisRecente.getMovimentacoes().stream()
                .map(MovimentacaoCaixaResponse::from)
                .toList();
        return ResponseEntity.ok(movs);
    }

    @PostMapping("/abrir")
    public ResponseEntity<?> abrirCaixa(@RequestBody AbrirCaixaRequest request,
                                        @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        if (request.numeroCaixa() <= 0) {
            return ResponseEntity.badRequest().body(new ErroResponse("Numero do caixa invalido."));
        }
        if (request.saldoInicial() < 0) {
            return ResponseEntity.badRequest().body(new ErroResponse("Saldo inicial nao pode ser negativo."));
        }
        try {
            Usuario ator = ControllerUtils.resolveUser(ruHeader, usuarioRepository);
            Caixa caixa = caixaService.abrirCaixa(ator, request.numeroCaixa(), request.saldoInicial());
            return ResponseEntity.ok(CaixaResponse.from(caixa));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErroResponse(e.getMessage()));
        }
    }

    @PostMapping("/venda")
    public ResponseEntity<?> registrarVenda(@RequestBody MovimentacaoRequest request,
                                            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        ResponseEntity<?> erro = validarMovimentacao(request);
        if (erro != null) return erro;
        try {
            Usuario ator = ControllerUtils.resolveUser(ruHeader, usuarioRepository);
            caixaService.registrarVenda(ator, request.numeroCaixa(), request.valor(), request.descricao());
            Caixa caixa = caixaService.buscarCaixaAberto(request.numeroCaixa());
            return ResponseEntity.ok(CaixaResponse.from(caixa));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErroResponse(e.getMessage()));
        }
    }

    @PostMapping("/sangria")
    public ResponseEntity<?> registrarSangria(@RequestBody MovimentacaoRequest request,
                                              @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        ResponseEntity<?> erro = validarMovimentacao(request);
        if (erro != null) return erro;
        try {
            Usuario ator = ControllerUtils.resolveUser(ruHeader, usuarioRepository);
            caixaService.registrarSangria(ator, request.numeroCaixa(), request.valor(), request.descricao());
            Caixa caixa = caixaService.buscarCaixaAberto(request.numeroCaixa());
            return ResponseEntity.ok(CaixaResponse.from(caixa));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErroResponse(e.getMessage()));
        }
    }

    @PostMapping("/suprimento")
    public ResponseEntity<?> registrarSuprimento(@RequestBody MovimentacaoRequest request,
                                                 @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        ResponseEntity<?> erro = validarMovimentacao(request);
        if (erro != null) return erro;
        try {
            Usuario ator = ControllerUtils.resolveUser(ruHeader, usuarioRepository);
            caixaService.registrarSuprimento(ator, request.numeroCaixa(), request.valor(), request.descricao());
            Caixa caixa = caixaService.buscarCaixaAberto(request.numeroCaixa());
            return ResponseEntity.ok(CaixaResponse.from(caixa));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErroResponse(e.getMessage()));
        }
    }

    @PostMapping("/encerrar")
    public ResponseEntity<?> encerrarCaixa(@RequestBody EncerrarCaixaRequest request,
                                           @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        if (request.numeroCaixa() <= 0) {
            return ResponseEntity.badRequest().body(new ErroResponse("Numero do caixa invalido."));
        }
        try {
            Usuario ator = ControllerUtils.resolveUser(ruHeader, usuarioRepository);
            FechamentoCaixa fechamento = caixaService.encerrarCaixa(ator, request.numeroCaixa());
            return ResponseEntity.ok(FechamentoResponse.from(fechamento));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErroResponse(e.getMessage()));
        }
    }

    @GetMapping("/consolidado")
    public ResponseEntity<?> consolidadoDia(
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Usuario ator = ControllerUtils.resolveUser(ruHeader, usuarioRepository);
        if (ator.getClasse() == null || !ator.getClasse().possuiPermissao(model.Permissao.VER_FINANCAS)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErroResponse("Voce nao tem permissao para acessar o consolidado diario."));
        }
        String resumo = caixaService.consolidarDia(LocalDate.now());
        return ResponseEntity.ok(new ConsolidadoResponse(LocalDate.now().toString(), resumo));
    }

    private ResponseEntity<?> validarMovimentacao(MovimentacaoRequest request) {
        if (request.numeroCaixa() <= 0) {
            return ResponseEntity.badRequest().body(new ErroResponse("Numero do caixa invalido."));
        }
        if (request.valor() <= 0) {
            return ResponseEntity.badRequest().body(new ErroResponse("Valor deve ser maior que zero."));
        }
        return null;
    }

    public record AbrirCaixaRequest(int numeroCaixa, double saldoInicial) {}

    public record MovimentacaoRequest(int numeroCaixa, double valor, String descricao) {}

    public record EncerrarCaixaRequest(int numeroCaixa) {}

    public record CaixaResponse(
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
            BigDecimal valor,
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
            BigDecimal saldoInicial,
            BigDecimal totalEntradas,
            BigDecimal totalSaidas,
            BigDecimal totalVendas,
            BigDecimal saldoFinal,
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

    public record MetricasResponse(
            BigDecimal totalVendas,
            long caixasAbertos,
            long totalMovimentacoesVenda,
            BigDecimal ticketMedio
    ) {}

    public record ConsolidadoResponse(String data, String resumo) {}

    public record ErroResponse(String erro) {}
}
