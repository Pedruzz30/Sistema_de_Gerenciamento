package repository;

import model.FichaTecnica;

import java.util.List;
import java.util.Optional;

public interface FichaTecnicaRepository {

    FichaTecnica salvar(FichaTecnica fichaTecnica);

    Optional<FichaTecnica> buscarPorId(long id);

    Optional<FichaTecnica> buscarPorIdComLock(long id);

    Optional<FichaTecnica> buscarAtivaPorVariacao(long variacaoId);

    List<FichaTecnica> listarAtivasPorVariacoes(List<Long> variacaoIds);

    List<FichaTecnica> listarTodos();
}
