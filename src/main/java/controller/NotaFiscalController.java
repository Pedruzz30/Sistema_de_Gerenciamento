package controller;

import model.Fornecedor;
import model.ItemNotaFiscal;
import model.NotaFiscal;
import model.Permissao;
import model.Produto;
import model.Usuario;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import repository.NotaFiscalRepository;
import repository.UsuarioRepository;
import service.EstoqueService;
import service.FornecedorService;
import service.NotaFiscalService;

import java.math.BigDecimal;
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

    public NotaFiscalController(NotaFiscalService notaFiscalService,
                                NotaFiscalRepository notaFiscalRepository,
                                FornecedorService fornecedorService,
                                EstoqueService estoqueService,
                                UsuarioRepository usuarioRepository) {
        this.notaFiscalService = notaFiscalService;
        this.notaFiscalRepository = notaFiscalRepository;
        this.fornecedorService = fornecedorService;
        this.estoqueService = estoqueService;
        this.usuarioRepository = usuarioRepository;
    }

    @GetMapping
    public ResponseEntity<List<NotaResponse>> listarTodas(
            @RequestParam(value = "includeEmpty", defaultValue = "false") boolean includeEmpty,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        ControllerUtils.resolveUserAndRequirePermission(
                ruHeader,
                usuarioRepository,
                Permissao.VER_COMPRAS,
                "Voce nao tem permissao para visualizar notas fiscais."
        );
        List<NotaFiscal> notas = includeEmpty
                ? notaFiscalService.listarTodas(true)
                : notaFiscalService.listarTodas();
        return ResponseEntity.ok(notas.stream().map(NotaResponse::from).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> buscarPorId(@PathVariable long id,
                                         @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        ControllerUtils.resolveUserAndRequirePermission(
                ruHeader,
                usuarioRepository,
                Permissao.VER_COMPRAS,
                "Voce nao tem permissao para visualizar notas fiscais."
        );
        Optional<NotaFiscal> nota = notaFiscalRepository.buscarPorId(id);
        if (nota.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErroResponse("Nota fiscal nao encontrada com id: " + id));
        }
        return ResponseEntity.ok(NotaResponse.from(nota.get()));
    }

    @GetMapping("/fornecedor/{fornecedorId}")
    public ResponseEntity<List<NotaResponse>> listarPorFornecedor(@PathVariable long fornecedorId,
                                                                  @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        ControllerUtils.resolveUserAndRequirePermission(
                ruHeader,
                usuarioRepository,
                Permissao.VER_COMPRAS,
                "Voce nao tem permissao para visualizar notas fiscais."
        );
        return ResponseEntity.ok(
                notaFiscalService.listarNotasPorFornecedor(fornecedorId).stream()
                        .map(NotaResponse::from)
                        .toList()
        );
    }

    @PostMapping
    public ResponseEntity<?> abrirNota(@RequestBody AbrirNotaRequest request,
                                       @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        if (request.fornecedorId() <= 0) {
            return ResponseEntity.badRequest().body(new ErroResponse("ID do fornecedor invalido."));
        }
        try {
            Usuario ator = ControllerUtils.resolveUser(ruHeader, usuarioRepository);
            Fornecedor fornecedor = fornecedorService.buscarPorId(request.fornecedorId());
            NotaFiscal nota = notaFiscalService.abrirNota(ator, fornecedor);
            return ResponseEntity.status(HttpStatus.CREATED).body(NotaResponse.from(nota));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErroResponse(e.getMessage()));
        }
    }

    @PostMapping("/{id}/itens")
    public ResponseEntity<?> adicionarItem(@PathVariable long id,
                                           @RequestBody AdicionarItemRequest request,
                                           @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        if (request.quantidade() <= 0) {
            return ResponseEntity.badRequest().body(new ErroResponse("Quantidade deve ser maior que zero."));
        }
        if (request.precoUnitario() == null) {
            return ResponseEntity.badRequest().body(new ErroResponse("Preco unitario e obrigatorio."));
        }
        if (request.precoUnitario().compareTo(BigDecimal.ZERO) < 0) {
            return ResponseEntity.badRequest().body(new ErroResponse("Preco unitario nao pode ser negativo."));
        }

        boolean temProdutoExistente = request.produtoId() != null && request.produtoId() > 0;
        boolean temNovoProduto = request.novoProduto() != null;
        if (temProdutoExistente == temNovoProduto) {
            return ResponseEntity.badRequest().body(new ErroResponse(
                    "Cada item deve informar um produto existente ou um novo produto."
            ));
        }

        Optional<NotaFiscal> notaOpt = notaFiscalRepository.buscarPorId(id);
        if (notaOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErroResponse("Nota fiscal nao encontrada com id: " + id));
        }

        try {
            Usuario ator = ControllerUtils.resolveUser(ruHeader, usuarioRepository);

            if (temProdutoExistente) {
                Optional<Produto> produtoOpt = estoqueService.buscarProdutoPorId(request.produtoId());
                if (produtoOpt.isEmpty()) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(new ErroResponse("Produto nao encontrado com id: " + request.produtoId()));
                }

                notaFiscalService.adicionarItem(
                        ator,
                        notaOpt.get(),
                        produtoOpt.get(),
                        request.quantidade(),
                        request.precoUnitario()
                );
            } else {
                notaFiscalService.adicionarItem(
                        ator,
                        notaOpt.get(),
                        new NotaFiscalService.NovoProdutoInput(
                                request.novoProduto().nome(),
                                request.novoProduto().quantidadeMinima(),
                                request.novoProduto().precoUnitarioBase(),
                                request.novoProduto().categoriaEstoque()
                        ),
                        request.quantidade(),
                        request.precoUnitario()
                );
            }

            NotaFiscal notaAtualizada = notaFiscalRepository.buscarPorId(id).orElse(notaOpt.get());
            return ResponseEntity.ok(NotaResponse.from(notaAtualizada));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(new ErroResponse(e.getMessage()));
        }
    }

    @PostMapping("/{id}/confirmar")
    public ResponseEntity<?> confirmarNota(@PathVariable long id,
                                           @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Optional<NotaFiscal> notaOpt = notaFiscalRepository.buscarPorId(id);
        if (notaOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErroResponse("Nota fiscal nao encontrada com id: " + id));
        }
        try {
            Usuario ator = ControllerUtils.resolveUser(ruHeader, usuarioRepository);
            notaFiscalService.confirmarNota(ator, notaOpt.get());
            NotaFiscal notaAtualizada = notaFiscalRepository.buscarPorId(id).orElse(notaOpt.get());
            return ResponseEntity.ok(NotaResponse.from(notaAtualizada));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(new ErroResponse(e.getMessage()));
        }
    }

    @PostMapping("/{id}/pagamento")
    public ResponseEntity<?> registrarPagamento(@PathVariable long id,
                                                @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Optional<NotaFiscal> notaOpt = notaFiscalRepository.buscarPorId(id);
        if (notaOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErroResponse("Nota fiscal nao encontrada com id: " + id));
        }
        try {
            Usuario ator = ControllerUtils.resolveUser(ruHeader, usuarioRepository);
            notaFiscalService.registrarPagamento(ator, notaOpt.get());
            NotaFiscal notaAtualizada = notaFiscalRepository.buscarPorId(id).orElse(notaOpt.get());
            return ResponseEntity.ok(NotaResponse.from(notaAtualizada));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(new ErroResponse(e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> descartarRascunho(@PathVariable long id,
                                               @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Optional<NotaFiscal> notaOpt = notaFiscalRepository.buscarPorId(id);
        if (notaOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErroResponse("Nota fiscal nao encontrada com id: " + id));
        }
        try {
            Usuario ator = ControllerUtils.resolveUser(ruHeader, usuarioRepository);
            notaFiscalService.descartarRascunho(ator, notaOpt.get());
            return ResponseEntity.ok(new MensagemResponse("Rascunho descartado com sucesso."));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(new ErroResponse(e.getMessage()));
        }
    }

    @PostMapping("/{id}/cancelar")
    public ResponseEntity<?> cancelarNota(@PathVariable long id,
                                          @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Optional<NotaFiscal> notaOpt = notaFiscalRepository.buscarPorId(id);
        if (notaOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErroResponse("Nota fiscal nao encontrada com id: " + id));
        }
        try {
            Usuario ator = ControllerUtils.resolveUser(ruHeader, usuarioRepository);
            notaFiscalService.cancelarNota(ator, notaOpt.get());
            NotaFiscal notaAtualizada = notaFiscalRepository.buscarPorId(id).orElse(notaOpt.get());
            return ResponseEntity.ok(NotaResponse.from(notaAtualizada));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(new ErroResponse(e.getMessage()));
        }
    }

    public record AbrirNotaRequest(long fornecedorId) {}

    public record AdicionarItemRequest(
            Integer produtoId,
            NovoProdutoRequest novoProduto,
            int quantidade,
            BigDecimal precoUnitario
    ) {}

    public record NovoProdutoRequest(
            String nome,
            Integer quantidadeMinima,
            BigDecimal precoUnitarioBase,
            String categoriaEstoque
    ) {}

    public record NotaResponse(
            long id,
            long fornecedorId,
            String fornecedorNome,
            String status,
            String dataEmissao,
            BigDecimal total,
            List<ItemResponse> itens
    ) {
        public static NotaResponse from(NotaFiscal n) {
            List<ItemResponse> itens = n.getItens().stream()
                    .map(ItemResponse::from)
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
            BigDecimal precoUnitario,
            BigDecimal subtotal
    ) {
        public static ItemResponse from(ItemNotaFiscal item) {
            return new ItemResponse(
                    item.getProduto().getId(),
                    item.getProduto().getNome(),
                    item.getQuantidade(),
                    item.getPrecoUnitarioNota(),
                    item.calcularSubtotal()
            );
        }
    }

    public record MensagemResponse(String mensagem) {}

    public record ErroResponse(String erro) {}
}
