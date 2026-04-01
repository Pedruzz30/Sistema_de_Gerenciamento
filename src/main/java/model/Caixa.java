package model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Representa um ponto de venda físico (PDV).
 *
 * <h2>Ciclo de vida</h2>
 * <pre>
 *   FECHADO ──► ABERTO ──► ENCERRADO
 * </pre>
 * Um caixa encerrado não pode receber mais movimentações.
 * No dia seguinte, um novo caixa é aberto para o mesmo número.
 *
 * <h2>Convenções monetárias</h2>
 * Todos os saldos e totais são {@link BigDecimal} com escala 2 e
 * {@link RoundingMode#HALF_UP}. A conversão de {@code double} ocorre na
 * fronteira — em {@link #abrir} e em {@link #registrarMovimentacao} —
 * usando {@link BigDecimal#valueOf(double)}, que é seguro para esse fim.
 * Os cálculos internos nunca usam {@code double}.
 *
 * <h2>Estado de ID</h2>
 * {@code id == 0} indica que o caixa ainda não foi persistido.
 * IDs negativos são rejeitados.
 */
public class Caixa {

    // ── Constantes ────────────────────────────────────────────────────────────

    private static final int    ESCALA_MONETARIA = 2;
    private static final String FORMATO_DATA     = "dd/MM/yyyy HH:mm";

    // ── Status e state machine ────────────────────────────────────────────────

    /**
     * Estados possíveis de um caixa PDV.
     *
     * <p>As transições válidas ficam no próprio enum — mesma decisão
     * arquitetural de {@link NotaFiscal.Status}: a regra mora junto de quem
     * ela descreve, não espalhada em {@code if}s pelo código.
     */
    public enum Status {

        FECHADO {
            @Override
            public Set<Status> transicoesPermitidas() {
                return EnumSet.of(ABERTO);
            }
        },
        ABERTO {
            @Override
            public Set<Status> transicoesPermitidas() {
                return EnumSet.of(ENCERRADO);
            }
        },
        ENCERRADO {
            @Override
            public Set<Status> transicoesPermitidas() {
                return EnumSet.noneOf(Status.class); // terminal
            }
        };

        public abstract Set<Status> transicoesPermitidas();

        public boolean podeTransicionarPara(Status destino) {
            return transicoesPermitidas().contains(destino);
        }
    }

    // ── Campos imutáveis ──────────────────────────────────────────────────────

    private final long                    id;
    private final int                     numeroCaixa;
    private final List<MovimentacaoCaixa> movimentacoes;

    // ── Campos mutáveis — evoluem com o ciclo de vida ─────────────────────────

    private BigDecimal    saldoInicial;
    private BigDecimal    saldoAtual;
    private Status        status;
    private Usuario       operadorAtual;
    private LocalDateTime dataAbertura;
    private LocalDateTime dataEncerramento;

    // ── Construtor ────────────────────────────────────────────────────────────

    /**
     * Cria um caixa PDV no estado {@code FECHADO}.
     *
     * @param id          identificador; {@code >= 0} (0 = ainda não persistido)
     * @param numeroCaixa número físico do PDV (ex: 1, 2, 3); deve ser {@code > 0}
     * @throws IllegalArgumentException se qualquer argumento for inválido
     */
    public Caixa(long id, int numeroCaixa) {
        validar(id >= 0,          "ID do caixa não pode ser negativo.");
        validar(numeroCaixa > 0,  "Número do caixa deve ser maior que zero.");

        this.id            = id;
        this.numeroCaixa   = numeroCaixa;
        this.status        = Status.FECHADO;
        this.saldoInicial  = BigDecimal.ZERO;
        this.saldoAtual    = BigDecimal.ZERO;
        this.movimentacoes = new ArrayList<>();
    }

    // ── Abertura ──────────────────────────────────────────────────────────────

    /**
     * Abre o caixa para operação, registrando o operador e o fundo inicial.
     *
     * @param operador      usuário que está abrindo o caixa; não pode ser {@code null}
     * @param saldoInicial  fundo de caixa inicial em reais; deve ser {@code >= 0}
     * @throws IllegalStateException    se o caixa não estiver {@code FECHADO}
     * @throws IllegalArgumentException se {@code operador} for nulo ou saldo negativo
     */
    public void abrir(Usuario operador, double saldoInicial) {
        validar(operador != null,   "Operador é obrigatório para abrir o caixa.");
        validar(saldoInicial >= 0,  "Saldo inicial não pode ser negativo.");
        transicionarPara(Status.ABERTO);

        this.operadorAtual = operador;
        this.saldoInicial  = bd(saldoInicial);
        this.saldoAtual    = this.saldoInicial;
        this.dataAbertura  = LocalDateTime.now();
    }

    // ── Movimentações ─────────────────────────────────────────────────────────

    /**
     * Registra uma transação no caixa, atualizando o saldo atual.
     *
     * <p>Entradas ({@link TipoMovimentacaoCaixa#isEntrada()}) somam ao saldo;
     * saídas subtraem. Saídas que excederiam o saldo atual são rejeitadas
     * imediatamente — sem deixar o caixa negativo.
     *
     * @param movimentacao transação a registrar; não pode ser {@code null}
     * @throws IllegalStateException    se o caixa não estiver {@code ABERTO}
     * @throws IllegalArgumentException se a movimentação for nula ou o saldo for insuficiente
     */
    public void registrarMovimentacao(MovimentacaoCaixa movimentacao) {
        validar(status == Status.ABERTO,
                "Caixa " + numeroCaixa + " não está aberto. Status: " + status);
        validar(movimentacao != null, "Movimentação não pode ser nula.");

        // Converte na fronteira: double → BigDecimal (seguro via valueOf)
        BigDecimal valor = bd(movimentacao.getValor());

        if (movimentacao.getTipo().isEntrada()) {
            this.saldoAtual = this.saldoAtual.add(valor);
        } else {
            // BigDecimal.compareTo — não usa == para comparação de valor
            validar(valor.compareTo(this.saldoAtual) <= 0,
                    String.format("Saldo insuficiente para %s. " +
                                    "Saldo atual: R$ %s | Valor solicitado: R$ %s",
                            movimentacao.getTipo(),
                            this.saldoAtual.toPlainString(),
                            valor.toPlainString()));
            this.saldoAtual = this.saldoAtual.subtract(valor);
        }

        movimentacoes.add(movimentacao);
    }

    // ── Encerramento ──────────────────────────────────────────────────────────

    /**
     * Encerra o caixa, impedindo novas movimentações.
     *
     * @throws IllegalStateException se o caixa não estiver {@code ABERTO}
     */
    public void encerrar() {
        transicionarPara(Status.ENCERRADO);
        this.dataEncerramento = LocalDateTime.now();
    }

    // ── Cálculos ──────────────────────────────────────────────────────────────

    /**
     * Soma os valores de todas as movimentações de entrada
     * ({@link TipoMovimentacaoCaixa#isEntrada()} == {@code true}).
     *
     * <p>Conversão de {@code double} para {@link BigDecimal} ocorre aqui,
     * na fronteira com {@link MovimentacaoCaixa} — que ainda usa {@code double}.
     */
    public BigDecimal calcularTotalEntradas() {
        return movimentacoes.stream()
                .filter(m -> m.getTipo().isEntrada())
                .map(m -> bd(m.getValor()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
    }

    /**
     * Soma os valores de todas as movimentações de saída
     * (sangrias, despesas — onde {@link TipoMovimentacaoCaixa#isEntrada()} == {@code false}).
     */
    public BigDecimal calcularTotalSaidas() {
        return movimentacoes.stream()
                .filter(m -> !m.getTipo().isEntrada())
                .map(m -> bd(m.getValor()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
    }

    /**
     * Soma apenas as movimentações do tipo {@link TipoMovimentacaoCaixa#VENDA}.
     * Subconjunto de {@link #calcularTotalEntradas()}.
     */
    public BigDecimal calcularTotalVendas() {
        return movimentacoes.stream()
                .filter(m -> m.getTipo() == TipoMovimentacaoCaixa.VENDA)
                .map(m -> bd(m.getValor()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
    }

    // ── Consultas de estado ───────────────────────────────────────────────────

    public boolean estaFechado()    { return status == Status.FECHADO;    }
    public boolean estaAberto()     { return status == Status.ABERTO;     }
    public boolean estaEncerrado()  { return status == Status.ENCERRADO;  }

    // ── Getters ───────────────────────────────────────────────────────────────

    public long                    getId()                { return id;                }
    public int                     getNumeroCaixa()       { return numeroCaixa;       }
    public BigDecimal              getSaldoInicial()      { return saldoInicial;      }
    public BigDecimal              getSaldoAtual()        { return saldoAtual;        }
    public Status                  getStatus()            { return status;            }
    public Usuario                 getOperadorAtual()     { return operadorAtual;     }
    public LocalDateTime           getDataAbertura()      { return dataAbertura;      }
    public LocalDateTime           getDataEncerramento()  { return dataEncerramento;  }

    /** Retorna uma visão imutável das movimentações registradas. */
    public List<MovimentacaoCaixa> getMovimentacoes() {
        return Collections.unmodifiableList(movimentacoes);
    }

    // ── Infraestrutura de objeto ──────────────────────────────────────────────

    @Override
    public String toString() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern(FORMATO_DATA);
        return String.format(
                "Caixa{id=%d, nº=%d, status=%s, saldo=R$%s, operador=%s, abertura=%s}",
                id,
                numeroCaixa,
                status,
                saldoAtual.toPlainString(),
                operadorAtual != null ? operadorAtual.getNomeCompleto() : "N/A",
                dataAbertura  != null ? dataAbertura.format(fmt) : "N/A"
        );
    }

    /**
     * Dois caixas são iguais se tiverem o mesmo {@code id}.
     * Caixas com {@code id == 0} (não persistidos) nunca são iguais entre si.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Caixa other)) return false;
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    // ── Auxiliares privados ───────────────────────────────────────────────────

    /**
     * Máquina de estados central.
     *
     * <p>Toda transição passa por aqui — mesma decisão arquitetural de
     * {@link NotaFiscal}. Nunca modifique {@code this.status} diretamente
     * fora deste método.
     */
    private void transicionarPara(Status destino) {
        if (!status.podeTransicionarPara(destino)) {
            throw new IllegalStateException(String.format(
                    "Transição inválida para Caixa %d: %s → %s. Permitidas: %s",
                    numeroCaixa, status, destino, status.transicoesPermitidas()
            ));
        }
        this.status = destino;
    }

    /**
     * Converte {@code double} para {@link BigDecimal} com escala 2 de forma segura.
     *
     * <p>Usado na fronteira com classes que ainda usam {@code double}
     * ({@link MovimentacaoCaixa}, parâmetros de {@link #abrir}).
     * {@code BigDecimal.valueOf} respeita a representação decimal do {@code double}
     * — diferente de {@code new BigDecimal(double)}, que usaria a representação
     * binária de ponto flutuante.
     */
    private static BigDecimal bd(double valor) {
        return BigDecimal.valueOf(valor).setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
    }

    /** Lança {@link IllegalArgumentException} se a condição for falsa. */
    private static void validar(boolean condicao, String mensagem) {
        if (!condicao) throw new IllegalArgumentException(mensagem);
    }
}