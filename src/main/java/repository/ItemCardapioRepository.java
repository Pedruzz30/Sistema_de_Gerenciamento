package repository;

import model.ItemCardapio;

import java.util.List;
import java.util.Optional;

public interface ItemCardapioRepository {

    ItemCardapio salvar(ItemCardapio itemCardapio);

    Optional<ItemCardapio> buscarPorId(int id);

    Optional<ItemCardapio> buscarPorCodigo(String codigo);

    Optional<ItemCardapio> buscarPrimeiroPorProdutoVinculadoId(int produtoId);

    List<ItemCardapio> listarTodos();
}
