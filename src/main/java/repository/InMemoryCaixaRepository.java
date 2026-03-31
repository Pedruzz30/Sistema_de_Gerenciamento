package repository;

import model.Caixa;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class InMemoryCaixaRepository implements CaixaRepository {

    private final List<Caixa> caixas = new ArrayList<>();
    private final AtomicLong sequenceId = new AtomicLong(1);

    @Override
    public long proximoId() {
        return sequenceId.getAndIncrement();
    }

    @Override
    public synchronized Caixa salvar(Caixa caixa) {
        if (caixa == null) {
            throw new IllegalArgumentException("Caixa não pode ser nulo.");
        }
        caixas.removeIf(c -> c.getId() == caixa.getId());
        caixas.add(caixa);
        return caixa;
    }

    @Override
    public synchronized Optional<Caixa> buscarPorId(long id) {
        return caixas.stream()
                .filter(c -> c.getId() == id)
                .findFirst();
    }

    @Override
    public synchronized Optional<Caixa> buscarAbertoporNumero(int numeroCaixa) {
        return caixas.stream()
                .filter(c -> c.getNumeroCaixa() == numeroCaixa)
                .filter(c -> c.getStatus() == Caixa.Status.ABERTO)
                .findFirst();
    }

    @Override
    public synchronized List<Caixa> buscarHistoricoPorNumero(int numeroCaixa) {
        return caixas.stream()
                .filter(c -> c.getNumeroCaixa() == numeroCaixa)
                .collect(Collectors.toList());
    }

    @Override
    public synchronized List<Caixa> buscarEncerradosPorData(LocalDate data) {
        return caixas.stream()
                .filter(c -> c.getStatus() == Caixa.Status.ENCERRADO)
                .filter(c -> c.getDataEncerramento() != null
                        && c.getDataEncerramento().toLocalDate().equals(data))
                .collect(Collectors.toList());
    }

    @Override
    public synchronized List<Caixa> listarTodos() {
        return new ArrayList<>(caixas);
    }
}
