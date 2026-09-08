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
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "venda_item_modificadores")
public class VendaItemModificador {

    private static final int ESCALA_DINHEIRO = 4;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "venda_item_id", nullable = false)
    private VendaItem vendaItem;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "grupo_modificador_id", nullable = false)
    private GrupoModificador grupoModificador;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "opcao_modificador_id", nullable = false)
    private OpcaoModificador opcaoModificador;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "venda_item_parte_id")
    private VendaItemParte vendaItemParte;

    @Column(name = "nome_grupo_snapshot", nullable = false, length = 120)
    private String nomeGrupoSnapshot;

    @Column(name = "nome_opcao_snapshot", nullable = false, length = 120)
    private String nomeOpcaoSnapshot;

    @Column(name = "codigo_grupo_snapshot", nullable = false, length = 80)
    private String codigoGrupoSnapshot;

    @Column(name = "codigo_opcao_snapshot", nullable = false, length = 80)
    private String codigoOpcaoSnapshot;

    @Column(name = "quantidade_selecionada", nullable = false)
    private int quantidadeSelecionada;

    @Column(name = "selecao_padrao", nullable = false)
    private Boolean selecaoPadrao = Boolean.FALSE;

    @Column(name = "acrescimo_unitario_snapshot", nullable = false, precision = 19, scale = ESCALA_DINHEIRO)
    private BigDecimal acrescimoUnitarioSnapshot;

    @Column(name = "acrescimo_total_snapshot", nullable = false, precision = 19, scale = ESCALA_DINHEIRO)
    private BigDecimal acrescimoTotalSnapshot;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    protected VendaItemModificador() {
        // JPA
    }

    public VendaItemModificador(long id,
                                VendaItem vendaItem,
                                GrupoModificador grupoModificador,
                                OpcaoModificador opcaoModificador,
                                int quantidadeSelecionada,
                                boolean selecaoPadrao) {
        this(id, vendaItem, null, grupoModificador, opcaoModificador, quantidadeSelecionada, selecaoPadrao);
    }

    public VendaItemModificador(long id,
                                VendaItem vendaItem,
                                VendaItemParte vendaItemParte,
                                GrupoModificador grupoModificador,
                                OpcaoModificador opcaoModificador,
                                int quantidadeSelecionada,
                                boolean selecaoPadrao) {
        validar(id >= 0, "ID do modificador da venda nao pode ser negativo.");
        validar(vendaItem != null, "Item da venda e obrigatorio.");
        validar(grupoModificador != null, "Grupo do modificador e obrigatorio.");
        validar(opcaoModificador != null, "Opcao do modificador e obrigatoria.");
        validar(quantidadeSelecionada > 0, "Quantidade selecionada deve ser maior que zero.");
        validar(vendaItemParte == null || vendaItemParte.getVendaItem().equals(vendaItem),
                "Parte do modificador nao pertence ao item da venda.");
        validar(opcaoModificador.getGrupoModificador().equals(grupoModificador),
                "Opcao nao pertence ao grupo informado.");
        this.id = id;
        this.vendaItem = vendaItem;
        this.vendaItemParte = vendaItemParte;
        this.grupoModificador = grupoModificador;
        this.opcaoModificador = opcaoModificador;
        this.nomeGrupoSnapshot = grupoModificador.getNome();
        this.nomeOpcaoSnapshot = opcaoModificador.getNome();
        this.codigoGrupoSnapshot = grupoModificador.getCodigo();
        this.codigoOpcaoSnapshot = opcaoModificador.getCodigo();
        this.quantidadeSelecionada = quantidadeSelecionada;
        this.selecaoPadrao = selecaoPadrao;
        this.acrescimoUnitarioSnapshot = opcaoModificador.getAcrescimoPreco()
                .setScale(ESCALA_DINHEIRO, RoundingMode.HALF_UP);
        this.acrescimoTotalSnapshot = this.acrescimoUnitarioSnapshot
                .multiply(BigDecimal.valueOf(quantidadeSelecionada))
                .setScale(ESCALA_DINHEIRO, RoundingMode.HALF_UP);
        this.criadoEm = LocalDateTime.now();
    }

    @PrePersist
    void prePersist() {
        if (criadoEm == null) {
            criadoEm = LocalDateTime.now();
        }
    }

    public long getId() { return id; }
    public VendaItem getVendaItem() { return vendaItem; }
    public VendaItemParte getVendaItemParte() { return vendaItemParte; }
    public GrupoModificador getGrupoModificador() { return grupoModificador; }
    public OpcaoModificador getOpcaoModificador() { return opcaoModificador; }
    public String getNomeGrupoSnapshot() { return nomeGrupoSnapshot; }
    public String getNomeOpcaoSnapshot() { return nomeOpcaoSnapshot; }
    public String getCodigoGrupoSnapshot() { return codigoGrupoSnapshot; }
    public String getCodigoOpcaoSnapshot() { return codigoOpcaoSnapshot; }
    public int getQuantidadeSelecionada() { return quantidadeSelecionada; }
    public boolean isSelecaoPadrao() { return selecaoPadrao != null && selecaoPadrao; }
    public BigDecimal getAcrescimoUnitarioSnapshot() { return acrescimoUnitarioSnapshot; }
    public BigDecimal getAcrescimoTotalSnapshot() { return acrescimoTotalSnapshot; }
    public LocalDateTime getCriadoEm() { return criadoEm; }

    private static void validar(boolean condicao, String mensagem) {
        if (!condicao) throw new IllegalArgumentException(mensagem);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VendaItemModificador other)) return false;
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
