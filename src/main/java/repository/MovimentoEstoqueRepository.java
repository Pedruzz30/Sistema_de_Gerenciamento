
package repository;

import java.util.List;
import java.util.Optional;

import model.MovimentoEstoque;

/**
 * Contrato de persistencia de movimentos.
 *
 * Permite implementacoes diferentes:
 * - PostgreSQL / Spring Data JPA;
 * - Repositorio em memoria para testes.
 */
public interface MovimentoEstoqueRepository {

    MovimentoEstoque salvar(
            MovimentoEstoque movimento
    );

    Optional<MovimentoEstoque> buscarPorId(
            long id
    );

    Optional<MovimentoEstoque> buscarEstornoDoMovimento(
            long movimentoId
    );

    List<MovimentoEstoque> listarEstornosDoMovimento(
            long movimentoId
    );

    List<MovimentoEstoque> listarTodos();

    /**
     * Lista apenas movimentos do produto.
     *
     * Ordem:
     * - Data decrescente;
     * - ID decrescente em caso de empate.
     */
    List<MovimentoEstoque> listarPorProduto(
            int produtoId
    );
}

