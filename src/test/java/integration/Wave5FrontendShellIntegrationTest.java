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
        Map<String, String> rotas = Map.ofEntries(
                Map.entry("/", "StockOS"),
                Map.entry("/dashboard", "Dashboard"),
                Map.entry("/cardapio", "Cardapio"),
                Map.entry("/estoque", "Estoque"),
                Map.entry("/fornecedores", "Fornecedores"),
                Map.entry("/notas", "Notas"),
                Map.entry("/caixas", "Caixas"),
                Map.entry("/cotacoes", "Cot"),
                Map.entry("/funcionarios", "Funcion"),
                Map.entry("/logs", "Logs"),
                Map.entry("/login", "Login")
        );

        rotas.forEach(this::assertPage);
    }

    @Test
    void paginasHtmlEstaoDisponiveisComShellCompartilhado() {
        Map<String, String> paginas = Map.of(
                "/dashboard.html", "/js/shared.js",
                "/cardapio.html", "/js/shared.js",
                "/caixas.html", "/js/shared.js",
                "/cotacoes.html", "/js/shared.js",
                "/funcionarios.html", "/js/shared.js",
                "/logs.html", "/js/shared.js",
                "/login.html", "StockOS"
        );

        paginas.forEach(this::assertPage);
    }

    @Test
    void telaDeGestaoDeCardapioExposeAsSecoesAdministrativasPrincipais() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/cardapio.html",
                String.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody() != null && response.getBody().contains("Gestao de Cardapio"));
        assertTrue(response.getBody() != null && response.getBody().contains("tabela-categorias"));
        assertTrue(response.getBody() != null && response.getBody().contains("tabela-itens"));
        assertTrue(response.getBody() != null && response.getBody().contains("/js/cardapio.js"));
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
