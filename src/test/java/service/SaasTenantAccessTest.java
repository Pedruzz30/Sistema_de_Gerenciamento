package service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SaasTenantAccessTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final SaasTenantAccess tenant = new SaasTenantAccess(jdbc);

    @AfterEach
    void cleanup() { SecurityContextHolder.clearContext(); }

    private void login(UUID id) {
        Jwt jwt = Jwt.withTokenValue("test-token").header("alg", "RS256")
                .subject(id.toString()).claim("role", "authenticated").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @Test
    void mustHaveVerifiedJwtNotCallerId() {
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ResponseStatusException.class,
                tenant::authenticatedSubject).getStatusCode());
        verifyNoInteractions(jdbc);
    }

    @Test
    void crossTenantRequestsWithNoMembershipAreForbidden() {
        login(UUID.randomUUID());
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
                .thenReturn(List.of());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> tenant.requireMembership(UUID.randomUUID()));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void attendantCannotPerformManagerOperations() {
        login(UUID.randomUUID());
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
                .thenReturn(List.of("ATENDENTE"));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> tenant.requireMembership(UUID.randomUUID(), "GERENTE", "PROPRIETARIO"));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void nonMatchingBranchIsNotFound() {
        login(UUID.randomUUID());
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
                .thenReturn(List.of());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> tenant.requireFilial(UUID.randomUUID(), UUID.randomUUID())).getStatusCode());
    }
}
