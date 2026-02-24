import java.util.Scanner;

public class Cadastros {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        Estoque estoque = new Estoque();

        // Usuário “fake” só para testes
        Usuario admin = new Usuario(1, "Admin", "admin", "123", Usuario.Perfil.ADMIN);

        int opcao;

        do {
            System.out.println("\n=== SISTEMA DE GERENCIAMENTO DE ESTOQUE ===");
            System.out.println("1 - Cadastrar produto");
            System.out.println("2 - Listar produtos");
            System.out.println("3 - Registrar entrada");
            System.out.println("4 - Registrar saída");
            System.out.println("0 - Sair");
            System.out.print("Opção: ");
            opcao = scanner.nextInt();
            scanner.nextLine(); // consumir quebra de linha

            switch (opcao) {
                case 1:
                    System.out.print("Nome do produto: ");
                    String nome = scanner.nextLine();

                    System.out.print("Quantidade inicial: ");
                    int qtdInicial = scanner.nextInt();

                    System.out.print("Quantidade mínima: ");
                    int qtdMin = scanner.nextInt();

                    System.out.print("Preço unitário: ");
                    double preco = scanner.nextDouble();

                    estoque.cadastrarProduto(nome, qtdInicial, qtdMin, preco);
                    System.out.println("Produto cadastrado com sucesso!");
                    break;

                case 2:
                    estoque.listarProdutos();
                    break;

                case 3:
                    System.out.print("ID do produto: ");
                    int idEntrada = scanner.nextInt();

                    System.out.print("Quantidade de entrada: ");
                    int qtdEntrada = scanner.nextInt();

                    Produto pEntrada = estoque.buscarProdutoPorId(idEntrada);
                    if (pEntrada == null) {
                        System.out.println("Produto não encontrado.");
                    } else {
                        estoque.registrarMovimentacao(pEntrada, qtdEntrada, Pedido.Tipo.ENTRADA, admin);
                    }
                    break;

                case 4:
                    System.out.print("ID do produto: ");
                    int idSaida = scanner.nextInt();

                    System.out.print("Quantidade de saída: ");
                    int qtdSaida = scanner.nextInt();

                    Produto pSaida = estoque.buscarProdutoPorId(idSaida);
                    if (pSaida == null) {
                        System.out.println("Produto não encontrado.");
                    } else {
                        estoque.registrarMovimentacao(pSaida, qtdSaida, Pedido.Tipo.SAIDA, admin);
                    }
                    break;

                case 0:
                    System.out.println("Encerrando o sistema...");
                    break;

                default:
                    System.out.println("Opção inválida.");
            }

        } while (opcao != 0);

        scanner.close();
    }
}