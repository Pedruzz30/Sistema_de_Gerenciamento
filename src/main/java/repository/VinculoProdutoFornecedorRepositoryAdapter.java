package repository;

import model.VinculoProdutoFornecedor;
import org.springframework.stereotype.Repository;

import java.util.Objects;
import java.util.Optional;

@Repository
public class VinculoProdutoFornecedorRepositoryAdapter
        implements VinculoProdutoFornecedorRepository {

    private final JpaVinculoProdutoFornecedorRepository delegate;

    public VinculoProdutoFornecedorRepositoryAdapter(
            JpaVinculoProdutoFornecedorRepository delegate) {

        this.delegate = Objects.requireNonNull(
                delegate,
                "Repositorio JPA de vinculo entre produto e fornecedor e obrigatorio.");
    }

    @Override
    public VinculoProdutoFornecedor salvar(
            VinculoProdutoFornecedor vinculo) {

        if (vinculo == null) {
            throw new IllegalArgumentException(
                    "Vinculo entre produto e fornecedor e obrigatorio.");
        }

        return delegate.save(vinculo);
    }

    @Override
    public Optional<VinculoProdutoFornecedor> buscar(
            long fornecedorId, String codigoProduto) {

        return delegate.findByFornecedorIdAndCodigoProduto(
                fornecedorId, codigoProduto);
    }
}
