package model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Representa uma nota fiscal de compra emitida por um fornecedor.
 *
 * <h2>Ciclo de vida (state machine)</h2>
 * <pre>
 *   PENDENTE ──► CONFIRMADA ──► PAGA
 *      │               │
 *      └───────────────┴──► CANCELADA
 * </pre>
 *
 * <ul>
 *   <li>Itens só podem ser adicionados/removidos enquanto a nota está {@code PENDENTE}.</li>
 *   <li>Uma nota {@code CONFIRMADA} afeta o estoque; cancela-la reverte esse efeito.</li>
 *   <li>Uma nota {@code PAGA} é imutável — não pode ser cancelada.</li>
 * </ul>
 *
 * <h2>Convenções monetárias</h2>
 * Todos os valores usam {@link BigDecimal} com escala 2 e arredondamento
 * {@link RoundingMode#HALF_UP}, seguindo o padrão contábil brasileiro (NBR 6023).
 * Nunca use {@code double} para dinheiro.
 */
@Entity
@Table(name = "notas_fiscais")
public class NotaFiscal {

    // ── Constantes ────────────────────────────────────────────────────────────

    private static final int    ESCALA_MONETARIA   = 2;
    private static final String FORMATO_DATA       = "dd/MM/yyyy HH:mm";

    // ── Status e transições válidas ──────────────────────────────────────────

    /**
     * Estados possíveis de uma nota fiscal.
     *
     * O EnumSet de transições válidas fica aqui, dentro do enum, para que
     * a regra de negócio fique junto de quem ela descreve — não espalhada
     * em ifs pelo código.
     */
    public enum Status {

        PENDENTE {
            @Override
            public Set<Status> transicoesPermitidas() {
                return EnumSet.of(CONFIRMADA, CANCELADA);
            }
        },
        CONFIRMADA {
            @Override
            public Set<Status> transicoesPermitidas() {
                return EnumSet.of(PAGA);
            }
        },
        PAGA {
            @Override
            public Set<Status> transicoesPermitidas() {
                // Nota paga é terminal: nenhuma transição permitida.
                return EnumSet.noneOf(Status.class);
            }
        },
        CANCELADA {
            @Override
            public Set<Status> transicoesPermitidas() {
                // Nota cancelada também é terminal.
                return EnumSet.noneOf(Status.class);
            }
        };

        /**
         * Retorna os estados para os quais este status pode avançar.
         * Usado por {@link NotaFiscal#transicionarPara} para validação central.
         */
        public abstract Set<Status> transicoesPermitidas();

        /** Verifica se a transição para {@code destino} é válida a partir deste status. */
        public boolean podeTransicionarPara(Status destino) {
            return transicoesPermitidas().contains(destino);
        }
    }

    // ── Campos imutáveis (definidos na criação, nunca mudam) ─────────────────

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "fornecedor_id", nullable = false)
    private Fornecedor fornecedor;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "usuario_responsavel_ru", nullable = false)
    private Usuario usuarioResponsavel;

    @Column(name = "data_emissao", nullable = false)
    private LocalDateTime dataEmissao;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "nota_fiscal_id", nullable = false)
    @OrderBy("id ASC")
    private List<ItemNotaFiscal> itens;

    // ── Campos mutáveis (evoluem com o ciclo de vida) ────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "data_confirmacao")
    private LocalDateTime dataConfirmacao;

    @Column(name = "data_pagamento")
    private LocalDateTime dataPagamento;

    @Column(name = "data_cancelamento")
    private LocalDateTime dataCancelamento;

    // ── Construtor ────────────────────────────────────────────────────────────

    protected NotaFiscal() {
        this.itens = new ArrayList<>();
    }

    /**
     * Cria uma nova nota fiscal no estado {@code PENDENTE}.
     *
     * <p>O {@code id} pode ser {@code 0} quando a nota ainda não foi persistida.
     * O repositório é responsável por atribuir o ID definitivo ao salvar.
     * IDs negativos indicam estado inválido e são rejeitados imediatamente.
     *
     * @param id                 identificador único, {@code >= 0} (0 = não persistido ainda)
     * @param fornecedor         fornecedor emissor da nota; não pode ser {@code null}
     * @param usuarioResponsavel usuário que criou a nota; não pode ser {@code null}
     * @throws IllegalArgumentException se qualquer argumento obrigatório for inválido
     */
    public NotaFiscal(long id, Fornecedor fornecedor, Usuario usuarioResponsavel) {
        validar(id >= 0,            "ID da nota não pode ser negativo.");
        validar(fornecedor != null,  "Fornecedor é obrigatório.");
        validar(usuarioResponsavel != null, "Usuário responsável é obrigatório.");

        this.id                  = id;
        this.fornecedor          = fornecedor;
        this.usuarioResponsavel  = usuarioResponsavel;
        this.itens               = new ArrayList<>();
        this.dataEmissao         = LocalDateTime.now();
        this.status              = Status.PENDENTE;
    }

    private NotaFiscal(long id,
                       Fornecedor fornecedor,
                       Usuario usuarioResponsavel,
                       LocalDateTime dataEmissao,
                       List<ItemNotaFiscal> itens,
                       Status status,
                       LocalDateTime dataConfirmacao,
                       LocalDateTime dataPagamento,
                       LocalDateTime dataCancelamento) {
        this.id = id;
        this.fornecedor = fornecedor;
        this.usuarioResponsavel = usuarioResponsavel;
        this.dataEmissao = dataEmissao;
        this.itens = new ArrayList<>(itens);
        this.status = status;
        this.dataConfirmacao = dataConfirmacao;
        this.dataPagamento = dataPagamento;
        this.dataCancelamento = dataCancelamento;
    }

    public NotaFiscal comId(long novoId) {
        validar(novoId > 0, "Novo ID da nota deve ser maior que zero.");
        return new NotaFiscal(
                novoId,
                fornecedor,
                usuarioResponsavel,
                dataEmissao,
                itens,
                status,
                dataConfirmacao,
                dataPagamento,
                dataCancelamento
        );
    }

    // ── Gerenciamento de itens ────────────────────────────────────────────────

    /**
     * Adiciona um item à nota.
     *
     * <p>Se já existir um item para o mesmo produto, as quantidades são somadas
     * em vez de criar uma linha duplicada (agrupamento automático).
     *
     * @param novoItem item a adicionar; não pode ser {@code null}
     * @throws IllegalStateException    se a nota não está mais em {@code PENDENTE}
     * @throws IllegalArgumentException se {@code novoItem} for {@code null}
     */
    public void adicionarItem(ItemNotaFiscal novoItem) {
        exigirStatusEditavel();
        validar(novoItem != null, "Item não pode ser nulo.");

        itens.stream()
                .filter(existente -> existente.podeSerAgrupadoCom(novoItem))
                .findFirst()
                .ifPresentOrElse(
                        existente -> existente.adicionarQuantidade(novoItem.getQuantidade()),
                        ()        -> itens.add(novoItem)
                );
    }

    /**
     * Remove todos os itens referentes ao produto informado.
     *
     * @param produtoId ID do produto a remover
     * @throws IllegalStateException    se a nota não está em {@code PENDENTE}
     * @throws IllegalArgumentException se nenhum item do produto existir na nota
     */
    public void removerItemPorProduto(long produtoId) {
        exigirStatusEditavel();

        boolean removido = itens.removeIf(item -> item.pertenceAoProduto(produtoId));

        validar(removido, "Nenhum item do produto informado foi encontrado na nota.");
    }

    /**
     * Busca o item referente a um produto específico.
     *
     * @param produtoId ID do produto
     * @return {@code Optional} com o item, ou vazio se não encontrado
     */
    public Optional<ItemNotaFiscal> buscarItemPorProduto(long produtoId) {
        return itens.stream()
                .filter(item -> item.pertenceAoProduto(produtoId))
                .findFirst();
    }

    /** Retorna {@code true} se existe ao menos um item para o produto informado. */
    public boolean temItemDoProduto(long produtoId) {
        return buscarItemPorProduto(produtoId).isPresent();
    }

    /** Retorna a soma das quantidades de todos os itens da nota. */
    public int calcularQuantidadeTotalItens() {
        return itens.stream()
                .mapToInt(ItemNotaFiscal::getQuantidade)
                .sum();
    }

    /**
     * Retorna uma visão imutável da lista de itens.
     *
     * <p>Use {@link #adicionarItem} e {@link #removerItemPorProduto} para
     * modificar — nunca manipule a lista diretamente.
     */
    public List<ItemNotaFiscal> getItens() {
        return Collections.unmodifiableList(itens);
    }

    // ── Transições de estado ──────────────────────────────────────────────────

    /**
     * Confirma a nota, avançando de {@code PENDENTE} para {@code CONFIRMADA}.
     *
     * <p>Uma nota confirmada sinaliza ao sistema de estoque que as mercadorias
     * devem ser contabilizadas.
     *
     * @throws IllegalStateException se a nota não estiver em {@code PENDENTE}
     *                               ou se não houver itens
     */
    public void confirmar() {
        if (itens.isEmpty()) {
            throw new IllegalStateException("Não é possível confirmar uma nota sem itens.");
        }
        transicionarPara(Status.CONFIRMADA);
        this.dataConfirmacao = LocalDateTime.now();
    }

    /**
     * Cancela a nota, avançando para {@code CANCELADA}.
     *
     * <p>Notas {@code PENDENTES} e {@code CONFIRMADAS} podem ser canceladas.
     * Notas {@code PAGAS} são imutáveis — o sistema deve emitir uma nota de
     * devolução em vez de cancelar diretamente.
     *
     * @throws IllegalStateException se a nota estiver em {@code PAGA} ou já {@code CANCELADA}
     */
    public void cancelar() {
        transicionarPara(Status.CANCELADA);
        this.dataCancelamento = LocalDateTime.now();
    }

    /**
     * Registra o pagamento, avançando de {@code CONFIRMADA} para {@code PAGA}.
     *
     * @throws IllegalStateException se a nota não estiver em {@code CONFIRMADA}
     */
    public void registrarPagamento() {
        transicionarPara(Status.PAGA);
        this.dataPagamento = LocalDateTime.now();
    }

    // ── Cálculos ──────────────────────────────────────────────────────────────

    /**
     * Calcula o valor total da nota somando os subtotais de todos os itens.
     *
     * <p>Sempre retorna {@link BigDecimal} com exatamente 2 casas decimais,
     * arredondado por {@code HALF_UP}. Nunca retorna {@code null}.
     */
    public BigDecimal calcularTotal() {
        return itens.stream()
                .map(ItemNotaFiscal::calcularSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
    }

    // ── Consultas de estado ───────────────────────────────────────────────────

    public boolean estaPendente()    { return status == Status.PENDENTE;    }
    public boolean estaConfirmada()  { return status == Status.CONFIRMADA;  }
    public boolean estaPaga()        { return status == Status.PAGA;        }
    public boolean estaCancelada()   { return status == Status.CANCELADA;   }

    /**
     * Indica se a nota ainda aceita alterações de itens.
     * Equivale a {@link #estaPendente()}, mas comunica intenção de forma mais
     * expressiva em contextos de validação.
     */
    public boolean podeSerEditada()  { return estaPendente(); }

    // ── Getters ───────────────────────────────────────────────────────────────

    public long             getId()                  { return id;                  }
    public Fornecedor       getFornecedor()           { return fornecedor;          }
    public Usuario          getUsuarioResponsavel()   { return usuarioResponsavel;  }
    public Status           getStatus()              { return status;              }
    public LocalDateTime    getDataEmissao()         { return dataEmissao;         }
    public LocalDateTime    getDataConfirmacao()     { return dataConfirmacao;     }
    public LocalDateTime    getDataPagamento()       { return dataPagamento;       }
    public LocalDateTime    getDataCancelamento()    { return dataCancelamento;    }

    // ── Infraestrutura de objeto ──────────────────────────────────────────────

    /**
     * Representação textual da nota, útil para logs e debug.
     *
     * <p>Não depende de estado calculado pesado — o total é calculado inline
     * apenas aqui. Evite chamar em loops de alta frequência.
     */
    @Override
    public String toString() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern(FORMATO_DATA);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format(
                "╔═ Nota Fiscal #%d ══════════════════════════════%n" +
                        "║  Fornecedor : %s%n"                               +
                        "║  Responsável: %s%n"                               +
                        "║  Emissão    : %s%n"                               +
                        "║  Status     : %s%n"                               +
                        "║  Total      : R$ %s%n"                            +
                        "╠═ Itens (%d) ═══════════════════════════════════%n",
                id,
                fornecedor.getNome(),
                usuarioResponsavel.getNomeCompleto(),
                dataEmissao.format(fmt),
                status,
                calcularTotal().toPlainString(),
                itens.size()
        ));

        if (itens.isEmpty()) {
            sb.append("║  (sem itens)\n");
        } else {
            itens.forEach(item ->
                    sb.append("║  • ").append(item.gerarDescricao()).append("\n")
            );
        }

        sb.append("╚══════════════════════════════════════════════\n");
        return sb.toString();
    }

    /**
     * Duas notas são iguais se tiverem o mesmo {@code id}.
     * Notas com {@code id == 0} (não persistidas) nunca são iguais entre si.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NotaFiscal other)) return false;
        // Notas não persistidas (id == 0) não devem ser consideradas iguais.
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    // ── Métodos auxiliares privados ───────────────────────────────────────────

    /**
     * Máquina de estados central.
     *
     * <p>Toda transição passa por aqui — nunca modifique {@code this.status}
     * diretamente fora deste método. Isso garante que nenhuma transição
     * inválida passe em branco.
     *
     * @param destino status desejado
     * @throws IllegalStateException se a transição não for permitida
     */
    private void transicionarPara(Status destino) {
        if (!status.podeTransicionarPara(destino)) {
            throw new IllegalStateException(String.format(
                    "Transição inválida: %s → %s. Transições permitidas a partir de %s: %s",
                    status, destino, status, status.transicoesPermitidas()
            ));
        }
        this.status = destino;
    }

    /**
     * Lança {@link IllegalStateException} se a nota não estiver em estado editável.
     * Centraliza a mensagem de erro para consistência.
     */
    private void exigirStatusEditavel() {
        if (!podeSerEditada()) {
            throw new IllegalStateException(
                    "Alteração de itens não permitida. Status atual: " + status
            );
        }
    }

    /**
     * Substituto mais limpo para o padrão {@code if (!cond) throw}.
     *
     * <p>Inspirado em {@link java.util.Objects#requireNonNull}, mas para
     * validações de negócio com {@code IllegalArgumentException}.
     *
     * @param condicao  expressão booleana que deve ser {@code true}
     * @param mensagem  mensagem da exceção caso a condição falhe
     */
    private static void validar(boolean condicao, String mensagem) {
        if (!condicao) throw new IllegalArgumentException(mensagem);
    }
}
