package model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Lembra que o código {@code codigoProduto} nas notas de um fornecedor é o
 * nosso {@code produto}, e quantas unidades vêm em cada embalagem comprada.
 */
@Entity
@Table(name = "vinculos_produto_fornecedor")
public class VinculoProdutoFornecedor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "fornecedor_id", nullable = false)
    private Fornecedor fornecedor;

    @Column(name = "codigo_produto", nullable = false, length = 60)
    private String codigoProduto;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @Column(name = "unidades_por_embalagem", nullable = false)
    private int unidadesPorEmbalagem;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    protected VinculoProdutoFornecedor() {
        // JPA
    }

    public VinculoProdutoFornecedor(Fornecedor fornecedor, String codigoProduto, Produto produto, int unidadesPorEmbalagem) {
        if (fornecedor == null || codigoProduto == null || codigoProduto.isBlank()) {
            throw new IllegalArgumentException("Fornecedor e codigo do produto sao obrigatorios.");
        }
        this.fornecedor = fornecedor;
        this.codigoProduto = codigoProduto.trim();
        atualizar(produto, unidadesPorEmbalagem);
    }

    public final void atualizar(Produto novoProduto, int unidades) {
        if (novoProduto == null || unidades < 1) {
            throw new IllegalArgumentException("Produto e unidades por embalagem sao obrigatorios.");
        }
        this.produto = novoProduto;
        this.unidadesPorEmbalagem = unidades;
        this.atualizadoEm = LocalDateTime.now();
    }

    public long getId() { return id; }
    public Fornecedor getFornecedor() { return fornecedor; }
    public String getCodigoProduto() { return codigoProduto; }
    public Produto getProduto() { return produto; }
    public int getUnidadesPorEmbalagem() { return unidadesPorEmbalagem; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VinculoProdutoFornecedor other)) return false;
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
