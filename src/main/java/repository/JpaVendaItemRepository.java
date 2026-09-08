package repository;

import model.StatusVendaItem;
import model.VendaItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JpaVendaItemRepository extends JpaRepository<VendaItem, Long> {

    List<VendaItem> findByVendaIdOrderByIdAsc(long vendaId);

    List<VendaItem> findByVendaIdAndStatusOrderByIdAsc(long vendaId, StatusVendaItem status);
}
