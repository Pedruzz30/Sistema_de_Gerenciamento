package service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CalculadoraCapacidadeServiceTest {

    private final CalculadoraCapacidadeService service = new CalculadoraCapacidadeService();

    @Test
    void arredondaCapacidadeParaBaixoEEscolheLimitante() {
        CalculadoraCapacidadeService.ResultadoCapacidade resultado = service.calcular(List.of(
                entrada(1, "Mussarela", "5000", "190", true),
                entrada(2, "Calabresa", "2500", "150", true),
                entrada(3, "Massa", "8000", "350", true)
        ));

        assertEquals(16, resultado.capacidadeEstimada());
        assertEquals("Calabresa", resultado.ingredienteLimitantePrincipal().produto());
    }

    @Test
    void retornaEmpateEntreLimitantes() {
        CalculadoraCapacidadeService.ResultadoCapacidade resultado = service.calcular(List.of(
                entrada(1, "A", "100", "50", true),
                entrada(2, "B", "200", "100", true),
                entrada(3, "C", "300", "100", true)
        ));

        assertEquals(2, resultado.capacidadeEstimada());
        assertEquals(2, resultado.ingredientesLimitantes().size());
    }

    @Test
    void ingredienteNaoBloqueanteNaoLimitaCapacidade() {
        CalculadoraCapacidadeService.ResultadoCapacidade resultado = service.calcular(List.of(
                entrada(1, "Massa", "1000", "100", true),
                entrada(2, "Oregano", "0", "2", false)
        ));

        assertEquals(10, resultado.capacidadeEstimada());
        assertEquals("Massa", resultado.ingredienteLimitantePrincipal().produto());
    }

    @Test
    void semIngredienteBloqueanteNaoEstimaCapacidade() {
        CalculadoraCapacidadeService.ResultadoCapacidade resultado = service.calcular(List.of(
                entrada(1, "Oregano", "0", "2", false)
        ));

        assertNull(resultado.capacidadeEstimada());
        assertNull(resultado.ingredienteLimitantePrincipal());
    }

    private static CalculadoraCapacidadeService.EntradaCapacidade entrada(Integer produtoId,
                                                                          String produto,
                                                                          String disponivel,
                                                                          String necessario,
                                                                          boolean bloqueante) {
        return new CalculadoraCapacidadeService.EntradaCapacidade(
                produtoId,
                produto,
                new BigDecimal(disponivel),
                new BigDecimal(necessario),
                "GRAMA",
                bloqueante
        );
    }
}
