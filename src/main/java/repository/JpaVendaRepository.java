package repository;

import jakarta.persistence.LockModeType;
import model.StatusVenda;
import model.Venda;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface JpaVendaRepository extends JpaRepository<Venda, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Venda v where v.id = :id")
    Optional<Venda> findByIdForUpdate(@Param("id") long id);

    @Query("""
            select v from Venda v
            where (:filialId is null or v.filialId = :filialId)
              and (:status is null or v.status = :status)
              and (:usuarioId is null or v.criadaPor = :usuarioId or v.finalizadaPor = :usuarioId)
              and (:dataInicio is null or v.criadaEm >= :dataInicio)
              and (:dataFim is null or v.criadaEm <= :dataFim)
            order by v.criadaEm desc, v.id desc
            """)
    List<Venda> listarFiltradas(@Param("filialId") Long filialId,
                                @Param("status") StatusVenda status,
                                @Param("usuarioId") Long usuarioId,
                                @Param("dataInicio") LocalDateTime dataInicio,
                                @Param("dataFim") LocalDateTime dataFim);
}
