package service;

import model.ClasseFuncionario;
import model.NivelEstoque;
import model.Pedido;
import model.Permissao;
import model.Produto;
import model.TipoMovimentacao;
import model.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import repository.InMemoryPedidoRepository;
import repository.InMemoryProdutoRepository;

import static org.junit.jupiter.api.Assertions.*;

class EstoqueServiceTest {

    private EstoqueService service;
    private Usuario operador;

    @BeforeEach
    void setUp() {
        service = new EstoqueService(new InMemoryProdutoRepository(), new InMemoryPedidoRepository());

        ClasseFuncionario classe = new ClasseFuncionario(1, "ESTOQUISTA", "Testes");
        classe.adicionarPermissao(Permissao.VER_ESTOQUE);
        classe.adicionarPermissao(Permissao.EDITAR_ESTOQUE);
        operador = new Usuario(1, "Teste", "User", "999.999.999-99", "1234", classe, Usuario.Perfil.OPERADOR);
    }

    @Test
    void cadastrarProduto_comDadosValidos_retornaProdutoComId() {
        Produto p = service.cadastrarProduto("Teclado", 10, 5, 150.0);

        assertTrue(p.getId() > 0);
        assertEquals("Teclado", p.getNome());
        assertEquals(10, p.getQuantidadeAtual());
    }

    @Test
    void cadastrarProduto_comNomeVazio_lancaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> service.cadastrarProduto("", 5, 2, 10.0));
    }

    @Test
    void cadastrarProduto_comValoresNegativos_lancaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> service.cadastrarProduto("Produto", -1, 0, 0.0));
    }

    @Test
    void registrarEntrada_aumentaEstoque() {
        Produto p = service.cadastrarProduto("Mouse", 10, 5, 50.0);

        Pedido pedido = service.registrarMovimentacao(p.getId(), 5, TipoMovimentacao.ENTRADA, operador);

        assertEquals(15, p.getQuantidadeAtual());
        assertEquals(TipoMovimentacao.ENTRADA, pedido.getTipo());
        assertEquals(5, pedido.getQuantidade());
    }

    @Test
    void registrarSaida_diminuiEstoque() {
        Produto p = service.cadastrarProduto("Monitor", 20, 5, 800.0);

        service.registrarMovimentacao(p.getId(), 8, TipoMovimentacao.SAIDA, operador);

        assertEquals(12, p.getQuantidadeAtual());
    }

    @Test
    void registrarSaida_comEstoqueInsuficiente_lancaIllegalArgument() {
        Produto p = service.cadastrarProduto("Webcam", 3, 1, 200.0);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.registrarMovimentacao(p.getId(), 50, TipoMovimentacao.SAIDA, operador));

        assertTrue(ex.getMessage().contains("insuficiente"));
    }

    @Test
    void registrarMovimentacao_comProdutoInexistente_lancaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> service.registrarMovimentacao(9999, 1, TipoMovimentacao.ENTRADA, operador));
    }

    @Test
    void calcularNivelEstoque_semEstoque_retornaSemEstoque() {
        Produto p = service.cadastrarProduto("Produto Vazio", 0, 10, 5.0);

        assertEquals(NivelEstoque.SEM_ESTOQUE, service.calcularNivelEstoque(p));
    }

    @Test
    void calcularNivelEstoque_acimaDominimo_retornaAdequado() {
        Produto p = service.cadastrarProduto("Produto OK", 100, 10, 5.0);

        assertEquals(NivelEstoque.ADEQUADO, service.calcularNivelEstoque(p));
    }

    @Test
    void listarProdutos_aposMultiplosCadastros_retornaTodos() {
        service.cadastrarProduto("A", 1, 1, 1.0);
        service.cadastrarProduto("B", 2, 1, 2.0);
        service.cadastrarProduto("C", 3, 1, 3.0);

        assertEquals(3, service.listarProdutos().size());
    }
}
