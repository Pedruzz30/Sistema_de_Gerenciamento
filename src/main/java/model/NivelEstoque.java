package model;

public enum NivelEstoque {
    SEM_ESTOQUE(0, "⚠ Produto sem estoque (0%)."),
    CRITICO(25, "⚠ Estoque crítico (<= 25%)."),
    BAIXO(50, "Atenção: estoque baixo (<= 50%)."),
    MODERADO(75, "Aviso: estoque moderado (<= 75%)."),
    ADEQUADO(100, "Estoque em nível adequado.");

    private final int threshold;
    private final String message;

    NivelEstoque(int threshold, String message) {
        this.threshold = threshold;
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public static NivelEstoque fromPercentage(double percentage) {
        if (percentage <= SEM_ESTOQUE.threshold) {
            return SEM_ESTOQUE;
        }
        if (percentage <= CRITICO.threshold) {
            return CRITICO;
        }
        if (percentage <= BAIXO.threshold) {
            return BAIXO;
        }
        if (percentage <= MODERADO.threshold) {
            return MODERADO;
        }
        return ADEQUADO;
    }
}