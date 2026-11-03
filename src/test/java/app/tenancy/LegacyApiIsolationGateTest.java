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
                "/api/v2/estabelecimentos/" + tenant + "/estoque/saldos",
                "/api/v2/estabelecimentos/" + tenant + "/estoque/movimentos",
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
    void descobertaSomentePermiteGetExato() throws Exception {
        MockHttpServletRequest allowed = request("GET", "/api/v2/estabelecimentos");
        FilterChain allowedChain = mock(FilterChain.class);
        gate.doFilter(allowed, new MockHttpServletResponse(), allowedChain);
        verify(allowedChain).doFilter(eq(allowed), any());
        for (String[] attempt : new String[][]{
                {"POST", "/api/v2/estabelecimentos"},
                {"DELETE", "/api/v2/estabelecimentos"},
                {"GET", "/api/v2/estabelecimentos/export"},
                {"GET", "/api/v2/estabelecimentos-extra"}
        }) {
            MockHttpServletRequest input = request(attempt[0], attempt[1]);
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);
            gate.doFilter(input, response, chain);
            assertEquals(403, response.getStatus());
            verifyNoInteractions(chain);
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

    @Test
    void estoqueNaoPermiteEscritaNemRotasNaoRevisadas() throws Exception {
        String tenant = UUID.randomUUID().toString();
        for (String path : new String[]{
                "/api/v2/estabelecimentos/" + tenant + "/estoque/saldos",
                "/api/v2/estabelecimentos/" + tenant + "/estoque/movimentos",
                "/api/v2/estabelecimentos/" + tenant + "/estoque/ajustes"}) {
            MockHttpServletRequest request = request("POST", path);
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);
            gate.doFilter(request, response, chain);
            assertEquals(403, response.getStatus(), path);
            verifyNoInteractions(chain);
        }
        MockHttpServletRequest request = request("GET",
                "/api/v2/estabelecimentos/" + tenant + "/estoque/ajustes");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        gate.doFilter(request, response, chain);
        assertEquals(403, response.getStatus());
        verifyNoInteractions(chain);
    }

    @Test
    void permiteApenasEscritasDeCatalogoJaAuditadas() throws Exception {
        String tenant = UUID.randomUUID().toString();
        String base = "/api/v2/estabelecimentos/" + tenant;
        String[][] allowed = {
                {"GET", base + "/catalogo/produtos/12"},
                {"GET", base + "/catalogo/fornecedores"},
                {"POST", base + "/catalogo/produtos"},
                {"PATCH", base + "/catalogo/produtos/12/nome"}
        };
        for (String[] route : allowed) {
            MockHttpServletRequest request = request(route[0], route[1]);
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);
            gate.doFilter(request, response, chain);
            verify(chain).doFilter(request, response);
        }
    }

    @Test
    void bloqueiaMetodosECaminhosNovosSemRevisaoDeSeguranca() throws Exception {
        String tenant = UUID.randomUUID().toString();
        String base = "/api/v2/estabelecimentos/" + tenant;
        String[][] denied = {
                {"DELETE", base + "/catalogo/produtos/12"},
                {"PUT", base + "/catalogo/produtos/12"},
                {"POST", base + "/catalogo/fornecedores"},
                {"GET", base + "/catalogo/relatorios"},
                {"GET", base + "/catalogo/produtos/12/segredos"},
                {"GET", base + "/catalogo/produtos/12/nome"},
                {"PATCH", base + "/catalogo/produtos/12/preco"},
                {"PATCH", base + "/catalogo/produtos/invalido/nome"},
                {"POST", base + "/estoque/saldos"},
                {"POST", "/api/auth/me"},
                {"POST", "/api/auth/config"},
                {"GET", base + "/catalogo/"}
        };
        for (String[] route : denied) {
            MockHttpServletRequest request = request(route[0], route[1]);
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);
            gate.doFilter(request, response, chain);
            assertEquals(403, response.getStatus(), route[0] + " " + route[1]);
            verifyNoInteractions(chain);
        }
    }

    private static MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        return request;
    }
}
