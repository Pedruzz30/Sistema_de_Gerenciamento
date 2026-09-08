package repository;

import model.UnidadeMedida;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JpaUnidadeMedidaRepository extends JpaRepository<UnidadeMedida, Long> {

    Optional<UnidadeMedida> findByCodigoIgnoreCase(String codigo);

    List<UnidadeMedida> findAllByOrderByCodigoAsc();
}
