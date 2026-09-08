package repository;

import model.FichaTecnicaCustoSnapshot;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class FichaTecnicaCustoSnapshotRepositoryAdapter implements FichaTecnicaCustoSnapshotRepository {

    private final JpaFichaTecnicaCustoSnapshotRepository delegate;

    public FichaTecnicaCustoSnapshotRepositoryAdapter(JpaFichaTecnicaCustoSnapshotRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public FichaTecnicaCustoSnapshot salvar(FichaTecnicaCustoSnapshot snapshot) {
        return delegate.save(snapshot);
    }

    @Override
    public Optional<FichaTecnicaCustoSnapshot> buscarPorVersaoEFilial(long versaoId, Long filialId) {
        return delegate.findByVersaoAndFilial(versaoId, filialId);
    }

    @Override
    public List<FichaTecnicaCustoSnapshot> listarPorVersao(long versaoId) {
        return delegate.findByVersao(versaoId);
    }
}
