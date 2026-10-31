package app;

import model.CategoriaCardapio;
import model.CategoriaEstoque;
import model.ClasseFuncionario;
import model.ItemCardapio;
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

import static org.junit.jupiter.api.Assertions.*;

class DemoCatalogSeedRunnerTest {

    private EstoqueService estoqueService;
    private InMemoryProdutoRepository produtos;
    private InMemoryCategoriaCardapioRepository categorias;
    private InMemoryItemCardapioRepository itens;
    private Usuario admin;
    private DemoCatalogSeedRunner runner;

    @BeforeEach
    void setUp() {
        produtos = new InMemoryProdutoRepository();
        categorias = new InMemoryCategoriaCardapioRepository();
        itens = new InMemoryItemCardapioRepository();
        estoqueService = new EstoqueService(produtos, new InMemoryPedidoRepository());
        ClasseFuncionario superior = new ClasseFuncionario(1, "SUPERIOR", "Acesso total");
        for (Permissao permissao : Permissao.values()) superior.adicionarPermissao(permissao);
        admin = new Usuario(1, "Admin", "Seed", "529.982.247-25",
                "1234", superior, Usuario.Perfil.ADMIN);
        runner = new DemoCatalogSeedRunner(estoqueService, categorias, itens, produtos, admin);
    }

    @Test
    void run_criaExemplosGenericosEIdempotentes() {
        runner.run();
        assertEquals(2, estoqueService.listarProdutos().size());
        assertEquals(2, categorias.listarTodos().size());
        assertEquals(3, itens.listarTodos().size());
        assertTrue(categorias.buscarPorCodigo("preparados").isPresent());
        assertTrue(categorias.buscarPorCodigo("bebidas").isPresent());
        assertTrue(categorias.buscarPorCodigo("pizzas_artesanais").isEmpty());
        ItemCardapio preparado = itens.buscarPorCodigo("demo_prato_da_casa").orElseThrow();
        assertEquals(TipoItemCardapio.PREPARADO_SOB_DEMANDA, preparado.getTipoItem());
        assertNull(preparado.getProdutoVinculado());
        Produto agua = estoqueService.buscarProdutoPorNome("Agua mineral 500ml").orElseThrow();
        ItemCardapio bebida = itens.buscarPrimeiroPorProdutoVinculadoId(agua.getId()).orElseThrow();
        assertEquals(TipoItemCardapio.ESTOQUE_DIRETO, bebida.getTipoItem());

        runner.run();
        assertEquals(2, estoqueService.listarProdutos().size());
        assertEquals(2, categorias.listarTodos().size());
        assertEquals(3, itens.listarTodos().size());
    }

    @Test
    void run_naoSobrescreveCadastroExistenteNemDesativaPizzaControladaPorEstoque() {
        Produto agua = estoqueService.cadastrarProduto(
                admin, "Agua mineral 500ml", 72, 4, new BigDecimal("9.90"), CategoriaEstoque.FRIO);
        Produto pizzaCongelada = estoqueService.cadastrarProduto(
                admin, "Pizza congelada", 12, 3, new BigDecimal("25.00"), CategoriaEstoque.CONGELADO);
        CategoriaCardapio categoriaCustomizada = categorias.salvar(
                new CategoriaCardapio(0, "bebidas", "Bebidas do cliente", 99, Boolean.TRUE)
        );
        runner.run();
        Produto mantido = estoqueService.buscarProdutoPorId(agua.getId()).orElseThrow();
        assertEquals(72, mantido.getQuantidadeAtual());
        assertEquals(0, new BigDecimal("9.90").compareTo(mantido.getPrecoUnitario()));
        assertEquals(CategoriaEstoque.FRIO, mantido.getCategoriaEstoque());
        assertEquals("Bebidas do cliente",
                categorias.buscarPorCodigo("bebidas").orElseThrow().getNomeExibicao());
        assertEquals(categoriaCustomizada.getId(),
                categorias.buscarPorCodigo("bebidas").orElseThrow().getId());
        assertTrue(estoqueService.buscarProdutoPorId(pizzaCongelada.getId()).isPresent());
        assertEquals(3, estoqueService.listarProdutos().size());
    }
}
