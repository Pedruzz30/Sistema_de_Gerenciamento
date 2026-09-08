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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

@Entity
@Table(
        name = "unidades_medida",
        uniqueConstraints = @UniqueConstraint(name = "uk_unidades_medida_codigo", columnNames = "codigo")
)
public class UnidadeMedida {

    public static final int ESCALA_FATOR = 8;
    public static final int ESCALA_QUANTIDADE = 6;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(nullable = false, length = 40)
    private String codigo;

    @Column(nullable = false, length = 120)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DimensaoUnidade dimensao;

    @Column(name = "fator_base", nullable = false, precision = 19, scale = ESCALA_FATOR)
    private BigDecimal fatorBase;

    @Column(nullable = false)
    private Boolean ativa = Boolean.TRUE;

    protected UnidadeMedida() {
        // JPA
    }

    public UnidadeMedida(long id,
                         String codigo,
                         String nome,
                         DimensaoUnidade dimensao,
                         BigDecimal fatorBase,
                         Boolean ativa) {
        validar(id >= 0, "ID da unidade nao pode ser negativo.");
        validar(codigo != null && !codigo.isBlank(), "Codigo da unidade e obrigatorio.");
        validar(nome != null && !nome.isBlank(), "Nome da unidade e obrigatorio.");
        validar(dimensao != null, "Dimensao da unidade e obrigatoria.");
        validar(fatorBase != null && fatorBase.compareTo(BigDecimal.ZERO) > 0,
                "Fator de conversao para base deve ser maior que zero.");

        this.id = id;
        this.codigo = codigo.trim().toUpperCase();
        this.nome = nome.trim();
        this.dimensao = dimensao;
        this.fatorBase = fatorBase.setScale(ESCALA_FATOR, RoundingMode.HALF_UP);
        this.ativa = ativa == null ? Boolean.TRUE : ativa;
    }

    public UnidadeMedida comId(long novoId) {
        validar(novoId > 0, "Novo ID da unidade deve ser maior que zero.");
        return new UnidadeMedida(novoId, codigo, nome, dimensao, fatorBase, ativa);
    }

    public BigDecimal converterParaBase(BigDecimal quantidade) {
        validar(quantidade != null && quantidade.compareTo(BigDecimal.ZERO) >= 0,
                "Quantidade para conversao deve ser nao negativa.");
        return quantidade.multiply(fatorBase).setScale(ESCALA_QUANTIDADE, RoundingMode.HALF_UP);
    }

    public void exigirCompatibilidade(UnidadeMedida destino) {
        validar(destino != null, "Unidade destino e obrigatoria.");
        validar(dimensao == destino.dimensao,
                "Unidades incompatíveis: " + codigo + " (" + dimensao + ") para " +
                        destino.codigo + " (" + destino.dimensao + ").");
    }

    public long getId() { return id; }
    public String getCodigo() { return codigo; }
    public String getNome() { return nome; }
    public DimensaoUnidade getDimensao() { return dimensao; }
    public BigDecimal getFatorBase() { return fatorBase; }
    public boolean isAtiva() { return ativa == null || ativa; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UnidadeMedida other)) return false;
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
