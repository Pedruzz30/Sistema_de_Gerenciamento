
package repository;

import java.util.List;

import model.MovimentoEstoque;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repositorio Spring Data JPA.
 *
 * As consultas sao executadas no banco
 * de dados, evitando filtros em memoria
 * na camada de controller.
 */
public interface JpaMovimentoEstoqueRepository
        extends JpaRepository<MovimentoEstoque, Long> {

    // =====================================================
    // ESTORNOS
    // =====================================================

    @Query("""
            SELECT m
            FROM MovimentoEstoque m
            WHERE m.movimentoEstornado.id = :movimentoId
            ORDER BY m.id ASC
            """)
    List<MovimentoEstoque> findEstornosByMovimentoId(
            @Param("movimentoId") long movimentoId
    );

    // =====================================================
    // TODOS OS MOVIMENTOS
    // =====================================================

    List<MovimentoEstoque>
    findAllByOrderByCriadoEmDescIdDesc();

    // =====================================================
    // FILTRAR POR PRODUTO
    // =====================================================

    /**
     * Aplica WHERE no banco de dados.
     *
     * Preserva a mesma ordenacao utilizada
     * na listagem geral.
     */
    @Query("""
            SELECT m
            FROM MovimentoEstoque m
            WHERE m.produto.id = :produtoId
            ORDER BY m.criadoEm DESC, m.id DESC
            """)
    List<MovimentoEstoque> findByProdutoId(
            @Param("produtoId") int produtoId
    );
}

