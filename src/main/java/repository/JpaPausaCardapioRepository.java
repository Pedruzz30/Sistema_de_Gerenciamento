package repository;

import model.PausaCardapio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface JpaPausaCardapioRepository extends JpaRepository<PausaCardapio, Long> {

    @Query("""
            select p from PausaCardapio p
            left join fetch p.itemCardapio i
            left join fetch p.variacaoItemCardapio v
            where p.filialId = :filialId
              and p.ativa = true
              and p.inicio <= :agora
              and (p.fim is null or p.fim > :agora)
            order by p.id asc
            """)
    List<PausaCardapio> findAtivas(@Param("filialId") Long filialId,
                                   @Param("agora") LocalDateTime agora);
}
