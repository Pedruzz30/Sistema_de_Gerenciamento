package service;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** JWT subject + active DB membership are the only sources of SaaS identity and privileges. */
@Service
public class SaasTenantAccess {
    private final JdbcTemplate jdbc;
    public SaasTenantAccess(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public UUID authenticatedSubject() {
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        if (!(current instanceof JwtAuthenticationToken token) || !current.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "JWT de acesso obrigatorio");
        }
        try {
            return UUID.fromString(token.getToken().getSubject());
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Identidade de usuario invalida");
        }
    }

    public String requireMembership(UUID empresaId, String... roles) {
        UUID userId = authenticatedSubject();
        List<String> found = jdbc.query("""
            select m.papel from stockos_private.membros m
            join stockos_private.empresas e on e.id=m.empresa_id and e.ativa=true
            where m.empresa_id=? and m.auth_user_id=? and m.ativo=true
            """, (rs, i) -> rs.getString("papel"), empresaId, userId);
        if (found.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sem acesso a esta empresa");
        }
        String role = found.get(0);
        if (roles.length > 0 && java.util.Arrays.stream(roles).noneMatch(role::equals)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Permissao insuficiente");
        }
        return role;
    }

    public void requireFilial(UUID empresaId, UUID filialId) {
        List<UUID> found = jdbc.query("""
            select id from stockos_private.filiais
            where id=? and empresa_id=? and ativa=true
            """, (rs, i) -> rs.getObject("id", UUID.class), filialId, empresaId);
        if (found.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Filial nao encontrada");
        }
    }

    public List<Map<String, Object>> empresas() {
        return jdbc.queryForList("""
            select e.id, e.nome, m.papel
            from stockos_private.membros m
            join stockos_private.empresas e on e.id=m.empresa_id and e.ativa=true
            where m.auth_user_id=? and m.ativo=true
            order by e.nome
            """, authenticatedSubject());
    }
}
