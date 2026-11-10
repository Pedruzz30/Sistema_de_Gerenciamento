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
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Um pagamento de uma venda do PDV. Uma venda pode ter vários (ex.: parte em
 * dinheiro, parte no Pix).
 *
 * <ul>
 *   <li>{@code valor}: quanto este pagamento abate do total da venda.</li>
 *   <li>{@code valorRecebido}: só no dinheiro — quanto o cliente entregou.</li>
 *   <li>{@code troco}: {@code valorRecebido - valor} (só no dinheiro).</li>
 * </ul>
 */
@Entity
@Table(name = "venda_pagamentos")
public class VendaPagamento {

    private static final int ESCALA = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venda_id", nullable = false)
    private Venda venda;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FormaPagamento forma;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal valor;

    @Column(name = "valor_recebido", precision = 19, scale = 2)
    private BigDecimal valorRecebido;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal troco;

    @Column(length = 40)
    private String nsu;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    protected VendaPagamento() {
        // JPA
    }

    public VendaPagamento(Venda venda, FormaPagamento forma, BigDecimal valor, BigDecimal valorRecebido, String nsu) {
        validar(venda != null, "Venda do pagamento e obrigatoria.");
        validar(forma != null, "Forma de pagamento e obrigatoria.");
        validar(valor != null && valor.compareTo(BigDecimal.ZERO) > 0, "Valor do pagamento deve ser maior que zero.");
        BigDecimal valorEscala = valor.setScale(ESCALA, RoundingMode.HALF_UP);
        BigDecimal recebido = null;
        BigDecimal trocoCalculado = BigDecimal.ZERO.setScale(ESCALA, RoundingMode.HALF_UP);
        if (valorRecebido != null) {
            recebido = valorRecebido.setScale(ESCALA, RoundingMode.HALF_UP);
            validar(recebido.compareTo(valorEscala) >= 0, "Valor recebido nao pode ser menor que o valor pago.");
            validar(forma == FormaPagamento.DINHEIRO || recebido.compareTo(valorEscala) == 0,
                    "Troco so e permitido em dinheiro.");
            trocoCalculado = recebido.subtract(valorEscala);
        }
        this.venda = venda;
        this.forma = forma;
        this.valor = valorEscala;
        this.valorRecebido = recebido;
        this.troco = trocoCalculado;
        this.nsu = nsu == null || nsu.isBlank() ? null : nsu.trim();
        this.criadoEm = LocalDateTime.now();
    }

    public long getId() { return id; }
    public Venda getVenda() { return venda; }
    public FormaPagamento getForma() { return forma; }
    public BigDecimal getValor() { return valor; }
    public BigDecimal getValorRecebido() { return valorRecebido; }
    public BigDecimal getTroco() { return troco; }
    public String getNsu() { return nsu; }
    public LocalDateTime getCriadoEm() { return criadoEm; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VendaPagamento other)) return false;
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
