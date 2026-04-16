package service;

import model.ClasseFuncionario;
import model.Fornecedor;
import model.Permissao;
import model.Produto;
import model.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import repository.InMemoryFornecedorRepository;
import repository.InMemoryLogRepository;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FornecedorServiceTest {

    private FornecedorService service;
    private Usuario gerente;
    private Usuario semPermissao;

    @BeforeEach
    void setUp() {
        service = new FornecedorService(new InMemoryFornecedorRepository(), new InMemoryLogRepository());

        ClasseFuncionario classeGerente = new ClasseFuncionario(1, "GERENTE", "Gerencia fornecedores");
        classeGerente.adicionarPermissao(Permissao.GERENCIAR_FUNCIONARIOS);
        gerente = new Usuario(1, "Carlos", "Silva", "529.982.247-25", "1234", classeGerente, Usuario.Perfil.OPERADOR);

        ClasseFuncionario classeSemPermissao = new ClasseFuncionario(2, "CAIXA", "Sem acesso");
        classeSemPermissao.adicionarPermissao(Permissao.VER_VENDAS);
        semPermissao = new Usuario(2, "Ana", "Costa", "111.444.777-35", "1234",
                classeSemPermissao, Usuario.Perfil.OPERADOR);
    }

    @Test
    void cadastrarFornecedor_comDadosValidos_retornaFornecedorPersistidoComId() {
        Fornecedor fornecedor = service.cadastrarFornecedor(
                gerente,
                "Hortifruit Central",
                "12.345.678/0001-95",
                "(11) 99999-0000"
        );

        assertTrue(fornecedor.getId() > 0);
        assertEquals("Hortifruit Central", fornecedor.getNome());
        assertEquals("12.345.678/0001-95", fornecedor.getCnpj());
        assertEquals(1, service.listarTodos().size());
    }

    @Test
    void cadastrarFornecedor_comCnpjDuplicado_lancaIllegalArgumentException() {
        service.cadastrarFornecedor(gerente, "Fornecedor A", "12.345.678/0001-95", "(11) 99999-0000");

        assertThrows(IllegalArgumentException.class,
                () -> service.cadastrarFornecedor(gerente, "Fornecedor B", "12345678000195", "(11) 98888-0000"));
    }

    @Test
    void vincularDesativarEReativarFornecedor_preservaEstadoEsperado() {
        Fornecedor fornecedor = service.cadastrarFornecedor(
                gerente,
                "Distribuidora Sul",
                "45.723.174/0001-10",
                "(41) 3333-4444"
        );
        Produto produto = new Produto(1, "Arroz", 10, 2, BigDecimal.valueOf(25.90));

        service.vincularProduto(gerente, fornecedor.getId(), produto);
        assertTrue(service.buscarPorId(fornecedor.getId()).possuiProduto(produto.getId()));

        service.desativarFornecedor(gerente, fornecedor.getId());
        assertFalse(service.buscarPorId(fornecedor.getId()).isAtivo());
        assertTrue(service.listarAtivos().isEmpty());

        service.reativarFornecedor(gerente, fornecedor.getId());
        assertTrue(service.buscarPorId(fornecedor.getId()).isAtivo());
        assertEquals(1, service.listarAtivos().size());
    }

    @Test
    void cadastrarFornecedor_semPermissao_lancaSecurityException() {
        assertThrows(SecurityException.class,
                () -> service.cadastrarFornecedor(semPermissao, "Bloqueado", "12.345.678/0001-95", ""));
    }
}
