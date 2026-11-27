package repository;

import model.CompraEstoque;

import java.util.List;
import java.util.Optional;

public interface CompraEstoqueRepository {

    CompraEstoque salvar(CompraEstoque compra);

    Optional<CompraEstoque> buscarPorId(long id);

    default Optional<CompraEstoque> buscarPorIdComLock(long id) {
        return buscarPorId(id);
    }

    List<CompraEstoque> listarTodos();
}
