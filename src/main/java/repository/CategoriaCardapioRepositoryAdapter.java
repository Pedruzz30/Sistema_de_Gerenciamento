package repository;

import model.CategoriaCardapio;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class CategoriaCardapioRepositoryAdapter implements CategoriaCardapioRepository {

    private final JpaCategoriaCardapioRepository delegate;

    public CategoriaCardapioRepositoryAdapter(JpaCategoriaCardapioRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public CategoriaCardapio salvar(CategoriaCardapio categoriaCardapio) {
        return delegate.save(categoriaCardapio);
    }

    @Override
    public Optional<CategoriaCardapio> buscarPorId(int id) {
        return delegate.findById(id);
    }

    @Override
    public Optional<CategoriaCardapio> buscarPorCodigo(String codigo) {
        return delegate.findByCodigoIgnoreCase(codigo);
    }

    @Override
    public List<CategoriaCardapio> listarTodos() {
        return delegate.findAll(Sort.by(
                Sort.Order.asc("ordemExibicao"),
                Sort.Order.asc("nomeExibicao"),
                Sort.Order.asc("id")
        ));
    }
}
