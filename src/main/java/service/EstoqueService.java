package service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import model.NivelEstoque;
import model.Pedido;
import model.Permissao;
import model.CategoriaEstoque;
import model.PoliticaFalta;
import model.Produto;
import model.TipoMovimentacao;
import model.UnidadeMedida;
import model.Usuario;
import org.springframework.transaction.annotation.Transactional;
import repository.PedidoRepository;
import repository.ProdutoRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Transactional(readOnly = true)
public class EstoqueService {

    private final ProdutoRepository produtoRepository;
    private final PedidoRepository pedidoRepository;
    private final Map<Integer, List<NivelEstoque>> historicoNiveis = new HashMap<>();

    public static final String ORIGEM_MOVIMENTACAO_MANUAL = "MOVIMENTACAO_MANUAL";
    public static final String ORIGEM_NOTA_FISCAL = "NOTA_FISCAL";
    public static final String ORIGEM_VENDA_CAIXA = "VENDA_CAIXA";

    /**
     * Saldo único: quando presente, todo movimento passa primeiro pelo saldo
     * decimal ({@code estoques_produto}) e a coluna legada vira espelho.
     * Nulo só no modo console legado e em testes antigos.
     */
    private final SincronizadorEstoque sincronizador;

    public EstoqueService(ProdutoRepository produtoRepository, PedidoRepository pedidoRepository) {
        this(produtoRepository, pedidoRepository, null);
    }

    public EstoqueService(ProdutoRepository produtoRepository,
                          PedidoRepository pedidoRepository,
                          SincronizadorEstoque sincronizador) {
        this.produtoRepository = produtoRepository;
        this.pedidoRepository = pedidoRepository;
        this.sincronizador = sincronizador;
    }

    public boolean usaSaldoUnico() {
        return sincronizador != null;
    }

    public Produto cadastrarProduto(Usuario usuario, String nome, int quantidadeInicial, int quantidadeMinima, double preco) {
        return cadastrarProduto(usuario, nome, quantidadeInicial, quantidadeMinima, BigDecimal.valueOf(preco), null);
    }

    public Produto cadastrarProduto(Usuario usuario, String nome, int quantidadeInicial, int quantidadeMinima,
                                    BigDecimal preco) {
        return cadastrarProduto(usuario, nome, quantidadeInicial, quantidadeMinima, preco, null);
    }

    public Produto cadastrarProduto(Usuario usuario, String nome, int quantidadeInicial, int quantidadeMinima,
                                    double preco, CategoriaEstoque categoriaEstoque) {
        return cadastrarProduto(usuario, nome, quantidadeInicial, quantidadeMinima, BigDecimal.valueOf(preco), categoriaEstoque);
    }

    @Transactional
    public Produto cadastrarProduto(Usuario usuario, String nome, int quantidadeInicial, int quantidadeMinima,
                                    BigDecimal preco, CategoriaEstoque categoriaEstoque) {
        return cadastrarProduto(
                usuario,
                nome,
                quantidadeInicial,
                quantidadeMinima,
                preco,
                categoriaEstoque,
                null,
                PoliticaFalta.BLOQUEAR,
                Boolean.TRUE
        );
    }

    @Transactional
    public Produto cadastrarProduto(Usuario usuario,
                                    String nome,
                                    int quantidadeInicial,
                                    int quantidadeMinima,
                                    BigDecimal preco,
                                    CategoriaEstoque categoriaEstoque,
                                    UnidadeMedida unidadeBase,
                                    PoliticaFalta politicaFalta,
                                    Boolean ativo) {
        validarPermissaoEdicaoEstoque(usuario);

        if (nome == null || nome.trim().isEmpty()) {
            throw new IllegalArgumentException("Nome do produto e obrigatorio.");
        }
        if (quantidadeInicial < 0 || quantidadeMinima < 0) {
            throw new IllegalArgumentException("Quantidade e preco devem ser nao negativos.");
        }
        if (preco == null || preco.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Quantidade e preco devem ser nao negativos.");
        }

        Produto produto = produtoRepository.salvar(
                new Produto(
                        0,
                        nome,
                        sincronizador == null ? quantidadeInicial : 0,
                        quantidadeMinima,
                        preco,
                        categoriaEstoque,
                        Boolean.TRUE,
                        unidadeBase,
                        politicaFalta,
                        ativo
                )
        );
        if (sincronizador != null && quantidadeInicial > 0) {
            produto = sincronizador.aplicarMovimento(
                    produto,
                    model.TipoMovimentoEstoque.AJUSTE_POSITIVO,
                    BigDecimal.valueOf(quantidadeInicial),
                    null,
                    "CADASTRO_PRODUTO",
                    produto.getId(),
                    usuario.getRu(),
                    "Estoque inicial no cadastro do produto"
            ).produtoEspelhado();
        }
        registrarNivelEstoque(produto);
        return produto;
    }

    public Produto atualizarProduto(Usuario usuario, int id, String nome, int quantidadeMinima, double preco,
                                    CategoriaEstoque categoriaEstoque) {
        return atualizarProduto(usuario, id, nome, quantidadeMinima, BigDecimal.valueOf(preco), categoriaEstoque);
    }

    @Transactional
    public Produto atualizarProduto(Usuario usuario, int id, String nome, int quantidadeMinima, BigDecimal preco,
                                    CategoriaEstoque categoriaEstoque) {
        Produto produtoAtual = buscarProdutoPorId(id)
                .orElseThrow(() -> new IllegalArgumentException("Produto nao encontrado com ID: " + id));
        return atualizarProduto(
                usuario,
                id,
                nome,
                quantidadeMinima,
                preco,
                categoriaEstoque,
                produtoAtual.getUnidadeBase(),
                produtoAtual.getPoliticaFalta(),
                produtoAtual.isAtivo()
        );
    }

    @Transactional
    public Produto atualizarProduto(Usuario usuario,
                                    int id,
                                    String nome,
                                    int quantidadeMinima,
                                    BigDecimal preco,
                                    CategoriaEstoque categoriaEstoque,
                                    UnidadeMedida unidadeBase,
                                    PoliticaFalta politicaFalta,
                                    Boolean ativo) {
        validarPermissaoEdicaoEstoque(usuario);

        if (nome == null || nome.trim().isEmpty()) {
            throw new IllegalArgumentException("Nome do produto e obrigatorio.");
        }
        if (quantidadeMinima < 0) {
            throw new IllegalArgumentException("Quantidade minima e preco devem ser nao negativos.");
        }
        if (preco == null || preco.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Quantidade minima e preco devem ser nao negativos.");
        }

        Produto produtoAtual = buscarProdutoPorId(id)
                .orElseThrow(() -> new IllegalArgumentException("Produto nao encontrado com ID: " + id));
        UnidadeMedida unidadeFinal = unidadeBase != null ? unidadeBase : produtoAtual.getUnidadeBase();
        PoliticaFalta politicaFinal = politicaFalta != null ? politicaFalta : produtoAtual.getPoliticaFalta();
        Boolean ativoFinal = ativo != null ? ativo : produtoAtual.isAtivo();

        Produto alterado = new Produto(
                produtoAtual.getId(),
                nome,
                produtoAtual.getQuantidadeAtual(),
                quantidadeMinima,
                preco,
                categoriaEstoque,
                produtoAtual.isControladoPorEstoque(),
                unidadeFinal,
                politicaFinal,
                ativoFinal
        );
        // Editar o produto não pode apagar NCM e CEST.
        alterado.preencherDadosFiscais(produtoAtual.getNcm(), produtoAtual.getCest());
        return produtoRepository.salvar(alterado);
    }

    public Optional<Produto> buscarProdutoPorId(int id) {
        return produtoRepository.buscarPorId(id)
                .filter(Produto::isControladoPorEstoque);
    }

    public Optional<Produto> buscarProdutoPorNome(String nome) {
        if (nome == null || nome.trim().isEmpty()) {
            return Optional.empty();
        }
        return produtoRepository.buscarPorNome(nome.trim())
                .filter(Produto::isControladoPorEstoque);
    }

    public Pedido registrarMovimentacao(int idProduto, int quantidade, TipoMovimentacao tipo, Usuario usuario) {
        return registrarMovimentacao(idProduto, quantidade, tipo, usuario, null);
    }

    @Transactional
    public Pedido registrarMovimentacao(int idProduto,
                                        int quantidade,
                                        TipoMovimentacao tipo,
                                        Usuario usuario,
                                        String descricao) {
        validarPermissaoEdicaoEstoque(usuario);
        Produto produto = buscarProdutoExistente(idProduto);
        return registrarMovimentacaoInterna(produto, quantidade, tipo, usuario, descricao,
                null, ORIGEM_MOVIMENTACAO_MANUAL, null);
    }

    /**
     * Entrada com custo e origem conhecidos (ex.: confirmação de nota fiscal).
     * O custo alimenta o custo médio do saldo oficial, que o PDV exige para vender.
     */
    @Transactional
    public Pedido registrarEntradaComCusto(int idProduto,
                                           int quantidade,
                                           BigDecimal custoUnitario,
                                           Usuario usuario,
                                           String descricao,
                                           String origemTipo,
                                           long origemId) {
        validarPermissaoEdicaoEstoque(usuario);
        Produto produto = buscarProdutoExistente(idProduto);
        return registrarMovimentacaoInterna(produto, quantidade, TipoMovimentacao.ENTRADA, usuario, descricao,
                custoUnitario, origemTipo, origemId);
    }

    /** Custo do produto sem saldo inicial (sem efeito no modo console legado). */
    @Transactional
    public void definirCustoInicial(Produto produto, BigDecimal custoUnitario) {
        if (sincronizador != null) {
            sincronizador.definirCustoInicial(produto, custoUnitario);
        }
    }

    /** Saldo oficial disponível do produto (decimal quando o saldo único está ativo). */
    public BigDecimal saldoDisponivel(Produto produto) {
        if (sincronizador == null) {
            return BigDecimal.valueOf(produto.getQuantidadeAtual());
        }
        return sincronizador.saldoDisponivel(produto);
    }

    @Transactional
    public BigDecimal registrarSaidasPorVenda(Usuario usuario,
                                              List<VendaItemInput> itensVendidos,
                                              String descricaoOrigem) {
        validarPermissaoSaidaPorVenda(usuario);
        if (itensVendidos == null || itensVendidos.isEmpty()) {
            throw new IllegalArgumentException("A venda deve informar ao menos um item.");
        }

        BigDecimal total = BigDecimal.ZERO;
        for (VendaItemInput item : itensVendidos) {
            if (item == null || !item.possuiProdutoEmEstoque() || item.quantidade() <= 0) {
                throw new IllegalArgumentException("Itens da venda devem conter produtoId e quantidade positivos.");
            }

            Produto produto = buscarProdutoExistente(item.produtoId());
            registrarMovimentacaoInterna(
                    produto,
                    item.quantidade(),
                    TipoMovimentacao.SAIDA,
                    usuario,
                    descricaoOrigem,
                    null,
                    ORIGEM_VENDA_CAIXA,
                    null
            );
            total = total.add(
                    produto.getPrecoUnitario().multiply(BigDecimal.valueOf(item.quantidade()))
            );
        }

        return total.setScale(2, RoundingMode.HALF_UP);
    }

    public List<Produto> listarProdutos() {
        return produtoRepository.listarTodos().stream()
                .filter(Produto::isControladoPorEstoque)
                .toList();
    }

    public Map<Integer,Integer> quantidadesVendidasPorProduto() {
        return pedidoRepository.quantidadesVendidasPorProduto();
    }
    public List<Pedido> listarPedidosRecentes(int limite) {
        return pedidoRepository.listarRecentes(limite);
    }

    public List<Pedido> listarPedidos() {
        return pedidoRepository.listarTodos();
    }

    public NivelEstoque calcularNivelEstoque(Produto produto) {
        return NivelEstoque.fromPercentage(produto.calcularPercentualEstoque());
    }

    public void exibirHistoricoNiveis(int idProduto) {
        List<NivelEstoque> historico = historicoNiveis.get(idProduto);
        if (historico == null || historico.isEmpty()) {
            System.out.println("Sem historico de niveis para o produto informado.");
            return;
        }

        System.out.println("Historico de niveis do produto " + idProduto + ":");
        for (NivelEstoque nivel : historico) {
            System.out.println("- " + nivel + " | " + nivel.gerarMensagem());
        }
    }

    private void registrarNivelEstoque(Produto produto) {
        NivelEstoque nivel = calcularNivelEstoque(produto);
        historicoNiveis.computeIfAbsent(produto.getId(), ignored -> new ArrayList<>()).add(nivel);
    }

    private Pedido registrarMovimentacaoInterna(Produto produto,
                                                int quantidade,
                                                TipoMovimentacao tipo,
                                                Usuario usuario,
                                                String descricao,
                                                BigDecimal custoUnitarioEntrada,
                                                String origemTipo,
                                                Long origemId) {
        if (produto == null) {
            throw new IllegalArgumentException("Produto nao encontrado.");
        }
        if (quantidade <= 0) {
            throw new IllegalArgumentException("Quantidade deve ser maior que zero.");
        }
        if (tipo == TipoMovimentacao.SAIDA
                && saldoDisponivel(produto).compareTo(BigDecimal.valueOf(quantidade)) < 0) {
            throw new IllegalArgumentException("Quantidade insuficiente em estoque para saida.");
        }

        int saldoAnterior = produto.getQuantidadeAtual();
        int saldoPosterior;

        if (sincronizador != null) {
            // Saldo oficial (decimal) primeiro; a coluna legada recebe o espelho.
            produto = sincronizador.aplicarMovimento(
                    produto,
                    tipoDecimal(tipo, origemTipo),
                    BigDecimal.valueOf(quantidade),
                    custoUnitarioEntrada,
                    origemTipo,
                    origemId != null && origemId > 0 ? origemId : produto.getId(),
                    usuario == null ? null : usuario.getRu(),
                    descricao
            ).produtoEspelhado();
            saldoPosterior = produto.getQuantidadeAtual();
        } else {
            saldoPosterior = tipo.aplicarAoEstoque(saldoAnterior, quantidade);
            produto.atualizarQuantidade(saldoPosterior);
            produto = produtoRepository.salvar(produto);
        }

        Pedido pedido = pedidoRepository.salvar(
                new Pedido(0, produto, quantidade, tipo, usuario, descricao, saldoAnterior, saldoPosterior)
        );
        registrarNivelEstoque(produto);
        return pedido;
    }

    private static model.TipoMovimentoEstoque tipoDecimal(TipoMovimentacao tipo, String origemTipo) {
        if (tipo == TipoMovimentacao.ENTRADA) {
            return ORIGEM_NOTA_FISCAL.equals(origemTipo)
                    ? model.TipoMovimentoEstoque.ENTRADA_COMPRA
                    : model.TipoMovimentoEstoque.AJUSTE_POSITIVO;
        }
        return ORIGEM_VENDA_CAIXA.equals(origemTipo)
                ? model.TipoMovimentoEstoque.SAIDA_VENDA
                : model.TipoMovimentoEstoque.AJUSTE_NEGATIVO;
    }

    private Produto buscarProdutoExistente(int idProduto) {
        Produto produto = produtoRepository.buscarPorId(idProduto)
                .orElseThrow(() -> new IllegalArgumentException("Produto nao encontrado com ID: " + idProduto));
        if (!produto.isControladoPorEstoque()) {
            throw new IllegalArgumentException("Produto nao e controlado por estoque.");
        }
        return produto;
    }

    private void validarPermissaoEdicaoEstoque(Usuario usuario) {
        if (usuario == null
                || usuario.getClasse() == null
                || !usuario.getClasse().possuiPermissao(Permissao.EDITAR_ESTOQUE)) {
            throw new SecurityException("Voce nao tem permissao para editar estoque.");
        }
    }

    private void validarPermissaoSaidaPorVenda(Usuario usuario) {
        if (usuario == null || usuario.getClasse() == null) {
            throw new SecurityException("Voce nao tem permissao para registrar saidas por venda.");
        }

        boolean podeEditarEstoque = usuario.getClasse().possuiPermissao(Permissao.EDITAR_ESTOQUE);
        boolean podeOperarVendas = usuario.getClasse().possuiPermissao(Permissao.VER_VENDAS);
        if (!podeEditarEstoque && !podeOperarVendas) {
            throw new SecurityException("Voce nao tem permissao para registrar saidas por venda.");
        }
    }
}
