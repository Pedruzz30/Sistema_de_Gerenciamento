package service;

import model.*;
import repository.*;

public class FornecedorServiceTest {

    public static void main(String[] args) {

        // ── Setup ──────────────────────────────────────────────
        FornecedorRepository fornecedorRepo = new InMemoryFornecedorRepository();
        LogRepository logRepo               = new InMemoryLogRepository();
        EstoqueService estoqueService        = new EstoqueService();
        FornecedorService fornecedorService  = new FornecedorService(fornecedorRepo, logRepo);

        // Usuário com permissão de gestão
        ClasseFuncionario classeSuperior = new ClasseFuncionario(1, "Superior", "Acesso total");
        classeSuperior.adicionarPermissao(Permissao.GERENCIAR_FUNCIONARIOS);
        classeSuperior.adicionarPermissao(Permissao.EDITAR_ESTOQUE);

        Usuario superior = new Usuario(
                1, "Pedro", "Henrique", "000.000.000-00",
                "senha123", classeSuperior, Usuario.Perfil.ADMIN
        );

        // ── Cadastro ───────────────────────────────────────────
        Fornecedor hortifruit = fornecedorService.cadastrarFornecedor(
                superior,
                "Hortifruit Central",
                "12.345.678/0001-99",
                "(11) 99999-0000"
        );
        assertCondition(hortifruit.isAtivo(), "Fornecedor deveria estar ativo após cadastro.");
        assertCondition(hortifruit.getId() > 0, "Fornecedor deveria ter ID gerado.");

        // ── CNPJ duplicado deve falhar ─────────────────────────
        boolean lancouExcecao = false;
        try {
            fornecedorService.cadastrarFornecedor(
                    superior, "Outro Nome", "12.345.678/0001-99", ""
            );
        } catch (IllegalArgumentException e) {
            lancouExcecao = true;
        }
        assertCondition(lancouExcecao, "Deveria impedir CNPJ duplicado.");

        // ── Vincular produtos ──────────────────────────────────
        Produto laranja = estoqueService.cadastrarProduto("Laranja", 0, 10, 4.99);
        Produto alface  = estoqueService.cadastrarProduto("Alface",  0, 5,  2.50);

        fornecedorService.vincularProduto(superior, hortifruit.getId(), laranja);
        fornecedorService.vincularProduto(superior, hortifruit.getId(), alface);
        assertCondition(hortifruit.getProdutos().size() == 2,
                "Fornecedor deveria ter 2 produtos vinculados.");

        // ── Produto duplicado deve falhar ──────────────────────
        lancouExcecao = false;
        try {
            fornecedorService.vincularProduto(superior, hortifruit.getId(), laranja);
        } catch (IllegalArgumentException e) {
            lancouExcecao = true;
        }
        assertCondition(lancouExcecao, "Deveria impedir produto duplicado no fornecedor.");

        // ── Desativar e reativar ───────────────────────────────
        fornecedorService.desativarFornecedor(superior, hortifruit.getId());
        assertCondition(!hortifruit.isAtivo(), "Fornecedor deveria estar inativo.");
        assertCondition(fornecedorService.listarAtivos().isEmpty(),
                "Lista de ativos deveria estar vazia.");

        fornecedorService.reativarFornecedor(superior, hortifruit.getId());
        assertCondition(hortifruit.isAtivo(), "Fornecedor deveria estar ativo novamente.");
        assertCondition(fornecedorService.listarAtivos().size() == 1,
                "Lista de ativos deveria ter 1 fornecedor.");

        System.out.println("Todos os testes de FornecedorService passaram.");
    }

    private static void assertCondition(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
