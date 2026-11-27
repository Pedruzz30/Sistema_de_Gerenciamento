package repository;

import model.Fornecedor;
import org.springframework.lang.NonNull;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JpaFornecedorRepository extends JpaRepository<Fornecedor, Long> {

    @Override
    @EntityGraph(attributePaths = "produtos")
    @NonNull
    Optional<Fornecedor> findById(@NonNull Long id);

    @EntityGraph(attributePaths = "produtos")
    Optional<Fornecedor> findByCnpj(String cnpj);

    @EntityGraph(attributePaths = "produtos")
    List<Fornecedor> findAllByAtivoTrueOrderByIdAsc();

    @EntityGraph(attributePaths = "produtos")
    List<Fornecedor> findAllByOrderByIdAsc();
}
