package repository;

import model.LogAcao;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryLogRepository implements LogRepository {

    private final List<LogAcao> logs = new ArrayList<>();
    private final AtomicLong sequenceId = new AtomicLong(1);

    @Override
    public LogAcao salvar(LogAcao log) {
        if (log == null) {
            throw new IllegalArgumentException("Log não pode ser nulo.");
        }

        LogAcao persistido = new LogAcao(
                sequenceId.getAndIncrement(),
                log.getUsuarioRu(),
                log.getAcao(),
                log.getDescricao()
        );
        logs.add(persistido);
        return persistido;
    }

    @Override
    public List<LogAcao> listarTodos() {
        return new ArrayList<>(logs);
    }
}