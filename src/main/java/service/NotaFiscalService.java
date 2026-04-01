package service;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
                              BigDecimal precoUnitario) {
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

        // Pré-validação: garante que todos os produtos existem no estoque antes de
        // iniciar qualquer mutação. Evita entrada parcial de estoque em caso de erro
        // no meio do loop.
        for (ItemNotaFiscal item : nota.getItens()) {
            estoqueService.buscarProdutoPorId(item.getProduto().getId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Produto '" + item.getProduto().getNome() +
                            "' (ID: " + item.getProduto().getId() +
                            ") não encontrado no estoque. Confirme o cadastro do produto antes de confirmar a nota."
                    ));
        }

        // Dá entrada no estoque para cada item da nota
        for (ItemNotaFiscal item : nota.getItens()) {
            estoqueService.registrarMovimentacao(
                    item.getProduto().getId(),
                    item.getQuantidade(),
                    TipoMovimentacao.ENTRADA,
                    usuarioLogado
            );

            // Garante trilha de auditoria explícita para cada entrada de estoque
            // originada pela confirmação da nota fiscal.
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
                        " — R$" + formatarValorMonetario(nota.calcularTotal())
        ));
    }

    // ── Etapa: Descartar rascunho ──────────────────────────────

    /**
     * Exclui permanentemente uma nota PENDENTE sem itens.
     * Usado para limpar ghost invoices do wizard.
     */
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
                0, usuarioLogado.getRu(),
                "NOTA_DESCARTADA",
                "Rascunho da nota #" + nota.getId() + " descartado."
        ));
    }

    // ── Etapa: Cancelar nota ───────────────────────────────────

    /**
     * Cancela uma nota PENDENTE.
     * CONFIRMADA e PAGA não podem ser canceladas.
     */
    public void cancelarNota(Usuario usuarioLogado, NotaFiscal nota) {
        validarPermissao(usuarioLogado, Permissao.EDITAR_ESTOQUE);
        nota.cancelar();
        notaFiscalRepository.salvar(nota);
        logRepository.salvar(new LogAcao(
                0, usuarioLogado.getRu(),
                "NOTA_CANCELADA",
                "Nota #" + nota.getId() + " cancelada."
        ));
    }

    // ── Consultas ──────────────────────────────────────────────

    public List<NotaFiscal> listarNotasPorFornecedor(long fornecedorId) {
        return notaFiscalRepository.buscarPorFornecedor(fornecedorId);
    }

    /**
     * Lista notas fiscais. Por padrão, oculta rascunhos vazios (PENDENTE sem itens).
     * Use incluirVazias=true para incluí-las.
     */
    public List<NotaFiscal> listarTodas(boolean incluirVazias) {
        List<NotaFiscal> todas = notaFiscalRepository.listarTodos();
        if (incluirVazias) return todas;
        return todas.stream()
                .filter(n -> !(n.getStatus() == NotaFiscal.Status.PENDENTE && n.getItens().isEmpty()))
                .toList();
    }

    public List<NotaFiscal> listarTodas() {
        return listarTodas(false);
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

    private String formatarValorMonetario(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
