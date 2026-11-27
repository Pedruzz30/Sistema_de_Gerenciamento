package repository;

import model.EmbalagemProduto;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class EmbalagemProdutoRepositoryAdapter
        implements EmbalagemProdutoRepository {

    private final JpaEmbalagemProdutoRepository delegate;

    public EmbalagemProdutoRepositoryAdapter(
            JpaEmbalagemProdutoRepository delegate) {

        this.delegate = Objects.requireNonNull(
                delegate, "Repositorio JPA de embalagem e obrigatorio.");
    }

    @Override
    public EmbalagemProduto salvar(EmbalagemProduto embalagem) {
        if (embalagem == null) {
            throw new IllegalArgumentException(
                    "Embalagem do produto e obrigatoria.");
        }

        return delegate.save(embalagem);
    }

    @Override
    public Optional<EmbalagemProduto> buscarPorId(long id) {
        return delegate.findById(id);
    }

    @Override
    public List<EmbalagemProduto> listarPorProduto(int produtoId) {
        return delegate.findAllByProdutoIdOrderByNomeAsc(produtoId);
    }
}
