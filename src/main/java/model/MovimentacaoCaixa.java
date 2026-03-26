package model;

import java.time.LocalDateTime;

/**
 * Representa uma transação individual dentro de um caixa PDV.
 *
 * Exemplo real:
 *   14:32 — VENDA — R$25,00 — "Venda de 3 laranjas" — Operador: João
 *   15:10 — SANGRIA — R$200,00 — "Envio ao cofre" — Operador: João
 */
public class MovimentacaoCaixa {

    private final long id;
    private final TipoMovimentacaoCaixa tipo;
    private final double valor;
    private final String descricao;
    private final LocalDateTime dataHora;
    private final Usuario operador;

    public MovimentacaoCaixa(long id,
                             TipoMovimentacaoCaixa tipo,
                             double valor,
                             String descricao,
                             Usuario operador) {
        if (tipo == null) {
            throw new IllegalArgumentException("Tipo de movimentação é obrigatório.");
        }
        if (valor <= 0) {
            throw new IllegalArgumentException("Valor deve ser maior que zero.");
        }
        if (operador == null) {
            throw new IllegalArgumentException("Operador é obrigatório.");
        }

        this.id = id;
        this.tipo = tipo;
        this.valor = valor;
        this.descricao = descricao != null ? descricao.trim() : "";
        this.operador = operador;
        this.dataHora = LocalDateTime.now();
    }

    public long getId()                    { return id; }
    public TipoMovimentacaoCaixa getTipo() { return tipo; }
    public double getValor()               { return valor; }
    public String getDescricao()           { return descricao; }
    public LocalDateTime getDataHora()     { return dataHora; }
    public Usuario getOperador()           { return operador; }

    @Override
    public String toString() {
        return String.format("[%s] %s — R$%.2f — %s — op: %s",
                dataHora.toLocalTime(),
                tipo,
                valor,
                descricao,
                operador.getNomeCompleto());
    }
}