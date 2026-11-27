package repository;

import model.MovimentoEstoque;
import org.springframework.lang.NonNull;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryMovimentoEstoqueRepository
        implements MovimentoEstoqueRepository {

    private static final Comparator<MovimentoEstoque> ORDENACAO_POR_ID =
            Comparator.comparingLong(movimento -> movimento.getId());

    private static final Comparator<MovimentoEstoque> ORDENACAO_RECENTES =
            Comparator.comparing(
                    (MovimentoEstoque movimento) -> movimento.getCriadoEm())
                    .reversed()
                    .thenComparing(ORDENACAO_POR_ID.reversed());

    private final List<MovimentoEstoque> movimentos = new ArrayList<>();
    private final AtomicLong sequenceId = new AtomicLong(1);

    @Override
    public synchronized MovimentoEstoque salvar(
            @NonNull MovimentoEstoque movimento) {

        Assert.notNull(
                movimento, "Movimento de estoque nao pode ser nulo.");

        MovimentoEstoque persistido = movimento;

        if (movimento.getId() <= 0) {
            persistido = movimento.comId(
                    sequenceId.getAndIncrement());
        } else {
            sequenceId.updateAndGet(atual ->
                    Math.max(atual, movimento.getId() + 1));
        }

        final long idPersistido = persistido.getId();

        movimentos.removeIf(registro ->
                registro.getId() == idPersistido);

        movimentos.add(persistido);

        return persistido;
    }

    @Override
    public synchronized Optional<MovimentoEstoque> buscarPorId(long id) {
        return movimentos.stream()
                .filter(movimento -> movimento.getId() == id)
                .findFirst();
    }

    @Override
    public synchronized Optional<MovimentoEstoque> buscarEstornoDoMovimento(
            long movimentoId) {

        return listarEstornosDoMovimento(movimentoId)
                .stream()
                .findFirst();
    }

    @Override
    public synchronized List<MovimentoEstoque> listarEstornosDoMovimento(
            long movimentoId) {

        return movimentos.stream()
                .filter(movimento -> {
                    MovimentoEstoque estornado =
                            movimento.getMovimentoEstornado();

                    return estornado != null
                            && estornado.getId() == movimentoId;
                })
                .sorted(ORDENACAO_POR_ID)
                .toList();
    }

    @Override
    public synchronized List<MovimentoEstoque> listarTodos() {
        return movimentos.stream()
                .sorted(ORDENACAO_RECENTES)
                .toList();
    }
}
