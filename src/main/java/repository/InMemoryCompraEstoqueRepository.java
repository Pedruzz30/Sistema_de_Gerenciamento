package repository;

import model.CompraEstoque;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryCompraEstoqueRepository implements CompraEstoqueRepository {

    private final List<CompraEstoque> compras = new ArrayList<>();
    private final AtomicLong sequenceId = new AtomicLong(1);

    @Override
    public synchronized CompraEstoque salvar(CompraEstoque compra) {
        CompraEstoque persistida = compra;
        if (compra.getId() <= 0) {
            persistida = compra.comId(sequenceId.getAndIncrement());
        } else {
            sequenceId.updateAndGet(atual -> Math.max(atual, compra.getId() + 1));
        }
        CompraEstoque finalPersistida = persistida;
        compras.removeIf(c -> c.getId() == finalPersistida.getId());
        compras.add(persistida);
        return persistida;
    }

    @Override
    public synchronized Optional<CompraEstoque> buscarPorId(long id) {
        return compras.stream().filter(c -> c.getId() == id).findFirst();
    }

    @Override
    public synchronized List<CompraEstoque> listarTodos() {
        return compras.stream()
                .sorted(Comparator.comparingLong(CompraEstoque::getId).reversed())
                .toList();
    }
}
