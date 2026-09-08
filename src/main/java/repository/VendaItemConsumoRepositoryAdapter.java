package repository;

import model.VendaItemConsumo;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public class VendaItemConsumoRepositoryAdapter implements VendaItemConsumoRepository {

    private final JpaVendaItemConsumoRepository delegate;

    public VendaItemConsumoRepositoryAdapter(JpaVendaItemConsumoRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public VendaItemConsumo salvar(VendaItemConsumo consumo) {
        return delegate.save(consumo);
    }

    @Override
    public List<VendaItemConsumo> listarPorVenda(long vendaId) {
        return delegate.findByVendaId(vendaId);
    }

    @Override
    public List<VendaItemConsumo> listarPorVendaItem(long vendaItemId) {
        return delegate.findByVendaItemIdOrderByIdAsc(vendaItemId);
    }

    @Override
    public List<VendaItemConsumo> listarPorVendaItens(Collection<Long> vendaItemIds) {
        if (vendaItemIds == null || vendaItemIds.isEmpty()) {
            return List.of();
        }
        return delegate.findByVendaItemIds(vendaItemIds);
    }
}
