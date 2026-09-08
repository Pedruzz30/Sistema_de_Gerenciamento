package repository;

import model.UnidadeMedida;

import java.util.List;
import java.util.Optional;

public interface UnidadeMedidaRepository {

    UnidadeMedida salvar(UnidadeMedida unidade);

    Optional<UnidadeMedida> buscarPorId(long id);

    Optional<UnidadeMedida> buscarPorCodigo(String codigo);

    List<UnidadeMedida> listarTodos();
}
