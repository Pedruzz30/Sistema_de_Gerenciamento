package repository;

import model.RegraHorarioCardapio;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class RegraHorarioCardapioRepositoryAdapter
        implements RegraHorarioCardapioRepository {

    private final JpaRegraHorarioCardapioRepository delegate;

    public RegraHorarioCardapioRepositoryAdapter(
            JpaRegraHorarioCardapioRepository delegate) {

        this.delegate = Objects.requireNonNull(
                delegate, "Repositorio JPA de regra de horario e obrigatorio.");
    }

    @Override
    public RegraHorarioCardapio salvar(RegraHorarioCardapio regra) {
        if (regra == null) {
            throw new IllegalArgumentException(
                    "Regra de horario do cardapio e obrigatoria.");
        }

        return delegate.save(regra);
    }

    @Override
    public Optional<RegraHorarioCardapio> buscarPorId(long id) {
        return delegate.findById(id);
    }

    @Override
    public List<RegraHorarioCardapio> listarTodas() {
        return delegate.findAll();
    }

    @Override
    public List<RegraHorarioCardapio> listarAtivasPorFilial(Long filialId) {
        return delegate.findAtivasByFilial(filialId);
    }

    @Override
    public void remover(RegraHorarioCardapio regra) {
        if (regra == null) {
            throw new IllegalArgumentException(
                    "Regra de horario do cardapio e obrigatoria.");
        }

        delegate.delete(regra);
    }
}
