package repository;

import model.PausaCardapio;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class PausaCardapioRepositoryAdapter implements PausaCardapioRepository {

    private final JpaPausaCardapioRepository delegate;

    public PausaCardapioRepositoryAdapter(JpaPausaCardapioRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public PausaCardapio salvar(PausaCardapio pausa) {
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
    public List<PausaCardapio> listarAtivas(Long filialId, LocalDateTime agora) {
        return delegate.findAtivas(filialId, agora);
    }
}
