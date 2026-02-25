package service;

import model.NivelEstoque;
import model.Pedido;
import model.Produto;
import model.TipoMovimentacao;
import model.Usuario;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EstoqueService {

    private final List<Produto> produtos = new ArrayList<>();
    private final List<Pedido> pedidos = new ArrayList<>();
    private final Map<Integer, List<NivelEstoque>> historicoNiveis = new HashMap<>();
    private int proximoIdProduto = 1;
    private int proximoIdPedido = 1;

    public Produto cadastrarProduto(String nome, int quantidadeInicial, int quantidadeMinima, double preco) {
        if (nome == null || nome.trim().isEmpty()) {
            throw new IllegalArgumentException("Nome do produto é obrigatório.");
        }
        if (quantidadeInicial < 0 || quantidadeMinima < 0 || preco < 0) {
            throw new IllegalArgumentException("Quantidade e preço devem ser não negativos.");
        }

        Produto produto = new Produto(proximoIdProduto++, nome, quantidadeInicial, quantidadeMinima, preco);
        produtos.add(produto);
        registrarNivelEstoque(produto);
        return produto;
    }

    public Produto buscarProdutoPorId(int id) {
        for (Produto produto : produtos) {
            if (produto.getId() == id) {
                return produto;
            }
        }
        return null;
    }

    public Pedido registrarMovimentacao(int idProduto, int quantidade, TipoMovimentacao tipo, Usuario usuario) {
        Produto produto = buscarProdutoPorId(idProduto);
        if (produto == null) {
            System.out.println("Produto não encontrado.");
            return null;
        }

        if (quantidade <= 0) {
            throw new IllegalArgumentException("Quantidade deve ser maior que zero.");
        }

        if (tipo == TipoMovimentacao.SAIDA && produto.getQuantidadeAtual() < quantidade) {
            throw new IllegalArgumentException("Quantidade insuficiente em estoque para saída.");
        }

        if (tipo == TipoMovimentacao.ENTRADA) {
            produto.setQuantidadeAtual(produto.getQuantidadeAtual() + quantidade);
        } else {
            produto.setQuantidadeAtual(produto.getQuantidadeAtual() - quantidade);
        }

        Pedido pedido = new Pedido(proximoIdPedido++, produto, quantidade, tipo, usuario);
        pedidos.add(pedido);
        registrarNivelEstoque(produto);
        return pedido;
    }

    public List<Produto> listarProdutos() {
        return new ArrayList<>(produtos);
    }

    public List<Pedido> listarPedidos() {
        return new ArrayList<>(pedidos);
    }

    public NivelEstoque calcularNivelEstoque(Produto produto) {
        if (produto.getQuantidadeAtual() == 0) {
            return NivelEstoque.SEM_ESTOQUE;
        }

        if (produto.getQuantidadeMinima() <= 0) {
            return NivelEstoque.EXCELENTE;
        }

        double percentual = (produto.getQuantidadeAtual() * 100.0) / produto.getQuantidadeMinima();
        if (percentual <= 25) {
            return NivelEstoque.CRITICO;
        }
        if (percentual <= 50) {
            return NivelEstoque.BAIXO;
        }
        if (percentual <= 100) {
            return NivelEstoque.BOM;
        }
        return NivelEstoque.EXCELENTE;
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
