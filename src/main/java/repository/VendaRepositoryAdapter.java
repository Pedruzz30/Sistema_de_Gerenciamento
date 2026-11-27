package repository;

import model.StatusVenda;
import model.Venda;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class VendaRepositoryAdapter implements VendaRepository {

    private final JpaVendaRepository delegate;

    public VendaRepositoryAdapter(JpaVendaRepository delegate) {
        this.delegate = Objects.requireNonNull(
                delegate, "Repositorio JPA de venda e obrigatorio.");
    }

    @Override
    public Venda salvar(Venda venda) {
        if (venda == null) {
            throw new IllegalArgumentException(
                    "Venda e obrigatoria.");
        }

        return delegate.save(venda);
    }

    @Override
    public Optional<Venda> buscarPorId(long id) {
        return delegate.findById(id);
    }

    @Override
    public Optional<Venda> buscarPorIdComLock(long id) {
        return delegate.findByIdForUpdate(id);
    }

    @Override
    public List<Venda> listarFinalizadasEntre(
            LocalDateTime inicio, LocalDateTime fim) {

        return delegate.listarFinalizadasEntre(inicio, fim);
    }

    @Override
    public List<Venda> listar(
            Long filialId,
            StatusVenda status,
            Long usuarioId,
            LocalDateTime dataInicio,
            LocalDateTime dataFim) {

        return delegate.listarFiltradas(
                filialId,
                status,
                usuarioId,
                dataInicio,
                dataFim);
    }
}
