package repository;

import model.NotaFiscal;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JpaNotaFiscalRepository extends JpaRepository<NotaFiscal, Long> {

    @Override
    @EntityGraph(attributePaths = {"fornecedor", "usuarioResponsavel", "itens", "itens.produto"})
    Optional<NotaFiscal> findById(Long id);

    @EntityGraph(attributePaths = {"fornecedor", "usuarioResponsavel", "itens", "itens.produto"})
    List<NotaFiscal> findAllByFornecedorIdOrderByIdAsc(long fornecedorId);

    @EntityGraph(attributePaths = {"fornecedor", "usuarioResponsavel", "itens", "itens.produto"})
    List<NotaFiscal> findAllByOrderByIdAsc();

    Optional<NotaFiscal> findTopByOrderByIdDesc();
}
