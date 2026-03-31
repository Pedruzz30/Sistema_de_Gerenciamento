package repository;

import model.Pedido;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public class InMemoryPedidoRepository implements PedidoRepository {

    private final List<Pedido> pedidos = new ArrayList<>();
    private final AtomicInteger sequenceId = new AtomicInteger(1);

    @Override
    public synchronized Pedido salvar(Pedido pedido) {
        if (pedido == null) {
            throw new IllegalArgumentException("Pedido não pode ser nulo.");
        }

        Pedido persistido = pedido;
        if (pedido.getId() <= 0) {
            persistido = new Pedido(
                    sequenceId.getAndIncrement(),
                    pedido.getProduto(),
                    pedido.getQuantidade(),
                    pedido.getTipo(),
                    pedido.getUsuario()
            );
        } else {
            sequenceId.updateAndGet(atual -> Math.max(atual, pedido.getId() + 1));
        }

        final Pedido p = persistido;
        pedidos.removeIf(existing -> existing.getId() == p.getId());
        pedidos.add(persistido);
        return persistido;
    }

    @Override
    public synchronized Optional<Pedido> buscarPorId(int id) {
        return pedidos.stream().filter(p -> p.getId() == id).findFirst();
    }

    @Override
    public synchronized List<Pedido> listarTodos() {
        return new ArrayList<>(pedidos);
    }
}
