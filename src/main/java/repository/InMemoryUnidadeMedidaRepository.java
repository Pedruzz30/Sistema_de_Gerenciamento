package repository;

import model.UnidadeMedida;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryUnidadeMedidaRepository implements UnidadeMedidaRepository {

    private final List<UnidadeMedida> unidades = new ArrayList<>();
    private final AtomicLong sequenceId = new AtomicLong(1);

    @Override
    public synchronized UnidadeMedida salvar(UnidadeMedida unidade) {
        UnidadeMedida persistida = unidade;
        if (unidade.getId() <= 0) {
            persistida = unidade.comId(sequenceId.getAndIncrement());
        } else {
            sequenceId.updateAndGet(atual -> Math.max(atual, unidade.getId() + 1));
        }
        UnidadeMedida finalPersistida = persistida;
        unidades.removeIf(u -> u.getId() == finalPersistida.getId()
                || u.getCodigo().equalsIgnoreCase(finalPersistida.getCodigo()));
        unidades.add(persistida);
        return persistida;
    }

    @Override
    public synchronized Optional<UnidadeMedida> buscarPorId(long id) {
        return unidades.stream().filter(u -> u.getId() == id).findFirst();
    }

    @Override
    public synchronized Optional<UnidadeMedida> buscarPorCodigo(String codigo) {
        if (codigo == null || codigo.isBlank()) return Optional.empty();
        return unidades.stream()
                .filter(u -> u.getCodigo().equalsIgnoreCase(codigo.trim()))
                .findFirst();
    }

    @Override
    public synchronized List<UnidadeMedida> listarTodos() {
        return unidades.stream()
                .sorted(Comparator.comparing(UnidadeMedida::getCodigo))
                .toList();
    }
}
