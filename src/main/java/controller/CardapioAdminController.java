package controller;

import model.CategoriaCardapio;
import model.ItemCardapio;
import model.LogAcao;
import model.Permissao;
import model.TipoItemCardapio;
import model.Usuario;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import repository.LogRepository;
import repository.UsuarioRepository;
import service.CardapioService;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/cardapio/admin")
public class CardapioAdminController {

    private final CardapioService cardapioService;
    private final UsuarioRepository usuarioRepository;
    private final LogRepository logRepository;

    public CardapioAdminController(CardapioService cardapioService,
                                   UsuarioRepository usuarioRepository,
                                   LogRepository logRepository) {
        this.cardapioService = cardapioService;
        this.usuarioRepository = usuarioRepository;
        this.logRepository = logRepository;
    }

    @GetMapping("/categorias")
    public ResponseEntity<List<CategoriaAdminResponse>> listarCategorias(
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        validarPermissaoGestaoCardapio(ruHeader);
        List<CategoriaAdminResponse> resposta = cardapioService.listarCategoriasAdministrativas().stream()
                .map(CategoriaAdminResponse::from)
                .toList();
        return ResponseEntity.ok(resposta);
    }

    @PostMapping("/categorias")
    public ResponseEntity<CategoriaAdminResponse> criarCategoria(
            @RequestBody CategoriaRequest request,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Usuario ator = validarPermissaoGestaoCardapio(ruHeader);
        CategoriaCardapio categoria = cardapioService.criarCategoria(
                request.codigo(),
                request.nomeExibicao(),
                request.ordemExibicao(),
                Boolean.TRUE
        );
        registrarLog(ator, "CARDAPIO_CATEGORIA_CRIADA",
                "Categoria de cardapio criada: " + categoria.getNomeExibicao() + " (" + categoria.getCodigo() + ")");
        return ResponseEntity.status(HttpStatus.CREATED).body(CategoriaAdminResponse.from(categoria));
    }

    @PutMapping("/categorias/{id}")
    public ResponseEntity<CategoriaAdminResponse> atualizarCategoria(
            @PathVariable int id,
            @RequestBody CategoriaRequest request,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Usuario ator = validarPermissaoGestaoCardapio(ruHeader);
        CategoriaCardapio categoria = cardapioService.atualizarCategoria(
                id,
                request.codigo(),
                request.nomeExibicao(),
                request.ordemExibicao()
        );
        registrarLog(ator, "CARDAPIO_CATEGORIA_ATUALIZADA",
                "Categoria de cardapio atualizada: " + categoria.getNomeExibicao() + " (" + categoria.getCodigo() + ")");
        return ResponseEntity.ok(CategoriaAdminResponse.from(categoria));
    }

    @RequestMapping(value = "/categorias/{id}/ativo", method = {RequestMethod.PATCH, RequestMethod.PUT})
    public ResponseEntity<CategoriaAdminResponse> atualizarStatusCategoria(
            @PathVariable int id,
            @RequestBody AtualizarAtivoRequest request,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Usuario ator = validarPermissaoGestaoCardapio(ruHeader);
        CategoriaCardapio categoria = cardapioService.atualizarStatusCategoria(id, request.ativo());
        registrarLog(ator, "CARDAPIO_CATEGORIA_STATUS",
                "Categoria de cardapio " + (categoria.isAtivo() ? "ativada: " : "desativada: ")
                        + categoria.getNomeExibicao() + " (" + categoria.getCodigo() + ")");
        return ResponseEntity.ok(CategoriaAdminResponse.from(categoria));
    }

    @RequestMapping(value = "/categorias/{id}/ordem", method = {RequestMethod.PATCH, RequestMethod.PUT})
    public ResponseEntity<CategoriaAdminResponse> atualizarOrdemCategoria(
            @PathVariable int id,
            @RequestBody AtualizarOrdemRequest request,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Usuario ator = validarPermissaoGestaoCardapio(ruHeader);
        CategoriaCardapio categoria = cardapioService.atualizarOrdemCategoria(id, request.ordemExibicao());
        registrarLog(ator, "CARDAPIO_CATEGORIA_ORDEM",
                "Categoria de cardapio reordenada: " + categoria.getNomeExibicao() + " -> ordem " + categoria.getOrdemExibicao());
        return ResponseEntity.ok(CategoriaAdminResponse.from(categoria));
    }

    @GetMapping("/itens")
    public ResponseEntity<List<ItemAdminResponse>> listarItens(
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader,
            @RequestParam(value = "categoriaId", required = false) Integer categoriaId,
            @RequestParam(value = "busca", required = false) String busca) {
        validarPermissaoGestaoCardapio(ruHeader);
        List<ItemAdminResponse> resposta = cardapioService.listarItensAdministrativos(categoriaId, busca).stream()
                .map(item -> ItemAdminResponse.from(item, cardapioService))
                .toList();
        return ResponseEntity.ok(resposta);
    }

    @PostMapping("/itens")
    public ResponseEntity<ItemAdminResponse> criarItem(
            @RequestBody ItemRequest request,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Usuario ator = validarPermissaoGestaoCardapio(ruHeader);
        ItemCardapio item = cardapioService.criarItem(
                request.codigo(),
                request.nome(),
                request.descricaoCurta(),
                request.precoVenda(),
                request.categoriaId(),
                parseTipoItem(request.tipoItem()),
                Boolean.TRUE,
                Boolean.TRUE,
                request.ordemExibicao(),
                request.produtoVinculadoId()
        );
        registrarLog(ator, "CARDAPIO_ITEM_CRIADO",
                "Item de cardapio criado: " + item.getNome() + " (" + item.getCodigo() + ")");
        return ResponseEntity.status(HttpStatus.CREATED).body(ItemAdminResponse.from(item, cardapioService));
    }

    @PutMapping("/itens/{id}")
    public ResponseEntity<ItemAdminResponse> atualizarItem(
            @PathVariable int id,
            @RequestBody ItemRequest request,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Usuario ator = validarPermissaoGestaoCardapio(ruHeader);
        ItemCardapio item = cardapioService.atualizarItem(
                id,
                request.codigo(),
                request.nome(),
                request.descricaoCurta(),
                request.precoVenda(),
                request.categoriaId(),
                parseTipoItem(request.tipoItem()),
                request.ordemExibicao(),
                request.produtoVinculadoId()
        );
        registrarLog(ator, "CARDAPIO_ITEM_ATUALIZADO",
                "Item de cardapio atualizado: " + item.getNome() + " (" + item.getCodigo() + ")");
        return ResponseEntity.ok(ItemAdminResponse.from(item, cardapioService));
    }

    @RequestMapping(value = "/itens/{id}/ativo", method = {RequestMethod.PATCH, RequestMethod.PUT})
    public ResponseEntity<ItemAdminResponse> atualizarStatusItem(
            @PathVariable int id,
            @RequestBody AtualizarAtivoRequest request,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Usuario ator = validarPermissaoGestaoCardapio(ruHeader);
        ItemCardapio item = cardapioService.atualizarStatusItem(id, request.ativo());
        registrarLog(ator, "CARDAPIO_ITEM_STATUS",
                "Item de cardapio " + (item.isAtivo() ? "ativado: " : "desativado: ") + item.getNome());
        return ResponseEntity.ok(ItemAdminResponse.from(item, cardapioService));
    }

    @RequestMapping(value = "/itens/{id}/disponibilidade", method = {RequestMethod.PATCH, RequestMethod.PUT})
    public ResponseEntity<ItemAdminResponse> atualizarDisponibilidadeItem(
            @PathVariable int id,
            @RequestBody AtualizarDisponibilidadeRequest request,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Usuario ator = validarPermissaoGestaoCardapio(ruHeader);
        ItemCardapio item = cardapioService.atualizarDisponibilidadeItem(id, request.disponivelParaVenda());
        registrarLog(ator, "CARDAPIO_ITEM_DISPONIBILIDADE",
                "Item de cardapio " + (item.isDisponivel() ? "disponibilizado: " : "indisponibilizado: ") + item.getNome());
        return ResponseEntity.ok(ItemAdminResponse.from(item, cardapioService));
    }

    @RequestMapping(value = "/itens/{id}/ordem", method = {RequestMethod.PATCH, RequestMethod.PUT})
    public ResponseEntity<ItemAdminResponse> atualizarOrdemItem(
            @PathVariable int id,
            @RequestBody AtualizarOrdemRequest request,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Usuario ator = validarPermissaoGestaoCardapio(ruHeader);
        ItemCardapio item = cardapioService.atualizarOrdemItem(id, request.ordemExibicao());
        registrarLog(ator, "CARDAPIO_ITEM_ORDEM",
                "Item de cardapio reordenado: " + item.getNome() + " -> ordem " + item.getOrdemExibicao());
        return ResponseEntity.ok(ItemAdminResponse.from(item, cardapioService));
    }

    private Usuario validarPermissaoGestaoCardapio(String ruHeader) {
        return ControllerUtils.resolveUserAndRequirePermission(
                ruHeader,
                usuarioRepository,
                Permissao.EDITAR_ESTOQUE,
                "Voce nao tem permissao para gerenciar o cardapio."
        );
    }

    private void registrarLog(Usuario ator, String acao, String descricao) {
        logRepository.salvar(new LogAcao(0, ator.getRu(), acao, descricao));
    }

    private static TipoItemCardapio parseTipoItem(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("Tipo do item de cardapio e obrigatorio.");
        }
        try {
            return TipoItemCardapio.valueOf(valor.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Tipo do item de cardapio invalido.");
        }
    }

    public record CategoriaRequest(
            String codigo,
            String nomeExibicao,
            int ordemExibicao
    ) {
    }

    public record ItemRequest(
            String codigo,
            String nome,
            String descricaoCurta,
            BigDecimal precoVenda,
            int categoriaId,
            String tipoItem,
            int ordemExibicao,
            Integer produtoVinculadoId
    ) {
    }

    public record AtualizarAtivoRequest(boolean ativo) {
    }

    public record AtualizarDisponibilidadeRequest(boolean disponivelParaVenda) {
    }

    public record AtualizarOrdemRequest(int ordemExibicao) {
    }

    public record CategoriaAdminResponse(
            int id,
            String codigo,
            String nomeExibicao,
            int ordemExibicao,
            boolean ativo
    ) {
        public static CategoriaAdminResponse from(CategoriaCardapio categoria) {
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
        public static ItemAdminResponse from(ItemCardapio item, CardapioService cardapioService) {
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
                    item.getCategoriaCardapio().getId(),
                    item.getCategoriaCardapio().getCodigo(),
                    item.getCategoriaCardapio().getNomeExibicao(),
                    item.getCategoriaCardapio().isAtivo(),
                    item.possuiProdutoVinculado() ? item.getProdutoVinculado().getId() : null,
                    item.possuiProdutoVinculado() ? item.getProdutoVinculado().getNome() : null,
                    cardapioService.nivelEstoque(item),
                    item.possuiProdutoVinculado() && item.getProdutoVinculado().isControladoPorEstoque(),
                    cardapioService.quantidadeDisponivel(item),
                    cardapioService.itemDisponivelAtual(item)
            );
        }
    }
}
