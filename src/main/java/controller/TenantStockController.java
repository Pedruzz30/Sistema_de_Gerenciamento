package controller;

import app.tenancy.TenantScopedStockService;
import app.tenancy.TenantScopedStockService.MovimentoResumo;
import app.tenancy.TenantScopedStockService.SaldoResumo;
import jakarta.servlet.http.HttpServletRequest;
import model.Usuario;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only tenant stock API. Never accepts employee RU from a header.
 * Activated exclusively alongside the Supabase JWT verified-identity filter.
 *
 * Legacy stock mutations still remain blocked in Supabase mode until each
 * write operation and transaction has been migrated and audited.
 */
@RestController
@ConditionalOnClass(name = "app.security.LinkedStockosUserFilter")
@ConditionalOnProperty(name = "stockos.auth.mode", havingValue = "supabase")
@RequestMapping("/api/v2/estabelecimentos/{estabelecimentoId}/estoque")
public class TenantStockController {
    private static final String VERIFIED_USER_ATTRIBUTE =
            "app.security.LinkedStockosUserFilter.verifiedUser";

    private final TenantScopedStockService stock;

    public TenantStockController(TenantScopedStockService stock) {
        this.stock = stock;
    }

    @GetMapping("/saldos")
    public List<SaldoResumo> saldos(@PathVariable UUID estabelecimentoId,
                                    HttpServletRequest request) {
        return stock.listarSaldos(estabelecimentoId, verifiedRu(request));
    }

    @GetMapping("/movimentos")
    public List<MovimentoResumo> movimentos(@PathVariable UUID estabelecimentoId,
                                            HttpServletRequest request) {
        return stock.listarMovimentos(estabelecimentoId, verifiedRu(request));
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, String>> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("erro", "Acesso negado."));
    }

    private static long verifiedRu(HttpServletRequest request) {
        Object identity = request.getAttribute(VERIFIED_USER_ATTRIBUTE);
        if (!(identity instanceof Usuario user) || !user.isAtivo() || user.getRu() <= 0) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Autenticacao requerida.");
        }
        return user.getRu();
    }
}
