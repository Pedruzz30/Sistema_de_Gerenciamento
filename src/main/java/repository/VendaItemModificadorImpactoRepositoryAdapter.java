package repository;

import model.VendaItemModificadorImpacto;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;

@Repository
public class VendaItemModificadorImpactoRepositoryAdapter
        implements VendaItemModificadorImpactoRepository {

    private final JpaVendaItemModificadorImpactoRepository delegate;

    public VendaItemModificadorImpactoRepositoryAdapter(
            JpaVendaItemModificadorImpactoRepository delegate) {

        this.delegate = Objects.requireNonNull(
                delegate,
                "Repositorio JPA de impacto do modificador e obrigatorio.");
    }

    @Override
    public VendaItemModificadorImpacto salvar(
            VendaItemModificadorImpacto impacto) {

        if (impacto == null) {
            throw new IllegalArgumentException(
                    "Impacto do modificador da venda e obrigatorio.");
        }

        return delegate.save(impacto);
    }

    @Override
    public List<VendaItemModificadorImpacto> listarPorVenda(long vendaId) {
        return delegate.findByVenda(vendaId);
    }
}
