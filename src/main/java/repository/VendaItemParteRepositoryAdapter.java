package repository;

import model.StatusVendaItem;
import model.VendaItemParte;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;

@Repository
public class VendaItemParteRepositoryAdapter
        implements VendaItemParteRepository {

    private final JpaVendaItemParteRepository delegate;

    public VendaItemParteRepositoryAdapter(
            JpaVendaItemParteRepository delegate) {

        this.delegate = Objects.requireNonNull(
                delegate,
                "Repositorio JPA de partes do item da venda e obrigatorio.");
    }

    @Override
    public VendaItemParte salvar(VendaItemParte parte) {
        if (parte == null) {
            throw new IllegalArgumentException(
                    "Parte do item da venda e obrigatoria.");
        }

        return delegate.save(parte);
    }

    @Override
    public List<VendaItemParte> salvarTodos(List<VendaItemParte> partes) {
        if (partes == null) {
            throw new IllegalArgumentException(
                    "Lista de partes do item da venda e obrigatoria.");
        }

        return delegate.saveAll(partes);
    }

    @Override
    public List<VendaItemParte> listarPorItem(long vendaItemId) {
        return delegate.findByVendaItemIdOrderByOrdemVisualAscIdAsc(
                vendaItemId);
    }

    @Override
    public List<VendaItemParte> listarPorVenda(long vendaId) {
        return delegate
                .findByVendaItemVendaIdOrderByVendaItemIdAscOrdemVisualAscIdAsc(
                        vendaId);
    }

    @Override
    public List<VendaItemParte> listarPorItemEStatus(
            long vendaItemId, StatusVendaItem status) {

        return delegate.findByVendaItemIdAndStatusOrderByOrdemVisualAscIdAsc(
                vendaItemId, status);
    }

    @Override
    public void removerPorItem(long vendaItemId) {
        delegate.deleteByVendaItemId(vendaItemId);
    }
}
