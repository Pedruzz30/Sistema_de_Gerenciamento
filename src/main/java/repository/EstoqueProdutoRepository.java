package repository;

import model.EstoqueProduto;

import java.util.List;
import java.util.Optional;

public interface EstoqueProdutoRepository {

    EstoqueProduto salvar(EstoqueProduto estoque);

    Optional<EstoqueProduto> buscarPorFilialEProduto(Long filialId, int produtoId);

    List<EstoqueProduto> listarPorFilialEProdutos(Long filialId, List<Integer> produtoIds);

    Optional<EstoqueProduto> buscarPorFilialEProdutoComLock(Long filialId, int produtoId);

    List<EstoqueProduto> listarTodos();
}
