package service;

import model.CanalVenda;
import model.LogAcao;
import model.Usuario;
import model.VariacaoCanalDisponibilidade;
import model.VariacaoItemCardapio;
import repository.LogRepository;
import repository.VariacaoCanalDisponibilidadeRepository;
import repository.VariacaoItemCardapioRepository;

public class DisponibilidadeCanalService {

    private final VariacaoCanalDisponibilidadeRepository canalRepository;
    private final VariacaoItemCardapioRepository variacaoRepository;
    private final LogRepository logRepository;

    public DisponibilidadeCanalService(VariacaoCanalDisponibilidadeRepository canalRepository,
                                       VariacaoItemCardapioRepository variacaoRepository,
                                       LogRepository logRepository) {
        this.canalRepository = canalRepository;
        this.variacaoRepository = variacaoRepository;
        this.logRepository = logRepository;
    }

    public VariacaoCanalDisponibilidade definir(long variacaoId,
                                                Long filialId,
                                                CanalVenda canal,
                                                boolean permitida,
                                                Usuario usuario) {
        if (filialId == null || filialId <= 0) {
            throw new ErroNegocioException("FILIAL_INVALIDA", "Filial e obrigatoria.");
        }
        if (canal == null) {
            throw new ErroNegocioException("CANAL_INVALIDO", "Canal e obrigatorio.");
        }
        VariacaoItemCardapio variacao = variacaoRepository.buscarPorId(variacaoId)
                .orElseThrow(() -> new ErroNegocioException("VARIACAO_NAO_ENCONTRADA", "Variacao nao encontrada."));
        VariacaoCanalDisponibilidade regra = canalRepository
                .buscarPorVariacaoFilialCanal(variacaoId, filialId, canal)
                .orElseGet(() -> new VariacaoCanalDisponibilidade(0, variacao, filialId, canal, permitida));
        regra.alterarPermitida(permitida);
        regra = canalRepository.salvar(regra);
        logRepository.salvar(new LogAcao(
                0,
                usuario == null ? null : usuario.getRu(),
                "CARDAPIO_CANAL_DISPONIBILIDADE",
                "Disponibilidade por canal alterada: variacao=" + variacaoId + ", canal=" + canal
        ));
        return regra;
    }
}
