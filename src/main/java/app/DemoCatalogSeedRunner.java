package app;

import model.CategoriaCardapio;
import model.CategoriaEstoque;
import model.ItemCardapio;
import model.Produto;
import model.TipoItemCardapio;
import model.Usuario;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import repository.CategoriaCardapioRepository;
import repository.ItemCardapioRepository;
import repository.ProdutoRepository;
import service.EstoqueService;

import java.math.BigDecimal;
import java.util.List;

/**
 * Opt-in sample catalogue for local development and demos.
 *
 * No domain-specific product name is interpreted as "not inventory": whether
 * an item consumes stock is decided by its explicit TipoItemCardapio and linked
 * Produto, never by matching words such as "pizza".
 *
 * This runner never edits an existing product or category. It is NOT a tenant
 * provisioning mechanism and must remain disabled for customer databases.
 */
@Component
@ConditionalOnProperty(name = "stockos.seed.demo-catalog.enabled", havingValue = "true")
public class DemoCatalogSeedRunner implements CommandLineRunner {

    private static final List<BebidaDemo> BEBIDAS = List.of(
            new BebidaDemo("Agua mineral 500ml", new BigDecimal("4.00")),
            new BebidaDemo("Refrigerante lata 350ml", new BigDecimal("6.50"))
    );

    private final EstoqueService estoqueService;
    private final CategoriaCardapioRepository categoriaRepository;
    private final ItemCardapioRepository itemRepository;
    private final ProdutoRepository produtoRepository;
    private final Usuario adminSuperior;

    public DemoCatalogSeedRunner(EstoqueService estoqueService,
                                 CategoriaCardapioRepository categoriaRepository,
                                 ItemCardapioRepository itemRepository,
                                 ProdutoRepository produtoRepository,
                                 Usuario adminSuperior) {
        this.estoqueService = estoqueService;
        this.categoriaRepository = categoriaRepository;
        this.itemRepository = itemRepository;
        this.produtoRepository = produtoRepository;
        this.adminSuperior = adminSuperior;
    }

    @Override
    public void run(String... args) {
        CategoriaCardapio preparados = garantirCategoria("preparados", "Preparados", 10);
        CategoriaCardapio bebidas = garantirCategoria("bebidas", "Bebidas", 20);

        criarPreparadoDemo(preparados);
        int ordem = 10;
        for (BebidaDemo exemplo : BEBIDAS) {
            Produto produto = estoqueService.buscarProdutoPorNome(exemplo.nome()).orElseGet(
                    () -> estoqueService.cadastrarProduto(
                            adminSuperior,
                            exemplo.nome(),
                            30,
                            8,
                            exemplo.preco(),
                            CategoriaEstoque.BEBIDAS
                    )
            );
            vincularProdutoDemo(bebidas, produto, ordem);
            ordem += 10;
        }
    }

    private CategoriaCardapio garantirCategoria(String codigo, String nome, int ordem) {
        return categoriaRepository.buscarPorCodigo(codigo).orElseGet(
                () -> categoriaRepository.salvar(
                        new CategoriaCardapio(0, codigo, nome, ordem, Boolean.TRUE)
                )
        );
    }

    private void criarPreparadoDemo(CategoriaCardapio preparados) {
        if (itemRepository.buscarPorCodigo("demo_prato_da_casa").isPresent()) return;
        itemRepository.salvar(new ItemCardapio(
                0,
                "demo_prato_da_casa",
                "Prato da casa (demo)",
                "Exemplo de item preparado sob demanda; personalize o cardapio.",
                new BigDecimal("29.90"),
                preparados,
                TipoItemCardapio.PREPARADO_SOB_DEMANDA,
                Boolean.TRUE,
                Boolean.TRUE,
                10,
                null
        ));
    }

    private void vincularProdutoDemo(CategoriaCardapio bebidas, Produto produto, int ordem) {
        // Respect existing menus, even if the product is linked elsewhere.
        if (itemRepository.buscarPrimeiroPorProdutoVinculadoId(produto.getId()).isPresent()) return;
        String codigo = "demo_bebida_produto_" + produto.getId();
        if (itemRepository.buscarPorCodigo(codigo).isPresent()) return;
        itemRepository.salvar(new ItemCardapio(
                0,
                codigo,
                produto.getNome(),
                "Exemplo de venda direta com controle de estoque.",
                produto.getPrecoUnitario(),
                bebidas,
                TipoItemCardapio.ESTOQUE_DIRETO,
                Boolean.TRUE,
                Boolean.TRUE,
                ordem,
                produto
        ));
    }

    private record BebidaDemo(String nome, BigDecimal preco) {}
}
