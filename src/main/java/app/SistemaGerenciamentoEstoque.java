package app;

import model.*;
import repository.*;
import service.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Scanner;

public class SistemaGerenciamentoEstoque {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        // ── Repositórios ───────────────────────────────────────
        UsuarioRepository    usuarioRepository   = new InMemoryUsuarioRepository();
        LogRepository        logRepository       = new InMemoryLogRepository();
        FornecedorRepository fornecedorRepo      = new InMemoryFornecedorRepository();
        NotaFiscalRepository notaFiscalRepo      = new InMemoryNotaFiscalRepository();
        CotacaoRepository    cotacaoRepo         = new InMemoryCotacaoRepository();
        CaixaRepository      caixaRepo           = new InMemoryCaixaRepository();
        FechamentoCaixaRepository fechamentoCaixaRepo = new InMemoryFechamentoCaixaRepository();
        ProdutoRepository    produtoRepo         = new InMemoryProdutoRepository();
        PedidoRepository     pedidoRepo          = new InMemoryPedidoRepository();
        CategoriaCardapioRepository categoriaCardapioRepository = new InMemoryCategoriaCardapioRepository();
        ItemCardapioRepository itemCardapioRepository = new InMemoryItemCardapioRepository();

        // ── Services ───────────────────────────────────────────
        EstoqueService              estoqueService    = new EstoqueService(produtoRepo, pedidoRepo);
        AutenticacaoService         autenticacaoService
                = new AutenticacaoService(usuarioRepository, logRepository);
        CadastroFuncionarioService  cadastroService
                = new CadastroFuncionarioService(usuarioRepository, logRepository);
        FornecedorService           fornecedorService
                = new FornecedorService(fornecedorRepo, logRepository);
        NotaFiscalService           notaFiscalService
                = new NotaFiscalService(notaFiscalRepo, logRepository, estoqueService, fornecedorService);
        CotacaoService              cotacaoService
                = new CotacaoService(cotacaoRepo, logRepository);
        CardapioService             cardapioService
                = new CardapioService(categoriaCardapioRepository, itemCardapioRepository, produtoRepo);
        CaixaService                caixaService
                = new CaixaService(caixaRepo, fechamentoCaixaRepo, logRepository, estoqueService, cardapioService);

        // ── Classes de funcionário ─────────────────────────────
        ClasseFuncionario classeSuperior       = criarClasseSuperior();
        ClasseFuncionario classeGerenteEstoque = criarClasseGerenteEstoque();
        ClasseFuncionario classeEstoquista     = criarClasseEstoquista();
        ClasseFuncionario classeCaixa          = criarClasseCaixa();

        // ── Seed de dados ──────────────────────────────────────
        criarUsuariosPadrao(usuarioRepository,
                classeSuperior, classeGerenteEstoque, classeEstoquista, classeCaixa);
        Usuario usuarioSeedProdutos = usuarioRepository.buscarPorRu(1)
                .orElseThrow(() -> new IllegalStateException("Usuario seed nao encontrado para criar produtos padrao."));
        criarProdutosPadrao(estoqueService, usuarioSeedProdutos);

        // ── Login ──────────────────────────────────────────────
        System.out.println("╔══════════════════════════════════════════╗");
        System.out.println("║  SISTEMA DE GERENCIAMENTO DE ESTOQUE     ║");
        System.out.println("╚══════════════════════════════════════════╝");
        System.out.println("\n=== LOGIN ===");
        System.out.print("Nome: ");
        String nome = scanner.nextLine();
        System.out.print("Sobrenome: ");
        String sobrenome = scanner.nextLine();
        System.out.print("Senha: ");
        String senha = scanner.nextLine();

        Usuario usuarioLogado = autenticacaoService.autenticar(nome, sobrenome, senha).orElse(null);
        if (usuarioLogado == null) {
            System.out.println("Credenciais inválidas ou usuário inativo. Encerrando...");
            scanner.close();
            return;
        }

        System.out.println("\nBem-vindo, " + usuarioLogado.getNomeCompleto()
                + " [" + usuarioLogado.getClasse().getNome() + "]");

        // ── Loop principal ─────────────────────────────────────
        int opcao;
        do {
            exibirMenuPrincipal(usuarioLogado);
            opcao = lerInteiro(scanner);
            executarOpcao(
                    opcao, scanner, usuarioLogado,
                    estoqueService, cadastroService, logRepository,
                    fornecedorService, notaFiscalService,
                    cotacaoService, caixaService,
                    classeSuperior, classeGerenteEstoque,
                    classeEstoquista, classeCaixa
            );
        } while (opcao != 0);

        scanner.close();
    }

    // ─────────────────────────────────────────────────────────
    // MENU PRINCIPAL
    // ─────────────────────────────────────────────────────────

    private static void exibirMenuPrincipal(Usuario usuarioLogado) {
        System.out.println("\n--- MENU PRINCIPAL ---");
        System.out.println("1 - Operações de estoque");
        System.out.println("2 - Fornecedores e Notas Fiscais");
        System.out.println("3 - Caixas");
        System.out.println("4 - Cotações Mensais");

        if (usuarioLogado.getClasse().possuiPermissao(Permissao.GERENCIAR_FUNCIONARIOS)) {
            System.out.println("5 - Cadastrar funcionário");
            System.out.println("6 - Listar funcionários");
            System.out.println("7 - Mudar classe de funcionário");
            System.out.println("8 - Desativar funcionário");
        }

        if (usuarioLogado.getClasse().possuiPermissao(Permissao.VER_LOGS)) {
            System.out.println("9 - Visualizar logs");
        }

        System.out.println("0 - Sair");
        System.out.print("Opção: ");
    }

    private static void executarOpcao(int opcao,
                                      Scanner scanner,
                                      Usuario usuarioLogado,
                                      EstoqueService estoqueService,
                                      CadastroFuncionarioService cadastroService,
                                      LogRepository logRepository,
                                      FornecedorService fornecedorService,
                                      NotaFiscalService notaFiscalService,
                                      CotacaoService cotacaoService,
                                      CaixaService caixaService,
                                      ClasseFuncionario classeSuperior,
                                      ClasseFuncionario classeGerenteEstoque,
                                      ClasseFuncionario classeEstoquista,
                                      ClasseFuncionario classeCaixa) {
        try {
            switch (opcao) {
                case 1 -> abrirMenuEstoque(scanner, estoqueService, usuarioLogado);
                case 2 -> menuFornecedores(scanner, usuarioLogado,
                        fornecedorService, notaFiscalService, estoqueService);
                case 3 -> menuCaixas(scanner, usuarioLogado, caixaService);
                case 4 -> menuCotacoes(scanner, usuarioLogado, cotacaoService, estoqueService);
                case 5 -> cadastrarFuncionario(scanner, cadastroService, usuarioLogado,
                        classeSuperior, classeGerenteEstoque, classeEstoquista, classeCaixa);
                case 6 -> listarFuncionarios(cadastroService, usuarioLogado);
                case 7 -> mudarClasseFuncionario(scanner, cadastroService, usuarioLogado,
                        classeSuperior, classeGerenteEstoque, classeEstoquista, classeCaixa);
                case 8 -> desativarFuncionario(scanner, cadastroService, usuarioLogado);
                case 9 -> visualizarLogs(logRepository, usuarioLogado);
                case 0 -> System.out.println("Encerrando o sistema...");
                default -> System.out.println("Opção inválida.");
            }
        } catch (SecurityException ex) {
            System.out.println("Você não tem permissão para executar esta ação.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            System.out.println("Erro: " + ex.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────
    // ESTOQUE (seu código original, mantido integralmente)
    // ─────────────────────────────────────────────────────────

    private static void abrirMenuEstoque(Scanner scanner,
                                         EstoqueService estoqueService,
                                         Usuario usuarioLogado) {
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
                    cadastrarProduto(scanner, estoqueService, usuarioLogado);
                }
                case 2 -> {
                    validarPermissao(usuarioLogado, Permissao.VER_ESTOQUE);
                    listarProdutos(estoqueService);
                }
                case 3 -> {
                    validarPermissao(usuarioLogado, Permissao.EDITAR_ESTOQUE);
                    registrarMovimentacaoEstoque(scanner, estoqueService, usuarioLogado, TipoMovimentacao.ENTRADA);
                }
                case 4 -> {
                    validarPermissao(usuarioLogado, Permissao.EDITAR_ESTOQUE);
                    registrarMovimentacaoEstoque(scanner, estoqueService, usuarioLogado, TipoMovimentacao.SAIDA);
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

    private static void cadastrarProduto(Scanner scanner, EstoqueService estoqueService, Usuario usuarioLogado) {
        System.out.print("Nome do produto: ");
        String nome = scanner.nextLine();
        System.out.print("Quantidade inicial: ");
        int qtdInicial = lerInteiro(scanner);
        System.out.print("Quantidade mínima: ");
        int qtdMin = lerInteiro(scanner);
        System.out.print("Preço unitário: ");
        double preco = lerDouble(scanner);

        estoqueService.cadastrarProduto(usuarioLogado, nome, qtdInicial, qtdMin, preco);
        System.out.println("Produto cadastrado com sucesso!");
    }

    private static void listarProdutos(EstoqueService estoqueService) {
        List<Produto> produtos = estoqueService.listarProdutos();
        if (produtos.isEmpty()) {
            System.out.println("Nenhum produto cadastrado.");
            return;
        }
        produtos.forEach(p -> System.out.printf(
                "  [%d] %-20s qtd: %3d | mín: %3d | R$%s%n",
                p.getId(), p.getNome(), p.getQuantidadeAtual(),
                p.getQuantidadeMinima(), p.getPrecoUnitario().toPlainString()
        ));
    }

    private static void registrarMovimentacaoEstoque(Scanner scanner,
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
        pedidos.forEach(System.out::println);
    }

    private static void historicoNiveis(Scanner scanner, EstoqueService estoqueService) {
        System.out.print("ID do produto: ");
        int idProduto = lerInteiro(scanner);
        estoqueService.exibirHistoricoNiveis(idProduto);
    }

    // ─────────────────────────────────────────────────────────
    // FORNECEDORES E NOTAS FISCAIS
    // ─────────────────────────────────────────────────────────

    private static void menuFornecedores(Scanner scanner,
                                         Usuario usuarioLogado,
                                         FornecedorService fornecedorService,
                                         NotaFiscalService notaFiscalService,
                                         EstoqueService estoqueService) {
        int opcao;
        do {
            System.out.println("\n--- FORNECEDORES / NOTAS FISCAIS ---");
            System.out.println("1 - Listar fornecedores ativos");
            System.out.println("2 - Cadastrar fornecedor");
            System.out.println("3 - Vincular produto a fornecedor");
            System.out.println("4 - Abrir nota fiscal");
            System.out.println("5 - Listar notas fiscais");
            System.out.println("0 - Voltar");
            System.out.print("Opção: ");

            opcao = lerInteiro(scanner);
            switch (opcao) {
                case 1 -> listarFornecedores(fornecedorService);
                case 2 -> cadastrarFornecedor(scanner, usuarioLogado, fornecedorService);
                case 3 -> vincularProduto(scanner, usuarioLogado,
                        fornecedorService, estoqueService);
                case 4 -> fluxoNotaFiscal(scanner, usuarioLogado,
                        fornecedorService, notaFiscalService, estoqueService);
                case 5 -> listarNotas(notaFiscalService);
                case 0 -> System.out.println("Retornando ao menu principal...");
                default -> System.out.println("Opção inválida.");
            }
        } while (opcao != 0);
    }

    private static void listarFornecedores(FornecedorService fornecedorService) {
        List<Fornecedor> lista = fornecedorService.listarAtivos();
        if (lista.isEmpty()) {
            System.out.println("Nenhum fornecedor ativo.");
            return;
        }
        lista.forEach(f -> System.out.printf(
                "  [%d] %-25s CNPJ: %s | Produtos: %d%n",
                f.getId(), f.getNome(), f.getCnpj(), f.getProdutos().size()
        ));
    }

    private static void cadastrarFornecedor(Scanner scanner,
                                            Usuario usuarioLogado,
                                            FornecedorService fornecedorService) {
        System.out.print("Nome: ");
        String nome = scanner.nextLine();
        System.out.print("CNPJ: ");
        String cnpj = scanner.nextLine();
        System.out.print("Telefone: ");
        String tel = scanner.nextLine();

        Fornecedor f = fornecedorService.cadastrarFornecedor(usuarioLogado, nome, cnpj, tel);
        System.out.println("Fornecedor cadastrado: " + f);
    }

    private static void vincularProduto(Scanner scanner,
                                        Usuario usuarioLogado,
                                        FornecedorService fornecedorService,
                                        EstoqueService estoqueService) {
        listarFornecedores(fornecedorService);
        System.out.print("ID do fornecedor: ");
        long fornId = lerLong(scanner);

        listarProdutos(estoqueService);
        System.out.print("ID do produto: ");
        int prodId = lerInteiro(scanner);

        Produto produto = estoqueService.buscarProdutoPorId(prodId)
                .orElseThrow(() -> new IllegalArgumentException("Produto não encontrado."));
        fornecedorService.vincularProduto(usuarioLogado, fornId, produto);
        System.out.println("Produto vinculado ao fornecedor.");
    }

    private static void fluxoNotaFiscal(Scanner scanner,
                                        Usuario usuarioLogado,
                                        FornecedorService fornecedorService,
                                        NotaFiscalService notaFiscalService,
                                        EstoqueService estoqueService) {
        listarFornecedores(fornecedorService);
        System.out.print("ID do fornecedor: ");
        long fornId = lerLong(scanner);
        Fornecedor fornecedor = fornecedorService.buscarPorId(fornId);

        // Etapa 1 — abre a nota
        NotaFiscal nota = notaFiscalService.abrirNota(usuarioLogado, fornecedor);
        System.out.println("Nota #" + nota.getId() + " aberta.");

        // Etapa 2 — adiciona itens
        int opcao;
        do {
            System.out.println("\nProdutos deste fornecedor:");
            fornecedor.getProdutos().forEach(p ->
                    System.out.printf("  [%d] %s%n", p.getId(), p.getNome()));
            System.out.println("0 - Finalizar itens");
            System.out.print("ID do produto: ");
            opcao = lerInteiro(scanner);
            if (opcao == 0) break;

            Produto produto = estoqueService.buscarProdutoPorId(opcao)
                    .orElseThrow(() -> new IllegalArgumentException("Produto não encontrado."));
            System.out.print("Quantidade: ");
            int qtd = lerInteiro(scanner);
            System.out.print("Preço unitário nesta nota: ");
            BigDecimal preco = BigDecimal.valueOf(lerDouble(scanner));

            notaFiscalService.adicionarItem(usuarioLogado, nota, produto, qtd, preco);
            System.out.printf("Item adicionado: %s × %d @ R$%s%n",
                    produto.getNome(), qtd, preco.toPlainString());
        } while (true);

        // Etapa 3 — confirma e dá entrada no estoque
        notaFiscalService.confirmarNota(usuarioLogado, nota);
        System.out.printf("Nota confirmada. Total: R$%s | Estoque atualizado.%n",
                nota.calcularTotal().toPlainString());

        // Etapa 4 — pagamento
        System.out.print("Registrar pagamento agora? (s/n): ");
        if (scanner.nextLine().trim().equalsIgnoreCase("s")) {
            notaFiscalService.registrarPagamento(usuarioLogado, nota);
            System.out.println("Pagamento registrado.");
        }
    }

    private static void listarNotas(NotaFiscalService notaFiscalService) {
        List<NotaFiscal> notas = notaFiscalService.listarTodas();
        if (notas.isEmpty()) {
            System.out.println("Nenhuma nota fiscal registrada.");
            return;
        }
        notas.forEach(n -> System.out.printf(
                "  [%d] %-20s status: %-12s total: R$%s%n",
                n.getId(), n.getFornecedor().getNome(),
                n.getStatus(), n.calcularTotal().toPlainString()
        ));
    }

    // ─────────────────────────────────────────────────────────
    // CAIXAS
    // ─────────────────────────────────────────────────────────

    private static void menuCaixas(Scanner scanner,
                                   Usuario usuarioLogado,
                                   CaixaService caixaService) {
        int opcao;
        do {
            System.out.println("\n--- CAIXAS ---");
            System.out.println("1 - Abrir caixa");
            System.out.println("2 - Registrar venda");
            System.out.println("3 - Registrar sangria");
            System.out.println("4 - Registrar suprimento");
            System.out.println("5 - Encerrar caixa");
            System.out.println("6 - Consolidado do dia");
            System.out.println("0 - Voltar");
            System.out.print("Opção: ");

            opcao = lerInteiro(scanner);
            switch (opcao) {
                case 1 -> {
                    System.out.print("Número do caixa (1, 2 ou 3): ");
                    int num = lerInteiro(scanner);
                    System.out.print("Saldo inicial (troco): R$ ");
                    double saldo = lerDouble(scanner);
                    caixaService.abrirCaixa(usuarioLogado, num, saldo);
                    System.out.println("Caixa " + num + " aberto.");
                }
                case 2 -> {
                    System.out.print("Número do caixa: ");
                    int num = lerInteiro(scanner);
                    System.out.print("Valor da venda: R$ ");
                    double valor = lerDouble(scanner);
                    System.out.print("Descrição: ");
                    String desc = scanner.nextLine();
                    caixaService.registrarVenda(usuarioLogado, num, valor, desc);
                    System.out.println("Venda registrada.");
                }
                case 3 -> {
                    System.out.print("Número do caixa: ");
                    int num = lerInteiro(scanner);
                    System.out.print("Valor da sangria: R$ ");
                    double valor = lerDouble(scanner);
                    System.out.print("Motivo: ");
                    String desc = scanner.nextLine();
                    caixaService.registrarSangria(usuarioLogado, num, valor, desc);
                    System.out.println("Sangria registrada.");
                }
                case 4 -> {
                    System.out.print("Número do caixa: ");
                    int num = lerInteiro(scanner);
                    System.out.print("Valor do suprimento: R$ ");
                    double valor = lerDouble(scanner);
                    System.out.print("Motivo: ");
                    String desc = scanner.nextLine();
                    caixaService.registrarSuprimento(usuarioLogado, num, valor, desc);
                    System.out.println("Suprimento registrado.");
                }
                case 5 -> {
                    System.out.print("Número do caixa: ");
                    int num = lerInteiro(scanner);
                    FechamentoCaixa fechamento = caixaService.encerrarCaixa(usuarioLogado, num);
                    System.out.println(fechamento.gerarResumo());
                }
                case 6 -> System.out.println(caixaService.consolidarDia(LocalDate.now()));
                case 0 -> System.out.println("Retornando ao menu principal...");
                default -> System.out.println("Opção inválida.");
            }
        } while (opcao != 0);
    }

    // ─────────────────────────────────────────────────────────
    // COTAÇÕES
    // ─────────────────────────────────────────────────────────

    private static void menuCotacoes(Scanner scanner,
                                     Usuario usuarioLogado,
                                     CotacaoService cotacaoService,
                                     EstoqueService estoqueService) {
        int opcao;
        do {
            System.out.println("\n--- COTAÇÕES MENSAIS ---");
            System.out.println("1 - Registrar cotação");
            System.out.println("2 - Comparar mês atual com anterior");
            System.out.println("3 - Relatório mensal completo");
            System.out.println("4 - Histórico de um produto");
            System.out.println("0 - Voltar");
            System.out.print("Opção: ");

            opcao = lerInteiro(scanner);
            switch (opcao) {
                case 1 -> {
                    listarProdutos(estoqueService);
                    System.out.print("ID do produto: ");
                    int prodId = lerInteiro(scanner);
                    Produto produto = estoqueService.buscarProdutoPorId(prodId)
                            .orElseThrow(() -> new IllegalArgumentException("Produto não encontrado."));
                    YearMonth mes = lerMes(scanner);
                    System.out.print("Preço unitário: R$ ");
                    double preco = lerDouble(scanner);
                    System.out.print("Quantidade comprada no mês: ");
                    int qtd = lerInteiro(scanner);
                    cotacaoService.registrarCotacao(usuarioLogado, produto, mes, BigDecimal.valueOf(preco), qtd);
                    System.out.println("Cotação registrada.");
                }
                case 2 -> {
                    listarProdutos(estoqueService);
                    System.out.print("ID do produto: ");
                    int prodId = lerInteiro(scanner);
                    Produto produto = estoqueService.buscarProdutoPorId(prodId)
                            .orElseThrow(() -> new IllegalArgumentException("Produto não encontrado."));
                    YearMonth mes = lerMes(scanner);
                    cotacaoService.compararComMesAnterior(produto, mes)
                            .ifPresentOrElse(
                                    r -> System.out.println(r.gerarMensagem()),
                                    () -> System.out.println("Sem dados suficientes para comparar.")
                            );
                }
                case 3 -> {
                    YearMonth mes = lerMes(scanner);
                    List<ResultadoComparacao> resultados = cotacaoService
                            .gerarRelatorioMensal(estoqueService.listarProdutos(), mes);
                    if (resultados.isEmpty()) {
                        System.out.println("Sem dados suficientes para o relatório.");
                    } else {
                        System.out.println("\n── Relatório " + mes + " ──");
                        resultados.forEach(r -> System.out.println(r.gerarMensagem()));
                    }
                }
                case 4 -> {
                    listarProdutos(estoqueService);
                    System.out.print("ID do produto: ");
                    int prodId = lerInteiro(scanner);
                    Produto produto = estoqueService.buscarProdutoPorId(prodId)
                            .orElseThrow(() -> new IllegalArgumentException("Produto não encontrado."));
                    List<CotacaoMensal> historico = cotacaoService.buscarHistorico(produto);
                    if (historico.isEmpty()) {
                        System.out.println("Nenhuma cotação registrada para este produto.");
                    } else {
                        System.out.println("\n── Histórico: " + produto.getNome() + " ──");
                        historico.forEach(c -> System.out.println("  " + c));
                    }
                }
                case 0 -> System.out.println("Retornando ao menu principal...");
                default -> System.out.println("Opção inválida.");
            }
        } while (opcao != 0);
    }

    // ─────────────────────────────────────────────────────────
    // FUNCIONÁRIOS (seu código original, mantido integralmente)
    // ─────────────────────────────────────────────────────────

    private static void cadastrarFuncionario(Scanner scanner,
                                             CadastroFuncionarioService cadastroService,
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
                scanner, classeSuperior, classeGerenteEstoque,
                classeEstoquista, classeCaixa);

        Usuario novoUsuario = cadastroService.cadastrarFuncionario(
                usuarioLogado, nome, sobrenome, cpf, senhaInicial, classeDestino);
        System.out.println("Funcionário cadastrado com RU " + novoUsuario.getRu());
    }

    private static void listarFuncionarios(CadastroFuncionarioService cadastroService,
                                           Usuario usuarioLogado) {
        validarPermissao(usuarioLogado, Permissao.GERENCIAR_FUNCIONARIOS);
        List<Usuario> usuarios = cadastroService.listarFuncionarios(usuarioLogado);
        if (usuarios.isEmpty()) {
            System.out.println("Nenhum funcionário cadastrado.");
            return;
        }
        usuarios.forEach(u -> System.out.printf(
                "  RU=%-4d | %-25s | Classe: %-20s | Ativo: %s%n",
                u.getRu(), u.getNomeCompleto(),
                u.getClasse().getNome(), u.isAtivo() ? "sim" : "não"
        ));
    }

    private static void mudarClasseFuncionario(Scanner scanner,
                                               CadastroFuncionarioService cadastroService,
                                               Usuario usuarioLogado,
                                               ClasseFuncionario classeSuperior,
                                               ClasseFuncionario classeGerenteEstoque,
                                               ClasseFuncionario classeEstoquista,
                                               ClasseFuncionario classeCaixa) {
        validarPermissao(usuarioLogado, Permissao.GERENCIAR_FUNCIONARIOS);
        System.out.print("RU do funcionário: ");
        long ru = lerLong(scanner);

        ClasseFuncionario novaClasse = selecionarClasse(
                scanner, classeSuperior, classeGerenteEstoque,
                classeEstoquista, classeCaixa);

        Usuario atualizado = cadastroService.mudarClasse(usuarioLogado, ru, novaClasse);
        System.out.println("Classe atualizada para " + atualizado.getClasse().getNome());
    }

    private static void desativarFuncionario(Scanner scanner,
                                             CadastroFuncionarioService cadastroService,
                                             Usuario usuarioLogado) {
        validarPermissao(usuarioLogado, Permissao.GERENCIAR_FUNCIONARIOS);
        System.out.print("RU do funcionário: ");
        long ru = lerLong(scanner);
        cadastroService.desativarFuncionario(usuarioLogado, ru);
        System.out.println("Funcionário desativado com sucesso.");
    }

    private static void visualizarLogs(LogRepository logRepository, Usuario usuarioLogado) {
        validarPermissao(usuarioLogado, Permissao.VER_LOGS);
        List<LogAcao> logs = logRepository.listarTodos();
        if (logs.isEmpty()) {
            System.out.println("Nenhum log registrado.");
            return;
        }
        logs.forEach(l -> System.out.printf(
                "  [%s] RU:%-4s | %-30s | %s%n",
                l.getDataHora().toLocalTime(),
                l.getUsuarioRu() != null ? l.getUsuarioRu() : "—",
                l.getAcao(),
                l.getDescricao()
        ));
    }

    // ─────────────────────────────────────────────────────────
    // UTILITÁRIOS (seu código original, mantido integralmente)
    // ─────────────────────────────────────────────────────────

    private static ClasseFuncionario selecionarClasse(Scanner scanner,
                                                      ClasseFuncionario classeSuperior,
                                                      ClasseFuncionario classeGerenteEstoque,
                                                      ClasseFuncionario classeEstoquista,
                                                      ClasseFuncionario classeCaixa) {
        System.out.println("Selecione a classe:");
        System.out.println("1 - " + classeSuperior.getNome());
        System.out.println("2 - " + classeGerenteEstoque.getNome());
        System.out.println("3 - " + classeEstoquista.getNome());
        System.out.println("4 - " + classeCaixa.getNome());
        System.out.print("Opção: ");

        return switch (lerInteiro(scanner)) {
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

    /** Lê mês no formato MM/AAAA e converte para YearMonth */
    private static YearMonth lerMes(Scanner scanner) {
        while (true) {
            System.out.print("Mês (MM/AAAA): ");
            String input = scanner.nextLine().trim();
            try {
                String[] partes = input.split("/");
                return YearMonth.of(Integer.parseInt(partes[1]), Integer.parseInt(partes[0]));
            } catch (Exception e) {
                System.out.println("Formato inválido. Use MM/AAAA (ex: 01/2025).");
            }
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
            System.out.println("Digite um número decimal válido (use vírgula ou ponto).");
            scanner.nextLine();
        }
        double valor = scanner.nextDouble();
        scanner.nextLine();
        return valor;
    }

    // ─────────────────────────────────────────────────────────
    // SEED DE DADOS
    // ─────────────────────────────────────────────────────────

    private static void criarUsuariosPadrao(UsuarioRepository usuarioRepository,
                                            ClasseFuncionario classeSuperior,
                                            ClasseFuncionario classeGerenteEstoque,
                                            ClasseFuncionario classeEstoquista,
                                            ClasseFuncionario classeCaixa) {
        usuarioRepository.salvar(new Usuario(0, "Admin", "Superior",
                "529.982.247-25", "1234", classeSuperior, Usuario.Perfil.ADMIN));
        usuarioRepository.salvar(new Usuario(0, "Maria", "Gerente",
                "111.444.777-35", "1234", classeGerenteEstoque, Usuario.Perfil.OPERADOR));
        usuarioRepository.salvar(new Usuario(0, "Joao", "Silva",
                "123.456.789-09", "1234", classeEstoquista, Usuario.Perfil.OPERADOR));
        usuarioRepository.salvar(new Usuario(0, "Ana", "Caixa",
                "935.411.347-80", "1234", classeCaixa, Usuario.Perfil.OPERADOR));

        System.out.println("Usuários padrão criados:");
        System.out.println("  Admin Superior  / senha: 1234  (acesso total)");
        System.out.println("  Maria Gerente   / senha: 1234");
        System.out.println("  Joao  Silva     / senha: 1234");
        System.out.println("  Ana   Caixa     / senha: 1234");
    }

    private static void criarProdutosPadrao(EstoqueService estoqueService, Usuario usuarioLogado) {
        estoqueService.cadastrarProduto(usuarioLogado, "Laranja",  50, 20, 4.99);
        estoqueService.cadastrarProduto(usuarioLogado, "Morango",  30, 10, 8.50);
        estoqueService.cadastrarProduto(usuarioLogado, "Alface",   40, 15, 2.50);
        estoqueService.cadastrarProduto(usuarioLogado, "Tomate",   60, 25, 3.80);
    }

    // ─────────────────────────────────────────────────────────
    // CLASSES DE FUNCIONÁRIO (seu código original)
    // ─────────────────────────────────────────────────────────

    private static ClasseFuncionario criarClasseSuperior() {
        ClasseFuncionario classe = new ClasseFuncionario(1, "SUPERIOR", "Acesso total ao sistema");
        for (Permissao p : Permissao.values()) classe.adicionarPermissao(p);
        return classe;
    }

    private static ClasseFuncionario criarClasseGerenteEstoque() {
        ClasseFuncionario classe = new ClasseFuncionario(2, "GERENTE_ESTOQUE", "Gerencia estoque e compras");
        classe.adicionarPermissao(Permissao.VER_ESTOQUE);
        classe.adicionarPermissao(Permissao.EDITAR_ESTOQUE);
        classe.adicionarPermissao(Permissao.VER_COMPRAS);
        classe.adicionarPermissao(Permissao.VER_FINANCAS);
        classe.adicionarPermissao(Permissao.GERENCIAR_FUNCIONARIOS);
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
        classe.adicionarPermissao(Permissao.VER_ESTOQUE);
        return classe;
    }
}
