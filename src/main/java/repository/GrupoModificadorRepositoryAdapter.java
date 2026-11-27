package repository;

import model.GrupoModificador;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class GrupoModificadorRepositoryAdapter
        implements GrupoModificadorRepository {

    private static final Comparator<GrupoModificador> ORDENACAO_PADRAO =
            Comparator.comparingInt(
                    (GrupoModificador grupo) -> grupo.getOrdemExibicao())
                    .thenComparing(grupo -> grupo.getNome())
                    .thenComparingLong(grupo -> grupo.getId());

    private final JpaGrupoModificadorRepository delegate;

    public GrupoModificadorRepositoryAdapter(
            JpaGrupoModificadorRepository delegate) {

        this.delegate = Objects.requireNonNull(
                delegate, "Repositorio JPA de grupo modificador e obrigatorio.");
    }

    @Override
    public GrupoModificador salvar(GrupoModificador grupo) {
        if (grupo == null) {
            throw new IllegalArgumentException(
                    "Grupo modificador e obrigatorio.");
        }

        return delegate.save(grupo);
    }

    @Override
    public Optional<GrupoModificador> buscarPorId(long id) {
        return delegate.findById(id);
    }

    @Override
    public List<GrupoModificador> listarTodos() {
        return delegate.findAll().stream()
                .sorted(ORDENACAO_PADRAO)
                .toList();
    }

    @Override
    public boolean existeCodigo(String codigo, Long ignorarId) {
        return delegate.existsByCodigo(codigo, ignorarId);
    }
}
