package service;

import model.CanalVenda;
import model.EstadoDisponibilidade;
import model.SeveridadeDisponibilidade;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class DisponibilidadeDtos {

    public static final String TIPO_CAPACIDADE_ITEM_ISOLADO = "ESTIMATIVA_ITEM_ISOLADO";
    public static final String TIPO_VALIDACAO_SEM_RESERVA = "ESTIMATIVA_SEM_RESERVA";

    private DisponibilidadeDtos() {
    }

    public record ResultadoDisponibilidade(
            int itemCardapioId,
            long variacaoId,
            Long filialId,
            CanalVenda canal,
            boolean disponivel,
            EstadoDisponibilidade estadoPrincipal,
            Integer capacidadeEstimada,
            String tipoCapacidade,
            String tipoValidacao,
            boolean garantia,
            IngredienteLimitante ingredienteLimitantePrincipal,
            List<IngredienteLimitante> ingredientesLimitantes,
            List<MotivoDisponibilidade> motivos,
            List<MotivoDisponibilidade> alertas,
            LocalDateTime calculadoEm
    ) {
    }

    public record MotivoDisponibilidade(
            String codigo,
            SeveridadeDisponibilidade severidade,
            Integer produtoId,
            String produtoNome,
            BigDecimal quantidadeNecessaria,
            BigDecimal quantidadeDisponivel,
            String unidade,
            String mensagem,
            boolean bloqueante
    ) {
    }

    public record IngredienteLimitante(
            Integer produtoId,
            String produto,
            BigDecimal quantidadeDisponivel,
            BigDecimal quantidadeNecessariaPorUnidade,
            String unidade,
            Integer capacidade
    ) {
    }

    public record ProdutoDiagnostico(
            Integer produtoId,
            String produto,
            BigDecimal quantidadeNecessariaPorUnidade,
            BigDecimal quantidadeAtual,
            BigDecimal quantidadeReservada,
            BigDecimal quantidadeDisponivel,
            String unidade,
            String politicaFalta,
            Integer capacidadeIndividual,
            boolean bloqueanteParaCapacidade
    ) {
    }

    public record DiagnosticoDisponibilidade(
            ResultadoDisponibilidade resumo,
            Long fichaTecnicaId,
            Long fichaTecnicaVersaoId,
            Integer numeroVersaoFicha,
            List<ProdutoDiagnostico> produtos
    ) {
    }

    public record ResumoItemDisponibilidade(
            int itemCardapioId,
            boolean itemDisponivel,
            int quantidadeVariacoesDisponiveis,
            int quantidadeVariacoesIndisponiveis
    ) {
    }

    public record ConsultaLoteDisponibilidade(
            Long filialId,
            CanalVenda canal,
            List<ResumoItemDisponibilidade> itens,
            List<ResultadoDisponibilidade> variacoes,
            LocalDateTime calculadoEm
    ) {
    }
}
