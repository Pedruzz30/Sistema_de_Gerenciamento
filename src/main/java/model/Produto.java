package model;

public class Produto {
    private final int id;
    private final String nome;
    private int quantidadeAtual;
    private final int quantidadeMinima;
    private final double precoUnitario;

    public Produto(int id, String nome, int quantidadeAtual, int quantidadeMinima, double precoUnitario) {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("Product name is required.");
        }
        if (quantidadeAtual < 0 || quantidadeMinima < 0 || precoUnitario < 0) {
            throw new IllegalArgumentException("Product values cannot be negative.");
        }

        this.id = id;
        this.nome = nome;
        this.quantidadeAtual = quantidadeAtual;
        this.quantidadeMinima = quantidadeMinima;
        this.precoUnitario = precoUnitario;
    }

    public int getId() { return id; }

    public String getNome() { return nome; }

    public int getQuantidadeAtual() { return quantidadeAtual; }

    public int getQuantidadeMinima() { return quantidadeMinima; }

    public double getPrecoUnitario() { return precoUnitario; }

    public void atualizarQuantidade(int novaQuantidade) {
        if (novaQuantidade < 0) {
            throw new IllegalArgumentException("Stock quantity cannot be negative.");
        }
        this.quantidadeAtual = novaQuantidade;
    }

    public double calcularPercentualEstoque() {
        if (quantidadeMinima == 0) {
            return quantidadeAtual > 0 ? 100 : 0;
        }
        return (quantidadeAtual * 100.0) / quantidadeMinima;
    }

    @Override
    public String toString() {
        return String.format(
                "Produto{id=%d, nome='%s', qtd=%d, qtdMinima=%d, preco=%.2f}",
                id,
                nome,
                quantidadeAtual,
                quantidadeMinima,
                precoUnitario
        );
    }
}
