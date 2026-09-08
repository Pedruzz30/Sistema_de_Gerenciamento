package service;

import model.CanalVenda;
import model.ItemCardapio;
import model.LogAcao;
import model.MotivoPausaCardapio;
import model.PausaCardapio;
import model.Usuario;
import model.VariacaoItemCardapio;
import repository.ItemCardapioRepository;
import repository.LogRepository;
import repository.PausaCardapioRepository;
import repository.VariacaoItemCardapioRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

public class PausaCardapioService {

    private final PausaCardapioRepository pausaRepository;
    private final ItemCardapioRepository itemRepository;
    private final VariacaoItemCardapioRepository variacaoRepository;
    private final LogRepository logRepository;
    private final Clock clock;

    public PausaCardapioService(PausaCardapioRepository pausaRepository,
                                ItemCardapioRepository itemRepository,
                                VariacaoItemCardapioRepository variacaoRepository,
                                LogRepository logRepository,
                                Clock clock) {
        this.pausaRepository = pausaRepository;
        this.itemRepository = itemRepository;
        this.variacaoRepository = variacaoRepository;
        this.logRepository = logRepository;
        this.clock = clock;
    }

    public PausaCardapio criar(Long filialId,
                               Integer itemId,
                               Long variacaoId,
                               CanalVenda canal,
                               LocalDateTime inicio,
                               LocalDateTime fim,
                               MotivoPausaCardapio motivoCodigo,
                               String motivoDescricao,
                               Usuario usuario) {
        LocalDateTime inicioEfetivo = inicio == null ? LocalDateTime.now(clock) : inicio;
        if (fim != null && !fim.isAfter(inicioEfetivo)) {
            throw new ErroNegocioException("PAUSA_PERIODO_INVALIDO", "Fim da pausa deve ser posterior ao inicio.");
        }
        VariacaoItemCardapio variacao = variacaoId == null
                ? null
                : variacaoRepository.buscarPorId(variacaoId)
                .orElseThrow(() -> new ErroNegocioException("VARIACAO_NAO_ENCONTRADA", "Variacao nao encontrada."));
        ItemCardapio item = itemId == null
                ? null
                : itemRepository.buscarPorId(itemId)
                .orElseThrow(() -> new ErroNegocioException("ITEM_CARDAPIO_NAO_ENCONTRADO", "Item de cardapio nao encontrado."));
        if (item == null && variacao == null) {
            throw new ErroNegocioException("CONFIGURACAO_DISPONIBILIDADE_INCOMPLETA", "Pausa exige item ou variacao.");
        }
        if (item != null && variacao != null && variacao.getItemCardapio().getId() != item.getId()) {
            throw new ErroNegocioException("CONFIGURACAO_DISPONIBILIDADE_INCOMPLETA", "Item e variacao da pausa sao inconsistentes.");
        }
        PausaCardapio pausa = new PausaCardapio(
                0,
                filialId,
                item,
                variacao,
                canal,
                inicioEfetivo,
                fim,
                motivoCodigo,
                motivoDescricao,
                usuario == null ? null : usuario.getRu()
        );
        pausa = pausaRepository.salvar(pausa);
        registrarLog(usuario, "CARDAPIO_PAUSA_CRIADA", "Pausa de cardapio criada: id=" + pausa.getId());
        return pausa;
    }

    public List<PausaCardapio> listarTodas() {
        return pausaRepository.listarTodas();
    }

    public List<PausaCardapio> listarAtivas(Long filialId) {
        if (filialId == null || filialId <= 0) {
            throw new ErroNegocioException("FILIAL_INVALIDA", "Filial e obrigatoria.");
        }
        return pausaRepository.listarAtivas(filialId, LocalDateTime.now(clock));
    }

    public PausaCardapio encerrar(long id, Usuario usuario) {
        PausaCardapio pausa = pausaRepository.buscarPorId(id)
                .orElseThrow(() -> new ErroNegocioException("PAUSA_NAO_ENCONTRADA", "Pausa nao encontrada."));
        if (!pausa.isAtiva()) {
            throw new ErroNegocioException("PAUSA_JA_ENCERRADA", "Pausa ja encerrada.");
        }
        pausa.encerrar(usuario == null ? null : usuario.getRu());
        pausa = pausaRepository.salvar(pausa);
        registrarLog(usuario, "CARDAPIO_PAUSA_ENCERRADA", "Pausa de cardapio encerrada: id=" + pausa.getId());
        return pausa;
    }

    private void registrarLog(Usuario usuario, String acao, String descricao) {
        logRepository.salvar(new LogAcao(0, usuario == null ? null : usuario.getRu(), acao, descricao));
    }
}
