package integration;

import app.Main;
import app.security.LinkedStockosUserFilter;
import app.tenancy.LegacyApiIsolationGate;
import app.tenancy.TenantMembershipGuard;
import app.tenancy.TenantScopedCatalogService;
import app.tenancy.TenantScopedStockService;
import controller.TenantCatalogController;
import controller.TenantEstablishmentsController;
import controller.TenantStockController;
import model.Usuario;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import repository.UsuarioRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Two independent authenticated subjects traverse actual HTTP MVC filters,
 * controllers, membership checks and tenant-scoped SQL on disposable
 * PostgreSQL. JWT signing/issuer validation is covered by a separate
 * localhost-JWKS test. Does NOT call the live Supabase Auth service.
 *
 * This test runs in the legacy Spring test application context deliberately:
 * the Supabase startup guard would reject the local superuser/test datasource.
 * The controllers/filters are explicitly assembled in the test MockMvc.
 */
@SpringBootTest(classes = Main.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "stockos.auth.mode=legacy")
@Transactional
class TenantHttpPostgresIsolationIntegrationTest {
    private static final AtomicLong NEXT_CPF = new AtomicLong(89_000_000_000L);

    @Autowired JdbcTemplate jdbc;
    @Autowired TenantMembershipGuard membership;
    @Autowired TenantScopedCatalogService catalog;
    @Autowired TenantScopedStockService stock;

    private MockMvc mvc;
    private UsuarioRepository users;
    private UUID subjectA;
    private UUID subjectB;
    private UUID storeA;
    private UUID storeB;
    private long ruA;
    private long ruB;

    @BeforeEach
    void setup() {
        subjectA = UUID.randomUUID();
        subjectB = UUID.randomUUID();
        storeA = jdbc.queryForObject(
                "insert into estabelecimentos(nome) values ('HTTP loja A') returning id",
                UUID.class);
        storeB = jdbc.queryForObject(
                "insert into estabelecimentos(nome) values ('HTTP loja B') returning id",
                UUID.class);
        ruA = newUser();
        ruB = newUser();
        link(storeA, ruA, "PROPRIETARIO");
        link(storeB, ruB, "PROPRIETARIO");

        users = mock(UsuarioRepository.class);
        when(users.buscarPorSupabaseAuthUserId(subjectA)).thenReturn(Optional.of(linked(ruA)));
        when(users.buscarPorSupabaseAuthUserId(subjectB)).thenReturn(Optional.of(linked(ruB)));
        mvc = MockMvcBuilders.standaloneSetup(
                new TenantEstablishmentsController(membership),
                new TenantCatalogController(catalog),
                new TenantStockController(stock))
                .addFilters(new LegacyApiIsolationGate(), new LinkedStockosUserFilter(users))
                .build();
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void independentIdentitiesSeeOnlyTheirOwnPostgresRows() throws Exception {
        int productA = catalog.cadastrarProduto(storeA, ruA,
                "Produto exclusivo A", 4, 1, new BigDecimal("8.00"));
        int productB = catalog.cadastrarProduto(storeB, ruB,
                "Produto exclusivo B", 5, 1, new BigDecimal("9.00"));

        as(subjectA);
        mvc.perform(get("/api/v2/estabelecimentos")
                        .servletPath("/api/v2/estabelecimentos")
                        .header("X-User-RU", Long.toString(ruB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(storeA.toString()));

        String productsA = "/api/v2/estabelecimentos/" + storeA + "/catalogo/produtos";
        String productsB = "/api/v2/estabelecimentos/" + storeB + "/catalogo/produtos";
        mvc.perform(get(productsA).servletPath(productsA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(productA))
                .andExpect(jsonPath("$[0].nome").value("Produto exclusivo A"));
        mvc.perform(get(productsB).servletPath(productsB))
                .andExpect(status().isForbidden());

        as(subjectB);
        mvc.perform(get("/api/v2/estabelecimentos")
                        .servletPath("/api/v2/estabelecimentos")
                        .header("X-User-RU", Long.toString(ruA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(storeB.toString()));
        mvc.perform(get(productsB).servletPath(productsB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(productB));
        mvc.perform(get(productsA).servletPath(productsA))
                .andExpect(status().isForbidden());
    }

    @Test
    void revokedMembershipImmediatelyDeniesApiEvenWithSameJwtSubject() throws Exception {
        String list = "/api/v2/estabelecimentos/" + storeA + "/catalogo/produtos";
        as(subjectA);
        mvc.perform(get(list).servletPath(list)).andExpect(status().isOk());
        jdbc.update("""
                update usuarios_estabelecimentos set ativo = false
                 where estabelecimento_id = ? and usuario_ru = ?
                """, storeA, ruA);
        mvc.perform(get(list).servletPath(list)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v2/estabelecimentos").servletPath("/api/v2/estabelecimentos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void authSubjectCannotUseFinancialOrLegacyRoutes() throws Exception {
        as(subjectA);
        for (String url : List.of("/api/vendas", "/api/caixas",
                "/api/v2/estabelecimentos/" + storeA + "/caixa")) {
            mvc.perform(get(url).servletPath(url)).andExpect(status().isForbidden());
        }
    }

    private void as(UUID subject) {
        SecurityContextHolder.clearContext();
        Jwt jwt = Jwt.withTokenValue("verified-in-separate-JWKS-test")
                .header("alg", "RS256").subject(subject.toString())
                .claim("role", "authenticated").build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of()));
    }

    private long newUser() {
        return jdbc.queryForObject("""
                insert into usuarios(nome, sobrenome, cpf, senha, ativo, perfil)
                values ('HTTP', 'Isolamento', ?, 'test-only', true, 'OPERADOR')
                returning ru
                """, Long.class, Long.toString(NEXT_CPF.incrementAndGet()));
    }

    private static Usuario linked(long ru) {
        return new Usuario(ru, "HTTP", "Isolamento",
                "52998224725", "test-only", null, Usuario.Perfil.OPERADOR);
    }

    private void link(UUID tenant, long ru, String role) {
        jdbc.update("""
                insert into usuarios_estabelecimentos
                    (estabelecimento_id,usuario_ru,ru_local,papel)
                values (?, ?, 1, ?)
                """, tenant, ru, role);
    }
}
