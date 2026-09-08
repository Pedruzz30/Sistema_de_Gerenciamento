package repository;

import model.FichaTecnicaCustoSnapshot;

import java.util.List;
import java.util.Optional;

public interface FichaTecnicaCustoSnapshotRepository {

    FichaTecnicaCustoSnapshot salvar(FichaTecnicaCustoSnapshot snapshot);

    Optional<FichaTecnicaCustoSnapshot> buscarPorVersaoEFilial(long versaoId, Long filialId);

    List<FichaTecnicaCustoSnapshot> listarPorVersao(long versaoId);
}
