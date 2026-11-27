package repository;

import model.StatusVendaItem;
import model.VendaItem;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class VendaItemRepositoryAdapter
        implements VendaItemRepository {

    private final JpaVendaItemRepository delegate;

    public VendaItemRepositoryAdapter(JpaVendaItemRepository delegate) {
        this.delegate = Objects.requireNonNull(
                delegate, "Repositorio JPA de item da venda e obrigatorio.");
    }

    @Override
    public VendaItem salvar(VendaItem item) {
        if (item == null) {
            throw new IllegalArgumentException(
                    "Item da venda e obrigatorio.");
        }

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
        return delegate.findByVendaIdAndStatusOrderByIdAsc(
                vendaId, StatusVendaItem.ATIVO);
    }

    @Override
    public void remover(VendaItem item) {
        if (item == null) {
            throw new IllegalArgumentException(
                    "Item da venda e obrigatorio.");
        }

        delegate.delete(item);
    }
}
