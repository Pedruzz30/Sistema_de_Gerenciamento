package integration;

import app.Main;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import model.DimensaoUnidade;
import model.UnidadeMedida;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.NonNull;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import repository.EstoqueProdutoRepository;
import repository.InMemoryEstoqueProdutoRepository;
import repository.InMemoryRegistroIdempotenciaRepository;
import repository.ProdutoRepository;
import repository.RegistroIdempotenciaRepository;
import repository.UsuarioRepository;
import model.EstoqueProduto;
import model.RegistroIdempotencia;
import service.CompraEstoqueService;
import service.ErroNegocioException;
import service.UnidadeMedidaService;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @Autowired private CompraEstoqueService compraService;
    @Autowired private EstoqueProdutoRepository estoques;
    @Autowired private ProdutoRepository produtos;
    @Autowired private RegistroIdempotenciaRepository idempotencia;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UnidadeMedidaService unidades;
    @PersistenceContext private EntityManager entityManager;

    private final TransactionTemplate tx;

    @Autowired
    Fase0EstoqueDecimalApiIntegrationTest(@NonNull PlatformTransactionManager transactionManager) {
        this.tx = new TransactionTemplate(transactionManager);
    }

    private Usuario gerenteEstoque;

    @BeforeEach
    void setUp() {
        gerenteEstoque = usuarioRepository.listarTodos().stream()
                .filter(usuario -> "11144477735".equals(usuario.getCpfBruto()))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void compraConcorrenteComChavesDistintasLancaEstoqueUmaVez() throws Exception {
        String sufixo = Long.toUnsignedString(System.nanoTime());
        var produtoCriado = post("/api/produtos", produtoPayload("Compra concorrente " + sufixo));
        assertEquals(HttpStatus.OK, produtoCriado.getStatusCode());
        int produtoId = objectMapper.readTree(java.util.Objects.requireNonNull(produtoCriado.getBody())).path("id").asInt();
        estoques.salvar(new EstoqueProduto(0, 1L, produtos.buscarPorId(produtoId).orElseThrow(),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        long compraId = criarCompra();
        assertEquals(HttpStatus.OK, post("/api/compras/" + compraId + "/itens",
                itemCompraUnidadePayload(produtoId, "GRAMA", "100", "30.00")).getStatusCode());

        try (var executor = Executors.newSingleThreadExecutor()) {
            var segundo = tx.execute(status -> {
                compraService.confirmarCompra(gerenteEstoque, compraId, "primeira-" + sufixo, "Concorrencia");
                var concorrente = executor.submit(() -> {
                    var erro = assertThrows(ErroNegocioException.class, () -> tx.executeWithoutResult(ignorado -> {
                        jdbc.execute("SET LOCAL application_name = 'stockos-compra-concorrente'");
                        compraService.confirmarCompra(gerenteEstoque, compraId, "segunda-" + sufixo, "Concorrencia");
                    }));
                    return erro.getCodigo();
                });
                long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                boolean aguardando = false;
                while (!concorrente.isDone() && System.nanoTime() < limite) {
                    jdbc.execute("SELECT pg_stat_clear_snapshot()");
                    aguardando = Boolean.TRUE.equals(jdbc.queryForObject("""
                            SELECT exists (select 1 from pg_stat_activity
                            where application_name = 'stockos-compra-concorrente' and wait_event_type = 'Lock')
                            """, Boolean.class));
                    if (aguardando) {
                        break;
                    }
                    java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(20));
                }
                assertTrue(aguardando, "A segunda transacao deve chegar ao lock antes do commit");
                return concorrente;
            });
            assertEquals("COMPRA_JA_CONFIRMADA", java.util.Objects.requireNonNull(segundo).get(15, TimeUnit.SECONDS));
        }

        var estoque = estoques.buscarPorFilialEProduto(1L, produtoId).orElseThrow();
        assertBigDecimalEquals("100", estoque.getQuantidadeAtual());
        assertEquals(1L, jdbc.queryForObject("""
                SELECT count(*) FROM movimentos_estoque_decimal WHERE produto_id = ? AND origem_id = ?
                """, Long.class, produtoId, compraId));
        assertEquals(0L, jdbc.queryForObject("""
                SELECT count(*) FROM registros_idempotencia WHERE recurso_id = ? AND idempotency_key = ?
                """, Long.class, compraId, "segunda-" + sufixo));
    }

    @Test
    void saldoEIdempotenciaSaoSeparadosPorFilialEmJpaEEmMemoria() throws Exception {
        var resposta = post("/api/produtos", produtoPayload("Saldo por filial " + System.nanoTime()));
        assertEquals(HttpStatus.OK, resposta.getStatusCode());
        int id = objectMapper.readTree(java.util.Objects.requireNonNull(resposta.getBody())).path("id").asInt();
        var produto = produtos.buscarPorId(id).orElseThrow();
        for (var repository : java.util.List.of(estoques, new InMemoryEstoqueProdutoRepository())) {
            var filialA = repository.salvar(new EstoqueProduto(0, 9031L, produto, BigDecimal.ONE,
                    BigDecimal.ZERO, BigDecimal.ONE));
            repository.salvar(new EstoqueProduto(0, 9032L, produto, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ONE));
            filialA.aplicarEntrada(BigDecimal.ONE, BigDecimal.ONE);
            repository.salvar(filialA);

            assertBigDecimalEquals("2", repository.buscarPorFilialEProduto(9031L, id).orElseThrow().getQuantidadeAtual());
            var resultadosB = repository.listarPorFilialEProdutos(9032L, java.util.List.of(id));
            assertEquals(1, resultadosB.size());
            assertEquals(9032L, resultadosB.getFirst().getFilialId());
            assertBigDecimalEquals("10", resultadosB.getFirst().getQuantidadeAtual());
            assertTrue(repository.buscarPorFilialEProduto(9033L, id).isEmpty());
        }
        String chave = "contrato-filiais-" + id;
        for (var repository : java.util.List.of(idempotencia, new InMemoryRegistroIdempotenciaRepository())) {
            repository.salvarEFlush(RegistroIdempotencia.iniciar(9031L, "CONTRATO", chave, "a", null));
            repository.salvarEFlush(RegistroIdempotencia.iniciar(9032L, "CONTRATO", chave, "b", null));
            assertTrue(repository.buscar(9031L, "CONTRATO", chave).orElseThrow().mesmoPayload("a"));
            assertTrue(repository.buscar(9032L, "CONTRATO", chave).orElseThrow().mesmoPayload("b"));
            assertTrue(repository.buscar(9033L, "CONTRATO", chave).isEmpty());
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () ->
                    repository.salvarEFlush(RegistroIdempotencia.iniciar(9031L, "CONTRATO", chave, "outro", null)));
            assertTrue(repository.buscar(9031L, "CONTRATO", " " + chave + " ").orElseThrow().mesmoPayload("a"));
        }
    }

    @Test
    void converteParaUnidadeDeDestinoCarregadaComoProxyJpa() {
        tx.executeWithoutResult(status -> {
            Long id = jdbc.queryForObject("""
                    insert into unidades_medida (codigo, nome, dimensao, fator_base, ativa)
                    values ('PROXY_FASE3', 'Unidade proxy', 'CONTAGEM', 1, true) returning id
                    """, Long.class);
            var destino = entityManager.getReference(UnidadeMedida.class, java.util.Objects.requireNonNull(id));
            var origem = new UnidadeMedida(0, "UNIDADE_ORIGEM", "Unidade de origem",
                    DimensaoUnidade.CONTAGEM, BigDecimal.ONE, true);
            assertBigDecimalEquals("1", unidades.converter(BigDecimal.ONE, origem, destino));
            status.setRollbackOnly();
        });
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
