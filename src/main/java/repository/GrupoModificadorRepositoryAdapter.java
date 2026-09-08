package repository;

import model.GrupoModificador;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Repository
public class GrupoModificadorRepositoryAdapter implements GrupoModificadorRepository {

    private final JpaGrupoModificadorRepository delegate;

    public GrupoModificadorRepositoryAdapter(JpaGrupoModificadorRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public GrupoModificador salvar(GrupoModificador grupo) {
        return delegate.save(grupo);
    }

    @Override
    public Optional<GrupoModificador> buscarPorId(long id) {
        return delegate.findById(id);
    }

    @Override
    public List<GrupoModificador> listarTodos() {
        return delegate.findAll().stream()
                .sorted(Comparator.comparingInt(GrupoModificador::getOrdemExibicao)
                        .thenComparing(GrupoModificador::getNome)
                        .thenComparingLong(GrupoModificador::getId))
                .toList();
    }

    @Override
    public boolean existeCodigo(String codigo, Long ignorarId) {
        return delegate.existsByCodigo(codigo, ignorarId);
    }
}
