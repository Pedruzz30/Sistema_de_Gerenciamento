package repository;

import model.VendaItemModificador;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface JpaVendaItemModificadorRepository extends JpaRepository<VendaItemModificador, Long> {

    List<VendaItemModificador> findByVendaItemIdOrderByIdAsc(long vendaItemId);

    @Query("""
            select m from VendaItemModificador m
            join fetch m.vendaItem i
            join fetch m.grupoModificador
            join fetch m.opcaoModificador
            where i.id in :itemIds
            order by i.id asc, m.id asc
            """)
    List<VendaItemModificador> findByItens(@Param("itemIds") List<Long> itemIds);

    @Query("""
            select m from VendaItemModificador m
            join fetch m.vendaItem i
            join fetch m.grupoModificador
            join fetch m.opcaoModificador
            where i.venda.id = :vendaId
            order by i.id asc, m.id asc
            """)
    List<VendaItemModificador> findByVenda(@Param("vendaId") long vendaId);

    @Modifying
    @Query("delete from VendaItemModificador m where m.vendaItem.id = :vendaItemId")
    void deleteByVendaItemId(@Param("vendaItemId") long vendaItemId);
}
