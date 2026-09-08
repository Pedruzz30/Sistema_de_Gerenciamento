package repository;

import model.FichaTecnicaIngredienteCustoSnapshot;

import java.util.List;

public interface FichaTecnicaIngredienteCustoSnapshotRepository {

    FichaTecnicaIngredienteCustoSnapshot salvar(FichaTecnicaIngredienteCustoSnapshot snapshot);

    List<FichaTecnicaIngredienteCustoSnapshot> listarPorSnapshot(long snapshotId);
}
