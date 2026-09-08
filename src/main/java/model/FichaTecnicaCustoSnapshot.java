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
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "fichas_tecnicas_custo_snapshot")
public class FichaTecnicaCustoSnapshot {

    public static final int ESCALA_DINHEIRO = 4;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "ficha_tecnica_versao_id", nullable = false)
    private FichaTecnicaVersao fichaTecnicaVersao;

    @Column(name = "filial_id", nullable = false)
    private Long filialId;

    @Column(name = "custo_total", nullable = false, precision = 19, scale = ESCALA_DINHEIRO)
    private BigDecimal custoTotal;

    @Column(name = "custo_por_unidade_rendimento", nullable = false, precision = 19, scale = ESCALA_DINHEIRO)
    private BigDecimal custoPorUnidadeRendimento;

    @Column(name = "calculado_em", nullable = false)
    private LocalDateTime calculadoEm;

    protected FichaTecnicaCustoSnapshot() {
        // JPA
    }

    public FichaTecnicaCustoSnapshot(long id,
                                     FichaTecnicaVersao fichaTecnicaVersao,
                                     Long filialId,
                                     BigDecimal custoTotal,
                                     BigDecimal custoPorUnidadeRendimento,
                                     LocalDateTime calculadoEm) {
        validar(id >= 0, "ID do snapshot de custo nao pode ser negativo.");
        validar(fichaTecnicaVersao != null, "Versao da ficha tecnica e obrigatoria.");
        validar(filialId != null, "Filial do snapshot e obrigatoria.");
        validar(custoTotal != null && custoTotal.compareTo(BigDecimal.ZERO) >= 0,
                "Custo total do snapshot nao pode ser negativo.");
        validar(custoPorUnidadeRendimento != null && custoPorUnidadeRendimento.compareTo(BigDecimal.ZERO) >= 0,
                "Custo por unidade do snapshot nao pode ser negativo.");
        this.id = id;
        this.fichaTecnicaVersao = fichaTecnicaVersao;
        this.filialId = filialId;
        this.custoTotal = custoTotal.setScale(ESCALA_DINHEIRO, RoundingMode.HALF_UP);
        this.custoPorUnidadeRendimento = custoPorUnidadeRendimento.setScale(ESCALA_DINHEIRO, RoundingMode.HALF_UP);
        this.calculadoEm = calculadoEm == null ? LocalDateTime.now() : calculadoEm;
    }

    public long getId() { return id; }
    public FichaTecnicaVersao getFichaTecnicaVersao() { return fichaTecnicaVersao; }
    public Long getFilialId() { return filialId; }
    public BigDecimal getCustoTotal() { return custoTotal; }
    public BigDecimal getCustoPorUnidadeRendimento() { return custoPorUnidadeRendimento; }
    public LocalDateTime getCalculadoEm() { return calculadoEm; }

    private static void validar(boolean condicao, String mensagem) {
        if (!condicao) throw new IllegalArgumentException(mensagem);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FichaTecnicaCustoSnapshot other)) return false;
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
