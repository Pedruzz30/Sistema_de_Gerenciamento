package repository;

import model.CodigoVenda;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JpaCodigoVendaRepository extends JpaRepository<CodigoVenda, Long> {
    List<CodigoVenda> findByCodigoAndAtivoTrueOrderByPadraoDescIdAsc(String codigo);
    List<CodigoVenda> findByVariacaoIdAndAtivoTrueOrderByIdAsc(long variacaoId);
    List<CodigoVenda> findByAtivoTrueOrderByCodigoAscPadraoDescIdAsc();
}
