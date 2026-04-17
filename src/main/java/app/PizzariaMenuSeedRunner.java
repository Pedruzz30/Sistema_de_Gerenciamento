package app;

import model.CategoriaCardapio;
import model.CategoriaEstoque;
import model.ItemCardapio;
import model.Produto;
import model.TipoItemCardapio;
import model.Usuario;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import repository.CategoriaCardapioRepository;
import repository.ItemCardapioRepository;
import repository.ProdutoRepository;
import service.EstoqueService;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class PizzariaMenuSeedRunner implements CommandLineRunner {

    private static final BigDecimal PRECO_BROTO = new BigDecimal("29.90");
    private static final BigDecimal PRECO_GRANDE = new BigDecimal("49.90");
    private static final BigDecimal PRECO_BEBIDA_LATA_350 = new BigDecimal("6.50");
    private static final BigDecimal PRECO_AGUA_500 = new BigDecimal("4.00");
    private static final BigDecimal PRECO_SUCO_1L = new BigDecimal("12.00");

    private static final int ESTOQUE_INICIAL_BEBIDA = 30;
    private static final int ESTOQUE_MINIMO_BEBIDA = 8;

    private static final List<CategoriaSeed> CATEGORIAS_CARDAPIO = List.of(
            new CategoriaSeed("entradas", "Entradas", 10),
            new CategoriaSeed("massas_classicas", "Massas classicas", 20),
            new CategoriaSeed("massas_especiais", "Massas especiais / pratos da casa", 30),
            new CategoriaSeed("pizzas_artesanais", "Pizzas artesanais", 40),
            new CategoriaSeed("pratos_executivos", "Pratos executivos / almoco", 50),
            new CategoriaSeed("sobremesas", "Sobremesas", 60),
            new CategoriaSeed("bebidas", "Bebidas", 70)
    );

    private static final List<MenuSeedItem> CATALOGO_BEBIDAS = List.of(
            new MenuSeedItem("Coca-Cola Lata 350ml", ESTOQUE_INICIAL_BEBIDA, ESTOQUE_MINIMO_BEBIDA, PRECO_BEBIDA_LATA_350, CategoriaEstoque.BEBIDAS),
            new MenuSeedItem("Coca-Cola Zero Lata 350ml", ESTOQUE_INICIAL_BEBIDA, ESTOQUE_MINIMO_BEBIDA, PRECO_BEBIDA_LATA_350, CategoriaEstoque.BEBIDAS),
            new MenuSeedItem("Fanta Laranja Lata 350ml", ESTOQUE_INICIAL_BEBIDA, ESTOQUE_MINIMO_BEBIDA, PRECO_BEBIDA_LATA_350, CategoriaEstoque.BEBIDAS),
            new MenuSeedItem("Fanta Uva Lata 350ml", ESTOQUE_INICIAL_BEBIDA, ESTOQUE_MINIMO_BEBIDA, PRECO_BEBIDA_LATA_350, CategoriaEstoque.BEBIDAS),
            new MenuSeedItem("Guarana Lata 350ml", ESTOQUE_INICIAL_BEBIDA, ESTOQUE_MINIMO_BEBIDA, PRECO_BEBIDA_LATA_350, CategoriaEstoque.BEBIDAS),
            new MenuSeedItem("Agua Sem Gas Garrafa 500ml", ESTOQUE_INICIAL_BEBIDA, ESTOQUE_MINIMO_BEBIDA, PRECO_AGUA_500, CategoriaEstoque.BEBIDAS),
            new MenuSeedItem("Agua Com Gas Garrafa 500ml", ESTOQUE_INICIAL_BEBIDA, ESTOQUE_MINIMO_BEBIDA, PRECO_AGUA_500, CategoriaEstoque.BEBIDAS),
            new MenuSeedItem("Suco Uva Garrafa 1L", ESTOQUE_INICIAL_BEBIDA, ESTOQUE_MINIMO_BEBIDA, PRECO_SUCO_1L, CategoriaEstoque.BEBIDAS),
            new MenuSeedItem("Suco Laranja Garrafa 1L", ESTOQUE_INICIAL_BEBIDA, ESTOQUE_MINIMO_BEBIDA, PRECO_SUCO_1L, CategoriaEstoque.BEBIDAS)
    );

    private static final List<ItemCardapioSeed> ENTRADAS = List.of(
            new ItemCardapioSeed("bruschetta_pomodori_basilico", "Bruschetta pomodori e basilico", "Pao italiano tostado com tomate confit, manjericao fresco e azeite extravirgem.", new BigDecimal("24.90"), 10),
            new ItemCardapioSeed("polenta_cremosa_ragu", "Polenta cremosa com ragu", "Polenta de cozimento lento finalizada com ragu de carne e toque de parmesao.", new BigDecimal("31.90"), 20),
            new ItemCardapioSeed("carpaccio_file_mostarda", "Carpaccio de file ao molho de mostarda", "Laminas finas de file bovino, molho de mostarda suave, alcaparras e parmesao.", new BigDecimal("36.90"), 30),
            new ItemCardapioSeed("burrata_pesto_tomates_assados", "Burrata com pesto e tomates assados", "Burrata fresca servida com pesto de manjericao, tomates assados e pao da casa.", new BigDecimal("39.90"), 40)
    );

    private static final List<ItemCardapioSeed> MASSAS_CLASSICAS = List.of(
            new ItemCardapioSeed("spaghetti_carbonara", "Spaghetti a Carbonara", "Massa al dente envolvida em creme de gemas, pancetta crocante e pecorino.", new BigDecimal("44.90"), 10),
            new ItemCardapioSeed("tagliatelle_bolonhesa", "Tagliatelle a Bolonhesa", "Tagliatelle fresco com molho bolonhesa artesanal cozido lentamente.", new BigDecimal("42.90"), 20),
            new ItemCardapioSeed("penne_amatriciana", "Penne all'Amatriciana", "Penne ao molho de tomates italianos, bacon, cebola roxa e toque picante.", new BigDecimal("41.90"), 30),
            new ItemCardapioSeed("fettuccine_alfredo", "Fettuccine Alfredo", "Fettuccine na manteiga, creme de leite fresco e parmesao maturado.", new BigDecimal("40.90"), 40),
            new ItemCardapioSeed("spaghetti_pesto_genovese", "Spaghetti ao pesto genovese", "Spaghetti finalizado com pesto de manjericao, castanhas e parmesao.", new BigDecimal("43.90"), 50)
    );

    private static final List<ItemCardapioSeed> MASSAS_ESPECIAIS = List.of(
            new ItemCardapioSeed("nhoque_da_casa", "Nhoque da Casa", "Nhoque artesanal de batata ao molho pomodoro, stracciatella e manjericao.", new BigDecimal("47.90"), 10),
            new ItemCardapioSeed("carpelletti_funghi", "Carpelletti ao molho funghi", "Carpelletti recheado servido com molho cremoso de funghi secchi e vinho branco.", new BigDecimal("52.90"), 20),
            new ItemCardapioSeed("fettuccine_file_bacon", "Fettuccine com file e bacon", "Fettuccine fresco com tiras de file mignon, bacon crocante e molho roti aveludado.", new BigDecimal("56.90"), 30),
            new ItemCardapioSeed("ravioli_ricota_espinafre", "Ravioli de ricota e espinafre", "Ravioli artesanal servido com manteiga de salvia e lascas de parmesao.", new BigDecimal("49.90"), 40),
            new ItemCardapioSeed("lasanha_bolonhesa_artesanal", "Lasanha bolonhesa artesanal", "Camadas de massa fresca, molho bolonhesa, bechamel e gratinado de queijos.", new BigDecimal("51.90"), 50)
    );

    private static final List<ItemCardapioSeed> PRATOS_EXECUTIVOS = List.of(
            new ItemCardapioSeed("file_grelhado_fettuccine_manteiga", "File grelhado com fettuccine na manteiga", "Medalhoes de file, fettuccine delicado na manteiga e legumes salteados.", new BigDecimal("49.90"), 10),
            new ItemCardapioSeed("frango_ervas_penne_pomodoro", "Frango grelhado com penne ao pomodoro", "Peito de frango nas ervas, penne ao pomodoro e legumes da estacao.", new BigDecimal("41.90"), 20),
            new ItemCardapioSeed("polpetone_tagliatelle_caseiro", "Polpetone com tagliatelle caseiro", "Polpetone bovino recheado com muzzarella, molho rustico e tagliatelle fresco.", new BigDecimal("46.90"), 30),
            new ItemCardapioSeed("peixe_grelhado_linguine_limao", "Peixe grelhado com linguine ao limao", "File de peixe grelhado, linguine leve ao limao siciliano e legumes salteados.", new BigDecimal("44.90"), 40)
    );

    private static final List<ItemCardapioSeed> SOBREMESAS = List.of(
            new ItemCardapioSeed("tiramisu_tradicional", "Tiramisu tradicional", "Camadas de biscoito embebido em cafe, creme mascarpone e cacau.", new BigDecimal("21.90"), 10),
            new ItemCardapioSeed("panna_cotta_frutas_vermelhas", "Panna cotta com frutas vermelhas", "Panna cotta de baunilha servida com calda artesanal de frutas vermelhas.", new BigDecimal("19.90"), 20),
            new ItemCardapioSeed("cannoli_creme_baunilha", "Cannoli com creme de baunilha", "Massa crocante recheada com creme delicado e raspas de laranja.", new BigDecimal("18.90"), 30),
            new ItemCardapioSeed("torta_caprese_gelato_creme", "Torta caprese com gelato de creme", "Torta de chocolate com amendoas servida com gelato de creme.", new BigDecimal("23.90"), 40)
    );

    private static final List<ItemCardapioSeed> PIZZAS_ARTESANAIS = List.of(
            new ItemCardapioSeed("pizza_calabresa_broto", "Pizza Calabresa Broto", "Pizza artesanal de calabresa no tamanho broto.", PRECO_BROTO, 10),
            new ItemCardapioSeed("pizza_calabresa_grande", "Pizza Calabresa Grande", "Pizza artesanal de calabresa no tamanho grande.", PRECO_GRANDE, 20),
            new ItemCardapioSeed("pizza_mussarela_broto", "Pizza Mussarela Broto", "Pizza artesanal de mussarela no tamanho broto.", PRECO_BROTO, 30),
            new ItemCardapioSeed("pizza_mussarela_grande", "Pizza Mussarela Grande", "Pizza artesanal de mussarela no tamanho grande.", PRECO_GRANDE, 40),
            new ItemCardapioSeed("pizza_portuguesa_broto", "Pizza Portuguesa Broto", "Pizza artesanal portuguesa no tamanho broto.", PRECO_BROTO, 50),
            new ItemCardapioSeed("pizza_portuguesa_grande", "Pizza Portuguesa Grande", "Pizza artesanal portuguesa no tamanho grande.", PRECO_GRANDE, 60),
            new ItemCardapioSeed("pizza_frango_catupiry_broto", "Pizza Frango Catupiry Broto", "Pizza artesanal de frango com catupiry no tamanho broto.", PRECO_BROTO, 70),
            new ItemCardapioSeed("pizza_frango_catupiry_grande", "Pizza Frango Catupiry Grande", "Pizza artesanal de frango com catupiry no tamanho grande.", PRECO_GRANDE, 80),
            new ItemCardapioSeed("pizza_quatro_queijos_broto", "Pizza Quatro Queijos Broto", "Pizza artesanal quatro queijos no tamanho broto.", PRECO_BROTO, 90),
            new ItemCardapioSeed("pizza_quatro_queijos_grande", "Pizza Quatro Queijos Grande", "Pizza artesanal quatro queijos no tamanho grande.", PRECO_GRANDE, 100),
            new ItemCardapioSeed("pizza_pepperoni_broto", "Pizza Pepperoni Broto", "Pizza artesanal de pepperoni no tamanho broto.", PRECO_BROTO, 110),
            new ItemCardapioSeed("pizza_pepperoni_grande", "Pizza Pepperoni Grande", "Pizza artesanal de pepperoni no tamanho grande.", PRECO_GRANDE, 120)
    );

    private final EstoqueService estoqueService;
    private final CategoriaCardapioRepository categoriaCardapioRepository;
    private final ItemCardapioRepository itemCardapioRepository;
    private final ProdutoRepository produtoRepository;
    private final Usuario adminSuperior;

    public PizzariaMenuSeedRunner(EstoqueService estoqueService,
                                  CategoriaCardapioRepository categoriaCardapioRepository,
                                  ItemCardapioRepository itemCardapioRepository,
                                  ProdutoRepository produtoRepository,
                                  Usuario adminSuperior) {
        this.estoqueService = estoqueService;
        this.categoriaCardapioRepository = categoriaCardapioRepository;
        this.itemCardapioRepository = itemCardapioRepository;
        this.produtoRepository = produtoRepository;
        this.adminSuperior = adminSuperior;
    }

    @Override
    public void run(String... args) {
        migrarPizzasLegadasParaSobDemanda();
        seedBebidasEmEstoque();

        Map<String, CategoriaCardapio> categorias = garantirCategoriasCardapio();
        seedItensPreparadosCardapio(categorias.get("entradas"), ENTRADAS);
        seedItensPreparadosCardapio(categorias.get("massas_classicas"), MASSAS_CLASSICAS);
        seedItensPreparadosCardapio(categorias.get("massas_especiais"), MASSAS_ESPECIAIS);
        seedPizzasCardapio(categorias.get("pizzas_artesanais"));
        seedItensPreparadosCardapio(categorias.get("pratos_executivos"), PRATOS_EXECUTIVOS);
        seedItensPreparadosCardapio(categorias.get("sobremesas"), SOBREMESAS);
        seedBebidasCardapio(categorias.get("bebidas"));
    }

    void migrarPizzasLegadasParaSobDemanda() {
        estoqueService.desativarProdutosPizzaSobDemanda(adminSuperior);
    }

    void seedBebidasEmEstoque() {
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

    Map<String, CategoriaCardapio> garantirCategoriasCardapio() {
        Map<String, CategoriaCardapio> categorias = new LinkedHashMap<>();
        for (CategoriaSeed seed : CATEGORIAS_CARDAPIO) {
            CategoriaCardapio existente = categoriaCardapioRepository.buscarPorCodigo(seed.codigo()).orElse(null);
            CategoriaCardapio persistida = categoriaCardapioRepository.salvar(
                    new CategoriaCardapio(
                            existente != null ? existente.getId() : 0,
                            seed.codigo(),
                            seed.nomeExibicao(),
                            seed.ordemExibicao(),
                            Boolean.TRUE
                    )
            );
            categorias.put(seed.codigo(), persistida);
        }
        return categorias;
    }

    void seedPizzasCardapio(CategoriaCardapio categoriaPizzas) {
        seedItensPreparadosCardapio(categoriaPizzas, PIZZAS_ARTESANAIS);
    }

    void seedItensPreparadosCardapio(CategoriaCardapio categoria, List<ItemCardapioSeed> itensSeed) {
        if (categoria == null || itensSeed == null || itensSeed.isEmpty()) {
            return;
        }

        for (ItemCardapioSeed seed : itensSeed) {
            if (itemCardapioRepository.buscarPorCodigo(seed.codigo()).isPresent()) {
                continue;
            }

            itemCardapioRepository.salvar(new ItemCardapio(
                    0,
                    seed.codigo(),
                    seed.nome(),
                    seed.descricaoCurta(),
                    seed.precoVenda(),
                    categoria,
                    TipoItemCardapio.PREPARADO_SOB_DEMANDA,
                    Boolean.TRUE,
                    Boolean.TRUE,
                    seed.ordemExibicao(),
                    null
            ));
        }
    }

    void seedBebidasCardapio(CategoriaCardapio categoriaBebidas) {
        if (categoriaBebidas == null) {
            return;
        }

        List<Produto> bebidas = produtoRepository.listarTodos().stream()
                .filter(Produto::isControladoPorEstoque)
                .filter(this::ehProdutoElegivelParaCardapioBebidas)
                .sorted(Comparator.comparing(Produto::getNome, String.CASE_INSENSITIVE_ORDER))
                .toList();

        int ordem = 10;
        for (Produto bebida : bebidas) {
            if (itemCardapioRepository.buscarPrimeiroPorProdutoVinculadoId(bebida.getId()).isPresent()) {
                ordem += 10;
                continue;
            }

            itemCardapioRepository.salvar(new ItemCardapio(
                    0,
                    "bebida_produto_" + bebida.getId(),
                    bebida.getNome(),
                    descreverBebida(bebida.getNome()),
                    bebida.getPrecoUnitario(),
                    categoriaBebidas,
                    TipoItemCardapio.ESTOQUE_DIRETO,
                    Boolean.TRUE,
                    Boolean.TRUE,
                    ordem,
                    bebida
            ));
            ordem += 10;
        }
    }

    private String descreverBebida(String nome) {
        String texto = normalizarTexto(nome);
        if (texto.contains("agua")) {
            return "Bebida gelada com baixa automatica de estoque.";
        }
        if (texto.contains("suco")) {
            return "Suco vendido por unidade com baixa automatica de estoque.";
        }
        return "Bebida vinculada ao estoque para venda direta no PDV.";
    }

    private boolean ehProdutoElegivelParaCardapioBebidas(Produto produto) {
        if (produto == null) {
            return false;
        }
        if (produto.getCategoriaEstoque() == CategoriaEstoque.BEBIDAS) {
            return true;
        }

        String nomeNormalizado = normalizarTexto(produto.getNome());
        return CATALOGO_BEBIDAS.stream()
                .map(MenuSeedItem::nome)
                .map(this::normalizarTexto)
                .anyMatch(nomeNormalizado::equals);
    }

    private String normalizarTexto(String valor) {
        return Normalizer.normalize(valor == null ? "" : valor, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase()
                .trim();
    }

    private record CategoriaSeed(String codigo, String nomeExibicao, int ordemExibicao) {
    }

    private record MenuSeedItem(
            String nome,
            int quantidadeInicial,
            int quantidadeMinima,
            BigDecimal preco,
            CategoriaEstoque categoriaEstoque
    ) {
    }

    private record ItemCardapioSeed(
            String codigo,
            String nome,
            String descricaoCurta,
            BigDecimal precoVenda,
            int ordemExibicao
    ) {
    }
}
