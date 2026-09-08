package repository;

import model.CompraEstoque;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class CompraEstoqueRepositoryAdapter implements CompraEstoqueRepository {

    private final JpaCompraEstoqueRepository delegate;

    public CompraEstoqueRepositoryAdapter(JpaCompraEstoqueRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public CompraEstoque salvar(CompraEstoque compra) {
        return delegate.save(compra);
    }

    @Override
    public Optional<CompraEstoque> buscarPorId(long id) {
        return delegate.findById(id);
    }

    @Override
    public List<CompraEstoque> listarTodos() {
        return delegate.findAllByOrderByIdDesc();
    }
}
