package service;

import model.EstoqueProduto;
import model.FichaTecnicaIngrediente;
import model.FichaTecnicaVersao;
import model.UnidadeMedida;
import org.springframework.transaction.annotation.Transactional;
import repository.EstoqueProdutoRepository;
import repository.FichaTecnicaIngredienteRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class CalculadoraCustoFichaTecnicaService {

    public static final String TIPO_ANALISE_INGREDIENTES = "ESTIMATIVA_APENAS_INGREDIENTES";
    private static final int ESCALA_CUSTO_UNITARIO = 8;
    private static final int ESCALA_DINHEIRO = 4;
    private static final int ESCALA_PERCENTUAL = 4;
    private static final BigDecimal CEM = new BigDecimal("100");

    private final FichaTecnicaIngredienteRepository ingredienteRepository;
    private final EstoqueProdutoRepository estoqueProdutoRepository;

    public CalculadoraCustoFichaTecnicaService(FichaTecnicaIngredienteRepository ingredienteRepository,
                                               EstoqueProdutoRepository estoqueProdutoRepository) {
        this.ingredienteRepository = ingredienteRepository;
        this.estoqueProdutoRepository = estoqueProdutoRepository;
    }

    @Transactional(readOnly = true)
    public CustoFichaTecnicaResultado calcular(FichaTecnicaVersao versao,
                                               Long filialId,
                                               BigDecimal precoVenda) {
        if (versao == null) {
            throw new ErroNegocioException("FICHA_TECNICA_NAO_ENCONTRADA", "Versao da ficha tecnica nao encontrada.");
        }
        if (filialId == null) {
            throw new ErroNegocioException("FILIAL_INVALIDA", "Filial e obrigatoria para calculo de custo.");
        }

        List<FichaTecnicaIngrediente> ingredientes = ingredienteRepository.listarPorVersao(versao.getId());
        List<CustoFichaTecnicaResultado.IngredienteCusto> custos = new ArrayList<>();
        List<CustoFichaTecnicaResultado.IngredientePendente> pendentes = new ArrayList<>();
        BigDecimal custoTotal = BigDecimal.ZERO.setScale(ESCALA_DINHEIRO, RoundingMode.HALF_UP);

        for (FichaTecnicaIngrediente ingrediente : ingredientes) {
            UnidadeMedida unidadeBase = ingrediente.getProduto().getUnidadeBase();
            String unidadeBaseCodigo = unidadeBase == null ? null : unidadeBase.getCodigo();
            EstoqueProduto estoque = estoqueProdutoRepository
                    .buscarPorFilialEProduto(filialId, ingrediente.getProduto().getId())
                    .orElse(null);

            BigDecimal custoMedio = estoque == null ? null : estoque.getCustoMedio();
            boolean custoDisponivel = custoMedio != null && custoMedio.compareTo(BigDecimal.ZERO) > 0;
            if (!custoDisponivel) {
                pendentes.add(new CustoFichaTecnicaResultado.IngredientePendente(
                        ingrediente.getProduto().getId(),
                        ingrediente.getProduto().getNome(),
                        "CUSTO_MEDIO_INDISPONIVEL"
                ));
                custos.add(new CustoFichaTecnicaResultado.IngredienteCusto(
                        ingrediente.getId(),
                        ingrediente.getProduto().getId(),
                        ingrediente.getProduto().getNome(),
                        ingrediente.getQuantidadeBrutaBase(),
                        unidadeBaseCodigo,
                        null,
                        BigDecimal.ZERO.setScale(ESCALA_DINHEIRO, RoundingMode.HALF_UP),
                        false,
                        ingrediente
                ));
                continue;
            }

            BigDecimal custoUnitario = custoMedio.setScale(ESCALA_CUSTO_UNITARIO, RoundingMode.HALF_UP);
            BigDecimal custoIngrediente = ingrediente.getQuantidadeBrutaBase()
                    .multiply(custoUnitario)
                    .setScale(ESCALA_DINHEIRO, RoundingMode.HALF_UP);
            custoTotal = custoTotal.add(custoIngrediente).setScale(ESCALA_DINHEIRO, RoundingMode.HALF_UP);
            custos.add(new CustoFichaTecnicaResultado.IngredienteCusto(
                    ingrediente.getId(),
                    ingrediente.getProduto().getId(),
                    ingrediente.getProduto().getNome(),
                    ingrediente.getQuantidadeBrutaBase(),
                    unidadeBaseCodigo,
                    custoUnitario,
                    custoIngrediente,
                    true,
                    ingrediente
            ));
        }

        BigDecimal custoPorUnidade = custoTotal.divide(
                versao.getRendimentoQuantidade(),
                ESCALA_DINHEIRO,
                RoundingMode.HALF_UP
        );
        BigDecimal preco = precoVenda == null ? BigDecimal.ZERO : precoVenda;
        BigDecimal cmv = BigDecimal.ZERO.setScale(ESCALA_PERCENTUAL, RoundingMode.HALF_UP);
        BigDecimal margem = BigDecimal.ZERO.setScale(ESCALA_DINHEIRO, RoundingMode.HALF_UP);
        BigDecimal margemPercentual = BigDecimal.ZERO.setScale(ESCALA_PERCENTUAL, RoundingMode.HALF_UP);
        if (preco.compareTo(BigDecimal.ZERO) > 0) {
            margem = preco.subtract(custoPorUnidade).setScale(ESCALA_DINHEIRO, RoundingMode.HALF_UP);
            cmv = custoPorUnidade.multiply(CEM).divide(preco, ESCALA_PERCENTUAL, RoundingMode.HALF_UP);
            margemPercentual = margem.multiply(CEM).divide(preco, ESCALA_PERCENTUAL, RoundingMode.HALF_UP);
        }

        return new CustoFichaTecnicaResultado(
                versao.getFichaTecnica().getId(),
                versao.getId(),
                versao.getNumeroVersao(),
                versao.getStatus(),
                filialId,
                versao.getRendimentoQuantidade(),
                versao.getRendimentoUnidade().getCodigo(),
                List.copyOf(custos),
                List.copyOf(pendentes),
                custoTotal,
                custoPorUnidade,
                preco.setScale(2, RoundingMode.HALF_UP),
                cmv,
                margem,
                margemPercentual,
                pendentes.isEmpty(),
                TIPO_ANALISE_INGREDIENTES,
                LocalDateTime.now()
        );
    }
}
