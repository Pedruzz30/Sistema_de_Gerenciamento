package model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.Objects;

@Entity
@Table(
        name = "categorias_cardapio",
        uniqueConstraints = @UniqueConstraint(name = "uk_categorias_cardapio_codigo", columnNames = "codigo")
)
public class CategoriaCardapio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(nullable = false, length = 80)
    private String codigo;

    @Column(name = "nome_exibicao", nullable = false, length = 120)
    private String nomeExibicao;

    @Column(name = "ordem_exibicao", nullable = false)
    private int ordemExibicao;

    @Column(nullable = false)
    private Boolean ativo = Boolean.TRUE;

    protected CategoriaCardapio() {
        // Construtor exigido pelo JPA.
    }

    public CategoriaCardapio(int id, String codigo, String nomeExibicao, int ordemExibicao, Boolean ativo) {
        validar(id >= 0, "ID da categoria de cardapio nao pode ser negativo.");
        validar(codigo != null && !codigo.isBlank(), "Codigo da categoria de cardapio e obrigatorio.");
        validar(nomeExibicao != null && !nomeExibicao.isBlank(), "Nome de exibicao da categoria de cardapio e obrigatorio.");
        validar(ordemExibicao >= 0, "Ordem de exibicao da categoria de cardapio nao pode ser negativa.");

        this.id = id;
        this.codigo = codigo.trim();
        this.nomeExibicao = nomeExibicao.trim();
        this.ordemExibicao = ordemExibicao;
        this.ativo = ativo == null ? Boolean.TRUE : ativo;
    }

    public CategoriaCardapio comId(int novoId) {
        validar(novoId > 0, "Novo ID da categoria de cardapio deve ser maior que zero.");
        return new CategoriaCardapio(novoId, codigo, nomeExibicao, ordemExibicao, ativo);
    }

    public int getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNomeExibicao() {
        return nomeExibicao;
    }

    public int getOrdemExibicao() {
        return ordemExibicao;
    }

    public boolean isAtivo() {
        return ativo == null || ativo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CategoriaCardapio other)) return false;
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
