package repository;

import model.Produto;
import org.springframework.lang.NonNull;

import java.util.List;
import java.util.Optional;

public interface ProdutoRepository {

    Produto salvar(@NonNull Produto produto);

    Optional<Produto> buscarPorId(int id);

    Optional<Produto> buscarPorNome(String nome);

    List<Produto> listarTodos();
}
