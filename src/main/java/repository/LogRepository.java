package repository;

import model.LogAcao;

import java.util.List;

public interface LogRepository {

    LogAcao salvar(LogAcao log);

    List<LogAcao> listarTodos();
}
