package model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import org.hibernate.annotations.Check;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(
        name = "estoques_produto",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_estoques_produto_filial_produto",
                columnNames = {"filial_id", "produto_id"}
        )
)
@Check(constraints = "quantidade_reservada >= 0")
public class EstoqueProduto {

    public static final int ESCALA_QUANTIDADE = 6;
    public static final int ESCALA_CUSTO = 8;
    public static final int ESCALA_VALOR = 4;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(name = "filial_id", nullable = false)
    private Long filialId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @Column(name = "quantidade_atual", nullable = false, precision = 19, scale = ESCALA_QUANTIDADE)
    private BigDecimal quantidadeAtual;

    @Column(name = "quantidade_reservada", nullable = false, precision = 19, scale = ESCALA_QUANTIDADE)
    private BigDecimal quantidadeReservada;

    @Column(name = "custo_medio", nullable = false, precision = 19, scale = ESCALA_CUSTO)
    private BigDecimal custoMedio;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    protected EstoqueProduto() {
        // JPA
    }

    public EstoqueProduto(long id,
                          Long filialId,
                          Produto produto,
                          BigDecimal quantidadeAtual,
                          BigDecimal quantidadeReservada,
                          BigDecimal custoMedio) {
        validar(id >= 0, "ID do estoque nao pode ser negativo.");
        validar(filialId != null && filialId > 0, "Filial do estoque e obrigatoria.");
        validar(produto != null, "Produto do estoque e obrigatorio.");
        validar(quantidadeAtual != null, "Quantidade atual e obrigatoria.");
        validar(quantidadeReservada != null && quantidadeReservada.compareTo(BigDecimal.ZERO) >= 0,
                "Quantidade reservada deve ser nao negativa.");
        validar(custoMedio != null && custoMedio.compareTo(BigDecimal.ZERO) >= 0,
                "Custo medio deve ser nao negativo.");

        this.id = id;
        this.filialId = filialId;
        this.produto = produto;
        this.quantidadeAtual = escalaQuantidade(quantidadeAtual);
        this.quantidadeReservada = escalaQuantidade(quantidadeReservada);
        this.custoMedio = escalaCusto(custoMedio);
        this.atualizadoEm = LocalDateTime.now();
    }

    public EstoqueProduto comId(long novoId) {
        validar(novoId > 0, "Novo ID do estoque deve ser maior que zero.");
        EstoqueProduto copia = new EstoqueProduto(novoId, filialId, produto, quantidadeAtual, quantidadeReservada, custoMedio);
        copia.version = version;
        copia.atualizadoEm = atualizadoEm;
        return copia;
    }

    public BigDecimal quantidadeDisponivel() {
        return quantidadeAtual.subtract(quantidadeReservada).setScale(ESCALA_QUANTIDADE, RoundingMode.HALF_UP);
    }

    public BigDecimal valorEstoque() {
        if (quantidadeAtual.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(ESCALA_VALOR, RoundingMode.HALF_UP);
        }
        return quantidadeAtual.multiply(custoMedio).setScale(ESCALA_VALOR, RoundingMode.HALF_UP);
    }

    public void aplicarEntrada(BigDecimal quantidadeEntradaBase, BigDecimal custoUnitarioBase) {
        validarQuantidadeMovimento(quantidadeEntradaBase);
        validar(custoUnitarioBase != null && custoUnitarioBase.compareTo(BigDecimal.ZERO) >= 0,
                "Custo unitario da entrada deve ser nao negativo.");

        BigDecimal quantidadeEntrada = escalaQuantidade(quantidadeEntradaBase);
        BigDecimal custoEntrada = escalaCusto(custoUnitarioBase);
        BigDecimal novaQuantidade = quantidadeAtual.add(quantidadeEntrada);
        BigDecimal novoCustoMedio;
        if (quantidadeAtual.compareTo(BigDecimal.ZERO) <= 0 || novaQuantidade.compareTo(BigDecimal.ZERO) == 0) {
            novoCustoMedio = custoEntrada;
        } else {
            BigDecimal valorAtual = quantidadeAtual.multiply(custoMedio);
            BigDecimal valorEntrada = quantidadeEntrada.multiply(custoEntrada);
            novoCustoMedio = valorAtual.add(valorEntrada).divide(novaQuantidade, ESCALA_CUSTO, RoundingMode.HALF_UP);
        }

        quantidadeAtual = escalaQuantidade(novaQuantidade);
        custoMedio = escalaCusto(novoCustoMedio);
        atualizadoEm = LocalDateTime.now();
    }

    public void aplicarSaida(BigDecimal quantidadeSaidaBase, BigDecimal custoUnitarioSaida, boolean bloquearNegativo) {
        validarQuantidadeMovimento(quantidadeSaidaBase);
        validar(custoUnitarioSaida != null && custoUnitarioSaida.compareTo(BigDecimal.ZERO) >= 0,
                "Custo unitario da saida deve ser nao negativo.");

        BigDecimal quantidadeSaida = escalaQuantidade(quantidadeSaidaBase);
        BigDecimal novaQuantidade = quantidadeAtual.subtract(quantidadeSaida);
        if (bloquearNegativo && novaQuantidade.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException("Estoque insuficiente para movimento de saida.");
        }

        quantidadeAtual = escalaQuantidade(novaQuantidade);
        atualizadoEm = LocalDateTime.now();
    }

    public void redefinirQuantidadeECusto(BigDecimal novaQuantidade, BigDecimal novoCustoMedio) {
        validar(novaQuantidade != null && novaQuantidade.compareTo(BigDecimal.ZERO) >= 0,
                "Nova quantidade deve ser nao negativa.");
        validar(novoCustoMedio != null && novoCustoMedio.compareTo(BigDecimal.ZERO) >= 0,
                "Novo custo medio deve ser nao negativo.");

        quantidadeAtual = escalaQuantidade(novaQuantidade);
        custoMedio = escalaCusto(novoCustoMedio);
        atualizadoEm = LocalDateTime.now();
    }

    @PrePersist
    @PreUpdate
    void atualizarTimestamp() {
        atualizadoEm = LocalDateTime.now();
    }

    public long getId() { return id; }
    public Long getFilialId() { return filialId; }
    public Produto getProduto() { return produto; }
    public BigDecimal getQuantidadeAtual() { return quantidadeAtual; }
    public BigDecimal getQuantidadeReservada() { return quantidadeReservada; }
    public BigDecimal getCustoMedio() { return custoMedio; }
    public long getVersion() { return version; }
    public LocalDateTime getAtualizadoEm() { return atualizadoEm; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EstoqueProduto other)) return false;
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    private static void validarQuantidadeMovimento(BigDecimal quantidade) {
        validar(quantidade != null && quantidade.compareTo(BigDecimal.ZERO) > 0,
                "Quantidade do movimento deve ser maior que zero.");
    }

    private static BigDecimal escalaQuantidade(BigDecimal valor) {
        return valor.setScale(ESCALA_QUANTIDADE, RoundingMode.HALF_UP);
    }

    private static BigDecimal escalaCusto(BigDecimal valor) {
        return valor.setScale(ESCALA_CUSTO, RoundingMode.HALF_UP);
    }

    private static void validar(boolean condicao, String mensagem) {
        if (!condicao) throw new IllegalArgumentException(mensagem);
    }
}
