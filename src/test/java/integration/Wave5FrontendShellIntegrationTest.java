package integration;

import app.Main;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        classes = Main.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.port=0"
)
class Wave5FrontendShellIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void rotasPrincipaisContinuamServindoAsViewsDaAplicacao() {
        Map<String, String> rotas = Map.of(
                "/", "StockOS",
                "/dashboard", "Dashboard",
                "/estoque", "Estoque",
                "/fornecedores", "Fornecedores",
                "/notas", "Notas",
                "/caixas", "Caixas",
                "/cotacoes", "Cot",
                "/funcionarios", "Funcion",
                "/logs", "Logs",
                "/login", "Login"
        );

        rotas.forEach(this::assertPage);
    }

    @Test
    void paginasHtmlEstaoDisponiveisComShellCompartilhado() {
        Map<String, String> paginas = Map.of(
                "/dashboard.html", "/js/shared.js",
                "/caixas.html", "/js/shared.js",
                "/cotacoes.html", "/js/shared.js",
                "/funcionarios.html", "/js/shared.js",
                "/logs.html", "/js/shared.js",
                "/login.html", "StockOS"
        );

        paginas.forEach(this::assertPage);
    }

    private void assertPage(String path, String trechoEsperado) {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + path,
                String.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode(), path);
        assertTrue(response.getBody() != null && response.getBody().contains(trechoEsperado), path);
    }
}
