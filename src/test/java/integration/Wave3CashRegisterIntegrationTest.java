package integration;

import app.Main;
import model.Caixa;
import model.Pedido;
import model.Produto;
import model.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import repository.CaixaRepository;
import repository.PedidoRepository;
import repository.UsuarioRepository;
import service.CaixaService;
import service.EstoqueService;
import service.VendaItemInput;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = Main.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class Wave3CashRegisterIntegrationTest {

    @Autowired
    private CaixaService caixaService;

    @Autowired
    private EstoqueService estoqueService;

    @Autowired
    private CaixaRepository caixaRepository;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private Usuario admin;
    private Usuario gerenteEstoque;

    @BeforeEach
    void setUp() {
        admin = usuarioRepository.listarTodos().stream()
                .filter(usuario -> "52998224725".equals(usuario.getCpfBruto()))
                .findFirst()
                .orElseThrow();
        gerenteEstoque = usuarioRepository.listarTodos().stream()
                .filter(usuario -> "11144477735".equals(usuario.getCpfBruto()))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void vendaComItensAtualizaCaixaEEstoqueNoMesmoFluxo() {
        String sufixo = String.valueOf(System.nanoTime());
        Produto produto = estoqueService.cadastrarProduto(
                gerenteEstoque,
                "PDV Wave3 " + sufixo,
                12,
                1,
                BigDecimal.valueOf(14.50)
        );
        int numeroCaixa = caixaRepository.listarTodos().stream()
                .mapToInt(Caixa::getNumeroCaixa)
                .max()
                .orElse(0) + 50;

        Caixa caixa = caixaService.abrirCaixa(admin, numeroCaixa, 100.00);
        caixaService.registrarVenda(
                admin,
                numeroCaixa,
                BigDecimal.ZERO,
                "3x PDV Wave3",
                List.of(new VendaItemInput(produto.getId(), 3))
        );
        caixaService.registrarSuprimento(admin, numeroCaixa, 10.00, "Troco");
        caixaService.registrarSangria(admin, numeroCaixa, 5.00, "Retirada");

        Caixa caixaPersistido = caixaService.buscarCaixaAberto(numeroCaixa);
        Produto produtoPersistido = estoqueService.buscarProdutoPorId(produto.getId()).orElseThrow();
        Pedido saidaVenda = pedidoRepository.listarTodos().stream()
                .filter(pedido -> pedido.getProduto().getId() == produto.getId())
                .max(Comparator.comparing(Pedido::getDataHora))
                .orElseThrow();

        assertEquals(9, produtoPersistido.getQuantidadeAtual());
        assertEquals(0, BigDecimal.valueOf(148.50).compareTo(caixaPersistido.getSaldoAtual()));
        assertEquals(3, caixaPersistido.getMovimentacoes().size());
        assertTrue(caixaPersistido.getMovimentacoes().stream().allMatch(mov -> mov.getId() > 0));
        assertEquals("Saida via venda no caixa " + numeroCaixa + ": 3x PDV Wave3", saidaVenda.getDescricao());
        assertEquals(12, saidaVenda.getSaldoAnterior());
        assertEquals(9, saidaVenda.getSaldoPosterior());
    }

    @Test
    void vendaMistaMantemBaixaApenasDeItensComEstoque() {
        String sufixo = String.valueOf(System.nanoTime());
        Produto bebida = estoqueService.cadastrarProduto(
                gerenteEstoque,
                "Bebida Wave3 " + sufixo,
                6,
                1,
                BigDecimal.valueOf(7.50)
        );
        int numeroCaixa = caixaRepository.listarTodos().stream()
                .mapToInt(Caixa::getNumeroCaixa)
                .max()
                .orElse(0) + 60;

        caixaService.abrirCaixa(admin, numeroCaixa, 100.00);
        caixaService.registrarVenda(
                admin,
                numeroCaixa,
                BigDecimal.ZERO,
                "1x Pizza Calabresa Broto, 2x Bebida Wave3",
                List.of(
                        new VendaItemInput("pizza_calabresa_broto", 1),
                        new VendaItemInput(bebida.getId(), 2)
                )
        );

        Caixa caixaPersistido = caixaService.buscarCaixaAberto(numeroCaixa);
        Produto bebidaPersistida = estoqueService.buscarProdutoPorId(bebida.getId()).orElseThrow();

        assertEquals(4, bebidaPersistida.getQuantidadeAtual());
        assertEquals(0, BigDecimal.valueOf(144.90).compareTo(caixaPersistido.getSaldoAtual()));
    }
}
