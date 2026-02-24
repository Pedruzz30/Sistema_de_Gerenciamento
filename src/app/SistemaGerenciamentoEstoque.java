package app;

import model.Pedido;
import model.Produto;
import model.TipoMovimentacao;
import model.Usuario;
import service.EstoqueService;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class SistemaGerenciamentoEstoque {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        EstoqueService estoque = new EstoqueService();

        // Usuários “fake” só para testes
        List<Usuario> usuarios = criarUsuariosPadrao();

        System.out.println("=== LOGIN ===");
        System.out.print("Login: ");
        String login = scanner.nextLine();

        System.out.print("Senha: ");
        String senha = scanner.nextLine();

        Usuario admin = autenticar(usuarios, login, senha);

        if (admin == null) {
            System.out.println("Credenciais inválidas. Encerrando...");
            return;
        }

        System.out.println("Bem-vindo, " + admin.getNome() + "! Perfil: " + admin.getPerfil());

        int opcao;

        do {
            System.out.println("\n=== SISTEMA DE GERENCIAMENTO DE ESTOQUE ===");
            System.out.println("1 - Cadastrar produto");
            System.out.println("2 - Listar produtos");
            System.out.println("3 - Registrar entrada");
            System.out.println("4 - Registrar saída");
            System.out.println("5 - Listar movimentações");
            System.out.println("6 - Histórico de níveis de um produto");
            System.out.println("0 - Sair");
            System.out.print("Opção: ");

            while (!scanner.hasNextInt()) {
                System.out.println("Digite um número válido.");
                scanner.nextLine();
            }
            opcao = scanner.nextInt();
            scanner.nextLine(); // consumir quebra de linha

            switch (opcao) {
                case 1 -> cadastrarProduto(scanner, estoque);
                case 2 -> listarProdutos(estoque);
                case 3 -> registrarMovimentacao(scanner, estoque, admin, TipoMovimentacao.ENTRADA);
                case 4 -> registrarMovimentacao(scanner, estoque, admin, TipoMovimentacao.SAIDA);
                case 5 -> listarPedidos(estoque);
                case 6 -> historicoNiveis(scanner, estoque);
                case 0 -> System.out.println("Encerrando o sistema...");
                default -> System.out.println("Opção inválida.");
            }

        } while (opcao != 0);

        scanner.close();
    }

    private static List<Usuario> criarUsuariosPadrao() {
        List<Usuario> usuarios = new ArrayList<>();
        usuarios.add(new Usuario(1, "Administrador", "admin", "123", Usuario.Perfil.ADMIN));
        usuarios.add(new Usuario(2, "Operador", "operador", "123", Usuario.Perfil.OPERADOR));
        return usuarios;
    }

    private static Usuario autenticar(List<Usuario> usuarios, String login, String senha) {
        for (Usuario u : usuarios) {
            if (u.autenticar(login, senha)) {
                return u;
            }
        }
        return null;
    }

    private static void cadastrarProduto(Scanner scanner, EstoqueService estoque) {
        System.out.print("Nome do produto: ");
        String nome = scanner.nextLine();

        System.out.print("Quantidade inicial: ");
        int qtdInicial = scanner.nextInt();

        System.out.print("Quantidade mínima: ");
        int qtdMin = scanner.nextInt();

        System.out.print("Preço unitário: ");
        double preco = scanner.nextDouble();
        scanner.nextLine(); // consumir quebra de linha

        estoque.cadastrarProduto(nome, qtdInicial, qtdMin, preco);
        System.out.println("Produto cadastrado com sucesso!");
    }

    private static void listarProdutos(EstoqueService estoque) {
        var produtos = estoque.listarProdutos();
        if (produtos.isEmpty()) {
            System.out.println("Nenhum produto cadastrado.");
            return;
        }
        for (Produto p : produtos) {
            System.out.println(p);
        }
    }

    private static void registrarMovimentacao(
            Scanner scanner,
            EstoqueService estoque,
            Usuario usuario,
            TipoMovimentacao tipo
    ) {
        System.out.print("ID do produto: ");
        int idProduto = scanner.nextInt();

        System.out.print("Quantidade: ");
        int quantidade = scanner.nextInt();
        scanner.nextLine(); // consumir quebra de linha

        Pedido pedido = estoque.registrarMovimentacao(idProduto, quantidade, tipo, usuario);
        if (pedido != null) {
            System.out.println("Movimentação registrada: " + pedido);
        }
    }

    private static void listarPedidos(EstoqueService estoque) {
        var pedidos = estoque.listarPedidos();
        if (pedidos.isEmpty()) {
            System.out.println("Nenhuma movimentação registrada.");
            return;
        }
        for (Pedido p : pedidos) {
            System.out.println(p);
        }
    }

    private static void historicoNiveis(Scanner scanner, EstoqueService estoque) {
        System.out.print("ID do produto: ");
        int idProduto = scanner.nextInt();
        scanner.nextLine(); // consumir quebra de linha

        estoque.exibirHistoricoNiveis(idProduto);
    }
}