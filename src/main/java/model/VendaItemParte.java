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
@Table(name = "venda_item_partes")
public class VendaItemParte {

    public static final int ESCALA_PROPORCAO = 6;
    private static final int ESCALA_DINHEIRO = 4;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "venda_item_id", nullable = false)
    private VendaItem vendaItem;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "variacao_parte_id", nullable = false)
    private VariacaoItemCardapio variacaoParte;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "item_cardapio_parte_id", nullable = false)
    private ItemCardapio itemCardapioParte;

    @Column(name = "nome_item_snapshot", nullable = false, length = 160)
    private String nomeItemSnapshot;

    @Column(name = "nome_variacao_snapshot", nullable = false, length = 120)
    private String nomeVariacaoSnapshot;

    @Column(name = "codigo_variacao_snapshot", nullable = false, length = 80)
    private String codigoVariacaoSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_composicao_snapshot", nullable = false, length = 30)
    private TipoComposicaoVariacao tipoComposicaoSnapshot;

    @Column(nullable = false, precision = 19, scale = ESCALA_PROPORCAO)
    private BigDecimal proporcao;

    @Column(name = "preco_referencia_snapshot", nullable = false, precision = 19, scale = ESCALA_DINHEIRO)
    private BigDecimal precoReferenciaSnapshot;

    @Column(name = "custo_referencia_snapshot", nullable = false, precision = 19, scale = ESCALA_DINHEIRO)
    private BigDecimal custoReferenciaSnapshot;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "ficha_tecnica_id")
    private FichaTecnica fichaTecnica;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "ficha_tecnica_versao_id")
    private FichaTecnicaVersao fichaTecnicaVersao;

    @Column(name = "numero_versao_ficha")
    private Integer numeroVersaoFicha;

    @Column(name = "configuracao_hash_snapshot", length = 128)
    private String configuracaoHashSnapshot;

    @Column(name = "ordem_visual", nullable = false)
    private int ordemVisual;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusVendaItem status;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    protected VendaItemParte() {
        // JPA
    }

    public VendaItemParte(long id,
                          VendaItem vendaItem,
                          VariacaoItemCardapio variacaoParte,
                          BigDecimal proporcao,
                          BigDecimal precoReferenciaSnapshot,
                          BigDecimal custoReferenciaSnapshot,
                          FichaTecnica fichaTecnica,
                          FichaTecnicaVersao fichaTecnicaVersao,
                          String configuracaoHashSnapshot,
                          int ordemVisual) {
        validar(id >= 0, "ID da parte do item nao pode ser negativo.");
        validar(vendaItem != null, "Item da venda e obrigatorio para a parte.");
        validar(variacaoParte != null, "Variacao da parte e obrigatoria.");
        validar(proporcao != null && proporcao.compareTo(BigDecimal.ZERO) > 0,
                "Proporcao da parte deve ser maior que zero.");
        validar(precoReferenciaSnapshot != null && precoReferenciaSnapshot.compareTo(BigDecimal.ZERO) >= 0,
                "Preco de referencia da parte nao pode ser negativo.");
        validar(custoReferenciaSnapshot != null && custoReferenciaSnapshot.compareTo(BigDecimal.ZERO) >= 0,
                "Custo de referencia da parte nao pode ser negativo.");
        validar(ordemVisual >= 0, "Ordem visual da parte nao pode ser negativa.");
        if (fichaTecnicaVersao != null) {
            validar(fichaTecnica != null, "Ficha tecnica da parte e obrigatoria quando ha versao.");
            validar(fichaTecnicaVersao.getFichaTecnica().equals(fichaTecnica),
                    "Versao da ficha nao pertence a ficha da parte.");
        }
        this.id = id;
        this.vendaItem = vendaItem;
        this.variacaoParte = variacaoParte;
        this.itemCardapioParte = variacaoParte.getItemCardapio();
        this.nomeItemSnapshot = variacaoParte.getItemCardapio().getNome();
        this.nomeVariacaoSnapshot = variacaoParte.getNome();
        this.codigoVariacaoSnapshot = variacaoParte.getCodigo();
        this.tipoComposicaoSnapshot = variacaoParte.getTipoComposicao();
        this.proporcao = proporcao.setScale(ESCALA_PROPORCAO, RoundingMode.HALF_UP);
        this.precoReferenciaSnapshot = precoReferenciaSnapshot.setScale(ESCALA_DINHEIRO, RoundingMode.HALF_UP);
        this.custoReferenciaSnapshot = custoReferenciaSnapshot.setScale(ESCALA_DINHEIRO, RoundingMode.HALF_UP);
        this.fichaTecnica = fichaTecnica;
        this.fichaTecnicaVersao = fichaTecnicaVersao;
        this.numeroVersaoFicha = fichaTecnicaVersao == null ? null : fichaTecnicaVersao.getNumeroVersao();
        this.configuracaoHashSnapshot = normalizar(configuracaoHashSnapshot);
        this.ordemVisual = ordemVisual;
        this.status = StatusVendaItem.ATIVO;
        this.criadoEm = LocalDateTime.now();
    }

    public void cancelar() {
        this.status = StatusVendaItem.CANCELADO;
    }

    public void atualizarCustoReferencia(BigDecimal custoReferenciaSnapshot) {
        validar(custoReferenciaSnapshot != null && custoReferenciaSnapshot.compareTo(BigDecimal.ZERO) >= 0,
                "Custo de referencia da parte nao pode ser negativo.");
        this.custoReferenciaSnapshot = custoReferenciaSnapshot.setScale(ESCALA_DINHEIRO, RoundingMode.HALF_UP);
    }

    @PrePersist
    void prePersist() {
        if (criadoEm == null) {
            criadoEm = LocalDateTime.now();
        }
    }

    public long getId() { return id; }
    public VendaItem getVendaItem() { return vendaItem; }
    public VariacaoItemCardapio getVariacaoParte() { return variacaoParte; }
    public ItemCardapio getItemCardapioParte() { return itemCardapioParte; }
    public String getNomeItemSnapshot() { return nomeItemSnapshot; }
    public String getNomeVariacaoSnapshot() { return nomeVariacaoSnapshot; }
    public String getCodigoVariacaoSnapshot() { return codigoVariacaoSnapshot; }
    public TipoComposicaoVariacao getTipoComposicaoSnapshot() { return tipoComposicaoSnapshot; }
    public BigDecimal getProporcao() { return proporcao; }
    public BigDecimal getPrecoReferenciaSnapshot() { return precoReferenciaSnapshot; }
    public BigDecimal getCustoReferenciaSnapshot() { return custoReferenciaSnapshot; }
    public FichaTecnica getFichaTecnica() { return fichaTecnica; }
    public FichaTecnicaVersao getFichaTecnicaVersao() { return fichaTecnicaVersao; }
    public Integer getNumeroVersaoFicha() { return numeroVersaoFicha; }
    public String getConfiguracaoHashSnapshot() { return configuracaoHashSnapshot; }
    public int getOrdemVisual() { return ordemVisual; }
    public StatusVendaItem getStatus() { return status; }
    public LocalDateTime getCriadoEm() { return criadoEm; }

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
        if (!(o instanceof VendaItemParte other)) return false;
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
