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
    void pdvCarregaCategoriasEItensDoCardapioUnificado() {
        ResponseEntity<String> produtos = get("/api/produtos");
        ResponseEntity<String> categorias = get("/api/cardapio/categorias");
        ResponseEntity<String> itens = get("/api/cardapio/itens");
        ResponseEntity<String> pizzas = get("/api/cardapio/itens?categoria=pizzas_artesanais");
        ResponseEntity<String> massasClassicas = get("/api/cardapio/itens?categoria=massas_classicas");

        assertEquals(HttpStatus.OK, produtos.getStatusCode());
        assertTrue(produtos.getBody() != null && produtos.getBody().contains("Coca-Cola Lata 350ml"));
        assertTrue(produtos.getBody() != null && !produtos.getBody().contains("\"nome\":\"Pizza Calabresa Broto\""));

        assertEquals(HttpStatus.OK, categorias.getStatusCode());
        assertTrue(categorias.getBody() != null && categorias.getBody().contains("\"codigo\":\"entradas\""));
        assertTrue(categorias.getBody() != null && categorias.getBody().contains("\"codigo\":\"massas_classicas\""));
        assertTrue(categorias.getBody() != null && categorias.getBody().contains("\"codigo\":\"massas_especiais\""));
        assertTrue(categorias.getBody() != null && categorias.getBody().contains("\"codigo\":\"pizzas_artesanais\""));
        assertTrue(categorias.getBody() != null && categorias.getBody().contains("\"codigo\":\"pratos_executivos\""));
        assertTrue(categorias.getBody() != null && categorias.getBody().contains("\"codigo\":\"sobremesas\""));
        assertTrue(categorias.getBody() != null && categorias.getBody().contains("\"codigo\":\"bebidas\""));

        assertEquals(HttpStatus.OK, itens.getStatusCode());
        assertTrue(itens.getBody() != null && itens.getBody().contains("\"nome\":\"Spaghetti a Carbonara\""));
        assertTrue(itens.getBody() != null && itens.getBody().contains("\"nome\":\"Nhoque da Casa\""));
        assertTrue(itens.getBody() != null && itens.getBody().contains("\"nome\":\"Tiramisu tradicional\""));
        assertTrue(itens.getBody() != null && itens.getBody().contains("\"nome\":\"Pizza Calabresa Broto\""));
        assertTrue(itens.getBody() != null && itens.getBody().contains("\"codigo\":\"pizza_calabresa_broto\""));
        assertTrue(itens.getBody() != null && itens.getBody().contains("\"tipoItem\":\"PREPARADO_SOB_DEMANDA\""));
        assertTrue(itens.getBody() != null && itens.getBody().contains("\"categoriaCodigo\":\"bebidas\""));

        assertEquals(HttpStatus.OK, pizzas.getStatusCode());
        assertTrue(pizzas.getBody() != null && pizzas.getBody().contains("\"categoriaCodigo\":\"pizzas_artesanais\""));
        assertTrue(pizzas.getBody() != null && !pizzas.getBody().contains("\"categoriaCodigo\":\"bebidas\""));

        assertEquals(HttpStatus.OK, massasClassicas.getStatusCode());
        assertTrue(massasClassicas.getBody() != null && massasClassicas.getBody().contains("\"categoriaCodigo\":\"massas_classicas\""));
        assertTrue(massasClassicas.getBody() != null && massasClassicas.getBody().contains("\"nome\":\"Spaghetti a Carbonara\""));
        assertTrue(massasClassicas.getBody() != null && !massasClassicas.getBody().contains("\"categoriaCodigo\":\"bebidas\""));
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
