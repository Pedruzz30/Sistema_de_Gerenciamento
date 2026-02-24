import java.time.LocalDateTime;

public class Pedido {

    public enum Tipo {
        ENTRADA, SAIDA
    }

    private int id;
    private Produto produto;
    private int quantidade;
    private Tipo tipo;
    private LocalDateTime dataHora;
    private Usuario usuario; // quem fez

    public Pedido(int id, Produto produto, int quantidade, Tipo tipo, Usuario usuario) {
        this.id = id;
        this.produto = produto;
        this.quantidade = quantidade;
        this.tipo = tipo;
        this.usuario = usuario;
        this.dataHora = LocalDateTime.now();
    }

    public int getId() {
        return id;
    }

    public Produto getProduto() {
        return produto;
    }

    public int getQuantidade() {
        return quantidade;
    }

    public Tipo getTipo() {
        return tipo;
    }

    public LocalDateTime getDataHora() {
        return dataHora;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    @Override
    public String toString() {
        return "Pedido{" +
                "id=" + id +
                ", tipo=" + tipo +
                ", produto=" + produto.getNome() +
                ", quantidade=" + quantidade +
                ", dataHora=" + dataHora +
                ", usuario=" + usuario.getLogin() +
                '}';
    }
}
