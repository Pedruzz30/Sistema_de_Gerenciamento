package service;

import model.DimensaoUnidade;
import model.UnidadeMedida;
import org.springframework.transaction.annotation.Transactional;
import repository.UnidadeMedidaRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class UnidadeMedidaService {

    private static final int ESCALA_QUANTIDADE = 6;
    private static final BigDecimal MIL = BigDecimal.valueOf(1000);

    private final UnidadeMedidaRepository unidadeMedidaRepository;

    public UnidadeMedidaService(
            UnidadeMedidaRepository unidadeMedidaRepository) {

        this.unidadeMedidaRepository = Objects.requireNonNull(
                unidadeMedidaRepository,
                "unidadeMedidaRepository");
    }

    @Transactional
    public void garantirUnidadesPadrao() {
        criarPadraoSeAusente(
                "GRAMA", "Grama",
                DimensaoUnidade.MASSA, BigDecimal.ONE);

        criarPadraoSeAusente(
                "QUILOGRAMA", "Quilograma",
                DimensaoUnidade.MASSA, MIL);

        criarPadraoSeAusente(
                "MILILITRO", "Mililitro",
                DimensaoUnidade.VOLUME, BigDecimal.ONE);

        criarPadraoSeAusente(
                "LITRO", "Litro",
                DimensaoUnidade.VOLUME, MIL);

        criarPadraoSeAusente(
                "UNIDADE", "Unidade",
                DimensaoUnidade.CONTAGEM, BigDecimal.ONE);
    }

    @Transactional
    public List<UnidadeMedida> listarTodas() {
        garantirUnidadesPadrao();
        return unidadeMedidaRepository.listarTodos();
    }

    @Transactional
    public UnidadeMedida criar(
            String codigo,
            String nome,
            DimensaoUnidade dimensao,
            BigDecimal fatorBase,
            Boolean ativa) {

        garantirUnidadesPadrao();

        String codigoNormalizado = normalizarCodigo(codigo);

        if (unidadeMedidaRepository
                .buscarPorCodigo(codigoNormalizado)
                .isPresent()) {

            throw new IllegalArgumentException(
                    "Ja existe unidade de medida com este codigo.");
        }

        return unidadeMedidaRepository.salvar(
                new UnidadeMedida(
                        0,
                        codigoNormalizado,
                        nome,
                        dimensao,
                        fatorBase,
                        ativa
                )
        );
    }

    @Transactional
    public UnidadeMedida buscarPorCodigoObrigatorio(String codigo) {
        garantirUnidadesPadrao();

        String codigoNormalizado = normalizarCodigo(codigo);

        return unidadeMedidaRepository
                .buscarPorCodigo(codigoNormalizado)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unidade de medida nao encontrada: " + codigo));
    }

    @Transactional
    public UnidadeMedida buscarPorIdObrigatorio(long id) {
        garantirUnidadesPadrao();

        return unidadeMedidaRepository.buscarPorId(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unidade de medida nao encontrada: " + id));
    }

    public BigDecimal converter(
            BigDecimal quantidade,
            UnidadeMedida origem,
            UnidadeMedida destino) {

        if (quantidade == null || quantidade.signum() < 0) {
            throw new IllegalArgumentException(
                    "Quantidade para conversao deve ser nao negativa.");
        }

        if (origem == null) {
            throw new IllegalArgumentException(
                    "Unidade de origem e obrigatoria.");
        }

        if (destino == null) {
            throw new IllegalArgumentException(
                    "Unidade de destino e obrigatoria.");
        }

        origem.exigirCompatibilidade(destino);

        BigDecimal fatorOrigem = origem.getFatorBase();
        BigDecimal fatorDestino = destino.getFatorBase();

        if (fatorOrigem == null || fatorOrigem.signum() <= 0) {
            throw new IllegalStateException(
                    "Fator-base invalido na unidade de origem.");
        }

        if (fatorDestino == null || fatorDestino.signum() <= 0) {
            throw new IllegalStateException(
                    "Fator-base invalido na unidade de destino.");
        }

        BigDecimal quantidadeNaBaseDaDimensao =
                origem.converterParaBase(quantidade);

        return quantidadeNaBaseDaDimensao.divide(
                fatorDestino,
                ESCALA_QUANTIDADE,
                RoundingMode.HALF_UP);
    }

    public BigDecimal converterParaUnidadeBaseProduto(
            BigDecimal quantidade,
            UnidadeMedida unidadeOrigem,
            UnidadeMedida unidadeBaseProduto) {

        if (unidadeBaseProduto == null) {
            throw new IllegalArgumentException(
                    "Produto precisa de unidade-base para compras decimais.");
        }

        return converter(
                quantidade,
                unidadeOrigem,
                unidadeBaseProduto);
    }

    private void criarPadraoSeAusente(
            String codigo,
            String nome,
            DimensaoUnidade dimensao,
            BigDecimal fatorBase) {

        if (unidadeMedidaRepository.buscarPorCodigo(codigo).isPresent()) {
            return;
        }

        unidadeMedidaRepository.salvar(
                new UnidadeMedida(
                        0,
                        codigo,
                        nome,
                        dimensao,
                        fatorBase,
                        Boolean.TRUE
                )
        );
    }

    private static String normalizarCodigo(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            throw new IllegalArgumentException(
                    "Codigo da unidade e obrigatorio.");
        }

        return codigo.trim().toUpperCase(Locale.ROOT);
    }
}
