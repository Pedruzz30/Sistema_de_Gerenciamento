package repository;

import model.UnidadeMedida;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryUnidadeMedidaRepository
        implements UnidadeMedidaRepository {

    private static final Comparator<UnidadeMedida> ORDENACAO_POR_CODIGO =
            Comparator.comparing(unidade -> unidade.getCodigo());

    private final List<UnidadeMedida> unidades = new ArrayList<>();
    private final AtomicLong sequenceId = new AtomicLong(1);

    @Override
    public synchronized UnidadeMedida salvar(UnidadeMedida unidade) {
        if (unidade == null) {
            throw new IllegalArgumentException(
                    "Unidade de medida e obrigatoria.");
        }

        UnidadeMedida persistida = unidade;

        if (unidade.getId() <= 0) {
            persistida = unidade.comId(
                    sequenceId.getAndIncrement());
        } else {
            sequenceId.updateAndGet(atual ->
                    Math.max(atual, unidade.getId() + 1));
        }

        final UnidadeMedida unidadePersistida = persistida;

        unidades.removeIf(registro ->
                registro.getId() == unidadePersistida.getId()
                        || registro.getCodigo().equalsIgnoreCase(
                                unidadePersistida.getCodigo()));

        unidades.add(unidadePersistida);

        return unidadePersistida;
    }

    @Override
    public synchronized Optional<UnidadeMedida> buscarPorId(long id) {
        return unidades.stream()
                .filter(unidade -> unidade.getId() == id)
                .findFirst();
    }

    @Override
    public synchronized Optional<UnidadeMedida> buscarPorCodigo(
            String codigo) {

        if (codigo == null || codigo.isBlank()) {
            return Optional.empty();
        }

        String codigoNormalizado = codigo.trim();

        return unidades.stream()
                .filter(unidade -> unidade.getCodigo()
                        .equalsIgnoreCase(codigoNormalizado))
                .findFirst();
    }

    @Override
    public synchronized List<UnidadeMedida> listarTodos() {
        return unidades.stream()
                .sorted(ORDENACAO_POR_CODIGO)
                .toList();
    }
}
