
package service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import model.EstoqueProduto;
import model.MovimentoEstoque;
import model.Permissao;
import model.PoliticaFalta;
import model.TipoMovimentoEstoque;
import model.Usuario;

import org.springframework.transaction.annotation.Transactional;

import repository.EstoqueProdutoRepository;
import repository.MovimentoEstoqueRepository;

/**
 * Servico de movimentacoes de estoque.
 *
 * Responsabilidades:
 * - Listar movimentos;
 * - Filtrar movimentos por produto;
 * - Validar estornos;
 * - Atualizar saldos;
 * - Registrar historico de estorno.
 *
 * As operacoes de escrita sao transacionais.
 */
@Transactional(readOnly = true)
public class MovimentoEstoqueService {

    private final MovimentoEstoqueRepository movimentoEstoqueRepository;
    private final EstoqueProdutoRepository estoqueProdutoRepository;

    public MovimentoEstoqueService(
            MovimentoEstoqueRepository movimentoEstoqueRepository,
            EstoqueProdutoRepository estoqueProdutoRepository
    ) {
        this.movimentoEstoqueRepository = movimentoEstoqueRepository;
        this.estoqueProdutoRepository = estoqueProdutoRepository;
    }

    // =====================================================
    // LISTAGEM
    // =====================================================

    public List<MovimentoEstoque> listarTodos() {
        return movimentoEstoqueRepository.listarTodos();
    }

    /**
     * Lista movimentos usando filtro opcional.
     *
     * Quando produtoId e informado, a consulta
     * e delegada ao repository.
     *
     * Na implementacao JPA, o filtro ocorre
     * diretamente no PostgreSQL.
     */
    public List<MovimentoEstoque> listar(
            Integer produtoId
    ) {
        if (produtoId == null) {
            return movimentoEstoqueRepository.listarTodos();
        }

        if (produtoId <= 0) {
            throw new IllegalArgumentException(
                    "ID do produto deve ser maior que zero."
            );
        }

        return movimentoEstoqueRepository
                .listarPorProduto(produtoId);
    }

    // =====================================================
    // ESTORNO
    // =====================================================

    @Transactional
    public synchronized MovimentoEstoque estornar(
            Usuario usuario,
            long movimentoId,
            String motivo,
            String observacao
    ) {
        validarPermissaoEdicao(usuario);

        if (movimentoId <= 0) {
            throw new IllegalArgumentException(
                    "ID do movimento deve ser maior que zero."
            );
        }

        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException(
                    "Motivo do estorno e obrigatorio."
            );
        }

        MovimentoEstoque original =
                movimentoEstoqueRepository
                        .buscarPorId(movimentoId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Movimento nao encontrado com ID: "
                                                + movimentoId
                                )
                        );

        // Nao permite estornar outro estorno.
        if (original.ehEstorno()) {
            throw new ErroNegocioException(
                    "MOVIMENTO_NAO_PODE_SER_ESTORNADO",
                    "Movimento de estorno nao pode ser estornado nesta fase.",
                    Map.of(
                            "movimentoId",
                            original.getId(),
                            "motivoBloqueio",
                            "ESTORNO_DE_ESTORNO"
                    )
            );
        }

        // Movimentos de venda devem ser tratados
        // pelo fluxo de cancelamento auditavel.
        if (original.getTipo()
                == TipoMovimentoEstoque.SAIDA_VENDA) {
            throw new ErroNegocioException(
                    "MOVIMENTO_NAO_PODE_SER_ESTORNADO",
                    "Movimento de venda deve ser estornado "
                            + "pelo cancelamento auditavel da venda.",
                    Map.of(
                            "movimentoId",
                            original.getId(),
                            "motivoBloqueio",
                            "USE_CANCELAMENTO_VENDA"
                    )
            );
        }

        // Bloqueia a linha de estoque correspondente
        // ao produto e a filial originais.
        EstoqueProduto estoque =
                estoqueProdutoRepository
                        .buscarPorFilialEProdutoComLock(
                                original.getFilialId(),
                                original.getProduto().getId()
                        )
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Estoque do produto nao encontrado para estorno."
                                )
                        );

        // A verificacao fica apos o lock para
        // reduzir corridas entre estornos concorrentes.
        //
        // Ainda e recomendada uma restricao unica
        // no PostgreSQL para impedir duplicidade
        // entre multiplas instancias.
        movimentoEstoqueRepository
                .buscarEstornoDoMovimento(original.getId())
                .ifPresent(estorno -> {
                    throw new ErroNegocioException(
                            "MOVIMENTO_JA_ESTORNADO",
                            "Movimento ja foi estornado.",
                            Map.of(
                                    "movimentoId",
                                    original.getId(),
                                    "estornoId",
                                    estorno.getId()
                            )
                    );
                });

        BigDecimal saldoAnterior =
                estoque.getQuantidadeAtual();

        BigDecimal custoMedioAnterior =
                estoque.getCustoMedio();

        BigDecimal valorEstoqueAnterior =
                estoque.valorEstoque();

        boolean originalAumentouSaldo =
                original.getSaldoPosterior()
                        .compareTo(original.getSaldoAnterior()) > 0;

        if (originalAumentouSaldo) {
            estornarEntrada(original, estoque);
        } else {
            estoque.aplicarEntrada(
                    original.getQuantidadeBase(),
                    original.getCustoUnitario()
            );
        }

        BigDecimal saldoPosterior =
                estoque.getQuantidadeAtual();

        BigDecimal custoMedioPosterior =
                estoque.getCustoMedio();

        BigDecimal valorEstoquePosterior =
                estoque.valorEstoque();

        estoqueProdutoRepository.salvar(estoque);

        // CORRECAO:
        // A filial do estorno deve ser a mesma
        // do movimento originalmente registrado.
        return movimentoEstoqueRepository.salvar(
                new MovimentoEstoque(
                        0,
                        original.getFilialId(),
                        original.getProduto(),
                        TipoMovimentoEstoque.ESTORNO_MOVIMENTO,
                        original.getQuantidadeBase(),
                        saldoAnterior,
                        saldoPosterior,
                        original.getCustoUnitario(),
                        original.getCustoTotal(),
                        custoMedioAnterior,
                        custoMedioPosterior,
                        valorEstoqueAnterior,
                        valorEstoquePosterior,
                        "ESTORNO_MOVIMENTO",
                        original.getId(),
                        usuario.getRu(),
                        motivo.trim(),
                        observacao,
                        null,
                        original
                )
        );
    }

    // =====================================================
    // ESTORNAR ENTRADA
    // =====================================================

    private void estornarEntrada(
            MovimentoEstoque original,
            EstoqueProduto estoque
    ) {
        BigDecimal quantidadeNecessaria =
                original.getQuantidadeBase();

        BigDecimal quantidadeDisponivel =
                estoque.quantidadeDisponivel();

        boolean bloquearNegativo =
                original.getProduto().getPoliticaFalta()
                        == PoliticaFalta.BLOQUEAR;

        if (quantidadeDisponivel.compareTo(
                quantidadeNecessaria
        ) < 0) {
            throw movimentoNaoPodeSerEstornado(
                    original,
                    quantidadeNecessaria,
                    quantidadeDisponivel,
                    "ESTOQUE_INSUFICIENTE_PARA_ESTORNO"
            );
        }

        BigDecimal novaQuantidade =
                estoque.getQuantidadeAtual()
                        .subtract(quantidadeNecessaria)
                        .setScale(
                                EstoqueProduto.ESCALA_QUANTIDADE,
                                RoundingMode.HALF_UP
                        );

        if (bloquearNegativo
                && novaQuantidade.compareTo(
                        BigDecimal.ZERO
                ) < 0) {
            throw movimentoNaoPodeSerEstornado(
                    original,
                    quantidadeNecessaria,
                    quantidadeDisponivel,
                    "POLITICA_BLOQUEAR_IMPEDE_ESTOQUE_NEGATIVO"
            );
        }

        BigDecimal valorMovimento =
                quantidadeNecessaria
                        .multiply(original.getCustoUnitario())
                        .setScale(
                                EstoqueProduto.ESCALA_VALOR,
                                RoundingMode.HALF_UP
                        );

        BigDecimal novoValorEstoque =
                estoque.valorEstoque()
                        .subtract(valorMovimento)
                        .setScale(
                                EstoqueProduto.ESCALA_VALOR,
                                RoundingMode.HALF_UP
                        );

        if (novaQuantidade.compareTo(
                BigDecimal.ZERO
        ) == 0) {
            novoValorEstoque =
                    BigDecimal.ZERO.setScale(
                            EstoqueProduto.ESCALA_VALOR,
                            RoundingMode.HALF_UP
                    );
        }

        if (novoValorEstoque.compareTo(
                BigDecimal.ZERO
        ) < 0) {
            throw movimentoNaoPodeSerEstornado(
                    original,
                    quantidadeNecessaria,
                    quantidadeDisponivel,
                    "VALOR_ESTOQUE_NEGATIVO"
            );
        }

        BigDecimal novoCustoMedio =
                estoque.getCustoMedio();

        if (novaQuantidade.compareTo(
                BigDecimal.ZERO
        ) > 0) {
            novoCustoMedio =
                    novoValorEstoque.divide(
                            novaQuantidade,
                            EstoqueProduto.ESCALA_CUSTO,
                            RoundingMode.HALF_UP
                    );
        }

        estoque.redefinirQuantidadeECusto(
                novaQuantidade,
                novoCustoMedio
        );
    }

    // =====================================================
    // ERRO DE ESTORNO
    // =====================================================

    private ErroNegocioException movimentoNaoPodeSerEstornado(
            MovimentoEstoque original,
            BigDecimal quantidadeNecessaria,
            BigDecimal quantidadeDisponivel,
            String motivoBloqueio
    ) {
        Map<String, Object> detalhes =
                new LinkedHashMap<>();

        detalhes.put(
                "movimentoId",
                original.getId()
        );

        detalhes.put(
                "produtoId",
                original.getProduto().getId()
        );

        detalhes.put(
                "quantidadeNecessaria",
                quantidadeNecessaria
        );

        detalhes.put(
                "quantidadeDisponivel",
                quantidadeDisponivel
        );

        detalhes.put(
                "motivoBloqueio",
                motivoBloqueio
        );

        detalhes.put(
                "recomendacao",
                "Use devolucao ao fornecedor ou ajuste "
                        + "autorizado para reversoes parciais."
        );

        return new ErroNegocioException(
                "MOVIMENTO_NAO_PODE_SER_ESTORNADO",
                "Movimento nao pode ser estornado integralmente.",
                detalhes
        );
    }

    // =====================================================
    // SEGURANCA
    // =====================================================

    private void validarPermissaoEdicao(
            Usuario usuario
    ) {
        if (usuario == null
                || !usuario.isAtivo()
                || usuario.getClasse() == null
                || !usuario.getClasse().possuiPermissao(
                        Permissao.EDITAR_ESTOQUE
                )) {
            throw new SecurityException(
                    "Voce nao tem permissao para estornar movimentos de estoque."
            );
        }
    }
}

