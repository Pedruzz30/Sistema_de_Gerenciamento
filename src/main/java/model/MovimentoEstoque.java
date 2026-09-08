package model;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "movimentos_estoque_decimal")
public class MovimentoEstoque {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(name = "filial_id", nullable = false)
    private Long filialId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private TipoMovimentoEstoque tipo;

    @Column(name = "quantidade_base", nullable = false, precision = 19, scale = 6)
    private BigDecimal quantidadeBase;

    @Column(name = "saldo_anterior", nullable = false, precision = 19, scale = 6)
    private BigDecimal saldoAnterior;

    @Column(name = "saldo_posterior", nullable = false, precision = 19, scale = 6)
    private BigDecimal saldoPosterior;

    @Column(name = "custo_unitario", nullable = false, precision = 19, scale = 8)
    private BigDecimal custoUnitario;

    @Column(name = "custo_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal custoTotal;

    @Column(name = "custo_medio_anterior", precision = 19, scale = 8)
    private BigDecimal custoMedioAnterior;

    @Column(name = "custo_medio_posterior", precision = 19, scale = 8)
    private BigDecimal custoMedioPosterior;

    @Column(name = "valor_estoque_anterior", precision = 19, scale = 4)
    private BigDecimal valorEstoqueAnterior;

    @Column(name = "valor_estoque_posterior", precision = 19, scale = 4)
    private BigDecimal valorEstoquePosterior;

    @Column(name = "origem_tipo", nullable = false, length = 60)
    private String origemTipo;

    @Column(name = "origem_id", nullable = false)
    private Long origemId;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(nullable = false, length = 240)
    private String motivo;

    @Column(length = 500)
    private String observacao;

    @Column(name = "idempotency_key", length = 120)
    private String idempotencyKey;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "movimento_estornado_id")
    private MovimentoEstoque movimentoEstornado;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    protected MovimentoEstoque() {
        // JPA
    }

    public MovimentoEstoque(long id,
                            Long filialId,
                            Produto produto,
                            TipoMovimentoEstoque tipo,
                            BigDecimal quantidadeBase,
                            BigDecimal saldoAnterior,
                            BigDecimal saldoPosterior,
                            BigDecimal custoUnitario,
                            BigDecimal custoTotal,
                            BigDecimal custoMedioAnterior,
                            BigDecimal custoMedioPosterior,
                            BigDecimal valorEstoqueAnterior,
                            BigDecimal valorEstoquePosterior,
                            String origemTipo,
                            Long origemId,
                            Long usuarioId,
                            String motivo,
                            String observacao,
                            String idempotencyKey,
                            MovimentoEstoque movimentoEstornado) {
        validar(id >= 0, "ID do movimento nao pode ser negativo.");
        validar(filialId != null && filialId > 0, "Filial do movimento e obrigatoria.");
        validar(produto != null, "Produto do movimento e obrigatorio.");
        validar(tipo != null, "Tipo de movimento e obrigatorio.");
        validar(quantidadeBase != null && quantidadeBase.compareTo(BigDecimal.ZERO) > 0,
                "Quantidade base do movimento deve ser maior que zero.");
        validar(saldoAnterior != null, "Saldo anterior e obrigatorio.");
        validar(saldoPosterior != null, "Saldo posterior e obrigatorio.");
        validar(custoUnitario != null && custoUnitario.compareTo(BigDecimal.ZERO) >= 0,
                "Custo unitario do movimento deve ser nao negativo.");
        validar(custoTotal != null && custoTotal.compareTo(BigDecimal.ZERO) >= 0,
                "Custo total do movimento deve ser nao negativo.");
        validar(custoMedioAnterior != null && custoMedioAnterior.compareTo(BigDecimal.ZERO) >= 0,
                "Custo medio anterior do movimento deve ser nao negativo.");
        validar(custoMedioPosterior != null && custoMedioPosterior.compareTo(BigDecimal.ZERO) >= 0,
                "Custo medio posterior do movimento deve ser nao negativo.");
        validar(valorEstoqueAnterior != null && valorEstoqueAnterior.compareTo(BigDecimal.ZERO) >= 0,
                "Valor de estoque anterior do movimento deve ser nao negativo.");
        validar(valorEstoquePosterior != null && valorEstoquePosterior.compareTo(BigDecimal.ZERO) >= 0,
                "Valor de estoque posterior do movimento deve ser nao negativo.");
        validar(origemTipo != null && !origemTipo.isBlank(), "Tipo de origem do movimento e obrigatorio.");
        validar(origemId != null && origemId > 0, "ID de origem do movimento e obrigatorio.");
        validar(motivo != null && !motivo.isBlank(), "Motivo do movimento e obrigatorio.");

        this.id = id;
        this.filialId = filialId;
        this.produto = produto;
        this.tipo = tipo;
        this.quantidadeBase = quantidadeBase.setScale(6, RoundingMode.HALF_UP);
        this.saldoAnterior = saldoAnterior.setScale(6, RoundingMode.HALF_UP);
        this.saldoPosterior = saldoPosterior.setScale(6, RoundingMode.HALF_UP);
        this.custoUnitario = custoUnitario.setScale(8, RoundingMode.HALF_UP);
        this.custoTotal = custoTotal.setScale(4, RoundingMode.HALF_UP);
        this.custoMedioAnterior = custoMedioAnterior.setScale(8, RoundingMode.HALF_UP);
        this.custoMedioPosterior = custoMedioPosterior.setScale(8, RoundingMode.HALF_UP);
        this.valorEstoqueAnterior = valorEstoqueAnterior.setScale(4, RoundingMode.HALF_UP);
        this.valorEstoquePosterior = valorEstoquePosterior.setScale(4, RoundingMode.HALF_UP);
        this.origemTipo = origemTipo.trim();
        this.origemId = origemId;
        this.usuarioId = usuarioId;
        this.motivo = motivo.trim();
        this.observacao = normalizar(observacao);
        this.idempotencyKey = normalizar(idempotencyKey);
        this.movimentoEstornado = movimentoEstornado;
        this.criadoEm = LocalDateTime.now();
    }

    public MovimentoEstoque comId(long novoId) {
        validar(novoId > 0, "Novo ID do movimento deve ser maior que zero.");
        MovimentoEstoque copia = new MovimentoEstoque(
                novoId,
                filialId,
                produto,
                tipo,
                quantidadeBase,
                saldoAnterior,
                saldoPosterior,
                custoUnitario,
                custoTotal,
                custoMedioAnterior,
                custoMedioPosterior,
                valorEstoqueAnterior,
                valorEstoquePosterior,
                origemTipo,
                origemId,
                usuarioId,
                motivo,
                observacao,
                idempotencyKey,
                movimentoEstornado
        );
        copia.criadoEm = criadoEm;
        return copia;
    }

    public boolean ehEstorno() {
        return tipo == TipoMovimentoEstoque.ESTORNO_MOVIMENTO
                || tipo == TipoMovimentoEstoque.ESTORNO_VENDA;
    }

    @PrePersist
    void prePersist() {
        if (criadoEm == null) {
            criadoEm = LocalDateTime.now();
        }
    }

    public long getId() { return id; }
    public Long getFilialId() { return filialId; }
    public Produto getProduto() { return produto; }
    public TipoMovimentoEstoque getTipo() { return tipo; }
    public BigDecimal getQuantidadeBase() { return quantidadeBase; }
    public BigDecimal getSaldoAnterior() { return saldoAnterior; }
    public BigDecimal getSaldoPosterior() { return saldoPosterior; }
    public BigDecimal getCustoUnitario() { return custoUnitario; }
    public BigDecimal getCustoTotal() { return custoTotal; }
    public BigDecimal getCustoMedioAnterior() { return custoMedioAnterior; }
    public BigDecimal getCustoMedioPosterior() { return custoMedioPosterior; }
    public BigDecimal getValorEstoqueAnterior() { return valorEstoqueAnterior; }
    public BigDecimal getValorEstoquePosterior() { return valorEstoquePosterior; }
    public String getOrigemTipo() { return origemTipo; }
    public Long getOrigemId() { return origemId; }
    public Long getUsuarioId() { return usuarioId; }
    public String getMotivo() { return motivo; }
    public String getObservacao() { return observacao; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public MovimentoEstoque getMovimentoEstornado() { return movimentoEstornado; }
    public LocalDateTime getCriadoEm() { return criadoEm; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MovimentoEstoque other)) return false;
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
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
