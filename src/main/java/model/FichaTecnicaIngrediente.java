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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "fichas_tecnicas_ingredientes")
public class FichaTecnicaIngrediente {

    public static final int ESCALA_QUANTIDADE = 6;
    public static final int ESCALA_PERCENTUAL = 4;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "ficha_tecnica_versao_id", nullable = false)
    private FichaTecnicaVersao fichaTecnicaVersao;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "unidade_informada_id", nullable = false)
    private UnidadeMedida unidadeInformada;

    @Column(name = "quantidade_informada", nullable = false, precision = 19, scale = ESCALA_QUANTIDADE)
    private BigDecimal quantidadeInformada;

    @Column(name = "quantidade_liquida_base", precision = 19, scale = ESCALA_QUANTIDADE)
    private BigDecimal quantidadeLiquidaBase;

    @Column(name = "percentual_perda", precision = 9, scale = ESCALA_PERCENTUAL)
    private BigDecimal percentualPerda;

    @Column(name = "quantidade_bruta_base", nullable = false, precision = 19, scale = ESCALA_QUANTIDADE)
    private BigDecimal quantidadeBrutaBase;

    @Column(name = "ingrediente_critico", nullable = false)
    private Boolean ingredienteCritico = Boolean.FALSE;

    @Enumerated(EnumType.STRING)
    @Column(name = "politica_falta", nullable = false, length = 30)
    private PoliticaFaltaIngrediente politicaFalta;

    @Column(name = "ordem_preparo", nullable = false)
    private int ordem;

    @Column(length = 1000)
    private String observacao;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    protected FichaTecnicaIngrediente() {
        // JPA
    }

    public FichaTecnicaIngrediente(long id,
                                   FichaTecnicaVersao fichaTecnicaVersao,
                                   Produto produto,
                                   UnidadeMedida unidadeInformada,
                                   BigDecimal quantidadeInformada,
                                   BigDecimal quantidadeLiquidaBase,
                                   BigDecimal percentualPerda,
                                   BigDecimal quantidadeBrutaBase,
                                   Boolean ingredienteCritico,
                                   PoliticaFaltaIngrediente politicaFalta,
                                   int ordem,
                                   String observacao) {
        validar(id >= 0, "ID do ingrediente nao pode ser negativo.");
        this.id = id;
        this.criadoEm = LocalDateTime.now();
        atualizar(fichaTecnicaVersao, produto, unidadeInformada, quantidadeInformada, quantidadeLiquidaBase,
                percentualPerda, quantidadeBrutaBase, ingredienteCritico, politicaFalta, ordem, observacao);
    }

    public void atualizar(FichaTecnicaVersao fichaTecnicaVersao,
                          Produto produto,
                          UnidadeMedida unidadeInformada,
                          BigDecimal quantidadeInformada,
                          BigDecimal quantidadeLiquidaBase,
                          BigDecimal percentualPerda,
                          BigDecimal quantidadeBrutaBase,
                          Boolean ingredienteCritico,
                          PoliticaFaltaIngrediente politicaFalta,
                          int ordem,
                          String observacao) {
        validar(fichaTecnicaVersao != null, "Versao da ficha tecnica e obrigatoria.");
        fichaTecnicaVersao.exigirRascunho();
        validar(produto != null, "Produto do ingrediente e obrigatorio.");
        validar(produto.isAtivo(), "Produto inativo nao pode ser usado em ficha tecnica.");
        validar(unidadeInformada != null, "Unidade informada e obrigatoria.");
        validar(quantidadeInformada != null && quantidadeInformada.compareTo(BigDecimal.ZERO) > 0,
                "Quantidade informada deve ser maior que zero.");
        validar(quantidadeBrutaBase != null && quantidadeBrutaBase.compareTo(BigDecimal.ZERO) > 0,
                "Quantidade bruta em unidade-base deve ser maior que zero.");
        validar(percentualPerda == null || percentualPerda.compareTo(BigDecimal.ZERO) >= 0,
                "Percentual de perda nao pode ser negativo.");
        validar(percentualPerda == null || percentualPerda.compareTo(new BigDecimal("100")) < 0,
                "Percentual de perda deve ser menor que 100.");
        validar(ordem >= 0, "Ordem do ingrediente nao pode ser negativa.");

        this.fichaTecnicaVersao = fichaTecnicaVersao;
        this.produto = produto;
        this.unidadeInformada = unidadeInformada;
        this.quantidadeInformada = quantidadeInformada.setScale(ESCALA_QUANTIDADE, RoundingMode.HALF_UP);
        this.quantidadeLiquidaBase = quantidadeLiquidaBase == null
                ? null
                : quantidadeLiquidaBase.setScale(ESCALA_QUANTIDADE, RoundingMode.HALF_UP);
        this.percentualPerda = percentualPerda == null
                ? null
                : percentualPerda.setScale(ESCALA_PERCENTUAL, RoundingMode.HALF_UP);
        this.quantidadeBrutaBase = quantidadeBrutaBase.setScale(ESCALA_QUANTIDADE, RoundingMode.HALF_UP);
        this.ingredienteCritico = ingredienteCritico != null && ingredienteCritico;
        this.politicaFalta = politicaFalta == null ? PoliticaFaltaIngrediente.HERDAR_PRODUTO : politicaFalta;
        this.ordem = ordem;
        this.observacao = observacao == null ? null : observacao.trim();
    }

    public long getId() { return id; }
    public FichaTecnicaVersao getFichaTecnicaVersao() { return fichaTecnicaVersao; }
    public Produto getProduto() { return produto; }
    public UnidadeMedida getUnidadeInformada() { return unidadeInformada; }
    public BigDecimal getQuantidadeInformada() { return quantidadeInformada; }
    public BigDecimal getQuantidadeLiquidaBase() { return quantidadeLiquidaBase; }
    public BigDecimal getPercentualPerda() { return percentualPerda; }
    public BigDecimal getQuantidadeBrutaBase() { return quantidadeBrutaBase; }
    public boolean isIngredienteCritico() { return ingredienteCritico != null && ingredienteCritico; }
    public PoliticaFaltaIngrediente getPoliticaFalta() { return politicaFalta; }
    public int getOrdem() { return ordem; }
    public String getObservacao() { return observacao; }
    public LocalDateTime getCriadoEm() { return criadoEm; }

    private static void validar(boolean condicao, String mensagem) {
        if (!condicao) throw new IllegalArgumentException(mensagem);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FichaTecnicaIngrediente other)) return false;
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
