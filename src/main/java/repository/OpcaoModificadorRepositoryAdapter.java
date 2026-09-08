package repository;

import model.OpcaoModificador;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class OpcaoModificadorRepositoryAdapter implements OpcaoModificadorRepository {

    private final JpaOpcaoModificadorRepository delegate;

    public OpcaoModificadorRepositoryAdapter(JpaOpcaoModificadorRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public OpcaoModificador salvar(OpcaoModificador opcao) {
        return delegate.save(opcao);
    }

    @Override
    public Optional<OpcaoModificador> buscarPorId(long id) {
        return delegate.findById(id);
    }

    @Override
    public List<OpcaoModificador> listarPorGrupo(long grupoId) {
        return delegate.findByGrupo(grupoId);
    }

    @Override
    public List<OpcaoModificador> listarPorGrupos(List<Long> grupoIds) {
        if (grupoIds == null || grupoIds.isEmpty()) {
            return List.of();
        }
        return delegate.findByGrupos(grupoIds);
    }

    @Override
    public List<OpcaoModificador> listarPorIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return delegate.findByIds(ids);
    }

    @Override
    public boolean existeCodigoNoGrupo(long grupoId, String codigo, Long ignorarId) {
        return delegate.existsCodigoNoGrupo(grupoId, codigo, ignorarId);
    }
}
