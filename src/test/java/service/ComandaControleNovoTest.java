package service;

import model.ClasseFuncionario;
import model.Permissao;
import model.Usuario;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Controles da comanda: quem tira item já lançado e o aviso ao encerrar o caixa. */
class ComandaControleNovoTest {

    private static Usuario usuario(String nome, Permissao... permissoes) {
        ClasseFuncionario classe = new ClasseFuncionario(9, "C_" + nome, "Teste");
        for (Permissao p : permissoes) classe.adicionarPermissao(p);
        return new Usuario(0, nome, "Teste", "529.982.247-25", "x", classe, Usuario.Perfil.OPERADOR);
    }

    @Test
    void operadoraSoTiraItemDaComandaComOGerente() {
        Usuario ana = usuario("Ana", Permissao.VER_VENDAS);
        Usuario gerente = usuario("Gerente", Permissao.VER_VENDAS, Permissao.VER_FINANCAS);
        Usuario estoquista = usuario("Estoque", Permissao.EDITAR_ESTOQUE);

        SecurityException semGerente = assertThrows(SecurityException.class, () -> ComandaService.responsavelRetirada(ana, null));
        assertTrue(semGerente.getMessage().contains("gerente"));
        assertThrows(SecurityException.class, () -> ComandaService.responsavelRetirada(ana, estoquista),
                "Quem só cuida do estoque não autoriza");
        assertSame(gerente, ComandaService.responsavelRetirada(ana, gerente));
        assertSame(gerente, ComandaService.responsavelRetirada(gerente, null), "O gerente no caixa não precisa de outro");
    }

    @Test
    void avisoDeComandasAbertasDizQuaisEQuantoSoma() {
        LocalDateTime agora = LocalDateTime.of(2026, 11, 10, 22, 0);
        String uma = ComandaService.avisoComandasAbertas(List.of(
                new ComandaService.Resumo(7, 1, agora, new BigDecimal("2"), new BigDecimal("13.50"))));
        assertEquals("A comanda 7 está aberta, somando R$ 13,50. Receba antes de encerrar, ou confirme que elas ficam abertas.", uma);

        String duas = ComandaService.avisoComandasAbertas(List.of(
                new ComandaService.Resumo(7, 1, agora, new BigDecimal("2"), new BigDecimal("13.50")),
                new ComandaService.Resumo(9, 2, agora, new BigDecimal("1"), new BigDecimal("4.50"))));
        assertTrue(duas.startsWith("As comandas 7, 9 estão abertas, somando R$ 18,00."), duas);
    }
}
