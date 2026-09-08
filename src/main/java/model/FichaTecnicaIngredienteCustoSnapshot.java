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
@Table(name = "fichas_tecnicas_ingredientes_custo_snapshot")
public class FichaTecnicaIngredienteCustoSnapshot {

    public static final int ESCALA_QUANTIDADE = 6;
    public static final int ESCALA_CUSTO_UNITARIO = 8;
    public static final int ESCALA_DINHEIRO = 4;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "ficha_tecnica_custo_snapshot_id", nullable = false)
    private FichaTecnicaCustoSnapshot fichaTecnicaCustoSnapshot;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "ficha_tecnica_ingrediente_id", nullable = false)
    private FichaTecnicaIngrediente fichaTecnicaIngrediente;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @Column(name = "quantidade_base", nullable = false, precision = 19, scale = ESCALA_QUANTIDADE)
    private BigDecimal quantidadeBase;

    @Column(name = "custo_medio_unitario", nullable = false, precision = 19, scale = ESCALA_CUSTO_UNITARIO)
    private BigDecimal custoMedioUnitario;

    @Column(name = "custo_total_ingrediente", nullable = false, precision = 19, scale = ESCALA_DINHEIRO)
    private BigDecimal custoTotalIngrediente;

    protected FichaTecnicaIngredienteCustoSnapshot() {
        // JPA
    }

    public FichaTecnicaIngredienteCustoSnapshot(long id,
                                                FichaTecnicaCustoSnapshot fichaTecnicaCustoSnapshot,
                                                FichaTecnicaIngrediente fichaTecnicaIngrediente,
                                                Produto produto,
                                                BigDecimal quantidadeBase,
                                                BigDecimal custoMedioUnitario,
                                                BigDecimal custoTotalIngrediente) {
        validar(id >= 0, "ID do snapshot de ingrediente nao pode ser negativo.");
        validar(fichaTecnicaCustoSnapshot != null, "Snapshot de custo e obrigatorio.");
        validar(fichaTecnicaIngrediente != null, "Ingrediente da ficha e obrigatorio.");
        validar(produto != null, "Produto e obrigatorio.");
        validar(quantidadeBase != null && quantidadeBase.compareTo(BigDecimal.ZERO) > 0,
                "Quantidade do snapshot deve ser maior que zero.");
        validar(custoMedioUnitario != null && custoMedioUnitario.compareTo(BigDecimal.ZERO) >= 0,
                "Custo medio unitario nao pode ser negativo.");
        validar(custoTotalIngrediente != null && custoTotalIngrediente.compareTo(BigDecimal.ZERO) >= 0,
                "Custo total do ingrediente nao pode ser negativo.");
        this.id = id;
        this.fichaTecnicaCustoSnapshot = fichaTecnicaCustoSnapshot;
        this.fichaTecnicaIngrediente = fichaTecnicaIngrediente;
        this.produto = produto;
        this.quantidadeBase = quantidadeBase.setScale(ESCALA_QUANTIDADE, RoundingMode.HALF_UP);
        this.custoMedioUnitario = custoMedioUnitario.setScale(ESCALA_CUSTO_UNITARIO, RoundingMode.HALF_UP);
        this.custoTotalIngrediente = custoTotalIngrediente.setScale(ESCALA_DINHEIRO, RoundingMode.HALF_UP);
    }

    public long getId() { return id; }
    public FichaTecnicaCustoSnapshot getFichaTecnicaCustoSnapshot() { return fichaTecnicaCustoSnapshot; }
    public FichaTecnicaIngrediente getFichaTecnicaIngrediente() { return fichaTecnicaIngrediente; }
    public Produto getProduto() { return produto; }
    public BigDecimal getQuantidadeBase() { return quantidadeBase; }
    public BigDecimal getCustoMedioUnitario() { return custoMedioUnitario; }
    public BigDecimal getCustoTotalIngrediente() { return custoTotalIngrediente; }

    private static void validar(boolean condicao, String mensagem) {
        if (!condicao) throw new IllegalArgumentException(mensagem);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FichaTecnicaIngredienteCustoSnapshot other)) return false;
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
