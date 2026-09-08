package repository;

import model.VendaItemConsumoEstorno;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public class VendaItemConsumoEstornoRepositoryAdapter implements VendaItemConsumoEstornoRepository {

    private final JpaVendaItemConsumoEstornoRepository delegate;

    public VendaItemConsumoEstornoRepositoryAdapter(JpaVendaItemConsumoEstornoRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public VendaItemConsumoEstorno salvar(VendaItemConsumoEstorno estorno) {
        return delegate.save(estorno);
    }

    @Override
    public List<VendaItemConsumoEstorno> listarPorConsumo(long vendaItemConsumoId) {
        return delegate.findByVendaItemConsumoIdOrderByIdAsc(vendaItemConsumoId);
    }

    @Override
    public List<VendaItemConsumoEstorno> listarPorConsumos(Collection<Long> vendaItemConsumoIds) {
        if (vendaItemConsumoIds == null || vendaItemConsumoIds.isEmpty()) {
            return List.of();
        }
        return delegate.findByVendaItemConsumoIds(vendaItemConsumoIds);
    }
}
