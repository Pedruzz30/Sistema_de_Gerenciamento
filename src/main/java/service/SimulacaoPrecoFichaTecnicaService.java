package service;

import model.FichaTecnicaVersao;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

public class SimulacaoPrecoFichaTecnicaService {

    private static final int ESCALA_DINHEIRO = 4;
    private static final BigDecimal CEM = BigDecimal.valueOf(100);
    private static final BigDecimal ACRESCIMO_COMERCIAL = new BigDecimal("0.90");

    private static final int ESCALA_COMERCIAL = 2;
    private static final int ESCALA_PERCENTUAL = 4;
    private static final BigDecimal CENTAVO = new BigDecimal("0.01");
    private static final BigDecimal FINAL_90 = new BigDecimal("0.90");
    private static final BigDecimal FINAL_99 = new BigDecimal("0.99");
    private static final BigDecimal LIMITE_VALOR_ENTRADA = new BigDecimal("999999999999.9999");

    private final CalculadoraCustoFichaTecnicaService calculadoraCusto;

    public SimulacaoPrecoFichaTecnicaService(CalculadoraCustoFichaTecnicaService calculadoraCusto) {
        this.calculadoraCusto = Objects.requireNonNull(calculadoraCusto, "calculadoraCusto");
    }

    @Transactional(readOnly = true)
    public ResultadoSimulacao simular(FichaTecnicaVersao versao,
                                      Long filialId,
                                      BigDecimal precoAtual,
                                      BigDecimal cmvAlvoPercentual) {
        if (cmvAlvoPercentual == null
                || cmvAlvoPercentual.signum() <= 0
                || cmvAlvoPercentual.compareTo(CEM) >= 0) {
            throw new ErroNegocioException(
                    "CMV_ALVO_INVALIDO",
                    "CMV alvo deve ser maior que zero e menor que 100."
            );
        }

        CustoFichaTecnicaResultado custo = calculadoraCusto.calcular(versao, filialId, precoAtual);
        if (!custo.custoCompleto()) {
            return new ResultadoSimulacao(custo, null, List.of(), false);
        }

        BigDecimal custoUnitario = custo.custoPorUnidadeRendimento();
        if (custoUnitario == null || custoUnitario.signum() < 0) {
            throw new IllegalStateException("Custo unitario invalido em ficha com custo completo.");
        }

        BigDecimal precoSugerido = custoUnitario.multiply(CEM)
                .divide(cmvAlvoPercentual, ESCALA_DINHEIRO, RoundingMode.HALF_UP);
        BigDecimal precoComercial = precoSugerido.setScale(2, RoundingMode.HALF_UP);
        BigDecimal precoAcima = precoComercial.add(ACRESCIMO_COMERCIAL)
                .setScale(2, RoundingMode.HALF_UP);

        return new ResultadoSimulacao(
                custo,
                precoSugerido,
                List.of(precoComercial, precoAcima),
                true
        );
    }

    /**
     * Simulacao opcional, sem persistencia. Taxas percentuais incidem sobre o preco
     * bruto de venda. Despesas variaveis unitarias nao devem duplicar custos da ficha.
     * A margem de contribuicao nao representa lucro liquido nem inclui custos fixos.
     */
    @Transactional(readOnly = true)
    public ResultadoSimulacaoAvancada simularAvancado(
            FichaTecnicaVersao versao,
            Long filialId,
            BigDecimal precoAtual,
            BigDecimal cmvAlvoPercentual,
            ParametrosPrecificacao parametros) {

        validarPercentual(cmvAlvoPercentual, false, "CMV_ALVO_INVALIDO");
        if (parametros == null) {
            throw new ErroNegocioException("PARAMETROS_PRECIFICACAO_INVALIDOS",
                    "Informe os parametros de precificacao.");
        }
        BigDecimal taxa = parametros.taxaSobreVendaPercentual();
        BigDecimal despesa = parametros.despesaVariavelUnitaria();
        BigDecimal margemAlvo = parametros.margemContribuicaoAlvoPercentual();
        validarPercentual(taxa, true, "TAXA_VENDA_INVALIDA");
        validarPercentual(margemAlvo, true, "MARGEM_ALVO_INVALIDA");
        validarValor(despesa, ESCALA_DINHEIRO, "DESPESA_VARIAVEL_INVALIDA");
        if (precoAtual != null) {
            validarValor(precoAtual, ESCALA_DINHEIRO, "PRECO_ATUAL_INVALIDO");
        }
        BigDecimal restante = CEM.subtract(taxa).subtract(margemAlvo);
        if (restante.signum() <= 0) {
            throw new ErroNegocioException("PRECIFICACAO_INVIAVEL",
                    "A soma das taxas e da margem alvo deve ser menor que 100%.");
        }

        CustoFichaTecnicaResultado custo = Objects.requireNonNull(
                calculadoraCusto.calcular(versao, filialId, precoAtual),
                "Resultado do calculo de custo.");
        if (!custo.custoCompleto()) {
            return new ResultadoSimulacaoAvancada(custo, parametros, cmvAlvoPercentual,
                    null, null, null, null, null, List.of(), false);
        }
        BigDecimal custoUnitario = custo.custoPorUnidadeRendimento();
        if (custoUnitario == null || custoUnitario.signum() < 0) {
            throw new IllegalStateException("Custo unitario invalido em ficha com custo completo.");
        }
        validarValor(custoUnitario, 8, "CUSTO_UNITARIO_INVALIDO");
        BigDecimal custoVariavel = custoUnitario.add(despesa);
        BigDecimal numeradorCmv = custoUnitario.multiply(CEM);
        BigDecimal numeradorContribuicao = custoVariavel.multiply(CEM);
        BigDecimal precoCmv = numeradorCmv.divide(cmvAlvoPercentual,
                ESCALA_DINHEIRO, RoundingMode.HALF_UP);
        BigDecimal precoContribuicao = numeradorContribuicao.divide(restante,
                ESCALA_DINHEIRO, RoundingMode.HALF_UP);
        BigDecimal pontoEquilibrio = numeradorContribuicao.divide(CEM.subtract(taxa),
                ESCALA_COMERCIAL, RoundingMode.CEILING).max(CENTAVO);

        // Arredonda diretamente das razoes exatas: nao usa o preco matematico ja arredondado.
        BigDecimal minimo = numeradorCmv.divide(cmvAlvoPercentual,
                ESCALA_COMERCIAL, RoundingMode.CEILING)
                .max(numeradorContribuicao.divide(restante,
                        ESCALA_COMERCIAL, RoundingMode.CEILING))
                .max(CENTAVO);

        List<SugestaoPreco> sugestoes = List.of(
                avaliar(EstrategiaPreco.MINIMO_COMERCIAL, minimo, custoUnitario,
                        despesa, taxa, cmvAlvoPercentual, margemAlvo),
                avaliar(EstrategiaPreco.FINAL_90, precoComFinal(minimo, FINAL_90), custoUnitario,
                        despesa, taxa, cmvAlvoPercentual, margemAlvo),
                avaliar(EstrategiaPreco.FINAL_99, precoComFinal(minimo, FINAL_99), custoUnitario,
                        despesa, taxa, cmvAlvoPercentual, margemAlvo),
                avaliar(EstrategiaPreco.INTEIRO_ACIMA, minimo.setScale(0, RoundingMode.CEILING)
                        .setScale(ESCALA_COMERCIAL), custoUnitario,
                        despesa, taxa, cmvAlvoPercentual, margemAlvo));

        SugestaoPreco avaliacaoAtual = precoAtual != null && precoAtual.signum() > 0
                ? avaliar(EstrategiaPreco.PRECO_ATUAL, precoAtual, custoUnitario,
                        despesa, taxa, cmvAlvoPercentual, margemAlvo)
                : null;
        return new ResultadoSimulacaoAvancada(custo, parametros, cmvAlvoPercentual,
                precoCmv, precoContribuicao, pontoEquilibrio, minimo,
                avaliacaoAtual, sugestoes, true);
    }

    private static BigDecimal precoComFinal(BigDecimal minimo, BigDecimal finalDesejado) {
        BigDecimal candidato = minimo.setScale(0, RoundingMode.FLOOR).add(finalDesejado);
        return candidato.compareTo(minimo) < 0 ? candidato.add(BigDecimal.ONE) : candidato;
    }

    private static SugestaoPreco avaliar(EstrategiaPreco estrategia, BigDecimal preco,
            BigDecimal custo, BigDecimal despesa, BigDecimal taxa,
            BigDecimal cmvAlvo, BigDecimal margemAlvo) {
        // Divisao por 100 exata. Arredondamento apenas para os valores de exibicao.
        BigDecimal valorTaxa = preco.multiply(taxa).movePointLeft(2);
        BigDecimal contribuicao = preco.subtract(custo).subtract(despesa).subtract(valorTaxa);
        BigDecimal lucroBruto = preco.subtract(custo);
        boolean atendeCmv = custo.multiply(CEM).compareTo(preco.multiply(cmvAlvo)) <= 0;
        boolean atendeMargem = contribuicao.multiply(CEM).compareTo(preco.multiply(margemAlvo)) >= 0;
        return new SugestaoPreco(estrategia, preco, moeda(valorTaxa), moeda(lucroBruto),
                moeda(contribuicao), percentual(custo, preco), percentual(lucroBruto, preco),
                percentual(contribuicao, preco), atendeCmv, atendeMargem,
                contribuicao.signum() < 0);
    }

    private static BigDecimal moeda(BigDecimal valor) {
        return valor.setScale(ESCALA_DINHEIRO, RoundingMode.HALF_UP);
    }

    private static BigDecimal percentual(BigDecimal valor, BigDecimal preco) {
        return valor.multiply(CEM).divide(preco, ESCALA_PERCENTUAL, RoundingMode.HALF_UP);
    }

    private static void validarPercentual(BigDecimal valor, boolean permiteZero, String codigo) {
        if (valor == null || valor.signum() < 0 || (!permiteZero && valor.signum() == 0)
                || valor.compareTo(CEM) >= 0 || valor.stripTrailingZeros().scale() > ESCALA_PERCENTUAL) {
            throw new ErroNegocioException(codigo,
                    "Percentual fora do intervalo permitido ou com mais de quatro casas decimais.");
        }
    }

    private static void validarValor(BigDecimal valor, int escala, String codigo) {
        if (valor == null || valor.signum() < 0 || valor.compareTo(LIMITE_VALOR_ENTRADA) > 0
                || valor.stripTrailingZeros().scale() > escala) {
            throw new ErroNegocioException(codigo,
                    "Valor negativo, ausente, fora do limite ou com precisao invalida.");
        }
    }

    public enum EstrategiaPreco {
        PRECO_ATUAL, MINIMO_COMERCIAL, FINAL_90, FINAL_99, INTEIRO_ACIMA
    }

    /** Use zero para taxas/despesas inexistentes e para margem alvo de equilibrio. */
    public record ParametrosPrecificacao(
            BigDecimal taxaSobreVendaPercentual,
            BigDecimal despesaVariavelUnitaria,
            BigDecimal margemContribuicaoAlvoPercentual
    ) {}

    public record SugestaoPreco(
            EstrategiaPreco estrategia,
            BigDecimal preco,
            BigDecimal taxaUnitariaEstimada,
            BigDecimal lucroBrutoUnitario,
            BigDecimal contribuicaoUnitaria,
            BigDecimal cmvPercentual,
            BigDecimal margemBrutaPercentual,
            BigDecimal margemContribuicaoPercentual,
            boolean atendeCmvAlvo,
            boolean atendeMargemContribuicaoAlvo,
            boolean contribuicaoNegativa
    ) {}

    public record ResultadoSimulacaoAvancada(
            CustoFichaTecnicaResultado custo,
            ParametrosPrecificacao parametros,
            BigDecimal cmvAlvoPercentual,
            BigDecimal precoMatematicoCmv,
            BigDecimal precoMatematicoMargemContribuicao,
            BigDecimal precoEquilibrioVariavel,
            BigDecimal precoMinimoComercial,
            SugestaoPreco avaliacaoPrecoAtual,
            List<SugestaoPreco> sugestoes,
            boolean simulacaoCompleta
    ) {}

    public record ResultadoSimulacao(
            CustoFichaTecnicaResultado custo,
            BigDecimal precoSugeridoMatematico,
            List<BigDecimal> sugestoesComerciais,
            boolean simulacaoCompleta
    ) {}
}
