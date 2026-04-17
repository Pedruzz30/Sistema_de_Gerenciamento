package repository;

import model.FechamentoCaixa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JpaFechamentoCaixaRepository extends JpaRepository<FechamentoCaixa, Long> {

    List<FechamentoCaixa> findAllByOrderByIdAsc();
}
