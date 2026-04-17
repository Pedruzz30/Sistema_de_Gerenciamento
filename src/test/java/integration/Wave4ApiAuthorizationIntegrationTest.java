package integration;

import app.Main;
import model.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import repository.UsuarioRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        classes = Main.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.port=0"
)
class Wave4ApiAuthorizationIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private Usuario gerenteEstoque;
    private Usuario caixa;

    @BeforeEach
    void setUp() {
        gerenteEstoque = buscarUsuarioPorCpf("11144477735");
        caixa = buscarUsuarioPorCpf("93541134780");
    }

    @Test
    void endpointsDeLeituraExigemHeaderDeIdentidade() {
        List<String> paths = List.of(
                "/api/produtos",
                "/api/cardapio/categorias",
                "/api/cardapio/itens",
                "/api/cardapio/admin/categorias",
                "/api/cardapio/admin/itens",
                "/api/movimentacoes",
                "/api/fornecedores",
                "/api/fornecedores/ativos",
                "/api/notas",
                "/api/cotacoes/relatorio?mes=2026-04",
                "/api/logs",
                "/api/caixas",
                "/api/caixas/metricas",
                "/api/caixas/sessoes/1",
                "/api/caixas/sessoes/1/movimentacoes",
                "/api/caixas/sessoes/1/historico",
                "/api/funcionarios/classes"
        );

        for (String path : paths) {
            ResponseEntity<String> response = get(path, null);
            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode(), path);
            assertTrue(response.getBody() != null && response.getBody().contains("X-User-RU"), path);
        }
    }

    @Test
    void permissoesDosPerfisSaoRespeitadasNosEndpointsProtegidos() {
        assertEquals(HttpStatus.OK, get("/api/produtos", gerenteEstoque.getRu()).getStatusCode());
        assertEquals(HttpStatus.OK, get("/api/cardapio/categorias", gerenteEstoque.getRu()).getStatusCode());
        assertEquals(HttpStatus.OK, get("/api/cardapio/itens", gerenteEstoque.getRu()).getStatusCode());
        assertEquals(HttpStatus.OK, get("/api/cardapio/admin/categorias", gerenteEstoque.getRu()).getStatusCode());
        assertEquals(HttpStatus.OK, get("/api/cardapio/admin/itens", gerenteEstoque.getRu()).getStatusCode());
        assertEquals(HttpStatus.OK, get("/api/movimentacoes", gerenteEstoque.getRu()).getStatusCode());
        assertEquals(HttpStatus.OK, get("/api/fornecedores", gerenteEstoque.getRu()).getStatusCode());
        assertEquals(HttpStatus.OK, get("/api/notas", gerenteEstoque.getRu()).getStatusCode());
        assertEquals(HttpStatus.OK, get("/api/cotacoes/relatorio?mes=2026-04", gerenteEstoque.getRu()).getStatusCode());
        assertEquals(HttpStatus.OK, get("/api/logs", gerenteEstoque.getRu()).getStatusCode());
        assertEquals(HttpStatus.OK, get("/api/caixas", gerenteEstoque.getRu()).getStatusCode());
        assertEquals(HttpStatus.OK, get("/api/caixas/metricas", gerenteEstoque.getRu()).getStatusCode());
        assertEquals(HttpStatus.OK, get("/api/funcionarios/classes", gerenteEstoque.getRu()).getStatusCode());

        assertEquals(HttpStatus.OK, get("/api/produtos", caixa.getRu()).getStatusCode());
        assertEquals(HttpStatus.OK, get("/api/cardapio/categorias", caixa.getRu()).getStatusCode());
        assertEquals(HttpStatus.OK, get("/api/cardapio/itens", caixa.getRu()).getStatusCode());
        assertEquals(HttpStatus.OK, get("/api/caixas", caixa.getRu()).getStatusCode());
        assertEquals(HttpStatus.OK, get("/api/caixas/metricas", caixa.getRu()).getStatusCode());

        assertEquals(HttpStatus.FORBIDDEN, get("/api/cardapio/admin/categorias", caixa.getRu()).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, get("/api/cardapio/admin/itens", caixa.getRu()).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, get("/api/fornecedores", caixa.getRu()).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, get("/api/notas", caixa.getRu()).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, get("/api/cotacoes/relatorio?mes=2026-04", caixa.getRu()).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, get("/api/logs", caixa.getRu()).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, get("/api/funcionarios/classes", caixa.getRu()).getStatusCode());
    }

    private ResponseEntity<String> get(String path, Long ru) {
        HttpHeaders headers = new HttpHeaders();
        if (ru != null) {
            headers.set("X-User-RU", String.valueOf(ru));
        }
        return restTemplate.exchange(
                "http://localhost:" + port + path,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );
    }

    private Usuario buscarUsuarioPorCpf(String cpfBruto) {
        return usuarioRepository.listarTodos().stream()
                .filter(usuario -> cpfBruto.equals(usuario.getCpfBruto()))
                .findFirst()
                .orElseThrow();
    }
}
