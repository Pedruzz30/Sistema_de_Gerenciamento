package service;

import java.math.BigDecimal;
import java.util.List;

import model.Caixa;
import model.CategoriaCardapio;
import model.ClasseFuncionario;
import model.FechamentoCaixa;
import model.ItemCardapio;
import model.Permissao;
import model.Produto;
import model.TipoItemCardapio;
import model.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import repository.InMemoryCaixaRepository;
import repository.InMemoryCategoriaCardapioRepository;
import repository.InMemoryFechamentoCaixaRepository;
import repository.InMemoryItemCardapioRepository;
import repository.InMemoryLogRepository;
import repository.InMemoryPedidoRepository;
import repository.InMemoryProdutoRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CaixaServiceTest {

    private CaixaService service;
    private EstoqueService estoqueService;
    private InMemoryItemCardapioRepository itemCardapioRepository;
    private InMemoryFechamentoCaixaRepository fechamentoCaixaRepository;
    private CategoriaCardapio categoriaPizzas;
    private CategoriaCardapio categoriaBebidas;
    private Usuario operadorFinancas;
    private Usuario operadorFechamento;
    private Usuario operadorSemPermissao;

    @BeforeEach
    void setUp() {
        InMemoryProdutoRepository produtoRepository = new InMemoryProdutoRepository();
        estoqueService = new EstoqueService(produtoRepository, new InMemoryPedidoRepository());
        InMemoryCategoriaCardapioRepository categoriaCardapioRepository = new InMemoryCategoriaCardapioRepository();
        itemCardapioRepository = new InMemoryItemCardapioRepository();
        CardapioService cardapioService = new CardapioService(
                categoriaCardapioRepository,
                itemCardapioRepository,
                produtoRepository
        );

        fechamentoCaixaRepository = new InMemoryFechamentoCaixaRepository();
        service = new CaixaService(
                new InMemoryCaixaRepository(),
                fechamentoCaixaRepository,
                new InMemoryLogRepository(),
                estoqueService,
                cardapioService
        );

        categoriaPizzas = categoriaCardapioRepository.salvar(
                new CategoriaCardapio(0, "pizzas_artesanais", "Pizzas artesanais", 10, Boolean.TRUE)
        );
        categoriaBebidas = categoriaCardapioRepository.salvar(
                new CategoriaCardapio(0, "bebidas", "Bebidas", 20, Boolean.TRUE)
        );

        ClasseFuncionario classeCompleta = new ClasseFuncionario(1, "CAIXA", "PDV");
        classeCompleta.adicionarPermissao(Permissao.VER_VENDAS);
        classeCompleta.adicionarPermissao(Permissao.VER_FINANCAS);
        classeCompleta.adicionarPermissao(Permissao.EDITAR_ESTOQUE);
        operadorFinancas = new Usuario(1, "Joao", "Silva", "529.982.247-25", "1234",
                classeCompleta, Usuario.Perfil.OPERADOR);
        operadorFechamento = new Usuario(3, "Carlos", "Fechamento", "390.533.447-05", "1234",
                classeCompleta, Usuario.Perfil.OPERADOR);

        ClasseFuncionario classeSemPermissao = new ClasseFuncionario(2, "ESTOQUISTA", "Estoque");
        classeSemPermissao.adicionarPermissao(Permissao.VER_ESTOQUE);
        operadorSemPermissao = new Usuario(2, "Maria", "Lima", "111.444.777-35", "1234",
                classeSemPermissao, Usuario.Perfil.OPERADOR);
    }

    @Test
    void abrirCaixa_comPermissaoValida_retornaCaixaAberto() {
        Caixa caixa = service.abrirCaixa(operadorFinancas, 1, 100.00);

        assertEquals(Caixa.Status.ABERTO, caixa.getStatus());
        assertBigDecimalEquals(100.00, caixa.getSaldoAtual());
        assertEquals(1, caixa.getNumeroCaixa());
    }

    @Test
    void abrirCaixa_semPermissao_lancaSecurityException() {
        assertThrows(SecurityException.class,
                () -> service.abrirCaixa(operadorSemPermissao, 1, 100.00));
    }

    @Test
    void abrirCaixa_duplicado_lancaIllegalState() {
        service.abrirCaixa(operadorFinancas, 1, 100.00);

        assertThrows(IllegalStateException.class,
                () -> service.abrirCaixa(operadorFinancas, 1, 50.00));
    }

    @Test
    void registrarVenda_aumentaSaldoEContabilizaVenda() {
        Caixa caixa = service.abrirCaixa(operadorFinancas, 2, 100.00);

        service.registrarVenda(operadorFinancas, 2, 30.00, "Venda de produto");

        assertBigDecimalEquals(130.00, caixa.getSaldoAtual());
        assertBigDecimalEquals(30.00, caixa.calcularTotalVendas());
    }

    @Test
    void registrarVenda_semPermissao_lancaSecurityException() {
        service.abrirCaixa(operadorFinancas, 6, 100.00);

        assertThrows(SecurityException.class,
                () -> service.registrarVenda(operadorSemPermissao, 6, 30.00, "Venda bloqueada"));
    }

    @Test
    void registrarVendaComItens_diminuiEstoqueEUsaTotalCalculadoNoServidor() {
        Produto produto = estoqueService.cadastrarProduto(operadorFinancas, "Refrigerante", 10, 1, 8.00);
        Caixa caixa = service.abrirCaixa(operadorFinancas, 9, 100.00);

        service.registrarVenda(
                operadorFinancas,
                9,
                BigDecimal.ZERO,
                "2x Refrigerante",
                java.util.List.of(new VendaItemInput(produto.getId(), 2))
        );

        assertBigDecimalEquals(116.00, caixa.getSaldoAtual());
        assertEquals(8, estoqueService.buscarProdutoPorId(produto.getId()).orElseThrow().getQuantidadeAtual());
    }

    @Test
    void registrarVendaComItemCardapioSobDemandaEItemVinculadoAoEstoque() {
        Produto bebida = estoqueService.cadastrarProduto(operadorFinancas, "Refrigerante", 10, 1, 8.00);
        ItemCardapio pizza = itemCardapioRepository.salvar(new ItemCardapio(
                0,
                "pizza_calabresa_broto",
                "Pizza Calabresa Broto",
                "Pizza artesanal sob demanda.",
                BigDecimal.valueOf(29.90),
                categoriaPizzas,
                TipoItemCardapio.PREPARADO_SOB_DEMANDA,
                Boolean.TRUE,
                Boolean.TRUE,
                10,
                null
        ));
        ItemCardapio bebidaCardapio = itemCardapioRepository.salvar(new ItemCardapio(
                0,
                "bebida_refrigerante",
                "Refrigerante",
                "Bebida com baixa automatica de estoque.",
                BigDecimal.valueOf(8.00),
                categoriaBebidas,
                TipoItemCardapio.ESTOQUE_DIRETO,
                Boolean.TRUE,
                Boolean.TRUE,
                20,
                bebida
        ));
        Caixa caixa = service.abrirCaixa(operadorFinancas, 10, 100.00);

        service.registrarVenda(
                operadorFinancas,
                10,
                BigDecimal.ZERO,
                "1x Pizza Calabresa Broto, 2x Refrigerante",
                java.util.List.of(
                        VendaItemInput.itemCardapio(pizza.getId(), 1),
                        VendaItemInput.itemCardapio(bebidaCardapio.getId(), 2)
                )
        );

        assertBigDecimalEquals(145.90, caixa.getSaldoAtual());
        assertEquals(8, estoqueService.buscarProdutoPorId(bebida.getId()).orElseThrow().getQuantidadeAtual());
    }

    @Test
    void registrarSangria_comSaldoInsuficiente_lancaIllegalArgument() {
        service.abrirCaixa(operadorFinancas, 3, 50.00);

        assertThrows(IllegalArgumentException.class,
                () -> service.registrarSangria(operadorFinancas, 3, 200.00, "Sangria invalida"));
    }

    @Test
    void registrarSangria_semPermissao_lancaSecurityException() {
        service.abrirCaixa(operadorFinancas, 7, 100.00);

        assertThrows(SecurityException.class,
                () -> service.registrarSangria(operadorSemPermissao, 7, 10.00, "Sangria bloqueada"));
    }

    @Test
    void registrarSuprimento_semPermissao_lancaSecurityException() {
        service.abrirCaixa(operadorFinancas, 8, 100.00);

        assertThrows(SecurityException.class,
                () -> service.registrarSuprimento(operadorSemPermissao, 8, 10.00, "Suprimento bloqueado"));
    }

    @Test
    void encerrarCaixa_retornaFechamentoCorreto() {
        Caixa caixa = service.abrirCaixa(operadorFinancas, 4, 100.00);
        service.registrarVenda(operadorFinancas, 4, 25.00, "Venda A");
        service.registrarVenda(operadorFinancas, 4, 18.50, "Venda B");
        service.registrarSangria(operadorFinancas, 4, 50.00, "Envio ao cofre");

        FechamentoCaixa fechamento = service.encerrarCaixa(
                operadorFechamento,
                4,
                BigDecimal.valueOf(91.00),
                "Quebra de caixa"
        );

        assertBigDecimalEquals(43.50, fechamento.getTotalVendas());
        assertBigDecimalEquals(93.50, fechamento.getSaldoFinal());
        assertBigDecimalEquals(91.00, fechamento.getValorContado());
        assertBigDecimalEquals(-2.50, fechamento.getDivergencia());
        assertEquals("Joao Silva", fechamento.getAbertoPor());
        assertEquals("Carlos Fechamento", fechamento.getFechadoPor());
        assertEquals("Quebra de caixa", fechamento.getObservacao());
        assertEquals(1, fechamentoCaixaRepository.listarTodos().size());
        assertEquals(fechamento, fechamentoCaixaRepository.buscarPorId(fechamento.getId()).orElseThrow());
        assertEquals(Caixa.Status.ENCERRADO, caixa.getStatus());
        assertThrows(IllegalStateException.class, () -> service.buscarCaixaAberto(4));
    }

    @Test
    void encerrarCaixa_semPermissao_lancaSecurityException() {
        service.abrirCaixa(operadorFinancas, 5, 100.00);

        assertThrows(SecurityException.class,
                () -> service.encerrarCaixa(operadorSemPermissao, 5));
    }

    @Test
    void caixaIdIdentificaSessaoMesmoComReaberturaDoMesmoNumero() {
        Caixa primeiraSessao = service.abrirCaixa(operadorFinancas, 11, 100.00);
        service.registrarVenda(operadorFinancas, 11, 15.00, "Venda sessao 1");
        service.encerrarCaixa(
                operadorFechamento,
                11,
                BigDecimal.valueOf(115.00),
                "Fechamento sessao 1"
        );

        Caixa segundaSessao = service.abrirCaixa(operadorFinancas, 11, 80.00);
        service.registrarVenda(operadorFinancas, 11, 20.00, "Venda sessao 2");

        Caixa sessaoRecuperadaPorId = service.buscarCaixaPorId(primeiraSessao.getId());
        List<Long> historicoIds = service.buscarHistoricoPorCaixaId(primeiraSessao.getId()).stream()
                .map(Caixa::getId)
                .toList();

        assertEquals(primeiraSessao.getId(), sessaoRecuperadaPorId.getId());
        assertEquals(Caixa.Status.ENCERRADO, sessaoRecuperadaPorId.getStatus());
        assertEquals(1, service.listarMovimentacoesPorCaixaId(primeiraSessao.getId()).size());
        assertBigDecimalEquals(15.00, service.listarMovimentacoesPorCaixaId(primeiraSessao.getId()).get(0).getValor());
        assertEquals(1, service.listarMovimentacoesPorCaixaId(segundaSessao.getId()).size());
        assertBigDecimalEquals(20.00, service.listarMovimentacoesPorCaixaId(segundaSessao.getId()).get(0).getValor());
        assertEquals(List.of(primeiraSessao.getId(), segundaSessao.getId()), historicoIds);
        assertEquals(segundaSessao.getId(), service.buscarCaixaAberto(11).getId());
    }

    @Test
    void buscarCaixaAberto_caixaInexistente_lancaIllegalState() {
        assertThrows(IllegalStateException.class,
                () -> service.buscarCaixaAberto(999));
    }

    private static void assertBigDecimalEquals(double esperado, BigDecimal atual) {
        assertEquals(0, BigDecimal.valueOf(esperado).compareTo(atual));
    }
}
