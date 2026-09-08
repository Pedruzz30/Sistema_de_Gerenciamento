package repository;

import model.EstoqueProduto;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class EstoqueProdutoRepositoryAdapter implements EstoqueProdutoRepository {

    private final JpaEstoqueProdutoRepository delegate;

    public EstoqueProdutoRepositoryAdapter(JpaEstoqueProdutoRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public EstoqueProduto salvar(EstoqueProduto estoque) {
        return delegate.save(estoque);
    }

    @Override
    public Optional<EstoqueProduto> buscarPorFilialEProduto(Long filialId, int produtoId) {
        return delegate.findByFilialAndProduto(filialId, produtoId);
    }

    @Override
    public List<EstoqueProduto> listarPorFilialEProdutos(Long filialId, List<Integer> produtoIds) {
        if (produtoIds == null || produtoIds.isEmpty()) {
            return List.of();
        }
        return delegate.findByFilialAndProdutos(filialId, produtoIds);
    }

    @Override
    public Optional<EstoqueProduto> buscarPorFilialEProdutoComLock(Long filialId, int produtoId) {
        return delegate.findByFilialAndProdutoForUpdate(filialId, produtoId);
    }

    @Override
    public List<EstoqueProduto> listarTodos() {
        return delegate.findAllOrdenado();
    }
}
