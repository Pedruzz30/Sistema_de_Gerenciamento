
package app;

import model.CategoriaCardapio;
import model.CategoriaEstoque;
import model.ItemCardapio;
import model.Produto;
import model.TipoItemCardapio;
import model.Usuario;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import repository.CategoriaCardapioRepository;
import repository.ItemCardapioRepository;
import repository.ProdutoRepository;

import service.EstoqueService;

import java.math.BigDecimal;
import java.util.List;

/**
 * Popula um catálogo demonstrativo para desenvolvimento.
 *
 * Executa somente quando:
 * stockos.seed.demo-catalog.enabled=true
 *
 * Não deve ser utilizado para provisionar clientes reais.
 *
 * Preserva registros existentes e evita duplicações
 * em execuções sequenciais.
 */
@Component
@ConditionalOnProperty(
        name = "stockos.seed.demo-catalog.enabled",
        havingValue = "true"
)
public class DemoCatalogSeedRunner implements CommandLineRunner {

    private static final Logger log =
            LoggerFactory.getLogger(DemoCatalogSeedRunner.class);

    // Códigos estáveis
    private static final String CATEGORIA_PREPARADOS = "preparados";
    private static final String CATEGORIA_BEBIDAS = "bebidas";
    private static final String CODIGO_PRATO = "demo_prato_da_casa";
    private static final String PREFIXO_BEBIDA = "demo_bebida_produto_";

    // Configurações de demonstração
    private static final int ESTOQUE_INICIAL = 30;
    private static final int ESTOQUE_MINIMO = 8;
    private static final int INTERVALO_ORDEM = 10;

    private static final BigDecimal PRECO_PRATO =
            new BigDecimal("29.90");

    private static final List<BebidaDemo> BEBIDAS = List.of(
            new BebidaDemo(
                    "Agua mineral 500ml",
                    new BigDecimal("4.00")
            ),
            new BebidaDemo(
                    "Refrigerante lata 350ml",
                    new BigDecimal("6.50")
            )
    );

    private final EstoqueService estoqueService;
    private final CategoriaCardapioRepository categoriaRepository;
    private final ItemCardapioRepository itemRepository;
    private final ProdutoRepository produtoRepository;
    private final Usuario adminSuperior;

    public DemoCatalogSeedRunner(
            EstoqueService estoqueService,
            CategoriaCardapioRepository categoriaRepository,
            ItemCardapioRepository itemRepository,
            ProdutoRepository produtoRepository,
            Usuario adminSuperior
    ) {
        this.estoqueService = estoqueService;
        this.categoriaRepository = categoriaRepository;
        this.itemRepository = itemRepository;
        this.produtoRepository = produtoRepository;
        this.adminSuperior = adminSuperior;
    }

    @Override
    @Transactional
    public void run(String... args) {

        log.warn("Seed demonstrativo habilitado.");

        CategoriaCardapio preparados = garantirCategoria(
                CATEGORIA_PREPARADOS,
                "Preparados",
                10
        );

        CategoriaCardapio bebidas = garantirCategoria(
                CATEGORIA_BEBIDAS,
                "Bebidas",
                20
        );

        garantirPratoDemo(preparados);
        cadastrarBebidasDemo(bebidas);

        log.info("Verificação do catálogo demonstrativo concluída.");
    }

    private CategoriaCardapio garantirCategoria(
            String codigo,
            String nome,
            int ordem
    ) {
        return categoriaRepository.buscarPorCodigo(codigo)
                .orElseGet(() -> {

                    CategoriaCardapio categoria =
                            new CategoriaCardapio(
                                    0,
                                    codigo,
                                    nome,
                                    ordem,
                                    true
                            );

                    CategoriaCardapio salva =
                            categoriaRepository.salvar(categoria);

                    log.info(
                            "Categoria demonstrativa criada: {}",
                            codigo
                    );

                    return salva;
                });
    }

    private void garantirPratoDemo(CategoriaCardapio categoria) {

        if (itemRepository.buscarPorCodigo(CODIGO_PRATO).isPresent()) {
            return;
        }

        ItemCardapio prato = new ItemCardapio(
                0,
                CODIGO_PRATO,
                "Prato da casa (demo)",
                "Exemplo de item preparado sob demanda; personalize o cardapio.",
                PRECO_PRATO,
                categoria,
                TipoItemCardapio.PREPARADO_SOB_DEMANDA,
                true,
                true,
                10,
                null
        );

        itemRepository.salvar(prato);

        log.info("Prato demonstrativo cadastrado.");
    }

    private void cadastrarBebidasDemo(CategoriaCardapio categoria) {

        int ordem = INTERVALO_ORDEM;

        for (BebidaDemo bebida : BEBIDAS) {

            Produto produto = garantirProdutoDemo(bebida);

            garantirItemBebida(categoria, produto, ordem);

            ordem += INTERVALO_ORDEM;
        }
    }

    private Produto garantirProdutoDemo(BebidaDemo bebida) {

        return produtoRepository.buscarPorNome(bebida.nome())
                .filter(Produto::isControladoPorEstoque)
                .orElseGet(() -> {

                    Produto produto = estoqueService.cadastrarProduto(
                            adminSuperior,
                            bebida.nome(),
                            ESTOQUE_INICIAL,
                            ESTOQUE_MINIMO,
                            bebida.preco(),
                            CategoriaEstoque.BEBIDAS
                    );

                    log.info(
                            "Produto demonstrativo criado: {}",
                            produto.getNome()
                    );

                    return produto;
                });
    }

    private void garantirItemBebida(
            CategoriaCardapio categoria,
            Produto produto,
            int ordem
    ) {

        // Produto já vinculado a outro item do cardápio.
        if (itemRepository
                .buscarPrimeiroPorProdutoVinculadoId(produto.getId())
                .isPresent()) {
            return;
        }

        String codigo = PREFIXO_BEBIDA + produto.getId();

        // Evita cadastrar novamente o mesmo código.
        if (itemRepository.buscarPorCodigo(codigo).isPresent()) {
            return;
        }

        ItemCardapio item = new ItemCardapio(
                0,
                codigo,
                produto.getNome(),
                "Exemplo de venda direta com controle de estoque.",
                produto.getPrecoUnitario(),
                categoria,
                TipoItemCardapio.ESTOQUE_DIRETO,
                true,
                true,
                ordem,
                produto
        );

        itemRepository.salvar(item);

        log.info(
                "Item demonstrativo vinculado ao produto ID: {}",
                produto.getId()
        );
    }

    private record BebidaDemo(
            String nome,
            BigDecimal preco
    ) {
    }
}

