package service;

import model.EstoqueProduto;
import model.VendaItemConsumo;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class CalculadoraCustoVendaService {

    public static final String TIPO_ANALISE = "ESTIMATIVA_APENAS_CUSTO_ESTOQUE";
    private static final int ESCALA_CUSTO = 8;
    private static final int ESCALA_DINHEIRO = 4;
    private static final BigDecimal CEM = new BigDecimal("100");

    public VendaItemConsumo criarConsumo(MontagemComposicaoVendaService.ItemMontado itemMontado,
                                         MontagemComposicaoVendaService.ConsumoPlanejado planejado,
                                         EstoqueProduto estoque) {
        BigDecimal custoMedio = custoMedioObrigatorio(estoque, planejado.produto().getId(), planejado.produto().getNome());
        BigDecimal custoTotal = planejado.quantidadeBaseTotal()
                .multiply(custoMedio)
                .setScale(ESCALA_DINHEIRO, RoundingMode.HALF_UP);
        return new VendaItemConsumo(
                0,
                itemMontado.item(),
                planejado.vendaItemParte(),
                planejado.produto(),
                planejado.unidadeBase(),
                planejado.quantidadeBasePorUnidade(),
                planejado.quantidadeBaseTotal(),
                custoMedio.setScale(ESCALA_CUSTO, RoundingMode.HALF_UP),
                custoTotal,
                planejado.origem(),
                planejado.ingrediente()
        );
    }

    public BigDecimal cmvPercentual(BigDecimal custo, BigDecimal receita) {
        if (receita == null || receita.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return custo.multiply(CEM).divide(receita, 4, RoundingMode.HALF_UP);
    }

    public BigDecimal margemBruta(BigDecimal receita, BigDecimal custo) {
        return receita.subtract(custo).setScale(ESCALA_DINHEIRO, RoundingMode.HALF_UP);
    }

    private BigDecimal custoMedioObrigatorio(EstoqueProduto estoque, int produtoId, String produtoNome) {
        BigDecimal custoMedio = estoque == null ? null : estoque.getCustoMedio();
        if (custoMedio == null || custoMedio.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ErroNegocioException(
                    "CUSTO_MEDIO_INDISPONIVEL",
                    "Produto sem custo medio valido para venda.",
                    java.util.Map.of("produtoId", produtoId, "produto", produtoNome)
            );
        }
        return custoMedio.setScale(ESCALA_CUSTO, RoundingMode.HALF_UP);
    }
}
