package repository;

import model.Caixa;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface JpaCaixaRepository extends JpaRepository<Caixa, Long> {

    @Override
    @EntityGraph(attributePaths = {"operadorAtual", "movimentacoes", "movimentacoes.operador"})
    Optional<Caixa> findById(Long id);

    @EntityGraph(attributePaths = {"operadorAtual", "movimentacoes", "movimentacoes.operador"})
    List<Caixa> findAllByNumeroCaixaAndStatusOrderByIdAsc(int numeroCaixa, Caixa.Status status);

    @EntityGraph(attributePaths = {"operadorAtual", "movimentacoes", "movimentacoes.operador"})
    List<Caixa> findAllByNumeroCaixaOrderByIdAsc(int numeroCaixa);

    @EntityGraph(attributePaths = {"operadorAtual", "movimentacoes", "movimentacoes.operador"})
    List<Caixa> findAllByStatusAndDataEncerramentoGreaterThanEqualAndDataEncerramentoLessThanOrderByNumeroCaixaAsc(
            Caixa.Status status,
            LocalDateTime inicio,
            LocalDateTime fim
    );

    @EntityGraph(attributePaths = {"operadorAtual", "movimentacoes", "movimentacoes.operador"})
    List<Caixa> findAllByOrderByIdAsc();

    Optional<Caixa> findTopByOrderByIdDesc();
}
