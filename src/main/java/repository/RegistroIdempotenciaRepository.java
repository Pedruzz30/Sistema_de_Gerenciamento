package repository;

import model.RegistroIdempotencia;

import java.util.Optional;

public interface RegistroIdempotenciaRepository {

    RegistroIdempotencia salvar(RegistroIdempotencia registro);

    default RegistroIdempotencia salvarEFlush(RegistroIdempotencia registro) {
        return salvar(registro);
    }

    Optional<RegistroIdempotencia> buscar(Long filialId, String operacao, String idempotencyKey);
}
