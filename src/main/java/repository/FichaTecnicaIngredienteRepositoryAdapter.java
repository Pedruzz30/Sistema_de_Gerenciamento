package repository;

import model.FichaTecnicaIngrediente;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class FichaTecnicaIngredienteRepositoryAdapter implements FichaTecnicaIngredienteRepository {

    private final JpaFichaTecnicaIngredienteRepository delegate;

    public FichaTecnicaIngredienteRepositoryAdapter(JpaFichaTecnicaIngredienteRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public FichaTecnicaIngrediente salvar(FichaTecnicaIngrediente ingrediente) {
        return delegate.save(ingrediente);
    }

    @Override
    public Optional<FichaTecnicaIngrediente> buscarPorId(long id) {
        return delegate.findById(id);
    }

    @Override
    public List<FichaTecnicaIngrediente> listarPorVersao(long fichaTecnicaVersaoId) {
        return delegate.findByVersao(fichaTecnicaVersaoId);
    }

    @Override
    public List<FichaTecnicaIngrediente> listarPorVersoes(List<Long> fichaTecnicaVersaoIds) {
        if (fichaTecnicaVersaoIds == null || fichaTecnicaVersaoIds.isEmpty()) {
            return List.of();
        }
        return delegate.findByVersoes(fichaTecnicaVersaoIds);
    }

    @Override
    public void remover(FichaTecnicaIngrediente ingrediente) {
        delegate.delete(ingrediente);
    }
}
