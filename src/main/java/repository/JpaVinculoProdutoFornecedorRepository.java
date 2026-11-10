package repository;

import model.VinculoProdutoFornecedor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JpaVinculoProdutoFornecedorRepository extends JpaRepository<VinculoProdutoFornecedor, Long> {
    Optional<VinculoProdutoFornecedor> findByFornecedorIdAndCodigoProduto(long fornecedorId, String codigoProduto);
}
