package repository;

import model.VariacaoGrupoModificador;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class VariacaoGrupoModificadorRepositoryAdapter implements VariacaoGrupoModificadorRepository {

    private final JpaVariacaoGrupoModificadorRepository delegate;

    public VariacaoGrupoModificadorRepositoryAdapter(JpaVariacaoGrupoModificadorRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public VariacaoGrupoModificador salvar(VariacaoGrupoModificador associacao) {
        return delegate.save(associacao);
    }

    @Override
    public Optional<VariacaoGrupoModificador> buscarPorId(long id) {
        return delegate.findById(id);
    }

    @Override
    public Optional<VariacaoGrupoModificador> buscarPorVariacaoEGrupo(long variacaoId, long grupoId) {
        return delegate.findByVariacaoAndGrupo(variacaoId, grupoId);
    }

    @Override
    public List<VariacaoGrupoModificador> listarPorVariacao(long variacaoId) {
        return delegate.findByVariacao(variacaoId);
    }

    @Override
    public List<VariacaoGrupoModificador> listarAtivasPorVariacao(long variacaoId) {
        return delegate.findAtivasByVariacao(variacaoId);
    }

    @Override
    public List<VariacaoGrupoModificador> listarAtivasPorVariacoes(List<Long> variacaoIds) {
        if (variacaoIds == null || variacaoIds.isEmpty()) {
            return List.of();
        }
        return delegate.findAtivasByVariacoes(variacaoIds);
    }
}
