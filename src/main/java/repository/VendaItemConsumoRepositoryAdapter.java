package repository;

import model.VendaItemConsumo;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

@Repository
public class VendaItemConsumoRepositoryAdapter
        implements VendaItemConsumoRepository {

    private final JpaVendaItemConsumoRepository delegate;

    public VendaItemConsumoRepositoryAdapter(
            JpaVendaItemConsumoRepository delegate) {

        this.delegate = Objects.requireNonNull(
                delegate,
                "Repositorio JPA de consumo da venda e obrigatorio.");
    }

    @Override
    public VendaItemConsumo salvar(VendaItemConsumo consumo) {
        if (consumo == null) {
            throw new IllegalArgumentException(
                    "Consumo do item da venda e obrigatorio.");
        }

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
    public List<VendaItemConsumo> listarPorVendaItens(
            Collection<Long> vendaItemIds) {

        if (vendaItemIds == null || vendaItemIds.isEmpty()) {
            return List.of();
        }

        return delegate.findByVendaItemIds(vendaItemIds);
    }
}
