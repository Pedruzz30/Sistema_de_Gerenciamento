package model;

public class Produto {
    private int id;
    private String nome;
    private int quantidadeAtual;
    private int quantidadeMinima;
    private double precoUnitario;

    public Produto(int id, String nome, int quantidadeAtual, int quantidadeMinima, double precoUnitario) {
        this.id = id;
        this.nome = nome;
        this.quantidadeAtual = quantidadeAtual;
        this.quantidadeMinima = quantidadeMinima;
        this.precoUnitario = precoUnitario;
    }

    public int getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public int getQuantidadeAtual() {
        return quantidadeAtual;
    }

    public int getQuantidadeMinima() {
        return quantidadeMinima;
    }

    public double getPrecoUnitario() {
        return precoUnitario;
    }

    public void setQuantidadeAtual(int quantidadeAtual) {
        this.quantidadeAtual = quantidadeAtual;
    }

    @Override
    public String toString() {
        return "Produto{" +
                "id=" + id +
                ", nome='" + nome + '\'' +
                ", qtd=" + quantidadeAtual +
                ", qtdMinima=" + quantidadeMinima +
                ", preco=" + precoUnitario +
                '}';
    }
}
