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
import java.util.Locale;
import java.util.UUID;

/**
 * Default-deny guard for legacy business APIs while in Supabase mode.
 *
 * Only already-reviewed tenant routes and their exact HTTP methods are allowed.
 * Broad path prefixes could accidentally expose future controllers before their
 * tenant authorization and SQL scopes have been audited.
 *
 * This is NOT authentication: Spring Security still verifies the bearer JWT,
 * and each tenant-scoped service must enforce membership and row isolation.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "stockos.auth.mode", havingValue = "supabase")
public class LegacyApiIsolationGate extends OncePerRequestFilter {

    private static final String TENANT_PREFIX = "/api/v2/estabelecimentos/";
    private static final String PRODUCT_PREFIX = "/catalogo/produtos/";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        String method = request.getMethod().toUpperCase(Locale.ROOT);

        if (!path.startsWith("/api/") || "OPTIONS".equals(method)) {
            return true;
        }
        if ("GET".equals(method) &&
                ("/api/auth/config".equals(path) || "/api/auth/me".equals(path))) {
            return true;
        }

        // Discovery is strictly GET and derives identity from verified JWT.
        if ("GET".equals(method) && "/api/v2/estabelecimentos".equals(path)) {
            return true;
        }

        String scopedPath = scopedPathForValidTenant(path);
        if (scopedPath == null) {
            return false;
        }

        return switch (method) {
            case "GET" -> "/catalogo/produtos".equals(scopedPath)
                    || "/catalogo/fornecedores".equals(scopedPath)
                    || isSingleProductPath(scopedPath)
                    || "/estoque/saldos".equals(scopedPath)
                    || "/estoque/movimentos".equals(scopedPath);
            case "POST" -> "/catalogo/produtos".equals(scopedPath);
            case "PATCH" -> scopedPath.endsWith("/nome")
                    && isSingleProductPath(scopedPath.substring(0, scopedPath.length() - "/nome".length()));
            default -> false;
        };
    }

    private static String scopedPathForValidTenant(String path) {
        if (!path.startsWith(TENANT_PREFIX)) {
            return null;
        }
        String tail = path.substring(TENANT_PREFIX.length());
        int slash = tail.indexOf('/');
        if (slash <= 0) {
            return null;
        }
        String tenantId = tail.substring(0, slash);
        try {
            UUID parsed = UUID.fromString(tenantId);
            if (!parsed.toString().equalsIgnoreCase(tenantId)) {
                return null;
            }
        } catch (IllegalArgumentException exception) {
            return null;
        }
        return tail.substring(slash);
    }

    private static boolean isSingleProductPath(String path) {
        if (!path.startsWith(PRODUCT_PREFIX)) {
            return false;
        }
        String id = path.substring(PRODUCT_PREFIX.length());
        return !id.isEmpty() && id.chars().allMatch(ch -> ch >= '0' && ch <= '9');
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {
        response.sendError(HttpServletResponse.SC_FORBIDDEN, "API legada indisponivel em modo multi-tenant.");
    }
}
