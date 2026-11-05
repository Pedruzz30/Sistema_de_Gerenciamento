package repository;

import model.VendaPagamento;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class VendaPagamentoRepositoryAdapter implements VendaPagamentoRepository {

    private final JpaVendaPagamentoRepository delegate;

    public VendaPagamentoRepositoryAdapter(JpaVendaPagamentoRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public VendaPagamento salvar(VendaPagamento pagamento) {
        return delegate.save(pagamento);
    }

    @Override
    public List<VendaPagamento> listarPorVenda(long vendaId) {
        return delegate.findByVendaIdOrderByIdAsc(vendaId);
    }
}
