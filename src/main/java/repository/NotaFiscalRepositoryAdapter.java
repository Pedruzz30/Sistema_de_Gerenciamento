package repository;

import model.NotaFiscal;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class NotaFiscalRepositoryAdapter implements NotaFiscalRepository {

    private final JpaNotaFiscalRepository delegate;

    public NotaFiscalRepositoryAdapter(JpaNotaFiscalRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public long proximoId() {
        return delegate.findTopByOrderByIdDesc()
                .map(nota -> nota.getId() + 1)
                .orElse(1L);
    }

    @Override
    public NotaFiscal salvar(NotaFiscal nota) {
        return delegate.save(nota);
    }

    @Override
    public Optional<NotaFiscal> buscarPorId(long id) {
        return delegate.findById(id);
    }

    @Override
    public List<NotaFiscal> buscarPorFornecedor(long fornecedorId) {
        return delegate.findAllByFornecedorIdOrderByIdAsc(fornecedorId);
    }

    @Override
    public List<NotaFiscal> listarTodos() {
        return delegate.findAllByOrderByIdAsc();
    }

    @Override
    public void deletar(long id) {
        delegate.deleteById(id);
    }
}
