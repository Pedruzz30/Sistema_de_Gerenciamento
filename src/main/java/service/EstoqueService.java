package service;

import model.NivelEstoque;
import model.Pedido;
import model.Permissao;
import model.CategoriaEstoque;
import model.Produto;
import model.TipoMovimentacao;
import model.Usuario;
import repository.PedidoRepository;
import repository.ProdutoRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class EstoqueService {

    private final ProdutoRepository produtoRepository;
    private final PedidoRepository pedidoRepository;
    private final Map<Integer, List<NivelEstoque>> historicoNiveis = new HashMap<>();

    public EstoqueService(ProdutoRepository produtoRepository, PedidoRepository pedidoRepository) {
        this.produtoRepository = produtoRepository;
        this.pedidoRepository = pedidoRepository;
    }

    public Produto cadastrarProduto(Usuario usuario, String nome, int quantidadeInicial, int quantidadeMinima, double preco) {
        return cadastrarProduto(usuario, nome, quantidadeInicial, quantidadeMinima, preco, null);
    }

    public Produto cadastrarProduto(Usuario usuario, String nome, int quantidadeInicial, int quantidadeMinima,
                                    double preco, CategoriaEstoque categoriaEstoque) {
        validarPermissaoEdicaoEstoque(usuario);

        if (nome == null || nome.trim().isEmpty()) {
            throw new IllegalArgumentException("Nome do produto e obrigatorio.");
        }
        if (quantidadeInicial < 0 || quantidadeMinima < 0 || preco < 0) {
            throw new IllegalArgumentException("Quantidade e preco devem ser nao negativos.");
        }

        Produto produto = produtoRepository.salvar(
                new Produto(0, nome, quantidadeInicial, quantidadeMinima, preco, categoriaEstoque)
        );
        registrarNivelEstoque(produto);
        return produto;
    }

    public Produto atualizarProduto(Usuario usuario, int id, String nome, int quantidadeMinima, double preco,
                                    CategoriaEstoque categoriaEstoque) {
        validarPermissaoEdicaoEstoque(usuario);

        if (nome == null || nome.trim().isEmpty()) {
            throw new IllegalArgumentException("Nome do produto e obrigatorio.");
        }
        if (quantidadeMinima < 0 || preco < 0) {
            throw new IllegalArgumentException("Quantidade minima e preco devem ser nao negativos.");
        }

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
        return produtoRepository.buscarPorId(id);
    }

    public Pedido registrarMovimentacao(int idProduto, int quantidade, TipoMovimentacao tipo, Usuario usuario) {
        validarPermissaoEdicaoEstoque(usuario);

        Optional<Produto> optProduto = buscarProdutoPorId(idProduto);
        if (optProduto.isEmpty()) {
            throw new IllegalArgumentException("Produto nao encontrado com ID: " + idProduto);
        }
        Produto produto = optProduto.get();

        if (quantidade <= 0) {
            throw new IllegalArgumentException("Quantidade deve ser maior que zero.");
        }

        if (tipo == TipoMovimentacao.SAIDA && produto.getQuantidadeAtual() < quantidade) {
            throw new IllegalArgumentException("Quantidade insuficiente em estoque para saida.");
        }

        produto.atualizarQuantidade(tipo.applyTo(produto.getQuantidadeAtual(), quantidade));

        Pedido pedido = pedidoRepository.salvar(new Pedido(0, produto, quantidade, tipo, usuario));
        registrarNivelEstoque(produto);
        return pedido;
    }

    public List<Produto> listarProdutos() {
        return produtoRepository.listarTodos();
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
            System.out.println("- " + nivel + " | " + nivel.getMessage());
        }
    }

    private void registrarNivelEstoque(Produto produto) {
        NivelEstoque nivel = calcularNivelEstoque(produto);
        historicoNiveis.computeIfAbsent(produto.getId(), ignored -> new ArrayList<>()).add(nivel);
    }

    private void validarPermissaoEdicaoEstoque(Usuario usuario) {
        if (usuario == null
                || usuario.getClasse() == null
                || !usuario.getClasse().possuiPermissao(Permissao.EDITAR_ESTOQUE)) {
            throw new SecurityException("Voce nao tem permissao para editar estoque.");
        }
    }
}
