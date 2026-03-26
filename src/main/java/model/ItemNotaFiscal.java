package model;

/**
 * Representa uma linha de item dentro de uma nota fiscal.
 *
 * Exemplo real: uma nota de hortifruit pode ter:
 *   - 2 caixas de laranja   a R$ 4,99/cx
 *   - 5 caixas de alface    a R$ 2,50/cx
 *   - 1 caixa  de morango   a R$ 12,00/cx
 *
 * Cada uma dessas linhas é um ItemNotaFiscal.
 */
public class ItemNotaFiscal {

    private final Produto produto;
    private final int quantidade;
    private final double precoUnitarioNota; // preço NA HORA da compra (pode variar mês a mês)

    public ItemNotaFiscal(Produto produto, int quantidade, double precoUnitarioNota) {
        if (produto == null) {
            throw new IllegalArgumentException("Produto é obrigatório.");
        }
        if (quantidade <= 0) {
            throw new IllegalArgumentException("Quantidade deve ser maior que zero.");
        }
        if (precoUnitarioNota < 0) {
            throw new IllegalArgumentException("Preço não pode ser negativo.");
        }

        this.produto = produto;
        this.quantidade = quantidade;
        this.precoUnitarioNota = precoUnitarioNota;
    }

    public Produto getProduto()             { return produto; }
    public int getQuantidade()              { return quantidade; }
    public double getPrecoUnitarioNota()    { return precoUnitarioNota; }

    // Calcula o subtotal deste item: 5 caixas × R$4,99 = R$24,95
    public double calcularSubtotal() {
        return quantidade * precoUnitarioNota;
    }

    @Override
    public String toString() {
        return String.format("  - %s: %d unid. × R$%.2f = R$%.2f",
                produto.getNome(), quantidade, precoUnitarioNota, calcularSubtotal());
    }
}