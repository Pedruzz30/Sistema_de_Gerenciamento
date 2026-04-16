package model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Registro imutável de uma transação financeira dentro de um {@link Caixa} PDV.
 *
 * <h2>Exemplos reais</h2>
 * <pre>
 *   14:32 — VENDA      — R$ 25,00  — "3 laranjas"    — João
 *   15:10 — SANGRIA    — R$ 200,00 — "Envio ao cofre" — João
 *   16:45 — SUPRIMENTO — R$ 50,00  — "Reposição troco"— Maria
 * </pre>
 *
 * <h2>Papel no domínio</h2>
 * {@code MovimentacaoCaixa} é um <strong>registro imutável de auditoria</strong>
 * financeira: cada transação fica gravada com timestamp e operador responsável,
 * nunca alterada após o registro. O {@link Caixa} agrega essas transações e
 * mantém o saldo consistente.
 *
 * <h2>Convenções monetárias</h2>
 * O valor é armazenado como {@link BigDecimal} com escala 2 e
 * {@link RoundingMode#HALF_UP}. Isso elimina o erro de ponto flutuante que
 * ocorria quando {@code double} era usado — e simplifica o {@link Caixa},
 * que não precisa mais converter na fronteira.
 *
 * <h2>Timestamp e ID</h2>
 * O {@code dataHora} é capturado no construtor público e preservado por
 * {@link #comId}, exatamente como em {@link Pedido} e {@link LogAcao} —
 * prevenindo o desvio de timestamp ao persistir.
 */
@Entity
@Table(name = "movimentacoes_caixa")
public class MovimentacaoCaixa {

    // ── Constantes ────────────────────────────────────────────────────────────

    private static final int    ESCALA_MONETARIA = 2;
    private static final String FORMATO_DATA     = "dd/MM/yyyy HH:mm:ss";

    // ── Campos — todos imutáveis ──────────────────────────────────────────────

    @Id
    private long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoMovimentacaoCaixa tipo;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal valor;

    @Column(length = 255)
    private String descricao;

    @Column(name = "data_hora", nullable = false)
    private LocalDateTime dataHora;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "operador_ru", nullable = false)
    private Usuario operador;

    // ── Construtor público ────────────────────────────────────────────────────

    protected MovimentacaoCaixa() {
        // Construtor exigido pelo JPA.
    }

    /**
     * Cria uma nova transação de caixa, capturando o timestamp atual.
     *
     * <p>{@code id == 0} indica que a transação ainda não foi persistida.
     * Use {@link #comId} para que o repositório ou service retorne a cópia
     * com o ID definitivo sem alterar o {@code dataHora} original.
     *
     * @param id        identificador; {@code >= 0} (0 = não persistido)
     * @param tipo      tipo da movimentação; não pode ser {@code null}
     * @param valor     valor em reais; deve ser {@code > 0}; não pode ser {@code null}
     * @param descricao descrição opcional; {@code null} é aceito (tratado como vazio)
     * @param operador  usuário que realizou a operação; não pode ser {@code null}
     * @throws IllegalArgumentException se qualquer argumento obrigatório for inválido
     */
    public MovimentacaoCaixa(long id,
                             TipoMovimentacaoCaixa tipo,
                             BigDecimal valor,
                             String descricao,
                             Usuario operador) {
        validar(id >= 0,                                   "ID não pode ser negativo.");
        validar(tipo != null,                              "Tipo de movimentação é obrigatório.");
        validar(valor != null,                             "Valor é obrigatório.");
        validar(valor.compareTo(BigDecimal.ZERO) > 0,      "Valor deve ser maior que zero.");
        validar(operador != null,                          "Operador é obrigatório.");

        this.id        = id;
        this.tipo      = tipo;
        this.valor     = valor.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
        this.descricao = descricao != null ? descricao.trim() : "";
        this.operador  = operador;
        this.dataHora  = LocalDateTime.now();
    }

    /**
     * Construtor privado usado por {@link #comId} para preservar o timestamp.
     */
    private MovimentacaoCaixa(long id, TipoMovimentacaoCaixa tipo, BigDecimal valor,
                              String descricao, Usuario operador, LocalDateTime dataHora) {
        this.id        = id;
        this.tipo      = tipo;
        this.valor     = valor;
        this.descricao = descricao;
        this.operador  = operador;
        this.dataHora  = dataHora; // timestamp original preservado
    }

    // ── Fábrica para repositório / service ────────────────────────────────────

    /**
     * Retorna uma cópia desta movimentação com o ID substituído,
     * preservando o {@code dataHora} original.
     *
     * <p>Sem este método, atribuir o ID definitivo exigiria chamar
     * {@code new MovimentacaoCaixa(novoId, ...)}, que invocaria
     * {@code LocalDateTime.now()} de novo — deslocando o timestamp do registro
     * financeiro e corrompendo a trilha de auditoria do caixa.
     *
     * @param novoId ID definitivo; deve ser {@code > 0}
     * @return cópia com o novo ID e timestamp original
     */
    public MovimentacaoCaixa comId(long novoId) {
        validar(novoId > 0, "Novo ID deve ser maior que zero.");
        return new MovimentacaoCaixa(novoId, tipo, valor, descricao, operador, dataHora);
    }

    // ── Semântica de movimentação ─────────────────────────────────────────────

    /**
     * @return {@code true} se esta transação aumenta o saldo do caixa
     * @see TipoMovimentacaoCaixa#isEntrada()
     */
    public boolean ehEntrada() {
        return tipo.isEntrada();
    }

    /** @return {@code true} se esta transação reduz o saldo do caixa */
    public boolean ehSaida() {
        return !tipo.isEntrada();
    }

    /** @return {@code true} se a movimentação tem uma descrição não vazia */
    public boolean possuiDescricao() {
        return !descricao.isEmpty();
    }

    // ── Exibição ──────────────────────────────────────────────────────────────

    /**
     * Gera uma linha de extrato legível para logs e exibição em terminal.
     *
     * <pre>
     *   [31/03/2026 14:32:05] VENDA      | R$ 25,00 | 3 laranjas | João Silva
     *   [31/03/2026 15:10:44] SANGRIA    | R$ 200,00 | Envio ao cofre | João Silva
     * </pre>
     */
    public String gerarExtrato() {
        return String.format("[%s] %-12s | R$ %s | %s | %s",
                dataHora.format(DateTimeFormatter.ofPattern(FORMATO_DATA)),
                tipo,
                valor.toPlainString(),
                possuiDescricao() ? descricao : "sem descrição",
                operador.getNomeCompleto()
        );
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public long                  getId()        { return id;        }
    public TipoMovimentacaoCaixa getTipo()      { return tipo;      }
    public BigDecimal            getValor()     { return valor;     }
    public String                getDescricao() { return descricao; }
    public LocalDateTime         getDataHora()  { return dataHora;  }
    public Usuario               getOperador()  { return operador;  }

    // ── Infraestrutura de objeto ──────────────────────────────────────────────

    /** Delega para {@link #gerarExtrato()} — saída rica para logs e debug. */
    @Override
    public String toString() {
        return gerarExtrato();
    }

    /**
     * Duas movimentações são iguais se tiverem o mesmo {@code id}.
     * Movimentações com {@code id == 0} (não persistidas) nunca são iguais entre si.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MovimentacaoCaixa other)) return false;
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    // ── Auxiliar privado ──────────────────────────────────────────────────────

    /** Lança {@link IllegalArgumentException} se a condição for falsa. */
    private static void validar(boolean condicao, String mensagem) {
        if (!condicao) throw new IllegalArgumentException(mensagem);
    }
}
