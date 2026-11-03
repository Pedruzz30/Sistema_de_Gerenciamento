package app.security;

import app.tenancy.LegacyApiIsolationGate;
import app.tenancy.TenantMembershipGuard;
import app.tenancy.TenantMembershipGuard.EstabelecimentoDisponivel;
import app.tenancy.TenantMembershipGuard.Papel;
import app.tenancy.TenantScopedCatalogService;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import controller.TenantCatalogController;
import controller.TenantEstablishmentsController;
import model.Usuario;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import repository.UsuarioRepository;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MVC slice with the REAL Spring Security chain, JWT decoder, filter order,
 * legacy API isolation and controllers. Signing keys/JWKS stay on localhost;
 * production Supabase and databases are never accessed or modified.
 *
 * This complements the PostgreSQL tenant isolation tests, but it does not
 * replace a real Supabase session + separated-DB-roles staging E2E.
 */
@WebMvcTest(controllers = {TenantEstablishmentsController.class, TenantCatalogController.class})
@ContextConfiguration(classes = TenantRealSecurityChainIntegrationTest.SliceConfiguration.class)
@Import({StockosSecurityConfig.class, LegacyApiIsolationGate.class,
        TenantCatalogController.class, TenantEstablishmentsController.class})
class TenantRealSecurityChainIntegrationTest {
    // Avoid app.Main's @EnableJpaRepositories in a web-only security slice.
    // The real security filter chain is still wired by StockosSecurityConfig.
    @SpringBootConfiguration
    static class SliceConfiguration {}

    private static final RSAKey TRUSTED_KEY;
    private static final HttpServer JWKS_SERVER;
    private static final String ROOT;
    private static final String ISSUER;

    static {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            TRUSTED_KEY = new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                    .privateKey((RSAPrivateKey) pair.getPrivate()).keyID("tenant-ci-key").build();
            JWKS_SERVER = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            byte[] jwks = new JWKSet(TRUSTED_KEY.toPublicJWK())
                    .toString().getBytes(StandardCharsets.UTF_8);
            JWKS_SERVER.createContext("/auth/v1/.well-known/jwks.json", exchange -> {
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, jwks.length);
                try (var body = exchange.getResponseBody()) {
                    body.write(jwks);
                }
            });
            JWKS_SERVER.start();
            ROOT = "http://127.0.0.1:" + JWKS_SERVER.getAddress().getPort();
            ISSUER = ROOT + "/auth/v1";
        } catch (Exception failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    @DynamicPropertySource
    static void stagingOnly(DynamicPropertyRegistry registry) {
        registry.add("stockos.auth.mode", () -> "supabase");
        registry.add("stockos.auth.supabase-url", () -> ROOT);
    }

    @AfterAll
    static void stopJWKS() {
        JWKS_SERVER.stop(0);
    }

    @Autowired MockMvc mvc;
    @MockitoBean UsuarioRepository users;
    @MockitoBean TenantMembershipGuard memberships;
    @MockitoBean TenantScopedCatalogService catalog;

    private static final UUID USER_A = UUID.fromString("fd422af2-cd52-4710-a7ab-a97b1da2e9fe");
    private static final UUID STORE_A = UUID.fromString("5429ed7d-5cfa-4693-945e-4490ef741fcf");
    private static final UUID STORE_B = UUID.fromString("93574391-4e8c-4b1e-acdd-e6ff5ab6155d");

    @Test
    void authenticatedBearerReachesOnlyVerifiedTenant() throws Exception {
        Usuario user = mock(Usuario.class);
        when(user.isAtivo()).thenReturn(true);
        when(user.getRu()).thenReturn(91L);
        when(users.buscarPorSupabaseAuthUserId(USER_A)).thenReturn(Optional.of(user));
        when(memberships.listarEstabelecimentosAutorizados(91L)).thenReturn(
                List.of(new EstabelecimentoDisponivel(STORE_A, "Loja A", Papel.OPERADOR, 7)));
        when(catalog.listarProdutos(STORE_B, 91L)).thenThrow(new SecurityException("not a member"));

        mvc.perform(get("/api/v2/estabelecimentos")
                .header("Authorization", "Bearer " + token(TRUSTED_KEY, USER_A.toString(),
                        ISSUER, "authenticated", "authenticated", Instant.now().plusSeconds(240)))
                .header("X-User-RU", "99999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(STORE_A.toString()))
                .andExpect(jsonPath("$[0].ruLocal").value(7));

        String guessed = "/api/v2/estabelecimentos/" + STORE_B + "/catalogo/produtos";
        mvc.perform(get(guessed).header("Authorization", "Bearer " +
                token(TRUSTED_KEY, USER_A.toString(), ISSUER,
                        "authenticated", "authenticated", Instant.now().plusSeconds(240))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.erro").value("Acesso negado."));
        verify(memberships).listarEstabelecimentosAutorizados(91L);
        verify(catalog).listarProdutos(STORE_B, 91L);
        verify(users, times(2)).buscarPorSupabaseAuthUserId(USER_A);
    }

    @Test
    void noBearerCannotUseForgedRuHeader() throws Exception {
        mvc.perform(get("/api/v2/estabelecimentos").header("X-User-RU", "91"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(users, memberships, catalog);
    }

    @Test
    void forgedSignatureExpiredOrWrongRoleNeverLooksUpOperationalUser() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        RSAKey untrusted = new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                .privateKey((RSAPrivateKey) pair.getPrivate())
                .keyID("tenant-ci-key").build();
        String[] badTokens = {
                token(untrusted, USER_A.toString(), ISSUER,
                        "authenticated", "authenticated", Instant.now().plusSeconds(240)),
                token(TRUSTED_KEY, USER_A.toString(), ISSUER,
                        "authenticated", "authenticated", Instant.now().minusSeconds(300)),
                token(TRUSTED_KEY, USER_A.toString(), ISSUER,
                        "authenticated", "service_role", Instant.now().plusSeconds(240)),
                token(TRUSTED_KEY, USER_A.toString(), ISSUER,
                        "anon", "authenticated", Instant.now().plusSeconds(240)),
                token(TRUSTED_KEY, USER_A.toString(), "https://wrong.supabase.co/auth/v1",
                        "authenticated", "authenticated", Instant.now().plusSeconds(240))
        };
        for (String signed : badTokens) {
            mvc.perform(get("/api/v2/estabelecimentos")
                    .header("Authorization", "Bearer " + signed))
                    .andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(users, memberships, catalog);
    }

    @Test
    void signedButUnlinkedUserCannotEnumerateTenants() throws Exception {
        UUID unknown = UUID.randomUUID();
        when(users.buscarPorSupabaseAuthUserId(unknown)).thenReturn(Optional.empty());
        mvc.perform(get("/api/v2/estabelecimentos")
                .header("Authorization", "Bearer " + token(TRUSTED_KEY, unknown.toString(),
                        ISSUER, "authenticated", "authenticated", Instant.now().plusSeconds(240))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(memberships, catalog);
    }

    @Test
    void legacyAndFinancialRoutesStayDeniedEvenWithValidJwt() throws Exception {
        Usuario user = mock(Usuario.class);
        when(user.isAtivo()).thenReturn(true);
        when(user.getRu()).thenReturn(91L);
        when(users.buscarPorSupabaseAuthUserId(USER_A)).thenReturn(Optional.of(user));
        String bearer = "Bearer " + token(TRUSTED_KEY, USER_A.toString(), ISSUER,
                "authenticated", "authenticated", Instant.now().plusSeconds(240));

        mvc.perform(get("/api/vendas").header("Authorization", bearer))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v2/estabelecimentos")
                .header("Authorization", bearer))
                .andExpect(status().isForbidden());
        verifyNoInteractions(memberships, catalog);
    }

    private static String token(RSAKey key, String subject, String issuer,
                                String audience, String role, Instant expires) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer).subject(subject).audience(audience)
                .claim("role", role)
                .issueTime(Date.from(Instant.now().minusSeconds(30)))
                .expirationTime(Date.from(expires)).build();
        SignedJWT signed = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(JOSEObjectType.JWT).keyID(key.getKeyID()).build(), claims);
        signed.sign(new RSASSASigner(key));
        return signed.serialize();
    }
}
