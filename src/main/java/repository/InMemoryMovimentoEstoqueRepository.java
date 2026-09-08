package repository;

import model.MovimentoEstoque;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryMovimentoEstoqueRepository implements MovimentoEstoqueRepository {

    private final List<MovimentoEstoque> movimentos = new ArrayList<>();
    private final AtomicLong sequenceId = new AtomicLong(1);

    @Override
    public synchronized MovimentoEstoque salvar(MovimentoEstoque movimento) {
        MovimentoEstoque persistido = movimento;
        if (movimento.getId() <= 0) {
            persistido = movimento.comId(sequenceId.getAndIncrement());
        } else {
            sequenceId.updateAndGet(atual -> Math.max(atual, movimento.getId() + 1));
        }
        MovimentoEstoque finalPersistido = persistido;
        movimentos.removeIf(m -> m.getId() == finalPersistido.getId());
        movimentos.add(persistido);
        return persistido;
    }

    @Override
    public synchronized Optional<MovimentoEstoque> buscarPorId(long id) {
        return movimentos.stream().filter(m -> m.getId() == id).findFirst();
    }

    @Override
    public synchronized Optional<MovimentoEstoque> buscarEstornoDoMovimento(long movimentoId) {
        return listarEstornosDoMovimento(movimentoId).stream().findFirst();
    }

    @Override
    public synchronized List<MovimentoEstoque> listarEstornosDoMovimento(long movimentoId) {
        return movimentos.stream()
                .filter(m -> m.getMovimentoEstornado() != null && m.getMovimentoEstornado().getId() == movimentoId)
                .sorted(Comparator.comparingLong(MovimentoEstoque::getId))
                .toList();
    }

    @Override
    public synchronized List<MovimentoEstoque> listarTodos() {
        return movimentos.stream()
                .sorted(Comparator.comparing(MovimentoEstoque::getCriadoEm).reversed()
                        .thenComparing(Comparator.comparingLong(MovimentoEstoque::getId).reversed()))
                .toList();
    }
}
