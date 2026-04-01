package model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

/**
 * Snapshot imutável da comparação de preço de um {@link Produto} entre dois meses.
 *
 * <h2>Papel no domínio</h2>
 * {@code ResultadoComparacao} é um objeto de valor calculado: agrega produto,
 * período, preços e métricas derivadas (variação absoluta, percentual, tendência)
 * em uma estrutura coesa — ao invés de o {@code CotacaoService} retornar
 * múltiplos valores soltos.
 *
 * <h2>Exemplo</h2>
 * <pre>
 *   Morango | jan/25 → fev/25 | R$4,99 → R$5,15 | +R$0,16 | +3,2% | SUBIU
 *   Laranja | jan/25 → fev/25 | R$2,50 → R$2,35 | -R$0,15 | -6,0% | CAIU
 *   Alface  | jan/25 → fev/25 | R$1,80 → R$1,80 |  R$0,00 |  0,0% | ESTÁVEL
 * </pre>
 *
 * <h2>Convenções monetárias</h2>
 * Todos os valores monetários e percentuais usam {@link BigDecimal} com
 * {@link RoundingMode#HALF_UP}. A variação percentual usa escala 4 no cálculo
 * intermediário (divisão) e é arredondada para 2 casas ao final.
 *
 * <h2>Detecção de tendência</h2>
 * Com {@link BigDecimal}, a comparação é exata — sem epsilon. Se
 * {@code variacaoAbsoluta.compareTo(ZERO) == 0}, a tendência é {@code ESTAVEL}.
 * Não há margem de tolerância arbitrária como {@code 0.001}.
 *
 * <h2>Localização</h2>
 * Os nomes de mês em {@link #gerarMensagem()} são formatados em
 * português brasileiro ({@code "jan/25"}, {@code "fev/25"} etc.) via
 * {@link Locale#forLanguageTag(String) Locale.forLanguageTag("pt-BR")}.
 */
public class ResultadoComparacao {

    // ── Constantes ────────────────────────────────────────────────────────────

    private static final int    ESCALA_MONETARIA   = 2;
    private static final int    ESCALA_PERCENTUAL  = 2;
    private static final int    ESCALA_DIVISAO     = 4; // precisão intermediária
    private static final DateTimeFormatter FORMATO_MES =
            DateTimeFormatter.ofPattern("MMM/yy", Locale.forLanguageTag("pt-BR"));

    // ── Enum de tendência ─────────────────────────────────────────────────────

    /**
     * Direção da variação de preço entre os dois meses comparados.
     *
     * <p>Cada valor carrega seu ícone visual e verbo em português, eliminando
     * a necessidade de {@code switch} em {@link #gerarMensagem()} para esses
     * detalhes de exibição.
     */
    public enum Tendencia {

        SUBIU  ("⚠", "subiu"),
        CAIU   ("✓", "caiu"),
        ESTAVEL("=", "manteve o preço");

        private final String icone;
        private final String verbo;

        Tendencia(String icone, String verbo) {
            this.icone = icone;
            this.verbo = verbo;
        }

        /** Ícone visual para logs, terminal e frontend. */
        public String getIcone() { return icone; }

        /** Verbo em português descrevendo a direção da variação. */
        public String getVerbo() { return verbo; }
    }

    // ── Campos — todos imutáveis e calculados no construtor ───────────────────

    private final Produto    produto;
    private final YearMonth  mesAnterior;
    private final YearMonth  mesAtual;
    private final BigDecimal precoAnterior;
    private final BigDecimal precoAtual;
    private final BigDecimal variacaoAbsoluta;   // precoAtual − precoAnterior
    private final BigDecimal variacaoPercentual; // (variação / precoAnterior) × 100
    private final Tendencia  tendencia;

    // ── Construtor ────────────────────────────────────────────────────────────

    /**
     * Cria o resultado calculando automaticamente variação e tendência.
     *
     * <p>Todos os campos derivados ({@code variacaoAbsoluta},
     * {@code variacaoPercentual}, {@code tendencia}) são calculados aqui
     * de forma determinística — sem lógica espalhada pelo chamador.
     *
     * @param produto       produto comparado; não pode ser {@code null}
     * @param mesAnterior   mês de referência mais antigo; não pode ser {@code null}
     * @param mesAtual      mês de referência mais recente; não pode ser {@code null}
     * @param precoAnterior preço unitário no mês anterior; não pode ser {@code null}
     *                      nem negativo
     * @param precoAtual    preço unitário no mês atual; não pode ser {@code null}
     *                      nem negativo
     * @throws IllegalArgumentException se qualquer argumento obrigatório for inválido
     */
    public ResultadoComparacao(Produto produto,
                               YearMonth mesAnterior,
                               YearMonth mesAtual,
                               BigDecimal precoAnterior,
                               BigDecimal precoAtual) {
        validar(produto != null,       "Produto é obrigatório.");
        validar(mesAnterior != null,   "Mês anterior é obrigatório.");
        validar(mesAtual != null,      "Mês atual é obrigatório.");
        validar(precoAnterior != null, "Preço anterior é obrigatório.");
        validar(precoAtual != null,    "Preço atual é obrigatório.");
        validar(precoAnterior.compareTo(BigDecimal.ZERO) >= 0,
                "Preço anterior não pode ser negativo.");
        validar(precoAtual.compareTo(BigDecimal.ZERO) >= 0,
                "Preço atual não pode ser negativo.");

        this.produto       = produto;
        this.mesAnterior   = mesAnterior;
        this.mesAtual      = mesAtual;
        this.precoAnterior = precoAnterior.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
        this.precoAtual    = precoAtual.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);

        this.variacaoAbsoluta = this.precoAtual
                .subtract(this.precoAnterior)
                .setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);

        // Se precoAnterior == 0, a variação percentual é indefinida.
        // Retornamos 0% por convenção — sem divisão por zero.
        this.variacaoPercentual = this.precoAnterior.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO.setScale(ESCALA_PERCENTUAL, RoundingMode.HALF_UP)
                : variacaoAbsoluta
                .divide(this.precoAnterior, ESCALA_DIVISAO, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(ESCALA_PERCENTUAL, RoundingMode.HALF_UP);

        // Comparação exata com BigDecimal — sem epsilon arbitrário
        int cmp = variacaoAbsoluta.compareTo(BigDecimal.ZERO);
        this.tendencia = cmp > 0 ? Tendencia.SUBIU
                : cmp < 0 ? Tendencia.CAIU
                :           Tendencia.ESTAVEL;
    }

    // ── Exibição ──────────────────────────────────────────────────────────────

    /**
     * Gera uma mensagem legível em português brasileiro.
     *
     * <pre>
     *   ⚠ Morango subiu R$ 0,16 (+3,2%) de jan/25 para fev/25.
     *   ✓ Laranja caiu R$ 0,15 (-6,0%) de jan/25 para fev/25.
     *   = Alface manteve o preço de jan/25 para fev/25 (R$ 1,80).
     * </pre>
     *
     * <p>Os valores percentuais de {@code SUBIU} e {@code CAIU} sempre exibem
     * o sinal explícito ({@code +} ou {@code -}) para clareza visual.
     */
    public String gerarMensagem() {
        String de   = mesAnterior.format(FORMATO_MES);
        String para = mesAtual.format(FORMATO_MES);
        String nome = produto.getNome();

        return switch (tendencia) {

            case SUBIU -> String.format(
                    "%s %s %s R$ %s (+%s%%) de %s para %s.",
                    tendencia.getIcone(), nome, tendencia.getVerbo(),
                    variacaoAbsoluta.toPlainString(),
                    variacaoPercentual.toPlainString(),
                    de, para
            );

            case CAIU -> String.format(
                    "%s %s %s R$ %s (-%s%%) de %s para %s.",
                    tendencia.getIcone(), nome, tendencia.getVerbo(),
                    variacaoAbsoluta.abs().toPlainString(),
                    variacaoPercentual.abs().toPlainString(),
                    de, para
            );

            case ESTAVEL -> String.format(
                    "%s %s %s de %s para %s (R$ %s).",
                    tendencia.getIcone(), nome, tendencia.getVerbo(),
                    de, para,
                    precoAtual.toPlainString()
            );
        };
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Produto    getProduto()            { return produto;            }
    public YearMonth  getMesAnterior()        { return mesAnterior;        }
    public YearMonth  getMesAtual()           { return mesAtual;           }
    public BigDecimal getPrecoAnterior()      { return precoAnterior;      }
    public BigDecimal getPrecoAtual()         { return precoAtual;         }
    public BigDecimal getVariacaoAbsoluta()   { return variacaoAbsoluta;   }
    public BigDecimal getVariacaoPercentual() { return variacaoPercentual; }
    public Tendencia  getTendencia()          { return tendencia;          }

    // ── Infraestrutura de objeto ──────────────────────────────────────────────

    /**
     * Dois resultados são iguais se comparam o mesmo produto no mesmo período.
     * Baseado na chave natural ({@code produto}, {@code mesAnterior}, {@code mesAtual}).
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ResultadoComparacao other)) return false;
        return Objects.equals(produto, other.produto)
                && Objects.equals(mesAnterior, other.mesAnterior)
                && Objects.equals(mesAtual, other.mesAtual);
    }

    @Override
    public int hashCode() {
        return Objects.hash(produto, mesAnterior, mesAtual);
    }

    /** Delega para {@link #gerarMensagem()} — saída rica para logs e debug. */
    @Override
    public String toString() {
        return gerarMensagem();
    }

    // ── Auxiliar privado ──────────────────────────────────────────────────────

    /** Lança {@link IllegalArgumentException} se a condição for falsa. */
    private static void validar(boolean condicao, String mensagem) {
        if (!condicao) throw new IllegalArgumentException(mensagem);
    }
}
