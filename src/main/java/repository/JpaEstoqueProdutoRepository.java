package repository;

import jakarta.persistence.LockModeType;
import model.EstoqueProduto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface JpaEstoqueProdutoRepository extends JpaRepository<EstoqueProduto, Long> {

    @Query("select e from EstoqueProduto e where e.filialId = :filialId and e.produto.id = :produtoId")
    Optional<EstoqueProduto> findByFilialAndProduto(@Param("filialId") Long filialId,
                                                    @Param("produtoId") int produtoId);

    @Query("""
            select e from EstoqueProduto e
            join fetch e.produto p
            left join fetch p.unidadeBase
            where e.filialId = :filialId
              and e.produto.id in :produtoIds
            order by e.produto.id asc
            """)
    List<EstoqueProduto> findByFilialAndProdutos(@Param("filialId") Long filialId,
                                                 @Param("produtoIds") List<Integer> produtoIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EstoqueProduto e where e.filialId = :filialId and e.produto.id = :produtoId")
    Optional<EstoqueProduto> findByFilialAndProdutoForUpdate(@Param("filialId") Long filialId,
                                                             @Param("produtoId") int produtoId);

    @Query("select e from EstoqueProduto e order by e.filialId asc, e.produto.id asc")
    List<EstoqueProduto> findAllOrdenado();
}
