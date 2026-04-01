package model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Registro imutável de uma movimentação de estoque (entrada ou saída).
 *
 * <h2>Papel no domínio</h2>
 * {@code Pedido} é um <strong>registro de auditoria</strong>: cada vez que
 * o estoque de um produto é alterado, um {@code Pedido} é criado e nunca
 * mais modificado. Pense nele como a linha de um extrato bancário — imutável
 * após a gravação.
 *
 * <h2>Timestamp e identidade</h2>
 * O {@code dataHora} é capturado no momento da construção e <em>preservado</em>
 * pelo método {@link #comId}, que cria a cópia persistida sem alterar o
 * timestamp original. Sem isso, o repositório — ao criar um novo {@code Pedido}
 * só para atribuir o ID — deslocaria o timestamp em alguns milissegundos,
 * corrompendo a trilha de auditoria.
 *
 * <h2>Usuário opcional</h2>
 * {@code usuario} pode ser {@code null} em movimentações internas do sistema
 * (ex: ajuste automático de estoque). Use {@link #possuiUsuario()} antes
 * de acessar dados do usuário.
 *
 * <h2>Impacto no estoque</h2>
 * Use {@link #getImpactoAssinado()} para obter a variação líquida:
 * positiva para entradas, negativa para saídas. O switch é exaustivo —
 * se um novo {@link TipoMovimentacao} for adicionado, o compilador exigirá
 * tratamento aqui.
 */
public class Pedido {

    // ── Constantes ────────────────────────────────────────────────────────────

    private static final String FORMATO_DATA = "dd/MM/yyyy HH:mm:ss";

    // ── Campos — todos imutáveis ──────────────────────────────────────────────

    private final int              id;
    private final Produto          produto;
    private final int              quantidade;
    private final TipoMovimentacao tipo;
    private final LocalDateTime    dataHora;
    private final Usuario          usuario; // nullable por design

    // ── Construtor público ────────────────────────────────────────────────────

    /**
     * Cria um novo registro de movimentação capturando o timestamp atual.
     *
     * <p>{@code id == 0} indica que o pedido ainda não foi persistido.
     * O repositório deve chamar {@link #comId} para retornar a cópia com
     * o ID definitivo, preservando o {@code dataHora} original.
     *
     * @param id        identificador; {@code >= 0} (0 = não persistido)
     * @param produto   produto movimentado; não pode ser {@code null}
     * @param quantidade unidades movimentadas; deve ser {@code > 0}
     * @param tipo      direção da movimentação; não pode ser {@code null}
     * @param usuario   responsável pela movimentação; {@code null} é aceito
     *                  para operações automáticas do sistema
     * @throws IllegalArgumentException se qualquer argumento obrigatório for inválido
     */
    public Pedido(int id, Produto produto, int quantidade, TipoMovimentacao tipo, Usuario usuario) {
        validar(id >= 0,           "ID do pedido não pode ser negativo.");
        validar(produto != null,   "Produto é obrigatório.");
        validar(quantidade > 0,    "Quantidade deve ser maior que zero.");
        validar(tipo != null,      "Tipo de movimentação é obrigatório.");

        this.id        = id;
        this.produto   = produto;
        this.quantidade = quantidade;
        this.tipo      = tipo;
        this.usuario   = usuario;
        this.dataHora  = LocalDateTime.now();
    }

    /**
     * Construtor privado usado por {@link #comId} para preservar o timestamp.
     *
     * <p>Não exposto publicamente: o único caso de uso é a cópia com ID novo,
     * onde toda a validação já ocorreu na instância original.
     */
    private Pedido(int id, Produto produto, int quantidade, TipoMovimentacao tipo,
                   Usuario usuario, LocalDateTime dataHora) {
        this.id        = id;
        this.produto   = produto;
        this.quantidade = quantidade;
        this.tipo      = tipo;
        this.usuario   = usuario;
        this.dataHora  = dataHora; // timestamp original preservado
    }

    // ── Fábrica para repositório ──────────────────────────────────────────────

    /**
     * Retorna uma cópia deste pedido com o ID substituído, preservando o
     * {@code dataHora} original.
     *
     * <h3>Por que isso importa?</h3>
     * Sem este método, o repositório precisaria chamar
     * {@code new Pedido(novoId, produto, qtd, tipo, usuario)}, que invocaria
     * {@code LocalDateTime.now()} de novo — deslocando o timestamp alguns
     * milissegundos e corrompendo a trilha de auditoria.
     *
     * <p>Uso esperado no repositório:
     * <pre>
     *   Pedido persistido = pedido.comId(sequenceId.getAndIncrement());
     *   lista.add(persistido);
     *   return persistido;
     * </pre>
     *
     * @param novoId ID definitivo atribuído pelo repositório; deve ser {@code > 0}
     * @return novo {@code Pedido} com o ID fornecido e o timestamp original
     */
    public Pedido comId(int novoId) {
        validar(novoId > 0, "Novo ID deve ser maior que zero.");
        return new Pedido(novoId, produto, quantidade, tipo, usuario, dataHora);
    }

    // ── Semântica de movimentação ─────────────────────────────────────────────

    /** @return {@code true} se esta movimentação é uma entrada de mercadoria */
    public boolean ehEntrada() {
        return tipo == TipoMovimentacao.ENTRADA;
    }

    /** @return {@code true} se esta movimentação é uma saída de mercadoria */
    public boolean ehSaida() {
        return tipo == TipoMovimentacao.SAIDA;
    }

    /**
     * Retorna o impacto líquido desta movimentação no estoque.
     *
     * <ul>
     *   <li>Entradas retornam valor <strong>positivo</strong> (+quantidade).</li>
     *   <li>Saídas retornam valor <strong>negativo</strong> (-quantidade).</li>
     * </ul>
     *
     * <p>O switch é exaustivo: se um novo valor for adicionado a
     * {@link TipoMovimentacao}, o compilador exigirá tratamento aqui —
     * prevenindo bugs silenciosos.
     *
     * @return variação líquida de estoque com sinal
     */
    public int getImpactoAssinado() {
        return switch (tipo) {
            case ENTRADA ->  quantidade;
            case SAIDA   -> -quantidade;
        };
    }

    // ── Exibição ──────────────────────────────────────────────────────────────

    /**
     * Gera um resumo legível da movimentação para logs e exibição.
     *
     * <pre>
     *   ENTRADA de 50 unidade(s) de 'Laranja' em 31/03/2026 14:22:05 por Maria Gerente
     * </pre>
     */
    public String gerarResumo() {
        return String.format(
                "%s de %d unidade(s) de '%s' em %s por %s",
                tipo,
                quantidade,
                produto.getNome(),
                dataHora.format(DateTimeFormatter.ofPattern(FORMATO_DATA)),
                possuiUsuario() ? usuario.getNomeCompleto() : "sistema"
        );
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public int              getId()        { return id;        }
    public Produto          getProduto()   { return produto;   }
    public int              getQuantidade(){ return quantidade; }
    public TipoMovimentacao getTipo()      { return tipo;      }
    public LocalDateTime    getDataHora()  { return dataHora;  }
    public Usuario          getUsuario()   { return usuario;   }

    /** @return {@code true} se esta movimentação tem um usuário responsável associado */
    public boolean possuiUsuario() {
        return usuario != null;
    }

    // ── Infraestrutura de objeto ──────────────────────────────────────────────

    /**
     * Delega para {@link #gerarResumo()} — única fonte de verdade para
     * a representação textual do pedido.
     */
    @Override
    public String toString() {
        return "Pedido{id=" + id + ", " + gerarResumo() + "}";
    }

    /**
     * Dois pedidos são iguais se tiverem o mesmo {@code id}.
     * Pedidos com {@code id == 0} (não persistidos) nunca são iguais entre si.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Pedido other)) return false;
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    // ── Auxiliar privado ──────────────────────────────────────────────────────

    /** Lança {@link IllegalArgumentException} se a condição for falsa. */
    private static void validar(boolean condicao, String mensagem) {
        if (!condicao) throw new IllegalArgumentException(mensagem);
    }
}