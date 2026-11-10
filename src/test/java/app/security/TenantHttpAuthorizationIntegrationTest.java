package app.security;

import app.tenancy.LegacyApiIsolationGate;
import app.tenancy.TenantMembershipGuard;
import app.tenancy.TenantMembershipGuard.EstabelecimentoDisponivel;
import app.tenancy.TenantMembershipGuard.Papel;
import app.tenancy.TenantScopedCatalogService;
import app.tenancy.TenantScopedStockService;
import controller.TenantCatalogController;
import controller.TenantEstablishmentsController;
import controller.TenantStockController;
import model.Usuario;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import repository.UsuarioRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Real MVC dispatch through legacy deny-by-default gate and the verified-identity
 * filter, with independent per-tenant services stubbed. JwtDecoder signature and
 * claim checks are verified separately by SupabaseJwtCryptographicVerificationTest.
 * Does NOT replace the pending real-Supabase / database / TLS E2E environment.
 */
class TenantHttpAuthorizationIntegrationTest {
    private MockMvc mvc;
    private UsuarioRepository users;
    private TenantMembershipGuard memberships;
    private TenantScopedCatalogService catalog;
    private TenantScopedStockService stock;
    private UUID authSubject;
    private UUID storeA;
    private UUID storeB;

    @BeforeEach
    void setup() {
        users = mock(UsuarioRepository.class);
        memberships = mock(TenantMembershipGuard.class);
        catalog = mock(TenantScopedCatalogService.class);
        stock = mock(TenantScopedStockService.class);
        authSubject = UUID.randomUUID();
        storeA = UUID.randomUUID();
        storeB = UUID.randomUUID();
        mvc = MockMvcBuilders.standaloneSetup(
                new TenantEstablishmentsController(memberships),
                new TenantCatalogController(catalog),
                new TenantStockController(stock))
                .addFilters(new LegacyApiIsolationGate(), new LinkedStockosUserFilter(users))
                .build();
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder getPath(String path) {
        return get(path).servletPath(path);
    }

    private void authenticate(long ru) {
        Usuario linked = mock(Usuario.class);
        when(linked.isAtivo()).thenReturn(true);
        when(linked.getRu()).thenReturn(ru);
        when(users.buscarPorSupabaseAuthUserId(authSubject)).thenReturn(Optional.of(linked));
        Jwt jwt = Jwt.withTokenValue("unit-test-jwt-already-verified")
                .header("alg", "RS256").subject(authSubject.toString())
                .claim("role", "authenticated").build();
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }

    @Test
    void missingJwtDeniedEvenWhenRuIsSpoofed() throws Exception {
        mvc.perform(getPath("/api/v2/estabelecimentos")
                .header("X-User-RU", "1"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(users, memberships, catalog, stock);
    }

    @Test
    void unlinkedJwtCannotEnumerateTenants() throws Exception {
        Jwt jwt = Jwt.withTokenValue("unit-test-jwt-already-verified")
                .header("alg", "RS256").subject(authSubject.toString()).build();
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
        when(users.buscarPorSupabaseAuthUserId(authSubject)).thenReturn(Optional.empty());
        mvc.perform(getPath("/api/v2/estabelecimentos")).andExpect(status().isForbidden());
        verifyNoInteractions(memberships, catalog, stock);
    }

    @Test
    void onlyVerifiedRuIsForwardedAndEachTenantReadIsScoped() throws Exception {
        authenticate(87L);
        when(memberships.listarEstabelecimentosAutorizados(87L))
                .thenReturn(List.of(new EstabelecimentoDisponivel(storeA, "Loja A", Papel.OPERADOR, 4)));
        when(catalog.listarProdutos(storeA, 87L))
                .thenReturn(List.of(new TenantScopedCatalogService.ProdutoResumo(10, "Água")));
        when(stock.listarSaldos(storeA, 87L)).thenReturn(List.of());

        mvc.perform(getPath("/api/v2/estabelecimentos").header("X-User-RU", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(storeA.toString()))
                .andExpect(jsonPath("$[0].ruLocal").value(4));
        mvc.perform(getPath("/api/v2/estabelecimentos/" + storeA + "/catalogo/produtos")
                        .header("X-User-RU", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nome").value("Água"));
        mvc.perform(getPath("/api/v2/estabelecimentos/" + storeA + "/estoque/saldos"))
                .andExpect(status().isOk());

        verify(memberships).listarEstabelecimentosAutorizados(87L);
        verify(catalog).listarProdutos(storeA, 87L);
        verify(stock).listarSaldos(storeA, 87L);
        verifyNoMoreInteractions(memberships, catalog, stock);
        verify(users, times(3)).buscarPorSupabaseAuthUserId(authSubject);
    }

    @Test
    void crossStoreReadIsDeniedEvenWithValidUserAndGuessedTenantId() throws Exception {
        authenticate(87L);
        when(stock.listarSaldos(storeB, 87L)).thenThrow(new SecurityException("No membership"));
        when(catalog.listarProdutos(storeB, 87L)).thenThrow(new SecurityException("No membership"));

        mvc.perform(getPath("/api/v2/estabelecimentos/" + storeB + "/estoque/saldos"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.erro").value("Acesso negado."));
        mvc.perform(getPath("/api/v2/estabelecimentos/" + storeB + "/catalogo/produtos"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.erro").value("Acesso negado."));
        verify(stock).listarSaldos(storeB, 87L);
        verify(catalog).listarProdutos(storeB, 87L);
    }

    @Test
    void legacyFinancialRoutesRemainClosedForAuthenticatedUser() throws Exception {
        authenticate(87L);
        for (String path : new String[]{
                "/api/caixas", "/api/vendas", "/api/movimentacoes",
                "/api/v2/estabelecimentos/" + storeA + "/caixa",
                "/api/v2/estabelecimentos/" + storeA + "/vendas"
        }) {
            mvc.perform(getPath(path)).andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/v2/estabelecimentos").servletPath("/api/v2/estabelecimentos"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(memberships, catalog, stock);
    }
}
