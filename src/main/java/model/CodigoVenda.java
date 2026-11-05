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

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Código lido no caixa (código de barras EAN/GTIN ou código interno curto)
 * que leva direto à variação vendida — ex.: "Skol lata — Gelada".
 *
 * <p>O mesmo código pode apontar para mais de uma variação do mesmo produto
 * (lata gelada e lata natural têm o mesmo EAN). Só uma delas é a
 * {@code padrao}; as outras aparecem como alternativa no PDV (tecla F4).
 */
@Entity
@Table(name = "codigos_venda")
public class CodigoVenda {

    public enum Tipo { EAN13, EAN8, GTIN14, UPCA, INTERNO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(nullable = false, length = 32)
    private String codigo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Tipo tipo;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "variacao_id", nullable = false)
    private VariacaoItemCardapio variacao;

    @Column(nullable = false)
    private boolean padrao;

    @Column(nullable = false)
    private boolean ativo;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    protected CodigoVenda() {
        // JPA
    }

    public CodigoVenda(String codigo, Tipo tipo, VariacaoItemCardapio variacao, boolean padrao) {
        if (codigo == null || codigo.isBlank()) {
            throw new IllegalArgumentException("Codigo e obrigatorio.");
        }
        if (tipo == null) {
            throw new IllegalArgumentException("Tipo do codigo e obrigatorio.");
        }
        if (variacao == null) {
            throw new IllegalArgumentException("Variacao do codigo e obrigatoria.");
        }
        this.codigo = codigo.trim();
        this.tipo = tipo;
        this.variacao = variacao;
        this.padrao = padrao;
        this.ativo = true;
        this.criadoEm = LocalDateTime.now();
    }

    public void desativar() {
        this.ativo = false;
    }

    public void definirPadrao(boolean padrao) {
        this.padrao = padrao;
    }

    /** Uso do repositório em memória (o banco gera o ID). */
    public void atribuirIdGerado(long novoId) {
        if (this.id != 0 || novoId <= 0) {
            throw new IllegalStateException("ID invalido para codigo de venda.");
        }
        this.id = novoId;
    }

    public long getId() { return id; }
    public String getCodigo() { return codigo; }
    public Tipo getTipo() { return tipo; }
    public VariacaoItemCardapio getVariacao() { return variacao; }
    public boolean isPadrao() { return padrao; }
    public boolean isAtivo() { return ativo; }
    public LocalDateTime getCriadoEm() { return criadoEm; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CodigoVenda other)) return false;
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
