package repository;

import model.FichaTecnicaVersao;
import model.StatusFichaTecnicaVersao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface JpaFichaTecnicaVersaoRepository extends JpaRepository<FichaTecnicaVersao, Long> {

    @Query("""
            select v from FichaTecnicaVersao v
            where v.fichaTecnica.id = :fichaId
            order by v.numeroVersao asc
            """)
    List<FichaTecnicaVersao> findByFicha(@Param("fichaId") long fichaId);

    @Query("""
            select v from FichaTecnicaVersao v
            where v.fichaTecnica.id = :fichaId
              and v.status = :status
            order by v.numeroVersao desc
            """)
    Optional<FichaTecnicaVersao> findFirstByFichaAndStatus(@Param("fichaId") long fichaId,
                                                           @Param("status") StatusFichaTecnicaVersao status);

    @Query("select coalesce(max(v.numeroVersao), 0) from FichaTecnicaVersao v where v.fichaTecnica.id = :fichaId")
    int maxNumeroVersao(@Param("fichaId") long fichaId);
}
