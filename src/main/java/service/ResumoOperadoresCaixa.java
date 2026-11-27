package service;

import model.FormaPagamento;
import model.MovimentacaoCaixa;
import model.TipoMovimentacaoCaixa;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Quanto cada pessoa movimentou numa sessão de caixa, a partir das movimentações:
 * cada uma já grava quem operava, o tipo, a forma e a venda.
 *
 * <p>Venda conta para quem finalizou; cancelamento e sangria, para quem fez.
 */
public final class ResumoOperadoresCaixa {

    private static final int ESCALA = 2;

    public record Linha(
            String operador,
            int vendas,
            BigDecimal vendido,
            BigDecimal dinheiro,
            BigDecimal pixECartoes,
            int cancelamentos,
            BigDecimal cancelado,
            BigDecimal descontos,
            BigDecimal sangrias,
            BigDecimal suprimentos
    ) {}

    private ResumoOperadoresCaixa() {}

    /**
     * @param descontoPorVenda desconto dado em cada venda, pelo id dela
     */
    public static List<Linha> calcular(
            List<MovimentacaoCaixa> movimentacoes,
            Map<Long, BigDecimal> descontoPorVenda) {

        Objects.requireNonNull(movimentacoes, "movimentacoes");
        Objects.requireNonNull(descontoPorVenda, "descontoPorVenda");

        Map<String, Acumulado> porOperador = new LinkedHashMap<>();
        Set<Long> descontoContado = new HashSet<>();

        for (MovimentacaoCaixa movimentacao : movimentacoes) {
            var operador = movimentacao.getOperador();

            String nome = operador == null
                    ? "—"
                    : operador.getNomeCompleto();

            Acumulado acumulado = porOperador.computeIfAbsent(
                    nome,
                    chave -> new Acumulado(chave));

            BigDecimal valor = Objects.requireNonNull(
                    movimentacao.getValor(),
                    "Valor da movimentacao de caixa.");

            TipoMovimentacaoCaixa tipo = movimentacao.getTipo();
            Long vendaId = movimentacao.getVendaId();

            if (tipo == TipoMovimentacaoCaixa.VENDA) {

                acumulado.vendas.add(
                        vendaId != null
                                ? vendaId
                                : -movimentacao.getId() - 1);

                acumulado.vendido = acumulado.vendido.add(valor);

                FormaPagamento formaPagamento =
                        movimentacao.getFormaPagamento();

                if (formaPagamento == null
                        || formaPagamento == FormaPagamento.DINHEIRO) {

                    acumulado.dinheiro =
                            acumulado.dinheiro.add(valor);

                } else {
                    acumulado.pixECartoes =
                            acumulado.pixECartoes.add(valor);
                }

                if (vendaId != null && descontoContado.add(vendaId)) {
                    BigDecimal desconto = descontoPorVenda.getOrDefault(
                            vendaId,
                            BigDecimal.ZERO);

                    acumulado.descontos = acumulado.descontos.add(
                            Objects.requireNonNull(
                                    desconto,
                                    "Desconto da venda."));
                }

            } else if (tipo == TipoMovimentacaoCaixa.ESTORNO_VENDA) {

                acumulado.cancelamentos.add(
                        vendaId != null
                                ? vendaId
                                : -movimentacao.getId() - 1);

                acumulado.cancelado =
                        acumulado.cancelado.add(valor);

            } else if (tipo == TipoMovimentacaoCaixa.SANGRIA) {

                acumulado.sangrias =
                        acumulado.sangrias.add(valor);

            } else if (tipo == TipoMovimentacaoCaixa.SUPRIMENTO) {

                acumulado.suprimentos =
                        acumulado.suprimentos.add(valor);
            }
        }

        List<Linha> linhas = new ArrayList<>();

        for (Acumulado acumulado : porOperador.values()) {
            linhas.add(new Linha(
                    acumulado.nome,
                    acumulado.vendas.size(),
                    dinheiro(acumulado.vendido),
                    dinheiro(acumulado.dinheiro),
                    dinheiro(acumulado.pixECartoes),
                    acumulado.cancelamentos.size(),
                    dinheiro(acumulado.cancelado),
                    dinheiro(acumulado.descontos),
                    dinheiro(acumulado.sangrias),
                    dinheiro(acumulado.suprimentos)
            ));
        }

        linhas.sort(Comparator
                .comparing((Linha linha) -> linha.vendido())
                .reversed()
                .thenComparing(linha -> linha.operador()));

        return linhas;
    }

    private static BigDecimal dinheiro(BigDecimal valor) {
        return valor.setScale(
                ESCALA,
                RoundingMode.HALF_UP);
    }

    private static final class Acumulado {

        final String nome;

        final Set<Long> vendas = new HashSet<>();
        final Set<Long> cancelamentos = new HashSet<>();

        BigDecimal vendido = BigDecimal.ZERO;
        BigDecimal dinheiro = BigDecimal.ZERO;
        BigDecimal pixECartoes = BigDecimal.ZERO;
        BigDecimal cancelado = BigDecimal.ZERO;
        BigDecimal descontos = BigDecimal.ZERO;
        BigDecimal sangrias = BigDecimal.ZERO;
        BigDecimal suprimentos = BigDecimal.ZERO;

        Acumulado(String nome) {
            this.nome = nome;
        }
    }
}