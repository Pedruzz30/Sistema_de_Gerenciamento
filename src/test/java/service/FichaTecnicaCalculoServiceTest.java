package service;

import model.CategoriaCardapio;
import model.DimensaoUnidade;
import model.EstoqueProduto;
import model.FichaTecnica;
import model.FichaTecnicaIngrediente;
import model.FichaTecnicaVersao;
import model.ItemCardapio;
import model.PoliticaFalta;
import model.PoliticaFaltaIngrediente;
import model.Produto;
import model.TipoComposicaoVariacao;
import model.TipoItemCardapio;
import model.UnidadeMedida;
import model.VariacaoItemCardapio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import repository.EstoqueProdutoRepository;
import repository.FichaTecnicaIngredienteRepository;
import repository.InMemoryEstoqueProdutoRepository;
import repository.InMemoryLogRepository;
import repository.InMemoryProdutoRepository;
import repository.InMemoryUnidadeMedidaRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FichaTecnicaCalculoServiceTest {

    private UnidadeMedidaService unidadeService;
    private InMemoryProdutoRepository produtoRepository;
    private UnidadeMedida grama;
    private UnidadeMedida quilograma;
    private UnidadeMedida mililitro;
    private UnidadeMedida litro;
    private UnidadeMedida unidade;

    @BeforeEach
    void setUp() {
        InMemoryUnidadeMedidaRepository unidadeRepository = new InMemoryUnidadeMedidaRepository();
        unidadeService = new UnidadeMedidaService(unidadeRepository);
        unidadeService.garantirUnidadesPadrao();
        produtoRepository = new InMemoryProdutoRepository();

        grama = unidadeService.buscarPorCodigoObrigatorio("GRAMA");
        quilograma = unidadeService.buscarPorCodigoObrigatorio("QUILOGRAMA");
        mililitro = unidadeService.buscarPorCodigoObrigatorio("MILILITRO");
        litro = unidadeService.buscarPorCodigoObrigatorio("LITRO");
        unidade = unidadeService.buscarPorCodigoObrigatorio("UNIDADE");
    }

    @Test
    void conversaoKgParaGramaEmIngrediente() {
        Produto mussarela = produto("Mussarela", grama);
        FichaTecnicaIngredienteService service = ingredienteService();

        FichaTecnicaIngredienteService.IngredienteCalculado calculado = service.calcularIngrediente(
                mussarela.getId(),
                quilograma.getId(),
                new BigDecimal("0.180"),
                false,
                null
        );

        assertBigDecimalEquals("180.000000", calculado.quantidadeBrutaBase());
    }

    @Test
    void conversaoLitroParaMililitro() {
        Produto molho = produto("Molho", mililitro);

        FichaTecnicaIngredienteService.IngredienteCalculado calculado = ingredienteService().calcularIngrediente(
                molho.getId(),
                litro.getId(),
                new BigDecimal("0.120"),
                false,
                null
        );

        assertBigDecimalEquals("120.000000", calculado.quantidadeBrutaBase());
    }

    @Test
    void bloqueiaDimensaoIncompativel() {
        Produto farinha = produto("Farinha", grama);

        ErroNegocioException erro = assertThrows(ErroNegocioException.class, () -> ingredienteService().calcularIngrediente(
                farinha.getId(),
                litro.getId(),
                new BigDecimal("1"),
                false,
                null
        ));

        assertEquals("UNIDADE_INCOMPATIVEL", erro.getCodigo());
    }

    @Test
    void quantidadeLiquidaComPerdaGeraQuantidadeBruta() {
        Produto cebola = produto("Cebola", grama);

        FichaTecnicaIngredienteService.IngredienteCalculado calculado = ingredienteService().calcularIngrediente(
                cebola.getId(),
                grama.getId(),
                new BigDecimal("180"),
                true,
                new BigDecimal("5")
        );

        assertBigDecimalEquals("180.000000", calculado.quantidadeLiquidaBase());
        assertBigDecimalEquals("5.0000", calculado.percentualPerda());
        assertBigDecimalEquals("189.473684", calculado.quantidadeBrutaBase());
    }

    @Test
    void perdaZeroMantemQuantidadeBrutaIgualLiquidaEPerdaProximaDe100Bloqueia() {
        Produto cebola = produto("Cebola", grama);

        FichaTecnicaIngredienteService.IngredienteCalculado calculado = ingredienteService().calcularIngrediente(
                cebola.getId(),
                grama.getId(),
                new BigDecimal("180"),
                true,
                BigDecimal.ZERO
        );
        assertBigDecimalEquals("180.000000", calculado.quantidadeBrutaBase());

        ErroNegocioException erro = assertThrows(ErroNegocioException.class, () -> ingredienteService().calcularIngrediente(
                cebola.getId(),
                grama.getId(),
                new BigDecimal("180"),
                true,
                new BigDecimal("100")
        ));
        assertEquals("PERCENTUAL_PERDA_INVALIDO", erro.getCodigo());
    }

    @Test
    void calculaCustoTotalCmvMargemEPrecoSugeridoDaPizza() {
        Produto massa = produto("Massa pronta", grama);
        Produto molho = produto("Molho", mililitro);
        Produto mussarela = produto("Mussarela", grama);
        Produto calabresa = produto("Calabresa", grama);
        Produto cebola = produto("Cebola", grama);

        FichaTecnicaVersao versao = versaoPizza();
        FakeIngredienteRepository ingredienteRepository = new FakeIngredienteRepository(List.of(
                ingrediente(1, versao, massa, grama, "350", "350"),
                ingrediente(2, versao, molho, mililitro, "120", "120"),
                ingrediente(3, versao, mussarela, grama, "190", "190"),
                ingrediente(4, versao, calabresa, grama, "150", "150"),
                ingrediente(5, versao, cebola, grama, "30", "30")
        ));
        EstoqueProdutoRepository estoqueRepository = estoqueComCustos(
                massa, "0.00628571",
                molho, "0.00916667",
                mussarela, "0.04000000",
                calabresa, "0.03000000",
                cebola, "0.01000000"
        );
        CalculadoraCustoFichaTecnicaService calculadora =
                new CalculadoraCustoFichaTecnicaService(ingredienteRepository, estoqueRepository);

        CustoFichaTecnicaResultado resultado = calculadora.calcular(versao, 1L, new BigDecimal("55.00"));

        assertTrue(resultado.custoCompleto());
        assertBigDecimalEquals("15.7000", resultado.custoTotal());
        assertBigDecimalEquals("15.7000", resultado.custoPorUnidadeRendimento());
        assertBigDecimalEquals("28.5455", resultado.cmvPercentual());
        assertBigDecimalEquals("39.3000", resultado.margemBrutaEstimada());

        SimulacaoPrecoFichaTecnicaService simulacao = new SimulacaoPrecoFichaTecnicaService(calculadora);
        SimulacaoPrecoFichaTecnicaService.ResultadoSimulacao simulado =
                simulacao.simular(versao, 1L, new BigDecimal("55.00"), new BigDecimal("35"));
        assertBigDecimalEquals("44.8571", simulado.precoSugeridoMatematico());
    }

    @Test
    void produtoSemCustoMedioGeraPendenciaECalculoIncompleto() {
        Produto mussarela = produto("Mussarela", grama);
        FichaTecnicaVersao versao = versaoPizza();
        FakeIngredienteRepository ingredienteRepository = new FakeIngredienteRepository(List.of(
                ingrediente(1, versao, mussarela, grama, "190", "190")
        ));
        CalculadoraCustoFichaTecnicaService calculadora =
                new CalculadoraCustoFichaTecnicaService(ingredienteRepository, new InMemoryEstoqueProdutoRepository());

        CustoFichaTecnicaResultado resultado = calculadora.calcular(versao, 1L, new BigDecimal("55.00"));

        assertFalse(resultado.custoCompleto());
        assertEquals(1, resultado.ingredientesPendentes().size());
        assertEquals("CUSTO_MEDIO_INDISPONIVEL", resultado.ingredientesPendentes().get(0).motivo());
    }

    @Test
    void versaoPublicadaEImutavelNoDominio() {
        FichaTecnicaVersao versao = versaoPizza();
        versao.publicar(1L, null);

        assertThrows(IllegalStateException.class, () -> versao.atualizarRascunho(
                BigDecimal.ONE,
                unidade,
                null,
                null,
                null,
                null
        ));
    }

    private FichaTecnicaIngredienteService ingredienteService() {
        return new FichaTecnicaIngredienteService(
                null,
                null,
                produtoRepository,
                unidadeService,
                new InMemoryLogRepository()
        );
    }

    private Produto produto(String nome, UnidadeMedida unidadeBase) {
        return produtoRepository.salvar(new Produto(
                0,
                nome + " " + System.nanoTime(),
                0,
                0,
                BigDecimal.ZERO,
                null,
                Boolean.TRUE,
                unidadeBase,
                PoliticaFalta.BLOQUEAR,
                Boolean.TRUE
        ));
    }

    private FichaTecnicaVersao versaoPizza() {
        CategoriaCardapio categoria = new CategoriaCardapio(1, "pizzas", "Pizzas", 1, true);
        ItemCardapio item = new ItemCardapio(
                1,
                "pizza_calabresa",
                "Pizza Calabresa",
                "Pizza",
                new BigDecimal("55.00"),
                categoria,
                TipoItemCardapio.PREPARADO_SOB_DEMANDA,
                true,
                true,
                1,
                null
        );
        VariacaoItemCardapio variacao = new VariacaoItemCardapio(
                1,
                item,
                "Grande",
                "GRANDE",
                new BigDecimal("55.00"),
                TipoComposicaoVariacao.PREPARADO,
                null,
                true,
                true,
                1
        );
        FichaTecnica ficha = new FichaTecnica(1, variacao, "Pizza Calabresa Grande", 1L);
        return new FichaTecnicaVersao(
                1,
                ficha,
                1,
                BigDecimal.ONE,
                unidade,
                null,
                20,
                null,
                null,
                1L
        );
    }

    private FichaTecnicaIngrediente ingrediente(long id,
                                                FichaTecnicaVersao versao,
                                                Produto produto,
                                                UnidadeMedida unidadeInformada,
                                                String quantidadeInformada,
                                                String quantidadeBrutaBase) {
        return new FichaTecnicaIngrediente(
                id,
                versao,
                produto,
                unidadeInformada,
                new BigDecimal(quantidadeInformada),
                null,
                null,
                new BigDecimal(quantidadeBrutaBase),
                false,
                PoliticaFaltaIngrediente.HERDAR_PRODUTO,
                (int) id,
                null
        );
    }

    private EstoqueProdutoRepository estoqueComCustos(Object... paresProdutoCusto) {
        InMemoryEstoqueProdutoRepository repo = new InMemoryEstoqueProdutoRepository();
        for (int i = 0; i < paresProdutoCusto.length; i += 2) {
            Produto produto = (Produto) paresProdutoCusto[i];
            String custo = (String) paresProdutoCusto[i + 1];
            repo.salvar(new EstoqueProduto(
                    0,
                    1L,
                    produto,
                    new BigDecimal("10000"),
                    BigDecimal.ZERO,
                    new BigDecimal(custo)
            ));
        }
        return repo;
    }

    private static void assertBigDecimalEquals(String esperado, BigDecimal atual) {
        assertEquals(0, new BigDecimal(esperado).compareTo(atual), "Esperado " + esperado + ", atual " + atual);
    }

    private static class FakeIngredienteRepository implements FichaTecnicaIngredienteRepository {
        private final List<FichaTecnicaIngrediente> ingredientes = new ArrayList<>();

        FakeIngredienteRepository(List<FichaTecnicaIngrediente> ingredientes) {
            this.ingredientes.addAll(ingredientes);
        }

        @Override
        public FichaTecnicaIngrediente salvar(FichaTecnicaIngrediente ingrediente) {
            ingredientes.removeIf(i -> i.getId() == ingrediente.getId());
            ingredientes.add(ingrediente);
            return ingrediente;
        }

        @Override
        public Optional<FichaTecnicaIngrediente> buscarPorId(long id) {
            return ingredientes.stream().filter(i -> i.getId() == id).findFirst();
        }

        @Override
        public List<FichaTecnicaIngrediente> listarPorVersao(long fichaTecnicaVersaoId) {
            return ingredientes.stream()
                    .filter(i -> i.getFichaTecnicaVersao().getId() == fichaTecnicaVersaoId)
                    .toList();
        }

        @Override
        public List<FichaTecnicaIngrediente> listarPorVersoes(List<Long> fichaTecnicaVersaoIds) {
            return ingredientes.stream()
                    .filter(i -> fichaTecnicaVersaoIds.contains(i.getFichaTecnicaVersao().getId()))
                    .toList();
        }

        @Override
        public void remover(FichaTecnicaIngrediente ingrediente) {
            ingredientes.removeIf(i -> i.getId() == ingrediente.getId());
        }
    }
}
