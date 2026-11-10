
package controller;

import java.math.BigDecimal;
import java.util.List;

import model.CompraEstoque;
import model.EmbalagemProduto;
import model.Fornecedor;
import model.ItemCompraEstoque;
import model.Permissao;
import model.Produto;
import model.Usuario;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import repository.UsuarioRepository;
import service.CompraEstoqueService;

/**
 * API de compras de estoque do StockOS.
 *
 * Responsabilidades:
 * - Listar compras;
 * - Criar uma compra em rascunho;
 * - Adicionar itens a uma compra;
 * - Confirmar compras com chave de idempotencia.
 *
 * As regras de estoque, autorizacao de edicao
 * e transacoes ficam no CompraEstoqueService.
 *
 * ATENCAO:
 * A autenticacao por X-User-RU e legada.
 * O isolamento por estabelecimento ainda precisa
 * ser implementado nos servicos e repositorios.
 */
@RestController
@RequestMapping("/api/compras")
public class CompraEstoqueController {

    private static final String ERRO_VISUALIZACAO =
            "Voce nao tem permissao para visualizar compras.";

    private final CompraEstoqueService compraEstoqueService;
    private final UsuarioRepository usuarioRepository;

    public CompraEstoqueController(
            CompraEstoqueService compraEstoqueService,
            UsuarioRepository usuarioRepository
    ) {
        this.compraEstoqueService = compraEstoqueService;
        this.usuarioRepository = usuarioRepository;
    }

    // =====================================================
    // LISTAR COMPRAS
    // =====================================================

    @GetMapping
    public ResponseEntity<List<CompraResponse>> listar(
            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        ControllerUtils.resolveUserAndRequirePermission(
                ruHeader,
                usuarioRepository,
                Permissao.VER_COMPRAS,
                ERRO_VISUALIZACAO
        );

        List<CompraResponse> compras =
                compraEstoqueService.listarTodas()
                        .stream()
                        .map(CompraResponse::from)
                        .toList();

        return ResponseEntity.ok(compras);
    }

    // =====================================================
    // CRIAR COMPRA
    // =====================================================

    /**
     * Cria uma compra em rascunho.
     *
     * O fornecedor continua opcional para manter
     * compatibilidade com o fluxo existente.
     */
    @PostMapping
    public ResponseEntity<CompraResponse> criar(
            @RequestBody(required = false)
            CriarCompraRequest request,

            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        Usuario ator = resolverUsuario(ruHeader);

        Long fornecedorId = request == null
                ? null
                : request.fornecedorId();

        if (fornecedorId != null) {
            validarId(fornecedorId, "fornecedor");
        }

        CompraEstoque compra = compraEstoqueService.criarCompra(
                ator,
                fornecedorId
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CompraResponse.from(compra));
    }

    // =====================================================
    // ADICIONAR ITEM
    // =====================================================

    /**
     * Adiciona um produto a uma compra em rascunho.
     *
     * O service continua responsavel pela conversao
     * de unidades e pelas regras de custo.
     */
    @PostMapping("/{id}/itens")
    public ResponseEntity<CompraResponse> adicionarItem(
            @PathVariable long id,

            @RequestBody(required = false)
            AdicionarItemCompraRequest request,

            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        Usuario ator = resolverUsuario(ruHeader);

        validarId(id, "compra");
        validarAdicionarItemRequest(request);

        CompraEstoque compra = compraEstoqueService.adicionarItem(
                ator,
                id,
                request.produtoId(),
                request.embalagemId(),
                request.unidadeCompraCodigo(),
                request.quantidadeComprada(),
                request.custoTotal(),
                request.motivoEntradaGratuita()
        );

        return ResponseEntity.ok(
                CompraResponse.from(compra)
        );
    }

    // =====================================================
    // CONFIRMAR COMPRA
    // =====================================================

    /**
     * Confirma a compra e efetiva a entrada de estoque.
     *
     * A chave de idempotencia evita que o reenvio
     * da mesma operacao gere entradas duplicadas.
     *
     * A prioridade da chave permanece:
     * 1. Cabecalho Idempotency-Key nao vazio;
     * 2. Campo idempotencyKey no corpo.
     */
    @PostMapping("/{id}/confirmar")
    public ResponseEntity<CompraResponse> confirmar(
            @PathVariable long id,

            @RequestBody(required = false)
            ConfirmarCompraRequest request,

            @RequestHeader(
                    value = "Idempotency-Key",
                    required = false
            ) String idempotencyHeader,

            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        Usuario ator = resolverUsuario(ruHeader);

        validarId(id, "compra");

        String chave = resolverChaveIdempotencia(
                idempotencyHeader,
                request
        );

        String motivo = request == null
                ? null
                : request.motivo();

        CompraEstoque compra = compraEstoqueService.confirmarCompra(
                ator,
                id,
                chave,
                motivo
        );

        return ResponseEntity.ok(
                CompraResponse.from(compra)
        );
    }

    // =====================================================
    // AUTORIZACAO LEGADA
    // =====================================================

    /**
     * Resolve o usuario da requisicao.
     *
     * A permissao de edicao e verificada novamente
     * pelo CompraEstoqueService.
     *
     * IMPORTANTE: este metodo nao autentica
     * criptograficamente a identidade.
     */
    private Usuario resolverUsuario(String ruHeader) {
        return ControllerUtils.resolveUser(
                ruHeader,
                usuarioRepository
        );
    }

    // =====================================================
    // VALIDACOES
    // =====================================================

    private static void validarId(
            long id,
            String recurso
    ) {
        if (id <= 0) {
            throw new IllegalArgumentException(
                    "ID de " + recurso + " deve ser maior que zero."
            );
        }
    }

    private static void validarAdicionarItemRequest(
            AdicionarItemCompraRequest request
    ) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "Dados do item de compra sao obrigatorios."
            );
        }

        validarId(request.produtoId(), "produto");

        if (request.embalagemId() != null) {
            validarId(request.embalagemId(), "embalagem");
        }

        if (request.quantidadeComprada() == null
                || request.quantidadeComprada()
                        .compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Quantidade comprada deve ser maior que zero."
            );
        }

        if (request.custoTotal() == null
                || request.custoTotal()
                        .compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                    "Custo total nao pode ser negativo."
            );
        }

        // O service verifica:
        // - Se foi informada embalagem OU unidade;
        // - Se a embalagem pertence ao produto;
        // - Se as unidades sao compativeis;
        // - Se custo zero possui motivo;
        // - Se o produto pode receber estoque.
    }

    // =====================================================
    // IDEMPOTENCIA
    // =====================================================

    /**
     * Preserva a prioridade original:
     * cabecalho primeiro, corpo como alternativa.
     *
     * O service realiza a validacao final
     * e a normalizacao da chave.
     */
    private static String resolverChaveIdempotencia(
            String idempotencyHeader,
            ConfirmarCompraRequest request
    ) {
        if (idempotencyHeader != null
                && !idempotencyHeader.isBlank()) {
            return idempotencyHeader;
        }

        return request == null
                ? null
                : request.idempotencyKey();
    }

    // =====================================================
    // REQUEST DTOs
    // =====================================================

    public record CriarCompraRequest(
            Long fornecedorId
    ) {
    }

    public record AdicionarItemCompraRequest(
            int produtoId,
            Long embalagemId,
            String unidadeCompraCodigo,
            BigDecimal quantidadeComprada,
            BigDecimal custoTotal,
            String motivoEntradaGratuita
    ) {
    }

    public record ConfirmarCompraRequest(
            String idempotencyKey,
            String motivo
    ) {
    }

    // =====================================================
    // RESPONSE DTO - COMPRA
    // =====================================================

    public record CompraResponse(
            long id,
            Long filialId,
            Long fornecedorId,
            String fornecedorNome,
            String status,
            BigDecimal total,
            String dataCriacao,
            String dataConfirmacao,
            Long usuarioCriacaoId,
            Long usuarioConfirmacaoId,
            List<ItemCompraResponse> itens
    ) {

        public static CompraResponse from(
                CompraEstoque compra
        ) {
            Fornecedor fornecedor = compra.getFornecedor();

            String dataConfirmacao =
                    compra.getDataConfirmacao() == null
                            ? null
                            : compra.getDataConfirmacao().toString();

            List<ItemCompraResponse> itens =
                    compra.getItens()
                            .stream()
                            .map(ItemCompraResponse::from)
                            .toList();

            return new CompraResponse(
                    compra.getId(),
                    compra.getFilialId(),
                    fornecedor != null ? fornecedor.getId() : null,
                    fornecedor != null ? fornecedor.getNome() : null,
                    compra.getStatus().name(),
                    compra.calcularTotal(),
                    compra.getDataCriacao().toString(),
                    dataConfirmacao,
                    compra.getUsuarioCriacaoId(),
                    compra.getUsuarioConfirmacaoId(),
                    itens
            );
        }
    }

    // =====================================================
    // RESPONSE DTO - ITEM
    // =====================================================

    public record ItemCompraResponse(
            long id,
            int produtoId,
            String produtoNome,
            Long embalagemId,
            String embalagemNome,
            String unidadeCompraCodigo,
            BigDecimal quantidadeComprada,
            BigDecimal quantidadeBase,
            String unidadeBaseProdutoCodigo,
            BigDecimal custoTotal,
            BigDecimal custoUnitarioCompra,
            BigDecimal custoUnitarioBase
    ) {

        public static ItemCompraResponse from(
                ItemCompraEstoque item
        ) {
            Produto produto = item.getProduto();
            EmbalagemProduto embalagem = item.getEmbalagem();

            String unidadeCompraCodigo =
                    item.getUnidadeCompra() == null
                            ? null
                            : item.getUnidadeCompra().getCodigo();

            String unidadeBaseProdutoCodigo =
                    produto.getUnidadeBase() == null
                            ? null
                            : produto.getUnidadeBase().getCodigo();

            return new ItemCompraResponse(
                    item.getId(),
                    produto.getId(),
                    produto.getNome(),
                    embalagem != null ? embalagem.getId() : null,
                    embalagem != null ? embalagem.getNome() : null,
                    unidadeCompraCodigo,
                    item.getQuantidadeComprada(),
                    item.getQuantidadeBase(),
                    unidadeBaseProdutoCodigo,
                    item.getCustoTotal(),
                    item.getCustoUnitarioCompra(),
                    item.getCustoUnitarioBase()
            );
        }
    }
}

