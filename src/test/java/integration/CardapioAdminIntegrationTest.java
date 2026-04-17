package integration;

import app.Main;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import model.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import repository.UsuarioRepository;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        classes = Main.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.port=0"
)
class CardapioAdminIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Usuario gerenteEstoque;
    private Usuario caixa;

    @BeforeEach
    void setUp() {
        gerenteEstoque = buscarUsuarioPorCpf("11144477735");
        caixa = buscarUsuarioPorCpf("93541134780");
    }

    @Test
    void fluxoAdministrativoDeCardapioMantemCompatibilidadeComPdv() throws Exception {
        String sufixo = String.valueOf(System.nanoTime());

        ResponseEntity<String> categoriaCriada = post(
                "/api/cardapio/admin/categorias",
                gerenteEstoque.getRu(),
                categoriaPayload("Menu especial " + sufixo, "Menu especial " + sufixo, 321)
        );
        assertEquals(HttpStatus.CREATED, categoriaCriada.getStatusCode());
        JsonNode categoriaNode = objectMapper.readTree(categoriaCriada.getBody());
        int categoriaId = categoriaNode.path("id").asInt();
        String categoriaCodigo = categoriaNode.path("codigo").asText();
        assertTrue(categoriaCodigo.startsWith("menu_especial_"));

        ResponseEntity<String> categoriaAtualizada = put(
                "/api/cardapio/admin/categorias/" + categoriaId,
                gerenteEstoque.getRu(),
                categoriaPayload("Menu atualizado " + sufixo, "Menu atualizado " + sufixo, 322)
        );
        assertEquals(HttpStatus.OK, categoriaAtualizada.getStatusCode());
        JsonNode categoriaAtualizadaNode = objectMapper.readTree(categoriaAtualizada.getBody());
        categoriaCodigo = categoriaAtualizadaNode.path("codigo").asText();
        assertEquals(322, categoriaAtualizadaNode.path("ordemExibicao").asInt());

        ResponseEntity<String> itemCriado = post(
                "/api/cardapio/admin/itens",
                gerenteEstoque.getRu(),
                itemPayload(
                        "Prato chef " + sufixo,
                        "Prato chef " + sufixo,
                        "Especial do dia",
                        BigDecimal.valueOf(48.90),
                        categoriaId,
                        "PREPARADO_SOB_DEMANDA",
                        15,
                        null
                )
        );
        assertEquals(HttpStatus.CREATED, itemCriado.getStatusCode());
        JsonNode itemNode = objectMapper.readTree(itemCriado.getBody());
        int itemId = itemNode.path("id").asInt();
        String itemCodigo = itemNode.path("codigo").asText();
        assertTrue(itemNode.path("disponivel").asBoolean());

        ResponseEntity<String> itemAtualizado = put(
                "/api/cardapio/admin/itens/" + itemId,
                gerenteEstoque.getRu(),
                itemPayload(
                        "Prato chef premium " + sufixo,
                        "Prato chef premium " + sufixo,
                        "Especial ajustado",
                        BigDecimal.valueOf(52.50),
                        categoriaId,
                        "PREPARADO_SOB_DEMANDA",
                        18,
                        null
                )
        );
        assertEquals(HttpStatus.OK, itemAtualizado.getStatusCode());
        JsonNode itemAtualizadoNode = objectMapper.readTree(itemAtualizado.getBody());
        itemCodigo = itemAtualizadoNode.path("codigo").asText();
        assertEquals("Prato chef premium " + sufixo, itemAtualizadoNode.path("nome").asText());

        ResponseEntity<String> itensFiltrados = get(
                "/api/cardapio/admin/itens?categoriaId=" + categoriaId + "&busca=premium",
                gerenteEstoque.getRu()
        );
        assertEquals(HttpStatus.OK, itensFiltrados.getStatusCode());
        assertTrue(itensFiltrados.getBody() != null && itensFiltrados.getBody().contains(itemCodigo));

        ResponseEntity<String> itemReordenado = patch(
                "/api/cardapio/admin/itens/" + itemId + "/ordem",
                gerenteEstoque.getRu(),
                ordemPayload(5)
        );
        assertEquals(HttpStatus.OK, itemReordenado.getStatusCode());
        assertTrue(itemReordenado.getBody() != null && itemReordenado.getBody().contains("\"ordemExibicao\":5"));

        ResponseEntity<String> categoriaReordenada = patch(
                "/api/cardapio/admin/categorias/" + categoriaId + "/ordem",
                gerenteEstoque.getRu(),
                ordemPayload(12)
        );
        assertEquals(HttpStatus.OK, categoriaReordenada.getStatusCode());
        assertTrue(categoriaReordenada.getBody() != null && categoriaReordenada.getBody().contains("\"ordemExibicao\":12"));

        ResponseEntity<String> itemIndisponivel = patch(
                "/api/cardapio/admin/itens/" + itemId + "/disponibilidade",
                gerenteEstoque.getRu(),
                disponibilidadePayload(false)
        );
        assertEquals(HttpStatus.OK, itemIndisponivel.getStatusCode());
        assertTrue(itemIndisponivel.getBody() != null && itemIndisponivel.getBody().contains("\"disponivelParaVenda\":false"));

        ResponseEntity<String> cardapioPdvComItemBloqueado = get(
                "/api/cardapio/itens?categoria=" + categoriaCodigo,
                caixa.getRu()
        );
        assertEquals(HttpStatus.OK, cardapioPdvComItemBloqueado.getStatusCode());
        assertTrue(cardapioPdvComItemBloqueado.getBody() != null && cardapioPdvComItemBloqueado.getBody().contains(itemCodigo));
        assertTrue(cardapioPdvComItemBloqueado.getBody() != null && cardapioPdvComItemBloqueado.getBody().contains("\"disponivel\":false"));

        ResponseEntity<String> itemInativo = patch(
                "/api/cardapio/admin/itens/" + itemId + "/ativo",
                gerenteEstoque.getRu(),
                ativoPayload(false)
        );
        assertEquals(HttpStatus.OK, itemInativo.getStatusCode());

        ResponseEntity<String> cardapioPdvSemItemInativo = get(
                "/api/cardapio/itens?categoria=" + categoriaCodigo,
                caixa.getRu()
        );
        assertEquals(HttpStatus.OK, cardapioPdvSemItemInativo.getStatusCode());
        assertFalse(cardapioPdvSemItemInativo.getBody() != null && cardapioPdvSemItemInativo.getBody().contains(itemCodigo));

        ResponseEntity<String> categoriaInativa = patch(
                "/api/cardapio/admin/categorias/" + categoriaId + "/ativo",
                gerenteEstoque.getRu(),
                ativoPayload(false)
        );
        assertEquals(HttpStatus.OK, categoriaInativa.getStatusCode());
        assertTrue(categoriaInativa.getBody() != null && categoriaInativa.getBody().contains("\"ativo\":false"));

        ResponseEntity<String> categoriasAdmin = get("/api/cardapio/admin/categorias", gerenteEstoque.getRu());
        assertEquals(HttpStatus.OK, categoriasAdmin.getStatusCode());
        assertTrue(categoriasAdmin.getBody() != null && categoriasAdmin.getBody().contains(categoriaCodigo));
        assertTrue(categoriasAdmin.getBody() != null && categoriasAdmin.getBody().contains("\"ativo\":false"));

        ResponseEntity<String> categoriasPdv = get("/api/cardapio/categorias", caixa.getRu());
        assertEquals(HttpStatus.OK, categoriasPdv.getStatusCode());
        assertFalse(categoriasPdv.getBody() != null && categoriasPdv.getBody().contains(categoriaCodigo));
    }

    @Test
    void endpointsAdministrativosExigemPermissaoDeEdicaoDeEstoque() {
        ResponseEntity<String> semCabecalho = get("/api/cardapio/admin/categorias", null);
        ResponseEntity<String> usuarioCaixa = get("/api/cardapio/admin/categorias", caixa.getRu());

        assertEquals(HttpStatus.FORBIDDEN, semCabecalho.getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, usuarioCaixa.getStatusCode());
    }

    @Test
    void camadaAdministrativaRejeitaCodigoDuplicadoEVinculoInvalido() throws Exception {
        String sufixo = String.valueOf(System.nanoTime());

        ResponseEntity<String> categoriaCriada = post(
                "/api/cardapio/admin/categorias",
                gerenteEstoque.getRu(),
                categoriaPayload("Massas premium " + sufixo, "Massas premium " + sufixo, 410)
        );
        assertEquals(HttpStatus.CREATED, categoriaCriada.getStatusCode());
        int categoriaId = objectMapper.readTree(categoriaCriada.getBody()).path("id").asInt();

        ResponseEntity<String> categoriaDuplicada = post(
                "/api/cardapio/admin/categorias",
                gerenteEstoque.getRu(),
                categoriaPayload("massas_premium_" + sufixo, "Outra categoria", 411)
        );
        assertEquals(HttpStatus.BAD_REQUEST, categoriaDuplicada.getStatusCode());
        assertTrue(categoriaDuplicada.getBody() != null
                && categoriaDuplicada.getBody().contains("Ja existe uma categoria de cardapio com este codigo."));

        ResponseEntity<String> itemCriado = post(
                "/api/cardapio/admin/itens",
                gerenteEstoque.getRu(),
                itemPayload(
                        "Torta do chef " + sufixo,
                        "Torta do chef " + sufixo,
                        "Primeira versao",
                        BigDecimal.valueOf(33.50),
                        categoriaId,
                        "PREPARADO_SOB_DEMANDA",
                        10,
                        null
                )
        );
        assertEquals(HttpStatus.CREATED, itemCriado.getStatusCode());

        ResponseEntity<String> itemDuplicado = post(
                "/api/cardapio/admin/itens",
                gerenteEstoque.getRu(),
                itemPayload(
                        "torta_do_chef_" + sufixo,
                        "Outra torta",
                        "Duplicado",
                        BigDecimal.valueOf(35.00),
                        categoriaId,
                        "PREPARADO_SOB_DEMANDA",
                        11,
                        null
                )
        );
        assertEquals(HttpStatus.BAD_REQUEST, itemDuplicado.getStatusCode());
        assertTrue(itemDuplicado.getBody() != null
                && itemDuplicado.getBody().contains("Ja existe um item de cardapio com este codigo."));

        ResponseEntity<String> produtos = get("/api/produtos", gerenteEstoque.getRu());
        assertEquals(HttpStatus.OK, produtos.getStatusCode());
        int produtoId = objectMapper.readTree(produtos.getBody()).get(0).path("id").asInt();

        ResponseEntity<String> itemComVinculoInvalido = post(
                "/api/cardapio/admin/itens",
                gerenteEstoque.getRu(),
                itemPayload(
                        "Raviole aberto " + sufixo,
                        "Raviole aberto " + sufixo,
                        "Nao pode vincular produto",
                        BigDecimal.valueOf(39.90),
                        categoriaId,
                        "PREPARADO_SOB_DEMANDA",
                        12,
                        produtoId
                )
        );
        assertEquals(HttpStatus.BAD_REQUEST, itemComVinculoInvalido.getStatusCode());
        assertTrue(itemComVinculoInvalido.getBody() != null
                && itemComVinculoInvalido.getBody().contains("Itens preparados sob demanda nao devem vincular produto de estoque."));
    }

    private Map<String, Object> categoriaPayload(String codigo, String nomeExibicao, int ordemExibicao) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("codigo", codigo);
        body.put("nomeExibicao", nomeExibicao);
        body.put("ordemExibicao", ordemExibicao);
        return body;
    }

    private Map<String, Object> itemPayload(String codigo,
                                            String nome,
                                            String descricaoCurta,
                                            BigDecimal precoVenda,
                                            int categoriaId,
                                            String tipoItem,
                                            int ordemExibicao,
                                            Integer produtoVinculadoId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("codigo", codigo);
        body.put("nome", nome);
        body.put("descricaoCurta", descricaoCurta);
        body.put("precoVenda", precoVenda);
        body.put("categoriaId", categoriaId);
        body.put("tipoItem", tipoItem);
        body.put("ordemExibicao", ordemExibicao);
        body.put("produtoVinculadoId", produtoVinculadoId);
        return body;
    }

    private Map<String, Object> ativoPayload(boolean ativo) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ativo", ativo);
        return body;
    }

    private Map<String, Object> disponibilidadePayload(boolean disponivelParaVenda) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("disponivelParaVenda", disponivelParaVenda);
        return body;
    }

    private Map<String, Object> ordemPayload(int ordemExibicao) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ordemExibicao", ordemExibicao);
        return body;
    }

    private ResponseEntity<String> get(String path, Long ru) {
        HttpHeaders headers = new HttpHeaders();
        if (ru != null) {
            headers.set("X-User-RU", String.valueOf(ru));
        }
        return restTemplate.exchange(url(path), HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private ResponseEntity<String> post(String path, Long ru, Object body) {
        HttpHeaders headers = jsonHeaders(ru);
        return restTemplate.exchange(url(path), HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<String> put(String path, Long ru, Object body) {
        HttpHeaders headers = jsonHeaders(ru);
        return restTemplate.exchange(url(path), HttpMethod.PUT, new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<String> patch(String path, Long ru, Object body) {
        HttpHeaders headers = jsonHeaders(ru);
        return restTemplate.exchange(url(path), HttpMethod.PUT, new HttpEntity<>(body, headers), String.class);
    }

    private HttpHeaders jsonHeaders(Long ru) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (ru != null) {
            headers.set("X-User-RU", String.valueOf(ru));
        }
        return headers;
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private Usuario buscarUsuarioPorCpf(String cpfBruto) {
        return usuarioRepository.listarTodos().stream()
                .filter(usuario -> cpfBruto.equals(usuario.getCpfBruto()))
                .findFirst()
                .orElseThrow();
    }
}
