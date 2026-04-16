package repository;

import model.CategoriaCardapio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JpaCategoriaCardapioRepository extends JpaRepository<CategoriaCardapio, Integer> {

    Optional<CategoriaCardapio> findByCodigoIgnoreCase(String codigo);
}
