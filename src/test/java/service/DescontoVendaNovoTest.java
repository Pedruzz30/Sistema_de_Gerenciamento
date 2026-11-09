package service;

import model.ClasseFuncionario;
import model.Permissao;
import model.Usuario;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DescontoVendaNovoTest {

    private static BigDecimal bd(String valor) {
        return new BigDecimal(valor);
    }

    private static DescontoVendaService.Linha linha(long id, String preco, String quantidade) {
        return new DescontoVendaService.Linha(id, bd(preco), bd(quantidade));
    }

    /** Total pago depois do desconto, arredondado como o caixa confere. */
    private static BigDecimal totalPago(List<DescontoVendaService.Linha> linhas, Map<Long, BigDecimal> porUnidade) {
        return linhas.stream()
                .map(l -> l.precoUnitario().subtract(porUnidade.get(l.id())).multiply(l.quantidade()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static Usuario usuario(String nome, Permissao... permissoes) {
        ClasseFuncionario classe = new ClasseFuncionario(9, "C_" + nome, "Teste");
        for (Permissao p : permissoes) classe.adicionarPermissao(p);
        return new Usuario(0, nome, "Teste", "529.982.247-25", "x", classe, Usuario.Perfil.OPERADOR);
    }

    @Test
    void faltaPorCinquentaFechaExatoMesmoComRepartiçãoQuebrada() {
        // 3 latas a 4,50 + 1 fardo a 38,90 = 52,40; o cliente pede "faz por 50".
        List<DescontoVendaService.Linha> linhas = List.of(linha(1, "4.50", "3"), linha(2, "38.90", "1"));
        Map<Long, BigDecimal> porUnidade = DescontoVendaService.distribuir(linhas, bd("2.40"));
        assertEquals(bd("50.00"), totalPago(linhas, porUnidade));
    }

    @Test
    void repartiçãoExataEmMuitosCasos() {
        String[][] carrinhos = {
                {"4.50:3", "38.90:1"}, {"3.99:7"}, {"1.00:3"}, {"9.90:2", "3.33:3", "0.99:11"}, {"12.00:1", "0.50:9"}
        };
        for (String[] carrinho : carrinhos) {
            List<DescontoVendaService.Linha> linhas = new java.util.ArrayList<>();
            long id = 1;
            for (String parte : carrinho) {
                String[] p = parte.split(":");
                linhas.add(linha(id++, p[0], p[1]));
            }
            BigDecimal subtotal = DescontoVendaService.subtotal(linhas);
            for (String desconto : new String[]{"0.01", "0.10", "1.00", "2.37", "5.55"}) {
                if (bd(desconto).compareTo(subtotal) >= 0) continue;
                Map<Long, BigDecimal> porUnidade = DescontoVendaService.distribuir(linhas, bd(desconto));
                assertEquals(subtotal.subtract(bd(desconto)), totalPago(linhas, porUnidade),
                        String.join(",", carrinho) + " com desconto " + desconto);
                porUnidade.forEach((item, valor) -> assertTrue(valor.signum() >= 0, "Desconto negativo no item " + item));
            }
        }
    }

    @Test
    void porcentagemEValorERecusas() {
        assertEquals(bd("5.24"), DescontoVendaService.calcularDesconto(bd("52.40"), null, bd("10")));
        assertEquals(bd("2.40"), DescontoVendaService.calcularDesconto(bd("52.40"), bd("2.4"), null));
        assertEquals(bd("0.00"), DescontoVendaService.calcularDesconto(bd("52.40"), null, null));
        assertThrows(ErroNegocioException.class, () -> DescontoVendaService.calcularDesconto(bd("52.40"), bd("52.40"), null));
        assertThrows(ErroNegocioException.class, () -> DescontoVendaService.calcularDesconto(bd("52.40"), bd("-1"), null));
        assertThrows(ErroNegocioException.class, () -> DescontoVendaService.calcularDesconto(bd("52.40"), null, bd("100")));
    }

    @Test
    void operadoraPrecisaDoGerenteMasNaoParaRetirar() {
        Usuario ana = usuario("Ana", Permissao.VER_VENDAS);
        Usuario gerente = usuario("Gil", Permissao.VER_VENDAS, Permissao.VER_FINANCAS);
        Usuario estoquista = usuario("Edu", Permissao.EDITAR_ESTOQUE);

        assertSame(ana, DescontoVendaService.responsavel(ana, null, BigDecimal.ZERO), "Retirar o desconto é livre");
        assertThrows(SecurityException.class, () -> DescontoVendaService.responsavel(ana, null, bd("1.00")));
        assertThrows(SecurityException.class, () -> DescontoVendaService.responsavel(ana, estoquista, bd("1.00")));
        assertSame(gerente, DescontoVendaService.responsavel(ana, gerente, bd("1.00")));
        assertSame(gerente, DescontoVendaService.responsavel(gerente, null, bd("1.00")), "O gerente dá sozinho");
    }
}
