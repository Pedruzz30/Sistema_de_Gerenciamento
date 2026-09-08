package model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(
        name = "registros_idempotencia",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_registros_idempotencia_escopo",
                columnNames = {"filial_id", "operacao", "idempotency_key"}
        )
)
public class RegistroIdempotencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(name = "filial_id", nullable = false)
    private Long filialId;

    @Column(nullable = false, length = 80)
    private String operacao;

    @Column(name = "idempotency_key", nullable = false, length = 120)
    private String idempotencyKey;

    @Column(name = "payload_hash", nullable = false, length = 128)
    private String payloadHash;

    @Column(name = "recurso_id")
    private Long recursoId;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private StatusRegistroIdempotencia status;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    protected RegistroIdempotencia() {
        // JPA
    }

    public RegistroIdempotencia(long id,
                                Long filialId,
                                String operacao,
                                String idempotencyKey,
                                String payloadHash,
                                Long recursoId) {
        this(id, filialId, operacao, idempotencyKey, payloadHash, recursoId, StatusRegistroIdempotencia.CONCLUIDA);
    }

    private RegistroIdempotencia(long id,
                                 Long filialId,
                                 String operacao,
                                 String idempotencyKey,
                                 String payloadHash,
                                 Long recursoId,
                                 StatusRegistroIdempotencia status) {
        validar(id >= 0, "ID do registro de idempotencia nao pode ser negativo.");
        validar(filialId != null && filialId > 0, "Filial da idempotencia e obrigatoria.");
        validar(operacao != null && !operacao.isBlank(), "Operacao da idempotencia e obrigatoria.");
        validar(idempotencyKey != null && !idempotencyKey.isBlank(), "Chave de idempotencia e obrigatoria.");
        validar(payloadHash != null && !payloadHash.isBlank(), "Hash do payload e obrigatorio.");
        validar(status != null, "Status da idempotencia e obrigatorio.");
        validar(recursoId == null || recursoId > 0, "Recurso da idempotencia deve ser maior que zero.");
        if (status == StatusRegistroIdempotencia.CONCLUIDA) {
            validar(recursoId != null && recursoId > 0, "Recurso da idempotencia e obrigatorio.");
        }

        this.id = id;
        this.filialId = filialId;
        this.operacao = operacao.trim();
        this.idempotencyKey = idempotencyKey.trim();
        this.payloadHash = payloadHash.trim();
        this.recursoId = recursoId;
        this.status = status;
        this.criadoEm = LocalDateTime.now();
    }

    public static RegistroIdempotencia iniciar(Long filialId,
                                               String operacao,
                                               String idempotencyKey,
                                               String payloadHash,
                                               Long recursoId) {
        return new RegistroIdempotencia(
                0,
                filialId,
                operacao,
                idempotencyKey,
                payloadHash,
                recursoId,
                StatusRegistroIdempotencia.PROCESSANDO
        );
    }

    public RegistroIdempotencia comId(long novoId) {
        validar(novoId > 0, "Novo ID da idempotencia deve ser maior que zero.");
        RegistroIdempotencia copia = new RegistroIdempotencia(
                novoId,
                filialId,
                operacao,
                idempotencyKey,
                payloadHash,
                recursoId,
                getStatus()
        );
        copia.criadoEm = criadoEm;
        return copia;
    }

    public void concluir(Long recursoId) {
        validar(recursoId != null && recursoId > 0, "Recurso da idempotencia e obrigatorio.");
        this.recursoId = recursoId;
        this.status = StatusRegistroIdempotencia.CONCLUIDA;
    }

    public boolean mesmoPayload(String outroHash) {
        return payloadHash.equals(outroHash);
    }

    public boolean estaConcluida() {
        return getStatus() == StatusRegistroIdempotencia.CONCLUIDA;
    }

    public boolean estaProcessando() {
        return getStatus() == StatusRegistroIdempotencia.PROCESSANDO;
    }

    @PrePersist
    void prePersist() {
        if (criadoEm == null) {
            criadoEm = LocalDateTime.now();
        }
    }

    public long getId() { return id; }
    public Long getFilialId() { return filialId; }
    public String getOperacao() { return operacao; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getPayloadHash() { return payloadHash; }
    public Long getRecursoId() { return recursoId; }
    public StatusRegistroIdempotencia getStatus() {
        return status == null ? StatusRegistroIdempotencia.CONCLUIDA : status;
    }
    public LocalDateTime getCriadoEm() { return criadoEm; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RegistroIdempotencia other)) return false;
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    private static void validar(boolean condicao, String mensagem) {
        if (!condicao) throw new IllegalArgumentException(mensagem);
    }
}
