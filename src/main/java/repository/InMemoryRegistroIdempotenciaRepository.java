package repository;

import model.RegistroIdempotencia;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryRegistroIdempotenciaRepository implements RegistroIdempotenciaRepository {

    private final List<RegistroIdempotencia> registros = new ArrayList<>();
    private final AtomicLong sequenceId = new AtomicLong(1);

    @Override
    public synchronized RegistroIdempotencia salvar(RegistroIdempotencia registro) {
        RegistroIdempotencia persistido = registro;
        if (registro.getId() <= 0) {
            persistido = registro.comId(sequenceId.getAndIncrement());
        } else {
            sequenceId.updateAndGet(atual -> Math.max(atual, registro.getId() + 1));
        }
        RegistroIdempotencia finalPersistido = persistido;
        registros.removeIf(r -> r.getId() == finalPersistido.getId()
                || (r.getFilialId().equals(finalPersistido.getFilialId())
                && r.getOperacao().equals(finalPersistido.getOperacao())
                && r.getIdempotencyKey().equals(finalPersistido.getIdempotencyKey())));
        registros.add(persistido);
        return persistido;
    }

    @Override
    public synchronized Optional<RegistroIdempotencia> buscar(Long filialId, String operacao, String idempotencyKey) {
        return registros.stream()
                .filter(r -> r.getFilialId().equals(filialId)
                        && r.getOperacao().equals(operacao)
                        && r.getIdempotencyKey().equals(idempotencyKey))
                .findFirst();
    }
}
