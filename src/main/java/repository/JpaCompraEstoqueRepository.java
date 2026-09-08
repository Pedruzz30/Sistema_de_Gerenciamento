package repository;

import model.CompraEstoque;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JpaCompraEstoqueRepository extends JpaRepository<CompraEstoque, Long> {

    @Override
    @EntityGraph(attributePaths = {
            "fornecedor",
            "itens",
            "itens.produto",
            "itens.produto.unidadeBase",
            "itens.embalagem",
            "itens.embalagem.unidadeBase",
            "itens.unidadeCompra"
    })
    Optional<CompraEstoque> findById(Long id);

    @EntityGraph(attributePaths = {
            "fornecedor",
            "itens",
            "itens.produto",
            "itens.produto.unidadeBase",
            "itens.embalagem",
            "itens.embalagem.unidadeBase",
            "itens.unidadeCompra"
    })
    List<CompraEstoque> findAllByOrderByIdDesc();
}
