package controller;

import model.Fornecedor;
import model.ItemNotaFiscal;
import model.NotaFiscal;
import model.Produto;
import model.Usuario;
import repository.NotaFiscalRepository;
import repository.UsuarioRepository;
import service.EstoqueService;
import service.FornecedorService;
import service.NotaFiscalService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/notas")
public class NotaFiscalController {

    private final NotaFiscalService notaFiscalService;
    private final NotaFiscalRepository notaFiscalRepository;
    private final FornecedorService fornecedorService;
    private final EstoqueService estoqueService;
    private final UsuarioRepository usuarioRepository;
    private final Usuario adminSuperior;

    public NotaFiscalController(NotaFiscalService notaFiscalService,
                                NotaFiscalRepository notaFiscalRepository,
                                FornecedorService fornecedorService,
                                EstoqueService estoqueService,
                                UsuarioRepository usuarioRepository,
                                Usuario adminSuperior) {
        this.notaFiscalService = notaFiscalService;
        this.notaFiscalRepository = notaFiscalRepository;
        this.fornecedorService = fornecedorService;
        this.estoqueService = estoqueService;
        this.usuarioRepository = usuarioRepository;
        this.adminSuperior = adminSuperior;
    }

    // ── GET ───────────────────────────────────────────────────────────────────

    // GET /api/notas
    // By default hides empty drafts (PENDENTE with 0 items).
    // Pass ?includeEmpty=true to include them.
    @GetMapping
    public ResponseEntity<List<NotaResponse>> listarTodas(
            @RequestParam(value = "includeEmpty", defaultValue = "false") boolean includeEmpty) {
        List<NotaFiscal> notas = includeEmpty
                ? notaFiscalService.listarTodas(true)
                : notaFiscalService.listarTodas();
        return ResponseEntity.ok(notas.stream().map(NotaResponse::from).toList());
    }

    // GET /api/notas/{id}
    @GetMapping("/{id}")
    public ResponseEntity<?> buscarPorId(@PathVariable long id) {
        Optional<NotaFiscal> nota = notaFiscalRepository.buscarPorId(id);
        if (nota.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErroResponse("Nota fiscal não encontrada com id: " + id));
        }
        return ResponseEntity.ok(NotaResponse.from(nota.get()));
    }

    // GET /api/notas/fornecedor/{fornecedorId}
    @GetMapping("/fornecedor/{fornecedorId}")
    public ResponseEntity<List<NotaResponse>> listarPorFornecedor(@PathVariable long fornecedorId) {
        return ResponseEntity.ok(
                notaFiscalService.listarNotasPorFornecedor(fornecedorId).stream()
                        .map(NotaResponse::from)
                        .toList()
        );
    }

    // ── ETAPA 1: Abrir nota ───────────────────────────────────────────────────

    // POST /api/notas
    @PostMapping
    public ResponseEntity<?> abrirNota(@RequestBody AbrirNotaRequest request,
                                       @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        if (request.fornecedorId() <= 0) {
            return ResponseEntity.badRequest().body(new ErroResponse("ID do fornecedor inválido."));
        }
        try {
            Usuario ator = ControllerUtils.resolveUser(ruHeader, usuarioRepository, adminSuperior);
            Fornecedor fornecedor = fornecedorService.buscarPorId(request.fornecedorId());
            NotaFiscal nota = notaFiscalService.abrirNota(ator, fornecedor);
            return ResponseEntity.status(HttpStatus.CREATED).body(NotaResponse.from(nota));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErroResponse(e.getMessage()));
        }
    }

    // ── ETAPA 2: Adicionar item ───────────────────────────────────────────────

    // POST /api/notas/{id}/itens
    @PostMapping("/{id}/itens")
    public ResponseEntity<?> adicionarItem(@PathVariable long id,
                                           @RequestBody AdicionarItemRequest request,
                                           @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        if (request.produtoId() <= 0) {
            return ResponseEntity.badRequest().body(new ErroResponse("ID do produto inválido."));
        }
        if (request.quantidade() <= 0) {
            return ResponseEntity.badRequest().body(new ErroResponse("Quantidade deve ser maior que zero."));
        }
        if (request.precoUnitario() < 0) {
            return ResponseEntity.badRequest().body(new ErroResponse("Preço não pode ser negativo."));
        }

        Optional<NotaFiscal> notaOpt = notaFiscalRepository.buscarPorId(id);
        if (notaOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErroResponse("Nota fiscal não encontrada com id: " + id));
        }

        Optional<Produto> produtoOpt = estoqueService.buscarProdutoPorId(request.produtoId());
        if (produtoOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErroResponse("Produto não encontrado com id: " + request.produtoId()));
        }

        try {
            Usuario ator = ControllerUtils.resolveUser(ruHeader, usuarioRepository, adminSuperior);
            notaFiscalService.adicionarItem(ator, notaOpt.get(),
                    produtoOpt.get(), request.quantidade(), request.precoUnitario());
            NotaFiscal notaAtualizada = notaFiscalRepository.buscarPorId(id).orElse(notaOpt.get());
            return ResponseEntity.ok(NotaResponse.from(notaAtualizada));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(new ErroResponse(e.getMessage()));
        }
    }

    // ── ETAPA 3: Confirmar nota ───────────────────────────────────────────────

    // POST /api/notas/{id}/confirmar
    @PostMapping("/{id}/confirmar")
    public ResponseEntity<?> confirmarNota(@PathVariable long id,
                                           @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Optional<NotaFiscal> notaOpt = notaFiscalRepository.buscarPorId(id);
        if (notaOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErroResponse("Nota fiscal não encontrada com id: " + id));
        }
        try {
            Usuario ator = ControllerUtils.resolveUser(ruHeader, usuarioRepository, adminSuperior);
            notaFiscalService.confirmarNota(ator, notaOpt.get());
            NotaFiscal notaAtualizada = notaFiscalRepository.buscarPorId(id).orElse(notaOpt.get());
            return ResponseEntity.ok(NotaResponse.from(notaAtualizada));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(new ErroResponse(e.getMessage()));
        }
    }

    // ── ETAPA 4: Registrar pagamento ──────────────────────────────────────────

    // POST /api/notas/{id}/pagamento
    @PostMapping("/{id}/pagamento")
    public ResponseEntity<?> registrarPagamento(@PathVariable long id,
                                                @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Optional<NotaFiscal> notaOpt = notaFiscalRepository.buscarPorId(id);
        if (notaOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErroResponse("Nota fiscal não encontrada com id: " + id));
        }
        try {
            Usuario ator = ControllerUtils.resolveUser(ruHeader, usuarioRepository, adminSuperior);
            notaFiscalService.registrarPagamento(ator, notaOpt.get());
            NotaFiscal notaAtualizada = notaFiscalRepository.buscarPorId(id).orElse(notaOpt.get());
            return ResponseEntity.ok(NotaResponse.from(notaAtualizada));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(new ErroResponse(e.getMessage()));
        }
    }

    // ── Descartar rascunho (BUG 1) ────────────────────────────────────────────

    // DELETE /api/notas/{id}
    // Only allowed when status == PENDENTE and item count == 0
    @DeleteMapping("/{id}")
    public ResponseEntity<?> descartarRascunho(@PathVariable long id,
                                               @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Optional<NotaFiscal> notaOpt = notaFiscalRepository.buscarPorId(id);
        if (notaOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErroResponse("Nota fiscal não encontrada com id: " + id));
        }
        try {
            Usuario ator = ControllerUtils.resolveUser(ruHeader, usuarioRepository, adminSuperior);
            notaFiscalService.descartarRascunho(ator, notaOpt.get());
            return ResponseEntity.ok(new MensagemResponse("Rascunho descartado com sucesso."));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(new ErroResponse(e.getMessage()));
        }
    }

    // ── Cancelar nota (Improvement 4) ────────────────────────────────────────

    // POST /api/notas/{id}/cancelar
    // Only PENDENTE invoices can be cancelled
    @PostMapping("/{id}/cancelar")
    public ResponseEntity<?> cancelarNota(@PathVariable long id,
                                          @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Optional<NotaFiscal> notaOpt = notaFiscalRepository.buscarPorId(id);
        if (notaOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErroResponse("Nota fiscal não encontrada com id: " + id));
        }
        try {
            Usuario ator = ControllerUtils.resolveUser(ruHeader, usuarioRepository, adminSuperior);
            notaFiscalService.cancelarNota(ator, notaOpt.get());
            NotaFiscal notaAtualizada = notaFiscalRepository.buscarPorId(id).orElse(notaOpt.get());
            return ResponseEntity.ok(NotaResponse.from(notaAtualizada));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(new ErroResponse(e.getMessage()));
        }
    }

    // ── Records ───────────────────────────────────────────────────────────────

    public record AbrirNotaRequest(long fornecedorId) {}

    public record AdicionarItemRequest(int produtoId, int quantidade, double precoUnitario) {}

    public record NotaResponse(
            long id,
            long fornecedorId,
            String fornecedorNome,
            String status,
            String dataEmissao,
            double total,
            List<ItemResponse> itens
    ) {
        public static NotaResponse from(NotaFiscal n) {
            List<ItemResponse> itens = n.getItens().stream()
                    .map(i -> new ItemResponse(
                            i.getProduto().getId(),
                            i.getProduto().getNome(),
                            i.getQuantidade(),
                            i.getPrecoUnitarioNota(),
                            i.calcularSubtotal()
                    ))
                    .toList();
            return new NotaResponse(
                    n.getId(),
                    n.getFornecedor().getId(),
                    n.getFornecedor().getNome(),
                    n.getStatus().name(),
                    n.getDataEmissao().toString(),
                    n.calcularTotal(),
                    itens
            );
        }
    }

    public record ItemResponse(
            int produtoId,
            String produtoNome,
            int quantidade,
            double precoUnitario,
            double subtotal
    ) {}

    public record MensagemResponse(String mensagem) {}

    public record ErroResponse(String erro) {}
}
