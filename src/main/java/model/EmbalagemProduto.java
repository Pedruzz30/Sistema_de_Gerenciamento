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
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

@Entity
@Table(
        name = "embalagens_produto",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_embalagens_produto_nome",
                columnNames = {"produto_id", "nome"}
        )
)
public class EmbalagemProduto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(name = "quantidade_base", nullable = false, precision = 19, scale = 6)
    private BigDecimal quantidadeBase;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "unidade_base_id", nullable = false)
    private UnidadeMedida unidadeBase;

    @Column(name = "codigo_barras", length = 80)
    private String codigoBarras;

    @Column(nullable = false)
    private Boolean ativa = Boolean.TRUE;

    protected EmbalagemProduto() {
        // JPA
    }

    public EmbalagemProduto(long id,
                            Produto produto,
                            String nome,
                            BigDecimal quantidadeBase,
                            UnidadeMedida unidadeBase,
                            String codigoBarras,
                            Boolean ativa) {
        validar(id >= 0, "ID da embalagem nao pode ser negativo.");
        validar(produto != null, "Produto da embalagem e obrigatorio.");
        validar(nome != null && !nome.isBlank(), "Nome da embalagem e obrigatorio.");
        validar(quantidadeBase != null && quantidadeBase.compareTo(BigDecimal.ZERO) > 0,
                "Quantidade base da embalagem deve ser maior que zero.");
        validar(unidadeBase != null, "Unidade base da embalagem e obrigatoria.");
        if (produto.getUnidadeBase() != null) {
            produto.getUnidadeBase().exigirCompatibilidade(unidadeBase);
        }

        this.id = id;
        this.produto = produto;
        this.nome = nome.trim();
        this.quantidadeBase = quantidadeBase.setScale(6, RoundingMode.HALF_UP);
        this.unidadeBase = unidadeBase;
        this.codigoBarras = normalizar(codigoBarras);
        this.ativa = ativa == null ? Boolean.TRUE : ativa;
    }

    public EmbalagemProduto comId(long novoId) {
        validar(novoId > 0, "Novo ID da embalagem deve ser maior que zero.");
        return new EmbalagemProduto(novoId, produto, nome, quantidadeBase, unidadeBase, codigoBarras, ativa);
    }

    public boolean pertenceAoProduto(int produtoId) {
        return produto != null && produto.getId() == produtoId;
    }

    public long getId() { return id; }
    public Produto getProduto() { return produto; }
    public String getNome() { return nome; }
    public BigDecimal getQuantidadeBase() { return quantidadeBase; }
    public UnidadeMedida getUnidadeBase() { return unidadeBase; }
    public String getCodigoBarras() { return codigoBarras; }
    public boolean isAtiva() { return ativa == null || ativa; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EmbalagemProduto other)) return false;
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    private static String normalizar(String valor) {
        if (valor == null) return null;
        String texto = valor.trim();
        return texto.isEmpty() ? null : texto;
    }

    private static void validar(boolean condicao, String mensagem) {
        if (!condicao) throw new IllegalArgumentException(mensagem);
    }
}
