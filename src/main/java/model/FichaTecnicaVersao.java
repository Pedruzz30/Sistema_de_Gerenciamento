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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(
        name = "fichas_tecnicas_versoes",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ficha_versao_numero",
                columnNames = {"ficha_tecnica_id", "numero_versao"}
        )
)
public class FichaTecnicaVersao {

    public static final int ESCALA_QUANTIDADE = 6;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "ficha_tecnica_id", nullable = false)
    private FichaTecnica fichaTecnica;

    @Column(name = "numero_versao", nullable = false)
    private int numeroVersao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StatusFichaTecnicaVersao status;

    @Column(name = "rendimento_quantidade", nullable = false, precision = 19, scale = ESCALA_QUANTIDADE)
    private BigDecimal rendimentoQuantidade;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "rendimento_unidade_id", nullable = false)
    private UnidadeMedida rendimentoUnidade;

    @Column(name = "quantidade_por_porcao", precision = 19, scale = ESCALA_QUANTIDADE)
    private BigDecimal quantidadePorPorcao;

    @Column(name = "tempo_preparo_minutos")
    private Integer tempoPreparoMinutos;

    @Column(name = "observacao_interna", length = 1000)
    private String observacaoInterna;

    @Column(name = "motivo_alteracao", length = 500)
    private String motivoAlteracao;

    @Column(name = "criada_em", nullable = false)
    private LocalDateTime criadaEm;

    @Column(name = "criada_por")
    private Long criadaPor;

    @Column(name = "publicada_em")
    private LocalDateTime publicadaEm;

    @Column(name = "publicada_por")
    private Long publicadaPor;

    @Column(name = "substituida_em")
    private LocalDateTime substituidaEm;

    @Version
    private long version;

    protected FichaTecnicaVersao() {
        // JPA
    }

    public FichaTecnicaVersao(long id,
                              FichaTecnica fichaTecnica,
                              int numeroVersao,
                              BigDecimal rendimentoQuantidade,
                              UnidadeMedida rendimentoUnidade,
                              BigDecimal quantidadePorPorcao,
                              Integer tempoPreparoMinutos,
                              String observacaoInterna,
                              String motivoAlteracao,
                              Long criadaPor) {
        validar(id >= 0, "ID da versao da ficha tecnica nao pode ser negativo.");
        validar(fichaTecnica != null, "Ficha tecnica e obrigatoria.");
        validar(numeroVersao > 0, "Numero da versao deve ser maior que zero.");
        this.id = id;
        this.fichaTecnica = fichaTecnica;
        this.numeroVersao = numeroVersao;
        this.status = StatusFichaTecnicaVersao.RASCUNHO;
        this.criadaEm = LocalDateTime.now();
        this.criadaPor = criadaPor;
        atualizarRascunho(rendimentoQuantidade, rendimentoUnidade, quantidadePorPorcao,
                tempoPreparoMinutos, observacaoInterna, motivoAlteracao);
    }

    public void atualizarRascunho(BigDecimal rendimentoQuantidade,
                                  UnidadeMedida rendimentoUnidade,
                                  BigDecimal quantidadePorPorcao,
                                  Integer tempoPreparoMinutos,
                                  String observacaoInterna,
                                  String motivoAlteracao) {
        exigirRascunho();
        validar(rendimentoQuantidade != null && rendimentoQuantidade.compareTo(BigDecimal.ZERO) > 0,
                "Rendimento da ficha tecnica deve ser maior que zero.");
        validar(rendimentoUnidade != null, "Unidade de rendimento e obrigatoria.");
        validar(quantidadePorPorcao == null || quantidadePorPorcao.compareTo(BigDecimal.ZERO) > 0,
                "Quantidade por porcao deve ser maior que zero.");
        validar(tempoPreparoMinutos == null || tempoPreparoMinutos >= 0,
                "Tempo de preparo nao pode ser negativo.");

        this.rendimentoQuantidade = rendimentoQuantidade.setScale(ESCALA_QUANTIDADE, RoundingMode.HALF_UP);
        this.rendimentoUnidade = rendimentoUnidade;
        this.quantidadePorPorcao = quantidadePorPorcao == null
                ? null
                : quantidadePorPorcao.setScale(ESCALA_QUANTIDADE, RoundingMode.HALF_UP);
        this.tempoPreparoMinutos = tempoPreparoMinutos;
        this.observacaoInterna = observacaoInterna == null ? null : observacaoInterna.trim();
        this.motivoAlteracao = motivoAlteracao == null ? null : motivoAlteracao.trim();
    }

    public void publicar(Long publicadaPor, String motivo) {
        exigirRascunho();
        if (numeroVersao > 1) {
            validar(motivo != null && !motivo.isBlank(), "Motivo da publicacao e obrigatorio para nova versao.");
        }
        this.status = StatusFichaTecnicaVersao.PUBLICADA;
        this.publicadaPor = publicadaPor;
        this.publicadaEm = LocalDateTime.now();
        this.motivoAlteracao = motivo == null ? motivoAlteracao : motivo.trim();
    }

    public void substituir() {
        validar(status == StatusFichaTecnicaVersao.PUBLICADA,
                "Apenas versao publicada pode ser substituida.");
        this.status = StatusFichaTecnicaVersao.SUBSTITUIDA;
        this.substituidaEm = LocalDateTime.now();
    }

    public void descartar() {
        exigirRascunho();
        this.status = StatusFichaTecnicaVersao.DESCARTADA;
    }

    public void exigirRascunho() {
        if (status != StatusFichaTecnicaVersao.RASCUNHO) {
            throw new IllegalStateException("Versao de ficha tecnica publicada/substituida/descartada e imutavel.");
        }
    }

    public long getId() { return id; }
    public FichaTecnica getFichaTecnica() { return fichaTecnica; }
    public int getNumeroVersao() { return numeroVersao; }
    public StatusFichaTecnicaVersao getStatus() { return status; }
    public BigDecimal getRendimentoQuantidade() { return rendimentoQuantidade; }
    public UnidadeMedida getRendimentoUnidade() { return rendimentoUnidade; }
    public BigDecimal getQuantidadePorPorcao() { return quantidadePorPorcao; }
    public Integer getTempoPreparoMinutos() { return tempoPreparoMinutos; }
    public String getObservacaoInterna() { return observacaoInterna; }
    public String getMotivoAlteracao() { return motivoAlteracao; }
    public LocalDateTime getCriadaEm() { return criadaEm; }
    public Long getCriadaPor() { return criadaPor; }
    public LocalDateTime getPublicadaEm() { return publicadaEm; }
    public Long getPublicadaPor() { return publicadaPor; }
    public LocalDateTime getSubstituidaEm() { return substituidaEm; }
    public long getVersion() { return version; }

    private static void validar(boolean condicao, String mensagem) {
        if (!condicao) throw new IllegalArgumentException(mensagem);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FichaTecnicaVersao other)) return false;
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
