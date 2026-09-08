package repository;

import model.VendaItemModificador;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class VendaItemModificadorRepositoryAdapter implements VendaItemModificadorRepository {

    private final JpaVendaItemModificadorRepository delegate;

    public VendaItemModificadorRepositoryAdapter(JpaVendaItemModificadorRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public VendaItemModificador salvar(VendaItemModificador modificador) {
        return delegate.save(modificador);
    }

    @Override
    public List<VendaItemModificador> salvarTodos(List<VendaItemModificador> modificadores) {
        return delegate.saveAll(modificadores);
    }

    @Override
    public List<VendaItemModificador> listarPorItem(long vendaItemId) {
        return delegate.findByVendaItemIdOrderByIdAsc(vendaItemId);
    }

    @Override
    public List<VendaItemModificador> listarPorItens(List<Long> vendaItemIds) {
        if (vendaItemIds == null || vendaItemIds.isEmpty()) {
            return List.of();
        }
        return delegate.findByItens(vendaItemIds);
    }

    @Override
    public List<VendaItemModificador> listarPorVenda(long vendaId) {
        return delegate.findByVenda(vendaId);
    }

    @Override
    public void removerPorItem(long vendaItemId) {
        delegate.deleteByVendaItemId(vendaItemId);
    }
}
