package service;

import model.ClasseFuncionario;
import model.Fornecedor;
import model.NotaFiscal;
import model.Permissao;
import model.Produto;
import model.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import repository.InMemoryFornecedorRepository;
import repository.InMemoryLogRepository;
import repository.InMemoryNotaFiscalRepository;
import repository.InMemoryPedidoRepository;
import repository.InMemoryProdutoRepository;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class NotaFiscalServiceTest {

    private NotaFiscalService service;
    private EstoqueService estoqueService;
    private FornecedorService fornecedorService;
    private Usuario gerente;
    private Usuario semPermissao;
    private Fornecedor fornecedor;
    private Produto laranja;
    private Produto alface;

    @BeforeEach
    void setUp() {
        estoqueService = new EstoqueService(new InMemoryProdutoRepository(), new InMemoryPedidoRepository());
        fornecedorService = new FornecedorService(new InMemoryFornecedorRepository(), new InMemoryLogRepository());
        service = new NotaFiscalService(
                new InMemoryNotaFiscalRepository(),
                new InMemoryLogRepository(),
                estoqueService,
                fornecedorService
        );

        ClasseFuncionario classe = new ClasseFuncionario(1, "GERENTE", "Gerente de estoque");
        classe.adicionarPermissao(Permissao.EDITAR_ESTOQUE);
        classe.adicionarPermissao(Permissao.VER_ESTOQUE);
        classe.adicionarPermissao(Permissao.GERENCIAR_FUNCIONARIOS);
        gerente = new Usuario(1, "Carlos", "Silva", "529.982.247-25", "senha", classe, Usuario.Perfil.OPERADOR);

        ClasseFuncionario classeSemPermissao = new ClasseFuncionario(2, "CAIXA", "Caixa");
        classeSemPermissao.adicionarPermissao(Permissao.VER_VENDAS);
        semPermissao = new Usuario(2, "Ana", "Costa", "111.444.777-35", "1234",
                classeSemPermissao, Usuario.Perfil.OPERADOR);

        laranja = estoqueService.cadastrarProduto(gerente, "Laranja", 0, 10, 4.99);
        alface = estoqueService.cadastrarProduto(gerente, "Alface", 0, 5, 2.50);

        fornecedor = fornecedorService.cadastrarFornecedor(
                gerente,
                "Hortifruit Central",
                "12.345.678/0001-95",
                "(11) 99999-0000"
        );
        fornecedorService.vincularProduto(gerente, fornecedor.getId(), laranja);
        fornecedorService.vincularProduto(gerente, fornecedor.getId(), alface);
    }

    @Test
    void abrirNota_criaNotaPendente() {
        NotaFiscal nota = service.abrirNota(gerente, fornecedor);

        assertEquals(NotaFiscal.Status.PENDENTE, nota.getStatus());
        assertTrue(nota.getId() > 0);
    }

    @Test
    void abrirNota_semPermissao_lancaSecurityException() {
        assertThrows(SecurityException.class, () -> service.abrirNota(semPermissao, fornecedor));
    }

    @Test
    void adicionarItem_produtoNaoVinculado_criaVinculoAutomaticamente() {
        NotaFiscal nota = service.abrirNota(gerente, fornecedor);
        Produto manga = estoqueService.cadastrarProduto(gerente, "Manga", 0, 1, 3.0);

        service.adicionarItem(gerente, nota, manga, 1, valor(3.0));

        assertTrue(fornecedor.possuiProduto(manga.getId()));
        assertEquals(1, nota.getItens().size());
        assertEquals(manga.getId(), nota.getItens().get(0).getProduto().getId());
    }

    @Test
    void adicionarItem_novoProdutoInline_criaProdutoEVinculoSemDuplicar() {
        NotaFiscal nota = service.abrirNota(gerente, fornecedor);

        Produto criado = service.adicionarItem(
                gerente,
                nota,
                new NotaFiscalService.NovoProdutoInput("Arroz Integral 5kg", 5, valor(25.90), "SECO"),
                20,
                valor(25.90)
        );

        assertNotNull(criado);
        assertTrue(criado.getId() > 0);
        assertEquals("Arroz Integral 5kg", criado.getNome());
        assertTrue(fornecedor.possuiProduto(criado.getId()));
        assertEquals(1, nota.getItens().size());
        assertEquals(criado.getId(), nota.getItens().get(0).getProduto().getId());

        Produto reaproveitado = service.adicionarItem(
                gerente,
                nota,
                new NotaFiscalService.NovoProdutoInput("Arroz Integral 5kg", 5, valor(25.90), "SECO"),
                5,
                valor(25.90)
        );

        assertEquals(criado.getId(), reaproveitado.getId());
        assertEquals(1, estoqueService.listarProdutos().stream()
                .filter(produto -> produto.getNome().equalsIgnoreCase("Arroz Integral 5kg"))
                .count());
    }

    @Test
    void fluxoCompleto_abrirAdicionarConfirmarPagar_atualizaEstoque() {
        NotaFiscal nota = service.abrirNota(gerente, fornecedor);
        service.adicionarItem(gerente, nota, laranja, 20, valor(4.99));
        service.adicionarItem(gerente, nota, alface, 10, valor(2.50));

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
    void adicionarItem_semPermissao_lancaSecurityException() {
        NotaFiscal nota = service.abrirNota(gerente, fornecedor);

        assertThrows(SecurityException.class,
                () -> service.adicionarItem(semPermissao, nota, laranja, 1, valor(4.99)));
    }

    @Test
    void confirmarNota_semPermissao_lancaSecurityException() {
        NotaFiscal nota = service.abrirNota(gerente, fornecedor);
        service.adicionarItem(gerente, nota, laranja, 1, valor(4.99));

        assertThrows(SecurityException.class,
                () -> service.confirmarNota(semPermissao, nota));
    }

    @Test
    void registrarPagamento_semPermissao_lancaSecurityException() {
        NotaFiscal nota = service.abrirNota(gerente, fornecedor);
        service.adicionarItem(gerente, nota, laranja, 1, valor(4.99));
        service.confirmarNota(gerente, nota);

        assertThrows(SecurityException.class,
                () -> service.registrarPagamento(semPermissao, nota));
    }

    @Test
    void confirmarNota_semItens_lancaIllegalState() {
        NotaFiscal nota = service.abrirNota(gerente, fornecedor);

        assertThrows(IllegalStateException.class, () -> service.confirmarNota(gerente, nota));
    }

    @Test
    void registrarPagamento_semConfirmar_lancaIllegalState() {
        NotaFiscal nota = service.abrirNota(gerente, fornecedor);
        service.adicionarItem(gerente, nota, laranja, 1, valor(4.99));

        assertThrows(IllegalStateException.class, () -> service.registrarPagamento(gerente, nota));
    }

    @Test
    void cancelarNota_estadoPendente_mudaParaCancelada() {
        NotaFiscal nota = service.abrirNota(gerente, fornecedor);
        service.adicionarItem(gerente, nota, laranja, 1, valor(4.99));

        service.cancelarNota(gerente, nota);

        assertEquals(NotaFiscal.Status.CANCELADA, nota.getStatus());
    }

    @Test
    void cancelarNota_estadoConfirmado_lancaIllegalState() {
        NotaFiscal nota = service.abrirNota(gerente, fornecedor);
        service.adicionarItem(gerente, nota, laranja, 5, valor(4.99));
        service.confirmarNota(gerente, nota);

        assertThrows(IllegalStateException.class, () -> service.cancelarNota(gerente, nota));
    }

    @Test
    void descartarRascunho_semPermissao_lancaSecurityException() {
        NotaFiscal nota = service.abrirNota(gerente, fornecedor);

        assertThrows(SecurityException.class,
                () -> service.descartarRascunho(semPermissao, nota));
    }

    @Test
    void cancelarNota_semPermissao_lancaSecurityException() {
        NotaFiscal nota = service.abrirNota(gerente, fornecedor);
        service.adicionarItem(gerente, nota, laranja, 1, valor(4.99));

        assertThrows(SecurityException.class,
                () -> service.cancelarNota(semPermissao, nota));
    }

    @Test
    void confirmarNota_naoAlteraEstoqueSePreValidacaoFalhar() {
        NotaFiscal nota = service.abrirNota(gerente, fornecedor);
        service.adicionarItem(gerente, nota, laranja, 5, valor(4.99));

        assertEquals(0, laranja.getQuantidadeAtual());

        service.confirmarNota(gerente, nota);
        assertEquals(5, laranja.getQuantidadeAtual());
    }

    @Test
    void calcularTotal_somaSubtotaisCorretamente() {
        NotaFiscal nota = service.abrirNota(gerente, fornecedor);
        service.adicionarItem(gerente, nota, laranja, 2, valor(4.99));
        service.adicionarItem(gerente, nota, alface, 5, valor(2.50));

        BigDecimal esperado = valor(22.48);
        assertEquals(0, esperado.compareTo(nota.calcularTotal()));
    }

    private BigDecimal valor(double valor) {
        return BigDecimal.valueOf(valor);
    }
}
