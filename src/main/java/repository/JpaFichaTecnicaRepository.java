package repository;

import jakarta.persistence.LockModeType;
import model.FichaTecnica;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;

public interface JpaFichaTecnicaRepository extends JpaRepository<FichaTecnica, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from FichaTecnica f where f.id = :id")
    Optional<FichaTecnica> findByIdForUpdate(@Param("id") long id);

    @Query("""
            select f from FichaTecnica f
            where f.variacaoItemCardapio.id = :variacaoId
              and f.ativa = true
            order by f.id asc
            """)
    Optional<FichaTecnica> findAtivaByVariacao(@Param("variacaoId") long variacaoId);

    @Query("""
            select distinct f from FichaTecnica f
            left join fetch f.versaoPublicadaAtual v
            where f.variacaoItemCardapio.id in :variacaoIds
              and f.ativa = true
            order by f.variacaoItemCardapio.id asc, f.id asc
            """)
    List<FichaTecnica> findAtivasByVariacoes(@Param("variacaoIds") List<Long> variacaoIds);
}
