package app;

import model.CategoriaCardapio;
import model.CategoriaEstoque;
import model.ItemCardapio;
import model.ClasseFuncionario;
import model.Permissao;
import model.Produto;
import model.TipoItemCardapio;
import model.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import repository.InMemoryCategoriaCardapioRepository;
import repository.InMemoryItemCardapioRepository;
import repository.InMemoryPedidoRepository;
import repository.InMemoryProdutoRepository;
import service.EstoqueService;

import java.math.BigDecimal;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PizzariaMenuSeedRunnerTest {

    private EstoqueService estoqueService;
    private InMemoryProdutoRepository produtoRepository;
    private InMemoryCategoriaCardapioRepository categoriaCardapioRepository;
    private InMemoryItemCardapioRepository itemCardapioRepository;
    private Usuario adminSuperior;
    private PizzariaMenuSeedRunner runner;

    @BeforeEach
    void setUp() {
        produtoRepository = new InMemoryProdutoRepository();
        categoriaCardapioRepository = new InMemoryCategoriaCardapioRepository();
        itemCardapioRepository = new InMemoryItemCardapioRepository();
        estoqueService = new EstoqueService(produtoRepository, new InMemoryPedidoRepository());

        ClasseFuncionario classeSuperior = new ClasseFuncionario(1, "SUPERIOR", "Acesso total");
        for (Permissao permissao : Permissao.values()) {
            classeSuperior.adicionarPermissao(permissao);
        }

        adminSuperior = new Usuario(
                1,
                "Admin",
                "Seed",
                "529.982.247-25",
                "1234",
                classeSuperior,
                Usuario.Perfil.ADMIN
        );
        runner = new PizzariaMenuSeedRunner(
                estoqueService,
                categoriaCardapioRepository,
                itemCardapioRepository,
                produtoRepository,
                adminSuperior
        );
    }

    @Test
    void run_populaBebidasNoEstoqueECardapioSemDuplicarEmExecucoesSubsequentes() throws Exception {
        runner.run();

        Map<String, Produto> catalogoEstoque = estoqueService.listarProdutos().stream()
                .collect(Collectors.toMap(Produto::getNome, Function.identity()));
        Map<String, CategoriaCardapio> categorias = categoriaCardapioRepository.listarTodos().stream()
                .collect(Collectors.toMap(CategoriaCardapio::getCodigo, Function.identity()));
        Map<String, ItemCardapio> itens = itemCardapioRepository.listarTodos().stream()
                .collect(Collectors.toMap(ItemCardapio::getCodigo, Function.identity()));

        assertEquals(9, catalogoEstoque.size());
        assertEquals(7, categorias.size());
        assertEquals(43, itens.size());
        assertProduto(catalogoEstoque.get("Coca-Cola Zero Lata 350ml"), 30, 8, "6.50", CategoriaEstoque.BEBIDAS);
        assertTrue(categorias.containsKey("entradas"));
        assertTrue(categorias.containsKey("massas_classicas"));
        assertTrue(categorias.containsKey("massas_especiais"));
        assertTrue(categorias.containsKey("pizzas_artesanais"));
        assertTrue(categorias.containsKey("pratos_executivos"));
        assertTrue(categorias.containsKey("sobremesas"));
        assertTrue(categorias.containsKey("bebidas"));
        assertItemSobDemanda(itens.get("bruschetta_pomodori_basilico"), "24.90", "entradas");
        assertItemSobDemanda(itens.get("spaghetti_carbonara"), "44.90", "massas_classicas");
        assertItemSobDemanda(itens.get("nhoque_da_casa"), "47.90", "massas_especiais");
        assertItemSobDemanda(itens.get("pizza_calabresa_broto"), "29.90", "pizzas_artesanais");
        assertItemSobDemanda(itens.get("file_grelhado_fettuccine_manteiga"), "49.90", "pratos_executivos");
        assertItemSobDemanda(itens.get("tiramisu_tradicional"), "21.90", "sobremesas");
        assertItemEstoqueDireto(itens.get("bebida_produto_1"), "6.50", "bebidas");

        runner.run();

        assertEquals(9, estoqueService.listarProdutos().size());
        assertEquals(7, categoriaCardapioRepository.listarTodos().size());
        assertEquals(43, itemCardapioRepository.listarTodos().size());
    }

    @Test
    void run_criaItemDeCardapioParaBebidaPreexistenteSemAlterarProdutoOriginal() throws Exception {
        Produto guarana = estoqueService.cadastrarProduto(
                adminSuperior,
                "Guarana Lata 350ml",
                99,
                1,
                BigDecimal.valueOf(99),
                CategoriaEstoque.FRIO
        );

        runner.run();

        Produto persistido = produtoRepository.buscarPorId(guarana.getId()).orElseThrow();
        ItemCardapio itemCardapio = itemCardapioRepository.buscarPrimeiroPorProdutoVinculadoId(guarana.getId()).orElseThrow();

        assertProduto(persistido, 99, 1, "99.00", CategoriaEstoque.FRIO);
        assertEquals("Guarana Lata 350ml", itemCardapio.getNome());
        assertEquals(TipoItemCardapio.ESTOQUE_DIRETO, itemCardapio.getTipoItem());
        assertEquals(guarana.getId(), itemCardapio.getProdutoVinculado().getId());
    }

    @Test
    void run_reclassificaPizzasLegadasComoSobDemandaSemRemoverHistoricoDoRepositorio() throws Exception {
        Produto pizzaLegada = produtoRepository.salvar(
                new Produto(
                        0,
                        "Pizza Calabresa Broto",
                        99,
                        10,
                        new BigDecimal("29.90"),
                        CategoriaEstoque.CONGELADO
                )
        );

        runner.run();

        Produto pizzaPersistida = produtoRepository.buscarPorId(pizzaLegada.getId()).orElseThrow();
        ItemCardapio pizzaCardapio = itemCardapioRepository.buscarPorCodigo("pizza_calabresa_broto").orElseThrow();

        assertFalse(pizzaPersistida.isControladoPorEstoque());
        assertEquals(10, produtoRepository.listarTodos().size());
        assertEquals(9, estoqueService.listarProdutos().size());
        assertTrue(estoqueService.buscarProdutoPorNome("Pizza Calabresa Broto").isEmpty());
        assertEquals(TipoItemCardapio.PREPARADO_SOB_DEMANDA, pizzaCardapio.getTipoItem());
    }

    private static void assertProduto(Produto produto,
                                      int quantidadeAtual,
                                      int quantidadeMinima,
                                      String preco,
                                      CategoriaEstoque categoriaEstoque) {
        assertEquals(quantidadeAtual, produto.getQuantidadeAtual());
        assertEquals(quantidadeMinima, produto.getQuantidadeMinima());
        assertEquals(0, new BigDecimal(preco).compareTo(produto.getPrecoUnitario()));
        assertEquals(categoriaEstoque, produto.getCategoriaEstoque());
    }

    private static void assertItemSobDemanda(ItemCardapio item,
                                             String preco,
                                             String categoriaCodigo) {
        assertEquals(0, new BigDecimal(preco).compareTo(item.getPrecoVenda()));
        assertEquals(TipoItemCardapio.PREPARADO_SOB_DEMANDA, item.getTipoItem());
        assertEquals(categoriaCodigo, item.getCategoriaCardapio().getCodigo());
        assertTrue(item.getProdutoVinculado() == null);
    }

    private static void assertItemEstoqueDireto(ItemCardapio item,
                                                String preco,
                                                String categoriaCodigo) {
        assertEquals(0, new BigDecimal(preco).compareTo(item.getPrecoVenda()));
        assertEquals(TipoItemCardapio.ESTOQUE_DIRETO, item.getTipoItem());
        assertEquals(categoriaCodigo, item.getCategoriaCardapio().getCodigo());
        assertTrue(item.getProdutoVinculado() != null);
    }
}
