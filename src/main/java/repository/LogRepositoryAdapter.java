package repository;

import model.LogAcao;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;

@Repository
public class LogRepositoryAdapter implements LogRepository {

    private static final Sort ORDENACAO_PADRAO =
            Sort.by(Sort.Direction.ASC, "dataHora", "id");

    private final JpaLogRepository delegate;

    public LogRepositoryAdapter(JpaLogRepository delegate) {
        this.delegate = Objects.requireNonNull(
                delegate, "Repositorio JPA de logs e obrigatorio.");
    }

    @Override
    public LogAcao salvar(LogAcao log) {
        if (log == null) {
            throw new IllegalArgumentException(
                    "Log de acao e obrigatorio.");
        }

        return delegate.save(log);
    }

    @Override
    public List<LogAcao> listarTodos() {
        return delegate.findAll(
                Objects.requireNonNull(ORDENACAO_PADRAO));
    }
}
