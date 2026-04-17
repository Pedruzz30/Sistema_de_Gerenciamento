package service;

import model.CategoriaCardapio;
import model.ItemCardapio;
import model.Produto;
import model.TipoItemCardapio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import repository.InMemoryCategoriaCardapioRepository;
import repository.InMemoryItemCardapioRepository;
import repository.InMemoryProdutoRepository;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CardapioServiceTest {

    private CardapioService service;
    private CategoriaCardapio categoriaAtiva;
    private CategoriaCardapio categoriaInativa;
    private InMemoryProdutoRepository produtoRepository;

    @BeforeEach
    void setUp() {
        InMemoryCategoriaCardapioRepository categoriaRepository = new InMemoryCategoriaCardapioRepository();
        InMemoryItemCardapioRepository itemRepository = new InMemoryItemCardapioRepository();
        produtoRepository = new InMemoryProdutoRepository();

        service = new CardapioService(categoriaRepository, itemRepository, produtoRepository);
        categoriaAtiva = categoriaRepository.salvar(
                new CategoriaCardapio(0, "massas", "Massas", 10, Boolean.TRUE)
        );
        categoriaInativa = categoriaRepository.salvar(
                new CategoriaCardapio(0, "sobremesas", "Sobremesas", 20, Boolean.FALSE)
        );
    }

    @Test
    void itemDisponivelRespeitaCategoriaStatusManualEEstoque() {
        Produto bebidaComEstoque = produtoRepository.salvar(
                new Produto(0, "Suco de Uva", 4, 1, BigDecimal.valueOf(12.00))
        );
        Produto bebidaSemEstoque = produtoRepository.salvar(
                new Produto(0, "Agua sem gas", 0, 1, BigDecimal.valueOf(4.00))
        );

        ItemCardapio preparadoDisponivel = service.criarItem(
                null,
                "Lasanha da casa",
                "Feita na hora",
                BigDecimal.valueOf(39.90),
                categoriaAtiva.getId(),
                TipoItemCardapio.PREPARADO_SOB_DEMANDA,
                Boolean.TRUE,
                Boolean.TRUE,
                10,
                null
        );
        ItemCardapio preparadoIndisponivelManual = service.criarItem(
                null,
                "Ravioli especial",
                "Fora temporariamente",
                BigDecimal.valueOf(42.90),
                categoriaAtiva.getId(),
                TipoItemCardapio.PREPARADO_SOB_DEMANDA,
                Boolean.TRUE,
                Boolean.FALSE,
                20,
                null
        );
        ItemCardapio estoqueComProduto = service.criarItem(
                null,
                "Suco de Uva",
                "Garrafa 1L",
                BigDecimal.valueOf(12.00),
                categoriaAtiva.getId(),
                TipoItemCardapio.ESTOQUE_DIRETO,
                Boolean.TRUE,
                Boolean.TRUE,
                30,
                bebidaComEstoque.getId()
        );
        ItemCardapio estoqueSemQuantidade = service.criarItem(
                null,
                "Agua sem gas",
                "Garrafa 500ml",
                BigDecimal.valueOf(4.00),
                categoriaAtiva.getId(),
                TipoItemCardapio.ESTOQUE_DIRETO,
                Boolean.TRUE,
                Boolean.TRUE,
                40,
                bebidaSemEstoque.getId()
        );
        ItemCardapio estoqueManualSemProduto = service.criarItem(
                null,
                "Brownie pronto",
                "Controle manual",
                BigDecimal.valueOf(16.00),
                categoriaAtiva.getId(),
                TipoItemCardapio.ESTOQUE_DIRETO,
                Boolean.TRUE,
                Boolean.TRUE,
                50,
                null
        );
        ItemCardapio categoriaBloqueada = service.criarItem(
                null,
                "Pudim",
                "Categoria desativada",
                BigDecimal.valueOf(11.00),
                categoriaInativa.getId(),
                TipoItemCardapio.PREPARADO_SOB_DEMANDA,
                Boolean.TRUE,
                Boolean.TRUE,
                60,
                null
        );

        assertTrue(service.itemDisponivel(preparadoDisponivel));
        assertFalse(service.itemDisponivel(preparadoIndisponivelManual));
        assertTrue(service.itemDisponivel(estoqueComProduto));
        assertFalse(service.itemDisponivel(estoqueSemQuantidade));
        assertTrue(service.itemDisponivel(estoqueManualSemProduto));
        assertFalse(service.itemDisponivel(categoriaBloqueada));
    }

    @Test
    void buscarItemAtivoPorIdBloqueiaItemManualOuSemEstoque() {
        Produto bebidaSemEstoque = produtoRepository.salvar(
                new Produto(0, "Cha gelado", 0, 1, BigDecimal.valueOf(6.00))
        );

        ItemCardapio semEstoque = service.criarItem(
                null,
                "Cha gelado",
                "Sem unidades",
                BigDecimal.valueOf(6.00),
                categoriaAtiva.getId(),
                TipoItemCardapio.ESTOQUE_DIRETO,
                Boolean.TRUE,
                Boolean.TRUE,
                10,
                bebidaSemEstoque.getId()
        );
        ItemCardapio indisponivelManual = service.criarItem(
                null,
                "Torta de limao",
                "Pausa de venda",
                BigDecimal.valueOf(15.00),
                categoriaAtiva.getId(),
                TipoItemCardapio.PREPARADO_SOB_DEMANDA,
                Boolean.TRUE,
                Boolean.FALSE,
                20,
                null
        );

        assertThrows(IllegalArgumentException.class, () -> service.buscarItemAtivoPorId(semEstoque.getId()));
        assertThrows(IllegalArgumentException.class, () -> service.buscarItemAtivoPorId(indisponivelManual.getId()));
    }

    @Test
    void criarItemPreparadoSobDemandaNaoAceitaProdutoVinculado() {
        Produto produto = produtoRepository.salvar(
                new Produto(0, "Base pronta", 10, 1, BigDecimal.valueOf(5.00))
        );

        assertThrows(IllegalArgumentException.class, () -> service.criarItem(
                null,
                "Massa fresca",
                "Nao deve vincular estoque",
                BigDecimal.valueOf(23.00),
                categoriaAtiva.getId(),
                TipoItemCardapio.PREPARADO_SOB_DEMANDA,
                Boolean.TRUE,
                Boolean.TRUE,
                10,
                produto.getId()
        ));
    }

    @Test
    void categoriaDesativadaAposCadastroSaiDaListagemOperacionalEEfetiva() {
        ItemCardapio item = service.criarItem(
                "ravioli_funghi",
                "Ravioli Funghi",
                "Categoria sera pausada depois",
                BigDecimal.valueOf(45.00),
                categoriaAtiva.getId(),
                TipoItemCardapio.PREPARADO_SOB_DEMANDA,
                Boolean.TRUE,
                Boolean.TRUE,
                10,
                null
        );

        service.atualizarStatusCategoria(categoriaAtiva.getId(), false);

        assertFalse(service.itemDisponivel(item));
        assertTrue(service.listarItensAtivos("massas", null).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> service.buscarItemAtivoPorId(item.getId()));
    }

    @Test
    void codigosDeCategoriaEItemPrecisamSerUnicosAposNormalizacao() {
        service.criarCategoria("Massas Premium", "Massas Premium", 30, Boolean.TRUE);
        assertThrows(IllegalArgumentException.class, () -> service.criarCategoria("massas-premium", "Outra", 40, Boolean.TRUE));

        service.criarItem(
                "Lasanha Especial",
                "Lasanha Especial",
                "Primeira versao",
                BigDecimal.valueOf(39.90),
                categoriaAtiva.getId(),
                TipoItemCardapio.PREPARADO_SOB_DEMANDA,
                Boolean.TRUE,
                Boolean.TRUE,
                10,
                null
        );

        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class, () -> service.criarItem(
                "lasanha_especial",
                "Outra Lasanha",
                "Duplicado",
                BigDecimal.valueOf(41.90),
                categoriaAtiva.getId(),
                TipoItemCardapio.PREPARADO_SOB_DEMANDA,
                Boolean.TRUE,
                Boolean.TRUE,
                20,
                null
        ));

        assertEquals("Ja existe um item de cardapio com este codigo.", erro.getMessage());
    }

    @Test
    void listagemAdministrativaRefleteCategoriaAtualizadaParaBuscaEFiltro() {
        ItemCardapio item = service.criarItem(
                "gnocchi_trufado",
                "Gnocchi trufado",
                "Receita especial",
                BigDecimal.valueOf(59.00),
                categoriaAtiva.getId(),
                TipoItemCardapio.PREPARADO_SOB_DEMANDA,
                Boolean.TRUE,
                Boolean.TRUE,
                10,
                null
        );

        CategoriaCardapio categoriaRenomeada = service.atualizarCategoria(
                categoriaAtiva.getId(),
                "massas_nobres",
                "Massas Nobres",
                5
        );

        List<ItemCardapio> itensFiltrados = service.listarItensAdministrativos(categoriaRenomeada.getId(), "nobres");

        assertEquals(1, itensFiltrados.size());
        assertEquals(item.getId(), itensFiltrados.get(0).getId());
        assertEquals("massas_nobres", itensFiltrados.get(0).getCategoriaCardapio().getCodigo());
        assertEquals("Massas Nobres", itensFiltrados.get(0).getCategoriaCardapio().getNomeExibicao());
    }
}
