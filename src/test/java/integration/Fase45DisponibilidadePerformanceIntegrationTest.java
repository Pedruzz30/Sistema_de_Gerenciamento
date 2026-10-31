package integration;

import app.Main;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import model.CategoriaCardapio;
import model.CategoriaEstoque;
import model.DimensaoUnidade;
import model.EstoqueProduto;
import model.FichaTecnica;
import model.FichaTecnicaIngrediente;
import model.FichaTecnicaVersao;
import model.GrupoModificador;
import model.ItemCardapio;
import model.OpcaoModificador;
import model.OpcaoModificadorComponente;
import model.PoliticaFalta;
import model.PoliticaFaltaIngrediente;
import model.Produto;
import model.TipoComposicaoVariacao;
import model.TipoEfeitoModificador;
import model.TipoItemCardapio;
import model.TipoSelecaoModificador;
import model.UnidadeMedida;
import model.Usuario;
import model.VariacaoGrupoModificador;
import model.VariacaoItemCardapio;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import repository.CategoriaCardapioRepository;
import repository.EstoqueProdutoRepository;
import repository.FichaTecnicaIngredienteRepository;
import repository.FichaTecnicaRepository;
import repository.FichaTecnicaVersaoRepository;
import repository.GrupoModificadorRepository;
import repository.ItemCardapioRepository;
import repository.OpcaoModificadorComponenteRepository;
import repository.OpcaoModificadorRepository;
import repository.ProdutoRepository;
import repository.UnidadeMedidaRepository;
import repository.UsuarioRepository;
import repository.VariacaoGrupoModificadorRepository;
import repository.VariacaoItemCardapioRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        classes = Main.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.port=0"
)
class Fase45DisponibilidadePerformanceIntegrationTest {

    private static final int TOTAL_ITENS = 100;
    private static final int VARIACOES_POR_ITEM = 3;
    private static final int INGREDIENTES_POR_VARIACAO = 5;
    private static final int TOTAL_GRUPOS = 30;
    private static final int OPCOES_POR_GRUPO = 5;
    private static final int COMPONENTES_POR_OPCAO = 2;

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
    private UnidadeMedidaRepository unidadeRepository;

    @Autowired
    private CategoriaCardapioRepository categoriaRepository;

    @Autowired
    private ItemCardapioRepository itemRepository;

    @Autowired
    private VariacaoItemCardapioRepository variacaoRepository;

    @Autowired
    private FichaTecnicaRepository fichaRepository;

    @Autowired
    private FichaTecnicaVersaoRepository versaoRepository;

    @Autowired
    private FichaTecnicaIngredienteRepository ingredienteRepository;

    @Autowired
    private GrupoModificadorRepository grupoRepository;

    @Autowired
    private OpcaoModificadorRepository opcaoRepository;

    @Autowired
    private OpcaoModificadorComponenteRepository componenteRepository;

    @Autowired
    private VariacaoGrupoModificadorRepository associacaoRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Usuario gerente;

    @BeforeEach
    void setUp() {
        gerente = usuarioRepository.listarTodos().stream()
                .filter(usuario -> "52998224725".equals(usuario.getCpfBruto()))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void consultaEmLotePossuiMedicaoObjetivaComCargaRepresentativa() throws Exception {
        CargaRepresentativa carga = prepararCargaRepresentativa("f45_perf_" + System.nanoTime());

        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        Statistics statistics = sessionFactory.getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        long memoriaAntes = memoriaUsada();
        long inicio = System.nanoTime();

        JsonNode resposta = objectMapper.readTree(post(
                "/api/cardapio/disponibilidade/consultar",
                Map.of(
                        "filialId", 1,
                        "canal", "BALCAO",
                        "variacaoIds", carga.variacaoIds()
                )
        ).getBody());

        long tempoMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio);
        long memoriaDepois = memoriaUsada();
        long queries = statistics.getPrepareStatementCount();
        long entidadesCarregadas = statistics.getEntityLoadCount();
        long variacoesDaCarga = contarVariacoesDaCarga(resposta.path("variacoes"), carga.variacaoIds());

        System.out.printf(
                "Fase 4.5 disponibilidade lote: itens=%d, variacoes=%d, ingredientes=%d, grupos=%d, opcoes=%d, componentes=%d, queries=%d, tempoMs=%d, entidades=%d, memoriaDeltaKb=%d%n",
                TOTAL_ITENS,
                TOTAL_ITENS * VARIACOES_POR_ITEM,
                TOTAL_ITENS * VARIACOES_POR_ITEM * INGREDIENTES_POR_VARIACAO,
                TOTAL_GRUPOS,
                TOTAL_GRUPOS * OPCOES_POR_GRUPO,
                TOTAL_GRUPOS * OPCOES_POR_GRUPO * COMPONENTES_POR_OPCAO,
                queries,
                tempoMs,
                entidadesCarregadas,
                (memoriaDepois - memoriaAntes) / 1024
        );

        assertEquals(TOTAL_ITENS * VARIACOES_POR_ITEM, variacoesDaCarga);
        assertTrue(queries > 0, "A medicao de queries do Hibernate precisa estar ativa.");
        assertTrue(queries <= 1300,
                "Consulta em lote excedeu o limite de regressao medido para a carga representativa: " + queries);
        assertTrue(tempoMs >= 0, "Tempo observado invalido.");
    }

    private CargaRepresentativa prepararCargaRepresentativa(String sufixo) {
        // A fresh test database may not have any measurement units seeded.
        UnidadeMedida grama = unidadeRepository.buscarPorCodigo("GRAMA")
                .orElseGet(() -> unidadeRepository.salvar(
                        new UnidadeMedida(0, "GRAMA", "Grama", DimensaoUnidade.MASSA, BigDecimal.ONE, true)));
        UnidadeMedida unidade = unidadeRepository.buscarPorCodigo("UNIDADE")
                .orElseGet(() -> unidadeRepository.salvar(
                        new UnidadeMedida(0, "UNIDADE", "Unidade", DimensaoUnidade.CONTAGEM, BigDecimal.ONE, true)));
        List<Produto> produtos = criarProdutos(sufixo, grama);
        List<GrupoModificador> grupos = criarGruposComOpcoes(sufixo, grama, produtos);
        CategoriaCardapio categoria = categoriaRepository.salvar(new CategoriaCardapio(
                0,
                "CAT_" + sufixo,
                "Categoria " + sufixo,
                1,
                true
        ));

        List<Long> variacaoIds = new ArrayList<>();
        for (int itemIndex = 0; itemIndex < TOTAL_ITENS; itemIndex++) {
            ItemCardapio item = itemRepository.salvar(new ItemCardapio(
                    0,
                    "ITEM_" + itemIndex + "_" + sufixo,
                    "Item Performance " + itemIndex + " " + sufixo,
                    "Item de carga Fase 4.5",
                    new BigDecimal("35.00"),
                    categoria,
                    TipoItemCardapio.PREPARADO_SOB_DEMANDA,
                    true,
                    true,
                    itemIndex,
                    null
            ));
            for (int variacaoIndex = 0; variacaoIndex < VARIACOES_POR_ITEM; variacaoIndex++) {
                VariacaoItemCardapio variacao = variacaoRepository.salvar(new VariacaoItemCardapio(
                        0,
                        item,
                        "Variacao " + variacaoIndex,
                        "VAR_" + itemIndex + "_" + variacaoIndex + "_" + sufixo,
                        new BigDecimal("35.00"),
                        TipoComposicaoVariacao.PREPARADO,
                        null,
                        true,
                        variacaoIndex == 0,
                        variacaoIndex
                ));
                criarFichaPublicada(variacao, unidade, grama, produtos, itemIndex, variacaoIndex);
                associacaoRepository.salvar(new VariacaoGrupoModificador(
                        0,
                        variacao,
                        grupos.get((itemIndex * VARIACOES_POR_ITEM + variacaoIndex) % grupos.size()),
                        true,
                        1,
                        5,
                        variacaoIndex,
                        true
                ));
                variacaoIds.add(variacao.getId());
            }
        }
        return new CargaRepresentativa(variacaoIds);
    }

    private List<Produto> criarProdutos(String sufixo, UnidadeMedida grama) {
        List<Produto> produtos = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Produto produto = produtoRepository.salvar(new Produto(
                    0,
                    "Produto Performance " + i + " " + sufixo,
                    0,
                    0,
                    BigDecimal.ZERO,
                    CategoriaEstoque.SECO,
                    true,
                    grama,
                    PoliticaFalta.BLOQUEAR,
                    true
            ));
            estoqueRepository.salvar(new EstoqueProduto(
                    0,
                    1L,
                    produto,
                    new BigDecimal("1000000.000000"),
                    BigDecimal.ZERO,
                    new BigDecimal("0.01000000")
            ));
            produtos.add(produto);
        }
        return produtos;
    }

    private List<GrupoModificador> criarGruposComOpcoes(String sufixo, UnidadeMedida grama, List<Produto> produtos) {
        List<GrupoModificador> grupos = new ArrayList<>();
        for (int grupoIndex = 0; grupoIndex < TOTAL_GRUPOS; grupoIndex++) {
            GrupoModificador grupo = grupoRepository.salvar(new GrupoModificador(
                    0,
                    "Grupo Performance " + grupoIndex + " " + sufixo,
                    "Descricao nao operacional",
                    "GRUPO_" + grupoIndex + "_" + sufixo,
                    TipoSelecaoModificador.MULTIPLA,
                    true,
                    1,
                    5,
                    true,
                    true,
                    grupoIndex,
                    Long.valueOf(gerente.getRu())
            ));
            grupos.add(grupo);
            for (int opcaoIndex = 0; opcaoIndex < OPCOES_POR_GRUPO; opcaoIndex++) {
                OpcaoModificador opcao = opcaoRepository.salvar(new OpcaoModificador(
                        0,
                        grupo,
                        "Opcao Performance " + grupoIndex + "." + opcaoIndex,
                        "Descricao nao operacional",
                        "OPCAO_" + grupoIndex + "_" + opcaoIndex + "_" + sufixo,
                        new BigDecimal("1.00"),
                        5,
                        opcaoIndex == 0,
                        true,
                        opcaoIndex,
                        Long.valueOf(gerente.getRu())
                ));
                for (int componenteIndex = 0; componenteIndex < COMPONENTES_POR_OPCAO; componenteIndex++) {
                    Produto produto = produtos.get((grupoIndex + opcaoIndex + componenteIndex) % produtos.size());
                    componenteRepository.salvar(new OpcaoModificadorComponente(
                            0,
                            opcao,
                            TipoEfeitoModificador.ADICIONAR,
                            null,
                            produto,
                            new BigDecimal("5.000000"),
                            grama,
                            null,
                            PoliticaFaltaIngrediente.HERDAR_PRODUTO,
                            null,
                            componenteIndex
                    ));
                }
            }
        }
        return grupos;
    }

    private void criarFichaPublicada(VariacaoItemCardapio variacao,
                                     UnidadeMedida unidade,
                                     UnidadeMedida grama,
                                     List<Produto> produtos,
                                     int itemIndex,
                                     int variacaoIndex) {
        FichaTecnica ficha = fichaRepository.salvar(new FichaTecnica(
                0,
                variacao,
                "Ficha Performance " + itemIndex + "." + variacaoIndex,
                Long.valueOf(gerente.getRu())
        ));
        FichaTecnicaVersao versao = versaoRepository.salvar(new FichaTecnicaVersao(
                0,
                ficha,
                1,
                BigDecimal.ONE,
                unidade,
                null,
                10,
                null,
                null,
                Long.valueOf(gerente.getRu())
        ));
        for (int ingredienteIndex = 0; ingredienteIndex < INGREDIENTES_POR_VARIACAO; ingredienteIndex++) {
            Produto produto = produtos.get((itemIndex + variacaoIndex + ingredienteIndex) % produtos.size());
            ingredienteRepository.salvar(new FichaTecnicaIngrediente(
                    0,
                    versao,
                    produto,
                    grama,
                    new BigDecimal("10.000000"),
                    null,
                    null,
                    new BigDecimal("10.000000"),
                    true,
                    PoliticaFaltaIngrediente.HERDAR_PRODUTO,
                    ingredienteIndex,
                    null
            ));
        }
        versao.publicar(Long.valueOf(gerente.getRu()), "Benchmark Fase 4.5");
        versao = versaoRepository.salvar(versao);
        ficha.definirVersaoPublicadaAtual(versao);
        fichaRepository.salvar(ficha);
    }

    private long contarVariacoesDaCarga(JsonNode variacoes, List<Long> variacaoIds) {
        Set<Long> esperadas = new HashSet<>(variacaoIds);
        long total = 0;
        for (JsonNode variacao : variacoes) {
            if (esperadas.contains(variacao.path("variacaoId").asLong())) {
                total++;
            }
        }
        return total;
    }

    private long memoriaUsada() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
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

    private record CargaRepresentativa(List<Long> variacaoIds) {
    }
}
