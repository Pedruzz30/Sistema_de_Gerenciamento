package repository;

import model.FichaTecnicaCustoSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface JpaFichaTecnicaCustoSnapshotRepository extends JpaRepository<FichaTecnicaCustoSnapshot, Long> {

    @Query("""
            select s from FichaTecnicaCustoSnapshot s
            where s.fichaTecnicaVersao.id = :versaoId
              and s.filialId = :filialId
            order by s.id desc
            """)
    Optional<FichaTecnicaCustoSnapshot> findByVersaoAndFilial(@Param("versaoId") long versaoId,
                                                              @Param("filialId") Long filialId);

    @Query("""
            select s from FichaTecnicaCustoSnapshot s
            where s.fichaTecnicaVersao.id = :versaoId
            order by s.filialId asc, s.id asc
            """)
    List<FichaTecnicaCustoSnapshot> findByVersao(@Param("versaoId") long versaoId);
}
