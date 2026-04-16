package repository;

import model.Produto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JpaProdutoRepository extends JpaRepository<Produto, Integer> {

    Optional<Produto> findByNomeIgnoreCase(String nome);
}
