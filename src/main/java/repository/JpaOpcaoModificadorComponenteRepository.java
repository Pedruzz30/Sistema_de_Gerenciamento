package repository;

import model.OpcaoModificadorComponente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface JpaOpcaoModificadorComponenteRepository extends JpaRepository<OpcaoModificadorComponente, Long> {

    @Query("""
            select c from OpcaoModificadorComponente c
            join fetch c.opcaoModificador o
            left join fetch c.produtoOrigem po
            left join fetch po.unidadeBase
            left join fetch c.produtoDestino pd
            left join fetch pd.unidadeBase
            left join fetch c.unidadeBase
            where o.id = :opcaoId
            order by c.ordem asc, c.id asc
            """)
    List<OpcaoModificadorComponente> findByOpcao(@Param("opcaoId") long opcaoId);

    @Query("""
            select c from OpcaoModificadorComponente c
            join fetch c.opcaoModificador o
            left join fetch c.produtoOrigem po
            left join fetch po.unidadeBase
            left join fetch c.produtoDestino pd
            left join fetch pd.unidadeBase
            left join fetch c.unidadeBase
            where o.id in :opcaoIds
            order by o.id asc, c.ordem asc, c.id asc
            """)
    List<OpcaoModificadorComponente> findByOpcoes(@Param("opcaoIds") List<Long> opcaoIds);
}
