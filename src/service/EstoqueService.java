package service;

import model.NivelEstoque;
import model.Pedido;
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

    public Produto cadastrarProduto(String nome, int quantidadeInicial, int quantidadeMinima, double preco) {
        if (nome == null || nome.trim().isEmpty()) {
            throw new IllegalArgumentException("Nome do produto é obrigatório.");
        }
        if (quantidadeInicial < 0 || quantidadeMinima < 0 || preco < 0) {
            throw new IllegalArgumentException("Quantidade e preço devem ser não negativos.");
        }

        Produto produto = produtoRepository.salvar(new Produto(0, nome, quantidadeInicial, quantidadeMinima, preco));
        registrarNivelEstoque(produto);
        return produto;
    }

    public Optional<Produto> buscarProdutoPorId(int id) {
        return produtoRepository.buscarPorId(id);
    }

    public Pedido registrarMovimentacao(int idProduto, int quantidade, TipoMovimentacao tipo, Usuario usuario) {
        Optional<Produto> optProduto = buscarProdutoPorId(idProduto);
        if (optProduto.isEmpty()) {
            System.out.println("Produto não encontrado.");
            return null;
        }
        Produto produto = optProduto.get();

        if (quantidade <= 0) {
            throw new IllegalArgumentException("Quantidade deve ser maior que zero.");
        }

        if (tipo == TipoMovimentacao.SAIDA && produto.getQuantidadeAtual() < quantidade) {
            throw new IllegalArgumentException("Quantidade insuficiente em estoque para saída.");
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
            System.out.println("Sem histórico de níveis para o produto informado.");
            return;
        }

        System.out.println("Histórico de níveis do produto " + idProduto + ":");
        for (NivelEstoque nivel : historico) {
            System.out.println("- " + nivel + " | " + nivel.getMessage());
        }
    }

    private void registrarNivelEstoque(Produto produto) {
        NivelEstoque nivel = calcularNivelEstoque(produto);
        historicoNiveis.computeIfAbsent(produto.getId(), ignored -> new ArrayList<>()).add(nivel);
    }
}
