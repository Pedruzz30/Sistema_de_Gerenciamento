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
@Table(name = "opcoes_modificadores_componentes")
public class OpcaoModificadorComponente {

    public static final int ESCALA_QUANTIDADE = 6;
    public static final int ESCALA_MULTIPLICADOR = 6;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "opcao_modificador_id", nullable = false)
    private OpcaoModificador opcaoModificador;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_efeito", nullable = false, length = 40)
    private TipoEfeitoModificador tipoEfeito;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "produto_origem_id")
    private Produto produtoOrigem;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "produto_destino_id")
    private Produto produtoDestino;

    @Column(name = "quantidade_base", precision = 19, scale = ESCALA_QUANTIDADE)
    private BigDecimal quantidadeBase;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "unidade_base_id")
    private UnidadeMedida unidadeBase;

    @Column(precision = 19, scale = ESCALA_MULTIPLICADOR)
    private BigDecimal multiplicador;

    @Enumerated(EnumType.STRING)
    @Column(name = "politica_falta", nullable = false, length = 30)
    private PoliticaFaltaIngrediente politicaFalta;

    @Column(length = 1000)
    private String observacao;

    @Column(name = "ordem", nullable = false)
    private int ordem;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    protected OpcaoModificadorComponente() {
        // JPA
    }

    public OpcaoModificadorComponente(long id,
                                      OpcaoModificador opcaoModificador,
                                      TipoEfeitoModificador tipoEfeito,
                                      Produto produtoOrigem,
                                      Produto produtoDestino,
                                      BigDecimal quantidadeBase,
                                      UnidadeMedida unidadeBase,
                                      BigDecimal multiplicador,
                                      PoliticaFaltaIngrediente politicaFalta,
                                      String observacao,
                                      Integer ordem) {
        validar(id >= 0, "ID do componente nao pode ser negativo.");
        this.id = id;
        this.criadoEm = LocalDateTime.now();
        atualizar(opcaoModificador, tipoEfeito, produtoOrigem, produtoDestino, quantidadeBase,
                unidadeBase, multiplicador, politicaFalta, observacao, ordem);
    }

    public void atualizar(OpcaoModificador opcaoModificador,
                          TipoEfeitoModificador tipoEfeito,
                          Produto produtoOrigem,
                          Produto produtoDestino,
                          BigDecimal quantidadeBase,
                          UnidadeMedida unidadeBase,
                          BigDecimal multiplicador,
                          PoliticaFaltaIngrediente politicaFalta,
                          String observacao,
                          Integer ordem) {
        validar(opcaoModificador != null, "Opcao do componente e obrigatoria.");
        validar(tipoEfeito != null, "Tipo de efeito do componente e obrigatorio.");
        int ordemValor = ordem == null ? 0 : ordem;
        validar(ordemValor >= 0, "Ordem do componente nao pode ser negativa.");
        boolean semImpacto = tipoEfeito == TipoEfeitoModificador.SEM_IMPACTO_ESTOQUE;
        if (semImpacto) {
            validar(produtoOrigem == null && produtoDestino == null,
                    "Componente sem impacto nao deve possuir produtos.");
            validar(quantidadeBase == null, "Componente sem impacto nao deve possuir quantidade.");
            validar(unidadeBase == null, "Componente sem impacto nao deve possuir unidade.");
        } else {
            validar(quantidadeBase != null && quantidadeBase.compareTo(BigDecimal.ZERO) > 0,
                    "Quantidade do componente deve ser maior que zero.");
            validar(unidadeBase != null, "Unidade-base do componente e obrigatoria.");
        }
        if (tipoEfeito == TipoEfeitoModificador.ADICIONAR) {
            validar(produtoDestino != null, "Efeito ADICIONAR exige produto de destino.");
            validar(produtoOrigem == null, "Efeito ADICIONAR nao deve possuir produto de origem.");
        }
        if (tipoEfeito == TipoEfeitoModificador.REMOVER) {
            validar(produtoOrigem != null, "Efeito REMOVER exige produto de origem.");
            validar(produtoDestino == null, "Efeito REMOVER nao deve possuir produto de destino.");
        }
        if (tipoEfeito == TipoEfeitoModificador.SUBSTITUIR) {
            validar(produtoOrigem != null || produtoDestino != null,
                    "Efeito SUBSTITUIR exige produto de origem ou destino.");
        }
        validar(multiplicador == null || multiplicador.compareTo(BigDecimal.ZERO) > 0,
                "Multiplicador deve ser maior que zero.");

        this.opcaoModificador = opcaoModificador;
        this.tipoEfeito = tipoEfeito;
        this.produtoOrigem = produtoOrigem;
        this.produtoDestino = produtoDestino;
        this.quantidadeBase = quantidadeBase == null
                ? null
                : quantidadeBase.setScale(ESCALA_QUANTIDADE, RoundingMode.HALF_UP);
        this.unidadeBase = unidadeBase;
        this.multiplicador = multiplicador == null
                ? null
                : multiplicador.setScale(ESCALA_MULTIPLICADOR, RoundingMode.HALF_UP);
        this.politicaFalta = politicaFalta == null ? PoliticaFaltaIngrediente.HERDAR_PRODUTO : politicaFalta;
        this.observacao = normalizar(observacao);
        this.ordem = ordemValor;
    }

    public long getId() { return id; }
    public OpcaoModificador getOpcaoModificador() { return opcaoModificador; }
    public TipoEfeitoModificador getTipoEfeito() { return tipoEfeito; }
    public Produto getProdutoOrigem() { return produtoOrigem; }
    public Produto getProdutoDestino() { return produtoDestino; }
    public BigDecimal getQuantidadeBase() { return quantidadeBase; }
    public UnidadeMedida getUnidadeBase() { return unidadeBase; }
    public BigDecimal getMultiplicador() { return multiplicador; }
    public PoliticaFaltaIngrediente getPoliticaFalta() { return politicaFalta; }
    public String getObservacao() { return observacao; }
    public int getOrdem() { return ordem; }
    public LocalDateTime getCriadoEm() { return criadoEm; }

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
        if (!(o instanceof OpcaoModificadorComponente other)) return false;
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
