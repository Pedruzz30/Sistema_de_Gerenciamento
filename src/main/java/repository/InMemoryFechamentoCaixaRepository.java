package repository;

import model.FechamentoCaixa;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryFechamentoCaixaRepository implements FechamentoCaixaRepository {

    private final List<FechamentoCaixa> fechamentos = new ArrayList<>();
    private final AtomicLong sequenceId = new AtomicLong(1);

    @Override
    public synchronized FechamentoCaixa salvar(FechamentoCaixa fechamentoCaixa) {
        if (fechamentoCaixa == null) {
            throw new IllegalArgumentException("Fechamento de caixa nao pode ser nulo.");
        }

        FechamentoCaixa persistido = fechamentoCaixa;
        if (persistido.getId() <= 0) {
            persistido = fechamentoCaixa.comId(sequenceId.getAndIncrement());
        } else {
            long fechamentoId = persistido.getId();
            sequenceId.updateAndGet(atual -> Math.max(atual, fechamentoId + 1));
        }

        final long fechamentoId = persistido.getId();
        fechamentos.removeIf(fechamento -> fechamento.getId() == fechamentoId);
        fechamentos.add(persistido);
        return persistido;
    }

    @Override
    public synchronized Optional<FechamentoCaixa> buscarPorId(long id) {
        return fechamentos.stream()
                .filter(fechamento -> fechamento.getId() == id)
                .findFirst();
    }

    @Override
    public synchronized List<FechamentoCaixa> listarTodos() {
        return new ArrayList<>(fechamentos);
    }
}
