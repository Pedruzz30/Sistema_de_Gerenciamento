package repository;

import model.Fornecedor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class FornecedorRepositoryAdapter implements FornecedorRepository {

    private final JpaFornecedorRepository delegate;

    public FornecedorRepositoryAdapter(JpaFornecedorRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public Fornecedor salvar(Fornecedor fornecedor) {
        return delegate.save(fornecedor);
    }

    @Override
    public Optional<Fornecedor> buscarPorId(long id) {
        return delegate.findById(id);
    }

    @Override
    public Optional<Fornecedor> buscarPorCnpj(String cnpj) {
        if (cnpj == null || cnpj.isBlank()) {
            return Optional.empty();
        }
        return delegate.findByCnpj(cnpj.replaceAll("\\D", ""));
    }

    @Override
    public List<Fornecedor> listarAtivos() {
        return delegate.findAllByAtivoTrueOrderByIdAsc();
    }

    @Override
    public List<Fornecedor> listarTodos() {
        return delegate.findAllByOrderByIdAsc();
    }
}
