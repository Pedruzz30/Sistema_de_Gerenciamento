package repository;

import model.GrupoModificador;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaGrupoModificadorRepository extends JpaRepository<GrupoModificador, Long> {

    @Query("""
            select count(g) > 0 from GrupoModificador g
            where upper(g.codigo) = upper(:codigo)
              and (:ignorarId is null or g.id <> :ignorarId)
            """)
    boolean existsByCodigo(@Param("codigo") String codigo, @Param("ignorarId") Long ignorarId);
}
