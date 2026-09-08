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
@Table(name = "venda_item_modificador_impactos")
public class VendaItemModificadorImpacto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "venda_item_modificador_id", nullable = false)
    private VendaItemModificador vendaItemModificador;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "venda_item_consumo_id")
    private VendaItemConsumo vendaItemConsumo;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_efeito", nullable = false, length = 40)
    private TipoEfeitoModificador tipoEfeito;

    @Column(name = "quantidade_delta_base", nullable = false, precision = 19, scale = 6)
    private BigDecimal quantidadeDeltaBase;

    @Column(name = "custo_unitario_snapshot", nullable = false, precision = 19, scale = 8)
    private BigDecimal custoUnitarioSnapshot;

    @Column(name = "valor_impacto_snapshot", nullable = false, precision = 19, scale = 4)
    private BigDecimal valorImpactoSnapshot;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    protected VendaItemModificadorImpacto() {
        // JPA
    }

    public VendaItemModificadorImpacto(long id,
                                       VendaItemModificador vendaItemModificador,
                                       VendaItemConsumo vendaItemConsumo,
                                       Produto produto,
                                       TipoEfeitoModificador tipoEfeito,
                                       BigDecimal quantidadeDeltaBase,
                                       BigDecimal custoUnitarioSnapshot,
                                       BigDecimal valorImpactoSnapshot) {
        validar(id >= 0, "ID do impacto nao pode ser negativo.");
        validar(vendaItemModificador != null, "Modificador da venda e obrigatorio.");
        validar(produto != null, "Produto do impacto e obrigatorio.");
        validar(tipoEfeito != null, "Tipo do impacto e obrigatorio.");
        validar(quantidadeDeltaBase != null && quantidadeDeltaBase.compareTo(BigDecimal.ZERO) != 0,
                "Quantidade delta deve ser diferente de zero.");
        validar(custoUnitarioSnapshot != null && custoUnitarioSnapshot.compareTo(BigDecimal.ZERO) >= 0,
                "Custo unitario do impacto deve ser nao negativo.");
        validar(valorImpactoSnapshot != null,
                "Valor do impacto e obrigatorio.");
        this.id = id;
        this.vendaItemModificador = vendaItemModificador;
        this.vendaItemConsumo = vendaItemConsumo;
        this.produto = produto;
        this.tipoEfeito = tipoEfeito;
        this.quantidadeDeltaBase = quantidadeDeltaBase.setScale(6, RoundingMode.HALF_UP);
        this.custoUnitarioSnapshot = custoUnitarioSnapshot.setScale(8, RoundingMode.HALF_UP);
        this.valorImpactoSnapshot = valorImpactoSnapshot.setScale(4, RoundingMode.HALF_UP);
        this.criadoEm = LocalDateTime.now();
    }

    @PrePersist
    void prePersist() {
        if (criadoEm == null) {
            criadoEm = LocalDateTime.now();
        }
    }

    public long getId() { return id; }
    public VendaItemModificador getVendaItemModificador() { return vendaItemModificador; }
    public VendaItemConsumo getVendaItemConsumo() { return vendaItemConsumo; }
    public Produto getProduto() { return produto; }
    public TipoEfeitoModificador getTipoEfeito() { return tipoEfeito; }
    public BigDecimal getQuantidadeDeltaBase() { return quantidadeDeltaBase; }
    public BigDecimal getCustoUnitarioSnapshot() { return custoUnitarioSnapshot; }
    public BigDecimal getValorImpactoSnapshot() { return valorImpactoSnapshot; }
    public LocalDateTime getCriadoEm() { return criadoEm; }

    private static void validar(boolean condicao, String mensagem) {
        if (!condicao) throw new IllegalArgumentException(mensagem);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VendaItemModificadorImpacto other)) return false;
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
