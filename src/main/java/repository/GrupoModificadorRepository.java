package repository;

import model.GrupoModificador;

import java.util.List;
import java.util.Optional;

public interface GrupoModificadorRepository {

    GrupoModificador salvar(GrupoModificador grupo);

    Optional<GrupoModificador> buscarPorId(long id);

    List<GrupoModificador> listarTodos();

    boolean existeCodigo(String codigo, Long ignorarId);
}
