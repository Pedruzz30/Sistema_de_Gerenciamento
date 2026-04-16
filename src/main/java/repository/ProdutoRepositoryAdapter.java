package repository;

import model.Produto;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ProdutoRepositoryAdapter implements ProdutoRepository {

    private final JpaProdutoRepository delegate;

    public ProdutoRepositoryAdapter(JpaProdutoRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public Produto salvar(Produto produto) {
        return delegate.save(produto);
    }

    @Override
    public Optional<Produto> buscarPorId(int id) {
        return delegate.findById(id);
    }

    @Override
    public Optional<Produto> buscarPorNome(String nome) {
        return delegate.findByNomeIgnoreCase(nome);
    }

    @Override
    public List<Produto> listarTodos() {
        return delegate.findAll(Sort.by(Sort.Direction.ASC, "id"));
    }
}
