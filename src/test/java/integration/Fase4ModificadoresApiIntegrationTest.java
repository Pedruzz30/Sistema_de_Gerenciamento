package integration;

import app.Main;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import model.EstoqueProduto;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        classes = Main.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.port=0"
)
class Fase4ModificadoresApiIntegrationTest {

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
    void hamburguerComAdicoesRemocoesESubstituicaoFinalizaSnapshotEEstorna() throws Exception {
        HamburguerFixture h = prepararHamburguer("f4_hamb_" + System.nanoTime());

        JsonNode config = objectMapper.readTree(get(
                "/api/cardapio/variacoes/" + h.variacaoId() + "/configuracao?filialId=1&canal=BALCAO"
        ).getBody());
        assertEquals(h.variacaoId(), config.path("variacaoId").asLong());
        assertFalse(config.path("configuracaoVersao").asText().isBlank());

        List<Map<String, Object>> selecoes = List.of(
                selecao(h.pontoGrupoId(), h.aoPontoOpcaoId(), 1),
                selecao(h.adicionaisGrupoId(), h.baconOpcaoId(), 1),
                selecao(h.adicionaisGrupoId(), h.queijoOpcaoId(), 2),
                selecao(h.remocoesGrupoId(), h.semCebolaOpcaoId(), 1),
                selecao(h.molhoGrupoId(), h.barbecueOpcaoId(), 1)
        );
        JsonNode validacao = objectMapper.readTree(post(
                "/api/cardapio/variacoes/" + h.variacaoId() + "/validar-configuracao",
                Map.of(
                        "filialId", 1,
                        "canal", "BALCAO",
                        "quantidadeItem", "1",
                        "configuracaoVersao", config.path("configuracaoVersao").asText(),
                        "selecoes", selecoes
                )
        ).getBody());
        assertTrue(validacao.path("valida").asBoolean());
        assertTrue(validacao.path("disponivel").asBoolean());
        assertBigDecimalEquals("48.0000", validacao.path("precoUnitarioFinal").decimalValue());

        JsonNode venda = objectMapper.readTree(post(
                "/api/vendas",
                Map.of("filialId", 1, "canal", "BALCAO", "itens", List.of(
                        Map.of(
                                "variacaoId", h.variacaoId(),
                                "quantidade", "1",
                                "configuracaoVersao", config.path("configuracaoVersao").asText(),
                                "selecoes", selecoes,
                                "observacao", "Embalar separado"
                        )
                ))
        ).getBody());
        long vendaId = venda.path("id").asLong();
        JsonNode finalizada = objectMapper.readTree(postComIdempotencia(
                "/api/vendas/" + vendaId + "/finalizar",
                Map.of(),
                "f4-finaliza-" + vendaId
        ).getBody());

        assertEquals("FINALIZADA", finalizada.path("status").asText());
        assertBigDecimalEquals("48.0000", finalizada.path("valorTotal").decimalValue());
        JsonNode item = finalizada.path("itens").get(0);
        assertBigDecimalEquals("35.0000", item.path("precoUnitarioBase").decimalValue());
        assertBigDecimalEquals("13.0000", item.path("acrescimoUnitario").decimalValue());
        assertBigDecimalEquals("48.0000", item.path("subtotal").decimalValue());
        assertEquals(5, item.path("modificadores").size());
        JsonNode semCebola = modificadorPorOpcao(item.path("modificadores"), "SEM_CEBOLA");
        assertEquals(1, semCebola.path("impactos").size());
        assertEquals("REMOVER", semCebola.path("impactos").get(0).path("tipoEfeito").asText());
        assertBigDecimalEquals("-30.000000", semCebola.path("impactos").get(0).path("quantidadeDeltaBase").decimalValue());
        JsonNode aoPonto = modificadorPorOpcao(item.path("modificadores"), "AO_PONTO");
        assertEquals(0, aoPonto.path("impactos").size(), "Opcao sem impacto deve permanecer no snapshot sem gerar consumo.");

        assertConsumoTotal(item.path("consumos"), "Pao " + h.sufixo(), "1.000000");
        assertConsumoTotal(item.path("consumos"), "Carne " + h.sufixo(), "180.000000");
        assertConsumoTotal(item.path("consumos"), "Queijo " + h.sufixo(), "4.000000");
        assertConsumoTotal(item.path("consumos"), "Bacon " + h.sufixo(), "50.000000");
        assertConsumoTotal(item.path("consumos"), "Barbecue " + h.sufixo(), "25.000000");
        assertSemConsumo(item.path("consumos"), "Cebola " + h.sufixo());
        assertSemConsumo(item.path("consumos"), "Molho casa " + h.sufixo());

        assertEstoque(h.paoId(), "99.000000");
        assertEstoque(h.carneId(), "9820.000000");
        assertEstoque(h.queijoId(), "96.000000");
        assertEstoque(h.cebolaId(), "1000.000000");
        assertEstoque(h.molhoId(), "1000.000000");
        assertEstoque(h.baconId(), "950.000000");
        assertEstoque(h.barbecueId(), "975.000000");
        assertEquals(5, movimentoRepository.listarTodos().stream()
                .filter(m -> m.getOrigemId() == vendaId)
                .filter(m -> m.getTipo() == TipoMovimentoEstoque.SAIDA_VENDA)
                .count());

        JsonNode cancelada = objectMapper.readTree(postComIdempotencia(
                "/api/vendas/" + vendaId + "/cancelar",
                Map.of("motivo", "Cancelamento Fase 4"),
                "f4-cancela-" + vendaId
        ).getBody());
        assertEquals("CANCELADA", cancelada.path("status").asText());
        assertEstoque(h.paoId(), "100.000000");
        assertEstoque(h.carneId(), "10000.000000");
        assertEstoque(h.queijoId(), "100.000000");
        assertEstoque(h.cebolaId(), "1000.000000");
        assertEstoque(h.molhoId(), "1000.000000");
        assertEstoque(h.baconId(), "1000.000000");
        assertEstoque(h.barbecueId(), "1000.000000");
    }

    @Test
    void grupoObrigatorioAusenteEAdicionalSemEstoqueSaoBloqueadosSemAfetarBase() throws Exception {
        HamburguerFixture h = prepararHamburguer("f4_valid_" + System.nanoTime());
        JsonNode config = objectMapper.readTree(get(
                "/api/cardapio/variacoes/" + h.variacaoId() + "/configuracao?filialId=1&canal=BALCAO"
        ).getBody());

        ResponseEntity<String> obrigatorioAusente = post(
                "/api/vendas",
                Map.of("filialId", 1, "canal", "BALCAO", "itens", List.of(
                        Map.of(
                                "variacaoId", h.variacaoId(),
                                "quantidade", "1",
                                "configuracaoVersao", config.path("configuracaoVersao").asText(),
                                "selecoes", List.of()
                        )
                ))
        );
        assertEquals(HttpStatus.BAD_REQUEST, obrigatorioAusente.getStatusCode(), obrigatorioAusente.getBody());
        assertEquals("MODIFICADOR_OBRIGATORIO_AUSENTE",
                objectMapper.readTree(obrigatorioAusente.getBody()).path("codigo").asText());

        redefinirEstoque(h.baconId(), "0", "0.08000000");
        JsonNode semBacon = objectMapper.readTree(post(
                "/api/cardapio/variacoes/" + h.variacaoId() + "/validar-configuracao",
                Map.of(
                        "filialId", 1,
                        "canal", "BALCAO",
                        "quantidadeItem", "1",
                        "configuracaoVersao", config.path("configuracaoVersao").asText(),
                        "selecoes", List.of(selecao(h.pontoGrupoId(), h.aoPontoOpcaoId(), 1))
                )
        ).getBody());
        assertTrue(semBacon.path("valida").asBoolean());
        assertTrue(semBacon.path("disponivel").asBoolean());

        JsonNode comBacon = objectMapper.readTree(post(
                "/api/cardapio/variacoes/" + h.variacaoId() + "/validar-configuracao",
                Map.of(
                        "filialId", 1,
                        "canal", "BALCAO",
                        "quantidadeItem", "1",
                        "configuracaoVersao", config.path("configuracaoVersao").asText(),
                        "selecoes", List.of(
                                selecao(h.pontoGrupoId(), h.aoPontoOpcaoId(), 1),
                                selecao(h.adicionaisGrupoId(), h.baconOpcaoId(), 1)
                        )
                )
        ).getBody());
        assertFalse(comBacon.path("disponivel").asBoolean());
        assertEquals("ESTOQUE_INSUFICIENTE", comBacon.path("motivos").get(0).path("codigo").asText());
        assertTrue(comBacon.path("motivos").get(0).path("produtoNome").asText().startsWith("Bacon "));
    }

    @Test
    void hashOperacionalIgnoraDescricaoMasMudaComPreco() throws Exception {
        HamburguerFixture h = prepararHamburguer("f45_hash_" + System.nanoTime());
        JsonNode config1 = objectMapper.readTree(get(
                "/api/cardapio/variacoes/" + h.variacaoId() + "/configuracao?filialId=1&canal=BALCAO"
        ).getBody());

        assertEquals(HttpStatus.OK, put(
                "/api/cardapio/opcoes-modificadores/" + h.baconOpcaoId(),
                opcaoPayload("Bacon adicional", "Descricao operacionalmente neutra", "BACON", "5.00", 3, false, true, 1)
        ).getStatusCode());
        JsonNode configDescricao = objectMapper.readTree(get(
                "/api/cardapio/variacoes/" + h.variacaoId() + "/configuracao?filialId=1&canal=BALCAO"
        ).getBody());
        assertEquals(config1.path("configuracaoVersao").asText(), configDescricao.path("configuracaoVersao").asText());

        assertEquals(HttpStatus.OK, put(
                "/api/cardapio/opcoes-modificadores/" + h.baconOpcaoId(),
                opcaoPayload("Bacon adicional", "Descricao operacionalmente neutra", "BACON", "6.00", 3, false, true, 1)
        ).getStatusCode());
        JsonNode configPreco = objectMapper.readTree(get(
                "/api/cardapio/variacoes/" + h.variacaoId() + "/configuracao?filialId=1&canal=BALCAO"
        ).getBody());
        assertFalse(config1.path("configuracaoVersao").asText().equals(configPreco.path("configuracaoVersao").asText()));

        ResponseEntity<String> rascunhoComHashAntigo = post(
                "/api/vendas",
                Map.of("filialId", 1, "canal", "BALCAO", "itens", List.of(
                        Map.of(
                                "variacaoId", h.variacaoId(),
                                "quantidade", "1",
                                "configuracaoVersao", config1.path("configuracaoVersao").asText(),
                                "selecoes", List.of(selecao(h.pontoGrupoId(), h.aoPontoOpcaoId(), 1))
                        )
                ))
        );
        assertEquals(HttpStatus.CONFLICT, rascunhoComHashAntigo.getStatusCode(), rascunhoComHashAntigo.getBody());
        assertEquals("MODIFICADOR_CONFIGURACAO_ALTERADA",
                objectMapper.readTree(rascunhoComHashAntigo.getBody()).path("codigo").asText());
    }

    @Test
    void grupoObrigatorioSemOpcaoDisponivelBloqueiaDisponibilidadeBase() throws Exception {
        HamburguerFixture h = prepararHamburguer("f45_obrig_" + System.nanoTime());
        redefinirEstoque(h.baconId(), "0", "0.08000000");
        long escolhaGrupo = criarGrupo("Escolha obrigatoria " + h.sufixo(), "ESCOLHA_" + h.sufixo(), "UNICA", true, 1, 1, false);
        long baconObrigatorio = criarOpcao(escolhaGrupo, "Bacon obrigatorio", "BACON_OBRIGATORIO", "0.00", null, false);
        criarComponente(baconObrigatorio, "ADICIONAR", null, h.baconId(), "50", unidadesPorCodigo().get("GRAMA"));
        associarGrupo(h.variacaoId(), escolhaGrupo, 10, true, 1, 1);

        JsonNode disponibilidade = objectMapper.readTree(get(
                "/api/cardapio/variacoes/" + h.variacaoId() + "/disponibilidade?filialId=1&canal=BALCAO"
        ).getBody());

        assertFalse(disponibilidade.path("disponivel").asBoolean());
        motivoPorCodigo(disponibilidade.path("motivos"), "GRUPO_OBRIGATORIO_SEM_OPCAO_DISPONIVEL");
    }

    @Test
    void opcoesPadraoExplicamAplicacaoAutomaticaEPagaRequerConfirmacao() throws Exception {
        HamburguerFixture h = prepararHamburguer("f45_padrao_" + System.nanoTime());
        assertEquals(HttpStatus.OK, put(
                "/api/cardapio/opcoes-modificadores/" + h.aoPontoOpcaoId(),
                opcaoPayload("Ao ponto", null, "AO_PONTO", "0.00", null, true, true, 1)
        ).getStatusCode());
        assertEquals(HttpStatus.OK, put(
                "/api/cardapio/opcoes-modificadores/" + h.barbecueOpcaoId(),
                opcaoPayload("Trocar por barbecue", null, "BARBECUE", "2.00", null, true, true, 1)
        ).getStatusCode());

        JsonNode config = objectMapper.readTree(get(
                "/api/cardapio/variacoes/" + h.variacaoId() + "/configuracao?filialId=1&canal=BALCAO"
        ).getBody());
        JsonNode aoPonto = opcaoConfiguracao(config, "AO_PONTO");
        assertTrue(aoPonto.path("aplicacaoAutomaticaPermitida").asBoolean());
        assertFalse(aoPonto.path("requerConfirmacao").asBoolean());
        JsonNode barbecue = opcaoConfiguracao(config, "BARBECUE");
        assertFalse(barbecue.path("aplicacaoAutomaticaPermitida").asBoolean());
        assertTrue(barbecue.path("requerConfirmacao").asBoolean());

        ResponseEntity<String> rascunho = post(
                "/api/vendas",
                Map.of("filialId", 1, "canal", "BALCAO", "itens", List.of(
                        Map.of(
                                "variacaoId", h.variacaoId(),
                                "quantidade", "1",
                                "configuracaoVersao", config.path("configuracaoVersao").asText(),
                                "selecoes", List.of()
                        )
                ))
        );
        assertEquals(HttpStatus.CREATED, rascunho.getStatusCode(), rascunho.getBody());
        JsonNode item = objectMapper.readTree(rascunho.getBody()).path("itens").get(0);
        assertTrue(modificadorPorOpcao(item.path("modificadores"), "AO_PONTO").path("selecaoPadrao").asBoolean());
    }

    @Test
    void itemRascunhoPodeSerEditadoEEdicaoAposFinalizacaoERejeitada() throws Exception {
        HamburguerFixture h = prepararHamburguer("f45_edit_" + System.nanoTime());
        JsonNode config = objectMapper.readTree(get(
                "/api/cardapio/variacoes/" + h.variacaoId() + "/configuracao?filialId=1&canal=BALCAO"
        ).getBody());
        JsonNode venda = objectMapper.readTree(post(
                "/api/vendas",
                Map.of("filialId", 1, "canal", "BALCAO", "itens", List.of(
                        Map.of(
                                "variacaoId", h.variacaoId(),
                                "quantidade", "1",
                                "configuracaoVersao", config.path("configuracaoVersao").asText(),
                                "selecoes", List.of(
                                        selecao(h.pontoGrupoId(), h.aoPontoOpcaoId(), 1),
                                        selecao(h.adicionaisGrupoId(), h.baconOpcaoId(), 1)
                                )
                        )
                ))
        ).getBody());
        long vendaId = venda.path("id").asLong();
        long itemId = venda.path("itens").get(0).path("id").asLong();

        JsonNode editado = objectMapper.readTree(put(
                "/api/vendas/" + vendaId + "/itens/" + itemId,
                Map.of(
                        "variacaoId", h.variacaoId(),
                        "quantidade", "2",
                        "configuracaoVersao", config.path("configuracaoVersao").asText(),
                        "selecoes", List.of(
                                selecao(h.pontoGrupoId(), h.aoPontoOpcaoId(), 1),
                                selecao(h.adicionaisGrupoId(), h.queijoOpcaoId(), 1)
                        ),
                        "observacao", "Editar antes de finalizar"
                )
        ).getBody());
        assertBigDecimalEquals("2.000000", editado.path("quantidade").decimalValue());
        assertEquals(2, editado.path("modificadores").size());
        assertTrue(modificadorPorOpcao(editado.path("modificadores"), "QUEIJO").path("opcao").asText().contains("Queijo"));

        assertEquals(HttpStatus.OK, postComIdempotencia(
                "/api/vendas/" + vendaId + "/finalizar",
                Map.of(),
                "f45-edit-finaliza-" + vendaId
        ).getStatusCode());
        ResponseEntity<String> edicaoFinalizada = put(
                "/api/vendas/" + vendaId + "/itens/" + itemId,
                Map.of(
                        "variacaoId", h.variacaoId(),
                        "quantidade", "1",
                        "configuracaoVersao", config.path("configuracaoVersao").asText(),
                        "selecoes", List.of(selecao(h.pontoGrupoId(), h.aoPontoOpcaoId(), 1))
                )
        );
        assertEquals(HttpStatus.BAD_REQUEST, edicaoFinalizada.getStatusCode(), edicaoFinalizada.getBody());
        assertEquals("VENDA_NAO_ESTA_EM_RASCUNHO",
                objectMapper.readTree(edicaoFinalizada.getBody()).path("codigo").asText());
    }

    private HamburguerFixture prepararHamburguer(String sufixo) throws Exception {
        Map<String, Long> unidades = unidadesPorCodigo();
        int paoId = criarProduto("Pao " + sufixo, "UNIDADE", "100", "1.00000000", "BLOQUEAR");
        int carneId = criarProduto("Carne " + sufixo, "GRAMA", "10000", "0.05000000", "BLOQUEAR");
        int queijoId = criarProduto("Queijo " + sufixo, "UNIDADE", "100", "1.00000000", "BLOQUEAR");
        int cebolaId = criarProduto("Cebola " + sufixo, "GRAMA", "1000", "0.01000000", "BLOQUEAR");
        int molhoId = criarProduto("Molho casa " + sufixo, "MILILITRO", "1000", "0.02000000", "BLOQUEAR");
        int baconId = criarProduto("Bacon " + sufixo, "GRAMA", "1000", "0.08000000", "BLOQUEAR");
        int barbecueId = criarProduto("Barbecue " + sufixo, "MILILITRO", "1000", "0.04000000", "BLOQUEAR");

        int categoriaId = objectMapper.readTree(post(
                "/api/cardapio/admin/categorias",
                Map.of("codigo", "cat_" + sufixo, "nomeExibicao", "Hamburgueres " + sufixo, "ordemExibicao", 1)
        ).getBody()).path("id").asInt();
        int itemId = objectMapper.readTree(post(
                "/api/cardapio/admin/itens",
                itemPayload("item_" + sufixo, "Hamburguer " + sufixo, categoriaId)
        ).getBody()).path("id").asInt();
        long variacaoId = objectMapper.readTree(post(
                "/api/cardapio/itens/" + itemId + "/variacoes",
                variacaoPreparadaPayload("Duplo", "DUPLO_" + sufixo, "35.00")
        ).getBody()).path("id").asLong();
        JsonNode ficha = objectMapper.readTree(post(
                "/api/cardapio/variacoes/" + variacaoId + "/ficha-tecnica",
                Map.of(
                        "nome", "Ficha Hamburguer " + sufixo,
                        "rendimentoQuantidade", "1",
                        "rendimentoUnidadeId", unidades.get("UNIDADE"),
                        "tempoPreparoMinutos", 15
                )
        ).getBody());
        long fichaId = ficha.path("fichaTecnicaId").asLong();
        long versaoId = ficha.path("id").asLong();
        for (Map<String, Object> ingrediente : List.of(
                ingrediente(paoId, unidades.get("UNIDADE"), "1"),
                ingrediente(carneId, unidades.get("GRAMA"), "180"),
                ingrediente(queijoId, unidades.get("UNIDADE"), "2"),
                ingrediente(cebolaId, unidades.get("GRAMA"), "30"),
                ingrediente(molhoId, unidades.get("MILILITRO"), "20")
        )) {
            assertEquals(HttpStatus.CREATED, post(
                    "/api/fichas-tecnicas/" + fichaId + "/versoes/" + versaoId + "/ingredientes",
                    ingrediente
            ).getStatusCode());
        }
        assertEquals(HttpStatus.OK, post(
                "/api/fichas-tecnicas/" + fichaId + "/versoes/" + versaoId + "/publicar",
                Map.of("filialReferenciaId", 1, "motivo", "Publicacao Fase 4")
        ).getStatusCode());

        long pontoGrupo = criarGrupo("Ponto " + sufixo, "PONTO_" + sufixo, "UNICA", true, 1, 1, false);
        long aoPonto = criarOpcao(pontoGrupo, "Ao ponto", "AO_PONTO", "0.00", null, false);
        criarComponente(aoPonto, "SEM_IMPACTO_ESTOQUE", null, null, null, null);
        associarGrupo(variacaoId, pontoGrupo, 1, true, 1, 1);

        long adicionaisGrupo = criarGrupo("Adicionais " + sufixo, "ADICIONAIS_" + sufixo, "MULTIPLA", false, 0, 5, true);
        long baconOpcao = criarOpcao(adicionaisGrupo, "Bacon adicional", "BACON", "5.00", 3, false);
        criarComponente(baconOpcao, "ADICIONAR", null, baconId, "50", unidades.get("GRAMA"));
        long queijoOpcao = criarOpcao(adicionaisGrupo, "Queijo adicional", "QUEIJO", "3.00", 3, false);
        criarComponente(queijoOpcao, "ADICIONAR", null, queijoId, "1", unidades.get("UNIDADE"));
        associarGrupo(variacaoId, adicionaisGrupo, 2, false, 0, 5);

        long remocoesGrupo = criarGrupo("Remover " + sufixo, "REMOVER_" + sufixo, "MULTIPLA", false, 0, 5, false);
        long semCebola = criarOpcao(remocoesGrupo, "Sem cebola", "SEM_CEBOLA", "0.00", null, false);
        criarComponente(semCebola, "REMOVER", cebolaId, null, "30", unidades.get("GRAMA"));
        associarGrupo(variacaoId, remocoesGrupo, 3, false, 0, 5);

        long molhoGrupo = criarGrupo("Molho " + sufixo, "MOLHO_" + sufixo, "UNICA", false, 0, 1, false);
        long barbecueOpcao = criarOpcao(molhoGrupo, "Trocar por barbecue", "BARBECUE", "2.00", null, false);
        criarComponente(barbecueOpcao, "REMOVER", molhoId, null, "20", unidades.get("MILILITRO"));
        criarComponente(barbecueOpcao, "ADICIONAR", null, barbecueId, "25", unidades.get("MILILITRO"));
        associarGrupo(variacaoId, molhoGrupo, 4, false, 0, 1);

        return new HamburguerFixture(
                sufixo,
                variacaoId,
                paoId,
                carneId,
                queijoId,
                cebolaId,
                molhoId,
                baconId,
                barbecueId,
                pontoGrupo,
                aoPonto,
                adicionaisGrupo,
                baconOpcao,
                queijoOpcao,
                remocoesGrupo,
                semCebola,
                molhoGrupo,
                barbecueOpcao
        );
    }

    private long criarGrupo(String nome, String codigo, String tipo, boolean obrigatorio, int min, int max, boolean repeticao) throws Exception {
        return objectMapper.readTree(post(
                "/api/cardapio/grupos-modificadores",
                Map.of(
                        "nome", nome,
                        "codigo", codigo,
                        "tipoSelecao", tipo,
                        "obrigatorio", obrigatorio,
                        "quantidadeMinima", min,
                        "quantidadeMaxima", max,
                        "permiteRepeticao", repeticao,
                        "ativo", true,
                        "ordemExibicao", 1
                )
        ).getBody()).path("id").asLong();
    }

    private long criarOpcao(long grupoId, String nome, String codigo, String acrescimo, Integer maxPorItem, boolean padrao) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("nome", nome);
        payload.put("codigo", codigo);
        payload.put("acrescimoPreco", acrescimo);
        payload.put("quantidadeMaximaPorItem", maxPorItem);
        payload.put("selecionadaPorPadrao", padrao);
        payload.put("ativa", true);
        payload.put("ordemExibicao", 1);
        return objectMapper.readTree(post("/api/cardapio/grupos-modificadores/" + grupoId + "/opcoes", payload).getBody())
                .path("id").asLong();
    }

    private void criarComponente(long opcaoId,
                                 String tipoEfeito,
                                 Integer produtoOrigemId,
                                 Integer produtoDestinoId,
                                 String quantidadeBase,
                                 Long unidadeBaseId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tipoEfeito", tipoEfeito);
        payload.put("produtoOrigemId", produtoOrigemId);
        payload.put("produtoDestinoId", produtoDestinoId);
        payload.put("quantidadeBase", quantidadeBase);
        payload.put("unidadeBaseId", unidadeBaseId);
        payload.put("politicaFalta", "HERDAR_PRODUTO");
        payload.put("ordem", 1);
        assertEquals(HttpStatus.CREATED, post("/api/cardapio/opcoes-modificadores/" + opcaoId + "/componentes", payload).getStatusCode());
    }

    private void associarGrupo(long variacaoId, long grupoId, int ordem, boolean obrigatorio, int min, int max) {
        assertEquals(HttpStatus.CREATED, post(
                "/api/cardapio/variacoes/" + variacaoId + "/grupos-modificadores",
                Map.of(
                        "grupoId", grupoId,
                        "obrigatorioOverride", obrigatorio,
                        "quantidadeMinimaOverride", min,
                        "quantidadeMaximaOverride", max,
                        "ordemExibicao", ordem,
                        "ativo", true
                )
        ).getStatusCode());
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

    private void assertEstoque(int produtoId, String esperado) {
        assertBigDecimalEquals(esperado,
                estoqueRepository.buscarPorFilialEProduto(1L, produtoId).orElseThrow().getQuantidadeAtual());
    }

    private void assertConsumoTotal(JsonNode consumos, String produto, String esperado) {
        BigDecimal total = BigDecimal.ZERO;
        for (JsonNode consumo : consumos) {
            if (produto.equals(consumo.path("produto").asText())) {
                total = total.add(consumo.path("quantidadeBaseTotal").decimalValue());
            }
        }
        assertBigDecimalEquals(esperado, total);
    }

    private void assertSemConsumo(JsonNode consumos, String produto) {
        for (JsonNode consumo : consumos) {
            assertFalse(produto.equals(consumo.path("produto").asText()), "Consumo inesperado de " + produto);
        }
    }

    private JsonNode modificadorPorOpcao(JsonNode modificadores, String codigoOpcao) {
        for (JsonNode modificador : modificadores) {
            if (codigoOpcao.equals(modificador.path("codigoOpcao").asText())) {
                return modificador;
            }
        }
        throw new AssertionError("Modificador nao encontrado: " + codigoOpcao + " em " + modificadores);
    }

    private JsonNode opcaoConfiguracao(JsonNode configuracao, String codigoOpcao) {
        for (JsonNode grupo : configuracao.path("grupos")) {
            for (JsonNode opcao : grupo.path("opcoes")) {
                if (codigoOpcao.equals(opcao.path("codigo").asText())) {
                    return opcao;
                }
            }
        }
        throw new AssertionError("Opcao nao encontrada: " + codigoOpcao + " em " + configuracao);
    }

    private JsonNode motivoPorCodigo(JsonNode motivos, String codigo) {
        for (JsonNode motivo : motivos) {
            if (codigo.equals(motivo.path("codigo").asText())) {
                return motivo;
            }
        }
        throw new AssertionError("Motivo nao encontrado: " + codigo + " em " + motivos);
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

    private ResponseEntity<String> put(String path, Object body) {
        return restTemplate.exchange(url(path), HttpMethod.PUT, new HttpEntity<>(body, headers()), String.class);
    }

    private ResponseEntity<String> postComIdempotencia(String path, Object body, String chave) {
        HttpHeaders headers = headers();
        headers.set("Idempotency-Key", chave);
        return restTemplate.exchange(url(path), HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
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

    private static Map<String, Object> itemPayload(String codigo, String nome, int categoriaId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("codigo", codigo);
        body.put("nome", nome);
        body.put("descricaoCurta", "Item Fase 4");
        body.put("precoVenda", "35.00");
        body.put("categoriaId", categoriaId);
        body.put("tipoItem", "PREPARADO_SOB_DEMANDA");
        body.put("ordemExibicao", 1);
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

    private static Map<String, Object> ingrediente(int produtoId, long unidadeId, String quantidade) {
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

    private static Map<String, Object> selecao(long grupoId, long opcaoId, int quantidade) {
        return Map.of("grupoId", grupoId, "opcaoId", opcaoId, "quantidade", quantidade);
    }

    private static Map<String, Object> opcaoPayload(String nome,
                                                    String descricao,
                                                    String codigo,
                                                    String acrescimo,
                                                    Integer maxPorItem,
                                                    boolean padrao,
                                                    boolean ativa,
                                                    int ordem) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("nome", nome);
        payload.put("descricao", descricao);
        payload.put("codigo", codigo);
        payload.put("acrescimoPreco", acrescimo);
        payload.put("quantidadeMaximaPorItem", maxPorItem);
        payload.put("selecionadaPorPadrao", padrao);
        payload.put("ativa", ativa);
        payload.put("ordemExibicao", ordem);
        return payload;
    }

    private static void assertBigDecimalEquals(String esperado, BigDecimal atual) {
        assertEquals(0, new BigDecimal(esperado).compareTo(atual), "Esperado " + esperado + ", atual " + atual);
    }

    private record HamburguerFixture(
            String sufixo,
            long variacaoId,
            int paoId,
            int carneId,
            int queijoId,
            int cebolaId,
            int molhoId,
            int baconId,
            int barbecueId,
            long pontoGrupoId,
            long aoPontoOpcaoId,
            long adicionaisGrupoId,
            long baconOpcaoId,
            long queijoOpcaoId,
            long remocoesGrupoId,
            long semCebolaOpcaoId,
            long molhoGrupoId,
            long barbecueOpcaoId
    ) {
    }
}
