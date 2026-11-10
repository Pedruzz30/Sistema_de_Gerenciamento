package service;

import model.ClasseFuncionario;
import model.FormaPagamento;
import model.MovimentacaoCaixa;
import model.Permissao;
import model.TipoMovimentacaoCaixa;
import model.Usuario;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RelatorioVendasNovoTest {

    private static Usuario pessoa(String nome, String sobrenome, String cpf) {
        ClasseFuncionario classe = new ClasseFuncionario(4, "CAIXA", "Teste");
        classe.adicionarPermissao(Permissao.VER_VENDAS);
        return new Usuario(0, nome, sobrenome, cpf, "x", classe, Usuario.Perfil.OPERADOR);
    }

    private static MovimentacaoCaixa mov(TipoMovimentacaoCaixa tipo, String valor, Usuario quem, FormaPagamento forma, Long venda) {
        return new MovimentacaoCaixa(0, tipo, new BigDecimal(valor), "", quem, forma, venda);
    }

    private static RelatorioVendasService.ItemVendido item(String nome, String qtd, String valor, String desconto, String custo) {
        return new RelatorioVendasService.ItemVendido(nome, new BigDecimal(qtd), new BigDecimal(valor), new BigDecimal(desconto), new BigDecimal(custo));
    }

    private static void assertBig(String esperado, BigDecimal atual) {
        assertEquals(0, new BigDecimal(esperado).compareTo(atual), () -> "esperado " + esperado + " mas foi " + atual);
    }

    /** O mesmo dia do caminho completo da loja no navegador. */
    private static List<MovimentacaoCaixa> diaDaLoja() {
        Usuario dona = pessoa("Admin", "Superior", "529.982.247-25");
        Usuario ana = pessoa("Ana", "Caixa", "935.411.347-80");
        return List.of(
                mov(TipoMovimentacaoCaixa.VENDA, "52.40", dona, FormaPagamento.DINHEIRO, 1L),
                mov(TipoMovimentacaoCaixa.VENDA, "3.99", dona, FormaPagamento.PIX, 2L),
                mov(TipoMovimentacaoCaixa.VENDA, "2.00", ana, FormaPagamento.PIX, 3L),
                mov(TipoMovimentacaoCaixa.VENDA, "2.50", ana, FormaPagamento.DINHEIRO, 3L),
                mov(TipoMovimentacaoCaixa.ESTORNO_VENDA, "2.00", ana, FormaPagamento.PIX, 3L),
                mov(TipoMovimentacaoCaixa.ESTORNO_VENDA, "2.50", ana, FormaPagamento.DINHEIRO, 3L),
                mov(TipoMovimentacaoCaixa.VENDA, "8.00", ana, FormaPagamento.DINHEIRO, 4L),
                mov(TipoMovimentacaoCaixa.SANGRIA, "50.00", ana, null, null),
                mov(TipoMovimentacaoCaixa.VENDA, "9.00", dona, FormaPagamento.PIX, 5L),
                mov(TipoMovimentacaoCaixa.VENDA, "4.50", dona, FormaPagamento.DINHEIRO, 5L),
                mov(TipoMovimentacaoCaixa.VENDA, "13.50", dona, FormaPagamento.PIX, 6L));
    }

    private static List<RelatorioVendasService.ItemVendido> itensDaLoja() {
        return List.of(
                item("Skol lata 350ml Gelada", "3", "13.50", "0", "8.25"),
                item("Skol lata 350ml Fardo 12", "1", "38.90", "0", "33.00"),
                item("Skol lata 350ml Natural", "1", "3.99", "0", "2.75"),
                item("Skol lata 350ml Gelada", "2", "8.00", "1.00", "5.50"),
                item("Skol lata 350ml Gelada", "3", "13.50", "0", "8.25"),
                item("Skol lata 350ml Gelada", "3", "13.50", "0", "8.25"));
    }

    @Test
    void diaSomaDinheiroPelasMovimentacoesEProdutosPelasVendas() {
        RelatorioVendasService.Dia dia = RelatorioVendasService.montarDia(LocalDate.of(2026, 11, 10), diaDaLoja(), itensDaLoja(), 2);

        RelatorioVendasService.Resumo r = dia.resumo();
        assertEquals(6, r.vendas());
        assertEquals(1, r.cancelamentos());
        assertBig("95.89", r.bruto());
        assertBig("4.50", r.cancelado());
        assertBig("91.39", r.liquido());
        assertBig("18.28", r.ticketMedio());
        assertBig("1.00", r.descontos());
        assertBig("66.00", r.custoEstimado());
        assertBig("25.39", r.margemEstimada());
        assertBig("50.00", r.sangrias());
        assertEquals(2, r.comandasPagas());

        assertEquals("DINHEIRO", dia.porForma().get(0).forma(), "Maior forma primeiro");
        assertBig("64.90", dia.porForma().get(0).valor());
        assertBig("26.49", dia.porForma().get(1).valor());

        assertEquals("Skol lata 350ml Gelada", dia.produtos().get(0).nome());
        assertBig("11", dia.produtos().get(0).quantidade());
        assertBig("48.50", dia.produtos().get(0).valor());
        assertEquals(3, dia.produtos().size());

        assertEquals("Admin Superior", dia.porOperador().get(0).operador());
        assertEquals(4, dia.porOperador().get(0).vendas());
        assertEquals(1, dia.porHora().size(), "Tudo na mesma hora do teste");
        assertEquals(6, dia.porHora().get(0).vendas());
    }

    @Test
    void mesSeparaPorDia() {
        RelatorioVendasService.Mes mes = RelatorioVendasService.montarMes(YearMonth.of(2026, 11), diaDaLoja(), itensDaLoja(), 2);
        assertEquals(1, mes.dias().size());
        assertBig("91.39", mes.dias().get(0).liquido());
        assertEquals(6, mes.dias().get(0).vendas());
        assertBig("91.39", mes.resumo().liquido());
    }

    private static RelatorioVendasService.VendidoNoPeriodo vendido(String nome, String valor, String custo, String consumo, Integer estoque) {
        return new RelatorioVendasService.VendidoNoPeriodo(nome, new BigDecimal(valor), new BigDecimal(custo),
                consumo == null ? null : new BigDecimal(consumo), estoque);
    }

    @Test
    void curvaAbcSeparaOsQueFazemOFaturamento() {
        LocalDate inicio = LocalDate.of(2026, 10, 12);
        LocalDate fim = LocalDate.of(2026, 11, 10);
        List<RelatorioVendasService.VendidoNoPeriodo> vendidos = List.of(
                vendido("Gelo 5kg", "50.00", "30.00", "5", 20),
                vendido("Skol lata 350ml", "600.00", "420.00", "150", 84),
                vendido("Caldo de feijão", "20.00", "8.00", null, null),
                vendido("Heineken long neck", "250.00", "160.00", "40", 6),
                vendido("Copo de gelo", "80.00", "20.00", "40", 100));
        RelatorioVendasService.Abc abc = RelatorioVendasService.classificarAbc(inicio, fim, "valor", vendidos);

        assertEquals(30, abc.dias());
        assertBig("1000.00", abc.total());
        assertEquals(List.of("Skol lata 350ml", "Heineken long neck", "Copo de gelo", "Gelo 5kg", "Caldo de feijão"),
                abc.linhas().stream().map(RelatorioVendasService.LinhaAbc::nome).toList());
        // A Heineken cruza os 80% (60% → 85%) e fica em A; o Gelo cruza os 95% (93% → 98%) e fica em B; o Caldo, em C.
        assertEquals(List.of("A", "A", "B", "B", "C"), abc.linhas().stream().map(RelatorioVendasService.LinhaAbc::classe).toList());
        assertBig("60.0", abc.linhas().get(0).percentual());
        assertBig("85.0", abc.linhas().get(1).acumulado());
        assertEquals(2, abc.produtosPorClasse().get("A"));
        assertBig("85.0", abc.percentualPorClasse().get("A"));
        // 150 latas em 30 dias = 5 por dia; 84 em estoque dão 16 dias. A Heineken dura 4.
        assertBig("16", abc.linhas().get(0).coberturaDias());
        assertBig("4", abc.linhas().get(1).coberturaDias());
        assertEquals(null, abc.linhas().get(4).coberturaDias(), "Petisco com receita não tem cobertura");
    }

    @Test
    void curvaAbcPorMargemMudaAOrdem() {
        RelatorioVendasService.Abc abc = RelatorioVendasService.classificarAbc(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 10),
                "margem", List.of(
                        vendido("Skol lata 350ml", "600.00", "580.00", null, null),
                        vendido("Copo de gelo", "80.00", "20.00", null, null),
                        vendido("Prejuízo", "10.00", "15.00", null, null)));
        assertEquals("Copo de gelo", abc.linhas().get(0).nome(), "Copo dá mais margem que a Skol");
        assertEquals("C", abc.linhas().get(2).classe(), "Margem negativa fica em C");
        assertBig("-5.00", abc.linhas().get(2).margem());
    }

    @Test
    void diaSemMovimentoFicaZerado() {
        RelatorioVendasService.Dia dia = RelatorioVendasService.montarDia(LocalDate.of(2026, 11, 9), List.of(), List.of(), 0);
        assertEquals(0, dia.resumo().vendas());
        assertBig("0", dia.resumo().ticketMedio());
        assertEquals(List.of(), dia.produtos());
    }
}
