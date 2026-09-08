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
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "venda_itens")
public class VendaItem {

    public static final int ESCALA_QUANTIDADE = 6;
    public static final int ESCALA_DINHEIRO = 4;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "venda_id", nullable = false)
    private Venda venda;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "item_cardapio_id", nullable = false)
    private ItemCardapio itemCardapio;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "variacao_item_cardapio_id", nullable = false)
    private VariacaoItemCardapio variacaoItemCardapio;

    @Column(nullable = false, precision = 19, scale = ESCALA_QUANTIDADE)
    private BigDecimal quantidade;

    @Column(name = "nome_item_snapshot", nullable = false, length = 160)
    private String nomeItemSnapshot;

    @Column(name = "nome_variacao_snapshot", nullable = false, length = 120)
    private String nomeVariacaoSnapshot;

    @Column(name = "codigo_variacao_snapshot", nullable = false, length = 80)
    private String codigoVariacaoSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_composicao_snapshot", nullable = false, length = 30)
    private TipoComposicaoVariacao tipoComposicaoSnapshot;

    @Column(name = "preco_unitario_base", nullable = false, precision = 19, scale = ESCALA_DINHEIRO)
    private BigDecimal precoUnitarioBase;

    @Column(name = "desconto_unitario", nullable = false, precision = 19, scale = ESCALA_DINHEIRO)
    private BigDecimal descontoUnitario;

    @Column(name = "acrescimo_unitario", nullable = false, precision = 19, scale = ESCALA_DINHEIRO)
    private BigDecimal acrescimoUnitario;

    @Column(name = "preco_unitario_final", nullable = false, precision = 19, scale = ESCALA_DINHEIRO)
    private BigDecimal precoUnitarioFinal;

    @Column(nullable = false, precision = 19, scale = ESCALA_DINHEIRO)
    private BigDecimal subtotal;

    @Column(name = "custo_unitario_estimado", nullable = false, precision = 19, scale = ESCALA_DINHEIRO)
    private BigDecimal custoUnitarioEstimado;

    @Column(name = "custo_total_estimado", nullable = false, precision = 19, scale = ESCALA_DINHEIRO)
    private BigDecimal custoTotalEstimado;

    @Column(name = "margem_bruta_unitaria_estimada", nullable = false, precision = 19, scale = ESCALA_DINHEIRO)
    private BigDecimal margemBrutaUnitariaEstimada;

    @Column(name = "margem_bruta_total_estimada", nullable = false, precision = 19, scale = ESCALA_DINHEIRO)
    private BigDecimal margemBrutaTotalEstimada;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "ficha_tecnica_id")
    private FichaTecnica fichaTecnica;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "ficha_tecnica_versao_id")
    private FichaTecnicaVersao fichaTecnicaVersao;

    @Column(name = "numero_versao_ficha")
    private Integer numeroVersaoFicha;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "ficha_tecnica_observada_id")
    private FichaTecnica fichaTecnicaObservada;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "ficha_tecnica_versao_observada_id")
    private FichaTecnicaVersao fichaTecnicaVersaoObservada;

    @Column(name = "numero_versao_ficha_observada")
    private Integer numeroVersaoFichaObservada;

    @Column(name = "configuracao_modificadores_hash_observada", length = 128)
    private String configuracaoModificadoresHashObservada;

    @Column(name = "composicao_multipla", nullable = false)
    private Boolean composicaoMultipla = Boolean.FALSE;

    @Enumerated(EnumType.STRING)
    @Column(name = "politica_preco_composicao", length = 40)
    private PoliticaPrecoComposicaoMultipla politicaPrecoComposicao;

    @Column(name = "quantidade_partes_snapshot")
    private Integer quantidadePartesSnapshot;

    @Column(name = "resumo_partes_snapshot", length = 1000)
    private String resumoPartesSnapshot;

    @Column(name = "configuracao_multissabor_hash_observada", length = 128)
    private String configuracaoMultissaborHashObservada;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusVendaItem status;

    @Column(length = 500)
    private String observacao;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "cancelado_em")
    private LocalDateTime canceladoEm;

    @Column(name = "cancelado_por")
    private Long canceladoPor;

    @Column(name = "motivo_cancelamento", length = 500)
    private String motivoCancelamento;

    @Version
    private long version;

    protected VendaItem() {
        // JPA
    }

    public VendaItem(long id,
                     Venda venda,
                     ItemCardapio itemCardapio,
                     VariacaoItemCardapio variacaoItemCardapio,
                     BigDecimal quantidade,
                     String observacao) {
        validar(id >= 0, "ID do item da venda nao pode ser negativo.");
        validar(venda != null, "Venda do item e obrigatoria.");
        validar(itemCardapio != null, "Item de cardapio e obrigatorio.");
        validar(variacaoItemCardapio != null, "Variacao de cardapio e obrigatoria.");
        validar(quantidade != null && quantidade.compareTo(BigDecimal.ZERO) > 0,
                "Quantidade do item deve ser maior que zero.");

        this.id = id;
        this.venda = venda;
        this.itemCardapio = itemCardapio;
        this.variacaoItemCardapio = variacaoItemCardapio;
        this.quantidade = quantidade.setScale(ESCALA_QUANTIDADE, RoundingMode.HALF_UP);
        this.observacao = normalizar(observacao);
        this.status = StatusVendaItem.ATIVO;
        this.criadoEm = LocalDateTime.now();
        this.composicaoMultipla = Boolean.FALSE;
        aplicarSnapshotComercial(variacaoItemCardapio, BigDecimal.ZERO, BigDecimal.ZERO);
        aplicarCusto(null, null, null, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    public void aplicarSnapshotComercial(VariacaoItemCardapio variacao,
                                         BigDecimal descontoUnitario,
                                         BigDecimal acrescimoUnitario) {
        aplicarSnapshotComercialComPrecoBase(variacao, variacao == null ? null : variacao.getPrecoVenda(), descontoUnitario, acrescimoUnitario);
    }

    public void aplicarSnapshotComercialComPrecoBase(VariacaoItemCardapio variacao,
                                                     BigDecimal precoUnitarioBase,
                                                     BigDecimal descontoUnitario,
                                                     BigDecimal acrescimoUnitario) {
        validar(variacao != null, "Variacao de cardapio e obrigatoria.");
        BigDecimal desconto = dinheiro(descontoUnitario);
        BigDecimal acrescimo = dinheiro(acrescimoUnitario);
        BigDecimal precoBase = dinheiro(precoUnitarioBase);
        BigDecimal precoFinal = precoBase.subtract(desconto).add(acrescimo).setScale(ESCALA_DINHEIRO, RoundingMode.HALF_UP);
        validar(precoFinal.compareTo(BigDecimal.ZERO) >= 0, "Preco final do item nao pode ser negativo.");

        this.itemCardapio = variacao.getItemCardapio();
        this.variacaoItemCardapio = variacao;
        this.nomeItemSnapshot = variacao.getItemCardapio().getNome();
        this.nomeVariacaoSnapshot = variacao.getNome();
        this.codigoVariacaoSnapshot = variacao.getCodigo();
        this.tipoComposicaoSnapshot = variacao.getTipoComposicao();
        this.precoUnitarioBase = precoBase;
        this.descontoUnitario = desconto;
        this.acrescimoUnitario = acrescimo;
        this.precoUnitarioFinal = precoFinal;
        this.subtotal = precoFinal.multiply(quantidade).setScale(ESCALA_DINHEIRO, RoundingMode.HALF_UP);
    }

    public void aplicarCusto(FichaTecnica ficha,
                             FichaTecnicaVersao versao,
                             Integer numeroVersaoFicha,
                             BigDecimal custoTotal,
                             BigDecimal margemTotal) {
        BigDecimal total = dinheiro(custoTotal);
        BigDecimal margem = dinheiro(margemTotal);
        this.fichaTecnica = ficha;
        this.fichaTecnicaVersao = versao;
        this.numeroVersaoFicha = numeroVersaoFicha;
        this.custoTotalEstimado = total;
        this.custoUnitarioEstimado = total.divide(quantidade, ESCALA_DINHEIRO, RoundingMode.HALF_UP);
        this.margemBrutaTotalEstimada = margem;
        this.margemBrutaUnitariaEstimada = margem.divide(quantidade, ESCALA_DINHEIRO, RoundingMode.HALF_UP);
    }

    public void registrarFichaObservada(FichaTecnica ficha, FichaTecnicaVersao versao) {
        validar(ficha != null, "Ficha tecnica observada e obrigatoria.");
        validar(versao != null, "Versao observada e obrigatoria.");
        validar(versao.getFichaTecnica().equals(ficha), "Versao observada nao pertence a ficha observada.");
        this.fichaTecnicaObservada = ficha;
        this.fichaTecnicaVersaoObservada = versao;
        this.numeroVersaoFichaObservada = versao.getNumeroVersao();
    }

    public void registrarConfiguracaoModificadoresObservada(String hashConfiguracao,
                                                            BigDecimal acrescimoUnitario) {
        this.configuracaoModificadoresHashObservada = normalizar(hashConfiguracao);
        aplicarSnapshotComercial(variacaoItemCardapio, descontoUnitario, acrescimoUnitario);
    }

    public void registrarConfiguracaoModificadoresObservada(String hashConfiguracao,
                                                            BigDecimal precoUnitarioBaseOficial,
                                                            BigDecimal acrescimoUnitario) {
        this.configuracaoModificadoresHashObservada = normalizar(hashConfiguracao);
        aplicarSnapshotComercialComPrecoBase(
                variacaoItemCardapio,
                precoUnitarioBaseOficial,
                descontoUnitario,
                acrescimoUnitario
        );
    }

    public void registrarComposicaoMultipla(PoliticaPrecoComposicaoMultipla politicaPreco,
                                            int quantidadePartes,
                                            String resumoPartes,
                                            String hashConfiguracao,
                                            BigDecimal precoUnitarioBaseOficial,
                                            BigDecimal acrescimoUnitario) {
        validar(politicaPreco != null, "Politica de preco da composicao multipla e obrigatoria.");
        validar(quantidadePartes > 0, "Quantidade de partes da composicao multipla deve ser maior que zero.");
        this.composicaoMultipla = Boolean.TRUE;
        this.politicaPrecoComposicao = politicaPreco;
        this.quantidadePartesSnapshot = quantidadePartes;
        this.resumoPartesSnapshot = normalizar(resumoPartes);
        this.configuracaoMultissaborHashObservada = normalizar(hashConfiguracao);
        aplicarSnapshotComercialComPrecoBase(
                variacaoItemCardapio,
                precoUnitarioBaseOficial,
                descontoUnitario,
                acrescimoUnitario
        );
    }

    public void atualizarRascunho(VariacaoItemCardapio variacao,
                                  BigDecimal quantidade,
                                  String observacao) {
        validar(status == StatusVendaItem.ATIVO, "Item cancelado nao pode ser editado.");
        validar(variacao != null, "Variacao de cardapio e obrigatoria.");
        validar(quantidade != null && quantidade.compareTo(BigDecimal.ZERO) > 0,
                "Quantidade do item deve ser maior que zero.");
        this.quantidade = quantidade.setScale(ESCALA_QUANTIDADE, RoundingMode.HALF_UP);
        this.observacao = normalizar(observacao);
        this.fichaTecnica = null;
        this.fichaTecnicaVersao = null;
        this.numeroVersaoFicha = null;
        this.fichaTecnicaObservada = null;
        this.fichaTecnicaVersaoObservada = null;
        this.numeroVersaoFichaObservada = null;
        this.composicaoMultipla = Boolean.FALSE;
        this.politicaPrecoComposicao = null;
        this.quantidadePartesSnapshot = null;
        this.resumoPartesSnapshot = null;
        this.configuracaoMultissaborHashObservada = null;
        aplicarSnapshotComercial(variacao, descontoUnitario, BigDecimal.ZERO);
        aplicarCusto(null, null, null, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    public void cancelar(Long usuarioId, String motivo) {
        if (status == StatusVendaItem.CANCELADO) {
            throw new IllegalStateException("ITEM_VENDA_JA_CANCELADO");
        }
        validar(motivo != null && !motivo.isBlank(), "Motivo do cancelamento e obrigatorio.");
        this.status = StatusVendaItem.CANCELADO;
        this.canceladoPor = usuarioId;
        this.canceladoEm = LocalDateTime.now();
        this.motivoCancelamento = motivo.trim();
    }

    @PrePersist
    void prePersist() {
        if (criadoEm == null) {
            criadoEm = LocalDateTime.now();
        }
    }

    public long getId() { return id; }
    public Venda getVenda() { return venda; }
    public ItemCardapio getItemCardapio() { return itemCardapio; }
    public VariacaoItemCardapio getVariacaoItemCardapio() { return variacaoItemCardapio; }
    public BigDecimal getQuantidade() { return quantidade; }
    public String getNomeItemSnapshot() { return nomeItemSnapshot; }
    public String getNomeVariacaoSnapshot() { return nomeVariacaoSnapshot; }
    public String getCodigoVariacaoSnapshot() { return codigoVariacaoSnapshot; }
    public TipoComposicaoVariacao getTipoComposicaoSnapshot() { return tipoComposicaoSnapshot; }
    public BigDecimal getPrecoUnitarioBase() { return precoUnitarioBase; }
    public BigDecimal getDescontoUnitario() { return descontoUnitario; }
    public BigDecimal getAcrescimoUnitario() { return acrescimoUnitario; }
    public BigDecimal getPrecoUnitarioFinal() { return precoUnitarioFinal; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getCustoUnitarioEstimado() { return custoUnitarioEstimado; }
    public BigDecimal getCustoTotalEstimado() { return custoTotalEstimado; }
    public BigDecimal getMargemBrutaUnitariaEstimada() { return margemBrutaUnitariaEstimada; }
    public BigDecimal getMargemBrutaTotalEstimada() { return margemBrutaTotalEstimada; }
    public FichaTecnica getFichaTecnica() { return fichaTecnica; }
    public FichaTecnicaVersao getFichaTecnicaVersao() { return fichaTecnicaVersao; }
    public Integer getNumeroVersaoFicha() { return numeroVersaoFicha; }
    public FichaTecnica getFichaTecnicaObservada() { return fichaTecnicaObservada; }
    public FichaTecnicaVersao getFichaTecnicaVersaoObservada() { return fichaTecnicaVersaoObservada; }
    public Integer getNumeroVersaoFichaObservada() { return numeroVersaoFichaObservada; }
    public String getConfiguracaoModificadoresHashObservada() { return configuracaoModificadoresHashObservada; }
    public boolean isComposicaoMultipla() { return composicaoMultipla != null && composicaoMultipla; }
    public PoliticaPrecoComposicaoMultipla getPoliticaPrecoComposicao() { return politicaPrecoComposicao; }
    public Integer getQuantidadePartesSnapshot() { return quantidadePartesSnapshot; }
    public String getResumoPartesSnapshot() { return resumoPartesSnapshot; }
    public String getConfiguracaoMultissaborHashObservada() { return configuracaoMultissaborHashObservada; }
    public StatusVendaItem getStatus() { return status; }
    public String getObservacao() { return observacao; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public LocalDateTime getCanceladoEm() { return canceladoEm; }
    public Long getCanceladoPor() { return canceladoPor; }
    public String getMotivoCancelamento() { return motivoCancelamento; }
    public long getVersion() { return version; }

    private static BigDecimal dinheiro(BigDecimal valor) {
        return (valor == null ? BigDecimal.ZERO : valor).setScale(ESCALA_DINHEIRO, RoundingMode.HALF_UP);
    }

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
        if (!(o instanceof VendaItem other)) return false;
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
