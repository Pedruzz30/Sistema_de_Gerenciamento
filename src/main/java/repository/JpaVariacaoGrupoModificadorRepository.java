package repository;

import model.VariacaoGrupoModificador;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface JpaVariacaoGrupoModificadorRepository extends JpaRepository<VariacaoGrupoModificador, Long> {

    @Query("""
            select a from VariacaoGrupoModificador a
            join fetch a.variacaoItemCardapio v
            join fetch a.grupoModificador g
            where v.id = :variacaoId
            order by a.ordemExibicao asc, g.ordemExibicao asc, g.nome asc, a.id asc
            """)
    List<VariacaoGrupoModificador> findByVariacao(@Param("variacaoId") long variacaoId);

    @Query("""
            select a from VariacaoGrupoModificador a
            join fetch a.variacaoItemCardapio v
            join fetch a.grupoModificador g
            where v.id = :variacaoId
              and a.ativo = true
              and g.ativo = true
            order by a.ordemExibicao asc, g.ordemExibicao asc, g.nome asc, a.id asc
            """)
    List<VariacaoGrupoModificador> findAtivasByVariacao(@Param("variacaoId") long variacaoId);

    @Query("""
            select a from VariacaoGrupoModificador a
            join fetch a.variacaoItemCardapio v
            join fetch a.grupoModificador g
            where v.id in :variacaoIds
              and a.ativo = true
              and g.ativo = true
            order by v.id asc, a.ordemExibicao asc, g.ordemExibicao asc, g.nome asc, a.id asc
            """)
    List<VariacaoGrupoModificador> findAtivasByVariacoes(@Param("variacaoIds") List<Long> variacaoIds);

    @Query("""
            select a from VariacaoGrupoModificador a
            where a.variacaoItemCardapio.id = :variacaoId
              and a.grupoModificador.id = :grupoId
            """)
    Optional<VariacaoGrupoModificador> findByVariacaoAndGrupo(@Param("variacaoId") long variacaoId,
                                                              @Param("grupoId") long grupoId);
}
