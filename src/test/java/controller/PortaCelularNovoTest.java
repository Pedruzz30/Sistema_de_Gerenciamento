package controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A porta aberta para a rede da loja só serve a tela de comanda. */
class PortaCelularNovoTest {

    @Test
    void celularAlcancaSoATelaDeComanda() {
        assertTrue(PortaCelularFilter.permitidoNoCelular("/celular.html", "GET"));
        assertTrue(PortaCelularFilter.permitidoNoCelular("/certificado", "GET"));
        assertTrue(PortaCelularFilter.permitidoNoCelular("/certificado.mobileconfig", "GET"));
        assertTrue(PortaCelularFilter.permitidoNoCelular("/api/auth/celular/entrar", "POST"));
        assertTrue(PortaCelularFilter.permitidoNoCelular("/api/comandas", "GET"));
        assertTrue(PortaCelularFilter.permitidoNoCelular("/api/comandas/12", "GET"));
        assertTrue(PortaCelularFilter.permitidoNoCelular("/api/comandas/12/lancar", "POST"));
        assertTrue(PortaCelularFilter.permitidoNoCelular("/api/comandas/12/abrir", "POST"));
        assertTrue(PortaCelularFilter.permitidoNoCelular("/api/pdv/teclas/opcoes", "GET"));
    }

    @Test
    void restoDoSistemaNaoApareceNaRede() {
        assertFalse(PortaCelularFilter.permitidoNoCelular("/estoque.html", "GET"));
        assertFalse(PortaCelularFilter.permitidoNoCelular("/api/produtos", "GET"));
        assertFalse(PortaCelularFilter.permitidoNoCelular("/api/auth/login", "POST"), "Senha não entra pela rede");
        assertFalse(PortaCelularFilter.permitidoNoCelular("/api/comandas/12/retirar", "POST"), "Tirar item só no caixa");
        assertFalse(PortaCelularFilter.permitidoNoCelular("/api/comandas/12/transferir", "POST"));
        assertFalse(PortaCelularFilter.permitidoNoCelular("/api/comandas/12", "DELETE"));
        assertFalse(PortaCelularFilter.permitidoNoCelular("/api/vendas/5/itens", "POST"));
        assertFalse(PortaCelularFilter.permitidoNoCelular("/api/pdv/teclas", "PUT"));
        assertFalse(PortaCelularFilter.permitidoNoCelular("/api/sistema/celular", "GET"));
        assertFalse(PortaCelularFilter.permitidoNoCelular("/api/comandas/12/../../produtos", "GET"));
        assertFalse(PortaCelularFilter.permitidoNoCelular("/celular.html;jsessionid=1", "GET"));
        assertFalse(PortaCelularFilter.permitidoNoCelular("/api/comandas/12345", "GET"));
    }
}
