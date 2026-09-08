package service;

import model.FichaTecnicaIngrediente;
import model.StatusFichaTecnicaVersao;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record CustoFichaTecnicaResultado(
        long fichaTecnicaId,
        long versaoId,
        int numeroVersao,
        StatusFichaTecnicaVersao status,
        Long filialId,
        BigDecimal rendimentoQuantidade,
        String rendimentoUnidadeCodigo,
        List<IngredienteCusto> ingredientes,
        List<IngredientePendente> ingredientesPendentes,
        BigDecimal custoTotal,
        BigDecimal custoPorUnidadeRendimento,
        BigDecimal precoVenda,
        BigDecimal cmvPercentual,
        BigDecimal margemBrutaEstimada,
        BigDecimal margemBrutaPercentual,
        boolean custoCompleto,
        String tipoAnalise,
        LocalDateTime calculadoEm
) {

    public record IngredienteCusto(
            long ingredienteId,
            int produtoId,
            String produto,
            BigDecimal quantidadeBrutaBase,
            String unidadeBaseCodigo,
            BigDecimal custoMedioUnitario,
            BigDecimal custoTotal,
            boolean custoDisponivel,
            FichaTecnicaIngrediente ingrediente
    ) {}

    public record IngredientePendente(
            int produtoId,
            String produto,
            String motivo
    ) {}
}
