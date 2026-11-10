package repository;

import model.CodigoVenda;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/** Implementação em memória para testes de unidade. */
public class InMemoryCodigoVendaRepository implements CodigoVendaRepository {

    private final List<CodigoVenda> codigos = new ArrayList<>();
    private final AtomicLong sequence = new AtomicLong(1);

    @Override
    public synchronized CodigoVenda salvar(CodigoVenda codigo) {
        if (codigo.getId() == 0) {
            codigo.atribuirIdGerado(sequence.getAndIncrement());
        }
        codigos.removeIf(c -> c.getId() == codigo.getId());
        codigos.add(codigo);
        return codigo;
    }

    @Override
    public synchronized Optional<CodigoVenda> buscarPorId(long id) {
        return codigos.stream().filter(c -> c.getId() == id).findFirst();
    }

    @Override
    public synchronized List<CodigoVenda> listarAtivosPorCodigo(String codigo) {
        return codigos.stream()
                .filter(CodigoVenda::isAtivo)
                .filter(c -> c.getCodigo().equals(codigo))
                .sorted(Comparator.comparing(CodigoVenda::isPadrao).reversed().thenComparing(CodigoVenda::getId))
                .toList();
    }

    @Override
    public synchronized List<CodigoVenda> listarAtivosPorVariacao(long variacaoId) {
        return codigos.stream()
                .filter(CodigoVenda::isAtivo)
                .filter(c -> c.getVariacao().getId() == variacaoId)
                .sorted(Comparator.comparing(CodigoVenda::getId))
                .toList();
    }

    @Override
    public synchronized List<CodigoVenda> listarAtivos() {
        return codigos.stream()
                .filter(CodigoVenda::isAtivo)
                .sorted(Comparator.comparing(CodigoVenda::getCodigo)
                        .thenComparing(Comparator.comparing(CodigoVenda::isPadrao).reversed())
                        .thenComparing(CodigoVenda::getId))
                .toList();
    }
}
