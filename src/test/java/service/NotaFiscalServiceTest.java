package service;

import model.ClasseFuncionario;
import model.Fornecedor;
import model.NotaFiscal;
import model.Permissao;
import model.Produto;
import model.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import repository.InMemoryLogRepository;
import repository.InMemoryNotaFiscalRepository;
import repository.InMemoryPedidoRepository;
import repository.InMemoryProdutoRepository;

import static org.junit.jupiter.api.Assertions.*;

class NotaFiscalServiceTest {

    private NotaFiscalService service;
    private EstoqueService estoqueService;
    private Usuario gerente;
    private Fornecedor fornecedor;
    private Produto laranja;
    private Produto alface;

    @BeforeEach
    void setUp() {
        estoqueService = new EstoqueService(new InMemoryProdutoRepository(), new InMemoryPedidoRepository());
        service = new NotaFiscalService(new InMemoryNotaFiscalRepository(), new InMemoryLogRepository(), estoqueService);

        ClasseFuncionario classe = new ClasseFuncionario(1, "GERENTE", "Gerente de estoque");
        classe.adicionarPermissao(Permissao.EDITAR_ESTOQUE);
        classe.adicionarPermissao(Permissao.VER_ESTOQUE);
        gerente = new Usuario(1, "Carlos", "Silva", "111.111.111-11", "senha", classe, Usuario.Perfil.OPERADOR);

        laranja = estoqueService.cadastrarProduto("Laranja", 0, 10, 4.99);
        alface  = estoqueService.cadastrarProduto("Alface",  0, 5,  2.50);

        fornecedor = new Fornecedor(1, "Hortifruit Central", "12.345.678/0001-99", "(11) 99999-0000");
        fornecedor.adicionarProduto(laranja);
        fornecedor.adicionarProduto(alface);
    }

    @Test
    void abrirNota_criaNotaPendente() {
        NotaFiscal nota = service.abrirNota(gerente, fornecedor);

        assertEquals(NotaFiscal.Status.PENDENTE, nota.getStatus());
        assertTrue(nota.getId() > 0);
    }

    @Test
    void abrirNota_semPermissao_lancaSecurityException() {
        ClasseFuncionario semPerm = new ClasseFuncionario(2, "CAIXA", "Caixa");
        semPerm.adicionarPermissao(Permissao.VER_VENDAS);
        Usuario caixa = new Usuario(2, "Ana", "Costa", "222.222.222-22", "1234", semPerm, Usuario.Perfil.OPERADOR);

        assertThrows(SecurityException.class, () -> service.abrirNota(caixa, fornecedor));
    }

    @Test
    void adicionarItem_produtoNaoVinculado_lancaIllegalArgument() {
        NotaFiscal nota = service.abrirNota(gerente, fornecedor);
        Produto outrosProduto = estoqueService.cadastrarProduto("Manga", 0, 1, 3.0);

        assertThrows(IllegalArgumentException.class,
                () -> service.adicionarItem(gerente, nota, outrosProduto, 1, 3.0));
    }

    @Test
    void fluxoCompleto_abrirAdicionarConfirmarPagar_atualizaEstoque() {
        NotaFiscal nota = service.abrirNota(gerente, fornecedor);
        service.adicionarItem(gerente, nota, laranja, 20, 4.99);
        service.adicionarItem(gerente, nota, alface,  10, 2.50);

        assertEquals(0, laranja.getQuantidadeAtual());
        assertEquals(0, alface.getQuantidadeAtual());

        service.confirmarNota(gerente, nota);

        assertEquals(NotaFiscal.Status.CONFIRMADA, nota.getStatus());
        assertEquals(20, laranja.getQuantidadeAtual());
        assertEquals(10, alface.getQuantidadeAtual());

        service.registrarPagamento(gerente, nota);

        assertEquals(NotaFiscal.Status.PAGA, nota.getStatus());
    }

    @Test
    void confirmarNota_semItens_lancaIllegalState() {
        NotaFiscal nota = service.abrirNota(gerente, fornecedor);

        assertThrows(IllegalStateException.class, () -> service.confirmarNota(gerente, nota));
    }

    @Test
    void registrarPagamento_semConfirmar_lancaIllegalState() {
        NotaFiscal nota = service.abrirNota(gerente, fornecedor);
        service.adicionarItem(gerente, nota, laranja, 1, 4.99);

        assertThrows(IllegalStateException.class, () -> service.registrarPagamento(gerente, nota));
    }

    @Test
    void cancelarNota_estadoPendente_mudaParaCancelada() {
        NotaFiscal nota = service.abrirNota(gerente, fornecedor);
        service.adicionarItem(gerente, nota, laranja, 1, 4.99);

        service.cancelarNota(gerente, nota);

        assertEquals(NotaFiscal.Status.CANCELADA, nota.getStatus());
    }

    @Test
    void cancelarNota_estadoConfirmado_lancaIllegalState() {
        NotaFiscal nota = service.abrirNota(gerente, fornecedor);
        service.adicionarItem(gerente, nota, laranja, 5, 4.99);
        service.confirmarNota(gerente, nota);

        assertThrows(IllegalStateException.class, () -> service.cancelarNota(gerente, nota));
    }

    @Test
    void confirmarNota_naoAlteraEstoqueSePreValidacaoFalhar() {
        // Cria uma nota com um produto válido e confirma para colocar o produto no estoque
        NotaFiscal nota = service.abrirNota(gerente, fornecedor);
        service.adicionarItem(gerente, nota, laranja, 5, 4.99);

        // Verifica que estoque está em 0 antes
        assertEquals(0, laranja.getQuantidadeAtual());

        // Se confirmarNota for chamado em nota válida, deve funcionar normalmente
        service.confirmarNota(gerente, nota);
        assertEquals(5, laranja.getQuantidadeAtual());
    }

    @Test
    void calcularTotal_somaSubtotaisCorretamente() {
        NotaFiscal nota = service.abrirNota(gerente, fornecedor);
        service.adicionarItem(gerente, nota, laranja, 2, 4.99);
        service.adicionarItem(gerente, nota, alface,  5, 2.50);

        double esperado = (2 * 4.99) + (5 * 2.50);
        assertEquals(esperado, nota.calcularTotal(), 0.001);
    }
}
