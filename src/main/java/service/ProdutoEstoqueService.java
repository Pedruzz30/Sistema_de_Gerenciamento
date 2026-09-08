package service;

import model.EmbalagemProduto;
import model.EstoqueProduto;
import model.Permissao;
import model.Produto;
import model.UnidadeMedida;
import model.Usuario;
import org.springframework.transaction.annotation.Transactional;
import repository.EmbalagemProdutoRepository;
import repository.EstoqueProdutoRepository;
import repository.ProdutoRepository;

import java.math.BigDecimal;
import java.util.List;

@Transactional(readOnly = true)
public class ProdutoEstoqueService {

    public static final Long FILIAL_PADRAO_ID = 1L;

    private final ProdutoRepository produtoRepository;
    private final EmbalagemProdutoRepository embalagemProdutoRepository;
    private final EstoqueProdutoRepository estoqueProdutoRepository;
    private final UnidadeMedidaService unidadeMedidaService;

    public ProdutoEstoqueService(ProdutoRepository produtoRepository,
                                 EmbalagemProdutoRepository embalagemProdutoRepository,
                                 EstoqueProdutoRepository estoqueProdutoRepository,
                                 UnidadeMedidaService unidadeMedidaService) {
        this.produtoRepository = produtoRepository;
        this.embalagemProdutoRepository = embalagemProdutoRepository;
        this.estoqueProdutoRepository = estoqueProdutoRepository;
        this.unidadeMedidaService = unidadeMedidaService;
    }

    public List<EmbalagemProduto> listarEmbalagens(int produtoId) {
        buscarProdutoObrigatorio(produtoId);
        return embalagemProdutoRepository.listarPorProduto(produtoId);
    }

    @Transactional
    public EmbalagemProduto cadastrarEmbalagem(Usuario usuario,
                                               int produtoId,
                                               String nome,
                                               BigDecimal quantidadeBase,
                                               String unidadeBaseCodigo,
                                               String codigoBarras,
                                               Boolean ativa) {
        validarPermissaoEdicao(usuario);
        Produto produto = buscarProdutoObrigatorio(produtoId);
        UnidadeMedida unidadeBase = unidadeMedidaService.buscarPorCodigoObrigatorio(unidadeBaseCodigo);
        return embalagemProdutoRepository.salvar(
                new EmbalagemProduto(0, produto, nome, quantidadeBase, unidadeBase, codigoBarras, ativa)
        );
    }

    @Transactional
    public EstoqueProduto obterOuCriarEstoque(int produtoId) {
        Produto produto = buscarProdutoObrigatorio(produtoId);
        return obterOuCriarEstoque(produto);
    }

    @Transactional
    public EstoqueProduto obterOuCriarEstoque(Produto produto) {
        return estoqueProdutoRepository.buscarPorFilialEProduto(FILIAL_PADRAO_ID, produto.getId())
                .orElseGet(() -> estoqueProdutoRepository.salvar(
                        new EstoqueProduto(
                                0,
                                FILIAL_PADRAO_ID,
                                produto,
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                BigDecimal.ZERO
                        )
                ));
    }

    public Produto buscarProdutoObrigatorio(int produtoId) {
        return produtoRepository.buscarPorId(produtoId)
                .filter(Produto::isControladoPorEstoque)
                .orElseThrow(() -> new IllegalArgumentException("Produto nao encontrado com ID: " + produtoId));
    }

    private void validarPermissaoEdicao(Usuario usuario) {
        if (usuario == null
                || usuario.getClasse() == null
                || !usuario.getClasse().possuiPermissao(Permissao.EDITAR_ESTOQUE)) {
            throw new SecurityException("Voce nao tem permissao para editar estoque.");
        }
    }
}
