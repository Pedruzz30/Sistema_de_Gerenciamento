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
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(
        name = "incompatibilidades_opcoes_modificadores",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_incompatibilidade_opcoes",
                columnNames = {"opcao_origem_id", "opcao_incompativel_id"}
        )
)
public class IncompatibilidadeOpcaoModificador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "opcao_origem_id", nullable = false)
    private OpcaoModificador opcaoOrigem;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "opcao_incompativel_id", nullable = false)
    private OpcaoModificador opcaoIncompativel;

    @Column(length = 500)
    private String motivo;

    @Column(nullable = false)
    private Boolean ativa = Boolean.TRUE;

    @Column(name = "criada_em", nullable = false)
    private LocalDateTime criadaEm;

    protected IncompatibilidadeOpcaoModificador() {
        // JPA
    }

    public IncompatibilidadeOpcaoModificador(long id,
                                             OpcaoModificador opcaoOrigem,
                                             OpcaoModificador opcaoIncompativel,
                                             String motivo,
                                             Boolean ativa) {
        validar(id >= 0, "ID da incompatibilidade nao pode ser negativo.");
        validar(opcaoOrigem != null, "Opcao origem e obrigatoria.");
        validar(opcaoIncompativel != null, "Opcao incompativel e obrigatoria.");
        validar(!opcaoOrigem.equals(opcaoIncompativel), "Opcao nao pode ser incompativel consigo mesma.");
        this.id = id;
        this.opcaoOrigem = opcaoOrigem;
        this.opcaoIncompativel = opcaoIncompativel;
        this.motivo = normalizar(motivo);
        this.ativa = ativa == null || ativa;
        this.criadaEm = LocalDateTime.now();
    }

    public long getId() { return id; }
    public OpcaoModificador getOpcaoOrigem() { return opcaoOrigem; }
    public OpcaoModificador getOpcaoIncompativel() { return opcaoIncompativel; }
    public String getMotivo() { return motivo; }
    public boolean isAtiva() { return ativa == null || ativa; }
    public LocalDateTime getCriadaEm() { return criadaEm; }

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
        if (!(o instanceof IncompatibilidadeOpcaoModificador other)) return false;
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
