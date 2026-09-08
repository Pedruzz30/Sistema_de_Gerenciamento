package model;

import java.math.BigDecimal;

public enum TipoMovimentoEstoque {
    ENTRADA_COMPRA(1),
    DEVOLUCAO_COMPRA(-1),
    AJUSTE_POSITIVO(1),
    AJUSTE_NEGATIVO(-1),
    ESTORNO_MOVIMENTO(0),
    SAIDA_VENDA(-1),
    ESTORNO_VENDA(1);

    private final int sinalPadrao;

    TipoMovimentoEstoque(int sinalPadrao) {
        this.sinalPadrao = sinalPadrao;
    }

    public int getSinalPadrao() {
        return sinalPadrao;
    }

    public boolean ehEntrada() {
        return sinalPadrao > 0;
    }

    public boolean ehSaida() {
        return sinalPadrao < 0;
    }

    public BigDecimal aplicar(BigDecimal saldoAnterior, BigDecimal quantidade) {
        validar(saldoAnterior, quantidade);
        return saldoAnterior.add(quantidade.multiply(BigDecimal.valueOf(sinalPadrao)));
    }

    private static void validar(BigDecimal saldoAnterior, BigDecimal quantidade) {
        if (saldoAnterior == null) {
            throw new IllegalArgumentException("Saldo anterior e obrigatorio.");
        }
        if (quantidade == null || quantidade.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Quantidade deve ser maior que zero.");
        }
    }
}
