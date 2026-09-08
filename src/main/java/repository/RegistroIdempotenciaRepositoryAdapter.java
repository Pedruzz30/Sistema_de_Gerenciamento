package repository;

import model.RegistroIdempotencia;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class RegistroIdempotenciaRepositoryAdapter implements RegistroIdempotenciaRepository {

    private final JpaRegistroIdempotenciaRepository delegate;

    public RegistroIdempotenciaRepositoryAdapter(JpaRegistroIdempotenciaRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public RegistroIdempotencia salvar(RegistroIdempotencia registro) {
        return delegate.save(registro);
    }

    @Override
    public RegistroIdempotencia salvarEFlush(RegistroIdempotencia registro) {
        return delegate.saveAndFlush(registro);
    }

    @Override
    public Optional<RegistroIdempotencia> buscar(Long filialId, String operacao, String idempotencyKey) {
        if (filialId == null || operacao == null || idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        return delegate.findByFilialIdAndOperacaoAndIdempotencyKey(filialId, operacao, idempotencyKey.trim());
    }
}
