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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(
        name = "configuracoes_composicao_multipla",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_config_composicao_multipla_variacao",
                columnNames = "variacao_item_cardapio_id"
        )
)
public class ConfiguracaoComposicaoMultipla {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "variacao_item_cardapio_id", nullable = false)
    private VariacaoItemCardapio variacaoItemCardapio;

    @Column(nullable = false)
    private Boolean ativo = Boolean.FALSE;

    @Column(name = "minimo_partes", nullable = false)
    private int minimoPartes;

    @Column(name = "maximo_partes", nullable = false)
    private int maximoPartes;

    @Column(name = "quantidades_permitidas", length = 120)
    private String quantidadesPermitidas;

    @Column(name = "permite_repetir_partes", nullable = false)
    private Boolean permiteRepetirPartes = Boolean.FALSE;

    @Column(name = "proporcoes_iguais_obrigatorias", nullable = false)
    private Boolean proporcoesIguaisObrigatorias = Boolean.TRUE;

    @Column(name = "permite_proporcao_personalizada", nullable = false)
    private Boolean permiteProporcaoPersonalizada = Boolean.FALSE;

    @Enumerated(EnumType.STRING)
    @Column(name = "politica_preco", nullable = false, length = 40)
    private PoliticaPrecoComposicaoMultipla politicaPreco = PoliticaPrecoComposicaoMultipla.MAIOR_PRECO;

    @Enumerated(EnumType.STRING)
    @Column(name = "politica_arredondamento", nullable = false, length = 40)
    private PoliticaArredondamentoComposicaoMultipla politicaArredondamento = PoliticaArredondamentoComposicaoMultipla.HALF_UP;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    @Version
    private long version;

    protected ConfiguracaoComposicaoMultipla() {
        // JPA
    }

    public ConfiguracaoComposicaoMultipla(long id,
                                          VariacaoItemCardapio variacaoItemCardapio,
                                          boolean ativo,
                                          int minimoPartes,
                                          int maximoPartes,
                                          String quantidadesPermitidas,
                                          boolean permiteRepetirPartes,
                                          boolean proporcoesIguaisObrigatorias,
                                          boolean permiteProporcaoPersonalizada,
                                          PoliticaPrecoComposicaoMultipla politicaPreco,
                                          PoliticaArredondamentoComposicaoMultipla politicaArredondamento) {
        validar(id >= 0, "ID da configuracao multissabor nao pode ser negativo.");
        this.id = id;
        this.criadoEm = LocalDateTime.now();
        atualizar(
                variacaoItemCardapio,
                ativo,
                minimoPartes,
                maximoPartes,
                quantidadesPermitidas,
                permiteRepetirPartes,
                proporcoesIguaisObrigatorias,
                permiteProporcaoPersonalizada,
                politicaPreco,
                politicaArredondamento
        );
    }

    public void atualizar(VariacaoItemCardapio variacaoItemCardapio,
                          boolean ativo,
                          int minimoPartes,
                          int maximoPartes,
                          String quantidadesPermitidas,
                          boolean permiteRepetirPartes,
                          boolean proporcoesIguaisObrigatorias,
                          boolean permiteProporcaoPersonalizada,
                          PoliticaPrecoComposicaoMultipla politicaPreco,
                          PoliticaArredondamentoComposicaoMultipla politicaArredondamento) {
        validar(variacaoItemCardapio != null, "Variacao da configuracao multissabor e obrigatoria.");
        validar(minimoPartes >= 1, "Minimo de partes deve ser maior ou igual a um.");
        validar(maximoPartes >= minimoPartes, "Maximo de partes deve ser maior ou igual ao minimo.");
        validar(politicaPreco != null, "Politica de preco multissabor e obrigatoria.");
        validar(politicaArredondamento != null, "Politica de arredondamento multissabor e obrigatoria.");
        this.variacaoItemCardapio = variacaoItemCardapio;
        this.ativo = ativo;
        this.minimoPartes = minimoPartes;
        this.maximoPartes = maximoPartes;
        this.quantidadesPermitidas = normalizar(quantidadesPermitidas);
        this.permiteRepetirPartes = permiteRepetirPartes;
        this.proporcoesIguaisObrigatorias = proporcoesIguaisObrigatorias;
        this.permiteProporcaoPersonalizada = permiteProporcaoPersonalizada;
        this.politicaPreco = politicaPreco;
        this.politicaArredondamento = politicaArredondamento;
        this.atualizadoEm = LocalDateTime.now();
    }

    @PrePersist
    void prePersist() {
        if (criadoEm == null) {
            criadoEm = LocalDateTime.now();
        }
        if (atualizadoEm == null) {
            atualizadoEm = criadoEm;
        }
    }

    @PreUpdate
    void preUpdate() {
        atualizadoEm = LocalDateTime.now();
    }

    public long getId() { return id; }
    public VariacaoItemCardapio getVariacaoItemCardapio() { return variacaoItemCardapio; }
    public boolean isAtivo() { return ativo != null && ativo; }
    public int getMinimoPartes() { return minimoPartes; }
    public int getMaximoPartes() { return maximoPartes; }
    public String getQuantidadesPermitidas() { return quantidadesPermitidas; }
    public boolean isPermiteRepetirPartes() { return permiteRepetirPartes != null && permiteRepetirPartes; }
    public boolean isProporcoesIguaisObrigatorias() {
        return proporcoesIguaisObrigatorias == null || proporcoesIguaisObrigatorias;
    }
    public boolean isPermiteProporcaoPersonalizada() {
        return permiteProporcaoPersonalizada != null && permiteProporcaoPersonalizada;
    }
    public PoliticaPrecoComposicaoMultipla getPoliticaPreco() { return politicaPreco; }
    public PoliticaArredondamentoComposicaoMultipla getPoliticaArredondamento() { return politicaArredondamento; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public LocalDateTime getAtualizadoEm() { return atualizadoEm; }
    public long getVersion() { return version; }

    private static String normalizar(String valor) {
        if (valor == null) return null;
        String texto = valor.trim();
        return texto.isEmpty() ? null : texto;
    }

    private static void validar(boolean condicao, String mensagem) {
        if (!condicao) throw new IllegalArgumentException(mensagem);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConfiguracaoComposicaoMultipla other)) return false;
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
