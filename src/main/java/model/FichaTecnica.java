package model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "fichas_tecnicas")
public class FichaTecnica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "variacao_item_cardapio_id", nullable = false)
    private VariacaoItemCardapio variacaoItemCardapio;

    @Column(nullable = false, length = 160)
    private String nome;

    @Column(nullable = false)
    private Boolean ativa = Boolean.TRUE;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "versao_publicada_atual_id")
    private FichaTecnicaVersao versaoPublicadaAtual;

    @Column(name = "criada_em", nullable = false)
    private LocalDateTime criadaEm;

    @Column(name = "criada_por")
    private Long criadaPor;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    @Version
    private long version;

    protected FichaTecnica() {
        // JPA
    }

    public FichaTecnica(long id,
                        VariacaoItemCardapio variacaoItemCardapio,
                        String nome,
                        Long criadaPor) {
        validar(id >= 0, "ID da ficha tecnica nao pode ser negativo.");
        validar(variacaoItemCardapio != null, "Variacao da ficha tecnica e obrigatoria.");
        validar(nome != null && !nome.isBlank(), "Nome da ficha tecnica e obrigatorio.");
        validar(variacaoItemCardapio.isPreparada(), "Ficha tecnica so pode ser criada para variacao preparada.");

        this.id = id;
        this.variacaoItemCardapio = variacaoItemCardapio;
        this.nome = nome.trim();
        this.ativa = Boolean.TRUE;
        this.criadaPor = criadaPor;
        this.criadaEm = LocalDateTime.now();
        this.atualizadoEm = this.criadaEm;
    }

    public void atualizarNome(String nome) {
        validar(nome != null && !nome.isBlank(), "Nome da ficha tecnica e obrigatorio.");
        this.nome = nome.trim();
        this.atualizadoEm = LocalDateTime.now();
    }

    public void definirVersaoPublicadaAtual(FichaTecnicaVersao versao) {
        validar(versao != null, "Versao publicada atual e obrigatoria.");
        validar(versao.getFichaTecnica().equals(this), "Versao publicada nao pertence a esta ficha.");
        this.versaoPublicadaAtual = versao;
        this.atualizadoEm = LocalDateTime.now();
    }

    public long getId() { return id; }
    public VariacaoItemCardapio getVariacaoItemCardapio() { return variacaoItemCardapio; }
    public String getNome() { return nome; }
    public boolean isAtiva() { return ativa == null || ativa; }
    public FichaTecnicaVersao getVersaoPublicadaAtual() { return versaoPublicadaAtual; }
    public LocalDateTime getCriadaEm() { return criadaEm; }
    public Long getCriadaPor() { return criadaPor; }
    public LocalDateTime getAtualizadoEm() { return atualizadoEm; }
    public long getVersion() { return version; }

    private static void validar(boolean condicao, String mensagem) {
        if (!condicao) throw new IllegalArgumentException(mensagem);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FichaTecnica other)) return false;
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
