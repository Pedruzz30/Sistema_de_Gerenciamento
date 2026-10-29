package controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import model.Caixa;
import model.MovimentacaoCaixa;
import model.TipoMovimentacaoCaixa;
import org.junit.jupiter.api.Test;

class CaixaControllerDailyMetricsTest {
    private static final LocalDate DIA = LocalDate.of(2026, 10, 28);

    @Test
    void calculaSomenteVendasDoDiaIncluindoCaixaEncerrado() {
        Caixa aberto = caixa(Caixa.Status.ABERTO,
                movimento(TipoMovimentacaoCaixa.VENDA, "40.00", DIA.atTime(8, 30)),
                movimento(TipoMovimentacaoCaixa.VENDA, "60.00", DIA.atTime(13, 0)),
                movimento(TipoMovimentacaoCaixa.SUPRIMENTO, "900.00", DIA.atTime(15, 0)),
                movimento(TipoMovimentacaoCaixa.VENDA, "500.00", DIA.minusDays(1).atTime(23, 0)));
        Caixa encerrado = caixa(Caixa.Status.ENCERRADO,
                movimento(TipoMovimentacaoCaixa.VENDA, "20.00", DIA.atTime(16, 0)));

        CaixaController.MetricasResponse resultado = CaixaController.calcularMetricasDia(List.of(aberto, encerrado), DIA);
        assertEquals(new BigDecimal("120.00"), resultado.totalVendas());
        assertEquals(1, resultado.caixasAbertos());
        assertEquals(3, resultado.totalMovimentacoesVenda());
        assertEquals(new BigDecimal("40.00"), resultado.ticketMedio());
    }

    @Test
    void semVendasHojeRetornaZero() {
        Caixa encerrado = caixa(Caixa.Status.ENCERRADO,
                movimento(TipoMovimentacaoCaixa.VENDA, "35.00", DIA.minusDays(1).atStartOfDay()));
        CaixaController.MetricasResponse resultado = CaixaController.calcularMetricasDia(List.of(encerrado), DIA);
        assertEquals(new BigDecimal("0.00"), resultado.totalVendas());
        assertEquals(0, resultado.caixasAbertos());
        assertEquals(0, resultado.totalMovimentacoesVenda());
        assertEquals(new BigDecimal("0.00"), resultado.ticketMedio());
    }

    private static Caixa caixa(Caixa.Status status, MovimentacaoCaixa... movimentacoes) {
        Caixa caixa = mock(Caixa.class);
        when(caixa.getStatus()).thenReturn(status);
        when(caixa.getMovimentacoes()).thenReturn(List.of(movimentacoes));
        return caixa;
    }

    private static MovimentacaoCaixa movimento(TipoMovimentacaoCaixa tipo, String valor, LocalDateTime dataHora) {
        MovimentacaoCaixa movimento = mock(MovimentacaoCaixa.class);
        when(movimento.getTipo()).thenReturn(tipo);
        when(movimento.getValor()).thenReturn(new BigDecimal(valor));
        when(movimento.getDataHora()).thenReturn(dataHora);
        return movimento;
    }
}
