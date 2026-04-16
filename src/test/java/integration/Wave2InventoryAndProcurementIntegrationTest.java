package integration;

import app.Main;
import model.CotacaoMensal;
import model.Fornecedor;
import model.NotaFiscal;
import model.Pedido;
import model.Produto;
import model.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import repository.NotaFiscalRepository;
import repository.PedidoRepository;
import repository.UsuarioRepository;
import service.CotacaoService;
import service.EstoqueService;
import service.FornecedorService;
import service.NotaFiscalService;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = Main.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class Wave2InventoryAndProcurementIntegrationTest {

    @Autowired
    private EstoqueService estoqueService;

    @Autowired
    private FornecedorService fornecedorService;

    @Autowired
    private NotaFiscalService notaFiscalService;

    @Autowired
    private CotacaoService cotacaoService;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private NotaFiscalRepository notaFiscalRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private Usuario gerenteEstoque;

    @BeforeEach
    void setUp() {
        gerenteEstoque = usuarioRepository.listarTodos().stream()
                .filter(usuario -> "11144477735".equals(usuario.getCpfBruto()))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void confirmarNotaPersisteFornecedorItensMovimentacaoEAtualizaSaldo() {
        String sufixo = String.valueOf(System.nanoTime());
        Produto produto = estoqueService.cadastrarProduto(
                gerenteEstoque,
                "Produto Wave2 " + sufixo,
                0,
                2,
                BigDecimal.valueOf(19.90)
        );
        Fornecedor fornecedor = fornecedorService.cadastrarFornecedor(
                gerenteEstoque,
                "Fornecedor Wave2 " + sufixo,
                gerarCnpjValido(System.nanoTime()),
                "(11) 90000-0000"
        );

        NotaFiscal nota = notaFiscalService.abrirNota(gerenteEstoque, fornecedor);
        notaFiscalService.adicionarItem(gerenteEstoque, nota, produto, 7, BigDecimal.valueOf(18.75));
        notaFiscalService.confirmarNota(gerenteEstoque, nota);

        NotaFiscal notaPersistida = notaFiscalRepository.buscarPorId(nota.getId()).orElseThrow();
        Fornecedor fornecedorPersistido = fornecedorService.buscarPorId(fornecedor.getId());
        Produto produtoPersistido = estoqueService.buscarProdutoPorId(produto.getId()).orElseThrow();
        Pedido ultimaMovimentacao = pedidoRepository.listarTodos().stream()
                .filter(pedido -> pedido.getProduto().getId() == produto.getId())
                .max(Comparator.comparing(Pedido::getDataHora))
                .orElseThrow();

        assertEquals(NotaFiscal.Status.CONFIRMADA, notaPersistida.getStatus());
        assertEquals(1, notaPersistida.getItens().size());
        assertTrue(fornecedorPersistido.possuiProduto(produto.getId()));
        assertEquals(7, produtoPersistido.getQuantidadeAtual());
        assertEquals("Entrada via nota fiscal #" + nota.getId(), ultimaMovimentacao.getDescricao());
        assertEquals(0, ultimaMovimentacao.getSaldoAnterior());
        assertEquals(7, ultimaMovimentacao.getSaldoPosterior());
        assertEquals(gerenteEstoque.getRu(), ultimaMovimentacao.getUsuarioResponsavelRu());
    }

    @Test
    void registrarCotacaoNoMesmoMesMantemApenasUmRegistroPorProduto() {
        String sufixo = String.valueOf(System.nanoTime());
        Produto produto = estoqueService.cadastrarProduto(
                gerenteEstoque,
                "Cotado Wave2 " + sufixo,
                5,
                1,
                BigDecimal.valueOf(9.50)
        );
        YearMonth mes = YearMonth.of(2026, 4);

        CotacaoMensal primeira = cotacaoService.registrarCotacao(
                gerenteEstoque,
                produto,
                mes,
                BigDecimal.valueOf(10.10),
                12
        );
        CotacaoMensal atualizada = cotacaoService.registrarCotacao(
                gerenteEstoque,
                produto,
                mes,
                BigDecimal.valueOf(11.25),
                20
        );

        assertEquals(primeira.getId(), atualizada.getId());
        assertEquals(1, cotacaoService.buscarHistorico(produto).size());
        assertEquals(0, BigDecimal.valueOf(11.25).compareTo(
                cotacaoService.buscarHistorico(produto).get(0).getPrecoUnitario()
        ));
    }

    private static String gerarCnpjValido(long base) {
        String raiz = String.format("%012d", Math.abs(base % 1_000_000_000_000L));
        if (raiz.chars().distinct().count() == 1) {
            raiz = "123456780001";
        }
        int digito1 = calcularDigitoCnpj(raiz, new int[]{5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2});
        int digito2 = calcularDigitoCnpj(raiz + digito1, new int[]{6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2});
        return raiz + digito1 + digito2;
    }

    private static int calcularDigitoCnpj(String base, int[] pesos) {
        int soma = 0;
        for (int i = 0; i < pesos.length; i++) {
            soma += Character.getNumericValue(base.charAt(i)) * pesos[i];
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }
}
