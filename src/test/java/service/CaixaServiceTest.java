package service;

import java.math.BigDecimal;

import model.Caixa;
import model.ClasseFuncionario;
import model.FechamentoCaixa;
import model.Permissao;
import model.Produto;
import model.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import repository.InMemoryCaixaRepository;
import repository.InMemoryLogRepository;
import repository.InMemoryPedidoRepository;
import repository.InMemoryProdutoRepository;

import static org.junit.jupiter.api.Assertions.*;

class CaixaServiceTest {

    private CaixaService service;
    private EstoqueService estoqueService;
    private Usuario operadorFinancas;
    private Usuario operadorSemPermissao;

    @BeforeEach
    void setUp() {
        estoqueService = new EstoqueService(new InMemoryProdutoRepository(), new InMemoryPedidoRepository());
        service = new CaixaService(
                new InMemoryCaixaRepository(),
                new InMemoryLogRepository(),
                estoqueService,
                new PizzaMenuCatalogService()
        );

        ClasseFuncionario classeCompleta = new ClasseFuncionario(1, "CAIXA", "PDV");
        classeCompleta.adicionarPermissao(Permissao.VER_VENDAS);
        classeCompleta.adicionarPermissao(Permissao.VER_FINANCAS);
        classeCompleta.adicionarPermissao(Permissao.EDITAR_ESTOQUE);
        operadorFinancas = new Usuario(1, "João", "Silva", "529.982.247-25", "1234",
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
    void registrarVendaComPizzaSobDemandaESemBaixaDeEstoqueDaPizza() {
        Produto bebida = estoqueService.cadastrarProduto(operadorFinancas, "Refrigerante", 10, 1, 8.00);
        Caixa caixa = service.abrirCaixa(operadorFinancas, 10, 100.00);

        service.registrarVenda(
                operadorFinancas,
                10,
                BigDecimal.ZERO,
                "1x Pizza Calabresa Broto, 2x Refrigerante",
                java.util.List.of(
                        new VendaItemInput("pizza_calabresa_broto", 1),
                        new VendaItemInput(bebida.getId(), 2)
                )
        );

        assertBigDecimalEquals(145.90, caixa.getSaldoAtual());
        assertEquals(8, estoqueService.buscarProdutoPorId(bebida.getId()).orElseThrow().getQuantidadeAtual());
    }

    @Test
    void registrarSangria_comSaldoInsuficiente_lancaIllegalArgument() {
        service.abrirCaixa(operadorFinancas, 3, 50.00);

        assertThrows(IllegalArgumentException.class,
                () -> service.registrarSangria(operadorFinancas, 3, 200.00, "Sangria inválida"));
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

        FechamentoCaixa fechamento = service.encerrarCaixa(operadorFinancas, 4);

        assertBigDecimalEquals(43.50, fechamento.getTotalVendas());
        assertBigDecimalEquals(93.50, fechamento.getSaldoFinal());
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
    void buscarCaixaAberto_caixaInexistente_lancaIllegalState() {
        assertThrows(IllegalStateException.class,
                () -> service.buscarCaixaAberto(999));
    }

    private void assertBigDecimalEquals(double esperado, BigDecimal atual) {
        assertEquals(0, BigDecimal.valueOf(esperado).compareTo(atual));
    }
}
