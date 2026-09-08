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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import repository.UsuarioRepository;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        classes = Main.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.port=0"
)
class Fase0EstoqueDecimalApiIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Usuario gerenteEstoque;

    @BeforeEach
    void setUp() {
        gerenteEstoque = usuarioRepository.listarTodos().stream()
                .filter(usuario -> "11144477735".equals(usuario.getCpfBruto()))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void fluxoMussarelaCompraCustoMedioMovimentoEEstorno() throws Exception {
        ResponseEntity<String> unidades = get("/api/unidades");
        assertEquals(HttpStatus.OK, unidades.getStatusCode());
        assertTrue(unidades.getBody() != null && unidades.getBody().contains("\"codigo\":\"GRAMA\""));

        String sufixo = String.valueOf(System.nanoTime());
        int produtoId = objectMapper.readTree(post("/api/produtos", produtoPayload("Mussarela API " + sufixo)).getBody())
                .path("id")
                .asInt();

        ResponseEntity<String> embalagemCriada = post(
                "/api/produtos/" + produtoId + "/embalagens",
                embalagemPayload("Pacote 5 kg", "5000", "GRAMA")
        );
        assertEquals(HttpStatus.OK, embalagemCriada.getStatusCode());
        long embalagemId = objectMapper.readTree(embalagemCriada.getBody()).path("id").asLong();

        long primeiraCompraId = criarCompra();
        post("/api/compras/" + primeiraCompraId + "/itens",
                itemCompraEmbalagemPayload(produtoId, embalagemId, "2", "300.00"));
        ResponseEntity<String> primeiraConfirmada = confirmarCompra(
                primeiraCompraId,
                "api-mussarela-1-" + sufixo,
                "Entrada de 2 pacotes"
        );
        assertEquals(HttpStatus.OK, primeiraConfirmada.getStatusCode(), primeiraConfirmada.getBody());

        JsonNode estoquePrimeira = objectMapper.readTree(get("/api/produtos/" + produtoId + "/estoque").getBody());
        assertBigDecimalEquals("10000.000000", estoquePrimeira.path("quantidadeAtual").decimalValue());
        assertBigDecimalEquals("0.03000000", estoquePrimeira.path("custoMedio").decimalValue());

        long segundaCompraId = criarCompra();
        post("/api/compras/" + segundaCompraId + "/itens",
                itemCompraUnidadePayload(produtoId, "GRAMA", "5000", "180.00"));
        ResponseEntity<String> segundaConfirmada = confirmarCompra(
                segundaCompraId,
                "api-mussarela-2-" + sufixo,
                "Entrada de 5000g"
        );
        assertEquals(HttpStatus.OK, segundaConfirmada.getStatusCode(), segundaConfirmada.getBody());

        JsonNode estoqueSegunda = objectMapper.readTree(get("/api/produtos/" + produtoId + "/estoque").getBody());
        assertBigDecimalEquals("15000.000000", estoqueSegunda.path("quantidadeAtual").decimalValue());
        assertBigDecimalEquals("0.03200000", estoqueSegunda.path("custoMedio").decimalValue());

        JsonNode movimentos = objectMapper.readTree(get("/api/movimentos-estoque?produtoId=" + produtoId).getBody());
        assertEquals(2, movimentos.size());
        long movimentoSegundaEntrada = 0;
        for (JsonNode movimento : movimentos) {
            if (movimento.path("origemId").asLong() == segundaCompraId) {
                movimentoSegundaEntrada = movimento.path("id").asLong();
                assertBigDecimalEquals("10000.000000", movimento.path("saldoAnterior").decimalValue());
                assertBigDecimalEquals("15000.000000", movimento.path("saldoPosterior").decimalValue());
                assertBigDecimalEquals("0.03000000", movimento.path("custoMedioAnterior").decimalValue());
                assertBigDecimalEquals("0.03200000", movimento.path("custoMedioPosterior").decimalValue());
                assertBigDecimalEquals("300.0000", movimento.path("valorEstoqueAnterior").decimalValue());
                assertBigDecimalEquals("480.0000", movimento.path("valorEstoquePosterior").decimalValue());
            }
        }
        assertTrue(movimentoSegundaEntrada > 0);

        ResponseEntity<String> estorno = post(
                "/api/movimentos-estoque/" + movimentoSegundaEntrada + "/estornar",
                estornoPayload("Estorno da segunda entrada", "Teste API")
        );
        assertEquals(HttpStatus.OK, estorno.getStatusCode());
        JsonNode estornoNode = objectMapper.readTree(estorno.getBody());
        assertEquals("ESTORNO_MOVIMENTO", estornoNode.path("tipo").asText());
        assertEquals(movimentoSegundaEntrada, estornoNode.path("movimentoEstornadoId").asLong());
        assertBigDecimalEquals("15000.000000", estornoNode.path("saldoAnterior").decimalValue());
        assertBigDecimalEquals("10000.000000", estornoNode.path("saldoPosterior").decimalValue());
        assertBigDecimalEquals("0.03200000", estornoNode.path("custoMedioAnterior").decimalValue());
        assertBigDecimalEquals("0.03000000", estornoNode.path("custoMedioPosterior").decimalValue());
        assertBigDecimalEquals("480.0000", estornoNode.path("valorEstoqueAnterior").decimalValue());
        assertBigDecimalEquals("300.0000", estornoNode.path("valorEstoquePosterior").decimalValue());

        JsonNode estoqueFinal = objectMapper.readTree(get("/api/produtos/" + produtoId + "/estoque").getBody());
        assertBigDecimalEquals("10000.000000", estoqueFinal.path("quantidadeAtual").decimalValue());
        assertBigDecimalEquals("0.03000000", estoqueFinal.path("custoMedio").decimalValue());
    }

    @Test
    void confirmarCompraComMesmaChaveEPayloadDiferenteRetornaConflito() throws Exception {
        String sufixo = String.valueOf(System.nanoTime());
        int produtoId = objectMapper.readTree(post("/api/produtos", produtoPayload("Mussarela Conflito " + sufixo)).getBody())
                .path("id")
                .asInt();

        long primeiraCompraId = criarCompra();
        post("/api/compras/" + primeiraCompraId + "/itens",
                itemCompraUnidadePayload(produtoId, "GRAMA", "1000", "30.00"));
        assertEquals(HttpStatus.OK, confirmarCompra(primeiraCompraId, "api-conflito-" + sufixo, "Primeira").getStatusCode());

        long segundaCompraId = criarCompra();
        post("/api/compras/" + segundaCompraId + "/itens",
                itemCompraUnidadePayload(produtoId, "GRAMA", "2000", "80.00"));
        ResponseEntity<String> conflito = confirmarCompra(segundaCompraId, "api-conflito-" + sufixo, "Segunda");

        assertEquals(HttpStatus.CONFLICT, conflito.getStatusCode());
        assertTrue(conflito.getBody() != null
                && conflito.getBody().contains("CHAVE_IDEMPOTENCIA_REUTILIZADA_COM_PAYLOAD_DIFERENTE"));
    }

    private long criarCompra() throws Exception {
        ResponseEntity<String> compra = post("/api/compras", Map.of());
        assertEquals(HttpStatus.CREATED, compra.getStatusCode());
        return objectMapper.readTree(compra.getBody()).path("id").asLong();
    }

    private ResponseEntity<String> confirmarCompra(long compraId, String chave, String motivo) {
        HttpHeaders headers = jsonHeaders();
        headers.set("Idempotency-Key", chave);
        return restTemplate.exchange(
                url("/api/compras/" + compraId + "/confirmar"),
                HttpMethod.POST,
                new HttpEntity<>(Map.of("motivo", motivo), headers),
                String.class
        );
    }

    private ResponseEntity<String> get(String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-RU", String.valueOf(gerenteEstoque.getRu()));
        return restTemplate.exchange(url(path), HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private ResponseEntity<String> post(String path, Object body) {
        return restTemplate.exchange(url(path), HttpMethod.POST, new HttpEntity<>(body, jsonHeaders()), String.class);
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-User-RU", String.valueOf(gerenteEstoque.getRu()));
        return headers;
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private static Map<String, Object> produtoPayload(String nome) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("nome", nome);
        body.put("quantidadeInicial", 0);
        body.put("quantidadeMinima", 0);
        body.put("precoUnitario", "0.00");
        body.put("categoriaEstoque", "FRIO");
        body.put("unidadeBaseCodigo", "GRAMA");
        body.put("politicaFalta", "BLOQUEAR");
        body.put("ativo", true);
        return body;
    }

    private static Map<String, Object> embalagemPayload(String nome, String quantidadeBase, String unidadeBaseCodigo) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("nome", nome);
        body.put("quantidadeBase", quantidadeBase);
        body.put("unidadeBaseCodigo", unidadeBaseCodigo);
        body.put("ativa", true);
        return body;
    }

    private static Map<String, Object> itemCompraEmbalagemPayload(int produtoId,
                                                                 long embalagemId,
                                                                 String quantidadeComprada,
                                                                 String custoTotal) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("produtoId", produtoId);
        body.put("embalagemId", embalagemId);
        body.put("quantidadeComprada", quantidadeComprada);
        body.put("custoTotal", custoTotal);
        return body;
    }

    private static Map<String, Object> itemCompraUnidadePayload(int produtoId,
                                                               String unidadeCompraCodigo,
                                                               String quantidadeComprada,
                                                               String custoTotal) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("produtoId", produtoId);
        body.put("unidadeCompraCodigo", unidadeCompraCodigo);
        body.put("quantidadeComprada", quantidadeComprada);
        body.put("custoTotal", custoTotal);
        return body;
    }

    private static Map<String, Object> estornoPayload(String motivo, String observacao) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("motivo", motivo);
        body.put("observacao", observacao);
        return body;
    }

    private static void assertBigDecimalEquals(String esperado, BigDecimal atual) {
        assertEquals(0, new BigDecimal(esperado).compareTo(atual));
    }
}
