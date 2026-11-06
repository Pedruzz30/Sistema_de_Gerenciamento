package service;

import model.CategoriaCardapio;
import model.CategoriaEstoque;
import model.CodigoVenda;
import model.ItemCardapio;
import model.Permissao;
import model.PoliticaFalta;
import model.Produto;
import model.TipoComposicaoVariacao;
import model.TipoItemCardapio;
import model.UnidadeMedida;
import model.Usuario;
import model.VariacaoItemCardapio;
import org.springframework.transaction.annotation.Transactional;
import repository.CodigoVendaRepository;
import repository.ProdutoRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Cadastro rápido de produto de revenda (bebida, petisco industrializado).
 *
 * <p>Vender uma bebida exige Produto + Item de cardápio + Variações + códigos
 * de barras. Aqui tudo é criado de uma vez, numa única transação:
 * <ul>
 *   <li>com preço de gelada: variações "Gelada" (padrão) e "Natural", que
 *       baixam o mesmo estoque e compartilham o mesmo código de barras;</li>
 *   <li>sem preço de gelada: variação "Unidade";</li>
 *   <li>com fardo: variação "Fardo N" (baixa N unidades), embalagem para as
 *       compras e o código de barras do fardo.</li>
 * </ul>
 *
 * <p>O custo é obrigatório: o PDV recusa vender produto sem custo médio.
 */
public class ProdutoRevendaService {

    private static final String UNIDADE_BASE = "UNIDADE";
    private static final String CATEGORIA_PADRAO_CODIGO = "bebidas";
    private static final String CATEGORIA_PADRAO_NOME = "Bebidas";

    private final EstoqueService estoqueService;
    private final ProdutoEstoqueService produtoEstoqueService;
    private final CardapioService cardapioService;
    private final VariacaoItemCardapioService variacaoService;
    private final CodigoVendaService codigoVendaService;
    private final CodigoVendaRepository codigoVendaRepository;
    private final ProdutoRepository produtoRepository;
    private final UnidadeMedidaService unidadeMedidaService;

    public ProdutoRevendaService(EstoqueService estoqueService,
                                 ProdutoEstoqueService produtoEstoqueService,
                                 CardapioService cardapioService,
                                 VariacaoItemCardapioService variacaoService,
                                 CodigoVendaService codigoVendaService,
                                 CodigoVendaRepository codigoVendaRepository,
                                 ProdutoRepository produtoRepository,
                                 UnidadeMedidaService unidadeMedidaService) {
        this.estoqueService = estoqueService;
        this.produtoEstoqueService = produtoEstoqueService;
        this.cardapioService = cardapioService;
        this.variacaoService = variacaoService;
        this.codigoVendaService = codigoVendaService;
        this.codigoVendaRepository = codigoVendaRepository;
        this.produtoRepository = produtoRepository;
        this.unidadeMedidaService = unidadeMedidaService;
    }

    /**
     * @param precoVenda  preço da unidade (ou da natural, quando há gelada)
     * @param precoGelado opcional: preço da unidade gelada
     * @param custoUnitario custo de compra da unidade; alimenta o custo médio
     * @param fardo       opcional
     */
    public record Entrada(String nome,
                          String categoriaCardapioCodigo,
                          String categoriaCardapioNome,
                          CategoriaEstoque categoriaEstoque,
                          String codigoBarras,
                          BigDecimal precoVenda,
                          BigDecimal precoGelado,
                          BigDecimal custoUnitario,
                          int quantidadeInicial,
                          int quantidadeMinima,
                          Fardo fardo) {}

    public record Fardo(int quantidadeUnidades, BigDecimal preco, String codigoBarras) {}

    public record Resultado(Produto produto,
                            ItemCardapio item,
                            List<VariacaoItemCardapio> variacoes,
                            List<CodigoVenda> codigos) {}

    @Transactional
    public Resultado cadastrar(Usuario usuario, Entrada entrada) {
        validarPermissao(usuario);
        if (entrada == null) {
            throw new IllegalArgumentException("Dados do produto sao obrigatorios.");
        }
        String nome = entrada.nome() == null ? "" : entrada.nome().trim();
        validarDados(entrada, nome);
        String codigoUnidade = validarCodigoLivre(entrada.codigoBarras());
        String codigoFardo = entrada.fardo() == null ? "" : validarCodigoLivre(entrada.fardo().codigoBarras());
        if (!codigoUnidade.isEmpty() && codigoUnidade.equals(codigoFardo)) {
            throw new IllegalArgumentException("O codigo do fardo precisa ser diferente do codigo da unidade.");
        }
        if (produtoRepository.buscarPorNome(nome).isPresent()) {
            throw new ErroNegocioException("PRODUTO_DUPLICADO", "Ja existe um produto chamado " + nome + ".",
                    Map.of("nome", nome));
        }

        CategoriaCardapio categoria = resolverCategoria(entrada);
        UnidadeMedida unidade = unidadeMedidaService.buscarPorCodigoObrigatorio(UNIDADE_BASE);
        boolean temGelada = entrada.precoGelado() != null;
        BigDecimal precoPadrao = temGelada ? entrada.precoGelado() : entrada.precoVenda();

        Produto produto = estoqueService.cadastrarProduto(
                usuario,
                nome,
                0,
                entrada.quantidadeMinima(),
                entrada.precoVenda(),
                entrada.categoriaEstoque() == null ? CategoriaEstoque.BEBIDAS : entrada.categoriaEstoque(),
                unidade,
                PoliticaFalta.BLOQUEAR,
                Boolean.TRUE
        );
        int produtoId = produto.getId();
        if (entrada.quantidadeInicial() > 0) {
            estoqueService.registrarEntradaComCusto(
                    produtoId,
                    entrada.quantidadeInicial(),
                    entrada.custoUnitario(),
                    usuario,
                    "Estoque inicial no cadastro rapido",
                    "CADASTRO_PRODUTO",
                    produtoId
            );
        } else {
            estoqueService.definirCustoInicial(produto, entrada.custoUnitario());
        }

        ItemCardapio item = cardapioService.criarItem(
                "REV-" + produtoId,
                nome,
                null,
                precoPadrao,
                categoria.getId(),
                TipoItemCardapio.ESTOQUE_DIRETO,
                Boolean.TRUE,
                Boolean.TRUE,
                0,
                produtoId
        );

        List<VariacaoItemCardapio> variacoes = new ArrayList<>();
        List<CodigoVenda> codigos = new ArrayList<>();

        VariacaoItemCardapio principal;
        if (temGelada) {
            principal = criarVariacao(item, "Gelada", "GELADA", entrada.precoGelado(), produtoId, BigDecimal.ONE, true, 0, usuario);
            VariacaoItemCardapio natural = criarVariacao(item, "Natural", "NATURAL", entrada.precoVenda(), produtoId, BigDecimal.ONE, false, 1, usuario);
            variacoes.add(principal);
            variacoes.add(natural);
            if (!codigoUnidade.isEmpty()) {
                // Mesmo código de barras: a gelada é o padrão e a natural a alternativa (F4 no PDV).
                codigos.add(codigoVendaService.cadastrar(usuario, codigoUnidade, principal.getId(), Boolean.TRUE));
                codigos.add(codigoVendaService.cadastrar(usuario, codigoUnidade, natural.getId(), Boolean.FALSE));
            }
        } else {
            principal = criarVariacao(item, "Unidade", "UNIDADE", entrada.precoVenda(), produtoId, BigDecimal.ONE, true, 0, usuario);
            variacoes.add(principal);
            if (!codigoUnidade.isEmpty()) {
                codigos.add(codigoVendaService.cadastrar(usuario, codigoUnidade, principal.getId(), Boolean.TRUE));
            }
        }

        if (entrada.fardo() != null) {
            int unidades = entrada.fardo().quantidadeUnidades();
            BigDecimal quantidade = BigDecimal.valueOf(unidades);
            VariacaoItemCardapio fardo = criarVariacao(item, "Fardo " + unidades, "FARDO_" + unidades,
                    entrada.fardo().preco(), produtoId, quantidade, false, 2, usuario);
            variacoes.add(fardo);
            produtoEstoqueService.cadastrarEmbalagem(usuario, produtoId, "Fardo " + unidades, quantidade,
                    UNIDADE_BASE, codigoFardo.isEmpty() ? null : codigoFardo, Boolean.TRUE);
            if (!codigoFardo.isEmpty()) {
                codigos.add(codigoVendaService.cadastrar(usuario, codigoFardo, fardo.getId(), Boolean.TRUE));
            }
        }

        Produto atualizado = produtoRepository.buscarPorId(produtoId).orElse(produto);
        return new Resultado(atualizado, item, variacoes, codigos);
    }

    private VariacaoItemCardapio criarVariacao(ItemCardapio item,
                                               String nome,
                                               String codigo,
                                               BigDecimal preco,
                                               int produtoId,
                                               BigDecimal quantidadeBase,
                                               boolean padrao,
                                               int ordem,
                                               Usuario usuario) {
        return variacaoService.criar(item.getId(), nome, codigo, preco, TipoComposicaoVariacao.PRODUTO_DIRETO,
                produtoId, quantidadeBase, Boolean.TRUE, padrao, ordem, usuario);
    }

    private static void validarDados(Entrada entrada, String nome) {
        if (nome.isEmpty()) {
            throw new IllegalArgumentException("Nome do produto e obrigatorio.");
        }
        exigirPositivo(entrada.precoVenda(), "Preco de venda deve ser maior que zero.");
        if (entrada.precoGelado() != null) {
            exigirPositivo(entrada.precoGelado(), "Preco da gelada deve ser maior que zero.");
        }
        exigirPositivo(entrada.custoUnitario(), "Informe o custo da unidade: o PDV nao vende produto sem custo.");
        if (entrada.quantidadeInicial() < 0 || entrada.quantidadeMinima() < 0) {
            throw new IllegalArgumentException("Quantidades nao podem ser negativas.");
        }
        if (entrada.fardo() != null) {
            if (entrada.fardo().quantidadeUnidades() < 2) {
                throw new IllegalArgumentException("O fardo precisa ter pelo menos 2 unidades.");
            }
            exigirPositivo(entrada.fardo().preco(), "Preco do fardo deve ser maior que zero.");
        }
    }

    private static void exigirPositivo(BigDecimal valor, String mensagem) {
        if (valor == null || valor.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(mensagem);
        }
    }

    /** Código opcional: vazio é aceito; preenchido precisa ser válido e estar livre. */
    private String validarCodigoLivre(String informado) {
        String codigo = CodigoVendaService.normalizar(informado);
        if (codigo.isEmpty()) {
            return "";
        }
        CodigoVendaService.classificar(codigo);
        List<CodigoVenda> existentes = codigoVendaRepository.listarAtivosPorCodigo(codigo);
        if (!existentes.isEmpty()) {
            VariacaoItemCardapio variacao = existentes.get(0).getVariacao();
            String dono = variacao.getItemCardapio() == null ? variacao.getNome() : variacao.getItemCardapio().getNome();
            throw new ErroNegocioException("CODIGO_VENDA_DUPLICADO",
                    "O codigo " + codigo + " ja pertence a " + dono + ".",
                    Map.of("codigo", codigo));
        }
        return codigo;
    }

    private CategoriaCardapio resolverCategoria(Entrada entrada) {
        boolean semCodigo = entrada.categoriaCardapioCodigo() == null || entrada.categoriaCardapioCodigo().isBlank();
        String codigo = semCodigo ? CATEGORIA_PADRAO_CODIGO : entrada.categoriaCardapioCodigo().trim().toLowerCase(Locale.ROOT);
        return cardapioService.listarCategoriasAdministrativas().stream()
                .filter(categoria -> codigo.equalsIgnoreCase(categoria.getCodigo()))
                .findFirst()
                .orElseGet(() -> {
                    boolean semNome = entrada.categoriaCardapioNome() == null || entrada.categoriaCardapioNome().isBlank();
                    String nome = semNome ? CATEGORIA_PADRAO_NOME : entrada.categoriaCardapioNome().trim();
                    return cardapioService.criarCategoria(codigo, nome, 0, Boolean.TRUE);
                });
    }

    private static void validarPermissao(Usuario usuario) {
        if (usuario == null
                || usuario.getClasse() == null
                || !usuario.getClasse().possuiPermissao(Permissao.EDITAR_ESTOQUE)) {
            throw new SecurityException("Voce nao tem permissao para cadastrar produtos.");
        }
    }
}
