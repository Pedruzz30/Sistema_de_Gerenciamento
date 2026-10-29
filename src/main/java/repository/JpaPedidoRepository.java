package repository;

import model.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface JpaPedidoRepository extends JpaRepository<Pedido, Integer> {
}
