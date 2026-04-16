package repository;

import model.NotaFiscal;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class InMemoryNotaFiscalRepository implements NotaFiscalRepository {

    private final List<NotaFiscal> notas = new ArrayList<>();
    private final AtomicLong sequenceId = new AtomicLong(1);

    @Override
    public long proximoId() {
        return sequenceId.get();
    }

    @Override
    public synchronized NotaFiscal salvar(NotaFiscal nota) {
        if (nota == null) {
            throw new IllegalArgumentException("Nota fiscal nao pode ser nula.");
        }
        NotaFiscal persistida = nota;
        if (nota.getId() <= 0) {
            persistida = nota.comId(sequenceId.getAndIncrement());
        } else {
            sequenceId.updateAndGet(atual -> Math.max(atual, nota.getId() + 1));
        }

        final NotaFiscal notaPersistida = persistida;
        notas.removeIf(n -> n.getId() == notaPersistida.getId());
        notas.add(notaPersistida);
        return notaPersistida;
    }

    @Override
    public synchronized Optional<NotaFiscal> buscarPorId(long id) {
        return notas.stream()
                .filter(n -> n.getId() == id)
                .findFirst();
    }

    @Override
    public synchronized List<NotaFiscal> buscarPorFornecedor(long fornecedorId) {
        return notas.stream()
                .filter(n -> n.getFornecedor().getId() == fornecedorId)
                .collect(Collectors.toList());
    }

    @Override
    public synchronized List<NotaFiscal> listarTodos() {
        return new ArrayList<>(notas);
    }

    @Override
    public synchronized void deletar(long id) {
        notas.removeIf(n -> n.getId() == id);
    }
}
