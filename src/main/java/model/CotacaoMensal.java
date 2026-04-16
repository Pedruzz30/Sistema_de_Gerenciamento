package model;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Registra o preço de compra de um produto em um determinado mês.
 *
 * <h2>Analogia</h2>
 * Pense em uma planilha de controle de custos:
 * <pre>
 *   Produto   | Mês        | Preço unit. | Qtd | Gasto total
 *   Morango   | jan/2025   | R$  4,99    | 100 | R$  499,00
 *   Morango   | fev/2025   | R$  5,15    | 100 | R$  515,00  ← ficou mais caro
 *   Laranja   | jan/2025   | R$  2,50    |  50 | R$  125,00
 * </pre>
 * Cada linha é uma {@code CotacaoMensal}.
 *
 * <h2>Chave natural</h2>
 * Uma cotação é identificada unicamente pela combinação
 * ({@link Produto}, {@link YearMonth}). Não pode existir mais de uma
 * cotação para o mesmo produto no mesmo mês — o {@code CotacaoService}
 * substitui a cotação existente ao registrar uma nova para o mesmo par.
 * Por isso, {@link #equals} e {@link #hashCode} usam a chave natural
 * ({@code produto}, {@code mesReferencia}), não o ID sintético.
 *
 * <h2>Convenções monetárias</h2>
 * {@code precoUnitario} é armazenado como {@link BigDecimal} com escala 2
 * e {@link RoundingMode#HALF_UP}, eliminando o erro de ponto flutuante que
 * acumulava em {@link #calcularGastoTotal()} com {@code double}.
 */
@Entity
@Table(
        name = "cotacoes_mensais",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_cotacoes_mensais_produto_mes",
                columnNames = {"produto_id", "mes_referencia"}
        )
)
public class CotacaoMensal {

    // ── Constantes ────────────────────────────────────────────────────────────

    private static final int    ESCALA_MONETARIA = 2;
    private static final String FORMATO_MES      = "MM/yyyy";

    // ── Campos — todos imutáveis ──────────────────────────────────────────────

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @Convert(converter = YearMonthStringConverter.class)
    @Column(name = "mes_referencia", nullable = false, length = 7)
    private YearMonth mesReferencia;

    @Column(name = "preco_unitario", nullable = false, precision = 19, scale = 2)
    private BigDecimal precoUnitario;

    @Column(name = "quantidade_comprada", nullable = false)
    private int quantidadeComprada;

    // ── Construtor ────────────────────────────────────────────────────────────

    protected CotacaoMensal() {
        // Construtor exigido pelo JPA.
    }

    /**
     * Cria um registro de cotação mensal.
     *
     * <p>{@code id == 0} indica que a cotação ainda não foi persistida.
     * IDs negativos são rejeitados. Use {@link #comId(long)} para que o
     * repositório retorne a cópia com o ID definitivo.
     *
     * @param id                identificador; {@code >= 0} (0 = não persistido)
     * @param produto           produto cotado; não pode ser {@code null}
     * @param mesReferencia     mês de referência; não pode ser {@code null}
     * @param precoUnitario     preço médio por unidade no mês; deve ser {@code >= 0};
     *                          não pode ser {@code null}
     * @param quantidadeComprada total de unidades compradas no mês; deve ser {@code > 0}
     * @throws IllegalArgumentException se qualquer argumento obrigatório for inválido
     */
    public CotacaoMensal(long id,
                         Produto produto,
                         YearMonth mesReferencia,
                         BigDecimal precoUnitario,
                         int quantidadeComprada) {
        validar(id >= 0,                                    "ID não pode ser negativo.");
        validar(produto != null,                            "Produto é obrigatório.");
        validar(mesReferencia != null,                      "Mês de referência é obrigatório.");
        validar(precoUnitario != null,                      "Preço unitário é obrigatório.");
        validar(precoUnitario.compareTo(BigDecimal.ZERO) >= 0,
                "Preço não pode ser negativo.");
        validar(quantidadeComprada > 0,                     "Quantidade deve ser maior que zero.");

        this.id                 = id;
        this.produto            = produto;
        this.mesReferencia      = mesReferencia;
        this.precoUnitario      = precoUnitario.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
        this.quantidadeComprada = quantidadeComprada;
    }

    // ── Fábrica para repositório ──────────────────────────────────────────────

    /**
     * Retorna uma cópia desta cotação com o ID substituído.
     *
     * <p>Permite que o repositório atribua o ID definitivo após a persistência
     * sem expor um setter de ID. Padrão consistente com {@link Produto},
     * {@link Pedido} e {@link LogAcao}.
     *
     * @param novoId ID definitivo; deve ser {@code > 0}
     * @return cópia com o novo ID e todos os demais campos preservados
     */
    public CotacaoMensal comId(long novoId) {
        validar(novoId > 0, "Novo ID deve ser maior que zero.");
        return new CotacaoMensal(novoId, produto, mesReferencia, precoUnitario, quantidadeComprada);
    }

    // ── Cálculo ───────────────────────────────────────────────────────────────

    /**
     * Calcula o gasto total do mês com este produto.
     *
     * <pre>  100 unidades × R$ 4,99 = R$ 499,00  </pre>
     *
     * @return gasto total com precisão monetária; nunca {@code null}
     */
    public BigDecimal calcularGastoTotal() {
        return precoUnitario
                .multiply(BigDecimal.valueOf(quantidadeComprada))
                .setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
    }

    // ── Consulta semântica ────────────────────────────────────────────────────

    /**
     * Verifica se esta cotação pertence ao mesmo produto e mês que outra.
     *
     * <p>Útil para detectar duplicatas antes de persistir, sem depender do ID.
     * O {@code CotacaoService} usa esse critério para decidir se substitui
     * uma cotação existente.
     *
     * @param outro cotação a comparar; retorna {@code false} se {@code null}
     * @return {@code true} se ambas referenciam o mesmo produto no mesmo mês
     */
    public boolean mesmoProdutoEMes(CotacaoMensal outro) {
        if (outro == null) return false;
        return Objects.equals(produto, outro.produto)
                && Objects.equals(mesReferencia, outro.mesReferencia);
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public long       getId()                 { return id;                }
    public Produto    getProduto()            { return produto;           }
    public YearMonth  getMesReferencia()      { return mesReferencia;     }
    public BigDecimal getPrecoUnitario()      { return precoUnitario;     }
    public int        getQuantidadeComprada() { return quantidadeComprada; }

    // ── Infraestrutura de objeto ──────────────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>Baseado na chave natural ({@code produto}, {@code mesReferencia}).
     * Não pode existir mais de uma cotação para o mesmo par — portanto a
     * identidade semântica é definida por ele, não pelo ID sintético.
     *
     * <p>Isso permite detectar duplicatas em coleções mesmo antes da
     * persistência (quando {@code id == 0}).
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CotacaoMensal other)) return false;
        return Objects.equals(produto, other.produto)
                && Objects.equals(mesReferencia, other.mesReferencia);
    }

    @Override
    public int hashCode() {
        return Objects.hash(produto, mesReferencia);
    }

    @Override
    public String toString() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern(FORMATO_MES);
        return String.format(
                "CotacaoMensal{produto='%s', mes=%s, preco=R$%s, qtd=%d, total=R$%s}",
                produto.getNome(),
                mesReferencia.format(fmt),
                precoUnitario.toPlainString(),
                quantidadeComprada,
                calcularGastoTotal().toPlainString()
        );
    }

    // ── Auxiliar privado ──────────────────────────────────────────────────────

    /** Lança {@link IllegalArgumentException} se a condição for falsa. */
    private static void validar(boolean condicao, String mensagem) {
        if (!condicao) throw new IllegalArgumentException(mensagem);
    }
}
