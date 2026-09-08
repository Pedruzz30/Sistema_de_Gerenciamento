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

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Objects;

@Entity
@Table(name = "regras_horario_cardapio")
public class RegraHorarioCardapio {

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

    @Enumerated(EnumType.STRING)
    @Column(name = "dia_semana", nullable = false, length = 20)
    private DayOfWeek diaSemana;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fim", nullable = false)
    private LocalTime horaFim;

    @Column(nullable = false)
    private Boolean ativa = Boolean.TRUE;

    @Version
    private long version;

    protected RegraHorarioCardapio() {
        // JPA
    }

    public RegraHorarioCardapio(long id,
                                Long filialId,
                                ItemCardapio itemCardapio,
                                VariacaoItemCardapio variacaoItemCardapio,
                                CanalVenda canal,
                                DayOfWeek diaSemana,
                                LocalTime horaInicio,
                                LocalTime horaFim,
                                Boolean ativa) {
        validar(id >= 0, "ID da regra de horario nao pode ser negativo.");
        validar(filialId != null && filialId > 0, "Filial da regra e obrigatoria.");
        validar(itemCardapio != null || variacaoItemCardapio != null, "Regra de horario exige item ou variacao.");
        validar(diaSemana != null, "Dia da semana e obrigatorio.");
        validar(horaInicio != null && horaFim != null, "Horario de inicio e fim sao obrigatorios.");
        validar(!horaInicio.equals(horaFim), "Horario de inicio e fim nao podem ser iguais.");

        this.id = id;
        this.filialId = filialId;
        this.itemCardapio = itemCardapio;
        this.variacaoItemCardapio = variacaoItemCardapio;
        this.canal = canal;
        this.diaSemana = diaSemana;
        this.horaInicio = horaInicio;
        this.horaFim = horaFim;
        this.ativa = ativa == null || ativa;
    }

    public void atualizar(ItemCardapio itemCardapio,
                          VariacaoItemCardapio variacaoItemCardapio,
                          CanalVenda canal,
                          DayOfWeek diaSemana,
                          LocalTime horaInicio,
                          LocalTime horaFim,
                          Boolean ativa) {
        validar(itemCardapio != null || variacaoItemCardapio != null, "Regra de horario exige item ou variacao.");
        validar(diaSemana != null, "Dia da semana e obrigatorio.");
        validar(horaInicio != null && horaFim != null, "Horario de inicio e fim sao obrigatorios.");
        validar(!horaInicio.equals(horaFim), "Horario de inicio e fim nao podem ser iguais.");
        this.itemCardapio = itemCardapio;
        this.variacaoItemCardapio = variacaoItemCardapio;
        this.canal = canal;
        this.diaSemana = diaSemana;
        this.horaInicio = horaInicio;
        this.horaFim = horaFim;
        this.ativa = ativa == null || ativa;
    }

    public void inativar() {
        this.ativa = Boolean.FALSE;
    }

    public boolean atinge(VariacaoItemCardapio variacao, CanalVenda canalConsulta) {
        if (!isAtiva()) {
            return false;
        }
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

    public boolean permite(DayOfWeek dia, LocalTime hora) {
        if (dia != diaSemana) {
            return false;
        }
        if (horaInicio.isBefore(horaFim)) {
            return !hora.isBefore(horaInicio) && hora.isBefore(horaFim);
        }
        return !hora.isBefore(horaInicio) || hora.isBefore(horaFim);
    }

    public long getId() { return id; }
    public Long getFilialId() { return filialId; }
    public ItemCardapio getItemCardapio() { return itemCardapio; }
    public VariacaoItemCardapio getVariacaoItemCardapio() { return variacaoItemCardapio; }
    public CanalVenda getCanal() { return canal; }
    public DayOfWeek getDiaSemana() { return diaSemana; }
    public LocalTime getHoraInicio() { return horaInicio; }
    public LocalTime getHoraFim() { return horaFim; }
    public boolean isAtiva() { return ativa == null || ativa; }
    public long getVersion() { return version; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RegraHorarioCardapio other)) return false;
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
