package service;

import model.FichaTecnicaIngrediente;
import model.FichaTecnicaVersao;
import model.LogAcao;
import model.PoliticaFaltaIngrediente;
import model.Produto;
import model.UnidadeMedida;
import model.Usuario;
import org.springframework.transaction.annotation.Transactional;
import repository.FichaTecnicaIngredienteRepository;
import repository.FichaTecnicaVersaoRepository;
import repository.LogRepository;
import repository.ProdutoRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

public class FichaTecnicaIngredienteService {

    private static final int ESCALA_QUANTIDADE = 6;
    private static final BigDecimal CEM = new BigDecimal("100");

    private final FichaTecnicaVersaoRepository versaoRepository;
    private final FichaTecnicaIngredienteRepository ingredienteRepository;
    private final ProdutoRepository produtoRepository;
    private final UnidadeMedidaService unidadeMedidaService;
    private final LogRepository logRepository;

    public FichaTecnicaIngredienteService(FichaTecnicaVersaoRepository versaoRepository,
                                          FichaTecnicaIngredienteRepository ingredienteRepository,
                                          ProdutoRepository produtoRepository,
                                          UnidadeMedidaService unidadeMedidaService,
                                          LogRepository logRepository) {
        this.versaoRepository = versaoRepository;
        this.ingredienteRepository = ingredienteRepository;
        this.produtoRepository = produtoRepository;
        this.unidadeMedidaService = unidadeMedidaService;
        this.logRepository = logRepository;
    }

    @Transactional
    public FichaTecnicaIngrediente adicionar(long fichaId,
                                             long versaoId,
                                             Integer produtoId,
                                             Long unidadeInformadaId,
                                             BigDecimal quantidadeInformada,
                                             Boolean aplicarPerda,
                                             BigDecimal percentualPerda,
                                             Boolean ingredienteCritico,
                                             PoliticaFaltaIngrediente politicaFalta,
                                             Integer ordem,
                                             String observacao,
                                             Usuario usuario) {
        FichaTecnicaVersao versao = buscarVersaoDaFicha(fichaId, versaoId);
        IngredienteCalculado calculado = calcularIngrediente(produtoId, unidadeInformadaId,
                quantidadeInformada, aplicarPerda, percentualPerda);
        FichaTecnicaIngrediente ingrediente = new FichaTecnicaIngrediente(
                0,
                versao,
                calculado.produto(),
                calculado.unidadeInformada(),
                quantidadeInformada,
                calculado.quantidadeLiquidaBase(),
                calculado.percentualPerda(),
                calculado.quantidadeBrutaBase(),
                ingredienteCritico,
                politicaFalta,
                ordem == null ? 0 : ordem,
                observacao
        );
        ingrediente = ingredienteRepository.salvar(ingrediente);
        auditar(usuario, "FICHA_INGREDIENTE_INCLUIDO",
                "Ingrediente " + ingrediente.getProduto().getNome() + " incluido na ficha " + fichaId + ".");
        return ingrediente;
    }

    @Transactional
    public FichaTecnicaIngrediente atualizar(long fichaId,
                                             long versaoId,
                                             long ingredienteId,
                                             Integer produtoId,
                                             Long unidadeInformadaId,
                                             BigDecimal quantidadeInformada,
                                             Boolean aplicarPerda,
                                             BigDecimal percentualPerda,
                                             Boolean ingredienteCritico,
                                             PoliticaFaltaIngrediente politicaFalta,
                                             Integer ordem,
                                             String observacao,
                                             Usuario usuario) {
        FichaTecnicaVersao versao = buscarVersaoDaFicha(fichaId, versaoId);
        FichaTecnicaIngrediente ingrediente = buscarIngredienteDaVersao(versaoId, ingredienteId);
        IngredienteCalculado calculado = calcularIngrediente(produtoId, unidadeInformadaId,
                quantidadeInformada, aplicarPerda, percentualPerda);
        ingrediente.atualizar(
                versao,
                calculado.produto(),
                calculado.unidadeInformada(),
                quantidadeInformada,
                calculado.quantidadeLiquidaBase(),
                calculado.percentualPerda(),
                calculado.quantidadeBrutaBase(),
                ingredienteCritico,
                politicaFalta,
                ordem == null ? ingrediente.getOrdem() : ordem,
                observacao
        );
        ingrediente = ingredienteRepository.salvar(ingrediente);
        auditar(usuario, "FICHA_INGREDIENTE_ALTERADO",
                "Ingrediente " + ingrediente.getId() + " alterado na ficha " + fichaId + ".");
        return ingrediente;
    }

    @Transactional
    public void remover(long fichaId, long versaoId, long ingredienteId, Usuario usuario) {
        FichaTecnicaVersao versao = buscarVersaoDaFicha(fichaId, versaoId);
        versao.exigirRascunho();
        FichaTecnicaIngrediente ingrediente = buscarIngredienteDaVersao(versaoId, ingredienteId);
        ingredienteRepository.remover(ingrediente);
        auditar(usuario, "FICHA_INGREDIENTE_REMOVIDO",
                "Ingrediente " + ingredienteId + " removido da ficha " + fichaId + ".");
    }

    @Transactional(readOnly = true)
    public FichaTecnicaIngrediente buscarIngredienteDaVersao(long versaoId, long ingredienteId) {
        FichaTecnicaIngrediente ingrediente = ingredienteRepository.buscarPorId(ingredienteId)
                .orElseThrow(() -> new ErroNegocioException(
                        "INGREDIENTE_NAO_ENCONTRADO",
                        "Ingrediente da ficha tecnica nao encontrado."
                ));
        if (ingrediente.getFichaTecnicaVersao().getId() != versaoId) {
            throw new ErroNegocioException("INGREDIENTE_NAO_ENCONTRADO", "Ingrediente nao pertence a versao informada.");
        }
        return ingrediente;
    }

    public IngredienteCalculado calcularIngrediente(Integer produtoId,
                                                    Long unidadeInformadaId,
                                                    BigDecimal quantidadeInformada,
                                                    Boolean aplicarPerda,
                                                    BigDecimal percentualPerda) {
        if (produtoId == null) {
            throw new ErroNegocioException("PRODUTO_NAO_ENCONTRADO", "Produto do ingrediente e obrigatorio.");
        }
        if (unidadeInformadaId == null) {
            throw new ErroNegocioException("UNIDADE_INCOMPATIVEL", "Unidade do ingrediente e obrigatoria.");
        }
        if (quantidadeInformada == null || quantidadeInformada.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ErroNegocioException(
                    "QUANTIDADE_INGREDIENTE_INVALIDA",
                    "Quantidade do ingrediente deve ser maior que zero."
            );
        }

        Produto produto = produtoRepository.buscarPorId(produtoId)
                .orElseThrow(() -> new ErroNegocioException("PRODUTO_NAO_ENCONTRADO", "Produto nao encontrado."));
        if (!produto.isAtivo()) {
            throw new ErroNegocioException("PRODUTO_INATIVO", "Produto inativo nao pode ser usado em ficha tecnica.");
        }
        if (produto.getUnidadeBase() == null) {
            throw new ErroNegocioException(
                    "UNIDADE_INCOMPATIVEL",
                    "Produto precisa de unidade-base para ser usado em ficha tecnica.",
                    Map.of("produtoId", produto.getId())
            );
        }
        UnidadeMedida unidadeInformada = unidadeMedidaService.buscarPorIdObrigatorio(unidadeInformadaId);
        BigDecimal quantidadeBase;
        try {
            quantidadeBase = unidadeMedidaService.converterParaUnidadeBaseProduto(
                    quantidadeInformada,
                    unidadeInformada,
                    produto.getUnidadeBase()
            );
        } catch (IllegalArgumentException ex) {
            throw new ErroNegocioException(
                    "UNIDADE_INCOMPATIVEL",
                    ex.getMessage(),
                    Map.of("produtoId", produto.getId(), "unidadeInformadaId", unidadeInformadaId)
            );
        }

        boolean comPerda = aplicarPerda != null && aplicarPerda;
        if (!comPerda) {
            if (percentualPerda != null && percentualPerda.compareTo(BigDecimal.ZERO) != 0) {
                throw new ErroNegocioException(
                        "PERCENTUAL_PERDA_INVALIDO",
                        "Percentual de perda so pode ser informado no modo por perda."
                );
            }
            return new IngredienteCalculado(
                    produto,
                    unidadeInformada,
                    null,
                    null,
                    quantidadeBase.setScale(ESCALA_QUANTIDADE, RoundingMode.HALF_UP)
            );
        }

        validarPercentualPerda(percentualPerda);
        BigDecimal divisor = BigDecimal.ONE.subtract(percentualPerda.divide(CEM, 8, RoundingMode.HALF_UP));
        BigDecimal quantidadeBruta = quantidadeBase.divide(divisor, ESCALA_QUANTIDADE, RoundingMode.HALF_UP);
        return new IngredienteCalculado(
                produto,
                unidadeInformada,
                quantidadeBase.setScale(ESCALA_QUANTIDADE, RoundingMode.HALF_UP),
                percentualPerda.setScale(4, RoundingMode.HALF_UP),
                quantidadeBruta
        );
    }

    private void validarPercentualPerda(BigDecimal percentualPerda) {
        if (percentualPerda == null) {
            throw new ErroNegocioException("PERCENTUAL_PERDA_INVALIDO", "Percentual de perda e obrigatorio.");
        }
        if (percentualPerda.compareTo(BigDecimal.ZERO) < 0 || percentualPerda.compareTo(CEM) >= 0) {
            throw new ErroNegocioException(
                    "PERCENTUAL_PERDA_INVALIDO",
                    "Percentual de perda deve ser maior ou igual a zero e menor que 100."
            );
        }
    }

    private FichaTecnicaVersao buscarVersaoDaFicha(long fichaId, long versaoId) {
        FichaTecnicaVersao versao = versaoRepository.buscarPorId(versaoId)
                .orElseThrow(() -> new ErroNegocioException(
                        "FICHA_TECNICA_NAO_ENCONTRADA",
                        "Versao da ficha tecnica nao encontrada."
                ));
        if (versao.getFichaTecnica().getId() != fichaId) {
            throw new ErroNegocioException("FICHA_TECNICA_NAO_ENCONTRADA", "Versao nao pertence a ficha informada.");
        }
        versao.exigirRascunho();
        return versao;
    }

    private void auditar(Usuario usuario, String acao, String descricao) {
        logRepository.salvar(new LogAcao(0, usuario == null ? null : usuario.getRu(), acao, descricao));
    }

    public record IngredienteCalculado(
            Produto produto,
            UnidadeMedida unidadeInformada,
            BigDecimal quantidadeLiquidaBase,
            BigDecimal percentualPerda,
            BigDecimal quantidadeBrutaBase
    ) {}
}
