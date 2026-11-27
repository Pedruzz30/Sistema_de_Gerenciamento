package repository;

import model.NotaFiscal;
import org.springframework.lang.NonNull;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JpaNotaFiscalRepository extends JpaRepository<NotaFiscal, Long> {

    Optional<NotaFiscal> findByChaveAcesso(String chaveAcesso);

    @Override
    @EntityGraph(attributePaths = {"fornecedor", "usuarioResponsavel", "itens", "itens.produto"})
    @NonNull
    Optional<NotaFiscal> findById(@NonNull Long id);

    @EntityGraph(attributePaths = {"fornecedor", "usuarioResponsavel", "itens", "itens.produto"})
    List<NotaFiscal> findAllByFornecedorIdOrderByIdAsc(long fornecedorId);

    @EntityGraph(attributePaths = {"fornecedor", "usuarioResponsavel", "itens", "itens.produto"})
    List<NotaFiscal> findAllByOrderByIdAsc();

    Optional<NotaFiscal> findTopByOrderByIdDesc();
}
