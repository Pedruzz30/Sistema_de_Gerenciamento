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
        ResponseEntity<String> preparados = get("/api/cardapio/itens?categoria=preparados");
        ResponseEntity<String> bebidas = get("/api/cardapio/itens?categoria=bebidas");

        assertEquals(HttpStatus.OK, produtos.getStatusCode());
        assertTrue(produtos.getBody() != null && produtos.getBody().contains("Agua mineral 500ml"));
        assertEquals(HttpStatus.OK, categorias.getStatusCode());
        assertTrue(categorias.getBody() != null && categorias.getBody().contains("\"codigo\":\"preparados\""));
        assertTrue(categorias.getBody() != null && categorias.getBody().contains("\"codigo\":\"bebidas\""));
        assertEquals(HttpStatus.OK, itens.getStatusCode());
        assertTrue(itens.getBody() != null && itens.getBody().contains("\"codigo\":\"demo_prato_da_casa\""));
        assertTrue(itens.getBody() != null && itens.getBody().contains("\"tipoItem\":\"PREPARADO_SOB_DEMANDA\""));
        assertTrue(itens.getBody() != null && itens.getBody().contains("\"tipoItem\":\"ESTOQUE_DIRETO\""));
        assertEquals(HttpStatus.OK, preparados.getStatusCode());
        assertTrue(preparados.getBody() != null && preparados.getBody().contains("\"categoriaCodigo\":\"preparados\""));
        assertTrue(preparados.getBody() != null && !preparados.getBody().contains("\"categoriaCodigo\":\"bebidas\""));
        assertEquals(HttpStatus.OK, bebidas.getStatusCode());
        assertTrue(bebidas.getBody() != null && bebidas.getBody().contains("\"categoriaCodigo\":\"bebidas\""));
        assertTrue(bebidas.getBody() != null && !bebidas.getBody().contains("\"categoriaCodigo\":\"preparados\""));
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
