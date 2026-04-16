package app;

import model.CategoriaEstoque;
import model.ClasseFuncionario;
import model.Permissao;
import model.Produto;
import model.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
    private Usuario adminSuperior;
    private PizzariaMenuSeedRunner runner;

    @BeforeEach
    void setUp() {
        produtoRepository = new InMemoryProdutoRepository();
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
        runner = new PizzariaMenuSeedRunner(estoqueService, adminSuperior);
    }

    @Test
    void run_populaCatalogoInicialDeBebidasSemDuplicarEmExecucoesSubsequentes() throws Exception {
        runner.run();

        Map<String, Produto> catalogo = estoqueService.listarProdutos().stream()
                .collect(Collectors.toMap(Produto::getNome, Function.identity()));

        assertEquals(9, catalogo.size());
        assertProduto(catalogo.get("Coca-Cola Zero Lata 350ml"), 30, 8, "6.50", CategoriaEstoque.BEBIDAS);
        assertProduto(catalogo.get("Agua Com Gas Garrafa 500ml"), 30, 8, "4.00", CategoriaEstoque.BEBIDAS);
        assertProduto(catalogo.get("Suco Uva Garrafa 1L"), 30, 8, "12.00", CategoriaEstoque.BEBIDAS);
        assertTrue(catalogo.keySet().stream().noneMatch(nome -> nome.startsWith("Pizza ")));

        runner.run();

        assertEquals(9, estoqueService.listarProdutos().size());
        assertEquals(1, estoqueService.listarProdutos().stream()
                .filter(produto -> "Coca-Cola Zero Lata 350ml".equals(produto.getNome()))
                .count());
    }

    @Test
    void run_criaApenasBebidasAusentesSemAlterarProdutoPreexistente() throws Exception {
        estoqueService.cadastrarProduto(
                adminSuperior,
                "Guarana Lata 350ml",
                99,
                1,
                BigDecimal.valueOf(99),
                CategoriaEstoque.FRIO
        );

        runner.run();

        Map<String, Produto> catalogo = estoqueService.listarProdutos().stream()
                .collect(Collectors.toMap(Produto::getNome, Function.identity()));

        assertEquals(9, catalogo.size());
        assertProduto(catalogo.get("Guarana Lata 350ml"), 99, 1, "99.00", CategoriaEstoque.FRIO);
        assertTrue(catalogo.containsKey("Coca-Cola Lata 350ml"));
        assertTrue(catalogo.keySet().stream().noneMatch(nome -> nome.startsWith("Pizza ")));
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
        assertFalse(pizzaPersistida.isControladoPorEstoque());
        assertEquals(10, produtoRepository.listarTodos().size());
        assertEquals(9, estoqueService.listarProdutos().size());
        assertTrue(estoqueService.buscarProdutoPorNome("Pizza Calabresa Broto").isEmpty());
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
}
