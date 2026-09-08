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
import jakarta.persistence.Version;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "pausas_cardapio")
public class PausaCardapio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(name = "filial_id", nullable = false)
    private Long filialId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "item_cardapio_id")
    private ItemCardapio itemCardapio;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "variacao_item_cardapio_id")
    private VariacaoItemCardapio variacaoItemCardapio;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private CanalVenda canal;

    @Column(nullable = false)
    private LocalDateTime inicio;

    @Column
    private LocalDateTime fim;

    @Enumerated(EnumType.STRING)
    @Column(name = "motivo_codigo", nullable = false, length = 40)
    private MotivoPausaCardapio motivoCodigo;

    @Column(name = "motivo_descricao", length = 500)
    private String motivoDescricao;

    @Column(nullable = false)
    private Boolean ativa = Boolean.TRUE;

    @Column(name = "criada_em", nullable = false)
    private LocalDateTime criadaEm;

    @Column(name = "criada_por")
    private Long criadaPor;

    @Column(name = "encerrada_em")
    private LocalDateTime encerradaEm;

    @Column(name = "encerrada_por")
    private Long encerradaPor;

    @Version
    private long version;

    protected PausaCardapio() {
        // JPA
    }

    public PausaCardapio(long id,
                         Long filialId,
                         ItemCardapio itemCardapio,
                         VariacaoItemCardapio variacaoItemCardapio,
                         CanalVenda canal,
                         LocalDateTime inicio,
                         LocalDateTime fim,
                         MotivoPausaCardapio motivoCodigo,
                         String motivoDescricao,
                         Long criadaPor) {
        validar(id >= 0, "ID da pausa nao pode ser negativo.");
        validar(filialId != null && filialId > 0, "Filial da pausa e obrigatoria.");
        validar(itemCardapio != null || variacaoItemCardapio != null, "Pausa exige item ou variacao.");
        validar(inicio != null, "Inicio da pausa e obrigatorio.");
        validar(fim == null || fim.isAfter(inicio), "Fim da pausa deve ser posterior ao inicio.");
        validar(motivoCodigo != null, "Motivo da pausa e obrigatorio.");

        this.id = id;
        this.filialId = filialId;
        this.itemCardapio = itemCardapio;
        this.variacaoItemCardapio = variacaoItemCardapio;
        this.canal = canal;
        this.inicio = inicio;
        this.fim = fim;
        this.motivoCodigo = motivoCodigo;
        this.motivoDescricao = motivoDescricao == null ? null : motivoDescricao.trim();
        this.ativa = Boolean.TRUE;
        this.criadaEm = LocalDateTime.now();
        this.criadaPor = criadaPor;
    }

    public void encerrar(Long usuarioId) {
        if (!isAtiva()) {
            throw new IllegalStateException("Pausa ja encerrada.");
        }
        this.ativa = Boolean.FALSE;
        this.encerradaEm = LocalDateTime.now();
        this.encerradaPor = usuarioId;
    }

    public boolean estaVigente(LocalDateTime agora) {
        return isAtiva()
                && !inicio.isAfter(agora)
                && (fim == null || fim.isAfter(agora));
    }

    public boolean atinge(VariacaoItemCardapio variacao, CanalVenda canalConsulta) {
        if (canal != null && canal != canalConsulta) {
            return false;
        }
        if (variacaoItemCardapio != null) {
            return variacao != null && variacaoItemCardapio.getId() == variacao.getId();
        }
        return itemCardapio != null
                && variacao != null
                && variacao.getItemCardapio() != null
                && itemCardapio.getId() == variacao.getItemCardapio().getId();
    }

    public long getId() { return id; }
    public Long getFilialId() { return filialId; }
    public ItemCardapio getItemCardapio() { return itemCardapio; }
    public VariacaoItemCardapio getVariacaoItemCardapio() { return variacaoItemCardapio; }
    public CanalVenda getCanal() { return canal; }
    public LocalDateTime getInicio() { return inicio; }
    public LocalDateTime getFim() { return fim; }
    public MotivoPausaCardapio getMotivoCodigo() { return motivoCodigo; }
    public String getMotivoDescricao() { return motivoDescricao; }
    public boolean isAtiva() { return ativa == null || ativa; }
    public LocalDateTime getCriadaEm() { return criadaEm; }
    public Long getCriadaPor() { return criadaPor; }
    public LocalDateTime getEncerradaEm() { return encerradaEm; }
    public Long getEncerradaPor() { return encerradaPor; }
    public long getVersion() { return version; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PausaCardapio other)) return false;
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
