package repository;

import model.ItemCardapio;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class ItemCardapioRepositoryAdapter
        implements ItemCardapioRepository {

    private static final Sort ORDENACAO_PADRAO = Sort.by(
            Sort.Order.asc("categoriaCardapio.ordemExibicao"),
            Sort.Order.asc("categoriaCardapio.nomeExibicao"),
            Sort.Order.asc("ordemExibicao"),
            Sort.Order.asc("nome"),
            Sort.Order.asc("id")
    );

    private final JpaItemCardapioRepository delegate;

    public ItemCardapioRepositoryAdapter(
            JpaItemCardapioRepository delegate) {

        this.delegate = Objects.requireNonNull(
                delegate, "Repositorio JPA de item de cardapio e obrigatorio.");
    }

    @Override
    public ItemCardapio salvar(ItemCardapio itemCardapio) {
        if (itemCardapio == null) {
            throw new IllegalArgumentException(
                    "Item de cardapio e obrigatorio.");
        }

        return delegate.save(itemCardapio);
    }

    @Override
    public Optional<ItemCardapio> buscarPorId(int id) {
        return delegate.findById(id);
    }

    @Override
    public Optional<ItemCardapio> buscarPorCodigo(String codigo) {
        return delegate.findByCodigoIgnoreCase(codigo);
    }

    @Override
    public Optional<ItemCardapio> buscarPrimeiroPorProdutoVinculadoId(
            int produtoId) {

        return delegate.findFirstByProdutoVinculado_Id(produtoId);
    }

    @Override
    public List<ItemCardapio> listarTodos() {
        return delegate.findAll(
                Objects.requireNonNull(ORDENACAO_PADRAO));
    }
}
