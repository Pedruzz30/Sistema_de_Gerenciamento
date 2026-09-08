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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        classes = Main.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.port=0"
)
class Fase2VendaApiIntegrationTest {

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
    private Usuario caixa;

    @BeforeEach
    void setUp() {
        gerente = usuarioRepository.listarTodos().stream()
                .filter(usuario -> "52998224725".equals(usuario.getCpfBruto()))
                .findFirst()
                .orElseThrow();
        caixa = usuarioRepository.listarTodos().stream()
                .filter(usuario -> "93541134780".equals(usuario.getCpfBruto()))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void vendaPreparadaBaixaIngredientesPreservaSnapshotEEstorna() throws Exception {
        PizzaFixture pizza = prepararPizzaCalabresa("f2_pizza_" + System.nanoTime());
        long movimentosAntes = movimentosVendaDaVenda(0).size();

        JsonNode venda = objectMapper.readTree(post(
                "/api/vendas",
                Map.of("filialId", 1, "canal", "BALCAO", "itens", List.of(
                        Map.of("variacaoId", pizza.variacaoId(), "quantidade", "2", "observacao", "Sem cortar")
                )),
                gerente
        ).getBody());
        long vendaId = venda.path("id").asLong();

        ResponseEntity<String> respostaFinalizacao = postComIdempotencia(
                "/api/vendas/" + vendaId + "/finalizar",
                Map.of(),
                gerente,
                "venda-pizza-" + vendaId
        );
        assertEquals(HttpStatus.OK, respostaFinalizacao.getStatusCode(), respostaFinalizacao.getBody());
        JsonNode finalizada = objectMapper.readTree(respostaFinalizacao.getBody());

        assertEquals("FINALIZADA", finalizada.path("status").asText());
        assertBigDecimalEquals("110.0000", finalizada.path("valorTotal").decimalValue());
        assertBigDecimalEquals("31.4000", finalizada.path("custoIngredientesTotal").decimalValue());
        assertBigDecimalEquals("78.6000", finalizada.path("margemBrutaEstimadaTotal").decimalValue());
        JsonNode item = finalizada.path("itens").get(0);
        assertEquals(pizza.versao1Id(), item.path("fichaTecnicaVersaoId").asLong());
        assertEquals(1, item.path("numeroVersaoFicha").asInt());
        assertBigDecimalEquals("31.4000", item.path("custoTotalEstimado").decimalValue());
        assertEquals(5, item.path("consumos").size());
        assertConsumo(item.path("consumos"), "Mussarela", "380.000000");
        assertConsumo(item.path("consumos"), "Molho", "240.000000");

        assertEstoque(pizza.massaId(), "9300.000000");
        assertEstoque(pizza.molhoId(), "9760.000000");
        assertEstoque(pizza.mussarelaId(), "9620.000000");
        assertEstoque(pizza.calabresaId(), "9700.000000");
        assertEstoque(pizza.cebolaId(), "9940.000000");

        List<MovimentoEstoque> movimentosVenda = movimentosVendaDaVenda(vendaId);
        assertEquals(5, movimentosVenda.size());
        assertTrue(movimentosVenda.stream().allMatch(m -> m.getTipo() == TipoMovimentoEstoque.SAIDA_VENDA));
        assertNotEquals(movimentosAntes, movimentoRepository.listarTodos().size());

        JsonNode versao2 = objectMapper.readTree(post(
                "/api/fichas-tecnicas/" + pizza.fichaId() + "/nova-versao",
                Map.of(),
                gerente
        ).getBody());
        long ingredienteMussarelaV2 = encontrarIngrediente(versao2, pizza.mussarelaId());
        put(
                "/api/fichas-tecnicas/" + pizza.fichaId() + "/versoes/" + versao2.path("id").asLong()
                        + "/ingredientes/" + ingredienteMussarelaV2,
                ingredientePayload(pizza.mussarelaId(), pizza.unidades().get("GRAMA"), "180"),
                gerente
        );
        post(
                "/api/fichas-tecnicas/" + pizza.fichaId() + "/versoes/" + versao2.path("id").asLong() + "/publicar",
                Map.of("filialReferenciaId", 1, "motivo", "Fase 2 publica v2"),
                gerente
        );

        alterarCustoMedio(pizza.mussarelaId(), "0.05000000");
        JsonNode historico = objectMapper.readTree(get("/api/vendas/" + vendaId, gerente).getBody());
        assertEquals(pizza.versao1Id(), historico.path("itens").get(0).path("fichaTecnicaVersaoId").asLong());
        assertBigDecimalEquals("31.4000", historico.path("custoIngredientesTotal").decimalValue());

        JsonNode cancelada = objectMapper.readTree(postComIdempotencia(
                "/api/vendas/" + vendaId + "/cancelar",
                Map.of("motivo", "Cancelamento de teste"),
                gerente,
                "cancelar-pizza-" + vendaId
        ).getBody());
        assertEquals("CANCELADA", cancelada.path("status").asText());
        assertEquals(5, movimentosEstornoVendaDaVenda(vendaId).size());
    }

    @Test
    void produtoDiretoIdempotenciaConflitoECustoOcultoParaCaixa() throws Exception {
        long unidadeId = unidadesPorCodigo().get("UNIDADE");
        int produtoId = criarProduto("Refrigerante F2 " + System.nanoTime(), "UNIDADE", "4.00000000", "10");
        long variacaoId = criarItemDireto("refri_f2_" + System.nanoTime(), produtoId, unidadeId);

        long vendaId = objectMapper.readTree(post(
                "/api/vendas",
                Map.of("filialId", 1, "canal", "BALCAO", "itens", List.of(
                        Map.of("variacaoId", variacaoId, "quantidade", "1")
                )),
                gerente
        ).getBody()).path("id").asLong();

        ResponseEntity<String> primeiraResposta = postComIdempotencia(
                "/api/vendas/" + vendaId + "/finalizar",
                Map.of(),
                gerente,
                "refri-finaliza-" + vendaId
        );
        assertEquals(HttpStatus.OK, primeiraResposta.getStatusCode(), primeiraResposta.getBody());
        JsonNode primeira = objectMapper.readTree(primeiraResposta.getBody());
        ResponseEntity<String> repetidaResposta = postComIdempotencia(
                "/api/vendas/" + vendaId + "/finalizar",
                Map.of(),
                gerente,
                "refri-finaliza-" + vendaId
        );
        assertEquals(HttpStatus.OK, repetidaResposta.getStatusCode(), repetidaResposta.getBody());
        JsonNode repetida = objectMapper.readTree(repetidaResposta.getBody());
        assertEquals(primeira.path("id").asLong(), repetida.path("id").asLong());
        assertEstoque(produtoId, "9.000000");
        assertEquals(1, movimentosVendaDaVenda(vendaId).size());

        long outraVendaId = objectMapper.readTree(post(
                "/api/vendas",
                Map.of("filialId", 1, "canal", "BALCAO", "itens", List.of(
                        Map.of("variacaoId", variacaoId, "quantidade", "1")
                )),
                gerente
        ).getBody()).path("id").asLong();
        ResponseEntity<String> conflito = postComIdempotencia(
                "/api/vendas/" + outraVendaId + "/finalizar",
                Map.of(),
                gerente,
                "refri-finaliza-" + vendaId
        );
        assertEquals(HttpStatus.CONFLICT, conflito.getStatusCode());

        JsonNode visaoCaixa = objectMapper.readTree(get("/api/vendas/" + vendaId, caixa).getBody());
        assertTrue(visaoCaixa.path("custoIngredientesTotal").isNull());
        assertNull(visaoCaixa.path("itens").get(0).path("custoTotalEstimado").numberValue());

        JsonNode cancelada = objectMapper.readTree(postComIdempotencia(
                "/api/vendas/" + vendaId + "/cancelar",
                Map.of("motivo", "Estorno do refrigerante"),
                gerente,
                "cancelar-refri-" + vendaId
        ).getBody());
        assertEquals("CANCELADA", cancelada.path("status").asText());
        assertEstoque(produtoId, "10.000000");
        assertEquals(1, movimentosEstornoVendaDaVenda(vendaId).size());
    }

    @Test
    void rollbackNaoBaixaPizzaQuandoProdutoDiretoFalha() throws Exception {
        PizzaFixture pizza = prepararPizzaCalabresa("f2_rollback_" + System.nanoTime());
        int refrigeranteSemEstoque = criarProduto("Refri sem estoque F2 " + System.nanoTime(), "UNIDADE", "4.00000000", "0");
        long variacaoDireta = criarItemDireto("refri_sem_estoque_f2_" + System.nanoTime(), refrigeranteSemEstoque,
                unidadesPorCodigo().get("UNIDADE"));
        long movimentosAntes = movimentoRepository.listarTodos().size();

        long vendaId = objectMapper.readTree(post(
                "/api/vendas",
                Map.of("filialId", 1, "canal", "BALCAO", "itens", List.of(
                        Map.of("variacaoId", pizza.variacaoId(), "quantidade", "1"),
                        Map.of("variacaoId", variacaoDireta, "quantidade", "1")
                )),
                gerente
        ).getBody()).path("id").asLong();

        ResponseEntity<String> resposta = postComIdempotencia(
                "/api/vendas/" + vendaId + "/finalizar",
                Map.of(),
                gerente,
                "rollback-" + vendaId
        );

        assertEquals(HttpStatus.BAD_REQUEST, resposta.getStatusCode());
        JsonNode erro = objectMapper.readTree(resposta.getBody());
        assertEquals("ESTOQUE_INSUFICIENTE", erro.path("codigo").asText());
        assertEquals(movimentosAntes, movimentoRepository.listarTodos().size());
        assertEstoque(pizza.massaId(), "10000.000000");
        JsonNode venda = objectMapper.readTree(get("/api/vendas/" + vendaId, gerente).getBody());
        assertEquals("RASCUNHO", venda.path("status").asText());
    }

    @Test
    void duasVendasDisputandoUltimoEstoqueApenasUmaFinaliza() throws Exception {
        PizzaFixture pizza = prepararPizzaCalabresa("f2_concorrencia_" + System.nanoTime());
        alterarQuantidade(pizza.massaId(), "350");
        alterarQuantidade(pizza.molhoId(), "120");
        alterarQuantidade(pizza.mussarelaId(), "190");
        alterarQuantidade(pizza.calabresaId(), "150");
        alterarQuantidade(pizza.cebolaId(), "30");

        long venda1 = criarVendaComItem(pizza.variacaoId(), "1");
        long venda2 = criarVendaComItem(pizza.variacaoId(), "1");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ResponseEntity<String>> primeira = executor.submit(() -> postComIdempotencia(
                    "/api/vendas/" + venda1 + "/finalizar",
                    Map.of(),
                    gerente,
                    "concorrencia-" + venda1
            ));
            Future<ResponseEntity<String>> segunda = executor.submit(() -> postComIdempotencia(
                    "/api/vendas/" + venda2 + "/finalizar",
                    Map.of(),
                    gerente,
                    "concorrencia-" + venda2
            ));

            ResponseEntity<String> resposta1 = primeira.get(20, TimeUnit.SECONDS);
            ResponseEntity<String> resposta2 = segunda.get(20, TimeUnit.SECONDS);
            long finalizada = resposta1.getStatusCode() == HttpStatus.OK ? venda1 : venda2;
            long falha = finalizada == venda1 ? venda2 : venda1;
            int sucessos = (resposta1.getStatusCode() == HttpStatus.OK ? 1 : 0)
                    + (resposta2.getStatusCode() == HttpStatus.OK ? 1 : 0);
            int bloqueios = (resposta1.getStatusCode() == HttpStatus.BAD_REQUEST ? 1 : 0)
                    + (resposta2.getStatusCode() == HttpStatus.BAD_REQUEST ? 1 : 0);

            assertEquals(1, sucessos, resposta1.getBody() + " | " + resposta2.getBody());
            assertEquals(1, bloqueios, resposta1.getBody() + " | " + resposta2.getBody());
            assertEquals(5, movimentosVendaDaVenda(finalizada).size());
            assertEquals(0, movimentosVendaDaVenda(falha).size());
            assertEstoque(pizza.massaId(), "0.000000");
            assertEstoque(pizza.molhoId(), "0.000000");
            assertEstoque(pizza.mussarelaId(), "0.000000");
            assertEstoque(pizza.calabresaId(), "0.000000");
            assertEstoque(pizza.cebolaId(), "0.000000");
            JsonNode vendaFalha = objectMapper.readTree(get("/api/vendas/" + falha, gerente).getBody());
            assertEquals("RASCUNHO", vendaFalha.path("status").asText());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void duasFinalizacoesSimultaneasDaMesmaVendaNaoDuplicamMovimentos() throws Exception {
        PizzaFixture pizza = prepararPizzaCalabresa("f46_dupla_finalizacao_" + System.nanoTime());
        long vendaId = criarVendaComItem(pizza.variacaoId(), "1");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ResponseEntity<String>> primeira = executor.submit(() -> postComIdempotencia(
                    "/api/vendas/" + vendaId + "/finalizar",
                    Map.of(),
                    gerente,
                    "dupla-finalizacao-a-" + vendaId
            ));
            Future<ResponseEntity<String>> segunda = executor.submit(() -> postComIdempotencia(
                    "/api/vendas/" + vendaId + "/finalizar",
                    Map.of(),
                    gerente,
                    "dupla-finalizacao-b-" + vendaId
            ));

            ResponseEntity<String> resposta1 = primeira.get(20, TimeUnit.SECONDS);
            ResponseEntity<String> resposta2 = segunda.get(20, TimeUnit.SECONDS);
            int sucessos = (resposta1.getStatusCode() == HttpStatus.OK ? 1 : 0)
                    + (resposta2.getStatusCode() == HttpStatus.OK ? 1 : 0);
            int conflitos = (resposta1.getStatusCode() == HttpStatus.CONFLICT ? 1 : 0)
                    + (resposta2.getStatusCode() == HttpStatus.CONFLICT ? 1 : 0);
            ResponseEntity<String> conflito = resposta1.getStatusCode() == HttpStatus.CONFLICT ? resposta1 : resposta2;

            assertEquals(1, sucessos, resposta1.getBody() + " | " + resposta2.getBody());
            assertEquals(1, conflitos, resposta1.getBody() + " | " + resposta2.getBody());
            assertEquals("VENDA_JA_FINALIZADA", objectMapper.readTree(conflito.getBody()).path("codigo").asText());
            assertEquals(5, movimentosVendaDaVenda(vendaId).size());
            assertEquals(0, movimentosEstornoVendaDaVenda(vendaId).size());

            JsonNode venda = objectMapper.readTree(get("/api/vendas/" + vendaId, gerente).getBody());
            assertEquals("FINALIZADA", venda.path("status").asText());
            assertEquals(1, venda.path("itens").size());
            assertEquals(5, venda.path("itens").get(0).path("consumos").size());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void finalizacaoNaoConsomeEstoqueDecimalDeOutraFilial() throws Exception {
        int produtoId = criarProduto("Produto filial isolada F46 " + System.nanoTime(), "UNIDADE", "4.00000000", "5");
        long variacaoId = criarItemDireto(
                "filial_isolada_f46_" + System.nanoTime(),
                produtoId,
                unidadesPorCodigo().get("UNIDADE")
        );
        long vendaId = criarVendaComItemNaFilial(2L, variacaoId, "1");

        ResponseEntity<String> resposta = postComIdempotencia(
                "/api/vendas/" + vendaId + "/finalizar",
                Map.of(),
                gerente,
                "filial-isolada-" + vendaId
        );

        assertEquals(HttpStatus.BAD_REQUEST, resposta.getStatusCode(), resposta.getBody());
        assertEquals("ESTOQUE_PRODUTO_NAO_CONFIGURADO", objectMapper.readTree(resposta.getBody()).path("codigo").asText());
        assertEstoque(produtoId, "5.000000");
        assertEquals(0, movimentosVendaDaVenda(vendaId).size());
        JsonNode venda = objectMapper.readTree(get("/api/vendas/" + vendaId, gerente).getBody());
        assertEquals("RASCUNHO", venda.path("status").asText());
    }

    @Test
    void cancelamentoParcialPorItemInteiroDevolveApenasOItemCancelado() throws Exception {
        long unidadeId = unidadesPorCodigo().get("UNIDADE");
        int produtoA = criarProduto("Refri parcial A F2 " + System.nanoTime(), "UNIDADE", "4.00000000", "10");
        int produtoB = criarProduto("Refri parcial B F2 " + System.nanoTime(), "UNIDADE", "5.00000000", "10");
        long variacaoA = criarItemDireto("refri_parcial_a_f2_" + System.nanoTime(), produtoA, unidadeId);
        long variacaoB = criarItemDireto("refri_parcial_b_f2_" + System.nanoTime(), produtoB, unidadeId);

        JsonNode venda = objectMapper.readTree(post(
                "/api/vendas",
                Map.of("filialId", 1, "canal", "BALCAO", "itens", List.of(
                        Map.of("variacaoId", variacaoA, "quantidade", "1"),
                        Map.of("variacaoId", variacaoB, "quantidade", "1")
                )),
                gerente
        ).getBody());
        long vendaId = venda.path("id").asLong();
        JsonNode finalizada = objectMapper.readTree(postComIdempotencia(
                "/api/vendas/" + vendaId + "/finalizar",
                Map.of(),
                gerente,
                "parcial-finaliza-" + vendaId
        ).getBody());
        long itemCanceladoId = finalizada.path("itens").get(0).path("id").asLong();
        assertEstoque(produtoA, "9.000000");
        assertEstoque(produtoB, "9.000000");

        ResponseEntity<String> cancelamento = postComIdempotencia(
                "/api/vendas/" + vendaId + "/itens/" + itemCanceladoId + "/cancelar",
                Map.of("motivo", "Cancelamento parcial de teste"),
                gerente,
                "parcial-cancela-" + vendaId
        );

        assertEquals(HttpStatus.OK, cancelamento.getStatusCode(), cancelamento.getBody());
        JsonNode parcial = objectMapper.readTree(cancelamento.getBody());
        assertEquals("PARCIALMENTE_CANCELADA", parcial.path("status").asText());
        assertEquals("CANCELADO", parcial.path("itens").get(0).path("status").asText());
        assertEquals("ATIVO", parcial.path("itens").get(1).path("status").asText());
        assertEstoque(produtoA, "10.000000");
        assertEstoque(produtoB, "9.000000");
        assertEquals(1, movimentosEstornoVendaDaVenda(vendaId).size());
    }

    @Test
    void cancelamentoParcialDeItensCompartilhandoProdutoPermiteEstornosMultiplos() throws Exception {
        Map<String, Long> unidades = unidadesPorCodigo();
        int mussarelaId = criarProduto("Mussarela compartilhada F25 " + System.nanoTime(), "GRAMA", "0.04000000", "1000");
        long variacaoA = criarItemPreparadoComIngrediente(
                "compart_a_" + System.nanoTime(),
                "Pizza A compartilhada",
                mussarelaId,
                unidades.get("GRAMA"),
                "100",
                "30.00"
        );
        long variacaoB = criarItemPreparadoComIngrediente(
                "compart_b_" + System.nanoTime(),
                "Pizza B compartilhada",
                mussarelaId,
                unidades.get("GRAMA"),
                "150",
                "40.00"
        );

        JsonNode venda = objectMapper.readTree(post(
                "/api/vendas",
                Map.of("filialId", 1, "canal", "BALCAO", "itens", List.of(
                        Map.of("variacaoId", variacaoA, "quantidade", "1"),
                        Map.of("variacaoId", variacaoB, "quantidade", "1")
                )),
                gerente
        ).getBody());
        long vendaId = venda.path("id").asLong();
        JsonNode finalizada = objectMapper.readTree(postComIdempotencia(
                "/api/vendas/" + vendaId + "/finalizar",
                Map.of(),
                gerente,
                "compart-finaliza-" + vendaId
        ).getBody());

        long itemA = finalizada.path("itens").get(0).path("id").asLong();
        long itemB = finalizada.path("itens").get(1).path("id").asLong();
        List<MovimentoEstoque> saidas = movimentosVendaDaVenda(vendaId);
        assertEquals(1, saidas.size());
        assertBigDecimalEquals("250.000000", saidas.get(0).getQuantidadeBase());
        assertEstoque(mussarelaId, "750.000000");

        JsonNode parcial = objectMapper.readTree(postComIdempotencia(
                "/api/vendas/" + vendaId + "/itens/" + itemA + "/cancelar",
                Map.of("motivo", "Cancelamento item A"),
                gerente,
                "compart-cancela-a-" + vendaId
        ).getBody());
        assertEquals("PARCIALMENTE_CANCELADA", parcial.path("status").asText());
        assertEstoque(mussarelaId, "850.000000");
        assertEquals(1, movimentosEstornoVendaDaVenda(vendaId).size());
        assertBigDecimalEquals("100.000000", movimentosEstornoVendaDaVenda(vendaId).get(0).getQuantidadeBase());

        JsonNode cancelada = objectMapper.readTree(postComIdempotencia(
                "/api/vendas/" + vendaId + "/itens/" + itemB + "/cancelar",
                Map.of("motivo", "Cancelamento item B"),
                gerente,
                "compart-cancela-b-" + vendaId
        ).getBody());
        assertEquals("CANCELADA", cancelada.path("status").asText());
        assertEstoque(mussarelaId, "1000.000000");
        List<MovimentoEstoque> estornos = movimentosEstornoVendaDaVenda(vendaId);
        assertEquals(2, estornos.size());
        BigDecimal totalEstornado = estornos.stream()
                .map(MovimentoEstoque::getQuantidadeBase)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertBigDecimalEquals("250.000000", totalEstornado);
        assertTrue(estornos.stream().allMatch(m -> m.getMovimentoEstornado().getId() == saidas.get(0).getId()));
    }

    @Test
    void cancelamentoTardioAposNovaCompraRecompoeCustoMedioComCustoHistoricoDaVenda() throws Exception {
        int produtoId = criarProduto("Ingrediente tardio F25 " + System.nanoTime(), "GRAMA", "0.04000000", "1000");
        long variacaoId = criarItemDiretoComQuantidade(
                "direto_tardio_" + System.nanoTime(),
                produtoId,
                "10.00",
                "100"
        );
        long vendaId = criarVendaComItem(variacaoId, "1");
        assertEquals(HttpStatus.OK, postComIdempotencia(
                "/api/vendas/" + vendaId + "/finalizar",
                Map.of(),
                gerente,
                "tardio-finaliza-" + vendaId
        ).getStatusCode());
        assertEstoque(produtoId, "900.000000");
        assertBigDecimalEquals("0.04000000", estoqueRepository.buscarPorFilialEProduto(1L, produtoId).orElseThrow().getCustoMedio());

        long compraId = criarCompraEstoque();
        post(
                "/api/compras/" + compraId + "/itens",
                itemCompraUnidadePayload(produtoId, "GRAMA", "1000", "60.00"),
                gerente
        );
        assertEquals(HttpStatus.OK, confirmarCompraEstoque(compraId, "tardio-compra-" + vendaId, "Compra apos venda").getStatusCode());
        EstoqueProduto estoqueDepoisCompra = estoqueRepository.buscarPorFilialEProduto(1L, produtoId).orElseThrow();
        assertBigDecimalEquals("1900.000000", estoqueDepoisCompra.getQuantidadeAtual());
        assertBigDecimalEquals("0.05052632", estoqueDepoisCompra.getCustoMedio());
        assertBigDecimalEquals("96.0000", estoqueDepoisCompra.valorEstoque());

        assertEquals(HttpStatus.OK, postComIdempotencia(
                "/api/vendas/" + vendaId + "/cancelar",
                Map.of("motivo", "Cancelamento tardio"),
                gerente,
                "tardio-cancela-" + vendaId
        ).getStatusCode());
        EstoqueProduto estoqueFinal = estoqueRepository.buscarPorFilialEProduto(1L, produtoId).orElseThrow();
        assertBigDecimalEquals("2000.000000", estoqueFinal.getQuantidadeAtual());
        assertBigDecimalEquals("0.05000000", estoqueFinal.getCustoMedio());
        assertBigDecimalEquals("100.0000", estoqueFinal.valorEstoque());
        JsonNode historico = objectMapper.readTree(get("/api/vendas/" + vendaId, gerente).getBody());
        assertBigDecimalEquals("4.0000", historico.path("custoIngredientesTotal").decimalValue());
    }

    @Test
    void alteracoesDuranteRascunhoGeramConflitosEstruturados() throws Exception {
        int produtoPreco = criarProduto("Produto preco alterado F25 " + System.nanoTime(), "UNIDADE", "4.00000000", "5");
        long variacaoPreco = criarItemDireto("preco_alt_f25_" + System.nanoTime(), produtoPreco, unidadesPorCodigo().get("UNIDADE"));
        long vendaPreco = criarVendaComItem(variacaoPreco, "1");
        atualizarPrecoVariacao(variacaoPreco, "9.00");

        ResponseEntity<String> respostaPreco = postComIdempotencia(
                "/api/vendas/" + vendaPreco + "/finalizar",
                Map.of(),
                gerente,
                "preco-alt-" + vendaPreco
        );
        assertEquals(HttpStatus.CONFLICT, respostaPreco.getStatusCode(), respostaPreco.getBody());
        assertEquals("PRECO_VARIACAO_ALTERADO", objectMapper.readTree(respostaPreco.getBody()).path("codigo").asText());
        assertEstoque(produtoPreco, "5.000000");

        PizzaFixture pizza = prepararPizzaCalabresa("f25_ficha_alt_" + System.nanoTime());
        long vendaFicha = criarVendaComItem(pizza.variacaoId(), "1");
        JsonNode versao2 = objectMapper.readTree(post(
                "/api/fichas-tecnicas/" + pizza.fichaId() + "/nova-versao",
                Map.of(),
                gerente
        ).getBody());
        long ingredienteMussarelaV2 = encontrarIngrediente(versao2, pizza.mussarelaId());
        put(
                "/api/fichas-tecnicas/" + pizza.fichaId() + "/versoes/" + versao2.path("id").asLong()
                        + "/ingredientes/" + ingredienteMussarelaV2,
                ingredientePayload(pizza.mussarelaId(), pizza.unidades().get("GRAMA"), "180"),
                gerente
        );
        post(
                "/api/fichas-tecnicas/" + pizza.fichaId() + "/versoes/" + versao2.path("id").asLong() + "/publicar",
                Map.of("filialReferenciaId", 1, "motivo", "Ficha mudou durante rascunho"),
                gerente
        );

        ResponseEntity<String> respostaFicha = postComIdempotencia(
                "/api/vendas/" + vendaFicha + "/finalizar",
                Map.of(),
                gerente,
                "ficha-alt-" + vendaFicha
        );
        assertEquals(HttpStatus.CONFLICT, respostaFicha.getStatusCode(), respostaFicha.getBody());
        assertEquals("FICHA_PUBLICADA_ALTERADA", objectMapper.readTree(respostaFicha.getBody()).path("codigo").asText());
        assertEstoque(pizza.mussarelaId(), "10000.000000");

        int produtoInativo = criarProduto("Produto variacao inativa F25 " + System.nanoTime(), "UNIDADE", "4.00000000", "5");
        long variacaoInativa = criarItemDireto("var_inativa_f25_" + System.nanoTime(), produtoInativo, unidadesPorCodigo().get("UNIDADE"));
        long vendaInativa = criarVendaComItem(variacaoInativa, "1");
        atualizarStatusVariacao(variacaoInativa, false);
        ResponseEntity<String> respostaInativa = postComIdempotencia(
                "/api/vendas/" + vendaInativa + "/finalizar",
                Map.of(),
                gerente,
                "var-inativa-" + vendaInativa
        );
        assertEquals(HttpStatus.BAD_REQUEST, respostaInativa.getStatusCode(), respostaInativa.getBody());
        assertEquals("VARIACAO_INATIVA", objectMapper.readTree(respostaInativa.getBody()).path("codigo").asText());
    }

    @Test
    void falhaNaIntegracaoComCaixaReverteVendaEstoqueMovimentosEIdempotencia() throws Exception {
        int produtoId = criarProduto("Produto caixa falha F25 " + System.nanoTime(), "UNIDADE", "4.00000000", "3");
        long variacaoId = criarItemDireto("caixa_falha_f25_" + System.nanoTime(), produtoId, unidadesPorCodigo().get("UNIDADE"));
        long vendaId = criarVendaComItem(variacaoId, "1");
        long movimentosAntes = movimentoRepository.listarTodos().size();

        ResponseEntity<String> resposta = postComIdempotencia(
                "/api/vendas/" + vendaId + "/finalizar",
                Map.of("numeroCaixaLegado", 999999),
                gerente,
                "caixa-falha-" + vendaId
        );
        assertEquals(HttpStatus.BAD_REQUEST, resposta.getStatusCode(), resposta.getBody());
        assertEstoque(produtoId, "3.000000");
        assertEquals(movimentosAntes, movimentoRepository.listarTodos().size());
        JsonNode rascunho = objectMapper.readTree(get("/api/vendas/" + vendaId, gerente).getBody());
        assertEquals("RASCUNHO", rascunho.path("status").asText());

        ResponseEntity<String> retry = postComIdempotencia(
                "/api/vendas/" + vendaId + "/finalizar",
                Map.of(),
                gerente,
                "caixa-falha-" + vendaId
        );
        assertEquals(HttpStatus.OK, retry.getStatusCode(), retry.getBody());
        assertEstoque(produtoId, "2.000000");
    }

    @Test
    void cancelamentoDeItemEhIdempotenteENaoDuplicaEstorno() throws Exception {
        int produtoId = criarProduto("Produto cancela idem F25 " + System.nanoTime(), "UNIDADE", "4.00000000", "3");
        long variacaoId = criarItemDireto("cancela_idem_f25_" + System.nanoTime(), produtoId, unidadesPorCodigo().get("UNIDADE"));
        long vendaId = criarVendaComItem(variacaoId, "1");
        JsonNode finalizada = objectMapper.readTree(postComIdempotencia(
                "/api/vendas/" + vendaId + "/finalizar",
                Map.of(),
                gerente,
                "cancela-idem-finaliza-" + vendaId
        ).getBody());
        long itemId = finalizada.path("itens").get(0).path("id").asLong();

        ResponseEntity<String> primeira = postComIdempotencia(
                "/api/vendas/" + vendaId + "/itens/" + itemId + "/cancelar",
                Map.of("motivo", "Cancelamento idem"),
                gerente,
                "cancela-idem-" + vendaId
        );
        ResponseEntity<String> repetida = postComIdempotencia(
                "/api/vendas/" + vendaId + "/itens/" + itemId + "/cancelar",
                Map.of("motivo", "Cancelamento idem"),
                gerente,
                "cancela-idem-" + vendaId
        );

        assertEquals(HttpStatus.OK, primeira.getStatusCode(), primeira.getBody());
        assertEquals(HttpStatus.OK, repetida.getStatusCode(), repetida.getBody());
        assertEquals(1, movimentosEstornoVendaDaVenda(vendaId).size());
        assertEstoque(produtoId, "3.000000");
    }

    private PizzaFixture prepararPizzaCalabresa(String codigoBase) throws Exception {
        Map<String, Long> unidades = unidadesPorCodigo();
        int massaId = criarProduto("Massa pronta " + codigoBase, "GRAMA", "0.00628571", "10000");
        int molhoId = criarProduto("Molho " + codigoBase, "MILILITRO", "0.00916667", "10000");
        int mussarelaId = criarProduto("Mussarela " + codigoBase, "GRAMA", "0.04000000", "10000");
        int calabresaId = criarProduto("Calabresa " + codigoBase, "GRAMA", "0.03000000", "10000");
        int cebolaId = criarProduto("Cebola " + codigoBase, "GRAMA", "0.01000000", "10000");

        int categoriaId = objectMapper.readTree(post(
                "/api/cardapio/admin/categorias",
                Map.of("codigo", codigoBase + "_cat", "nomeExibicao", "Pizzas " + codigoBase, "ordemExibicao", 1),
                gerente
        ).getBody()).path("id").asInt();

        int itemId = objectMapper.readTree(post(
                "/api/cardapio/admin/itens",
                itemPreparadoPayload(codigoBase + "_pizza", "Pizza Calabresa " + codigoBase, categoriaId),
                gerente
        ).getBody()).path("id").asInt();

        long variacaoId = objectMapper.readTree(post(
                "/api/cardapio/itens/" + itemId + "/variacoes",
                variacaoPreparadaPayload("Grande", "GRANDE", "55.00"),
                gerente
        ).getBody()).path("id").asLong();

        JsonNode ficha = objectMapper.readTree(post(
                "/api/cardapio/variacoes/" + variacaoId + "/ficha-tecnica",
                fichaPayload("Pizza Calabresa Grande " + codigoBase, unidades.get("UNIDADE")),
                gerente
        ).getBody());
        long fichaId = ficha.path("fichaTecnicaId").asLong();
        long versaoId = ficha.path("id").asLong();

        adicionarIngrediente(fichaId, versaoId, massaId, unidades.get("GRAMA"), "350");
        adicionarIngrediente(fichaId, versaoId, molhoId, unidades.get("MILILITRO"), "120");
        adicionarIngrediente(fichaId, versaoId, mussarelaId, unidades.get("GRAMA"), "190");
        adicionarIngrediente(fichaId, versaoId, calabresaId, unidades.get("GRAMA"), "150");
        adicionarIngrediente(fichaId, versaoId, cebolaId, unidades.get("GRAMA"), "30");

        JsonNode publicada = objectMapper.readTree(post(
                "/api/fichas-tecnicas/" + fichaId + "/versoes/" + versaoId + "/publicar",
                Map.of("filialReferenciaId", 1, "motivo", "Publicacao Fase 2"),
                gerente
        ).getBody());
        assertEquals("PUBLICADA", publicada.path("status").asText());
        return new PizzaFixture(
                variacaoId,
                fichaId,
                versaoId,
                massaId,
                molhoId,
                mussarelaId,
                calabresaId,
                cebolaId,
                unidades
        );
    }

    private Map<String, Long> unidadesPorCodigo() throws Exception {
        JsonNode unidades = objectMapper.readTree(get("/api/unidades", gerente).getBody());
        Map<String, Long> ids = new LinkedHashMap<>();
        for (JsonNode unidade : unidades) {
            ids.put(unidade.path("codigo").asText(), unidade.path("id").asLong());
        }
        return ids;
    }

    private int criarProduto(String nome, String unidadeBaseCodigo, String custoMedio, String quantidade) throws Exception {
        int produtoId = objectMapper.readTree(post("/api/produtos", produtoPayload(nome, unidadeBaseCodigo), gerente).getBody())
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

    private long criarItemDireto(String codigo, int produtoId, long unidadeId) throws Exception {
        return criarItemDiretoComQuantidade(codigo, produtoId, "8.00", "1", unidadeId);
    }

    private long criarItemDiretoComQuantidade(String codigo, int produtoId, String preco, String quantidadeBase) throws Exception {
        return criarItemDiretoComQuantidade(codigo, produtoId, preco, quantidadeBase, unidadesPorCodigo().get("UNIDADE"));
    }

    private long criarItemDiretoComQuantidade(String codigo,
                                              int produtoId,
                                              String preco,
                                              String quantidadeBase,
                                              long unidadeId) throws Exception {
        int categoriaId = objectMapper.readTree(post(
                "/api/cardapio/admin/categorias",
                Map.of("codigo", codigo + "_cat", "nomeExibicao", "Bebidas " + codigo, "ordemExibicao", 2),
                gerente
        ).getBody()).path("id").asInt();
        int itemId = objectMapper.readTree(post(
                "/api/cardapio/admin/itens",
                itemDiretoPayload(codigo, "Refrigerante " + codigo, categoriaId, produtoId),
                gerente
        ).getBody()).path("id").asInt();
        JsonNode variacao = objectMapper.readTree(post(
                "/api/cardapio/itens/" + itemId + "/variacoes",
                variacaoDiretaPayload("Lata", "LATA", preco, produtoId, quantidadeBase),
                gerente
        ).getBody());
        assertBigDecimalEquals(new BigDecimal(quantidadeBase).setScale(6).toPlainString(), variacao.path("quantidadeProdutoDiretoBase").decimalValue());
        assertTrue(unidadeId > 0);
        return variacao.path("id").asLong();
    }

    private long criarItemPreparadoComIngrediente(String codigo,
                                                  String nomeItem,
                                                  int produtoId,
                                                  long unidadeId,
                                                  String quantidade,
                                                  String preco) throws Exception {
        int categoriaId = objectMapper.readTree(post(
                "/api/cardapio/admin/categorias",
                Map.of("codigo", codigo + "_cat", "nomeExibicao", "Preparados " + codigo, "ordemExibicao", 3),
                gerente
        ).getBody()).path("id").asInt();
        int itemId = objectMapper.readTree(post(
                "/api/cardapio/admin/itens",
                itemPreparadoPayload(codigo, nomeItem, categoriaId),
                gerente
        ).getBody()).path("id").asInt();
        long variacaoId = objectMapper.readTree(post(
                "/api/cardapio/itens/" + itemId + "/variacoes",
                variacaoPreparadaPayload("Padrao preparo", "PAD_PREP", preco),
                gerente
        ).getBody()).path("id").asLong();
        JsonNode ficha = objectMapper.readTree(post(
                "/api/cardapio/variacoes/" + variacaoId + "/ficha-tecnica",
                fichaPayload("Ficha " + nomeItem, unidadesPorCodigo().get("UNIDADE")),
                gerente
        ).getBody());
        long fichaId = ficha.path("fichaTecnicaId").asLong();
        long versaoId = ficha.path("id").asLong();
        adicionarIngrediente(fichaId, versaoId, produtoId, unidadeId, quantidade);
        JsonNode publicada = objectMapper.readTree(post(
                "/api/fichas-tecnicas/" + fichaId + "/versoes/" + versaoId + "/publicar",
                Map.of("filialReferenciaId", 1, "motivo", "Publicacao fixture compartilhado"),
                gerente
        ).getBody());
        assertEquals("PUBLICADA", publicada.path("status").asText());
        return variacaoId;
    }

    private long criarCompraEstoque() throws Exception {
        ResponseEntity<String> resposta = post("/api/compras", Map.of(), gerente);
        assertEquals(HttpStatus.CREATED, resposta.getStatusCode(), resposta.getBody());
        return objectMapper.readTree(resposta.getBody()).path("id").asLong();
    }

    private ResponseEntity<String> confirmarCompraEstoque(long compraId, String chave, String motivo) {
        HttpHeaders headers = headers(gerente);
        headers.set("Idempotency-Key", chave);
        return restTemplate.exchange(
                url("/api/compras/" + compraId + "/confirmar"),
                HttpMethod.POST,
                new HttpEntity<>(Map.of("motivo", motivo), headers),
                String.class
        );
    }

    private void atualizarPrecoVariacao(long variacaoId, String novoPreco) throws Exception {
        JsonNode variacao = objectMapper.readTree(get("/api/cardapio/variacoes/" + variacaoId, gerente).getBody());
        Integer produtoDiretoId = variacao.path("produtoDiretoId").isNull() ? null : variacao.path("produtoDiretoId").asInt();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("nome", variacao.path("nome").asText());
        body.put("codigo", variacao.path("codigo").asText());
        body.put("precoVenda", novoPreco);
        body.put("tipoComposicao", variacao.path("tipoComposicao").asText());
        body.put("produtoDiretoId", produtoDiretoId);
        body.put("quantidadeProdutoDiretoBase", variacao.path("quantidadeProdutoDiretoBase").asText());
        body.put("ativa", variacao.path("ativa").asBoolean());
        body.put("padrao", variacao.path("padrao").asBoolean());
        body.put("ordemExibicao", variacao.path("ordemExibicao").asInt());
        assertEquals(HttpStatus.OK, put("/api/cardapio/variacoes/" + variacaoId, body, gerente).getStatusCode());
    }

    private void atualizarStatusVariacao(long variacaoId, boolean ativa) throws Exception {
        JsonNode variacao = objectMapper.readTree(get("/api/cardapio/variacoes/" + variacaoId, gerente).getBody());
        Integer produtoDiretoId = variacao.path("produtoDiretoId").isNull() ? null : variacao.path("produtoDiretoId").asInt();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("nome", variacao.path("nome").asText());
        body.put("codigo", variacao.path("codigo").asText());
        body.put("precoVenda", variacao.path("precoVenda").asText());
        body.put("tipoComposicao", variacao.path("tipoComposicao").asText());
        body.put("produtoDiretoId", produtoDiretoId);
        body.put("quantidadeProdutoDiretoBase", variacao.path("quantidadeProdutoDiretoBase").asText());
        body.put("ativa", ativa);
        body.put("padrao", variacao.path("padrao").asBoolean());
        body.put("ordemExibicao", variacao.path("ordemExibicao").asInt());
        assertEquals(HttpStatus.OK, put("/api/cardapio/variacoes/" + variacaoId, body, gerente).getStatusCode());
    }

    private void adicionarIngrediente(long fichaId, long versaoId, int produtoId, long unidadeId, String quantidade) {
        ResponseEntity<String> resposta = post(
                "/api/fichas-tecnicas/" + fichaId + "/versoes/" + versaoId + "/ingredientes",
                ingredientePayload(produtoId, unidadeId, quantidade),
                gerente
        );
        assertEquals(HttpStatus.CREATED, resposta.getStatusCode(), resposta.getBody());
    }

    private void alterarCustoMedio(int produtoId, String novoCusto) {
        EstoqueProduto estoque = estoqueRepository.buscarPorFilialEProduto(1L, produtoId).orElseThrow();
        estoque.redefinirQuantidadeECusto(estoque.getQuantidadeAtual(), new BigDecimal(novoCusto));
        estoqueRepository.salvar(estoque);
    }

    private void alterarQuantidade(int produtoId, String novaQuantidade) {
        EstoqueProduto estoque = estoqueRepository.buscarPorFilialEProduto(1L, produtoId).orElseThrow();
        estoque.redefinirQuantidadeECusto(new BigDecimal(novaQuantidade), estoque.getCustoMedio());
        estoqueRepository.salvar(estoque);
    }

    private long criarVendaComItem(long variacaoId, String quantidade) throws Exception {
        return criarVendaComItemNaFilial(1L, variacaoId, quantidade);
    }

    private long criarVendaComItemNaFilial(Long filialId, long variacaoId, String quantidade) throws Exception {
        return objectMapper.readTree(post(
                "/api/vendas",
                Map.of("filialId", filialId, "canal", "BALCAO", "itens", List.of(
                        Map.of("variacaoId", variacaoId, "quantidade", quantidade)
                )),
                gerente
        ).getBody()).path("id").asLong();
    }

    private long encontrarIngrediente(JsonNode versao, int produtoId) {
        for (JsonNode ingrediente : versao.path("ingredientes")) {
            if (ingrediente.path("produtoId").asInt() == produtoId) {
                return ingrediente.path("id").asLong();
            }
        }
        throw new AssertionError("Ingrediente nao encontrado: " + produtoId);
    }

    private void assertConsumo(JsonNode consumos, String produtoContem, String quantidade) {
        for (JsonNode consumo : consumos) {
            if (consumo.path("produto").asText().contains(produtoContem)) {
                assertBigDecimalEquals(quantidade, consumo.path("quantidadeBaseTotal").decimalValue());
                return;
            }
        }
        throw new AssertionError("Consumo nao encontrado: " + produtoContem);
    }

    private void assertEstoque(int produtoId, String quantidadeEsperada) {
        EstoqueProduto estoque = estoqueRepository.buscarPorFilialEProduto(1L, produtoId).orElseThrow();
        assertBigDecimalEquals(quantidadeEsperada, estoque.getQuantidadeAtual());
    }

    private List<MovimentoEstoque> movimentosVendaDaVenda(long vendaId) {
        return movimentoRepository.listarTodos().stream()
                .filter(m -> "VENDA".equals(m.getOrigemTipo()))
                .filter(m -> vendaId == 0 || m.getOrigemId().equals(vendaId))
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

    private ResponseEntity<String> get(String path, Usuario usuario) {
        return restTemplate.exchange(url(path), HttpMethod.GET, new HttpEntity<>(headers(usuario)), String.class);
    }

    private ResponseEntity<String> post(String path, Object body, Usuario usuario) {
        return restTemplate.exchange(url(path), HttpMethod.POST, new HttpEntity<>(body, headers(usuario)), String.class);
    }

    private ResponseEntity<String> postComIdempotencia(String path, Object body, Usuario usuario, String chave) {
        HttpHeaders headers = headers(usuario);
        headers.set("Idempotency-Key", chave);
        return restTemplate.exchange(url(path), HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<String> put(String path, Object body, Usuario usuario) {
        return restTemplate.exchange(url(path), HttpMethod.PUT, new HttpEntity<>(body, headers(usuario)), String.class);
    }

    private HttpHeaders headers(Usuario usuario) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-User-RU", String.valueOf(usuario.getRu()));
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

    private static Map<String, Object> itemPreparadoPayload(String codigo, String nome, int categoriaId) {
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

    private static Map<String, Object> itemDiretoPayload(String codigo, String nome, int categoriaId, int produtoId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("codigo", codigo);
        body.put("nome", nome);
        body.put("descricaoCurta", "Produto direto");
        body.put("precoVenda", "8.00");
        body.put("categoriaId", categoriaId);
        body.put("tipoItem", "ESTOQUE_DIRETO");
        body.put("ordemExibicao", 1);
        body.put("produtoVinculadoId", produtoId);
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

    private static Map<String, Object> variacaoDiretaPayload(String nome, String codigo, String preco, int produtoId) {
        return variacaoDiretaPayload(nome, codigo, preco, produtoId, "1");
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

    private static Map<String, Object> fichaPayload(String nome, long rendimentoUnidadeId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("nome", nome);
        body.put("rendimentoQuantidade", "1");
        body.put("rendimentoUnidadeId", rendimentoUnidadeId);
        body.put("tempoPreparoMinutos", 20);
        body.put("observacaoInterna", "Teste Fase 2");
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

    private record PizzaFixture(
            long variacaoId,
            long fichaId,
            long versao1Id,
            int massaId,
            int molhoId,
            int mussarelaId,
            int calabresaId,
            int cebolaId,
            Map<String, Long> unidades
    ) {}
}
