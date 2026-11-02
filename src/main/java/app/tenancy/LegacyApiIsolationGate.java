package app.tenancy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Default-deny guard for legacy business APIs while in Supabase mode.
 *
 * Even with verified JWT, legacy controllers use global repositories and
 * are unsafe for multiple tenants. Block them until explicitly migrated.
 *
 * Registered before authentication filters; this does not authenticate
 * requests. The security chain in PR #31 must still enforce verified JWT
 * for ALL allowed /api/v2 endpoints.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "stockos.auth.mode", havingValue = "supabase")
public class LegacyApiIsolationGate extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return !path.startsWith("/api/")
                || "OPTIONS".equalsIgnoreCase(request.getMethod())
                || "/api/auth/config".equals(path)
                || "/api/auth/me".equals(path)
                || isTenantCatalogRoute(path);
    }

    private static boolean isTenantCatalogRoute(String path) {
        // Only the explicitly scoped catalog API is permitted at this stage.
        // New v2 domains require security review and a new allowlist entry.
        if (!path.startsWith("/api/v2/estabelecimentos/")) return false;
        String tail = path.substring("/api/v2/estabelecimentos/".length());
        int slash = tail.indexOf('/');
        if (slash <= 0 || !tail.substring(slash).startsWith("/catalogo")) return false;
        try {
            java.util.UUID.fromString(tail.substring(0, slash));
        } catch (IllegalArgumentException exception) {
            return false;
        }
        String catalogPath = tail.substring(slash);
        return "/catalogo".equals(catalogPath)
                || catalogPath.startsWith("/catalogo/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {
        response.sendError(HttpServletResponse.SC_FORBIDDEN, "API legada indisponivel em modo multi-tenant.");
    }
}
