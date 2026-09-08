package repository;

import model.RegistroIdempotencia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JpaRegistroIdempotenciaRepository extends JpaRepository<RegistroIdempotencia, Long> {

    Optional<RegistroIdempotencia> findByFilialIdAndOperacaoAndIdempotencyKey(
            Long filialId,
            String operacao,
            String idempotencyKey
    );
}
