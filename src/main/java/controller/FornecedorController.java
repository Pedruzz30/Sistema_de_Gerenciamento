
package controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import model.Fornecedor;
import model.Permissao;
import model.Produto;
import model.Usuario;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import repository.UsuarioRepository;

import service.EstoqueService;
import service.FornecedorService;

/**
 * Controller REST de fornecedores do StockOS.
 *
 * Responsabilidades:
 * - Listar fornecedores;
 * - Consultar fornecedores ativos;
 * - Buscar fornecedor por ID;
 * - Cadastrar fornecedores;
 * - Vincular produtos;
 * - Desativar e reativar fornecedores.
 *
 * As regras de negocio, persistencia e auditoria
 * permanecem no FornecedorService.
 *
 * SEGURANCA:
 * A identificacao por X-User-RU e legada.
 * Nao constitui autenticacao segura nem garante
 * isolamento entre estabelecimentos.
 */
@RestController
@RequestMapping("/api/fornecedores")
public class FornecedorController {

    private static final String ERRO_VISUALIZACAO =
            "Voce nao tem permissao para visualizar fornecedores.";

    private static final String ERRO_GERENCIAMENTO =
            "Voce nao tem permissao para gerenciar fornecedores.";

    private final FornecedorService fornecedorService;
    private final EstoqueService estoqueService;
    private final UsuarioRepository usuarioRepository;

    public FornecedorController(
            FornecedorService fornecedorService,
            EstoqueService estoqueService,
            UsuarioRepository usuarioRepository
    ) {
        this.fornecedorService = fornecedorService;
        this.estoqueService = estoqueService;
        this.usuarioRepository = usuarioRepository;
    }

    // =====================================================
    // LISTAR TODOS OS FORNECEDORES
    // =====================================================

    /**
     * GET /api/fornecedores
     */
    @GetMapping
    public ResponseEntity<List<FornecedorResponse>> listarTodos(
            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        validarVisualizacao(ruHeader);

        List<FornecedorResponse> fornecedores =
                fornecedorService.listarTodos()
                        .stream()
                        .map(FornecedorResponse::from)
                        .toList();

        return ResponseEntity.ok(fornecedores);
    }

    // =====================================================
    // LISTAR FORNECEDORES ATIVOS
    // =====================================================

    /**
     * GET /api/fornecedores/ativos
     */
    @GetMapping("/ativos")
    public ResponseEntity<List<FornecedorResponse>> listarAtivos(
            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        validarVisualizacao(ruHeader);

        List<FornecedorResponse> fornecedores =
                fornecedorService.listarAtivos()
                        .stream()
                        .map(FornecedorResponse::from)
                        .toList();

        return ResponseEntity.ok(fornecedores);
    }

    // =====================================================
    // BUSCAR FORNECEDOR POR ID
    // =====================================================

    /**
     * GET /api/fornecedores/{id}
     *
     * Mantem o retorno 404 quando o fornecedor
     * nao existe.
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> buscarPorId(
            @PathVariable long id,

            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        validarVisualizacao(ruHeader);

        if (id <= 0) {
            return badRequest(
                    "ID do fornecedor invalido."
            );
        }

        Fornecedor fornecedor;

        try {
            fornecedor = fornecedorService.buscarPorId(id);
        } catch (IllegalArgumentException ex) {
            return notFound(ex.getMessage());
        }

        return ResponseEntity.ok(
                FornecedorResponse.from(fornecedor)
        );
    }

    // =====================================================
    // CADASTRAR FORNECEDOR
    // =====================================================

    /**
     * POST /api/fornecedores
     *
     * A validacao de CNPJ e realizada
     * pelo modelo Fornecedor.
     */
    @PostMapping
    public ResponseEntity<?> cadastrar(
            @RequestBody(required = false)
            CadastroFornecedorRequest request,

            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        Usuario ator = validarGerenciamento(ruHeader);

        String erro = validarCadastroRequest(request);

        if (erro != null) {
            return badRequest(erro);
        }

        try {
            Fornecedor fornecedor =
                    fornecedorService.cadastrarFornecedor(
                            ator,
                            request.nome(),
                            request.cnpj(),
                            request.telefone() == null
                                    ? ""
                                    : request.telefone()
                    );

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(FornecedorResponse.from(fornecedor));

        } catch (IllegalArgumentException ex) {
            return badRequest(ex.getMessage());
        }
    }

    // =====================================================
    // VINCULAR PRODUTO AO FORNECEDOR
    // =====================================================

    /**
     * POST /api/fornecedores/{id}/vincular-produto
     *
     * A autorizacao acontece ANTES da consulta
     * ao produto, evitando expor informacoes
     * do estoque a usuarios nao autorizados.
     */
    @PostMapping("/{id}/vincular-produto")
    public ResponseEntity<?> vincularProduto(
            @PathVariable long id,

            @RequestBody(required = false)
            VincularProdutoRequest request,

            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        Usuario ator = validarGerenciamento(ruHeader);

        if (id <= 0) {
            return badRequest(
                    "ID do fornecedor invalido."
            );
        }

        if (request == null) {
            return badRequest(
                    "Dados do produto sao obrigatorios."
            );
        }

        if (request.produtoId() <= 0) {
            return badRequest(
                    "ID do produto invalido."
            );
        }

        Optional<Produto> produto =
                estoqueService.buscarProdutoPorId(
                        request.produtoId()
                );

        if (produto.isEmpty()) {
            return notFound(
                    "Produto nao encontrado com ID: "
                            + request.produtoId()
            );
        }

        try {
            fornecedorService.vincularProduto(
                    ator,
                    id,
                    produto.get()
            );

            Fornecedor fornecedor =
                    fornecedorService.buscarPorId(id);

            return ResponseEntity.ok(
                    FornecedorResponse.from(fornecedor)
            );

        } catch (IllegalArgumentException ex) {
            return badRequest(ex.getMessage());
        }
    }

    // =====================================================
    // DESATIVAR FORNECEDOR
    // =====================================================

    /**
     * PUT /api/fornecedores/{id}/desativar
     *
     * O fornecedor permanece cadastrado
     * para preservar o historico.
     */
    @PutMapping("/{id}/desativar")
    public ResponseEntity<?> desativar(
            @PathVariable long id,

            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        Usuario ator = validarGerenciamento(ruHeader);

        if (id <= 0) {
            return badRequest(
                    "ID do fornecedor invalido."
            );
        }

        try {
            fornecedorService.desativarFornecedor(
                    ator,
                    id
            );

            return ResponseEntity.ok(
                    new MensagemResponse(
                            "Fornecedor desativado com sucesso."
                    )
            );

        } catch (IllegalArgumentException
                 | IllegalStateException ex) {
            return badRequest(ex.getMessage());
        }
    }

    // =====================================================
    // REATIVAR FORNECEDOR
    // =====================================================

    /**
     * PUT /api/fornecedores/{id}/reativar
     */
    @PutMapping("/{id}/reativar")
    public ResponseEntity<?> reativar(
            @PathVariable long id,

            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        Usuario ator = validarGerenciamento(ruHeader);

        if (id <= 0) {
            return badRequest(
                    "ID do fornecedor invalido."
            );
        }

        try {
            fornecedorService.reativarFornecedor(
                    ator,
                    id
            );

            return ResponseEntity.ok(
                    new MensagemResponse(
                            "Fornecedor reativado com sucesso."
                    )
            );

        } catch (IllegalArgumentException
                 | IllegalStateException ex) {
            return badRequest(ex.getMessage());
        }
    }

    // =====================================================
    // AUTORIZACAO LEGADA
    // =====================================================

    /**
     * Permissao para consultar fornecedores.
     */
    private Usuario validarVisualizacao(
            String ruHeader
    ) {
        return ControllerUtils
                .resolveUserAndRequirePermission(
                        ruHeader,
                        usuarioRepository,
                        Permissao.VER_COMPRAS,
                        ERRO_VISUALIZACAO
                );
    }

    /**
     * Permissao para gerenciar fornecedores.
     *
     * Mantem GERENCIAR_FUNCIONARIOS para
     * compatibilidade com o FornecedorService.
     *
     * Futuramente, substituir pela permissao
     * especifica GERENCIAR_FORNECEDORES.
     */
    private Usuario validarGerenciamento(
            String ruHeader
    ) {
        return ControllerUtils
                .resolveUserAndRequirePermission(
                        ruHeader,
                        usuarioRepository,
                        Permissao.GERENCIAR_FUNCIONARIOS,
                        ERRO_GERENCIAMENTO
                );
    }

    // =====================================================
    // VALIDACAO DE CADASTRO
    // =====================================================

    /**
     * Valida campos obrigatorios.
     *
     * A validacao completa dos digitos do CNPJ
     * permanece na entidade Fornecedor.
     */
    private static String validarCadastroRequest(
            CadastroFornecedorRequest request
    ) {
        if (request == null) {
            return "Dados do fornecedor sao obrigatorios.";
        }

        if (request.nome() == null
                || request.nome().isBlank()) {
            return "Nome e obrigatorio.";
        }

        if (request.cnpj() == null
                || request.cnpj().isBlank()) {
            return "CNPJ e obrigatorio.";
        }

        if (request.nome().trim().length() > 160) {
            return "Nome do fornecedor excede 160 caracteres.";
        }

        if (request.telefone() != null
                && request.telefone().trim().length() > 40) {
            return "Telefone excede 40 caracteres.";
        }

        return null;
    }

    // =====================================================
    // RESPOSTAS DE ERRO
    // =====================================================

    /**
     * Preserva o formato de erro utilizado
     * pelo controller original.
     */
    private static ResponseEntity<ErroResponse> badRequest(
            String mensagem
    ) {
        return ResponseEntity.badRequest()
                .body(new ErroResponse(mensagem));
    }

    private static ResponseEntity<ErroResponse> notFound(
            String mensagem
    ) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErroResponse(mensagem));
    }

    // =====================================================
    // REQUEST DTOs
    // =====================================================

    public record CadastroFornecedorRequest(
            String nome,
            String cnpj,
            String telefone
    ) {
    }

    public record VincularProdutoRequest(
            int produtoId
    ) {
    }

    // =====================================================
    // RESPONSE DTO - FORNECEDOR
    // =====================================================

    public record FornecedorResponse(
            long id,
            String nome,
            String cnpj,
            String telefone,
            boolean ativo,
            List<ProdutoVinculadoResponse> produtos
    ) {

        public static FornecedorResponse from(
                Fornecedor fornecedor
        ) {
            List<ProdutoVinculadoResponse> produtos =
                    fornecedor.getProdutos()
                            .stream()
                            .map(ProdutoVinculadoResponse::from)
                            .toList();

            return new FornecedorResponse(
                    fornecedor.getId(),
                    fornecedor.getNome(),
                    fornecedor.getCnpj(),
                    fornecedor.getTelefone(),
                    fornecedor.isAtivo(),
                    produtos
            );
        }
    }

    // =====================================================
    // RESPONSE DTO - PRODUTO
    // =====================================================

    public record ProdutoVinculadoResponse(
            int id,
            String nome,
            BigDecimal precoUnitario
    ) {

        public static ProdutoVinculadoResponse from(
                Produto produto
        ) {
            return new ProdutoVinculadoResponse(
                    produto.getId(),
                    produto.getNome(),
                    produto.getPrecoUnitario()
            );
        }
    }

    // =====================================================
    // RESPONSE DTO - MENSAGEM
    // =====================================================

    public record MensagemResponse(
            String mensagem
    ) {
    }

    // =====================================================
    // RESPONSE DTO - ERRO
    // =====================================================

    public record ErroResponse(
            String erro
    ) {
    }
}

