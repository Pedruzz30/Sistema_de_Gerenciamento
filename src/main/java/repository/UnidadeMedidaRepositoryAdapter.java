package repository;

import model.UnidadeMedida;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class UnidadeMedidaRepositoryAdapter implements UnidadeMedidaRepository {

    private final JpaUnidadeMedidaRepository delegate;

    public UnidadeMedidaRepositoryAdapter(JpaUnidadeMedidaRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public UnidadeMedida salvar(UnidadeMedida unidade) {
        return delegate.save(unidade);
    }

    @Override
    public Optional<UnidadeMedida> buscarPorId(long id) {
        return delegate.findById(id);
    }

    @Override
    public Optional<UnidadeMedida> buscarPorCodigo(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return Optional.empty();
        }
        return delegate.findByCodigoIgnoreCase(codigo.trim());
    }

    @Override
    public List<UnidadeMedida> listarTodos() {
        return delegate.findAllByOrderByCodigoAsc();
    }
}
