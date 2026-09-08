package repository;

import model.FichaTecnica;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class FichaTecnicaRepositoryAdapter implements FichaTecnicaRepository {

    private final JpaFichaTecnicaRepository delegate;

    public FichaTecnicaRepositoryAdapter(JpaFichaTecnicaRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public FichaTecnica salvar(FichaTecnica fichaTecnica) {
        return delegate.save(fichaTecnica);
    }

    @Override
    public Optional<FichaTecnica> buscarPorId(long id) {
        return delegate.findById(id);
    }

    @Override
    public Optional<FichaTecnica> buscarPorIdComLock(long id) {
        return delegate.findByIdForUpdate(id);
    }

    @Override
    public Optional<FichaTecnica> buscarAtivaPorVariacao(long variacaoId) {
        return delegate.findAtivaByVariacao(variacaoId);
    }

    @Override
    public List<FichaTecnica> listarAtivasPorVariacoes(List<Long> variacaoIds) {
        if (variacaoIds == null || variacaoIds.isEmpty()) {
            return List.of();
        }
        return delegate.findAtivasByVariacoes(variacaoIds);
    }

    @Override
    public List<FichaTecnica> listarTodos() {
        return delegate.findAll();
    }
}
