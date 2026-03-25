package model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Representa um ponto de venda físico (PDV).
 *
 * Ciclo de vida de um caixa no dia:
 *
 *   FECHADO → (abertura) → ABERTO → (encerramento) → ENCERRADO
 *
 * Um caixa encerrado não pode receber mais movimentações.
 * No dia seguinte, um novo caixa é aberto para o mesmo número.
 */
public class Caixa {

    public enum Status {
        FECHADO,    // ainda não foi aberto no dia
        ABERTO,     // operando
        ENCERRADO   // fechado para o dia, aguardando consolidação
    }

    private final long id;
    private final int numeroCaixa;        // 1, 2 ou 3
    private double saldoInicial;
    private double saldoAtual;
    private Status status;
    private Usuario operadorAtual;
    private LocalDateTime dataAbertura;
    private LocalDateTime dataEncerramento;
    private final List<MovimentacaoCaixa> movimentacoes;

    public Caixa(long id, int numeroCaixa) {
        if (numeroCaixa <= 0) {
            throw new IllegalArgumentException("Número do caixa deve ser maior que zero.");
        }
        this.id = id;
        this.numeroCaixa = numeroCaixa;
        this.status = Status.FECHADO;
        this.saldoAtual = 0;
        this.movimentacoes = new ArrayList<>();
    }

    // ── Abertura ───────────────────────────────────────────────

    public void abrir(Usuario operador, double saldoInicial) {
        if (status != Status.FECHADO) {
            throw new IllegalStateException(
                    "Caixa " + numeroCaixa + " já está " + status + "."
            );
        }
        if (operador == null) {
            throw new IllegalArgumentException("Operador é obrigatório para abrir o caixa.");
        }
        if (saldoInicial < 0) {
            throw new IllegalArgumentException("Saldo inicial não pode ser negativo.");
        }

        this.operadorAtual = operador;
        this.saldoInicial = saldoInicial;
        this.saldoAtual = saldoInicial;
        this.status = Status.ABERTO;
        this.dataAbertura = LocalDateTime.now();
    }

    // ── Movimentações ──────────────────────────────────────────

    public void registrarMovimentacao(MovimentacaoCaixa movimentacao) {
        if (status != Status.ABERTO) {
            throw new IllegalStateException(
                    "Caixa " + numeroCaixa + " não está aberto. Status: " + status
            );
        }
        if (movimentacao == null) {
            throw new IllegalArgumentException("Movimentação não pode ser nula.");
        }

        // Atualiza saldo: entradas somam, saídas subtraem
        if (movimentacao.getTipo().isEntrada()) {
            this.saldoAtual += movimentacao.getValor();
        } else {
            if (movimentacao.getValor() > this.saldoAtual) {
                throw new IllegalArgumentException(
                        "Saldo insuficiente para " + movimentacao.getTipo() +
                                ". Saldo atual: R$" + String.format("%.2f", saldoAtual)
                );
            }
            this.saldoAtual -= movimentacao.getValor();
        }

        movimentacoes.add(movimentacao);
    }

    // ── Encerramento ───────────────────────────────────────────

    public void encerrar() {
        if (status != Status.ABERTO) {
            throw new IllegalStateException(
                    "Caixa " + numeroCaixa + " precisa estar ABERTO para ser encerrado."
            );
        }
        this.status = Status.ENCERRADO;
        this.dataEncerramento = LocalDateTime.now();
    }

    // ── Cálculos ───────────────────────────────────────────────

    public double calcularTotalEntradas() {
        return movimentacoes.stream()
                .filter(m -> m.getTipo().isEntrada())
                .mapToDouble(MovimentacaoCaixa::getValor)
                .sum();
    }

    public double calcularTotalSaidas() {
        return movimentacoes.stream()
                .filter(m -> !m.getTipo().isEntrada())
                .mapToDouble(MovimentacaoCaixa::getValor)
                .sum();
    }

    public double calcularTotalVendas() {
        return movimentacoes.stream()
                .filter(m -> m.getTipo() == TipoMovimentacaoCaixa.VENDA)
                .mapToDouble(MovimentacaoCaixa::getValor)
                .sum();
    }

    // ── Getters ────────────────────────────────────────────────

    public long getId()                        { return id; }
    public int getNumeroCaixa()               { return numeroCaixa; }
    public double getSaldoInicial()           { return saldoInicial; }
    public double getSaldoAtual()             { return saldoAtual; }
    public Status getStatus()                 { return status; }
    public Usuario getOperadorAtual()         { return operadorAtual; }
    public LocalDateTime getDataAbertura()    { return dataAbertura; }
    public LocalDateTime getDataEncerramento(){ return dataEncerramento; }

    public List<MovimentacaoCaixa> getMovimentacoes() {
        return Collections.unmodifiableList(movimentacoes);
    }
}