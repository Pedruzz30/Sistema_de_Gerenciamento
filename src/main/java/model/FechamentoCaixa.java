package model;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Relatório de fechamento diário de um caixa.
 *
 * Gerado automaticamente ao encerrar o caixa.
 * Representa o "extrato do dia" de um PDV específico.
 */
public class FechamentoCaixa {

    private final int numeroCaixa;
    private final LocalDate data;
    private final double saldoInicial;
    private final double totalEntradas;
    private final double totalSaidas;
    private final double totalVendas;
    private final double saldoFinal;
    private final int quantidadeMovimentacoes;
    private final String nomeOperador;

    public FechamentoCaixa(Caixa caixa) {
        if (caixa.getStatus() != Caixa.Status.ENCERRADO) {
            throw new IllegalArgumentException(
                    "Só é possível gerar fechamento de caixa ENCERRADO."
            );
        }

        this.numeroCaixa = caixa.getNumeroCaixa();
        this.data = caixa.getDataEncerramento().toLocalDate();
        this.saldoInicial = caixa.getSaldoInicial();
        this.totalEntradas = caixa.calcularTotalEntradas();
        this.totalSaidas = caixa.calcularTotalSaidas();
        this.totalVendas = caixa.calcularTotalVendas();
        this.saldoFinal = caixa.getSaldoAtual();
        this.quantidadeMovimentacoes = caixa.getMovimentacoes().size();
        this.nomeOperador = caixa.getOperadorAtual().getNomeCompleto();
    }

    public int getNumeroCaixa()              { return numeroCaixa; }
    public LocalDate getData()               { return data; }
    public double getSaldoInicial()          { return saldoInicial; }
    public double getTotalEntradas()         { return totalEntradas; }
    public double getTotalSaidas()           { return totalSaidas; }
    public double getTotalVendas()           { return totalVendas; }
    public double getSaldoFinal()            { return saldoFinal; }
    public int getQuantidadeMovimentacoes()  { return quantidadeMovimentacoes; }
    public String getNomeOperador()          { return nomeOperador; }

    public String gerarResumo() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        return String.format("""
                ┌─────────────────────────────────┐
                │   FECHAMENTO CAIXA %d — %s   │
                ├─────────────────────────────────┤
                │ Operador:       %-16s│
                │ Saldo inicial:  R$%12.2f   │
                │ Total entradas: R$%12.2f   │
                │ Total saídas:   R$%12.2f   │
                │ Total vendas:   R$%12.2f   │
                │ Saldo final:    R$%12.2f   │
                │ Movimentações:  %-16d│
                └─────────────────────────────────┘
                """,
                numeroCaixa,
                data.format(fmt),
                nomeOperador,
                saldoInicial,
                totalEntradas,
                totalSaidas,
                totalVendas,
                saldoFinal,
                quantidadeMovimentacoes);
    }
}
