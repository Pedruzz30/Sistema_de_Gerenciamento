package repository;

import model.FichaTecnicaVersao;
import model.StatusFichaTecnicaVersao;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class FichaTecnicaVersaoRepositoryAdapter
        implements FichaTecnicaVersaoRepository {

    private final JpaFichaTecnicaVersaoRepository delegate;

    public FichaTecnicaVersaoRepositoryAdapter(
            JpaFichaTecnicaVersaoRepository delegate) {

        this.delegate = Objects.requireNonNull(
                delegate, "Repositorio JPA de versao da ficha tecnica e obrigatorio.");
    }

    @Override
    public FichaTecnicaVersao salvar(FichaTecnicaVersao versao) {
        if (versao == null) {
            throw new IllegalArgumentException(
                    "Versao da ficha tecnica e obrigatoria.");
        }

        return delegate.save(versao);
    }

    @Override
    public Optional<FichaTecnicaVersao> buscarPorId(long id) {
        return delegate.findById(id);
    }

    @Override
    public List<FichaTecnicaVersao> listarPorFicha(long fichaTecnicaId) {
        return delegate.findByFicha(fichaTecnicaId);
    }

    @Override
    public Optional<FichaTecnicaVersao> buscarRascunhoAtivoPorFicha(
            long fichaTecnicaId) {

        return delegate.findFirstByFichaAndStatus(
                fichaTecnicaId, StatusFichaTecnicaVersao.RASCUNHO);
    }

    @Override
    public int proximoNumeroVersao(long fichaTecnicaId) {
        return delegate.maxNumeroVersao(fichaTecnicaId) + 1;
    }
}
