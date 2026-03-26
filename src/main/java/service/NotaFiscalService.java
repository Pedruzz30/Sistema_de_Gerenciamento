package service;

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

import java.util.List;

/**
 * Gerencia o ciclo de vida completo de uma nota fiscal.
 *
 * O fluxo tem 3 etapas obrigatórias, nessa ordem:
 *
 *   1. abrirNota()         → cria a nota vinculada ao fornecedor
 *   2. adicionarItem()     → adiciona os produtos da nota
 *   3. confirmarNota()     → confirma os produtos recebidos
 *   4. registrarPagamento()→ fecha o pagamento
 *
 * O EstoqueService é chamado internamente em confirmarNota()
 * para dar entrada dos produtos no estoque automaticamente.
 */
public class NotaFiscalService {

    private final NotaFiscalRepository notaFiscalRepository;
    private final LogRepository logRepository;
    private final EstoqueService estoqueService;

    public NotaFiscalService(NotaFiscalRepository notaFiscalRepository,
                             LogRepository logRepository,
                             EstoqueService estoqueService) {
        this.notaFiscalRepository = notaFiscalRepository;
        this.logRepository = logRepository;
        this.estoqueService = estoqueService;
    }

    // ── Etapa 1: Abrir nota ────────────────────────────────────

    /**
     * Cria uma nota fiscal em aberto para um fornecedor.
     * O usuário precisa ter permissão de EDITAR_ESTOQUE.
     */
    public NotaFiscal abrirNota(Usuario usuarioLogado, Fornecedor fornecedor) {
        validarPermissao(usuarioLogado, Permissao.EDITAR_ESTOQUE);

        if (fornecedor == null || !fornecedor.isAtivo()) {
            throw new IllegalArgumentException("Fornecedor inválido ou inativo.");
        }

        // O ID da nota é gerado pelo repositório
        long novoId = ((repository.InMemoryNotaFiscalRepository) notaFiscalRepository).proximoId();
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

    // ── Etapa 2: Adicionar itens ───────────────────────────────

    /**
     * Adiciona um produto à nota ainda aberta.
     *
     * Analogia: é como ir digitando cada linha da nota fiscal
     * conforme você confere as caixas que chegaram.
     */
    public void adicionarItem(Usuario usuarioLogado,
                              NotaFiscal nota,
                              Produto produto,
                              int quantidade,
                              double precoUnitario) {
        validarPermissao(usuarioLogado, Permissao.EDITAR_ESTOQUE);

        // Valida que o produto pertence ao fornecedor da nota
        boolean produtoDoFornecedor = nota.getFornecedor().getProdutos().stream()
                .anyMatch(p -> p.getId() == produto.getId());

        if (!produtoDoFornecedor) {
            throw new IllegalArgumentException(
                    "Produto '" + produto.getNome() + "' não está vinculado ao fornecedor '"
                            + nota.getFornecedor().getNome() + "'."
            );
        }

        ItemNotaFiscal item = new ItemNotaFiscal(produto, quantidade, precoUnitario);
        nota.adicionarItem(item);
        notaFiscalRepository.salvar(nota);
    }

    // ── Etapa 3: Confirmar nota ────────────────────────────────

    /**
     * Confirma os produtos da nota e dá entrada no estoque automaticamente.
     *
     * Esse é o momento em que o estoque é atualizado — não antes.
     * Se algo der errado na entrada, a nota NÃO é confirmada.
     */
    public void confirmarNota(Usuario usuarioLogado, NotaFiscal nota) {
        validarPermissao(usuarioLogado, Permissao.EDITAR_ESTOQUE);

        // Dá entrada no estoque para cada item da nota
        for (ItemNotaFiscal item : nota.getItens()) {
            estoqueService.registrarMovimentacao(
                    item.getProduto().getId(),
                    item.getQuantidade(),
                    TipoMovimentacao.ENTRADA,
                    usuarioLogado
            );
        }

        nota.confirmar();
        notaFiscalRepository.salvar(nota);

        logRepository.salvar(new LogAcao(
                0,
                usuarioLogado.getRu(),
                "NOTA_CONFIRMADA",
                "Nota #" + nota.getId() + " confirmada. " +
                        nota.getItens().size() + " item(ns), total R$" +
                        String.format("%.2f", nota.calcularTotal())
        ));
    }

    // ── Etapa 4: Registrar pagamento ───────────────────────────

    public void registrarPagamento(Usuario usuarioLogado, NotaFiscal nota) {
        validarPermissao(usuarioLogado, Permissao.EDITAR_ESTOQUE);

        nota.registrarPagamento();
        notaFiscalRepository.salvar(nota);

        logRepository.salvar(new LogAcao(
                0,
                usuarioLogado.getRu(),
                "NOTA_PAGA",
                "Pagamento registrado para nota #" + nota.getId() +
                        " — R$" + String.format("%.2f", nota.calcularTotal())
        ));
    }

    // ── Consultas ──────────────────────────────────────────────

    public List<NotaFiscal> listarNotasPorFornecedor(long fornecedorId) {
        return notaFiscalRepository.buscarPorFornecedor(fornecedorId);
    }

    public List<NotaFiscal> listarTodas() {
        return notaFiscalRepository.listarTodos();
    }

    // ── Validação interna ──────────────────────────────────────

    private void validarPermissao(Usuario usuario, Permissao permissao) {
        if (usuario == null
                || usuario.getClasse() == null
                || !usuario.getClasse().possuiPermissao(permissao)) {
            throw new SecurityException(
                    "Você não tem permissão para executar esta ação."
            );
        }
    }
}