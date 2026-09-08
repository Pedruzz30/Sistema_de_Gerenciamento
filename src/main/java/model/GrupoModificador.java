package model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(
        name = "grupos_modificadores",
        uniqueConstraints = @UniqueConstraint(name = "uk_grupo_modificador_codigo", columnNames = "codigo")
)
public class GrupoModificador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(length = 500)
    private String descricao;

    @Column(nullable = false, length = 80)
    private String codigo;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_selecao", nullable = false, length = 30)
    private TipoSelecaoModificador tipoSelecao;

    @Column(nullable = false)
    private Boolean obrigatorio = Boolean.FALSE;

    @Column(name = "quantidade_minima", nullable = false)
    private int quantidadeMinima;

    @Column(name = "quantidade_maxima", nullable = false)
    private int quantidadeMaxima;

    @Column(name = "permite_repeticao", nullable = false)
    private Boolean permiteRepeticao = Boolean.FALSE;

    @Column(nullable = false)
    private Boolean ativo = Boolean.TRUE;

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

    protected GrupoModificador() {
        // JPA
    }

    public GrupoModificador(long id,
                            String nome,
                            String descricao,
                            String codigo,
                            TipoSelecaoModificador tipoSelecao,
                            Boolean obrigatorio,
                            Integer quantidadeMinima,
                            Integer quantidadeMaxima,
                            Boolean permiteRepeticao,
                            Boolean ativo,
                            Integer ordemExibicao,
                            Long criadoPor) {
        validar(id >= 0, "ID do grupo de modificador nao pode ser negativo.");
        this.id = id;
        this.criadoPor = criadoPor;
        this.criadoEm = LocalDateTime.now();
        this.atualizadoEm = this.criadoEm;
        atualizar(nome, descricao, codigo, tipoSelecao, obrigatorio, quantidadeMinima,
                quantidadeMaxima, permiteRepeticao, ativo, ordemExibicao);
    }

    public void atualizar(String nome,
                          String descricao,
                          String codigo,
                          TipoSelecaoModificador tipoSelecao,
                          Boolean obrigatorio,
                          Integer quantidadeMinima,
                          Integer quantidadeMaxima,
                          Boolean permiteRepeticao,
                          Boolean ativo,
                          Integer ordemExibicao) {
        validar(nome != null && !nome.isBlank(), "Nome do grupo e obrigatorio.");
        validar(codigo != null && !codigo.isBlank(), "Codigo do grupo e obrigatorio.");
        validar(tipoSelecao != null, "Tipo de selecao do grupo e obrigatorio.");
        int minimo = quantidadeMinima == null ? 0 : quantidadeMinima;
        int maximo = quantidadeMaxima == null ? (tipoSelecao == TipoSelecaoModificador.UNICA ? 1 : minimo) : quantidadeMaxima;
        validar(minimo >= 0, "Quantidade minima nao pode ser negativa.");
        validar(maximo >= minimo, "Quantidade maxima precisa ser maior ou igual a minima.");
        boolean obrig = obrigatorio != null && obrigatorio;
        validar(!obrig || minimo >= 1, "Grupo obrigatorio precisa exigir pelo menos uma escolha.");
        validar(tipoSelecao != TipoSelecaoModificador.UNICA || maximo == 1,
                "Grupo de selecao unica nao pode permitir mais de uma escolha.");
        int ordem = ordemExibicao == null ? 0 : ordemExibicao;
        validar(ordem >= 0, "Ordem de exibicao nao pode ser negativa.");

        this.nome = nome.trim();
        this.descricao = normalizar(descricao);
        this.codigo = normalizarCodigo(codigo);
        this.tipoSelecao = tipoSelecao;
        this.obrigatorio = obrig;
        this.quantidadeMinima = minimo;
        this.quantidadeMaxima = maximo;
        this.permiteRepeticao = permiteRepeticao != null && permiteRepeticao;
        this.ativo = ativo == null || ativo;
        this.ordemExibicao = ordem;
        this.atualizadoEm = LocalDateTime.now();
    }

    public void alterarStatus(boolean ativo) {
        this.ativo = ativo;
        this.atualizadoEm = LocalDateTime.now();
    }

    public long getId() { return id; }
    public String getNome() { return nome; }
    public String getDescricao() { return descricao; }
    public String getCodigo() { return codigo; }
    public TipoSelecaoModificador getTipoSelecao() { return tipoSelecao; }
    public boolean isObrigatorio() { return obrigatorio != null && obrigatorio; }
    public int getQuantidadeMinima() { return quantidadeMinima; }
    public int getQuantidadeMaxima() { return quantidadeMaxima; }
    public boolean isPermiteRepeticao() { return permiteRepeticao != null && permiteRepeticao; }
    public boolean isAtivo() { return ativo == null || ativo; }
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
        if (!(o instanceof GrupoModificador other)) return false;
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
