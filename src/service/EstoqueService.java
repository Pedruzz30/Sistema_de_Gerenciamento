package service;

import model.Pedido;
import model.Produto;
import model.Usuario;

import java.util.ArrayList;
import java.util.List;

public class EstoqueService {

    private List<Produto> produtos = new ArrayList<>();
    private List<Pedido> pedidos = new ArrayList<>();
    private int proximoIdProduto = 1;
    private int proximoIdPedido = 1;

    // Cadastrar novo produto
    public Produto cadastrarProduto(String nome, int quantidadeInicial, int quantidadeMinima, double preco) {
        Produto produto = new Produto(proximoIdProduto++, nome, quantidadeInicial, quantidadeMinima, preco);
        produtos.add(produto);
        return produto;
    }

    // Buscar produto por ID
    public Produto buscarProdutoPorId(int id) {
        for (Produto p : produtos) {
            if (p.getId() == id) {
                return p;
            }
        }
        return null;
    }

    // Registrar movimentação (entrada ou saída)
    public Pedido registrarMovimentacao(Produto produto, int quantidade, Pedido.Tipo tipo, Usuario usuario) {
        if (tipo == Pedido.Tipo.SAIDA && produto.getQuantidadeAtual() < quantidade) {
            System.out.println("Quantidade insuficiente em estoque para saída.");
            return null;
        }

        if (tipo == Pedido.Tipo.ENTRADA) {
            produto.setQuantidadeAtual(produto.getQuantidadeAtual() + quantidade);
        } else {
            produto.setQuantidadeAtual(produto.getQuantidadeAtual() - quantidade);
        }

        Pedido pedido = new Pedido(proximoIdPedido++, produto, quantidade, tipo, usuario);
        pedidos.add(pedido);

        verificarAvisos(produto);

        return pedido;
    }

    // Listar produtos
    public void listarProdutos() {
        if (produtos.isEmpty()) {
            System.out.println("Nenhum produto cadastrado.");
            return;
        }
        for (Produto p : produtos) {
            System.out.println(p);
        }
    }

    // Expor lista de pedidos (se quiser usar no menu depois)
    public List<Pedido> listarPedidos() {
        return pedidos;
    }

    // Avisos de nível de estoque
    private void verificarAvisos(Produto produto) {
        double percentual = (produto.getQuantidadeAtual() * 100.0) / Math.max(produto.getQuantidadeMinima(), 1);

        if (produto.getQuantidadeAtual() == 0) {
            System.out.println("⚠ Produto " + produto.getNome() + " está FORA DE ESTOQUE (0%).");
        } else if (percentual <= 25) {
            System.out.println("⚠ Estoque crítico de " + produto.getNome() + " (<= 25%).");
        } else if (percentual <= 50) {
            System.out.println("Atenção: estoque baixo de " + produto.getNome() + " (<= 50%).");
        } else if (percentual <= 75) {
            System.out.println("Aviso: estoque de " + produto.getNome() + " está em 75% ou menos.");
        }
    }
}
