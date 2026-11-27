package repository;

import model.MovimentoEstoque;
import org.springframework.lang.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface JpaMovimentoEstoqueRepository extends JpaRepository<MovimentoEstoque, Long> {

    @Query("select m from MovimentoEstoque m where m.movimentoEstornado.id = :movimentoId order by m.id asc")
    @NonNull
    List<MovimentoEstoque> findEstornosByMovimentoId(@Param("movimentoId") long movimentoId);

    @NonNull
    List<MovimentoEstoque> findAllByOrderByCriadoEmDescIdDesc();
}
