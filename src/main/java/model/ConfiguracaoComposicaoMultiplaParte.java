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
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(
        name = "configuracao_composicao_multipla_partes",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_config_composicao_multipla_parte",
                columnNames = {"configuracao_id", "variacao_parte_id"}
        )
)
public class ConfiguracaoComposicaoMultiplaParte {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "configuracao_id", nullable = false)
    private ConfiguracaoComposicaoMultipla configuracao;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "variacao_parte_id", nullable = false)
    private VariacaoItemCardapio variacaoParte;

    @Column(nullable = false)
    private Boolean ativa = Boolean.TRUE;

    @Column(name = "ordem_exibicao", nullable = false)
    private int ordemExibicao;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    protected ConfiguracaoComposicaoMultiplaParte() {
        // JPA
    }

    public ConfiguracaoComposicaoMultiplaParte(long id,
                                               ConfiguracaoComposicaoMultipla configuracao,
                                               VariacaoItemCardapio variacaoParte,
                                               boolean ativa,
                                               int ordemExibicao) {
        validar(id >= 0, "ID da parte permitida nao pode ser negativo.");
        validar(configuracao != null, "Configuracao multissabor e obrigatoria.");
        validar(variacaoParte != null, "Variacao permitida como parte e obrigatoria.");
        validar(ordemExibicao >= 0, "Ordem de exibicao da parte nao pode ser negativa.");
        this.id = id;
        this.configuracao = configuracao;
        this.variacaoParte = variacaoParte;
        this.ativa = ativa;
        this.ordemExibicao = ordemExibicao;
        this.criadoEm = LocalDateTime.now();
    }

    public void atualizar(boolean ativa, int ordemExibicao) {
        validar(ordemExibicao >= 0, "Ordem de exibicao da parte nao pode ser negativa.");
        this.ativa = ativa;
        this.ordemExibicao = ordemExibicao;
    }

    @PrePersist
    void prePersist() {
        if (criadoEm == null) {
            criadoEm = LocalDateTime.now();
        }
    }

    public long getId() { return id; }
    public ConfiguracaoComposicaoMultipla getConfiguracao() { return configuracao; }
    public VariacaoItemCardapio getVariacaoParte() { return variacaoParte; }
    public boolean isAtiva() { return ativa == null || ativa; }
    public int getOrdemExibicao() { return ordemExibicao; }
    public LocalDateTime getCriadoEm() { return criadoEm; }

    private static void validar(boolean condicao, String mensagem) {
        if (!condicao) throw new IllegalArgumentException(mensagem);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConfiguracaoComposicaoMultiplaParte other)) return false;
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
