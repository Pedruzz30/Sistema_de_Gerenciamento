package repository;

import model.LogAcao;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class LogRepositoryAdapter implements LogRepository {

    private final JpaLogRepository delegate;

    public LogRepositoryAdapter(JpaLogRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public LogAcao salvar(LogAcao log) {
        return delegate.save(log);
    }

    @Override
    public List<LogAcao> listarTodos() {
        return delegate.findAll(Sort.by(Sort.Direction.ASC, "dataHora", "id"));
    }
}
