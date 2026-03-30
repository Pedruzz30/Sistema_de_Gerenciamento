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

    public long proximoId() {
        return sequenceId.getAndIncrement();
    }

    @Override
    public NotaFiscal salvar(NotaFiscal nota) {
        if (nota == null) {
            throw new IllegalArgumentException ("Nota fiscal nao pode ser nula.");
        }
        notas.removeIf(n -> n.getId() == nota.getId());
        notas.add(nota);
        return nota;
    }

    @Override
    public Optional<NotaFiscal> buscarPorId(long id) {
        return notas.stream()
                .filter(n -> n.getId() == id)
                .findFirst();
    }

    @Override
    public List<NotaFiscal> buscarPorFornecedor(long fornecedorId) {
        return notas.stream()
                .filter(n -> n.getFornecedor().getId() == fornecedorId)
                .collect(Collectors.toList());
    }
    @Override
    public List<NotaFiscal> listarTodos() {
        return new ArrayList<>(notas);
    }

    @Override
    public void deletar(long id) {
        notas.removeIf(n -> n.getId() == id);
    }
}