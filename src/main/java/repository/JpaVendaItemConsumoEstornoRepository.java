package repository;

import model.VendaItemConsumoEstorno;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface JpaVendaItemConsumoEstornoRepository extends JpaRepository<VendaItemConsumoEstorno, Long> {

    List<VendaItemConsumoEstorno> findByVendaItemConsumoIdOrderByIdAsc(long vendaItemConsumoId);

    @Query("""
            select e from VendaItemConsumoEstorno e
            where e.vendaItemConsumo.id in :consumoIds
            order by e.vendaItemConsumo.id asc, e.id asc
            """)
    List<VendaItemConsumoEstorno> findByVendaItemConsumoIds(@Param("consumoIds") Collection<Long> consumoIds);
}
