package repository;

import model.RegistroIdempotencia;
import org.springframework.stereotype.Repository;

import java.util.Objects;
import java.util.Optional;

@Repository
public class RegistroIdempotenciaRepositoryAdapter
        implements RegistroIdempotenciaRepository {

    private final JpaRegistroIdempotenciaRepository delegate;

    public RegistroIdempotenciaRepositoryAdapter(
            JpaRegistroIdempotenciaRepository delegate) {

        this.delegate = Objects.requireNonNull(
                delegate, "Repositorio JPA de idempotencia e obrigatorio.");
    }

    @Override
    public RegistroIdempotencia salvar(RegistroIdempotencia registro) {
        if (registro == null) {
            throw new IllegalArgumentException(
                    "Registro de idempotencia e obrigatorio.");
        }

        return delegate.save(registro);
    }

    @Override
    public RegistroIdempotencia salvarEFlush(RegistroIdempotencia registro) {
        if (registro == null) {
            throw new IllegalArgumentException(
                    "Registro de idempotencia e obrigatorio.");
        }

        return delegate.saveAndFlush(registro);
    }

    @Override
    public Optional<RegistroIdempotencia> buscar(
            Long filialId, String operacao, String idempotencyKey) {

        if (filialId == null
                || operacao == null
                || idempotencyKey == null
                || idempotencyKey.isBlank()) {
            return Optional.empty();
        }

        return delegate.findByFilialIdAndOperacaoAndIdempotencyKey(
                filialId, operacao, idempotencyKey.trim());
    }
}
