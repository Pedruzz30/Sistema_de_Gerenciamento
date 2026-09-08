package integration;

import app.Main;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import model.EstoqueProduto;
import model.MovimentoEstoque;
import model.Produto;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        classes = Main.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.port=0"
)
class Fase3DisponibilidadeApiIntegrationTest {

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
                .filter(usuario -> "11144477735".equals(usuario.getCpfBruto()))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void pizzaCalabresaRetornaCapacidadeLimitanteEFaltaBloqueanteSemMovimentarEstoque() throws Exception {
        PizzaFixture pizza = prepararPizzaCalabresa("f3_pizza_" + System.nanoTime());
        long movimentosAntes = movimentosDosProdutos(Set.of(
                pizza.massaId(),
                pizza.molhoId(),
                pizza.mussarelaId(),
                pizza.calabresaId(),
                pizza.cebolaId()
        ));

        JsonNode disponivel = objectMapper.readTree(get(
                "/api/cardapio/variacoes/" + pizza.variacaoId() + "/disponibilidade?filialId=1&canal=BALCAO"
        ).getBody());

        assertTrue(disponivel.path("disponivel").asBoolean());
        assertEquals("DISPONIVEL", disponivel.path("estadoPrincipal").asText());
        assertEquals(16, disponivel.path("capacidadeEstimada").asInt());
        assertEquals("ESTIMATIVA_ITEM_ISOLADO", disponivel.path("tipoCapacidade").asText());
        assertEquals("ESTIMATIVA_SEM_RESERVA", disponivel.path("tipoValidacao").asText());
        assertFalse(disponivel.path("garantia").asBoolean());
        assertTrue(disponivel.path("ingredienteLimitantePrincipal").path("produto").asText().startsWith("Calabresa F3"));

        redefinirEstoque(pizza.calabresaId(), "140", "0.03000000");
        JsonNode indisponivel = objectMapper.readTree(get(
                "/api/cardapio/variacoes/" + pizza.variacaoId() + "/disponibilidade?filialId=1&canal=BALCAO"
        ).getBody());

        assertFalse(indisponivel.path("disponivel").asBoolean());
        assertEquals("ESTOQUE_INSUFICIENTE", indisponivel.path("estadoPrincipal").asText());
        assertEquals(0, indisponivel.path("capacidadeEstimada").asInt());
        JsonNode motivo = encontrarMotivo(indisponivel.path("motivos"), "ESTOQUE_INSUFICIENTE");
        assertTrue(motivo.path("produtoNome").asText().startsWith("Calabresa F3"));
        assertBigDecimalEquals("150.000000", motivo.path("quantidadeNecessaria").decimalValue());
        assertBigDecimalEquals("140.000000", motivo.path("quantidadeDisponivel").decimalValue());
        assertEquals(movimentosAntes, movimentosDosProdutos(Set.of(
                pizza.massaId(),
                pizza.molhoId(),
                pizza.mussarelaId(),
                pizza.calabresaId(),
                pizza.cebolaId()
        )), "Consulta de disponibilidade nao deve criar movimento de estoque.");
    }

    @Test
    void politicaAlertarNaoLimitaCapacidade() throws Exception {
        Map<String, Long> unidades = unidadesPorCodigo();
        String sufixo = "f3_alerta_" + System.nanoTime();
        int massaId = criarProduto("Massa alerta " + sufixo, "GRAMA", "1000", "0.01000000", "BLOQUEAR");
        int oreganoId = criarProduto("Oregano alerta " + sufixo, "GRAMA", "0", "0.01000000", "BLOQUEAR");
        long variacaoId = criarVariacaoPreparadaPublicada(
                sufixo,
                List.of(
                        ingrediente(massaId, unidades.get("GRAMA"), "100", "HERDAR_PRODUTO"),
                        ingrediente(oreganoId, unidades.get("GRAMA"), "2", "ALERTAR")
                )
        );

        JsonNode resposta = objectMapper.readTree(get(
                "/api/cardapio/variacoes/" + variacaoId + "/disponibilidade?filialId=1&canal=BALCAO"
        ).getBody());

        assertTrue(resposta.path("disponivel").asBoolean());
        assertEquals("DISPONIVEL_COM_ALERTA", resposta.path("estadoPrincipal").asText());
        assertEquals(10, resposta.path("capacidadeEstimada").asInt());
        assertEquals("Oregano alerta " + sufixo,
                encontrarMotivo(resposta.path("alertas"), "ESTOQUE_INSUFICIENTE").path("produtoNome").asText());
    }

    @Test
    void pausaPorCanalBloqueiaApenasDeliveryEEncerramentoLibera() throws Exception {
        PizzaFixture pizza = prepararPizzaCalabresa("f3_pausa_" + System.nanoTime());

        ResponseEntity<String> pausaResponse = post(
                "/api/cardapio/pausas",
                Map.of(
                        "filialId", 1,
                        "variacaoId", pizza.variacaoId(),
                        "canal", "DELIVERY_PROPRIO",
                        "motivoCodigo", "SOBRECARGA_DA_COZINHA",
                        "motivoDescricao", "Fila acima de 40 minutos"
                )
        );
        assertEquals(HttpStatus.CREATED, pausaResponse.getStatusCode(), pausaResponse.getBody());
        long pausaId = objectMapper.readTree(pausaResponse.getBody()).path("id").asLong();

        assertTrue(disponibilidade(pizza.variacaoId(), "SALAO").path("disponivel").asBoolean());
        assertTrue(disponibilidade(pizza.variacaoId(), "BALCAO").path("disponivel").asBoolean());
        JsonNode delivery = disponibilidade(pizza.variacaoId(), "DELIVERY_PROPRIO");
        assertFalse(delivery.path("disponivel").asBoolean());
        assertEquals("PAUSADO", delivery.path("estadoPrincipal").asText());
        encontrarMotivo(delivery.path("motivos"), "VARIACAO_PAUSADA");

        ResponseEntity<String> encerrada = post("/api/cardapio/pausas/" + pausaId + "/encerrar", Map.of());
        assertEquals(HttpStatus.OK, encerrada.getStatusCode(), encerrada.getBody());
        assertTrue(disponibilidade(pizza.variacaoId(), "DELIVERY_PROPRIO").path("disponivel").asBoolean());
    }

    @Test
    void produtoDiretoUnitarioEFracionadoCalculaCapacidade() throws Exception {
        Map<String, Long> unidades = unidadesPorCodigo();
        int refriId = criarProduto("Refrigerante direto F3 " + System.nanoTime(), "UNIDADE", "10", "4.00000000", "BLOQUEAR");
        long refriVariacao = criarVariacaoProdutoDireto("refri_f3_" + System.nanoTime(), refriId, "UNIDADE", "1");
        assertEquals(10, disponibilidade(refriVariacao, "BALCAO").path("capacidadeEstimada").asInt());

        int bebidaId = criarProduto("Bebida dose F3 " + System.nanoTime(), "MILILITRO", "2000", "0.05000000", "BLOQUEAR");
        long doseVariacao = criarVariacaoProdutoDireto("dose_f3_" + System.nanoTime(), bebidaId, "MILILITRO", "50");
        JsonNode dose = disponibilidade(doseVariacao, "BALCAO");
        assertTrue(dose.path("disponivel").asBoolean());
        assertEquals(40, dose.path("capacidadeEstimada").asInt());
        assertEquals(unidades.get("MILILITRO").longValue(), unidadesPorCodigo().get("MILILITRO").longValue());
    }

    @Test
    void consultaEmLoteResumeItemENaoCriaReservaOuMovimento() throws Exception {
        PizzaFixture pizza = prepararPizzaCalabresa("f3_lote_" + System.nanoTime());
        long movimentosAntes = movimentoRepository.listarTodos().size();

        JsonNode lote = objectMapper.readTree(get("/api/cardapio/disponibilidade?filialId=1&canal=BALCAO").getBody());

        assertTrue(lote.path("variacoes").size() > 0);
        boolean encontrouItem = false;
        for (JsonNode item : lote.path("itens")) {
            if (item.path("itemCardapioId").asInt() == pizza.itemId()) {
                encontrouItem = true;
                assertTrue(item.path("itemDisponivel").asBoolean());
                assertTrue(item.path("quantidadeVariacoesDisponiveis").asInt() >= 1);
            }
        }
        assertTrue(encontrouItem);
        assertEquals(movimentosAntes, movimentoRepository.listarTodos().size());
        assertBigDecimalEquals("2500.000000",
                estoqueRepository.buscarPorFilialEProduto(1L, pizza.calabresaId()).orElseThrow().getQuantidadeAtual());
        assertBigDecimalEquals("0.000000",
                estoqueRepository.buscarPorFilialEProduto(1L, pizza.calabresaId()).orElseThrow().getQuantidadeReservada());
    }

    @Test
    void regraHorarioPodeBloquearCanal() throws Exception {
        PizzaFixture pizza = prepararPizzaCalabresa("f3_horario_" + System.nanoTime());
        ResponseEntity<String> criada = post(
                "/api/cardapio/regras-horario",
                Map.of(
                        "filialId", 1,
                        "variacaoId", pizza.variacaoId(),
                        "canal", "BALCAO",
                        "diaSemana", "MONDAY",
                        "horaInicio", "00:00:00",
                        "horaFim", "00:30:00",
                        "ativa", true
                )
        );
        assertEquals(HttpStatus.CREATED, criada.getStatusCode(), criada.getBody());

        JsonNode resposta = disponibilidade(pizza.variacaoId(), "BALCAO");
        if (!"MONDAY".equals(java.time.LocalDate.now().getDayOfWeek().name())
                || java.time.LocalTime.now().isAfter(java.time.LocalTime.of(0, 30))) {
            assertFalse(resposta.path("disponivel").asBoolean());
            assertEquals("FORA_DO_HORARIO", resposta.path("estadoPrincipal").asText());
        }
    }

    private PizzaFixture prepararPizzaCalabresa(String sufixo) throws Exception {
        Map<String, Long> unidades = unidadesPorCodigo();
        int massaId = criarProduto("Massa F3 " + sufixo, "GRAMA", "8000", "0.00628571", "BLOQUEAR");
        int molhoId = criarProduto("Molho F3 " + sufixo, "MILILITRO", "5000", "0.00916667", "BLOQUEAR");
        int mussarelaId = criarProduto("Mussarela F3 " + sufixo, "GRAMA", "5000", "0.04000000", "BLOQUEAR");
        int calabresaId = criarProduto("Calabresa F3 " + sufixo, "GRAMA", "2500", "0.03000000", "BLOQUEAR");
        int cebolaId = criarProduto("Cebola F3 " + sufixo, "GRAMA", "2000", "0.01000000", "BLOQUEAR");
        long variacaoId = criarVariacaoPreparadaPublicada(
                sufixo,
                List.of(
                        ingrediente(massaId, unidades.get("GRAMA"), "350", "HERDAR_PRODUTO"),
                        ingrediente(molhoId, unidades.get("MILILITRO"), "120", "HERDAR_PRODUTO"),
                        ingrediente(mussarelaId, unidades.get("GRAMA"), "190", "HERDAR_PRODUTO"),
                        ingrediente(calabresaId, unidades.get("GRAMA"), "150", "HERDAR_PRODUTO"),
                        ingrediente(cebolaId, unidades.get("GRAMA"), "30", "HERDAR_PRODUTO")
                )
        );
        int itemId = buscarItemIdDaVariacao(variacaoId);
        return new PizzaFixture(itemId, variacaoId, massaId, molhoId, mussarelaId, calabresaId, cebolaId);
    }

    private long criarVariacaoPreparadaPublicada(String sufixo, List<Map<String, Object>> ingredientes) throws Exception {
        Map<String, Long> unidades = unidadesPorCodigo();
        int categoriaId = objectMapper.readTree(post(
                "/api/cardapio/admin/categorias",
                Map.of("codigo", "cat_" + sufixo, "nomeExibicao", "Categoria " + sufixo, "ordemExibicao", 1)
        ).getBody()).path("id").asInt();
        int itemId = objectMapper.readTree(post(
                "/api/cardapio/admin/itens",
                itemPayload("item_" + sufixo, "Item " + sufixo, categoriaId, "PREPARADO_SOB_DEMANDA", null)
        ).getBody()).path("id").asInt();
        long variacaoId = objectMapper.readTree(post(
                "/api/cardapio/itens/" + itemId + "/variacoes",
                variacaoPreparadaPayload("Grande", "GRANDE_" + sufixo, "55.00")
        ).getBody()).path("id").asLong();
        JsonNode ficha = objectMapper.readTree(post(
                "/api/cardapio/variacoes/" + variacaoId + "/ficha-tecnica",
                Map.of(
                        "nome", "Ficha " + sufixo,
                        "rendimentoQuantidade", "1",
                        "rendimentoUnidadeId", unidades.get("UNIDADE"),
                        "tempoPreparoMinutos", 20
                )
        ).getBody());
        long fichaId = ficha.path("fichaTecnicaId").asLong();
        long versaoId = ficha.path("id").asLong();
        for (Map<String, Object> ingrediente : ingredientes) {
            assertEquals(HttpStatus.CREATED, post(
                    "/api/fichas-tecnicas/" + fichaId + "/versoes/" + versaoId + "/ingredientes",
                    ingrediente
            ).getStatusCode());
        }
        ResponseEntity<String> publicada = post(
                "/api/fichas-tecnicas/" + fichaId + "/versoes/" + versaoId + "/publicar",
                Map.of("filialReferenciaId", 1, "motivo", "Publicacao Fase 3")
        );
        assertEquals(HttpStatus.OK, publicada.getStatusCode(), publicada.getBody());
        return variacaoId;
    }

    private long criarVariacaoProdutoDireto(String sufixo,
                                            int produtoId,
                                            String unidadeCodigo,
                                            String quantidadeBase) throws Exception {
        int categoriaId = objectMapper.readTree(post(
                "/api/cardapio/admin/categorias",
                Map.of("codigo", "cat_" + sufixo, "nomeExibicao", "Categoria " + sufixo, "ordemExibicao", 1)
        ).getBody()).path("id").asInt();
        int itemId = objectMapper.readTree(post(
                "/api/cardapio/admin/itens",
                itemPayload("item_" + sufixo, "Item " + sufixo, categoriaId, "ESTOQUE_DIRETO", produtoId)
        ).getBody()).path("id").asInt();
        return objectMapper.readTree(post(
                "/api/cardapio/itens/" + itemId + "/variacoes",
                Map.of(
                        "nome", "Padrao " + sufixo,
                        "codigo", "PADRAO_" + sufixo,
                        "precoVenda", "12.00",
                        "tipoComposicao", "PRODUTO_DIRETO",
                        "produtoDiretoId", produtoId,
                        "quantidadeProdutoDiretoBase", quantidadeBase,
                        "ativa", true,
                        "padrao", false,
                        "ordemExibicao", 1
                )
        ).getBody()).path("id").asLong();
    }

    private int buscarItemIdDaVariacao(long variacaoId) throws Exception {
        return objectMapper.readTree(get("/api/cardapio/variacoes/" + variacaoId).getBody())
                .path("itemCardapioId")
                .asInt();
    }

    private int criarProduto(String nome,
                             String unidadeBaseCodigo,
                             String quantidadeAtual,
                             String custoMedio,
                             String politicaFalta) throws Exception {
        int produtoId = objectMapper.readTree(post("/api/produtos", produtoPayload(nome, unidadeBaseCodigo, politicaFalta)).getBody())
                .path("id")
                .asInt();
        Produto produto = produtoRepository.buscarPorId(produtoId).orElseThrow();
        estoqueRepository.salvar(new EstoqueProduto(
                0,
                1L,
                produto,
                new BigDecimal(quantidadeAtual),
                BigDecimal.ZERO,
                new BigDecimal(custoMedio)
        ));
        return produtoId;
    }

    private void redefinirEstoque(int produtoId, String quantidadeAtual, String custoMedio) {
        EstoqueProduto estoque = estoqueRepository.buscarPorFilialEProduto(1L, produtoId).orElseThrow();
        estoque.redefinirQuantidadeECusto(new BigDecimal(quantidadeAtual), new BigDecimal(custoMedio));
        estoqueRepository.salvar(estoque);
    }

    private JsonNode disponibilidade(long variacaoId, String canal) throws Exception {
        return objectMapper.readTree(get(
                "/api/cardapio/variacoes/" + variacaoId + "/disponibilidade?filialId=1&canal=" + canal
        ).getBody());
    }

    private JsonNode encontrarMotivo(JsonNode motivos, String codigo) {
        for (JsonNode motivo : motivos) {
            if (codigo.equals(motivo.path("codigo").asText())) {
                return motivo;
            }
        }
        throw new AssertionError("Motivo nao encontrado: " + codigo + " em " + motivos);
    }

    private long movimentosDosProdutos(Set<Integer> produtoIds) {
        return movimentoRepository.listarTodos().stream()
                .map(MovimentoEstoque::getProduto)
                .filter(produto -> produtoIds.contains(produto.getId()))
                .count();
    }

    private Map<String, Long> unidadesPorCodigo() throws Exception {
        JsonNode unidades = objectMapper.readTree(get("/api/unidades").getBody());
        Map<String, Long> ids = new LinkedHashMap<>();
        for (JsonNode unidade : unidades) {
            ids.put(unidade.path("codigo").asText(), unidade.path("id").asLong());
        }
        return ids;
    }

    private ResponseEntity<String> get(String path) {
        return restTemplate.exchange(url(path), HttpMethod.GET, new HttpEntity<>(headers()), String.class);
    }

    private ResponseEntity<String> post(String path, Object body) {
        return restTemplate.exchange(url(path), HttpMethod.POST, new HttpEntity<>(body, headers()), String.class);
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

    private static Map<String, Object> produtoPayload(String nome, String unidadeBaseCodigo, String politicaFalta) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("nome", nome);
        body.put("quantidadeInicial", 0);
        body.put("quantidadeMinima", 0);
        body.put("precoUnitario", "0.00");
        body.put("categoriaEstoque", "FRIO");
        body.put("unidadeBaseCodigo", unidadeBaseCodigo);
        body.put("politicaFalta", politicaFalta);
        body.put("ativo", true);
        return body;
    }

    private static Map<String, Object> itemPayload(String codigo,
                                                   String nome,
                                                   int categoriaId,
                                                   String tipoItem,
                                                   Integer produtoVinculadoId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("codigo", codigo);
        body.put("nome", nome);
        body.put("descricaoCurta", "Item Fase 3");
        body.put("precoVenda", "55.00");
        body.put("categoriaId", categoriaId);
        body.put("tipoItem", tipoItem);
        body.put("ordemExibicao", 1);
        body.put("produtoVinculadoId", produtoVinculadoId);
        return body;
    }

    private static Map<String, Object> variacaoPreparadaPayload(String nome, String codigo, String preco) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("nome", nome);
        body.put("codigo", codigo);
        body.put("precoVenda", preco);
        body.put("tipoComposicao", "PREPARADO");
        body.put("ativa", true);
        body.put("padrao", false);
        body.put("ordemExibicao", 1);
        return body;
    }

    private static Map<String, Object> ingrediente(int produtoId,
                                                   long unidadeId,
                                                   String quantidade,
                                                   String politicaFalta) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("produtoId", produtoId);
        body.put("unidadeInformadaId", unidadeId);
        body.put("quantidadeInformada", quantidade);
        body.put("aplicarPerda", false);
        body.put("ingredienteCritico", true);
        body.put("politicaFalta", politicaFalta);
        body.put("ordem", 1);
        return body;
    }

    private static void assertBigDecimalEquals(String esperado, BigDecimal atual) {
        assertEquals(0, new BigDecimal(esperado).compareTo(atual), "Esperado " + esperado + ", atual " + atual);
    }

    private record PizzaFixture(
            int itemId,
            long variacaoId,
            int massaId,
            int molhoId,
            int mussarelaId,
            int calabresaId,
            int cebolaId
    ) {
    }
}
