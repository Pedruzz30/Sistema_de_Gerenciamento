package controller;

import model.Usuario;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import repository.InMemoryUsuarioRepository;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ControllerUtilsJwtTest {
    private final InMemoryUsuarioRepository repo = new InMemoryUsuarioRepository();

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(UUID uid) {
        Jwt jwt = Jwt.withTokenValue("unit-test-token")
                .header("alg", "RS256")
                .subject(uid.toString())
                .claim("aud", "authenticated")
                .claim("role", "authenticated")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @Test
    void jwtIdentityIgnoresSpoofedRuHeader() {
        UUID uid = UUID.randomUUID();
        Usuario linked = new Usuario(0, "Ana", "Caixa", "935.411.347-80", "dev-only", null, Usuario.Perfil.OPERADOR);
        linked.vincularAuthUserId(uid);
        Usuario persisted = repo.salvar(linked);
        repo.salvar(new Usuario(0, "Maria", "Gerente", "111.444.777-35", "dev-only", null, Usuario.Perfil.ADMIN));
        authenticate(uid);

        Usuario resolved = ControllerUtils.resolveUser("2", repo);
        assertEquals(persisted.getRu(), resolved.getRu());
        assertNotEquals(2, resolved.getRu());
    }

    @Test
    void unknownOrInactiveJwtIdentityIsDenied() {
        UUID uid = UUID.randomUUID();
        authenticate(uid);
        assertThrows(SecurityException.class, () -> ControllerUtils.resolveUser("1", repo));

        Usuario employee = new Usuario(0, "Ana", "Caixa", "935.411.347-80", "dev-only", null, Usuario.Perfil.OPERADOR);
        employee.vincularAuthUserId(uid);
        employee.desativar();
        repo.salvar(employee);
        assertThrows(SecurityException.class, () -> ControllerUtils.resolveUser("1", repo));
    }

    @Test
    void noJwtRetainsLegacyLocalDevelopmentBehavior() {
        Usuario legacy = repo.salvar(new Usuario(0, "Ana", "Caixa", "935.411.347-80", "dev-only", null, Usuario.Perfil.OPERADOR));
        assertEquals(legacy.getRu(), ControllerUtils.resolveUser(String.valueOf(legacy.getRu()), repo).getRu());
        assertThrows(SecurityException.class, () -> ControllerUtils.resolveUser(null, repo));
    }

    @Test
    void clonedUserKeepsLinkedAuthIdentity() {
        UUID uid = UUID.randomUUID();
        Usuario employee = new Usuario(0, "Ana", "Caixa", "935.411.347-80", "dev-only", null, Usuario.Perfil.OPERADOR);
        employee.vincularAuthUserId(uid);
        assertEquals(uid, employee.comRu(12).getAuthUserId());
    }
}
