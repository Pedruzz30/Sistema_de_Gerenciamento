package repository;

import model.VendaPagamento;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;

@Repository
public class VendaPagamentoRepositoryAdapter
        implements VendaPagamentoRepository {

    private final JpaVendaPagamentoRepository delegate;

    public VendaPagamentoRepositoryAdapter(
            JpaVendaPagamentoRepository delegate) {

        this.delegate = Objects.requireNonNull(
                delegate, "Repositorio JPA de pagamento e obrigatorio.");
    }

    @Override
    public VendaPagamento salvar(VendaPagamento pagamento) {
        if (pagamento == null) {
            throw new IllegalArgumentException(
                    "Pagamento da venda e obrigatorio.");
        }

        return delegate.save(pagamento);
    }

    @Override
    public List<VendaPagamento> listarPorVenda(long vendaId) {
        return delegate.findByVendaIdOrderByIdAsc(vendaId);
    }
}
