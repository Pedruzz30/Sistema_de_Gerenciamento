package repository;

import model.LogAcao;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryLogRepository implements LogRepository {

    private final List<LogAcao> logs = new ArrayList<>();
    private final AtomicLong sequenceId = new AtomicLong(1);

    @Override
    public synchronized LogAcao salvar(LogAcao log) {
        if (log == null) {
            throw new IllegalArgumentException("Log não pode ser nulo.");
        }

        LogAcao persistido = log.getId() > 0
                ? log
                : log.comId(sequenceId.getAndIncrement());
        sequenceId.updateAndGet(atual -> Math.max(atual, persistido.getId() + 1));
        logs.add(persistido);
        return persistido;
    }

    @Override
    public synchronized List<LogAcao> listarTodos() {
        return new ArrayList<>(logs);
    }
}
