package model;

/**
 * Tipos de movimentação que podem ocorrer em um caixa PDV.
 *
 * VENDA      → cliente pagou por um produto
 * SANGRIA    → retirada de dinheiro do caixa (segurança ou depósito)
 * SUPRIMENTO → reposição de troco no caixa
 * ENTRADA    → outros recebimentos (ex: devolução de fornecedor)
 * DESPESA    → pequenas saídas autorizadas (ex: compra de material)
 */
public enum TipoMovimentacaoCaixa {
    VENDA,
    SANGRIA,
    SUPRIMENTO,
    ENTRADA,
    DESPESA;

    /** Retorna true se essa movimentação aumenta o saldo do caixa */
    public boolean isEntrada() {
        return this == VENDA || this == SUPRIMENTO || this == ENTRADA;
    }
}