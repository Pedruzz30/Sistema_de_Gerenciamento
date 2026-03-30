package model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Representa uma nota fiscal de compra de um fornecedor.
 *
 * O fluxo de vida de uma nota é:
 *   PENDENTE → CONFIRMADA → PAGA
 *
 * Isso mapeia exatamente o fluxo que você descreveu:
 *   "confirmar fornecedor → confirmar produtos → confirmar pagamento"
 */
public class NotaFiscal {

    // Os estados possíveis de uma nota
    public enum Status {
        PENDENTE,    // Nota criada, ainda não confirmada
        CONFIRMADA,  // Produtos conferidos e aceitos
        PAGA,        // Pagamento registrado
        CANCELADA    // Nota cancelada antes da confirmação
    }

    private final long id;
    private final Fornecedor fornecedor;
    private final List<ItemNotaFiscal> itens;
    private final LocalDateTime dataEmissao;
    private final Usuario usuarioResponsavel;
    private Status status;

    public NotaFiscal(long id, Fornecedor fornecedor, Usuario usuarioResponsavel) {
        if (fornecedor == null) {
            throw new IllegalArgumentException("Fornecedor é obrigatório.");
        }
        if (usuarioResponsavel == null) {
            throw new IllegalArgumentException("Usuário responsável é obrigatório.");
        }

        this.id = id;
        this.fornecedor = fornecedor;
        this.usuarioResponsavel = usuarioResponsavel;
        this.itens = new ArrayList<>();
        this.dataEmissao = LocalDateTime.now();
        this.status = Status.PENDENTE; // toda nota começa como pendente
    }

    // ── Itens ─────────────────────────────────────────────────

    public void adicionarItem(ItemNotaFiscal item) {
        if (status != Status.PENDENTE) {
            throw new IllegalStateException(
                    "Não é possível adicionar itens a uma nota com status: " + status
            );
        }
        if (item == null) {
            throw new IllegalArgumentException("Item não pode ser nulo.");
        }
        itens.add(item);
    }

    public List<ItemNotaFiscal> getItens() {
        return Collections.unmodifiableList(itens);
    }

    // ── Transições de status ───────────────────────────────────

    /**
     * Confirma a nota (produtos conferidos).
     * Só pode ser chamado quando está PENDENTE.
     */
    public void confirmar() {
        if (status != Status.PENDENTE) {
            throw new IllegalStateException("Nota já foi confirmada ou paga.");
        }
        if (itens.isEmpty()) {
            throw new IllegalStateException("Não é possível confirmar uma nota sem itens.");
        }
        this.status = Status.CONFIRMADA;
    }

    /**
     * Cancela a nota. Só pode ser chamado quando está PENDENTE.
     * Notas CONFIRMADAS ou PAGAS não podem ser canceladas.
     */
    public void cancelar() {
        if (status != Status.PENDENTE) {
            throw new IllegalStateException(
                    "Apenas notas PENDENTES podem ser canceladas. Status atual: " + status
            );
        }
        this.status = Status.CANCELADA;
    }

    /**
     * Registra o pagamento da nota.
     * Só pode ser chamado quando está CONFIRMADA.
     */
    public void registrarPagamento() {
        if (status != Status.CONFIRMADA) {
            throw new IllegalStateException(
                    "Pagamento só pode ser registrado em notas CONFIRMADAS. Status atual: " + status
            );
        }
        this.status = Status.PAGA;
    }

    // ── Cálculos ───────────────────────────────────────────────

    /** Soma o subtotal de todos os itens da nota */
    public double calcularTotal() {
        return itens.stream()
                .mapToDouble(ItemNotaFiscal::calcularSubtotal)
                .sum();
    }

    // ── Getters ────────────────────────────────────────────────

    public long getId()                         { return id; }
    public Fornecedor getFornecedor()           { return fornecedor; }
    public LocalDateTime getDataEmissao()       { return dataEmissao; }
    public Usuario getUsuarioResponsavel()      { return usuarioResponsavel; }
    public Status getStatus()                   { return status; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("NotaFiscal{id=%d, fornecedor='%s', status=%s, total=R$%.2f}%n",
                id, fornecedor.getNome(), status, calcularTotal()));
        itens.forEach(item -> sb.append(item.toString()).append("\n"));
        return sb.toString();
    }
}