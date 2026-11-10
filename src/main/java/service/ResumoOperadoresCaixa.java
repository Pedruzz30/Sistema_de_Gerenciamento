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
import java.util.Set;

/**
 * Quanto cada pessoa movimentou numa sessão de caixa, a partir das movimentações:
 * cada uma já grava quem operava, o tipo, a forma e a venda.
 *
 * <p>Venda conta para quem finalizou; cancelamento e sangria, para quem fez. Assim a dona
 * vê quem vendeu quanto, em que forma, quem cancelou e quem tirou dinheiro da gaveta.
 */
public final class ResumoOperadoresCaixa {

    private static final int ESCALA = 2;

    public record Linha(String operador,
                        int vendas,
                        BigDecimal vendido,
                        BigDecimal dinheiro,
                        BigDecimal pixECartoes,
                        int cancelamentos,
                        BigDecimal cancelado,
                        BigDecimal descontos,
                        BigDecimal sangrias,
                        BigDecimal suprimentos) {}

    private ResumoOperadoresCaixa() {}

    /** @param descontoPorVenda desconto dado em cada venda, pelo id dela */
    public static List<Linha> calcular(List<MovimentacaoCaixa> movimentacoes, Map<Long, BigDecimal> descontoPorVenda) {
        Map<String, Acumulado> porOperador = new LinkedHashMap<>();
        Set<Long> descontoContado = new HashSet<>();
        for (MovimentacaoCaixa m : movimentacoes) {
            String nome = m.getOperador() == null ? "—" : m.getOperador().getNomeCompleto();
            Acumulado a = porOperador.computeIfAbsent(nome, Acumulado::new);
            BigDecimal valor = m.getValor();
            TipoMovimentacaoCaixa tipo = m.getTipo();
            if (tipo == TipoMovimentacaoCaixa.VENDA) {
                a.vendas.add(m.getVendaId() != null ? m.getVendaId() : -m.getId() - 1);
                a.vendido = a.vendido.add(valor);
                if (m.getFormaPagamento() == null || m.getFormaPagamento() == FormaPagamento.DINHEIRO) {
                    a.dinheiro = a.dinheiro.add(valor);
                } else {
                    a.pixECartoes = a.pixECartoes.add(valor);
                }
                Long vendaId = m.getVendaId();
                if (vendaId != null && descontoContado.add(vendaId)) {
                    a.descontos = a.descontos.add(descontoPorVenda.getOrDefault(vendaId, BigDecimal.ZERO));
                }
            } else if (tipo == TipoMovimentacaoCaixa.ESTORNO_VENDA) {
                a.cancelamentos.add(m.getVendaId() != null ? m.getVendaId() : -m.getId() - 1);
                a.cancelado = a.cancelado.add(valor);
            } else if (tipo == TipoMovimentacaoCaixa.SANGRIA) {
                a.sangrias = a.sangrias.add(valor);
            } else if (tipo == TipoMovimentacaoCaixa.SUPRIMENTO) {
                a.suprimentos = a.suprimentos.add(valor);
            }
        }
        List<Linha> linhas = new ArrayList<>();
        for (Acumulado a : porOperador.values()) {
            linhas.add(new Linha(a.nome, a.vendas.size(), dinheiro(a.vendido), dinheiro(a.dinheiro),
                    dinheiro(a.pixECartoes), a.cancelamentos.size(), dinheiro(a.cancelado),
                    dinheiro(a.descontos), dinheiro(a.sangrias), dinheiro(a.suprimentos)));
        }
        linhas.sort(Comparator.comparing(Linha::vendido).reversed().thenComparing(Linha::operador));
        return linhas;
    }

    private static BigDecimal dinheiro(BigDecimal valor) {
        return valor.setScale(ESCALA, RoundingMode.HALF_UP);
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
