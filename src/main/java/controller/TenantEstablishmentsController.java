package controller;

import app.security.LinkedStockosUserFilter;
import app.tenancy.TenantMembershipGuard;
import app.tenancy.TenantMembershipGuard.EstabelecimentoDisponivel;
import jakarta.servlet.http.HttpServletRequest;
import model.Usuario;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Read-only tenant discovery for staging. The verified JWT-linked identity is
 * the sole source for RU; a client-supplied tenant is never authorization.
 */
@RestController
@ConditionalOnProperty(name = "stockos.auth.mode", havingValue = "supabase")
@RequestMapping("/api/v2/estabelecimentos")
public class TenantEstablishmentsController {
    private final TenantMembershipGuard membership;

    public TenantEstablishmentsController(TenantMembershipGuard membership) {
        this.membership = membership;
    }

    @GetMapping
    public List<EstabelecimentoDisponivel> meusEstabelecimentos(HttpServletRequest request) {
        Object value = request.getAttribute(LinkedStockosUserFilter.VERIFIED_USER_ATTRIBUTE);
        if (!(value instanceof Usuario user) || !user.isAtivo() || user.getRu() <= 0) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Autenticacao requerida.");
        }
        return membership.listarEstabelecimentosAutorizados(user.getRu());
    }
}
