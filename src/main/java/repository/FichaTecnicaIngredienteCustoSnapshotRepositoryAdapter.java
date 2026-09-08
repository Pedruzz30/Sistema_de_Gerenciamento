package repository;

import model.FichaTecnicaIngredienteCustoSnapshot;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class FichaTecnicaIngredienteCustoSnapshotRepositoryAdapter implements FichaTecnicaIngredienteCustoSnapshotRepository {

    private final JpaFichaTecnicaIngredienteCustoSnapshotRepository delegate;

    public FichaTecnicaIngredienteCustoSnapshotRepositoryAdapter(JpaFichaTecnicaIngredienteCustoSnapshotRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public FichaTecnicaIngredienteCustoSnapshot salvar(FichaTecnicaIngredienteCustoSnapshot snapshot) {
        return delegate.save(snapshot);
    }

    @Override
    public List<FichaTecnicaIngredienteCustoSnapshot> listarPorSnapshot(long snapshotId) {
        return delegate.findBySnapshot(snapshotId);
    }
}
