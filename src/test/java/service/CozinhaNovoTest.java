package service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pedido da cozinha: o que vai, como sai no papel e os comandos da térmica. */
class CozinhaNovoTest {

    @Test
    void semConfiguracaoVaiSoOPreparadoNaHora() {
        assertTrue(CozinhaService.vaiParaCozinha("porcoes", "PREPARADO_SOB_DEMANDA", List.of()));
        assertFalse(CozinhaService.vaiParaCozinha("bebidas", "ESTOQUE_DIRETO", List.of()));
        assertTrue(CozinhaService.vaiParaCozinha("Porcoes", "ESTOQUE_DIRETO", List.of("porcoes")), "Categoria marcada vai");
        assertFalse(CozinhaService.vaiParaCozinha("caldos", "PREPARADO_SOB_DEMANDA", List.of("porcoes")), "Marcando categorias, só elas");
    }

    @Test
    void papelTemComandaQuemPediuEItens() {
        List<CozinhaService.Linha> linhas = List.of(
                new CozinhaService.Linha(1, CozinhaService.descricao("Porção de batata", "Unidade"), new BigDecimal("2"), List.of("+ Cheddar", "obs: sem sal")),
                new CozinhaService.Linha(2, CozinhaService.descricao("Caldo de mocotó", "Grande"), new BigDecimal("1.5"), List.of()));
        String texto = CozinhaService.montarTexto("PEDIDO", 13, 99, LocalDateTime.of(2026, 11, 10, 21, 35), "Ana Caixa", linhas, 32);
        assertTrue(texto.startsWith("## COZINHA\n## COMANDA 13\n10/11 21:35 - Ana Caixa\n"), texto);
        assertTrue(texto.contains("2 x Porção de batata\n    + Cheddar\n    obs: sem sal\n"), texto);
        assertTrue(texto.contains("1,5 x Caldo de mocotó (Grande)"), texto);

        String cancelar = CozinhaService.montarTexto("CANCELAMENTO", null, 57,
                LocalDateTime.of(2026, 11, 10, 21, 40), "Admin", List.of(new CozinhaService.Linha(1, "Porção de batata", new BigDecimal("-1"), List.of())), 48);
        assertTrue(cancelar.startsWith("## *** CANCELAR ***\n## BALCAO - VENDA 57\n"), cancelar);
        assertTrue(cancelar.contains("1 x Porção de batata"), "Quantidade sem sinal no papel");
    }

    @Test
    void termicaRecebeTextoSemAcentoLetraGrandeECorte() {
        byte[] bytes = ImpressoraCozinha.escPos("## COMANDA 13\n2 x Porção de mocotó\n", 48);
        String texto = new String(bytes, StandardCharsets.US_ASCII);
        assertEquals(0x1B, bytes[0]);
        assertEquals('@', bytes[1]);
        assertTrue(texto.contains("2 x Porcao de mocoto"), texto);
        assertTrue(texto.contains("\u001D!\u0011COMANDA 13"), "Comanda em letra dupla");
        // Corte no fim: GS V 66 0.
        byte[] fim = java.util.Arrays.copyOfRange(bytes, bytes.length - 4, bytes.length);
        assertTrue(java.util.Arrays.equals(new byte[]{0x1D, 'V', 66, 0}, fim), java.util.Arrays.toString(fim));
        for (byte b : bytes) assertTrue(b >= 0, "Só ASCII vai para a impressora");
        assertEquals("Acai x2 - ok", ImpressoraCozinha.semAcento("Açaí ×2 – ok"));
    }
}
