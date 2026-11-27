package repository;

import model.FichaTecnicaIngredienteCustoSnapshot;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;

@Repository
public class FichaTecnicaIngredienteCustoSnapshotRepositoryAdapter
        implements FichaTecnicaIngredienteCustoSnapshotRepository {

    private final JpaFichaTecnicaIngredienteCustoSnapshotRepository delegate;

    public FichaTecnicaIngredienteCustoSnapshotRepositoryAdapter(
            JpaFichaTecnicaIngredienteCustoSnapshotRepository delegate) {

        this.delegate = Objects.requireNonNull(
                delegate,
                "Repositorio JPA de snapshot de custo de ingrediente e obrigatorio.");
    }

    @Override
    public FichaTecnicaIngredienteCustoSnapshot salvar(
            FichaTecnicaIngredienteCustoSnapshot snapshot) {

        if (snapshot == null) {
            throw new IllegalArgumentException(
                    "Snapshot de custo do ingrediente e obrigatorio.");
        }

        return delegate.save(snapshot);
    }

    @Override
    public List<FichaTecnicaIngredienteCustoSnapshot> listarPorSnapshot(
            long snapshotId) {

        return delegate.findBySnapshot(snapshotId);
    }
}
