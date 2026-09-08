package repository;

import model.OpcaoModificadorComponente;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class OpcaoModificadorComponenteRepositoryAdapter implements OpcaoModificadorComponenteRepository {

    private final JpaOpcaoModificadorComponenteRepository delegate;

    public OpcaoModificadorComponenteRepositoryAdapter(JpaOpcaoModificadorComponenteRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public OpcaoModificadorComponente salvar(OpcaoModificadorComponente componente) {
        return delegate.save(componente);
    }

    @Override
    public Optional<OpcaoModificadorComponente> buscarPorId(long id) {
        return delegate.findById(id);
    }

    @Override
    public List<OpcaoModificadorComponente> listarPorOpcao(long opcaoId) {
        return delegate.findByOpcao(opcaoId);
    }

    @Override
    public List<OpcaoModificadorComponente> listarPorOpcoes(List<Long> opcaoIds) {
        if (opcaoIds == null || opcaoIds.isEmpty()) {
            return List.of();
        }
        return delegate.findByOpcoes(opcaoIds);
    }

    @Override
    public void remover(OpcaoModificadorComponente componente) {
        delegate.delete(componente);
    }
}
