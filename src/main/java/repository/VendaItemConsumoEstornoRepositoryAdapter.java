package repository;

import model.VendaItemConsumoEstorno;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

@Repository
public class VendaItemConsumoEstornoRepositoryAdapter
        implements VendaItemConsumoEstornoRepository {

    private final JpaVendaItemConsumoEstornoRepository delegate;

    public VendaItemConsumoEstornoRepositoryAdapter(
            JpaVendaItemConsumoEstornoRepository delegate) {

        this.delegate = Objects.requireNonNull(
                delegate,
                "Repositorio JPA de estorno de consumo e obrigatorio.");
    }

    @Override
    public VendaItemConsumoEstorno salvar(
            VendaItemConsumoEstorno estorno) {

        if (estorno == null) {
            throw new IllegalArgumentException(
                    "Estorno de consumo da venda e obrigatorio.");
        }

        return delegate.save(estorno);
    }

    @Override
    public List<VendaItemConsumoEstorno> listarPorConsumo(
            long vendaItemConsumoId) {

        return delegate.findByVendaItemConsumoIdOrderByIdAsc(
                vendaItemConsumoId);
    }

    @Override
    public List<VendaItemConsumoEstorno> listarPorConsumos(
            Collection<Long> vendaItemConsumoIds) {

        if (vendaItemConsumoIds == null
                || vendaItemConsumoIds.isEmpty()) {
            return List.of();
        }

        return delegate.findByVendaItemConsumoIds(
                vendaItemConsumoIds);
    }
}
