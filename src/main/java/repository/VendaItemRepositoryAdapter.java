package repository;

import model.StatusVendaItem;
import model.VendaItem;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class VendaItemRepositoryAdapter implements VendaItemRepository {

    private final JpaVendaItemRepository delegate;

    public VendaItemRepositoryAdapter(JpaVendaItemRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public VendaItem salvar(VendaItem item) {
        return delegate.save(item);
    }

    @Override
    public Optional<VendaItem> buscarPorId(long id) {
        return delegate.findById(id);
    }

    @Override
    public List<VendaItem> listarPorVenda(long vendaId) {
        return delegate.findByVendaIdOrderByIdAsc(vendaId);
    }

    @Override
    public List<VendaItem> listarAtivosPorVenda(long vendaId) {
        return delegate.findByVendaIdAndStatusOrderByIdAsc(vendaId, StatusVendaItem.ATIVO);
    }

    @Override
    public void remover(VendaItem item) {
        delegate.delete(item);
    }
}
