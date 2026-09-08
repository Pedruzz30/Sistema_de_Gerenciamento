package integration;

import app.Main;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import model.EstoqueProduto;
import model.MovimentoEstoque;
import model.Produto;
import model.TipoMovimentoEstoque;
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
import repository.EstoqueProdutoRepository;
import repository.MovimentoEstoqueRepository;
import repository.ProdutoRepository;
import repository.UsuarioRepository;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        classes = Main.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.port=0"
)
class Fase5MultissaborApiIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private EstoqueProdutoRepository estoqueRepository;

    @Autowired
    private MovimentoEstoqueRepository movimentoRepository;

    private Usuario gerente;

    @BeforeEach
    void setUp() {
        gerente = usuarioRepository.listarTodos().stream()
                .filter(usuario -> "52998224725".equals(usuario.getCpfBruto()))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void vendaMultissaborPersistePartesConsolidaEstoqueECancelaPorSnapshot() throws Exception {
        MultissaborFixture multi = prepararMultissabor("f5_multi_" + System.nanoTime(), "10");
        JsonNode previsao = prever(multi.baseId(), partes(multi.parteAId(), multi.parteBId()));

        assertBigDecimalEquals("55.0000", previsao.path("precoUnitarioBase").decimalValue());
        assertEquals(2, previsao.path("partes").size());
        assertBigDecimalEquals("0.500000", previsao.path("partes").get(0).path("proporcao").decimalValue());
        assertBigDecimalEquals("0.500000", previsao.path("partes").get(1).path("proporcao").decimalValue());
        assertFalse(previsao.path("configuracaoHash").asText().isBlank());

        long vendaId = criarVendaMultissabor(
                multi.baseId(),
                "2",
                previsao.path("configuracaoHash").asText(),
                partes(multi.parteAId(), multi.parteBId())
        );
        JsonNode finalizada = objectMapper.readTree(postComIdempotencia(
                "/api/vendas/" + vendaId + "/finalizar",
                Map.of(),
                "f5-finaliza-" + vendaId
        ).getBody());

        assertEquals("FINALIZADA", finalizada.path("status").asText());
        assertBigDecimalEquals("110.0000", finalizada.path("valorTotal").decimalValue());
        assertBigDecimalEquals("8.0000", finalizada.path("custoIngredientesTotal").decimalValue());
        JsonNode item = finalizada.path("itens").get(0);
        assertTrue(item.path("composicaoMultipla").asBoolean());
        assertEquals("MAIOR_PRECO", item.path("politicaPrecoComposicao").asText());
        assertEquals(2, item.path("quantidadePartes").asInt());
        assertEquals(2, item.path("partes").size());
        assertEquals(2, item.path("consumos").size());
        assertTrue(item.path("resumoPartes").asText().contains("1/2"));
        assertConsumosComParteEMesmoMovimento(item.path("consumos"));
        assertEstoque(multi.produtoCompartilhadoId(), "8.000000");
        assertEquals(1, movimentosVendaDaVenda(vendaId).size());

        JsonNode cancelada = objectMapper.readTree(postComIdempotencia(
                "/api/vendas/" + vendaId + "/cancelar",
                Map.of("motivo", "Cancelamento multissabor"),
                "f5-cancela-" + vendaId
        ).getBody());
        assertEquals("CANCELADA", cancelada.path("status").asText());
        assertEstoque(multi.produtoCompartilhadoId(), "10.000000");
        assertEquals(1, movimentosEstornoVendaDaVenda(vendaId).size());
        JsonNode itemCancelado = cancelada.path("itens").get(0);
        assertEquals("CANCELADO", itemCancelado.path("status").asText());
        assertEquals("CANCELADO", itemCancelado.path("partes").get(0).path("status").asText());
        assertEquals("CANCELADO", itemCancelado.path("partes").get(1).path("status").asText());
    }

    @Test
    void regrasDePartesProporcoesEPoliticasDePrecoSaoValidadasNoBackend() throws Exception {
        MultissaborFixture multi = prepararMultissabor("f5_regras_" + System.nanoTime(), "10");

        ResponseEntity<String> duplicada = post(
                "/api/cardapio/variacoes/" + multi.baseId() + "/composicao-multipla/previsao",
                Map.of("partes", partes(multi.parteAId(), multi.parteAId()))
        );
        assertEquals(HttpStatus.BAD_REQUEST, duplicada.getStatusCode(), duplicada.getBody());
        assertEquals("PARTE_DUPLICADA_NAO_PERMITIDA",
                objectMapper.readTree(duplicada.getBody()).path("codigo").asText());

        ResponseEntity<String> naoPermitida = post(
                "/api/cardapio/variacoes/" + multi.baseId() + "/composicao-multipla/previsao",
                Map.of("partes", partes(multi.parteAId(), multi.baseId()))
        );
        assertEquals(HttpStatus.BAD_REQUEST, naoPermitida.getStatusCode(), naoPermitida.getBody());
        assertEquals("PARTE_NAO_PERMITIDA",
                objectMapper.readTree(naoPermitida.getBody()).path("codigo").asText());

        JsonNode tresPartes = prever(multi.baseId(), partes(multi.parteAId(), multi.parteBId(), multi.parteCId()));
        assertBigDecimalEquals("0.333333", tresPartes.path("partes").get(0).path("proporcao").decimalValue());
        assertBigDecimalEquals("0.333333", tresPartes.path("partes").get(1).path("proporcao").decimalValue());
        assertBigDecimalEquals("0.333334", tresPartes.path("partes").get(2).path("proporcao").decimalValue());

        configurarMultissabor(
                multi.baseId(),
                true,
                false,
                "MEDIA_ARITMETICA"
        );
        JsonNode media = prever(multi.baseId(), partes(multi.parteAId(), multi.parteBId()));
        assertBigDecimalEquals("47.5000", media.path("precoUnitarioBase").decimalValue());

        configurarMultissabor(
                multi.baseId(),
                false,
                true,
                "MEDIA_PONDERADA"
        );
        JsonNode ponderada = prever(multi.baseId(), List.of(
                parte(multi.parteAId(), "0.250000"),
                parte(multi.parteBId(), "0.750000")
        ));
        assertBigDecimalEquals("51.2500", ponderada.path("precoUnitarioBase").decimalValue());

        ResponseEntity<String> somaInvalida = post(
                "/api/cardapio/variacoes/" + multi.baseId() + "/composicao-multipla/previsao",
                Map.of("partes", List.of(
                        parte(multi.parteAId(), "0.400000"),
                        parte(multi.parteBId(), "0.500000")
                ))
        );
        assertEquals(HttpStatus.BAD_REQUEST, somaInvalida.getStatusCode(), somaInvalida.getBody());
        assertEquals("SOMA_PROPORCOES_INVALIDA",
                objectMapper.readTree(somaInvalida.getBody()).path("codigo").asText());

        configurarMultissabor(
                multi.baseId(),
                true,
                false,
                "PRECO_FIXO_DA_VARIACAO"
        );
        JsonNode precoFixo = prever(multi.baseId(), partes(multi.parteAId(), multi.parteBId()));
        assertBigDecimalEquals("50.0000", precoFixo.path("precoUnitarioBase").decimalValue());
    }

    @Test
    void hashAntigoDeComposicaoMultissaborBloqueiaFinalizacaoSemBaixarEstoque() throws Exception {
        MultissaborFixture multi = prepararMultissabor("f5_hash_" + System.nanoTime(), "10");
        JsonNode previsao = prever(multi.baseId(), partes(multi.parteAId(), multi.parteBId()));
        long vendaId = criarVendaMultissabor(
                multi.baseId(),
                "1",
                previsao.path("configuracaoHash").asText(),
                partes(multi.parteAId(), multi.parteBId())
        );

        atualizarPrecoVariacao(multi.parteBId(), "60.00");
        ResponseEntity<String> resposta = postComIdempotencia(
                "/api/vendas/" + vendaId + "/finalizar",
                Map.of(),
                "f5-hash-antigo-" + vendaId
        );

        assertEquals(HttpStatus.CONFLICT, resposta.getStatusCode(), resposta.getBody());
        assertEquals("CONFIGURACAO_MULTISSABOR_ALTERADA",
                objectMapper.readTree(resposta.getBody()).path("codigo").asText());
        assertEstoque(multi.produtoCompartilhadoId(), "10.000000");
        assertEquals(0, movimentosVendaDaVenda(vendaId).size());
        JsonNode rascunho = objectMapper.readTree(get("/api/vendas/" + vendaId).getBody());
        assertEquals("RASCUNHO", rascunho.path("status").asText());
    }

    @Test
    void duasVendasMultissaborDisputandoUltimaCapacidadeApenasUmaFinaliza() throws Exception {
        MultissaborFixture multi = prepararMultissabor("f5_concorrencia_" + System.nanoTime(), "1");
        JsonNode previsao = prever(multi.baseId(), partes(multi.parteAId(), multi.parteBId()));
        String hash = previsao.path("configuracaoHash").asText();
        long venda1 = criarVendaMultissabor(multi.baseId(), "1", hash, partes(multi.parteAId(), multi.parteBId()));
        long venda2 = criarVendaMultissabor(multi.baseId(), "1", hash, partes(multi.parteAId(), multi.parteBId()));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ResponseEntity<String>> primeira = executor.submit(() -> postComIdempotencia(
                    "/api/vendas/" + venda1 + "/finalizar",
                    Map.of(),
                    "f5-concorrencia-" + venda1
            ));
            Future<ResponseEntity<String>> segunda = executor.submit(() -> postComIdempotencia(
                    "/api/vendas/" + venda2 + "/finalizar",
                    Map.of(),
                    "f5-concorrencia-" + venda2
            ));

            ResponseEntity<String> resposta1 = primeira.get(20, TimeUnit.SECONDS);
            ResponseEntity<String> resposta2 = segunda.get(20, TimeUnit.SECONDS);
            int sucessos = (resposta1.getStatusCode() == HttpStatus.OK ? 1 : 0)
                    + (resposta2.getStatusCode() == HttpStatus.OK ? 1 : 0);
            int bloqueios = (resposta1.getStatusCode() == HttpStatus.BAD_REQUEST ? 1 : 0)
                    + (resposta2.getStatusCode() == HttpStatus.BAD_REQUEST ? 1 : 0);
            long finalizada = resposta1.getStatusCode() == HttpStatus.OK ? venda1 : venda2;
            long falha = finalizada == venda1 ? venda2 : venda1;
            ResponseEntity<String> bloqueada = resposta1.getStatusCode() == HttpStatus.BAD_REQUEST ? resposta1 : resposta2;

            assertEquals(1, sucessos, resposta1.getBody() + " | " + resposta2.getBody());
            assertEquals(1, bloqueios, resposta1.getBody() + " | " + resposta2.getBody());
            assertEquals("ESTOQUE_INSUFICIENTE", objectMapper.readTree(bloqueada.getBody()).path("codigo").asText());
            assertEstoque(multi.produtoCompartilhadoId(), "0.000000");
            assertEquals(1, movimentosVendaDaVenda(finalizada).size());
            assertEquals(0, movimentosVendaDaVenda(falha).size());
            JsonNode vendaFalha = objectMapper.readTree(get("/api/vendas/" + falha).getBody());
            assertEquals("RASCUNHO", vendaFalha.path("status").asText());
        } finally {
            executor.shutdownNow();
        }
    }

    private MultissaborFixture prepararMultissabor(String codigoBase, String quantidadeEstoque) throws Exception {
        Map<String, Long> unidades = unidadesPorCodigo();
        int produtoId = criarProduto(
                "Produto compartilhado multissabor " + codigoBase,
                "UNIDADE",
                "4.00000000",
                quantidadeEstoque
        );
        long baseId = criarItemDiretoComQuantidade(codigoBase + "_base", produtoId, "50.00", "1", unidades.get("UNIDADE"));
        long parteAId = criarItemDiretoComQuantidade(codigoBase + "_cal", produtoId, "40.00", "1", unidades.get("UNIDADE"));
        long parteBId = criarItemDiretoComQuantidade(codigoBase + "_cam", produtoId, "55.00", "1", unidades.get("UNIDADE"));
        long parteCId = criarItemDiretoComQuantidade(codigoBase + "_mar", produtoId, "45.00", "1", unidades.get("UNIDADE"));

        configurarMultissabor(baseId, true, false, "MAIOR_PRECO");
        permitirParte(baseId, parteAId, 1);
        permitirParte(baseId, parteBId, 2);
        permitirParte(baseId, parteCId, 3);
        return new MultissaborFixture(baseId, parteAId, parteBId, parteCId, produtoId);
    }

    private void configurarMultissabor(long baseId,
                                       boolean proporcoesIguaisObrigatorias,
                                       boolean permiteProporcaoPersonalizada,
                                       String politicaPreco) {
        ResponseEntity<String> resposta = put(
                "/api/cardapio/variacoes/" + baseId + "/composicao-multipla",
                Map.of(
                        "ativo", true,
                        "minimoPartes", 2,
                        "maximoPartes", 3,
                        "quantidadesPermitidas", "2,3",
                        "permiteRepetirPartes", false,
                        "proporcoesIguaisObrigatorias", proporcoesIguaisObrigatorias,
                        "permiteProporcaoPersonalizada", permiteProporcaoPersonalizada,
                        "politicaPreco", politicaPreco,
                        "politicaArredondamento", "HALF_UP"
                )
        );
        assertEquals(HttpStatus.OK, resposta.getStatusCode(), resposta.getBody());
    }

    private void permitirParte(long baseId, long parteId, int ordem) {
        ResponseEntity<String> resposta = put(
                "/api/cardapio/variacoes/" + baseId + "/composicao-multipla/partes/" + parteId,
                Map.of("ativa", true, "ordemExibicao", ordem)
        );
        assertEquals(HttpStatus.OK, resposta.getStatusCode(), resposta.getBody());
    }

    private JsonNode prever(long baseId, List<Map<String, Object>> partes) throws Exception {
        ResponseEntity<String> resposta = post(
                "/api/cardapio/variacoes/" + baseId + "/composicao-multipla/previsao",
                Map.of("partes", partes)
        );
        assertEquals(HttpStatus.OK, resposta.getStatusCode(), resposta.getBody());
        return objectMapper.readTree(resposta.getBody());
    }

    private long criarVendaMultissabor(long baseId,
                                       String quantidade,
                                       String hash,
                                       List<Map<String, Object>> partes) throws Exception {
        ResponseEntity<String> resposta = post(
                "/api/vendas",
                Map.of("filialId", 1, "canal", "BALCAO", "itens", List.of(
                        Map.of(
                                "variacaoId", baseId,
                                "quantidade", quantidade,
                                "configuracaoMultissaborVersao", hash,
                                "partes", partes
                        )
                ))
        );
        assertEquals(HttpStatus.CREATED, resposta.getStatusCode(), resposta.getBody());
        return objectMapper.readTree(resposta.getBody()).path("id").asLong();
    }

    private Map<String, Long> unidadesPorCodigo() throws Exception {
        JsonNode unidades = objectMapper.readTree(get("/api/unidades").getBody());
        Map<String, Long> ids = new LinkedHashMap<>();
        for (JsonNode unidade : unidades) {
            ids.put(unidade.path("codigo").asText(), unidade.path("id").asLong());
        }
        return ids;
    }

    private int criarProduto(String nome, String unidadeBaseCodigo, String custoMedio, String quantidade) throws Exception {
        int produtoId = objectMapper.readTree(post("/api/produtos", produtoPayload(nome, unidadeBaseCodigo)).getBody())
                .path("id")
                .asInt();
        Produto produto = produtoRepository.buscarPorId(produtoId).orElseThrow();
        estoqueRepository.salvar(new EstoqueProduto(
                0,
                1L,
                produto,
                new BigDecimal(quantidade),
                BigDecimal.ZERO,
                new BigDecimal(custoMedio)
        ));
        return produtoId;
    }

    private long criarItemDiretoComQuantidade(String codigo,
                                              int produtoId,
                                              String preco,
                                              String quantidadeBase,
                                              long unidadeId) throws Exception {
        int categoriaId = objectMapper.readTree(post(
                "/api/cardapio/admin/categorias",
                Map.of("codigo", codigo + "_cat", "nomeExibicao", "Multissabor " + codigo, "ordemExibicao", 5)
        ).getBody()).path("id").asInt();
        int itemId = objectMapper.readTree(post(
                "/api/cardapio/admin/itens",
                itemDiretoPayload(codigo, "Sabor " + codigo, categoriaId, produtoId)
        ).getBody()).path("id").asInt();
        JsonNode variacao = objectMapper.readTree(post(
                "/api/cardapio/itens/" + itemId + "/variacoes",
                variacaoDiretaPayload("Fase 5", "F5", preco, produtoId, quantidadeBase)
        ).getBody());
        assertBigDecimalEquals(new BigDecimal(quantidadeBase).setScale(6).toPlainString(), variacao.path("quantidadeProdutoDiretoBase").decimalValue());
        assertTrue(unidadeId > 0);
        return variacao.path("id").asLong();
    }

    private void atualizarPrecoVariacao(long variacaoId, String novoPreco) throws Exception {
        JsonNode variacao = objectMapper.readTree(get("/api/cardapio/variacoes/" + variacaoId).getBody());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("nome", variacao.path("nome").asText());
        body.put("codigo", variacao.path("codigo").asText());
        body.put("precoVenda", novoPreco);
        body.put("tipoComposicao", variacao.path("tipoComposicao").asText());
        body.put("produtoDiretoId", variacao.path("produtoDiretoId").asInt());
        body.put("quantidadeProdutoDiretoBase", variacao.path("quantidadeProdutoDiretoBase").asText());
        body.put("ativa", variacao.path("ativa").asBoolean());
        body.put("padrao", variacao.path("padrao").asBoolean());
        body.put("ordemExibicao", variacao.path("ordemExibicao").asInt());
        assertEquals(HttpStatus.OK, put("/api/cardapio/variacoes/" + variacaoId, body).getStatusCode());
    }

    private void assertConsumosComParteEMesmoMovimento(JsonNode consumos) {
        Set<Long> parteIds = new java.util.LinkedHashSet<>();
        Set<Long> movimentoIds = new java.util.LinkedHashSet<>();
        BigDecimal total = BigDecimal.ZERO;
        for (JsonNode consumo : consumos) {
            assertTrue(consumo.path("vendaItemParteId").asLong() > 0);
            assertTrue(consumo.path("movimentoEstoqueId").asLong() > 0);
            parteIds.add(consumo.path("vendaItemParteId").asLong());
            movimentoIds.add(consumo.path("movimentoEstoqueId").asLong());
            total = total.add(consumo.path("quantidadeBaseTotal").decimalValue());
        }
        assertEquals(2, parteIds.size());
        assertEquals(1, movimentoIds.size());
        assertBigDecimalEquals("2.000000", total);
    }

    private void assertEstoque(int produtoId, String quantidadeEsperada) {
        EstoqueProduto estoque = estoqueRepository.buscarPorFilialEProduto(1L, produtoId).orElseThrow();
        assertBigDecimalEquals(quantidadeEsperada, estoque.getQuantidadeAtual());
    }

    private List<MovimentoEstoque> movimentosVendaDaVenda(long vendaId) {
        return movimentoRepository.listarTodos().stream()
                .filter(m -> "VENDA".equals(m.getOrigemTipo()))
                .filter(m -> m.getOrigemId().equals(vendaId))
                .filter(m -> m.getTipo() == TipoMovimentoEstoque.SAIDA_VENDA)
                .toList();
    }

    private List<MovimentoEstoque> movimentosEstornoVendaDaVenda(long vendaId) {
        return movimentoRepository.listarTodos().stream()
                .filter(m -> "VENDA".equals(m.getOrigemTipo()))
                .filter(m -> m.getOrigemId().equals(vendaId))
                .filter(m -> m.getTipo() == TipoMovimentoEstoque.ESTORNO_VENDA)
                .toList();
    }

    private ResponseEntity<String> get(String path) {
        return restTemplate.exchange(url(path), HttpMethod.GET, new HttpEntity<>(headers()), String.class);
    }

    private ResponseEntity<String> post(String path, Object body) {
        return restTemplate.exchange(url(path), HttpMethod.POST, new HttpEntity<>(body, headers()), String.class);
    }

    private ResponseEntity<String> postComIdempotencia(String path, Object body, String chave) {
        HttpHeaders headers = headers();
        headers.set("Idempotency-Key", chave);
        return restTemplate.exchange(url(path), HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<String> put(String path, Object body) {
        return restTemplate.exchange(url(path), HttpMethod.PUT, new HttpEntity<>(body, headers()), String.class);
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-User-RU", String.valueOf(gerente.getRu()));
        return headers;
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private static List<Map<String, Object>> partes(long... variacaoIds) {
        return java.util.Arrays.stream(variacaoIds)
                .mapToObj(Fase5MultissaborApiIntegrationTest::parte)
                .collect(Collectors.toList());
    }

    private static Map<String, Object> parte(long variacaoId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("variacaoParteId", variacaoId);
        return body;
    }

    private static Map<String, Object> parte(long variacaoId, String proporcao) {
        Map<String, Object> body = parte(variacaoId);
        body.put("proporcao", proporcao);
        return body;
    }

    private static Map<String, Object> produtoPayload(String nome, String unidadeBaseCodigo) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("nome", nome);
        body.put("quantidadeInicial", 0);
        body.put("quantidadeMinima", 0);
        body.put("precoUnitario", "0.00");
        body.put("categoriaEstoque", "FRIO");
        body.put("unidadeBaseCodigo", unidadeBaseCodigo);
        body.put("politicaFalta", "BLOQUEAR");
        body.put("ativo", true);
        return body;
    }

    private static Map<String, Object> itemDiretoPayload(String codigo, String nome, int categoriaId, int produtoId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("codigo", codigo);
        body.put("nome", nome);
        body.put("descricaoCurta", "Item multissabor de teste");
        body.put("precoVenda", "50.00");
        body.put("categoriaId", categoriaId);
        body.put("tipoItem", "ESTOQUE_DIRETO");
        body.put("ordemExibicao", 1);
        body.put("produtoVinculadoId", produtoId);
        return body;
    }

    private static Map<String, Object> variacaoDiretaPayload(String nome,
                                                             String codigo,
                                                             String preco,
                                                             int produtoId,
                                                             String quantidadeBase) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("nome", nome);
        body.put("codigo", codigo);
        body.put("precoVenda", preco);
        body.put("tipoComposicao", "PRODUTO_DIRETO");
        body.put("produtoDiretoId", produtoId);
        body.put("quantidadeProdutoDiretoBase", quantidadeBase);
        body.put("ativa", true);
        body.put("padrao", false);
        body.put("ordemExibicao", 1);
        return body;
    }

    private static void assertBigDecimalEquals(String esperado, BigDecimal atual) {
        assertEquals(0, new BigDecimal(esperado).compareTo(atual), "Esperado " + esperado + ", atual " + atual);
    }

    private record MultissaborFixture(
            long baseId,
            long parteAId,
            long parteBId,
            long parteCId,
            int produtoCompartilhadoId
    ) {}
}
