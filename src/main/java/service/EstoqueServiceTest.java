package service;

import model.ClasseFuncionario;
import model.Permissao;
import model.Produto;
import model.TipoMovimentacao;
import model.Usuario;
import repository.InMemoryPedidoRepository;
import repository.InMemoryProdutoRepository;

public class EstoqueServiceTest {

    public static void main(String[] args) {
        EstoqueService estoqueService = new EstoqueService(
                new InMemoryProdutoRepository(),
                new InMemoryPedidoRepository()
        );

        ClasseFuncionario classeAdminTeste = new ClasseFuncionario(100, "ADMIN_TESTE", "Classe para testes de estoque");
        classeAdminTeste.adicionarPermissao(Permissao.VER_ESTOQUE);
        classeAdminTeste.adicionarPermissao(Permissao.EDITAR_ESTOQUE);

        Usuario adminTeste = new Usuario(
                1,
                "Admin",
                "Teste",
                "999.999.999-99",
                "1234",
                classeAdminTeste,
                Usuario.Perfil.ADMIN
        );

        Produto produto = estoqueService.cadastrarProduto("Teclado", 10, 5, 150.0);

        estoqueService.registrarMovimentacao(produto.getId(), 5, TipoMovimentacao.ENTRADA, adminTeste);
        assertCondition(produto.getQuantidadeAtual() == 15, "Entrada deveria aumentar estoque para 15.");

        estoqueService.registrarMovimentacao(produto.getId(), 8, TipoMovimentacao.SAIDA, adminTeste);
        assertCondition(produto.getQuantidadeAtual() == 7, "Saída deveria reduzir estoque para 7.");

        boolean lancouExcecao = false;
        try {
            estoqueService.registrarMovimentacao(produto.getId(), 50, TipoMovimentacao.SAIDA, adminTeste);
        } catch (IllegalArgumentException ex) {
            lancouExcecao = true;
        }

        assertCondition(lancouExcecao, "Deveria lançar exceção quando não há saldo.");
        System.out.println("Todos os testes de EstoqueService passaram.");
    }

    private static void assertCondition(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}