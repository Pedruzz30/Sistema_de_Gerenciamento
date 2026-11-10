
package controller;

import java.math.BigDecimal;
import java.util.List;

import model.MovimentoEstoque;
import model.Permissao;
import model.Usuario;

import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import repository.UsuarioRepository;
import service.MovimentoEstoqueService;

/**
 * API de movimentacoes de estoque do StockOS.
 *
 * Responsabilidades:
 * - Listar movimentacoes;
 * - Filtrar movimentacoes por produto;
 * - Solicitar estornos;
 * - Validar entradas;
 * - Verificar permissoes.
 *
 * O processamento financeiro e de estoque
 * permanece no MovimentoEstoqueService.
 *
 * ATENCAO:
 * X-User-RU e um mecanismo legado e nao
 * representa autenticacao segura.
 */
@RestController
@RequestMapping("/api/movimentos-estoque")
public class MovimentoEstoqueController {

    private static final String ERRO_VISUALIZACAO =
            "Voce nao tem permissao para visualizar movimentos de estoque.";

    private static final String ERRO_ESTORNO =
            "Voce nao tem permissao para estornar movimentos de estoque.";

    private static final int MAX_MOTIVO = 240;
    private static final int MAX_OBSERVACAO = 500;

    private final MovimentoEstoqueService movimentoEstoqueService;
    private final UsuarioRepository usuarioRepository;

    public MovimentoEstoqueController(
            MovimentoEstoqueService movimentoEstoqueService,
            UsuarioRepository usuarioRepository
    ) {
        this.movimentoEstoqueService = movimentoEstoqueService;
        this.usuarioRepository = usuarioRepository;
    }

    // =====================================================
    // LISTAGEM DE MOVIMENTACOES
    // =====================================================

    /**
     * GET /api/movimentos-estoque
     *
     * Filtro opcional:
     * produtoId
     *
     * O filtro ocorre no repository quando
     * um produto e informado.
     *
     * Mantem o retorno como List, garantindo
     * compatibilidade com o frontend atual.
     */
    @GetMapping
    public ResponseEntity<List<MovimentoResponse>> listar(
            @RequestParam(
                    value = "produtoId",
                    required = false
            ) Integer produtoId,

            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        validarVisualizacao(ruHeader);

        if (produtoId != null && produtoId <= 0) {
            throw new IllegalArgumentException(
                    "ID do produto deve ser maior que zero."
            );
        }

        List<MovimentoResponse> movimentos =
                movimentoEstoqueService.listar(produtoId)
                        .stream()
                        .map(MovimentoResponse::from)
                        .toList();

        return ResponseEntity.ok(movimentos);
    }

    // =====================================================
    // ESTORNO DE MOVIMENTO
    // =====================================================

    /**
     * POST /api/movimentos-estoque/{id}/estornar
     *
     * Requisitos:
     * - Usuario autorizado;
     * - ID valido;
     * - Motivo obrigatorio;
     * - Limites de texto respeitados.
     *
     * A consistencia do estoque e validada
     * novamente na camada de servico.
     */
    @PostMapping("/{id}/estornar")
    public ResponseEntity<MovimentoResponse> estornar(
            @PathVariable long id,

            @RequestBody(required = false)
            EstornoRequest request,

            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        Usuario ator = validarPermissaoEstorno(ruHeader);

        validarIdMovimento(id);
        validarEstornoRequest(request);

        MovimentoEstoque movimento =
                movimentoEstoqueService.estornar(
                        ator,
                        id,
                        request.motivo().trim(),
                        normalizarObservacao(request.observacao())
                );

        return ResponseEntity.ok(
                MovimentoResponse.from(movimento)
        );
    }

    // =====================================================
    // AUTORIZACAO LEGADA
    // =====================================================

    private Usuario validarVisualizacao(
            String ruHeader
    ) {
        return ControllerUtils
                .resolveUserAndRequirePermission(
                        ruHeader,
                        usuarioRepository,
                        Permissao.VER_ESTOQUE,
                        ERRO_VISUALIZACAO
                );
    }

    private Usuario validarPermissaoEstorno(
            String ruHeader
    ) {
        // Permissao existente no projeto.
        // Futuramente, separar estorno
        // de edicao comum do estoque.

        return ControllerUtils
                .resolveUserAndRequirePermission(
                        ruHeader,
                        usuarioRepository,
                        Permissao.EDITAR_ESTOQUE,
                        ERRO_ESTORNO
                );
    }

    // =====================================================
    // VALIDACOES
    // =====================================================

    private static void validarIdMovimento(long id) {
        if (id <= 0) {
            throw new IllegalArgumentException(
                    "ID do movimento deve ser maior que zero."
            );
        }
    }

    private static void validarEstornoRequest(
            EstornoRequest request
    ) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "Dados do estorno sao obrigatorios."
            );
        }

        String motivo = request.motivo();

        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException(
                    "Motivo do estorno e obrigatorio."
            );
        }

        if (motivo.trim().length() > MAX_MOTIVO) {
            throw new IllegalArgumentException(
                    "Motivo excede 240 caracteres."
            );
        }

        String observacao = request.observacao();

        if (observacao != null
                && observacao.trim().length() > MAX_OBSERVACAO) {
            throw new IllegalArgumentException(
                    "Observacao excede 500 caracteres."
            );
        }
    }

    private static String normalizarObservacao(
            String observacao
    ) {
        if (observacao == null || observacao.isBlank()) {
            return null;
        }

        return observacao.trim();
    }

    // =====================================================
    // REQUEST DTO
    // =====================================================

    public record EstornoRequest(
            String motivo,
            String observacao
    ) {}

    // =====================================================
    // RESPONSE DTO
    // =====================================================

    public record MovimentoResponse(
            long id,
            Long filialId,
            int produtoId,
            String produtoNome,
            String tipo,
            BigDecimal quantidadeBase,
            BigDecimal saldoAnterior,
            BigDecimal saldoPosterior,
            BigDecimal custoUnitario,
            BigDecimal custoTotal,
            BigDecimal custoMedioAnterior,
            BigDecimal custoMedioPosterior,
            BigDecimal valorEstoqueAnterior,
            BigDecimal valorEstoquePosterior,
            String origemTipo,
            Long origemId,
            Long usuarioId,
            String motivo,
            String observacao,
            String idempotencyKey,
            Long movimentoEstornadoId,
            String criadoEm
    ) {

        public static MovimentoResponse from(
                MovimentoEstoque movimento
        ) {
            var produto = movimento.getProduto();

            MovimentoEstoque original =
                    movimento.getMovimentoEstornado();

            return new MovimentoResponse(
                    movimento.getId(),
                    movimento.getFilialId(),
                    produto.getId(),
                    produto.getNome(),
                    movimento.getTipo().name(),
                    movimento.getQuantidadeBase(),
                    movimento.getSaldoAnterior(),
                    movimento.getSaldoPosterior(),
                    movimento.getCustoUnitario(),
                    movimento.getCustoTotal(),
                    movimento.getCustoMedioAnterior(),
                    movimento.getCustoMedioPosterior(),
                    movimento.getValorEstoqueAnterior(),
                    movimento.getValorEstoquePosterior(),
                    movimento.getOrigemTipo(),
                    movimento.getOrigemId(),
                    movimento.getUsuarioId(),
                    movimento.getMotivo(),
                    movimento.getObservacao(),
                    movimento.getIdempotencyKey(),
                    original == null ? null : original.getId(),
                    movimento.getCriadoEm().toString()
            );
        }
    }
}



