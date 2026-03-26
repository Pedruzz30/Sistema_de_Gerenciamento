package model;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

/**
 * Resultado da comparação de preço de um produto entre dois meses.
 *
 * Ao invés de retornar vários valores separados do CotacaoService,
 * agrupamos tudo aqui — mais limpo e fácil de usar no frontend.
 */
public class ResultadoComparacao {

    public enum Tendencia {
        SUBIU,     // preço aumentou
        CAIU,      // preço diminuiu
        ESTAVEL    // preço igual
    }

    private final Produto produto;
    private final YearMonth mesAnterior;
    private final YearMonth mesAtual;
    private final double precoAnterior;
    private final double precoAtual;
    private final double variacaoAbsoluta;   // ex: +0,16 ou -0,30
    private final double variacaoPercentual; // ex: +3,2% ou -6,0%
    private final Tendencia tendencia;

    public ResultadoComparacao(Produto produto,
                               YearMonth mesAnterior,
                               YearMonth mesAtual,
                               double precoAnterior,
                               double precoAtual) {
        this.produto = produto;
        this.mesAnterior = mesAnterior;
        this.mesAtual = mesAtual;
        this.precoAnterior = precoAnterior;
        this.precoAtual = precoAtual;
        this.variacaoAbsoluta = precoAtual - precoAnterior;
        this.variacaoPercentual = precoAnterior == 0
                ? 0
                : ((precoAtual - precoAnterior) / precoAnterior) * 100;

        if (variacaoAbsoluta > 0.001) {
            this.tendencia = Tendencia.SUBIU;
        } else if (variacaoAbsoluta < -0.001) {
            this.tendencia = Tendencia.CAIU;
        } else {
            this.tendencia = Tendencia.ESTAVEL;
        }
    }

    public Produto getProduto()             { return produto; }
    public YearMonth getMesAnterior()       { return mesAnterior; }
    public YearMonth getMesAtual()          { return mesAtual; }
    public double getPrecoAnterior()        { return precoAnterior; }
    public double getPrecoAtual()           { return precoAtual; }
    public double getVariacaoAbsoluta()     { return variacaoAbsoluta; }
    public double getVariacaoPercentual()   { return variacaoPercentual; }
    public Tendencia getTendencia()         { return tendencia; }

    /**
     * Gera uma mensagem legível para exibir no sistema.
     * Exemplo: "Morango subiu R$0,16 (+3,2%) de jan/25 para fev/25"
     */
    public String gerarMensagem() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM/yy");
        String de = mesAnterior.format(fmt);
        String para = mesAtual.format(fmt);

        return switch (tendencia) {
            case SUBIU -> String.format(
                    "⚠ %s subiu R$%.2f (+%.1f%%) de %s para %s.",
                    produto.getNome(), variacaoAbsoluta, variacaoPercentual, de, para
            );
            case CAIU -> String.format(
                    "✓ %s caiu R$%.2f (%.1f%%) de %s para %s.",
                    produto.getNome(), Math.abs(variacaoAbsoluta),
                    variacaoPercentual, de, para
            );
            case ESTAVEL -> String.format(
                    "= %s manteve o preço de %s para %s (R$%.2f).",
                    produto.getNome(), de, para, precoAtual
            );
        };
    }

    @Override
    public String toString() {
        return gerarMensagem();
    }
}
