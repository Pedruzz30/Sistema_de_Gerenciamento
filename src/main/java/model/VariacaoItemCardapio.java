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
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(
        name = "variacoes_item_cardapio",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_variacao_item_codigo",
                columnNames = {"item_cardapio_id", "codigo"}
        )
)
public class VariacaoItemCardapio {

    public static final int ESCALA_DINHEIRO = 2;
    public static final int ESCALA_QUANTIDADE = 6;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "item_cardapio_id", nullable = false)
    private ItemCardapio itemCardapio;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(nullable = false, length = 80)
    private String codigo;

    @Column(name = "preco_venda", nullable = false, precision = 19, scale = ESCALA_DINHEIRO)
    private BigDecimal precoVenda;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_composicao", nullable = false, length = 30)
    private TipoComposicaoVariacao tipoComposicao;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "produto_direto_id")
    private Produto produtoDireto;

    @Column(name = "quantidade_produto_direto_base", precision = 19, scale = ESCALA_QUANTIDADE)
    private BigDecimal quantidadeProdutoDiretoBase;

    @Column(nullable = false)
    private Boolean ativa = Boolean.TRUE;

    @Column(nullable = false)
    private Boolean padrao = Boolean.FALSE;

    @Column(name = "ordem_exibicao", nullable = false)
    private int ordemExibicao;

    @Column(name = "limite_alerta_capacidade")
    private Integer limiteAlertaCapacidade;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    @Version
    private long version;

    protected VariacaoItemCardapio() {
        // JPA
    }

    public VariacaoItemCardapio(long id,
                                ItemCardapio itemCardapio,
                                String nome,
                                String codigo,
                                BigDecimal precoVenda,
                                TipoComposicaoVariacao tipoComposicao,
                                Produto produtoDireto,
                                Boolean ativa,
                                Boolean padrao,
                                int ordemExibicao) {
        this(
                id,
                itemCardapio,
                nome,
                codigo,
                precoVenda,
                tipoComposicao,
                produtoDireto,
                null,
                ativa,
                padrao,
                ordemExibicao
        );
    }

    public VariacaoItemCardapio(long id,
                                ItemCardapio itemCardapio,
                                String nome,
                                String codigo,
                                BigDecimal precoVenda,
                                TipoComposicaoVariacao tipoComposicao,
                                Produto produtoDireto,
                                BigDecimal quantidadeProdutoDiretoBase,
                                Boolean ativa,
                                Boolean padrao,
                                int ordemExibicao) {
        this(id, itemCardapio, nome, codigo, precoVenda, tipoComposicao, produtoDireto,
                quantidadeProdutoDiretoBase, ativa, padrao, ordemExibicao, null);
    }

    public VariacaoItemCardapio(long id,
                                ItemCardapio itemCardapio,
                                String nome,
                                String codigo,
                                BigDecimal precoVenda,
                                TipoComposicaoVariacao tipoComposicao,
                                Produto produtoDireto,
                                BigDecimal quantidadeProdutoDiretoBase,
                                Boolean ativa,
                                Boolean padrao,
                                int ordemExibicao,
                                Integer limiteAlertaCapacidade) {
        validar(id >= 0, "ID da variacao nao pode ser negativo.");
        this.id = id;
        this.criadoEm = LocalDateTime.now();
        this.atualizadoEm = this.criadoEm;
        atualizarDados(itemCardapio, nome, codigo, precoVenda, tipoComposicao, produtoDireto,
                quantidadeProdutoDiretoBase, ativa, padrao, ordemExibicao, limiteAlertaCapacidade);
    }

    public void atualizar(String nome,
                          String codigo,
                          BigDecimal precoVenda,
                          TipoComposicaoVariacao tipoComposicao,
                          Produto produtoDireto,
                          Boolean ativa,
                          Boolean padrao,
                          int ordemExibicao) {
        atualizar(nome, codigo, precoVenda, tipoComposicao, produtoDireto,
                quantidadeProdutoDiretoBase, ativa, padrao, ordemExibicao);
    }

    public void atualizar(String nome,
                          String codigo,
                          BigDecimal precoVenda,
                          TipoComposicaoVariacao tipoComposicao,
                          Produto produtoDireto,
                          BigDecimal quantidadeProdutoDiretoBase,
                          Boolean ativa,
                          Boolean padrao,
                          int ordemExibicao) {
        atualizarDados(itemCardapio, nome, codigo, precoVenda, tipoComposicao, produtoDireto,
                quantidadeProdutoDiretoBase, ativa, padrao, ordemExibicao, limiteAlertaCapacidade);
    }

    public void definirLimiteAlertaCapacidade(Integer limiteAlertaCapacidade) {
        validar(limiteAlertaCapacidade == null || limiteAlertaCapacidade >= 0,
                "Limite de alerta de capacidade nao pode ser negativo.");
        this.limiteAlertaCapacidade = limiteAlertaCapacidade;
        this.atualizadoEm = LocalDateTime.now();
    }

    public void alterarStatus(boolean ativa) {
        this.ativa = ativa;
        this.atualizadoEm = LocalDateTime.now();
    }

    private void atualizarDados(ItemCardapio itemCardapio,
                                String nome,
                                String codigo,
                                BigDecimal precoVenda,
                                TipoComposicaoVariacao tipoComposicao,
                                Produto produtoDireto,
                                BigDecimal quantidadeProdutoDiretoBase,
                                Boolean ativa,
                                Boolean padrao,
                                int ordemExibicao,
                                Integer limiteAlertaCapacidade) {
        validar(itemCardapio != null, "Item do cardapio e obrigatorio.");
        validar(nome != null && !nome.isBlank(), "Nome da variacao e obrigatorio.");
        validar(codigo != null && !codigo.isBlank(), "Codigo da variacao e obrigatorio.");
        validar(precoVenda != null && precoVenda.compareTo(BigDecimal.ZERO) >= 0,
                "Preco da variacao nao pode ser negativo.");
        validar(tipoComposicao != null, "Tipo de composicao da variacao e obrigatorio.");
        validar(ordemExibicao >= 0, "Ordem de exibicao da variacao nao pode ser negativa.");
        validar(limiteAlertaCapacidade == null || limiteAlertaCapacidade >= 0,
                "Limite de alerta de capacidade nao pode ser negativo.");

        if (tipoComposicao == TipoComposicaoVariacao.PRODUTO_DIRETO) {
            validar(produtoDireto != null, "Variacao de produto direto exige produto vinculado.");
            if (quantidadeProdutoDiretoBase != null) {
                validar(quantidadeProdutoDiretoBase.compareTo(BigDecimal.ZERO) > 0,
                        "Quantidade consumida do produto direto deve ser maior que zero.");
            }
        } else {
            validar(produtoDireto == null, "Variacao preparada nao deve possuir produto direto.");
            validar(quantidadeProdutoDiretoBase == null,
                    "Variacao preparada nao deve possuir quantidade de produto direto.");
        }

        this.itemCardapio = itemCardapio;
        this.nome = nome.trim();
        this.codigo = normalizarCodigo(codigo);
        this.precoVenda = precoVenda.setScale(ESCALA_DINHEIRO, RoundingMode.HALF_UP);
        this.tipoComposicao = tipoComposicao;
        this.produtoDireto = produtoDireto;
        this.quantidadeProdutoDiretoBase = tipoComposicao == TipoComposicaoVariacao.PRODUTO_DIRETO
                ? (quantidadeProdutoDiretoBase == null ? BigDecimal.ONE : quantidadeProdutoDiretoBase)
                .setScale(ESCALA_QUANTIDADE, RoundingMode.HALF_UP)
                : null;
        this.ativa = ativa == null || ativa;
        this.padrao = padrao != null && padrao;
        this.ordemExibicao = ordemExibicao;
        this.limiteAlertaCapacidade = limiteAlertaCapacidade;
        this.atualizadoEm = LocalDateTime.now();
    }

    public long getId() { return id; }
    public ItemCardapio getItemCardapio() { return itemCardapio; }
    public String getNome() { return nome; }
    public String getCodigo() { return codigo; }
    public BigDecimal getPrecoVenda() { return precoVenda; }
    public TipoComposicaoVariacao getTipoComposicao() { return tipoComposicao; }
    public Produto getProdutoDireto() { return produtoDireto; }
    public BigDecimal getQuantidadeProdutoDiretoBase() {
        if (tipoComposicao == TipoComposicaoVariacao.PRODUTO_DIRETO && quantidadeProdutoDiretoBase == null) {
            return BigDecimal.ONE.setScale(ESCALA_QUANTIDADE, RoundingMode.HALF_UP);
        }
        return quantidadeProdutoDiretoBase;
    }
    public boolean isAtiva() { return ativa == null || ativa; }
    public boolean isPadrao() { return padrao != null && padrao; }
    public int getOrdemExibicao() { return ordemExibicao; }
    public Integer getLimiteAlertaCapacidade() { return limiteAlertaCapacidade; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public LocalDateTime getAtualizadoEm() { return atualizadoEm; }
    public long getVersion() { return version; }

    public boolean isPreparada() {
        return tipoComposicao == TipoComposicaoVariacao.PREPARADO;
    }

    private static String normalizarCodigo(String codigo) {
        return codigo.trim().toUpperCase();
    }

    private static void validar(boolean condicao, String mensagem) {
        if (!condicao) throw new IllegalArgumentException(mensagem);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VariacaoItemCardapio other)) return false;
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
