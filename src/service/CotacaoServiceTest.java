package service;

import model.*;
import repository.*;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

public class CotacaoServiceTest {

    public static void main(String[] args) {

        // ── Setup ──────────────────────────────────────────────
        CotacaoRepository cotacaoRepo = new InMemoryCotacaoRepository();
        LogRepository logRepo         = new InMemoryLogRepository();
        EstoqueService estoqueService  = new EstoqueService();
        CotacaoService cotacaoService  = new CotacaoService(cotacaoRepo, logRepo);

        ClasseFuncionario classeGerente = new ClasseFuncionario(1, "Gerente", "Acesso financeiro");
        classeGerente.adicionarPermissao(Permissao.VER_FINANCAS);
        classeGerente.adicionarPermissao(Permissao.EDITAR_ESTOQUE);

        Usuario gerente = new Usuario(
                1, "Pedro", "Henrique", "000.000.000-00",
                "senha123", classeGerente, Usuario.Perfil.ADMIN
        );

        Produto morango = estoqueService.cadastrarProduto("Morango", 0, 5, 4.99);
        Produto laranja = estoqueService.cadastrarProduto("Laranja", 0, 10, 3.50);

        YearMonth janeiro   = YearMonth.of(2025, 1);
        YearMonth fevereiro = YearMonth.of(2025, 2);
        YearMonth marco     = YearMonth.of(2025, 3);

        // ── Registrar cotações ─────────────────────────────────
        cotacaoService.registrarCotacao(gerente, morango, janeiro,   4.99, 20);
        cotacaoService.registrarCotacao(gerente, morango, fevereiro, 5.15, 18);
        cotacaoService.registrarCotacao(gerente, morango, marco,     4.89, 25);

        cotacaoService.registrarCotacao(gerente, laranja, janeiro,   3.50, 30);
        cotacaoService.registrarCotacao(gerente, laranja, fevereiro, 3.20, 35);

        // ── Comparar dois meses específicos ────────────────────
        Optional<ResultadoComparacao> resultado =
                cotacaoService.compararMeses(morango, janeiro, fevereiro);

        assertCondition(resultado.isPresent(), "Deveria ter resultado para jan→fev.");
        assertCondition(resultado.get().getTendencia() == ResultadoComparacao.Tendencia.SUBIU,
                "Morango deveria ter subido de janeiro para fevereiro.");
        System.out.println(resultado.get().gerarMensagem());

        // ── Comparar com mês anterior (atalho) ─────────────────
        Optional<ResultadoComparacao> marcoVsFev =
                cotacaoService.compararComMesAnterior(morango, marco);
        assertCondition(marcoVsFev.isPresent(), "Deveria ter resultado para fev→mar.");
        assertCondition(marcoVsFev.get().getTendencia() == ResultadoComparacao.Tendencia.CAIU,
                "Morango deveria ter caído de fevereiro para março.");
        System.out.println(marcoVsFev.get().gerarMensagem());

        // ── Sem cotação = Optional vazio ───────────────────────
        Optional<ResultadoComparacao> semDados =
                cotacaoService.compararComMesAnterior(morango, janeiro);
        assertCondition(semDados.isEmpty(),
                "Deveria retornar vazio se não há mês anterior cadastrado.");

        // ── Relatório mensal completo ──────────────────────────
        List<ResultadoComparacao> relatorio =
                cotacaoService.gerarRelatorioMensal(List.of(morango, laranja), fevereiro);

        assertCondition(relatorio.size() == 2, "Relatório deveria ter 2 produtos.");

        // O que subiu mais aparece primeiro (morango +3.2% vs laranja -8.5%)
        assertCondition(relatorio.get(0).getProduto().getNome().equals("Morango"),
                "Morango deveria aparecer primeiro (subiu mais %).");

        System.out.println("\n── Relatório fevereiro/2025 ──");
        relatorio.forEach(r -> System.out.println(r.gerarMensagem()));

        // ── Histórico completo ─────────────────────────────────
        List<CotacaoMensal> historico = cotacaoService.buscarHistorico(morango);
        assertCondition(historico.size() == 3, "Morango deveria ter 3 cotações no histórico.");
        assertCondition(historico.get(0).getMesReferencia().equals(janeiro),
                "Primeira cotação deveria ser janeiro.");

        System.out.println("\nTodos os testes de CotacaoService passaram.");
    }

    private static void assertCondition(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

