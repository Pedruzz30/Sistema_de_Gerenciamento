package model;

import java.math.BigDecimal;

/**
 * Tipos de transação financeira que podem ocorrer em um {@link Caixa} PDV.
 *
 * <h2>Classificação entrada/saída</h2>
 * <pre>
 *   ENTRADA ──► VENDA       cliente paga por um produto
 *               SUPRIMENTO  reposição de troco autorizada
 *               ENTRADA     outros recebimentos (ex: devolução de fornecedor)
 *
 *   SAÍDA   ──► SANGRIA     retirada de dinheiro (segurança ou depósito)
 *               DESPESA     pequenas saídas autorizadas (ex: material de limpeza)
 * </pre>
 *
 * <h2>Extensibilidade e segurança de compilação</h2>
 * {@link #isEntrada()} usa switch exaustivo — ao adicionar um novo valor ao
 * enum, o compilador exige que ele seja classificado explicitamente como
 * entrada ou saída. Sem isso, a lógica de saldo do {@link Caixa} poderia
 * aplicar o sinal errado silenciosamente.
 *
 * <h2>Dados de exibição</h2>
 * Cada valor carrega {@link #getLabel()} (português) e {@link #getIcone()}
 * para uso consistente em logs, terminal e frontend — sem switch espalhado
 * pelo código de apresentação.
 */
public enum TipoMovimentacaoCaixa {

    // ── Entradas ──────────────────────────────────────────────────────────────

    /** Cliente pagou por um produto ou serviço. */
    VENDA      ("Venda",      "💰"),

    /** Reposição de troco autorizada pelo gerente. */
    SUPRIMENTO ("Suprimento", "➕"),

    /** Outros recebimentos (ex: devolução de mercadoria por fornecedor). */
    ENTRADA    ("Entrada",    "📥"),

    // ── Saídas ────────────────────────────────────────────────────────────────

    /** Retirada de dinheiro para segurança ou depósito bancário. */
    SANGRIA    ("Sangria",    "🏦"),

    /** Pequenas saídas autorizadas (ex: compra de material de escritório). */
    DESPESA    ("Despesa",    "📤");

    // ── Campos ────────────────────────────────────────────────────────────────

    /** Rótulo em português para exibição em UI, logs e relatórios. */
    private final String label;

    /** Ícone visual para terminal e frontend. */
    private final String icone;

    // ── Construtor ────────────────────────────────────────────────────────────

    TipoMovimentacaoCaixa(String label, String icone) {
        this.label = label;
        this.icone = icone;
    }

    // ── Classificação financeira ──────────────────────────────────────────────

    /**
     * Indica se esta transação <strong>aumenta</strong> o saldo do caixa.
     *
     * <p>O switch é exaustivo: adicionar um novo valor ao enum sem classificá-lo
     * aqui gera erro de compilação — prevenindo que o {@link Caixa} aplique o
     * sinal errado ao saldo.
     *
     * @return {@code true} para VENDA, SUPRIMENTO e ENTRADA;
     *         {@code false} para SANGRIA e DESPESA
     */
    public boolean isEntrada() {
        return switch (this) {
            case VENDA, SUPRIMENTO, ENTRADA -> true;
            case SANGRIA, DESPESA           -> false;
        };
    }

    /**
     * Indica se esta transação <strong>reduz</strong> o saldo do caixa.
     * Complemento exato de {@link #isEntrada()}.
     *
     * @return {@code true} para SANGRIA e DESPESA; {@code false} caso contrário
     */
    public boolean isSaida() {
        return !isEntrada();
    }

    /**
     * Indica se esta transação representa uma venda ao cliente.
     *
     * <p>Substitui comparações diretas como
     * {@code tipo == TipoMovimentacaoCaixa.VENDA} no {@link Caixa} e
     * nos controllers, tornando o código mais expressivo e desacoplado
     * do nome do enum.
     *
     * @return {@code true} somente para {@code VENDA}
     */
    public boolean isVenda() {
        return this == VENDA;
    }

    /**
     * Aplica o impacto financeiro desta transação a um valor base.
     *
     * <p>Entradas somam; saídas subtraem. Útil para calcular variações
     * de saldo sem expor a lógica de sinal ao chamador:
     * <pre>
     *   BigDecimal novoSaldo = tipo.aplicarA(saldoAtual, valorMovimentacao);
     * </pre>
     *
     * @param saldoAtual        saldo antes da transação; não pode ser {@code null}
     * @param valorMovimentacao valor da transação (sempre positivo);
     *                          não pode ser {@code null}
     * @return novo saldo após a transação
     */
    public BigDecimal aplicarA(BigDecimal saldoAtual, BigDecimal valorMovimentacao) {
        return isEntrada()
                ? saldoAtual.add(valorMovimentacao)
                : saldoAtual.subtract(valorMovimentacao);
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    /**
     * Rótulo em português para exibição em UI, logs e relatórios.
     * Prefira {@code getLabel()} a {@code name()} em código de apresentação —
     * {@code name()} é o identificador interno do enum (em inglês / maiúsculas)
     * e pode quebrar se o enum for renomeado.
     */
    public String getLabel() {
        return label;
    }

    /**
     * Ícone visual para uso em logs de terminal e frontend.
     * Consistente com o padrão de {@link NivelEstoque#icone()} e
     * {@link ResultadoComparacao.Tendencia#getIcone()}.
     */
    public String getIcone() {
        return icone;
    }

    /**
     * Representação textual rica, incluindo ícone e classificação.
     *
     * <pre>
     *   VENDA      → "💰 Venda (entrada)"
     *   SANGRIA    → "🏦 Sangria (saída)"
     * </pre>
     *
     * <p>Prefira {@link #getLabel()} quando quiser apenas o nome em português,
     * e {@code name()} quando precisar do identificador para serialização JSON.
     */
    @Override
    public String toString() {
        return icone + " " + label + " (" + (isEntrada() ? "entrada" : "saída") + ")";
    }
}