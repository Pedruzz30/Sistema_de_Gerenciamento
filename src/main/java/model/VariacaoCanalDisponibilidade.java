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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(
        name = "variacao_canal_disponibilidade",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_variacao_canal_filial",
                columnNames = {"variacao_item_cardapio_id", "filial_id", "canal"}
        )
)
public class VariacaoCanalDisponibilidade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "variacao_item_cardapio_id", nullable = false)
    private VariacaoItemCardapio variacaoItemCardapio;

    @Column(name = "filial_id", nullable = false)
    private Long filialId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private CanalVenda canal;

    @Column(nullable = false)
    private Boolean permitida = Boolean.TRUE;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    @Version
    private long version;

    protected VariacaoCanalDisponibilidade() {
        // JPA
    }

    public VariacaoCanalDisponibilidade(long id,
                                        VariacaoItemCardapio variacaoItemCardapio,
                                        Long filialId,
                                        CanalVenda canal,
                                        Boolean permitida) {
        validar(id >= 0, "ID da disponibilidade por canal nao pode ser negativo.");
        validar(variacaoItemCardapio != null, "Variacao e obrigatoria.");
        validar(filialId != null && filialId > 0, "Filial e obrigatoria.");
        validar(canal != null, "Canal e obrigatorio.");
        this.id = id;
        this.variacaoItemCardapio = variacaoItemCardapio;
        this.filialId = filialId;
        this.canal = canal;
        this.permitida = permitida == null || permitida;
        this.criadoEm = LocalDateTime.now();
        this.atualizadoEm = this.criadoEm;
    }

    public void alterarPermitida(boolean permitida) {
        this.permitida = permitida;
        this.atualizadoEm = LocalDateTime.now();
    }

    public long getId() { return id; }
    public VariacaoItemCardapio getVariacaoItemCardapio() { return variacaoItemCardapio; }
    public Long getFilialId() { return filialId; }
    public CanalVenda getCanal() { return canal; }
    public boolean isPermitida() { return permitida == null || permitida; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public LocalDateTime getAtualizadoEm() { return atualizadoEm; }
    public long getVersion() { return version; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VariacaoCanalDisponibilidade other)) return false;
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
