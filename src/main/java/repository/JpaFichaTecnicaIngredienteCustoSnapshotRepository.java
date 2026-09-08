package repository;

import model.FichaTecnicaIngredienteCustoSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface JpaFichaTecnicaIngredienteCustoSnapshotRepository extends JpaRepository<FichaTecnicaIngredienteCustoSnapshot, Long> {

    @Query("""
            select s from FichaTecnicaIngredienteCustoSnapshot s
            where s.fichaTecnicaCustoSnapshot.id = :snapshotId
            order by s.id asc
            """)
    List<FichaTecnicaIngredienteCustoSnapshot> findBySnapshot(@Param("snapshotId") long snapshotId);
}
