package repository;

import model.CategoriaCardapio;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class CategoriaCardapioRepositoryAdapter
        implements CategoriaCardapioRepository {

    private static final Sort ORDENACAO_PADRAO = Sort.by(
            Sort.Order.asc("ordemExibicao"),
            Sort.Order.asc("nomeExibicao"),
            Sort.Order.asc("id")
    );

    private final JpaCategoriaCardapioRepository delegate;

    public CategoriaCardapioRepositoryAdapter(
            JpaCategoriaCardapioRepository delegate) {

        this.delegate = Objects.requireNonNull(
                delegate, "Repositorio JPA de categoria e obrigatorio.");
    }

    @Override
    public CategoriaCardapio salvar(CategoriaCardapio categoriaCardapio) {
        if (categoriaCardapio == null) {
            throw new IllegalArgumentException(
                    "Categoria de cardapio e obrigatoria.");
        }

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
    return delegate.findAll(
            Objects.requireNonNull(ORDENACAO_PADRAO));
}
}
