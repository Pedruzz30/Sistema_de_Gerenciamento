package controller;

import app.tenancy.TenantScopedStockService;
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

class TenantStockControllerTest {
    private static final String VERIFIED_ATTRIBUTE =
            "app.security.LinkedStockosUserFilter.verifiedUser";

    private TenantScopedStockService service;
    private TenantStockController controller;
    private MockHttpServletRequest request;
    private UUID tenant;

    @BeforeEach
    void init() {
        service = mock(TenantScopedStockService.class);
        controller = new TenantStockController(service);
        request = new MockHttpServletRequest();
        tenant = UUID.randomUUID();
    }

    @Test
    void ignoraRuForjadoNoCabecalhoEExigeAtributoAutenticado() {
        request.addHeader("X-User-RU", "1");
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.saldos(tenant, request));
        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatusCode());
        verifyNoInteractions(service);
    }

    @Test
    void usaIdentidadeVerificadaParaConsultarSaldosEMovimentos() {
        Usuario verified = mock(Usuario.class);
        when(verified.isAtivo()).thenReturn(true);
        when(verified.getRu()).thenReturn(63L);
        request.addHeader("X-User-RU", "1");
        request.setAttribute(VERIFIED_ATTRIBUTE, verified);
        when(service.listarSaldos(tenant, 63L)).thenReturn(List.of());
        when(service.listarMovimentos(tenant, 63L)).thenReturn(List.of());

        assertTrue(controller.saldos(tenant, request).isEmpty());
        assertTrue(controller.movimentos(tenant, request).isEmpty());
        verify(service).listarSaldos(tenant, 63L);
        verify(service).listarMovimentos(tenant, 63L);
        verifyNoMoreInteractions(service);
    }

    @Test
    void usuarioInativoNaoAcessaDados() {
        Usuario inactive = mock(Usuario.class);
        when(inactive.isAtivo()).thenReturn(false);
        request.setAttribute(VERIFIED_ATTRIBUTE, inactive);
        assertThrows(ResponseStatusException.class,
                () -> controller.movimentos(tenant, request));
        verifyNoInteractions(service);
    }

    @Test
    void acessoNegadoNaoRevelaExistenciaDeRecurso() {
        var response = controller.forbidden();
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("Acesso negado.", response.getBody().get("erro"));
    }
}
