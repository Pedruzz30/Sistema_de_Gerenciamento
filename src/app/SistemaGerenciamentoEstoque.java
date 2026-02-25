package app;

import model.ClasseFuncionario;
import model.LogAcao;
import model.Pedido;
import model.Permissao;
import model.Produto;
import model.TipoMovimentacao;
import model.Usuario;
import repository.InMemoryLogRepository;
import repository.InMemoryUsuarioRepository;
import repository.LogRepository;
import repository.UsuarioRepository;
import service.AutenticacaoService;
import service.CadastroFuncionarioService;
import service.EstoqueService;

import java.util.List;
import java.util.Scanner;

public class SistemaGerenciamentoEstoque {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        EstoqueService estoqueService = new EstoqueService();

        UsuarioRepository usuarioRepository = new InMemoryUsuarioRepository();
        LogRepository logRepository = new InMemoryLogRepository();

        AutenticacaoService autenticacaoService = new AutenticacaoService(usuarioRepository, logRepository);
        CadastroFuncionarioService cadastroFuncionarioService = new CadastroFuncionarioService(usuarioRepository, logRepository);

        ClasseFuncionario classeSuperior = criarClasseSuperior();
        ClasseFuncionario classeGerenteEstoque = criarClasseGerenteEstoque();
        ClasseFuncionario classeEstoquista = criarClasseEstoquista();
        ClasseFuncionario classeCaixa = criarClasseCaixa();

        criarUsuariosPadrao(usuarioRepository, classeSuperior, classeGerenteEstoque, classeEstoquista, classeCaixa);

        System.out.println("=== LOGIN ===");
        System.out.print("Nome: ");
        String nome = scanner.nextLine();

        System.out.print("Sobrenome: ");
        String sobrenome = scanner.nextLine();

        System.out.print("Senha: ");
        String senha = scanner.nextLine();

        // Fluxo de login usando nome + sobrenome + senha.
        Usuario usuarioLogado = autenticacaoService.autenticar(nome, sobrenome, senha);
        if (usuarioLogado == null) {
            System.out.println("Credenciais inválidas ou usuário inativo. Encerrando...");
            scanner.close();
            return;
        }

        System.out.println("Bem-vindo, " + usuarioLogado.getNomeCompleto()
                + " (Classe: " + usuarioLogado.getClasse().getNome() + ")");

        int opcao;
        do {
            exibirMenuPrincipal(usuarioLogado);
            opcao = lerInteiro(scanner);
            executarOpcao(
                    opcao,
                    scanner,
                    usuarioLogado,
                    estoqueService,
                    cadastroFuncionarioService,
                    logRepository,
                    classeSuperior,
                    classeGerenteEstoque,
                    classeEstoquista,
                    classeCaixa
            );
        } while (opcao != 0);

        scanner.close();
    }

    private static void exibirMenuPrincipal(Usuario usuarioLogado) {
        System.out.println("\n--- MENU PRINCIPAL ---");
        System.out.println("1 - Operações de estoque");

        if (usuarioLogado.getClasse().possuiPermissao(Permissao.GERENCIAR_FUNCIONARIOS)) {
            System.out.println("2 - Cadastrar funcionário");
            System.out.println("3 - Listar funcionários");
            System.out.println("4 - Mudar classe de funcionário");
            System.out.println("5 - Desativar funcionário");
        }

        if (usuarioLogado.getClasse().possuiPermissao(Permissao.VER_LOGS)) {
            System.out.println("6 - Visualizar logs");
        }

        System.out.println("0 - Sair");
        System.out.print("Opção: ");
    }

    private static void executarOpcao(int opcao,
                                      Scanner scanner,
                                      Usuario usuarioLogado,
                                      EstoqueService estoqueService,
                                      CadastroFuncionarioService cadastroFuncionarioService,
                                      LogRepository logRepository,
                                      ClasseFuncionario classeSuperior,
                                      ClasseFuncionario classeGerenteEstoque,
                                      ClasseFuncionario classeEstoquista,
                                      ClasseFuncionario classeCaixa) {
        try {
            switch (opcao) {
                case 1 -> abrirMenuEstoque(scanner, estoqueService, usuarioLogado);
                case 2 -> cadastrarFuncionario(
                        scanner,
                        cadastroFuncionarioService,
                        usuarioLogado,
                        classeSuperior,
                        classeGerenteEstoque,
                        classeEstoquista,
                        classeCaixa
                );
                case 3 -> listarFuncionarios(cadastroFuncionarioService, usuarioLogado);
                case 4 -> mudarClasseFuncionario(
                        scanner,
                        cadastroFuncionarioService,
                        usuarioLogado,
                        classeSuperior,
                        classeGerenteEstoque,
                        classeEstoquista,
                        classeCaixa
                );
                case 5 -> desativarFuncionario(scanner, cadastroFuncionarioService, usuarioLogado);
                case 6 -> visualizarLogs(logRepository, usuarioLogado);
                case 0 -> System.out.println("Encerrando o sistema...");
                default -> System.out.println("Opção inválida.");
            }
        } catch (SecurityException ex) {
            System.out.println("Você não tem permissão para executar esta ação.");
        } catch (IllegalArgumentException ex) {
            System.out.println("Erro: " + ex.getMessage());
        }
    }

    private static void abrirMenuEstoque(Scanner scanner, EstoqueService estoqueService, Usuario usuarioLogado) {
        int opcao;
        do {
            System.out.println("\n--- MENU ESTOQUE ---");
            System.out.println("1 - Cadastrar produto");
            System.out.println("2 - Listar produtos");
            System.out.println("3 - Registrar entrada");
            System.out.println("4 - Registrar saída");
            System.out.println("5 - Listar movimentações");
            System.out.println("6 - Histórico de níveis de um produto");
            System.out.println("0 - Voltar");
            System.out.print("Opção: ");

            opcao = lerInteiro(scanner);
            switch (opcao) {
                case 1 -> {
                    validarPermissao(usuarioLogado, Permissao.EDITAR_ESTOQUE);
                    cadastrarProduto(scanner, estoqueService);
                }
                case 2 -> {
                    validarPermissao(usuarioLogado, Permissao.VER_ESTOQUE);
                    listarProdutos(estoqueService);
                }
                case 3 -> {
                    validarPermissao(usuarioLogado, Permissao.EDITAR_ESTOQUE);
                    registrarMovimentacao(scanner, estoqueService, usuarioLogado, TipoMovimentacao.ENTRADA);
                }
                case 4 -> {
                    validarPermissao(usuarioLogado, Permissao.EDITAR_ESTOQUE);
                    registrarMovimentacao(scanner, estoqueService, usuarioLogado, TipoMovimentacao.SAIDA);
                }
                case 5 -> {
                    validarPermissao(usuarioLogado, Permissao.VER_ESTOQUE);
                    listarPedidos(estoqueService);
                }
                case 6 -> {
                    validarPermissao(usuarioLogado, Permissao.VER_ESTOQUE);
                    historicoNiveis(scanner, estoqueService);
                }
                case 0 -> System.out.println("Retornando ao menu principal...");
                default -> System.out.println("Opção inválida.");
            }
        } while (opcao != 0);
    }

    private static void cadastrarFuncionario(Scanner scanner,
                                             CadastroFuncionarioService cadastroFuncionarioService,
                                             Usuario usuarioLogado,
                                             ClasseFuncionario classeSuperior,
                                             ClasseFuncionario classeGerenteEstoque,
                                             ClasseFuncionario classeEstoquista,
                                             ClasseFuncionario classeCaixa) {
        validarPermissao(usuarioLogado, Permissao.GERENCIAR_FUNCIONARIOS);

        System.out.print("Nome: ");
        String nome = scanner.nextLine();
        System.out.print("Sobrenome: ");
        String sobrenome = scanner.nextLine();
        System.out.print("CPF: ");
        String cpf = scanner.nextLine();
        System.out.print("Senha inicial: ");
        String senhaInicial = scanner.nextLine();

        ClasseFuncionario classeDestino = selecionarClasse(
                scanner,
                classeSuperior,
                classeGerenteEstoque,
                classeEstoquista,
                classeCaixa
        );

        // Cadastro de funcionário protegido por permissão de gestão.
        Usuario novoUsuario = cadastroFuncionarioService.cadastrarFuncionario(
                usuarioLogado,
                nome,
                sobrenome,
                cpf,
                senhaInicial,
                classeDestino
        );

        System.out.println("Funcionário cadastrado com RU " + novoUsuario.getRu());
    }

    private static void listarFuncionarios(CadastroFuncionarioService cadastroFuncionarioService, Usuario usuarioLogado) {
        validarPermissao(usuarioLogado, Permissao.GERENCIAR_FUNCIONARIOS);

        List<Usuario> usuarios = cadastroFuncionarioService.listarFuncionarios(usuarioLogado);
        if (usuarios.isEmpty()) {
            System.out.println("Nenhum funcionário cadastrado.");
            return;
        }

        for (Usuario usuario : usuarios) {
            System.out.println("RU=" + usuario.getRu()
                    + " | Nome=" + usuario.getNomeCompleto()
                    + " | Classe=" + usuario.getClasse().getNome()
                    + " | Ativo=" + usuario.isAtivo());
        }
    }

    private static void mudarClasseFuncionario(Scanner scanner,
                                               CadastroFuncionarioService cadastroFuncionarioService,
                                               Usuario usuarioLogado,
                                               ClasseFuncionario classeSuperior,
                                               ClasseFuncionario classeGerenteEstoque,
                                               ClasseFuncionario classeEstoquista,
                                               ClasseFuncionario classeCaixa) {
        validarPermissao(usuarioLogado, Permissao.GERENCIAR_FUNCIONARIOS);

        System.out.print("Informe o RU do funcionário: ");
        long ruFuncionario = lerLong(scanner);

        ClasseFuncionario novaClasse = selecionarClasse(
                scanner,
                classeSuperior,
                classeGerenteEstoque,
                classeEstoquista,
                classeCaixa
        );

        // Mudança de classe para ajustar permissões do funcionário.
        Usuario atualizado = cadastroFuncionarioService.mudarClasse(usuarioLogado, ruFuncionario, novaClasse);
        System.out.println("Classe atualizada para " + atualizado.getClasse().getNome());
    }

    private static void desativarFuncionario(Scanner scanner,
                                             CadastroFuncionarioService cadastroFuncionarioService,
                                             Usuario usuarioLogado) {
        validarPermissao(usuarioLogado, Permissao.GERENCIAR_FUNCIONARIOS);

        System.out.print("Informe o RU do funcionário: ");
        long ruFuncionario = lerLong(scanner);

        // Desativação para bloquear acesso de funcionário desligado.
        cadastroFuncionarioService.desativarFuncionario(usuarioLogado, ruFuncionario);
        System.out.println("Funcionário desativado com sucesso.");
    }

    private static void visualizarLogs(LogRepository logRepository, Usuario usuarioLogado) {
        validarPermissao(usuarioLogado, Permissao.VER_LOGS);

        List<LogAcao> logs = logRepository.listarTodos();
        if (logs.isEmpty()) {
            System.out.println("Nenhum log registrado.");
            return;
        }

        for (LogAcao log : logs) {
            System.out.println(log.getDataHora() + " | " + log.getAcao() + " | " + log.getDescricao());
        }
    }

    private static void cadastrarProduto(Scanner scanner, EstoqueService estoqueService) {
        System.out.print("Nome do produto: ");
        String nome = scanner.nextLine();

        System.out.print("Quantidade inicial: ");
        int qtdInicial = lerInteiro(scanner);

        System.out.print("Quantidade mínima: ");
        int qtdMin = lerInteiro(scanner);

        System.out.print("Preço unitário: ");
        double preco = lerDouble(scanner);

        estoqueService.cadastrarProduto(nome, qtdInicial, qtdMin, preco);
        System.out.println("Produto cadastrado com sucesso!");
    }

    private static void listarProdutos(EstoqueService estoqueService) {
        List<Produto> produtos = estoqueService.listarProdutos();
        if (produtos.isEmpty()) {
            System.out.println("Nenhum produto cadastrado.");
            return;
        }

        for (Produto produto : produtos) {
            System.out.println(produto);
        }
    }

    private static void registrarMovimentacao(Scanner scanner,
                                              EstoqueService estoqueService,
                                              Usuario usuario,
                                              TipoMovimentacao tipo) {
        System.out.print("ID do produto: ");
        int idProduto = lerInteiro(scanner);

        System.out.print("Quantidade: ");
        int quantidade = lerInteiro(scanner);

        Pedido pedido = estoqueService.registrarMovimentacao(idProduto, quantidade, tipo, usuario);
        if (pedido != null) {
            System.out.println("Movimentação registrada: " + pedido);
        }
    }

    private static void listarPedidos(EstoqueService estoqueService) {
        List<Pedido> pedidos = estoqueService.listarPedidos();
        if (pedidos.isEmpty()) {
            System.out.println("Nenhuma movimentação registrada.");
            return;
        }
        for (Pedido pedido : pedidos) {
            System.out.println(pedido);
        }
    }

    private static void historicoNiveis(Scanner scanner, EstoqueService estoqueService) {
        System.out.print("ID do produto: ");
        int idProduto = lerInteiro(scanner);
        estoqueService.exibirHistoricoNiveis(idProduto);
    }

    private static ClasseFuncionario selecionarClasse(Scanner scanner,
                                                      ClasseFuncionario classeSuperior,
                                                      ClasseFuncionario classeGerenteEstoque,
                                                      ClasseFuncionario classeEstoquista,
                                                      ClasseFuncionario classeCaixa) {
        System.out.println("Selecione a classe do funcionário:");
        System.out.println("1 - " + classeSuperior.getNome());
        System.out.println("2 - " + classeGerenteEstoque.getNome());
        System.out.println("3 - " + classeEstoquista.getNome());
        System.out.println("4 - " + classeCaixa.getNome());
        System.out.print("Opção: ");

        int opcao = lerInteiro(scanner);
        return switch (opcao) {
            case 1 -> classeSuperior;
            case 2 -> classeGerenteEstoque;
            case 3 -> classeEstoquista;
            case 4 -> classeCaixa;
            default -> throw new IllegalArgumentException("Classe inválida.");
        };
    }

    private static void validarPermissao(Usuario usuario, Permissao permissao) {
        if (usuario.getClasse() == null || !usuario.getClasse().possuiPermissao(permissao)) {
            throw new SecurityException("Sem permissão.");
        }
    }

    private static int lerInteiro(Scanner scanner) {
        while (!scanner.hasNextInt()) {
            System.out.println("Digite um número inteiro válido.");
            scanner.nextLine();
        }
        int valor = scanner.nextInt();
        scanner.nextLine();
        return valor;
    }

    private static long lerLong(Scanner scanner) {
        while (!scanner.hasNextLong()) {
            System.out.println("Digite um número válido.");
            scanner.nextLine();
        }
        long valor = scanner.nextLong();
        scanner.nextLine();
        return valor;
    }

    private static double lerDouble(Scanner scanner) {
        while (!scanner.hasNextDouble()) {
            System.out.println("Digite um número decimal válido.");
            scanner.nextLine();
        }
        double valor = scanner.nextDouble();
        scanner.nextLine();
        return valor;
    }

    private static void criarUsuariosPadrao(UsuarioRepository usuarioRepository,
                                            ClasseFuncionario classeSuperior,
                                            ClasseFuncionario classeGerenteEstoque,
                                            ClasseFuncionario classeEstoquista,
                                            ClasseFuncionario classeCaixa) {
        Usuario superior = new Usuario(
                0,
                "Admin",
                "Superior",
                "000.000.000-00",
                "1234",
                classeSuperior,
                Usuario.Perfil.ADMIN
        );

        Usuario gerente = new Usuario(
                0,
                "Maria",
                "Gerente",
                "111.111.111-11",
                "1234",
                classeGerenteEstoque,
                Usuario.Perfil.OPERADOR
        );

        Usuario estoquista = new Usuario(
                0,
                "Joao",
                "Silva",
                "222.222.222-22",
                "1234",
                classeEstoquista,
                Usuario.Perfil.OPERADOR
        );

        Usuario caixa = new Usuario(
                0,
                "Ana",
                "Caixa",
                "333.333.333-33",
                "1234",
                classeCaixa,
                Usuario.Perfil.OPERADOR
        );

        usuarioRepository.salvar(superior);
        usuarioRepository.salvar(gerente);
        usuarioRepository.salvar(estoquista);
        usuarioRepository.salvar(caixa);
    }

    private static ClasseFuncionario criarClasseSuperior() {
        ClasseFuncionario classe = new ClasseFuncionario(1, "SUPERIOR", "Acesso total ao sistema");
        classe.adicionarPermissao(Permissao.VER_ESTOQUE);
        classe.adicionarPermissao(Permissao.EDITAR_ESTOQUE);
        classe.adicionarPermissao(Permissao.VER_COMPRAS);
        classe.adicionarPermissao(Permissao.VER_VENDAS);
        classe.adicionarPermissao(Permissao.VER_FINANCAS);
        classe.adicionarPermissao(Permissao.GERENCIAR_FUNCIONARIOS);
        classe.adicionarPermissao(Permissao.VER_LOGS);
        return classe;
    }

    private static ClasseFuncionario criarClasseGerenteEstoque() {
        ClasseFuncionario classe = new ClasseFuncionario(2, "GERENTE_ESTOQUE", "Gerencia estoque e compras");
        classe.adicionarPermissao(Permissao.VER_ESTOQUE);
        classe.adicionarPermissao(Permissao.EDITAR_ESTOQUE);
        classe.adicionarPermissao(Permissao.VER_COMPRAS);
        classe.adicionarPermissao(Permissao.VER_LOGS);
        return classe;
    }

    private static ClasseFuncionario criarClasseEstoquista() {
        ClasseFuncionario classe = new ClasseFuncionario(3, "ESTOQUISTA", "Movimenta itens de estoque");
        classe.adicionarPermissao(Permissao.VER_ESTOQUE);
        classe.adicionarPermissao(Permissao.EDITAR_ESTOQUE);
        return classe;
    }

    private static ClasseFuncionario criarClasseCaixa() {
        ClasseFuncionario classe = new ClasseFuncionario(4, "CAIXA", "Responsável por vendas");
        classe.adicionarPermissao(Permissao.VER_VENDAS);
        return classe;
    }
}