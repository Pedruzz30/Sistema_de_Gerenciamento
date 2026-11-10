package controller;

import model.ClasseFuncionario;
import model.Permissao;
import model.Usuario;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Teclas rápidas do caixa: quem muda e o que vale para quem entrou por PIN. */
class PdvTeclasNovoTest {

    private static Usuario usuario(String nome, Permissao... permissoes) {
        ClasseFuncionario classe = new ClasseFuncionario(9, "C_" + nome, "Teste");
        for (Permissao p : permissoes) classe.adicionarPermissao(p);
        return new Usuario(0, nome, "Teste", "529.982.247-25", "x", classe, Usuario.Perfil.OPERADOR);
    }

    @Test
    void mudarTeclasPrecisaDeQuemCuidaDoEstoque() {
        Usuario ana = usuario("Ana", Permissao.VER_VENDAS);
        Usuario dono = usuario("Dono", Permissao.VER_VENDAS, Permissao.EDITAR_ESTOQUE);
        assertThrows(SecurityException.class, () -> PdvTeclasController.responsavel(ana, null));
        assertThrows(SecurityException.class, () -> PdvTeclasController.responsavel(ana, usuario("Bia", Permissao.VER_VENDAS)));
        assertSame(dono, PdvTeclasController.responsavel(ana, dono));
        assertSame(dono, PdvTeclasController.responsavel(dono, null));
    }

    @Test
    void caixaPorPinLeEMudaTeclasETiraItemDaComanda() {
        assertTrue(SessaoFilter.permitidoPorPin("/api/pdv/teclas", "GET"));
        assertTrue(SessaoFilter.permitidoPorPin("/api/pdv/teclas/opcoes", "GET"));
        assertTrue(SessaoFilter.permitidoPorPin("/api/pdv/teclas", "PUT"));
        assertFalse(SessaoFilter.permitidoPorPin("/api/pdv/teclas", "DELETE"));
        assertTrue(SessaoFilter.permitidoPorPin("/api/comandas/7/retirar", "POST"));
    }
}
