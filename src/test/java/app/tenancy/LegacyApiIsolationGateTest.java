package app.tenancy;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class LegacyApiIsolationGateTest {
    private final LegacyApiIsolationGate gate = new LegacyApiIsolationGate();

    @Test
    void bloqueiaAcessoAoEstoqueLegadoMesmoComRuForjado() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/produtos");
        request.addHeader("X-User-RU", "1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        gate.doFilter(request, response, chain);

        assertEquals(403, response.getStatus());
        verifyNoInteractions(chain);
    }

    @Test
    void bloqueiaOperacoesGlobaisDeVendaECaixa() throws Exception {
        for (String path : new String[]{"/api/vendas", "/api/caixas", "/api/auth/login"}) {
            MockHttpServletRequest request = request("POST", path);
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);

            gate.doFilter(request, response, chain);

            assertEquals(403, response.getStatus(), path);
            verifyNoInteractions(chain);
        }
    }

    @Test
    void permiteSomenteRotaTenantAwareEEndpointsDeSessao() throws Exception {
        String tenant = UUID.randomUUID().toString();
        for (String path : new String[]{
                "/api/v2/estabelecimentos/" + tenant + "/catalogo/produtos",
                "/api/auth/config",
                "/api/auth/me"}) {
            MockHttpServletRequest request = request("GET", path);
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);

            gate.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
        }
    }

    @Test
    void rejeitaUuidInvalidoOuPrefixoDeRotaParecido() throws Exception {
        for (String path : new String[]{
                "/api/v2/estabelecimentos/forjado/catalogo/produtos",
                "/api/v2/estabelecimentos/" + UUID.randomUUID() + "/catalogo-admin",
                "/api/v2/estabelecimentos/" + UUID.randomUUID() + "/vendas"}) {
            MockHttpServletRequest request = request("GET", path);
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);

            gate.doFilter(request, response, chain);

            assertEquals(403, response.getStatus(), path);
            verifyNoInteractions(chain);
        }
    }

    private static MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        return request;
    }
}
