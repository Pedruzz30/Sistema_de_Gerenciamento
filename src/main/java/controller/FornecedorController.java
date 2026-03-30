package controller;

import model.Fornecedor;
import model.Produto;
import model.Usuario;
import repository.UsuarioRepository;
import service.EstoqueService;
import service.FornecedorService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/fornecedores")
public class FornecedorController {

    private final FornecedorService fornecedorService;
    private final EstoqueService estoqueService;
    private final UsuarioRepository usuarioRepository;
    private final Usuario adminSuperior;

    public FornecedorController(FornecedorService fornecedorService,
                                EstoqueService estoqueService,
                                UsuarioRepository usuarioRepository,
                                Usuario adminSuperior) {
        this.fornecedorService = fornecedorService;
        this.estoqueService = estoqueService;
        this.usuarioRepository = usuarioRepository;
        this.adminSuperior = adminSuperior;
    }

    // GET /api/fornecedores
    @GetMapping
    public ResponseEntity<List<FornecedorResponse>> listarTodos() {
        return ResponseEntity.ok(
                fornecedorService.listarTodos().stream()
                        .map(FornecedorResponse::from)
                        .toList()
        );
    }

    // GET /api/fornecedores/ativos
    @GetMapping("/ativos")
    public ResponseEntity<List<FornecedorResponse>> listarAtivos() {
        return ResponseEntity.ok(
                fornecedorService.listarAtivos().stream()
                        .map(FornecedorResponse::from)
                        .toList()
        );
    }

    // GET /api/fornecedores/{id}
    @GetMapping("/{id}")
    public ResponseEntity<?> buscarPorId(@PathVariable long id) {
        try {
            Fornecedor f = fornecedorService.buscarPorId(id);
            return ResponseEntity.ok(FornecedorResponse.from(f));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErroResponse(e.getMessage()));
        }
    }

    // POST /api/fornecedores
    @PostMapping
    public ResponseEntity<?> cadastrar(@RequestBody CadastroFornecedorRequest request,
                                       @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        if (request.nome() == null || request.nome().isBlank()) {
            return ResponseEntity.badRequest().body(new ErroResponse("Nome é obrigatório."));
        }
        if (request.cnpj() == null || request.cnpj().isBlank()) {
            return ResponseEntity.badRequest().body(new ErroResponse("CNPJ é obrigatório."));
        }
        try {
            Usuario ator = ControllerUtils.resolveUser(ruHeader, usuarioRepository, adminSuperior);
            Fornecedor f = fornecedorService.cadastrarFornecedor(
                    ator,
                    request.nome(),
                    request.cnpj(),
                    request.telefone() != null ? request.telefone() : ""
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(FornecedorResponse.from(f));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErroResponse(e.getMessage()));
        }
    }

    // POST /api/fornecedores/{id}/vincular-produto
    @PostMapping("/{id}/vincular-produto")
    public ResponseEntity<?> vincularProduto(@PathVariable long id,
                                              @RequestBody VincularProdutoRequest request,
                                              @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        if (request.produtoId() <= 0) {
            return ResponseEntity.badRequest().body(new ErroResponse("ID do produto inválido."));
        }
        Optional<Produto> produto = estoqueService.buscarProdutoPorId(request.produtoId());
        if (produto.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErroResponse("Produto não encontrado com id: " + request.produtoId()));
        }
        try {
            Usuario ator = ControllerUtils.resolveUser(ruHeader, usuarioRepository, adminSuperior);
            fornecedorService.vincularProduto(ator, id, produto.get());
            Fornecedor f = fornecedorService.buscarPorId(id);
            return ResponseEntity.ok(FornecedorResponse.from(f));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErroResponse(e.getMessage()));
        }
    }

    // PUT /api/fornecedores/{id}/desativar
    @PutMapping("/{id}/desativar")
    public ResponseEntity<?> desativar(@PathVariable long id,
                                       @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        try {
            Usuario ator = ControllerUtils.resolveUser(ruHeader, usuarioRepository, adminSuperior);
            fornecedorService.desativarFornecedor(ator, id);
            return ResponseEntity.ok(new MensagemResponse("Fornecedor desativado com sucesso."));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(new ErroResponse(e.getMessage()));
        }
    }

    // PUT /api/fornecedores/{id}/reativar
    @PutMapping("/{id}/reativar")
    public ResponseEntity<?> reativar(@PathVariable long id,
                                      @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        try {
            Usuario ator = ControllerUtils.resolveUser(ruHeader, usuarioRepository, adminSuperior);
            fornecedorService.reativarFornecedor(ator, id);
            return ResponseEntity.ok(new MensagemResponse("Fornecedor reativado com sucesso."));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(new ErroResponse(e.getMessage()));
        }
    }

    // ── Records ───────────────────────────────────────────────────────────────

    public record CadastroFornecedorRequest(String nome, String cnpj, String telefone) {}

    public record VincularProdutoRequest(int produtoId) {}

    public record FornecedorResponse(
            long id,
            String nome,
            String cnpj,
            String telefone,
            boolean ativo,
            List<ProdutoVinculadoResponse> produtos
    ) {
        public static FornecedorResponse from(Fornecedor f) {
            List<ProdutoVinculadoResponse> produtos = f.getProdutos().stream()
                    .map(p -> new ProdutoVinculadoResponse(p.getId(), p.getNome(), p.getPrecoUnitario()))
                    .toList();
            return new FornecedorResponse(f.getId(), f.getNome(), f.getCnpj(),
                    f.getTelefone(), f.isAtivo(), produtos);
        }
    }

    public record ProdutoVinculadoResponse(int id, String nome, double precoUnitario) {}

    public record MensagemResponse(String mensagem) {}

    public record ErroResponse(String erro) {}
}
