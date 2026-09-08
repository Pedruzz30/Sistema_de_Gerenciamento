package model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "venda_item_consumo_estornos")
public class VendaItemConsumoEstorno {

    public static final int ESCALA_QUANTIDADE = 6;
    public static final int ESCALA_CUSTO = 8;
    public static final int ESCALA_VALOR = 4;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "venda_item_consumo_id", nullable = false)
    private VendaItemConsumo vendaItemConsumo;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "movimento_estoque_id", nullable = false)
    private MovimentoEstoque movimentoEstoque;

    @Column(name = "quantidade_base_estornada", nullable = false, precision = 19, scale = ESCALA_QUANTIDADE)
    private BigDecimal quantidadeBaseEstornada;

    @Column(name = "custo_unitario_snapshot", nullable = false, precision = 19, scale = ESCALA_CUSTO)
    private BigDecimal custoUnitarioSnapshot;

    @Column(name = "valor_estornado", nullable = false, precision = 19, scale = ESCALA_VALOR)
    private BigDecimal valorEstornado;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    protected VendaItemConsumoEstorno() {
        // JPA
    }

    public VendaItemConsumoEstorno(long id,
                                   VendaItemConsumo vendaItemConsumo,
                                   MovimentoEstoque movimentoEstoque,
                                   BigDecimal quantidadeBaseEstornada,
                                   BigDecimal custoUnitarioSnapshot,
                                   BigDecimal valorEstornado) {
        validar(id >= 0, "ID do estorno de consumo nao pode ser negativo.");
        validar(vendaItemConsumo != null, "Consumo da venda e obrigatorio.");
        validar(movimentoEstoque != null, "Movimento de estorno e obrigatorio.");
        validar(movimentoEstoque.getTipo() == TipoMovimentoEstoque.ESTORNO_VENDA,
                "Movimento vinculado ao consumo precisa ser ESTORNO_VENDA.");
        validar(quantidadeBaseEstornada != null && quantidadeBaseEstornada.compareTo(BigDecimal.ZERO) > 0,
                "Quantidade estornada deve ser maior que zero.");
        validar(custoUnitarioSnapshot != null && custoUnitarioSnapshot.compareTo(BigDecimal.ZERO) >= 0,
                "Custo unitario do estorno deve ser nao negativo.");
        validar(valorEstornado != null && valorEstornado.compareTo(BigDecimal.ZERO) >= 0,
                "Valor estornado deve ser nao negativo.");

        this.id = id;
        this.vendaItemConsumo = vendaItemConsumo;
        this.movimentoEstoque = movimentoEstoque;
        this.quantidadeBaseEstornada = quantidadeBaseEstornada.setScale(ESCALA_QUANTIDADE, RoundingMode.HALF_UP);
        this.custoUnitarioSnapshot = custoUnitarioSnapshot.setScale(ESCALA_CUSTO, RoundingMode.HALF_UP);
        this.valorEstornado = valorEstornado.setScale(ESCALA_VALOR, RoundingMode.HALF_UP);
        this.criadoEm = LocalDateTime.now();
    }

    @PrePersist
    void prePersist() {
        if (criadoEm == null) {
            criadoEm = LocalDateTime.now();
        }
    }

    public long getId() { return id; }
    public VendaItemConsumo getVendaItemConsumo() { return vendaItemConsumo; }
    public MovimentoEstoque getMovimentoEstoque() { return movimentoEstoque; }
    public BigDecimal getQuantidadeBaseEstornada() { return quantidadeBaseEstornada; }
    public BigDecimal getCustoUnitarioSnapshot() { return custoUnitarioSnapshot; }
    public BigDecimal getValorEstornado() { return valorEstornado; }
    public LocalDateTime getCriadoEm() { return criadoEm; }

    private static void validar(boolean condicao, String mensagem) {
        if (!condicao) throw new IllegalArgumentException(mensagem);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VendaItemConsumoEstorno other)) return false;
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
