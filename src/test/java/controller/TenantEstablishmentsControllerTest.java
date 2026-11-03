package controller;

import app.tenancy.TenantMembershipGuard;
import app.tenancy.TenantMembershipGuard.EstabelecimentoDisponivel;
import app.tenancy.TenantMembershipGuard.Papel;
import model.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TenantEstablishmentsControllerTest {
    @Test
    void ignoresForgedRuHeaderWithoutVerifiedSession() {
        TenantMembershipGuard membership = mock(TenantMembershipGuard.class);
        TenantEstablishmentsController controller = new TenantEstablishmentsController(membership);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-RU", "1");
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> controller.meusEstabelecimentos(request));
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        verifyNoInteractions(membership);
    }

    @Test
    void returnsOnlyMembershipsOfVerifiedActiveUser() {
        TenantMembershipGuard membership = mock(TenantMembershipGuard.class);
        TenantEstablishmentsController controller = new TenantEstablishmentsController(membership);
        MockHttpServletRequest request = new MockHttpServletRequest();
        Usuario user = mock(Usuario.class);
        when(user.isAtivo()).thenReturn(true);
        when(user.getRu()).thenReturn(43L);
        request.addHeader("X-User-RU", "999");
        request.setAttribute("app.security.LinkedStockosUserFilter.verifiedUser", user);
        UUID tenant = UUID.randomUUID();
        var accessible = List.of(new EstabelecimentoDisponivel(tenant, "Loja A", Papel.OPERADOR, 7));
        when(membership.listarEstabelecimentosAutorizados(43L)).thenReturn(accessible);
        assertEquals(accessible, controller.meusEstabelecimentos(request));
        verify(membership).listarEstabelecimentosAutorizados(43L);
        verifyNoMoreInteractions(membership);
    }

    @Test
    void rejectsInactiveVerifiedAccount() {
        TenantMembershipGuard membership = mock(TenantMembershipGuard.class);
        TenantEstablishmentsController controller = new TenantEstablishmentsController(membership);
        MockHttpServletRequest request = new MockHttpServletRequest();
        Usuario inactive = mock(Usuario.class);
        when(inactive.isAtivo()).thenReturn(false);
        request.setAttribute("app.security.LinkedStockosUserFilter.verifiedUser", inactive);
        assertThrows(ResponseStatusException.class, () -> controller.meusEstabelecimentos(request));
        verifyNoInteractions(membership);
    }
}
