package model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Sessão de login. O banco guarda só o hash do token: quem ler a tabela não
 * consegue se passar por ninguém.
 *
 * <p>{@code viaPin}: a pessoa atual entrou por PIN na troca de operador. O PIN é
 * curto, então essa sessão só alcança a área de caixa, mesmo que seja do dono.
 */
@Entity
@Table(name = "sessoes_usuario")
public class SessaoUsuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "usuario_ru", nullable = false)
    private Usuario usuario;

    @Column(name = "via_pin", nullable = false)
    private boolean viaPin;

    @Column(name = "criada_em", nullable = false)
    private LocalDateTime criadaEm;

    @Column(name = "expira_em", nullable = false)
    private LocalDateTime expiraEm;

    protected SessaoUsuario() {
        // JPA
    }

    public SessaoUsuario(String tokenHash, Usuario usuario, LocalDateTime criadaEm, LocalDateTime expiraEm) {
        this.tokenHash = Objects.requireNonNull(tokenHash);
        this.usuario = Objects.requireNonNull(usuario);
        this.criadaEm = Objects.requireNonNull(criadaEm);
        this.expiraEm = Objects.requireNonNull(expiraEm);
        this.viaPin = false;
    }

    public void trocarOperador(Usuario novoOperador) {
        this.usuario = Objects.requireNonNull(novoOperador);
        this.viaPin = true;
    }

    public boolean expirada(LocalDateTime agora) {
        return !agora.isBefore(expiraEm);
    }

    public void encerrar(LocalDateTime agora) {
        this.expiraEm = agora;
    }

    public long getId() { return id; }
    public String getTokenHash() { return tokenHash; }
    public Usuario getUsuario() { return usuario; }
    public boolean isViaPin() { return viaPin; }
    public LocalDateTime getExpiraEm() { return expiraEm; }

    /** Uso do repositório em memória (o banco gera o ID). */
    public void atribuirIdGerado(long novoId) {
        if (this.id != 0 || novoId <= 0) {
            throw new IllegalStateException("ID invalido para sessao.");
        }
        this.id = novoId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SessaoUsuario other)) return false;
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
