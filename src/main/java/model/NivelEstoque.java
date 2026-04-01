package model;

import java.util.Arrays;

/**
 * Classifica o nível de estoque de um {@link Produto} com base no percentual
 * atual em relação ao mínimo configurado.
 *
 * <h2>Faixas</h2>
 * <pre>
 *   SEM_ESTOQUE  [  0%       ]  — produto zerado, venda impossível
 *   CRITICO      [  1% -  25%]  — risco iminente de ruptura
 *   BAIXO        [ 26% -  50%]  — atenção necessária em breve
 *   MODERADO     [ 51% -  75%]  — confortável, sem urgência
 *   ADEQUADO     [  > 75%    ]  — estoque saudável
 * </pre>
 *
 * <h2>Ordenação natural</h2>
 * Os valores são declarados em ordem crescente de {@code percentualLimite}.
 * O {@link Enum#ordinal()} reflete a gravidade: {@code SEM_ESTOQUE.ordinal() == 0}
 * é o mais grave. Use {@code nivel1.compareTo(nivel2)} para comparar gravidades
 * sem hardcode — enums implementam {@link Comparable} pela ordem de declaração.
 *
 * <h2>Escalabilidade</h2>
 * Para adicionar um novo nível (ex: {@code EXCEDENTE} para estoque acima de
 * 200%), basta declarar o novo valor na posição correta e ajustar
 * {@code percentualLimite}. O {@link #fromPercentage} percorre {@code values()}
 * em ordem — não precisa ser modificado.
 *
 * <h2>Convenção de ícone</h2>
 * {@link #icone()} retorna um emoji para uso em logs e frontend.
 * O switch é exaustivo — o compilador exige tratamento ao adicionar novos valores.
 */
public enum NivelEstoque {

    // ── Valores — declarados em ordem crescente de percentualLimite ───────────
    //
    // IMPORTANTE: fromPercentage() percorre values() em ordem. Manter a
    // declaração em ordem crescente de percentualLimite é um invariante
    // documentado, não apenas uma convenção estética.

    /** Estoque zerado. Nenhuma unidade disponível para venda. */
    SEM_ESTOQUE(0,           "Sem estoque"),

    /** Estoque abaixo de 25% do mínimo. Risco iminente de ruptura. */
    CRITICO    (25,          "Estoque crítico"),

    /** Estoque entre 26% e 50% do mínimo. Atenção necessária. */
    BAIXO      (50,          "Estoque baixo"),

    /** Estoque entre 51% e 75% do mínimo. Confortável, sem urgência. */
    MODERADO   (75,          "Estoque moderado"),

    /**
     * Estoque acima de 75% do mínimo. Nível saudável.
     *
     * <p>{@code percentualLimite == Integer.MAX_VALUE} funciona como sentinela
     * "ilimitado": qualquer percentual maior que MODERADO (75%) cai aqui.
     * {@link #fromPercentage} interrompe a busca no primeiro valor cujo limite
     * seja maior que o percentual — e {@code Integer.MAX_VALUE} garante que
     * ADEQUADO capture todo o restante.
     */
    ADEQUADO   (Integer.MAX_VALUE, "Estoque adequado");

    // ── Campos ────────────────────────────────────────────────────────────────

    /** Limite superior da faixa (inclusive). Sentinela {@code Integer.MAX_VALUE} para ADEQUADO. */
    private final int    percentualLimite;

    /** Rótulo curto para exibição em UI, logs e relatórios. */
    private final String descricao;

    // ── Construtor ────────────────────────────────────────────────────────────

    NivelEstoque(int percentualLimite, String descricao) {
        this.percentualLimite = percentualLimite;
        this.descricao        = descricao;
    }

    // ── Fábrica ───────────────────────────────────────────────────────────────

    /**
     * Classifica um percentual de estoque no nível correspondente.
     *
     * <p>Percorre {@code values()} em ordem crescente de {@code percentualLimite}
     * e retorna o primeiro nível cujo limite seja maior ou igual ao percentual.
     * Para adicionar novos níveis, basta declará-los na posição correta — este
     * método não precisa ser alterado.
     *
     * @param percentual percentual atual de estoque; deve ser {@code >= 0}
     * @return nível de estoque correspondente; nunca {@code null}
     * @throws IllegalArgumentException se {@code percentual < 0}
     */
    public static NivelEstoque fromPercentage(double percentual) {
        if (percentual < 0) {
            throw new IllegalArgumentException(
                    "Percentual de estoque não pode ser negativo. Recebido: " + percentual
            );
        }
        // Arrays.stream preserva a ordem de declaração — invariante documentado na classe.
        return Arrays.stream(values())
                .filter(nivel -> percentual <= nivel.percentualLimite)
                .findFirst()
                .orElse(ADEQUADO); // nunca alcançado: ADEQUADO tem Integer.MAX_VALUE
    }

    // ── Classificações semânticas ─────────────────────────────────────────────

    /**
     * @return {@code true} se o estoque está tão baixo que reposição é necessária
     *         ({@code SEM_ESTOQUE}, {@code CRITICO} ou {@code BAIXO})
     */
    public boolean exigeReposicao() {
        return this.compareTo(MODERADO) < 0; // mais grave que MODERADO
    }

    /**
     * @return {@code true} se o estoque está abaixo do nível ideal,
     *         independentemente da urgência (qualquer nível exceto {@code ADEQUADO})
     */
    public boolean estaEmAlerta() {
        return this != ADEQUADO;
    }

    /**
     * @return {@code true} se a situação exige ação imediata
     *         ({@code SEM_ESTOQUE} ou {@code CRITICO})
     */
    public boolean exigeAcaoImediata() {
        return this.compareTo(BAIXO) < 0; // apenas SEM_ESTOQUE e CRITICO
    }

    // ── Exibição ──────────────────────────────────────────────────────────────

    /**
     * Retorna um ícone visual para uso em logs, terminal e frontend.
     *
     * <p>O switch é exaustivo: o compilador exige que novos valores do enum
     * sejam tratados aqui — prevenindo ícones genéricos silenciosos.
     */
    public String icone() {
        return switch (this) {
            case SEM_ESTOQUE -> "🔴";
            case CRITICO     -> "🟠";
            case BAIXO       -> "🟡";
            case MODERADO    -> "🟢";
            case ADEQUADO    -> "✅";
        };
    }

    /**
     * Gera uma mensagem de diagnóstico completa, incluindo o ícone e o
     * limite percentual da faixa.
     *
     * <p>O switch é exaustivo e dinâmico — sem percentuais hardcoded.
     * Alterar {@code percentualLimite} de um nível atualiza a mensagem
     * automaticamente.
     *
     * <pre>
     *   SEM_ESTOQUE → "🔴 Sem estoque (0%)."
     *   CRITICO     → "🟠 Estoque crítico (≤ 25%)."
     *   ADEQUADO    → "✅ Estoque adequado."
     * </pre>
     */
    public String gerarMensagem() {
        return switch (this) {
            case SEM_ESTOQUE             -> icone() + " " + descricao + " (0%).";
            case ADEQUADO                -> icone() + " " + descricao + ".";
            case CRITICO, BAIXO, MODERADO-> icone() + " " + descricao +
                    " (≤ " + percentualLimite + "%).";
        };
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    /**
     * Rótulo curto do nível (ex: "Estoque crítico").
     * Use {@link #gerarMensagem()} para uma mensagem completa com ícone e faixa.
     */
    public String getDescricao() {
        return descricao;
    }

    /**
     * Limite superior da faixa percentual deste nível.
     *
     * <p>Retorna {@link Integer#MAX_VALUE} para {@code ADEQUADO}
     * (sentinela "ilimitado").
     */
    public int getPercentualLimite() {
        return percentualLimite;
    }

    /**
     * Alias de {@link #getDescricao()} mantido para compatibilidade com
     * {@code EstoqueService} que ainda chama {@code nivel.getMessage()}.
     *
     * <p>Migração recomendada: substituir chamadas a {@code getMessage()} por
     * {@link #getDescricao()} ou {@link #gerarMensagem()}, conforme o contexto.
     *
     * @deprecated use {@link #getDescricao()} ou {@link #gerarMensagem()}
     */
    @Deprecated(since = "refatoração BigDecimal/elite", forRemoval = true)
    public String getMessage() {
        return descricao;
    }
}