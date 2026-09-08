package repository;

import model.FichaTecnicaIngrediente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface JpaFichaTecnicaIngredienteRepository extends JpaRepository<FichaTecnicaIngrediente, Long> {

    @Query("""
            select i from FichaTecnicaIngrediente i
            where i.fichaTecnicaVersao.id = :versaoId
            order by i.ordem asc, i.id asc
            """)
    List<FichaTecnicaIngrediente> findByVersao(@Param("versaoId") long versaoId);

    @Query("""
            select i from FichaTecnicaIngrediente i
            join fetch i.produto p
            left join fetch p.unidadeBase
            join fetch i.unidadeInformada
            where i.fichaTecnicaVersao.id in :versaoIds
            order by i.fichaTecnicaVersao.id asc, i.ordem asc, i.id asc
            """)
    List<FichaTecnicaIngrediente> findByVersoes(@Param("versaoIds") List<Long> versaoIds);
}
