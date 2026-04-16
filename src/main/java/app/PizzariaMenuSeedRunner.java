package app;

import model.CategoriaEstoque;
import model.Usuario;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import service.EstoqueService;

import java.math.BigDecimal;
import java.util.List;

@Component
public class PizzariaMenuSeedRunner implements CommandLineRunner {

    private static final BigDecimal PRECO_BEBIDA_LATA_350 = new BigDecimal("6.50");
    private static final BigDecimal PRECO_AGUA_500 = new BigDecimal("4.00");
    private static final BigDecimal PRECO_SUCO_1L = new BigDecimal("12.00");

    private static final int ESTOQUE_INICIAL_BEBIDA = 30;
    private static final int ESTOQUE_MINIMO_BEBIDA = 8;

    private static final List<MenuSeedItem> CATALOGO_BEBIDAS = List.of(
            new MenuSeedItem(
                    "Coca-Cola Lata 350ml",
                    ESTOQUE_INICIAL_BEBIDA,
                    ESTOQUE_MINIMO_BEBIDA,
                    PRECO_BEBIDA_LATA_350,
                    CategoriaEstoque.BEBIDAS
            ),
            new MenuSeedItem(
                    "Coca-Cola Zero Lata 350ml",
                    ESTOQUE_INICIAL_BEBIDA,
                    ESTOQUE_MINIMO_BEBIDA,
                    PRECO_BEBIDA_LATA_350,
                    CategoriaEstoque.BEBIDAS
            ),
            new MenuSeedItem(
                    "Fanta Laranja Lata 350ml",
                    ESTOQUE_INICIAL_BEBIDA,
                    ESTOQUE_MINIMO_BEBIDA,
                    PRECO_BEBIDA_LATA_350,
                    CategoriaEstoque.BEBIDAS
            ),
            new MenuSeedItem(
                    "Fanta Uva Lata 350ml",
                    ESTOQUE_INICIAL_BEBIDA,
                    ESTOQUE_MINIMO_BEBIDA,
                    PRECO_BEBIDA_LATA_350,
                    CategoriaEstoque.BEBIDAS
            ),
            new MenuSeedItem(
                    "Guarana Lata 350ml",
                    ESTOQUE_INICIAL_BEBIDA,
                    ESTOQUE_MINIMO_BEBIDA,
                    PRECO_BEBIDA_LATA_350,
                    CategoriaEstoque.BEBIDAS
            ),
            new MenuSeedItem(
                    "Agua Sem Gas Garrafa 500ml",
                    ESTOQUE_INICIAL_BEBIDA,
                    ESTOQUE_MINIMO_BEBIDA,
                    PRECO_AGUA_500,
                    CategoriaEstoque.BEBIDAS
            ),
            new MenuSeedItem(
                    "Agua Com Gas Garrafa 500ml",
                    ESTOQUE_INICIAL_BEBIDA,
                    ESTOQUE_MINIMO_BEBIDA,
                    PRECO_AGUA_500,
                    CategoriaEstoque.BEBIDAS
            ),
            new MenuSeedItem(
                    "Suco Uva Garrafa 1L",
                    ESTOQUE_INICIAL_BEBIDA,
                    ESTOQUE_MINIMO_BEBIDA,
                    PRECO_SUCO_1L,
                    CategoriaEstoque.BEBIDAS
            ),
            new MenuSeedItem(
                    "Suco Laranja Garrafa 1L",
                    ESTOQUE_INICIAL_BEBIDA,
                    ESTOQUE_MINIMO_BEBIDA,
                    PRECO_SUCO_1L,
                    CategoriaEstoque.BEBIDAS
            )
    );

    private final EstoqueService estoqueService;
    private final Usuario adminSuperior;

    public PizzariaMenuSeedRunner(EstoqueService estoqueService, Usuario adminSuperior) {
        this.estoqueService = estoqueService;
        this.adminSuperior = adminSuperior;
    }

    @Override
    public void run(String... args) {
        migrarPizzasLegadasParaSobDemanda();
        seedBebidas();
    }

    void migrarPizzasLegadasParaSobDemanda() {
        estoqueService.desativarProdutosPizzaSobDemanda(adminSuperior);
    }

    void seedBebidas() {
        for (MenuSeedItem item : CATALOGO_BEBIDAS) {
            if (estoqueService.buscarProdutoPorNome(item.nome()).isPresent()) {
                continue;
            }

            estoqueService.cadastrarProduto(
                    adminSuperior,
                    item.nome(),
                    item.quantidadeInicial(),
                    item.quantidadeMinima(),
                    item.preco(),
                    item.categoriaEstoque()
            );
        }
    }

    private record MenuSeedItem(
            String nome,
            int quantidadeInicial,
            int quantidadeMinima,
            BigDecimal preco,
            CategoriaEstoque categoriaEstoque
    ) {
    }
}
