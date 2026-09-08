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
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        classes = Main.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.port=0"
)
class Fase1FichaTecnicaApiIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private EstoqueProdutoRepository estoqueProdutoRepository;

    @Autowired
    private MovimentoEstoqueRepository movimentoEstoqueRepository;

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
    void fluxoPizzaCalabresaFichaVersionadaCustoSnapshotECompatibilidade() throws Exception {
        Map<String, Long> unidades = unidadesPorCodigo();
        String sufixo = String.valueOf(System.nanoTime());

        int massaId = criarProduto("Massa pronta F1 " + sufixo, "GRAMA", "0.00628571");
        int molhoId = criarProduto("Molho F1 " + sufixo, "MILILITRO", "0.00916667");
        int mussarelaId = criarProduto("Mussarela F1 " + sufixo, "GRAMA", "0.04000000");
        int calabresaId = criarProduto("Calabresa F1 " + sufixo, "GRAMA", "0.03000000");
        int cebolaId = criarProduto("Cebola F1 " + sufixo, "GRAMA", "0.01000000");
        Set<Integer> produtosDaFicha = Set.of(massaId, molhoId, mussarelaId, calabresaId, cebolaId);
        long movimentosAntes = movimentosDosProdutos(produtosDaFicha);

        int categoriaId = objectMapper.readTree(post(
                "/api/cardapio/admin/categorias",
                Map.of("codigo", "pizzas_f1_" + sufixo, "nomeExibicao", "Pizzas F1", "ordemExibicao", 1)
        ).getBody()).path("id").asInt();

        int itemId = objectMapper.readTree(post(
                "/api/cardapio/admin/itens",
                itemPayload("pizza_calabresa_f1_" + sufixo, "Pizza Calabresa F1", categoriaId)
        ).getBody()).path("id").asInt();

        JsonNode variacoesIniciais = objectMapper.readTree(get("/api/cardapio/itens/" + itemId + "/variacoes").getBody());
        assertTrue(variacoesIniciais.size() >= 1);
        assertEquals("PADRAO", variacoesIniciais.get(0).path("codigo").asText());

        JsonNode variacaoGrande = objectMapper.readTree(post(
                "/api/cardapio/itens/" + itemId + "/variacoes",
                variacaoPreparadaPayload("Grande", "GRANDE", "55.00")
        ).getBody());
        long variacaoId = variacaoGrande.path("id").asLong();

        JsonNode fichaCriada = objectMapper.readTree(post(
                "/api/cardapio/variacoes/" + variacaoId + "/ficha-tecnica",
                fichaPayload("Pizza Calabresa Grande", unidades.get("UNIDADE"))
        ).getBody());
        long fichaId = fichaCriada.path("fichaTecnicaId").asLong();
        long versao1Id = fichaCriada.path("id").asLong();

        adicionarIngrediente(fichaId, versao1Id, massaId, unidades.get("GRAMA"), "350");
        adicionarIngrediente(fichaId, versao1Id, molhoId, unidades.get("MILILITRO"), "120");
        adicionarIngrediente(fichaId, versao1Id, mussarelaId, unidades.get("GRAMA"), "190");
        adicionarIngrediente(fichaId, versao1Id, calabresaId, unidades.get("GRAMA"), "150");
        adicionarIngrediente(fichaId, versao1Id, cebolaId, unidades.get("GRAMA"), "30");

        JsonNode custoV1 = objectMapper.readTree(get(
                "/api/fichas-tecnicas/" + fichaId + "/versoes/" + versao1Id + "/custo?filialId=1"
        ).getBody());
        assertTrue(custoV1.path("custoCompleto").asBoolean());
        assertBigDecimalEquals("15.7000", custoV1.path("custoTotal").decimalValue());
        assertBigDecimalEquals("28.5455", custoV1.path("cmvPercentual").decimalValue());
        assertEquals("ESTIMATIVA_APENAS_INGREDIENTES", custoV1.path("tipoAnalise").asText());

        JsonNode publicada = objectMapper.readTree(post(
                "/api/fichas-tecnicas/" + fichaId + "/versoes/" + versao1Id + "/publicar",
                Map.of("filialReferenciaId", 1, "motivo", "Publicacao inicial")
        ).getBody());
        assertEquals("PUBLICADA", publicada.path("status").asText());
        assertEquals(1, publicada.path("snapshots").size());
        BigDecimal snapshotV1 = publicada.path("snapshots").get(0).path("custoTotal").decimalValue();
        assertBigDecimalEquals("15.7000", snapshotV1);

        ResponseEntity<String> tentativaEditarPublicada = put(
                "/api/fichas-tecnicas/" + fichaId + "/versoes/" + versao1Id + "/ingredientes/" +
                        publicada.path("ingredientes").get(0).path("id").asLong(),
                ingredientePayload(massaId, unidades.get("GRAMA"), "360")
        );
        assertEquals(HttpStatus.CONFLICT, tentativaEditarPublicada.getStatusCode());

        alterarCustoMedio(mussarelaId, "0.05000000");
        JsonNode custoAtualAlterado = objectMapper.readTree(get(
                "/api/fichas-tecnicas/" + fichaId + "/versoes/" + versao1Id + "/custo?filialId=1"
        ).getBody());
        assertNotEquals(0, custoAtualAlterado.path("custoTotal").decimalValue().compareTo(snapshotV1));
        assertBigDecimalEquals("17.6000", custoAtualAlterado.path("custoTotal").decimalValue());

        JsonNode versao1Consultada = objectMapper.readTree(get(
                "/api/fichas-tecnicas/" + fichaId + "/versoes/" + versao1Id
        ).getBody());
        assertBigDecimalEquals("15.7000", versao1Consultada.path("snapshots").get(0).path("custoTotal").decimalValue());

        JsonNode versao2 = objectMapper.readTree(post(
                "/api/fichas-tecnicas/" + fichaId + "/nova-versao",
                Map.of()
        ).getBody());
        long versao2Id = versao2.path("id").asLong();
        long ingredienteMussarelaV2 = encontrarIngrediente(versao2, mussarelaId);
        put(
                "/api/fichas-tecnicas/" + fichaId + "/versoes/" + versao2Id + "/ingredientes/" + ingredienteMussarelaV2,
                ingredientePayload(mussarelaId, unidades.get("GRAMA"), "180")
        );
        JsonNode publicadaV2 = objectMapper.readTree(post(
                "/api/fichas-tecnicas/" + fichaId + "/versoes/" + versao2Id + "/publicar",
                Map.of("filialReferenciaId", 1, "motivo", "Reducao de mussarela para 180g")
        ).getBody());
        assertEquals("PUBLICADA", publicadaV2.path("status").asText());

        JsonNode versao1Substituida = objectMapper.readTree(get(
                "/api/fichas-tecnicas/" + fichaId + "/versoes/" + versao1Id
        ).getBody());
        assertEquals("SUBSTITUIDA", versao1Substituida.path("status").asText());
        assertBigDecimalEquals("15.7000", versao1Substituida.path("snapshots").get(0).path("custoTotal").decimalValue());

        long movimentosDepois = movimentosDosProdutos(produtosDaFicha);
        assertEquals(movimentosAntes, movimentosDepois, "Ficha tecnica nao deve gerar movimento de estoque nesta fase.");

        assertEquals(HttpStatus.OK, get("/api/cardapio/itens").getStatusCode());
        assertEquals(HttpStatus.OK, get("/api/rentabilidade/ranking").getStatusCode());
    }

    private Map<String, Long> unidadesPorCodigo() throws Exception {
        JsonNode unidades = objectMapper.readTree(get("/api/unidades").getBody());
        Map<String, Long> ids = new LinkedHashMap<>();
        for (JsonNode unidade : unidades) {
            ids.put(unidade.path("codigo").asText(), unidade.path("id").asLong());
        }
        return ids;
    }

    private int criarProduto(String nome, String unidadeBaseCodigo, String custoMedio) throws Exception {
        int produtoId = objectMapper.readTree(post("/api/produtos", produtoPayload(nome, unidadeBaseCodigo)).getBody())
                .path("id")
                .asInt();
        Produto produto = produtoRepository.buscarPorId(produtoId).orElseThrow();
        estoqueProdutoRepository.salvar(new EstoqueProduto(
                0,
                1L,
                produto,
                new BigDecimal("10000"),
                BigDecimal.ZERO,
                new BigDecimal(custoMedio)
        ));
        return produtoId;
    }

    private void alterarCustoMedio(int produtoId, String novoCusto) {
        EstoqueProduto estoque = estoqueProdutoRepository.buscarPorFilialEProduto(1L, produtoId).orElseThrow();
        estoque.redefinirQuantidadeECusto(estoque.getQuantidadeAtual(), new BigDecimal(novoCusto));
        estoqueProdutoRepository.salvar(estoque);
    }

    private long movimentosDosProdutos(Set<Integer> produtoIds) {
        return movimentoEstoqueRepository.listarTodos().stream()
                .map(MovimentoEstoque::getProduto)
                .filter(produto -> produtoIds.contains(produto.getId()))
                .count();
    }

    private void adicionarIngrediente(long fichaId,
                                      long versaoId,
                                      int produtoId,
                                      long unidadeId,
                                      String quantidade) {
        ResponseEntity<String> resposta = post(
                "/api/fichas-tecnicas/" + fichaId + "/versoes/" + versaoId + "/ingredientes",
                ingredientePayload(produtoId, unidadeId, quantidade)
        );
        assertEquals(HttpStatus.CREATED, resposta.getStatusCode(), resposta.getBody());
    }

    private long encontrarIngrediente(JsonNode versao, int produtoId) {
        for (JsonNode ingrediente : versao.path("ingredientes")) {
            if (ingrediente.path("produtoId").asInt() == produtoId) {
                return ingrediente.path("id").asLong();
            }
        }
        throw new AssertionError("Ingrediente nao encontrado: " + produtoId);
    }

    private ResponseEntity<String> get(String path) {
        return restTemplate.exchange(url(path), HttpMethod.GET, new HttpEntity<>(headers()), String.class);
    }

    private ResponseEntity<String> post(String path, Object body) {
        return restTemplate.exchange(url(path), HttpMethod.POST, new HttpEntity<>(body, headers()), String.class);
    }

    private ResponseEntity<String> put(String path, Object body) {
        return restTemplate.exchange(url(path), HttpMethod.PUT, new HttpEntity<>(body, headers()), String.class);
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-User-RU", String.valueOf(gerenteEstoque.getRu()));
        return headers;
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
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

    private static Map<String, Object> itemPayload(String codigo, String nome, int categoriaId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("codigo", codigo);
        body.put("nome", nome);
        body.put("descricaoCurta", "Pizza preparada sob demanda");
        body.put("precoVenda", "55.00");
        body.put("categoriaId", categoriaId);
        body.put("tipoItem", "PREPARADO_SOB_DEMANDA");
        body.put("ordemExibicao", 1);
        body.put("produtoVinculadoId", null);
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

    private static Map<String, Object> fichaPayload(String nome, long rendimentoUnidadeId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("nome", nome);
        body.put("rendimentoQuantidade", "1");
        body.put("rendimentoUnidadeId", rendimentoUnidadeId);
        body.put("tempoPreparoMinutos", 20);
        body.put("observacaoInterna", "Teste Fase 1");
        return body;
    }

    private static Map<String, Object> ingredientePayload(int produtoId, long unidadeId, String quantidade) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("produtoId", produtoId);
        body.put("unidadeInformadaId", unidadeId);
        body.put("quantidadeInformada", quantidade);
        body.put("aplicarPerda", false);
        body.put("ingredienteCritico", true);
        body.put("politicaFalta", "HERDAR_PRODUTO");
        body.put("ordem", 1);
        return body;
    }

    private static void assertBigDecimalEquals(String esperado, BigDecimal atual) {
        assertEquals(0, new BigDecimal(esperado).compareTo(atual), "Esperado " + esperado + ", atual " + atual);
    }
}
