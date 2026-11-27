package repository;

import model.CompraEstoque;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryCompraEstoqueRepository
        implements CompraEstoqueRepository {

    private static final Comparator<CompraEstoque> ORDENACAO_POR_ID =
            Comparator.comparingLong(
                    (CompraEstoque compra) -> compra.getId())
                    .reversed();

    private final List<CompraEstoque> compras = new ArrayList<>();
    private final AtomicLong sequenceId = new AtomicLong(1);

    @Override
    public synchronized CompraEstoque salvar(CompraEstoque compra) {
        if (compra == null) {
            throw new IllegalArgumentException(
                    "Compra de estoque e obrigatoria.");
        }

        CompraEstoque persistida = compra;

        if (compra.getId() <= 0) {
            persistida = compra.comId(sequenceId.getAndIncrement());
        } else {
            sequenceId.updateAndGet(atual ->
                    Math.max(atual, compra.getId() + 1));
        }

        final CompraEstoque compraPersistida = persistida;

        compras.removeIf(registro ->
                registro.getId() == compraPersistida.getId());

        compras.add(compraPersistida);

        return compraPersistida;
    }

    @Override
    public synchronized Optional<CompraEstoque> buscarPorId(long id) {
        return compras.stream()
                .filter(compra -> compra.getId() == id)
                .findFirst();
    }

    @Override
    public synchronized List<CompraEstoque> listarTodos() {
        return compras.stream()
                .sorted(ORDENACAO_POR_ID)
                .toList();
    }
}
