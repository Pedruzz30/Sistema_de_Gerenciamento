package repository;

import model.MovimentoEstoque;

import java.util.List;
import java.util.Optional;

public interface MovimentoEstoqueRepository {

    MovimentoEstoque salvar(MovimentoEstoque movimento);

    Optional<MovimentoEstoque> buscarPorId(long id);

    Optional<MovimentoEstoque> buscarEstornoDoMovimento(long movimentoId);

    List<MovimentoEstoque> listarEstornosDoMovimento(long movimentoId);

    List<MovimentoEstoque> listarTodos();
}
