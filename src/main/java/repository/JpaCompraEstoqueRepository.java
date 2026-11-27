package repository;

import model.CompraEstoque;
import jakarta.persistence.LockModeType;
import org.springframework.lang.NonNull;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
    @NonNull
    Optional<CompraEstoque> findById(@NonNull Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CompraEstoque c where c.id = :id")
    Optional<CompraEstoque> findByIdForUpdate(@Param("id") long id);

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
