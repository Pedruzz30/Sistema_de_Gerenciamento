package repository;

import model.LogAcao;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryLogRepositoryTest {

    @Test
    void salvar_preservaTimestampOriginalEAtribuiId() {
        InMemoryLogRepository repository = new InMemoryLogRepository();
        LogAcao original = new LogAcao(0, 1L, "LOGIN_SUCESSO", "Login efetuado");

        LogAcao persistido = repository.salvar(original);

        assertTrue(persistido.getId() > 0);
        assertEquals(original.getDataHora(), persistido.getDataHora());
        assertEquals(1, repository.listarTodos().size());
    }
}
