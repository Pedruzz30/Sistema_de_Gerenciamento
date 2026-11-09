package service;

import model.ClasseFuncionario;
import model.FormaPagamento;
import model.MovimentacaoCaixa;
import model.Permissao;
import model.TipoMovimentacaoCaixa;
import model.Usuario;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResumoOperadoresNovoTest {

    private static Usuario pessoa(String nome, String sobrenome, String cpf) {
        ClasseFuncionario classe = new ClasseFuncionario(4, "CAIXA", "Teste");
        classe.adicionarPermissao(Permissao.VER_VENDAS);
        return new Usuario(0, nome, sobrenome, cpf, "x", classe, Usuario.Perfil.OPERADOR);
    }

    private static MovimentacaoCaixa mov(TipoMovimentacaoCaixa tipo, String valor, Usuario quem, FormaPagamento forma, Long venda) {
        return new MovimentacaoCaixa(0, tipo, new BigDecimal(valor), "", quem, forma, venda);
    }

    private static void assertBig(String esperado, BigDecimal atual) {
        assertEquals(0, new BigDecimal(esperado).compareTo(atual), () -> "esperado " + esperado + " mas foi " + atual);
    }

    @Test
    void separaQuemVendeuCancelouDeuDescontoETirouDaGaveta() {
        Usuario dona = pessoa("Admin", "Superior", "529.982.247-25");
        Usuario ana = pessoa("Ana", "Caixa", "935.411.347-80");
        List<MovimentacaoCaixa> movs = List.of(
                mov(TipoMovimentacaoCaixa.VENDA, "52.40", dona, FormaPagamento.DINHEIRO, 1L),
                mov(TipoMovimentacaoCaixa.VENDA, "3.99", dona, FormaPagamento.PIX, 2L),
                // Venda em duas formas conta como uma.
                mov(TipoMovimentacaoCaixa.VENDA, "2.00", ana, FormaPagamento.PIX, 3L),
                mov(TipoMovimentacaoCaixa.VENDA, "2.50", ana, FormaPagamento.DINHEIRO, 3L),
                mov(TipoMovimentacaoCaixa.ESTORNO_VENDA, "2.00", ana, FormaPagamento.PIX, 3L),
                mov(TipoMovimentacaoCaixa.ESTORNO_VENDA, "2.50", ana, FormaPagamento.DINHEIRO, 3L),
                mov(TipoMovimentacaoCaixa.VENDA, "8.00", ana, FormaPagamento.DINHEIRO, 4L),
                mov(TipoMovimentacaoCaixa.SANGRIA, "50.00", ana, null, null),
                mov(TipoMovimentacaoCaixa.SUPRIMENTO, "20.00", dona, null, null));

        List<ResumoOperadoresCaixa.Linha> linhas = ResumoOperadoresCaixa.calcular(movs, Map.of(4L, new BigDecimal("1.0000")));

        assertEquals(2, linhas.size());
        ResumoOperadoresCaixa.Linha primeira = linhas.get(0);
        assertEquals("Admin Superior", primeira.operador(), "Quem mais vendeu vem primeiro");
        assertEquals(2, primeira.vendas());
        assertBig("56.39", primeira.vendido());
        assertBig("52.40", primeira.dinheiro());
        assertBig("3.99", primeira.pixECartoes());
        assertBig("20.00", primeira.suprimentos());

        ResumoOperadoresCaixa.Linha ana2 = linhas.get(1);
        assertEquals("Ana Caixa", ana2.operador());
        assertEquals(2, ana2.vendas());
        assertBig("12.50", ana2.vendido());
        assertBig("10.50", ana2.dinheiro());
        assertBig("2.00", ana2.pixECartoes());
        assertEquals(1, ana2.cancelamentos(), "Estorno em duas formas é um cancelamento");
        assertBig("4.50", ana2.cancelado());
        assertBig("1.00", ana2.descontos());
        assertBig("50.00", ana2.sangrias());
    }

    @Test
    void semMovimentacaoNaoHaLinha() {
        assertEquals(List.of(), ResumoOperadoresCaixa.calcular(List.of(), Map.of()));
    }
}
