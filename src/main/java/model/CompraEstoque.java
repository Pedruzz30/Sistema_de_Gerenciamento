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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "compras_estoque")
public class CompraEstoque {

    public enum Status {
        RASCUNHO,
        CONFIRMADA,
        CANCELADA
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(name = "filial_id", nullable = false)
    private Long filialId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fornecedor_id")
    private Fornecedor fornecedor;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "compra_id", nullable = false)
    @OrderBy("id ASC")
    private List<ItemCompraEstoque> itens;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "usuario_criacao_id")
    private Long usuarioCriacaoId;

    @Column(name = "usuario_confirmacao_id")
    private Long usuarioConfirmacaoId;

    @Column(name = "data_criacao", nullable = false)
    private LocalDateTime dataCriacao;

    @Column(name = "data_confirmacao")
    private LocalDateTime dataConfirmacao;

    @Column(name = "idempotency_key", length = 120)
    private String idempotencyKey;

    @Column(name = "payload_hash", length = 128)
    private String payloadHash;

    protected CompraEstoque() {
        this.itens = new ArrayList<>();
    }

    public CompraEstoque(long id, Long filialId, Fornecedor fornecedor, Long usuarioCriacaoId) {
        validar(id >= 0, "ID da compra nao pode ser negativo.");
        validar(filialId != null && filialId > 0, "Filial da compra e obrigatoria.");

        this.id = id;
        this.filialId = filialId;
        this.fornecedor = fornecedor;
        this.usuarioCriacaoId = usuarioCriacaoId;
        this.status = Status.RASCUNHO;
        this.dataCriacao = LocalDateTime.now();
        this.itens = new ArrayList<>();
    }

    private CompraEstoque(long id,
                          Long filialId,
                          Fornecedor fornecedor,
                          List<ItemCompraEstoque> itens,
                          Status status,
                          Long usuarioCriacaoId,
                          Long usuarioConfirmacaoId,
                          LocalDateTime dataCriacao,
                          LocalDateTime dataConfirmacao,
                          String idempotencyKey,
                          String payloadHash) {
        this.id = id;
        this.filialId = filialId;
        this.fornecedor = fornecedor;
        this.itens = new ArrayList<>(itens);
        this.status = status;
        this.usuarioCriacaoId = usuarioCriacaoId;
        this.usuarioConfirmacaoId = usuarioConfirmacaoId;
        this.dataCriacao = dataCriacao;
        this.dataConfirmacao = dataConfirmacao;
        this.idempotencyKey = idempotencyKey;
        this.payloadHash = payloadHash;
    }

    public CompraEstoque comId(long novoId) {
        validar(novoId > 0, "Novo ID da compra deve ser maior que zero.");
        return new CompraEstoque(
                novoId,
                filialId,
                fornecedor,
                itens,
                status,
                usuarioCriacaoId,
                usuarioConfirmacaoId,
                dataCriacao,
                dataConfirmacao,
                idempotencyKey,
                payloadHash
        );
    }

    public void adicionarItem(ItemCompraEstoque item) {
        exigirRascunho();
        validar(item != null, "Item da compra e obrigatorio.");
        itens.add(item);
    }

    public void confirmar(Long usuarioId, String idempotencyKey, String payloadHash) {
        exigirRascunho();
        validar(!itens.isEmpty(), "Nao e possivel confirmar compra sem itens.");
        this.status = Status.CONFIRMADA;
        this.usuarioConfirmacaoId = usuarioId;
        this.dataConfirmacao = LocalDateTime.now();
        this.idempotencyKey = normalizar(idempotencyKey);
        this.payloadHash = normalizar(payloadHash);
    }

    public BigDecimal calcularTotal() {
        return itens.stream()
                .map(ItemCompraEstoque::getCustoTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(4, RoundingMode.HALF_UP);
    }

    public boolean estaRascunho() { return status == Status.RASCUNHO; }
    public boolean estaConfirmada() { return status == Status.CONFIRMADA; }

    public long getId() { return id; }
    public Long getFilialId() { return filialId; }
    public Fornecedor getFornecedor() { return fornecedor; }
    public List<ItemCompraEstoque> getItens() { return Collections.unmodifiableList(itens); }
    public Status getStatus() { return status; }
    public Long getUsuarioCriacaoId() { return usuarioCriacaoId; }
    public Long getUsuarioConfirmacaoId() { return usuarioConfirmacaoId; }
    public LocalDateTime getDataCriacao() { return dataCriacao; }
    public LocalDateTime getDataConfirmacao() { return dataConfirmacao; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getPayloadHash() { return payloadHash; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CompraEstoque other)) return false;
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    private void exigirRascunho() {
        if (!estaRascunho()) {
            throw new IllegalStateException("Apenas compras em rascunho podem ser alteradas.");
        }
    }

    private static String normalizar(String valor) {
        if (valor == null) return null;
        String texto = valor.trim();
        return texto.isEmpty() ? null : texto;
    }

    private static void validar(boolean condicao, String mensagem) {
        if (!condicao) throw new IllegalArgumentException(mensagem);
    }
}
