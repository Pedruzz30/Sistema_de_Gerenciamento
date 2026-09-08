package repository;

import model.RegraHorarioCardapio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface JpaRegraHorarioCardapioRepository extends JpaRepository<RegraHorarioCardapio, Long> {

    @Query("""
            select r from RegraHorarioCardapio r
            left join fetch r.itemCardapio i
            left join fetch r.variacaoItemCardapio v
            where r.filialId = :filialId
              and r.ativa = true
            order by r.id asc
            """)
    List<RegraHorarioCardapio> findAtivasByFilial(@Param("filialId") Long filialId);
}
