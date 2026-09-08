package service;

import model.DimensaoUnidade;
import model.UnidadeMedida;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import repository.InMemoryUnidadeMedidaRepository;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UnidadeMedidaServiceTest {

    private UnidadeMedidaService service;

    @BeforeEach
    void setUp() {
        service = new UnidadeMedidaService(new InMemoryUnidadeMedidaRepository());
    }

    @Test
    void converteQuilogramaParaGrama() {
        UnidadeMedida kg = service.buscarPorCodigoObrigatorio("QUILOGRAMA");
        UnidadeMedida g = service.buscarPorCodigoObrigatorio("GRAMA");

        BigDecimal convertido = service.converter(new BigDecimal("2.5"), kg, g);

        assertBigDecimalEquals("2500.000000", convertido);
    }

    @Test
    void converteLitroParaMililitro() {
        UnidadeMedida litro = service.buscarPorCodigoObrigatorio("LITRO");
        UnidadeMedida ml = service.buscarPorCodigoObrigatorio("MILILITRO");

        BigDecimal convertido = service.converter(new BigDecimal("1.75"), litro, ml);

        assertBigDecimalEquals("1750.000000", convertido);
    }

    @Test
    void bloqueiaConversaoEntreMassaEVolume() {
        UnidadeMedida kg = service.buscarPorCodigoObrigatorio("QUILOGRAMA");
        UnidadeMedida litro = service.buscarPorCodigoObrigatorio("LITRO");

        IllegalArgumentException erro = assertThrows(
                IllegalArgumentException.class,
                () -> service.converter(BigDecimal.ONE, kg, litro)
        );

        assertEquals("Unidades incompatíveis: QUILOGRAMA (MASSA) para LITRO (VOLUME).", erro.getMessage());
    }

    @Test
    void permiteCriarUnidadeConfiguravelCompativel() {
        UnidadeMedida duzia = service.criar(
                "DUZIA",
                "Duzia",
                DimensaoUnidade.CONTAGEM,
                new BigDecimal("12"),
                Boolean.TRUE
        );

        assertEquals("DUZIA", duzia.getCodigo());
        assertEquals(DimensaoUnidade.CONTAGEM, duzia.getDimensao());
    }

    private static void assertBigDecimalEquals(String esperado, BigDecimal atual) {
        assertEquals(0, new BigDecimal(esperado).compareTo(atual));
    }
}
