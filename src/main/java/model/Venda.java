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
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "vendas")
public class Venda {

    public static final int ESCALA_DINHEIRO = 4;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(name = "filial_id", nullable = false)
    private Long filialId;

    @Column(nullable = false, length = 40)
    private String numero;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StatusVenda status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private CanalVenda canal;

    @Column(nullable = false, precision = 19, scale = ESCALA_DINHEIRO)
    private BigDecimal subtotal;

    @Column(name = "desconto_total", nullable = false, precision = 19, scale = ESCALA_DINHEIRO)
    private BigDecimal descontoTotal;

    @Column(name = "acrescimo_total", nullable = false, precision = 19, scale = ESCALA_DINHEIRO)
    private BigDecimal acrescimoTotal;

    @Column(name = "valor_total", nullable = false, precision = 19, scale = ESCALA_DINHEIRO)
    private BigDecimal valorTotal;

    @Column(name = "custo_ingredientes_total", nullable = false, precision = 19, scale = ESCALA_DINHEIRO)
    private BigDecimal custoIngredientesTotal;

    @Column(name = "margem_bruta_estimada_total", nullable = false, precision = 19, scale = ESCALA_DINHEIRO)
    private BigDecimal margemBrutaEstimadaTotal;

    @Column(name = "idempotency_key", length = 120)
    private String idempotencyKey;

    @Column(name = "numero_caixa_legado")
    private Integer numeroCaixaLegado;

    @Column(name = "criada_em", nullable = false)
    private LocalDateTime criadaEm;

    @Column(name = "criada_por")
    private Long criadaPor;

    @Column(name = "finalizada_em")
    private LocalDateTime finalizadaEm;

    @Column(name = "finalizada_por")
    private Long finalizadaPor;

    @Column(name = "cancelada_em")
    private LocalDateTime canceladaEm;

    @Column(name = "cancelada_por")
    private Long canceladaPor;

    @Column(name = "motivo_cancelamento", length = 500)
    private String motivoCancelamento;

    @Version
    private long version;

    protected Venda() {
        // JPA
    }

    public Venda(long id, Long filialId, String numero, CanalVenda canal, Long criadaPor) {
        validar(id >= 0, "ID da venda nao pode ser negativo.");
        validar(filialId != null && filialId > 0, "Filial da venda e obrigatoria.");
        validar(numero != null && !numero.isBlank(), "Numero da venda e obrigatorio.");
        validar(canal != null, "Canal da venda e obrigatorio.");

        this.id = id;
        this.filialId = filialId;
        this.numero = numero.trim();
        this.canal = canal;
        this.status = StatusVenda.RASCUNHO;
        this.subtotal = dinheiro(BigDecimal.ZERO);
        this.descontoTotal = dinheiro(BigDecimal.ZERO);
        this.acrescimoTotal = dinheiro(BigDecimal.ZERO);
        this.valorTotal = dinheiro(BigDecimal.ZERO);
        this.custoIngredientesTotal = dinheiro(BigDecimal.ZERO);
        this.margemBrutaEstimadaTotal = dinheiro(BigDecimal.ZERO);
        this.criadaPor = criadaPor;
        this.criadaEm = LocalDateTime.now();
    }

    public void exigirRascunho() {
        if (status != StatusVenda.RASCUNHO) {
            throw new IllegalStateException("VENDA_NAO_ESTA_EM_RASCUNHO");
        }
    }

    public void finalizar(BigDecimal subtotal,
                          BigDecimal descontoTotal,
                          BigDecimal acrescimoTotal,
                          BigDecimal valorTotal,
                          BigDecimal custoIngredientesTotal,
                          BigDecimal margemBrutaEstimadaTotal,
                          String idempotencyKey,
                          Integer numeroCaixaLegado,
                          Long usuarioId) {
        exigirRascunho();
        this.subtotal = dinheiro(subtotal);
        this.descontoTotal = dinheiro(descontoTotal);
        this.acrescimoTotal = dinheiro(acrescimoTotal);
        this.valorTotal = dinheiro(valorTotal);
        this.custoIngredientesTotal = dinheiro(custoIngredientesTotal);
        this.margemBrutaEstimadaTotal = dinheiro(margemBrutaEstimadaTotal);
        this.idempotencyKey = normalizar(idempotencyKey);
        this.numeroCaixaLegado = numeroCaixaLegado;
        this.status = StatusVenda.FINALIZADA;
        this.finalizadaPor = usuarioId;
        this.finalizadaEm = LocalDateTime.now();
    }

    public void cancelar(Long usuarioId, String motivo) {
        validar(motivo != null && !motivo.isBlank(), "Motivo do cancelamento e obrigatorio.");
        if (status == StatusVenda.CANCELADA) {
            throw new IllegalStateException("VENDA_JA_CANCELADA");
        }
        if (status != StatusVenda.FINALIZADA && status != StatusVenda.PARCIALMENTE_CANCELADA) {
            throw new IllegalStateException("CANCELAMENTO_NAO_PERMITIDO");
        }
        this.status = StatusVenda.CANCELADA;
        this.canceladaPor = usuarioId;
        this.canceladaEm = LocalDateTime.now();
        this.motivoCancelamento = motivo.trim();
    }

    public void marcarParcialmenteCancelada(Long usuarioId, String motivo) {
        validar(motivo != null && !motivo.isBlank(), "Motivo do cancelamento e obrigatorio.");
        if (status == StatusVenda.CANCELADA) {
            throw new IllegalStateException("VENDA_JA_CANCELADA");
        }
        this.status = StatusVenda.PARCIALMENTE_CANCELADA;
        this.canceladaPor = usuarioId;
        this.canceladaEm = LocalDateTime.now();
        this.motivoCancelamento = motivo.trim();
    }

    @PrePersist
    void prePersist() {
        if (criadaEm == null) {
            criadaEm = LocalDateTime.now();
        }
    }

    public long getId() { return id; }
    public Long getFilialId() { return filialId; }
    public String getNumero() { return numero; }
    public StatusVenda getStatus() { return status; }
    public CanalVenda getCanal() { return canal; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getDescontoTotal() { return descontoTotal; }
    public BigDecimal getAcrescimoTotal() { return acrescimoTotal; }
    public BigDecimal getValorTotal() { return valorTotal; }
    public BigDecimal getCustoIngredientesTotal() { return custoIngredientesTotal; }
    public BigDecimal getMargemBrutaEstimadaTotal() { return margemBrutaEstimadaTotal; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Integer getNumeroCaixaLegado() { return numeroCaixaLegado; }
    public LocalDateTime getCriadaEm() { return criadaEm; }
    public Long getCriadaPor() { return criadaPor; }
    public LocalDateTime getFinalizadaEm() { return finalizadaEm; }
    public Long getFinalizadaPor() { return finalizadaPor; }
    public LocalDateTime getCanceladaEm() { return canceladaEm; }
    public Long getCanceladaPor() { return canceladaPor; }
    public String getMotivoCancelamento() { return motivoCancelamento; }
    public long getVersion() { return version; }

    private static BigDecimal dinheiro(BigDecimal valor) {
        return (valor == null ? BigDecimal.ZERO : valor).setScale(ESCALA_DINHEIRO, RoundingMode.HALF_UP);
    }

    private static String normalizar(String valor) {
        if (valor == null) return null;
        String texto = valor.trim();
        return texto.isEmpty() ? null : texto;
    }

    private static void validar(boolean condicao, String mensagem) {
        if (!condicao) throw new IllegalArgumentException(mensagem);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Venda other)) return false;
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
