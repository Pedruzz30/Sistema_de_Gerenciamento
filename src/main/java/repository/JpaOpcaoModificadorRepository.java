package repository;

import model.OpcaoModificador;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface JpaOpcaoModificadorRepository extends JpaRepository<OpcaoModificador, Long> {

    @Query("""
            select o from OpcaoModificador o
            join fetch o.grupoModificador g
            where g.id = :grupoId
            order by o.ordemExibicao asc, o.nome asc, o.id asc
            """)
    List<OpcaoModificador> findByGrupo(@Param("grupoId") long grupoId);

    @Query("""
            select o from OpcaoModificador o
            join fetch o.grupoModificador g
            where g.id in :grupoIds
            order by g.id asc, o.ordemExibicao asc, o.nome asc, o.id asc
            """)
    List<OpcaoModificador> findByGrupos(@Param("grupoIds") List<Long> grupoIds);

    @Query("""
            select o from OpcaoModificador o
            join fetch o.grupoModificador
            where o.id in :ids
            order by o.id asc
            """)
    List<OpcaoModificador> findByIds(@Param("ids") List<Long> ids);

    @Query("""
            select count(o) > 0 from OpcaoModificador o
            where o.grupoModificador.id = :grupoId
              and upper(o.codigo) = upper(:codigo)
              and (:ignorarId is null or o.id <> :ignorarId)
            """)
    boolean existsCodigoNoGrupo(@Param("grupoId") long grupoId,
                                @Param("codigo") String codigo,
                                @Param("ignorarId") Long ignorarId);
}
