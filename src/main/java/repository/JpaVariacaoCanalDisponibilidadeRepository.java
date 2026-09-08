package repository;

import model.CanalVenda;
import model.VariacaoCanalDisponibilidade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface JpaVariacaoCanalDisponibilidadeRepository extends JpaRepository<VariacaoCanalDisponibilidade, Long> {

    @Query("""
            select r from VariacaoCanalDisponibilidade r
            where r.variacaoItemCardapio.id = :variacaoId
              and r.filialId = :filialId
              and r.canal = :canal
            """)
    Optional<VariacaoCanalDisponibilidade> findByVariacaoFilialCanal(@Param("variacaoId") long variacaoId,
                                                                     @Param("filialId") Long filialId,
                                                                     @Param("canal") CanalVenda canal);

    @Query("""
            select r from VariacaoCanalDisponibilidade r
            join fetch r.variacaoItemCardapio v
            where r.variacaoItemCardapio.id in :variacaoIds
              and r.filialId = :filialId
            order by r.variacaoItemCardapio.id asc, r.canal asc
            """)
    List<VariacaoCanalDisponibilidade> findByVariacoesFilial(@Param("variacaoIds") Collection<Long> variacaoIds,
                                                             @Param("filialId") Long filialId);
}
