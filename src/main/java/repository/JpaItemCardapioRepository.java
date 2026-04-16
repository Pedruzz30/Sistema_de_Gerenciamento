package repository;

import model.ItemCardapio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JpaItemCardapioRepository extends JpaRepository<ItemCardapio, Integer> {

    Optional<ItemCardapio> findByCodigoIgnoreCase(String codigo);

    Optional<ItemCardapio> findFirstByProdutoVinculado_Id(int produtoId);
}
