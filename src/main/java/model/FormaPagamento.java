package model;

/**
 * Forma de pagamento de uma venda no PDV.
 *
 * <p>Só {@link #DINHEIRO} entra na gaveta: Pix e cartões vão direto para a
 * conta/maquininha e são conferidos pelo extrato, não pela contagem de notas.
 */
public enum FormaPagamento {
    DINHEIRO("Dinheiro"),
    PIX("Pix"),
    DEBITO("Débito"),
    CREDITO("Crédito"),
    VOUCHER("Voucher");

    private final String label;

    FormaPagamento(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** {@code true} quando o valor físico entra (ou sai) da gaveta. */
    public boolean afetaGaveta() {
        return this == DINHEIRO;
    }

    /** Aceita nomes do enum e variações comuns vindas do front ("pix", "Débito"...). */
    public static FormaPagamento parse(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String normalizado = java.text.Normalizer.normalize(valor.trim(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(java.util.Locale.ROOT);
        if ("CARTAO_DEBITO".equals(normalizado)) return DEBITO;
        if ("CARTAO_CREDITO".equals(normalizado)) return CREDITO;
        try {
            return FormaPagamento.valueOf(normalizado);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Forma de pagamento invalida: " + valor);
        }
    }
}
