package repository;

import model.Comanda;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ComandaRepositoryAdapter implements ComandaRepository {

    private final JpaComandaRepository delegate;

    public ComandaRepositoryAdapter(JpaComandaRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public Comanda salvar(Comanda comanda) {
        // saveAndFlush: o índice único de comanda aberta precisa barrar a duplicata aqui.
        return delegate.saveAndFlush(comanda);
    }

    @Override
    public Optional<Comanda> buscarAbertaPorNumero(int numero) {
        return delegate.findFirstByNumeroAndStatus(numero, Comanda.Status.ABERTA);
    }

    @Override
    public Optional<Comanda> buscarPorVendaId(long vendaId) {
        return delegate.findFirstByVendaId(vendaId);
    }

    @Override
    public List<Comanda> listarAbertas() {
        return delegate.findByStatusOrderByNumeroAsc(Comanda.Status.ABERTA);
    }

    @Override
    public long contarFechadasEntre(java.time.LocalDateTime inicio, java.time.LocalDateTime fim) {
        return delegate.countByStatusAndFechadaEmGreaterThanEqualAndFechadaEmLessThan(Comanda.Status.FECHADA, inicio, fim);
    }
}
