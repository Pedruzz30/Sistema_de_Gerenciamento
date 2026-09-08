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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

@Entity
@Table(name = "itens_compra_estoque")
public class ItemCompraEstoque {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "embalagem_id")
    private EmbalagemProduto embalagem;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "unidade_compra_id")
    private UnidadeMedida unidadeCompra;

    @Column(name = "quantidade_comprada", nullable = false, precision = 19, scale = 6)
    private BigDecimal quantidadeComprada;

    @Column(name = "quantidade_base", nullable = false, precision = 19, scale = 6)
    private BigDecimal quantidadeBase;

    @Column(name = "custo_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal custoTotal;

    @Column(name = "custo_unitario_compra", nullable = false, precision = 19, scale = 8)
    private BigDecimal custoUnitarioCompra;

    @Column(name = "custo_unitario_base", nullable = false, precision = 19, scale = 8)
    private BigDecimal custoUnitarioBase;

    protected ItemCompraEstoque() {
        // JPA
    }

    public ItemCompraEstoque(long id,
                             Produto produto,
                             EmbalagemProduto embalagem,
                             UnidadeMedida unidadeCompra,
                             BigDecimal quantidadeComprada,
                             BigDecimal quantidadeBase,
                             BigDecimal custoTotal) {
        validar(id >= 0, "ID do item de compra nao pode ser negativo.");
        validar(produto != null, "Produto do item de compra e obrigatorio.");
        validar(embalagem != null || unidadeCompra != null,
                "Informe embalagem ou unidade de compra.");
        validar(embalagem == null || embalagem.pertenceAoProduto(produto.getId()),
                "Embalagem nao pertence ao produto informado.");
        validar(quantidadeComprada != null && quantidadeComprada.compareTo(BigDecimal.ZERO) > 0,
                "Quantidade comprada deve ser maior que zero.");
        validar(quantidadeBase != null && quantidadeBase.compareTo(BigDecimal.ZERO) > 0,
                "Quantidade convertida para base deve ser maior que zero.");
        validar(custoTotal != null && custoTotal.compareTo(BigDecimal.ZERO) >= 0,
                "Custo total deve ser nao negativo.");

        this.id = id;
        this.produto = produto;
        this.embalagem = embalagem;
        this.unidadeCompra = unidadeCompra;
        this.quantidadeComprada = quantidadeComprada.setScale(6, RoundingMode.HALF_UP);
        this.quantidadeBase = quantidadeBase.setScale(6, RoundingMode.HALF_UP);
        this.custoTotal = custoTotal.setScale(4, RoundingMode.HALF_UP);
        this.custoUnitarioCompra = this.custoTotal.divide(this.quantidadeComprada, 8, RoundingMode.HALF_UP);
        this.custoUnitarioBase = this.custoTotal.divide(this.quantidadeBase, 8, RoundingMode.HALF_UP);
    }

    public ItemCompraEstoque comId(long novoId) {
        validar(novoId > 0, "Novo ID do item de compra deve ser maior que zero.");
        return new ItemCompraEstoque(
                novoId,
                produto,
                embalagem,
                unidadeCompra,
                quantidadeComprada,
                quantidadeBase,
                custoTotal
        );
    }

    public long getId() { return id; }
    public Produto getProduto() { return produto; }
    public EmbalagemProduto getEmbalagem() { return embalagem; }
    public UnidadeMedida getUnidadeCompra() { return unidadeCompra; }
    public BigDecimal getQuantidadeComprada() { return quantidadeComprada; }
    public BigDecimal getQuantidadeBase() { return quantidadeBase; }
    public BigDecimal getCustoTotal() { return custoTotal; }
    public BigDecimal getCustoUnitarioCompra() { return custoUnitarioCompra; }
    public BigDecimal getCustoUnitarioBase() { return custoUnitarioBase; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ItemCompraEstoque other)) return false;
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    private static void validar(boolean condicao, String mensagem) {
        if (!condicao) throw new IllegalArgumentException(mensagem);
    }
}
