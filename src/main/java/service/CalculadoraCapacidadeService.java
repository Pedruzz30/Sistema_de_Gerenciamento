package service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

public class CalculadoraCapacidadeService {

    public DisponibilidadeDtos.IngredienteLimitante calcularLimitante(List<EntradaCapacidade> entradas) {
        return calcular(entradas).ingredienteLimitantePrincipal();
    }

    public ResultadoCapacidade calcular(List<EntradaCapacidade> entradas) {
        List<DisponibilidadeDtos.IngredienteLimitante> limitantes = entradas == null
                ? List.of()
                : entradas.stream()
                .filter(EntradaCapacidade::bloqueante)
                .filter(entrada -> entrada.quantidadeNecessariaPorUnidade() != null)
                .filter(entrada -> entrada.quantidadeNecessariaPorUnidade().compareTo(BigDecimal.ZERO) > 0)
                .map(this::calcularIngrediente)
                .toList();

        if (limitantes.isEmpty()) {
            return new ResultadoCapacidade(null, null, List.of());
        }

        int menorCapacidade = limitantes.stream()
                .map(DisponibilidadeDtos.IngredienteLimitante::capacidade)
                .min(Comparator.naturalOrder())
                .orElse(0);
        List<DisponibilidadeDtos.IngredienteLimitante> empatados = limitantes.stream()
                .filter(limitante -> limitante.capacidade() == menorCapacidade)
                .toList();
        return new ResultadoCapacidade(
                menorCapacidade,
                empatados.get(0),
                empatados
        );
    }

    private DisponibilidadeDtos.IngredienteLimitante calcularIngrediente(EntradaCapacidade entrada) {
        BigDecimal disponivel = entrada.quantidadeDisponivel() == null
                ? BigDecimal.ZERO
                : entrada.quantidadeDisponivel();
        int capacidade = disponivel.compareTo(BigDecimal.ZERO) <= 0
                ? 0
                : inteiroSeguro(disponivel.divideToIntegralValue(entrada.quantidadeNecessariaPorUnidade()));
        return new DisponibilidadeDtos.IngredienteLimitante(
                entrada.produtoId(),
                entrada.produto(),
                disponivel,
                entrada.quantidadeNecessariaPorUnidade(),
                entrada.unidade(),
                capacidade
        );
    }

    private static int inteiroSeguro(BigDecimal valor) {
        BigDecimal maximo = BigDecimal.valueOf(Integer.MAX_VALUE);
        if (valor.compareTo(maximo) > 0) {
            return Integer.MAX_VALUE;
        }
        return Math.max(0, valor.intValue());
    }

    public record EntradaCapacidade(
            Integer produtoId,
            String produto,
            BigDecimal quantidadeDisponivel,
            BigDecimal quantidadeNecessariaPorUnidade,
            String unidade,
            boolean bloqueante
    ) {
    }

    public record ResultadoCapacidade(
            Integer capacidadeEstimada,
            DisponibilidadeDtos.IngredienteLimitante ingredienteLimitantePrincipal,
            List<DisponibilidadeDtos.IngredienteLimitante> ingredientesLimitantes
    ) {
    }
}
