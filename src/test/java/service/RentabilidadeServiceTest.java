package service;

import model.ClasseFuncionario;
import model.Permissao;
import model.Produto;
import model.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import repository.InMemoryCotacaoRepository;
import repository.InMemoryLogRepository;
import repository.InMemoryPedidoRepository;
import repository.InMemoryProdutoRepository;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RentabilidadeServiceTest {

    private EstoqueService estoqueService;
    private CotacaoService cotacaoService;
    private RentabilidadeService rentabilidadeService;
    private Usuario estoquista;
    private Usuario financeiro;
    private Usuario caixa;

    @BeforeEach
    void setUp() {
        InMemoryProdutoRepository produtoRepository = new InMemoryProdutoRepository();
        InMemoryPedidoRepository pedidoRepository = new InMemoryPedidoRepository();
        InMemoryCotacaoRepository cotacaoRepository = new InMemoryCotacaoRepository();

        estoqueService = new EstoqueService(produtoRepository, pedidoRepository);
        cotacaoService = new CotacaoService(cotacaoRepository, new InMemoryLogRepository());
        rentabilidadeService = new RentabilidadeService(estoqueService, cotacaoRepository);

        ClasseFuncionario classeEstoque = new ClasseFuncionario(1, "ESTOQUE", "Edita estoque");
        classeEstoque.adicionarPermissao(Permissao.VER_ESTOQUE);
        classeEstoque.adicionarPermissao(Permissao.EDITAR_ESTOQUE);
        estoquista = new Usuario(1, "Estoquista", "Teste", "390.533.447-05", "1234", classeEstoque, Usuario.Perfil.OPERADOR);

        ClasseFuncionario classeFinanceira = new ClasseFuncionario(2, "FINANCEIRO", "Ve financas");
        classeFinanceira.adicionarPermissao(Permissao.VER_FINANCAS);
        financeiro = new Usuario(2, "Financeiro", "Teste", "111.444.777-35", "1234", classeFinanceira, Usuario.Perfil.OPERADOR);

        ClasseFuncionario classeCaixa = new ClasseFuncionario(3, "CAIXA", "Opera vendas");
        classeCaixa.adicionarPermissao(Permissao.VER_VENDAS);
        caixa = new Usuario(3, "Caixa", "Teste", "935.411.347-80", "1234", classeCaixa, Usuario.Perfil.OPERADOR);
    }

    @Test
    void gerarRanking_ordenaPorLucroTotalEstimadoEDepoisMargem() {
        Produto produtoVendido = cadastrarProduto("Bebida Vendida", 10, "20.00");
        Produto produtoSemGiro = cadastrarProduto("Ingrediente Sem Giro", 10, "100.00");
        Produto produtoPendente = cadastrarProduto("Sem Cotacao", 5, "15.00");

        cotar(produtoVendido, 2026, 5, "10.00");
        cotar(produtoSemGiro, 2026, 5, "20.00");
        estoqueService.registrarSaidasPorVenda(
                caixa,
                List.of(new VendaItemInput(produtoVendido.getId(), 3)),
                "Saida via venda no caixa 1."
        );

        RentabilidadeService.AnaliseRentabilidade analise = rentabilidadeService.gerarRanking();

        assertEquals(3, analise.totalProdutos());
        assertEquals(2, analise.produtosAnalisados());
        assertEquals(1, analise.produtosPendentesCusto());
        assertEquals(3, analise.unidadesVendidasTotal());
        assertEquals(0, BigDecimal.valueOf(60).compareTo(analise.receitaTotalEstimada()));
        assertEquals(0, BigDecimal.valueOf(30).compareTo(analise.lucroTotalEstimado()));

        RentabilidadeService.ItemRentabilidade lider = analise.itens().get(0);
        assertEquals(produtoVendido.getId(), lider.produtoId());
        assertEquals(1, lider.posicao());
        assertEquals(3, lider.unidadesVendidas());
        assertEquals(0, BigDecimal.valueOf(30).compareTo(lider.lucroTotalEstimado()));

        RentabilidadeService.ItemRentabilidade segundo = analise.itens().get(1);
        assertEquals(produtoSemGiro.getId(), segundo.produtoId());
        assertEquals(2, segundo.posicao());
        assertEquals(0, segundo.unidadesVendidas());
        assertEquals(0, BigDecimal.ZERO.setScale(2).compareTo(segundo.lucroTotalEstimado()));

        RentabilidadeService.ItemRentabilidade pendente = analise.itens().get(2);
        assertEquals(produtoPendente.getId(), pendente.produtoId());
        assertNull(pendente.posicao());
        assertEquals("CUSTO_PENDENTE", pendente.statusCodigo());
    }

    @Test
    void gerarRanking_usaCotacaoMaisRecenteComoCusto() {
        Produto produto = cadastrarProduto("Produto Cotado", 4, "30.00");
        cotar(produto, 2026, 1, "20.00");
        cotar(produto, 2026, 3, "12.00");

        RentabilidadeService.ItemRentabilidade item = rentabilidadeService.gerarRanking().itens().get(0);

        assertEquals(0, BigDecimal.valueOf(12).compareTo(item.precoCusto()));
        assertEquals(0, BigDecimal.valueOf(18).compareTo(item.lucroUnitario()));
        assertEquals(0, BigDecimal.valueOf(60).compareTo(item.margemPercentual()));
    }

    @Test
    void gerarRanking_ignoraSaidaManualAoCalcularUnidadesVendidas() {
        Produto produto = cadastrarProduto("Produto Com Ajuste", 10, "12.00");
        cotar(produto, 2026, 4, "8.00");

        estoqueService.registrarMovimentacao(produto.getId(), 2, model.TipoMovimentacao.SAIDA, estoquista, "Ajuste de estoque");
        estoqueService.registrarSaidasPorVenda(
                caixa,
                List.of(new VendaItemInput(produto.getId(), 3)),
                "Saida via venda no caixa 2."
        );

        RentabilidadeService.ItemRentabilidade item = rentabilidadeService.gerarRanking().itens().get(0);

        assertEquals(3, item.unidadesVendidas());
        assertEquals(0, BigDecimal.valueOf(36).compareTo(item.receitaEstimada()));
        assertEquals(0, BigDecimal.valueOf(12).compareTo(item.lucroTotalEstimado()));
        assertTrue(rentabilidadeService.gerarRanking().estimado());
    }

    private Produto cadastrarProduto(String nome, int quantidade, String preco) {
        return estoqueService.cadastrarProduto(
                estoquista,
                nome,
                quantidade,
                1,
                new BigDecimal(preco)
        );
    }

    private void cotar(Produto produto, int ano, int mes, String preco) {
        cotacaoService.registrarCotacao(
                financeiro,
                produto,
                YearMonth.of(ano, mes),
                new BigDecimal(preco),
                10
        );
    }
}
