
package app.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import model.Usuario;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import repository.UsuarioRepository;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Bridges an already VERIFIED Supabase JWT to the existing operational
 * user/permission model. Never associates users by editable email/metadata.
 */
public class LinkedStockosUserFilter extends OncePerRequestFilter {
    public static final String VERIFIED_USER_ATTRIBUTE = LinkedStockosUserFilter.class.getName() + ".verifiedUser";
    private final UsuarioRepository usuarioRepository;

    public LinkedStockosUserFilter(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return "OPTIONS".equalsIgnoreCase(request.getMethod())
                || !path.startsWith("/api/")
                || "/api/auth/config".equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication) || !authentication.isAuthenticated()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        final UUID subject;
        try {
            subject = UUID.fromString(jwtAuthentication.getToken().getSubject());
        } catch (RuntimeException e) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        Usuario usuario = usuarioRepository.buscarPorSupabaseAuthUserId(subject)
                .filter(Usuario::isAtivo).orElse(null);
        if (usuario == null) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Usuario nao vinculado ou inativo.");
            return;
        }

        request.setAttribute(VERIFIED_USER_ATTRIBUTE, usuario);

        // Compatibility boundary: existing controllers still expect X-User-RU,
        // but they now receive ONLY the server-derived RU, never the browser's.
        final String verifiedRu = Long.toString(usuario.getRu());
        HttpServletRequest safe = new HttpServletRequestWrapper(request) {
            @Override
            public String getHeader(String name) {
                return "X-User-RU".equalsIgnoreCase(name) ? verifiedRu : super.getHeader(name);
            }

            @Override
            public Enumeration<String> getHeaders(String name) {
                return "X-User-RU".equalsIgnoreCase(name)
                        ? Collections.enumeration(Collections.singleton(verifiedRu))
                        : super.getHeaders(name);
            }

            @Override
            public Enumeration<String> getHeaderNames() {
                Set<String> names = new LinkedHashSet<>();
                Enumeration<String> current = super.getHeaderNames();
                while (current.hasMoreElements()) {
                    String name = current.nextElement();
                    if (!"X-User-RU".equalsIgnoreCase(name)) names.add(name);
                }
                names.add("X-User-RU");
                return Collections.enumeration(names);
            }
        };
        chain.doFilter(safe, response);
    }
}
