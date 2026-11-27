package repository;

import model.RegistroIdempotencia;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryRegistroIdempotenciaRepository implements RegistroIdempotenciaRepository {

    private final List<RegistroIdempotencia> registros = new ArrayList<>();
    private final AtomicLong sequenceId = new AtomicLong(1);

    @Override
    public synchronized RegistroIdempotencia salvar(RegistroIdempotencia registro) {
        var existente = buscar(registro.getFilialId(), registro.getOperacao(), registro.getIdempotencyKey());
        if (existente.isPresent() && existente.get().getId() != registro.getId()) {
            throw new DataIntegrityViolationException("Chave de idempotencia ja registrada neste escopo.");
        }

        RegistroIdempotencia persistido = registro;
        if (registro.getId() <= 0) {
            persistido = registro.comId(sequenceId.getAndIncrement());
        } else {
            sequenceId.updateAndGet(atual -> Math.max(atual, registro.getId() + 1));
        }
        RegistroIdempotencia finalPersistido = persistido;
        registros.removeIf(r -> r.getId() == finalPersistido.getId());
        registros.add(persistido);
        return persistido;
    }

    @Override
    public synchronized Optional<RegistroIdempotencia> buscar(Long filialId, String operacao, String idempotencyKey) {
        if (filialId == null || operacao == null || idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        String chave = idempotencyKey.trim();
        return registros.stream()
                .filter(r -> r.getFilialId().equals(filialId)
                        && r.getOperacao().equals(operacao)
                        && r.getIdempotencyKey().equals(chave))
                .findFirst();
    }
}
