package repository;

import model.CategoriaCardapio;

import java.util.List;
import java.util.Optional;

public interface CategoriaCardapioRepository {

    CategoriaCardapio salvar(CategoriaCardapio categoriaCardapio);

    Optional<CategoriaCardapio> buscarPorId(int id);

    Optional<CategoriaCardapio> buscarPorCodigo(String codigo);

    List<CategoriaCardapio> listarTodos();
}
