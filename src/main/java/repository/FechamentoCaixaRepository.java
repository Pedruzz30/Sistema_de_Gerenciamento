package repository;

import model.FechamentoCaixa;

import java.util.List;
import java.util.Optional;

public interface FechamentoCaixaRepository {

    FechamentoCaixa salvar(FechamentoCaixa fechamentoCaixa);

    Optional<FechamentoCaixa> buscarPorId(long id);

    List<FechamentoCaixa> listarTodos();
}
