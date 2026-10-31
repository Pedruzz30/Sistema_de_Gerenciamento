package app.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import model.Usuario;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import repository.UsuarioRepository;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LinkedStockosUserFilterTest {

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/produtos");
        request.setServletPath("/api/produtos");
        return request;
    }

    @Test
    void missingTokenIsRejected() throws Exception {
        UsuarioRepository users = mock(UsuarioRepository.class);
        MockHttpServletResponse response = new MockHttpServletResponse();
        new LinkedStockosUserFilter(users).doFilter(request(), response, (req, res) -> {
            throw new AssertionError("No chain call expected without JWT");
        });
        assertEquals(401, response.getStatus());
    }

    @Test
    void unlinkedSubjectIsRejected() throws Exception {
        UUID subject = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("test-only")
                .header("alg", "RS256").subject(subject.toString()).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        UsuarioRepository users = mock(UsuarioRepository.class);
        when(users.buscarPorSupabaseAuthUserId(subject)).thenReturn(Optional.empty());
        MockHttpServletResponse response = new MockHttpServletResponse();
        new LinkedStockosUserFilter(users).doFilter(request(), response, (req, res) -> {
            throw new AssertionError("Unlinked account must not reach API");
        });
        assertEquals(403, response.getStatus());
    }

    @Test
    void verifiedUserOverridesSpoofedRuHeader() throws Exception {
        UUID subject = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("test-only")
                .header("alg", "RS256").subject(subject.toString()).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        Usuario linked = new Usuario(88, "Operador", "Vinculado", "52998224725",
                "unused-local-password", null, Usuario.Perfil.OPERADOR);
        UsuarioRepository users = mock(UsuarioRepository.class);
        when(users.buscarPorSupabaseAuthUserId(subject)).thenReturn(Optional.of(linked));

        MockHttpServletRequest request = request();
        request.addHeader("X-User-RU", "1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<ServletRequest> forwardedRequest = new AtomicReference<>();
        FilterChain chain = (req, res) -> forwardedRequest.set(req);

        new LinkedStockosUserFilter(users).doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        assertTrue(forwardedRequest.get() instanceof jakarta.servlet.http.HttpServletRequest);
        jakarta.servlet.http.HttpServletRequest safe = (jakarta.servlet.http.HttpServletRequest) forwardedRequest.get();
        assertEquals("88", safe.getHeader("X-User-RU"));
        assertSame(linked, safe.getAttribute(LinkedStockosUserFilter.VERIFIED_USER_ATTRIBUTE));
        verify(users).buscarPorSupabaseAuthUserId(subject);
    }

    @Test
    void inactiveLinkedUserIsRejected() throws Exception {
        UUID subject = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("test-only")
                .header("alg", "RS256").subject(subject.toString()).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        Usuario linked = new Usuario(88, "Operador", "Desativado", "52998224725",
                "unused-local-password", null, Usuario.Perfil.OPERADOR);
        linked.desativar();
        UsuarioRepository users = mock(UsuarioRepository.class);
        when(users.buscarPorSupabaseAuthUserId(subject)).thenReturn(Optional.of(linked));
        MockHttpServletResponse response = new MockHttpServletResponse();
        new LinkedStockosUserFilter(users).doFilter(request(), response, (req, res) -> {
            throw new AssertionError("Inactive account must not reach API");
        });
        assertEquals(403, response.getStatus());
    }
}
