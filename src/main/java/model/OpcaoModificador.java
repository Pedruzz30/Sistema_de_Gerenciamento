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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(
        name = "opcoes_modificadores",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_opcao_modificador_grupo_codigo",
                columnNames = {"grupo_modificador_id", "codigo"}
        )
)
public class OpcaoModificador {

    public static final int ESCALA_DINHEIRO = 4;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "grupo_modificador_id", nullable = false)
    private GrupoModificador grupoModificador;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(length = 500)
    private String descricao;

    @Column(nullable = false, length = 80)
    private String codigo;

    @Column(name = "acrescimo_preco", nullable = false, precision = 19, scale = ESCALA_DINHEIRO)
    private BigDecimal acrescimoPreco;

    @Column(name = "quantidade_maxima_por_item")
    private Integer quantidadeMaximaPorItem;

    @Column(name = "selecionada_por_padrao", nullable = false)
    private Boolean selecionadaPorPadrao = Boolean.FALSE;

    @Column(nullable = false)
    private Boolean ativa = Boolean.TRUE;

    @Column(name = "ordem_exibicao", nullable = false)
    private int ordemExibicao;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "criado_por")
    private Long criadoPor;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    @Version
    private long version;

    protected OpcaoModificador() {
        // JPA
    }

    public OpcaoModificador(long id,
                            GrupoModificador grupoModificador,
                            String nome,
                            String descricao,
                            String codigo,
                            BigDecimal acrescimoPreco,
                            Integer quantidadeMaximaPorItem,
                            Boolean selecionadaPorPadrao,
                            Boolean ativa,
                            Integer ordemExibicao,
                            Long criadoPor) {
        validar(id >= 0, "ID da opcao de modificador nao pode ser negativo.");
        this.id = id;
        this.criadoPor = criadoPor;
        this.criadoEm = LocalDateTime.now();
        this.atualizadoEm = this.criadoEm;
        atualizar(grupoModificador, nome, descricao, codigo, acrescimoPreco,
                quantidadeMaximaPorItem, selecionadaPorPadrao, ativa, ordemExibicao);
    }

    public void atualizar(GrupoModificador grupoModificador,
                          String nome,
                          String descricao,
                          String codigo,
                          BigDecimal acrescimoPreco,
                          Integer quantidadeMaximaPorItem,
                          Boolean selecionadaPorPadrao,
                          Boolean ativa,
                          Integer ordemExibicao) {
        validar(grupoModificador != null, "Grupo do modificador e obrigatorio.");
        validar(nome != null && !nome.isBlank(), "Nome da opcao e obrigatorio.");
        validar(codigo != null && !codigo.isBlank(), "Codigo da opcao e obrigatorio.");
        BigDecimal acrescimo = acrescimoPreco == null ? BigDecimal.ZERO : acrescimoPreco;
        validar(acrescimo.compareTo(BigDecimal.ZERO) >= 0,
                "Acrescimo negativo nao e permitido nesta fase.");
        validar(quantidadeMaximaPorItem == null || quantidadeMaximaPorItem > 0,
                "Quantidade maxima da opcao deve ser maior que zero.");
        int ordem = ordemExibicao == null ? 0 : ordemExibicao;
        validar(ordem >= 0, "Ordem de exibicao nao pode ser negativa.");

        this.grupoModificador = grupoModificador;
        this.nome = nome.trim();
        this.descricao = normalizar(descricao);
        this.codigo = normalizarCodigo(codigo);
        this.acrescimoPreco = acrescimo.setScale(ESCALA_DINHEIRO, RoundingMode.HALF_UP);
        this.quantidadeMaximaPorItem = quantidadeMaximaPorItem;
        this.selecionadaPorPadrao = selecionadaPorPadrao != null && selecionadaPorPadrao;
        this.ativa = ativa == null || ativa;
        this.ordemExibicao = ordem;
        this.atualizadoEm = LocalDateTime.now();
    }

    public void alterarStatus(boolean ativa) {
        this.ativa = ativa;
        this.atualizadoEm = LocalDateTime.now();
    }

    public long getId() { return id; }
    public GrupoModificador getGrupoModificador() { return grupoModificador; }
    public String getNome() { return nome; }
    public String getDescricao() { return descricao; }
    public String getCodigo() { return codigo; }
    public BigDecimal getAcrescimoPreco() { return acrescimoPreco; }
    public Integer getQuantidadeMaximaPorItem() { return quantidadeMaximaPorItem; }
    public boolean isSelecionadaPorPadrao() { return selecionadaPorPadrao != null && selecionadaPorPadrao; }
    public boolean isAtiva() { return ativa == null || ativa; }
    public int getOrdemExibicao() { return ordemExibicao; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public Long getCriadoPor() { return criadoPor; }
    public LocalDateTime getAtualizadoEm() { return atualizadoEm; }
    public long getVersion() { return version; }

    private static String normalizarCodigo(String valor) {
        return valor.trim().toUpperCase();
    }

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
        if (!(o instanceof OpcaoModificador other)) return false;
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
