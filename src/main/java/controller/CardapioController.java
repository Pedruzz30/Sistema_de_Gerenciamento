package controller;

import model.CategoriaCardapio;
import model.ItemCardapio;
import model.Permissao;
import model.Usuario;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import repository.UsuarioRepository;
import service.CardapioService;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/cardapio")
public class CardapioController {

    private final CardapioService cardapioService;
    private final UsuarioRepository usuarioRepository;

    public CardapioController(CardapioService cardapioService,
                              UsuarioRepository usuarioRepository) {
        this.cardapioService = cardapioService;
        this.usuarioRepository = usuarioRepository;
    }

    @GetMapping("/categorias")
    public ResponseEntity<List<CategoriaCardapioResponse>> listarCategorias(
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        validarPermissaoCardapio(ruHeader);
        List<CategoriaCardapioResponse> resposta = cardapioService.listarCategoriasAtivas().stream()
                .map(CategoriaCardapioResponse::from)
                .toList();
        return ResponseEntity.ok(resposta);
    }

    @GetMapping("/itens")
    public ResponseEntity<List<ItemCardapioResponse>> listarItens(
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader,
            @RequestParam(value = "categoria", required = false) String categoria,
            @RequestParam(value = "busca", required = false) String busca) {
        validarPermissaoCardapio(ruHeader);
        List<ItemCardapioResponse> resposta = cardapioService.listarItensAtivos(categoria, busca).stream()
                .map(item -> ItemCardapioResponse.from(item, cardapioService))
                .toList();
        return ResponseEntity.ok(resposta);
    }

    private void validarPermissaoCardapio(String ruHeader) {
        Usuario usuario = ControllerUtils.resolveUser(ruHeader, usuarioRepository);
        ControllerUtils.requireAnyPermission(
                usuario,
                "Voce nao tem permissao para visualizar o cardapio do PDV.",
                Permissao.VER_VENDAS,
                Permissao.VER_ESTOQUE,
                Permissao.VER_FINANCAS
        );
    }

    public record CategoriaCardapioResponse(
            int id,
            String codigo,
            String nomeExibicao,
            int ordemExibicao,
            boolean ativo
    ) {
        public static CategoriaCardapioResponse from(CategoriaCardapio categoria) {
            return new CategoriaCardapioResponse(
                    categoria.getId(),
                    categoria.getCodigo(),
                    categoria.getNomeExibicao(),
                    categoria.getOrdemExibicao(),
                    categoria.isAtivo()
            );
        }
    }

    public record ItemCardapioResponse(
            int id,
            String codigo,
            String nome,
            String descricaoCurta,
            BigDecimal precoVenda,
            String tipoItem,
            boolean ativo,
            int ordemExibicao,
            int categoriaId,
            String categoriaCodigo,
            String categoriaNomeExibicao,
            int categoriaOrdemExibicao,
            Integer produtoVinculadoId,
            String nivelEstoque,
            boolean controladoPorEstoque,
            Integer quantidadeAtual,
            boolean disponivel
    ) {
        public static ItemCardapioResponse from(ItemCardapio item, CardapioService cardapioService) {
            return new ItemCardapioResponse(
                    item.getId(),
                    item.getCodigo(),
                    item.getNome(),
                    item.getDescricaoCurta(),
                    item.getPrecoVenda(),
                    item.getTipoItem().name(),
                    item.isAtivo(),
                    item.getOrdemExibicao(),
                    item.getCategoriaCardapio().getId(),
                    item.getCategoriaCardapio().getCodigo(),
                    item.getCategoriaCardapio().getNomeExibicao(),
                    item.getCategoriaCardapio().getOrdemExibicao(),
                    item.possuiProdutoVinculado() ? item.getProdutoVinculado().getId() : null,
                    cardapioService.nivelEstoque(item),
                    item.possuiProdutoVinculado() && item.getProdutoVinculado().isControladoPorEstoque(),
                    cardapioService.quantidadeDisponivel(item),
                    cardapioService.itemDisponivelAtual(item)
            );
        }
    }
}
