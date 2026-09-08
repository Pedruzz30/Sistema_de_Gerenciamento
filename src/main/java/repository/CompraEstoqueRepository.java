package repository;

import model.CompraEstoque;

import java.util.List;
import java.util.Optional;

public interface CompraEstoqueRepository {

    CompraEstoque salvar(CompraEstoque compra);

    Optional<CompraEstoque> buscarPorId(long id);

    List<CompraEstoque> listarTodos();
}
