package model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "venda_item_consumos")
public class VendaItemConsumo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "venda_item_id", nullable = false)
    private VendaItem vendaItem;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "venda_item_parte_id")
    private VendaItemParte vendaItemParte;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @Column(name = "nome_produto_snapshot", nullable = false, length = 160)
    private String nomeProdutoSnapshot;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "unidade_base_id", nullable = false)
    private UnidadeMedida unidadeBase;

    @Column(name = "unidade_base_codigo_snapshot", nullable = false, length = 40)
    private String unidadeBaseCodigoSnapshot;

    @Column(name = "quantidade_base_por_unidade", nullable = false, precision = 19, scale = 6)
    private BigDecimal quantidadeBasePorUnidade;

    @Column(name = "quantidade_base_total", nullable = false, precision = 19, scale = 6)
    private BigDecimal quantidadeBaseTotal;

    @Column(name = "custo_medio_unitario_snapshot", nullable = false, precision = 19, scale = 8)
    private BigDecimal custoMedioUnitarioSnapshot;

    @Column(name = "custo_total_snapshot", nullable = false, precision = 19, scale = 4)
    private BigDecimal custoTotalSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "origem_composicao", nullable = false, length = 30)
    private OrigemComposicaoVenda origemComposicao;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "ficha_tecnica_ingrediente_id")
    private FichaTecnicaIngrediente fichaTecnicaIngrediente;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "movimento_estoque_id")
    private MovimentoEstoque movimentoEstoque;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    protected VendaItemConsumo() {
        // JPA
    }

    public VendaItemConsumo(long id,
                            VendaItem vendaItem,
                            Produto produto,
                            UnidadeMedida unidadeBase,
                            BigDecimal quantidadeBasePorUnidade,
                            BigDecimal quantidadeBaseTotal,
                            BigDecimal custoMedioUnitarioSnapshot,
                            BigDecimal custoTotalSnapshot,
                            OrigemComposicaoVenda origemComposicao,
                            FichaTecnicaIngrediente fichaTecnicaIngrediente) {
        this(id, vendaItem, null, produto, unidadeBase, quantidadeBasePorUnidade, quantidadeBaseTotal,
                custoMedioUnitarioSnapshot, custoTotalSnapshot, origemComposicao, fichaTecnicaIngrediente);
    }

    public VendaItemConsumo(long id,
                            VendaItem vendaItem,
                            VendaItemParte vendaItemParte,
                            Produto produto,
                            UnidadeMedida unidadeBase,
                            BigDecimal quantidadeBasePorUnidade,
                            BigDecimal quantidadeBaseTotal,
                            BigDecimal custoMedioUnitarioSnapshot,
                            BigDecimal custoTotalSnapshot,
                            OrigemComposicaoVenda origemComposicao,
                            FichaTecnicaIngrediente fichaTecnicaIngrediente) {
        validar(id >= 0, "ID do consumo nao pode ser negativo.");
        validar(vendaItem != null, "Item da venda e obrigatorio.");
        validar(vendaItemParte == null || vendaItemParte.getVendaItem().equals(vendaItem),
                "Parte do consumo nao pertence ao item da venda.");
        validar(produto != null, "Produto do consumo e obrigatorio.");
        validar(unidadeBase != null, "Unidade-base do consumo e obrigatoria.");
        validar(quantidadeBasePorUnidade != null && quantidadeBasePorUnidade.compareTo(BigDecimal.ZERO) > 0,
                "Quantidade por unidade deve ser maior que zero.");
        validar(quantidadeBaseTotal != null && quantidadeBaseTotal.compareTo(BigDecimal.ZERO) > 0,
                "Quantidade total deve ser maior que zero.");
        validar(custoMedioUnitarioSnapshot != null && custoMedioUnitarioSnapshot.compareTo(BigDecimal.ZERO) >= 0,
                "Custo medio unitario deve ser nao negativo.");
        validar(custoTotalSnapshot != null && custoTotalSnapshot.compareTo(BigDecimal.ZERO) >= 0,
                "Custo total deve ser nao negativo.");
        validar(origemComposicao != null, "Origem da composicao e obrigatoria.");

        this.id = id;
        this.vendaItem = vendaItem;
        this.vendaItemParte = vendaItemParte;
        this.produto = produto;
        this.nomeProdutoSnapshot = produto.getNome();
        this.unidadeBase = unidadeBase;
        this.unidadeBaseCodigoSnapshot = unidadeBase.getCodigo();
        this.quantidadeBasePorUnidade = quantidadeBasePorUnidade.setScale(6, RoundingMode.HALF_UP);
        this.quantidadeBaseTotal = quantidadeBaseTotal.setScale(6, RoundingMode.HALF_UP);
        this.custoMedioUnitarioSnapshot = custoMedioUnitarioSnapshot.setScale(8, RoundingMode.HALF_UP);
        this.custoTotalSnapshot = custoTotalSnapshot.setScale(4, RoundingMode.HALF_UP);
        this.origemComposicao = origemComposicao;
        this.fichaTecnicaIngrediente = fichaTecnicaIngrediente;
        this.criadoEm = LocalDateTime.now();
    }

    public void vincularMovimento(MovimentoEstoque movimentoEstoque) {
        validar(movimentoEstoque != null, "Movimento de estoque e obrigatorio.");
        this.movimentoEstoque = movimentoEstoque;
    }

    @PrePersist
    void prePersist() {
        if (criadoEm == null) {
            criadoEm = LocalDateTime.now();
        }
    }

    public long getId() { return id; }
    public VendaItem getVendaItem() { return vendaItem; }
    public VendaItemParte getVendaItemParte() { return vendaItemParte; }
    public Produto getProduto() { return produto; }
    public String getNomeProdutoSnapshot() { return nomeProdutoSnapshot; }
    public UnidadeMedida getUnidadeBase() { return unidadeBase; }
    public String getUnidadeBaseCodigoSnapshot() { return unidadeBaseCodigoSnapshot; }
    public BigDecimal getQuantidadeBasePorUnidade() { return quantidadeBasePorUnidade; }
    public BigDecimal getQuantidadeBaseTotal() { return quantidadeBaseTotal; }
    public BigDecimal getCustoMedioUnitarioSnapshot() { return custoMedioUnitarioSnapshot; }
    public BigDecimal getCustoTotalSnapshot() { return custoTotalSnapshot; }
    public OrigemComposicaoVenda getOrigemComposicao() { return origemComposicao; }
    public FichaTecnicaIngrediente getFichaTecnicaIngrediente() { return fichaTecnicaIngrediente; }
    public MovimentoEstoque getMovimentoEstoque() { return movimentoEstoque; }
    public LocalDateTime getCriadoEm() { return criadoEm; }

    private static void validar(boolean condicao, String mensagem) {
        if (!condicao) throw new IllegalArgumentException(mensagem);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VendaItemConsumo other)) return false;
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
