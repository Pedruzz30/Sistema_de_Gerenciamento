
package controller;

import model.CategoriaCardapio;
import model.ItemCardapio;
import model.LogAcao;
import model.Permissao;
import model.Produto;
import model.TipoItemCardapio;
import model.Usuario;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import repository.LogRepository;
import repository.UsuarioRepository;

import service.CardapioService;
import service.VariacaoItemCardapioService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/**
 * API administrativa do cardapio StockOS.
 *
 * Gerencia categorias, itens, disponibilidade
 * e organizacao do cardapio.
 *
 * IMPORTANTE:
 * A autenticacao X-User-RU e legada.
 * A autorizacao definitiva deve utilizar identidade
 * verificada e contexto do estabelecimento.
 */
@RestController
@RequestMapping("/api/cardapio/admin")
public class CardapioAdminController {

    private static final String MENSAGEM_ACESSO_NEGADO =
            "Voce nao tem permissao para gerenciar o cardapio.";

    private final CardapioService cardapioService;

    private final VariacaoItemCardapioService
            variacaoItemCardapioService;

    private final UsuarioRepository usuarioRepository;
    private final LogRepository logRepository;

    public CardapioAdminController(
            CardapioService cardapioService,
            VariacaoItemCardapioService variacaoItemCardapioService,
            UsuarioRepository usuarioRepository,
            LogRepository logRepository
    ) {
        this.cardapioService = cardapioService;
        this.variacaoItemCardapioService =
                variacaoItemCardapioService;
        this.usuarioRepository = usuarioRepository;
        this.logRepository = logRepository;
    }

    // =========================================================
    // CATEGORIAS - CONSULTA
    // =========================================================

    @GetMapping("/categorias")
    public ResponseEntity<List<CategoriaAdminResponse>>
    listarCategorias(
            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        validarPermissaoGestaoCardapio(ruHeader);

        List<CategoriaAdminResponse> categorias =
                cardapioService.listarCategoriasAdministrativas()
                        .stream()
                        .map(CategoriaAdminResponse::from)
                        .toList();

        return ResponseEntity.ok(categorias);
    }

    // =========================================================
    // CATEGORIAS - CADASTRO
    // =========================================================

    @PostMapping("/categorias")
    @Transactional
    public ResponseEntity<CategoriaAdminResponse> criarCategoria(
            @RequestBody(required = false) CategoriaRequest request,
            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        Usuario ator = validarPermissaoGestaoCardapio(ruHeader);

        validarCategoriaRequest(request);

        CategoriaCardapio categoria =
                cardapioService.criarCategoria(
                        request.codigo(),
                        request.nomeExibicao(),
                        request.ordemExibicao(),
                        true
                );

        registrarLog(
                ator,
                "CARDAPIO_CATEGORIA_CRIADA",
                "Categoria de cardapio criada: "
                        + categoria.getNomeExibicao()
                        + " (" + categoria.getCodigo() + ")"
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CategoriaAdminResponse.from(categoria));
    }

    // =========================================================
    // CATEGORIAS - ATUALIZACAO
    // =========================================================

    @PutMapping("/categorias/{id}")
    @Transactional
    public ResponseEntity<CategoriaAdminResponse> atualizarCategoria(
            @PathVariable int id,
            @RequestBody(required = false) CategoriaRequest request,
            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        Usuario ator = validarPermissaoGestaoCardapio(ruHeader);

        validarId(id);
        validarCategoriaRequest(request);

        CategoriaCardapio categoria =
                cardapioService.atualizarCategoria(
                        id,
                        request.codigo(),
                        request.nomeExibicao(),
                        request.ordemExibicao()
                );

        registrarLog(
                ator,
                "CARDAPIO_CATEGORIA_ATUALIZADA",
                "Categoria de cardapio atualizada: "
                        + categoria.getNomeExibicao()
                        + " (" + categoria.getCodigo() + ")"
        );

        return ResponseEntity.ok(
                CategoriaAdminResponse.from(categoria)
        );
    }

    // =========================================================
    // CATEGORIAS - STATUS
    // =========================================================

    @RequestMapping(
            value = "/categorias/{id}/ativo",
            method = {RequestMethod.PATCH, RequestMethod.PUT}
    )
    @Transactional
    public ResponseEntity<CategoriaAdminResponse>
    atualizarStatusCategoria(
            @PathVariable int id,
            @RequestBody(required = false) AtualizarAtivoRequest request,
            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        Usuario ator = validarPermissaoGestaoCardapio(ruHeader);

        validarId(id);
        validarAtualizarAtivoRequest(request);

        CategoriaCardapio categoria =
                cardapioService.atualizarStatusCategoria(
                        id,
                        request.ativo()
                );

        String acao = categoria.isAtivo()
                ? "ativada: "
                : "desativada: ";

        registrarLog(
                ator,
                "CARDAPIO_CATEGORIA_STATUS",
                "Categoria de cardapio "
                        + acao
                        + categoria.getNomeExibicao()
                        + " (" + categoria.getCodigo() + ")"
        );

        return ResponseEntity.ok(
                CategoriaAdminResponse.from(categoria)
        );
    }

    // =========================================================
    // CATEGORIAS - ORDENACAO
    // =========================================================

    @RequestMapping(
            value = "/categorias/{id}/ordem",
            method = {RequestMethod.PATCH, RequestMethod.PUT}
    )
    @Transactional
    public ResponseEntity<CategoriaAdminResponse>
    atualizarOrdemCategoria(
            @PathVariable int id,
            @RequestBody(required = false) AtualizarOrdemRequest request,
            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        Usuario ator = validarPermissaoGestaoCardapio(ruHeader);

        validarId(id);
        validarAtualizarOrdemRequest(request);

        CategoriaCardapio categoria =
                cardapioService.atualizarOrdemCategoria(
                        id,
                        request.ordemExibicao()
                );

        registrarLog(
                ator,
                "CARDAPIO_CATEGORIA_ORDEM",
                "Categoria de cardapio reordenada: "
                        + categoria.getNomeExibicao()
                        + " -> ordem "
                        + categoria.getOrdemExibicao()
        );

        return ResponseEntity.ok(
                CategoriaAdminResponse.from(categoria)
        );
    }

    // =========================================================
    // ITENS - CONSULTA
    // =========================================================

    @GetMapping("/itens")
    public ResponseEntity<List<ItemAdminResponse>> listarItens(
            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader,

            @RequestParam(
                    value = "categoriaId",
                    required = false
            ) Integer categoriaId,

            @RequestParam(
                    value = "busca",
                    required = false
            ) String busca
    ) {
        validarPermissaoGestaoCardapio(ruHeader);

        if (categoriaId != null && categoriaId <= 0) {
            throw new IllegalArgumentException(
                    "ID da categoria deve ser maior que zero."
            );
        }

        List<ItemAdminResponse> itens =
                cardapioService
                        .listarItensAdministrativos(categoriaId, busca)
                        .stream()
                        .map(item -> ItemAdminResponse.from(
                                item,
                                cardapioService
                        ))
                        .toList();

        return ResponseEntity.ok(itens);
    }

    // =========================================================
    // ITENS - CADASTRO
    // =========================================================

    @PostMapping("/itens")
    @Transactional
    public ResponseEntity<ItemAdminResponse> criarItem(
            @RequestBody(required = false) ItemRequest request,
            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        Usuario ator = validarPermissaoGestaoCardapio(ruHeader);

        validarItemRequest(request);

        TipoItemCardapio tipo =
                parseTipoItem(request.tipoItem());

        ItemCardapio item = cardapioService.criarItem(
                request.codigo(),
                request.nome(),
                request.descricaoCurta(),
                request.precoVenda(),
                request.categoriaId(),
                tipo,
                true,
                true,
                request.ordemExibicao(),
                request.produtoVinculadoId()
        );

        // Cria a variacao padrao quando aplicavel.
        variacaoItemCardapioService
                .garantirVariacaoPadraoSePossivel(item, ator);

        registrarLog(
                ator,
                "CARDAPIO_ITEM_CRIADO",
                "Item de cardapio criado: "
                        + item.getNome()
                        + " (" + item.getCodigo() + ")"
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ItemAdminResponse.from(
                        item,
                        cardapioService
                ));
    }

    // =========================================================
    // ITENS - ATUALIZACAO
    // =========================================================

    @PutMapping("/itens/{id}")
    @Transactional
    public ResponseEntity<ItemAdminResponse> atualizarItem(
            @PathVariable int id,
            @RequestBody(required = false) ItemRequest request,
            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        Usuario ator = validarPermissaoGestaoCardapio(ruHeader);

        validarId(id);
        validarItemRequest(request);

        TipoItemCardapio tipo =
                parseTipoItem(request.tipoItem());

        ItemCardapio item = cardapioService.atualizarItem(
                id,
                request.codigo(),
                request.nome(),
                request.descricaoCurta(),
                request.precoVenda(),
                request.categoriaId(),
                tipo,
                request.ordemExibicao(),
                request.produtoVinculadoId()
        );

        variacaoItemCardapioService
                .garantirVariacaoPadraoSePossivel(item, ator);

        registrarLog(
                ator,
                "CARDAPIO_ITEM_ATUALIZADO",
                "Item de cardapio atualizado: "
                        + item.getNome()
                        + " (" + item.getCodigo() + ")"
        );

        return ResponseEntity.ok(
                ItemAdminResponse.from(item, cardapioService)
        );
    }

    // =========================================================
    // ITENS - ATIVACAO
    // =========================================================

    @RequestMapping(
            value = "/itens/{id}/ativo",
            method = {RequestMethod.PATCH, RequestMethod.PUT}
    )
    @Transactional
    public ResponseEntity<ItemAdminResponse> atualizarStatusItem(
            @PathVariable int id,
            @RequestBody(required = false) AtualizarAtivoRequest request,
            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        Usuario ator = validarPermissaoGestaoCardapio(ruHeader);

        validarId(id);
        validarAtualizarAtivoRequest(request);

        ItemCardapio item = cardapioService.atualizarStatusItem(
                id,
                request.ativo()
        );

        registrarLog(
                ator,
                "CARDAPIO_ITEM_STATUS",
                "Item de cardapio "
                        + (item.isAtivo() ? "ativado: " : "desativado: ")
                        + item.getNome()
        );

        return ResponseEntity.ok(
                ItemAdminResponse.from(item, cardapioService)
        );
    }

    // =========================================================
    // ITENS - DISPONIBILIDADE
    // =========================================================

    @RequestMapping(
            value = "/itens/{id}/disponibilidade",
            method = {RequestMethod.PATCH, RequestMethod.PUT}
    )
    @Transactional
    public ResponseEntity<ItemAdminResponse>
    atualizarDisponibilidadeItem(
            @PathVariable int id,
            @RequestBody(required = false)
            AtualizarDisponibilidadeRequest request,

            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        Usuario ator = validarPermissaoGestaoCardapio(ruHeader);

        validarId(id);
        validarDisponibilidadeRequest(request);

        ItemCardapio item =
                cardapioService.atualizarDisponibilidadeItem(
                        id,
                        request.disponivelParaVenda()
                );

        registrarLog(
                ator,
                "CARDAPIO_ITEM_DISPONIBILIDADE",
                "Item de cardapio "
                        + (item.isDisponivel()
                                ? "disponibilizado: "
                                : "indisponibilizado: ")
                        + item.getNome()
        );

        return ResponseEntity.ok(
                ItemAdminResponse.from(item, cardapioService)
        );
    }

    // =========================================================
    // ITENS - ORDENACAO
    // =========================================================

    @RequestMapping(
            value = "/itens/{id}/ordem",
            method = {RequestMethod.PATCH, RequestMethod.PUT}
    )
    @Transactional
    public ResponseEntity<ItemAdminResponse> atualizarOrdemItem(
            @PathVariable int id,
            @RequestBody(required = false) AtualizarOrdemRequest request,
            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        Usuario ator = validarPermissaoGestaoCardapio(ruHeader);

        validarId(id);
        validarAtualizarOrdemRequest(request);

        ItemCardapio item = cardapioService.atualizarOrdemItem(
                id,
                request.ordemExibicao()
        );

        registrarLog(
                ator,
                "CARDAPIO_ITEM_ORDEM",
                "Item de cardapio reordenado: "
                        + item.getNome()
                        + " -> ordem "
                        + item.getOrdemExibicao()
        );

        return ResponseEntity.ok(
                ItemAdminResponse.from(item, cardapioService)
        );
    }

    // =========================================================
    // AUTORIZACAO LEGADA
    // =========================================================

    private Usuario validarPermissaoGestaoCardapio(
            String ruHeader
    ) {
        return ControllerUtils.resolveUserAndRequirePermission(
                ruHeader,
                usuarioRepository,
                Permissao.EDITAR_ESTOQUE,
                MENSAGEM_ACESSO_NEGADO
        );
    }

    // =========================================================
    // AUDITORIA
    // =========================================================

    private void registrarLog(
            Usuario ator,
            String acao,
            String descricao
    ) {
        logRepository.salvar(
                new LogAcao(
                        0,
                        ator.getRu(),
                        acao,
                        descricao
                )
        );
    }

    // =========================================================
    // VALIDACOES
    // =========================================================

    private static void validarId(int id) {
        if (id <= 0) {
            throw new IllegalArgumentException(
                    "ID deve ser maior que zero."
            );
        }
    }

    private static void validarCategoriaRequest(
            CategoriaRequest request
    ) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "Dados da categoria sao obrigatorios."
            );
        }

        if (request.nomeExibicao() == null
                || request.nomeExibicao().isBlank()) {
            throw new IllegalArgumentException(
                    "Nome da categoria e obrigatorio."
            );
        }

        validarOrdem(request.ordemExibicao());
    }

    private static void validarItemRequest(
            ItemRequest request
    ) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "Dados do item sao obrigatorios."
            );
        }

        if (request.nome() == null || request.nome().isBlank()) {
            throw new IllegalArgumentException(
                    "Nome do item e obrigatorio."
            );
        }

        if (request.precoVenda() == null
                || request.precoVenda().signum() < 0) {
            throw new IllegalArgumentException(
                    "Preco de venda deve ser nao negativo."
            );
        }

        if (request.categoriaId() == null
                || request.categoriaId() <= 0) {
            throw new IllegalArgumentException(
                    "Categoria valida e obrigatoria."
            );
        }

        validarOrdem(request.ordemExibicao());
    }

    private static void validarAtualizarAtivoRequest(
            AtualizarAtivoRequest request
    ) {
        if (request == null || request.ativo() == null) {
            throw new IllegalArgumentException(
                    "Status ativo e obrigatorio."
            );
        }
    }

    private static void validarDisponibilidadeRequest(
            AtualizarDisponibilidadeRequest request
    ) {
        if (request == null
                || request.disponivelParaVenda() == null) {
            throw new IllegalArgumentException(
                    "Disponibilidade e obrigatoria."
            );
        }
    }

    private static void validarAtualizarOrdemRequest(
            AtualizarOrdemRequest request
    ) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "Ordem de exibicao e obrigatoria."
            );
        }

        validarOrdem(request.ordemExibicao());
    }

    private static void validarOrdem(Integer ordem) {
        if (ordem == null || ordem < 0) {
            throw new IllegalArgumentException(
                    "Ordem de exibicao deve ser nao negativa."
            );
        }
    }

    private static TipoItemCardapio parseTipoItem(
            String valor
    ) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(
                    "Tipo do item de cardapio e obrigatorio."
            );
        }

        try {
            return TipoItemCardapio.valueOf(
                    valor.trim().toUpperCase(Locale.ROOT)
            );

        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "Tipo do item de cardapio invalido."
            );
        }
    }

    // =========================================================
    // REQUEST DTOs
    // =========================================================

    public record CategoriaRequest(
            String codigo,
            String nomeExibicao,
            Integer ordemExibicao
    ) {
    }

    public record ItemRequest(
            String codigo,
            String nome,
            String descricaoCurta,
            BigDecimal precoVenda,
            Integer categoriaId,
            String tipoItem,
            Integer ordemExibicao,
            Integer produtoVinculadoId
    ) {
    }

    public record AtualizarAtivoRequest(
            Boolean ativo
    ) {
    }

    public record AtualizarDisponibilidadeRequest(
            Boolean disponivelParaVenda
    ) {
    }

    public record AtualizarOrdemRequest(
            Integer ordemExibicao
    ) {
    }

    // =========================================================
    // RESPONSE DTOs
    // =========================================================

    public record CategoriaAdminResponse(
            int id,
            String codigo,
            String nomeExibicao,
            int ordemExibicao,
            boolean ativo
    ) {
        public static CategoriaAdminResponse from(
                CategoriaCardapio categoria
        ) {
            return new CategoriaAdminResponse(
                    categoria.getId(),
                    categoria.getCodigo(),
                    categoria.getNomeExibicao(),
                    categoria.getOrdemExibicao(),
                    categoria.isAtivo()
            );
        }
    }

    public record ItemAdminResponse(
            int id,
            String codigo,
            String nome,
            String descricaoCurta,
            BigDecimal precoVenda,
            String tipoItem,
            boolean ativo,
            boolean disponivelParaVenda,
            int ordemExibicao,
            int categoriaId,
            String categoriaCodigo,
            String categoriaNomeExibicao,
            boolean categoriaAtiva,
            Integer produtoVinculadoId,
            String produtoVinculadoNome,
            String nivelEstoque,
            boolean controladoPorEstoque,
            Integer quantidadeAtual,
            boolean disponivel
    ) {
        public static ItemAdminResponse from(
                ItemCardapio item,
                CardapioService cardapioService
        ) {
            CategoriaCardapio categoria =
                    item.getCategoriaCardapio();

            Produto produto = item.getProdutoVinculado();

            return new ItemAdminResponse(
                    item.getId(),
                    item.getCodigo(),
                    item.getNome(),
                    item.getDescricaoCurta(),
                    item.getPrecoVenda(),
                    item.getTipoItem().name(),
                    item.isAtivo(),
                    item.isDisponivel(),
                    item.getOrdemExibicao(),
                    categoria.getId(),
                    categoria.getCodigo(),
                    categoria.getNomeExibicao(),
                    categoria.isAtivo(),
                    produto != null ? produto.getId() : null,
                    produto != null ? produto.getNome() : null,
                    cardapioService.nivelEstoque(item),
                    produto != null
                            && produto.isControladoPorEstoque(),
                    cardapioService.quantidadeDisponivel(item),
                    cardapioService.itemDisponivelAtual(item)
            );
        }
    }
}
