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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        classes = Main.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.port=0"
)
class PdvMenuCatalogIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private Usuario caixa;

    @BeforeEach
    void setUp() {
        caixa = usuarioRepository.listarTodos().stream()
                .filter(usuario -> "93541134780".equals(usuario.getCpfBruto()))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void pdvCarregaBebidasDoEstoqueEPizzasDoCatalogoSobDemanda() {
        ResponseEntity<String> produtos = get("/api/produtos");
        ResponseEntity<String> pizzas = get("/api/cardapio/pizzas");

        assertEquals(HttpStatus.OK, produtos.getStatusCode());
        assertTrue(produtos.getBody() != null && produtos.getBody().contains("Coca-Cola Lata 350ml"));
        assertTrue(produtos.getBody() != null && produtos.getBody().contains("\"categoriaEstoque\":\"BEBIDAS\""));
        assertTrue(produtos.getBody() != null && !produtos.getBody().contains("\"nome\":\"Pizza Calabresa Broto\""));

        assertEquals(HttpStatus.OK, pizzas.getStatusCode());
        assertTrue(pizzas.getBody() != null && pizzas.getBody().contains("\"nome\":\"Pizza Calabresa Broto\""));
        assertTrue(pizzas.getBody() != null && pizzas.getBody().contains("\"cardapioItemId\":\"pizza_calabresa_broto\""));
        assertTrue(pizzas.getBody() != null && pizzas.getBody().contains("\"controladoPorEstoque\":false"));
        assertTrue(pizzas.getBody() != null && pizzas.getBody().contains("\"disponivel\":true"));
    }

    private ResponseEntity<String> get(String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-RU", String.valueOf(caixa.getRu()));
        return restTemplate.exchange(
                "http://localhost:" + port + path,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );
    }
}
