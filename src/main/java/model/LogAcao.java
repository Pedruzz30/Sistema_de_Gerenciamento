package model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Registro imutável de uma ação auditável realizada no sistema.
 *
 * <h2>Papel no domínio</h2>
 * {@code LogAcao} é a entrada de uma <strong>trilha de auditoria</strong>
 * (audit trail): cada operação relevante — login, abertura de caixa, confirmação
 * de nota, cadastro de funcionário — gera um {@code LogAcao} que nunca é
 * alterado após a gravação. Pense nele como a linha de um livro-caixa contábil:
 * imutável, sequencial e permanente.
 *
 * <h2>Usuário opcional</h2>
 * {@code usuarioRu} pode ser {@code null} quando a ação é executada pelo
 * próprio sistema (ex: rotina de consolidação automática de caixas).
 * Use {@link #possuiUsuario()} antes de interpretar o valor.
 *
 * <h2>Timestamp</h2>
 * O {@code dataHora} é capturado no construtor público e <em>preservado</em>
 * por {@link #comId}, que retorna a cópia persistida sem alterar o instante
 * original. Sem isso, o repositório deslocaria o timestamp ao criar um novo
 * {@code LogAcao} apenas para atribuir o ID, corrompendo a trilha.
 *
 * <h2>Campo {@code acao}</h2>
 * Atualmente uma {@code String} com constantes como {@code "CAIXA_ABERTO"} ou
 * {@code "NOTA_CONFIRMADA"}. O próximo passo natural é criar um enum
 * {@code TipoAcao} para eliminar magic strings e ganhar checagem em tempo de
 * compilação — refatoração simples e de alto impacto quando o sistema estiver
 * estável.
 */
public class LogAcao {

    // ── Constante ─────────────────────────────────────────────────────────────

    private static final String FORMATO_DATA = "dd/MM/yyyy HH:mm:ss";

    // ── Campos — todos imutáveis ──────────────────────────────────────────────

    private final long          id;
    private final Long          usuarioRu;   // nullable: null = ação do sistema
    private final String        acao;
    private final String        descricao;
    private final LocalDateTime dataHora;

    // ── Construtor público ────────────────────────────────────────────────────

    /**
     * Cria um novo registro de auditoria capturando o timestamp atual.
     *
     * <p>{@code id == 0} indica que o log ainda não foi persistido.
     * O repositório deve usar {@link #comId} para retornar a cópia
     * com o ID definitivo, preservando o {@code dataHora} original.
     *
     * @param id         identificador; {@code >= 0} (0 = não persistido)
     * @param usuarioRu  RU do usuário responsável; {@code null} para ações do sistema
     * @param acao       código da ação realizada (ex: {@code "CAIXA_ABERTO"});
     *                   não pode ser nulo ou vazio
     * @param descricao  descrição detalhada da ação; {@code null} vira string vazia
     * @throws IllegalArgumentException se {@code id < 0} ou {@code acao} for inválida
     */
    public LogAcao(long id, Long usuarioRu, String acao, String descricao) {
        validar(id >= 0,                       "ID do log não pode ser negativo.");
        validar(acao != null && !acao.isBlank(),"Ação é obrigatória e não pode ser vazia.");

        this.id        = id;
        this.usuarioRu = usuarioRu;
        this.acao      = acao.trim().toUpperCase();
        this.descricao = descricao != null ? descricao.trim() : "";
        this.dataHora  = LocalDateTime.now();
    }

    /**
     * Construtor privado usado por {@link #comId} para preservar o timestamp.
     *
     * <p>Não exposto publicamente: o único caso de uso é a cópia com ID novo,
     * onde a validação já ocorreu na instância original.
     */
    private LogAcao(long id, Long usuarioRu, String acao, String descricao,
                    LocalDateTime dataHora) {
        this.id        = id;
        this.usuarioRu = usuarioRu;
        this.acao      = acao;
        this.descricao = descricao;
        this.dataHora  = dataHora; // timestamp original preservado
    }

    // ── Fábrica para repositório ──────────────────────────────────────────────

    /**
     * Retorna uma cópia deste log com o ID substituído, preservando o
     * {@code dataHora} original.
     *
     * <h3>Por que isso importa?</h3>
     * Sem este método, o repositório chamaria
     * {@code new LogAcao(novoId, ru, acao, descricao)}, invocando
     * {@code LocalDateTime.now()} de novo — deslocando o timestamp de
     * <em>todos os logs do sistema</em> e tornando a trilha de auditoria
     * imprecisa.
     *
     * <p>Uso esperado no repositório:
     * <pre>
     *   LogAcao persistido = log.comId(sequenceId.getAndIncrement());
     *   lista.add(persistido);
     *   return persistido;
     * </pre>
     *
     * @param novoId ID definitivo atribuído pelo repositório; deve ser {@code > 0}
     * @return novo {@code LogAcao} com o ID fornecido e o timestamp original
     */
    public LogAcao comId(long novoId) {
        validar(novoId > 0, "Novo ID deve ser maior que zero.");
        return new LogAcao(novoId, usuarioRu, acao, descricao, dataHora);
    }

    // ── Consultas ─────────────────────────────────────────────────────────────

    /**
     * @return {@code true} se esta ação foi executada por um usuário identificado;
     *         {@code false} se foi uma ação automática do sistema
     */
    public boolean possuiUsuario() {
        return usuarioRu != null;
    }

    // ── Exibição ──────────────────────────────────────────────────────────────

    /**
     * Gera uma linha de log legível para terminal ou arquivo.
     *
     * <pre>
     *   [31/03/2026 14:22:05] CAIXA_ABERTO (RU:3) — Caixa 1 aberto com saldo R$200,00
     *   [31/03/2026 14:25:10] LOGIN_FALHA  (sistema) — Tentativa inválida para Admin Superior
     * </pre>
     */
    public String gerarResumo() {
        String autor = possuiUsuario() ? "RU:" + usuarioRu : "sistema";
        return String.format("[%s] %-30s (%s) — %s",
                dataHora.format(DateTimeFormatter.ofPattern(FORMATO_DATA)),
                acao,
                autor,
                descricao.isEmpty() ? "(sem descrição)" : descricao
        );
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public long          getId()        { return id;        }
    public Long          getUsuarioRu() { return usuarioRu; }
    public String        getAcao()      { return acao;      }
    public String        getDescricao() { return descricao; }
    public LocalDateTime getDataHora()  { return dataHora;  }

    // ── Infraestrutura de objeto ──────────────────────────────────────────────

    /**
     * Dois logs são iguais se tiverem o mesmo {@code id}.
     * Logs com {@code id == 0} (não persistidos) nunca são iguais entre si.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LogAcao other)) return false;
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    /** Delega para {@link #gerarResumo()} — saída rica para logs e debug. */
    @Override
    public String toString() {
        return gerarResumo();
    }

    // ── Auxiliar privado ──────────────────────────────────────────────────────

    /** Lança {@link IllegalArgumentException} se a condição for falsa. */
    private static void validar(boolean condicao, String mensagem) {
        if (!condicao) throw new IllegalArgumentException(mensagem);
    }
}

