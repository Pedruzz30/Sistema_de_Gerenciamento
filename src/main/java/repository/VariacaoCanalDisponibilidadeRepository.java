package repository;

import model.CanalVenda;
import model.VariacaoCanalDisponibilidade;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface VariacaoCanalDisponibilidadeRepository {

    VariacaoCanalDisponibilidade salvar(VariacaoCanalDisponibilidade regra);

    Optional<VariacaoCanalDisponibilidade> buscarPorVariacaoFilialCanal(long variacaoId, Long filialId, CanalVenda canal);

    List<VariacaoCanalDisponibilidade> listarPorVariacoesFilial(Collection<Long> variacaoIds, Long filialId);
}
