package repository;

import model.Pedido;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class PedidoRepositoryAdapter implements PedidoRepository {

    private final JpaPedidoRepository delegate;

    public PedidoRepositoryAdapter(JpaPedidoRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public Pedido salvar(Pedido pedido) {
        return delegate.save(pedido);
    }

    @Override
    public Optional<Pedido> buscarPorId(int id) {
        return delegate.findById(id);
    }

    @Override
    public List<Pedido> listarTodos() {
        return delegate.findAll(
                Sort.by(Sort.Direction.DESC, "dataHora")
                        .and(Sort.by(Sort.Direction.DESC, "id"))
        );
    }
}
