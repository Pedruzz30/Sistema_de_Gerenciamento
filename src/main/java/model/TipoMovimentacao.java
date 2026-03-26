package model;

public enum TipoMovimentacao {
    ENTRADA,
    SAIDA;

    public int applyTo(int currentQuantity, int amount) {
        return this == ENTRADA ? currentQuantity + amount : currentQuantity - amount;
    }
}
