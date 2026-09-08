package repository;

import model.VendaItemConsumo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface JpaVendaItemConsumoRepository extends JpaRepository<VendaItemConsumo, Long> {

    @Query("""
            select c from VendaItemConsumo c
            where c.vendaItem.venda.id = :vendaId
            order by c.vendaItem.id asc, c.id asc
            """)
    List<VendaItemConsumo> findByVendaId(@Param("vendaId") long vendaId);

    List<VendaItemConsumo> findByVendaItemIdOrderByIdAsc(long vendaItemId);

    @Query("""
            select c from VendaItemConsumo c
            where c.vendaItem.id in :vendaItemIds
            order by c.vendaItem.id asc, c.id asc
            """)
    List<VendaItemConsumo> findByVendaItemIds(@Param("vendaItemIds") Collection<Long> vendaItemIds);
}
