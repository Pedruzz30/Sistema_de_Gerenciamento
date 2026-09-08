package repository;

import model.StatusVendaItem;
import model.VendaItemParte;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JpaVendaItemParteRepository extends JpaRepository<VendaItemParte, Long> {

    List<VendaItemParte> findByVendaItemIdOrderByOrdemVisualAscIdAsc(long vendaItemId);

    List<VendaItemParte> findByVendaItemVendaIdOrderByVendaItemIdAscOrdemVisualAscIdAsc(long vendaId);

    List<VendaItemParte> findByVendaItemIdAndStatusOrderByOrdemVisualAscIdAsc(long vendaItemId, StatusVendaItem status);

    void deleteByVendaItemId(long vendaItemId);
}
