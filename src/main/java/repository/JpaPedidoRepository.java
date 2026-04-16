package repository;

import model.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaPedidoRepository extends JpaRepository<Pedido, Integer> {
}
