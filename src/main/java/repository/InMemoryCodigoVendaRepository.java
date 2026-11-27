package repository;

import model.CodigoVenda;
import org.springframework.lang.NonNull;

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
    public synchronized CodigoVenda salvar(@NonNull CodigoVenda codigo) {
        if (codigo == null) {
            throw new IllegalArgumentException("Codigo de venda nao pode ser nulo.");
        }
        if (codigo.getId() == 0) {
            codigo.atribuirIdGerado(sequence.getAndIncrement());
        } else {
            sequence.updateAndGet(atual -> Math.max(atual, codigo.getId() + 1));
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
                .sorted(Comparator.comparing(CodigoVenda::isPadrao).reversed().thenComparingLong(CodigoVenda::getId))
                .toList();
    }

    @Override
    public synchronized List<CodigoVenda> listarAtivosPorVariacao(long variacaoId) {
        return codigos.stream()
                .filter(CodigoVenda::isAtivo)
                .filter(c -> c.getVariacao().getId() == variacaoId)
                .sorted(Comparator.comparingLong(CodigoVenda::getId))
                .toList();
    }

    @Override
    public synchronized List<CodigoVenda> listarAtivos() {
        return codigos.stream()
                .filter(CodigoVenda::isAtivo)
                .sorted(Comparator.comparing(CodigoVenda::getCodigo)
                        .thenComparing(Comparator.comparing(CodigoVenda::isPadrao).reversed())
                        .thenComparingLong(CodigoVenda::getId))
                .toList();
    }
}
