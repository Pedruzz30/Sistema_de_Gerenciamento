package service;

import model.DimensaoUnidade;
import model.UnidadeMedida;
import org.springframework.transaction.annotation.Transactional;
import repository.UnidadeMedidaRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public class UnidadeMedidaService {

    private static final int ESCALA_QUANTIDADE = 6;
    private final UnidadeMedidaRepository unidadeMedidaRepository;

    public UnidadeMedidaService(UnidadeMedidaRepository unidadeMedidaRepository) {
        this.unidadeMedidaRepository = unidadeMedidaRepository;
    }

    @Transactional
    public void garantirUnidadesPadrao() {
        criarPadraoSeAusente("GRAMA", "Grama", DimensaoUnidade.MASSA, new BigDecimal("1"));
        criarPadraoSeAusente("QUILOGRAMA", "Quilograma", DimensaoUnidade.MASSA, new BigDecimal("1000"));
        criarPadraoSeAusente("MILILITRO", "Mililitro", DimensaoUnidade.VOLUME, new BigDecimal("1"));
        criarPadraoSeAusente("LITRO", "Litro", DimensaoUnidade.VOLUME, new BigDecimal("1000"));
        criarPadraoSeAusente("UNIDADE", "Unidade", DimensaoUnidade.CONTAGEM, new BigDecimal("1"));
    }

    @Transactional
    public List<UnidadeMedida> listarTodas() {
        garantirUnidadesPadrao();
        return unidadeMedidaRepository.listarTodos();
    }

    @Transactional
    public UnidadeMedida criar(String codigo,
                               String nome,
                               DimensaoUnidade dimensao,
                               BigDecimal fatorBase,
                               Boolean ativa) {
        garantirUnidadesPadrao();
        String codigoNormalizado = normalizarCodigo(codigo);
        unidadeMedidaRepository.buscarPorCodigo(codigoNormalizado).ifPresent(unidade -> {
            throw new IllegalArgumentException("Ja existe unidade de medida com este codigo.");
        });
        return unidadeMedidaRepository.salvar(
                new UnidadeMedida(0, codigoNormalizado, nome, dimensao, fatorBase, ativa)
        );
    }

    @Transactional
    public UnidadeMedida buscarPorCodigoObrigatorio(String codigo) {
        garantirUnidadesPadrao();
        return unidadeMedidaRepository.buscarPorCodigo(normalizarCodigo(codigo))
                .orElseThrow(() -> new IllegalArgumentException("Unidade de medida nao encontrada: " + codigo));
    }

    @Transactional
    public UnidadeMedida buscarPorIdObrigatorio(long id) {
        garantirUnidadesPadrao();
        return unidadeMedidaRepository.buscarPorId(id)
                .orElseThrow(() -> new IllegalArgumentException("Unidade de medida nao encontrada: " + id));
    }

    public BigDecimal converter(BigDecimal quantidade, UnidadeMedida origem, UnidadeMedida destino) {
        validarQuantidadeNaoNegativa(quantidade);
        origem.exigirCompatibilidade(destino);
        BigDecimal quantidadeNaBaseDaDimensao = origem.converterParaBase(quantidade);
        return quantidadeNaBaseDaDimensao
                .divide(destino.getFatorBase(), ESCALA_QUANTIDADE, RoundingMode.HALF_UP);
    }

    public BigDecimal converterParaUnidadeBaseProduto(BigDecimal quantidade,
                                                      UnidadeMedida unidadeOrigem,
                                                      UnidadeMedida unidadeBaseProduto) {
        if (unidadeBaseProduto == null) {
            throw new IllegalArgumentException("Produto precisa de unidade-base para compras decimais.");
        }
        return converter(quantidade, unidadeOrigem, unidadeBaseProduto);
    }

    private void criarPadraoSeAusente(String codigo, String nome, DimensaoUnidade dimensao, BigDecimal fatorBase) {
        if (unidadeMedidaRepository.buscarPorCodigo(codigo).isPresent()) {
            return;
        }
        unidadeMedidaRepository.salvar(new UnidadeMedida(0, codigo, nome, dimensao, fatorBase, Boolean.TRUE));
    }

    private static String normalizarCodigo(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            throw new IllegalArgumentException("Codigo da unidade e obrigatorio.");
        }
        return codigo.trim().toUpperCase();
    }

    private static void validarQuantidadeNaoNegativa(BigDecimal quantidade) {
        if (quantidade == null || quantidade.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Quantidade para conversao deve ser nao negativa.");
        }
    }
}
