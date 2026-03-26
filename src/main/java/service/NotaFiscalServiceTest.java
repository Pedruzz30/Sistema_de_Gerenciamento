package service;

import model.*;
import repository.*;

public class NotaFiscalServiceTest {

    public static void main(String[] args) {

        // ── Setup ──────────────────────────────────────────────
        LogRepository logRepo        = new InMemoryLogRepository();
        EstoqueService estoqueService = new EstoqueService(
                new InMemoryProdutoRepository(),
                new InMemoryPedidoRepository()
        );
        NotaFiscalRepository notaRepo = new InMemoryNotaFiscalRepository();
        NotaFiscalService notaService = new NotaFiscalService(notaRepo, logRepo, estoqueService);

        // Usuário com permissão
        ClasseFuncionario classeGerente = new ClasseFuncionario(1, "Gerente", "Gerente de estoque");
        classeGerente.adicionarPermissao(Permissao.EDITAR_ESTOQUE);
        classeGerente.adicionarPermissao(Permissao.VER_ESTOQUE);

        Usuario gerente = new Usuario(
                1, "Carlos", "Silva", "111.111.111-11",
                "senha123", classeGerente, Usuario.Perfil.OPERADOR
        );

        // Produtos no estoque
        Produto laranja = estoqueService.cadastrarProduto("Laranja", 0, 10, 4.99);
        Produto alface  = estoqueService.cadastrarProduto("Alface",  0, 5,  2.50);

        // Fornecedor com produtos vinculados
        Fornecedor hortifruit = new Fornecedor(1, "Hortifruit Central", "12.345.678/0001-99", "(11) 99999-0000");
        hortifruit.adicionarProduto(laranja);
        hortifruit.adicionarProduto(alface);

        // ── Etapa 1: Abrir nota ────────────────────────────────
        NotaFiscal nota = notaService.abrirNota(gerente, hortifruit);
        assertCondition(nota.getStatus() == NotaFiscal.Status.PENDENTE,
                "Nota deveria começar PENDENTE.");

        // ── Etapa 2: Adicionar itens ───────────────────────────
        notaService.adicionarItem(gerente, nota, laranja, 2, 4.99); // 2 caixas
        notaService.adicionarItem(gerente, nota, alface,  5, 2.50); // 5 caixas
        assertCondition(nota.getItens().size() == 2,
                "Nota deveria ter 2 itens.");
        assertCondition(nota.calcularTotal() == (2 * 4.99) + (5 * 2.50),
                "Total da nota incorreto.");

        // ── Etapa 3: Confirmar (entra no estoque) ─────────────
        notaService.confirmarNota(gerente, nota);
        assertCondition(nota.getStatus() == NotaFiscal.Status.CONFIRMADA,
                "Nota deveria estar CONFIRMADA.");
        assertCondition(laranja.getQuantidadeAtual() == 2,
                "Estoque de laranja deveria ser 2.");
        assertCondition(alface.getQuantidadeAtual() == 5,
                "Estoque de alface deveria ser 5.");

        // ── Etapa 4: Pagamento ─────────────────────────────────
        notaService.registrarPagamento(gerente, nota);
        assertCondition(nota.getStatus() == NotaFiscal.Status.PAGA,
                "Nota deveria estar PAGA.");

        // ── Testa que não pode pular etapas ───────────────────
        NotaFiscal nota2 = notaService.abrirNota(gerente, hortifruit);
        notaService.adicionarItem(gerente, nota2, laranja, 1, 4.99);
        boolean lancouExcecao = false;
        try {
            notaService.registrarPagamento(gerente, nota2); // pula confirmar()
        } catch (IllegalStateException e) {
            lancouExcecao = true;
        }
        assertCondition(lancouExcecao, "Deveria impedir pagamento sem confirmação.");

        System.out.println("Todos os testes de NotaFiscalService passaram.");
        System.out.println(nota);
    }

    private static void assertCondition(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}