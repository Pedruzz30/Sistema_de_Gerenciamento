package model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Snapshot imutável do estado financeiro de um caixa PDV ao ser encerrado.
 *
 * <h2>Papel no domínio</h2>
 * {@code FechamentoCaixa} é um <strong>relatório de encerramento</strong> —
 * um registro histórico gerado uma única vez a partir de um {@link Caixa}
 * encerrado. Pense nele como o "extrato impresso" que o operador assina
 * ao fechar o turno: imutável, auditável, nunca alterado após a geração.
 *
 * <h2>Convenções monetárias</h2>
 * Os valores são capturados de {@link Caixa} (que ainda usa {@code double})
 * e imediatamente convertidos para {@link BigDecimal} via
 * {@link BigDecimal#valueOf(double)} — o único método seguro para essa
 * conversão, pois respeita a representação decimal do {@code double} em vez
 * de usar a representação binária de ponto flutuante.
 *
 * <p>Assim o relatório exibe valores corretos mesmo que o {@link Caixa}
 * ainda não tenha sido migrado para {@link BigDecimal}.
 *
 * <h2>Verificação de consistência</h2>
 * Use {@link #calcularDivergencia()} para detectar discrepâncias entre o
 * saldo final informado pelo caixa e o saldo calculado pela fórmula:
 * <pre>
 *   saldoEsperado = saldoInicial + totalEntradas − totalSaidas
 * </pre>
 * Qualquer divergência diferente de zero indica erro de contagem ou bug.
 */
public class FechamentoCaixa {

    // ── Constantes ────────────────────────────────────────────────────────────

    private static final int    ESCALA_MONETARIA = 2;
    private static final String FORMATO_DATA     = "dd/MM/yyyy";

    /** Largura máxima do nome do operador no relatório. Nomes maiores são truncados. */
    private static final int LARGURA_NOME = 18;

    // ── Campos — todos imutáveis ──────────────────────────────────────────────

    private final int        numeroCaixa;
    private final LocalDate  data;
    private final String     nomeOperador;
    private final int        quantidadeMovimentacoes;

    private final BigDecimal saldoInicial;
    private final BigDecimal totalEntradas;
    private final BigDecimal totalSaidas;
    private final BigDecimal totalVendas;
    private final BigDecimal saldoFinal;

    // ── Construtor ────────────────────────────────────────────────────────────

    /**
     * Gera o fechamento a partir de um {@link Caixa} encerrado.
     *
     * <p>Captura o estado completo do caixa no momento do fechamento.
     * Alterações posteriores no objeto {@code Caixa} não afetam este snapshot.
     *
     * @param caixa caixa encerrado a partir do qual o relatório será gerado
     * @throws IllegalArgumentException se o caixa não estiver no status {@code ENCERRADO}
     *                                  ou se {@code dataEncerramento} for nula
     */
    public FechamentoCaixa(Caixa caixa) {
        validar(caixa != null,
                "Caixa não pode ser nulo.");
        validar(caixa.getStatus() == Caixa.Status.ENCERRADO,
                "Só é possível gerar fechamento de caixa ENCERRADO. Status atual: "
                        + caixa.getStatus());
        validar(caixa.getDataEncerramento() != null,
                "Data de encerramento ausente — o caixa pode não ter sido encerrado corretamente.");

        this.numeroCaixa             = caixa.getNumeroCaixa();
        this.data                    = caixa.getDataEncerramento().toLocalDate();
        this.nomeOperador            = caixa.getOperadorAtual().getNomeCompleto();
        this.quantidadeMovimentacoes = caixa.getMovimentacoes().size();

        // Conversão segura: BigDecimal.valueOf usa a representação decimal do double,
        // não a binária — evita que 49.99 vire 49.990000000000001 no relatório.
        this.saldoInicial   = caixa.getSaldoInicial();
        this.totalEntradas  = caixa.calcularTotalEntradas();
        this.totalSaidas    = caixa.calcularTotalSaidas();
        this.totalVendas    = caixa.calcularTotalVendas();
        this.saldoFinal     = caixa.getSaldoAtual();
    }

    // ── Métricas derivadas ────────────────────────────────────────────────────

    /**
     * Calcula o saldo esperado com base na fórmula contábil:
     * <pre>
     *   saldoEsperado = saldoInicial + totalEntradas − totalSaidas
     * </pre>
     *
     * @return saldo esperado com precisão monetária
     */
    public BigDecimal calcularSaldoEsperado() {
        return saldoInicial
                .add(totalEntradas)
                .subtract(totalSaidas)
                .setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
    }

    /**
     * Calcula a divergência entre o saldo final registrado e o saldo esperado.
     *
     * <pre>
     *   divergência = saldoFinal − saldoEsperado
     * </pre>
     *
     * <ul>
     *   <li>{@code ZERO} — caixa fechou corretamente, sem diferença.</li>
     *   <li>Positivo — sobrou dinheiro (mais do que deveria).</li>
     *   <li>Negativo — faltou dinheiro (menos do que deveria).</li>
     * </ul>
     *
     * @return diferença com sinal; {@code ZERO} indica fechamento sem divergência
     */
    public BigDecimal calcularDivergencia() {
        return saldoFinal
                .subtract(calcularSaldoEsperado())
                .setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
    }

    /** @return {@code true} se o saldo final bate exatamente com o esperado */
    public boolean estaBalanceado() {
        return calcularDivergencia().compareTo(BigDecimal.ZERO) == 0;
    }

    // ── Exibição ──────────────────────────────────────────────────────────────

    /**
     * Gera o relatório de fechamento formatado para exibição em terminal ou logs.
     *
     * <p>O nome do operador é truncado a {@value #LARGURA_NOME} caracteres para
     * preservar o alinhamento da caixa, independentemente do tamanho real do nome.
     *
     * <p>Inclui linha de divergência apenas quando o caixa não está balanceado,
     * chamando atenção para o problema sem poluir relatórios corretos.
     */
    public String gerarResumo() {
        DateTimeFormatter fmt   = DateTimeFormatter.ofPattern(FORMATO_DATA);
        String nomeExibido      = truncar(nomeOperador, LARGURA_NOME);
        BigDecimal divergencia  = calcularDivergencia();
        String statusBalanco    = estaBalanceado() ? "✓ Balanceado" : "⚠ DIVERGÊNCIA";

        StringBuilder sb = new StringBuilder();
        sb.append(String.format(
                "┌──────────────────────────────────────────┐%n"  +
                        "│  FECHAMENTO CAIXA %-2d  ─  %-16s│%n"          +
                        "├──────────────────────────────────────────┤%n"  +
                        "│  Operador       %-26s│%n"                      +
                        "│  Data           %-26s│%n"                      +
                        "│  Movimentações  %-26d│%n"                      +
                        "├──────────────────────────────────────────┤%n"  +
                        "│  Saldo inicial  %26s│%n"                       +
                        "│  Total entradas %26s│%n"                       +
                        "│  Total saídas   %26s│%n"                       +
                        "│  Total vendas   %26s│%n"                       +
                        "├──────────────────────────────────────────┤%n"  +
                        "│  Saldo final    %26s│%n"                       +
                        "│  Status         %-26s│%n",
                numeroCaixa,
                data.format(fmt),
                nomeExibido,
                data.format(fmt),
                quantidadeMovimentacoes,
                formatarMoeda(saldoInicial),
                formatarMoeda(totalEntradas),
                formatarMoeda(totalSaidas),
                formatarMoeda(totalVendas),
                formatarMoeda(saldoFinal),
                statusBalanco
        ));

        if (!estaBalanceado()) {
            sb.append(String.format(
                    "│  Divergência    %26s│%n",
                    formatarMoeda(divergencia)
            ));
        }

        sb.append("└──────────────────────────────────────────┘");
        return sb.toString();
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public int        getNumeroCaixa()             { return numeroCaixa;             }
    public LocalDate  getData()                    { return data;                    }
    public String     getNomeOperador()            { return nomeOperador;            }
    public int        getQuantidadeMovimentacoes() { return quantidadeMovimentacoes; }
    public BigDecimal getSaldoInicial()            { return saldoInicial;            }
    public BigDecimal getTotalEntradas()           { return totalEntradas;           }
    public BigDecimal getTotalSaidas()             { return totalSaidas;             }
    public BigDecimal getTotalVendas()             { return totalVendas;             }
    public BigDecimal getSaldoFinal()              { return saldoFinal;              }

    // ── Infraestrutura de objeto ──────────────────────────────────────────────

    /**
     * Dois fechamentos são iguais se referem ao mesmo caixa na mesma data.
     * Não deve existir mais de um fechamento por (numeroCaixa, data).
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FechamentoCaixa other)) return false;
        return numeroCaixa == other.numeroCaixa
                && Objects.equals(data, other.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(numeroCaixa, data);
    }

    /** Delega para {@link #gerarResumo()} — saída rica para logs e debug. */
    @Override
    public String toString() {
        return gerarResumo();
    }

    // ── Auxiliares privados ───────────────────────────────────────────────────

    /**
     * Converte {@code double} para {@link BigDecimal} com escala 2 de forma segura.
     *
     * <p>{@code BigDecimal.valueOf(double)} usa a representação String do double,
     * evitando o erro de ponto flutuante de {@code new BigDecimal(double)}.
     */
    private static BigDecimal bd(double valor) {
        return BigDecimal.valueOf(valor).setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
    }

    /** Formata um {@link BigDecimal} como {@code R$ 1.234,56} para o relatório. */
    private static String formatarMoeda(BigDecimal valor) {
        // Sinal explícito para divergências negativas.
        String sinal = valor.signum() < 0 ? "-" : " ";
        BigDecimal abs = valor.abs().setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
        return sinal + "R$ " + abs.toPlainString();
    }

    /**
     * Trunca uma string para no máximo {@code limite} caracteres,
     * adicionando {@code "…"} se o corte ocorrer.
     */
    private static String truncar(String texto, int limite) {
        if (texto == null)              return "";
        if (texto.length() <= limite)   return texto;
        return texto.substring(0, limite - 1) + "…";
    }

    /** Lança {@link IllegalArgumentException} se a condição for falsa. */
    private static void validar(boolean condicao, String mensagem) {
        if (!condicao) throw new IllegalArgumentException(mensagem);
    }
}
