package repository;

import model.VendaItemModificadorImpacto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface JpaVendaItemModificadorImpactoRepository extends JpaRepository<VendaItemModificadorImpacto, Long> {

    @Query("""
            select i from VendaItemModificadorImpacto i
            join fetch i.vendaItemModificador m
            join fetch m.vendaItem item
            where item.venda.id = :vendaId
            order by item.id asc, m.id asc, i.id asc
            """)
    List<VendaItemModificadorImpacto> findByVenda(@Param("vendaId") long vendaId);
}
