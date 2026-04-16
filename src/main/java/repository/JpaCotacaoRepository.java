package repository;

import model.CotacaoMensal;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

public interface JpaCotacaoRepository extends JpaRepository<CotacaoMensal, Long> {

    @EntityGraph(attributePaths = "produto")
    Optional<CotacaoMensal> findByProdutoIdAndMesReferencia(int produtoId, YearMonth mesReferencia);

    @EntityGraph(attributePaths = "produto")
    List<CotacaoMensal> findAllByProdutoIdOrderByMesReferenciaAsc(int produtoId);

    @EntityGraph(attributePaths = "produto")
    List<CotacaoMensal> findAllByOrderByMesReferenciaDescIdDesc();

    Optional<CotacaoMensal> findTopByOrderByIdDesc();
}
