package app;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** O sistema usa o fuso da loja, não o do Windows. */
class FusoDaLojaNovoTest {

    @Test
    void padraoEBrasiliaEArgumentoOuVariavelTrocam() {
        assertEquals(ZoneId.of("America/Sao_Paulo"), Main.fusoDaLoja(new String[0], null));
        assertEquals(ZoneId.of("America/Manaus"), Main.fusoDaLoja(new String[]{"--server.port=8080", "--stockos.timezone=America/Manaus"}, null));
        assertEquals(ZoneId.of("America/Recife"), Main.fusoDaLoja(new String[0], "America/Recife"));
        assertEquals(ZoneId.of("America/Sao_Paulo"), Main.fusoDaLoja(new String[]{"--stockos.timezone=Lugar/Inexistente"}, null), "Fuso inválido volta ao padrão");
    }
}
