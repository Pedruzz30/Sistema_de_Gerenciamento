package service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

public class CalculadoraCapacidadeService {

    public DisponibilidadeDtos.IngredienteLimitante calcularLimitante(
            List<EntradaCapacidade> entradas) {

        return calcular(entradas).ingredienteLimitantePrincipal();
    }

    public ResultadoCapacidade calcular(List<EntradaCapacidade> entradas) {
        List<DisponibilidadeDtos.IngredienteLimitante> limitantes =
                entradas == null
                        ? List.of()
                        : entradas.stream()
                                .filter(entrada -> entrada.bloqueante())
                                .filter(entrada -> {
                                    BigDecimal quantidade =
                                            entrada.quantidadeNecessariaPorUnidade();

                                    return quantidade != null
                                            && quantidade.signum() > 0;
                                })
                                .map(entrada -> calcularIngrediente(entrada))
                                .toList();

        if (limitantes.isEmpty()) {
            return new ResultadoCapacidade(null, null, List.of());
        }

        int menorCapacidade = limitantes.stream()
                .mapToInt(limitante -> limitante.capacidade())
                .min()
                .orElse(0);

        List<DisponibilidadeDtos.IngredienteLimitante> empatados =
                limitantes.stream()
                        .filter(limitante ->
                                limitante.capacidade() == menorCapacidade)
                        .toList();

        return new ResultadoCapacidade(
                menorCapacidade,
                empatados.get(0),
                empatados
        );
    }

    private DisponibilidadeDtos.IngredienteLimitante calcularIngrediente(
            EntradaCapacidade entrada) {

        BigDecimal disponivel = entrada.quantidadeDisponivel();

        if (disponivel == null) {
            disponivel = BigDecimal.ZERO;
        }

        BigDecimal quantidadeNecessaria = Objects.requireNonNull(
                entrada.quantidadeNecessariaPorUnidade(),
                "Quantidade necessaria por unidade e obrigatoria.");

        int capacidade = disponivel.signum() <= 0
                ? 0
                : inteiroSeguro(
                        disponivel.divideToIntegralValue(quantidadeNecessaria));

        return new DisponibilidadeDtos.IngredienteLimitante(
                entrada.produtoId(),
                entrada.produto(),
                disponivel,
                quantidadeNecessaria,
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
