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
import jakarta.persistence.Version;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(
        name = "variacao_grupos_modificadores",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_variacao_grupo_modificador",
                columnNames = {"variacao_item_cardapio_id", "grupo_modificador_id"}
        )
)
public class VariacaoGrupoModificador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "variacao_item_cardapio_id", nullable = false)
    private VariacaoItemCardapio variacaoItemCardapio;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "grupo_modificador_id", nullable = false)
    private GrupoModificador grupoModificador;

    @Column(name = "obrigatorio_override")
    private Boolean obrigatorioOverride;

    @Column(name = "quantidade_minima_override")
    private Integer quantidadeMinimaOverride;

    @Column(name = "quantidade_maxima_override")
    private Integer quantidadeMaximaOverride;

    @Column(name = "ordem_exibicao", nullable = false)
    private int ordemExibicao;

    @Column(nullable = false)
    private Boolean ativo = Boolean.TRUE;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    @Version
    private long version;

    protected VariacaoGrupoModificador() {
        // JPA
    }

    public VariacaoGrupoModificador(long id,
                                    VariacaoItemCardapio variacaoItemCardapio,
                                    GrupoModificador grupoModificador,
                                    Boolean obrigatorioOverride,
                                    Integer quantidadeMinimaOverride,
                                    Integer quantidadeMaximaOverride,
                                    Integer ordemExibicao,
                                    Boolean ativo) {
        validar(id >= 0, "ID da associacao nao pode ser negativo.");
        this.id = id;
        this.criadoEm = LocalDateTime.now();
        this.atualizadoEm = this.criadoEm;
        atualizar(variacaoItemCardapio, grupoModificador, obrigatorioOverride,
                quantidadeMinimaOverride, quantidadeMaximaOverride, ordemExibicao, ativo);
    }

    public void atualizar(VariacaoItemCardapio variacaoItemCardapio,
                          GrupoModificador grupoModificador,
                          Boolean obrigatorioOverride,
                          Integer quantidadeMinimaOverride,
                          Integer quantidadeMaximaOverride,
                          Integer ordemExibicao,
                          Boolean ativo) {
        validar(variacaoItemCardapio != null, "Variacao e obrigatoria.");
        validar(grupoModificador != null, "Grupo e obrigatorio.");
        validar(quantidadeMinimaOverride == null || quantidadeMinimaOverride >= 0,
                "Quantidade minima override nao pode ser negativa.");
        validar(quantidadeMaximaOverride == null || quantidadeMaximaOverride >= 0,
                "Quantidade maxima override nao pode ser negativa.");
        if (quantidadeMinimaOverride != null && quantidadeMaximaOverride != null) {
            validar(quantidadeMaximaOverride >= quantidadeMinimaOverride,
                    "Quantidade maxima override deve ser maior ou igual a minima.");
        }
        int ordem = ordemExibicao == null ? 0 : ordemExibicao;
        validar(ordem >= 0, "Ordem de exibicao nao pode ser negativa.");

        this.variacaoItemCardapio = variacaoItemCardapio;
        this.grupoModificador = grupoModificador;
        this.obrigatorioOverride = obrigatorioOverride;
        this.quantidadeMinimaOverride = quantidadeMinimaOverride;
        this.quantidadeMaximaOverride = quantidadeMaximaOverride;
        this.ordemExibicao = ordem;
        this.ativo = ativo == null || ativo;
        this.atualizadoEm = LocalDateTime.now();
    }

    public void inativar() {
        this.ativo = Boolean.FALSE;
        this.atualizadoEm = LocalDateTime.now();
    }

    public long getId() { return id; }
    public VariacaoItemCardapio getVariacaoItemCardapio() { return variacaoItemCardapio; }
    public GrupoModificador getGrupoModificador() { return grupoModificador; }
    public Boolean getObrigatorioOverride() { return obrigatorioOverride; }
    public Integer getQuantidadeMinimaOverride() { return quantidadeMinimaOverride; }
    public Integer getQuantidadeMaximaOverride() { return quantidadeMaximaOverride; }
    public int getOrdemExibicao() { return ordemExibicao; }
    public boolean isAtivo() { return ativo == null || ativo; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public LocalDateTime getAtualizadoEm() { return atualizadoEm; }
    public long getVersion() { return version; }

    public boolean obrigatorioEfetivo() {
        return obrigatorioOverride == null ? grupoModificador.isObrigatorio() : obrigatorioOverride;
    }

    public int minimoEfetivo() {
        return quantidadeMinimaOverride == null ? grupoModificador.getQuantidadeMinima() : quantidadeMinimaOverride;
    }

    public int maximoEfetivo() {
        return quantidadeMaximaOverride == null ? grupoModificador.getQuantidadeMaxima() : quantidadeMaximaOverride;
    }

    private static void validar(boolean condicao, String mensagem) {
        if (!condicao) throw new IllegalArgumentException(mensagem);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VariacaoGrupoModificador other)) return false;
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
