package repository;

import model.PausaCardapio;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class PausaCardapioRepositoryAdapter
        implements PausaCardapioRepository {

    private final JpaPausaCardapioRepository delegate;

    public PausaCardapioRepositoryAdapter(
            JpaPausaCardapioRepository delegate) {

        this.delegate = Objects.requireNonNull(
                delegate, "Repositorio JPA de pausa do cardapio e obrigatorio.");
    }

    @Override
    public PausaCardapio salvar(PausaCardapio pausa) {
        if (pausa == null) {
            throw new IllegalArgumentException(
                    "Pausa do cardapio e obrigatoria.");
        }

        return delegate.save(pausa);
    }

    @Override
    public Optional<PausaCardapio> buscarPorId(long id) {
        return delegate.findById(id);
    }

    @Override
    public List<PausaCardapio> listarTodas() {
        return delegate.findAll();
    }

    @Override
    public List<PausaCardapio> listarAtivas(
            Long filialId, LocalDateTime agora) {

        return delegate.findAtivas(filialId, agora);
    }
}
