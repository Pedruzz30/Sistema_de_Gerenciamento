package repository;

import model.IncompatibilidadeOpcaoModificador;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface JpaIncompatibilidadeOpcaoModificadorRepository
        extends JpaRepository<IncompatibilidadeOpcaoModificador, Long> {

    @Query("""
            select i from IncompatibilidadeOpcaoModificador i
            join fetch i.opcaoOrigem oo
            join fetch i.opcaoIncompativel oi
            where i.ativa = true
              and (oo.id in :opcaoIds or oi.id in :opcaoIds)
            order by i.id asc
            """)
    List<IncompatibilidadeOpcaoModificador> findAtivasByOpcoes(@Param("opcaoIds") List<Long> opcaoIds);
}
