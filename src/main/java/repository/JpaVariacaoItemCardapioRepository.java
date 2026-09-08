package repository;

import model.VariacaoItemCardapio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface JpaVariacaoItemCardapioRepository extends JpaRepository<VariacaoItemCardapio, Long> {

    @Query("""
            select v from VariacaoItemCardapio v
            where v.itemCardapio.id = :itemId
            order by v.padrao desc, v.ordemExibicao asc, v.nome asc, v.id asc
            """)
    List<VariacaoItemCardapio> findByItemOrdenado(@Param("itemId") int itemId);

    @Query("""
            select distinct v from VariacaoItemCardapio v
            join fetch v.itemCardapio i
            join fetch i.categoriaCardapio c
            left join fetch v.produtoDireto pd
            left join fetch pd.unidadeBase
            where v.id in :ids
            order by v.id asc
            """)
    List<VariacaoItemCardapio> findByIdsComContexto(@Param("ids") List<Long> ids);

    @Query("""
            select distinct v from VariacaoItemCardapio v
            join fetch v.itemCardapio i
            join fetch i.categoriaCardapio c
            left join fetch v.produtoDireto pd
            left join fetch pd.unidadeBase
            order by c.ordemExibicao asc, i.ordemExibicao asc, v.ordemExibicao asc, v.id asc
            """)
    List<VariacaoItemCardapio> findAllComContexto();

    @Query("""
            select v from VariacaoItemCardapio v
            where v.itemCardapio.id = :itemId
              and v.padrao = true
              and v.ativa = true
            order by v.id asc
            """)
    Optional<VariacaoItemCardapio> findPadraoAtivaByItem(@Param("itemId") int itemId);

    @Query("""
            select count(v) > 0 from VariacaoItemCardapio v
            where v.itemCardapio.id = :itemId
              and upper(v.codigo) = upper(:codigo)
              and (:ignorarId is null or v.id <> :ignorarId)
            """)
    boolean existsCodigoNoItem(@Param("itemId") int itemId,
                               @Param("codigo") String codigo,
                               @Param("ignorarId") Long ignorarId);

    @Query("""
            select count(v) > 0 from VariacaoItemCardapio v
            where v.itemCardapio.id = :itemId
              and v.padrao = true
              and v.ativa = true
              and (:ignorarId is null or v.id <> :ignorarId)
            """)
    boolean existsPadraoAtivaNoItem(@Param("itemId") int itemId,
                                    @Param("ignorarId") Long ignorarId);
}
