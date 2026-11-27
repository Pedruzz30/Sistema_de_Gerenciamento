
package repository;

import model.OpcaoModificadorComponente;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class OpcaoModificadorComponenteRepositoryAdapter
        implements OpcaoModificadorComponenteRepository {

    private final JpaOpcaoModificadorComponenteRepository delegate;

    public OpcaoModificadorComponenteRepositoryAdapter(
            JpaOpcaoModificadorComponenteRepository delegate) {

        this.delegate = Objects.requireNonNull(
                delegate,
                "Repositorio JPA de componente do modificador e obrigatorio.");
    }

    @Override
    public OpcaoModificadorComponente salvar(
            OpcaoModificadorComponente componente) {

        if (componente == null) {
            throw new IllegalArgumentException(
                    "Componente da opcao modificadora e obrigatorio.");
        }

        return delegate.save(componente);
    }

    @Override
    public Optional<OpcaoModificadorComponente> buscarPorId(long id) {
        return delegate.findById(id);
    }

    @Override
    public List<OpcaoModificadorComponente> listarPorOpcao(long opcaoId) {
        return delegate.findByOpcao(opcaoId);
    }

    @Override
    public List<OpcaoModificadorComponente> listarPorOpcoes(
            List<Long> opcaoIds) {

        if (opcaoIds == null || opcaoIds.isEmpty()) {
            return List.of();
        }

        return delegate.findByOpcoes(opcaoIds);
    }

    @Override
    public void remover(OpcaoModificadorComponente componente) {
        if (componente == null) {
            throw new IllegalArgumentException(
                    "Componente da opcao modificadora e obrigatorio.");
        }

        delegate.delete(componente);
    }
}