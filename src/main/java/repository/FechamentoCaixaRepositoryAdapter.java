package repository;

import model.FechamentoCaixa;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class FechamentoCaixaRepositoryAdapter
        implements FechamentoCaixaRepository {

    private final JpaFechamentoCaixaRepository delegate;

    public FechamentoCaixaRepositoryAdapter(
            JpaFechamentoCaixaRepository delegate) {

        this.delegate = Objects.requireNonNull(
                delegate, "Repositorio JPA de fechamento de caixa e obrigatorio.");
    }

    @Override
    public FechamentoCaixa salvar(FechamentoCaixa fechamentoCaixa) {
        if (fechamentoCaixa == null) {
            throw new IllegalArgumentException(
                    "Fechamento de caixa e obrigatorio.");
        }

        return delegate.save(fechamentoCaixa);
    }

    @Override
    public Optional<FechamentoCaixa> buscarPorId(long id) {
        return delegate.findById(id);
    }

    @Override
    public List<FechamentoCaixa> listarTodos() {
        return delegate.findAllByOrderByIdAsc();
    }
}
