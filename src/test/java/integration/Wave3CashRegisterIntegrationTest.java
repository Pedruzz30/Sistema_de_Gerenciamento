package integration;

import app.Main;
import model.Caixa;
import model.CategoriaCardapio;
import model.FechamentoCaixa;
import model.ItemCardapio;
import model.Pedido;
import model.Produto;
import model.TipoItemCardapio;
import model.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import repository.CaixaRepository;
import repository.CategoriaCardapioRepository;
import repository.FechamentoCaixaRepository;
import repository.ItemCardapioRepository;
import repository.PedidoRepository;
import repository.UsuarioRepository;
import service.CaixaService;
import service.EstoqueService;
import service.VendaItemInput;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    private FechamentoCaixaRepository fechamentoCaixaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private CategoriaCardapioRepository categoriaCardapioRepository;

    @Autowired
    private ItemCardapioRepository itemCardapioRepository;

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
        CategoriaCardapio categoriaBebidas = categoriaCardapioRepository.buscarPorCodigo("bebidas").orElseThrow();
        ItemCardapio pizza = itemCardapioRepository.buscarPorCodigo("pizza_calabresa_broto").orElseThrow();
        ItemCardapio bebidaCardapio = itemCardapioRepository.salvar(new ItemCardapio(
                0,
                "bebida_wave3_" + sufixo,
                bebida.getNome(),
                "Bebida vinculada ao estoque.",
                bebida.getPrecoUnitario(),
                categoriaBebidas,
                TipoItemCardapio.ESTOQUE_DIRETO,
                Boolean.TRUE,
                Boolean.TRUE,
                999,
                bebida
        ));
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
                        VendaItemInput.itemCardapio(pizza.getId(), 1),
                        VendaItemInput.itemCardapio(bebidaCardapio.getId(), 2)
                )
        );

        Caixa caixaPersistido = caixaService.buscarCaixaAberto(numeroCaixa);
        Produto bebidaPersistida = estoqueService.buscarProdutoPorId(bebida.getId()).orElseThrow();

        assertEquals(4, bebidaPersistida.getQuantidadeAtual());
        assertEquals(0, BigDecimal.valueOf(144.90).compareTo(caixaPersistido.getSaldoAtual()));
    }

    @Test
    void encerramentoPersisteEventoAuditavelComContagemEFechador() {
        int numeroCaixa = caixaRepository.listarTodos().stream()
                .mapToInt(Caixa::getNumeroCaixa)
                .max()
                .orElse(0) + 70;

        Caixa caixa = caixaService.abrirCaixa(admin, numeroCaixa, 100.00);
        caixaService.registrarVenda(admin, numeroCaixa, 25.00, "Venda fechamento");

        FechamentoCaixa fechamento = caixaService.encerrarCaixa(
                gerenteEstoque,
                numeroCaixa,
                BigDecimal.valueOf(120.00),
                "Conferencia manual"
        );
        FechamentoCaixa persistido = fechamentoCaixaRepository.buscarPorId(fechamento.getId()).orElseThrow();

        assertEquals(caixa.getId(), persistido.getCaixaId());
        assertEquals(numeroCaixa, persistido.getNumeroCaixa());
        assertEquals(admin.getNomeCompleto(), persistido.getAbertoPor());
        assertEquals(gerenteEstoque.getNomeCompleto(), persistido.getFechadoPor());
        assertEquals(0, BigDecimal.valueOf(125.00).compareTo(persistido.getValorSistema()));
        assertEquals(0, BigDecimal.valueOf(120.00).compareTo(persistido.getValorContado()));
        assertEquals(0, BigDecimal.valueOf(-5.00).compareTo(persistido.getDivergencia()));
        assertEquals("Conferencia manual", persistido.getObservacao());
        assertNotNull(persistido.getTimestamp());
    }

    @Test
    void caixaIdMantemIdentidadeDaSessaoMesmoAoReabrirMesmoNumero() {
        int numeroCaixa = caixaRepository.listarTodos().stream()
                .mapToInt(Caixa::getNumeroCaixa)
                .max()
                .orElse(0) + 80;

        Caixa primeiraSessao = caixaService.abrirCaixa(admin, numeroCaixa, 100.00);
        caixaService.registrarVenda(admin, numeroCaixa, 15.00, "Venda sessao 1");
        caixaService.encerrarCaixa(
                admin,
                numeroCaixa,
                BigDecimal.valueOf(115.00),
                "Fechamento sessao 1"
        );

        Caixa segundaSessao = caixaService.abrirCaixa(admin, numeroCaixa, 90.00);
        caixaService.registrarVenda(admin, numeroCaixa, 20.00, "Venda sessao 2");

        Caixa sessaoRecuperadaPorId = caixaService.buscarCaixaPorId(primeiraSessao.getId());
        List<Long> historicoIds = caixaService.buscarHistoricoPorCaixaId(primeiraSessao.getId()).stream()
                .map(Caixa::getId)
                .toList();

        assertEquals(primeiraSessao.getId(), sessaoRecuperadaPorId.getId());
        assertEquals(Caixa.Status.ENCERRADO, sessaoRecuperadaPorId.getStatus());
        assertEquals(1, caixaService.listarMovimentacoesPorCaixaId(primeiraSessao.getId()).size());
        assertEquals(0, BigDecimal.valueOf(15.00).compareTo(
                caixaService.listarMovimentacoesPorCaixaId(primeiraSessao.getId()).get(0).getValor()
        ));
        assertEquals(1, caixaService.listarMovimentacoesPorCaixaId(segundaSessao.getId()).size());
        assertEquals(0, BigDecimal.valueOf(20.00).compareTo(
                caixaService.listarMovimentacoesPorCaixaId(segundaSessao.getId()).get(0).getValor()
        ));
        assertEquals(List.of(primeiraSessao.getId(), segundaSessao.getId()), historicoIds);
        assertEquals(segundaSessao.getId(), caixaService.buscarCaixaAberto(numeroCaixa).getId());
    }
}
