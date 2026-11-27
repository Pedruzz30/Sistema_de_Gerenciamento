package repository;

import model.Caixa;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class CaixaRepositoryAdapter implements CaixaRepository {

    private final JpaCaixaRepository delegate;

    public CaixaRepositoryAdapter(JpaCaixaRepository delegate) {
        this.delegate = Objects.requireNonNull(delegate,
                "Repositorio JPA de caixa e obrigatorio.");
    }

    @Override
    public List<Caixa> listarAtivosNoPeriodo(
            LocalDateTime inicio,
            LocalDateTime fim) {

        return delegate.listarAtivosNoPeriodo(inicio, fim);
    }

    @Override
    public long proximoId() {
        return delegate.findTopByOrderByIdDesc()
                .map(caixa -> caixa.getId() + 1)
                .orElse(1L);
    }

    @Override
    public Caixa salvar(Caixa caixa) {
        return delegate.save(Objects.requireNonNull(
                caixa, "Caixa e obrigatorio."));
    }

    @Override
    public Optional<Caixa> buscarPorId(long id) {
        return delegate.findById(id);
    }

    @Override
    public Optional<Caixa> buscarAbertoporNumero(int numeroCaixa) {
        List<Caixa> abertos = delegate
                .findAllByNumeroCaixaAndStatusOrderByIdAsc(
                        numeroCaixa, Caixa.Status.ABERTO);

        if (abertos.isEmpty()) {
            return Optional.empty();
        }

        if (abertos.size() > 1) {
            throw new IllegalStateException(
                    "Existe mais de uma sessao de caixa aberta para o numero "
                            + numeroCaixa + ".");
        }

        return Optional.of(Objects.requireNonNull(
                abertos.get(0),
                "Repositorio retornou uma sessao de caixa nula."));
    }

    @Override
    public List<Caixa> buscarHistoricoPorNumero(int numeroCaixa) {
        return delegate.findAllByNumeroCaixaOrderByIdAsc(numeroCaixa);
    }

    @Override
    public List<Caixa> buscarEncerradosPorData(LocalDate data) {
        LocalDateTime inicio = data.atStartOfDay();
        LocalDateTime fim = data.plusDays(1).atStartOfDay();

        return delegate
                .findAllByStatusAndDataEncerramentoGreaterThanEqualAndDataEncerramentoLessThanOrderByNumeroCaixaAsc(
                        Caixa.Status.ENCERRADO,
                        inicio,
                        fim);
    }

    @Override
    public List<Caixa> listarTodos() {
        return delegate.findAllByOrderByIdAsc();
    }
}
