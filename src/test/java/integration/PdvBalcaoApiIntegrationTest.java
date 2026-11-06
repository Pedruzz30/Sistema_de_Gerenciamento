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
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fluxo de balcão no banco real: cadastrar a bebida, bipar, vender dividindo
 * entre Pix e dinheiro, conferir estoque e gaveta, e cancelar.
 */
@SpringBootTest(
        classes = Main.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.port=0"
)
class PdvBalcaoApiIntegrationTest {

    private static final AtomicInteger SEQUENCIA = new AtomicInteger();

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsuarioRepository usuarioRepository;

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
    void cadastrarBiparVenderDividindoPagamentoECancelar() throws Exception {
        String sufixo = sufixoUnico();
        String eanLata = ean13(sufixo + "1");
        String eanFardo = ean13(sufixo + "2");
        int numeroCaixa = numeroCaixaUnico();

        // Cadastro rápido
        JsonNode cadastro = json(assertOk(post("/api/produtos/revenda", revenda(
                "Cerveja teste " + sufixo, eanLata, eanFardo), gerente)));
        int produtoId = cadastro.path("produtoId").asInt();
        assertEquals(24, cadastro.path("quantidadeAtual").asInt());
        assertEquals(3, cadastro.path("variacoes").size());
        assertEquals(3, cadastro.path("codigos").size());

        // Bipe: a operadora de caixa lê o código
        JsonNode leitura = json(assertOk(get("/api/pdv/codigos/" + eanLata, caixa)));
        assertEquals("EAN13", leitura.path("tipo").asText());
        assertEquals("Gelada", leitura.path("variacao").path("variacaoNome").asText());
        assertEquals(1, leitura.path("alternativas").size());
        assertEquals("Natural", leitura.path("alternativas").get(0).path("variacaoNome").asText());
        long gelada = leitura.path("variacao").path("variacaoId").asLong();
        long natural = leitura.path("alternativas").get(0).path("variacaoId").asLong();
        long fardo = json(assertOk(get("/api/pdv/codigos/" + eanFardo, caixa))).path("variacao").path("variacaoId").asLong();

        assertEquals(HttpStatus.NOT_FOUND, get("/api/pdv/codigos/" + ean13(sufixo + "9"), caixa).getStatusCode());

        // A lista inteira, usada como cache na abertura do PDV, traz os códigos
        JsonNode lista = json(assertOk(get("/api/pdv/codigos", caixa)));
        assertTrue(contemCodigo(lista, eanLata));

        // A tela do PDV consulta a configuração antes de lançar o item
        JsonNode configuracao = json(assertOk(get(
                "/api/cardapio/variacoes/" + gelada + "/configuracao?filialId=1&canal=BALCAO", caixa)));
        assertEquals(0, configuracao.path("grupos").size());

        assertOk(post("/api/caixas/abrir", Map.of("numeroCaixa", numeroCaixa, "saldoInicial", 50), caixa));

        // 2 geladas (10,00) + 1 natural (4,00) + 1 fardo de 12 (40,00) = 54,00
        JsonNode venda = json(assertOk(post("/api/vendas", Map.of("filialId", 1, "canal", "BALCAO", "itens", List.of(
                Map.of("variacaoId", gelada, "quantidade", "2"),
                Map.of("variacaoId", natural, "quantidade", "1"),
                Map.of("variacaoId", fardo, "quantidade", "1")
        )), caixa)));
        long vendaId = venda.path("id").asLong();

        // Soma diferente do total é recusada
        ResponseEntity<String> divergente = postIdempotente("/api/vendas/" + vendaId + "/finalizar", Map.of(
                "numeroCaixaLegado", numeroCaixa,
                "pagamentos", List.of(Map.of("forma", "PIX", "valor", "10.00"))
        ), caixa, "pag-divergente-" + sufixo);
        assertEquals(HttpStatus.BAD_REQUEST, divergente.getStatusCode(), divergente.getBody());
        assertEquals(24, quantidadeNaTelaDeEstoque(produtoId), "Falha no pagamento nao pode baixar estoque");

        // R$ 30 no Pix + R$ 24 em dinheiro (recebeu R$ 30, troco de R$ 6)
        Map<String, Object> dinheiro = new LinkedHashMap<>();
        dinheiro.put("forma", "DINHEIRO");
        dinheiro.put("valor", "24.00");
        dinheiro.put("valorRecebido", "30.00");
        assertOk(postIdempotente("/api/vendas/" + vendaId + "/finalizar", Map.of(
                "numeroCaixaLegado", numeroCaixa,
                "pagamentos", List.of(Map.of("forma", "PIX", "valor", "30.00"), dinheiro)
        ), caixa, "pag-ok-" + sufixo));

        // Estoque: 24 - 2 - 1 - 12 = 9, no saldo oficial e na tela de Estoque
        assertEquals(9, quantidadeNaTelaDeEstoque(produtoId));
        assertBig("9", json(assertOk(get("/api/produtos/" + produtoId + "/estoque", gerente))).path("quantidadeAtual").decimalValue());

        // Gaveta: 50 + 24 (o Pix não passa por ela)
        JsonNode sessao = caixaAberto(numeroCaixa);
        assertBig("74", sessao.path("dinheiroNaGaveta").decimalValue());
        assertBig("54", sessao.path("totalVendas").decimalValue());
        assertBig("30", sessao.path("vendasPorForma").path("PIX").decimalValue());
        assertBig("24", sessao.path("vendasPorForma").path("DINHEIRO").decimalValue());
        assertEquals(1, sessao.path("quantidadeVendas").asInt());

        JsonNode movimentos = json(assertOk(get("/api/caixas/sessoes/" + sessao.path("caixaId").asLong() + "/movimentacoes", caixa)));
        assertEquals(2, movimentos.size());
        assertTrue(movimentos.get(0).path("id").asLong() > 0, "ID gerado pelo banco");
        assertTrue(movimentos.get(0).path("id").asLong() != movimentos.get(1).path("id").asLong());

        // Cancelamento: ESTORNO_VENDA nas formas pagas, nunca SANGRIA
        assertOk(postIdempotente("/api/vendas/" + vendaId + "/cancelar",
                Map.of("motivo", "Cliente desistiu"), gerente, "cancelar-" + sufixo));
        assertEquals(24, quantidadeNaTelaDeEstoque(produtoId));
        JsonNode depois = caixaAberto(numeroCaixa);
        assertBig("50", depois.path("dinheiroNaGaveta").decimalValue());
        assertBig("0", depois.path("totalVendas").decimalValue());
        JsonNode movimentosDepois = json(assertOk(get("/api/caixas/sessoes/" + depois.path("caixaId").asLong() + "/movimentacoes", caixa)));
        boolean temEstorno = false;
        for (JsonNode movimento : movimentosDepois) {
            assertFalse("SANGRIA".equals(movimento.path("tipo").asText()));
            temEstorno |= "ESTORNO_VENDA".equals(movimento.path("tipo").asText());
        }
        assertTrue(temEstorno);
    }

    @Test
    void operadoraDeCaixaNaoCadastraMasFazSangriaComAutorizacaoDoGerente() throws Exception {
        String sufixo = sufixoUnico();
        int numeroCaixa = numeroCaixaUnico();

        assertEquals(HttpStatus.FORBIDDEN,
                post("/api/produtos/revenda", revenda("Proibido " + sufixo, "", ""), caixa).getStatusCode());

        assertOk(post("/api/caixas/abrir", Map.of("numeroCaixa", numeroCaixa, "saldoInicial", 100), caixa));

        assertEquals(HttpStatus.FORBIDDEN, post("/api/caixas/sangria",
                Map.of("numeroCaixa", numeroCaixa, "valor", 40, "descricao", "Cofre"), caixa).getStatusCode());

        assertEquals(HttpStatus.FORBIDDEN, post("/api/caixas/sangria", Map.of(
                "numeroCaixa", numeroCaixa, "valor", 40, "descricao", "Cofre",
                "autorizacao", Map.of("nome", "Admin", "sobrenome", "Superior", "senha", "errada")
        ), caixa).getStatusCode());

        assertOk(post("/api/caixas/sangria", Map.of(
                "numeroCaixa", numeroCaixa, "valor", 40, "descricao", "Cofre",
                "autorizacao", Map.of("nome", "Admin", "sobrenome", "Superior", "senha", "1234")
        ), caixa));
        assertBig("60", caixaAberto(numeroCaixa).path("dinheiroNaGaveta").decimalValue());

        // Venda só com valor, sem itens, não baixa estoque: exige gerente.
        assertEquals(HttpStatus.FORBIDDEN, post("/api/caixas/venda",
                Map.of("numeroCaixa", numeroCaixa, "valor", 10, "descricao", "Avulsa"), caixa).getStatusCode());

        // Encerramento: compara só o dinheiro contado.
        JsonNode fechamento = json(assertOk(post("/api/caixas/encerrar", Map.of(
                "numeroCaixa", numeroCaixa, "valorContado", "55.00", "observacao", "",
                "autorizacao", Map.of("nome", "Admin", "sobrenome", "Superior", "senha", "1234")
        ), caixa)));
        assertBig("-5", fechamento.path("divergencia").decimalValue());
    }

    @Test
    void codigoRepetidoEInvalidoSaoRecusadosPelaApi() throws Exception {
        String sufixo = sufixoUnico();
        String ean = ean13(sufixo + "3");
        assertOk(post("/api/produtos/revenda", revenda("Refri teste " + sufixo, ean, ""), gerente));

        ResponseEntity<String> repetido = post("/api/produtos/revenda", revenda("Outro refri " + sufixo, ean, ""), gerente);
        assertEquals(HttpStatus.CONFLICT, repetido.getStatusCode(), repetido.getBody());

        // Último dígito trocado: o verificador não confere.
        char ultimo = ean.charAt(12);
        String invalido = ean.substring(0, 12) + (ultimo == '9' ? '0' : (char) (ultimo + 1));
        ResponseEntity<String> errado = post("/api/produtos/revenda", revenda("Mais um refri " + sufixo, invalido, ""), gerente);
        assertEquals(HttpStatus.BAD_REQUEST, errado.getStatusCode(), errado.getBody());
    }

    private static Map<String, Object> revenda(String nome, String ean, String eanFardo) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("nome", nome);
        body.put("codigoBarras", ean);
        body.put("precoVenda", "4.00");
        body.put("precoGelado", "5.00");
        body.put("custoUnitario", "2.50");
        body.put("quantidadeInicial", 24);
        body.put("quantidadeMinima", 6);
        body.put("fardo", Map.of("quantidadeUnidades", 12, "preco", "40.00", "codigoBarras", eanFardo));
        return body;
    }

    private static boolean contemCodigo(JsonNode lista, String codigo) {
        for (JsonNode item : lista) {
            if (codigo.equals(item.path("codigo").asText())) {
                return true;
            }
        }
        return false;
    }

    private int quantidadeNaTelaDeEstoque(int produtoId) throws Exception {
        return json(assertOk(get("/api/produtos/" + produtoId, gerente))).path("quantidadeAtual").asInt();
    }

    private JsonNode caixaAberto(int numero) throws Exception {
        for (JsonNode sessao : json(assertOk(get("/api/caixas", gerente)))) {
            if (sessao.path("numeroCaixa").asInt() == numero && "ABERTO".equals(sessao.path("status").asText())) {
                return sessao;
            }
        }
        throw new AssertionError("Caixa aberto nao encontrado: " + numero);
    }

    /** 11 dígitos únicos desta execução: a base de testes persiste entre classes. */
    private static String sufixoUnico() {
        String tempo = String.valueOf(System.nanoTime());
        return tempo.substring(tempo.length() - 9) + String.format("%02d", SEQUENCIA.incrementAndGet() % 100);
    }

    private static int numeroCaixaUnico() {
        return 100_000 + (int) Math.floorMod(System.nanoTime(), 1_000_000_000L) + SEQUENCIA.incrementAndGet();
    }

    /** Monta um EAN-13 válido a partir de 12 dígitos. */
    private static String ean13(String base) {
        String corpo = (base + "000000000000").substring(0, 12);
        int soma = 0;
        for (int i = 0; i < 12; i++) {
            soma += (corpo.charAt(i) - '0') * (i % 2 == 0 ? 1 : 3);
        }
        return corpo + (10 - soma % 10) % 10;
    }

    private static void assertBig(String esperado, BigDecimal atual) {
        assertEquals(0, new BigDecimal(esperado).compareTo(atual), () -> "esperado " + esperado + " mas foi " + atual);
    }

    private static ResponseEntity<String> assertOk(ResponseEntity<String> resposta) {
        assertTrue(resposta.getStatusCode().is2xxSuccessful(), () -> resposta.getStatusCode() + ": " + resposta.getBody());
        return resposta;
    }

    private JsonNode json(ResponseEntity<String> resposta) throws Exception {
        return objectMapper.readTree(resposta.getBody());
    }

    private ResponseEntity<String> get(String path, Usuario usuario) {
        return restTemplate.exchange(url(path), HttpMethod.GET, new HttpEntity<>(headers(usuario)), String.class);
    }

    private ResponseEntity<String> post(String path, Object body, Usuario usuario) {
        return restTemplate.exchange(url(path), HttpMethod.POST, new HttpEntity<>(body, headers(usuario)), String.class);
    }

    private ResponseEntity<String> postIdempotente(String path, Object body, Usuario usuario, String chave) {
        HttpHeaders headers = headers(usuario);
        headers.set("Idempotency-Key", chave);
        return restTemplate.exchange(url(path), HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
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
}
