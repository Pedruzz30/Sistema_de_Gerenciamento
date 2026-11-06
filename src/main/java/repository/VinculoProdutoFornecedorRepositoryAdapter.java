package repository;

import model.VinculoProdutoFornecedor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class VinculoProdutoFornecedorRepositoryAdapter implements VinculoProdutoFornecedorRepository {

    private final JpaVinculoProdutoFornecedorRepository delegate;

    public VinculoProdutoFornecedorRepositoryAdapter(JpaVinculoProdutoFornecedorRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public VinculoProdutoFornecedor salvar(VinculoProdutoFornecedor vinculo) {
        return delegate.save(vinculo);
    }

    @Override
    public Optional<VinculoProdutoFornecedor> buscar(long fornecedorId, String codigoProduto) {
        return delegate.findByFornecedorIdAndCodigoProduto(fornecedorId, codigoProduto);
    }
}
