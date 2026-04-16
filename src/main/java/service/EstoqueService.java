package service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import model.NivelEstoque;
import model.Pedido;
import model.Permissao;
import model.CategoriaEstoque;
import model.Produto;
import model.TipoMovimentacao;
import model.Usuario;
import org.springframework.transaction.annotation.Transactional;
import repository.PedidoRepository;
import repository.ProdutoRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.text.Normalizer;

@Transactional(readOnly = true)
public class EstoqueService {

    private final ProdutoRepository produtoRepository;
    private final PedidoRepository pedidoRepository;
    private final Map<Integer, List<NivelEstoque>> historicoNiveis = new HashMap<>();

    public EstoqueService(ProdutoRepository produtoRepository, PedidoRepository pedidoRepository) {
        this.produtoRepository = produtoRepository;
        this.pedidoRepository = pedidoRepository;
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
        validarProdutoDeEstoque(nome);

        Produto produto = produtoRepository.salvar(
                new Produto(0, nome, quantidadeInicial, quantidadeMinima, preco, categoriaEstoque)
        );
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
        validarProdutoDeEstoque(nome);

        Produto produtoAtual = buscarProdutoPorId(id)
                .orElseThrow(() -> new IllegalArgumentException("Produto nao encontrado com ID: " + id));

        Produto produtoAtualizado = produtoRepository.salvar(
                new Produto(
                        produtoAtual.getId(),
                        nome,
                        produtoAtual.getQuantidadeAtual(),
                        quantidadeMinima,
                        preco,
                        categoriaEstoque
                )
        );
        return produtoAtualizado;
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
        return registrarMovimentacaoInterna(produto, quantidade, tipo, usuario, descricao);
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
                    descricaoOrigem
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

    @Transactional
    public int desativarProdutosPizzaSobDemanda(Usuario usuario) {
        validarPermissaoEdicaoEstoque(usuario);

        int totalAtualizados = 0;
        for (Produto produto : produtoRepository.listarTodos()) {
            if (!produto.isControladoPorEstoque()) {
                continue;
            }
            if (!ehPizzaSobDemanda(produto.getNome())) {
                continue;
            }

            produto.desativarControleDeEstoque();
            produtoRepository.salvar(produto);
            totalAtualizados++;
        }
        return totalAtualizados;
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
                                                String descricao) {
        if (produto == null) {
            throw new IllegalArgumentException("Produto nao encontrado.");
        }
        if (quantidade <= 0) {
            throw new IllegalArgumentException("Quantidade deve ser maior que zero.");
        }
        if (tipo == TipoMovimentacao.SAIDA && produto.getQuantidadeAtual() < quantidade) {
            throw new IllegalArgumentException("Quantidade insuficiente em estoque para saida.");
        }

        int saldoAnterior = produto.getQuantidadeAtual();
        int saldoPosterior = tipo.aplicarAoEstoque(saldoAnterior, quantidade);

        produto.atualizarQuantidade(saldoPosterior);
        produto = produtoRepository.salvar(produto);

        Pedido pedido = pedidoRepository.salvar(
                new Pedido(0, produto, quantidade, tipo, usuario, descricao, saldoAnterior, saldoPosterior)
        );
        registrarNivelEstoque(produto);
        return pedido;
    }

    private Produto buscarProdutoExistente(int idProduto) {
        Produto produto = produtoRepository.buscarPorId(idProduto)
                .orElseThrow(() -> new IllegalArgumentException("Produto nao encontrado com ID: " + idProduto));
        if (!produto.isControladoPorEstoque()) {
            throw new IllegalArgumentException("Produto nao e controlado por estoque.");
        }
        return produto;
    }

    private void validarProdutoDeEstoque(String nome) {
        if (ehPizzaSobDemanda(nome)) {
            throw new IllegalArgumentException(
                    "Pizzas do cardapio sao itens sob demanda e nao devem ser cadastradas como produto de estoque."
            );
        }
    }

    private boolean ehPizzaSobDemanda(String nome) {
        if (nome == null || nome.isBlank()) {
            return false;
        }

        String nomeNormalizado = Normalizer.normalize(nome, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase()
                .replaceAll("\\s+", " ")
                .trim();
        return nomeNormalizado.equals("pizza") || nomeNormalizado.startsWith("pizza ");
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
