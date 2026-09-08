package service;

import model.CanalVenda;
import model.ItemCardapio;
import model.LogAcao;
import model.RegraHorarioCardapio;
import model.Usuario;
import model.VariacaoItemCardapio;
import repository.ItemCardapioRepository;
import repository.LogRepository;
import repository.RegraHorarioCardapioRepository;
import repository.VariacaoItemCardapioRepository;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

public class RegraHorarioCardapioService {

    private final RegraHorarioCardapioRepository regraRepository;
    private final ItemCardapioRepository itemRepository;
    private final VariacaoItemCardapioRepository variacaoRepository;
    private final LogRepository logRepository;

    public RegraHorarioCardapioService(RegraHorarioCardapioRepository regraRepository,
                                       ItemCardapioRepository itemRepository,
                                       VariacaoItemCardapioRepository variacaoRepository,
                                       LogRepository logRepository) {
        this.regraRepository = regraRepository;
        this.itemRepository = itemRepository;
        this.variacaoRepository = variacaoRepository;
        this.logRepository = logRepository;
    }

    public List<RegraHorarioCardapio> listar(Long filialId) {
        if (filialId == null || filialId <= 0) {
            return regraRepository.listarTodas();
        }
        return regraRepository.listarAtivasPorFilial(filialId);
    }

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
                0,
                filialId,
                alvo.item(),
                alvo.variacao(),
                canal,
                diaSemana,
                horaInicio,
                horaFim,
                ativa
        );
        regra = regraRepository.salvar(regra);
        registrarLog(usuario, "CARDAPIO_REGRA_HORARIO_CRIADA", "Regra de horario criada: id=" + regra.getId());
        return regra;
    }

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
        if (item != null && variacao != null && variacao.getItemCardapio().getId() != item.getId()) {
            throw new ErroNegocioException("REGRA_HORARIO_INVALIDA", "Item e variacao da regra sao inconsistentes.");
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
                .filter(regra -> ignorarId == null || regra.getId() != ignorarId)
                .filter(regra -> mesmoAlvo(regra, item, variacao, canal))
                .filter(regra -> regra.getDiaSemana() == diaSemana)
                .anyMatch(regra -> intervalosSobrepostos(regra.getHoraInicio(), regra.getHoraFim(), inicio, fim));
        if (sobreposta) {
            throw new ErroNegocioException("REGRA_HORARIO_SOBREPOSTA", "Ja existe regra de horario sobreposta para o alvo.");
        }
    }

    private boolean mesmoAlvo(RegraHorarioCardapio regra,
                              ItemCardapio item,
                              VariacaoItemCardapio variacao,
                              CanalVenda canal) {
        long itemId = item == null ? 0 : item.getId();
        long regraItemId = regra.getItemCardapio() == null ? 0 : regra.getItemCardapio().getId();
        long variacaoId = variacao == null ? 0 : variacao.getId();
        long regraVariacaoId = regra.getVariacaoItemCardapio() == null ? 0 : regra.getVariacaoItemCardapio().getId();
        return regraItemId == itemId && regraVariacaoId == variacaoId && regra.getCanal() == canal;
    }

    private boolean intervalosSobrepostos(LocalTime aInicio, LocalTime aFim, LocalTime bInicio, LocalTime bFim) {
        List<IntervaloMinuto> a = quebrarIntervalo(aInicio, aFim);
        List<IntervaloMinuto> b = quebrarIntervalo(bInicio, bFim);
        return a.stream().anyMatch(ai -> b.stream().anyMatch(bi -> ai.inicio() < bi.fim() && bi.inicio() < ai.fim()));
    }

    private List<IntervaloMinuto> quebrarIntervalo(LocalTime inicio, LocalTime fim) {
        int ini = inicio.toSecondOfDay() / 60;
        int f = fim.toSecondOfDay() / 60;
        if (ini < f) {
            return List.of(new IntervaloMinuto(ini, f));
        }
        return List.of(new IntervaloMinuto(ini, 24 * 60), new IntervaloMinuto(0, f));
    }

    private void registrarLog(Usuario usuario, String acao, String descricao) {
        logRepository.salvar(new LogAcao(0, usuario == null ? null : usuario.getRu(), acao, descricao));
    }

    private record Alvo(ItemCardapio item, VariacaoItemCardapio variacao) {
    }

    private record IntervaloMinuto(int inicio, int fim) {
    }
}
