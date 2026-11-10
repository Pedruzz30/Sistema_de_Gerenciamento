
package controller;

import model.CategoriaCardapio;
import model.ItemCardapio;
import model.Permissao;
import model.Produto;

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

/**
 * API de consulta do cardapio do StockOS.
 *
 * Responsabilidades:
 * - Listar categorias ativas;
 * - Consultar itens ativos;
 * - Filtrar itens por categoria e busca;
 * - Retornar informacoes de preco e estoque;
 * - Verificar permissoes de visualizacao.
 *
 * Este controller nao realiza alteracoes no cardapio.
 *
 * IMPORTANTE:
 * A autenticacao X-User-RU e legada.
 * A identidade e o estabelecimento devem ser
 * verificados pelo backend antes de liberar
 * esta API para uso comercial.
 */
@RestController
@RequestMapping("/api/cardapio")
public class CardapioController {

    private static final String MENSAGEM_ACESSO_NEGADO =
            "Voce nao tem permissao para visualizar o cardapio do PDV.";

    private final CardapioService cardapioService;
    private final UsuarioRepository usuarioRepository;

    /**
     * Injecao de dependencias pelo construtor.
     */
    public CardapioController(
            CardapioService cardapioService,
            UsuarioRepository usuarioRepository
    ) {
        this.cardapioService = cardapioService;
        this.usuarioRepository = usuarioRepository;
    }

    // =========================================================
    // CATEGORIAS
    // =========================================================

    /**
     * Lista apenas categorias ativas.
     *
     * GET /api/cardapio/categorias
     */
    @GetMapping("/categorias")
    public ResponseEntity<List<CategoriaCardapioResponse>>
    listarCategorias(
            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        validarPermissaoCardapio(ruHeader);

        List<CategoriaCardapioResponse> categorias =
                cardapioService.listarCategoriasAtivas()
                        .stream()
                        .map(CategoriaCardapioResponse::from)
                        .toList();

        return ResponseEntity.ok(categorias);
    }

    // =========================================================
    // ITENS
    // =========================================================

    /**
     * Lista itens ativos do cardapio.
     *
     * Filtros opcionais:
     * - categoria: codigo da categoria;
     * - busca: texto para pesquisa.
     *
     * GET /api/cardapio/itens
     */
    @GetMapping("/itens")
    public ResponseEntity<List<ItemCardapioResponse>>
    listarItens(
            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader,

            @RequestParam(
                    value = "categoria",
                    required = false
            ) String categoria,

            @RequestParam(
                    value = "busca",
                    required = false
            ) String busca
    ) {
        validarPermissaoCardapio(ruHeader);

        List<ItemCardapioResponse> itens =
                cardapioService
                        .listarItensAtivos(categoria, busca)
                        .stream()
                        .map(this::converterItemParaResponse)
                        .toList();

        return ResponseEntity.ok(itens);
    }

    // =========================================================
    // CONVERSAO DE RESPOSTAS
    // =========================================================

    /**
     * Centraliza a conversao de um item para o DTO
     * utilizado pelo frontend.
     */
    private ItemCardapioResponse converterItemParaResponse(
            ItemCardapio item
    ) {
        return ItemCardapioResponse.from(
                item,
                cardapioService
        );
    }

    // =========================================================
    // AUTORIZACAO LEGADA
    // =========================================================

    /**
     * Verifica se o usuario possui alguma das permissoes
     * necessarias para visualizar o cardapio.
     *
     * ATENCAO:
     * A verificacao do RU nao comprova a identidade.
     * Este mecanismo deve ser substituido por
     * autenticacao verificada.
     */
    private void validarPermissaoCardapio(
            String ruHeader
    ) {
        ControllerUtils.resolveUserAndRequireAnyPermission(
                ruHeader,
                usuarioRepository,
                MENSAGEM_ACESSO_NEGADO,
                Permissao.VER_VENDAS,
                Permissao.VER_ESTOQUE,
                Permissao.VER_FINANCAS
        );
    }

    // =========================================================
    // RESPONSE DTO - CATEGORIA
    // =========================================================

    /**
     * Dados de uma categoria apresentados ao PDV.
     */
    public record CategoriaCardapioResponse(
            int id,
            String codigo,
            String nomeExibicao,
            int ordemExibicao,
            boolean ativo
    ) {

        public static CategoriaCardapioResponse from(
                CategoriaCardapio categoria
        ) {
            return new CategoriaCardapioResponse(
                    categoria.getId(),
                    categoria.getCodigo(),
                    categoria.getNomeExibicao(),
                    categoria.getOrdemExibicao(),
                    categoria.isAtivo()
            );
        }
    }

    // =========================================================
    // RESPONSE DTO - ITEM
    // =========================================================

    /**
     * Dados de um item apresentados ao PDV.
     *
     * Os campos de estoque sao calculados pelo
     * CardapioService.
     *
     * As permissoes retornadas por outras APIs
     * nao substituem autorizacao no backend.
     */
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

        /**
         * Converte a entidade de dominio em uma
         * resposta segura para serializacao JSON.
         *
         * Mantem o contrato utilizado pelo frontend.
         */
        public static ItemCardapioResponse from(
                ItemCardapio item,
                CardapioService cardapioService
        ) {
            CategoriaCardapio categoria =
                    item.getCategoriaCardapio();

            Produto produto = item.getProdutoVinculado();

            Integer produtoVinculadoId =
                    produto != null ? produto.getId() : null;

            boolean controladoPorEstoque =
                    produto != null
                            && produto.isControladoPorEstoque();

            String nivelEstoque =
                    cardapioService.nivelEstoque(item);

            Integer quantidadeAtual =
                    cardapioService.quantidadeDisponivel(item);

            boolean disponivel =
                    cardapioService.itemDisponivelAtual(item);

            return new ItemCardapioResponse(
                    item.getId(),
                    item.getCodigo(),
                    item.getNome(),
                    item.getDescricaoCurta(),
                    item.getPrecoVenda(),
                    item.getTipoItem().name(),
                    item.isAtivo(),
                    item.getOrdemExibicao(),
                    categoria.getId(),
                    categoria.getCodigo(),
                    categoria.getNomeExibicao(),
                    categoria.getOrdemExibicao(),
                    produtoVinculadoId,
                    nivelEstoque,
                    controladoPorEstoque,
                    quantidadeAtual,
                    disponivel
            );
        }
    }
}

