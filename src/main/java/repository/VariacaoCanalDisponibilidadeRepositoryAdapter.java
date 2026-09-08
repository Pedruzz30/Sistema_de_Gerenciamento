package repository;

import model.CanalVenda;
import model.VariacaoCanalDisponibilidade;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public class VariacaoCanalDisponibilidadeRepositoryAdapter implements VariacaoCanalDisponibilidadeRepository {

    private final JpaVariacaoCanalDisponibilidadeRepository delegate;

    public VariacaoCanalDisponibilidadeRepositoryAdapter(JpaVariacaoCanalDisponibilidadeRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public VariacaoCanalDisponibilidade salvar(VariacaoCanalDisponibilidade regra) {
        return delegate.save(regra);
    }

    @Override
    public Optional<VariacaoCanalDisponibilidade> buscarPorVariacaoFilialCanal(long variacaoId, Long filialId, CanalVenda canal) {
        return delegate.findByVariacaoFilialCanal(variacaoId, filialId, canal);
    }

    @Override
    public List<VariacaoCanalDisponibilidade> listarPorVariacoesFilial(Collection<Long> variacaoIds, Long filialId) {
        if (variacaoIds == null || variacaoIds.isEmpty()) {
            return List.of();
        }
        return delegate.findByVariacoesFilial(variacaoIds, filialId);
    }
}
