package controller;

import app.tenancy.TenantScopedCatalogService;
import app.tenancy.TenantScopedCatalogService.FornecedorResumo;
import app.tenancy.TenantScopedCatalogService.ProdutoResumo;
import jakarta.servlet.http.HttpServletRequest;
import model.Usuario;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tenant-aware catalog API, disabled until Supabase JWT authentication
 * (PR #31) exists AND mode is explicitly set to "supabase".
 *
 * This controller never accepts RU, permissions or tenant identity from
 * headers or JSON. The tenant UUID in the path is a selector only;
 * TenantScopedCatalogService checks membership and scopes each SQL query.
 */
@RestController
@ConditionalOnClass(name = "app.security.LinkedStockosUserFilter")
@ConditionalOnProperty(name = "stockos.auth.mode", havingValue = "supabase")
@RequestMapping("/api/v2/estabelecimentos/{estabelecimentoId}/catalogo")
public class TenantCatalogController {
    // Intentionally mirrored from the verified-identity server-side filter.
    // The filter in PR #31 sets this *request attribute*, never a request header.
    private static final String VERIFIED_USER_ATTRIBUTE =
            "app.security.LinkedStockosUserFilter.verifiedUser";

    private final TenantScopedCatalogService catalog;

    public TenantCatalogController(TenantScopedCatalogService catalog) {
        this.catalog = catalog;
    }

    public record NovoProdutoRequest(String nome, int quantidadeInicial,
                                     int quantidadeMinima, BigDecimal precoUnitario) {}
    public record RenomearProdutoRequest(String nome) {}
    public record ProdutoCriadoResponse(int id) {}

    @GetMapping("/produtos")
    public List<ProdutoResumo> produtos(@PathVariable UUID estabelecimentoId,
                                       HttpServletRequest request) {
        long ru = verifiedRu(request);
        return catalog.listarProdutos(estabelecimentoId, ru);
    }

    @GetMapping("/produtos/{produtoId}")
    public ResponseEntity<ProdutoResumo> produto(@PathVariable UUID estabelecimentoId,
                                                 @PathVariable int produtoId,
                                                 HttpServletRequest request) {
        long ru = verifiedRu(request);
        return catalog.buscarProduto(estabelecimentoId, ru, produtoId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/fornecedores")
    public List<FornecedorResumo> fornecedores(@PathVariable UUID estabelecimentoId,
                                               HttpServletRequest request) {
        long ru = verifiedRu(request);
        return catalog.listarFornecedores(estabelecimentoId, ru);
    }

    @PostMapping("/produtos")
    public ResponseEntity<ProdutoCriadoResponse> criarProduto(
            @PathVariable UUID estabelecimentoId,
            @RequestBody NovoProdutoRequest body,
            HttpServletRequest request) {
        long ru = verifiedRu(request);
        int id = catalog.cadastrarProduto(estabelecimentoId, ru, body.nome(),
                body.quantidadeInicial(), body.quantidadeMinima(), body.precoUnitario());
        return ResponseEntity.status(HttpStatus.CREATED).body(new ProdutoCriadoResponse(id));
    }

    @PatchMapping("/produtos/{produtoId}/nome")
    public ResponseEntity<Void> renomearProduto(@PathVariable UUID estabelecimentoId,
                                                @PathVariable int produtoId,
                                                @RequestBody RenomearProdutoRequest body,
                                                HttpServletRequest request) {
        long ru = verifiedRu(request);
        catalog.renomearProduto(estabelecimentoId, ru, produtoId, body.nome());
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, String>> forbidden() {
        // Do not disclose tenant or resource existence in a denial.
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("erro", "Acesso negado."));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalidInput() {
        return ResponseEntity.badRequest().body(Map.of("erro", "Requisicao invalida."));
    }

    private static long verifiedRu(HttpServletRequest request) {
        Object identity = request.getAttribute(VERIFIED_USER_ATTRIBUTE);
        if (!(identity instanceof Usuario user) || !user.isAtivo() || user.getRu() <= 0) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Autenticacao requerida.");
        }
        return user.getRu();
    }
}
