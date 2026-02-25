package model;

import java.time.LocalDateTime;

public class Pedido {
    private final int id;
    private final Produto produto;
    private final int quantidade;
    private final TipoMovimentacao tipo;
    private final LocalDateTime dataHora;
    private final Usuario usuario;

    public Pedido(int id, Produto produto, int quantidade, TipoMovimentacao tipo, Usuario usuario) {
        this.id = id;
        this.produto = produto;
        this.quantidade = quantidade;
        this.tipo = tipo;
        this.usuario = usuario;
        this.dataHora = LocalDateTime.now();
    }

    public int getId() { return id; }

    public Produto getProduto() { return produto; }

    public int getQuantidade() { return quantidade; }

    public TipoMovimentacao getTipo() { return tipo; }

    public LocalDateTime getDataHora() { return dataHora; }

    public Usuario getUsuario() { return usuario; }

    @Override
    public String toString() {
        String usuarioNome = usuario == null ? "N/A" : usuario.getNomeCompleto();
        return "Pedido{" +
                "id=" + id +
                ", tipo=" + tipo +
                ", produto=" + produto.getNome() +
                ", quantidade=" + quantidade +
                ", dataHora=" + dataHora +
                ", usuario='" + usuarioNome + '\'' +
                '}';
    }
}