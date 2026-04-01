package service;

import model.CategoriaEstoque;
import model.Fornecedor;
import model.ItemNotaFiscal;
import model.LogAcao;
import model.NotaFiscal;
import model.Permissao;
import model.Produto;
import model.TipoMovimentacao;
import model.Usuario;
import repository.LogRepository;
import repository.NotaFiscalRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Gerencia o ciclo de vida completo de uma nota fiscal.
 *
 * O fluxo tem 4 etapas obrigatorias, nessa ordem:
 *   1. abrirNota()         -> cria a nota vinculada ao fornecedor
 *   2. adicionarItem()     -> adiciona os produtos da nota
 *   3. confirmarNota()     -> confirma os produtos recebidos
 *   4. registrarPagamento()-> fecha o pagamento
 *
 * O EstoqueService e chamado internamente em confirmarNota()
 * para dar entrada dos produtos no estoque automaticamente.
 */
public class NotaFiscalService {

    private final NotaFiscalRepository notaFiscalRepository;
    private final LogRepository logRepository;
    private final EstoqueService estoqueService;
    private final FornecedorService fornecedorService;

    public NotaFiscalService(NotaFiscalRepository notaFiscalRepository,
                             LogRepository logRepository,
                             EstoqueService estoqueService,
                             FornecedorService fornecedorService) {
        this.notaFiscalRepository = notaFiscalRepository;
        this.logRepository = logRepository;
        this.estoqueService = estoqueService;
        this.fornecedorService = fornecedorService;
    }

    public NotaFiscal abrirNota(Usuario usuarioLogado, Fornecedor fornecedor) {
        validarPermissao(usuarioLogado, Permissao.EDITAR_ESTOQUE);

        if (fornecedor == null || !fornecedor.isAtivo()) {
            throw new IllegalArgumentException("Fornecedor invalido ou inativo.");
        }

        long novoId = notaFiscalRepository.proximoId();
        NotaFiscal nota = new NotaFiscal(novoId, fornecedor, usuarioLogado);
        notaFiscalRepository.salvar(nota);

        logRepository.salvar(new LogAcao(
                0,
                usuarioLogado.getRu(),
                "NOTA_ABERTA",
                "Nota #" + novoId + " aberta para fornecedor: " + fornecedor.getNome()
        ));

        return nota;
    }

    public void adicionarItem(Usuario usuarioLogado,
                              NotaFiscal nota,
                              Produto produto,
                              int quantidade,
                              BigDecimal precoUnitario) {
        validarPermissao(usuarioLogado, Permissao.EDITAR_ESTOQUE);
        validarNotaEditavel(nota);
        validarDadosItem(produto, quantidade, precoUnitario);

        fornecedorService.garantirProdutoVinculadoDuranteNota(
                nota.getFornecedor().getId(),
                produto,
                usuarioLogado != null ? usuarioLogado.getRu() : null
        );

        ItemNotaFiscal item = new ItemNotaFiscal(produto, quantidade, precoUnitario);
        nota.adicionarItem(item);
        notaFiscalRepository.salvar(nota);
    }

    /**
     * Adiciona um item criando ou reaproveitando um produto no proprio fluxo da nota.
     */
    public Produto adicionarItem(Usuario usuarioLogado,
                                 NotaFiscal nota,
                                 NovoProdutoInput novoProduto,
                                 int quantidade,
                                 BigDecimal precoUnitario) {
        validarPermissao(usuarioLogado, Permissao.EDITAR_ESTOQUE);
        validarNotaEditavel(nota);
        validarNovoProduto(novoProduto);
        validarDadosItem(null, quantidade, precoUnitario);

        Produto produto = resolverOuCriarProdutoInline(usuarioLogado, nota, novoProduto, precoUnitario);
        adicionarItem(usuarioLogado, nota, produto, quantidade, precoUnitario);
        return produto;
    }

    public void confirmarNota(Usuario usuarioLogado, NotaFiscal nota) {
        validarPermissao(usuarioLogado, Permissao.EDITAR_ESTOQUE);

        for (ItemNotaFiscal item : nota.getItens()) {
            estoqueService.buscarProdutoPorId(item.getProduto().getId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Produto '" + item.getProduto().getNome() +
                                    "' (ID: " + item.getProduto().getId() +
                                    ") nao encontrado no estoque. Confirme o cadastro do produto antes de confirmar a nota."
                    ));
        }

        for (ItemNotaFiscal item : nota.getItens()) {
            estoqueService.registrarMovimentacao(
                    item.getProduto().getId(),
                    item.getQuantidade(),
                    TipoMovimentacao.ENTRADA,
                    usuarioLogado
            );

            logRepository.salvar(new LogAcao(
                    0,
                    usuarioLogado.getRu(),
                    "MOVIMENTACAO_ESTOQUE",
                    "Entrada por nota #" + nota.getId() + ": produto '" +
                            item.getProduto().getNome() + "' (ID: " + item.getProduto().getId() +
                            "), quantidade: " + item.getQuantidade()
            ));
        }

        nota.confirmar();
        notaFiscalRepository.salvar(nota);

        logRepository.salvar(new LogAcao(
                0,
                usuarioLogado.getRu(),
                "NOTA_CONFIRMADA",
                "Nota #" + nota.getId() + " confirmada. " +
                        nota.getItens().size() + " item(ns), total R$" +
                        formatarValorMonetario(nota.calcularTotal())
        ));
    }

    public void registrarPagamento(Usuario usuarioLogado, NotaFiscal nota) {
        validarPermissao(usuarioLogado, Permissao.EDITAR_ESTOQUE);

        nota.registrarPagamento();
        notaFiscalRepository.salvar(nota);

        logRepository.salvar(new LogAcao(
                0,
                usuarioLogado.getRu(),
                "NOTA_PAGA",
                "Pagamento registrado para nota #" + nota.getId() +
                        " - R$" + formatarValorMonetario(nota.calcularTotal())
        ));
    }

    public void descartarRascunho(Usuario usuarioLogado, NotaFiscal nota) {
        validarPermissao(usuarioLogado, Permissao.EDITAR_ESTOQUE);
        if (nota.getStatus() != NotaFiscal.Status.PENDENTE) {
            throw new IllegalStateException("Apenas rascunhos PENDENTES podem ser descartados.");
        }
        if (!nota.getItens().isEmpty()) {
            throw new IllegalStateException("Apenas notas sem itens podem ser descartadas. Use cancelar() para notas com itens.");
        }
        notaFiscalRepository.deletar(nota.getId());
        logRepository.salvar(new LogAcao(
                0,
                usuarioLogado.getRu(),
                "NOTA_DESCARTADA",
                "Rascunho da nota #" + nota.getId() + " descartado."
        ));
    }

    public void cancelarNota(Usuario usuarioLogado, NotaFiscal nota) {
        validarPermissao(usuarioLogado, Permissao.EDITAR_ESTOQUE);
        nota.cancelar();
        notaFiscalRepository.salvar(nota);
        logRepository.salvar(new LogAcao(
                0,
                usuarioLogado.getRu(),
                "NOTA_CANCELADA",
                "Nota #" + nota.getId() + " cancelada."
        ));
    }

    public List<NotaFiscal> listarNotasPorFornecedor(long fornecedorId) {
        return notaFiscalRepository.buscarPorFornecedor(fornecedorId);
    }

    public List<NotaFiscal> listarTodas(boolean incluirVazias) {
        List<NotaFiscal> todas = notaFiscalRepository.listarTodos();
        if (incluirVazias) {
            return todas;
        }
        return todas.stream()
                .filter(n -> !(n.getStatus() == NotaFiscal.Status.PENDENTE && n.getItens().isEmpty()))
                .toList();
    }

    public List<NotaFiscal> listarTodas() {
        return listarTodas(false);
    }

    private void validarPermissao(Usuario usuario, Permissao permissao) {
        if (usuario == null
                || usuario.getClasse() == null
                || !usuario.getClasse().possuiPermissao(permissao)) {
            throw new SecurityException("Voce nao tem permissao para executar esta acao.");
        }
    }

    private void validarNotaEditavel(NotaFiscal nota) {
        if (nota == null) {
            throw new IllegalArgumentException("Nota fiscal e obrigatoria.");
        }
        if (nota.getStatus() != NotaFiscal.Status.PENDENTE) {
            throw new IllegalStateException("Apenas notas pendentes podem receber novos itens.");
        }
        if (nota.getFornecedor() == null || !nota.getFornecedor().isAtivo()) {
            throw new IllegalArgumentException("Fornecedor invalido ou inativo.");
        }
    }

    private void validarDadosItem(Produto produto, int quantidade, BigDecimal precoUnitario) {
        if (produto == null && quantidade < 0) {
            // no-op to keep the method signature symmetric for both item paths
        }
        if (quantidade <= 0) {
            throw new IllegalArgumentException("Quantidade deve ser maior que zero.");
        }
        if (precoUnitario == null) {
            throw new IllegalArgumentException("Preco unitario e obrigatorio.");
        }
        if (precoUnitario.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Preco unitario nao pode ser negativo.");
        }
    }

    private void validarNovoProduto(NovoProdutoInput novoProduto) {
        if (novoProduto == null) {
            throw new IllegalArgumentException("Dados do novo produto sao obrigatorios.");
        }
        if (novoProduto.nome() == null || novoProduto.nome().isBlank()) {
            throw new IllegalArgumentException("Nome do novo produto e obrigatorio.");
        }
        if (novoProduto.quantidadeMinima() != null && novoProduto.quantidadeMinima() < 0) {
            throw new IllegalArgumentException("Quantidade minima do produto nao pode ser negativa.");
        }
        if (novoProduto.precoUnitarioBase() != null
                && novoProduto.precoUnitarioBase().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Preco base do produto nao pode ser negativo.");
        }
        parseCategoriaEstoque(novoProduto.categoriaEstoque());
    }

    private Produto resolverOuCriarProdutoInline(Usuario usuarioLogado,
                                                 NotaFiscal nota,
                                                 NovoProdutoInput novoProduto,
                                                 BigDecimal precoUnitarioNota) {
        String nomeNormalizado = novoProduto.nome().trim();
        Produto produtoExistente = estoqueService.buscarProdutoPorNome(nomeNormalizado).orElse(null);
        if (produtoExistente != null) {
            return produtoExistente;
        }

        BigDecimal precoBase = novoProduto.precoUnitarioBase() != null
                ? novoProduto.precoUnitarioBase()
                : precoUnitarioNota;
        int quantidadeMinima = novoProduto.quantidadeMinima() != null
                ? novoProduto.quantidadeMinima()
                : 0;

        Produto produtoCriado = estoqueService.cadastrarProduto(
                usuarioLogado,
                nomeNormalizado,
                0,
                quantidadeMinima,
                precoBase,
                parseCategoriaEstoque(novoProduto.categoriaEstoque())
        );

        logRepository.salvar(new LogAcao(
                0,
                usuarioLogado.getRu(),
                "PRODUTO_CADASTRADO",
                "Produto '" + produtoCriado.getNome() + "' criado no fluxo da nota #" + nota.getId()
        ));

        return produtoCriado;
    }

    private CategoriaEstoque parseCategoriaEstoque(String categoria) {
        if (categoria == null || categoria.isBlank()) {
            return null;
        }
        try {
            return CategoriaEstoque.valueOf(categoria.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Categoria de estoque invalida.");
        }
    }

    private String formatarValorMonetario(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    public record NovoProdutoInput(
            String nome,
            Integer quantidadeMinima,
            BigDecimal precoUnitarioBase,
            String categoriaEstoque
    ) {}
}
