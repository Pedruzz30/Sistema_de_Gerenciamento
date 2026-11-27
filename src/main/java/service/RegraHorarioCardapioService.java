package service;

import model.CanalVenda;
import model.ItemCardapio;
import model.LogAcao;
import model.RegraHorarioCardapio;
import model.Usuario;
import model.VariacaoItemCardapio;
import org.springframework.transaction.annotation.Transactional;
import repository.ItemCardapioRepository;
import repository.LogRepository;
import repository.RegraHorarioCardapioRepository;
import repository.VariacaoItemCardapioRepository;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

public class RegraHorarioCardapioService {

    private static final long NANOS_POR_DIA = 86_400_000_000_000L;
    private static final long NANOS_POR_SEMANA = 7 * NANOS_POR_DIA;

    private final RegraHorarioCardapioRepository regraRepository;
    private final ItemCardapioRepository itemRepository;
    private final VariacaoItemCardapioRepository variacaoRepository;
    private final LogRepository logRepository;

    public RegraHorarioCardapioService(RegraHorarioCardapioRepository regraRepository,
                                       ItemCardapioRepository itemRepository,
                                       VariacaoItemCardapioRepository variacaoRepository,
                                       LogRepository logRepository) {
        this.regraRepository = Objects.requireNonNull(regraRepository, "regraRepository");
        this.itemRepository = Objects.requireNonNull(itemRepository, "itemRepository");
        this.variacaoRepository = Objects.requireNonNull(variacaoRepository, "variacaoRepository");
        this.logRepository = Objects.requireNonNull(logRepository, "logRepository");
    }

    @Transactional(readOnly = true)
    public List<RegraHorarioCardapio> listar(Long filialId) {
        if (filialId == null || filialId <= 0) {
            return regraRepository.listarTodas();
        }
        return regraRepository.listarAtivasPorFilial(filialId);
    }

    @Transactional
    public RegraHorarioCardapio criar(Long filialId,
                                      Integer itemId,
                                      Long variacaoId,
                                      CanalVenda canal,
                                      DayOfWeek diaSemana,
                                      LocalTime horaInicio,
                                      LocalTime horaFim,
                                      Boolean ativa,
                                      Usuario usuario) {
        Alvo alvo = resolverAlvo(itemId, variacaoId);
        validarSobreposicao(null, filialId, alvo.item(), alvo.variacao(), canal, diaSemana, horaInicio, horaFim);
        RegraHorarioCardapio regra = new RegraHorarioCardapio(
                0, filialId, alvo.item(), alvo.variacao(), canal, diaSemana, horaInicio, horaFim, ativa
        );
        regra = regraRepository.salvar(regra);
        registrarLog(usuario, "CARDAPIO_REGRA_HORARIO_CRIADA", "Regra de horario criada: id=" + regra.getId());
        return regra;
    }

    @Transactional
    public RegraHorarioCardapio atualizar(long id,
                                          Integer itemId,
                                          Long variacaoId,
                                          CanalVenda canal,
                                          DayOfWeek diaSemana,
                                          LocalTime horaInicio,
                                          LocalTime horaFim,
                                          Boolean ativa,
                                          Usuario usuario) {
        RegraHorarioCardapio regra = buscarObrigatoria(id);
        Alvo alvo = resolverAlvo(itemId, variacaoId);
        validarSobreposicao(id, regra.getFilialId(), alvo.item(), alvo.variacao(), canal, diaSemana, horaInicio, horaFim);
        regra.atualizar(alvo.item(), alvo.variacao(), canal, diaSemana, horaInicio, horaFim, ativa);
        regra = regraRepository.salvar(regra);
        registrarLog(usuario, "CARDAPIO_REGRA_HORARIO_ATUALIZADA", "Regra de horario atualizada: id=" + regra.getId());
        return regra;
    }

    @Transactional
    public void remover(long id, Usuario usuario) {
        RegraHorarioCardapio regra = buscarObrigatoria(id);
        regra.inativar();
        regraRepository.salvar(regra);
        registrarLog(usuario, "CARDAPIO_REGRA_HORARIO_INATIVADA", "Regra de horario inativada: id=" + regra.getId());
    }

    private RegraHorarioCardapio buscarObrigatoria(long id) {
        return regraRepository.buscarPorId(id)
                .orElseThrow(() -> new ErroNegocioException("REGRA_HORARIO_NAO_ENCONTRADA", "Regra de horario nao encontrada."));
    }

    private Alvo resolverAlvo(Integer itemId, Long variacaoId) {
        VariacaoItemCardapio variacao = variacaoId == null
                ? null
                : variacaoRepository.buscarPorId(variacaoId)
                .orElseThrow(() -> new ErroNegocioException("VARIACAO_NAO_ENCONTRADA", "Variacao nao encontrada."));
        ItemCardapio item = itemId == null
                ? null
                : itemRepository.buscarPorId(itemId)
                .orElseThrow(() -> new ErroNegocioException("ITEM_CARDAPIO_NAO_ENCONTRADO", "Item de cardapio nao encontrado."));
        if (item == null && variacao == null) {
            throw new ErroNegocioException("REGRA_HORARIO_INVALIDA", "Regra de horario exige item ou variacao.");
        }
        if (item != null && variacao != null) {
            ItemCardapio itemDaVariacao = variacao.getItemCardapio();
            if (itemDaVariacao == null || !Objects.equals(itemDaVariacao.getId(), item.getId())) {
                throw new ErroNegocioException("REGRA_HORARIO_INVALIDA", "Item e variacao da regra sao inconsistentes.");
            }
        }
        return new Alvo(item, variacao);
    }

    private void validarSobreposicao(Long ignorarId,
                                     Long filialId,
                                     ItemCardapio item,
                                     VariacaoItemCardapio variacao,
                                     CanalVenda canal,
                                     DayOfWeek diaSemana,
                                     LocalTime inicio,
                                     LocalTime fim) {
        if (filialId == null || filialId <= 0 || diaSemana == null || inicio == null || fim == null || inicio.equals(fim)) {
            throw new ErroNegocioException("REGRA_HORARIO_INVALIDA", "Regra de horario invalida.");
        }
        boolean sobreposta = regraRepository.listarAtivasPorFilial(filialId).stream()
                .filter(regra -> ignorarId == null || !Objects.equals(regra.getId(), ignorarId))
                .filter(regra -> mesmoAlvo(regra, item, variacao, canal))
                .anyMatch(regra -> intervalosSobrepostos(
                        regra.getDiaSemana(), regra.getHoraInicio(), regra.getHoraFim(),
                        diaSemana, inicio, fim));
        if (sobreposta) {
            throw new ErroNegocioException("REGRA_HORARIO_SOBREPOSTA", "Ja existe regra de horario sobreposta para o alvo.");
        }
    }

    private boolean mesmoAlvo(RegraHorarioCardapio regra,
                              ItemCardapio item,
                              VariacaoItemCardapio variacao,
                              CanalVenda canal) {
        ItemCardapio regraItem = regra.getItemCardapio();
        VariacaoItemCardapio regraVariacao = regra.getVariacaoItemCardapio();
        long itemId = item == null ? 0 : item.getId();
        long regraItemId = regraItem == null ? 0 : regraItem.getId();
        long variacaoId = variacao == null ? 0 : variacao.getId();
        long regraVariacaoId = regraVariacao == null ? 0 : regraVariacao.getId();
        return regraItemId == itemId && regraVariacaoId == variacaoId && regra.getCanal() == canal;
    }

    /** Intervalos semiabertos [inicio, fim), com recorrencia semanal. */
    static boolean intervalosSobrepostos(DayOfWeek diaA, LocalTime inicioA, LocalTime fimA,
                                         DayOfWeek diaB, LocalTime inicioB, LocalTime fimB) {
        IntervaloSemanal a = intervaloSemanal(diaA, inicioA, fimA);
        IntervaloSemanal b = intervaloSemanal(diaB, inicioB, fimB);
        return intercepta(a, b, 0)
                || intercepta(a, b, NANOS_POR_SEMANA)
                || intercepta(a, b, -NANOS_POR_SEMANA);
    }

    private static IntervaloSemanal intervaloSemanal(DayOfWeek dia, LocalTime inicio, LocalTime fim) {
        if (dia == null || inicio == null || fim == null || inicio.equals(fim)) {
            throw new ErroNegocioException("REGRA_HORARIO_INVALIDA", "Regra de horario invalida.");
        }
        long deslocamento = (dia.getValue() - 1L) * NANOS_POR_DIA;
        long inicioAbsoluto = deslocamento + inicio.toNanoOfDay();
        long fimAbsoluto = deslocamento + fim.toNanoOfDay();
        if (fimAbsoluto < inicioAbsoluto) {
            fimAbsoluto += NANOS_POR_DIA;
        }
        return new IntervaloSemanal(inicioAbsoluto, fimAbsoluto);
    }

    private static boolean intercepta(IntervaloSemanal a, IntervaloSemanal b, long deslocamento) {
        return a.inicio() < b.fim() + deslocamento
                && b.inicio() + deslocamento < a.fim();
    }

    private void registrarLog(Usuario usuario, String acao, String descricao) {
        logRepository.salvar(new LogAcao(0, usuario == null ? null : usuario.getRu(), acao, descricao));
    }

    private record Alvo(ItemCardapio item, VariacaoItemCardapio variacao) {}
    private record IntervaloSemanal(long inicio, long fim) {}
}
