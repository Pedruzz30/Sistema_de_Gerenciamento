package repository;

import model.VendaItemModificadorImpacto;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class VendaItemModificadorImpactoRepositoryAdapter implements VendaItemModificadorImpactoRepository {

    private final JpaVendaItemModificadorImpactoRepository delegate;

    public VendaItemModificadorImpactoRepositoryAdapter(JpaVendaItemModificadorImpactoRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public VendaItemModificadorImpacto salvar(VendaItemModificadorImpacto impacto) {
        return delegate.save(impacto);
    }

    @Override
    public List<VendaItemModificadorImpacto> listarPorVenda(long vendaId) {
        return delegate.findByVenda(vendaId);
    }
}
