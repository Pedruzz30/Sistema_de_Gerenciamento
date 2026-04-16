package service;

import java.math.BigDecimal;
import model.ClasseFuncionario;
import model.CategoriaEstoque;
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
    private InMemoryProdutoRepository produtoRepository;
    private Usuario operador;
    private Usuario semPermissao;

    @BeforeEach
    void setUp() {
        produtoRepository = new InMemoryProdutoRepository();
        service = new EstoqueService(produtoRepository, new InMemoryPedidoRepository());

        ClasseFuncionario classe = new ClasseFuncionario(1, "ESTOQUISTA", "Testes");
        classe.adicionarPermissao(Permissao.VER_ESTOQUE);
        classe.adicionarPermissao(Permissao.EDITAR_ESTOQUE);
        operador = new Usuario(1, "Teste", "User", "390.533.447-05", "1234", classe, Usuario.Perfil.OPERADOR);

        ClasseFuncionario classeSemPermissao = new ClasseFuncionario(2, "CAIXA", "Sem acesso ao estoque");
        classeSemPermissao.adicionarPermissao(Permissao.VER_VENDAS);
        semPermissao = new Usuario(2, "Sem", "Permissao", "111.444.777-35", "1234",
                classeSemPermissao, Usuario.Perfil.OPERADOR);
    }

    @Test
    void cadastrarProduto_comDadosValidos_retornaProdutoComId() {
        Produto p = service.cadastrarProduto(operador, "Teclado", 10, 5, 150.0, CategoriaEstoque.SECO);

        assertTrue(p.getId() > 0);
        assertEquals("Teclado", p.getNome());
        assertEquals(10, p.getQuantidadeAtual());
        assertEquals(CategoriaEstoque.SECO, p.getCategoriaEstoque());
    }

    @Test
    void cadastrarProduto_comNomeVazio_lancaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> service.cadastrarProduto(operador, "", 5, 2, 10.0));
    }

    @Test
    void cadastrarProduto_comValoresNegativos_lancaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> service.cadastrarProduto(operador, "Produto", -1, 0, 0.0));
    }

    @Test
    void registrarEntrada_aumentaEstoque() {
        Produto p = service.cadastrarProduto(operador, "Mouse", 10, 5, 50.0);

        Pedido pedido = service.registrarMovimentacao(p.getId(), 5, TipoMovimentacao.ENTRADA, operador);

        assertEquals(15, p.getQuantidadeAtual());
        assertEquals(TipoMovimentacao.ENTRADA, pedido.getTipo());
        assertEquals(5, pedido.getQuantidade());
    }

    @Test
    void registrarMovimentacao_preencheHistoricoComSaldosDescricaoEResponsavel() {
        Produto p = service.cadastrarProduto(operador, "Cadeira", 10, 2, 120.0);

        Pedido pedido = service.registrarMovimentacao(
                p.getId(),
                3,
                TipoMovimentacao.ENTRADA,
                operador,
                "Reposicao do almoxarifado"
        );

        assertEquals(10, pedido.getSaldoAnterior());
        assertEquals(13, pedido.getSaldoPosterior());
        assertEquals("Reposicao do almoxarifado", pedido.getDescricao());
        assertEquals(operador.getRu(), pedido.getUsuarioResponsavelRu());
        assertEquals(operador.getNomeCompleto(), pedido.getUsuarioResponsavelNome());
    }

    @Test
    void registrarSaida_diminuiEstoque() {
        Produto p = service.cadastrarProduto(operador, "Monitor", 20, 5, 800.0);

        service.registrarMovimentacao(p.getId(), 8, TipoMovimentacao.SAIDA, operador);

        assertEquals(12, p.getQuantidadeAtual());
    }

    @Test
    void registrarSaida_comEstoqueInsuficiente_lancaIllegalArgument() {
        Produto p = service.cadastrarProduto(operador, "Webcam", 3, 1, 200.0);

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
        Produto p = service.cadastrarProduto(operador, "Produto Vazio", 0, 10, 5.0);

        assertEquals(NivelEstoque.SEM_ESTOQUE, service.calcularNivelEstoque(p));
    }

    @Test
    void calcularNivelEstoque_acimaDominimo_retornaAdequado() {
        Produto p = service.cadastrarProduto(operador, "Produto OK", 100, 10, 5.0);

        assertEquals(NivelEstoque.ADEQUADO, service.calcularNivelEstoque(p));
    }

    @Test
    void listarProdutos_aposMultiplosCadastros_retornaTodos() {
        service.cadastrarProduto(operador, "A", 1, 1, 1.0);
        service.cadastrarProduto(operador, "B", 2, 1, 2.0);
        service.cadastrarProduto(operador, "C", 3, 1, 3.0);

        assertEquals(3, service.listarProdutos().size());
    }

    @Test
    void cadastrarProduto_semPermissao_lancaSecurityException() {
        assertThrows(SecurityException.class,
                () -> service.cadastrarProduto(semPermissao, "Bloqueado", 1, 1, 1.0));
    }

    @Test
    void cadastrarProduto_dePizzaSobDemanda_lancaIllegalArgument() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.cadastrarProduto(operador, "Pizza Calabresa Broto", 10, 1, 29.90));

        assertTrue(ex.getMessage().contains("sob demanda"));
    }

    @Test
    void cadastrarProduto_semCategoria_permaneceCompativel() {
        Produto p = service.cadastrarProduto(operador, "Legado", 2, 1, 4.0);

        assertNull(p.getCategoriaEstoque());
    }

    @Test
    void registrarMovimentacao_semPermissao_lancaSecurityException() {
        Produto p = service.cadastrarProduto(operador, "Produto", 10, 1, 10.0);

        assertThrows(SecurityException.class,
                () -> service.registrarMovimentacao(p.getId(), 1, TipoMovimentacao.SAIDA, semPermissao));
    }

    @Test
    void atualizarProduto_atualizaCategoriaSemAlterarQuantidadeAtual() {
        Produto p = service.cadastrarProduto(operador, "Suco", 10, 2, 6.0, CategoriaEstoque.BEBIDAS);

        Produto atualizado = service.atualizarProduto(
                operador,
                p.getId(),
                "Suco Integral",
                3,
                7.5,
                CategoriaEstoque.FRIO
        );

        assertEquals("Suco Integral", atualizado.getNome());
        assertEquals(10, atualizado.getQuantidadeAtual());
        assertEquals(3, atualizado.getQuantidadeMinima());
        assertEquals(0, BigDecimal.valueOf(7.5).compareTo(atualizado.getPrecoUnitario()));
        assertEquals(CategoriaEstoque.FRIO, atualizado.getCategoriaEstoque());
    }

    @Test
    void listarProdutos_eMovimentacao_ignoramItensSobDemanda() {
        Produto bebida = service.cadastrarProduto(operador, "Suco", 10, 2, 6.0, CategoriaEstoque.BEBIDAS);
        Produto pizzaLegada = produtoRepository.salvar(
                new Produto(
                        0,
                        "Pizza Portuguesa Broto",
                        40,
                        5,
                        BigDecimal.valueOf(32.0),
                        CategoriaEstoque.CONGELADO,
                        Boolean.FALSE
                )
        );

        assertEquals(1, service.listarProdutos().size());
        assertTrue(service.buscarProdutoPorId(pizzaLegada.getId()).isEmpty());
        assertEquals(bebida.getId(), service.listarProdutos().get(0).getId());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.registrarMovimentacao(pizzaLegada.getId(), 1, TipoMovimentacao.SAIDA, operador));
        assertTrue(ex.getMessage().contains("nao e controlado por estoque"));
    }
}
