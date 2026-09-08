package repository;

import model.MovimentoEstoque;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class MovimentoEstoqueRepositoryAdapter implements MovimentoEstoqueRepository {

    private final JpaMovimentoEstoqueRepository delegate;

    public MovimentoEstoqueRepositoryAdapter(JpaMovimentoEstoqueRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public MovimentoEstoque salvar(MovimentoEstoque movimento) {
        return delegate.save(movimento);
    }

    @Override
    public Optional<MovimentoEstoque> buscarPorId(long id) {
        return delegate.findById(id);
    }

    @Override
    public Optional<MovimentoEstoque> buscarEstornoDoMovimento(long movimentoId) {
        return delegate.findEstornosByMovimentoId(movimentoId).stream().findFirst();
    }

    @Override
    public List<MovimentoEstoque> listarEstornosDoMovimento(long movimentoId) {
        return delegate.findEstornosByMovimentoId(movimentoId);
    }

    @Override
    public List<MovimentoEstoque> listarTodos() {
        return delegate.findAllByOrderByCriadoEmDescIdDesc();
    }
}
