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
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

@Entity
@Table(
        name = "itens_cardapio",
        uniqueConstraints = @UniqueConstraint(name = "uk_itens_cardapio_codigo", columnNames = "codigo")
)
public class ItemCardapio {

    private static final int ESCALA_MONETARIA = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(nullable = false, length = 120)
    private String codigo;

    @Column(nullable = false, length = 160)
    private String nome;

    @Column(name = "descricao_curta", length = 280)
    private String descricaoCurta;

    @Column(name = "preco_venda", nullable = false, precision = 19, scale = 2)
    private BigDecimal precoVenda;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "categoria_cardapio_id", nullable = false)
    private CategoriaCardapio categoriaCardapio;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_item", nullable = false, length = 40)
    private TipoItemCardapio tipoItem;

    @Column(nullable = false)
    @ColumnDefault("true")
    private Boolean ativo = Boolean.TRUE;

    @Column(nullable = false)
    @ColumnDefault("true")
    private Boolean disponivel = Boolean.TRUE;

    @Column(name = "ordem_exibicao", nullable = false)
    private int ordemExibicao;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "produto_vinculado_id")
    private Produto produtoVinculado;

    protected ItemCardapio() {
        // Construtor exigido pelo JPA.
    }

    public ItemCardapio(int id,
                        String codigo,
                        String nome,
                        String descricaoCurta,
                        BigDecimal precoVenda,
                        CategoriaCardapio categoriaCardapio,
                        TipoItemCardapio tipoItem,
                        Boolean ativo,
                        Boolean disponivel,
                        int ordemExibicao,
                        Produto produtoVinculado) {
        validar(id >= 0, "ID do item de cardapio nao pode ser negativo.");
        validar(codigo != null && !codigo.isBlank(), "Codigo do item de cardapio e obrigatorio.");
        validar(nome != null && !nome.isBlank(), "Nome do item de cardapio e obrigatorio.");
        validar(precoVenda != null, "Preco de venda do item de cardapio e obrigatorio.");
        validar(precoVenda.compareTo(BigDecimal.ZERO) >= 0, "Preco de venda do item de cardapio nao pode ser negativo.");
        validar(categoriaCardapio != null, "Categoria do item de cardapio e obrigatoria.");
        validar(tipoItem != null, "Tipo do item de cardapio e obrigatorio.");
        validar(ordemExibicao >= 0, "Ordem de exibicao do item de cardapio nao pode ser negativa.");
        validar(tipoItem != TipoItemCardapio.PREPARADO_SOB_DEMANDA || produtoVinculado == null,
                "Itens preparados sob demanda nao devem vincular produto de estoque.");
        validar(produtoVinculado == null || produtoVinculado.isControladoPorEstoque(),
                "Produto vinculado precisa estar controlado por estoque.");

        this.id = id;
        this.codigo = codigo.trim();
        this.nome = nome.trim();
        this.descricaoCurta = normalizarDescricao(descricaoCurta);
        this.precoVenda = precoVenda.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
        this.categoriaCardapio = categoriaCardapio;
        this.tipoItem = tipoItem;
        this.ativo = ativo == null ? Boolean.TRUE : ativo;
        this.disponivel = disponivel == null ? Boolean.TRUE : disponivel;
        this.ordemExibicao = ordemExibicao;
        this.produtoVinculado = produtoVinculado;
    }

    public ItemCardapio comId(int novoId) {
        validar(novoId > 0, "Novo ID do item de cardapio deve ser maior que zero.");
        return new ItemCardapio(
                novoId,
                codigo,
                nome,
                descricaoCurta,
                precoVenda,
                categoriaCardapio,
                tipoItem,
                ativo,
                disponivel,
                ordemExibicao,
                produtoVinculado
        );
    }

    public boolean isAtivo() {
        return ativo == null || ativo;
    }

    public boolean isDisponivel() {
        return disponivel == null || disponivel;
    }

    public boolean isEstoqueDireto() {
        return tipoItem == TipoItemCardapio.ESTOQUE_DIRETO;
    }

    public boolean isPreparadoSobDemanda() {
        return tipoItem == TipoItemCardapio.PREPARADO_SOB_DEMANDA;
    }

    public boolean possuiProdutoVinculado() {
        return produtoVinculado != null;
    }

    public int getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNome() {
        return nome;
    }

    public String getDescricaoCurta() {
        return descricaoCurta;
    }

    public BigDecimal getPrecoVenda() {
        return precoVenda;
    }

    public CategoriaCardapio getCategoriaCardapio() {
        return categoriaCardapio;
    }

    public TipoItemCardapio getTipoItem() {
        return tipoItem;
    }

    public int getOrdemExibicao() {
        return ordemExibicao;
    }

    public Produto getProdutoVinculado() {
        return produtoVinculado;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ItemCardapio other)) return false;
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    private static String normalizarDescricao(String descricaoCurta) {
        if (descricaoCurta == null) {
            return null;
        }
        String texto = descricaoCurta.trim();
        return texto.isEmpty() ? null : texto;
    }

    private static void validar(boolean condicao, String mensagem) {
        if (!condicao) throw new IllegalArgumentException(mensagem);
    }
}
