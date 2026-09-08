package service;

import model.FichaTecnicaVersao;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public class SimulacaoPrecoFichaTecnicaService {

    private static final int ESCALA_DINHEIRO = 4;
    private static final int ESCALA_PERCENTUAL = 4;
    private static final BigDecimal CEM = new BigDecimal("100");

    private final CalculadoraCustoFichaTecnicaService calculadoraCusto;

    public SimulacaoPrecoFichaTecnicaService(CalculadoraCustoFichaTecnicaService calculadoraCusto) {
        this.calculadoraCusto = calculadoraCusto;
    }

    @Transactional(readOnly = true)
    public ResultadoSimulacao simular(FichaTecnicaVersao versao,
                                      Long filialId,
                                      BigDecimal precoAtual,
                                      BigDecimal cmvAlvoPercentual) {
        if (cmvAlvoPercentual == null
                || cmvAlvoPercentual.compareTo(BigDecimal.ZERO) <= 0
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
        BigDecimal fator = cmvAlvoPercentual.divide(CEM, 8, RoundingMode.HALF_UP);
        BigDecimal precoSugerido = custo.custoPorUnidadeRendimento()
                .divide(fator, ESCALA_DINHEIRO, RoundingMode.HALF_UP);
        BigDecimal precoComercial = precoSugerido.setScale(2, RoundingMode.HALF_UP);
        BigDecimal precoAcima = precoComercial.add(new BigDecimal("0.90")).setScale(2, RoundingMode.HALF_UP);
        return new ResultadoSimulacao(
                custo,
                precoSugerido,
                List.of(precoComercial, precoAcima),
                true
        );
    }

    public record ResultadoSimulacao(
            CustoFichaTecnicaResultado custo,
            BigDecimal precoSugeridoMatematico,
            List<BigDecimal> sugestoesComerciais,
            boolean simulacaoCompleta
    ) {}
}
