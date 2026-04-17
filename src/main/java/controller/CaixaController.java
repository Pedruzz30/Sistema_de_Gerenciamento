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
import repository.CaixaRepository;
import repository.UsuarioRepository;
import service.CaixaService;
import service.VendaItemInput;

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
    public ResponseEntity<List<CaixaResponse>> listarCaixas(
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        ControllerUtils.resolveUserAndRequireAnyPermission(
                ruHeader,
                usuarioRepository,
                "Voce nao tem permissao para visualizar caixas.",
                Permissao.VER_VENDAS,
                Permissao.VER_FINANCAS
        );
        List<CaixaResponse> lista = caixaRepository.listarTodos().stream()
                .map(CaixaResponse::from)
                .toList();
        return ResponseEntity.ok(lista);
    }

    @GetMapping("/metricas")
    public ResponseEntity<?> metricas(
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        ControllerUtils.resolveUserAndRequireAnyPermission(
                ruHeader,
                usuarioRepository,
                "Voce nao tem permissao para visualizar metricas de caixa.",
                Permissao.VER_VENDAS,
                Permissao.VER_FINANCAS
        );

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
    public ResponseEntity<?> buscarCaixaPorNumero(@PathVariable int numero,
                                                  @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        ControllerUtils.resolveUserAndRequireAnyPermission(
                ruHeader,
                usuarioRepository,
                "Voce nao tem permissao para visualizar caixas.",
                Permissao.VER_VENDAS,
                Permissao.VER_FINANCAS
        );
        try {
            Caixa caixa = caixaService.buscarCaixaAberto(numero);
            return ResponseEntity.ok(CaixaResponse.from(caixa));
        } catch (IllegalStateException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{numero}/movimentacoes")
    public ResponseEntity<?> listarMovimentacoesDoCaixa(@PathVariable int numero,
                                                        @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        ControllerUtils.resolveUserAndRequireAnyPermission(
                ruHeader,
                usuarioRepository,
                "Voce nao tem permissao para visualizar movimentacoes de caixa.",
                Permissao.VER_VENDAS,
                Permissao.VER_FINANCAS
        );
        try {
            Caixa caixa = caixaService.buscarCaixaAberto(numero);
            List<MovimentacaoCaixaResponse> movs = caixa.getMovimentacoes().stream()
                    .map(movimentacao -> MovimentacaoCaixaResponse.from(caixa.getId(), caixa.getNumeroCaixa(), movimentacao))
                    .toList();
            return ResponseEntity.ok(movs);
        } catch (IllegalStateException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/sessoes/{caixaId}")
    public ResponseEntity<?> buscarCaixaPorId(@PathVariable long caixaId,
                                              @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        ControllerUtils.resolveUserAndRequireAnyPermission(
                ruHeader,
                usuarioRepository,
                "Voce nao tem permissao para visualizar caixas.",
                Permissao.VER_VENDAS,
                Permissao.VER_FINANCAS
        );
        try {
            Caixa caixa = caixaService.buscarCaixaPorId(caixaId);
            return ResponseEntity.ok(CaixaResponse.from(caixa));
        } catch (IllegalStateException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/sessoes/{caixaId}/movimentacoes")
    public ResponseEntity<?> listarMovimentacoesDoCaixaPorId(@PathVariable long caixaId,
                                                             @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        ControllerUtils.resolveUserAndRequireAnyPermission(
                ruHeader,
                usuarioRepository,
                "Voce nao tem permissao para visualizar movimentacoes de caixa.",
                Permissao.VER_VENDAS,
                Permissao.VER_FINANCAS
        );
        try {
            Caixa caixa = caixaService.buscarCaixaPorId(caixaId);
            List<MovimentacaoCaixaResponse> movs = caixaService.listarMovimentacoesPorCaixaId(caixaId).stream()
                    .map(movimentacao -> MovimentacaoCaixaResponse.from(caixa.getId(), caixa.getNumeroCaixa(), movimentacao))
                    .toList();
            return ResponseEntity.ok(movs);
        } catch (IllegalStateException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/sessoes/{caixaId}/historico")
    public ResponseEntity<?> listarHistoricoPorCaixaId(@PathVariable long caixaId,
                                                       @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        ControllerUtils.resolveUserAndRequireAnyPermission(
                ruHeader,
                usuarioRepository,
                "Voce nao tem permissao para visualizar historico de caixas.",
                Permissao.VER_VENDAS,
                Permissao.VER_FINANCAS
        );
        try {
            List<CaixaResponse> historico = caixaService.buscarHistoricoPorCaixaId(caixaId).stream()
                    .map(CaixaResponse::from)
                    .toList();
            return ResponseEntity.ok(historico);
        } catch (IllegalStateException e) {
            return ResponseEntity.notFound().build();
        }
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
            List<VendaItemInput> itens = request.itens() == null
                    ? List.of()
                    : request.itens().stream()
                    .map(item -> new VendaItemInput(item.produtoId(), item.itemCardapioId(), item.quantidade()))
                    .toList();
            caixaService.registrarVenda(
                    ator,
                    request.numeroCaixa(),
                    BigDecimal.valueOf(request.valor()),
                    request.descricao(),
                    itens
            );
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
        if (request.valorContado() == null) {
            return ResponseEntity.badRequest().body(new ErroResponse("Valor contado e obrigatorio."));
        }
        if (request.valorContado().compareTo(BigDecimal.ZERO) < 0) {
            return ResponseEntity.badRequest().body(new ErroResponse("Valor contado nao pode ser negativo."));
        }
        try {
            Usuario ator = ControllerUtils.resolveUser(ruHeader, usuarioRepository);
            FechamentoCaixa fechamento = caixaService.encerrarCaixa(
                    ator,
                    request.numeroCaixa(),
                    request.valorContado(),
                    request.observacao()
            );
            return ResponseEntity.ok(FechamentoResponse.from(fechamento));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErroResponse(e.getMessage()));
        }
    }

    @GetMapping("/consolidado")
    public ResponseEntity<?> consolidadoDia(
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        ControllerUtils.resolveUserAndRequirePermission(
                ruHeader,
                usuarioRepository,
                Permissao.VER_FINANCAS,
                "Voce nao tem permissao para acessar o consolidado diario."
        );
        String resumo = caixaService.consolidarDia(LocalDate.now());
        return ResponseEntity.ok(new ConsolidadoResponse(LocalDate.now().toString(), resumo));
    }

    private ResponseEntity<?> validarMovimentacao(MovimentacaoRequest request) {
        if (request.numeroCaixa() <= 0) {
            return ResponseEntity.badRequest().body(new ErroResponse("Numero do caixa invalido."));
        }
        boolean possuiItens = request.itens() != null && !request.itens().isEmpty();
        if (!possuiItens && request.valor() <= 0) {
            return ResponseEntity.badRequest().body(new ErroResponse("Valor deve ser maior que zero."));
        }
        if (possuiItens) {
            boolean itemInvalido = request.itens().stream()
                    .anyMatch(item -> item == null || !item.possuiReferenciaValida());
            if (itemInvalido) {
                return ResponseEntity.badRequest().body(new ErroResponse(
                        "Itens da venda devem informar produtoId ou itemCardapioId com quantidade positiva."
                ));
            }
        }
        return null;
    }

    public record AbrirCaixaRequest(int numeroCaixa, double saldoInicial) {}

    public record MovimentacaoRequest(
            int numeroCaixa,
            double valor,
            String descricao,
            List<ItemVendaRequest> itens
    ) {}

    public record ItemVendaRequest(Integer produtoId, Integer itemCardapioId, int quantidade) {
        boolean possuiReferenciaValida() {
            boolean possuiProduto = produtoId != null && produtoId > 0;
            boolean possuiItemCardapio = itemCardapioId != null && itemCardapioId > 0;
            return quantidade > 0 && possuiProduto != possuiItemCardapio;
        }
    }

    public record EncerrarCaixaRequest(
            int numeroCaixa,
            BigDecimal valorContado,
            String observacao
    ) {}

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
                    c.getOperadorAtual() != null ? c.getOperadorAtual().getNomeCompleto() : null,
                    c.getDataAbertura() != null ? c.getDataAbertura().toString() : null,
                    c.getDataEncerramento() != null ? c.getDataEncerramento().toString() : null
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
        public static MovimentacaoCaixaResponse from(long caixaId, int numeroCaixa, MovimentacaoCaixa m) {
            return new MovimentacaoCaixaResponse(
                    caixaId,
                    numeroCaixa,
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
    ) {}

    public record ConsolidadoResponse(String data, String resumo) {}

    public record ErroResponse(String erro) {}
}
