package repository;

import model.FechamentoCaixa;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class FechamentoCaixaRepositoryAdapter implements FechamentoCaixaRepository {

    private final JpaFechamentoCaixaRepository delegate;

    public FechamentoCaixaRepositoryAdapter(JpaFechamentoCaixaRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public FechamentoCaixa salvar(FechamentoCaixa fechamentoCaixa) {
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
