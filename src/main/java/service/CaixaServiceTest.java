package service;

import model.*;
import repository.*;

import java.time.LocalDate;

public class CaixaServiceTest {

    public static void main(String[] args) {

        // ── Setup ──────────────────────────────────────────────
        CaixaRepository caixaRepo  = new InMemoryCaixaRepository();
        LogRepository logRepo      = new InMemoryLogRepository();
        CaixaService caixaService  = new CaixaService(caixaRepo, logRepo);

        ClasseFuncionario classeOp = new ClasseFuncionario(1, "Operador", "PDV");
        classeOp.adicionarPermissao(Permissao.VER_ESTOQUE);
        classeOp.adicionarPermissao(Permissao.VER_FINANCAS);

        Usuario joao  = new Usuario(1, "João",  "Silva", "111.111.111-11", "1234", classeOp, Usuario.Perfil.OPERADOR);
        Usuario maria = new Usuario(2, "Maria", "Lima",  "222.222.222-22", "5678", classeOp, Usuario.Perfil.OPERADOR);

        // ── Abrir caixas ───────────────────────────────────────
        Caixa caixa1 = caixaService.abrirCaixa(joao,  1, 100.00);
        Caixa caixa2 = caixaService.abrirCaixa(maria, 2, 150.00);

        assertCondition(caixa1.getStatus() == Caixa.Status.ABERTO, "Caixa 1 deveria estar ABERTO.");
        assertCondition(caixa1.getSaldoAtual() == 100.00, "Saldo inicial caixa 1 deveria ser 100.");

        // ── Caixa duplicado deve falhar ─────────────────────────
        boolean lancouExcecao = false;
        try {
            caixaService.abrirCaixa(joao, 1, 50.00);
        } catch (IllegalStateException e) {
            lancouExcecao = true;
        }
        assertCondition(lancouExcecao, "Deveria impedir abrir caixa já aberto.");

        // ── Registrar movimentações ────────────────────────────
        caixaService.registrarVenda(joao, 1, 25.00, "Venda de laranjas");
        caixaService.registrarVenda(joao, 1, 18.50, "Venda de alface");
        caixaService.registrarSangria(joao, 1, 50.00, "Envio ao cofre");

        assertCondition(caixa1.getSaldoAtual() == 93.50,
                "Saldo deveria ser 100 + 25 + 18.50 - 50 = 93.50");
        assertCondition(caixa1.calcularTotalVendas() == 43.50,
                "Total de vendas deveria ser 43.50");

        // ── Saldo insuficiente deve falhar ─────────────────────
        lancouExcecao = false;
        try {
            caixaService.registrarSangria(joao, 1, 500.00, "Sangria inválida");
        } catch (IllegalArgumentException e) {
            lancouExcecao = true;
        }
        assertCondition(lancouExcecao, "Deveria impedir sangria maior que saldo.");

        // ── Movimentações no caixa 2 ───────────────────────────
        caixaService.registrarVenda(maria, 2, 60.00, "Venda de morangos");
        caixaService.registrarVenda(maria, 2, 30.00, "Venda de uvas");

        // ── Encerrar e gerar fechamento ────────────────────────
        FechamentoCaixa fechamento1 = caixaService.encerrarCaixa(joao, 1);
        FechamentoCaixa fechamento2 = caixaService.encerrarCaixa(maria, 2);

        assertCondition(caixa1.getStatus() == Caixa.Status.ENCERRADO, "Caixa 1 deveria estar ENCERRADO.");
        assertCondition(fechamento1.getTotalVendas() == 43.50, "Total vendas caixa 1 deveria ser 43.50.");
        assertCondition(fechamento2.getTotalVendas() == 90.00, "Total vendas caixa 2 deveria ser 90.00.");

        System.out.println(fechamento1.gerarResumo());
        System.out.println(fechamento2.gerarResumo());

        // ── Consolidação do dia ────────────────────────────────
        String consolidado = caixaService.consolidarDia(LocalDate.now());
        System.out.println(consolidado);

        // Verifica que o consolidado somou os dois caixas
        assertCondition(
                fechamento1.getTotalVendas() + fechamento2.getTotalVendas() == 133.50,
                "Total consolidado de vendas deveria ser R$133.50."
        );

        System.out.println("Todos os testes de CaixaService passaram.");
    }

    private static void assertCondition(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

