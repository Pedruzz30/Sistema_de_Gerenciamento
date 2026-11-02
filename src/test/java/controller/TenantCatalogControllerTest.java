package controller;

import app.tenancy.TenantScopedCatalogService;
import jakarta.servlet.http.HttpServletRequest;
import model.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * These unit tests exercise the fail-closed boundary even on a branch
 * without the pending Supabase Auth implementation.
 */
class TenantCatalogControllerTest {
    private static final String VERIFIED_USER_ATTRIBUTE =
            "app.security.LinkedStockosUserFilter.verifiedUser";

    private TenantScopedCatalogService catalog;
    private TenantCatalogController controller;
    private MockHttpServletRequest request;
    private UUID estabelecimento;

    @BeforeEach
    void setUp() {
        catalog = mock(TenantScopedCatalogService.class);
        controller = new TenantCatalogController(catalog);
        request = new MockHttpServletRequest();
        estabelecimento = UUID.randomUUID();
    }

    @Test
    void rejeitaAusenciaDeIdentidadeVerificadaMesmoComRuNoCabecalho() {
        request.addHeader("X-User-RU", "1");
        request.addHeader("X-Estabelecimento-ID", estabelecimento.toString());
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.produtos(estabelecimento, request));
        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatusCode());
        verifyNoInteractions(catalog);
    }

    @Test
    void rejeitaIdentidadeForjadaEmHeaderEStringComoAtributo() {
        request.addHeader("X-User-RU", "1");
        request.setAttribute(VERIFIED_USER_ATTRIBUTE, "1");
        assertThrows(ResponseStatusException.class,
                () -> controller.fornecedores(estabelecimento, request));
        verifyNoInteractions(catalog);
    }

    @Test
    void usaSomenteUsuarioVerificadoNoServidorEIgnoraRuDoNavegador() {
        Usuario usuario = mock(Usuario.class);
        when(usuario.isAtivo()).thenReturn(true);
        when(usuario.getRu()).thenReturn(42L);
        request.addHeader("X-User-RU", "1");
        request.setAttribute(VERIFIED_USER_ATTRIBUTE, usuario);
        when(catalog.listarProdutos(estabelecimento, 42L)).thenReturn(List.of());

        assertTrue(controller.produtos(estabelecimento, request).isEmpty());
        verify(catalog).listarProdutos(estabelecimento, 42L);
        verifyNoMoreInteractions(catalog);
    }

    @Test
    void rejeitaIdentidadeDesativadaMesmoSeAtributoEstiverPresente() {
        Usuario usuario = mock(Usuario.class);
        when(usuario.isAtivo()).thenReturn(false);
        request.setAttribute(VERIFIED_USER_ATTRIBUTE, usuario);

        assertThrows(ResponseStatusException.class,
                () -> controller.produtos(estabelecimento, request));
        verifyNoInteractions(catalog);
    }

    @Test
    void negacaoNaoRevelaSeProdutoOuEstabelecimentoExiste() {
        var result = controller.forbidden();
        assertEquals(HttpStatus.FORBIDDEN, result.getStatusCode());
        assertEquals("Acesso negado.", result.getBody().get("erro"));
    }
}
